/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.download

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.exoplayer.dash.DashSegmentIndex
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.dash.manifest.Representation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicLong

/**
 * R15 Phase 5: forced downloading, the way IDM does it. Many sources "don't allow" downloads only
 * in the sense that the simple one-request download fails: the file is DASH (a manifest of
 * segments), the picture and the sound come as two files, the host throttles one connection, or
 * the CDN refuses a request that doesn't look like its own web player. The player can play all of
 * these, so the downloader can save them:
 *
 *  - [Segmented]: a direct file in 4 MB chunks over several connections at once (2 on low-end
 *    phones, 4 otherwise), resumable chunk by chunk (a side file remembers finished chunks).
 *  - [ForceHeaders]: when the host answers 401/403, retry as its web player would (browser
 *    User-Agent, Origin from the Referer, the WebView's cookies for that host), then without a
 *    foreign Referer.
 *  - [Dash]: picks the best video (within the download quality) and the best audio from the MPD,
 *    saves each track's init + segments, then joins them with [MediaMux] (no re-encoding).
 *  - Separate audio ([OnlineLink.audioTracks]): video and audio are saved, then joined.
 *  - Torrents: through the user's TorrServer, which serves the file over HTTP.
 *
 * Never bypassed: DRM (Widevine / PlayReady / SAMPLE-AES). Live streams are refused (they never end).
 */
object ForceGrab {
    /** Chunk size for multi-connection downloads. */
    const val CHUNK = 4L * 1024 * 1024
    /** Files smaller than this use one connection (no gain, more requests). */
    const val SEGMENT_FROM = 16L * 1024 * 1024

    fun connections(lowEnd: Boolean) = if (lowEnd) 2 else 4

    /** Chunk ranges (inclusive) for a file of [total] bytes. Pure. */
    fun chunks(total: Long, size: Long = CHUNK): List<LongRange> =
        if (total <= 0) emptyList() else (0 until (total + size - 1) / size).map { i -> (i * size)..minOf(total - 1, (i + 1) * size - 1) }

    /** Parses `Content-Range: bytes 0-0/12345` → 12345 (null when unknown). Pure. */
    fun totalFromContentRange(v: String?): Long? = v?.substringAfterLast('/')?.trim()?.toLongOrNull()?.takeIf { it > 0 }

    /** Finished-chunk side file: one index per line. */
    /**
     * DL-11 measurement: counters of the last segmented download in this process (requests, pace
     * restarts, tail splits, retries after a drop, bytes read past a moved piece end). Read by the
     * device tests; no URL or header is kept.
     */
    @Volatile var lastStats: String = ""

    /**
     * DL-11 H3 control (device tests only, default off): let byte ranges use HTTP/2 again, so the
     * old behaviour can still be measured next to the fix. Not a user setting.
     */
    @Volatile var rangesAllowHttp2ForTest: Boolean = false

    /**
     * ADR-016 F: the client for byte-range downloads, limited to HTTP/1.1. On HTTP/2 every segment
     * is a stream on one TCP connection; a pace restart then cancels a stream whose bytes are
     * already in flight and the new stream rides the warm connection, so the gate calls it helped
     * (device 44118957078: h2 1.08–1.15× the file received, HTTP/1.1 1.03–1.07× and faster).
     */
    fun rangeClient(base: OkHttpClient): OkHttpClient =
        if (rangesAllowHttp2ForTest) base else base.newBuilder().protocols(listOf(okhttp3.Protocol.HTTP_1_1)).build()

    internal fun readDone(f: File): MutableSet<Int> =
        if (!f.exists()) HashSet() else f.readLines().mapNotNullTo(HashSet()) { it.trim().toIntOrNull() }
}

/** Escalating request headers for hosts that refuse a plain download request. Pure. */
object ForceHeaders {
    const val BROWSER_UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Mobile Safari/537.36"
    const val ATTEMPTS = 3

    /**
     * Attempt 0: the link's own headers (what the player sends). 1: like the site's web player
     * (browser UA, Origin from the Referer, Sec-Fetch for media, cookies). 2: as 1 without the
     * Referer/Origin (some CDNs refuse a foreign Referer).
     */
    fun forAttempt(base: Map<String, String>, attempt: Int, cookies: String?): Map<String, String> {
        if (attempt <= 0) return base
        val out = LinkedHashMap<String, String>()
        base.forEach { (k, v) -> if (!k.equals("User-Agent", true)) out[k] = v }
        out["User-Agent"] = BROWSER_UA
        out.putIfAbsent("Accept", "*/*")
        out["Sec-Fetch-Mode"] = "no-cors"; out["Sec-Fetch-Dest"] = "video"
        val referer = base.entries.firstOrNull { it.key.equals("Referer", true) }?.value
        if (referer != null && out.keys.none { it.equals("Origin", true) }) originOf(referer)?.let { out["Origin"] = it }
        if (!cookies.isNullOrBlank() && out.keys.none { it.equals("Cookie", true) }) out["Cookie"] = cookies
        if (attempt >= 2) out.keys.removeAll { it.equals("Referer", true) || it.equals("Origin", true) }
        return out
    }

