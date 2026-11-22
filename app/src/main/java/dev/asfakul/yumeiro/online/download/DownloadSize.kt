/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.download

import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * OWN-18 (owner 2026-11-22: "the download popup never shows the file size, mainly from the player page; every download
 * popup should show quality, size, sub / dub and the other details"): the size of a download before it starts.
 *
 * How other apps do it (behaviour only, no code copied): NewPipe's download dialog asks each stream's server for its
 * Content-Length before showing the list; yt-dlp reports `filesize_approx` = bitrate x duration when a stream has no
 * byte size (HLS / DASH). Here:
 *  - a plain file: HEAD, then a 1-byte Range GET (`Content-Range: bytes 0-0/<total>`) when HEAD gives nothing: exact;
 *  - HLS: the variant for the picked height, then its segment list: exact when the list has byte ranges, else
 *    BANDWIDTH x total EXTINF seconds / 8 ("about");
 *  - DASH: the representation's bandwidth x mediaPresentationDuration ("about").
 * Every network step has a short timeout; no answer means the popup says so honestly, it never invents a number.
 */
object DownloadSize {
    data class Size(val bytes: Long, val exact: Boolean)

    /** "340 MB", "about 1.2 GB" or null. Pure. */
    fun label(s: Size?): String? {
        if (s == null || s.bytes <= 0) return null
        val t = sizeText(s.bytes)
        return if (s.exact) t else "about $t"
    }

    fun sizeText(b: Long): String = when {
        b <= 0 -> ""
        b >= 1L shl 30 -> String.format(java.util.Locale.US, "%.1f GB", b / 1073741824.0)
        else -> String.format(java.util.Locale.US, "%.0f MB", (b / 1048576.0).coerceAtLeast(1.0))
    }

    /** Bitrate (bits / s) x seconds, in bytes. Pure. */
    fun estimate(bitsPerSecond: Long, seconds: Double): Long = if (bitsPerSecond <= 0 || seconds <= 0) 0 else (bitsPerSecond * seconds / 8).toLong()

    /** Songs at YouTube Music's usual best audio (AAC 128 kbps, itag 140): an estimate for a batch. Pure. */
    fun songsEstimate(durationsSec: List<Long>): Size? = durationsSec.filter { it > 0 }.takeIf { it.isNotEmpty() }
        ?.let { Size(estimate(128_000, it.sum().toDouble()), exact = false) }

    /** Total size from `Content-Range: bytes 0-0/12345` or `Content-Length`. Pure. */
    fun total(contentRange: String?, contentLength: String?): Long {
        contentRange?.substringAfterLast('/', "")?.trim()?.toLongOrNull()?.let { if (it > 0) return it }
        return contentLength?.trim()?.toLongOrNull()?.takeIf { it > 0 } ?: 0
    }

    data class Variant(val bandwidth: Long, val height: Int, val uri: String)

    /** Variants of an HLS master. Pure. */
    fun variants(master: String, base: String): List<Variant> {
        val out = ArrayList<Variant>()
        val lines = master.lines().map { it.trim() }
        lines.forEachIndexed { i, l ->
            if (!l.startsWith("#EXT-X-STREAM-INF")) return@forEachIndexed
            val a = DownloadTracks.attrs(l)
            val bw = (a["AVERAGE-BANDWIDTH"] ?: a["BANDWIDTH"])?.toLongOrNull() ?: 0
            val h = a["RESOLUTION"]?.substringAfter('x', "")?.toIntOrNull() ?: 0
            val uri = lines.drop(i + 1).firstOrNull { it.isNotEmpty() && !it.startsWith("#") } ?: return@forEachIndexed
            out += Variant(bw, h, DownloadTracks.resolve(base, uri))
        }
        return out
    }

    /** The variant the download will save: the tallest at or under [maxHeight] (<= 0 = best), else the smallest. Pure. */
    fun pick(variants: List<Variant>, maxHeight: Int): Variant? {
        if (variants.isEmpty()) return null
        val byQuality = variants.sortedWith(compareBy({ it.height }, { it.bandwidth }))
        if (maxHeight <= 0) return byQuality.last()
        return byQuality.lastOrNull { it.height in 1..maxHeight } ?: byQuality.first()
    }

    /** Seconds and exact bytes (byte-range segments) of an HLS media playlist. Pure. */
    fun media(playlist: String): Pair<Double, Long> {
        var seconds = 0.0; var bytes = 0L; var ranged = true; var segments = 0
        for (raw in playlist.lines()) {
            val l = raw.trim()
            when {
                l.startsWith("#EXTINF:") -> { seconds += l.removePrefix("#EXTINF:").substringBefore(',').trim().toDoubleOrNull() ?: 0.0; segments++ }
                l.startsWith("#EXT-X-BYTERANGE:") -> bytes += l.removePrefix("#EXT-X-BYTERANGE:").substringBefore('@').trim().toLongOrNull() ?: 0
            }
        }
        if (bytes == 0L) ranged = false
        return seconds to (if (ranged && segments > 0) bytes else 0L)
    }

    /** DASH: tallest representation at or under [maxHeight] times the presentation length. Pure (string reads only). */
    fun dash(mpd: String, maxHeight: Int): Size? {
        val seconds = isoSeconds(attr(mpd, "mediaPresentationDuration") ?: return null)
        if (seconds <= 0) return null
        val reps = mpd.split("<Representation").drop(1).map { it.substringBefore('>') }
        val video = reps.mapNotNull { r -> val h = attr(r, "height")?.toIntOrNull(); val bw = attr(r, "bandwidth")?.toLongOrNull(); if (h != null && bw != null) h to bw else null }
        val audio = reps.filter { attr(it, "height") == null }.mapNotNull { attr(it, "bandwidth")?.toLongOrNull() }.maxOrNull() ?: 0
        val v = video.sortedWith(compareBy({ it.first }, { it.second })).let { l -> if (maxHeight <= 0) l.lastOrNull() else l.lastOrNull { it.first <= maxHeight } ?: l.firstOrNull() }
        val bw = (v?.second ?: 0) + audio
        return estimate(bw, seconds).takeIf { it > 0 }?.let { Size(it, exact = false) }
    }

    private fun attr(s: String, name: String): String? {
        val i = s.indexOf(" $name=\"").takeIf { it >= 0 } ?: s.indexOf("\n$name=\"").takeIf { it >= 0 } ?: return null
        val from = i + name.length + 3
        val to = s.indexOf('"', from).takeIf { it > from } ?: return null
        return s.substring(from, to)
    }

    /** "PT1H2M3.5S" to seconds. Pure. */
    fun isoSeconds(d: String): Double {
        val t = d.trim().uppercase().substringAfter("PT", "")
        if (t.isEmpty()) return 0.0
        var total = 0.0; val num = StringBuilder()
        for (c in t) {
            if (c.isDigit() || c == '.') { num.append(c); continue }
            val v = num.toString().toDoubleOrNull() ?: 0.0; num.clear()
            total += when (c) { 'H' -> v * 3600; 'M' -> v * 60; 'S' -> v; else -> 0.0 }
        }
        return total
    }

    /** Network part: blocking, call it off the main thread (the popup does, with a timeout). */
    fun probe(client: OkHttpClient, url: String, headers: Map<String, String>, hls: Boolean, dashFormat: Boolean, maxHeight: Int): Size? {
        fun req(u: String) = Request.Builder().url(u).apply { headers.forEach { (k, v) -> runCatching { header(k, v) } } }
        fun text(u: String): String? = runCatching { client.newCall(req(u).build()).execute().use { r -> if (r.isSuccessful) r.body.string() else null } }.getOrNull()
        val lower = url.substringBefore('?').lowercase()
        if (hls || lower.endsWith(".m3u8")) {
            val first = text(url) ?: return null
            var bw = 0L
            val playlist = if ("#EXT-X-STREAM-INF" in first) {
                val v = pick(variants(first, url), maxHeight) ?: return null
                bw = v.bandwidth
                text(v.uri) ?: return null
            } else first
            val (seconds, exact) = media(playlist)
            if (exact > 0) return Size(exact, exact = true)
            return estimate(bw, seconds).takeIf { it > 0 }?.let { Size(it, exact = false) }
        }
        if (dashFormat || lower.endsWith(".mpd")) return text(url)?.let { dash(it, maxHeight) }
        val head = runCatching { client.newCall(req(url).head().build()).execute().use { r -> if (r.isSuccessful) total(null, r.header("Content-Length")) else 0L } }.getOrDefault(0L)
        if (head > 0) return Size(head, exact = true)
        val ranged = runCatching {
            client.newCall(req(url).header("Range", "bytes=0-0").build()).execute().use { r -> if (r.code == 206 || r.isSuccessful) total(r.header("Content-Range"), if (r.code == 206) null else r.header("Content-Length")) else 0L }
        }.getOrDefault(0L)
        return ranged.takeIf { it > 0 }?.let { Size(it, exact = true) }
    }
}
