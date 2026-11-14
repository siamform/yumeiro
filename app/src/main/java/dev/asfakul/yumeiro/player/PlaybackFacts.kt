/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Tracks
import java.util.Locale

/**
 * Round 10: the video size, length and audio language shown under the player. Extensions rarely
 * send them, so they are read from what the player actually decoded; whatever the stream itself
 * doesn't carry is listed in [Facts.missing] so the watch page can say so instead of staying blank.
 */
object PlaybackFacts {
    data class Facts(
        val resolution: String? = null,
        val duration: String? = null,
        val audio: String? = null,
        val size: String? = null,
        /** Size is computed from the bitrate (HLS / DASH / unknown length), not read from the file. */
        val sizeEstimated: Boolean = false,
        val missing: List<String> = emptyList(),
        /** Nothing decoded yet: show a quiet placeholder rather than "not provided". */
        val pending: Boolean = true,
    )

    /** Copies the selected audio / video track details into the snapshot. */
    fun fromTracks(s: PlaybackDiagnostics.Snapshot, tracks: Tracks): PlaybackDiagnostics.Snapshot {
        val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        val audio = audioGroups.firstNotNullOfOrNull { g -> (0 until g.length).firstOrNull { g.isTrackSelected(it) }?.let { g.getTrackFormat(it) } }
            ?: audioGroups.singleOrNull()?.takeIf { it.length == 1 }?.getTrackFormat(0)
        val video = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
            .firstNotNullOfOrNull { g -> (0 until g.length).firstOrNull { g.isTrackSelected(it) }?.let { g.getTrackFormat(it) } }
        val vb = video?.bitrateOrNull() ?: 0
        val ab = audio?.bitrateOrNull() ?: 0
        // Muxed HLS variants carry the combined bandwidth on the video format.
        val bitrate = if (vb > 0) vb + (if (audioGroups.isNotEmpty() && ab > 0) ab else 0) else ab
        return s.copy(
            audioLanguage = audio?.language?.takeIf { it.isNotBlank() && !it.equals("und", true) && !it.equals("unknown", true) },
            audioLabel = audio?.label?.takeIf { it.isNotBlank() },
            audioTracks = audioGroups.sumOf { it.length },
            audioChannels = audio?.channelCount?.takeIf { it != Format.NO_VALUE } ?: 0,
            bitrate = bitrate,
            width = if (s.width > 0) s.width else video?.width?.takeIf { it > 0 } ?: 0,
            height = if (s.height > 0) s.height else video?.height?.takeIf { it > 0 } ?: 0,
        )
    }

    private fun Format.bitrateOrNull(): Int? = (if (averageBitrate > 0) averageBitrate else if (peakBitrate > 0) peakBitrate else bitrate).takeIf { it > 0 }

    fun of(s: PlaybackDiagnostics.Snapshot, sourceAudio: String? = null): Facts {
        val pending = s.state == "IDLE" || (s.state == "BUFFERING" && !s.firstFrame && s.durationMs <= 0 && s.width <= 0)
        val resolution = if (s.width > 0 && s.height > 0) "${qualityName(s.width, s.height)} · ${s.width}×${s.height}" else null
        val duration = when {
            s.live -> "Live"
            s.durationMs > 0 -> formatDuration(s.durationMs)
            else -> null
        }
        val audio = audioName(s) ?: sourceAudio?.takeIf { it.isNotBlank() }
        val (size, estimated) = when {
            s.live -> null to false
            s.sizeBytes > 0 -> formatBytes(s.sizeBytes) to false
            s.bitrate > 0 && s.durationMs > 0 -> ("≈ " + formatBytes(s.bitrate.toLong() * (s.durationMs / 1000) / 8)) to true
            else -> null to false
        }
        val missing = buildList {
            if (resolution == null) add("video size")
            if (duration == null) add("duration")
            if (audio == null) add("audio language")
            if (size == null && !s.live) add("file size")
        }
        return Facts(resolution, duration, audio, size, estimated, if (pending) emptyList() else missing, pending)
    }

    internal fun audioName(s: PlaybackDiagnostics.Snapshot): String? {
        val lang = s.audioLanguage?.let { code ->
            val l = Locale.forLanguageTag(code.replace('_', '-'))
            l.getDisplayLanguage(Locale.ENGLISH).takeIf { it.isNotBlank() && !it.equals(code, true) } ?: code.uppercase(Locale.ROOT)
        } ?: s.audioLabel
        lang ?: return null
        val ch = when (s.audioChannels) { 1 -> "mono"; 2 -> "stereo"; 6 -> "5.1"; 8 -> "7.1"; else -> null }
        val extra = if (s.audioTracks > 1) "${s.audioTracks} tracks" else null
        return listOfNotNull(lang, ch, extra).joinToString(" · ")
    }

    internal fun qualityName(w: Int, h: Int): String {
        val short = minOf(w, h)
        return when {
            short >= 2000 -> "4K"
            short >= 1400 -> "1440p"
            short >= 1000 -> "1080p"
            short >= 700 -> "720p"
            short >= 520 -> "576p"
            short >= 460 -> "480p"
            short >= 340 -> "360p"
            else -> "${short}p"
        }
    }

    internal fun formatDuration(ms: Long): String {
        val t = ms / 1000
        val h = t / 3600; val m = (t % 3600) / 60; val sec = t % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%d:%02d", m, sec)
    }

    /**
     * Round 12: total file size from an HTTP response, or 0 when it can't be told. "Content-Range:
     * bytes 0-99/12345" gives the whole file even for a ranged request; a plain Content-Length only
     * counts when the request started at byte 0 (otherwise it's just the rest). Playlists (HLS /
     * DASH) and their small segments say nothing about the video's size, so they are ignored.
     */
    fun totalSizeFromHeaders(headers: Map<String, List<String>>, fromStart: Boolean): Long {
        fun h(name: String) = headers.entries.firstOrNull { it.key.equals(name, true) }?.value?.firstOrNull()?.trim()
        val type = h("Content-Type")?.lowercase().orEmpty()
        if (MANIFEST_TYPES.any { it in type }) return 0
        h("Content-Range")?.substringAfter('/', "")?.trim()?.toLongOrNull()?.takeIf { it > 0 }?.let { return it }
        if (!fromStart) return 0
        val enc = h("Content-Encoding")?.lowercase()
        if (enc != null && enc != "identity") return 0
        return h("Content-Length")?.toLongOrNull()?.takeIf { it > MIN_FILE_BYTES } ?: 0
    }

    private val MANIFEST_TYPES = listOf("mpegurl", "dash+xml", "vnd.ms-sstr", "text/", "json", "xml")
    /** Below this a "file" is a segment, a playlist or an error page, not the video. */
    private const val MIN_FILE_BYTES = 512L * 1024

    internal fun formatBytes(b: Long): String = when {
        b >= 1L shl 30 -> String.format(Locale.US, "%.2f GB", b / (1L shl 30).toDouble())
        b >= 1L shl 20 -> String.format(Locale.US, "%.0f MB", b / (1L shl 20).toDouble())
        b >= 1L shl 10 -> String.format(Locale.US, "%.0f KB", b / (1L shl 10).toDouble())
        else -> "$b B"
    }
}
