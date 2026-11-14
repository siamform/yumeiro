/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import dev.asfakul.yumeiro.data.model.AudioTrack
import dev.asfakul.yumeiro.data.model.PlaybackState
import dev.asfakul.yumeiro.ui.screens.player.NowPlayingScreen
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class MusicCodecUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val track = AudioTrack("yt:abc", "Idol", "YOASOBI", "THE BOOK 2", 213_000, "https://example.invalid/a")

    private fun show(codec: AudioCodecInfo?) = rule.setContent {
        MTPlayerTheme {
            NowPlayingScreen(
                playbackState = PlaybackState(currentTrack = track, durationMs = 213_000, audioCodec = codec),
                onClose = {}, onTogglePlayPause = {}, onNext = {}, onPrevious = {}, onSeek = {},
                onToggleShuffle = {}, onCycleRepeat = {}, onToggleFavorite = {}, onPlayQueueIndex = {}, onDismissError = {},
            )
        }
    }

    @Test fun codecLineShowsUnderTheArtist() {
        show(AudioCodecInfo("audio/opus", bitrate = 160_000, sampleRate = 48_000, channels = 2))
        rule.onNodeWithTag("now_playing_codec").assertIsDisplayed()
        rule.onNodeWithText("Opus \u00B7 160 kbps \u00B7 48 kHz \u00B7 Stereo", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("now_playing_lossless", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun losslessBadge() {
        show(AudioCodecInfo("audio/flac", sampleRate = 96_000, channels = 2, bitDepth = 24))
        rule.onNodeWithTag("now_playing_lossless", useUnmergedTree = true).assertExists()
    }

    @Test fun nothingWhenUnknown() {
        show(null)
        rule.onNodeWithTag("now_playing_codec").assertDoesNotExist()
    }
}
