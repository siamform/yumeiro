/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.online.OnlineRow
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** EXT-SEC-1 (owner list R21, item 15): one extension picked on Yumix shows its own sections, each with See all. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h1800dp")
class ExtensionSectionsTest {
    @get:Rule val rule = createComposeRule()
    private fun items(p: String, n: Int) = (1..n).map { OnlineItem("$p $it", "https://site.example/$p/$it", "Site", null) }
    private val rows = listOf(
        OnlineRow("Site", "Trending", items("Trend", 30), pageName = "Trending", pageData = "/trending", hasNext = true),
        OnlineRow("Site", "New dramas", items("Drama", 8)), // no paging: See all shows it as one section here
        OnlineRow("Site", "Anime", items("Anime", 12), pageName = "Anime", pageData = "/anime", hasNext = true),
    )
    private fun count(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().size

    private fun show(opened: MutableList<OnlineRow>) = rule.setContent {
        MTPlayerTheme {
            HuePage(
                name = "Site", api = null, rows = rows, loading = false, error = null, listState = rememberLazyListState(),
                strip = {}, onSearch = {}, onRetry = {}, openItem = {}, openInfo = {}, onOpenRow = { opened += it },
            )
        }
    }

    @Test fun sectionsShowAsShelvesWithSeeAll() {
        val opened = mutableListOf<OnlineRow>()
        show(opened)
        rule.waitForIdle()
        assertTrue("home tab selected", count("hue_tab_home") == 1)
        assertEquals("one shelf per section", 3, count("hue_shelf"))
        assertEquals("no endless grid on the extension's home", 0, count("hue_grid_row"))
        // Paged section → the endless See all page for that row.
        rule.onAllNodesWithTag("online_see_all", true).onFirst().performClick()
        rule.waitForIdle()
        assertEquals(listOf("Trending"), opened.map { it.title })
    }

    @Test fun unpagedSeeAllAndEverythingStayReachable() {
        val opened = mutableListOf<OnlineRow>()
        show(opened)
        rule.waitForIdle()
        val dramaSeeAll = rule.onAllNodesWithTag("online_see_all", true).fetchSemanticsNodes()
        assertEquals(3, dramaSeeAll.size)
        rule.onAllNodesWithTag("online_see_all", true)[1].performClick()
        rule.waitForIdle()
        assertTrue("no paging: shown here as that section", opened.isEmpty())
        assertEquals(0, count("hue_shelf"))
        assertTrue(count("hue_grid_row") > 0)
        rule.onNodeWithTag("hue_tab_home", true).performClick(); rule.waitForIdle()
        assertEquals(3, count("hue_shelf"))
        rule.onNodeWithTag("online_home_list").performScrollToNode(hasTestTag("hue_browse_all"))
        rule.onNodeWithTag("hue_browse_all", true).performClick(); rule.waitForIdle()
        assertEquals(0, count("hue_shelf"))
        assertTrue("Everything = the mixed endless feed", count("hue_grid_row") > 0)
    }
}