    fun originOf(url: String): String? = runCatching {
        val u = java.net.URI(url); if (u.scheme == null || u.host == null) null
        else u.scheme + "://" + u.host + (if (u.port > 0) ":${u.port}" else "")
    }.getOrNull()

    /** The WebView's cookies for [url] (the site may have set them while the user browsed it). */
    fun cookiesFor(url: String): String? = runCatching { android.webkit.CookieManager.getInstance().getCookie(url) }.getOrNull()
}

/** Multi-connection, resumable download of one direct file (see [ForceGrab]). */
/**
 * BUG-3: a byte range one connection is reading. Another connection may [split] off its second half;
 * [accept] only writes what is still inside the piece, so bytes past a moved end are dropped (the
 * other connection writes the same bytes at the same offsets). Pure → unit-tested.
 */
internal class Piece(val chunk: Int, start: Long, end: Long) {
    private var pos = start
    private var last = end
    @Synchronized fun position() = pos
    @Synchronized fun end() = last
    @Synchronized fun left() = (last - pos + 1).coerceAtLeast(0)

    /** Hands the second half to a new piece when at least 2 × [min] bytes are left; else null. */
    @Synchronized fun split(min: Long): Piece? {
        val rem = last - pos + 1
        if (rem < 2 * min) return null
        val mid = pos + rem / 2
        val tail = Piece(chunk, mid, last)
        last = mid - 1
        return tail
    }

    /** [n] bytes were read at the current position: writes the part inside the piece, returns its size. */
    @Synchronized fun accept(n: Int, write: (Long, Int) -> Unit): Int {
        val k = minOf(n.toLong(), last - pos + 1).coerceAtLeast(0).toInt()
        if (k > 0) { write(pos, k); pos += k }
        return k
    }
}

/** BUG-3: an idle connection never takes less than this from another one. */
internal const val MIN_SPLIT = 1L * 1024 * 1024

/** BUG-3: a slowed request is only replaced when at least this much of its piece is left. */
internal const val RESTART_MIN_LEFT = 512L * 1024

/** BUG-3: at most this many fresh requests per piece (a host that is slow for every request). */
internal const val MAX_RESTARTS = 32

/**
 * BUG-3: tells when one HTTP request has slowed down for good. Many hosts and CDNs serve the first
 * megabytes of every request fast and then cap that request (nginx `limit_rate_after`, per-request
 * shaping); a download that keeps one long request per piece then crawls once every connection is
 * past its burst, which is the "fast to 50-70 %, then slow" the owner saw (CI 43854742733: 4.8x).
 * The baseline is the request's own speed over its first [baselineBytes] (from the first byte, so
 * TCP slow start only lowers it); after that each [window] is compared with it. A window under
 * 1/[factor] of the baseline says: ask for the rest in a new request. A steady host never trips it.
 * DL-11 part 2 (ADR-016): a window is timed by the time spent waiting inside `read()` when the caller
 * gives it ([slowed] `waitedNs`), not by the wall clock. Our own pauses between reads (disk write, GC,
 * a busy low-RAM CPU) are not the host slowing down; closing the request for them threw away the
 * bytes already on the way ([ownPauses] counts the windows that the wall clock alone would have tripped).
 * Pure; `Bug3PieceTest`, `Dl11PaceTest`.
 */
