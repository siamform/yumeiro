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
    private val hosts = listOf(
        "https://ash-speed.hetzner.com/100MB.bin", "https://proof.ovh.net/files/100Mb.dat",
        "https://fsn1-speed.hetzner.com/100MB.bin", "https://nbg1-speed.hetzner.com/100MB.bin", "https://hel1-speed.hetzner.com/100MB.bin",
    )
    private val started = ArrayList<String>()

    private fun log(s: String) = Log.i("YuDownloadTest", s)

    @After fun tearDown() {
        FirstRun.shell("dumpsys deviceidle unforce")
        FirstRun.shell("input keyevent 224") // wake
        started.forEach { runCatching { OnlineDownloads.delete(ctx, it) } }
    }

    /** Probes [u] with a plain OkHttp client (not the app's): its size when it answers a byte range. */
    private fun probe(u: String): Long? {
        val c = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
        val r = runCatching {
            c.newCall(Request.Builder().url(u).header("Range", "bytes=0-0").build()).execute().use { resp ->
                resp.header("Content-Range")?.substringAfter('/')?.toLongOrNull()?.takeIf { resp.code == 206 }
            }
        }
        log("THROUGHPUT_DEVICE probe $u -> ${r.getOrNull() ?: r.exceptionOrNull()?.toString() ?: "no 206"}")
        return r.getOrNull()
    }

    /**
     * Tries the hosts in order. A host the plain probe cannot reach is skipped (runner network). If
     * the app's download fails before any byte while the plain probe still reaches the host, that is
     * an app failure, not the network (run 43855202294: both the IPv6 and IPv4 address of
     * proof.ovh.net timed out for the app and, seconds later, for the probe too).
     */
    private fun measure(case: String, screenOffAt: Int?) {
        for (u in hosts) {
            val size = probe(u) ?: continue
            if (measureOn(case, screenOffAt, u, size)) return
            assertTrue("the app could not connect to $u but a plain client can (app network bug)", probe(u) == null)
            log("THROUGHPUT_DEVICE $case: $u became unreachable for a plain client too; next host")
        }
        log("THROUGHPUT_DEVICE_RESULT SKIP case=$case no test host reachable from this runner")
        assumeTrue("no test host", false)
    }

    /** False only when the download failed before its first byte (connection). */
    private fun measureOn(case: String, screenOffAt: Int?, url: String, size: Long): Boolean = runBlocking {
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
        if (item?.status == Status.FAILED && item.bytes == 0L) return@runBlocking false
        assertEquals(line, Status.DONE, item?.status)
        assertEquals(line, size, OnlineDownloads.fileOf(ctx, item!!).length())
        val pass = ratio in 0.0..2.0
        log("THROUGHPUT_DEVICE_RESULT ${if (pass) "PASS" else "FAIL"} case=$case lastOverMean=${"%.2f".format(ratio)}")
        assertTrue("BUG-3 tail rule (≤ 2×): $line", pass)
        true
    }

    @Test fun a_realHostScreenOn() = measure("screen-on", null)

    @Test fun b_realHostScreenOffAndDoze() = measure("screen-off-doze", 30)
}
