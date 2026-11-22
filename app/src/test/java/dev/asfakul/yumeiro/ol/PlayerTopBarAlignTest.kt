/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ol

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.asfakul.yumeiro.data.model.VideoItem
import dev.asfakul.yumeiro.ui.screens.video.VideoPlayerTopBar
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * OL-7 (owner): the player's top-right Quality / Audio / More buttons sat with empty space on
 * their left. On a 411 dp phone the buttons must sit at the trailing edge right after the title,
 * and the title must take the room the buttons don't use.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class PlayerTopBarAlignTest {
    @get:Rule val rule = createComposeRule()

    private fun show(widthDp: Int) = rule.setContent {
        MTPlayerTheme {
            Box(Modifier.width(widthDp.dp).testTag("bar_host")) {
                VideoPlayerTopBar(
                    video = VideoItem(id = "1", title = "A long episode title that is longer than the bar can show", durationMs = 60_000, uri = "file:///x.mp4"),
                    currentSpeed = 1f, isPipSupported = true, onBackClick = {}, onAudioTrackClick = {}, onSubtitleClick = {},
                    onSpeedClick = {}, onAspectRatioClick = {}, onPipClick = {}, onInfoClick = {}, onLockClick = {},
                    qualityLabel = "1080p",
                )
            }
        }
    }

    private fun check(widthDp: Int) {
        show(widthDp)
        val host = rule.onNodeWithTag("bar_host").getUnclippedBoundsInRoot()
        val title = rule.onNodeWithTag("video_player_title", true).getUnclippedBoundsInRoot()
        val quality = rule.onNodeWithTag("video_quality_btn", true).getUnclippedBoundsInRoot()
        val more = rule.onNodeWithTag("video_more_btn", true).getUnclippedBoundsInRoot()
        val gap: Dp = quality.left - title.right
        val edge: Dp = host.right - more.right
        println("OL7_CASE width=$widthDp gap=$gap edge=$edge title=${title.right - title.left}")
        assertTrue("$widthDp dp: ${gap} of empty space between the title and the buttons", gap <= 8.dp)
        assertTrue("$widthDp dp: More is ${edge} from the edge (padding is 12 dp)", edge <= 13.dp)
    }

    @Test fun portraitPhoneHasNoGapLeftOfTheButtons() = check(411)
    @Test fun smallPhoneHasNoGapLeftOfTheButtons() = check(360)
    @Test fun landscapeHasNoGapLeftOfTheButtons() = check(800)
}
