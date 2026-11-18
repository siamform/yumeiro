/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.stream.PlaylistStore
import dev.asfakul.yumeiro.ui.components.AppMessageHost
import dev.asfakul.yumeiro.ui.screens.stream.StreamScreen
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * NBUG-15 (d) (NB15D-1, NB15D-2, ADR-027) on the real Live TV screen, playlist from a local server.
 *
 *  a: a channel played from the Live TV hub shows in the \"Recently played\" row on the Live TV feed
 *     (`live_recent_row`) and NOT in the Play Link page's recents. Old code: no row, and the channel is
 *     listed under Play Link \"Recent streams\".
 *  b: a link played from the Play Link page shows in its recents and not in the Live TV row.
 *
 * Nothing is consumed by a player here (no activity host), so a stream is only \"opened\" (recorded).
 * Log tag YuNb15: NB15_RESULT.
 */
@RunWith(AndroidJUnit4::class)
class Nbug15RecentsDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var server: MockWebServer
    private var id: String? = null
    private val run = (System.currentTimeMillis() % 100_000).toString()
    private val chOne = "YuRec One $run"
    private val chTwo = "YuRec Two $run"

    private fun log(s: String) = Log.i("YuNb15", s)
    private fun has(tag: String) = compose.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun hasText(t: String) = compose.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun inside(tag: String, text: String) = compose.onAllNodes(hasText(text, substring = true) and hasAnyAncestor(hasTestTag(tag)), true).fetchSemanticsNodes().isNotEmpty()
    private fun wait(what: String, ms: Long = 30_000, cond: () -> Boolean) {
        val ok = runCatching { compose.waitUntil(ms) { cond() }; true }.getOrDefault(false)
        if (!ok) FirstRun.shot("nb15_fail_" + what.filter { it.isLetterOrDigit() }.take(30))
        assertTrue("$what (after ${ms}ms)", ok)
    }

    @Before fun setUp() {
        server = MockWebServer()
        val m3u = "#EXTM3U\n#EXTINF:-1 group-title=\"News\",$chOne\nhttp://127.0.0.1:9/one-$run.m3u8\n" +
            "#EXTINF:-1 group-title=\"News\",$chTwo\nhttp://127.0.0.1:9/two-$run.m3u8\n"
        server.dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: mockwebserver3.RecordedRequest): MockResponse =
                MockResponse.Builder().addHeader("Content-Type", "audio/x-mpegurl").body(m3u).build()
        }
        server.start()
        val p = runBlocking { PlaylistStore.init(ctx); PlaylistStore.addRemote(ctx, "YuRec list $run", server.url("/list.m3u").toString()) }
        id = p.id
        compose.setContent { Box(Modifier.fillMaxSize()) { StreamScreen(); AppMessageHost() } }
    }

    @After fun tearDown() {
        id?.let { p -> runCatching { runBlocking { PlaylistStore.delete(ctx, p) } } }
        runCatching { server.close() }
    }

    private fun showList() {
        val strip = "live_strip_$id"
        wait("playlist in the strip") { has(strip) }
        compose.onAllNodesWithTag(strip, true)[0].performClick()
        wait("channels of the test playlist") { hasText(chOne) }
    }

    private fun toAll() {
        wait("All in the strip") { has("live_strip_all") }
        compose.onAllNodesWithTag("live_strip_all", true)[0].performClick()
        compose.waitForIdle()
        runCatching { compose.onAllNodesWithTag("stream_screen", true)[0].performScrollToIndex(0) }
    }

    private fun openLinkPage() {
        wait("Play link in the header") { has("live_header_link") }
        compose.onAllNodesWithTag("live_header_link", true)[0].performClick()
        wait("Play Link page") { has("stream_link_page") }
    }

    private fun backToHub() {
        compose.onAllNodesWithTag("stream_link_back", true)[0].performClick()
        wait("Live TV hub") { has("stream_screen") }
    }

    @Test fun a_liveChannelGoesToTheLiveRowNotPlayLink() {
        showList()
        compose.onAllNodesWithText(chOne, useUnmergedTree = true)[0].performClick()
        compose.waitForIdle()
        toAll()
        val row = runCatching { compose.waitUntil(15_000) { inside("live_recent_row", chOne) }; true }.getOrDefault(false)
        FirstRun.shot("nb15_a_hub")
        openLinkPage()
        compose.waitForIdle()
        val inLink = inside("stream_link_page", chOne)
        FirstRun.shot("nb15_a_link")
        log("NB15_RESULT a liveRow=$row inPlayLink=$inLink")
        assertTrue("NB15D-2: the channel played from Live TV is not in a 'Recently played' row on the Live TV feed", row)
        assertTrue("NB15D-1: the channel played from Live TV is listed in Play Link recents", !inLink)
    }

    @Test fun b_playLinkStaysInPlayLink() {
        val link = "rtsp://127.0.0.1:9/yurec-link-$run"
        openLinkPage()
        compose.onAllNodesWithTag("stream_url", true)[0].performTextInput(link)
        compose.onAllNodesWithTag("stream_play", true)[0].performClick()
        compose.waitForIdle()
        val inLink = runCatching { compose.waitUntil(10_000) { inside("stream_link_page", "yurec-link-$run") }; true }.getOrDefault(false)
        FirstRun.shot("nb15_b_link")
        backToHub()
        toAll()
        compose.waitForIdle()
        val inRow = inside("live_recent_row", "yurec-link-$run")
        log("NB15_RESULT b inPlayLink=$inLink liveRow=$inRow")
        assertTrue("a link played from Play Link is missing from its recents", inLink)
        assertTrue("a link played from Play Link shows in the Live TV row", !inRow)
    }
}
