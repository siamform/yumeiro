/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.web

import android.content.Context
import kotlinx.serialization.json.Json

/**
 * WEB-BLOCK-1 (owner list 5): the element blocker of Yumeiro's browser. AdFilter already stops ad requests;
 * this hides what is left on the page: common ad boxes (a short generic list, like EasyList's generic hiding
 * rules) and any element the user picks with "Block element" (one tap hides it, remembered for that site).
 * Selectors are checked before they reach the page, so a saved rule can never inject other CSS or script.
 */
object ElementBlocker {
    private const val PREFS = "yu_web_block"
    private const val KEY = "rules"
    const val MAX_PER_SITE = 60
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var rules: Map<String, List<String>>? = null

    /** Ad containers that are never page content. Kept short on purpose: a wrong rule hides real content. */
    val GENERIC = listOf(
        "ins.adsbygoogle", "div[id^=\"div-gpt-ad\"]", "div[id^=\"google_ads_\"]", "iframe[id^=\"google_ads_iframe\"]",
        "iframe[src*=\"doubleclick.net\"]", "iframe[src*=\"googlesyndication.com\"]", "iframe[src*=\"exoclick\"]",
        "iframe[src*=\"adsterra\"]", "iframe[src*=\"popads\"]", "a[href*=\"/aff_c?\"]", "div[class^=\"adsbox\"]",
        ".adsbygoogle", ".ad-slot", ".ad-banner", ".ad-container", ".banner-ad", ".sticky-ad", "#ad-banner", "#sticky-ad",
        "[data-ad-slot]", "[data-ad-client]", "[id^=\"taboola-\"]", ".OUTBRAIN",
    )

    /** A selector the picker made, or null when it is not a plain CSS selector. */
    fun valid(selector: String): String? {
        val s = selector.trim()
        if (s.isEmpty() || s.length > 300) return null
        if (s.any { it == '{' || it == '}' || it == ';' || it == '<' || it == '\\' || it == '\n' || it == '@' }) return null
        if (s.contains("/*") || s.contains("url(", true) || s.contains("expression", true)) return null
        return s
    }

    /** The site key: "watch.example.co.uk" and "example.co.uk" share their rules. */
    fun siteOf(url: String): String? = AdShield.hostOf(url)?.let { AdShield.site(it) }

    private fun load(context: Context): Map<String, List<String>> = rules ?: synchronized(this) {
        rules ?: (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?.let { runCatching { json.decodeFromString<Map<String, List<String>>>(it) }.getOrNull() }.orEmpty()).also { rules = it }
    }

    fun rulesFor(context: Context, url: String): List<String> = siteOf(url)?.let { load(context)[it] }.orEmpty()

    /** Adds [selector] to the site's rules. Returns false when it is not a valid selector. */
    fun add(context: Context, url: String, selector: String): Boolean {
        val site = siteOf(url) ?: return false
        val sel = valid(selector) ?: return false
        val now = load(context)
        val list = (now[site].orEmpty() - sel + sel).takeLast(MAX_PER_SITE)
        save(context, now + (site to list))
        return true
    }

    fun undoLast(context: Context, url: String): Boolean {
        val site = siteOf(url) ?: return false
        val now = load(context)
        val list = now[site].orEmpty().takeIf { it.isNotEmpty() } ?: return false
        save(context, if (list.size == 1) now - site else now + (site to list.dropLast(1)))
        return true
    }

    /** GROUP E (Manage ads): every site with its hidden parts, sites sorted by name. */
    fun all(context: Context): List<Pair<String, List<String>>> = load(context).entries.filter { it.value.isNotEmpty() }
        .sortedBy { it.key }.map { it.key to it.value }

    /** GROUP E (Manage ads): stops hiding one part on [site] (a key from [all]). */
    fun removeRule(context: Context, site: String, selector: String) {
        val now = load(context)
        val list = now[site].orEmpty() - selector
        save(context, if (list.isEmpty()) now - site else now + (site to list))
    }

    /** GROUP E (Manage ads): shows everything on [site] again (a key from [all]). */
    fun clearSite(context: Context, site: String) { save(context, load(context) - site) }

    fun clear(context: Context, url: String) {
        val site = siteOf(url) ?: return
        save(context, load(context) - site)
    }

    private fun save(context: Context, map: Map<String, List<String>>) {
        rules = map
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, json.encodeToString(map)).apply()
    }

    /** The style sheet for a page: generic rules plus the site's own. Pure. */
    fun css(siteRules: List<String>): String =
        (GENERIC + siteRules.mapNotNull { valid(it) }).joinToString(",\n") + " { display: none !important; }"

    /** Script that puts [css] into the page (replacing the last copy), also into pages that rebuild <head>. */
    fun styleJs(css: String): String {
        val q = org.json.JSONObject.quote(css)
        return "(function(){try{var c=$q;var s=document.getElementById('yu-block');" +
            "if(!s){s=document.createElement('style');s.id='yu-block';(document.head||document.documentElement).appendChild(s);}" +
            "if(s.textContent!==c)s.textContent=c;}catch(e){}})();"
    }

    /**
     * One-tap picker: the next tap on the page outlines and hides the tapped element instead of clicking it, and
     * reports a selector for it to `YuBlock.onPicked`. A second call cancels picking.
     */
    const val PICK_JS = """(function(){
  if (window.__yuPick) { window.__yuPick(); return; }
  function esc(s){ return (window.CSS && CSS.escape) ? CSS.escape(s) : s.replace(/[^a-zA-Z0-9_-]/g, '\\$&'); }
  function sel(el){
    var parts = [];
    while (el && el.nodeType === 1 && el !== document.body && parts.length < 5) {
      if (el.id && !/\d{3,}/.test(el.id)) { parts.unshift('#' + esc(el.id)); break; }
      var p = el.tagName.toLowerCase();
      var cls = Array.prototype.filter.call(el.classList || [], function(c){ return !/\d{3,}/.test(c); }).slice(0, 2);
      if (cls.length) p += '.' + cls.map(esc).join('.');
      var par = el.parentElement;
      if (par) {
        var same = Array.prototype.filter.call(par.children, function(c){ return c.tagName === el.tagName; });
        if (same.length > 1) p += ':nth-of-type(' + (same.indexOf(el) + 1) + ')';
      }
      parts.unshift(p);
      el = par;
    }
    return parts.join(' > ');
  }
  var last = null;
  function over(e){ if (last) last.style.outline = ''; last = e.target; last.style.outline = '3px solid #e8b04b'; }
  function pick(e){
    e.preventDefault(); e.stopPropagation(); e.stopImmediatePropagation();
    var t = e.target; done();
    if (!t || t === document.body || t === document.documentElement) return;
    var s = sel(t); t.style.setProperty('display', 'none', 'important');
    try { YuBlock.onPicked(s); } catch (x) {}
  }
  function done(){
    if (last) last.style.outline = '';
    document.removeEventListener('click', pick, true);
    document.removeEventListener('touchstart', over, true);
    window.__yuPick = null;
    try { YuBlock.onPickEnd(); } catch (x) {}
  }
  document.addEventListener('touchstart', over, true);
  document.addEventListener('click', pick, true);
  window.__yuPick = done;
})();"""

    internal fun resetForTest() { rules = null }
}
