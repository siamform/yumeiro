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

    fun isSiteCheck(message: String?): Boolean {
        val m = message?.lowercase() ?: return false
        return markers.any { it in m } || Regex("""\b(403|503)\b""").containsMatchIn(m) && ("forbidden" in m || "unavailable" in m || "blocked" in m)
    }

    fun message(siteName: String): String =
        "$siteName asks for a quick check that you're a person. Open $siteName, finish the check on the page, and Yumeiro loads it again."
}
