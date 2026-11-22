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
 * 2 India channels (only the iptv-org tvg-id suffix ".in"). Live TV shows the country chips; each chip shows only
 * its channels; Any country shows all. Other playlists are switched off for the test and back on after.
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

    @Test fun countryChipsShowOnlyThatCountry() {
        compose.setContent { MTPlayerTheme { StreamScreen() } }
        val bar = runCatching { compose.waitUntil(25_000) { has("live_countries") }; true }.getOrDefault(false)
        val chips = compose.onAllNodes(SemanticsMatcher("country chip") { (it.config.getOrNull(SemanticsProperties.TestTag) ?: "").startsWith("live_country_") }, true)
            .fetchSemanticsNodes().mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }.sorted()
        log("CTY_CASE bar=$bar chips=$chips")
        assertTrue("Country: no country chips for a BD + IN playlist", bar)
        assertEquals(listOf("live_country_BD", "live_country_IN", "live_country_all"), chips)
        pick("live_country_BD")
        val bd = shows("YuCty Dhaka") to shows("YuCty Mumbai")
        FirstRun.shot("cty_bd")
        pick("live_country_IN")
        val inn = shows("YuCty Dhaka") to shows("YuCty Mumbai")
        FirstRun.shot("cty_in")
        pick("live_country_all")
        val all = shows("YuCty Dhaka") to shows("YuCty Mumbai")
        log("CTY_CASE bd=$bd in=$inn all=$all")
        assertEquals("Bangladesh shows only Dhaka channels", true to false, bd)
        assertEquals("India (from the tvg-id suffix) shows only Mumbai channels", false to true, inn)
        assertEquals("Any country shows both", true to true, all)
    }
}
