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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.stream.PlaylistStore
import dev.asfakul.yumeiro.ui.components.AppMessageHost
import dev.asfakul.yumeiro.ui.screens.stream.StreamScreen
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * NBUG-15 (a)-(c) (NB15-A, B1-B3, C1-C2; ADR-028) on the real Live TV screen. Two playlists from a local
 * server; every other playlist is turned off for the test and back on after it.
 *
 * A: 30 "News", 40 "Sports;HD". B: 10 "news", 40 without a group, and one copy of A's first News stream.
 * Merged: News 40, Sports 40, Other 40 = 120 channels (the copy shows once).
 *
 *  a: the header has a Filter button; its "Show videos from" sheet lists both playlists, search narrows the
 *     list, a pin puts B first and adds a one-tap chip, and picking A shows only A's channels.
 *  b: the feed shows no playlist name and no "View"; channels are grouped by category across both
 *     playlists (News holds A's and B's); 50 show first, then 50 more each time the end is reached.
 *  c: the header has a Tools button; its modal holds every tool, including Playlists, which opens the
 *     playlist management page where a playlist can be turned off.
 *
 * Old code: a stories strip, one section per playlist with its name and "View", tools and playlist cards on
 * the feed, no Filter or Tools button. Log tag YuNb15R: NB15R_RESULT.
 */
