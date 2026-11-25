/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.online.OnlineRow
import dev.asfakul.yumeiro.sources.novel.NovelItem
import dev.asfakul.yumeiro.sources.novel.NovelLibrary
import dev.asfakul.yumeiro.ui.screens.novel.NovelBanner
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** GROUP G: G1 one type line, G2 one search on a single-extension page, G3 a Novel banner. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h1800dp")
class GroupGTest {
    @get:Rule val rule = createComposeRule()

    @Test fun g1TypeShownOnceOnly() {
        val movie = OnlineItem("Film", "https://s.example/1", "Site", null, type = "Movie")
        assertEquals("type chip on the poster → not repeated under the name", "", posterSubline(movie, false))
        assertEquals("Site", posterSubline(movie, true))
        val odd = OnlineItem("Thing", "https://s.example/2", "Site", null, type = "Podcast")
        assertEquals("no chip for this type → it stays in the line", "Podcast", posterSubline(odd, false))
    }

    @Test fun g2OneSearchOnASingleExtensionPage() {
        var changed = 0; var cleared = 0
        val items = (1..6).map { OnlineItem("T $it", "https://s.example/$it", "Site", null) }
        rule.setContent {
            MTPlayerTheme {
                HuePage(
                    name = "Site", api = null, rows = listOf(OnlineRow("Site", "Trending", items)), loading = false, error = null,
                    listState = rememberLazyListState(), strip = {}, onSearch = {}, onRetry = {}, openItem = {}, openInfo = {},
                    onOpenRow = {}, onChangeFilter = { changed++ }, onClearFilter = { cleared++ },
                )
            }
        }
        rule.waitForIdle()
        assertEquals("one header", 1, rule.onAllNodesWithTag("online_channel_header", true).fetchSemanticsNodes().size)
        val header = rule.onNodeWithTag("online_channel_header", true).fetchSemanticsNode()
        val descs = ArrayList<String>()
        fun walk(n: androidx.compose.ui.semantics.SemanticsNode) {
            if (n.config.contains(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)) descs += n.config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription]
            n.children.forEach { c -> walk(c) }
        }
        walk(header)
        assertEquals("no second magnifier in the header: $descs", 0, descs.count { it.contains("Search", true) })
        assertEquals("exactly one search box", 1, rule.onAllNodesWithTag("hue_section_search", true).fetchSemanticsNodes().size)
        assertEquals("no 'Showing:' strip repeating the name", 0, rule.onAllNodesWithText("Showing", substring = true).fetchSemanticsNodes().size)
        rule.onNodeWithTag("online_filter_active", true).performClick()
        rule.onNodeWithTag("online_filter_active_clear", true).performClick()
        rule.waitForIdle()
        assertEquals(1, changed); assertEquals(1, cleared)
    }

    @Test fun g3NovelBannerContinueFirstThenPicksWithCovers() {
        val items = (1..9).map { NovelItem("p", "N$it", "/n/$it", if (it == 2) null else "https://c.example/$it.jpg") }
        val reading = NovelLibrary.Entry("p", "/n/3", "N3", "https://c.example/3.jpg", chapterName = "Chapter 7", readAt = 5L)
        val s = NovelBanner.slides(items, reading, "Src", latest = false)
        assertEquals("Continue reading", s.first().slide.badge)
        assertEquals("Chapter 7", s.first().slide.line)
        val picks = s.drop(1).map { it.item.path }
        assertEquals("no cover skipped, reading not repeated, 5 picks", listOf("/n/1", "/n/4", "/n/5", "/n/6", "/n/7"), picks)
        assertEquals(5, NovelBanner.slides(items, null, "Src", latest = true).size)
        assertEquals(0, NovelBanner.slides(emptyList(), null, "Src", latest = false).size)
    }
}
