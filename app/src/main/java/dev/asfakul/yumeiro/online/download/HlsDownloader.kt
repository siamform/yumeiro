/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.download

import kotlinx.coroutines.ensureActive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.coroutineContext

// Precompiled once (never during composition, scrolling or per call): see docs/STABILITY-R15.md.
private val RX_HLSDOWNLOADER_1 = Regex("""#EXT-X-KEY:.*METHOD=(SAMPLE-AES|SAMPLE-AES-CTR)""")
private val RX_HLSDOWNLOADER_2 = Regex("""(?<![A-Z-])BANDWIDTH=(\d+)""")
private val RX_HLSDOWNLOADER_3 = Regex("""#EXT-X-MEDIA-SEQUENCE:(\d+)""")
private val RX_HLSDOWNLOADER_4 = Regex("""METHOD=([A-Z0-9-]+)""")
private val RX_HLS_RESOLUTION = Regex("""RESOLUTION=\d+x(\d+)""")
private val RX_HLS_URI = Regex("""URI="([^"]+)"""")
private val RX_HLSDOWNLOADER_5 = Regex("""IV=0[xX]([0-9a-fA-F]+)""")
private val RX_HLS_AUDIO_GROUP = Regex("""(?<![A-Z-])AUDIO="([^"]+)"""")
private val RX_HLS_GROUP_ID = Regex("""GROUP-ID="([^"]+)"""")
private val RX_HLS_MAP_BYTERANGE = Regex("""BYTERANGE="(\d+)(?:@(\d+))?"""")

/**
 * Saves an HLS stream into a single file: picks the best variant, then appends the init section
 * (fMP4) and every segment in order. Clear AES-128 segments are decrypted with the key the
 * playlist points to (the same thing any HLS player does). SAMPLE-AES / DRM is refused.
 * Resumes by skipping segments already written (tracked in a small side file).
 */
class HlsDownloader(private val client: OkHttpClient, private val headers: Map<String, String>, private val maxHeight: Int = 0) {

    /**
     * One media segment. G8-1: [start]/[length] is its `#EXT-X-BYTERANGE` inside [url] (-1 = the whole
     * resource), [initStart]/[initLength] the `EXT-X-MAP` `BYTERANGE` of [initUrl].
     */
    data class Segment(
        val url: String, val keyUrl: String?, val iv: ByteArray?, val sequence: Long, val initUrl: String?,
        val start: Long = -1, val length: Long = -1, val initStart: Long = -1, val initLength: Long = -1,
    )

    /**
     * Saves the stream at [url] into [out]. G8-2: when the chosen variant takes its audio from a separate
     * rendition (`AUDIO="group"` + `EXT-X-MEDIA:TYPE=AUDIO,URI=…`), the video and the audio are saved to
     * side files `out.v` / `out.a` (each with its own resume record) and joined into [out] by [join]
     * (MediaMuxer on the device); [onContainer] is then told the result is "mp4". Without a [join] such a
     * stream fails instead of being saved silent.
     */
    suspend fun download(
        url: String, out: File,
        join: ((video: File, audio: File, out: File) -> Unit)? = null,
        onContainer: (String) -> Unit = {},
        progress: (Long, Long) -> Unit,
    ): Long {
        var playlistUrl = url
        var text = fetchText(url)
        if ("#EXT-X-STREAM-INF" in text) {
            playlistUrl = bestVariant(text, url, maxHeight) ?: throw IOException("No playable variant in the playlist")
            val audioUrl = audioRendition(text, url, playlistUrl)
            text = fetchText(playlistUrl)
            if (audioUrl != null) return downloadJoined(playlistUrl, text, audioUrl, out, join, onContainer, progress)
        }
        return saveMedia(playlistUrl, text, out, progress)
    }

    private suspend fun downloadJoined(
        videoUrl: String, videoText: String, audioUrl: String, out: File,
        join: ((File, File, File) -> Unit)?, onContainer: (String) -> Unit, progress: (Long, Long) -> Unit,
    ): Long {
        val v = File(out.parentFile, out.name + ".v"); val a = File(out.parentFile, out.name + ".a")
        var vb = 0L; var ab = 0L; var vt = 0L; var at = 0L
        fun report() = progress(vb + ab, if (vt > 0 && at > 0) vt + at else -1)
        saveMedia(videoUrl, videoText, v) { d, t -> vb = d; vt = t; report() }
        saveMedia(audioUrl, fetchText(audioUrl), a) { d, t -> ab = d; at = t; report() }
        if (join == null) throw IOException("This stream keeps its audio in a separate track and can't be joined here")
        coroutineContext.ensureActive()
        join(v, a, out)
        onContainer("mp4")
        listOf(v, a).forEach { f -> f.delete(); File(f.parentFile, f.name + ".idx").delete() }
        return out.length()
    }

    private suspend fun saveMedia(playlistUrl: String, text: String, out: File, progress: (Long, Long) -> Unit): Long {
        if (RX_HLSDOWNLOADER_1.containsMatchIn(text)) throw IOException("This stream is DRM-protected and can't be saved")
        if ("#EXT-X-ENDLIST" !in text) throw IOException("Live streams can't be downloaded")
        val segments = parseMedia(text, playlistUrl)
        if (segments.isEmpty()) throw IOException("The playlist has no segments")
        val state = File(out.parentFile, out.name + ".idx")
        // R16 (G8): the side file now records "segments:bytes". It is written every 5 segments, so
        // after a pause the file can hold a few segments more than recorded; they were appended a
        // second time on resume (repeated video). The file is cut back to the recorded length.
        val (resumeCount, resumeBytes) = HlsResume.read(if (out.exists() && state.exists()) state.readText() else null, out.length())
        var doneCount = resumeCount
        if (doneCount == 0) { out.delete() } else if (out.length() > resumeBytes) {
            java.io.RandomAccessFile(out, "rw").use { it.setLength(resumeBytes) }
        }
        val keys = HashMap<String, ByteArray>()
        var bytes = if (doneCount > 0) resumeBytes else 0L
        FileOutputStream(out, doneCount > 0).use { fos ->
            if (doneCount == 0) segments.first().let { f -> f.initUrl?.let { init -> val b = fetchBytes(init, f.initStart, f.initLength); fos.write(b); bytes += b.size } }
            for (i in doneCount until segments.size) {
                coroutineContext.ensureActive()
                val seg = segments[i]
                var data = fetchBytesRetry(seg.url, seg.start, seg.length)
                if (seg.keyUrl != null) {
                    val key = keys.getOrPut(seg.keyUrl) { fetchBytes(seg.keyUrl) }
                    data = decrypt(data, key, seg.iv ?: ivFor(seg.sequence))
                }
                fos.write(data); bytes += data.size
                doneCount = i + 1
                if (doneCount % 5 == 0 || doneCount == segments.size) { fos.flush(); state.writeText(HlsResume.write(doneCount, bytes)) }
                // Estimate the final size from the average segment so far.
                val estimate = bytes * segments.size / doneCount
                progress(bytes, if (doneCount == segments.size) bytes else estimate)
            }
        }
        state.delete()
        return bytes
    }

    private fun request(url: String, start: Long = -1, length: Long = -1): Request {
        val b = Request.Builder().url(url)
        if (start >= 0 && length > 0) b.header("Range", "bytes=$start-${start + length - 1}")
        headers.forEach { (k, v) -> runCatching { b.header(k, v) } }
        return b.build()
    }

    private fun fetchText(url: String): String = client.newCall(request(url)).execute().use { r ->
        if (!r.isSuccessful) throw HttpStatus(r.code); r.body.string()
    }

    /**
     * The resource at [url], or only [length] bytes from [start] (G8-1, a byte-range segment). A host that
     * ignores the Range header answers 200 with the whole file; the range is cut out of it.
     */
    private fun fetchBytes(url: String, start: Long = -1, length: Long = -1): ByteArray = client.newCall(request(url, start, length)).execute().use { r ->
        if (!r.isSuccessful) throw HttpStatus(r.code)
        val body = r.body.bytes()
        when {
            start < 0 || length <= 0 || r.code == 206 -> body
            body.size < start + length -> throw IOException("Byte range $start+$length is past the end of ${body.size} bytes")
            else -> body.copyOfRange(start.toInt(), (start + length).toInt())
        }
    }

    private fun fetchBytesRetry(url: String, start: Long = -1, length: Long = -1): ByteArray {
        var last: IOException? = null
        repeat(4) { attempt ->
            try { return fetchBytes(url, start, length) } catch (e: HttpStatus) { if (e.code in 400..499 && e.code != 429) throw e; last = e } catch (e: IOException) { last = e }
            Thread.sleep(500L shl attempt)
        }
        throw last ?: IOException("Segment failed")
    }

    companion object {
        fun resolve(base: String, ref: String): String =
            base.toHttpUrlOrNull()?.resolve(ref.trim())?.toString() ?: ref.trim()

        /**
         * Variant to save. [maxHeight] 0 = highest bandwidth. G8 (R17): with a limit (Settings →
         * Downloads → Quality) the best variant at or under it, then one without a RESOLUTION, then
         * the smallest above it, like [DownloadNaming.pickLink] and DASH. HLS ignored the setting
         * and always saved the top variant.
         */
        fun bestVariant(master: String, base: String, maxHeight: Int = 0): String? {
            val lines = master.lines()
            val all = ArrayList<Triple<Long, Int, String>>() // bandwidth, height (0 = unknown), url
            for (i in lines.indices) {
                val l = lines[i].trim()
                if (!l.startsWith("#EXT-X-STREAM-INF")) continue
                val bw = RX_HLSDOWNLOADER_2.find(l)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                val h = RX_HLS_RESOLUTION.find(l)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val uri = lines.drop(i + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") }?.trim() ?: continue
                all += Triple(bw, h, resolve(base, uri))
            }
            if (all.isEmpty()) return null
            if (maxHeight <= 0) return all.maxWith(compareBy<Triple<Long, Int, String>> { it.first }.thenBy { it.second }).third
            return (all.filter { it.second in 1..maxHeight }.maxWithOrNull(compareBy<Triple<Long, Int, String>> { it.second }.thenBy { it.first })
                ?: all.filter { it.second <= 0 }.maxByOrNull { it.first }
                ?: all.minWith(compareBy<Triple<Long, Int, String>> { it.second }.thenBy { it.first })).third
        }

        /**
         * G8-2: media playlist of the separate audio rendition the chosen [variant] of [master] plays with
         * (its `AUDIO="group"`; the `DEFAULT=YES` rendition of that group first), or null when the variant
         * has no audio group or the group's audio is muxed into the variant (no `URI`).
         */
        fun audioRendition(master: String, base: String, variant: String): String? {
            val lines = master.lines().map { it.trim() }
            val group = lines.indices.firstNotNullOfOrNull { i ->
                val l = lines[i]
                if (!l.startsWith("#EXT-X-STREAM-INF")) return@firstNotNullOfOrNull null
                val uri = lines.drop(i + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") } ?: return@firstNotNullOfOrNull null
                if (resolve(base, uri) != variant) null else RX_HLS_AUDIO_GROUP.find(l)?.groupValues?.get(1) ?: ""
            }?.takeIf { it.isNotEmpty() } ?: return null
            val media = lines.filter { it.startsWith("#EXT-X-MEDIA:") && "TYPE=AUDIO" in it && RX_HLS_GROUP_ID.find(it)?.groupValues?.get(1) == group }
            val pick = media.firstOrNull { "DEFAULT=YES" in it && RX_HLS_URI.containsMatchIn(it) } ?: media.firstOrNull { RX_HLS_URI.containsMatchIn(it) } ?: return null
            return RX_HLS_URI.find(pick)?.groupValues?.get(1)?.let { resolve(base, it) }
        }

        /**
         * Segments of a media playlist. G8-1: `#EXT-X-BYTERANGE:<n>[@<o>]` makes the next segment bytes
         * o … o+n-1 of its URI; without `@o` it starts where the previous range of the same URI ended
         * (RFC 8216 §4.3.2.2). `EXT-X-MAP` takes its `BYTERANGE` attribute (offset 0 when absent).
         */
        fun parseMedia(text: String, base: String): List<Segment> {
            val out = ArrayList<Segment>()
            var key: String? = null; var iv: ByteArray? = null; var init: String? = null
            var initStart = -1L; var initLength = -1L
            var range: Pair<Long, Long?>? = null // length, offset
            val rangeEnd = HashMap<String, Long>()
            var seq = RX_HLSDOWNLOADER_3.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            for (raw in text.lines()) {
                val l = raw.trim()
                when {
                    l.startsWith("#EXT-X-KEY") -> {
                        val method = RX_HLSDOWNLOADER_4.find(l)?.groupValues?.get(1)
                        if (method == null || method == "NONE") { key = null; iv = null } else {
                            key = RX_HLS_URI.find(l)?.groupValues?.get(1)?.let { resolve(base, it) }
                            iv = RX_HLSDOWNLOADER_5.find(l)?.groupValues?.get(1)?.let(::hex)
                        }
                    }
                    l.startsWith("#EXT-X-MAP") -> {
                        init = RX_HLS_URI.find(l)?.groupValues?.get(1)?.let { resolve(base, it) }
                        val m = RX_HLS_MAP_BYTERANGE.find(l)
                        initLength = m?.groupValues?.get(1)?.toLongOrNull() ?: -1L
                        initStart = if (m != null) m.groupValues[2].toLongOrNull() ?: 0L else -1L
                    }
                    l.startsWith("#EXT-X-BYTERANGE:") -> {
                        val spec = l.substringAfter(':').trim()
                        range = spec.substringBefore('@').toLongOrNull()?.let { n -> n to (if ('@' in spec) spec.substringAfter('@').toLongOrNull() else null) }
                    }
                    l.isNotEmpty() && !l.startsWith("#") -> {
                        val u = resolve(base, l)
                        val r = range; range = null
                        out += if (r == null) Segment(u, key, iv, seq, init, initStart = initStart, initLength = initLength) else {
                            val from = r.second ?: rangeEnd[u] ?: 0L
                            rangeEnd[u] = from + r.first
                            Segment(u, key, iv, seq, init, from, r.first, initStart, initLength)
                        }
                        seq++
                    }
                }
            }
            return out
        }

        fun hex(s: String): ByteArray {
            val h = s.padStart(32, '0').takeLast(32)
            return ByteArray(16) { i -> h.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        }

        fun ivFor(sequence: Long): ByteArray = ByteArray(16).also { b -> for (i in 0 until 8) b[15 - i] = (sequence shr (8 * i)).toByte() }

        fun decrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
            val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
            c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            return c.doFinal(data)
        }
    }
}

/** R16 (G8): resume point of a segmented download, stored as "segments:bytes". */
internal object HlsResume {
    fun write(segments: Int, bytes: Long) = "$segments:$bytes"

    /** Segments to skip and the file length they fill. Old or bad state, or a short file, starts over. */
    fun read(state: String?, fileLength: Long): Pair<Int, Long> {
        val parts = state?.trim()?.split(':') ?: return 0 to 0L
        val n = parts.getOrNull(0)?.toIntOrNull(); val b = parts.getOrNull(1)?.toLongOrNull()
        if (parts.size != 2 || n == null || b == null || n <= 0 || b <= 0 || fileLength < b) return 0 to 0L
        return n to b
    }
}
