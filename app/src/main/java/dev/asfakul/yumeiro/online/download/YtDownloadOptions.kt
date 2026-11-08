/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.download

/**
 * Every YouTube quality we can save, built from the streams NewPipe Extractor reports.
 * Pure → unit-tested; [dev.asfakul.yumeiro.online.download.YtDownloads] maps StreamInfo into it.
 *
 * YouTube serves HD as separate video and audio. We download both and join them on the phone
 * with Android's own MediaMuxer (the approach NewPipe takes with its own muxers, without
 * bundling yt-dlp + ffmpeg): MP4 video (H.264 / AV1) + AAC, or WebM VP9 + Opus.
 */
object YtDownloadOptions {
    data class Video(val itag: Int, val height: Int, val fps: Int, val container: String, val codec: String, val size: Long, val videoOnly: Boolean)
    data class Audio(val itag: Int, val bitrate: Int, val container: String, val codec: String, val size: Long, val original: Boolean = true)

    data class Choice(
        val label: String,
        val detail: String,
        val videoItag: Int,
        val audioItag: Int,
        /** Output container: mp4, webm (video), m4a, webm (audio only). */
        val container: String,
        val sizeBytes: Long,
        val audioOnly: Boolean,
        val height: Int = 0,
    )

    private fun codecName(c: String): String = c.lowercase().let {
        when {
            it.startsWith("avc") -> "H.264"; it.startsWith("av01") -> "AV1"; it.startsWith("vp9") || it.startsWith("vp09") -> "VP9"
            it.startsWith("mp4a") -> "AAC"; it.startsWith("opus") -> "Opus"; it.startsWith("vorbis") -> "Vorbis"
            else -> c.substringBefore('.').uppercase().ifBlank { "?" }
        }
    }

    /** Muxable on this Android version (MediaMuxer: AV1 in MP4 from Android 14). */
    fun muxable(v: Video, sdk: Int): Boolean {
        val c = v.codec.lowercase()
        return when (v.container) {
            "mp4" -> c.startsWith("avc") || (c.startsWith("av01") && sdk >= 34) || c.startsWith("hev") || c.startsWith("hvc")
            "webm" -> c.startsWith("vp9") || c.startsWith("vp09") || c.startsWith("vp8")
            else -> false
        }
    }

    fun mb(bytes: Long): String = when {
        bytes <= 0 -> ""
        bytes >= 1_000_000_000 -> String.format(java.util.Locale.US, "%.1f GB", bytes / 1e9)
        bytes < 1_000_000 -> "<1 MB"
        else -> String.format(java.util.Locale.US, "%.0f MB", bytes / 1e6)
    }

    fun build(videos: List<Video>, audios: List<Audio>, sdk: Int): List<Choice> {
        val main = audios.filter { it.original }.ifEmpty { audios }
        val bestM4a = main.filter { it.container == "m4a" }.maxByOrNull { it.bitrate }
        val bestWebmA = main.filter { it.container == "webm" }.maxByOrNull { it.bitrate }
        val out = ArrayList<Choice>()
        // One choice per resolution + frame rate: MP4 H.264 first (plays everywhere), then VP9, AV1.
        val split = videos.filter { it.videoOnly && it.height > 0 && muxable(it, sdk) }
        split.groupBy { it.height to (if (it.fps > 30) 60 else 30) }
            .toSortedMap(compareByDescending<Pair<Int, Int>> { it.first }.thenByDescending { it.second })
            .forEach { (key, list) ->
                val pick = list.sortedWith(compareBy<Video>({ codecRank(it.codec) }, { -it.size })).firstOrNull { v ->
                    if (v.container == "mp4") bestM4a != null else bestWebmA != null
                } ?: return@forEach
                val a = if (pick.container == "mp4") bestM4a!! else bestWebmA!!
                val total = if (pick.size > 0 && a.size > 0) pick.size + a.size else 0
                out += Choice(
                    label = "${key.first}p" + if (key.second == 60) "60" else "",
                    detail = listOf(pick.container.uppercase(), codecName(pick.codec), mb(total)).filter { it.isNotBlank() }.joinToString(" · "),
                    videoItag = pick.itag, audioItag = a.itag, container = pick.container, sizeBytes = total, audioOnly = false, height = key.first,
                )
            }
        // Ready-made (muxed) files cover qualities the split list lacks (usually 360p).
        videos.filter { !it.videoOnly && it.height > 0 }.sortedByDescending { it.height }.forEach { v ->
            if (out.none { !it.audioOnly && it.height == v.height }) out += Choice(
                label = "${v.height}p", detail = listOf(v.container.uppercase(), codecName(v.codec), mb(v.size)).filter { it.isNotBlank() }.joinToString(" · "),
                videoItag = v.itag, audioItag = -1, container = v.container, sizeBytes = v.size, audioOnly = false, height = v.height,
            )
        }
        out.sortByDescending { it.height }
        // Audio only (music): every bitrate, original language track.
        main.filter { it.container == "m4a" || it.container == "webm" }.sortedByDescending { it.bitrate }.forEach { a ->
            out += Choice(
                label = "Audio ${(a.bitrate / 1000).coerceAtLeast(1)} kbps",
                detail = listOf(if (a.container == "m4a") "M4A" else "WebM", codecName(a.codec), mb(a.size)).filter { it.isNotBlank() }.joinToString(" · "),
                videoItag = -1, audioItag = a.itag, container = a.container, sizeBytes = a.size, audioOnly = true,
            )
        }
        return out.distinctBy { Triple(it.videoItag, it.audioItag, it.audioOnly) }
    }

    private fun codecRank(c: String): Int = c.lowercase().let { when { it.startsWith("avc") -> 0; it.startsWith("vp9") || it.startsWith("vp09") -> 1; it.startsWith("av01") -> 2; else -> 3 } }
}
