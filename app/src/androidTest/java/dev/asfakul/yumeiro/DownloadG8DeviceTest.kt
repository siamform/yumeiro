/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.asfakul.yumeiro.online.OnlineLink
import dev.asfakul.yumeiro.online.download.OnlineDownloads
import dev.asfakul.yumeiro.online.download.OnlineDownloads.Status
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

/**
 * G8 group batch (§13a), the findings that need the real worker, MediaMuxer or the phone's disk
 * (docs/groups/GROUP-8-DOWNLOADS.md):
 *  a) G8-2: an HLS variant whose audio is a separate rendition (EXT-X-MEDIA TYPE=AUDIO) must be saved
 *     with its audio track (it was saved silent: video only);
 *  b) G8-6: an fMP4 HLS stream (EXT-X-MAP) must be saved as .mp4 (it was named .ts and published as
 *     video/mp2t);
 *  c) G8-7: a split video + audio download that fits track by track but not with the join must stop
 *     up front with "Storage full" (it downloaded both tracks and failed or retried at the join).
 *  d) G8-8: Retry tapped while the failed download's worker is still finishing (the item already says
 *     FAILED, the unique work dl_<id> is still RUNNING) must still download. ExistingWorkPolicy.KEEP
 *     dropped the new request and the item stayed "Queued" with no worker (tv-api34 44299928223).
 * Real WorkManager → DownloadWorker path, local MockWebServer. Markers: G8D_RESULT <case> PASS|FAIL.
 */
