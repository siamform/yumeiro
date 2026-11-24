// VERIFY-1 (ADR-059): runs ExtensionCheckWindows.KIND_JS on saved real pages in jsdom.
// Usage: npm i jsdom@24 && node scripts/verify1_kind_js_check.js
const { JSDOM } = require('jsdom');
const fs = require('fs');
const path = require('path');
const root = path.join(__dirname, '..');
const src = fs.readFileSync(path.join(root, 'app/src/main/java/dev/asfakul/yumeiro/online/ExtensionCheckWindows.kt'), 'utf8');
const block = src.split('const val KIND_JS =')[1].split('\n\n')[0];
const js = [...block.matchAll(/"((?:[^"\\]|\\.)*)"/g)].map(m => m[1]).join('').replace(/\\"/g, '"').replace(/\\\\/g, '\\');
const cases = [
  ['app/src/test/resources/pages/cloudflare-block-animepahe-2026-11-24.html', 'block'],
  ['app/src/test/resources/pages/cloudflare-challenge-just-a-moment-2026-11-24.html', 'challenge'],
  [null, 'human', '<html><head><title>Demo</title></head><body><div class="cf-turnstile"></div>Verify</body></html>'],
  [null, 'page', '<html><body><h1>AnimePahe</h1>Latest releases</body></html>'],
];
let bad = 0;
for (const [file, want, inline] of cases) {
  const dom = new JSDOM(inline || fs.readFileSync(path.join(root, file), 'utf8'), { runScripts: 'outside-only' });
  Object.defineProperty(dom.window.HTMLElement.prototype, 'innerText', { get() { return this.textContent; } });
  const got = dom.window.eval(js);
  console.log((got === want ? 'ok   ' : 'FAIL ') + (file || 'inline ' + want) + ' -> ' + got);
  if (got !== want) bad++;
}
process.exit(bad ? 1 : 0);
