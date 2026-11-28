/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.ui.navigation.NavPrefs
import dev.asfakul.yumeiro.ui.navigation.Screen
import dev.asfakul.yumeiro.ui.screens.settings.SettingsCatalog
import dev.asfakul.yumeiro.ui.screens.settings.SettingsLaunch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * B14 in the real app: Settings → Navigation bar is a reorderable list. Pin a shortcut with its
 * switch, move it with the arrow buttons (phone and TV), drag a row by its handle (phone), see the
 * real bar follow, and reset after the confirmation. Log tag YuGroupB14; GB14_RESULT_A PASS; shots gb14_*.
 */
@RunWith(AndroidJUnit4::class)
class GroupB14NavEditorDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(s: String) = Log.i("YuGroupB14", s)
    private val live = NavPrefs.SHORTCUT_PREFIX + "live"

    @After fun tearDown() { runCatching { NavPrefs.reset(ctx) } }

    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(what: String, ms: Long = 10_000, ok: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + ms
        while (SystemClock.uptimeMillis() < end) { rule.waitForIdle(); if (ok()) return; SystemClock.sleep(100) }
        FirstRun.shot("gb14_timeout"); throw AssertionError("timed out: $what · routes=${NavPrefs.config.value.routes}")
    }
    private fun tap(tag: String) {
        val n = rule.onNodeWithTag(tag, true)
        runCatching { n.performScrollTo() }
        if (Edition.isTv) n.performSemanticsAction(SemanticsActions.OnClick) else n.performClick()
        rule.waitForIdle()
    }
    private fun routes() = NavPrefs.config.value.routes

    @Test fun a_pinMoveDragReset() {
        NavPrefs.reset(ctx)
        NavPrefs.setShortcut(ctx, NavPrefs.Shortcut.SETTINGS, true)
        FirstRun.toTabs(rule)
        SettingsLaunch.request(SettingsCatalog.Category.NAVIGATION)
        FirstRun.clickTag(rule, "nav_shortcut_settings") // TV: the rail's Settings entry (FirstRun.navTag)
        waitFor("the editor") { has("nav_order_row_nav_tab_home") }
        FirstRun.shot("gb14_a_editor")

        tap("nav_settings_shortcut_live")
        waitFor("Live on the bar") { live in routes() && has("nav_order_row_nav_shortcut_live") }
        assertEquals(live, routes().last())
        repeat(5) { if (routes().indexOf(live) > 1) tap("nav_order_up_nav_shortcut_live") }
        waitFor("Live second") { routes().indexOf(live) == 1 }
        log("GB14 after arrows: ${routes()}")
        FirstRun.shot("gb14_a_moved")
        if (!Edition.isTv) {
            // The real bar on this page (B13) follows at once.
            waitFor("bar order") { has("nav_shortcut_live") && has("nav_tab_music") }
            val l = rule.onNodeWithTag("nav_shortcut_live", true).getUnclippedBoundsInRoot().left
            val m = rule.onNodeWithTag("nav_tab_music", true).getUnclippedBoundsInRoot().left
            assertTrue("Live left of Music on the bar ($l vs $m)", l < m)

            // Drag Music down two rows by its handle.
            val before = routes().indexOf(Screen.Music.route)
            rule.onNodeWithTag("nav_order_handle_nav_tab_music", true).performScrollTo()
            rule.onNodeWithTag("nav_order_handle_nav_tab_music", true).performTouchInput {
                val row = 60.dp.toPx()
                down(center)
                repeat(20) { moveBy(androidx.compose.ui.geometry.Offset(0f, row * 2 / 20f)) ; advanceEventTime(16) }
                up()
            }
            waitFor("Music dragged two down") { routes().indexOf(Screen.Music.route) == before + 2 }
            log("GB14 after drag: ${routes()}")
            FirstRun.shot("gb14_a_dragged")
        }

        tap("nav_settings_reset")
        waitFor("confirm") { has("nav_settings_reset_confirm") }
        FirstRun.shot("gb14_a_reset_confirm")
        tap("nav_settings_reset_confirm")
        waitFor("defaults") { routes() == listOf(Screen.Home.route, Screen.Music.route, Screen.Videos.route, Screen.Library.route) }
        log("GB14_RESULT_A PASS")
    }
}
