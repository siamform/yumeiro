/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.web

/**
 * GROUP E3: what the ad filter clears from a page after it loads, besides blocked ad hosts and pop-ups:
 *  - invisible click-catchers: a full-screen, see-through layer over the page whose only job is to turn the
 *    first tap into an ad window;
 *  - anti-ad-blocker walls: a full-screen box asking to turn the ad blocker off (and the blur it puts on the page);
 *  - the scroll lock those layers leave on the page (overflow: hidden, or body pinned with position: fixed).
 * A layer that holds a video, a player frame, a form or buttons is never touched, so players, sign-in boxes and
 * cookie choices keep working. Runs again for 20 s while the page builds itself (debounced), and only when the
 * page's ad filter is on. The counts are in `window.__yuCleanStats` (device tests read them).
 */
object PageAnnoyances {
    /** Words of an anti-ad-blocker wall (lower case). Kept in step with [CLEAN_JS]'s list; unit-tested. */
    val WALL_WORDS = listOf(
        "adblock detected", "ad blocker detected", "ad-blocker detected", "adblocker detected",
        "disable your ad blocker", "disable your adblock", "disable adblock", "turn off your ad blocker", "turn off adblock",
        "whitelist this site", "whitelist our site", "allow ads on this site", "please disable your ad",
        "you are using an ad blocker", "you're using an ad blocker", "using an adblocker",
    )

    /** True when [text] reads like an anti-ad-blocker wall. Pure. */
    fun isWall(text: String): Boolean {
        val sb = StringBuilder(text.length)
        var space = false
        for (c in text.lowercase()) {
            if (c.isWhitespace()) { if (!space) sb.append(' '); space = true } else { sb.append(if (c == '\u2019') '\'' else c); space = false }
        }
        val t = sb.toString()
        return WALL_WORDS.any { t.contains(it) }
    }

    val CLEAN_JS: String = """(function(){
  try {
    if (window.__yuClean) { window.__yuClean(); return; }
    var S = window.__yuCleanStats = {overlays:0, walls:0, unlocked:0};
    var W = ${org.json.JSONArray(WALL_WORDS)};
    function wall(t){ t = (t||'').toLowerCase().replace(/\u2019/g,"'").replace(/\s+/g,' '); for (var i=0;i<W.length;i++) if (t.indexOf(W[i])>=0) return true; return false; }
    function big(r){ var vw=innerWidth, vh=innerHeight; var w=Math.max(0,Math.min(r.right,vw)-Math.max(r.left,0)), h=Math.max(0,Math.min(r.bottom,vh)-Math.max(r.top,0)); return w*h >= 0.4*vw*vh; }
    function keep(el){ return el.matches('video,canvas,form') || !!el.querySelector('video,canvas,form,input,select,textarea,button,iframe[allowfullscreen]'); }
    function clear(c){ return c==='transparent' || /rgba\([^)]*,\s*0\)$/.test(c||''); }
    var removed = false;
    function clean(){
      var b = document.body; if (!b) return;
      var els = b.querySelectorAll('div,section,aside,a,span,ins,iframe');
      for (var i=0; i<els.length && i<3000; i++) {
        var el = els[i]; if (!el.isConnected) continue;
        // Cheap size check first (low-end phones): only page-sized boxes get their style read.
        if (!big(el.getBoundingClientRect())) continue;
        var cs = getComputedStyle(el);
        if (cs.position !== 'fixed' && cs.position !== 'absolute') continue;
        if ((parseInt(cs.zIndex,10)||0) < 100) continue;
        if (cs.display === 'none' || cs.visibility === 'hidden' || cs.pointerEvents === 'none') continue;
        var txt = (el.innerText||'').slice(0,3000);
        if (wall(txt) && !el.querySelector('video,form')) { el.remove(); S.walls++; removed = true; continue; }
        if (keep(el)) continue;
        var empty = txt.replace(/\s+/g,'').length === 0 && !el.querySelector('img,svg,picture');
        var seeThrough = parseFloat(cs.opacity) < 0.1 || (clear(cs.backgroundColor) && cs.backgroundImage === 'none');
        if (empty && seeThrough) { el.remove(); S.overlays++; removed = true; }
      }
      if (!removed) return;
      [document.documentElement, b].forEach(function(e){
        var cs = getComputedStyle(e);
        if (cs.overflow === 'hidden' || cs.overflowY === 'hidden') { e.style.setProperty('overflow','auto','important'); e.style.setProperty('overflow-y','auto','important'); S.unlocked++; }
        if (e === b && cs.position === 'fixed') { b.style.setProperty('position','static','important'); S.unlocked++; }
      });
      if (S.walls) Array.prototype.forEach.call(b.children, function(c){ var f = getComputedStyle(c).filter||''; if (f.indexOf('blur')>=0) c.style.setProperty('filter','none','important'); });
    }
    var t = null, until = Date.now() + 20000;
    window.__yuClean = function(){ until = Date.now() + 20000; clean(); };
    clean();
    try { new MutationObserver(function(){ if (Date.now() > until) return; clearTimeout(t); t = setTimeout(clean, 300); })
      .observe(document.documentElement, {childList:true, subtree:true, attributes:true, attributeFilter:['style','class']}); } catch(e) {}
  } catch (e) {}
})();"""
}
