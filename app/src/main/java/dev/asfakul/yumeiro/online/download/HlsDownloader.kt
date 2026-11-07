/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
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

/**
 * Saves an HLS stream into a single file: picks the best variant, then appends the init section
 * (fMP4) and every segment in order. Clear AES-128 segments are decrypted with the key the
 * playlist points to (the same thing any HLS player does). SAMPLE-AES / DRM is refused.
 * Resumes by skipping segments already written (tracked in a small side file).
 */
class HlsDownloader(private val client: OkHttpClient, private val headers: Map<String, String>) {

    data class Segment(val url: String, val keyUrl: String?, val iv: ByteArray?, val sequence: Long, val initUrl: String?)

    suspend fun download(url: String, out: File, progress: (Long, Long) -> Unit): Long {
        var playlistUrl = url
        var text = fetchText(url)
        if ("#EXT-X-STREAM-INF" in text) {
            playlistUrl = bestVariant(text, url) ?: throw IOException("No playable variant in the playlist")
            text = fetchText(playlistUrl)
        }
        if (Regex("""#EXT-X-KEY:.*METHOD=(SAMPLE-AES|SAMPLE-AES-CTR)""").containsMatchIn(text)) throw IOException("This stream is DRM-protected and can't be saved")
        if ("#EXT-X-ENDLIST" !in text) throw IOException("Live streams can't be downloaded")
        val segments = parseMedia(text, playlistUrl)
        if (segments.isEmpty()) throw IOException("The playlist has no segments")
        val state = File(out.parentFile, out.name + ".idx")
        var doneCount = if (out.exists() && state.exists()) state.readText().trim().toIntOrNull() ?: 0 else 0
        if (doneCount == 0) { out.delete() }
        val keys = HashMap<String, ByteArray>()
        var bytes = if (doneCount > 0) out.length() else 0L
        FileOutputStream(out, doneCount > 0).use { fos ->
            if (doneCount == 0) segments.first().initUrl?.let { init -> val b = fetchBytes(init); fos.write(b); bytes += b.size }
            for (i in doneCount until segments.size) {
                coroutineContext.ensureActive()
                val seg = segments[i]
                var data = fetchBytesRetry(seg.url)
                if (seg.keyUrl != null) {
                    val key = keys.getOrPut(seg.keyUrl) { fetchBytes(seg.keyUrl) }
                    data = decrypt(data, key, seg.iv ?: ivFor(seg.sequence))
                }
                fos.write(data); bytes += data.size
                doneCount = i + 1
                if (doneCount % 5 == 0 || doneCount == segments.size) { fos.flush(); state.writeText(doneCount.toString()) }
                // Estimate the final size from the average segment so far.
                val estimate = bytes * segments.size / doneCount
                progress(bytes, if (doneCount == segments.size) bytes else estimate)
            }
        }
        state.delete()
        return bytes
    }

    private fun request(url: String): Request {
        val b = Request.Builder().url(url)
        headers.forEach { (k, v) -> runCatching { b.header(k, v) } }
        return b.build()
    }

    private fun fetchText(url: String): String = client.newCall(request(url)).execute().use { r ->
        if (!r.isSuccessful) throw HttpStatus(r.code); r.body.string()
    }

    private fun fetchBytes(url: String): ByteArray = client.newCall(request(url)).execute().use { r ->
        if (!r.isSuccessful) throw HttpStatus(r.code); r.body.bytes()
    }

    private fun fetchBytesRetry(url: String): ByteArray {
        var last: IOException? = null
        repeat(4) { attempt ->
            try { return fetchBytes(url) } catch (e: HttpStatus) { if (e.code in 400..499 && e.code != 429) throw e; last = e } catch (e: IOException) { last = e }
            Thread.sleep(500L shl attempt)
        }
        throw last ?: IOException("Segment failed")
    }

    companion object {
        fun resolve(base: String, ref: String): String =
            base.toHttpUrlOrNull()?.resolve(ref.trim())?.toString() ?: ref.trim()

        /** Highest-bandwidth variant (ties → larger resolution). */
        fun bestVariant(master: String, base: String): String? {
            val lines = master.lines()
            var best: Pair<Long, String>? = null
            for (i in lines.indices) {
                val l = lines[i].trim()
                if (!l.startsWith("#EXT-X-STREAM-INF")) continue
                val bw = Regex("""(?<![A-Z-])BANDWIDTH=(\d+)""").find(l)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                val uri = lines.drop(i + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") }?.trim() ?: continue
                if (best == null || bw > best.first) best = bw to resolve(base, uri)
            }
            return best?.second
        }

        fun parseMedia(text: String, base: String): List<Segment> {
            val out = ArrayList<Segment>()
            var key: String? = null; var iv: ByteArray? = null; var init: String? = null
            var seq = Regex("""#EXT-X-MEDIA-SEQUENCE:(\d+)""").find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            for (raw in text.lines()) {
                val l = raw.trim()
                when {
                    l.startsWith("#EXT-X-KEY") -> {
                        val method = Regex("""METHOD=([A-Z0-9-]+)""").find(l)?.groupValues?.get(1)
                        if (method == null || method == "NONE") { key = null; iv = null } else {
                            key = Regex("""URI="([^"]+)"""").find(l)?.groupValues?.get(1)?.let { resolve(base, it) }
                            iv = Regex("""IV=0[xX]([0-9a-fA-F]+)""").find(l)?.groupValues?.get(1)?.let(::hex)
                        }
                    }
                    l.startsWith("#EXT-X-MAP") -> init = Regex("""URI="([^"]+)"""").find(l)?.groupValues?.get(1)?.let { resolve(base, it) }
                    l.isNotEmpty() && !l.startsWith("#") -> { out += Segment(resolve(base, l), key, iv, seq, init); seq++ }
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
