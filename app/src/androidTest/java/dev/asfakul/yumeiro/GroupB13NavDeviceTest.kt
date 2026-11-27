/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.ui.navigation.NavPrefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * B13 in the real app (phone): pinned functions light their own bar item (Live TV and Network stream
 * both open the Videos tab, so before they lit "Videos"), and the bar stays on sub-pages (it vanished
 * on every page that was not a main tab). Log tag YuGroupB13; GB13_RESULT_A/B PASS; shots gb13_*.
 */
@RunWith(AndroidJUnit4::class)
class GroupB13NavDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(s: String) = Log.i("YuGroupB13", s)

    @After fun tearDown() { runCatching { NavPrefs.reset(ctx) } }

    private fun settle() { rule.waitForIdle(); SystemClock.sleep(1200); rule.waitForIdle() }

    /** The bar item tags whose "selected" flag is on. */
    private fun lit(): List<String> = rule.onAllNodesWithTag("mt_bottom_navigation_bar", true).fetchSemanticsNodes().let { bars ->
        if (bars.isEmpty()) return emptyList()
        NavPrefs.barItems(NavPrefs.config.value).map { it.testTag }.filter { tag ->
            rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().any { n ->
                n.config.getOrElse(SemanticsProperties.Selected) { false }
            }
        }
    }

    private fun bars() = rule.onAllNodesWithTag("mt_bottom_navigation_bar", true).fetchSemanticsNodes().size

    @Test fun a_liveAndNetworkStreamLightThemselves() {
        assumeFalse("phone bar only (TV uses the side rail)", Edition.isTv)
        NavPrefs.reset(ctx)
        NavPrefs.setShortcut(ctx, NavPrefs.Shortcut.LIVE, true)
        NavPrefs.setShortcut(ctx, NavPrefs.Shortcut.NETWORK_STREAM, true)
        FirstRun.toTabs(rule)
        FirstRun.clickTag(rule, "nav_shortcut_live"); settle()
        val afterLive = lit()
        log("GB13 after Live TV: lit=$afterLive"); FirstRun.shot("gb13_a_live")
        assertEquals("Live TV pinned and tapped", listOf("nav_shortcut_live"), afterLive)
        FirstRun.clickTag(rule, "nav_shortcut_network_stream"); settle()
        val afterStream = lit()
        log("GB13 after Network stream: lit=$afterStream"); FirstRun.shot("gb13_a_stream")
        assertEquals("Network stream pinned and tapped", listOf("nav_shortcut_network_stream"), afterStream)
        androidx.test.espresso.Espresso.pressBackUnconditionally(); settle() // the link sheet
        if (FirstRun.hasNav(rule, "nav_tab_videos")) {
            FirstRun.clickTag(rule, "nav_tab_videos"); settle()
            val afterVideos = lit()
            log("GB13 after Videos: lit=$afterVideos")
            assertEquals("Videos tapped", listOf("nav_tab_videos"), afterVideos)
        }
        log("GB13_RESULT_A PASS")
    }

    @Test fun b_barStaysOnSubPages() {
        assumeFalse("phone bar only (TV uses the side rail)", Edition.isTv)
        NavPrefs.reset(ctx)
        NavPrefs.setShortcut(ctx, NavPrefs.Shortcut.DOWNLOADS, true)
        NavPrefs.setShortcut(ctx, NavPrefs.Shortcut.SETTINGS, true)
        FirstRun.toTabs(rule)
        FirstRun.clickTag(rule, "nav_shortcut_downloads"); settle()
        log("GB13 Downloads: bars=${bars()} subDock=${rule.onAllNodesWithTag("sub_dock", true).fetchSemanticsNodes().size} lit=${lit()}")
        FirstRun.shot("gb13_b_downloads")
        assertEquals("one bar on the Downloads page", 1, bars())
        assertEquals(listOf("nav_shortcut_downloads"), lit())
        FirstRun.clickTag(rule, "nav_shortcut_settings"); settle()
        log("GB13 Settings: bars=${bars()} lit=${lit()}"); FirstRun.shot("gb13_b_settings")
        assertEquals("one bar on Settings", 1, bars())
        assertEquals(listOf("nav_shortcut_settings"), lit())
        // A main tab from a sub-page goes back to the tab and lights it.
        FirstRun.clickTag(rule, "nav_tab_music"); settle()
        log("GB13 Music from Settings: bars=${bars()} lit=${lit()}")
        assertTrue("Music lit", lit() == listOf("nav_tab_music"))
        log("GB13_RESULT_B PASS")
    }
}
