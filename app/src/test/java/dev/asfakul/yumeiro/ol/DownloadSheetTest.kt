/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ol

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.online.download.DownloadSettings
import dev.asfakul.yumeiro.online.download.DownloadTracks
import dev.asfakul.yumeiro.ui.screens.downloads.DownloadPrompt
import dev.asfakul.yumeiro.ui.screens.downloads.DownloadPromptHost
import dev.asfakul.yumeiro.ui.screens.downloads.DownloadSheet
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** OL-6: the download popup is a short sheet: summary, one Offline / Permanent switch, chips, details folded, one button. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class DownloadSheetTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Before fun setUp() { DownloadSettings.init(ctx); DownloadSettings.setAskFirst(ctx, true) }

    private fun open(onStart: (Boolean, DownloadTracks.Pick) -> Unit) {
        rule.setContent { MTPlayerTheme { DownloadPromptHost() } }
        rule.waitForIdle()
        rule.runOnIdle {
            DownloadPrompt.ask(ctx, DownloadPrompt.Request(
                type = DownloadPrompt.Type.LINK, title = "Frieren S2 E3", what = "this video", count = 1, folder = "Frieren", quality = "HLS", defaultKeep = false,
                // Many subtitle tracks: the button must still be on screen.
                offer = DownloadTracks.Offer(heights = listOf(1080, 720, 480), audios = listOf("Japanese", "English"), subtitles = (1..12).map { "Lang $it" }),
                startWith = { keep, p -> onStart(keep, p); null },
            ) { null })
        }
        rule.waitForIdle()
    }

    @Test fun ol6_downloadButtonIsInViewWithoutScrolling() {
        open { _, _ -> }
        rule.onNodeWithTag("download_confirm_start").assertIsDisplayed()
        rule.onNodeWithTag("download_summary").assertIsDisplayed()
    }

    @Test fun ol6_keepIsOneSwitchWithOneLine() {
        var kept: Boolean? = null
        open { keep, _ -> kept = keep }
        rule.onNodeWithTag("download_keep_switch").assertIsDisplayed()
        rule.onNodeWithTag("download_mode_permanent").performClick()
        rule.onNodeWithTag("download_keep_note").assertTextEquals(DownloadSheet.keepNote(true, DownloadSettings.autoDeleteDays.value))
        rule.onNodeWithTag("download_confirm_start").performClick()
        rule.waitForIdle()
        assertEquals("Permanent is what starts", true, kept)
    }

    @Test fun ol6_whereAndTipsAreFoldedAway() {
        open { _, _ -> }
        assertTrue("tips are hidden at first", rule.onAllNodesWithText("Downloads keep going", substring = true).fetchSemanticsNodes().isEmpty())
        rule.onNodeWithTag("download_details").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("download_details_body").assertIsDisplayed()
    }

    @Test fun ol6_sheetOnPhonesDialogOnTvAndWide() {
        assertTrue(DownloadSheet.useSheet(360, tv = false))
        assertTrue(DownloadSheet.useSheet(411, tv = false))
        assertFalse("TV keeps a centred dialog (D-pad)", DownloadSheet.useSheet(411, tv = true))
        assertFalse("tablets / landscape: dialog", DownloadSheet.useSheet(800, tv = false))
    }

    @Test fun ol6_summaryIsOneShortLine() {
        assertEquals("Up to 720p · about 300 MB · 2.0 GB free", DownloadSheet.summary("Up to 720p", 300L * 1024 * 1024, 2L * 1024 * 1024 * 1024))
        assertEquals("Best", DownloadSheet.summary("Best", 0, 0))
        assertEquals("Download 5", DownloadSheet.startLabel(5))
        assertEquals("Download", DownloadSheet.startLabel(1))
        assertTrue(DownloadSheet.keepNote(false, 7).startsWith("Deleted by itself"))
    }
}
