/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.web

import java.net.URI

private val RX_SITEMETA_META = Regex("""<meta\s[^>]*>""", RegexOption.IGNORE_CASE)
private val RX_SITEMETA_LINK = Regex("""<link\s[^>]*>""", RegexOption.IGNORE_CASE)
private val RX_SITEMETA_TITLE = Regex("""<title[^>]*>([^<]{1,300})</title>""", RegexOption.IGNORE_CASE)
private val RX_SITEMETA_ATTR = Regex("""([a-zA-Z:_-]+)\s*=\s*("([^"]*)"|'([^']*)')""")
private val RX_SITEMETA_SEP = Regex("""\s*:\s+|\s+[|–—·\-]\s+""")
private val RX_SITEMETA_SPACE = Regex("""\s+""")

/**
 * WEB-ADD-1 (owner list 5): the name and icon of a website the user adds, read from the page itself
 * (og:site_name, application-name, then the title) instead of a guess from the address. Pure parsing; the
 * fetch runs on IO.
 */
object SiteMeta {
    data class Meta(val name: String?, val iconUrl: String?)

    fun parse(html: String, pageUrl: String): Meta {
        val head = html.take(200_000)
        val metas = RX_SITEMETA_META.findAll(head).map { attrs(it.value) }.toList()
        fun meta(vararg keys: String) = metas.firstOrNull { m -> keys.any { k -> m["property"].equals(k, true) || m["name"].equals(k, true) } }?.get("content")?.let(::clean)?.takeIf { it.isNotEmpty() }
        val title = RX_SITEMETA_TITLE.find(head)?.groupValues?.get(1)?.let(::clean)
        val name = meta("og:site_name") ?: meta("application-name", "apple-mobile-web-app-title") ?: title?.let { siteNameFromTitle(it, pageUrl) }
        val icons = RX_SITEMETA_LINK.findAll(head).map { attrs(it.value) }.filter { l ->
            val rel = l["rel"].orEmpty().lowercase().split(' ')
            ("icon" in rel || "apple-touch-icon" in rel) && !l["href"].isNullOrBlank()
        }.toList()
        // The largest declared icon wins; apple-touch-icon is usually 180 px, a plain "icon" 16-32 px.
        val best = icons.maxByOrNull { l -> l["sizes"]?.substringBefore('x')?.toIntOrNull() ?: if (l["rel"].orEmpty().contains("apple", true)) 180 else 32 }
        val icon = best?.get("href")?.let { resolve(pageUrl, it) } ?: resolve(pageUrl, "/favicon.ico")
        return Meta(name?.take(40), icon)
    }

    /**
     * "Watch Free Movies Online | MovieLinkBD" → "MovieLinkBD": the title part that looks like the site's
     * address, else the shortest part (site names are short), else the whole title.
     */
    fun siteNameFromTitle(title: String, pageUrl: String): String {
        val parts = title.split(RX_SITEMETA_SEP).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size < 2) return title
        val core = SavedSites.defaultName(pageUrl).lowercase().replace(" ", "")
        parts.firstOrNull { p -> p.lowercase().replace(" ", "").let { it.contains(core) || (it.length >= 3 && core.contains(it)) } }?.let { return it }
        return parts.minByOrNull { it.length }!!
    }

    private fun attrs(tag: String): Map<String, String> =
        RX_SITEMETA_ATTR.findAll(tag).associate { m -> m.groupValues[1].lowercase() to (m.groups[3]?.value ?: m.groups[4]?.value.orEmpty()) }

    private fun clean(s: String): String = android.text.Html.fromHtml(s, android.text.Html.FROM_HTML_MODE_LEGACY).toString().replace(RX_SITEMETA_SPACE, " ").trim()

    private fun resolve(base: String, href: String): String? = runCatching {
        val u = URI(base).resolve(href.trim())
        u.toString().takeIf { u.scheme == "https" || u.scheme == "http" }
    }.getOrNull()

    /** Reads the page (first 200 KB) and parses it. Null when the site can't be reached; call on IO. */
    fun fetch(client: okhttp3.OkHttpClient, url: String): Meta? = runCatching {
        val req = okhttp3.Request.Builder().url(url).header("Accept", "text/html").build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return@use null
            val body = r.body.source().let { src -> src.request(200_000); src.buffer.clone().readUtf8() }
            parse(body, r.request.url.toString())
        }
    }.getOrNull()
}
