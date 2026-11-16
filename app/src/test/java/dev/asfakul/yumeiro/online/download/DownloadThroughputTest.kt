/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.download

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * BUG-3 measurement harness: a local HTTP server with real Range support and per-request rate
 * limits, and every download engine of the app against it. Prints, for each engine and server
 * profile, the time of every 10 % of the file (THROUGHPUT lines in the test's system-out), and
 * checks the saved bytes. Server profiles:
 *  - steady: every request is capped (a host that limits each connection);
 *  - burst: every request is fast for its first bytes, then capped (nginx limit_rate_after,
 *    googlevideo and many file hosts do this).
 * The pass/fail rules come after the first measured run (docs/proposals/FEATURE-BUG-3-*.md).
 */
class DownloadThroughputTest {
    companion object {
        const val SIZE = 24 * 1024 * 1024
        const val TAIL_LIMIT = 2.0
        const val SEG = 512 * 1024 // 48 segments cover SIZE exactly (600 KB left 576 KB out: test error in 43851470283)
        private lateinit var server: java.net.ServerSocket
        private val pool = Executors.newCachedThreadPool()
        private val requests = AtomicInteger()
        /** G8 (R17): body bytes the server wrote (all requests); over SIZE = data read and thrown away. */
        private val served = java.util.concurrent.atomic.AtomicLong()
        private val data = ByteArray(SIZE) { (it % 251).toByte() }
        @Volatile var profile = Profile("steady", burst = 0, bytesPerSec = 3L * 1024 * 1024)
        @Volatile var ranges = true
        val base get() = "http://127.0.0.1:${server.localPort}"

        /** A minimal HTTP/1.1 server (one request per connection; android.jar has no HttpServer). */
        @BeforeClass @JvmStatic fun start() {
            server = java.net.ServerSocket(0, 64, java.net.InetAddress.getByName("127.0.0.1"))
            pool.execute {
                while (!server.isClosed) {
                    val sock = runCatching { server.accept() }.getOrNull() ?: break
                    pool.execute { runCatching { sock.use { handle(it) } } }
                }
            }
        }

        @AfterClass @JvmStatic fun stop() { server.close(); pool.shutdownNow() }

        private fun handle(sock: java.net.Socket) {
            val input = sock.getInputStream().bufferedReader(Charsets.ISO_8859_1)
            val line = input.readLine() ?: return
            val path = line.split(' ').getOrNull(1) ?: return
            val headers = HashMap<String, String>()
            while (true) {
                val h = input.readLine() ?: break
                if (h.isEmpty()) break
                headers[h.substringBefore(':').trim().lowercase()] = h.substringAfter(':').trim()
            }
            val out = sock.getOutputStream()
            when {
                path == "/file" -> serve(out, headers["range"], 0, SIZE, sock)
                path.endsWith("index.m3u8") -> {
                    val body = buildString {
                        append("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:6\n#EXT-X-MEDIA-SEQUENCE:0\n")
                        repeat(SIZE / SEG) { append("#EXTINF:6.0,\nseg$it.ts\n") }
                        append("#EXT-X-ENDLIST\n")
                    }.toByteArray()
                    out.write("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray()); out.write(body); out.flush()
                }
                path.contains("/hls/seg") -> {
                    val i = path.substringAfterLast("seg").removeSuffix(".ts").toInt()
                    serve(out, null, i * SEG, SEG)
                }
                else -> { out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()); out.flush() }
            }
        }

        private fun serve(os: java.io.OutputStream, rangeIn: String?, off: Int, len: Int, sock: java.net.Socket? = null) {
            requests.incrementAndGet()
            try {
                val range = rangeIn?.takeIf { ranges }
                var start = 0L; var end = len - 1L
                val head = StringBuilder()
                if (range != null) {
                    val spec = range.removePrefix("bytes=")
                    start = spec.substringBefore('-').toLong()
                    spec.substringAfter('-').takeIf { it.isNotBlank() }?.toLong()?.let { end = minOf(it, len - 1L) }
                    if (start >= len) {
                        os.write("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$len\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()); os.flush(); return
                    }
                    head.append("HTTP/1.1 206 Partial Content\r\nContent-Range: bytes $start-$end/$len\r\n")
                } else head.append("HTTP/1.1 200 OK\r\n")
                if (ranges) head.append("Accept-Ranges: bytes\r\n")
                val count = end - start + 1
                head.append("Content-Type: application/octet-stream\r\nContent-Length: $count\r\nConnection: close\r\n\r\n")
                os.write(head.toString().toByteArray())
                val p = profile
                val t0 = System.nanoTime()
                var sent = 0L
                while (sent < count) {
                    val n = minOf(16 * 1024L, count - sent).toInt()
                    os.write(data, off + (start + sent).toInt(), n); sent += n; served.addAndGet(n.toLong())
                    if (p.cutAfter > 0 && sent >= p.cutAfter && sent < count) { os.flush(); runCatching { sock?.close() }; return } // G8: drop mid-body
                    if (sent > p.burst && p.bytesPerSec > 0) {
                        val due = (sent - p.burst) * 1_000_000_000L / p.bytesPerSec
                        val ahead = due - (System.nanoTime() - t0)
                        if (ahead > 0) TimeUnit.NANOSECONDS.sleep(ahead)
                    }
                }
                os.flush()
            } catch (_: java.io.IOException) {
                // The client closed early (cancel, or it had enough): normal.
            }
        }
    }

    data class Profile(val name: String, val burst: Long, val bytesPerSec: Long, val cutAfter: Long = 0)

    private val client = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()

    /** Times (ms since start) when 10 %, 20 % … 100 % of [total] arrived. */
    private class Deciles(val total: Long) {
        val t0 = System.nanoTime()
        val at = LongArray(10) { -1 }
        @Synchronized fun on(done: Long) {
            for (i in 0 until 10) if (at[i] < 0 && done >= total * (i + 1) / 10) at[i] = (System.nanoTime() - t0) / 1_000_000
        }
        fun steps(): List<Long> = at.indices.map { i -> if (at[i] < 0) -1 else at[i] - (if (i == 0) 0 else at[i - 1]) }
        /** Average time of the last three tenths over the first three (1.0 = steady). */
        fun tailRatio(): Double { val s = steps(); return s.takeLast(3).average() / s.take(3).average().coerceAtLeast(1.0) }
        /** BUG-3 rule: the last 10 % over the average 10 % (the owner saw 4–5×; pass ≤ [TAIL_LIMIT]). */
        fun lastOverMean(): Double = steps().last() / (at.last() / 10.0).coerceAtLeast(1.0)
    }

    /** Engines whose tail the app controls (a host without ranges allows one request only). */
    private val ruled = setOf("segmented4", "segmented2-lowend", "youtube-8mb-chunks", "hls-sequential")
    private val broken = ArrayList<String>()

    private fun report(engine: String, d: Deciles, requestsUsed: Int, servedBytes: Long) {
        val s = d.steps()
        val total = d.at.last()
        println("THROUGHPUT engine=$engine profile=${profile.name} ranges=$ranges totalMs=$total MBps=${"%.2f".format(SIZE / 1048576.0 / (total / 1000.0))} " +
            "requests=$requestsUsed served=$servedBytes servedOverSize=${"%.3f".format(servedBytes / SIZE.toDouble())} per10pctMs=$s tailRatio=${"%.2f".format(d.tailRatio())} lastOverMean=${"%.2f".format(d.lastOverMean())}")
        if (engine in ruled && d.lastOverMean() > TAIL_LIMIT) broken += "$engine/${profile.name}: last 10 % took ${"%.2f".format(d.lastOverMean())}× the average 10 % $s"
    }

    private fun check(f: File) {
        assertEquals("size of ${f.name}", SIZE.toLong(), f.length())
        val got = f.readBytes()
        assertTrue("content of ${f.name}", got.contentEquals(data))
    }

    private fun run(engine: String, body: suspend (File, Deciles) -> Unit) = runBlocking {
        val f = File.createTempFile("bug3-$engine", ".bin").also { it.delete() }
        val d = Deciles(SIZE.toLong())
        val r0 = requests.get()
        val s0 = served.get()
        body(f, d)
        Thread.sleep(300) // writes already in the socket when the client closed
        report(engine, d, requests.get() - r0, served.get() - s0)
        check(f)
        File(f.parentFile, f.name + ".chunks").delete(); File(f.parentFile, f.name + ".idx").delete(); f.delete()
    }

    private fun allEngines() {
        ranges = true
        run("segmented4") { f, d -> Segmented(client, 4).download("$base/file", emptyMap(), f) { done, _ -> d.on(done) } }
        run("segmented2-lowend") { f, d -> Segmented(client, 2).download("$base/file", emptyMap(), f) { done, _ -> d.on(done) } }
        run("youtube-8mb-chunks") { f, d -> YtDownloads.fetch(client, "$base/file", emptyMap(), f, SIZE.toLong()) { done -> d.on(done) } }
        run("hls-sequential") { f, d -> HlsDownloader(client, emptyMap()).download("$base/hls/index.m3u8", f) { done, _ -> d.on(done) } }
        ranges = false
        run("single-no-ranges") { f, d -> Segmented(client, 4).download("$base/file", emptyMap(), f) { done, _ -> d.on(done) } }
        ranges = true
        assertTrue("BUG-3 tail rule (≤ $TAIL_LIMIT×): $broken", broken.isEmpty())
    }

    @Test fun steadyPerConnectionCap() {
        profile = Profile("steady-3MBps", burst = 0, bytesPerSec = 3L * 1024 * 1024)
        allEngines()
    }

    /**
     * G8 (R17): every connection drops after 700 KB of body. Each engine must still finish in one
     * call with the exact bytes (it used to fail: 4 drops per 4 MB piece, any drop for YouTube).
     */
    @Test fun droppedConnectionsResumeInsideOneRun() {
        profile = Profile("drop-every-700KB", burst = 0, bytesPerSec = 0, cutAfter = 700L * 1024)
        ranges = true
        run("segmented4") { f, d -> Segmented(client, 4).download("$base/file", emptyMap(), f) { done, _ -> d.on(done) } }
        run("segmented2-lowend") { f, d -> Segmented(client, 2).download("$base/file", emptyMap(), f) { done, _ -> d.on(done) } }
        run("youtube-8mb-chunks") { f, d -> YtDownloads.fetch(client, "$base/file", emptyMap(), f, SIZE.toLong()) { done -> d.on(done) } }
        broken.clear() // speed is not the point here; the bytes are (check() in run)
    }

    @Test fun burstThenCapPerRequest() {
        profile = Profile("burst3MB-then-1.5MBps", burst = 3L * 1024 * 1024, bytesPerSec = 1536L * 1024)
        allEngines()
    }
}
