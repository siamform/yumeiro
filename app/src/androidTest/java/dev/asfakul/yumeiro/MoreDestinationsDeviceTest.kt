/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.ui.navigation.NavPrefs
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * OL-9 (owner): Home Spaces "See more" opens the newer pages (Telegram, Profiles, Your stats,
 * Airing soon, Reading). OL-10 (owner): Settings -> Navigation bar can pin them too, and the
 * pinned button opens the page. Lines: OL_DEST home=<card> ok=… / OL_DEST nav=<id> ok=….
 */
@RunWith(AndroidJUnit4::class)
class MoreDestinationsDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val ctx: Context get() = inst.targetContext
    private fun log(s: String) = Log.i("YuOlDest", s)
    private fun has(tag: String) = runCatching { rule.onAllNodesWithTag(FirstRun.navTag(rule, tag), true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    private fun waitFor(tag: String, ms: Long = 15_000) = runCatching { rule.waitUntil(ms) { has(tag) }; true }.getOrDefault(false)
    private fun back() { FirstRun.dismissForeignDialog(rule); FirstRun.shell("input keyevent 4"); rule.waitForIdle() }
    private fun home() { repeat(4) { if (!has("home_screen_root")) back() }; waitFor("home_screen_root", 10_000) }

    @Before fun setUp() {
        runCatching { inst.uiAutomation.grantRuntimePermission(ctx.packageName, "android.permission.POST_NOTIFICATIONS") }
        FirstRun.toTabs(rule)
        waitFor("home_screen_root", 20_000)
    }

    @Test fun ol9_homeSpacesOpenTheNewerPages() {
        val cases = listOf(
            "quick_card_telegram" to "telegram_screen",
            "quick_card_profiles" to "settings_page_profiles",
            "quick_card_stats" to "stats_screen",
            "quick_card_upcoming" to "upcoming_screen",
            "quick_card_reading" to "reading_library",
        )
        val failed = ArrayList<String>()
        for ((card, page) in cases) {
            home()
            if (!has("home_spaces_more_block")) FirstRun.tapVisible(rule, "home_spaces_more")
            waitFor("home_spaces_more_block", 5_000)
            val shown = runCatching { FirstRun.tapVisible(rule, card, 10_000); true }.getOrDefault(false)
            val ok = shown && waitFor(page)
            FirstRun.shot("ol9_$card")
            log("OL_DEST home=$card shown=$shown ok=$ok")
            if (!ok) failed += "$card -> $page"
            if (shown) back()
        }
        assertTrue("OL-9: Home Spaces did not open: $failed", failed.isEmpty())
    }

    @Test fun ol10_navBarPinsAndOpensTheNewerPages() {
        val cases = listOf("profiles" to "settings_page_profiles", "stats" to "stats_screen", "upcoming" to "upcoming_screen", "reading" to "reading_library", "tracking" to "tracking_screen")
        val failed = ArrayList<String>()
        for ((id, page) in cases) {
            val s = NavPrefs.Shortcut.of(id)
            log("OL_DEST nav=$id known=${s != null}")
            assertNotNull("OL-10: Navigation bar cannot pin '$id'", s)
            try {
                inst.runOnMainSync { NavPrefs.setShortcut(ctx, s!!, true) }
                home()
                val pinned = waitFor("nav_shortcut_$id")
                val ok = pinned && runCatching { FirstRun.tapVisible(rule, "nav_shortcut_$id", 10_000); true }.getOrDefault(false) && waitFor(page)
                FirstRun.shot("ol10_$id")
                log("OL_DEST nav=$id pinned=$pinned ok=$ok")
                if (!ok) failed += "$id -> $page"
                back()
            } finally {
                inst.runOnMainSync { NavPrefs.setShortcut(ctx, s!!, false) }
            }
        }
        assertTrue("OL-10: pinned buttons did not open: $failed", failed.isEmpty())
    }
}
