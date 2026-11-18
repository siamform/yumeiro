/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.net.TrafficStats
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * G8 on an emulator against a real host (the real path: WorkManager → DownloadWorker → Segmented).
 * a) phase a: a download reaches 25 %, then the app process is killed (no clean stop).
 * b) phase b, a new process: the download continues by itself and finishes with the right size,
 *    reading clearly less than the whole file again (the done chunks are kept), and leaves no work file.
 * c) phase b: a download deleted at 10 % leaves no file of it in the folder (DL-7).
 * Phases are run by scripts/device-check.sh (like ResumeAcrossProcessDeviceTest).
 */
@RunWith(AndroidJUnit4::class)
class DownloadKillDeviceTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext.applicationContext
    private val phase = InstrumentationRegistry.getArguments().getString("phase", "")
    private val state get() = File(ctx.filesDir, "yu_kill_test.txt")
    private val hosts = listOf(
        "https://cdn.kernel.org/pub/linux/kernel/v6.x/linux-6.1.tar.xz",
        "https://mirrors.edge.kernel.org/pub/linux/kernel/v6.x/linux-6.6.tar.xz",
    )

    private fun log(s: String) = Log.i("YuDownloadTest", s)

    private fun probe(u: String): Long? = runCatching {
        OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
            .newCall(Request.Builder().url(u).header("Range", "bytes=0-0").build()).execute().use { r ->
                r.header("Content-Range")?.substringAfter('/')?.toLongOrNull()?.takeIf { r.code == 206 }
            }
    }.getOrNull()

    private fun host(): Pair<String, Long> {
        for (u in hosts) probe(u)?.let { return u to it }
        log("DL_KILL_RESULT SKIP no test host reachable from this runner")
        assumeTrue("no test host", false)
        error("unreachable")
    }

    private fun enqueue(tag: String, url: String): String {
        OnlineDownloads.ensureLoaded(ctx)
        val title = "YuDlTest G8 $tag ${System.currentTimeMillis()}"
        val err = OnlineDownloads.enqueue(ctx, title, "yu-test-g8", "t-g8-$title", "E1", null, emptyMap(),
            OnlineLink("Test", "test", url, null, 720, false, false, emptyMap()))
        assertEquals(null, err)
        return OnlineDownloads.items.value.first { it.title == title }.id
    }

    private suspend fun waitFor(id: String, ms: Long, what: String, ok: (OnlineDownloads.Item) -> Boolean): OnlineDownloads.Item {
        val until = System.currentTimeMillis() + ms
        var lastLog = 0L
        while (true) {
            val it = OnlineDownloads.get(id)
            if (it != null && ok(it)) return it
            val now = System.currentTimeMillis()
            if (now - lastLog > 5000) { lastLog = now; log("DL_KILL waiting for $what: status=${it?.status} bytes=${it?.bytes} error=${it?.error}") }
            if (now > until) throw AssertionError("timed out waiting for $what: status=${it?.status} bytes=${it?.bytes} error=${it?.error}")
            delay(100)
        }
    }

    /** Files in the download folder that belong to [item] (its name and every `name.` work file). */
    private fun filesOf(item: OnlineDownloads.Item): List<String> {
        val f = OnlineDownloads.fileOf(ctx, item)
        return f.parentFile?.listFiles()?.map { it.name }?.filter { it == f.name || it.startsWith(f.name + ".") }.orEmpty().sorted()
    }

    @Test fun a_startThenDie() = runBlocking<Unit> {
        assumeTrue("phase a runs from scripts/device-check.sh", phase == "a")
        val (url, size) = host()
        val id = enqueue("kill", url)
        val item = waitFor(id, 180_000, "25 %") { it.bytes >= size / 4 || it.status == Status.FAILED }
        assertTrue("failed before 25 %: ${item.error}", item.status != Status.FAILED)
        // G8 regression (tv-api24 44299923467): a fixed 1.5 s wait let a ~50 MB/s link finish the whole file
        // before the kill, so phase b had nothing to resume. Kill as soon as a finished chunk is on disk.
        val chunks = File(OnlineDownloads.fileOf(ctx, item).path + ".part.chunks")
        val until = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < until && chunks.length() == 0L &&
            OnlineDownloads.get(id)?.status != Status.DONE) delay(20)
        val bytes = OnlineDownloads.get(id)?.bytes ?: 0
        assertTrue("finished before the kill (bytes=$bytes of $size): the kill must land mid-download", bytes < size)
        state.writeText("$id\n$size\n$bytes\n")
        log("DL_KILL_A id=$id size=$size bytesAtKill=$bytes files=${filesOf(item)} pid=${android.os.Process.myPid()} — killing the process now")
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    @Test fun b_continuesAfterProcessDeath() = runBlocking<Unit> {
        assumeTrue("phase b runs from scripts/device-check.sh", phase == "b")
        assumeTrue("phase a left no state (skipped: no host)", state.exists())
        val (id, sizeS, killS) = state.readText().trim().lines()
        val size = sizeS.toLong(); val atKill = killS.toLong()
        state.delete()
        assertTrue("phase a killed after the end (atKill=$atKill of $size): nothing to resume", atKill < size)
        val rx0 = TrafficStats.getUidRxBytes(android.os.Process.myUid())
        OnlineDownloads.ensureLoaded(ctx)
        val start = OnlineDownloads.get(id)
        log("DL_KILL_B start status=${start?.status} bytes=${start?.bytes} atKill=$atKill")
        assertTrue("the download is gone after the kill", start != null)
        try {
            val done = waitFor(id, 6 * 60_000, "done after the kill") { it.status == Status.DONE || it.status == Status.FAILED }
            delay(500)
            val rx = TrafficStats.getUidRxBytes(android.os.Process.myUid()).let { if (it >= 0 && rx0 >= 0) it - rx0 else -1 }
            val file = OnlineDownloads.fileOf(ctx, done)
            val files = filesOf(done)
            val line = "DL_KILL_B status=${done.status} error=${done.error} size=${file.length()} expected=$size atKill=$atKill rxAfterKill=$rx files=$files"
            log(line)
            log("DL_KILL_B SEGMENTED_STATS ${dev.asfakul.yumeiro.online.download.ForceGrab.lastStats}")
            assertEquals(line, Status.DONE, done.status)
            assertEquals(line, size, file.length())
            assertEquals("work files left: $line", listOf(file.name), files)
            // The property: the chunks finished before the kill are kept, not read again. Only the
            // chunks in flight at the kill (one per connection, at most 4 × 4 MiB) may be lost.
            // DL-10/DL-11: this used to be a byte count (received < rest + 24 MiB), but the bytes
            // received also include what each RequestPace restart throws away, which varies from run
            // to run on a host that never caps (44037559115: 1 to 29 restarts, 1.04-1.44x the file);
            // 44033132233 failed that count while every finished chunk was kept. rx stays logged.
            val stats = dev.asfakul.yumeiro.online.download.ForceGrab.lastStats
            val resumed = Regex("resumedChunks=(\\d+)").find(stats)?.groupValues?.get(1)?.toLong()
            val chunk = dev.asfakul.yumeiro.online.download.ForceGrab.CHUNK
            assertTrue("no segmented stats: $stats", resumed != null)
            assertTrue("read the file again from the start (kept ${resumed!! * chunk} of $atKill): $line", resumed * chunk >= atKill - 4 * chunk)
            log("DL_KILL_B kept=${resumed * chunk} atKill=$atKill rxOverRest=${if (rx > 0) "%.3f".format(rx / (size - atKill).toDouble()) else "n/a"}")
            log("DL_KILL_RESULT PASS b rxAfterKill=$rx rest=${size - atKill}")
        } finally {
            runCatching { OnlineDownloads.delete(ctx, id) }
        }
    }

    @Test fun c_deleteMidDownloadLeavesNoFiles() = runBlocking<Unit> {
        assumeTrue("phase b runs from scripts/device-check.sh", phase == "b")
        val (url, size) = host()
        val id = enqueue("delete", url)
        val item = waitFor(id, 180_000, "10 %") { it.bytes >= size / 10 || it.status == Status.FAILED }
        assertTrue("failed before 10 %: ${item.error}", item.status != Status.FAILED)
        val during = filesOf(item)
        OnlineDownloads.delete(ctx, id)
        delay(8_000) // a connection that was mid-read finishes its piece; cleanup must follow it
        val after = filesOf(item)
        val line = "DL_KILL_C during=$during after=$after listed=${OnlineDownloads.get(id) != null}"
        log(line)
        assertTrue(line, after.isEmpty())
        assertTrue(line, OnlineDownloads.get(id) == null)
        log("DL_KILL_RESULT PASS c")
    }
}
