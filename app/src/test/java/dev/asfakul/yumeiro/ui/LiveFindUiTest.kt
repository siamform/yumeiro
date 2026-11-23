/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.asfakul.yumeiro.stream.M3uParser
import dev.asfakul.yumeiro.stream.PlaylistStore
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LIVE-1 (owner list R21, item 1) on the real Live TV page, fed a trimmed copy of the real Yumeiro Live playlist
 * (group-title = country): one header search icon, a Country sheet instead of the country chip row, and a genre
 * row (from tvg-category) instead of a second row of countries.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class LiveFindUiTest {
    @get:Rule val rule = createComposeRule()
    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun shows(t: String) = rule.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun click(tag: String) { rule.onNodeWithTag(tag, true).performClick(); rule.waitForIdle() }

    @Before fun setUp() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        java.io.File(ctx.filesDir, "streams").deleteRecursively()
        PlaylistStore.resetForTests()
        val m3u = requireNotNull(javaClass.classLoader!!.getResource("live/yumeiro_live_sample.m3u")).readText()
        kotlinx.coroutines.runBlocking {
            PlaylistStore.init(ctx)
            PlaylistStore.playlists.value.forEach { PlaylistStore.delete(ctx, it.id) }
            PlaylistStore.createLocal(ctx, "Yumeiro Live sample", M3uParser.parse(m3u))
        }
        rule.setContent { MTPlayerTheme { dev.asfakul.yumeiro.ui.screens.stream.StreamScreen() } }
        rule.waitUntil(10_000) { has("live_channel") }
    }

    @Test fun countriesMoveToASheetAndGenresTakeTheRow() {
        assertTrue("country button in the header", has("live_country_button"))
        assertTrue("search icon in the header", has("live_search_button"))
        assertFalse("no permanent search bar", has("live_search_field"))
        assertFalse("country chips no longer repeat as a category row", has("live_categories"))
        assertTrue("genre row from tvg-category", has("live_genres"))
        assertTrue(has("live_genre_News"))

        click("live_country_button")
        rule.waitUntil(5_000) { has("live_country_sheet") }
        assertTrue(has("live_country_any"))
        rule.onNodeWithTag("live_country_search", true).performTextInput("bangla")
        rule.waitForIdle()
        assertTrue(has("live_country_Bangladesh"))
        assertFalse(has("live_country_India"))
        click("live_country_Bangladesh")
        rule.mainClock.advanceTimeBy(1_000); rule.waitForIdle()
        rule.waitUntil(5_000) { !has("live_country_sheet") }
        assertTrue("Bangladesh channel shown", shows("ATN Bangla"))
        assertFalse("US channel hidden", shows("24 Hour Free Movies"))
        assertTrue("header shows the picked country", shows("Bangladesh"))

        click("live_country_button")
        rule.waitUntil(5_000) { has("live_country_sheet") }
        assertTrue("recent country remembered", has("live_country_recent_Bangladesh") || has("live_country_Bangladesh"))
        click("live_country_any")
        rule.mainClock.advanceTimeBy(1_000); rule.waitForIdle()
        rule.waitUntil(5_000) { !has("live_country_sheet") }
        rule.waitUntil(5_000) { shows("24 Hour Free Movies") }
    }

    @Test fun searchFindsAChannelAndExplainsNoResults() {
        click("live_search_button")
        rule.waitUntil(5_000) { has("live_search_field") }
        rule.onNodeWithTag("live_search_field", true).performTextInput("atn bangla")
        rule.mainClock.advanceTimeBy(1_000); rule.waitForIdle()
        rule.waitUntil(5_000) { shows("ATN Bangla") && !shows("24 Hour Free Movies") }
        assertTrue(has("live_search_count"))

        click("live_search_close")
        rule.waitUntil(5_000) { !has("live_search_field") }
        click("live_search_button")
        rule.waitUntil(5_000) { has("live_search_field") }
        rule.onNodeWithTag("live_search_field", true).performTextInput("zzzz no such channel")
        rule.mainClock.advanceTimeBy(1_000); rule.waitForIdle()
        rule.waitUntil(5_000) { has("live_search_empty") }
        click("live_search_clear")
        rule.waitUntil(5_000) { shows("24 Hour Free Movies") }
    }
}
