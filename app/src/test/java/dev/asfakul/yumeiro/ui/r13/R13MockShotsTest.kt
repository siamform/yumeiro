/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.r13

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import dev.asfakul.yumeiro.player.VideoPrefs
import dev.asfakul.yumeiro.ui.components.SubPageHeader
import dev.asfakul.yumeiro.ui.screens.settings.BackupScreen
import dev.asfakul.yumeiro.ui.screens.settings.PlayerInfoOverlayCard
import dev.asfakul.yumeiro.ui.screens.settings.QualityAndAudioCard
import dev.asfakul.yumeiro.ui.screens.video.PlayerInfoPanel
import dev.asfakul.yumeiro.ui.screens.watch.WatchDetailsSkeleton
import dev.asfakul.yumeiro.ui.screens.watch.WatchKind
import dev.asfakul.yumeiro.ui.screens.watch.WatchServer
import dev.asfakul.yumeiro.ui.screens.watch.WatchServerBox
import dev.asfakul.yumeiro.ui.screens.watch.WatchServerRow
import dev.asfakul.yumeiro.ui.theme.DarkBackground
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * v1.8.0 mock images for the website and social posts, rendered from the real composables.
 * Output: app/build/screenshots/r13. Values in the player info panel are sample values.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-night-xxhdpi")
class R13MockShotsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun out(name: String) = File(File(System.getProperty("user.dir"), "build/screenshots/r13").apply { mkdirs() }, "$name.png")

    private fun set(content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent { MTPlayerTheme { Box(Modifier.fillMaxSize().background(DarkBackground)) { content() } } }
        rule.mainClock.advanceTimeBy(1_500)
    }

    private fun save(name: String, tag: String? = null) {
        rule.mainClock.advanceTimeBy(800)
        val node = if (tag != null) rule.onNodeWithTag(tag, useUnmergedTree = true) else rule.onRoot()
        out(name).outputStream().use { node.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun prefs(anime4k: Boolean = true) {
        val c = rule.activity
        VideoPrefs.init(c); VideoPrefs.setAnime4k(c, anime4k); VideoPrefs.setInfoOverlay(c, true)
        VideoPrefs.setInfoFields(c, VideoPrefs.DEFAULT_INFO_FIELDS)
    }

    @Composable private fun qualityPage(scrollTo: Int = 0) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState(scrollTo))) {
            SubPageHeader(title = "Quality and audio", onBack = {}, subtitle = "Audio quality & sound", handleBack = false)
            Column(Modifier.padding(horizontal = 16.dp)) { QualityAndAudioCard() }
        }
    }

    @Test fun qualityAudio() { prefs(); set { qualityPage() }; save("01_quality_audio") }
    @Test fun qualityAudioControls() { prefs(); set { qualityPage(scrollTo = 100_000) }; save("02_player_controls") }
    @Test fun decoderDialog() { prefs(); set { qualityPage() }; rule.onNodeWithTag("qa_decoder").performClick(); save("03_decoder_dialog") }
    @Test fun audioDialog() { prefs(); set { qualityPage() }; rule.onNodeWithTag("qa_audio").performClick(); save("04_audio_dialog") }
    @Test fun gpuDialog() { prefs(); set { qualityPage() }; rule.onNodeWithTag("qa_gpu_tier").performClick(); save("05_gpu_dialog") }

    @Test fun infoSettings() {
        prefs()
        set { Column(Modifier.padding(16.dp)) { PlayerInfoOverlayCard() } }
        save("06_info_settings")
    }

    @Test fun infoPanel() {
        set {
            Box(Modifier.padding(24.dp)) {
                PlayerInfoPanel(
                    listOf(
                        "Resolution" to "1920×1080", "Source" to "Vidplay", "Provider" to "Sample extension",
                        "Quality" to "1080p", "Video codec" to "HEVC (hvc1.1.6.L120.90)", "Audio codec" to "AAC · 2ch · 48.0kHz",
                        "Frame rate" to "23.98 fps", "Video bitrate" to "4.20 Mbps", "Buffer" to "38.4 s",
                        "Dropped frames" to "0 of 14382", "Decoder" to "V: c2.qti.hevc (hardware)", "Speed" to "1.0x",
                        "Audio track" to "Japanese · ja", "Subtitle track" to "English · en", "Audio boost" to "Off",
                    ),
                )
            }
        }
        save("07_info_panel", tag = "player_info_overlay")
    }

    @Test fun sourcesLoadMore() {
        val names = listOf("Vidplay", "Filemoon", "StreamWish", "MixDrop", "DoodStream", "Voe", "Upstream", "StreamTape", "Mp4Upload", "Kwik")
        val facts = listOf("1080p · HLS · AAC", "1080p · HLS", "720p · MP4 · AAC", "720p · HLS", "480p · MP4", "1080p · DASH · Opus")
        set {
            val sub = WatchServerRow("Sub · Japanese", Icons.Default.Mic, names.mapIndexed { i, n -> WatchServer(n, i == 0, "srv_sub", detail = facts[i % facts.size]) {} })
            Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 24.dp)) {
                WatchServerBox("Episode 1 · Sources", "10 shown, more load when you ask", listOf(sub), moreSources = 14)
            }
        }
        save("08_sources_load_more")
    }

    @Test fun backup() { set { BackupScreen(onBack = {}) }; save("09_backup_restore") }

    @Test fun skeletonMovie() { set { WatchDetailsSkeleton(WatchKind.MOVIE) }; save("10_skeleton_movie") }
    @Test fun skeletonSeries() { set { WatchDetailsSkeleton(WatchKind.SERIES) }; save("11_skeleton_series") }
    @Test fun skeletonDirect() { set { WatchDetailsSkeleton(WatchKind.DIRECT) }; save("12_skeleton_direct") }
    @Test fun skeletonPage() { set { WatchDetailsSkeleton(WatchKind.PAGE) }; save("13_skeleton_page") }

    @Test fun manga() { set { dev.asfakul.yumeiro.ui.screens.manga.MangaHomeScreen(onBack = {}, onOpenTitle = {}, onOpenSources = {}) }; rule.mainClock.advanceTimeBy(3_000); save("14_manga_home") }
}
