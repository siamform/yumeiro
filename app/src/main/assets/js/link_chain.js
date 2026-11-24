/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 *
 * ADCHAIN-1 (ADR-060, owner list 23): reads a link-shortener page in Yumeiro's browser.
 * Evaluated as one expression; returns a JSON string. Read-only towards the site's logic: it never
 * fills a captcha, never changes a timer and never makes a token. It only
 *  - defuses ad layers that sit on top of the page (fake "Download is ready" / "Install …" dialogs
 *    put beside <body>, full-screen click-catcher frames): they stay in the page but cannot be seen
 *    or tapped, so a tap reaches the real button (removing them makes the sites' ad scripts show an
 *    "Adblocker detected" wall and stop loading the captcha);
 *  - reports what the page is waiting for: a person (captcha), the site's own timer, or a button;
 *  - for a button, reports where it is, after checking nothing else sits on top of it.
 * Yumeiro then taps the button like a person would (a real touch), or asks the user.
 * Studied on the owner's links (2026-11-24): srnky.com / shrinkearn (AdLinkFly + WP article steps),
 * shrinkme.click (Verify → Read → Get link, themezon.net "smg" steps).
 */
(function () {
  try {
    var W = window, D = document;
    if (!D || !D.documentElement) return JSON.stringify({ chain: false, step: 'none' });
    var CAPTCHA_SRC = /challenges\.cloudflare\.com|hcaptcha\.com|\/recaptcha\/|recaptcha\.net/i;
    var state = W.__yuChain || (W.__yuChain = { removed: 0, installed: false });

    function visible(el) {
      if (!el || !el.getBoundingClientRect) return false;
      var r = el.getBoundingClientRect();
      if (r.width < 12 || r.height < 10) return false;
      var cs = W.getComputedStyle(el);
      return cs.visibility !== 'hidden' && cs.display !== 'none' && parseFloat(cs.opacity || '1') > 0.05;
    }
    function text(el) { return ((el.innerText || el.value || el.getAttribute('aria-label') || '') + '').replace(/\s+/g, ' ').trim(); }
    function isCaptchaFrame(el) { return el.tagName === 'IFRAME' && CAPTCHA_SRC.test(el.src || ''); }
    function hasCaptchaInside(el) {
      if (isCaptchaFrame(el)) return true;
      var fs = el.querySelectorAll ? el.querySelectorAll('iframe') : [];
      for (var i = 0; i < fs.length; i++) if (isCaptchaFrame(fs[i])) return true;
      return false;
    }

    // ---- chain signals (markup the shortener scripts need; ordinary sites don't carry them) ----
    var signals = 0;
    if (D.querySelector('form[action*="/links/go"], #go-link, form[action*="/go/next"], #j-next-form, #getmylink, .wp2continuelink, #smg-start, #newtimer')) signals += 2;
    if (W.app_vars && typeof W.app_vars === 'object' && ('get_link' in W.app_vars || 'getting_link' in W.app_vars)) signals += 2;
    if (D.querySelector('#captchaShortlink, #link-view, form input[name="alias"], #j-journey, .smg-spot, .smg-block')) signals += 2;
    // textContent needs no layout (innerText does): ordinary pages are cheap to rule out.
    var raw = ((D.body && D.body.textContent) || '').slice(0, 30000).replace(/\s+/g, ' ').toLowerCase();
    if (/your link (will be|is almost) ready|please wait \d+ seconds|get link|generate link|continue to your link/.test(raw)) signals += 1;
    if (/step \d+ (of|\/) \d+/.test(raw)) signals += 1;
    if (signals < 2) return JSON.stringify({ chain: false, step: 'none', signals: signals });
    var body = (D.body && D.body.innerText || '').slice(0, 20000);
    var low = body.toLowerCase();
    var chain = true;

    // ---- defuse ad layers on top of the page (only on chain pages: ordinary sites are left alone) ----
    // They are NOT removed: the shorteners' ad scripts watch for removal and then show an
    // "Adblocker detected" wall and stop loading the captcha (seen live on srnky.com 2026-11-24).
    // A defused layer stays in the page with its size, but it can no longer be seen or catch a tap.
    function defuse(el) {
      if (!el || el.getAttribute('data-ym-off') === '1') return 0;
      el.setAttribute('data-ym-off', '1');
      el.style.setProperty('pointer-events', 'none', 'important');
      el.style.setProperty('opacity', '0', 'important');
      return 1;
    }
    function sweep() {
      var n = 0, kids = D.documentElement.children;
      for (var i = kids.length - 1; i >= 0; i--) {
        var k = kids[i];
        if (k === D.body || k === D.head || k.tagName === 'SCRIPT' || k.tagName === 'STYLE') continue;
        if (hasCaptchaInside(k)) continue;
        n += defuse(k);
      }
      // Full-viewport fixed frames / layers with the highest z-index (click-catchers) anywhere in the page.
      var all = D.querySelectorAll('body > div, body > iframe, body > a, body > ins, iframe');
      for (var j = 0; j < all.length; j++) {
        var el = all[j];
        if (!el.isConnected || hasCaptchaInside(el)) continue;
        var cs = W.getComputedStyle(el);
        if (cs.position !== 'fixed') continue;
        var z = parseInt(cs.zIndex || '0', 10) || 0;
        var r = el.getBoundingClientRect();
        var big = r.width >= W.innerWidth * 0.8 && r.height >= W.innerHeight * 0.6;
        if (z >= 2147483000 && (big || el.tagName === 'IFRAME')) n += defuse(el);
      }
      state.removed += n;
      return n;
    }
    if (chain) {
      sweep();
      if (!state.installed && W.MutationObserver) {
        state.installed = true;
        try { new MutationObserver(function () { sweep(); }).observe(D.documentElement, { childList: true }); } catch (e) {}
      }
    }
    if (!chain) return JSON.stringify({ chain: false, step: 'none', signals: signals });

    // ---- what the page waits for ----
    var CAND = 'a, button, input[type=submit], input[type=button], [role=button]';
    var FINAL = /^(get link|get your link|continue to your link|go to link|open link|get the link|download link|your link)\b/i;
    var NEXT = /^(continue next|continue|next|next step|proceed|go next|continue to next|click here to continue|i am not a robot)\b/i;
    var START = /^(generate link|click to verify|click to continue|verify|start|unlock link|click here)\b/i;
    var DECOY = /(download now|play now|watch now|install|start download|free download|^download$|^play$)/i;

    var list = [];
    var nodes = D.querySelectorAll(CAND);
    for (var q = 0; q < nodes.length; q++) {
      var b = nodes[q];
      var t = text(b);
      if (!t || t.length > 40 || DECOY.test(t)) continue;
      var rank = FINAL.test(t) ? 3 : NEXT.test(t) ? 2 : START.test(t) ? 1 : 0;
      if (!rank || !visible(b)) continue;
      var href = (b.getAttribute('href') || '').trim();
      // "#getmylink" / "#smg-next": an in-page jump to the real step (scrolling there is all it does).
      var jump = b.tagName === 'A' && href.length > 1 && href.charAt(0) === '#' && !b.getAttribute('onclick') ? D.getElementById(href.slice(1)) : null;
      var anchorOnly = !!jump;
      var off = b.disabled || /(^|\s)disabled(\s|$)/.test(b.className || '') || b.getAttribute('aria-disabled') === 'true';
      // A submit button in a form is the real step; a "#..." anchor only scrolls to it.
      // A jump anchor always ranks below every real button (themezon: "Continue…" vs the real "Generate Link").
      var score = anchorOnly ? rank : rank * 10 + (b.form || b.closest('form') ? 3 : 0);
      list.push({ el: b, t: t, off: off, score: score, anchor: anchorOnly, jump: jump });
    }
    list.sort(function (a, c) { return c.score - a.score; });
    var best = null, waiting = null;
    for (var m = 0; m < list.length; m++) { if (!list[m].off) { best = list[m]; break; } if (!waiting) waiting = list[m]; }

    var captcha = null;
    var cf = D.querySelectorAll('iframe');
    for (var f = 0; f < cf.length; f++) if (isCaptchaFrame(cf[f]) && visible(cf[f])) { captcha = cf[f]; break; }
    // Turnstile renders inside a closed shadow root, so its iframe is often not reachable:
    // fall back to the widget host / the site's own captcha box, and the hidden token field.
    var tokenField = D.querySelector('[name="cf-turnstile-response"], input[id^="cf-chl-widget"][id$="_response"], [name="h-captcha-response"], [name="g-recaptcha-response"]');
    if (!captcha) captcha = D.querySelector('.cf-turnstile, .h-captcha, .g-recaptcha, #captchaShortlink, [data-sitekey]');
    if (!captcha && tokenField && tokenField.parentElement && tokenField.parentElement !== D.body) captcha = tokenField.parentElement;
    if (captcha && !visible(captcha)) captcha = null;
    var captchaDone = !!(tokenField && tokenField.value);

    var secs = null;
    var tm = low.match(/(?:wait|ready in|in)\s*(\d{1,3})\s*(?:s\b|sec|second)/) || low.match(/\b(\d{1,3})\s*seconds?\b/);
    if (tm) secs = parseInt(tm[1], 10);
    var stepText = null;
    var st = body.match(/step\s*(\d+)\s*(?:of|\/)\s*(\d+)/i);
    if (st) stepText = st[1] + '/' + st[2];

    var out = { chain: true, signals: signals, removed: state.removed, secs: secs, stepText: stepText, vw: W.innerWidth, vh: W.innerHeight };
    if (captcha && !captchaDone) {
      // The site wants a person (and its button stays off until then). Yumeiro never ticks it.
      if (captcha.scrollIntoView && !state.captchaScrolled) { state.captchaScrolled = true; captcha.scrollIntoView({ block: 'center' }); }
      var cr = captcha.getBoundingClientRect();
      out.step = 'captcha';
      out.x = cr.left + cr.width / 2; out.y = cr.top + cr.height / 2; out.w = cr.width; out.h = cr.height;
      out.cl = cr.left;
      return JSON.stringify(out);
    }
    if (!best) {
      out.step = (waiting || secs) ? 'timer' : 'none';
      if (waiting) out.label = waiting.t;
      return JSON.stringify(out);
    }
    if (best.anchor) {
      (best.jump || best.el).scrollIntoView({ block: 'center' });
      out.step = 'scroll'; out.label = best.t;
      return JSON.stringify(out);
    }
    best.el.scrollIntoView({ block: 'center' });
    var r = best.el.getBoundingClientRect();
    var cx = r.left + r.width / 2, cy = r.top + r.height / 2;
    var top = D.elementFromPoint(cx, cy);
    if (!top || !(top === best.el || best.el.contains(top) || top.contains(best.el))) {
      // Something sits on top of the button (an ad layer): clear and look again next time.
      sweep();
      out.step = 'covered'; out.label = best.t;
      out.cover = top ? (top.tagName + (top.id ? '#' + top.id : '')) : null;
      return JSON.stringify(out);
    }
    out.step = 'ready'; out.label = best.t; out.final = best.score >= 30;
    out.x = cx; out.y = cy;
    return JSON.stringify(out);
  } catch (e) {
    return JSON.stringify({ chain: false, step: 'none', error: String(e && e.message || e) });
  }
})()
