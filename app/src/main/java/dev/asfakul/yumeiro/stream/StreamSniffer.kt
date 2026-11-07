/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

/** What a URL actually returns, decided from content first, then MIME type, then the URL. */
enum class MediaKind(val label: String) {
    HLS("HLS stream"),
    DASH("MPEG-DASH stream"),
    SMOOTH("Smooth Streaming"),
    IPTV_PLAYLIST("IPTV channel list"),
    VIDEO("Video file / stream"),
    AUDIO("Audio file / stream"),
    WEB_PAGE("Web page"),
    UNKNOWN("Unknown"),
}

data class Sniff(val kind: MediaKind, val container: String? = null, val source: String = "content") {
    val format: StreamFormat get() = when (kind) {
        MediaKind.HLS -> StreamFormat.HLS
        MediaKind.DASH -> StreamFormat.DASH
        MediaKind.SMOOTH -> StreamFormat.SMOOTH
        else -> StreamFormat.PROGRESSIVE
    }
    val isPlayableMedia: Boolean get() = kind in setOf(MediaKind.HLS, MediaKind.DASH, MediaKind.SMOOTH, MediaKind.VIDEO, MediaKind.AUDIO)
    val label: String get() = listOfNotNull(kind.label, container).distinct().joinToString(" · ")
}

/**
 * Stream-type detection that doesn't rely on file extensions: magic bytes and manifest markers
 * are checked first (they can't lie), then the Content-Type header, and the URL only as a last hint.
 */
object StreamSniffer {
    private val VIDEO_EXT = mapOf(
        "mp4" to "MP4", "m4v" to "MP4", "mov" to "QuickTime", "mkv" to "Matroska", "webm" to "WebM",
        "ts" to "MPEG-TS", "mts" to "MPEG-TS", "m2ts" to "MPEG-TS", "flv" to "FLV", "avi" to "AVI", "3gp" to "3GP", "mpg" to "MPEG-PS", "mpeg" to "MPEG-PS",
    )
    private val AUDIO_EXT = mapOf(
        "mp3" to "MP3", "aac" to "AAC", "m4a" to "M4A", "flac" to "FLAC", "ogg" to "Ogg", "oga" to "Ogg", "opus" to "Opus",
        "wav" to "WAV", "amr" to "AMR", "mka" to "Matroska audio", "ac3" to "AC-3", "eac3" to "E-AC-3",
    )

    /** URL-only guess. Returns null when the URL doesn't say anything reliable. */
    fun fromUrl(url: String): Sniff? {
        val u = url.trim().lowercase()
        if (u.startsWith("rtsp://") || u.startsWith("rtsps://")) return Sniff(MediaKind.VIDEO, "RTSP", "url")
        val path = u.substringBefore('#').substringBefore('?').substringBefore('|')
        val query = u.substringAfter('?', "")
        val ext = path.substringAfterLast('/').substringAfterLast('.', "")
        return when {
            ext == "m3u8" -> Sniff(MediaKind.HLS, null, "url")
            ext == "m3u" -> Sniff(MediaKind.IPTV_PLAYLIST, null, "url")
            ext == "mpd" -> Sniff(MediaKind.DASH, null, "url")
            ext == "ism" || ext == "isml" || path.endsWith("/manifest") && (path.contains(".ism/") || path.contains(".isml/")) -> Sniff(MediaKind.SMOOTH, null, "url")
            ext in VIDEO_EXT -> Sniff(MediaKind.VIDEO, VIDEO_EXT[ext], "url")
            ext in AUDIO_EXT -> Sniff(MediaKind.AUDIO, AUDIO_EXT[ext], "url")
            query.contains("format=m3u8") || query.contains("type=m3u8") -> Sniff(MediaKind.HLS, null, "url")
            query.contains("format=mpd") -> Sniff(MediaKind.DASH, null, "url")
            query.contains("type=m3u") || path.endsWith("get.php") -> Sniff(MediaKind.IPTV_PLAYLIST, null, "url")
            else -> null
        }
    }

    fun fromMime(contentType: String?): Sniff? {
        val mime = contentType?.substringBefore(';')?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        return when {
            mime in setOf("application/vnd.apple.mpegurl", "application/x-mpegurl", "audio/mpegurl", "audio/x-mpegurl", "video/mpegurl") -> Sniff(MediaKind.HLS, null, "mime")
            mime == "application/dash+xml" -> Sniff(MediaKind.DASH, null, "mime")
            mime == "application/vnd.ms-sstr+xml" -> Sniff(MediaKind.SMOOTH, null, "mime")
            mime == "video/mp2t" -> Sniff(MediaKind.VIDEO, "MPEG-TS", "mime")
            mime.startsWith("video/") -> Sniff(MediaKind.VIDEO, mime.substringAfter('/').uppercase(), "mime")
            mime.startsWith("audio/") -> Sniff(MediaKind.AUDIO, mime.substringAfter('/').uppercase(), "mime")
            mime == "text/html" || mime == "application/xhtml+xml" -> Sniff(MediaKind.WEB_PAGE, null, "mime")
            mime == "audio/x-scpls" -> Sniff(MediaKind.IPTV_PLAYLIST, "PLS", "mime")
            else -> null
        }
    }

