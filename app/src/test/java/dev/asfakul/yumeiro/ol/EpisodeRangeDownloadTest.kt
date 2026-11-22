/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ol

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import dev.asfakul.yumeiro.online.OnlineEpisode
import dev.asfakul.yumeiro.online.download.EpisodeScope
import dev.asfakul.yumeiro.ui.screens.online.EpisodeRangePicker
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** OL-11 (owner): download one episode, all, or a range "from 50 to 80" by episode number. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class EpisodeRangeDownloadTest {
    @get:Rule val rule = createComposeRule()

    private fun ep(n: Int?) = OnlineEpisode("d$n", null, 1, n, null, null, null)
    private val hundred = (1..100).map { ep(it) }.withIndex().toList()

    @Test fun ol11_rangeFiftyToEightyTakesThirtyOneEpisodesByNumber() {
        val r = EpisodeScope.range(hundred, 50, 80)
        assertEquals(31, r.size)
        assertEquals(50, r.first().second.episode); assertEquals(80, r.last().second.episode)
        assertEquals("indices point into the full list", 49, r.first().first)
        assertEquals("either order works", r, EpisodeScope.range(hundred, 80, 50))
    }

    @Test fun ol11_rangeSkipsSavedAndUsesNumbersNotPositions() {
        val r = EpisodeScope.range(hundred, 50, 80, have = setOf("d55", "d60"))
        assertEquals(29, r.size)
        // A season list that starts at E13 (positions 0.., numbers 13..24).
        val season = (13..24).map { ep(it) }.withIndex().map { IndexedValue(it.index + 12, it.value) }
        assertEquals(13..24, EpisodeScope.bounds(season))
        val picked = EpisodeScope.range(season, 14, 15)
        assertEquals("by episode number, not position", listOf(14, 15), picked.map { it.second.episode })
        assertEquals("indices point into the full list (E14 sits at 13)", listOf(13, 14), picked.map { it.first })
        // No numbers from the source: 1-based place in the list.
        val bare = listOf(ep(null), ep(null), ep(null)).withIndex().toList()
        assertEquals(2, EpisodeScope.range(bare, 2, 3).size)
    }

    @Test fun ol11_thisEpisodeOnlyAndParsing() {
        val list = hundred.map { it.value }
        assertEquals(41, EpisodeScope.single(list, 41)!!.first)
        assertNull("already saved", EpisodeScope.single(list, 41, setOf("d42")))
        assertNull(EpisodeScope.single(list, null))
        assertEquals(50, EpisodeScope.parse(" 50 ")); assertNull(EpisodeScope.parse("")); assertNull(EpisodeScope.parse("5a"))
    }

    @Test fun ol11_rangePickerShowsLiveCountAndQueuesTheRange() {
        var picked: List<Pair<Int, OnlineEpisode>>? = null
        rule.setContent { MTPlayerTheme { EpisodeRangePicker(hundred, setOf("d60")) { picked = it } } }
        rule.onNodeWithTag("episode_scope_range").performClick()
        rule.onNodeWithTag("episode_range_from").performTextReplacement("50")
        rule.onNodeWithTag("episode_range_to").performTextReplacement("80")
        rule.onNodeWithTag("episode_range_count").assertTextContains("30 to download · 1 already saved or queued")
        rule.onNodeWithTag("episode_range_start").assertIsEnabled().assertTextContains("Download 30 episodes").performClick()
        assertEquals(30, picked!!.size)
        assertEquals((50..80).filter { it != 60 }, picked!!.map { it.second.episode })
        rule.onNodeWithTag("episode_range_to").performTextReplacement("")
        rule.onNodeWithTag("episode_range_start").assertIsNotEnabled()
    }
}
