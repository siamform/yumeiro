/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.asfakul.yumeiro.online.OnlineBookmarks
import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.ui.screens.online.PosterCard
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
 * F-09 (ADR-033): every poster offers Watch later and Favorite without opening the title.
 * Phone: long press. TV: the Menu key, with the first remote key on "Watch later".
 * Old code: a poster has one plain click, so no sheet opens. Log tag YuPosterActions.
 */
@RunWith(AndroidJUnit4::class)
class PosterActionsDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val item = OnlineItem("Frieren", "https://example.org/frieren", "Test Source", null, type = "Anime")

    private fun has(tag: String) = compose.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun key(code: Int) { FirstRun.shell("input keyevent $code"); Thread.sleep(400); compose.waitForIdle() }
    private fun log(s: String) = Log.i("YuPosterActions", s)

    @Before fun setUp() { OnlineBookmarks.reset() }
    @After fun tearDown() { OnlineBookmarks.reset() }

    private fun show(tv: Boolean) = compose.setContent {
        CompositionLocalProvider(LocalTvMode provides tv) {
            MTPlayerTheme { Box(Modifier.fillMaxSize().padding(24.dp)) { PosterCard(item, onClick = {}) } }
        }
    }

    @Test fun a_longPressFavoriteMarksThePoster() {
        show(tv = false)
        compose.onAllNodesWithTag("online_poster", true).onFirst().performTouchInput { longClick() }
        val opened = runCatching { compose.waitUntil(8_000) { has("poster_actions_favorite") } }.isSuccess
        log("POSTER_ACTIONS phone sheet=$opened")
        FirstRun.shot("poster_actions_phone")
        assertTrue("long press did not open Watch later / Favorite", opened)
        compose.onAllNodesWithTag("poster_actions_favorite", true).onFirst().performClick()
        compose.waitUntil(8_000) { !has("poster_actions") }
        val saved = OnlineBookmarks.items.value
        log("POSTER_ACTIONS phone saved=$saved mark=${has("poster_mark")}")
        assertEquals(1, saved.size)
        assertTrue("saved as Favorite: $saved", saved.single().toString().contains("favorite=true"))
        compose.waitUntil(5_000) { has("poster_mark") }
    }

    @Test fun b_menuKeyOnTvOpensTheSheetOnWatchLater() {
        show(tv = true)
        Thread.sleep(500)
        key(20) // DPAD_DOWN: focus lands on the poster
        key(82) // MENU
        val opened = runCatching { compose.waitUntil(8_000) { has("poster_actions") } }.isSuccess
        Thread.sleep(600)
        compose.waitForIdle()
        val focused = compose.onAllNodes(isFocused(), true).fetchSemanticsNodes()
            .joinToString("+") { it.config.getOrElseNullable(SemanticsProperties.TestTag) { null }.orEmpty() }
        log("POSTER_ACTIONS tv sheet=$opened focused=$focused")
        FirstRun.shot("poster_actions_tv")
        assertTrue("Menu did not open the poster sheet", opened)
        // Each window keeps its own focused node: the poster stays focused in the main window behind the
        // dialog (api36, tv-api34 showed "online_poster+poster_actions_watch_later"). Keys go to the dialog
        // window, so the sheet's own focus must be Watch later, and only that row in the sheet.
        val inSheet = focused.split("+").filter { it.startsWith("poster_actions") }
        assertEquals("focused=$focused", listOf("poster_actions_watch_later"), inSheet)
        key(23) // DPAD_CENTER
        compose.waitUntil(8_000) { !has("poster_actions") }
        assertEquals(OnlineBookmarks.Status.PLAN, OnlineBookmarks.items.value.singleOrNull()?.status)
    }
}
