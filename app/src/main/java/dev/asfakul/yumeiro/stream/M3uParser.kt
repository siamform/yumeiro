/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import org.json.JSONObject

/**
 * Tolerant M3U / M3U Plus (IPTV) parser.
 *
 * Handles real-world lists: missing or repeated `#EXTM3U` headers (concatenated playlists), CRLF
 * line endings, a UTF-8 BOM, junk lines such as `404: Not Found`, titles containing commas,
 * `#EXTINF` attributes (tvg-name, tvg-logo, group-title, http-user-agent, http-referrer),
 * `#EXTGRP`, `#EXTVLCOPT` (user-agent / referrer / origin), `#KODIPROP` DRM properties,
 * `#EXTHTTP` JSON headers and `url|Header=value&...` pipe headers.
 * Never throws for malformed input; unparseable entries are skipped.
 */
object M3uParser {
    /** G7-L5-A: `key="value"` or `key='value'`. */
    private val ATTR = Regex("""([\w-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')""")
    private val URL_LINE = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://\\S+")
    private val PLAYABLE_SCHEMES = setOf("http", "https", "rtsp", "rtsps", "rtmp", "rtmps", "rtp", "udp", "mms", "srt")

    /** True when [text] is an HLS media/master playlist rather than a channel list. */
    fun isHlsPlaylist(text: String): Boolean =
        text.contains("#EXT-X-STREAM-INF") || text.contains("#EXT-X-TARGETDURATION") || text.contains("#EXT-X-MEDIA-SEQUENCE")

    /** Same test as [isHlsPlaylist] for one line (the tags never span lines); for files read line by line. */
    fun isHlsLine(line: String): Boolean = isHlsPlaylist(line)

    /** Splits an `#EXTINF:` line into (attribute part, title) at the first comma outside quotes. */
    internal fun splitExtInf(line: String): Pair<String, String> {
        val body = line.substringAfter(':', "")
        // G7-L5-A: a single quote opens a value only right after `=` (so an apostrophe stays an apostrophe).
        var quote: Char? = null
        for (i in body.indices) {
            when (val c = body[i]) {
                '"' -> quote = if (quote == null) '"' else if (quote == '"') null else quote
                '\'' -> if (quote == '\'') quote = null
                    else if (quote == null && body.substring(0, i).trimEnd().endsWith('=')) quote = '\''
                ',' -> if (quote == null) return body.substring(0, i) to body.substring(i + 1).trim()
                else -> Unit
            }
        }
        return body to ""
    }

    fun parse(text: String, baseHeaders: StreamRequest? = null, baseUrl: String? = null): List<StreamRequest> = parseLines(text.lineSequence(), baseHeaders, baseUrl)

    /**
     * R16 (G7): same as [parse] but reads lines one by one, so a large saved playlist (up to 40 MB)
     * is never held as one String next to its parsed channels on a low-RAM phone.
     */
    /** G7-L5-B: a path-like channel line (no scheme, no spaces, no colon). */
    private val RELATIVE = Regex("""^[A-Za-z0-9._~%/+\-][A-Za-z0-9._~%/+\-?=&;,!@$()*]*$""")

    /** G7-L5-B: [line] resolved against [base] (the playlist's address), or null when it isn't a link. */
    internal fun resolveRelative(line: String, base: String?): String? {
        if (base == null) return null
        val path = line.substringBefore('|')
        if (!RELATIVE.matches(path) || !(path.contains('/') || path.contains('.'))) return null
        val abs = runCatching { java.net.URI(base).resolve(path).toString() }.getOrNull() ?: return null
        return abs.takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let { a -> if (line.contains('|')) a + "|" + line.substringAfter('|') else a }
    }