internal class RequestPace(
    private val baselineBytes: Long = 1L * 1024 * 1024,
    private val window: Long = 256L * 1024,
    private val factor: Int = 3,
) {
    private var t0 = -1L
    // DL-11 cause logging reads these two from other connections' threads (log only), hence @Volatile.
    @Volatile private var seen = 0L
    @Volatile private var baseline = -1.0 // bytes per ns
    private var winBytes = 0L
    private var winStart = 0L
    private var winWait = 0L

    /** The request's first-[baselineBytes] speed in bytes per ns (wall clock); -1 until known. */
    val baselineRate: Double get() = baseline

    /** Bytes this request has brought so far (DL-11 cause logging). */
    val seenBytes: Long get() = seen

    /** The speed of the last finished window in bytes per ns (the one that tripped, after [slowed] = true). */
    @Volatile var lastWindowRate = -1.0
        private set

    /** Windows the wall clock would have tripped but the time spent waiting on the host did not (DL-11). */
    var ownPauses = 0
        private set

    /**
     * [n] bytes arrived at [nowNs]; [waitedNs] is the time the read that brought them waited for the
     * host (-1: unknown, use the wall clock as before). True when the last window ran under the
     * baseline / factor.
     */
    fun slowed(n: Long, nowNs: Long, waitedNs: Long = -1L): Boolean {
        if (t0 < 0) { t0 = nowNs; winStart = nowNs }
        seen += n
        if (baseline < 0) {
            if (seen >= baselineBytes) { baseline = seen / maxOf(nowNs - t0, 1_000_000L).toDouble(); winBytes = 0; winWait = 0; winStart = nowNs }
            return false
        }
        winBytes += n
        if (waitedNs >= 0) winWait += waitedNs
        if (winBytes < window) return false
        val wall = maxOf(nowNs - winStart, 1_000_000L)
        val span = if (waitedNs >= 0) minOf(maxOf(winWait, 1_000_000L), wall) else wall
        val rate = winBytes / span.toDouble()
        val wallSlow = winBytes / wall.toDouble() * factor < baseline
        winBytes = 0; winWait = 0; winStart = nowNs
        lastWindowRate = rate
        val slow = rate * factor < baseline
        if (wallSlow && !slow) ownPauses++
        return slow
    }
}

/** DL-11 cause logging: at most this many pace trips are recorded per download. */
internal const val TRIP_LOG_MAX = 48

/** DL-11 part 2: a fresh request only counts as a help when its first MiB is this much faster than the slow window. */
internal const val RESTART_GAIN = 1.5

/** DL-11 part 2: pace restarts stop for a download after this many restarts that did not help (and more than helped). */
internal const val MAX_USELESS_RESTARTS = 2

/**
 * DL-11 part 2 (ADR-016 option D): one per download. A pace restart helps on a host that caps each
 * request (the new request starts fast again, BUG-3); on a link that slows as a whole the new request
 * is as slow as the one it replaced, and every restart only throws away the bytes in flight (~2 MB
 * each on the real host). Once restarts have not helped [MAX_USELESS_RESTARTS] times, and more often
 * than they helped, pace restarts stop for the rest of the download. Thread-safe; pure.
 */
internal class PaceGate {
    private val useful = java.util.concurrent.atomic.AtomicInteger()
    private val useless = java.util.concurrent.atomic.AtomicInteger()
    @Volatile var open = true
        private set
    val helped get() = useful.get()
    val wasted get() = useless.get()

    /** A restarted request's first-MiB speed [newBaseline] against the window [slowRate] that caused it. */
    fun judge(newBaseline: Double, slowRate: Double): Boolean {
        val ok = newBaseline >= slowRate * RESTART_GAIN
        if (ok) useful.incrementAndGet()
        else { val u = useless.incrementAndGet(); if (u >= MAX_USELESS_RESTARTS && u > useful.get()) open = false }
        return ok
    }
}