@RunWith(AndroidJUnit4::class)
class DownloadG8DeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets
    private val started = ArrayList<String>()
    private val servers = ArrayList<MockWebServer>()
    private fun log(s: String) = Log.i("YuG8Download", s)

    @After fun tearDown() {
        started.forEach { runCatching { OnlineDownloads.delete(ctx, it) } }
        servers.forEach { runCatching { it.close() } }
    }

    private fun asset(name: String) = assets.open(name).use { it.readBytes() }

    private fun server(route: (RecordedRequest, String) -> MockResponse): MockWebServer {
        val s = MockWebServer().also { servers += it }
        s.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = route(request, request.url.encodedPath)
        }
        s.start()
        return s
    }

    private fun body(b: ByteArray) = MockResponse.Builder().body(Buffer().write(b)).build()
    private fun text(s: String) = MockResponse.Builder().body(s).build()

    private fun run(case: String, link: OnlineLink, ms: Long = 120_000): OnlineDownloads.Item? = runBlocking {
        OnlineDownloads.ensureLoaded(ctx)
        val title = "YuG8 $case ${System.currentTimeMillis()}"
        val err = OnlineDownloads.enqueue(ctx, title, "yu-test-g8", "t-g8-$title", "E1", null, emptyMap(), link)
        assertTrue("enqueue: $err", err == null)
        val id = OnlineDownloads.items.value.first { it.title == title }.id
        started += id
        val until = System.currentTimeMillis() + ms
        var item = OnlineDownloads.get(id)
        while (System.currentTimeMillis() < until) {
            item = OnlineDownloads.get(id)
            if (item?.status == Status.DONE || item?.status == Status.FAILED) break
            delay(200)
        }
        log("G8D_CASE $case status=${item?.status} file=${item?.fileName} bytes=${item?.bytes} error='${item?.error}'")
        item
    }

    private fun tracks(path: String): List<String> {
        val ex = MediaExtractor()
        return try {
            ex.setDataSource(path)
            (0 until ex.trackCount).mapNotNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME) }
        } catch (e: Exception) { listOf("error:${e.message}") } finally { ex.release() }
    }

    @Test fun a_hlsAudioRenditionIsSavedWithTheVideo() {
        val video = asset("yt_video_only.mp4"); val audio = asset("yt_audio_only.m4a")
        val master = """#EXTM3U
#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",NAME="English",LANGUAGE="en",DEFAULT=YES,AUTOSELECT=YES,URI="audio/index.m3u8"
#EXT-X-STREAM-INF:BANDWIDTH=900000,RESOLUTION=640x360,AUDIO="aud"
video/index.m3u8
"""
        fun pl(seg: String) = "#EXTM3U\n#EXT-X-TARGETDURATION:10\n#EXTINF:10.0,\n$seg\n#EXT-X-ENDLIST\n"
        val s = server { _, path ->
            when (path) {
                "/master.m3u8" -> text(master)
                "/video/index.m3u8" -> text(pl("v.mp4"))
                "/audio/index.m3u8" -> text(pl("a.m4a"))
                "/video/v.mp4" -> body(video)
                "/audio/a.m4a" -> body(audio)
                else -> MockResponse.Builder().code(404).build()
            }
        }
        val item = run("a-rendition", OnlineLink("Test", "test", s.url("/master.m3u8").toString(), null, 360, true, false, emptyMap()))
        val file = item?.let { OnlineDownloads.fileOf(ctx, it) }
        val mimes = file?.takeIf { it.exists() }?.let { tracks(it.path) }.orEmpty()
        val ok = item?.status == Status.DONE && mimes.any { it.startsWith("video/") } && mimes.any { it.startsWith("audio/") }
        log("G8D_RESULT a ${if (ok) "PASS" else "FAIL"} tracks=$mimes file=${item?.fileName}")
        assertTrue("G8-2: status=${item?.status} error='${item?.error}' tracks=$mimes (needs video + audio)", ok)
    }

    @Test fun b_fmp4HlsIsSavedAsMp4() {
        val video = asset("yt_video_only.mp4")
        val media = "#EXTM3U\n#EXT-X-VERSION:7\n#EXT-X-TARGETDURATION:10\n#EXT-X-MAP:URI=\"init.mp4\"\n#EXTINF:10.0,\nseg0.m4s\n#EXT-X-ENDLIST\n"
        val s = server { _, path ->
            when (path) {
                "/f.m3u8" -> text(media)
                "/init.mp4" -> body(video) // the whole small file as the init section, then an empty segment
                "/seg0.m4s" -> body(ByteArray(0))
                else -> MockResponse.Builder().code(404).build()
            }
        }
        val item = run("b-fmp4", OnlineLink("Test", "test", s.url("/f.m3u8").toString(), null, 360, true, false, emptyMap()))
        val file = item?.let { OnlineDownloads.fileOf(ctx, it) }
        val mimes = file?.takeIf { it.exists() }?.let { tracks(it.path) }.orEmpty()
        val ok = item?.status == Status.DONE && item?.fileName?.endsWith(".mp4") == true && file?.exists() == true && mimes.any { it.startsWith("video/") }
        log("G8D_RESULT b ${if (ok) "PASS" else "FAIL"} file=${item?.fileName} tracks=$mimes")
        assertTrue("G8-6: status=${item?.status} file=${item?.fileName} tracks=$mimes (an fMP4 stream must be .mp4)", ok)
    }

    @Test fun c_splitTracksCountTheJoinInTheSpaceCheck() {
        val free = OnlineDownloads.freeBytes(ctx)
        assumeTrue("free space unknown", free > 0)
        // Each track alone fits (free - 64 MB margin); both tracks plus the joined file (about the same
        // size again) do not. Only probes are answered: the check must stop it before any real read.
        val each = free * 45 / 100
        assumeTrue("too little free space for the case (${free / 1048576} MB)", each > 256L * 1024 * 1024)
        val s = server { req, _ ->
            if (req.headers["Range"] != null) MockResponse.Builder().code(206)
                .setHeader("Content-Range", "bytes 0-0/$each").setHeader("Accept-Ranges", "bytes").body("x").build()
            else MockResponse.Builder().code(200).body("x").build()
        }
        val link = OnlineLink("Test", "test", s.url("/v.mp4").toString(), null, 720, false, false, emptyMap(),
            audioTracks = listOf(s.url("/a.m4a").toString() to emptyMap()))
        val item = run("c-split-space", link)
        val err = item?.error.orEmpty()
        val ok = item?.status == Status.FAILED && err.startsWith("Storage full") && (item?.bytes ?: 0L) < 1024 * 1024
        log("G8D_RESULT c ${if (ok) "PASS" else "FAIL"} free=${free / 1048576}MB each=${each / 1048576}MB requests=${s.requestCount}")
        assertTrue("G8-7: status=${item?.status} error='$err' bytes=${item?.bytes} (each track ${each / 1048576} MB, free ${free / 1048576} MB)", ok)
    }

    @Test fun d_retryWhileTheFailedWorkIsStillRunningStarts() = runBlocking<Unit> {
        val up = AtomicBoolean(false)
        val payload = ByteArray(64 * 1024) { (it % 251).toByte() }
        val s = server { _, _ -> if (up.get()) body(payload) else MockResponse.Builder().code(503).build() }
        val first = run("d-fail", OnlineLink("Test", "test", s.url("/d.mp4").toString(), null, 720, false, false, emptyMap()))
        assertTrue("first attempt must fail: ${first?.status} '${first?.error}'", first?.status == Status.FAILED)
        val id = first!!.id
        // The window between DownloadWorker's failed() and its Result.failure(): the item says FAILED,
        // the unique work is still RUNNING. A test worker holds that state for 4 s.
        val wm = WorkManager.getInstance(ctx)
        // The first run must be over in WorkManager too, or KEEP drops the hold worker itself (the G8-8
        // window: api33-lowram 44302259076 enqueued it 0.2 s before WorkManager recorded FAILURE).
        val doneUntil = System.currentTimeMillis() + 15_000
        while (!wm.getWorkInfosForUniqueWork("dl_$id").get().all { it.state.isFinished } && System.currentTimeMillis() < doneUntil) delay(50)
        val hold = OneTimeWorkRequestBuilder<G8HoldWorker>().build()
        wm.enqueueUniqueWork("dl_$id", ExistingWorkPolicy.KEEP, hold).result.get()
        val runUntil = System.currentTimeMillis() + 15_000
        while (wm.getWorkInfoById(hold.id).get()?.state != WorkInfo.State.RUNNING && System.currentTimeMillis() < runUntil) delay(50)
        assertTrue("hold worker never ran", wm.getWorkInfoById(hold.id).get()?.state == WorkInfo.State.RUNNING)
        up.set(true)
        OnlineDownloads.start(ctx, id) // the user's Retry
        val until = System.currentTimeMillis() + 40_000
        var item = OnlineDownloads.get(id)
        while (System.currentTimeMillis() < until) {
            item = OnlineDownloads.get(id)
            if (item?.status == Status.DONE || item?.status == Status.FAILED) break
            delay(200)
        }
        val file = item?.let { OnlineDownloads.fileOf(ctx, it) }
        val ok = item?.status == Status.DONE && file?.exists() == true && file!!.readBytes().contentEquals(payload)
        log("G8D_RESULT d ${if (ok) "PASS" else "FAIL"} status=${item?.status} error='${item?.error}' requests=${s.requestCount}")
        assertTrue("G8-8: retry during the failing worker's last moment: status=${item?.status} error='${item?.error}'", ok)
    }
}

/** G8-8 test helper: occupies the unique work name like a worker that is about to return failure. */
class G8HoldWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result { delay(4_000); return Result.failure() }
}
