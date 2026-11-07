/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.mtrevanced.stream

import android.net.Uri
import androidx.media3.common.MimeTypes
import org.json.JSONArray
import org.json.JSONObject

/** Container / protocol of a network stream. AUTO detects from the URL. */
enum class StreamFormat(val label: String, val mimeType: String?) {
    AUTO("Auto", null),
    HLS("HLS (m3u8)", MimeTypes.APPLICATION_M3U8),
    DASH("DASH (mpd)", MimeTypes.APPLICATION_MPD),
    SMOOTH("Smooth Streaming", MimeTypes.APPLICATION_SS),
    RTSP("RTSP", MimeTypes.APPLICATION_RTSP),
    PROGRESSIVE("Progressive (mp4, ts, mkv…)", null),
}

enum class DrmScheme(val label: String) { NONE("None"), WIDEVINE("Widevine"), CLEARKEY("ClearKey") }

/** User-agent presets; many stream hosts reject the default ExoPlayer UA. */
enum class UserAgentPreset(val label: String, val value: String?) {
    DEFAULT("Yumeiro", null),
    CHROME("Chrome (Android)", "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Mobile Safari/537.36"),
    DESKTOP("Chrome (Desktop)", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36"),
    SAFARI("Safari (iPhone)", "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1"),
    VLC("VLC", "VLC/3.0.21 LibVLC/3.0.21"),
}

/**
 * A playable network stream with everything needed to reach it: custom headers (User-Agent,
 * Referer, Origin, Cookie…) act as the "CORS bypass" — the native player has no browser CORS,
 * but many hosts only answer requests that look like they come from their own web player.
 */
/** A subtitle file that comes with a stream (e.g. from an online extension). */
data class StreamSubtitle(
    val url: String,
    val label: String,
    val language: String? = null,
    val mimeType: String? = null,
    /** Headers this subtitle file needs (they can differ from the video's). Empty = use the video's. */
    val headers: Map<String, String> = emptyMap(),
)

/** A separate audio rendition that plays together with the video (some sources split them). */
data class StreamAudio(
    val url: String,
    val label: String? = null,
    val language: String? = null,
    val headers: Map<String, String> = emptyMap(),
)

data class StreamRequest(
    val url: String,
    val title: String = "",
    val format: StreamFormat = StreamFormat.AUTO,
    val userAgent: String? = null,
    val referer: String? = null,
    val origin: String? = null,
    val cookie: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val drmScheme: DrmScheme = DrmScheme.NONE,
    val drmLicenseUrl: String? = null,
    /** ClearKey pairs "kid:key" (hex), one per line or comma separated. */
    val clearKeys: String? = null,
    val logoUrl: String? = null,
    val group: String? = null,
    /**
     * Web page this stream was resolved from (Toffee-type links). Used to fetch a fresh manifest
     * when the resolved one expires; null for direct links.
     */
    val pageUrl: String? = null,
    /** Side-loaded subtitles offered by the source (online extensions). Not persisted. */
    val subtitles: List<StreamSubtitle> = emptyList(),
    /** Separate audio tracks merged with the video at playback (online extensions). Not persisted. */
    val audioTracks: List<StreamAudio> = emptyList(),
    /**
     * The [format] was declared by the source (an extension) rather than chosen by the user, so it
     * is trusted for the first attempt (no network probe) but may be re-detected after a
     * container/parse error.
     */
    val formatFromSource: Boolean = false,
) {
    /** Headers to use for a request to [uri]: a subtitle's / audio track's own headers if it has some. */
    fun headersFor(uri: String): Map<String, String>? =
        subtitles.firstOrNull { it.url == uri && it.headers.isNotEmpty() }?.headers
            ?: audioTracks.firstOrNull { it.url == uri && it.headers.isNotEmpty() }?.headers

    val displayTitle: String get() = title.ifBlank { guessTitle(url) }

    /** Full header map sent with every manifest, segment and key request. */
    fun requestHeaders(): Map<String, String> = buildMap {
        putAll(headers.filterKeys { it.isNotBlank() })
        referer?.takeIf { it.isNotBlank() }?.let { put("Referer", it) }
        origin?.takeIf { it.isNotBlank() }?.let { put("Origin", it) }
        cookie?.takeIf { it.isNotBlank() }?.let { put("Cookie", it) }
    }

    fun resolvedFormat(): StreamFormat = if (format != StreamFormat.AUTO) format else detectFormat(url)

    fun toJson(): JSONObject = JSONObject().apply {
        put("url", url); put("title", title); put("format", format.name)
        userAgent?.let { put("ua", it) }; referer?.let { put("referer", it) }
        origin?.let { put("origin", it) }; cookie?.let { put("cookie", it) }
        if (headers.isNotEmpty()) put("headers", JSONObject(headers as Map<*, *>))
        put("drm", drmScheme.name); drmLicenseUrl?.let { put("license", it) }; clearKeys?.let { put("clearkeys", it) }
        logoUrl?.let { put("logo", it) }; group?.let { put("group", it) }
        pageUrl?.let { put("page", it) }
    }

    companion object {
        fun fromJson(o: JSONObject) = StreamRequest(
            url = o.getString("url"),
            title = o.optString("title"),
            format = runCatching { StreamFormat.valueOf(o.optString("format", "AUTO")) }.getOrDefault(StreamFormat.AUTO),
            userAgent = o.optString("ua").ifBlank { null },
            referer = o.optString("referer").ifBlank { null },
            origin = o.optString("origin").ifBlank { null },
            cookie = o.optString("cookie").ifBlank { null },
            headers = o.optJSONObject("headers")?.let { h -> h.keys().asSequence().associateWith { h.getString(it) } } ?: emptyMap(),
            drmScheme = runCatching { DrmScheme.valueOf(o.optString("drm", "NONE")) }.getOrDefault(DrmScheme.NONE),
            drmLicenseUrl = o.optString("license").ifBlank { null },
            clearKeys = o.optString("clearkeys").ifBlank { null },
            logoUrl = o.optString("logo").ifBlank { null },
            group = o.optString("group").ifBlank { null },
            pageUrl = o.optString("page").ifBlank { null },
        )

        fun listToJson(list: List<StreamRequest>) = JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()
        fun listFromJson(s: String?): List<StreamRequest> = runCatching {
            val a = JSONArray(s ?: return emptyList())
            (0 until a.length()).map { fromJson(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }
}

/** Parses "Key: Value" lines into a header map. */
fun parseHeaderLines(text: String): Map<String, String> = text.lines()
    .mapNotNull { line ->
        val i = line.indexOf(':')
        if (i <= 0) null else line.substring(0, i).trim() to line.substring(i + 1).trim()
    }
    .filter { it.first.isNotEmpty() && it.first.none { c -> c == '\r' || c == '\n' } }
    .toMap()

fun detectFormat(url: String): StreamFormat {
    val u = url.trim().lowercase()
    val path = runCatching { Uri.parse(u).path ?: u }.getOrDefault(u)
    return when {
        u.startsWith("rtsp://") || u.startsWith("rtsps://") -> StreamFormat.RTSP
        path.endsWith(".m3u8") || u.contains(".m3u8?") || u.contains("/hls/") || u.contains("format=m3u8") -> StreamFormat.HLS
        path.endsWith(".mpd") || u.contains(".mpd?") || u.contains("format=mpd") || u.contains("/dash/") -> StreamFormat.DASH
        path.endsWith(".ism") || path.endsWith("/manifest") || u.contains(".ism/") || u.contains(".isml/") -> StreamFormat.SMOOTH
        else -> StreamFormat.PROGRESSIVE
    }
}

/** True when the URL most likely points at an IPTV playlist (list of channels), not a single stream. */
fun looksLikeM3uPlaylist(url: String): Boolean {
    val path = runCatching { Uri.parse(url.lowercase()).path ?: "" }.getOrDefault("")
    return path.endsWith(".m3u") || url.lowercase().contains("type=m3u") || url.lowercase().contains("get.php?")
}

fun guessTitle(url: String): String {
    val uri = runCatching { Uri.parse(url) }.getOrNull()
    val last = uri?.lastPathSegment?.substringBeforeLast('.')?.takeIf { it.isNotBlank() && it.lowercase() !in setOf("index", "master", "playlist", "manifest", "stream") }
    return last ?: uri?.host ?: "Network stream"
}
