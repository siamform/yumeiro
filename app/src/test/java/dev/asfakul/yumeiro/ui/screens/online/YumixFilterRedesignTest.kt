/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.asfakul.yumeiro.online.PinnedExtensions
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * YF-2 (owner list 10): "Show videos from" keeps up to 30 pins, filters by the source each extension comes
 * from (built from what is installed), and opens search from an icon instead of an always-open box.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class YumixFilterRedesignTest {
    @get:Rule val rule = createComposeRule()

    private val eco = mapOf("HiAnime" to "CloudStream", "AnimePahe" to "CloudStream", "Moviesdrive" to "Vega", "VegaMovies" to "Vega", "SkyFlix" to "SkyStream", "Local" to null)
    private val names = eco.keys.toList()

    @Test fun thirtyPinsFit() {
        assertEquals(30, PinnedExtensions.MAX)
        val full = (1..29).map { "E$it" }
        assertEquals(30, PinnedExtensions.toggled(full, "E30")!!.size)
        assertEquals(null, PinnedExtensions.toggled(full + "E30", "E31"))
    }

    @Test fun sourcesComeFromTheListMostFirst() {
        assertEquals(listOf("CloudStream" to 2, "Vega" to 2, "SkyStream" to 1, OTHER_SOURCE to 1), sourceGroups(names, eco::get))
        assertEquals(listOf("Moviesdrive", "VegaMovies"), filterBySource(names, eco::get, "Vega"))
        assertEquals(listOf("Local"), filterBySource(names, eco::get, OTHER_SOURCE))
        assertEquals(names, filterBySource(names, eco::get, null))
        assertEquals("no source known = no chips", emptyList<Pair<String, Int>>(), sourceGroups(names) { null })
    }

    @Test fun searchOpensFromTheIconAndSourceChipsNarrowTheList() {
        rule.setContent {
            MTPlayerTheme {
                SourceFilterSheet(names, null, status = { SourceStatus.Ready(5) }, onPick = {}, onDismiss = {}, pins = listOf("SkyFlix"), onTogglePin = {}, group = eco::get)
            }
        }
        rule.waitForIdle()
        assertEquals("no search box until the icon is tapped", 0, rule.onAllNodesWithTag("online_filter_search").fetchSemanticsNodes().size)
        rule.onNodeWithTag("online_filter-head-pinned").assertIsDisplayed()
        rule.onNodeWithText("1 of 30").assertIsDisplayed()
        rule.onNodeWithTag("online_filter-head-others").assertExists()

        rule.onNodeWithTag("online_filter-source-Vega").performClick()
        rule.waitForIdle()
        assertEquals(1, rule.onAllNodesWithTag("online_filter_Moviesdrive").fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithTag("online_filter_HiAnime").fetchSemanticsNodes().size)

        rule.onNodeWithTag("online_filter-search-open").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("online_filter_search").assertIsDisplayed().performTextInput("vegam")
        rule.waitForIdle()
        assertEquals(1, rule.onAllNodesWithTag("online_filter_VegaMovies").fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithTag("online_filter_Moviesdrive").fetchSemanticsNodes().size)

        rule.onNodeWithTag("online_filter-search-open").performClick() // now "Close search": clears it
        rule.onNodeWithTag("online_filter-source-all").performClick()
        rule.waitForIdle()
        assertEquals(0, rule.onAllNodesWithTag("online_filter_search").fetchSemanticsNodes().size)
        assertEquals(1, rule.onAllNodesWithTag("online_filter_HiAnime").fetchSemanticsNodes().size)
    }
}
