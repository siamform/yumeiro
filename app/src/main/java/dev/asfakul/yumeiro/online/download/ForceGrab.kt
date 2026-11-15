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
class Segmented(
    private val client: OkHttpClient,
    private val connections: Int,
    /** Called with the bytes still to fetch once the size is known (throw to stop, e.g. disk full). */
    private val checkSpace: (Long) -> Unit = {},
    private val segmentFrom: Long = ForceGrab.SEGMENT_FROM,
    private val chunk: Long = ForceGrab.CHUNK,
) {
    class Refused(val code: Int) : IOException("HTTP $code")

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
        val probe = probe(url, headers)
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
        coroutineScope {
            (0 until connections).map {
                async {
                    while (true) {
                        val i = lock.withLock { todo.removeFirstOrNull() } ?: break
                        val r = ranges[i]
                        var tries = 0
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            try {
                                fetchRange(url, headers, r, out) { n -> progress(have.addAndGet(n), total) }
                                break
                            } catch (e: Refused) { throw e } catch (e: IOException) {
                                if (++tries >= 4) throw e
                                delay(800L * tries)
                            }
                        }
                        lock.withLock { done += i; state.appendText("$i\n") }
                    }
                }
            }.awaitAll()
        }
        state.delete()
        return total
    }

    /** Total size when the host serves byte ranges, else null. */
    private fun probe(url: String, headers: Map<String, String>): Long? {
        val b = Request.Builder().url(url).header("Range", "bytes=0-0")
        headers.forEach { (k, v) -> runCatching { b.header(k, v) } }
        client.newCall(b.build()).execute().use { r ->
            if (r.code == 401 || r.code == 403) throw Refused(r.code)
            if (r.code != 206) return null
            return ForceGrab.totalFromContentRange(r.header("Content-Range"))
        }
    }

    private fun fetchRange(url: String, headers: Map<String, String>, r: LongRange, out: File, read: (Long) -> Unit) {
        val b = Request.Builder().url(url).header("Range", "bytes=${r.first}-${r.last}")
        headers.forEach { (k, v) -> runCatching { b.header(k, v) } }
        client.newCall(b.build()).execute().use { resp ->
            if (resp.code == 401 || resp.code == 403) throw Refused(resp.code)
            if (resp.code != 206) throw IOException("HTTP ${resp.code} for a byte range")
            val want = r.last - r.first + 1
            var got = 0L
            RandomAccessFile(out, "rw").use { raf ->
                raf.seek(r.first)
                resp.body.byteStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (got < want) {
                        val n = input.read(buf, 0, minOf(buf.size.toLong(), want - got).toInt()); if (n < 0) break
                        raf.write(buf, 0, n); got += n; read(n.toLong())
                    }
                }
            }
            if (got < want) { read(-got); throw IOException("Connection closed early") }
        }
    }

    /** One connection, resumable with Range when the host allows it. */
    private suspend fun single(url: String, headers: Map<String, String>, out: File, progress: (Long, Long) -> Unit): Long {
        val have = if (out.exists()) out.length() else 0L
        val b = Request.Builder().url(url)
        headers.forEach { (k, v) -> runCatching { b.header(k, v) } }
        if (have > 0) b.header("Range", "bytes=$have-")
        client.newCall(b.build()).execute().use { r ->
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
