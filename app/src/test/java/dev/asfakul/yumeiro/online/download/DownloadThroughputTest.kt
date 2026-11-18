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
        /**
         * A last 10 % under this many ms passes whatever the ratio. CI 43871066269: segmented2 on
         * burst-then-cap took 349 ms in all (35 ms per 10 %); one RequestPace window (256 KiB at
         * 1.5 MB/s ≈ 170 ms) landing in the last tenth read as 3.07×. BUG-3 was a slow tail of
         * seconds on a long download, which this floor does not hide.
         */
        const val TAIL_FLOOR_MS = 250L
        /**
         * A tail is slow only if the last 10 % is also slower than every earlier 10 % by this factor.
         * CI 43899660445 (slower runner, 851 ms in all): segmented2 burst-then-cap steps
         * [9, 4, 270, 7, 4, 117, 160, 5, 4, 271]: the same pace window landed in tenths 3 and 10
         * (270 vs 271 ms), so the last tenth was no slower than the middle; the fixed floor alone is
         * machine-speed dependent. A real BUG-3 tail (one connection left at the capped rate) makes
         * the last tenth far longer than any earlier one, which this still fails.
         */
        const val TAIL_OVER_PEAK = 1.5

        /** BUG-3 tail rule on the ten per-10 % times. */
        fun slowTail(s: List<Long>): Boolean {
            if (s.size < 2 || s.any { it < 0 }) return false
            val mean = s.sum() / s.size.toDouble()
            val last = s.last()
            val earlierPeak = s.dropLast(1).max()
            return last / mean.coerceAtLeast(1.0) > TAIL_LIMIT && last > TAIL_FLOOR_MS && last > earlierPeak * TAIL_OVER_PEAK
        }
        const val SEG = 512 * 1024 // 48 segments cover SIZE exactly (600 KB left 576 KB out: test error in 43851470283)
        private lateinit var server: java.net.ServerSocket
        private val pool = Executors.newCachedThreadPool()
        private val requests = AtomicInteger()
        /** G8 (R17): body bytes the server wrote (all requests); over SIZE = data read and thrown away. */
        private val served = java.util.concurrent.atomic.AtomicLong()
        private val data = ByteArray(SIZE) { (it % 251).toByte() }
        @Volatile var profile = Profile("steady", burst = 0, bytesPerSec = 3L * 1024 * 1024)
        @Volatile var ranges = true
        @Volatile var dipEpoch = System.nanoTime()
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
                var stalled = false
                while (sent < count) {
                    val n = minOf(16 * 1024L, count - sent).toInt()
                    os.write(data, off + (start + sent).toInt(), n); sent += n; served.addAndGet(n.toLong())
                    if (p.cutAfter > 0 && sent >= p.cutAfter && sent < count) { os.flush(); runCatching { sock?.close() }; return } // G8: drop mid-body
                    // DL-11 part 2: one stall per request (a lost packet waiting for its retransmit), then full speed.
                    if (p.stallMs > 0 && !stalled && sent >= p.stallAt && sent < count) { os.flush(); stalled = true; Thread.sleep(p.stallMs) }
                    if (p.dipCycleMs > 0) {
                        // DL-11: the whole link dips (every request at once), by the wall clock.
                        val phase = ((System.nanoTime() - dipEpoch) / 1_000_000L) % p.dipCycleMs
                        val rate = if (phase >= p.dipCycleMs - p.dipMs) p.dipRate else p.bytesPerSec
                        TimeUnit.NANOSECONDS.sleep(n * 1_000_000_000L / rate)
                    } else if (sent > p.burst && p.bytesPerSec > 0) {
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

    data class Profile(val name: String, val burst: Long, val bytesPerSec: Long, val cutAfter: Long = 0,
                       val dipCycleMs: Long = 0, val dipMs: Long = 0, val dipRate: Long = 0,
                       val stallAt: Long = 0, val stallMs: Long = 0)

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
    private var lastRequests = 0
    private var lastServed = 0L

    private fun report(engine: String, d: Deciles, requestsUsed: Int, servedBytes: Long) {
        val s = d.steps()
        val total = d.at.last()
        println("THROUGHPUT engine=$engine profile=${profile.name} ranges=$ranges totalMs=$total MBps=${"%.2f".format(SIZE / 1048576.0 / (total / 1000.0))} " +
            "requests=$requestsUsed served=$servedBytes servedOverSize=${"%.3f".format(servedBytes / SIZE.toDouble())} per10pctMs=$s tailRatio=${"%.2f".format(d.tailRatio())} lastOverMean=${"%.2f".format(d.lastOverMean())}")
        if (engine in ruled && slowTail(s)) broken += "$engine/${profile.name}: last 10 % took ${"%.2f".format(d.lastOverMean())}× the average 10 % $s"
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
        lastRequests = requests.get() - r0; lastServed = served.get() - s0
        report(engine, d, lastRequests, lastServed)
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
        // DL-11: the probe's full 200 answer is the download; before, it was closed and asked for
        // again (CI 43877618176: requests=2, servedOverSize=1.132).
        if (profile.cutAfter == 0L) {
            assertEquals("single-no-ranges requests (${profile.name})", 1, lastRequests)
            assertTrue("single-no-ranges read ${lastServed} bytes for $SIZE (${profile.name})", lastServed <= SIZE + 64 * 1024)
        }
        assertTrue("BUG-3 tail rule (≤ $TAIL_LIMIT×): $broken", broken.isEmpty())
    }

    /** The rule itself: the BUG-3 shape fails, a pace window repeated in the last tenth does not. */
    @Test fun tailRuleSeparatesSlowTailFromRepeatedPause() {
        assertTrue("owner-like slow tail", slowTail(listOf(400, 400, 400, 400, 400, 400, 400, 400, 400, 2000)))
        assertTrue("short download, tail of seconds", slowTail(listOf(10, 10, 10, 10, 10, 10, 10, 10, 10, 900)))
        assertTrue("CI 43899660445 segmented2 burst", !slowTail(listOf(9, 4, 270, 7, 4, 117, 160, 5, 4, 271)))
        assertTrue("CI 43889319143 segmented2 burst", !slowTail(listOf(10, 5, 103, 5, 112, 48, 5, 61, 4, 111)))
        assertTrue("steady", !slowTail(listOf(398, 401, 401, 398, 396, 401, 398, 401, 401, 396)))
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

    /**
     * DL-11 cause logging (note §8.3): every pace restart leaves one `trips` entry (left, read, slow and
     * baseline speed, the other requests, how the restart was judged) so the next fix is chosen from
     * device data. Logging only: the burst host must still restart (BUG-3) and the bytes must be exact.
     */
    @Test fun paceTripsAreLoggedOncePerRestart() {
        profile = Profile("burst3MB-then-1.5MBps", burst = 3L * 1024 * 1024, bytesPerSec = 1536L * 1024)
        ranges = true
        run("segmented2-trip-log") { f, d -> Segmented(client, 2).download("$base/file", emptyMap(), f) { done, _ -> d.on(done) } }
        println("DL11 trip-log served=$lastServed ${ForceGrab.lastStats}")
        broken.clear()
        val list = Regex("""\btrips=\[([^\]]*)]""").find(ForceGrab.lastStats)?.groupValues?.get(1)
        assertTrue("trips field missing: ${ForceGrab.lastStats}", list != null)
        val entries = list!!.split(',').filter { it.isNotEmpty() }
        val restarts = stat("paceRestarts")
        assertTrue("the burst host must still restart (BUG-3): ${ForceGrab.lastStats}", restarts > 0)
        assertEquals("one entry per restart: ${ForceGrab.lastStats}", minOf(restarts, TRIP_LOG_MAX), entries.size)
        val shape = Regex("""L\d+s\d+r\d+b\d+p[0-9?/|]*(j[HU-])?""")
        entries.forEach { assertTrue("entry shape: $it", shape.matches(it)) }
    }

    /** A counter of the last segmented download (ForceGrab.lastStats), -1 when absent. */
    private fun stat(key: String): Int = Regex("\\b$key=(\\d+)").find(ForceGrab.lastStats)?.groupValues?.get(1)?.toInt() ?: -1

    /**
     * DL-11 part 2 (ADR-016): the app's own pauses (a slow disk write, a GC, a busy low-RAM CPU) are
     * not a host that slowed down. RequestPace timed its window by the wall clock, so a 200 ms pause
     * of ours inside a 256 KiB window read as a request under a third of its baseline; the request was
     * closed and the bytes already on the way were thrown away (real host: 1–29 restarts per 135 MB,
     * 1.04–1.44x received, 44036060683 / 44037559115). The host here is steady and never caps.
     */
    @Test fun ownPausesAreNotAHostSlowdown() {
        profile = Profile("steady-12MBps", burst = 0, bytesPerSec = 12L * 1024 * 1024)
        ranges = true
        for (c in listOf(2, 4)) {
            val every = 3L * 512 * 1024
            var next = every
            val lock = Any()
            run("segmented$c-own-pauses") { f, d ->
                Segmented(client, c).download("$base/file", emptyMap(), f) { done, _ ->
                    d.on(done)
                    val pause = synchronized(lock) { if (done >= next) { next += every; true } else false }
                    if (pause) Thread.sleep(200)
                }
            }
            println("DL11 own-pauses connections=$c ${ForceGrab.lastStats}")
            assertEquals("DL-11: our own pauses restarted requests (connections=$c): ${ForceGrab.lastStats}", 0, stat("paceRestarts"))
        }
        broken.clear() // the pauses make the tenths uneven on purpose; the point is the restarts
    }

    /**
     * DL-11 part 2 (ADR-016): when the whole link slows (Wi-Fi, a shared runner), a new request is as
     * slow as the one it replaced, so every restart only throws away bytes. After restarts that did not
     * help (new request's first MiB not 1.5x the slow window), pace restarts stop for that download.
     */
    @Test fun linkWideDipsStopUselessRestarts() {
        profile = Profile("link-dips", burst = 0, bytesPerSec = 4L * 1024 * 1024, dipCycleMs = 3500, dipMs = 2500, dipRate = 512L * 1024)
        ranges = true
        dipEpoch = System.nanoTime()
        run("segmented2-link-dips") { f, d -> Segmented(client, 2).download("$base/file", emptyMap(), f) { done, _ -> d.on(done) } }
        println("DL11 link-dips served=$lastServed ${ForceGrab.lastStats}")
        broken.clear()
        assertTrue("DL-11: restarts kept going on a link that dips as a whole: ${ForceGrab.lastStats}", stat("paceRestarts") in 0..3)
    }

    /**
     * DL-11 part 2 (G8 batch, ADR-016 amendment): one connection stalls once (a lost packet: the read
     * waits for the retransmit, then the data comes at full speed) while the host never caps. The
     * window that holds the stall read as a request under a third of its baseline, so the request was
     * closed and the bytes on the way were thrown away (real host 44121429260: one download 1.1004x;
     * 44180844505 trips with peers at 202-1868 % of their baseline). A stall that one read waited out
     * recovers by itself and must not restart the request. Steady 12 MiB/s, a 600 ms stall 1.5 MiB into
     * every request.
     */
    @Test fun aSingleStallIsRiddenOutNotRestarted() {
        profile = Profile("stall-once-per-request", burst = 0, bytesPerSec = 12L * 1024 * 1024, stallAt = 1536L * 1024, stallMs = 600)
        ranges = true
        for (c in listOf(2, 4)) {
            run("segmented$c-stalls") { f, d -> Segmented(client, c).download("$base/file", emptyMap(), f) { done, _ -> d.on(done) } }
            println("DL11 stalls connections=$c served=$lastServed ${ForceGrab.lastStats}")
            assertEquals("DL-11: a single stall restarted the request (connections=$c): ${ForceGrab.lastStats}", 0, stat("paceRestarts"))
        }
        broken.clear() // the stalls make the tenths uneven on purpose; the point is the restarts
    }
}
