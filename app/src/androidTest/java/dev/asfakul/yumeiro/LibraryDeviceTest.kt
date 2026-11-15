/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.player.ListeningTime
import dev.asfakul.yumeiro.service.PlaybackService
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * R16 Group 5 (library and personal data).
 *
 * a: listening time is counted by the shared player itself. No activity and no ViewModel exist in
 *    this test, which is the state after Back-exit with music still playing. Before the fix the
 *    clock lived on the ViewModel and nothing was counted here.
 */
@RunWith(AndroidJUnit4::class)
class LibraryDeviceTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext
    private fun log(s: String) = Log.i("YuLibraryTest", s)

    @Test fun a_listeningTimeCountsWithoutAnyScreen() {
        val clip = File(ctx.cacheDir, "yu_library_clip.mp4")
        inst.context.assets.open(LocalPlayerDeviceTest.NAME).use { i -> clip.outputStream().use { i.copyTo(it) } }
        ListeningTime.load(ctx)
        val before = ListeningTime.total.value
        var player: Player? = null
        inst.runOnMainSync {
            player = PlaybackService.getOrCreatePlayer(ctx).apply {
                setMediaItem(MediaItem.fromUri(Uri.fromFile(clip)))
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 0f
                prepare(); play()
            }
        }
        try {
            val t0 = System.currentTimeMillis()
            var playing = false
            while (System.currentTimeMillis() - t0 < 20_000 && !playing) {
                inst.runOnMainSync { playing = player!!.isPlaying }
                if (!playing) Thread.sleep(250)
            }
            assertTrue("player never started", playing)
            Thread.sleep(35_000) // one 30 s save tick plus margin
            inst.runOnMainSync { player!!.pause() } // pause adds the rest and saves
            Thread.sleep(1_000)
            val gained = ListeningTime.total.value - before
            log("LIB_A gained=${gained}ms")
            assertTrue("listening time grew by only $gained ms", gained in 25_000..60_000)
            log("LIB_A_RESULT PASS")
        } finally {
            inst.runOnMainSync { player?.stop(); player?.clearMediaItems(); player?.repeatMode = Player.REPEAT_MODE_OFF; player?.volume = 1f }
            clip.delete()
        }
    }
}
