/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.service.PlaybackService
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.ServerSocket

/**
 * G6-WL: the shared music player used `WAKE_MODE_LOCAL` for everything, YouTube Music streams too.
 * With the screen off, a stream needs a Wi-Fi lock or the radio may drop to power save and the song
 * stalls (Media3 `C.WAKE_MODE_NETWORK`). The video/stream player already switches per URI
 * (StreamMediaFactory). Here the shared player plays one song from a file, then from a local HTTP
 * server: while streaming, `dumpsys wifi` must list ExoPlayer's Wi-Fi lock; for the file, none.
 *
 * Logs `YuLibraryTest WL_…`.
 */
@RunWith(AndroidJUnit4::class)
class MusicWifiLockDeviceTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx: Context get() = inst.targetContext
    private fun log(s: String) = Log.i("YuLibraryTest", s)
    private fun playing(): Boolean { var p = false; inst.runOnMainSync { p = PlaybackService.sharedPlayer?.isPlaying == true }; return p }

    private fun shell(cmd: String): String =
        inst.uiAutomation.executeShellCommand(cmd).let { pfd -> android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() } }

    /**
     * Held locks only: the `WifiLock{…}` lines right under a "Locks held:" header. The tag can also
     * appear further down in lock history/stats sections (api33 does this after a release), which
     * must not count (44218535243 api33-lowram: FILE2 matched the tag while "Locks held:" listed nothing;
     * `tagElsewhere` logs where it was).
     */
    private fun heldLockLines(dump: String): List<String> {
        val held = mutableListOf<String>(); var inHeld = false
        for (raw in dump.lineSequence()) {
            val l = raw.trim()
            when {
                l == "Locks held:" -> inHeld = true
                inHeld && l.startsWith("WifiLock{") -> held += l
                else -> inHeld = false
            }
        }
        return held
    }

    private fun wifiLockHeld(dump: String): Boolean = heldLockLines(dump).any { TAG in it }

    private fun playAndCheck(uri: String, what: String): Boolean {
        inst.runOnMainSync {
            val p = PlaybackService.getOrCreatePlayer(ctx)
            p.setMediaItem(MediaItem.Builder().setMediaId("yu-wl-$what").setUri(uri).build())
            p.repeatMode = Player.REPEAT_MODE_ONE
            p.volume = 0f
            p.prepare(); p.play()
        }
        val t0 = SystemClock.elapsedRealtime()
        while (!playing() && SystemClock.elapsedRealtime() - t0 < 20_000) Thread.sleep(200)
        assertTrue("$what did not start", playing())
        Thread.sleep(1_500)
        val dump = shell("dumpsys wifi")
        val held = wifiLockHeld(dump)
        val lines = dump.lineSequence().filter { "WifiLock" in it || "Locks held" in it || TAG in it }.take(8).joinToString(" | ") { it.trim().take(160) }
        val elsewhere = dump.lineSequence().filter { TAG in it }.map { it.trim().take(160) }.filter { it !in heldLockLines(dump) }.take(3).joinToString(" | ")
        log("WL_$what playing=${playing()} wifiLock=$held dump=[$lines] tagElsewhere=[$elsewhere]")
        inst.runOnMainSync { PlaybackService.sharedPlayer?.pause() }
        Thread.sleep(800)
        return held
    }

    @Test fun streamHoldsWifiLockFileDoesNot() {
        val bytes = inst.context.assets.open("yt_audio_only.m4a").use { it.readBytes() }
        val file = File(ctx.cacheDir, "yu_wl_song.m4a").apply { writeBytes(bytes) }
        ServerSocket(0).use { server ->
            val serving = Thread {
                runCatching {
                    while (true) {
                        val s = server.accept()
                        Thread {
                            runCatching {
                                s.use { sock ->
                                    val r = sock.getInputStream().bufferedReader()
                                    while (true) { val l = r.readLine() ?: break; if (l.isEmpty()) break }
                                    val o = sock.getOutputStream()
                                    o.write("HTTP/1.1 200 OK\r\nContent-Type: audio/mp4\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                                    o.write(bytes); o.flush()
                                }
                            }
                        }.apply { isDaemon = true; start() }
                    }
                }
            }.apply { isDaemon = true; start() }
            try {
                val local = playAndCheck(android.net.Uri.fromFile(file).toString(), "FILE")
                val stream = playAndCheck("http://127.0.0.1:${server.localPort}/song.m4a", "HTTP")
                val back = playAndCheck(android.net.Uri.fromFile(file).toString(), "FILE2")
                val pass = stream && !local && !back
                log("WL_RESULT ${if (pass) "PASS" else "FAIL"} file=$local http=$stream file2=$back sdk=${Build.VERSION.SDK_INT}")
                assertTrue("no Wi-Fi lock while streaming", stream)
                assertTrue("Wi-Fi lock held for a local file (file=$local, after stream=$back)", !local && !back)
            } finally {
                inst.runOnMainSync { PlaybackService.sharedPlayer?.let { it.repeatMode = Player.REPEAT_MODE_OFF; it.stop(); it.clearMediaItems() } }
                serving.interrupt(); file.delete()
            }
        }
    }

    private companion object { const val TAG = "ExoPlayer:WifiLockManager" }
}
