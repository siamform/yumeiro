/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.ui.screens.online.UpcomingPrefs
import dev.asfakul.yumeiro.sources.hub.HubDiscover
import dev.asfakul.yumeiro.ui.screens.online.Discover
import dev.asfakul.yumeiro.ui.screens.online.DiscoverBar
import dev.asfakul.yumeiro.ui.screens.online.DiscoverGrid
import dev.asfakul.yumeiro.ui.screens.online.DiscoverShelf
import dev.asfakul.yumeiro.ui.screens.online.UpcomingScreen
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File

/**
 * Group O on the real screens (phones and TV), against a local Yumeiro Cloud hub ([GroupOFakeHub]). Log tag YuGroupO.
 *  a (B14): Upcoming › Series + India lists titles (it was empty: TVmaze has no Indian channels); the genre filter
 *     narrows them; the credit names TMDB.
 *  b (B14): Movies + India too, with the day filter.
 *  c (F10): the Upcoming page's multi-browse carousel, 205 dp tall.
 *  d (F5/F6/F9): Discover bar and shelves: hot, "If you like" with its seed picker, streaming services, genres; a
 *     section switch; a genre opens its grid.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class GroupOScreensDeviceTest {
    @get:Rule val rule = createComposeRule()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(s: String) = Log.i("YuGroupO", s)
    private fun count(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().size
    private fun tap(tag: String) { rule.onAllNodesWithTag(tag, true)[0].performClick(); rule.waitForIdle() }
    private fun texts(tag: String): List<String> = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes()
        .mapNotNull { n -> n.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text } }
    private fun allText(): String = rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("any text") { it.config.contains(SemanticsProperties.Text) }, true)
        .fetchSemanticsNodes().joinToString(" | ") { n -> n.config[SemanticsProperties.Text].joinToString("") { it.text } }
    private val hub = GroupOFakeHub()
    // Vertical and horizontal lists both answer scroll-to-node; the ones without [tag] throw and are skipped.
    private fun scrollTo(tag: String) {
        val m = androidx.compose.ui.test.hasScrollToNodeAction()
        val n = rule.onAllNodes(m, true).fetchSemanticsNodes().size
        for (i in 0 until n) runCatching { rule.onAllNodes(m, true)[i].performScrollToNode(androidx.compose.ui.test.hasTestTag(tag)) }
        rule.waitForIdle()
    }
    private val inGrid = androidx.compose.ui.test.hasTestTag("online_poster") and androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag("discover_grid"))
    private fun gridPosters() = rule.onAllNodes(inGrid, true).fetchSemanticsNodes().size
    /** Every text inside the node tagged [tag] (a clickable row keeps its texts in child nodes). */
    private fun under(tag: String): List<String> = rule.onAllNodes(
        androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag(tag)) and androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Text), true,
    ).fetchSemanticsNodes().flatMap { node -> node.config[androidx.compose.ui.semantics.SemanticsProperties.Text].map { it.text } }
    private fun scrollToTop(list: String) { runCatching { rule.onAllNodesWithTag(list, true)[0].performScrollToIndex(0) }; rule.waitForIdle() }

    @Before fun setUp() {
        hub.start()
        HubDiscover.baseForTest = hub.base
        HubDiscover.clearCache()
        Discover.memory.clear(); Discover.providerMemory.clear(); Discover.grid = null; Discover.section = Discover.Section.DISCOVER
        ctx.cacheDir.listFiles()?.filter { it.name.startsWith("upcoming_") }?.forEach(File::delete)
        UpcomingPrefs.setCountry(ctx, null); UpcomingPrefs.setGenre(ctx, null)
    }

    @After fun tearDown() { HubDiscover.baseForTest = null; HubDiscover.clearCache(); hub.close() }

    private fun kind(k: String) {
        if (count("upcoming_kind_$k") == 0) { tap("upcoming_category_button"); rule.waitUntil(5_000) { count("upcoming_kind_$k") > 0 } }
        tap("upcoming_kind_$k")
        if (count("upcoming_category_sheet_done") > 0) tap("upcoming_category_sheet_done")
        rule.waitUntil(5_000) { count("upcoming_category_sheet") == 0 }
    }
    private fun country(c: String) {
        tap("upcoming_country_button")
        rule.waitUntil(5_000) { count("upcoming_country_sheet") > 0 }
        rule.waitUntil(5_000) { count("upcoming_country_${c.lowercase()}") > 0 }
        tap("upcoming_country_${c.lowercase()}")
        if (count("upcoming_country_sheet_done") > 0) tap("upcoming_country_sheet_done")
        if (count("upcoming_country_sheet") > 0) androidx.test.espresso.Espresso.pressBack()
        rule.waitUntil(5_000) { count("upcoming_country_sheet") == 0 }
    }
    private fun settled(ms: Long = 60_000) = runCatching {
        rule.waitUntil(ms) { count("upcoming_loading") == 0 && (count("upcoming_entry") > 0 || count("upcoming_error") > 0 || count("upcoming_empty") > 0 || count("upcoming_empty_country") > 0) }; true
    }.getOrDefault(false)

    private fun upcomingIndia(k: String): Int {
        rule.setContent { MTPlayerTheme { UpcomingScreen(onBack = {}, onSearch = {}) } }
        rule.waitUntil(10_000) { count("upcoming_category_button") > 0 }
        kind(k)
        settled()
        country("IN")
        settled()
        val n = count("upcoming_entry")
        log("GROUPO_B14 $k IN entries=$n emptyCountry=${count("upcoming_empty_country")} credit=${texts("upcoming_credit")} hub=${hub.requests.filter { "discover" in it }.take(3)}")
        return n
    }

    @Test fun a_indiaSeriesListsTitlesAndFiltersWork() {
        val n = upcomingIndia("series")
        FirstRun.shot("groupo_b14_series_in")
        assertTrue("B14: Series + India is empty · ${allText().take(400)}", n > 0)
        assertTrue("asked the hub for India series", hub.requests.any { it.startsWith("/v1/hub/discover") && "type=tv" in it && "origin=IN" in it && "sort=upcoming" in it })
        scrollTo("upcoming_credit")
        assertTrue("TMDB credited: ${texts("upcoming_credit")}", texts("upcoming_credit").any { "TMDB" in it })
        scrollToTop("upcoming_list")
        // The genre filter works on these titles too.
        tap("upcoming_category_button")
        rule.waitUntil(5_000) { count("upcoming_genre_drama") > 0 }
        tap("upcoming_genre_drama")
        if (count("upcoming_category_sheet_done") > 0) tap("upcoming_category_sheet_done")
        rule.waitUntil(5_000) { count("upcoming_category_sheet") == 0 }
        rule.waitForIdle()
        val after = count("upcoming_entry")
        val badges = texts("upcoming_entry_badge")
        log("GROUPO_B14 a drama entries=$after badges=${badges.take(4)}")
        assertTrue("drama filter kept titles", after > 0)
        assertTrue("badges show the country and the genre: $badges", badges.isNotEmpty() && badges.all { it.startsWith("IN") && "Drama" in it })
        assertTrue("the comedy titles are gone: ${allText().take(300)}", badges.none { "Comedy" in it })
        log("GROUPO_B14_RESULT a PASS")
    }

    @Test fun b_indiaMoviesListTitlesWithDays() {
        val n = upcomingIndia("movies")
        FirstRun.shot("groupo_b14_movies_in")
        assertTrue("B14: Movies + India is empty · ${allText().take(400)}", n > 0)
        assertTrue("asked the hub for India films", hub.requests.any { it.startsWith("/v1/hub/discover") && "type=movie" in it && "origin=IN" in it })
        tap("upcoming_date_button")
        val days = runCatching { rule.waitUntil(5_000) { rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("day chip") { n -> n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)?.startsWith("upcoming_day_") == true }, true).fetchSemanticsNodes().isNotEmpty() }; true }.getOrDefault(false)
        log("GROUPO_B14 b day chips=$days")
        assertTrue("B14: the day filter has the films' days", days)
        log("GROUPO_B14_RESULT b PASS")
    }

    @Test fun c_upcomingCarousel() {
        upcomingIndia("series")
        scrollToTop("upcoming_list")
        val ok = runCatching { rule.waitUntil(10_000) { count("upcoming_highlight_carousel") > 0 }; true }.getOrDefault(false)
        assertTrue("F10: no carousel on the Upcoming page · ${allText().take(300)}", ok)
        val node = rule.onAllNodesWithTag("upcoming_highlight_carousel", true).fetchSemanticsNodes()[0]
        val dp = node.boundsInRoot.height / ctx.resources.displayMetrics.density
        val cards = count("upcoming_highlight")
        log("GROUPO_F10 carousel height=${dp}dp cards=$cards")
        FirstRun.shot("groupo_f10_carousel")
        // F10 profile: frames while the carousel is swiped (gfxinfo of this process; emulators render in software, so
        // the gate is loose and the numbers go to the audit).
        FirstRun.shell("dumpsys gfxinfo ${ctx.packageName} reset")
        repeat(6) { i ->
            rule.onAllNodesWithTag("upcoming_highlight_carousel", true)[0].performTouchInput { if (i < 4) swipeLeft() else swipeRight() }
            rule.waitForIdle()
        }
        val gfx = FirstRun.shell("dumpsys gfxinfo ${ctx.packageName}")
        val total = Regex("Total frames rendered: (\\d+)").find(gfx)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val janky = Regex("Janky frames: (\\d+)").find(gfx)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val p90 = Regex("90th percentile: (\\d+)ms").find(gfx)?.groupValues?.get(1)
        log("GROUPO_F10 profile frames=$total janky=$janky p90=${p90}ms")
        assertTrue("F10: the carousel drew frames while swiped", total > 0)
        assertTrue("F10: under half the frames janky ($janky of $total)", janky * 2 < total)
        assertEquals("F10: carousel 205 dp tall", 205f, dp, 2f)
        assertTrue("F10: several cards shown at once ($cards)", cards >= 2)
        tap("upcoming_highlight")
        log("GROUPO_F10_RESULT c PASS")
    }

    @Test fun d_discoverShelvesSectionsGenresAndGrid() {
        val opened = mutableListOf<String>()
        val seeds = listOf(OnlineItem("YuO Seed One", "catalog:movie:111", "Yumeiro", null), OnlineItem("YuO Seed Two", "catalog:tv:222", "Yumeiro", null))
        rule.setContent {
            MTPlayerTheme {
                androidx.compose.foundation.layout.Box {
                    LazyColumn(Modifier.testTag("groupo_discover_list")) {
                        item("bar") { DiscoverBar(Discover.section, onPick = { Discover.section = it }) }
                        Discover.shelves(Discover.section).forEach { sh -> item(Discover.section.name + sh.key) { DiscoverShelf(sh, seeds, onOpen = { opened += it.name + "|" + it.url }, onGrid = { Discover.grid = it }) } }
                    }
                    Discover.grid?.let { g -> DiscoverGrid(g, onOpen = { opened += it.name + "|" + it.url }, onClose = { Discover.grid = null }) }
                }
            }
        }
        rule.waitUntil(20_000) { count("discover_row_hot") > 0 }
        rule.waitUntil(20_000) { count("discover_if_you_like") > 0 && under("discover_seed").isNotEmpty() }
        log("GROUPO_F6 hot=${count("discover_row_hot")} like=${count("discover_if_you_like")} reqs=${hub.requests.take(6)}")
        assertTrue("If you like asks for the first seed", hub.requests.any { it.startsWith("/v1/hub/recommend") && "id=111" in it })
        tap("discover_seed")
        rule.waitUntil(5_000) { count("discover_seed_option") >= 2 }
        log("GROUPO_F6 options=${under("discover_seed_option").take(4)}")
        rule.onAllNodesWithTag("discover_seed_option", true)[1].performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); rule.waitForIdle()
        log("GROUPO_F6 seed now=${under("discover_seed")} reqs=${hub.requests.filter { "recommend" in it }}")
        rule.waitUntil(10_000) { hub.requests.any { it.startsWith("/v1/hub/recommend") && "id=222" in it } }
        scrollTo("discover_services")
        rule.waitUntil(20_000) { count("discover_service_8") > 0 }
        scrollTo("discover_genres")
        rule.waitUntil(5_000) { count("discover_genre_28") > 0 }
        FirstRun.shot("groupo_f5_f9_discover")
        tap("discover_genre_28")
        rule.waitUntil(20_000) { gridPosters() > 0 }
        assertTrue("genre grid asks with the genre", hub.requests.any { it.startsWith("/v1/hub/discover") && "genre=28" in it })
        tap("discover_grid_back")
        rule.waitUntil(5_000) { count("discover_grid") == 0 }
        scrollToTop("groupo_discover_list")
        tap("discover_section_tv")
        rule.waitUntil(20_000) { count("discover_row_t_pop") > 0 }
        scrollTo("discover_service_8")
        tap("discover_service_8")
        rule.waitUntil(20_000) { count("discover_grid") > 0 }
        assertTrue("service grid asks by provider and region", hub.requests.any { it.startsWith("/v1/hub/discover") && "provider=8" in it && "region=" in it && "type=tv" in it })
        rule.waitUntil(20_000) { gridPosters() > 0 }
        rule.onAllNodes(inGrid, true)[0].performClick(); rule.waitForIdle()
        log("GROUPO_F5 opened=$opened")
        assertTrue("a service card opens its catalog title: $opened", opened.any { "p8" in it && "|catalog:tv:" in it })
        log("GROUPO_F5_F6_F9_RESULT d PASS")
    }
}
