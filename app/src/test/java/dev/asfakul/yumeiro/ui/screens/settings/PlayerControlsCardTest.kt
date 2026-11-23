/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.player.VideoPrefs
import dev.asfakul.yumeiro.player.VideoPrefs.Control
import dev.asfakul.yumeiro.player.VideoPrefs.Place
import dev.asfakul.yumeiro.ui.screens.video.VideoPlayerBottomBar
import dev.asfakul.yumeiro.ui.screens.video.VideoPlayerCenterControls
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** PC-1 (owner list 11): every player button can be switched off from Settings > Video player. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class PlayerControlsCardTest {
    @get:Rule val rule = createComposeRule()
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @After fun reset() {
        VideoPrefs.setControls(ctx, Control.entries.toList(), emptySet())
        VideoPrefs.setBarCount(ctx, VideoPrefs.BAR_DEFAULT)
        VideoPrefs.Extra.entries.forEach { VideoPrefs.setExtra(ctx, it, true) }
    }

    @Test fun placeFollowsOrderHiddenAndBarCount() {
        val order = listOf(Control.QUALITY, Control.AUDIO, Control.SUBTITLES, Control.SPEED)
        val p = VideoPrefs.placeOf(order, setOf(Control.AUDIO), 2)
        assertEquals(mapOf(Control.QUALITY to Place.BAR, Control.AUDIO to Place.HIDDEN, Control.SUBTITLES to Place.BAR, Control.SPEED to Place.MORE), p)
    }

    @Test fun switchesInTheCardRemoveTheButtonsFromThePlayer() {
        VideoPrefs.init(ctx)
        rule.setContent {
            MTPlayerTheme {
                Column {
                    Box(Modifier.weight(1f)) {
                        Column(Modifier.verticalScroll(rememberScrollState())) { PlayerControlsCard() }
                    }
                    VideoPlayerCenterControls(isPlaying = true, onRewind10s = {}, onPlayPause = {}, onForward10s = {}, hasPrevious = true, hasNext = true)
                    VideoPlayerBottomBar(0L, 60_000L, 0f, false, {}, {})
                }
            }
        }
        fun count(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().size
        assertEquals(1, count("video_previous_btn")); assertEquals(1, count("video_rewind_10s_btn")); assertEquals(1, count("video_fullscreen_btn"))

        listOf("qa_extra_prev_next", "qa_extra_seek", "qa_extra_fullscreen").forEach { rule.onNodeWithTag(it).performScrollTo().performClick() }
        rule.waitForIdle()
        assertEquals(0, count("video_previous_btn")); assertEquals(0, count("video_next_btn"))
        assertEquals(0, count("video_rewind_10s_btn")); assertEquals(0, count("video_forward_10s_btn"))
        assertEquals(0, count("video_fullscreen_btn"))
        assertEquals("play / pause always stays", 1, count("video_play_pause_btn"))

        rule.onNodeWithTag("qa_control_place_speed").performScrollTo().assertTextEquals("Under More")
        rule.onNodeWithTag("qa_bar_more").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(4, VideoPrefs.barCount.value)
        rule.onNodeWithTag("qa_control_place_speed").assertTextEquals("On the bar")
    }
}
