/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ol

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.online.download.DownloadSettings
import dev.asfakul.yumeiro.online.download.DownloadTracks
import dev.asfakul.yumeiro.online.download.YtDownloadOptions.Choice
import dev.asfakul.yumeiro.online.download.YtOneSheet
import dev.asfakul.yumeiro.ui.screens.downloads.DownloadPrompt
import dev.asfakul.yumeiro.ui.screens.downloads.DownloadPromptHost
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * OWN-11 (owner 2026-11-22): "there is no audio download option while downloading a youtube video".
 * Root cause: DL-ONE moved YouTube into the shared popup, which (a) started the best video at once when
 * "Ask before download" was off, and (b) hid the audio row unless there were two or more audio choices.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class Own11YtAudioChoiceTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private val v720 = Choice("720p", "MP4 · H.264", 136, 140, "mp4", 150_000_000, false, 720)
    private val a128 = Choice("Audio 128 kbps", "AAC", -1, 140, "m4a", 4_000_000, true)
    private val oneVideoOneAudio = listOf(v720, a128)

    @After fun tearDown() { DownloadPrompt.dismiss(); DownloadSettings.setAskFirst(ctx, true) }

    @Test fun oneAudioChoiceIsStillAChoiceForAVideo() {
        val o = YtOneSheet.offer(oneVideoOneAudio, music = false)
        assertTrue("one height + one audio must still open the popup", o.hasChoices)
        val row = DownloadTracks.audioRow(o)
        assertEquals("Save as", row?.first)
        assertEquals(listOf<Pair<String?, String>>(null to "Video", "Audio only · 128 kbps" to "Audio only · 128 kbps"), row?.second)
        assertTrue(YtOneSheet.alwaysAsk(oneVideoOneAudio, music = false))
        assertFalse("a song has only audio: nothing new to ask", YtOneSheet.alwaysAsk(oneVideoOneAudio, music = true))
    }

    @Test fun popupShowsWithAskBeforeDownloadOffAndSavesAudioOnly() {
        DownloadSettings.setAskFirst(ctx, false)
        rule.setContent { MTPlayerTheme { DownloadPromptHost() } }
        rule.waitForIdle()
        var got: Choice? = null
        var plain = false
        rule.runOnIdle {
            DownloadPrompt.ask(ctx, DownloadPrompt.Request(
                type = DownloadPrompt.Type.YT_VIDEO, title = "Clip", what = "this video", count = 1, folder = "YouTube", quality = "720p",
                defaultKeep = true,
                offer = YtOneSheet.offer(oneVideoOneAudio, music = false),
                alwaysAsk = YtOneSheet.alwaysAsk(oneVideoOneAudio, music = false),
                startWith = { _, p -> got = YtOneSheet.choose(oneVideoOneAudio, false, p); null },
            ) { plain = true; null })
        }
        rule.waitForIdle()
        assertFalse("the best video must not start before the user picks", plain)
        rule.onNodeWithText("Save as").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("download_track_audio_1").performScrollTo().performClick() // Audio only · 128 kbps (0 = Video)
        rule.onNodeWithTag("download_confirm_start").performClick()
        rule.waitForIdle()
        assertEquals(a128, got)
    }

    @Test fun flagIsOn() { assertTrue(FeatureFlags.Flag.YT_AUDIO_CHOICE.default) }
}