    /**
     * [baseUrl] (G7-L5-B): the playlist's own address; channel lines without a scheme are relative to it.
     * Null (a file picked on the phone) keeps them skipped.
     */
    fun parseLines(lines: Sequence<String>, baseHeaders: StreamRequest? = null, baseUrl: String? = null): List<StreamRequest> {
        val out = mutableListOf<StreamRequest>()
        var title = ""; var logo: String? = null; var group: String? = null; var tvgId: String? = null; var country: String? = null
        var ua: String? = null; var referer: String? = null; var origin: String? = null
        var cookie: String? = null; var extra = mutableMapOf<String, String>()
        var drm = DrmScheme.NONE; var license: String? = null
        var format = StreamFormat.AUTO

        fun reset() {
            title = ""; logo = null; group = null; tvgId = null; country = null; ua = null; referer = null; origin = null
            cookie = null; extra = mutableMapOf(); drm = DrmScheme.NONE; license = null; format = StreamFormat.AUTO
        }

        fun header(key: String, value: String) {
            when (key.trim().lowercase()) {
                "user-agent", "http-user-agent" -> ua = value
                "referer", "referrer", "http-referrer", "http-referer" -> referer = value
                "origin", "http-origin" -> origin = value
                "cookie" -> cookie = value
                else -> if (key.isNotBlank()) extra[key.trim()] = value
            }
        }

        for (raw in lines) {
            val line0 = raw.trim().trimStart('\uFEFF').trim()
            // Only right after an #EXTINF entry, so a stray word in a broken file never becomes a channel.
            val line = if (baseUrl != null && (title.isNotEmpty() || logo != null || group != null) && line0.isNotEmpty() && !line0.startsWith("#") && !URL_LINE.matches(line0.substringBefore('|')))
                resolveRelative(line0, baseUrl) ?: line0 else line0
            try {
                when {
                    line.isEmpty() || line.startsWith("#EXTM3U", ignoreCase = true) -> Unit
                    line.startsWith("#EXTINF", ignoreCase = true) -> {
                        // Options (#EXTVLCOPT / #KODIPROP) may come before or after #EXTINF, so only the
                        // title fields are replaced here; everything is cleared after each URL line.
                        val (attrPart, name) = splitExtInf(line)
                        val attrs = ATTR.findAll(attrPart).associate { m -> m.groupValues[1].lowercase() to (m.groups[2] ?: m.groups[3])?.value.orEmpty().trim() }
                        title = name.ifBlank { attrs["tvg-name"].orEmpty() }
                        logo = attrs["tvg-logo"]?.ifBlank { null }
                        tvgId = attrs["tvg-id"]?.ifBlank { null } // F-15: guide (XMLTV) channel id
                        country = LiveCountry.of(attrs["tvg-country"], tvgId)
                        group = attrs["group-title"]?.ifBlank { null }
                        attrs["http-user-agent"]?.ifBlank { null }?.let { ua = it }
                        attrs["http-referrer"]?.ifBlank { null }?.let { referer = it }
                    }
                    line.startsWith("#EXTGRP:", ignoreCase = true) -> group = line.substringAfter(':').trim().ifBlank { group }
                    line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                        val opt = line.substringAfter(':')
                        val key = opt.substringBefore('=').trim().lowercase(); val v = opt.substringAfter('=', "").trim()
                        if (v.isNotEmpty()) when (key) {
                            "http-user-agent" -> ua = v
                            "http-referrer", "http-referer" -> referer = v
                            "http-origin" -> origin = v
                            "http-cookie" -> cookie = v
                        }
                    }
                    line.startsWith("#EXTHTTP:", ignoreCase = true) -> {
                        val json = runCatching { JSONObject(line.substringAfter(':')) }.getOrNull()
                        json?.keys()?.forEach { k -> json.optString(k).takeIf { it.isNotEmpty() }?.let { header(k, it) } }
                    }
                    line.startsWith("#KODIPROP:", ignoreCase = true) -> {
                        val opt = line.substringAfter(':')
                        val key = opt.substringBefore('=').trim().lowercase(); val v = opt.substringAfter('=', "").trim()
                        when (key) {
                            "inputstream.adaptive.license_type" -> drm = when {
                                v.contains("widevine", true) -> DrmScheme.WIDEVINE
                                v.contains("clearkey", true) -> DrmScheme.CLEARKEY
                                else -> drm
                            }
                            "inputstream.adaptive.license_key" -> license = v.ifBlank { null }
                            "inputstream.adaptive.manifest_type" -> format = when (v.lowercase()) {
                                "mpd", "dash" -> StreamFormat.DASH
                                "hls", "m3u8" -> StreamFormat.HLS
                                "ism", "smooth", "ss" -> StreamFormat.SMOOTH
                                else -> format
                            }
                            "inputstream.adaptive.stream_headers", "inputstream.adaptive.common_headers" ->
                                v.split('&').forEach { h ->
                                    val k = h.substringBefore('=', ""); if (k.isNotBlank()) header(k, decode(h.substringAfter('=', "")))
                                }
                        }
                    }
                    line.startsWith("#") -> Unit
                    URL_LINE.matches(line.substringBefore('|')) -> {
                        val url = line.substringBefore('|').trim()
                        val scheme = url.substringBefore("://").lowercase()
                        if (scheme in PLAYABLE_SCHEMES) {
                            val pipe = line.substringAfter('|', "")
                            if (pipe.isNotBlank()) pipe.split('&').forEach { h ->
                                val k = h.substringBefore('=', ""); if (k.isNotBlank()) header(k, decode(h.substringAfter('=', "")))
                            }
                            val lic = license
                            val clearKeys = if (drm == DrmScheme.CLEARKEY && lic != null && !lic.startsWith("http", true)) lic else null
                            out += StreamRequest(
                                url = url,
                                title = title.ifBlank { guessTitle(url) },
                                format = format,
                                userAgent = ua ?: baseHeaders?.userAgent,
                                referer = referer ?: baseHeaders?.referer,
                                origin = origin ?: baseHeaders?.origin,
                                cookie = cookie ?: baseHeaders?.cookie,
                                headers = extra.toMap(),
                                drmScheme = drm,
                                drmLicenseUrl = lic?.takeIf { it.startsWith("http", true) },
                                clearKeys = clearKeys,
                                logoUrl = logo,
                                tvgId = tvgId,
                                country = country,
                                group = group,
                            )
                        }
                        reset()
                    }
                    // Junk such as "404: Not Found" or a broken URL: drop the pending entry.
                    else -> reset()
                }
            } catch (_: Exception) {
                reset()
            }
        }
        return out
    }

    private fun decode(v: String): String = runCatching { java.net.URLDecoder.decode(v, "UTF-8") }.getOrDefault(v)
}

