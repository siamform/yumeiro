// ADCHAIN-1 (ADR-060): runs app/src/main/assets/js/link_chain.js on saved real shortener pages in jsdom.
// Usage: npm i jsdom@24 && node scripts/adchain1_link_chain_check.js
// jsdom has no layout, so every element gets a 120x40 box (hidden ones keep 0x0) and
// elementFromPoint returns the element measured last (the candidate itself: not covered).
const { JSDOM } = require('jsdom');
const fs = require('fs');
const path = require('path');
const root = path.join(__dirname, '..');
const js = fs.readFileSync(path.join(root, 'app/src/main/assets/js/link_chain.js'), 'utf8');
const pages = path.join(root, 'app/src/test/resources/pages');

// [file or null, inline html, expected chain, expected step (or null), expected label prefix (or null)]
const cases = JSON.parse(fs.readFileSync(path.join(pages, 'chain-cases.json'), 'utf8'));
cases.push(
  [null, '<html><body><h1>News</h1><p>Read more</p><a href="/x">Continue</a></body></html>', false, null, null],
  [null, '<html><body><article>How to get link previews in chat apps</article><button>Next</button></body></html>', false, null, null],
);

let bad = 0;
for (const [file, inline, wantChain, wantStep, wantLabel] of cases) {
  const html = inline || fs.readFileSync(path.join(pages, file), 'utf8');
  const dom = new JSDOM(html, { runScripts: 'outside-only', pretendToBeVisual: true });
  const W = dom.window;
  Object.defineProperty(W.HTMLElement.prototype, 'innerText', { get() { return this.textContent; } });
  let last = null;
  W.Element.prototype.getBoundingClientRect = function () {
    last = this;
    const cs = W.getComputedStyle(this);
    let hidden = false;
    for (let e = this; e && e.nodeType === 1; e = e.parentElement) {
      const c = W.getComputedStyle(e);
      if (c.display === 'none' || e.hidden) { hidden = true; break; }
    }
    if (hidden || cs.visibility === 'hidden') return { left: 0, top: 0, width: 0, height: 0, right: 0, bottom: 0 };
    return { left: 100, top: 200, width: 120, height: 40, right: 220, bottom: 240 };
  };
  W.Element.prototype.scrollIntoView = function () {};
  W.document.elementFromPoint = () => last;
  let out;
  try { out = JSON.parse(W.eval(js)); } catch (e) { out = { error: String(e) }; }
  const ok = out.chain === wantChain && (wantStep == null || out.step === wantStep) &&
    (wantLabel == null || (out.label || '').toLowerCase().startsWith(wantLabel.toLowerCase()));
  console.log((ok ? 'ok   ' : 'FAIL ') + (file || 'inline') + ' -> ' + JSON.stringify({ chain: out.chain, step: out.step, label: out.label, secs: out.secs, error: out.error }));
  if (!ok) bad++;
}
// ADCHAIN-2: the destination is read from the site's own /links/go answer (XHR and fetch), never made up.
function destCase(name, reqUrl, body, want, viaFetch) {
  const html = '<html><body><form id="go-link" action="/links/go" method="post"><input name="_token" value="t"></form><p>Please wait 5 seconds</p></body></html>';
  const dom = new JSDOM(html, { runScripts: 'outside-only', pretendToBeVisual: true, url: 'https://srnky.com/abc' });
  const W = dom.window;
  Object.defineProperty(W.HTMLElement.prototype, 'innerText', { get() { return this.textContent; } });
  W.Element.prototype.getBoundingClientRect = () => ({ left: 0, top: 0, width: 0, height: 0 });
  W.Element.prototype.scrollIntoView = function () {};
  // A stand-in XHR / fetch that answers like the site's server (no network).
  W.eval(`(function(){
    function X(){ this.l=[]; this.responseType=''; }
    X.prototype.open=function(m,u){ this.u=u; };
    X.prototype.addEventListener=function(t,f){ if(t==='load') this.l.push(f); };
    X.prototype.send=function(){ var x=this; x.responseText=window.__body; x.l.forEach(function(f){ f(); }); };
    window.XMLHttpRequest=X;
    window.fetch=function(u){ return Promise.resolve({ headers:{ get:function(){ return 'application/json'; } }, clone:function(){ return { text:function(){ return Promise.resolve(window.__body); } }; } }); };
  })()`);
  W.__body = body;
  W.eval(js); // installs the listener
  if (viaFetch) W.eval(`fetch(${JSON.stringify(reqUrl)})`);
  else W.eval(`var x=new XMLHttpRequest(); x.open('POST', ${JSON.stringify(reqUrl)}); x.send('a=1');`);
  return new Promise((res) => setTimeout(() => {
    const out = JSON.parse(W.eval(js));
    const ok = (out.dest || null) === want;
    console.log((ok ? 'ok   ' : 'FAIL ') + name + ' -> dest=' + out.dest);
    if (!ok) bad++;
    res();
  }, 20));
}
(async () => {
  await destCase('links/go xhr', '/links/go', '{"status":"success","message":"","url":"https://pixeldrain.com/u/abc123"}', 'https://pixeldrain.com/u/abc123');
  await destCase('links/go fetch', '/links/go', '{"status":"success","url":"https://gofile.io/d/AbC12"}', 'https://gofile.io/d/AbC12', true);
  await destCase('other json ignored', '/api/stats', '{"views":3,"url":"https://ads.example/x"}', null);
  await destCase('non-web url ignored', '/links/go', '{"status":"success","url":"javascript:alert(1)"}', null);
  process.exit(bad ? 1 : 0);
})();
