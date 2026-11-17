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
 * G5-PC1: swiping the app away from recents while music plays clears the screen's ViewModel and
 * its MusicPlayerManager listener; PlaybackService keeps playing the queue. Songs after that must
 * still reach Recently played and play counts, and nothing may be counted twice while a screen
 * listens. Uses only APIs that existed before the fix, so it runs unchanged before and after.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlaysCountedWithoutScreenTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = ctx.getSharedPreferences("mt_player_music_prefs", Context.MODE_PRIVATE)

    private fun tracks(tag: String) = (1..3).map { AudioTrack("$tag$it", "Song $it", "Artist", "Album", 200_000, "file:///nope/$tag$it.mp3") }
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun count(id: String): Int = org.json.JSONObject(prefs.getString("play_counts_json", null) ?: "{}").optInt(id, 0)
    private fun recent(): List<String> = prefs.getString("recently_played_ids", null).orEmpty().split('\n').filter { it.isNotBlank() }

    @Before fun setUp() {
        prefs.edit().clear().commit()
        // Statics live across Robolectric tests; attach the watch to the current shared player.
        BackgroundPlaybackWatch::class.java.getDeclaredField("attached").apply { isAccessible = true }.setBoolean(BackgroundPlaybackWatch, false)
        BackgroundPlaybackWatch.attach(ctx, PlaybackService.getOrCreatePlayer(ctx))
        idle()
    }

    @Test fun songsAfterTheScreenIsGoneAreCounted() {
        val t = tracks("gone")
        val screen = MusicPlayerManager(ctx) { /* the ViewModel's repository records this */ }
        screen.playTrack(t[0], t)
        idle()
        screen.release() // swiped from recents: ViewModel cleared, music keeps playing
        val player = PlaybackService.getOrCreatePlayer(ctx)
        player.seekToNextMediaItem() // the queue moves on with no screen
        idle()
        assertEquals("second song counted with no screen", 1, count("gone2"))
        assertEquals("gone2", recent().firstOrNull())
        player.seekToNextMediaItem()
        idle()
        assertEquals(1, count("gone3"))
        assertEquals(listOf("gone3", "gone2"), recent().take(2))
    }

    @Test fun nothingIsCountedTwiceWhileAScreenListens() {
        val t = tracks("live")
        val calls = mutableListOf<String>()
        val screen = MusicPlayerManager(ctx) { calls += it }
        screen.playTrack(t[0], t)
        idle()
        PlaybackService.getOrCreatePlayer(ctx).seekToNextMediaItem()
        idle()
        assertEquals("the screen records both songs", listOf("live1", "live2"), calls)
        assertEquals("the background watch writes nothing while a screen listens", 0, count("live2"))
        screen.release()
    }
}
