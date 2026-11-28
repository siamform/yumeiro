/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.OnlineSettings
import dev.asfakul.yumeiro.player.PlaybackDiagnostics
import dev.asfakul.yumeiro.stream.IncomingMedia
import dev.asfakul.yumeiro.stream.Resolution
import dev.asfakul.yumeiro.stream.StreamRequest
import dev.asfakul.yumeiro.stream.StreamResolver
import dev.asfakul.yumeiro.ui.components.AppMessageHost
import dev.asfakul.yumeiro.ui.screens.settings.TrackingScreen
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import dev.asfakul.yumeiro.ui.theme.ThemeMode
import dev.asfakul.yumeiro.ui.theme.ThemeSettings
import dev.asfakul.yumeiro.ui.tv.LocalTvMode
import dev.asfakul.yumeiro.ui.navigation.NavPrefs
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * GROUP Z (beta r27, B15–B19) captures for the mockups: the real app in the dark theme on the real network.
 * Sports with ESPN's real matches and a real match page, a real public Telegram post playing from its link,
 * the Tracking page with its "?" help, the Trakt steps and the SubDL key form answered by the real SubDL.
 * Each capture waits for what it claims (`R27_SHOT name ok|missing`, fails when missing). Not a feature test.
 * Shots: r27_<phone|tv>_<name>. Log tag YuR27Shots.
 */
@RunWith(AndroidJUnit4::class)
class GroupZR27ShotsDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val form get() = if (ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) "tv" else "phone"
    private fun log(s: String) = Log.i("YuR27Shots", s)
    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(ms: Long, ok: () -> Boolean) = runCatching { rule.waitUntil(ms) { runCatching(ok).getOrDefault(false) }; true }.getOrDefault(false)
    private fun click(tag: String) { rule.onAllNodesWithTag(tag, true)[0].performSemanticsAction(SemanticsActions.OnClick); rule.waitForIdle() }
    private var modeBefore: ThemeMode? = null

    private fun snap(name: String, shows: Boolean) {
        SystemClock.sleep(700); rule.waitForIdle()
        FirstRun.shot("r27_${form}_$name")
        log("R27_SHOT ${form}_$name ${if (shows) "ok" else "missing"}")
        assertTrue("capture $name does not show what it claims", shows)
    }

    @Before fun dark() {
        ThemeSettings.init(ctx); modeBefore = ThemeSettings.mode.value
        rule.runOnUiThread { ThemeSettings.setMode(ctx, ThemeMode.DARK) }
    }

    @After fun tearDown() {
        runCatching { NavPrefs.reset(ctx) }
        runCatching { IncomingMedia.consume() }
        modeBefore?.let { m -> runCatching { ThemeSettings.setMode(ctx, m) } }
    }

    private fun board() = waitFor(45_000) { has("sports_list") || has("sports_empty") || has("sports_error") }

    @Test fun a_sports() {
        NavPrefs.reset(ctx)
        NavPrefs.setShortcut(ctx, NavPrefs.Shortcut.LIVE, true)
        FirstRun.toTabs(rule)
        FirstRun.clickTag(rule, FirstRun.navTag(rule, "nav_shortcut_live"))
        assertTrue(waitFor(30_000) { has("live_tools_button") })
        click("live_tools_button")
        assertTrue(waitFor(10_000) { has("live_tool_sports") })
        snap("live_tools", has("live_tool_sports"))
        click("live_tool_sports")
        assertTrue(waitFor(10_000) { has("sports_page") })
        board(); snap("sports_football", has("sports_match"))
        click("sports_day_-1"); board()
        rule.onAllNodesWithTag("sports_match", true)[0].performSemanticsAction(SemanticsActions.OnClick); rule.waitForIdle()
        waitFor(30_000) { listOf("sports_detail_stat", "sports_detail_moment", "sports_detail_form", "sports_detail_periods").any { has(it) } }
        snap("sports_match", has("sports_match_sheet") && !has("sports_detail_failed"))
        androidx.test.espresso.Espresso.pressBack(); rule.waitForIdle()
        waitFor(10_000) { !has("sports_match_sheet") }
        click("sports_day_0")
        rule.onNodeWithTag("sports_sports", true).performScrollToNode(hasTestTag("sports_sport_cricket")); rule.waitForIdle()
        click("sports_sport_cricket"); board()
        snap("sports_cricket", has("sports_match") || has("sports_empty"))
    }

    @Test fun b_telegramPostPlays() {
        FirstRun.toTabs(rule)
        // A public SpaceX launch video (no people's faces up close), opened as a plain t.me link.
        val r = runBlocking { StreamResolver.resolve(ctx, StreamRequest("https://t.me/SpaceX/10493")) }
        log("telegram resolve $r")
        assertTrue("the public post did not resolve: $r", r is Resolution.Playable)
        PlaybackDiagnostics.reset("")
        rule.runOnUiThread { IncomingMedia.open(ctx, (r as Resolution.Playable).request) }
        waitFor(60_000) { PlaybackDiagnostics.state.value.firstFrame }
        SystemClock.sleep(6_000)
        snap("telegram_playing", PlaybackDiagnostics.state.value.firstFrame && has("video_player_root"))
        rule.runOnUiThread {
            androidx.lifecycle.ViewModelProvider(rule.activity)[dev.asfakul.yumeiro.ui.viewmodel.MainViewModel::class.java].closeVideoPlayer()
        }
    }
}

