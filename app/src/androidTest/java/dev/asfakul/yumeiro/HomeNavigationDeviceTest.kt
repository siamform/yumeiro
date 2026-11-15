/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.ui.screens.TabPagerProbe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R16 Step 3, group 1 (Home and navigation) on an emulator, through the real UI:
 * H1 Home shows its list, H4 every bottom tab opens its page and back returns Home,
 * H10 the top-bar search suggests a recent search, H7 a VIEW intent for a web video opens the player, H8 the open tab survives the activity
 * being recreated, H13 the inbox opens from the top bar and back closes it, H12 the
 * "Continue" widget is offered to the launcher.
 */
@RunWith(AndroidJUnit4::class)
class HomeNavigationDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun log(s: String) = Log.i("YuHomeNavTest", s)
    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun waitTag(tag: String, ms: Long = 30_000) {
        val ok = runCatching { rule.waitUntil(ms) { has(tag) }; true }.getOrDefault(false)
        assertTrue("no '$tag' after ${ms}ms · ${FirstRun.onScreen(rule)}", ok)
    }
    private fun waitGone(tag: String, ms: Long = 10_000) {
        val ok = runCatching { rule.waitUntil(ms) { !has(tag) }; true }.getOrDefault(false)
        assertTrue("'$tag' still shown after ${ms}ms", ok)
    }
    private fun waitPage(p: Int, ms: Long = 8_000) {
        val ok = runCatching { rule.waitUntil(ms) { TabPagerProbe.page == p }; true }.getOrDefault(false)
        assertTrue("tab page ${TabPagerProbe.page}, wanted $p · ${FirstRun.onScreen(rule)}", ok)
    }
    private fun tap(tag: String) = FirstRun.tapVisible(rule, tag)
    private fun back() { FirstRun.shell("input keyevent 4") }

    @Before fun setUp() {
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        runCatching { ui.grantRuntimePermission(ctx.packageName, "android.permission.POST_NOTIFICATIONS") }
        FirstRun.toTabs(rule)
    }

    @Test fun h1_h4_everyTabOpensAndBackReturnsHome() {
        waitTag("home_screen_root")
        val tabs = listOf("nav_tab_home", "nav_tab_music", "nav_tab_videos", "nav_tab_library")
        tabs.forEach { waitTag(it) }
        for ((i, t) in tabs.withIndex().drop(1)) {
            tap(t); waitPage(i); log("tab $t → page $i")
        }
        back(); waitPage(0)
        waitTag("home_screen_root")
    }

    @Test fun h2_continueVideoCardOpensVideosWhenEmpty() {
        // Fresh data: nothing watched, so the card says "Open videos" and goes to the Videos tab.
        waitTag("home_continue_video")
        tap("home_continue_video_play")
        waitPage(2, 15_000)
        log("continue video (empty) → page ${TabPagerProbe.page}")
        back(); waitPage(0)
    }

    @Test fun h3_navBarShortcutOpensItsPage() {
        val inst = InstrumentationRegistry.getInstrumentation()
        try {
            inst.runOnMainSync { dev.asfakul.yumeiro.ui.navigation.NavPrefs.setShortcut(ctx, dev.asfakul.yumeiro.ui.navigation.NavPrefs.Shortcut.INBOX, true) }
            waitTag("nav_shortcut_inbox", 15_000)
            tap("nav_shortcut_inbox")
            waitTag("inbox_screen")
            back(); waitGone("inbox_screen")
        } finally {
            inst.runOnMainSync { dev.asfakul.yumeiro.ui.navigation.NavPrefs.setShortcut(ctx, dev.asfakul.yumeiro.ui.navigation.NavPrefs.Shortcut.INBOX, false) }
        }
    }

    @Test fun h5_tvLayoutShowsTheRailAndDpadMovesTabs() {
        val inst = InstrumentationRegistry.getInstrumentation()
        try {
            inst.runOnMainSync { dev.asfakul.yumeiro.ui.tv.TvMode.set(ctx, dev.asfakul.yumeiro.ui.tv.TvMode.Pref.TV) }
            waitTag("tv_nav_rail", 15_000)
            waitTag("tv_nav_tab_music")
            // D-pad: tap gives the rail item focus on a touch emulator, then centre selects.
            tap("tv_nav_tab_music"); waitPage(1, 15_000)
            FirstRun.shell("input keyevent KEYCODE_DPAD_DOWN"); Thread.sleep(600)
            FirstRun.shell("input keyevent KEYCODE_DPAD_CENTER")
            val moved = runCatching { rule.waitUntil(10_000) { TabPagerProbe.page != 1 }; true }.getOrDefault(false)
            log("tv rail: dpad down + centre from page 1 → page ${TabPagerProbe.page} moved=$moved")
            assertTrue("D-pad down + centre on the rail did not change tab (page ${TabPagerProbe.page}) · ${FirstRun.onScreen(rule)}", moved)
        } finally {
            inst.runOnMainSync { dev.asfakul.yumeiro.ui.tv.TvMode.set(ctx, dev.asfakul.yumeiro.ui.tv.TvMode.Pref.AUTO) }
        }
    }

    @Test fun h7_viewIntentForAWebVideoOpensThePlayer() {
        val url = "https://archive.org/download/f20110625mad/f20110625mad.mp4"
        val out = FirstRun.shell("am start -W -a android.intent.action.VIEW -d $url -t video/mp4 -n ${ctx.packageName}/dev.asfakul.yumeiro.MainActivity")
        log("am start: ${out.trim().take(200)}")
        waitTag("video_player_root", 60_000)
        // A key back first hides the controls (TV rule, VideoPlayerModal), the next one leaves
        // the full player; the default back action keeps the video in the floating window.
        var presses = 0
        while (presses < 3 && !has("video_floating_player")) { back(); presses++; Thread.sleep(1_200) }
        log("left the full player after $presses back presses; floating=${has("video_floating_player")}")
        waitTag("video_floating_player", 10_000)
        tap("floating_close"); waitGone("video_player_root", 15_000)
    }

    @Test fun h8_openTabSurvivesRecreate() {
        tap("nav_tab_library"); waitPage(3)
        rule.activityRule.scenario.recreate()
        FirstRun.toTabs(rule)
        waitPage(3, 15_000)
        log("after recreate page=${TabPagerProbe.page}")
    }

    @Test fun h13_inboxOpensFromTheTopBarAndBackCloses() {
        waitTag("topbar_inbox_action")
        tap("topbar_inbox_action")
        waitTag("inbox_screen")
        back(); waitGone("inbox_screen")
        assertEquals(0, TabPagerProbe.page)
    }

    @Test fun h10_topBarSearchSuggestsARecentSearch() {
        dev.asfakul.yumeiro.ui.components.RecentSearches.add(ctx, "library", "yumeiro nightfall")
        waitTag("topbar_search_action")
        tap("topbar_search_action")
        waitTag("topbar_search_input")
        // Compose text input, not `adb input text`: run 43688049790 (api36 run 2) lost the shell
        // keystrokes when they arrived before the field had focus.
        rule.onNodeWithTag("topbar_search_input", true).performTextInput("yumei")
        // Ranked off the main thread after a 120 ms pause in typing (MTPlayerApp produceState).
        val ok = runCatching { rule.waitUntil(10_000) { has("topbar_suggestions") }; true }.getOrDefault(false)
        val typed = runCatching { rule.onNodeWithTag("topbar_search_input", true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text }.getOrNull()
        log("h10: field text='$typed' suggestions=$ok")
        assertTrue("no 'topbar_suggestions' for field text '$typed' · ${FirstRun.onScreen(rule)}", ok)
        tap("search_close_button")
        waitGone("topbar_search_input")
    }

    @Test fun h12_continueWidgetIsOffered() {
        val providers = AppWidgetManager.getInstance(ctx).installedProviders.filter { it.provider.packageName == ctx.packageName }
        log("widgets: ${providers.map { it.provider.className }}")
        assertTrue("no Continue widget in ${providers.map { it.provider.className }}", providers.any { it.provider.className.endsWith("ContinueWidget") || it.provider.className.contains("Continue") })
    }
}
