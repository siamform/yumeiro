/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import dev.asfakul.yumeiro.ui.tv.LocalTvMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import org.junit.runner.RunWith

/**
 * Release 2026-11-19 features on a real Android build (phones and TV images): F-17 statistics page, TV-4 search
 * online, F-10 glass tier, F-12 Shorts player unhappy path (a Short that can't resolve shows Retry; Close works),
 * F-04 site search. Each test drives the UI with taps / keys and reads the result. Log tag YuRelease.
 * Old code: none of these tags exist.
 */
@RunWith(AndroidJUnit4::class)
class ReleaseFeaturesDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun has(tag: String) = compose.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun waitTag(tag: String, ms: Long = 10_000) =
        runCatching { compose.waitUntil(ms) { has(tag) }; true }.getOrDefault(false)
    private fun log(s: String) = Log.i("YuRelease", s)
    private val tv get() = Edition.isTv

    /** F-17: the page opens, the map is drawn, a tap on today shows that day in words (not colour alone). */
    @Test fun statsPageShowsTheMapAndTheSelectedDay() {
        dev.asfakul.yumeiro.stats.ActivityLog.ensureLoaded(ctx)
        compose.setContent { CompositionLocalProvider(LocalTvMode provides tv) { MTPlayerTheme { dev.asfakul.yumeiro.ui.screens.stats.StatsScreen(onBack = {}) } } }
        assertTrue("no stats_screen", waitTag("stats_screen"))
        compose.onAllNodesWithTag("stats_screen", true).onFirst().performScrollToNode(hasTestTag("stats_activity_map"))
        assertTrue("no activity map", waitTag("stats_activity_map"))
        compose.onAllNodesWithTag("stats_activity_map", true).onFirst().performTouchInput { click(centerLeft) }
        assertTrue("no selected-day text", waitTag("stats_selected_day"))
        FirstRun.shot("release_stats")
        log("RELEASE stats ok")
    }

    /** TV-4: a committed library search offers "Search online" and hands over the same words. */
    @Test fun homeSearchOffersSearchOnline() {
        var handed: String? = null
        compose.setContent {
            CompositionLocalProvider(LocalTvMode provides tv) {
                MTPlayerTheme {
                    var q by remember { mutableStateOf("") }
                    dev.asfakul.yumeiro.ui.components.MTTopBar(
                        title = "Home", searchQuery = q, onSearchQueryChange = { q = it }, onSettingsClick = {}, onInboxClick = {},
                        onSearchOnline = { handed = it },
                    )
                }
            }
        }
        compose.onAllNodesWithTag("topbar_search_action", true).onFirst().performClick()
        assertTrue("search box did not open", waitTag("topbar_search_input"))
        compose.onAllNodesWithTag("topbar_search_input", true).onFirst().performTextInput("frieren")
        compose.onAllNodesWithTag("topbar_search_input", true).onFirst().performImeAction()
        assertTrue("no Search online button after Search", waitTag("topbar_search_online"))
        FirstRun.shot("release_search_online")
        compose.onAllNodesWithTag("topbar_search_online", true).onFirst().performClick()
        compose.waitForIdle()
        log("RELEASE tv4 handed=$handed tv=$tv")
        assertEquals("frieren", handed)
    }

    /** F-10 (ADR-017): low-RAM phones and TV always get the solid tint, never see-through bars. */
    @Test fun glassTierIsSolidOnLowRamAndTv() {
        DeviceClass.init(ctx)
        val tier = dev.asfakul.yumeiro.ui.theme.GlassTier.current(ctx)
        log("RELEASE glass tier=$tier lowEnd=${DeviceClass.lowEnd} tv=$tv sdk=${android.os.Build.VERSION.SDK_INT}")
        if (DeviceClass.lowEnd || tv) assertEquals(dev.asfakul.yumeiro.ui.theme.GlassTier.TINT, tier)
        val before = dev.asfakul.yumeiro.ui.theme.ThemeSettings.mode.value
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { dev.asfakul.yumeiro.ui.theme.ThemeSettings.setMode(ctx, dev.asfakul.yumeiro.ui.theme.ThemeMode.GLASS) }
            compose.setContent { CompositionLocalProvider(LocalTvMode provides tv) { MTPlayerTheme { dev.asfakul.yumeiro.ui.screens.stats.StatsScreen(onBack = {}) } } }
            assertTrue("Kasumi theme did not render a page", waitTag("stats_screen"))
            FirstRun.shot("release_kasumi")
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { dev.asfakul.yumeiro.ui.theme.ThemeSettings.setMode(ctx, before) }
        }
    }

    /** F-12: a Short that can't resolve shows Retry (no endless spinner), and Close leaves at once. Phones only. */
    @Test fun shortsPlayerUnresolvableShortShowsRetryAndCloses() {
        if (tv) { log("RELEASE shorts skipped on TV (phones only, ADR-018)"); return }
        var closed = false
        val bad = dev.asfakul.yumeiro.youtube.YtItem("zzzzzzzzzzz", "https://www.youtube.com/shorts/zzzzzzzzzzz", "No such Short", "Test")
        compose.setContent {
            MTPlayerTheme {
                var open by remember { mutableStateOf(true) }
                Box(Modifier.fillMaxSize()) {
                    if (open) dev.asfakul.yumeiro.ui.screens.youtube.ShortsPlayerDialog(listOf(bad), 0, onDismiss = { open = false; closed = true }, onFullPlayer = {})
                }
            }
        }
        assertTrue("no shorts_player", waitTag("shorts_player"))
        val retry = waitTag("shorts_retry", 60_000)
        FirstRun.shot("release_shorts_retry")
        log("RELEASE shorts retry=$retry")
        assertTrue("an unplayable Short never showed Retry within 60 s", retry)
        compose.onAllNodesWithTag("shorts_close", true).onFirst().performClick()
        compose.waitUntil(5_000) { closed }
    }

    /** F-04: with six or more sites a search box filters the list; no match says so. */
    @Test fun siteListSearchFilters() {
        val s = dev.asfakul.yumeiro.online.web.SavedSites
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            s.init(ctx)
            listOf("alpha", "bravo", "charlie", "delta", "echo", "foxtrot").forEach { s.add(ctx, "https://$it.example.org") }
        }
        try {
            compose.setContent { CompositionLocalProvider(LocalTvMode provides tv) { MTPlayerTheme { dev.asfakul.yumeiro.ui.screens.sites.WebSitesScreen(onBack = {}) } } }
            assertTrue("no site_search with 6 sites", waitTag("site_search"))
            compose.onAllNodesWithTag("site_search", true).onFirst().performTextInput("delta")
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("site_row", true).fetchSemanticsNodes().size == 1 }
            compose.onAllNodesWithTag("site_search", true).onFirst().performTextInput("zzz")
            assertTrue("no 'no match' line", waitTag("site_no_match"))
            FirstRun.shot("release_site_search")
            log("RELEASE sites ok")
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                listOf("alpha", "bravo", "charlie", "delta", "echo", "foxtrot").forEach { s.remove(ctx, "https://$it.example.org") }
            }
        }
    }
}
