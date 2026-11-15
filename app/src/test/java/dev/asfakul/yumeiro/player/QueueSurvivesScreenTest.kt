/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.data.model.AudioTrack
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QueueSurvivesScreenTest {
    @Test fun aNewScreenSeesThePlayingQueue() {
        // R16 (G6): Back-exit releases the screen's manager while the shared player keeps playing.
        // The next screen's manager used to start with an empty queue.
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val tracks = (1..3).map { AudioTrack("t$it", "Song $it", "Artist", "Album", 200_000, "file:///nope/$it.mp3") }
        val first = MusicPlayerManager(ctx)
        first.playTrack(tracks[1], tracks)
        first.release()
        val second = MusicPlayerManager(ctx)
        val st = second.playbackState.value
        assertEquals(listOf("t1", "t2", "t3"), st.queue.map { it.id })
        assertEquals(1, st.currentQueueIndex)
        assertEquals("Song 2", st.currentTrack?.title)
        second.release()
    }
}
