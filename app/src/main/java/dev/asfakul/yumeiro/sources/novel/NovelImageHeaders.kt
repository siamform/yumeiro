/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.novel

import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * B7 (beta chapter): novel covers and the Novel page's banner loaded with no headers at all. LNReader plugins
 * say which headers their images need in `imageRequestInit` (NovelFull, LibRead, FreeWebNovel and others send
 * `Referer: <site>`; Webnovel writes it as `referrer`; MTLNovel sends `Alt-Used`), and many sites refuse a
 * hot-linked image without a Referer. Covers now go out with the plugin's headers, plus the site as Referer and
 * a browser User-Agent when the plugin gives none, like a reader app's browser would.
 */
object NovelImageHeaders {
    private val known = ConcurrentHashMap<String, Map<String, String>>()

    /** `imageRequestInit.headers` from `__pluginInfo`, with `referrer` written as `Referer`. Pure. */
    fun fromInfo(info: JSONObject): Map<String, String> {
        val h = info.optJSONObject("imageRequestInit")?.optJSONObject("headers") ?: return emptyMap()
        val out = LinkedHashMap<String, String>()
        h.keys().forEach { k ->
            val v = h.opt(k)?.toString()?.trim().orEmpty()
            if (v.isEmpty() || v == "null") return@forEach
            out[if (k.equals("referrer", true) || k.equals("referer", true)) "Referer" else k] = v
        }
        return out
    }

    /** What a cover request sends: the plugin's headers, then Referer = site and a browser User-Agent if missing. Pure. */
    fun forCovers(site: String, plugin: Map<String, String>, userAgent: String? = null): Map<String, String> {
        val out = LinkedHashMap(plugin)
        if (out.keys.none { it.equals("Referer", true) } && site.startsWith("http")) out["Referer"] = site.trimEnd('/') + "/"
        val ua = userAgent ?: runCatching { dev.asfakul.yumeiro.online.web.BrowserCheck.userAgent() }.getOrNull()
        if (out.keys.none { it.equals("User-Agent", true) } && !ua.isNullOrBlank()) out["User-Agent"] = ua
        return out
    }

    fun remember(pluginId: String, headers: Map<String, String>) { known[pluginId] = headers }

    /** Headers for a cover of [pluginId]: what its engine said, or the site's Referer before the engine ran. */
    fun of(pluginId: String): Map<String, String> =
        known[pluginId] ?: NovelSources.plugin(pluginId)?.let { p ->
            // Called while composing: never start WebView for its user agent here (main thread); Referer only.
            forCovers(p.site, emptyMap(), userAgent = "").also { known[pluginId] = it }
        } ?: emptyMap()
}
