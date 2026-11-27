/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.web

/**
 * Decides which page loads a site's WebView may make while the user solves a captcha or signs in.
 *
 * Free streaming sites hang "pop-under" ads on every tap: a click on the page opens a new window or
 * sends the whole page to an ad domain. In the captcha page that showed up as a black screen that
 * came back after every tap. The shield keeps the user on the site: pop-ups are dropped silently,
 * jumps to ad networks are refused, and a cross-site jump the user didn't ask for is held back
 * (the page offers "Open anyway"). Captcha and sign-in providers are always allowed.
 * Pure Kotlin, unit-tested.
 */
object AdShield {
    enum class Decision { ALLOW, BLOCK }

    /** Captcha / anti-bot / sign-in hosts a check page legitimately moves to. */
    private val TRUSTED = setOf(
        "challenges.cloudflare.com", "cloudflare.com", "hcaptcha.com", "recaptcha.net", "gstatic.com",
        "accounts.google.com", "google.com", "ddos-guard.net", "sucuri.net", "appleid.apple.com",
        "facebook.com", "discord.com", "github.com", "twitter.com", "x.com", "login.live.com", "microsoftonline.com",
    )

    /** Second-level public suffixes, so "a.example.co.uk" and "b.example.co.uk" count as one site. */
    private val TWO_LEVEL = setOf(
        "co.uk", "org.uk", "ac.uk", "com.au", "net.au", "co.jp", "ne.jp", "co.kr", "co.in", "net.in", "com.br", "com.bd",
        "net.bd", "com.pk", "com.tr", "com.mx", "com.ar", "com.cn", "com.tw", "com.hk", "co.id", "my.id", "web.id", "com.vn",
        "co.za", "com.sg", "com.my", "com.ph", "co.nz", "com.ua", "com.ru", "co.th", "in.th",
    )

    fun hostOf(url: String?): String? = url?.trim()?.substringAfter("://", "")?.substringBefore('/')?.substringBefore('?')
        ?.substringBefore('#')?.substringAfterLast('@')?.substringBefore(':')?.lowercase()?.trimEnd('.')?.takeIf { it.isNotBlank() }

    /** "www.watch.example.co.uk" → "example.co.uk". IP addresses stay as they are. */
    fun site(host: String): String {
        val h = host.lowercase().trimEnd('.')
        if (h.all { it.isDigit() || it == '.' } || h.contains(':')) return h
        val parts = h.split('.')
        if (parts.size <= 2) return h
        val last2 = parts.takeLast(2).joinToString(".")
        return if (last2 in TWO_LEVEL) parts.takeLast(3).joinToString(".") else last2
    }

    fun sameSite(a: String?, b: String?): Boolean {
        val ha = hostOf(a) ?: return false
        val hb = hostOf(b) ?: return false
        return site(ha) == site(hb)
    }

    private fun trusted(host: String): Boolean = generateSequence(host) { h -> h.substringAfter('.', "").takeIf { it.contains('.') } }.any { it in TRUSTED }

    private fun scheme(url: String): String = url.trim().substringBefore(':').lowercase()

    /**
     * A navigation of the whole page (not a frame) from [current] to [target].
     * [home] is the site the check page was opened for; [gesture] = the user tapped something;
     * [redirect] = a server-side redirect; [login] = sign-in page (OAuth providers allowed on tap).
     */
    fun decideNavigation(home: String, current: String?, target: String, gesture: Boolean, redirect: Boolean, login: Boolean): Decision {
        val s = scheme(target)
        if (s == "about" || s == "javascript" || s == "blob") return Decision.ALLOW
        // intent://, market://, tel:, data: pages … are how ad pages escape into other apps.
        if (s != "http" && s != "https") return Decision.BLOCK
        if (AdFilter.isAd(target)) return Decision.BLOCK
        val host = hostOf(target) ?: return Decision.BLOCK
        if (trusted(host)) return Decision.ALLOW
        if (sameSite(target, home) || sameSite(target, current)) return Decision.ALLOW
        // Mirror / CDN redirects done by the site's server are fine; pop-under jumps are client-side.
        if (redirect && !gesture) return Decision.ALLOW
        if (login && gesture) return Decision.ALLOW
        return Decision.BLOCK
    }

    /** A pop-up / new tab the page tried to open. Only the site's own pages and sign-in providers get through (into the same view). */
    fun decidePopup(home: String, current: String?, target: String?, login: Boolean): Decision {
        val t = target?.takeIf { scheme(it) == "http" || scheme(it) == "https" } ?: return Decision.BLOCK
        if (AdFilter.isAd(t)) return Decision.BLOCK
        val host = hostOf(t) ?: return Decision.BLOCK
        if (login && trusted(host)) return Decision.ALLOW
        if (login && (sameSite(t, home) || sameSite(t, current))) return Decision.ALLOW
        return Decision.BLOCK
    }

    /** A sub-resource request (script, image, frame, XHR). Ad servers get an empty answer. */
    fun blockRequest(url: String): Boolean {
        val host = hostOf(url) ?: return false
        if (trusted(host)) return false
        return AdFilter.isAd(url)
    }

    /**
     * Runs before the page's own scripts: drops `window.open` (pop-unders) and stops full-screen
     * click-catcher overlays from swallowing the first tap. Captcha widgets are iframes from
     * trusted hosts and are not touched.
     */
    const val DOCUMENT_START_JS = """(function(){try{
var noop=function(){return null};
try{Object.defineProperty(window,'open',{value:noop,writable:false,configurable:false});}catch(e){window.open=noop;}
var kill=function(){try{var els=document.querySelectorAll('body > div, body > a, body > iframe');for(var i=0;i<els.length;i++){var el=els[i];var cs=getComputedStyle(el);
var z=parseInt(cs.zIndex||'0',10);var src=(el.src||el.href||'')+'';
if(src.indexOf('challenges.cloudflare.com')>=0||src.indexOf('hcaptcha')>=0||src.indexOf('recaptcha')>=0)continue;
if((cs.position==='fixed'||cs.position==='absolute')&&z>=2147483000&&el.offsetWidth>=window.innerWidth*0.9&&el.offsetHeight>=window.innerHeight*0.9&&!(el.innerText||'').trim()){el.remove();}}}catch(e){}};
document.addEventListener('DOMContentLoaded',kill);setTimeout(kill,1500);setTimeout(kill,4000);
}catch(e){}})();"""
}

/** Wording for the shield bar. Pure → unit-tested. */
object AdShieldText {
    fun blocked(count: Int): String = if (count == 1) "Blocked 1 ad or pop-up" else "Blocked $count ads & pop-ups"
}
