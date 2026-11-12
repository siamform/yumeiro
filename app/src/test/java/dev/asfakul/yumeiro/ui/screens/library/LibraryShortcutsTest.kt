/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.library

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import dev.asfakul.yumeiro.ui.components.LocalCurrentTrackId
import dev.asfakul.yumeiro.ui.screens.youtube.QuickPickRow
import dev.asfakul.yumeiro.ui.theme.DarkBackground
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import dev.asfakul.yumeiro.youtube.music.YmItem
import dev.asfakul.yumeiro.youtube.music.YmRef
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Round 10: Library shortcuts (instead of tabs) and Quick picks rows without a download button. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-night-xxhdpi")
class LibraryShortcutsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun save(name: String) {
        val dir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun first(tag: String) = rule.onAllNodes(hasTestTag(tag))[0]

    @Test fun shortcutsOpenTheirPages() {
        val opened = mutableListOf<String>()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MTPlayerTheme {
                Column(Modifier.fillMaxSize().background(DarkBackground).padding(16.dp)) {
                    LibraryShortcutGrid(libraryShortcuts(
                        scanning = false,
                        onVideos = { opened += "videos" }, onReading = { opened += "reading" }, onEqualizer = { opened += "eq" },
                        onNewPlaylist = { opened += "new" }, onRescan = { opened += "rescan" }, onHistory = { opened += "history" },
                        onDownloads = { opened += "downloads" }, onTogether = { opened += "together" },
                    ))
                    // While a scan runs, Rescan shows a spinner and "Scanning…".
                    LibraryShortcutGrid(libraryShortcuts(true, {}, {}, {}, {}, {}, {}, {}, {}), Modifier.padding(top = 24.dp))
                }
            }
        }
        rule.mainClock.advanceTimeBy(900)
        save("r10_library_shortcuts")
        listOf("lib_sc_videos", "lib_sc_reading", "lib_eq_card", "add_playlist_button", "banner_rescan_button", "lib_sc_history", "lib_sc_downloads", "lib_sc_together").forEach {
            first(it).performClick(); rule.mainClock.advanceTimeBy(300)
        }
        assertEquals(listOf("videos", "reading", "eq", "new", "rescan", "history", "downloads", "together"), opened)
    }

    @Test fun quickPickRowsHaveNoButtonAndMarkTheSongThatsOn() {
        var played = ""
        val songs = listOf(
            YmItem.Song("aaa", "Idol", listOf(YmRef("YOASOBI")), durationSec = 213),
            YmItem.Song("bbb", "Bling-Bang-Bang-Born", listOf(YmRef("Creepy Nuts"))),
        )
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MTPlayerTheme {
                CompositionLocalProvider(LocalCurrentTrackId provides "yt:aaa") {
                    Column(Modifier.fillMaxSize().background(DarkBackground)) { songs.forEach { QuickPickRow(it, null, 5.dp) { s -> played = s.videoId } } }
                }
            }
        }
        rule.mainClock.advanceTimeBy(900)
        save("r10_quick_picks_rows")
        rule.onNodeWithTag("song_download").assertDoesNotExist()
        rule.onNodeWithTag("quick_pick_now_playing", useUnmergedTree = true).assertExists()
        first("youtube_song").performClick()
        assertEquals("aaa", played)
    }
}
