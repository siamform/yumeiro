/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSearchResponseList
import dev.asfakul.yumeiro.online.OnlineRepository
import dev.asfakul.yumeiro.online.YumixDefaults
import dev.asfakul.yumeiro.sources.SourceHub
import dev.asfakul.yumeiro.sources.hub.HubDiscover
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Group O in the real app (phones and TV), with local extensions and a local hub ([GroupOFakeHub]). Log tag YuGroupO.
 *  e (F5/F6): Yumix "All" and Home both show Discover (section bar and Yumeiro Cloud shelves).
 *  f (F13): search is grouped by extension (rows with name, count, See all), sorted, with the extensions that
 *     found nothing and the failed ones folded into summary lines; See all opens that extension's results.
 *  g (F8): the title page shows the extension's own score as a named chip.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class GroupOAppDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx: Context get() = inst.targetContext
    private fun log(s: String) = Log.i("YuGroupO", s)
    private fun n(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().size
    private fun has(tag: String) = n(tag) > 0
    private fun waitTag(tag: String, ms: Long = 30_000): Boolean = runCatching { rule.waitUntil(ms) { has(tag) }; true }.getOrDefault(false)
    private fun texts(tag: String): List<String> = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes()
        .flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
    private val hub = GroupOFakeHub()
    /** Every text inside the node tagged [tag]. */
    private fun under(tag: String): List<String> = rule.onAllNodes(
        androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag(tag)) and androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), true,
    ).fetchSemanticsNodes().flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }

    /** A local extension: [films] for every search with "yuo" in it ("" = finds nothing, null = fails). */
    private class Local(override var name: String, private val films: List<String>?) : MainAPI() {
        override var mainUrl = "https://${name.lowercase().replace(' ', '-')}.invalid"
        override val hasMainPage = true
        override val supportedTypes = setOf(TvType.Movie)
        override val mainPage = mainPageOf("$mainUrl/latest" to "Latest")
        override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse =
            newHomePageResponse(request.name, listOf(newMovieSearchResponse("$name pick", "$mainUrl/pick", TvType.Movie)))
        override suspend fun search(query: String, page: Int): SearchResponseList {
            val f = films ?: error("YuO test: this extension fails on purpose")
            val items = if ("yuo" in query.lowercase()) f.map { newMovieSearchResponse(it, "$mainUrl/${it.hashCode()}", TvType.Movie) } else emptyList()
            return newSearchResponseList(items, false)
        }
        override suspend fun load(url: String): LoadResponse = newMovieLoadResponse("$name title", url, TvType.Movie, url) { score = Score.from10(7.5) }
    }

    private fun scrollTo(tag: String) {
        val vertical = androidx.compose.ui.test.hasScrollToNodeAction() and
            androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        val lists = rule.onAllNodes(vertical, true).fetchSemanticsNodes().size
        for (i in 0 until lists) runCatching { rule.onAllNodes(vertical, true)[i].performScrollToNode(androidx.compose.ui.test.hasTestTag(tag)) }
        rule.waitForIdle()
    }

    @Before fun setUp() {
        runCatching { inst.uiAutomation.grantRuntimePermission(ctx.packageName, "android.permission.POST_NOTIFICATIONS") }
        hub.start()
        HubDiscover.baseForTest = hub.base
        HubDiscover.clearCache()
        dev.asfakul.yumeiro.ui.screens.online.Discover.memory.clear()
        dev.asfakul.yumeiro.ui.screens.online.Discover.providerMemory.clear()
        YumixDefaults.resetForTest()
        inst.runOnMainSync { YumixDefaults.init(ctx); YumixDefaults.setPicked(ctx, null) }
        SourceHub.publishVideo(BRIDGE, SourceHub.Ecosystem.CLOUDSTREAM, listOf(
            Local(A, listOf("YuO Alpha One", "YuO Alpha Two", "YuO Alpha Three")), Local(B, listOf("YuO")), Local(C, emptyList()), Local(D, null),
        ))
        OnlineRepository.clearCaches()
        FirstRun.toTabs(rule)
    }

    @After fun tearDown() { SourceHub.remove(BRIDGE); HubDiscover.baseForTest = null; HubDiscover.clearCache(); hub.close() }

    private fun openYumix() {
        FirstRun.tapVisible(rule, "nav_tab_home"); rule.waitForIdle()
        FirstRun.tapVisible(rule, "quick_card_hues"); rule.waitForIdle()
        assertTrue("Yumix did not open · ${FirstRun.onScreen(rule)}", waitTag("online_filter", 120_000))
    }

    @Test fun e_discoverInYumixAndHome() {
        // The catalog part is published when the hub finishes loading after start; wait for it (as the screen does).
        val hubOn = runCatching { rule.waitUntil(60_000) { dev.asfakul.yumeiro.sources.hub.YumeiroHub.catalogOn() }; true }.getOrDefault(false)
        log("GROUPO_F5 e catalogOn=$hubOn")
        assertTrue("the Yumeiro catalog part is on for a fresh install", hubOn)
        openYumix()
        val list = waitTag("online_home_list", 60_000)
        scrollTo("discover_bar")
        val bar = waitTag("discover_bar", 30_000)
        scrollTo("discover_row_hot")
        val hot = waitTag("discover_row_hot", 30_000)
        log("GROUPO_F5 e yumix list=$list bar=$bar hot=$hot reqs=${hub.requests.take(5)}")
        FirstRun.shot("groupo_f5_yumix_discover")
        assertTrue("F5/F6: Yumix All has the Discover bar · ${FirstRun.onScreen(rule).take(300)}", bar)
        assertTrue("F6: Hot right now from the hub", hot && hub.requests.any { "shelf=hot_today" in it })
        rule.onAllNodesWithTag("discover_section_movies", true).onFirst().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        rule.waitForIdle()
        val movies = run { scrollTo("discover_row_m_pop"); waitTag("discover_row_m_pop", 30_000) }
        log("GROUPO_F6 e section=${dev.asfakul.yumeiro.ui.screens.online.Discover.section} movies=$movies reqs=${hub.requests.filter { "discover" in it }.take(4)} rows=${FirstRun.onScreen(rule).take(300)}")
        assertTrue("F6: Movies section", movies)
        // Home too.
        FirstRun.tapVisible(rule, "nav_tab_home"); rule.waitForIdle()
        waitTag("home_list", 30_000)
        scrollTo("discover_bar")
        val homeBar = waitTag("discover_bar", 30_000)
        log("GROUPO_F5 e home bar=$homeBar")
        FirstRun.shot("groupo_f5_home_discover")
        assertTrue("F5/F6: Home has Discover · ${FirstRun.onScreen(rule).take(300)}", homeBar)
        log("GROUPO_F5_F6_RESULT e PASS")
    }

    @Test fun f_searchIsGroupedAndSorted() {
        openYumix()
        FirstRun.tapVisible(rule, "topbar_search_action")
        assertTrue("search did not open", waitTag("online_search_input"))
        rule.onAllNodesWithTag("online_search_input", true).onFirst().performTextReplacement("yuo")
        // Submit through the field's IME action (a key event can land on the suggestions list instead).
        rule.onAllNodesWithTag("online_search_input", true).onFirst().performImeAction()
        rule.waitForIdle()
        // A row may sit below the fold on a short (TV, landscape) screen: scroll the list to it while waiting.
        fun seen(tag: String, ms: Long) = runCatching { rule.waitUntil(ms) {
            runCatching { rule.onAllNodesWithTag("online_search_groups", true).onFirst().performScrollToNode(androidx.compose.ui.test.hasTestTag(tag)) }
            has(tag)
        }; true }.getOrDefault(false)
        val grouped = seen("online_search_group_$A", 60_000)
        seen("online_search_group_$B", 30_000)
        FirstRun.shot("groupo_f13_grouped")
        val rows = listOf(A, B).map { if (seen("online_search_group_$it", 5_000)) n("online_search_group_$it") else 0 }
        log("GROUPO_F13 f grouped=$grouped groups=$rows")
        assertTrue("F13: one row for $A · ${FirstRun.onScreen(rule).take(400)}", grouped)
        assertTrue("F13: one row for $B", rows[1] > 0)
        assertTrue("F13: no row for the empty $C", !seen("online_search_group_$C", 3_000))
        // The summary lines sit under the rows; bring them into view (a LazyColumn composes only what is shown).
        runCatching { rule.waitUntil(60_000) {
            runCatching { rule.onAllNodesWithTag("online_search_groups", true).onFirst().performScrollToNode(androidx.compose.ui.test.hasTestTag("online_search_group_failed")) }
            has("online_search_group_failed")
        } }
        log("GROUPO_F13 f searching=${under("online_search_group_searching")} none=${under("online_search_group_none")} failed=${under("online_search_group_failed")}")
        assertTrue("F13: '$C' folded into 'Nothing found in'", texts("online_search_group_none").any { C in it } || under("online_search_group_none").any { C in it })
        assertTrue("F13: failed '$D' folded with Retry", has("online_search_group_failed") && has("online_search_group_retry"))
        runCatching { rule.onAllNodesWithTag("online_search_groups", true).onFirst().performScrollToIndex(0) }; rule.waitForIdle()
        // Best match: B's exact "YuO" first; Most results: A (3) first.
        fun firstGroup(): String? = rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("group") { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)?.let { it.startsWith("online_search_group_") && it.removePrefix("online_search_group_") in setOf(A, B) } == true
        }, true).fetchSemanticsNodes().minByOrNull { it.boundsInRoot.top }?.config?.get(SemanticsProperties.TestTag)?.removePrefix("online_search_group_")
        val best = firstGroup()
        FirstRun.tapVisible(rule, "online_search_sort_most")
        val most = firstGroup()
        log("GROUPO_F13 f order best=$best most=$most")
        assertTrue("F13: best match puts $B (exact title) first, got $best", best == B)
        assertTrue("F13: most results puts $A first, got $most", most == A)
        FirstRun.tapVisible(rule, "online_search_group_all_$A")
        assertTrue("F13: See all opens $A's results", waitTag("online_search_results", 10_000))
        log("GROUPO_F13_RESULT f PASS")
    }

    @Test fun g_titleShowsNamedRatings() {
        openYumix()
        rule.runOnUiThread { com.lagradost.cloudstream3.utils.AppContextUtils.loadResult("https://yuo-alpha.invalid/t", A, "$A title") }
        assertTrue("title page did not open · ${FirstRun.onScreen(rule).take(300)}", waitTag("online_detail_title", 60_000))
        val ok = waitTag("online_detail_ratings", 20_000)
        val chips = under("online_detail_ratings")
        log("GROUPO_F8 g ratings=$ok chips=$chips")
        FirstRun.shot("groupo_f8_ratings")
        assertTrue("F8: ratings row", ok)
        assertTrue("F8: the extension's own score, named: $chips", chips.joinToString(" ").let { A in it && "7.5" in it })
        log("GROUPO_F8_RESULT g PASS")
    }

    private companion object {
        const val BRIDGE = "yu-groupo-app"
        const val A = "YuO Alpha"
        const val B = "YuO Beta"
        const val C = "YuO Gamma"
        const val D = "YuO Delta"
    }
}
