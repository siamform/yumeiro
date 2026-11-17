/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.stream.PlaylistStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * G7 LT-4 on the device: adding and refreshing a large remote playlist. The download path held the
 * whole body as bytes, then as one String, and parsed that String (the whole-file pattern LT-3
 * removed for saved playlists, which peaked at 183 MB of the 192 MB heap). A small local server
 * streams the same 16.3 MB playlist from a file, so the test itself holds no copy. Each step's
 * heap peak above its start must stay under half the heap, and no step may run out of memory.
 * Log tag YuRemoteM3u.
 */
@RunWith(AndroidJUnit4::class)
class RemotePlaylistMemoryDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(s: String) = Log.i("YuRemoteM3u", s)
    private fun used(): Long { val r = Runtime.getRuntime(); return r.totalMemory() - r.freeMemory() }

    private fun <T> peakOf(block: () -> T): Pair<Result<T>, Long> {
        System.gc(); System.runFinalization(); System.gc(); Thread.sleep(300)
        val base = used()
        val peak = AtomicLong(base)
        val run = AtomicBoolean(true)
        val sampler = Thread { while (run.get()) { peak.accumulateAndGet(used()) { a, b -> maxOf(a, b) }; Thread.sleep(2) } }.apply { start() }
        val out = try { Result.success(block()) } catch (t: Throwable) { Result.failure(t) } finally { run.set(false); sampler.join() }
        return out to (peak.get() - base)
    }

    /** Serves [file] for every request (HTTP/1.0, one request per connection). */
    private fun serve(file: File): Pair<ServerSocket, Int> {
        val ss = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        thread(isDaemon = true, name = "yu-m3u-server") {
            while (!ss.isClosed) {
                val s = runCatching { ss.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    s.use { sock ->
                        val input = sock.getInputStream().bufferedReader()
                        while (true) { val l = input.readLine() ?: break; if (l.isEmpty()) break }
                        val out = sock.getOutputStream()
                        out.write("HTTP/1.0 200 OK\r\nContent-Type: audio/x-mpegurl\r\nContent-Length: ${file.length()}\r\nConnection: close\r\n\r\n".toByteArray())
                        runCatching { file.inputStream().use { it.copyTo(out, 64 * 1024) } }
                        out.flush()
                    }
                }
            }
        }
        return ss to ss.localPort
    }

    @Test fun bigRemotePlaylistAddAndRefresh() = runBlocking {
        val src = File(ctx.cacheDir, "yu_remote_big.m3u")
        src.bufferedWriter().use { w ->
            w.write("#EXTM3U\n")
            for (n in 0 until CHANNELS) {
                w.write("#EXTINF:-1 tvg-id=\"ch$n.example\" tvg-name=\"Channel $n\" tvg-logo=\"https://logos.example.invalid/very/long/path/to/logo/$n/w_300,h_300/logo.png\" group-title=\"Group ${n % 40}\",Channel number $n with a fairly long display name\n")
                w.write("https://streams.example.invalid/live/hls/$n/index.m3u8?token=abcdefghijklmnopqrstuvwxyz0123456789\n")
            }
        }
        val (server, port) = serve(src)
        val half = Runtime.getRuntime().maxMemory() / 2
        var id: String? = null
        try {
            log("REMOTE file=${"%.1f".format(src.length() / 1_048_576.0)} MB maxHeap=${Runtime.getRuntime().maxMemory() / 1_048_576} MB")
            // The app's own start-up work (warm-ups, catalogs) allocates in this process for the first
            // seconds; 44017550181 api33-lowram measured it with the first step. Let it settle, then
            // measure each step twice and keep the lower peak (as LargePlaylistMemoryDeviceTest).
            Thread.sleep(12_000)
            val url = "http://127.0.0.1:$port/big.m3u"
            val (added, addPeak1) = peakOf { runBlocking { PlaylistStore.addRemote(ctx, "YuRemote big", url) } }
            id = added.getOrNull()?.id
            log("REMOTE add ok=${added.isSuccess} err=${added.exceptionOrNull()?.let { it::class.java.simpleName + ": " + it.message }} channels=${added.getOrNull()?.channelCount} peakMB=${addPeak1 / 1_048_576}")
            assertTrue("LT-4: adding the playlist failed: ${added.exceptionOrNull()}", added.isSuccess)
            assertEquals(CHANNELS, added.getOrThrow().channelCount)
            val (added2, addPeak2) = peakOf { runBlocking { PlaylistStore.addRemote(ctx, "YuRemote big 2", url) } }
            added2.getOrNull()?.id?.let { second -> runCatching { runBlocking { PlaylistStore.delete(ctx, second) } } }
            assertTrue("LT-4: adding the playlist again failed: ${added2.exceptionOrNull()}", added2.isSuccess)
            val (refreshed, refreshPeak1) = peakOf { runBlocking { PlaylistStore.refresh(ctx, id!!) } }
            log("REMOTE refresh ok=${refreshed.isSuccess} err=${refreshed.exceptionOrNull()?.let { it::class.java.simpleName + ": " + it.message }} channels=${refreshed.getOrNull()} peakMB=${refreshPeak1 / 1_048_576}")
            assertTrue("LT-4: refreshing the playlist failed: ${refreshed.exceptionOrNull()}", refreshed.isSuccess)
            assertEquals(CHANNELS, refreshed.getOrThrow())
            val (refreshed2, refreshPeak2) = peakOf { runBlocking { PlaylistStore.refresh(ctx, id!!) } }
            assertTrue("LT-4: refreshing again failed: ${refreshed2.exceptionOrNull()}", refreshed2.isSuccess)
            val (read, readPeak) = peakOf { runBlocking { PlaylistStore.channels(ctx, id!!).size } }
            log("REMOTE channels=${read.getOrNull()} peakMB=${readPeak / 1_048_576}")
            assertEquals(CHANNELS, read.getOrThrow())
            val addPeak = minOf(addPeak1, addPeak2)
            val refreshPeak = minOf(refreshPeak1, refreshPeak2)
            log("REMOTE peaksMB add=${addPeak1 / 1_048_576}/${addPeak2 / 1_048_576} refresh=${refreshPeak1 / 1_048_576}/${refreshPeak2 / 1_048_576} read=${readPeak / 1_048_576}")
            assertTrue("LT-4: adding peaked at ${addPeak / 1_048_576} MB (limit ${half / 1_048_576})", addPeak < half)
            assertTrue("LT-4: refreshing peaked at ${refreshPeak / 1_048_576} MB (limit ${half / 1_048_576})", refreshPeak < half)
            log("REMOTE_RESULT PASS add=${addPeak / 1_048_576} MB refresh=${refreshPeak / 1_048_576} MB")
        } finally {
            server.close()
            src.delete()
            id?.let { runCatching { PlaylistStore.delete(ctx, it) } }
        }
        Unit
    }

    private companion object { const val CHANNELS = 52_000 }
}
