/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.novel

/**
 * NOVEL-2 (field probe 45475470803): an LNReader plugin whose site put up a person check throws its own text, made
 * for LNReader ("Royal Road's bot check could not be passed. Open a novel from Royal Road in WebView, …"). Yumeiro
 * has no such WebView button, so the page told people to do something they couldn't. Pure: which errors are a
 * site check, and Yumeiro's sentence for them. The screen adds "Open <site>", which runs Yumeiro's own check page
 * (BrowserCheck.openSite: the person passes it themselves; nothing is solved for them) and loads again once passed.
 */
object NovelSiteCheck {
    private val markers = listOf(
        "bot check", "cloudflare", "captcha", "in webview", "just a moment", "attention required",
        "verify you are human", "are you a robot", "checking your browser", "turnstile", "ddos-guard",
    )
    private val statusCode = Regex("""\b(403|503)\b""")

    fun isSiteCheck(message: String?): Boolean {
        val m = message?.lowercase() ?: return false
        return markers.any { it in m } || statusCode.containsMatchIn(m) && ("forbidden" in m || "unavailable" in m || "blocked" in m)
    }

    /**
     * B7: the host a request ended on when it is not the site asked for (nor the source's own site), e.g. MTL Novel's
     * list redirecting to an ad page; null when it stayed. `www.` and sub-domains of the same site count as the same. Pure.
     */
    fun awayHost(requested: String, final: String, site: String): String? {
        fun host(u: String) = runCatching { java.net.URI(u).host?.lowercase()?.removePrefix("www.") }.getOrNull()
        val f = host(final) ?: return null
        val same = { h: String? -> h != null && (f == h || f.endsWith(".$h") || h.endsWith(".$f")) }
        return if (same(host(requested)) || same(host(site))) null else f
    }

    private val cleartext = Regex("""CLEARTEXT communication to (\S+) not permitted""")

    /**
     * B7: the host of a plain-http hop Android refused (MTL Novel redirects its list to http://survey-twist.com, so the
     * request failed with no response to read the host from); null for other failures. Pure.
     */
    fun cleartextHost(message: String?): String? = message?.let { cleartext.find(it)?.groupValues?.get(1)?.lowercase() }

    /** B7: an empty list because the site's firewall refused the request (no check page to pass). */
    fun blockedMessage(siteName: String): String =
        "$siteName's firewall refused Yumeiro's request, so it sent no novels. Try again later or on another network, or pick another source."

    /** B7: an empty list because the site sent Yumeiro elsewhere. */
    fun movedMessage(siteName: String, host: String): String =
        "$siteName sent Yumeiro to another site ($host) instead of its novels. The site may have moved or closed; try again later or pick another source."

    /** B7: the error to raise for an empty first page, or null when empty really means empty. */
    fun emptyReason(siteName: String, challenged: Boolean, away: String?, blocked: Boolean = false): String? = when {
        challenged -> "$siteName: Cloudflare check (just a moment)"
        blocked -> blockedMessage(siteName)
        away != null -> movedMessage(siteName, away)
        else -> null
    }

    fun message(siteName: String): String =
        "$siteName asks for a quick check that you're a person. Open $siteName, finish the check on the page, and Yumeiro loads it again."
}
