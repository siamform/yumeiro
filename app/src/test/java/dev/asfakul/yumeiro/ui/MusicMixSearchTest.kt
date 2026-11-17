/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import dev.asfakul.yumeiro.data.model.AudioTrack
import dev.asfakul.yumeiro.ui.components.TabSearch
import dev.asfakul.yumeiro.ui.screens.music.MusicMixScreen
import dev.asfakul.yumeiro.ui.screens.music.mixMatchProbe
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Collections

/**
 * GROUP-13 M9-1 (unit, Robolectric Compose): Music → Mix search matched every device song in
 * composition, on the main thread, on every keystroke. The match must run off the main thread and
 * still show the right songs.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class MusicMixSearchTest {
    @get:Rule val rule = createComposeRule()

    @After fun tearDown() {
        mixMatchProbe = null
        TabSearch.close(TabSearch.MUSIC_MIX)
    }

    @Test fun deviceSearchRunsOffTheMainThreadAndFindsTheSongs() {
        val calls = Collections.synchronizedList(ArrayList<Boolean>())
        mixMatchProbe = { onMain -> calls += onMain }
        val tracks = (0 until 200).map { AudioTrack("mx$it", "Sóng $it", "Ártist", "Álbum", 1_000, "content://test/$it") }
        TabSearch.toggle(TabSearch.MUSIC_MIX)
        rule.setContent {
            MTPlayerTheme {
                MusicMixScreen(tracks, emptyList(), emptyList(), null, { _, _ -> }, { _, _ -> }, {}, {})
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("youtube_search", useUnmergedTree = true).performTextInput("song 157")
        // Only "Sóng 157" holds both words (accents folded); the section label is upper-cased.
        rule.waitUntil(10_000) { rule.onAllNodesWithText("1 SONG").fetchSemanticsNodes().isNotEmpty() }
        assertTrue("the device search never ran", calls.isNotEmpty())
        assertTrue("M9-1: device search ran on the main thread (${calls.count { it }} of ${calls.size} calls)", calls.none { it })
    }
}
