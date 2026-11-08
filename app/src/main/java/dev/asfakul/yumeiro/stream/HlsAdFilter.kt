/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import java.net.URI

/**
 * Removes ad breaks that free streaming hosts splice into HLS playlists. Those breaks are the
 * "sudden black screen" users see: a run of foreign segments (other CDN, other codec/resolution,
 * often fake .png/.jpg-named .ts files) behind an `#EXT-X-DISCONTINUITY`, which many decoders
 * render as black frames or which stall the player.
 *
 * Conservative by design (pure, unit-tested):
 *  - only VOD media playlists (live sequence numbers must stay aligned across reloads);
 *  - a segment is an ad when it sits inside a SCTE-35 `CUE-OUT … CUE-IN` / ad `DATERANGE`, when its
 *    URL is an obvious ad URL, or when its discontinuity group is short (≤ [MAX_AD_GROUP_S]) and
 *    served from a different site than the main content;
 *  - never drops more than [MAX_REMOVED_SHARE] of the runtime: if it would, the playlist is
 *    returned untouched.
 */
object HlsAdFilter {
    const val MAX_AD_GROUP_S = 120.0
    const val MAX_REMOVED_SHARE = 0.35

    data class Result(val text: String, val removedSegments: Int, val removedSeconds: Double)

    private class Segment(val tags: List<String>, val uri: String, val duration: Double, val group: Int, val key: String?, val map: String?, val inCue: Boolean)

    private val AD_URL = Regex("""(^|[/._-])(ads?|adv|advert|adjump|preroll|midroll|postroll|vast|vpaid|doubleclick|commercial)([/._-]|\d|$)""", RegexOption.IGNORE_CASE)

    fun clean(playlist: String, playlistUrl: String? = null): Result {
        val unchanged = Result(playlist, 0, 0.0)
        if (!playlist.trimStart().startsWith("#EXTM3U")) return unchanged
        val lines = playlist.lines()
        if (lines.any { it.startsWith("#EXT-X-STREAM-INF") }) return unchanged // master playlist
        val vod = lines.any { it.startsWith("#EXT-X-ENDLIST") } || lines.any { it.trim() == "#EXT-X-PLAYLIST-TYPE:VOD" }
        if (!vod) return unchanged

        val header = ArrayList<String>()
        val footer = ArrayList<String>()
        val segments = ArrayList<Segment>()
        var pending = ArrayList<String>()
        var group = 0
        var key: String? = null
        var map: String? = null
        var inCue = false
        var seenSegment = false
        for (raw in lines) {
            val line = raw.trimEnd()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXT-X-ENDLIST") -> footer += line
                line.startsWith("#EXT-X-DISCONTINUITY") && !line.startsWith("#EXT-X-DISCONTINUITY-SEQUENCE") -> { if (seenSegment) group++ }
                line.startsWith("#EXT-X-KEY") -> key = line
                line.startsWith("#EXT-X-MAP") -> map = line
                line.startsWith("#EXT-X-CUE-OUT") && !line.startsWith("#EXT-X-CUE-OUT-CONT") -> inCue = true
                line.startsWith("#EXT-X-CUE-IN") -> inCue = false
                line.startsWith("#EXT-X-DATERANGE") && (line.contains("SCTE35-OUT") || line.contains("CLASS=\"ad", true)) -> inCue = true
                line.startsWith("#EXTINF") || line.startsWith("#EXT-X-BYTERANGE") || line.startsWith("#EXT-X-PROGRAM-DATE-TIME") ||
                    line.startsWith("#EXT-X-CUE-OUT-CONT") || line.startsWith("#EXT-X-GAP") -> pending += line
                line.startsWith("#") -> if (!seenSegment) header += line else pending += line
                else -> {
                    val dur = pending.firstOrNull { it.startsWith("#EXTINF") }?.substringAfter(':')?.substringBefore(',')?.trim()?.toDoubleOrNull() ?: 0.0
                    segments += Segment(pending, line, dur, group, key, map, inCue)
                    pending = ArrayList()
                    seenSegment = true
                }
            }
        }
        if (segments.size < 3) return unchanged

        val total = segments.sumOf { it.duration }.takeIf { it > 0 } ?: return unchanged
        val groups = segments.groupBy { it.group }
        val mainGroup = groups.maxByOrNull { (_, s) -> s.sumOf { it.duration } }?.key ?: return unchanged
        val mainOrigin = dominantOrigin(groups.getValue(mainGroup), playlistUrl)
        val adGroups = groups.filter { (g, s) ->
            g != mainGroup && groups.size >= 2 &&
                s.sumOf { it.duration } <= MAX_AD_GROUP_S &&
                dominantOrigin(s, playlistUrl).let { it != null && mainOrigin != null && it != mainOrigin }
        }.keys

        val drop = segments.map { s -> s.inCue || s.group in adGroups || AD_URL.containsMatchIn(pathOf(s.uri)) }
        val removed = segments.indices.filter { drop[it] }
        if (removed.isEmpty()) return unchanged
        val removedSeconds = removed.sumOf { segments[it].duration }
        if (removedSeconds / total > MAX_REMOVED_SHARE || removed.size == segments.size) return unchanged

        val out = StringBuilder()
        header.forEach { out.append(it).append('\n') }
        var lastGroup: Int? = null
        var lastKey: String? = null
        var lastMap: String? = null
        segments.forEachIndexed { i, s ->
            if (drop[i]) return@forEachIndexed
            if (lastGroup != null && s.group != lastGroup) out.append("#EXT-X-DISCONTINUITY\n")
            if (s.key != lastKey && s.key != null) out.append(s.key).append('\n')
            if (s.map != lastMap && s.map != null) out.append(s.map).append('\n')
            lastGroup = s.group; lastKey = s.key; lastMap = s.map
            s.tags.forEach { out.append(it).append('\n') }
            out.append(s.uri).append('\n')
        }
        footer.forEach { out.append(it).append('\n') }
        return Result(out.toString(), removed.size, removedSeconds)
    }

    private fun pathOf(uri: String): String = runCatching { URI(uri).let { (it.host.orEmpty()) + it.path.orEmpty() } }.getOrDefault(uri)

    /** The site most segments of a group come from (relative URIs count as the playlist's own). */
    private fun dominantOrigin(s: List<Segment>, playlistUrl: String?): String? = s.map { originOf(it.uri, playlistUrl) }
        .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

    private fun originOf(uri: String, playlistUrl: String?): String {
        val abs = runCatching { if (playlistUrl != null) URI(playlistUrl).resolve(uri.trim()) else URI(uri.trim()) }.getOrNull()
            ?: return "?"
        if (abs.host == null) return "relative"
        return siteOf(abs.host.lowercase())
    }

    /** Rough registrable domain ("s3.cdn.example.co.uk" → "example.co.uk") so load-balanced CDN hosts count as one site. */
    internal fun siteOf(host: String): String {
        val parts = host.trimEnd('.').split('.')
        if (parts.size <= 2 || host.all { it.isDigit() || it == '.' || it == ':' }) return host
        val sld = parts[parts.size - 2]
        val take = if (sld.length <= 3 && parts.last().length == 2) 3 else 2
        return parts.takeLast(take).joinToString(".")
    }
}
