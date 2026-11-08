/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.downloads

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.online.download.DownloadSettings
import dev.asfakul.yumeiro.online.download.OnlineDownloads
import dev.asfakul.yumeiro.online.download.OnlineDownloads.Item
import dev.asfakul.yumeiro.online.download.OnlineDownloads.Status
import dev.asfakul.yumeiro.ui.screens.downloads.DownloadConfirmBody
import dev.asfakul.yumeiro.ui.screens.downloads.DownloadPrompt
import dev.asfakul.yumeiro.ui.screens.downloads.DownloadsScreen
import dev.asfakul.yumeiro.ui.theme.DarkBackground
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class DownloadUiScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun save(name: String) {
        val dir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun episodes(started: MutableList<Boolean>) = DownloadPrompt.Request(
        DownloadPrompt.Type.EPISODES, "Solo Leveling", "12 episodes", 12, "Solo Leveling-(HiAnime-(Sub))", "1080p (up to)", defaultKeep = false,
    ) { keep -> started += keep; null }

    @Composable
    private fun Card(req: DownloadPrompt.Request) {
        var keep by remember { mutableStateOf(req.defaultKeep) }
        var dontAsk by remember { mutableStateOf(false) }
        Box(Modifier.fillMaxSize().background(DarkBackground).padding(16.dp)) {
            Surface(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("Download ${req.what}?", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.padding(bottom = 12.dp))
                    DownloadConfirmBody(req, keep, { keep = it }, dontAsk, { dontAsk = it })
                }
            }
        }
    }

    @Test fun confirmEpisodesOffline() {
        val started = mutableListOf<Boolean>()
        rule.setContent { MTPlayerTheme { Card(episodes(started)) } }
        rule.onNodeWithText("Where it goes").assertIsDisplayed()
        rule.onNodeWithTag("download_mode_offline").assertIsDisplayed()
        save("dl_1_confirm_episodes")
        rule.onNodeWithTag("download_mode_permanent").performClick()
        save("dl_2_confirm_permanent")
    }

    @Test fun confirmYouTubeAudio() {
        rule.setContent {
            MTPlayerTheme {
                Card(DownloadPrompt.Request(DownloadPrompt.Type.YT_AUDIO, "Lofi Girl — beats to relax", "audio (160 kbps)", 1, "YouTube Music", "Audio 160 kbps · WebM · Opus", 6_000_000, defaultKeep = true) { null })
            }
        }
        save("dl_3_confirm_yt_audio")
    }

    @Test fun promptRunsWithChosenModeWhenNotAsking() {
        val ctx = rule.activity
        DownloadSettings.init(ctx); DownloadSettings.setAskFirst(ctx, false)
        val started = mutableListOf<Boolean>()
        DownloadPrompt.ask(ctx, episodes(started))
        assertEquals(listOf(false), started)
        DownloadSettings.setAskFirst(ctx, true)
    }

    @Test fun downloadsPageGroups() {
        val day = 86_400_000L
        val now = System.currentTimeMillis()
        fun item(id: String, ep: String, folder: String, status: Status, keep: Boolean, order: Int, bytes: Long = 280_000_000) = Item(
            id, "Solo Leveling", "HiAnime", "u", ep, null, emptyMap(), "", false, emptyMap(), "f$id.mp4", status = status,
            bytes = bytes, total = 400_000_000, keep = keep, folder = folder, order = order, kind = OnlineDownloads.Kind.EPISODE,
            finishedAt = if (status == Status.DONE) now - day else 0, quality = "1080p",
        )
        rule.setContent {
            val ctx = LocalContext.current
            remember {
                DownloadSettings.init(ctx); DownloadSettings.setAutoDeleteDays(ctx, 7); DownloadSettings.setShowTips(ctx, true)
                OnlineDownloads.setItemsForTest(ctx, listOf(
                    item("1", "Episode 4", "Solo Leveling-(HiAnime-(Sub))", Status.RUNNING, false, 3, 120_000_000),
                    item("2", "Episode 5", "Solo Leveling-(HiAnime-(Sub))", Status.QUEUED, false, 4, 0),
                    item("3", "Episode 1", "Solo Leveling-(HiAnime-(Sub))", Status.DONE, true, 0),
                    item("4", "Episode 2", "Solo Leveling-(HiAnime-(Sub))", Status.DONE, true, 1),
                    item("5", "Episode 3", "Solo Leveling-(HiAnime-(Sub))", Status.DONE, false, 2),
                    item("6", "Episode 1", "Frieren-(AnimePahe-(Dub))", Status.DONE, false, 0),
                ))
                true
            }
            MTPlayerTheme { Box(Modifier.fillMaxSize().background(DarkBackground)) { DownloadsScreen(onBack = {}, onPlayVideo = {}, onBrowse = {}) } }
        }
        rule.onNodeWithText("Permanent", substring = false).assertIsDisplayed()
        save("dl_4_downloads_page")
        rule.onNodeWithTag("downloads_tips").assertIsDisplayed()
    }
}