/** The Tracking page captures (B18/B19), dark theme, phone and TV layout. Shots r27_<form>_tracking*. */
@RunWith(AndroidJUnit4::class)
class GroupZR27TrackingShotsDeviceTest {
    @get:Rule val rule = createComposeRule()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val tv get() = ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    private val form get() = if (tv) "tv" else "phone"
    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(ms: Long, ok: () -> Boolean) = runCatching { rule.waitUntil(ms) { runCatching(ok).getOrDefault(false) }; true }.getOrDefault(false)
    private var modeBefore: ThemeMode? = null

    private fun snap(name: String, shows: Boolean) {
        SystemClock.sleep(600); rule.waitForIdle()
        FirstRun.shot("r27_${form}_$name")
        Log.i("YuR27Shots", "R27_SHOT ${form}_$name ${if (shows) "ok" else "missing"}")
        assertTrue("capture $name does not show what it claims", shows)
    }
    private fun tap(tag: String) {
        runCatching { rule.onNodeWithTag(tag, true).performScrollTo() }
        rule.waitForIdle(); rule.onNodeWithTag(tag, true).performClick(); rule.waitForIdle()
    }

    @Before fun setUp() {
        ThemeSettings.init(ctx); modeBefore = ThemeSettings.mode.value
        ThemeSettings.setMode(ctx, ThemeMode.DARK)
        OnlineSettings.init(ctx)
        OnlineSettings.setSubdlKey(ctx, ""); OnlineSettings.setSecret(ctx, "subdl_user", null)
        dev.asfakul.yumeiro.tracking.Kitsu.signOut(ctx)
        rule.setContent { CompositionLocalProvider(LocalTvMode provides tv) { MTPlayerTheme { Box(Modifier.fillMaxSize().background(dev.asfakul.yumeiro.ui.theme.DarkBackground)) { TrackingScreen(onBack = {}); AppMessageHost() } } } }
        assertTrue(waitFor(20_000) { has("tracking_screen") })
    }

    @After fun tearDown() { modeBefore?.let { m -> runCatching { ThemeSettings.setMode(ctx, m) } } }

    @Test fun a_trackingPageHelpAndSubdl() {
        snap("tracking", has("tracker_anilist_help"))
        tap("tracker_trakt_help")
        assertTrue(waitFor(10_000) { has("tracker_trakt_help_dialog") })
        snap("tracking_trakt_help", has("guide_trakt_body"))
        tap("tracker_trakt_help_ok")
        waitFor(5_000) { !has("tracker_trakt_help_dialog") }
        runCatching { rule.onNodeWithTag("tracker_subdl", true).performScrollTo() }
        snap("tracking_subdl_card", has("tracker_subdl"))
        tap("tracker_subdl_signin")
        assertTrue(waitFor(10_000) { has("subdl_key_field") })
        rule.onNodeWithTag("subdl_key_field", true).performTextInput("not-a-real-subdl-key-r27")
        runCatching { androidx.test.espresso.Espresso.closeSoftKeyboard() }
        tap("subdl_key_save")
        snap("tracking_subdl_checked", waitFor(45_000) { has("subdl_key_error") })
    }
}