@RunWith(AndroidJUnit4::class)
class Nbug15RedesignDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var server: MockWebServer
    private val run = (System.currentTimeMillis() % 100_000).toString()
    private val nameA = "YuFA $run"
    private val nameB = "YuFB $run"
    private var idA = ""
    private var idB = ""
    private val turnedOff = ArrayList<String>()

    private fun log(s: String) = Log.i("YuNb15R", s)
    private fun has(tag: String) = compose.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun shown(t: String) = compose.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun inside(tag: String, text: String, substring: Boolean = true) =
        compose.onAllNodes(hasText(text, substring = substring) and hasAnyAncestor(hasTestTag(tag)), true).fetchSemanticsNodes().isNotEmpty()
    private fun tagInside(parent: String, tag: String) =
        compose.onAllNodes(hasTestTag(tag) and hasAnyAncestor(hasTestTag(parent)), true).fetchSemanticsNodes().isNotEmpty()
    private fun click(tag: String) { compose.onAllNodesWithTag(tag, true)[0].performClick(); compose.waitForIdle() }
    private fun wait(what: String, ms: Long = 30_000, cond: () -> Boolean) {
        val ok = runCatching { compose.waitUntil(ms) { cond() }; true }.getOrDefault(false)
        if (!ok) FirstRun.shot("nb15r_fail_" + what.filter { it.isLetterOrDigit() }.take(30))
        assertTrue("$what (after ${ms}ms)", ok)
    }

    private fun m3u(vararg lines: Pair<String, String?>) = "#EXTM3U\n" + lines.joinToString("") { (t, g) ->
        val slug = t.substringAfter("$run ").replace(' ', '-').lowercase()
        "#EXTINF:-1${g?.let { " group-title=\"$it\"" } ?: ""},$t\nhttp://127.0.0.1:9/$slug.m3u8\n"
    }

    @Before fun setUp() {
        server = MockWebServer()
        val a = m3u(*(0 until 30).map { "YuF$run News $it" to "News" }.toTypedArray(), *(0 until 40).map { "YuF$run Sports $it" to "Sports;HD" }.toTypedArray())
        val b = m3u(
            *(0 until 10).map { "YuF$run BNews $it" to "news" }.toTypedArray(),
            *(0 until 40).map { "YuF$run Other $it" to null }.toTypedArray(),
        ) + "#EXTINF:-1 group-title=\"News\",YuF$run Dup\nhttp://127.0.0.1:9/news-0.m3u8\n"
        server.dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: mockwebserver3.RecordedRequest): MockResponse =
                MockResponse.Builder().addHeader("Content-Type", "audio/x-mpegurl").body(if (request.url.encodedPath.startsWith("/b")) b else a).build()
        }
        server.start()
        runBlocking {
            PlaylistStore.init(ctx)
            PlaylistStore.playlists.value.filter { it.enabled }.forEach { turnedOff += it.id; PlaylistStore.setEnabled(ctx, it.id, false) }
            idA = PlaylistStore.addRemote(ctx, nameA, server.url("/a.m3u").toString()).id
            idB = PlaylistStore.addRemote(ctx, nameB, server.url("/b.m3u").toString()).id
        }
        compose.setContent { MTPlayerTheme { Box(Modifier.fillMaxSize()) { StreamScreen(); AppMessageHost() } } }
    }

    @After fun tearDown() {
        runCatching { runBlocking { listOf(idA, idB).filter { it.isNotEmpty() }.forEach { PlaylistStore.delete(ctx, it) } } }
        runCatching { runBlocking { turnedOff.forEach { PlaylistStore.setEnabled(ctx, it, true) } } }
        runCatching { server.close() }
    }

    private fun top(tag: String) = compose.onAllNodesWithTag(tag, true).fetchSemanticsNodes().first().boundsInRoot.top

    private fun headerState(): String = compose.onAllNodesWithTag("live_header", true).fetchSemanticsNodes().firstOrNull()
        ?.config?.getOrElseNullable(SemanticsProperties.StateDescription) { null }.orEmpty()

    @Test fun a_filterSheetPicksSearchesAndPinsPlaylists() {
        wait("NB15-A: a Filter button in the Live TV header") { has("live_filter_button") }
        click("live_filter_button")
        wait("filter sheet lists both playlists") { has("live_filter_sheet") && has("live_filter_$idA") && has("live_filter_$idB") }
        val title = inside("live_filter_sheet", "Show videos from")
        compose.onAllNodesWithTag("live_filter_search", true)[0].performTextInput(nameB)
        wait("search keeps only the playlist typed") { has("live_filter_$idB") && !has("live_filter_$idA") }
        compose.onAllNodesWithTag("live_filter_search", true)[0].performTextClearance()
        wait("clearing the search lists both again") { has("live_filter_$idA") && has("live_filter_$idB") }
        val beforePin = top("live_filter_$idB") > top("live_filter_$idA")
        click("live_filter_pin_$idB")
        wait("a pinned playlist goes first") { has("live_filter_$idA") && top("live_filter_$idB") < top("live_filter_$idA") }
        click("live_filter_$idA")
        // A alone: 70 channels (Sports 40 sorts above News 30, so A's first Sports tile is the one on screen).
        wait("picking A shows A's channels") { !has("live_filter_sheet") && headerState() == "Showing 50 of 70 channels" && shown("YuF$run Sports 0") }
        val onlyA = !shown("YuF$run BNews") && !shown("YuF$run Other")
        val chip = has("live_pinned_$idB")
        FirstRun.shot("nb15r_a_filtered")
        click("live_pinned_$idB")
        // B alone: 10 + 40 + its copy of A's stream = 51 channels.
        wait("the pinned chip shows B's channels") { headerState() == "Showing 50 of 51 channels" && shown("YuF$run Other 0") && !shown("YuF$run Sports 0") }
        // Unpin again so the next test starts clean.
        click("live_filter_button")
        wait("filter sheet again") { has("live_filter_pin_$idB") }
        click("live_filter_pin_$idB"); click("live_filter_all")
        log("NB15R_RESULT a title=$title pinOrder=$beforePin onlyA=$onlyA chip=$chip")
        assertTrue("the sheet is not titled 'Show videos from'", title)
        assertTrue("picking playlist A still showed B's channels", onlyA)
        assertTrue("a pinned playlist has no one-tap chip", chip)
    }

    @Test fun b_feedMergesByCategoryWithoutNamesOrViewAndPagesBy50() {
        wait("Live TV feed") { has("stream_screen") }
        runCatching { compose.onAllNodesWithTag("stream_screen", true)[0].performScrollToIndex(0) }
        wait("NB15-B2: a 'News' category heading on the feed", 40_000) { has("live_cat_h_News") }
        wait("both playlists loaded") { headerState().contains("of 120") }
        val newsCount = inside("live_cat_h_News", "40")
        val view = compose.onAllNodes(hasText("View") and hasAnyAncestor(hasTestTag("stream_screen")), true).fetchSemanticsNodes().size
        val names = inside("stream_screen", nameA) || inside("stream_screen", nameB)
        val chips = has("live_cat_Sports") && has("live_cat_Other")
        val first = headerState()
        val title = inside("live_header", "120 channels", substring = false)
        FirstRun.shot("nb15r_b_feed")
        val list = compose.onAllNodesWithTag("stream_screen", true)[0]
        list.performScrollToNode(hasText("YuF$run Sports 9")); compose.waitForIdle()
        list.performScrollToIndex(0); compose.waitForIdle()
        val second = headerState()
        list.performScrollToNode(hasText("YuF$run Other 19")); compose.waitForIdle()
        val dup = shown("YuF$run Dup")
        list.performScrollToIndex(0); compose.waitForIdle()
        val third = headerState()
        log("NB15R_RESULT b newsCount40=$newsCount view=$view names=$names chips=$chips paging=[$first | $second | $third] dup=$dup title120=$title")
        assertEquals("NB15-B1: the feed shows 'View' buttons", 0, view)
        assertTrue("NB15-B1: the feed shows playlist names", !names)
        assertTrue("NB15-B2: 'News' does not hold A's 30 and B's 10 channels", newsCount)
        assertTrue("category chips missing", chips)
        assertEquals("NB15-B3: first page", "Showing 50 of 120 channels", first)
        assertEquals("NB15-B3: after reaching the end once", "Showing 100 of 120 channels", second)
        assertEquals("NB15-B3: after reaching the end twice", "All 120 channels", third)
        assertTrue("a stream in both playlists showed twice", !dup)
        assertTrue("the header counts a stream in both playlists twice (not '120 channels')", title)
    }

    @Test fun c_toolsModalHoldsEveryToolAndOpensPlaylists() {
        wait("NB15-C1: a Tools button in the Live TV header") { has("live_tools_button") }
        val headerLink = has("live_header_link")
        val toolsOnFeed = has("live_tools")
        click("live_tools_button")
        wait("Tools modal") { has("live_tools_sheet") }
        val tools = listOf("live_tool_playlists", "live_tool_link", "live_tool_file", "market_entry", "stream_websites", "live_tool_add_url", "live_tool_new", "live_tool_sync", "live_tool_recent")
        val missing = tools.filterNot { tagInside("live_tools_sheet", it) }
        FirstRun.shot("nb15r_c_tools")
        click("live_tool_playlists")
        wait("NB15-C2: Playlists page") { has("playlists_page") && !has("live_tools_sheet") }
        compose.onAllNodesWithTag("playlists_list", true)[0].performScrollToNode(hasTestTag("playlist_toggle_$idB"))
        click("playlist_toggle_$idB")
        wait("turned off on the Playlists page") { PlaylistStore.get(idB)?.enabled == false && has("playlists_off_header") }
        FirstRun.shot("nb15r_c_playlists")
        click("playlists_back")
        wait("back on Live TV") { has("stream_screen") && !has("playlists_page") }
        log("NB15R_RESULT c headerLink=$headerLink toolsOnFeed=$toolsOnFeed missing=$missing")
        assertTrue("NB15-C1: 'Play link' is still a separate header action", !headerLink)
        assertTrue("NB15-C1: the tools grid is still on the feed", !toolsOnFeed)
        assertTrue("NB15-C1: tools missing from the Tools modal: $missing", missing.isEmpty())
    }
}
