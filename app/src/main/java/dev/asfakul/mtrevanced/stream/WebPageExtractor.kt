/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.mtrevanced.stream

import java.net.URI

/** A media source that a web page publishes for its own player. */
data class PageCandidate(val url: String, val hint: Sniff?, val origin: String) {
    /** Lower = better. Adaptive manifests first, then progressive video, then audio, then iframes. */
    val rank: Int get() = when {
        origin == "iframe" -> 50
        hint?.kind == MediaKind.HLS || hint?.kind == MediaKind.DASH -> 0
        hint?.kind == MediaKind.SMOOTH -> 1
        hint?.kind == MediaKind.VIDEO -> 5
        hint?.kind == MediaKind.AUDIO -> 8
        else -> 20
    }
}

data class PageAnalysis(
    val title: String?,
    val candidates: List<PageCandidate>,
    /** The page mentions EME / Widevine / PlayReady — its streams are probably licence-protected. */
    val mentionsDrm: Boolean,
    /** The page shows a sign-in form (password field). */
    val hasLoginForm: Boolean,
    /** `<meta http-equiv="refresh">` target. */
    val refreshUrl: String?,
)

/**
 * Reads the playback sources a web page exposes in its HTML: `<video>`/`<source>`, Open Graph and
 * Twitter player tags, JSON-LD `contentUrl`/`embedUrl`, common player configs (`file:`, `src:`,
 * `hls:`, `manifest:`) and any quoted .m3u8 / .mpd URL in inline scripts. Pages that build their
 * player URL at runtime (signed tokens from an API, DRM) won't expose anything here — those are
 * handled by the in-app page browser (which captures what the site's own player requests).
 */
object WebPageExtractor {
    private val META = Regex("""<meta\s[^>]*>""", RegexOption.IGNORE_CASE)
    private val ATTR = Regex("""([\w:-]+)\s*=\s*("([^"]*)"|'([^']*)')""")
    private val TAG_SRC = Regex("""<(video|source|audio|iframe|embed)\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val MANIFEST_URL = Regex("""["'`]((?:https?:)?(?:\\?/){2}[^"'`\s<>]+?\.(?:m3u8|mpd)(?:\?[^"'`\s<>]*)?|/[^"'`\s<>]+?\.(?:m3u8|mpd)(?:\?[^"'`\s<>]*)?)["'`]""", RegexOption.IGNORE_CASE)
    private val PLAYER_KEY = Regex("""["']?(?:file|src|source|hls|hlsUrl|dash|dashUrl|manifest|manifestUrl|streamUrl|stream_url|videoUrl|video_url|contentUrl|playbackUrl)["']?\s*[:=]\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val TITLE = Regex("""<title[^>]*>([^<]{1,200})</title>""", RegexOption.IGNORE_CASE)
    private val DRM = Regex("""requestMediaKeySystemAccess|com\.widevine\.alpha|com\.microsoft\.playready|widevine|playready|fairplay|licenseUrl|license_url|drm\s*[:=]""", RegexOption.IGNORE_CASE)
    private val LOGIN = Regex("""<input[^>]+type\s*=\s*["']?password""", RegexOption.IGNORE_CASE)

    fun analyze(html: String, pageUrl: String): PageAnalysis {
        val out = LinkedHashMap<String, PageCandidate>()
        fun add(raw: String?, origin: String) {
            val abs = absolutize(clean(raw ?: return) ?: return, pageUrl) ?: return
            if (abs == pageUrl || abs.startsWith("blob:") || abs.startsWith("data:")) return
            val hint = StreamSniffer.fromUrl(abs)
            if (origin != "iframe" && hint == null && origin == "script") return
            if (hint?.kind == MediaKind.WEB_PAGE) return
            out.putIfAbsent(abs, PageCandidate(abs, hint, origin))
        }

        var refresh: String? = null
        META.findAll(html).forEach { m ->
            val attrs = attrs(m.value)
            val key = (attrs["property"] ?: attrs["name"] ?: attrs["itemprop"] ?: attrs["http-equiv"])?.lowercase()
            val content = attrs["content"]
            when (key) {
                "og:video", "og:video:url", "og:video:secure_url", "twitter:player:stream", "contenturl" -> add(content, "meta")
                "refresh" -> refresh = content?.substringAfter("url=", "")?.substringAfter("URL=", "")?.trim('\'', '"', ' ')?.ifBlank { null }
            }
        }
        TAG_SRC.findAll(html).forEach { m ->
            val tag = m.groupValues[1].lowercase()
            val a = attrs(m.value)
            val src = a["src"] ?: a["data-src"] ?: a["data-hls"] ?: a["data-stream"]
            when (tag) {
                "iframe", "embed" -> add(src, "iframe")
                "source" -> {
                    val type = a["type"].orEmpty().lowercase()
                    // <source> is also used by <picture>: only keep media types or media-looking URLs.
                    if (type.startsWith("video") || type.startsWith("audio") || "mpegurl" in type || "dash" in type) add(src, "tag")
                    else add(src, "script")
                }
                else -> add(src, "tag")
            }
        }
        Regex(""""(?:contentUrl|embedUrl)"\s*:\s*"([^"]+)"""").findAll(html).forEach { add(it.groupValues[1], if (it.value.contains("embed", true)) "iframe" else "jsonld") }
        PLAYER_KEY.findAll(html).forEach { add(it.groupValues[1], "script") }
        MANIFEST_URL.findAll(html).forEach { add(it.groupValues[1], "script") }

        return PageAnalysis(
            title = TITLE.find(html)?.groupValues?.get(1)?.let(::decodeEntities)?.trim()?.ifBlank { null },
            candidates = out.values.sortedBy { it.rank },
            mentionsDrm = DRM.containsMatchIn(html),
            hasLoginForm = LOGIN.containsMatchIn(html),
            refreshUrl = refresh?.let { absolutize(it, pageUrl) },
        )
    }

    private fun attrs(tag: String): Map<String, String> =
        ATTR.findAll(tag).associate { it.groupValues[1].lowercase() to decodeEntities(it.groupValues[3].ifEmpty { it.groupValues[4] }) }

    /** Unescapes JSON / JS / HTML escaping commonly found around URLs. */
    internal fun clean(raw: String): String? {
        val s = decodeEntities(raw.trim())
            .replace("\\/", "/")
            .replace("\\u0026", "&").replace("\\u003d", "=").replace("\\u002F", "/", ignoreCase = true)
            .trim()
        return s.ifBlank { null }?.takeIf { !it.startsWith("javascript:", true) && !it.contains("\${") && !it.contains("{{") }
    }

    private fun decodeEntities(s: String) = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&#x2F;", "/").replace("&lt;", "<").replace("&gt;", ">")

    internal fun absolutize(url: String, base: String): String? = runCatching {
        val u = if (url.startsWith("//")) (URI(base).scheme ?: "https") + ":" + url else url
        val resolved = URI(base).resolve(URI(u.replace(" ", "%20")))
        resolved.toString().takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
    }.getOrNull()
}
