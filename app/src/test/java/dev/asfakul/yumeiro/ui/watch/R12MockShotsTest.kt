/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.watch

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import dev.asfakul.yumeiro.ui.screens.video.AudioTrackDialog
import dev.asfakul.yumeiro.ui.screens.video.AudioTrackInfo
import dev.asfakul.yumeiro.ui.screens.watch.WatchServer
import dev.asfakul.yumeiro.ui.screens.watch.WatchServerBox
import dev.asfakul.yumeiro.ui.screens.watch.WatchServerRow
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import dev.asfakul.yumeiro.ui.screens.settings.BackupScreen
import dev.asfakul.yumeiro.ui.theme.DarkBackground
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * v1.7.0 mock images for the website and social posts, from the real composables:
 * Backup center, sources with facts, audio tracks by language. Output: app/build/screenshots/r12.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class R12MockShotsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun shot(name: String, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent { MTPlayerTheme { Box(Modifier.fillMaxSize().background(DarkBackground)) { content() } } }
        rule.mainClock.advanceTimeBy(1_500)
        val dir = File(System.getProperty("user.dir"), "build/screenshots/r12").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun backupCenter() = shot("01_backup_center") { BackupScreen(onBack = {}) }

    @Test fun sources() = shot("02_sources") {
        val names = listOf("Vidplay", "Filemoon", "StreamWish", "MixDrop", "DoodStream", "Voe", "Upstream", "StreamTape", "Mp4Upload", "Kwik", "Okru", "Gogo")
        val facts = listOf("1080p · WEB-DL · HLS · 1.4 GB · AAC 5.1", "1080p · HLS · 1.1 GB", "720p · MP4 · 640 MB · AAC", "720p · HLS", "480p · MP4 · 310 MB", "1080p · DASH · Opus")
        val sub = WatchServerRow("Sub · Japanese", Icons.Default.Mic, names.mapIndexed { i, n -> WatchServer(n, i == 0, "srv_sub", detail = facts[i % facts.size]) {} })
        val dub = WatchServerRow("Dub · English", Icons.Default.Mic, (0 until 4).map { WatchServer("Dub server ${it + 1}", false, "srv_dub", detail = "720p · MP4") {} })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 24.dp)) { WatchServerBox("Episode 1 · Sources", "Quality, format, size and audio on every source", listOf(sub, dub)) }
    }

    @Test fun audioTracks() = shot("03_audio_by_language") {
        val langs = listOf("en", "en", "hi", "ja", "ja", "ja", "ko")
        val tracks = langs.mapIndexed { i, l -> AudioTrackInfo(0, i, l, null, if (i % 2 == 0) 6 else 2, 48000, isSelected = i == 3) }
        AudioTrackDialog(tracks, { _, _ -> }, {})
    }
}
