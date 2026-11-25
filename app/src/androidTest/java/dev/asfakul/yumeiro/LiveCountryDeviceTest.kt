/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.stream.PlaylistStore
import dev.asfakul.yumeiro.ui.screens.stream.StreamScreen
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Country filter on a device: a remote M3U (local server) with 3 Bangladesh channels (tvg-country="BD") and
 * 2 India channels (only the iptv-org tvg-id suffix ".in"). Live TV shows a Country button; its sheet lists BD and IN;
 * each shows only its channels; Every country shows all; search finds a channel by name (LIVE-1). Other playlists are switched off for the test and back on after.
 * Lines: CTY_CASE ….
 */
@RunWith(AndroidJUnit4::class)
class LiveCountryDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(s: String) = Log.i("YuCountry", s)
    private fun has(tag: String) = compose.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun shows(t: String) = compose.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private lateinit var server: MockWebServer
    private var mine: String? = null
    private var wasOn = emptyList<String>()

    @Before fun setUp() {
        val m3u = buildString {
            append("#EXTM3U\n")
            for (i in 1..3) append("#EXTINF:-1 tvg-country=\"BD\" group-title=\"News\",YuCty Dhaka $i\nhttp://127.0.0.1:9/bd$i.m3u8\n")
            for (i in 1..2) append("#EXTINF:-1 tvg-id=\"YuCtyMumbai$i.in\" group-title=\"News\",YuCty Mumbai $i\nhttp://127.0.0.1:9/in$i.m3u8\n")
        }
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse.Builder().addHeader("Content-Type", "audio/x-mpegurl").body(m3u).build()
        }
        server.start()
        runBlocking {
            PlaylistStore.init(ctx)
            wasOn = PlaylistStore.playlists.value.filter { it.enabled }.map { it.id }
            wasOn.forEach { PlaylistStore.setEnabled(ctx, it, false) }
            mine = PlaylistStore.addRemote(ctx, "YuCty test", server.url("/cty.m3u").toString()).id
        }
    }

    @After fun tearDown() {
        runBlocking {
            mine?.let { runCatching { PlaylistStore.delete(ctx, it) } }
            wasOn.forEach { runCatching { PlaylistStore.setEnabled(ctx, it, true) } }
        }
        runCatching { server.close() }
    }

    private fun pick(tag: String) { compose.onNodeWithTag(tag, true).performClick(); compose.waitForIdle(); Thread.sleep(300); compose.waitForIdle() }

    private fun openSheet() { pick("live_country_button"); compose.waitUntil(5_000) { has("live_country_sheet") } }

    // LIVE-1 (owner list R21, item 1): the country chip row became a Country sheet behind a header button.
    @Test fun countryChipsShowOnlyThatCountry() {
        compose.setContent { MTPlayerTheme { StreamScreen() } }
        val button = runCatching { compose.waitUntil(25_000) { has("live_country_button") }; true }.getOrDefault(false)
        log("CTY_CASE button=$button")
        assertTrue("Country: no country button for a BD + IN playlist", button)
        openSheet()
        val rows = compose.onAllNodes(SemanticsMatcher("country row") { (it.config.getOrNull(SemanticsProperties.TestTag) ?: "").startsWith("live_country_") }, true)
            .fetchSemanticsNodes().mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }.filter { it != "live_country_sheet" && it != "live_country_button" }.sorted()
        log("CTY_CASE rows=$rows")
        FirstRun.shot("cty_sheet")
        assertEquals(listOf("live_country_BD", "live_country_IN", "live_country_any"), rows)
        pick("live_country_BD")
        compose.waitUntil(5_000) { !has("live_country_sheet") }
        val bd = shows("YuCty Dhaka") to shows("YuCty Mumbai")
        FirstRun.shot("cty_bd")
        openSheet(); pick("live_country_IN")
        compose.waitUntil(5_000) { !has("live_country_sheet") }
        val inn = shows("YuCty Dhaka") to shows("YuCty Mumbai")
        FirstRun.shot("cty_in")
        openSheet(); pick("live_country_any")
        compose.waitUntil(5_000) { !has("live_country_sheet") }
        val all = shows("YuCty Dhaka") to shows("YuCty Mumbai")
        log("CTY_CASE bd=$bd in=$inn all=$all")
        assertEquals("Bangladesh shows only Dhaka channels", true to false, bd)
        assertEquals("India (from the tvg-id suffix) shows only Mumbai channels", false to true, inn)
        assertEquals("Every country shows both", true to true, all)
    }

    @Test fun searchFindsTheChannel() {
        compose.setContent { MTPlayerTheme { StreamScreen() } }
        compose.waitUntil(25_000) { has("live_search_button") && shows("YuCty Dhaka") }
        pick("live_search_button")
        compose.onNodeWithTag("live_search_field", true).performTextInput("mumbai 2")
        compose.waitUntil(5_000) { !shows("YuCty Dhaka") }
        FirstRun.shot("cty_search")
        log("CTY_CASE search m2=${shows("YuCty Mumbai 2")} m1=${shows("YuCty Mumbai 1")}")
        assertTrue(shows("YuCty Mumbai 2"))
    }
}
