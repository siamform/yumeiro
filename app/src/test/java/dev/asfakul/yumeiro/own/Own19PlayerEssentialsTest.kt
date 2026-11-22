/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.own

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.dp
import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.data.model.VideoItem
import dev.asfakul.yumeiro.player.VideoPrefs.Control
import dev.asfakul.yumeiro.ui.screens.online.PlayerAction
import dev.asfakul.yumeiro.ui.screens.online.PlayerActions
import dev.asfakul.yumeiro.ui.screens.video.TopBarFit
import dev.asfakul.yumeiro.ui.screens.video.VideoPlayerTopBar
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * OWN-19 (owner 2026-11-22: "in full screen there are lots of buttons, it doesn't look good; show only the necessary
 * ones and move the others into the 3-dot menu"): the full-screen bar keeps three controls plus More, and the online
 * row keeps Episodes and Next plus More. Nothing is removed: every other control is in More.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Own19PlayerEssentialsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun flagIsOn() = assertTrue(FeatureFlags.isOn(FeatureFlags.Flag.PLAYER_ESSENTIALS))

    @Test fun essentialsKeepTheFirstThreeInTheUsersOrder() {
        val all = Control.entries.toList()
        val s = TopBarFit.essentials(TopBarFit.Split(all, emptyList()))
        assertEquals(listOf(Control.QUALITY, Control.AUDIO, Control.SUBTITLES), s.inline)
        assertEquals("nothing is lost, order kept", all.drop(3), s.overflow)
        val narrow = TopBarFit.Split(listOf(Control.QUALITY, Control.AUDIO), listOf(Control.SPEED))
        assertEquals("already short: unchanged", narrow, TopBarFit.essentials(narrow))
        val custom = TopBarFit.essentials(TopBarFit.Split(listOf(Control.LOCK, Control.SPEED, Control.PIP, Control.INFO), listOf(Control.ASPECT)))
        assertEquals(listOf(Control.LOCK, Control.SPEED, Control.PIP), custom.inline)
        assertEquals(listOf(Control.INFO, Control.ASPECT), custom.overflow)
    }

    private fun a(key: String) = PlayerAction(key, Icons.Default.Download, key, "t_$key") {}

    @Test fun onlineRowKeepsEpisodesAndNext() {
        val all = listOf(PlayerActions.EPISODES, PlayerActions.NEXT, PlayerActions.SOURCES, PlayerActions.SUBTITLES, PlayerActions.VERSION, PlayerActions.CAST, PlayerActions.DOWNLOAD).map(::a)
        val (shown, more) = PlayerActions.split(all)
        assertEquals(listOf(PlayerActions.EPISODES, PlayerActions.NEXT), shown.map { it.key })
        assertEquals(listOf(PlayerActions.SOURCES, PlayerActions.SUBTITLES, PlayerActions.VERSION, PlayerActions.CAST, PlayerActions.DOWNLOAD), more.map { it.key })
        // A film: Source stays, the rest under More.
        val (fs, fm) = PlayerActions.split(listOf(PlayerActions.SOURCES, PlayerActions.SUBTITLES, PlayerActions.DOWNLOAD).map(::a))
        assertEquals(listOf(PlayerActions.SOURCES), fs.map { it.key })
        assertEquals(2, fm.size)
        val (one, none) = PlayerActions.split(listOf(a(PlayerActions.SUBTITLES)))
        assertEquals(1, one.size); assertTrue(none.isEmpty())
    }

    @Test fun landscapeBarShowsOnlyTheMainButtons() {
        rule.setContent {
            MTPlayerTheme {
                Box(Modifier.width(891.dp)) {
                    VideoPlayerTopBar(
                        video = VideoItem(id = "1", title = "Episode", durationMs = 60_000, uri = "file:///x.mp4"),
                        currentSpeed = 1f, isPipSupported = true, onBackClick = {}, onAudioTrackClick = {}, onSubtitleClick = {},
                        onSpeedClick = {}, onAspectRatioClick = {}, onPipClick = {}, onInfoClick = {}, onLockClick = {},
                        qualityLabel = "1080p",
                    )
                }
            }
        }
        fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
        assertTrue(has("video_quality_btn")); assertTrue(has("video_audio_track_btn")); assertTrue(has("video_subtitle_btn"))
        assertTrue(has("video_more_btn"))
        for (t in listOf("video_speed_btn", "video_aspect_ratio_btn", "video_pip_btn", "video_info_btn", "video_screen_lock_btn")) assertFalse("$t is in More", has(t))
    }
}
