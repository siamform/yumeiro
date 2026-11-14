/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.r15

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertTextEquals
import dev.asfakul.yumeiro.player.SeekMath
import dev.asfakul.yumeiro.ui.screens.video.VideoPlayerBottomBar
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Phase 2: while a video opens the time row reads 00:00 / 00:00, never "--:--". */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class DurationLabelTest {
    @get:Rule val rule = createComposeRule()

    private fun show(compact: Boolean, loading: Boolean, live: Boolean = false) = rule.setContent {
        MTPlayerTheme {
            VideoPlayerBottomBar(
                currentPositionMs = 0L, durationMs = SeekMath.UNKNOWN, sliderProgress = 0f, isUserSeeking = false,
                onSeekProgressChange = {}, onSeekProgressFinish = {}, compact = compact, isLive = live, isLoading = loading,
            )
        }
    }

    @Test fun fullBarOpening() {
        show(compact = false, loading = true)
        rule.onNodeWithTag("video_duration_pending").assertTextEquals("00:00")
        assertEquals(0, rule.onAllNodesWithText("--:--", substring = true).fetchSemanticsNodes().size)
        assertEquals(2, rule.onAllNodesWithText("00:00").fetchSemanticsNodes().size)
    }

    @Test fun compactBarUnknownLength() {
        show(compact = true, loading = false)
        rule.onNodeWithTag("video_duration_pending").assertTextEquals("00:00")
        assertEquals(0, rule.onAllNodesWithText("--:--", substring = true).fetchSemanticsNodes().size)
    }

    @Test fun liveShowsBadge() {
        show(compact = false, loading = true, live = true)
        rule.onNodeWithTag("video_live_badge").assertTextEquals("LIVE")
    }
}
