/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.download

import dev.asfakul.yumeiro.online.OnlineLink

// Precompiled once (R15 audit): these ran a regex compile on every call.
private val RX_DOWNLOADNAMING_HOT_1 = Regex("\\s+")

/**
 * Folder and file names for offline copies, plus the pure rules around them (unit-tested).
 *
 * A series is saved as one folder per title, extension and audio version, every episode inside:
 * `Downloads/Solo Leveling-(HiAnime-(Sub))/S01E03 - Title.mp4`. Same idea as Aniyomi's
 * `downloads/<source>/<title>/<episode>`, flattened to one readable folder per series.
 */
object DownloadNaming {
    private val ILLEGAL = Regex("""[\\/:*?"<>|\u0000-\u001f]""")

    fun clean(s: String, max: Int = 80): String =
        s.replace(ILLEGAL, " ").replace(RX_DOWNLOADNAMING_HOT_1, " ").trim().trim('.').take(max).trim().ifBlank { "video" }

    /** `Title-(Extension-(Sub))`; without a version: `Title-(Extension)`. */
    fun seriesFolder(title: String, extension: String, version: String?): String {
        val v = version?.trim()?.takeIf { it.isNotBlank() && !it.equals("Original", true) && !it.equals("Default", true) }
        val tail = if (v != null) "${clean(extension, 40)}-(${clean(v, 20)})" else clean(extension, 40)
        return clean(title, 70) + "-(" + tail + ")"
    }

    /** `S01E03 - Name` / `E03 - Name` / `Episode 4` (no extension). */
    fun episodeBase(season: Int?, episode: Int?, name: String?, index: Int): String {
        val num = when {
            season != null && episode != null -> String.format(java.util.Locale.US, "S%02dE%02d", season, episode)
            episode != null -> String.format(java.util.Locale.US, "E%02d", episode)
            else -> "Episode ${index + 1}"
        }
        val n = name?.trim()?.takeIf { it.isNotBlank() && !it.equals("Episode $episode", true) && !it.equals("Episode", true) }
        return clean(if (n != null) "$num - $n" else num, 90)
    }

    fun extensionFor(link: OnlineLink): String = when {
        // R15: DASH and split video/audio are joined into one MP4 (WebM when both tracks are WebM).
        link.isDash -> "mp4"
        link.audioTracks.isNotEmpty() -> if (link.container == "webm") "webm" else "mp4"
        // NF-2b-4: TS HLS is remuxed to MP4 after the download (fMP4 already is MP4); the worker renames
        // it .ts only when the remux is off or fails.
        link.isHls -> "mp4"
        link.container in setOf("mp4", "mkv", "webm", "m4v", "mov", "avi") -> link.container!!
        else -> "mp4"
    }

    /** NF-2b-4: an HLS file keeps its name across the ts / mp4 naming change, so a partial file resumes. */
    fun sameFile(fileName: String, ext: String, isHls: Boolean): Boolean {
        val have = fileName.substringAfterLast('.', "").lowercase()
        return have == ext || (isHls && have in HLS_EXT && ext in HLS_EXT)
    }
    private val HLS_EXT = setOf("ts", "mp4")

    /**
     * The link to save for an episode: DASH only when [allowDash] (forced download), best one within
     * [maxHeight] (0 = best), then unknown-quality links, then the smallest one above the limit.
     * [links] are expected in [dev.asfakul.yumeiro.online.OnlineRepository.sortLinks] order.
     */
    fun pickLink(links: List<OnlineLink>, maxHeight: Int, allowDash: Boolean = false): OnlineLink? {
        val ok = links.filter { (allowDash || !it.isDash) && !it.audioOnly }
        if (ok.isEmpty()) return null
        if (maxHeight <= 0) return ok.maxByOrNull { it.quality.coerceAtLeast(0) }?.takeIf { it.quality > 0 } ?: ok.first()
        return ok.filter { it.quality in 1..maxHeight }.maxByOrNull { it.quality }
            ?: ok.firstOrNull { it.quality <= 0 }
            ?: ok.minByOrNull { it.quality }
    }

    /** True when a finished, unpinned download is older than the auto-delete timer. */
    fun expired(finishedAt: Long, keep: Boolean, days: Int, now: Long): Boolean =
        !keep && days > 0 && finishedAt > 0 && now - finishedAt >= days * 86_400_000L
}
