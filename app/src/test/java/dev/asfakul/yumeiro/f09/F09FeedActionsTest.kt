/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.f09

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.online.OnlineBookmarks
import dev.asfakul.yumeiro.online.OnlineHistory
import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.ui.screens.online.ContinueRow
import dev.asfakul.yumeiro.ui.screens.online.FeedCard
import dev.asfakul.yumeiro.ui.screens.online.TopTenRow
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F-09-FEED: owner report "taps not working" for Watch later / Favorite. Only [PosterCard] had the long press;
 * the Yumix feed cards, Top 10 and Continue watching had a plain click, so a long press there did nothing.
 * Each must open the Watch later / Favorite sheet, a tap must still open the title, and a choice must save.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class F09FeedActionsTest {
    @get:Rule val rule = createComposeRule()
    private val item = OnlineItem("Frieren", "https://example.org/frieren", "Test Source", null, type = "Anime")
    private var opened = 0

    @Before fun setUp() { OnlineBookmarks.reset() }
    @After fun tearDown() { OnlineBookmarks.reset() }

    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun show(content: @Composable () -> Unit) = rule.setContent { MTPlayerTheme { Box(Modifier.fillMaxSize()) { content() } } }

    private fun longPressOpensSheetAndSaves(tag: String? = null, text: String? = null) {
        val nodes = if (tag != null) rule.onAllNodesWithTag(tag, true) else rule.onAllNodesWithText(text!!, useUnmergedTree = true)
        nodes.onFirst().performTouchInput { longClick() }
        rule.waitUntil(5_000) { has("poster_actions_favorite") }
        assertEquals("a long press must not open the title", 0, opened)
        rule.onAllNodesWithTag("poster_actions_favorite", true).onFirst().performClick()
        rule.waitUntil(5_000) { !has("poster_actions") }
        val saved = OnlineBookmarks.items.value
        assertEquals(1, saved.size)
        assertTrue("saved as Favorite: $saved", saved.single().toString().contains("favorite=true"))
    }

    @Test fun yumixFeedCardLongPress() {
        show { FeedCard(item, horizontal = false, onClick = { opened++ }, onChannel = {}) }
        longPressOpensSheetAndSaves("online_feed_card")
        rule.waitUntil(5_000) { has("poster_mark") }
    }

    @Test fun yumixFeedCardTapStillOpens() {
        show { FeedCard(item, horizontal = true, onClick = { opened++ }, onChannel = {}) }
        rule.onAllNodesWithTag("online_feed_card", true).onFirst().performClick()
        rule.waitForIdle()
        assertEquals(1, opened)
        assertTrue("a tap must not open the sheet", !has("poster_actions"))
    }

    @Test fun topTenLongPress() {
        show { TopTenRow("Top 10", listOf(item)) { opened++ } }
        longPressOpensSheetAndSaves("online_poster")
    }

    @Test fun continueWatchingLongPress() {
        val e = OnlineHistory.Entry(item.api, item.url, item.name, null, emptyMap(), "Anime", "v1", null, "E1", 60_000, 1_440_000, 1L)
        show { ContinueRow(listOf(e), onClick = { opened++ }, onSeeAll = {}) }
        longPressOpensSheetAndSaves(text = item.name)
    }

    @Test fun flagIsOn() { assertTrue(FeatureFlags.Flag.F09_FEED_ACTIONS.default) }
}
