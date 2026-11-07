/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.mtrevanced.stream

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
    private val ATTR = Regex("""([\w-]+)\s*=\s*"([^"]*)"""")
    private val URL_LINE = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://\\S+")
    private val PLAYABLE_SCHEMES = setOf("http", "https", "rtsp", "rtsps", "rtmp", "rtmps", "rtp", "udp", "mms", "srt")

    /** True when [text] is an HLS media/master playlist rather than a channel list. */
    fun isHlsPlaylist(text: String): Boolean =
        text.contains("#EXT-X-STREAM-INF") || text.contains("#EXT-X-TARGETDURATION") || text.contains("#EXT-X-MEDIA-SEQUENCE")

    /** Splits an `#EXTINF:` line into (attribute part, title) at the first comma outside quotes. */
    internal fun splitExtInf(line: String): Pair<String, String> {
        val body = line.substringAfter(':', "")
        var inQuotes = false
        for (i in body.indices) {
            when (body[i]) {
                '"' -> inQuotes = !inQuotes
                ',' -> if (!inQuotes) return body.substring(0, i) to body.substring(i + 1).trim()
            }
        }
        return body to ""
    }

    fun parse(text: String, baseHeaders: StreamRequest? = null): List<StreamRequest> {
        val out = mutableListOf<StreamRequest>()
        var title = ""; var logo: String? = null; var group: String? = null
        var ua: String? = null; var referer: String? = null; var origin: String? = null
        var cookie: String? = null; var extra = mutableMapOf<String, String>()
        var drm = DrmScheme.NONE; var license: String? = null
        var format = StreamFormat.AUTO

        fun reset() {
            title = ""; logo = null; group = null; ua = null; referer = null; origin = null
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

        for (raw in text.lineSequence()) {
            val line = raw.trim().trimStart('\uFEFF').trim()
            try {
                when {
                    line.isEmpty() || line.startsWith("#EXTM3U", ignoreCase = true) -> Unit
                    line.startsWith("#EXTINF", ignoreCase = true) -> {
                        // Options (#EXTVLCOPT / #KODIPROP) may come before or after #EXTINF, so only the
                        // title fields are replaced here; everything is cleared after each URL line.
                        val (attrPart, name) = splitExtInf(line)
                        val attrs = ATTR.findAll(attrPart).associate { it.groupValues[1].lowercase() to it.groupValues[2].trim() }
                        title = name.ifBlank { attrs["tvg-name"].orEmpty() }
                        logo = attrs["tvg-logo"]?.ifBlank { null }
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
