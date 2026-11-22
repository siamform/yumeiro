/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.asfakul.yumeiro.online.OnlineBookmarks
import dev.asfakul.yumeiro.online.OnlineHistory
import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.ui.screens.online.ContinueRow
import dev.asfakul.yumeiro.ui.screens.online.FeedCard
import dev.asfakul.yumeiro.ui.screens.online.TopTenRow
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import dev.asfakul.yumeiro.ui.tv.LocalTvMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F-09-FEED: owner "taps not working" for Watch later / Favorite. The Yumix feed card, Top 10 and Continue
 * watching must open the poster sheet on a real long press (phone) and on the Menu key (TV), and keep a tap
 * opening the title. Log tag YuF09Feed.
 */
@RunWith(AndroidJUnit4::class)
class F09FeedActionsDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val item = OnlineItem("Frieren", "https://example.org/frieren", "Test Source", null, type = "Anime")
    private var opened = 0

    private fun has(tag: String) = compose.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun key(code: Int) { FirstRun.shell("input keyevent $code"); Thread.sleep(400); compose.waitForIdle() }
    private fun log(s: String) = Log.i("YuF09Feed", s)

    @Before fun setUp() { OnlineBookmarks.reset() }
    @After fun tearDown() { OnlineBookmarks.reset() }

    private fun show(tv: Boolean, content: @Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalTvMode provides tv) {
            MTPlayerTheme { Box(Modifier.fillMaxSize().padding(24.dp)) { Column(Modifier.fillMaxWidth()) { content() } } }
        }
    }

    private fun longPress(name: String, tag: String?) {
        val nodes = if (tag != null) compose.onAllNodesWithTag(tag, true) else compose.onAllNodesWithText(item.name, useUnmergedTree = true)
        nodes.onFirst().performTouchInput { longClick() }
        val sheet = runCatching { compose.waitUntil(8_000) { has("poster_actions_favorite") } }.isSuccess
        log("F09_FEED $name long press sheet=$sheet opened=$opened")
        FirstRun.shot("f09_feed_$name")
        assertTrue("$name: long press did not open Watch later / Favorite", sheet)
        assertEquals("$name: a long press must not open the title", 0, opened)
        compose.onAllNodesWithTag("poster_actions_favorite", true).onFirst().performClick()
        compose.waitUntil(8_000) { !has("poster_actions") }
        val saved = OnlineBookmarks.items.value
        log("F09_FEED $name saved=$saved")
        assertEquals(1, saved.size)
        assertTrue("$name saved as Favorite: $saved", saved.single().toString().contains("favorite=true"))
    }

    @Test fun a_yumixFeedCard() {
        show(false) { FeedCard(item, horizontal = false, onClick = { opened++ }, onChannel = {}) }
        longPress("yumix", "online_feed_card")
        compose.waitUntil(5_000) { has("poster_mark") }
    }

    @Test fun b_topTen() {
        show(false) { TopTenRow("Top 10", listOf(item)) { opened++ } }
        longPress("top10", "online_poster")
    }

    @Test fun c_continueWatching() {
        val e = OnlineHistory.Entry(item.api, item.url, item.name, null, emptyMap(), "Anime", "v1", null, "E1", 60_000, 1_440_000, 1L)
        show(false) { ContinueRow(listOf(e), onClick = { opened++ }, onSeeAll = {}) }
        longPress("continue", null)
    }

    @Test fun d_tapStillOpens() {
        show(false) { FeedCard(item, horizontal = true, onClick = { opened++ }, onChannel = {}) }
        compose.onAllNodesWithTag("online_feed_card", true).onFirst().performClick()
        compose.waitForIdle()
        log("F09_FEED tap opened=$opened sheet=${has("poster_actions")}")
        assertEquals(1, opened)
        assertTrue("a tap must not open the sheet", !has("poster_actions"))
    }

    @Test fun e_tvMenuKeyOnFeedCard() {
        show(true) { FeedCard(item, horizontal = false, onClick = { opened++ }, onChannel = {}) }
        Thread.sleep(500)
        key(20) // DPAD_DOWN: focus lands on the card
        key(82) // MENU
        val sheet = runCatching { compose.waitUntil(8_000) { has("poster_actions") } }.isSuccess
        log("F09_FEED tv menu sheet=$sheet opened=$opened")
        FirstRun.shot("f09_feed_tv")
        assertTrue("Menu did not open the poster sheet on a feed card", sheet)
        key(23) // DPAD_CENTER on Watch later (first row)
        compose.waitUntil(8_000) { !has("poster_actions") }
        assertEquals(OnlineBookmarks.Status.PLAN, OnlineBookmarks.items.value.singleOrNull()?.status)
    }
}
