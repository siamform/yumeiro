package dev.asfakul.yumeiro

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * R16 (G8): real downloads through WorkManager and DownloadWorker against a local server.
 * a) An HLS download paused after segment 8 (resume point saved at 5) resumes into a file with
 *    every segment exactly once (it used to repeat segments 6–8).
 * b) A .part that already holds the whole file finishes (416 with the same size) instead of failing.
 * c) DL-4: a server that answers the resume Range with a 206 from byte 0 (another offset) must not
 *    be appended to the .part (that gave a file of .part + whole file); the download starts over.
 * d) DL-4: a 416 whose size is not the .part size (the file changed on the server) is not "done";
 *    it used to finish with the old, wrong bytes. The download starts over with the new file.
 */
@RunWith(AndroidJUnit4::class)
class DownloadResumeDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private lateinit var server: MockWebServer
    private val segCount = 12
    private fun seg(i: Int) = ByteArray(4096) { (i * 7 + it % 13).toByte() }
    private val blob = ByteArray(300_000) { (it % 251).toByte() }
    @Volatile private var hold: CountDownLatch? = null
    @Volatile private var blockAt = -1
    @Volatile private var blobDown = false
    private val blob2 = ByteArray(200_000) { (it % 241 + 3).toByte() }

    private fun oneByte(total: Int, b: Byte) = MockResponse.Builder().code(206)
        .addHeader("Content-Range", "bytes 0-0/$total").body(Buffer().writeByte(b.toInt())).build()

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                return when {
                    path.endsWith("/v.m3u8") -> MockResponse.Builder().addHeader("Content-Type", "application/vnd.apple.mpegurl").body(
                        buildString {
                            append("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:2\n")
                            for (i in 0 until segCount) append("#EXTINF:2.0,\ns$i.ts\n")
                            append("#EXT-X-ENDLIST\n")
                        },
                    ).build()
                    path.contains("/s") && path.endsWith(".ts") -> {
                        val i = path.substringAfterLast("/s").removeSuffix(".ts").toInt()
                        if (i == blockAt) hold?.await(30, TimeUnit.SECONDS)
                        MockResponse.Builder().body(Buffer().write(seg(i))).build()
                    }
                    path.endsWith("/blob.mp4") -> {
                        if (blobDown) return MockResponse.Builder().code(503).build()
                        val range = request.headers["Range"]
                        if (range != null && range.startsWith("bytes=${blob.size}-"))
                            MockResponse.Builder().code(416).addHeader("Content-Range", "bytes */${blob.size}").build()
                        else MockResponse.Builder().addHeader("Content-Type", "video/mp4").body(Buffer().write(blob)).build()
                    }
                    // DL-4 c: every resume Range is answered from byte 0 (a 206 at the wrong offset).
                    path.endsWith("/shift.mp4") -> {
                        if (blobDown) return MockResponse.Builder().code(503).build()
                        val range = request.headers["Range"]
                        when {
                            range == "bytes=0-0" -> oneByte(blob.size, blob[0])
                            range != null -> MockResponse.Builder().code(206).addHeader("Content-Range", "bytes 0-${blob.size - 1}/${blob.size}")
                                .addHeader("Content-Type", "video/mp4").body(Buffer().write(blob)).build()
                            else -> MockResponse.Builder().addHeader("Content-Type", "video/mp4").body(Buffer().write(blob)).build()
                        }
                    }
                    // DL-4 d: the file is now blob2 (200 000 bytes); a resume past its end gets 416 with the new size.
                    path.endsWith("/changed.mp4") -> {
                        if (blobDown) return MockResponse.Builder().code(503).build()
                        val range = request.headers["Range"]
                        val from = range?.removePrefix("bytes=")?.substringBefore('-')?.toIntOrNull()
                        when {
                            range == "bytes=0-0" -> oneByte(blob2.size, blob2[0])
                            from != null && from >= blob2.size -> MockResponse.Builder().code(416).addHeader("Content-Range", "bytes */${blob2.size}").build()
                            from != null && from > 0 -> MockResponse.Builder().code(206).addHeader("Content-Range", "bytes $from-${blob2.size - 1}/${blob2.size}")
                                .body(Buffer().write(blob2.copyOfRange(from, blob2.size))).build()
                            else -> MockResponse.Builder().addHeader("Content-Type", "video/mp4").body(Buffer().write(blob2)).build()
                        }
                    }
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
        OnlineDownloads.ensureLoaded(ctx)
    }

    @After fun tearDown() { hold?.countDown(); runCatching { server.close() } }

    private fun link(url: String, hls: Boolean) = OnlineLink("Test", "test", url, null, 720, hls, false, emptyMap())

    private suspend fun waitFor(id: String, ms: Long, what: String, ok: (OnlineDownloads.Item) -> Boolean): OnlineDownloads.Item {
        val until = System.currentTimeMillis() + ms
        var lastLog = 0L
        while (true) {
            val it = OnlineDownloads.get(id)
            if (it != null && ok(it)) return it
            val now = System.currentTimeMillis()
            if (now - lastLog > 5000) { lastLog = now; Log.i("YuDownloadTest", "waiting for $what: status=${it?.status} bytes=${it?.bytes} error=${it?.error} items=${OnlineDownloads.items.value.size}") }
            if (now > until) throw AssertionError("timed out waiting for $what: status=${it?.status} bytes=${it?.bytes} error=${it?.error} found=${it != null}")
            delay(200)
        }
    }

    @Test fun a_pausedHlsDownloadResumesWithoutRepeatedSegments() = runBlocking<Unit> {
        val title = "YuDlTest HLS ${System.currentTimeMillis()}"
        hold = CountDownLatch(1); blockAt = 8
        val err = OnlineDownloads.enqueue(ctx, title, "yu-test", "t-hls-$title", "E1", null, emptyMap(), link(server.url("/v.m3u8").toString(), true))
        assertEquals(null, err)
        val id = OnlineDownloads.items.value.first { it.title == title }.id
        // Wait until segments 0–7 are written (segment 8 is held), then pause like the user does.
        waitFor(id, 60_000, "a running") { it.status == Status.RUNNING && it.bytes >= 8L * 4096 }
        OnlineDownloads.pause(ctx, id)
        hold?.countDown()
        delay(1500)
        assertEquals(Status.PAUSED, OnlineDownloads.get(id)?.status)
        blockAt = -1
        OnlineDownloads.start(ctx, id)
        val done = waitFor(id, 90_000, "a done") { it.status == Status.DONE || it.status == Status.FAILED }
        val file = OnlineDownloads.fileOf(ctx, done)
        val expected = (0 until segCount).fold(ByteArray(0)) { acc, i -> acc + seg(i) }
        val line = "DL_A status=${done.status} size=${file.length()} expected=${expected.size} error=${done.error}"
        Log.i("YuDownloadTest", line)
        assertEquals(line, Status.DONE, done.status)
        assertArrayEquals(line, expected, file.readBytes())
        Log.i("YuDownloadTest", "DL_A_RESULT PASS")
        OnlineDownloads.delete(ctx, id)
    }

    @Test fun b_aCompletePartFileFinishes() = runBlocking<Unit> {
        val title = "YuDlTest Blob ${System.currentTimeMillis()}"
        val url = server.url("/blob.mp4").toString()
        // First attempt fails (503) so nothing is saved; then a complete .part is put in place, as
        // after a kill between the last byte and the rename.
        blobDown = true
        OnlineDownloads.enqueue(ctx, title, "yu-test", "t-blob-$title", "E1", null, emptyMap(), link(url, false))
        val probeId = OnlineDownloads.items.value.first { it.title == title }.id
        val failed = waitFor(probeId, 60_000, "b failed") { it.status == Status.FAILED }
        val out = OnlineDownloads.fileOf(ctx, failed)
        out.delete()
        File(out.parentFile, out.name + ".part").writeBytes(blob)
        blobDown = false
        OnlineDownloads.start(ctx, probeId)
        val done = waitFor(probeId, 60_000, "b done") { it.status == Status.DONE || it.status == Status.FAILED }
        val line = "DL_B status=${done.status} size=${OnlineDownloads.fileOf(ctx, done).length()} expected=${blob.size} error=${done.error}"
        Log.i("YuDownloadTest", line)
        assertEquals(line, Status.DONE, done.status)
        assertArrayEquals(line, blob, OnlineDownloads.fileOf(ctx, done).readBytes())
        Log.i("YuDownloadTest", "DL_B_RESULT PASS")
        OnlineDownloads.delete(ctx, probeId)
    }

    /** Fails a first attempt (503) so the item has its file name, puts [part] in place, then resumes. */
    private suspend fun resumeWith(tag: String, path: String, part: ByteArray): Pair<OnlineDownloads.Item, ByteArray> {
        val title = "YuDlTest $tag ${System.currentTimeMillis()}"
        blobDown = true
        OnlineDownloads.enqueue(ctx, title, "yu-test", "t-$tag-$title", "E1", null, emptyMap(), link(server.url(path).toString(), false))
        val id = OnlineDownloads.items.value.first { it.title == title }.id
        val failed = waitFor(id, 60_000, "$tag failed") { it.status == Status.FAILED }
        val out = OnlineDownloads.fileOf(ctx, failed)
        out.delete()
        File(out.parentFile, out.name + ".part").writeBytes(part)
        blobDown = false
        OnlineDownloads.start(ctx, id)
        val done = waitFor(id, 60_000, "$tag done") { it.status == Status.DONE || it.status == Status.FAILED }
        val bytes = OnlineDownloads.fileOf(ctx, done).let { if (it.exists()) it.readBytes() else ByteArray(0) }
        OnlineDownloads.delete(ctx, id)
        return done to bytes
    }

    @Test fun c_aRangeAnswerAtAnotherOffsetStartsOver() = runBlocking<Unit> {
        val (done, bytes) = resumeWith("shift", "/shift.mp4", blob.copyOfRange(0, 100_000))
        val line = "DL_C status=${done.status} size=${bytes.size} expected=${blob.size} error=${done.error}"
        Log.i("YuDownloadTest", line)
        assertEquals(line, Status.DONE, done.status)
        assertArrayEquals(line, blob, bytes)
        Log.i("YuDownloadTest", "DL_C_RESULT PASS")
    }

    @Test fun d_a416WithAnotherSizeStartsOver() = runBlocking<Unit> {
        val (done, bytes) = resumeWith("changed", "/changed.mp4", blob.copyOfRange(0, 250_000))
        val line = "DL_D status=${done.status} size=${bytes.size} expected=${blob2.size} error=${done.error}"
        Log.i("YuDownloadTest", line)
        assertEquals(line, Status.DONE, done.status)
        assertArrayEquals(line, blob2, bytes)
        Log.i("YuDownloadTest", "DL_D_RESULT PASS")
    }
}