    /** Magic bytes / manifest markers. */
    fun fromContent(head: ByteArray): Sniff? {
        if (head.isEmpty()) return null
        fun at(i: Int) = if (i < head.size) head[i].toInt() and 0xFF else -1
        fun ascii(from: Int, len: Int) = if (from + len <= head.size) String(head, from, len, Charsets.ISO_8859_1) else ""

        // Binary containers
        if (at(0) == 0x47 && at(188) == 0x47) return Sniff(MediaKind.VIDEO, "MPEG-TS")
        when (ascii(4, 4)) {
            "ftyp" -> return if (ascii(8, 4).startsWith("M4A") || ascii(8, 4).startsWith("M4B")) Sniff(MediaKind.AUDIO, "M4A") else Sniff(MediaKind.VIDEO, "MP4")
            "moov", "moof", "styp", "mdat", "free" -> return Sniff(MediaKind.VIDEO, "MP4")
        }
        if (at(0) == 0x1A && at(1) == 0x45 && at(2) == 0xDF && at(3) == 0xA3) {
            val webm = String(head, 0, minOf(head.size, 64), Charsets.ISO_8859_1).contains("webm")
            return Sniff(MediaKind.VIDEO, if (webm) "WebM" else "Matroska")
        }
        if (ascii(0, 3) == "FLV") return Sniff(MediaKind.VIDEO, "FLV")
        if (ascii(0, 4) == "RIFF") return when (ascii(8, 4)) {
            "WAVE" -> Sniff(MediaKind.AUDIO, "WAV"); "AVI " -> Sniff(MediaKind.VIDEO, "AVI"); else -> null
        }
        if (at(0) == 0x00 && at(1) == 0x00 && at(2) == 0x01 && at(3) == 0xBA) return Sniff(MediaKind.VIDEO, "MPEG-PS")
        if (ascii(0, 3) == "ID3") return Sniff(MediaKind.AUDIO, "MP3")
        if (ascii(0, 4) == "fLaC") return Sniff(MediaKind.AUDIO, "FLAC")
        if (ascii(0, 4) == "OggS") return Sniff(MediaKind.AUDIO, if (ascii(28, 8).startsWith("OpusHead")) "Opus" else "Ogg")
        if (ascii(0, 5) == "#!AMR") return Sniff(MediaKind.AUDIO, "AMR")
        if (at(0) == 0x0B && at(1) == 0x77) return Sniff(MediaKind.AUDIO, "AC-3")
        if (at(0) == 0xFF && (at(1) and 0xF6) == 0xF0) return Sniff(MediaKind.AUDIO, "AAC (ADTS)")
        if (at(0) == 0xFF && (at(1) and 0xE0) == 0xE0) return Sniff(MediaKind.AUDIO, "MP3")

        // Text manifests / pages
        val text = String(head, 0, minOf(head.size, 64 * 1024), Charsets.UTF_8).trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        val lower = text.take(4096).lowercase()
        return when {
            text.startsWith("#EXTM3U") || text.startsWith("#EXTINF") -> when {
                M3uParser.isHlsPlaylist(text) -> Sniff(MediaKind.HLS)
                else -> Sniff(MediaKind.IPTV_PLAYLIST, "M3U")
            }
            M3uParser.isHlsPlaylist(text) && text.startsWith("#") -> Sniff(MediaKind.HLS)
            lower.startsWith("[playlist]") -> Sniff(MediaKind.IPTV_PLAYLIST, "PLS")
            lower.contains("<mpd") && lower.contains("urn:mpeg:dash") -> Sniff(MediaKind.DASH)
            lower.contains("<smoothstreamingmedia") -> Sniff(MediaKind.SMOOTH)
            lower.startsWith("<!doctype html") || lower.startsWith("<html") || lower.contains("<head") || lower.contains("<body") -> Sniff(MediaKind.WEB_PAGE)
            else -> null
        }
    }

    /** Full decision: content ➜ MIME ➜ URL. Generic MIME types (octet-stream, text/plain) don't count. */
    fun classify(contentType: String?, url: String, head: ByteArray): Sniff {
        fromContent(head)?.let { return it }
        val mime = fromMime(contentType)
        val byUrl = fromUrl(url)
        // An m3u8 MIME type is also used for IPTV channel lists; content already ruled that out above
        // only when we had content, so prefer the URL's opinion if it says playlist.
        if (mime != null && !(mime.kind == MediaKind.HLS && byUrl?.kind == MediaKind.IPTV_PLAYLIST)) return mime
        return byUrl ?: Sniff(MediaKind.UNKNOWN, null, "none")
    }
}

/** `[playlist]` (PLS) files used by internet radio: File1=..., Title1=... */
object PlsParser {
    fun parse(text: String): List<StreamRequest> {
        val files = sortedMapOf<Int, String>(); val titles = mutableMapOf<Int, String>()
        text.lineSequence().map { it.trim() }.forEach { line ->
            val key = line.substringBefore('=').trim().lowercase(); val value = line.substringAfter('=', "").trim()
            when {
                key.startsWith("file") -> key.removePrefix("file").toIntOrNull()?.let { files[it] = value }
                key.startsWith("title") -> key.removePrefix("title").toIntOrNull()?.let { titles[it] = value }
            }
        }
        return files.filterValues { it.startsWith("http", true) }.map { (i, u) -> StreamRequest(url = u, title = titles[i].orEmpty().ifBlank { guessTitle(u) }) }
    }
}
