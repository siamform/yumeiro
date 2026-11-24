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
process.exit(bad ? 1 : 0);