/** Serialises channels back to an M3U Plus playlist that [M3uParser] (and VLC / Kodi / TiviMate) can read. */
object M3uWriter {
    private fun attr(v: String) = v.replace('"', '\'').replace('\n', ' ').replace('\r', ' ').trim()
    private fun line(v: String) = v.replace('\n', ' ').replace('\r', ' ').trim()

    fun write(channels: List<StreamRequest>, name: String? = null): String = buildString {
        append("#EXTM3U")
        if (!name.isNullOrBlank()) append(" x-playlist-name=\"").append(attr(name)).append('"')
        append('\n')
        for (c in channels) {
            append("#EXTINF:-1")
            c.tvgId?.takeIf { it.isNotBlank() }?.let { append(" tvg-id=\"").append(attr(it)).append('"') }
            c.country?.takeIf { it.isNotBlank() }?.let { append(" tvg-country=\"").append(attr(it)).append('"') }
            append(" tvg-name=\"").append(attr(c.displayTitle)).append('"')
            c.logoUrl?.takeIf { it.isNotBlank() }?.let { append(" tvg-logo=\"").append(attr(it)).append('"') }
            c.group?.takeIf { it.isNotBlank() }?.let { append(" group-title=\"").append(attr(it)).append('"') }
            append(',').append(line(c.displayTitle)).append('\n')
            c.userAgent?.takeIf { it.isNotBlank() }?.let { append("#EXTVLCOPT:http-user-agent=").append(line(it)).append('\n') }
            c.referer?.takeIf { it.isNotBlank() }?.let { append("#EXTVLCOPT:http-referrer=").append(line(it)).append('\n') }
            c.origin?.takeIf { it.isNotBlank() }?.let { append("#EXTVLCOPT:http-origin=").append(line(it)).append('\n') }
            val http = buildMap {
                c.cookie?.takeIf { it.isNotBlank() }?.let { put("cookie", it) }
                putAll(c.headers.filterKeys { it.isNotBlank() })
            }
            if (http.isNotEmpty()) append("#EXTHTTP:").append(JSONObject(http as Map<*, *>).toString()).append('\n')
            when (c.format) {
                StreamFormat.DASH -> "mpd"; StreamFormat.HLS -> "hls"; StreamFormat.SMOOTH -> "ism"; else -> null
            }?.let { append("#KODIPROP:inputstream.adaptive.manifest_type=").append(it).append('\n') }
            when (c.drmScheme) {
                DrmScheme.WIDEVINE -> append("#KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha\n")
                DrmScheme.CLEARKEY -> append("#KODIPROP:inputstream.adaptive.license_type=clearkey\n")
                DrmScheme.NONE -> Unit
            }
            (c.drmLicenseUrl ?: c.clearKeys)?.takeIf { c.drmScheme != DrmScheme.NONE && it.isNotBlank() }?.let {
                append("#KODIPROP:inputstream.adaptive.license_key=").append(line(it)).append('\n')
            }
            append(line(c.url)).append('\n')
        }
    }
}
