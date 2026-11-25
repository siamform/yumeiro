/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import dev.asfakul.yumeiro.ui.screens.video.PlaybackErrorOverlay
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ERR-CARD-1: on the emulator (probe 45257033081) the "This is a web page" card in the portrait player
 * (about 220 dp tall) had its text cut off and its "Find the video" button below the player's edge.
 * The card must keep every button inside the player box.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ErrorCardFitsTest {
    @get:Rule val rule = createComposeRule()

    private val longReason = "This link is a web page, not a media file, and its HTML doesn't publish a stream address — " +
        "the site's player loads it at runtime. Open it in the page browser and start playback; the stream the page " +
        "plays is picked up with the headers it needs."

    private fun show(height: Int, retry: Boolean, onAction: () -> Unit) {
        rule.setContent {
            MTPlayerTheme {
                Box(Modifier.fillMaxWidth().height(height.dp)) {
                    PlaybackErrorOverlay(
                        errorMessage = "", onRetry = {}, onClose = {},
                        modifier = Modifier.align(Alignment.Center),
                        title = "This is a web page", detail = longReason,
                        actionLabel = "Find the video", onAction = onAction, showRetry = retry,
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun assertInside(tag: String, height: Int) {
        val b = rule.onNodeWithTag(tag, true).getBoundsInRoot()
        assertTrue("$tag bottom ${b.bottom} is outside the ${height}dp player", b.bottom.value <= height + 0.5f)
        assertTrue("$tag top ${b.top} is outside the player", b.top.value >= -0.5f)
    }

    @Test fun portraitPlayerKeepsActionVisible() {
        var tapped = false
        show(220, retry = false) { tapped = true }
        assertInside("error_action_btn", 220)
        assertInside("error_close_btn", 220)
        rule.onNodeWithTag("error_action_btn").assertIsDisplayed().performClick()
        assertTrue(tapped)
    }

    @Test fun portraitPlayerWithRetryKeepsAllButtons() {
        show(220, retry = true) {}
        assertInside("error_action_btn", 220)
        assertInside("error_retry_btn", 220)
        assertInside("error_close_btn", 220)
        for (t in listOf("error_action_btn", "error_retry_btn", "error_close_btn")) rule.onNodeWithTag(t).assertIsDisplayed()
    }

    @Test fun fullScreenKeepsTheRoomyCard() {
        show(800, retry = true) {}
        assertInside("error_action_btn", 800)
        assertInside("error_retry_btn", 800)
        rule.onNodeWithTag("error_action_btn").assertIsDisplayed()
    }
}

