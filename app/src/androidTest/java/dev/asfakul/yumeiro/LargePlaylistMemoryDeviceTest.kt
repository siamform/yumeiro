/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.stream.M3uParser
import dev.asfakul.yumeiro.stream.PlaylistStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * G7 LT-3 on the device: a large saved playlist is parsed line by line (`PlaylistStore.channels`).
 * The old path read the whole file into one String first. Both paths parse the same 20 MB playlist
 * on this phone; a sampler records the Java heap peak above the starting level. The line-by-line
 * path must give the same channels with a lower peak, and nothing may run out of memory on the
 * 192 MB-heap phone. Log tag YuBigM3u.
 */
@RunWith(AndroidJUnit4::class)
class LargePlaylistMemoryDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(s: String) = Log.i("YuBigM3u", s)
    private fun used(): Long { val r = Runtime.getRuntime(); return r.totalMemory() - r.freeMemory() }

    private fun <T> peakOf(block: () -> T): Pair<T, Long> {
        System.gc(); System.runFinalization(); System.gc(); Thread.sleep(300)
        val base = used()
        val peak = java.util.concurrent.atomic.AtomicLong(base)
        val run = java.util.concurrent.atomic.AtomicBoolean(true)
        val sampler = Thread { while (run.get()) { peak.accumulateAndGet(used()) { a, b -> maxOf(a, b) }; Thread.sleep(2) } }.apply { start() }
        val out = try { block() } finally { run.set(false); sampler.join() }
        return out to (peak.get() - base)
    }

    @Test fun bigPlaylistParsesLineByLine() = runBlocking {
        val p = PlaylistStore.createLocal(ctx, "YuBigM3u test")
        val file = File(File(ctx.filesDir, "streams"), "ch_${p.id.filter { it.isLetterOrDigit() || it == '_' || it == '-' }}.m3u")
        try {
            file.bufferedWriter().use { w ->
                w.write("#EXTM3U\n")
                var n = 0
                while (n < 52_000) {
                    w.write("#EXTINF:-1 tvg-id=\"ch$n.example\" tvg-name=\"Channel $n\" tvg-logo=\"https://logos.example.invalid/very/long/path/to/logo/$n/w_300,h_300/logo.png\" group-title=\"Group ${n % 40}\",Channel number $n with a fairly long display name\n")
                    w.write("https://streams.example.invalid/live/hls/$n/index.m3u8?token=abcdefghijklmnopqrstuvwxyz0123456789\n")
                    n++
                }
            }
            val mb = file.length() / 1_048_576.0
            val maxHeapMb = Runtime.getRuntime().maxMemory() / 1_048_576
            log("BIGM3U file=${"%.1f".format(mb)} MB maxHeap=${maxHeapMb} MB")
            // Old path first, then the new one, then both again; the lower peak of each counts.
            val (old1, oldPeak1) = peakOf { M3uParser.parse(file.readText()).size }
            val (new1, newPeak1) = peakOf { runBlocking { PlaylistStore.channels(ctx, p.id).size } }
            val (old2, oldPeak2) = peakOf { M3uParser.parse(file.readText()).size }
            val (new2, newPeak2) = peakOf { runBlocking { PlaylistStore.channels(ctx, p.id).size } }
            val oldPeak = minOf(oldPeak1, oldPeak2) / 1_048_576
            val newPeak = minOf(newPeak1, newPeak2) / 1_048_576
            log("BIGM3U channels old=$old1/$old2 new=$new1/$new2 peakMB old=${oldPeak1 / 1_048_576}/${oldPeak2 / 1_048_576} new=${newPeak1 / 1_048_576}/${newPeak2 / 1_048_576}")
            assertEquals(52_000, new1)
            assertEquals(old1, new1)
            assertTrue("LT-3: line-by-line peak ${newPeak} MB is not below the whole-file peak ${oldPeak} MB", newPeak < oldPeak)
            log("BIGM3U_RESULT PASS old=${oldPeak} MB new=${newPeak} MB")
        } finally {
            file.delete()
            runCatching { PlaylistStore.delete(ctx, p.id) }
        }
        Unit
    }
}
