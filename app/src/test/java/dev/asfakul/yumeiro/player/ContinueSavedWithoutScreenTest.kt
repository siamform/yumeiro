/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.data.model.AudioTrack
import dev.asfakul.yumeiro.service.PlaybackService
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * G5-PC2: Home → Continue ("Jump back in") was saved only by the screen's ViewModel. After a swipe
 * from recents the queue plays on, and if the process then dies, Home showed an older song at an
 * older position. The song playing with no screen must be saved with its full details (album art
 * comes from the queue, not from the player's thin metadata). Existing APIs only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ContinueSavedWithoutScreenTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Before fun setUp() {
        BackgroundPlaybackWatch::class.java.getDeclaredField("attached").apply { isAccessible = true }.setBoolean(BackgroundPlaybackWatch, false)
        BackgroundPlaybackWatch.attach(ctx, PlaybackService.getOrCreatePlayer(ctx))
        LastPlayed.clear(ctx)
        idle()
    }

    @Test fun theSongPlayingWithNoScreenIsHomeContinue() {
        val t = (1..3).map { AudioTrack("cont$it", "Song $it", "Artist", "Album", 200_000, "file:///nope/cont$it.mp3", albumArtUri = "content://art/$it") }
        val screen = MusicPlayerManager(ctx) { }
        screen.playTrack(t[0], t)
        idle()
        screen.release() // swiped from recents
        PlaybackService.getOrCreatePlayer(ctx).seekToNextMediaItem()
        idle()
        val a = LastPlayed.audio.value
        assertEquals("Home Continue after a song change with no screen", "cont2", a?.track?.id)
        assertEquals("Song 2", a?.track?.title)
        assertEquals("album art kept from the queue", "content://art/2", a?.track?.albumArtUri)
    }

    @Test fun whileAScreenListensTheWatchLeavesContinueAlone() {
        val t = (1..2).map { AudioTrack("own$it", "Own $it", "Artist", "Album", 200_000, "file:///nope/own$it.mp3") }
        val screen = MusicPlayerManager(ctx) { }
        screen.playTrack(t[0], t)
        idle()
        PlaybackService.getOrCreatePlayer(ctx).seekToNextMediaItem()
        idle()
        // The screen's ViewModel saves Continue (not built in this test); the watch must not.
        assertEquals(null, LastPlayed.audio.value)
        screen.release()
    }
}
