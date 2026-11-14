/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import android.content.Context
import dev.asfakul.yumeiro.stream.StreamRequest

/**
 * Round 13: a quality selector for every video, including YouTube.
 *
 * Streams with an adaptive ladder (most HLS / DASH) list their own renditions as player tracks.
 * For everything else this engine builds the list itself:
 *  - YouTube: every resolution YouTube offers for the video (144p up to 4K), each opened as its
 *    own stream at the same position;
 *  - extension sources: the other sources of the same episode, grouped by resolution, so picking
 *    "720p" moves to the best 720p source;
 *  - anything with a single fixed stream: the one quality it really has, labelled honestly.
 *
 * Round 2 (14): the round-13 claim "streams without quality options get Yumeiro's own ladder" was
 * not true: a single stream still showed one option, YouTube only worked when the request was
 * tagged as YouTube, and local videos had nothing. Now:
 *  - YouTube heights come from [dev.asfakul.yumeiro.online.download.YtDownloads.options], the same
 *    list the download sheet shows (one source of truth), plus any playable stream height;
 *  - a YouTube video is recognised from its page or stream URL, tagged or not;
 *  - when a video really has one rendition (or the source list has one height), the engine adds
 *    its own levels: lower ones scaled down on the GPU (media3 Presentation) and one level up
 *    scaled with Anime4K sharpening. They are labelled "Scaled by Yumeiro" / "Upscaled by Yumeiro"
 *    so nobody mistakes them for the source's own renditions.
 */
object QualityEngine {
    /** One choice. [open] returns a new request to play, or null when it switched by itself. */
    class Alt(
        val label: String, val height: Int, val note: String? = null, val current: Boolean = false,
        /** Round 2: a level the engine renders from the playing stream (no reload); null = a real rendition. */
        val engineHeight: Int? = null,
        val upscale: Boolean = false,
        val open: suspend (Context, Long) -> StreamRequest?,
    )

    /** Alternatives for [request] (off the main thread). Empty = nothing beyond the stream's own tracks. */
    suspend fun alternatives(context: Context, request: StreamRequest?, currentHeight: Int, engineHeight: Int? = null): List<Alt> {
        val id = request?.let { youTubeId(it) }
        val real = when {
            id != null -> youTube(context, id, currentHeight)
            request != null -> online(currentHeight)
            else -> emptyList()
        }
        if (real.size >= 2) return real
        return engine(currentHeight, engineHeight)
    }

    internal fun youTubeId(r: StreamRequest): String? =
        listOfNotNull(r.pageUrl, r.url).firstNotNullOfOrNull { u ->
            if (r.group == "YouTube" || dev.asfakul.yumeiro.stream.PlatformRules.isYouTube(u)) runCatching { dev.asfakul.yumeiro.youtube.YouTube.idFrom(u) }.getOrNull() else null
        }

    /** The playing rendition plus the engine's own levels; [selected] = the engine level in use. */
    internal fun engine(sourceHeight: Int, selected: Int?): List<Alt> {
        if (sourceHeight <= 0) return emptyList()
        val src = snap(sourceHeight)
        val levels = engineLevels(src)
        return levels.map { h ->
            when {
                h == src -> Alt(label(h), h, note = "Original from the source", current = selected == null) { _, _ -> null }
                h > src -> Alt(label(h), h, note = "Upscaled by Yumeiro (Anime4K)", current = selected == h, engineHeight = h, upscale = true) { _, _ -> null }
                else -> Alt(label(h), h, note = "Scaled by Yumeiro (uses less GPU, same data)", current = selected == h, engineHeight = h) { _, _ -> null }
            }
        }
    }

    /** Source height → offered heights, highest first: one step up (to 4K at most), the source, up to 4 steps down. Pure. */
    internal fun engineLevels(src: Int): List<Int> {
        val std = listOf(144, 240, 360, 480, 720, 1080, 1440, 2160)
        val up = std.firstOrNull { it > src && it <= src * 2 }
        val down = std.filter { it < src }.sortedDescending().take(4)
        return listOfNotNull(up) + src + down
    }

    private suspend fun youTube(context: Context, id: String, currentHeight: Int): List<Alt> {
        val info = runCatching { dev.asfakul.yumeiro.youtube.YouTube.info(context, id, false) }.getOrNull() ?: return emptyList()
        // Same list as the download sheet (one source of truth), plus anything playable it skips.
        val heights = dev.asfakul.yumeiro.online.download.YtDownloads.options(info).filter { !it.audioOnly }.map { it.height } +
            info.videoOnlyStreams.filter { it.isUrl }.map { it.height } + info.videoStreams.filter { it.isUrl }.map { it.height }
        return ladder(heights).map { h ->
            Alt(label(h), h, current = h == currentHeight) { c, _ -> dev.asfakul.yumeiro.youtube.YouTubeStreams.videoRequest(c, id, maxHeight = h) }
        }
    }

    private fun online(currentHeight: Int): List<Alt> {
        val s = dev.asfakul.yumeiro.online.OnlineSession.state.value ?: return emptyList()
        if (s.phase != dev.asfakul.yumeiro.online.OnlineSession.Phase.PLAYING) return emptyList()
        val byHeight = s.links.withIndex()
            .filter { (_, l) -> !l.audioOnly && l.quality in 144..4320 && l.url !in s.failed }
            .groupBy { it.value.quality }
        if (byHeight.size < 2) return emptyList()
        val playing = s.link?.quality
        return byHeight.keys.sortedDescending().map { h ->
            val first = byHeight.getValue(h).first()
            Alt(label(h), h, note = first.value.name.ifBlank { first.value.source }, current = h == playing || (playing == null && h == currentHeight)) { c, pos ->
                val cur = dev.asfakul.yumeiro.online.OnlineSession.state.value
                val i = cur?.links?.indexOfFirst { it.url == first.value.url } ?: -1
                if (i >= 0) dev.asfakul.yumeiro.online.OnlineSession.switchSource(c, i, pos)
                null
            }
        }
    }

    /** Distinct, sensible heights, highest first. Pure → unit-tested. */
    internal fun ladder(heights: List<Int>): List<Int> =
        heights.filter { it in 100..4320 }.map { snap(it) }.distinct().sortedDescending()

    /** 1072 → 1080, 718 → 720 (YouTube's odd crops). */
    internal fun snap(h: Int): Int = listOf(144, 240, 360, 480, 720, 1080, 1440, 2160, 4320).minByOrNull { kotlin.math.abs(it - h) }?.takeIf { kotlin.math.abs(it - h) <= 24 } ?: h

    fun label(h: Int): String = when (h) { 2160 -> "2160p (4K)"; 4320 -> "4320p (8K)"; 1440 -> "1440p (2K)"; else -> "${h}p" }
}