class Segmented(
    private val client: OkHttpClient,
    private val connections: Int,
    /** Called with the bytes still to fetch once the size is known (throw to stop, e.g. disk full). */
    private val checkSpace: (Long) -> Unit = {},
    private val segmentFrom: Long = ForceGrab.SEGMENT_FROM,
    private val chunk: Long = ForceGrab.CHUNK,
) {
    class Refused(val code: Int) : IOException("HTTP $code")

    /** ADR-016 F: one TCP connection per segment (HTTP/1.1), see [ForceGrab.rangeClient]. */
    private val http: OkHttpClient = ForceGrab.rangeClient(client)

    /**
     * Downloads [url] into [out] (resuming). Returns the bytes written. Uses one connection when
     * the host has no byte ranges or the file is small; escalates headers on 401/403.
     */
    suspend fun download(url: String, headers: Map<String, String>, out: File, progress: (Long, Long) -> Unit): Long = withContext(Dispatchers.IO) {
        var lastRefusal: Refused? = null
        for (attempt in 0 until ForceHeaders.ATTEMPTS) {
            val h = ForceHeaders.forAttempt(headers, attempt, if (attempt > 0) ForceHeaders.cookiesFor(url) else null)
            try {
                return@withContext once(url, h, out, progress)
            } catch (e: Refused) {
                if (e.code != 401 && e.code != 403) throw e
                lastRefusal = e
            }
        }
        throw lastRefusal ?: IOException("Download refused")
    }

    private suspend fun once(url: String, headers: Map<String, String>, out: File, progress: (Long, Long) -> Unit): Long {
        val answer = probe(url, headers)
        // DL-11: a host without byte ranges answers the 1-byte probe with the whole file (200). That
        // body was closed and the file asked for again, so the bytes the host had already sent were
        // lost (local server: 1.13x the file). The 200 answer is now the download itself.
        // With part of the file already on disk, single() still asks for the rest with a Range first.
        answer.whole?.let { r ->
            if (out.exists() && out.length() > 0) r.close() else return r.use { whole(it, out, progress) }
        }
        val probe = answer.total
        if (probe == null || probe < segmentFrom || connections <= 1) return single(url, headers, out, progress)
        val total = probe
        val ranges = ForceGrab.chunks(total, chunk)
        checkSpace(total - (if (out.exists() && out.length() == total) out.length() / 2 else 0L))
        val state = File(out.parentFile, out.name + ".chunks")
        val done = if (out.exists() && out.length() == total) ForceGrab.readDone(state) else HashSet<Int>().also { state.delete() }
        RandomAccessFile(out, "rw").use { it.setLength(total) }
        val have = AtomicLong(done.sumOf { ranges.getOrNull(it)?.let { r -> r.last - r.first + 1 } ?: 0L })
        progress(have.get(), total)
        val lock = Mutex()
        val todo = ranges.indices.filter { it !in done }.toMutableList()
        // BUG-3: when the list ran out, a connection went idle while the others were still on their
        // last chunk, so the last part ran on fewer connections (and, on a host that is fast only for
        // the first bytes of a request, on the slow end of every request). An idle connection now
        // takes the second half of the in-flight piece with the most bytes left (IDM-style), so every
        // connection works to the end. A chunk counts as done (side file) once all its pieces are in.
        val running = ArrayList<Piece>()
        val pieces = HashMap<Int, Int>()
        val resumedChunks = done.size
        val splits = java.util.concurrent.atomic.AtomicInteger()
        val restartsAll = java.util.concurrent.atomic.AtomicInteger()
        val retries = java.util.concurrent.atomic.AtomicInteger()
        val gate = PaceGate()
        requests.set(0); dropped.set(0); ownPauses.set(0); trips.clear(); active.clear()
        coroutineScope {
            (0 until connections).map {
                async {
                    while (true) {
                        val piece = lock.withLock {
                            val next = todo.removeFirstOrNull()?.let { i -> Piece(i, ranges[i].first, ranges[i].last).also { pieces[i] = 1 } }
                                ?: running.maxByOrNull { it.left() }?.split(MIN_SPLIT)?.also { pieces[it.chunk] = (pieces[it.chunk] ?: 0) + 1; splits.incrementAndGet() }
                            next?.also { running += it }
                        } ?: break
                        var tries = 0
                        var restarts = 0
                        var slowRate = -1.0 // the window that made the last restart (DL-11: judged by the next request)
                        var trip: StringBuilder? = null
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val at = piece.position()
                            val after = slowRate; slowRate = -1.0
                            val lastTrip = trip; trip = null
                            try {
                                val (slow, entry) = fetchPiece(url, headers, piece, out, pace = restarts < MAX_RESTARTS, after = after, gate = gate, trip = lastTrip) { n -> progress(have.addAndGet(n), total) }
                                if (slow > 0) { restarts++; restartsAll.incrementAndGet(); slowRate = slow; trip = entry; continue } // same piece, fresh request
                                break
                            } catch (e: Refused) { throw e } catch (e: IOException) {
                                // The bytes already written stay; the retry starts where the piece stopped.
                                // G8 (R17): only tries that brought nothing count. Every drop counted, so a
                                // connection that drops each ~1 MB failed a 4 MB piece and the whole run.
                                retries.incrementAndGet()
                                if (piece.position() > at) { tries = 0; delay(200L); continue }
                                if (++tries >= 4) throw e
                                delay(800L * tries)
                            }
                        }
                        lock.withLock {
                            running -= piece
                            val left = (pieces[piece.chunk] ?: 1) - 1
                            pieces[piece.chunk] = left
                            if (left == 0) { done += piece.chunk; state.appendText("${piece.chunk}\n") }
                        }
                    }
                }
            }.awaitAll()
        }
        state.delete()
        ForceGrab.lastStats = "total=$total connections=$connections chunks=${ranges.size} resumedChunks=$resumedChunks " +
            "requests=${requests.get()} paceRestarts=${restartsAll.get()} splits=${splits.get()} retries=${retries.get()} droppedPastEnd=${dropped.get()}" +
            " paceHelped=${gate.helped} paceWasted=${gate.wasted} paceOff=${if (gate.open) 0 else 1} ownPauses=${ownPauses.get()} protocol=$protocol" +
            " trips=${synchronized(trips) { trips.joinToString(",", "[", "]") }}"
        return total
    }

    private val requests = java.util.concurrent.atomic.AtomicInteger()
    private val dropped = AtomicLong()

    /**
     * DL-11 cause logging (no behaviour change): one entry per pace trip, at most [TRIP_LOG_MAX].
     * `L` KiB left in the piece, `s` KiB the request had read, `r` slow window and `b` baseline in
     * KiB/s, `p` the other active requests as (last window ÷ own baseline in %)/(KiB read), `j` how
     * the restarted request was judged (H helped, U useless, - not judged: piece ended first).
     * Read from the device test's SEGMENTED_STATS line to choose the next fix from data (note §8.3).
     */
    private val trips = ArrayList<StringBuilder>()
    private val active = java.util.concurrent.ConcurrentHashMap<Piece, RequestPace>()

    private fun tripEntry(p: Piece, m: RequestPace): StringBuilder? {
        fun kbps(bytesPerNs: Double) = (bytesPerNs * 1e9 / 1024).toLong()
        val peers = active.entries.filter { it.key !== p }.joinToString("|") { (_, o) ->
            val b = o.baselineRate; val w = o.lastWindowRate
            (if (b > 0 && w > 0) "${(w / b * 100).toLong()}" else "?") + "/${o.seenBytes / 1024}"
        }
        val e = StringBuilder("L${p.left() / 1024}s${m.seenBytes / 1024}r${kbps(m.lastWindowRate)}b${kbps(m.baselineRate)}p$peers")
        synchronized(trips) { if (trips.size >= TRIP_LOG_MAX) return null; trips += e }
        return e
    }
    private val ownPauses = java.util.concurrent.atomic.AtomicInteger()
    /** DL-11 cause logging: the wire protocol of the probe (h2 = all ranges share one TCP connection). */
    @Volatile private var protocol = "?"

    /** [total]: size when the host serves byte ranges. [whole]: the open 200 answer of a host without them. */
    private class Probe(val total: Long?, val whole: okhttp3.Response?)

    private fun probe(url: String, headers: Map<String, String>): Probe {
        val b = Request.Builder().url(url).header("Range", "bytes=0-0")
        headers.forEach { (k, v) -> runCatching { b.header(k, v) } }
        val r = http.newCall(b.build()).execute()
        protocol = r.protocol.toString()
        if (r.code == 200) return Probe(null, r) // caller closes it
        r.use {
            if (r.code == 401 || r.code == 403) throw Refused(r.code)
            if (r.code != 206) return Probe(null, null)
            return Probe(ForceGrab.totalFromContentRange(r.header("Content-Range")), null)
        }
    }

    /** Writes a full 200 answer to [out] from the start (a host without ranges can't resume). */
    private suspend fun whole(r: okhttp3.Response, out: File, progress: (Long, Long) -> Unit): Long {
        val total = r.body.contentLength().takeIf { it > 0 } ?: -1L
        if (total > 0) checkSpace(total)
        var done = 0L
        FileOutputStream(out, false).use { fos ->
            r.body.byteStream().use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buf); if (n < 0) break
                    fos.write(buf, 0, n); done += n; progress(done, total)
                }
            }
        }
        if (total > 0 && done < total) throw IOException("Connection closed early")
        return done
    }

    /**
     * Reads [p] from its position to its (possibly shrinking) end. Returns -1 when the piece is full;
     * the slow window's speed (bytes/ns, > 0) when the request slowed to under a third of its own
     * first-MiB speed with at least [RESTART_MIN_LEFT] to go (with [pace] and an open [gate]), so the
     * caller asks for the rest in a new request. [after] > 0: this request replaces one that slowed to
     * that speed; its first MiB tells [gate] whether the restart helped (DL-11 part 2).
     */
    private fun fetchPiece(url: String, headers: Map<String, String>, p: Piece, out: File, pace: Boolean, after: Double, gate: PaceGate, trip: StringBuilder? = null, read: (Long) -> Unit): Pair<Double, StringBuilder?> {
        val from = p.position()
        val to = p.end()
        if (from > to) { trip?.append("j-"); return -1.0 to null }
        val meter = RequestPace()
        var judged = after <= 0
        requests.incrementAndGet()
        val b = Request.Builder().url(url).header("Range", "bytes=$from-$to")
        headers.forEach { (k, v) -> runCatching { b.header(k, v) } }
        http.newCall(b.build()).execute().use { resp ->
            if (resp.code == 401 || resp.code == 403) throw Refused(resp.code)
            if (resp.code != 206) throw IOException("HTTP ${resp.code} for a byte range")
            RandomAccessFile(out, "rw").use { raf ->
                resp.body.byteStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    active[p] = meter
                    try {
                        while (true) {
                            val want = p.end() - p.position() + 1
                            if (want <= 0) { if (!judged) trip?.append("j-"); return -1.0 to null } // full, or another connection took the rest
                            val r0 = System.nanoTime()
                            val n = input.read(buf, 0, minOf(buf.size.toLong(), want).toInt()); if (n < 0) break
                            val r1 = System.nanoTime()
                            val k = p.accept(n) { at, len -> raf.seek(at); raf.write(buf, 0, len) }
                            if (k < n) dropped.addAndGet((n - k).toLong())
                            if (k > 0) read(k.toLong())
                            if (!pace) continue
                            val slowed = meter.slowed(k.toLong(), r1, r1 - r0)
                            if (!judged && meter.baselineRate > 0) { judged = true; trip?.append(if (gate.judge(meter.baselineRate, after)) "jH" else "jU") }
                            if (slowed && gate.open && p.left() >= RESTART_MIN_LEFT) return meter.lastWindowRate to tripEntry(p, meter)
                        }
                    } finally { active.remove(p); ownPauses.addAndGet(meter.ownPauses) }
                }
            }
            if (!judged) trip?.append("j-")
            if (p.position() <= p.end()) throw IOException("Connection closed early")
        }
        return -1.0 to null
    }

    /** One connection, resumable with Range when the host allows it. */
    private suspend fun single(url: String, headers: Map<String, String>, out: File, progress: (Long, Long) -> Unit): Long {
        val have = if (out.exists()) out.length() else 0L
        val b = Request.Builder().url(url)
        headers.forEach { (k, v) -> runCatching { b.header(k, v) } }
        if (have > 0) b.header("Range", "bytes=$have-")
        http.newCall(b.build()).execute().use { r ->
            if (r.code == 401 || r.code == 403) throw Refused(r.code)
            // 416 for "bytes=<size>-": the file is already complete. R16 (G8): only when the server's
            // size matches; a changed file (other size) or a range at another offset starts over.
            if (have > 0 && (r.code == 416 || r.code == 206)) {
                when (RangeResume.decide(have, r.code, r.header("Content-Range"))) {
                    RangeResume.Action.COMPLETE -> { progress(have, have); return have }
                    RangeResume.Action.RESTART -> { r.close(); out.delete(); return single(url, headers, out, progress) }
                    RangeResume.Action.APPEND -> Unit
                }
            }
            if (!r.isSuccessful) throw HttpStatus(r.code)
            val resumed = r.code == 206 && have > 0
            val length = r.body.contentLength()
            val total = if (length > 0) length + (if (resumed) have else 0) else -1
            var done = if (resumed) have else 0L
            if (total > 0) checkSpace(total - done)
            FileOutputStream(out, resumed).use { fos ->
                r.body.byteStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf); if (n < 0) break
                        fos.write(buf, 0, n); done += n; progress(done, total)
                    }
                }
            }
            if (total > 0 && done < total) throw IOException("Connection closed early")
            return done
        }
    }
}

