/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.service.PlaybackService
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * GATE-1-MUSIC-START: on a cold process nothing plays, so the ExoPlayer build (1.6-3.9 s of main
 * thread on api33-lowram right after start, device-check 44973946688 / 44973948479) waits for the
 * first music command. A player built elsewhere (the session service for a headset Play) is
 * attached so the screen still shows that music.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlayerBuiltOnFirstUseTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val flag = FeatureFlags.Flag.MUSIC_PLAYER_ON_USE
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Before fun coldProcess() {
        PlaybackService.forgetSharedPlayerForTest()
        FeatureFlags.set(ctx, flag, true)
    }

    @After fun restore() = FeatureFlags.set(ctx, flag, flag.default)

    @Test fun aColdStartBuildsNoPlayerUntilACommand() {
        val m = MusicPlayerManager(ctx)
        idle()
        assertNull("the player was built with nothing to play", PlaybackService.sharedPlayer)
        assertFalse(m.isAttached)
        m.setPlayerVolume(0.5f)
        assertTrue("a command builds and attaches the player at once", m.isAttached)
        assertNotNull(PlaybackService.sharedPlayer)
        m.release()
    }

    @Test fun aPlayerBuiltElsewhereIsAttached() {
        val m = MusicPlayerManager(ctx)
        idle()
        PlaybackService.getOrCreatePlayer(ctx) // the session service, e.g. a headset Play
        idle()
        assertTrue("the screen did not attach the player the service built", m.isAttached)
        m.release()
    }

    @Test fun aReleasedScreenDoesNotAttachLater() {
        val m = MusicPlayerManager(ctx)
        m.release()
        PlaybackService.getOrCreatePlayer(ctx)
        idle()
        assertFalse(m.isAttached)
    }

    @Test fun theFlagIsOnByDefault() = assertTrue(flag.default)
}
