/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.OnlineLink
import dev.asfakul.yumeiro.online.download.OnlineDownloads
import dev.asfakul.yumeiro.online.download.OnlineDownloads.Status
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G10 low storage, through the real download path (WorkManager → DownloadWorker → Segmented):
 *  a) the server reports a file bigger than the free space → the up-front check fails it at once
 *     with "Storage full — needs about … MB more, … MB free …", no retry, nothing written;
 *  b) the size is unknown (chunked, no ranges) and the disk really fills while writing (a shell
 *     `fallocate` file leaves ~16 MB) → ENOSPC is shown as "Storage full …", not "Network error".
 * The app must not crash in either case. The fill file is always removed.
 */
@RunWith(AndroidJUnit4::class)
class LowStorageDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val fill = "/data/local/tmp/yu-lowstorage.fill"
    private val started = ArrayList<String>()
    private val servers = ArrayList<MockWebServer>()
    private fun log(s: String) = Log.i("YuLowStorage", s)

    @After fun tearDown() {
        FirstRun.shell("rm -f $fill")
        started.forEach { runCatching { OnlineDownloads.delete(ctx, it) } }
        servers.forEach { runCatching { it.close() } }
    }

    private fun run(case: String, url: String): OnlineDownloads.Item? = runBlocking {
        OnlineDownloads.ensureLoaded(ctx)
        val title = "YuLowStorage $case ${System.currentTimeMillis()}"
        assertEquals(null, OnlineDownloads.enqueue(ctx, title, "yu-test-lowst", "t-lowst-$title", "E1", null, emptyMap(),
            OnlineLink("Test", "test", url, null, 720, false, false, emptyMap())))
        val id = OnlineDownloads.items.value.first { it.title == title }.id
        started += id
        val until = System.currentTimeMillis() + 120_000
        var item = OnlineDownloads.get(id)
        while (System.currentTimeMillis() < until) {
            item = OnlineDownloads.get(id)
            if (item?.status == Status.DONE || item?.status == Status.FAILED) break
            delay(200)
        }
        log("LOWST_CASE $case status=${item?.status} bytes=${item?.bytes} error='${item?.error}' free=${OnlineDownloads.freeBytes(ctx) / 1048576}MB")
        item
    }

    @Test fun a_tooBigUpFront() {
        val free = OnlineDownloads.freeBytes(ctx)
        assumeTrue("free space unknown", free > 0)
        val total = free + 1024L * 1024 * 1024
        val server = MockWebServer().also { servers += it }
        server.dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: mockwebserver3.RecordedRequest): MockResponse {
                val range = request.headers["Range"]
                // Only the probe and the first bytes: the check must stop it before any real read.
                return if (range != null) MockResponse.Builder().code(206)
                    .setHeader("Content-Range", "bytes 0-0/$total").setHeader("Accept-Ranges", "bytes").body("x").build()
                else MockResponse.Builder().code(200).body("x").build()
            }
        }
        server.start()
        val item = run("a-upfront", server.url("/big.mp4").toString())
        val err = item?.error.orEmpty()
        val ok = item?.status == Status.FAILED && err.startsWith("Storage full") && "MB free" in err && (item?.bytes ?: 0L) < 1024 * 1024
        log("LOWST_RESULT a ${if (ok) "PASS" else "FAIL"} requests=${server.requestCount}")
        assertTrue("up-front check: status=${item?.status} error='$err' bytes=${item?.bytes}", ok)
    }

    @Test fun b_fillsWhileWriting() {
        val before = OnlineDownloads.freeBytes(ctx)
        assumeTrue("free space unknown", before > 0)
        val keep = 16L * 1024 * 1024
        // UiAutomation.executeShellCommand runs no shell: "; echo rc=$?" went to fallocate as extra
        // arguments, so nothing was filled (run 43891331959, both phones, case skipped). Plain command,
        // then the file size as the proof.
        val fillOut = FirstRun.shell("fallocate -l ${before - keep} $fill")
        val out = fillOut + " · " + FirstRun.shell("ls -l $fill")
        val free = OnlineDownloads.freeBytes(ctx)
        log("LOWST_FILL before=${before / 1048576}MB after=${free / 1048576}MB shell='${out.trim()}'")
        assumeTrue("could not fill the disk (fallocate: ${out.trim()})", free in 0..(48L * 1024 * 1024))
        val server = MockWebServer().also { servers += it }
        // No length, no ranges: nothing to check up front; the write has to hit ENOSPC.
        val body = okio.Buffer().write(ByteArray(40 * 1024 * 1024))
        server.dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: mockwebserver3.RecordedRequest): MockResponse =
                MockResponse.Builder().code(200).chunkedBody(body.clone(), 256 * 1024).build()
        }
        server.start()
        val item = run("b-enospc", server.url("/stream.mp4").toString())
        FirstRun.shell("rm -f $fill")
        val err = item?.error.orEmpty()
        val alive = FirstRun.shell("pidof ${ctx.packageName}").trim().isNotEmpty()
        val ok = item?.status == Status.FAILED && err.startsWith("Storage full") && alive
        log("LOWST_RESULT b ${if (ok) "PASS" else "FAIL"} alive=$alive requests=${server.requestCount}")
        assertTrue("ENOSPC while writing: status=${item?.status} error='$err' alive=$alive", ok)
    }
}