/** DASH: best video + best audio from the MPD, saved track by track, then joined. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class]) // G12 X10-4: Media3 unstable API, pinned version
class DashGrab(private val client: OkHttpClient, private val headers: Map<String, String>) {
    /** One track to save: init segment (may be null) + media segments, or one whole file. */
    data class Track(val kind: Int, val mime: String?, val height: Int, val bitrate: Int, val init: Part?, val parts: List<Part>)
    data class Part(val url: String, val start: Long, val length: Long)
    data class Plan(val video: Track?, val audio: Track?, val webm: Boolean)

    companion object {
        /** Builds what to download from an MPD. Pure apart from parsing (needs the Android XML parser). */
        fun plan(mpd: String, mpdUrl: String, maxHeight: Int): Plan {
            val m = DashManifestParser().parse(Uri.parse(mpdUrl), mpd.byteInputStream())
            if (m.dynamic) throw IOException("Live streams can't be downloaded")
            if (m.periodCount < 1) throw IOException("The DASH manifest is empty")
            if (m.periodCount > 1) throw IOException("This DASH stream has ${m.periodCount} parts (ads or chapters); saving it isn't supported yet")
            val period = m.getPeriod(0)
            val durationUs = m.getPeriodDurationUs(0)
            val sets = period.adaptationSets
            // DRM-protected tracks are never saved (no bypass); clear tracks next to them are fine.
            // Also our own scan: media3 drops a <ContentProtection> it can't read (malformed pssh),
            // and that set must still count as protected.
            val marked = protectedSets(mpd)
            val clear = sets.filterIndexed { i, s -> i !in marked && s.representations.none { it.format.drmInitData != null } }
            val videos = clear.filter { it.type == C.TRACK_TYPE_VIDEO }.flatMap { it.representations }
            val audios = clear.filter { it.type == C.TRACK_TYPE_AUDIO }.flatMap { it.representations }
            val (v, a) = pickPair(videos, audios, maxHeight, java.util.Locale.getDefault().language)
            if (v == null && a == null) throw IOException(
                if (clear.size < sets.size) "This stream is DRM-protected and can't be saved" else "No video or audio track in the DASH manifest")
            val webm = listOfNotNull(v, a).all { isWebm(it) }
            return Plan(v?.let { track(it, C.TRACK_TYPE_VIDEO, durationUs) }, a?.let { track(it, C.TRACK_TYPE_AUDIO, durationUs) }, webm)
        }

        /** Indexes of the first Period's AdaptationSets that carry any <ContentProtection>. */
        internal fun protectedSets(mpd: String): Set<Int> {
            val out = HashSet<Int>()
            val xpp = android.util.Xml.newPullParser().apply { setInput(mpd.reader()) }
            var periods = 0; var set = -1; var inSet = false
            while (true) {
                val ev = xpp.next()
                if (ev == org.xmlpull.v1.XmlPullParser.END_DOCUMENT) break
                val name = xpp.name?.substringAfter(':')
                if (ev == org.xmlpull.v1.XmlPullParser.START_TAG) when (name) {
                    "Period" -> periods++
                    "AdaptationSet" -> if (periods == 1) { set++; inSet = true }
                    "ContentProtection" -> if (inSet && periods == 1) out += set
                } else if (ev == org.xmlpull.v1.XmlPullParser.END_TAG && name == "AdaptationSet") inSet = false
            }
            return out
        }

        private fun isWebm(r: Representation) = (r.format.containerMimeType ?: "").contains("webm")

        /**
         * Video + audio that fit one container (mp4: H.264/HEVC/AV1 + AAC; webm: VP9/AV1 + Opus),
         * as MediaMuxer can't put VP9/Opus in mp4 on older phones. The family with the taller video
         * wins (mp4 on a tie). Audio: the main track, else the phone's language, else the first set.
         */
        internal fun pickPair(videos: List<Representation>, audios: List<Representation>, maxHeight: Int, lang: String): Pair<Representation?, Representation?> {
            fun audioFor(list: List<Representation>): Representation? {
                if (list.isEmpty()) return null
                val main = list.filter { it.format.roleFlags and C.ROLE_FLAG_MAIN != 0 }
                val mine = list.filter { it.format.language?.substringBefore('-')?.equals(lang, true) == true }
                val firstLang = list.first().format.language
                val pool = main.ifEmpty { mine }.ifEmpty { list.filter { it.format.language == firstLang } }
                return pool.maxByOrNull { it.format.bitrate }
            }
            val families = listOf(false, true).map { webm ->
                val v = pickVideo(videos.filter { isWebm(it) == webm }, maxHeight)
                val a = audioFor(audios.filter { isWebm(it) == webm })
                Triple(webm, v, a)
            }
            val complete = families.filter { it.second != null && it.third != null }
            if (complete.isNotEmpty()) {
                val best = complete.maxWith(compareBy<Triple<Boolean, Representation?, Representation?>> { fit(it.second!!.format.height, maxHeight) }.thenBy { if (it.first) 0 else 1 })
                return best.second to best.third
            }
            // Only one kind of track (video-only or audio-only stream).
            return pickVideo(videos, maxHeight) to audioFor(audios)
        }

        /** Higher is better: heights within the cap rank by size; above the cap rank below all of them. */
        private fun fit(h: Int, maxHeight: Int): Int = if (maxHeight <= 0 || h <= maxHeight) h else -h

        internal fun pickVideo(reps: List<Representation>, maxHeight: Int): Representation? {
            if (reps.isEmpty()) return null
            val within = if (maxHeight > 0) reps.filter { it.format.height in 1..maxHeight } else reps
            return (within.ifEmpty { listOf(reps.minBy { it.format.height }) }).maxWith(compareBy<Representation> { it.format.height }.thenBy { it.format.bitrate })
        }

        private fun track(r: Representation, kind: Int, durationUs: Long): Track {
            val base = r.baseUrls.firstOrNull()?.url ?: throw IOException("A DASH track has no address")
            val init = r.initializationUri?.let { Part(it.resolveUriString(base), it.start, it.length) }
            val index: DashSegmentIndex? = r.index
            val parts = if (r is Representation.MultiSegmentRepresentation && index != null) {
                val count = index.getSegmentCount(if (durationUs == C.TIME_UNSET) C.TIME_UNSET else durationUs)
                if (count == DashSegmentIndex.INDEX_UNBOUNDED.toLong()) throw IOException("Live streams can't be downloaded")
                val first = index.getFirstSegmentNum()
                (first until first + count).map { n -> index.getSegmentUrl(n).let { u -> Part(u.resolveUriString(base), u.start, u.length) } }
            } else {
                // SegmentBase / single file: the whole file is the track (its index is inside it).
                listOf(Part(base, 0, C.LENGTH_UNSET.toLong()))
            }
            return Track(kind, r.format.sampleMimeType, r.format.height, r.format.bitrate, if (parts.size == 1 && parts[0].length < 0) null else init, parts)
        }
    }

    data class Saved(val bytes: Long, val webm: Boolean)

    /** Saves the stream at [url] into [out] (mp4, or webm when both tracks are webm), resuming per track. */
    suspend fun download(url: String, out: File, maxHeight: Int, lowEnd: Boolean, progress: (Long, Long) -> Unit): Saved = withContext(Dispatchers.IO) {
        val mpd = text(url)
        val plan = plan(mpd, url, maxHeight)
        val tracks = listOfNotNull(plan.video, plan.audio)
        val files = tracks.map { t -> File(out.parentFile, out.name + if (t.kind == C.TRACK_TYPE_VIDEO) ".v" else ".a") }
        val counted = LongArray(tracks.size)
        val report = { progress(counted.sum(), -1L) }
        coroutineScope {
            tracks.mapIndexed { i, t -> async { saveTrack(t, files[i], lowEnd) { n -> counted[i] = n; report() } } }.awaitAll()
        }
        val written = if (files.size == 2) {
            MediaMux.merge(files[0], files[1], out, plan.webm)
            files.forEach { it.delete() }; out.length()
        } else {
            out.delete(); if (!files[0].renameTo(out)) { files[0].copyTo(out, overwrite = true); files[0].delete() }; out.length()
        }
        progress(written, written)
        Saved(written, plan.webm)
    }

    private suspend fun saveTrack(t: Track, f: File, lowEnd: Boolean, progress: (Long) -> Unit) {
        // A single-file track is a plain file: multi-connection download.
        if (t.parts.size == 1 && t.parts[0].length < 0) {
            Segmented(client, ForceGrab.connections(lowEnd)).download(t.parts[0].url, headers, f) { d, _ -> progress(d) }
            return
        }
        val state = File(f.parentFile, f.name + ".idx")
        val all = listOfNotNull(t.init) + t.parts
        var done = if (f.exists() && state.exists()) state.readText().trim().toIntOrNull() ?: 0 else 0
        val lengths = File(f.parentFile, f.name + ".len")
        if (done > 0) {
            // Cut a half-written segment before resuming.
            lengths.takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull()?.let { RandomAccessFile(f, "rw").use { raf -> raf.setLength(it) } }
        } else f.delete()
        var bytes = if (done > 0) f.length() else 0L
        FileOutputStream(f, done > 0).use { fos ->
            for (i in done until all.size) {
                currentCoroutineContext().ensureActive()
                val data = fetchRetry(all[i])
                fos.write(data); fos.flush(); bytes += data.size
                done = i + 1
                lengths.writeText(bytes.toString()); state.writeText(done.toString())
                progress(bytes)
            }
        }
        state.delete(); lengths.delete()
    }

    private suspend fun fetchRetry(p: Part): ByteArray {
        var last: IOException? = null
        for (attempt in 0 until 4) {
            val h = ForceHeaders.forAttempt(headers, if (attempt >= 2) attempt - 1 else 0, if (attempt >= 2) ForceHeaders.cookiesFor(p.url) else null)
            try { return bytes(p, h) } catch (e: IOException) { last = e; delay(600L * (attempt + 1)) }
        }
        throw last ?: IOException("Segment failed")
    }

    private fun bytes(p: Part, h: Map<String, String>): ByteArray {
        val b = Request.Builder().url(p.url)
        h.forEach { (k, v) -> runCatching { b.header(k, v) } }
        if (p.length > 0) b.header("Range", "bytes=${p.start}-${p.start + p.length - 1}")
        else if (p.start > 0) b.header("Range", "bytes=${p.start}-")
        client.newCall(b.build()).execute().use { r ->
            if (!r.isSuccessful) throw HttpStatus(r.code)
            return r.body.bytes()
        }
    }

    private fun text(url: String): String {
        val b = Request.Builder().url(url)
        headers.forEach { (k, v) -> runCatching { b.header(k, v) } }
        client.newCall(b.build()).execute().use { r ->
            if (!r.isSuccessful) throw HttpStatus(r.code)
            return r.body.string()
        }
    }
}
