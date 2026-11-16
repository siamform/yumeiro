/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.service.PlaybackService
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
 * ST-18: the ViewModel (and this manager) is created inside the first Home frame. On a cold
 * process the ExoPlayer build must wait for the next main-loop message instead of running in
 * that frame; with a player already alive it attaches at once (now-playing bar on frame one).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlayerBuiltAfterFirstFrameTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    @Before fun coldProcess() = PlaybackService.releaseSharedPlayerForTest()

    @Test fun coldStartDefersTheBuild() {
        val m = MusicPlayerManager(ctx)
        assertNull("the player was built inside the constructor (first frame)", PlaybackService.sharedPlayer)
        assertFalse(m.isAttached)
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull("the deferred build never ran", PlaybackService.sharedPlayer)
        assertTrue(m.isAttached)
        m.release()
    }

    @Test fun aCommandBeforeTheDeferredBuildAttachesAtOnce() {
        val m = MusicPlayerManager(ctx)
        m.setPlayerVolume(0.5f)
        assertTrue(m.isAttached)
        assertNotNull(PlaybackService.sharedPlayer)
        shadowOf(Looper.getMainLooper()).idle()
        m.release()
    }

    @Test fun anExistingPlayerAttachesInTheConstructor() {
        PlaybackService.getOrCreatePlayer(ctx)
        val m = MusicPlayerManager(ctx)
        assertTrue(m.isAttached)
        m.release()
    }

    @Test fun releaseBeforeTheBuildBuildsNothing() {
        val m = MusicPlayerManager(ctx)
        m.release()
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(PlaybackService.sharedPlayer)
    }
}
