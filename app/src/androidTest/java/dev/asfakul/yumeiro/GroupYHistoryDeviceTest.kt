/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.OnlineHistory
import dev.asfakul.yumeiro.security.Privacy
import dev.asfakul.yumeiro.ui.screens.online.OnlineHistoryScreen
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Group Y (B6): the real Continue watching page with five titles watched 10 minutes, 3 hours, 3 days, 20 days and
 * 90 days ago.
 *  a: Delete history → each time range shows how many titles it removes; "Last 24 hours" removes exactly the two
 *     newest; Undo brings both back with their resume spots.
 *  b: Select → pick two titles → Delete selected removes exactly those; Undo restores them; Select all picks all.
 *  c: Remove on one title → Undo bar → it goes away by itself after the undo time and the delete stays.
 * Log tag YuGroupY: GY_… lines, GY_RESULT_A/B/C PASS. Shots gy_a_sheet, gy_a_undo, gy_b_select, gy_c_undo.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class GroupYHistoryDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(s: String) = Log.i("YuGroupY", s)
    private val now = System.currentTimeMillis()
    private val day = 86_400_000L
    private val ages = listOf("Aoi" to 10 * 60_000L, "Beni" to 3 * 3_600_000L, "Cha" to 3 * day, "Doro" to 20 * day, "Enji" to 90 * day)

    private fun entry(name: String, ago: Long) = OnlineHistory.Entry(
        "YuDemo", "https://yu.test/$name", name, null, emptyMap(), "Anime", "1", "$name-ep2", "Episode 2", 600_000, 1_400_000, now - ago,
    )

    @Before fun setUp() {
        Privacy.setIncognito(ctx, false)
        OnlineHistory.seedForTests(ctx, ages.map { (n, a) -> entry(n, a) },
            ages.associate { (n, a) -> OnlineHistory.progressKey("YuDemo", "$n-ep2") to OnlineHistory.Progress(600_000, 1_400_000, now - a) })
    }

    @After fun tearDown() { OnlineHistory.clear(ctx); OnlineHistory.flush() }

    private fun has(tag: String) = runCatching { compose.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    private fun textShown(s: String) = runCatching { compose.onAllNodesWithText(s, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    private fun names() = OnlineHistory.entries.value.map { it.name }
    private fun waitFor(what: String, ms: Long = 8_000, ok: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + ms
        while (SystemClock.uptimeMillis() < end) { compose.waitForIdle(); if (ok()) return; SystemClock.sleep(100) }
        throw AssertionError("timed out: $what")
    }
    private fun open() {
        compose.setContent { FirstRun.AppBackground { Box(Modifier.fillMaxSize()) { OnlineHistoryScreen(onBack = {}, onOpenTitle = {}) } } }
        waitFor("grid") { has("history_grid") }
    }
    private fun click(tag: String) { compose.onNodeWithTag(tag, true).performClick(); compose.waitForIdle() }
    private fun countText(range: String) = compose.onNodeWithTag("history_range_count_$range", true).fetchSemanticsNode()
        .config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }.joinToString { it.text }

    @Test fun a_deleteByTimeRangeWithCountsAndUndo() {
        open()
        click("online_history_clear")
        waitFor("sheet") { has("history_delete_sheet") }
        val counts = listOf("hour", "day", "week", "month", "all").map { r -> countText(r).filter(Char::isDigit) }
        log("GY_A counts=$counts")
        assertEquals(listOf("1", "2", "3", "4", "5"), counts)
        click("history_range_day")
        SystemClock.sleep(500); compose.waitForIdle(); FirstRun.shot("gy_a_sheet")
        click("history_range_delete")
        waitFor("undo bar") { has("history_undo_bar") }
        log("GY_A after delete=${names()}")
        assertEquals(listOf("Cha", "Doro", "Enji"), names())
        assertTrue(!has("history_item_https://yu.test/Aoi"))
        assertEquals(null, OnlineHistory.progressOf("YuDemo", "Beni-ep2"))
        FirstRun.shot("gy_a_undo")
        click("history_undo")
        waitFor("restored") { names().size == 5 && has("history_item_https://yu.test/Aoi") }
        assertEquals(listOf("Aoi", "Beni", "Cha", "Doro", "Enji"), names())
        assertEquals(600_000L, OnlineHistory.progressOf("YuDemo", "Beni-ep2")?.positionMs)
        assertTrue(!has("history_undo_bar"))
        log("GY_RESULT_A PASS")
    }

    @Test fun b_selectManyDeleteTogetherAndUndo() {
        open()
        click("history_select")
        waitFor("pick overlays") { has("history_pick_https://yu.test/Beni") }
        click("history_pick_https://yu.test/Beni")
        compose.onNodeWithTag("history_pick_https://yu.test/Doro", true).performScrollTo().performClick(); compose.waitForIdle()
        assertTrue("header shows 2 selected", textShown(ctx.getString(R.string.hx_selected, 2)))
        SystemClock.sleep(400); compose.waitForIdle(); FirstRun.shot("gy_b_select")
        click("history_delete_selected")
        waitFor("undo bar") { has("history_undo_bar") }
        log("GY_B after delete=${names()}")
        assertEquals(listOf("Aoi", "Cha", "Enji"), names())
        assertTrue("selection mode ended", has("history_select"))
        click("history_undo")
        waitFor("restored") { names().size == 5 }
        click("history_select")
        click("history_select_all")
        assertTrue("all five selected", textShown(ctx.getString(R.string.hx_selected, 5)))
        log("GY_RESULT_B PASS")
    }

    @Test fun c_singleRemoveUndoExpires() {
        open()
        click("history_remove_https://yu.test/Cha")
        waitFor("undo bar") { has("history_undo_bar") }
        assertEquals(listOf("Aoi", "Beni", "Doro", "Enji"), names())
        FirstRun.shot("gy_c_undo")
        val shown = SystemClock.uptimeMillis()
        waitFor("undo bar leaves", ms = 15_000) { !has("history_undo_bar") }
        val stayed = SystemClock.uptimeMillis() - shown
        log("GY_C undo stayed ${stayed}ms")
        assertTrue("Undo stayed long enough to use ($stayed ms)", stayed >= 6_000)
        assertEquals(listOf("Aoi", "Beni", "Doro", "Enji"), names())
        log("GY_RESULT_C PASS")
    }
}
