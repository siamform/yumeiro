/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.net.Uri
import android.util.Log
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.data.model.AudioTrack
import dev.asfakul.yumeiro.player.MusicPlayerManager
import dev.asfakul.yumeiro.service.PlaybackService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * G6 MU-4 on the device: music keeps playing after Back-exit (the screen's manager is released, the
 * shared player plays on). The next screen's manager must show the playing song and queue, and
 * "Play next" must insert after the playing song, not replace the queue. Real ExoPlayer and media
 * session, a real clip. Log tag YuMusicQueue.
 */
@RunWith(AndroidJUnit4::class)
class MusicQueueDeviceTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext
    private fun log(s: String) = Log.i("YuMusicQueue", s)

    private fun waitFor(ms: Long, what: () -> Boolean): Boolean {
        val t0 = System.currentTimeMillis()
        while (System.currentTimeMillis() - t0 < ms) {
            var ok = false
            inst.runOnMainSync { ok = what() }
            if (ok) return true
            Thread.sleep(200)
        }
        return false
    }

    @Test fun queueSurvivesBackExit() {
        val clips = (1..4).map { n ->
            File(ctx.cacheDir, "yu_queue_$n.mp4").also { f -> inst.context.assets.open(LocalPlayerDeviceTest.NAME).use { i -> f.outputStream().use { i.copyTo(it) } } }
        }
        val tracks = clips.mapIndexed { i, f -> AudioTrack("yuq${i + 1}", "Queue song ${i + 1}", "Yumeiro test", "Queue", 0L, Uri.fromFile(f).toString()) }
        var first: MusicPlayerManager? = null
        var second: MusicPlayerManager? = null
        try {
            inst.runOnMainSync {
                first = MusicPlayerManager(ctx).apply { playTrack(tracks[1], tracks.take(3)) }
                PlaybackService.sharedPlayer?.volume = 0f
            }
            assertTrue("player never started", waitFor(20_000) { PlaybackService.sharedPlayer?.isPlaying == true })
            inst.runOnMainSync { first!!.release() } // Back-exit: the screen's manager goes, music plays on
            Thread.sleep(1_000)
            inst.runOnMainSync { second = MusicPlayerManager(ctx) }
            waitFor(5_000) { second!!.playbackState.value.currentTrack != null }
            var st = second!!.playbackState.value
            var stillPlaying = false
            inst.runOnMainSync { stillPlaying = PlaybackService.sharedPlayer?.isPlaying == true } // the player is main-thread only
            log("MUQ reopen track=${st.currentTrack?.id} index=${st.currentQueueIndex} queue=${st.queue.map { it.id }} playing=$stillPlaying")
            assertEquals("LB/MU-4: the reopened screen lost the playing song", "yuq2", st.currentTrack?.id)
            assertEquals("MU-4: the reopened screen lost the queue", listOf("yuq1", "yuq2", "yuq3"), st.queue.map { it.id })
            inst.runOnMainSync { second!!.playNext(tracks[3]) }
            Thread.sleep(1_000)
            var ids = emptyList<String>(); var index = -1; var playing = false
            inst.runOnMainSync {
                val p: Player = PlaybackService.sharedPlayer!!
                ids = (0 until p.mediaItemCount).map { p.getMediaItemAt(it).mediaId }; index = p.currentMediaItemIndex; playing = p.isPlaying
            }
            st = second!!.playbackState.value
            log("MUQ playNext player=$ids index=$index playing=$playing state=${st.queue.map { it.id }}")
            assertEquals("MU-4: Play next replaced the playing queue", listOf("yuq1", "yuq2", "yuq4", "yuq3"), ids)
            assertEquals("MU-4: Play next changed the playing song", 1, index)
            assertTrue("MU-4: playback stopped", playing)
            assertEquals(ids, st.queue.map { it.id })
            log("MUQ_RESULT PASS")
        } finally {
            inst.runOnMainSync {
                second?.release(); first?.release()
                PlaybackService.sharedPlayer?.apply { stop(); clearMediaItems(); volume = 1f }
            }
            clips.forEach { it.delete() }
        }
    }
}
