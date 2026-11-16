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
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * BUG-3 on an emulator against a real host: the real download path (WorkManager → DownloadWorker →
 * ForceGrab.Segmented) saves a public 100 MB test file, and the time of every 10 % is logged
 * (`THROUGHPUT_DEVICE`). Case b turns the screen off and forces Doze at 30 % (the T5 screen-off
 * question; the emulator has no Wi-Fi power save, so a phone is still needed for that half).
 * Rule (the same as DownloadThroughputTest): the last 10 % takes at most 2× the average 10 %.
 */
@RunWith(AndroidJUnit4::class)
class DownloadThroughputDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val hosts = listOf("https://ash-speed.hetzner.com/100MB.bin", "https://proof.ovh.net/files/100Mb.dat")
    private val started = ArrayList<String>()

    private fun log(s: String) = Log.i("YuDownloadTest", s)

    @After fun tearDown() {
        FirstRun.shell("dumpsys deviceidle unforce")
        FirstRun.shell("input keyevent 224") // wake
        started.forEach { runCatching { OnlineDownloads.delete(ctx, it) } }
    }

    /** First host that answers a byte range here, with its size. */
    private fun host(): Pair<String, Long>? {
        val c = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
        return hosts.firstNotNullOfOrNull { u ->
            runCatching {
                c.newCall(Request.Builder().url(u).header("Range", "bytes=0-0").build()).execute().use { r ->
                    r.header("Content-Range")?.substringAfter('/')?.toLongOrNull()?.takeIf { r.code == 206 }?.let { u to it }
                }
            }.getOrNull()
        }
    }

    private fun measure(case: String, screenOffAt: Int?) = runBlocking<Unit> {
        val (url, size) = host() ?: run { log("THROUGHPUT_DEVICE_RESULT SKIP no test host reachable"); assumeTrue("no test host", false); return@runBlocking }
        OnlineDownloads.ensureLoaded(ctx)
        val title = "YuDlTest BUG-3 $case ${System.currentTimeMillis()}"
        val err = OnlineDownloads.enqueue(ctx, title, "yu-test-bug3", "t-bug3-$title", "E1", null, emptyMap(),
            OnlineLink("Test", "test", url, null, 720, false, false, emptyMap()))
        assertEquals(null, err)
        val id = OnlineDownloads.items.value.first { it.title == title }.id
        started += id
        val at = LongArray(10) { -1 }
        var t0 = 0L
        var off = false
        val until = System.currentTimeMillis() + 6 * 60_000
        var item: OnlineDownloads.Item?
        while (true) {
            item = OnlineDownloads.get(id)
            val now = System.currentTimeMillis()
            val bytes = item?.bytes ?: 0L
            if (t0 == 0L && bytes > 0) t0 = now
            if (t0 > 0) for (i in 0 until 10) if (at[i] < 0 && bytes >= size * (i + 1) / 10) at[i] = now - t0
            if (screenOffAt != null && !off && bytes >= size * screenOffAt / 100) {
                off = true
                FirstRun.shell("input keyevent 223") // screen off
                FirstRun.shell("dumpsys deviceidle force-idle")
                log("THROUGHPUT_DEVICE $case screen off + Doze forced at ${bytes * 100 / size} %: ${FirstRun.shell("dumpsys deviceidle get deep").trim()}")
            }
            if (item?.status == Status.DONE || item?.status == Status.FAILED) break
            if (now > until) break
            delay(100)
        }
        val steps = at.indices.map { i -> if (at[i] < 0) -1 else at[i] - (if (i == 0) 0 else at[i - 1]) }
        val total = at.last()
        val ratio = if (total > 0) steps.last() / (total / 10.0) else -1.0
        val mbps = if (total > 0) size / 1048576.0 / (total / 1000.0) else -1.0
        val line = "THROUGHPUT_DEVICE case=$case host=${url.substringAfter("//").substringBefore('/')} size=$size status=${item?.status} " +
            "error=${item?.error} totalMs=$total MBps=${"%.2f".format(mbps)} per10pctMs=$steps lastOverMean=${"%.2f".format(ratio)}"
        log(line)
        assertEquals(line, Status.DONE, item?.status)
        assertEquals(line, size, OnlineDownloads.fileOf(ctx, item!!).length())
        val pass = ratio in 0.0..2.0
        log("THROUGHPUT_DEVICE_RESULT ${if (pass) "PASS" else "FAIL"} case=$case lastOverMean=${"%.2f".format(ratio)}")
        assertTrue("BUG-3 tail rule (≤ 2×): $line", pass)
    }

    @Test fun a_realHostScreenOn() = measure("screen-on", null)

    @Test fun b_realHostScreenOffAndDoze() = measure("screen-off-doze", 30)
}
