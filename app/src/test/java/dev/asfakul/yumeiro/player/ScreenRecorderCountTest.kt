/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.data.MusicPlayHistory
import dev.asfakul.yumeiro.service.PlaybackService
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * B-27 (CI 44789262370: ContinueSavedWithoutScreenTest failed with unrelated changes): the count of
 * screens that record plays must return to where it was after every release, also when the screen
 * was released before its player attached and was touched afterwards.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ScreenRecorderCountTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    @Before fun coldProcess() = PlaybackService.forgetSharedPlayerForTest()

    @Test fun releasedBeforeAttachThenTouchedLeavesNoCount() {
        val before = MusicPlayHistory.screenRecorders.get()
        val m = MusicPlayerManager(ctx) { }
        m.release()
        m.seekTo(1_000) // a late command after release attaches the player
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(before, MusicPlayHistory.screenRecorders.get())
    }

    @Test fun releaseTwiceCountsDownOnce() {
        PlaybackService.getOrCreatePlayer(ctx)
        val before = MusicPlayHistory.screenRecorders.get()
        val m = MusicPlayerManager(ctx) { }
        assertEquals(before + 1, MusicPlayHistory.screenRecorders.get())
        m.release(); m.release()
        assertEquals(before, MusicPlayHistory.screenRecorders.get())
    }
}
