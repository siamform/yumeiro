/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import dev.asfakul.yumeiro.ui.screens.watch.WatchAction
import dev.asfakul.yumeiro.ui.screens.watch.WatchActionsRow
import dev.asfakul.yumeiro.ui.screens.watch.WatchControlBar
import dev.asfakul.yumeiro.ui.screens.watch.WatchNumberGrid
import dev.asfakul.yumeiro.ui.screens.watch.WatchServer
import dev.asfakul.yumeiro.ui.screens.watch.WatchServerBox
import dev.asfakul.yumeiro.ui.screens.watch.WatchServerRow
import dev.asfakul.yumeiro.ui.screens.watch.WatchTitle
import dev.asfakul.yumeiro.ui.theme.ForceDarkTheme
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import dev.asfakul.yumeiro.ui.theme.pageBackground
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Renders the watch page building blocks for README / social images (build/screenshots/app_watch_page.png). */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h1180dp-xxhdpi")
class WatchShowcaseTest {
    @get:Rule val rule = createComposeRule()

    @Test fun watchPage() {
        rule.setContent {
            MTPlayerTheme { ForceDarkTheme {
                Column(Modifier.fillMaxSize().pageBackground()) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                            .background(Brush.linearGradient(listOf(Color(0xFF1B1036), Color(0xFF3A2459), Color(0xFFF5C46B).copy(alpha = 0.55f)))),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(56.dp)) }
                    WatchControlBar(onPrevious = {}, onNext = {}, autoSkip = true)
                    WatchTitle("Moonlit Harbor", overline = "Hue · Season 1", meta = "Episode 12 · 24 min · Sub & Dub")
                    WatchServerBox(
                        watching = "Episode 12 · The Lantern Tide",
                        hint = "Stuck on black? Tap another source. Yumeiro already retries refused ones with fresh links and headers.",
                        rows = listOf(
                            WatchServerRow("Sub", Icons.Default.ClosedCaption, listOf(WatchServer("Source 1", true, "a") {}, WatchServer("Source 2", false, "b") {}, WatchServer("Source 3", false, "c") {})),
                            WatchServerRow("Dub", Icons.Default.Mic, listOf(WatchServer("Switch to Dub", false, "d") {})),
                        ),
                    )
                    WatchActionsRow {
                        WatchAction(Icons.Default.Bookmark, "My List", "x", highlighted = true) {}
                        WatchAction(Icons.Default.Share, "Share", "y") {}
                    }
                    WatchNumberGrid("Moonlit Harbor", count = 148, current = 11, numberOf = { it + 1 }, onPick = {}, watched = { it < 11 })
                }
            } }
        }
        rule.waitForIdle()
        val dir = java.io.File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
        java.io.File(dir, "app_watch_page.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 95, it)
        }
    }

    @Test fun searchSuggestions() {
        val K = dev.asfakul.yumeiro.ui.components.Suggestion.Kind.RECENT
        val list = listOf(
            dev.asfakul.yumeiro.ui.components.Suggestion("moonlight drive", K),
            dev.asfakul.yumeiro.ui.components.Suggestion("Moonlight Sonata", dev.asfakul.yumeiro.ui.components.Suggestion.Kind.SONG, "Ludwig van Beethoven"),
            dev.asfakul.yumeiro.ui.components.Suggestion("Moonlit Harbor", dev.asfakul.yumeiro.ui.components.Suggestion.Kind.TITLE, "My List · Hue"),
            dev.asfakul.yumeiro.ui.components.Suggestion("Moon Garden", dev.asfakul.yumeiro.ui.components.Suggestion.Kind.ALBUM, "Album · Aoi Sora"),
            dev.asfakul.yumeiro.ui.components.Suggestion("Moonrise Kingdom trailer", dev.asfakul.yumeiro.ui.components.Suggestion.Kind.VIDEO, "Movies"),
            dev.asfakul.yumeiro.ui.components.Suggestion("moon lofi mix", dev.asfakul.yumeiro.ui.components.Suggestion.Kind.QUERY),
        )
        rule.setContent {
            MTPlayerTheme { ForceDarkTheme {
                Column(Modifier.fillMaxSize().pageBackground()) {
                    dev.asfakul.yumeiro.ui.components.MTTopBar(
                        title = "Music", searchQuery = "", onSearchQueryChange = {}, onSettingsClick = {}, onInboxClick = {}, overline = "ミュージック · Listen",
                    )
                    dev.asfakul.yumeiro.ui.components.SuggestionList("moon", list, {}, {})
                }
            } }
        }
        rule.waitForIdle()
        val dir = java.io.File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
        java.io.File(dir, "app_search_suggestions.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 95, it)
        }
    }
}
