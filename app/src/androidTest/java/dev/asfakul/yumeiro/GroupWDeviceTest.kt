/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.asfakul.yumeiro.sources.hub.DiscoverFilter
import dev.asfakul.yumeiro.sources.hub.GenreCatalog
import dev.asfakul.yumeiro.sources.hub.HubDiscover
import dev.asfakul.yumeiro.ui.screens.home.HeroCarousel
import dev.asfakul.yumeiro.ui.screens.home.HeroCarouselSkeleton
import dev.asfakul.yumeiro.ui.screens.home.HeroSlide
import dev.asfakul.yumeiro.ui.screens.home.YtCard
import dev.asfakul.yumeiro.ui.screens.home.YtRowSkeleton
import dev.asfakul.yumeiro.ui.screens.online.Discover
import dev.asfakul.yumeiro.ui.screens.online.DiscoverScreen
import dev.asfakul.yumeiro.ui.screens.online.DiscoverShelf
import dev.asfakul.yumeiro.ui.screens.online.GenresScreen
import dev.asfakul.yumeiro.ui.screens.online.ServicesScreen
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import dev.asfakul.yumeiro.youtube.YtItem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Group W on the real screens (phones and TV), against the real Yumeiro Cloud hub code on the beta Worker
 * (yumeiro-cloud-beta, real TMDB answers; no fake server). Log tag YuGroupW.
 *  a (B1): Find something opens its own results page with real titles; the filter sheet applies genre + score,
 *     the active chips show them, Clear all removes them.
 *  b (B2): Genres page: a card opens results for that genre; search narrows the grid; anime themes exist.
 *  c (B3): Services page: real services for the region, Bangladesh falls back to India with a note; a card opens
 *     results for that service.
 *  d (B12): every placeholder is exactly the size of what replaces it (Discover shelf, Home carousel, mood rows).
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class GroupWDeviceTest {
    @get:Rule val rule = createComposeRule()
    private fun log(s: String) = Log.i("YuGroupW", s)
    private fun count(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().size
    private fun tap(tag: String) { rule.onAllNodesWithTag(tag, true)[0].performClick(); rule.waitForIdle() }
    private fun height(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes()[0].size.height
    private fun texts(tag: String): List<String> = rule.onAllNodes(
        hasAnyAncestor(hasTestTag(tag)) and androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), true,
    ).fetchSemanticsNodes().flatMap { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
    private val inGrid = hasTestTag("online_poster") and hasAnyAncestor(hasTestTag("discover_results"))
    private fun gridPosters() = rule.onAllNodes(inGrid, true).fetchSemanticsNodes().size
    // Every scrollable list on screen is asked; the ones without [tag] throw and are skipped ([list] is a hint only).
    @Suppress("UNUSED_PARAMETER")
    private fun scrollTo(list: String, tag: String) {
        val m = androidx.compose.ui.test.hasScrollToNodeAction()
        val n = rule.onAllNodes(m, true).fetchSemanticsNodes().size
        for (i in 0 until n) runCatching { rule.onAllNodes(m, true)[i].performScrollToNode(hasTestTag(tag)) }
        rule.waitForIdle()
    }

    @Before fun setUp() {
        HubDiscover.baseForTest = BETA_HUB
        HubDiscover.clearCache()
        Discover.memory.clear(); Discover.providerMemory.clear(); Discover.providerRegion.clear()
    }

    @After fun tearDown() { HubDiscover.baseForTest = null; HubDiscover.clearCache() }

    private fun results(ms: Long = 45_000) = runCatching {
        rule.waitUntil(ms) { gridPosters() > 0 || count("discover_error") > 0 || count("discover_empty") > 0 }
    }.isSuccess

    @Test fun a_findSomethingHasItsOwnPageAndFilters() {
        rule.setContent { MTPlayerTheme { Box(Modifier.fillMaxSize().background(LocalMtPalette.current.background)) {
            DiscoverScreen(DiscoverFilter(), onBack = {}, onOpenTitle = {})
        } } }
        rule.waitUntil(10_000) { count("discover_page") > 0 }
        results()
        val first = gridPosters()
        log("GROUPW_B1 unfiltered posters=$first error=${count("discover_error")}")
        FirstRun.shot("groupw_b1_results")
        assertEquals("B1: the page shows an error instead of results", 0, count("discover_error"))
        assertTrue("B1: Find something shows no titles", first > 0)

        tap("discover_filters")
        rule.waitUntil(5_000) { count("discover_sheet") > 0 }
        scrollTo("discover_sheet", "discover_sheet_genre_horror")
        tap("discover_sheet_genre_horror")
        scrollTo("discover_sheet", "discover_sheet_score_7")
        tap("discover_sheet_score_7")
        FirstRun.shot("groupw_b1_sheet")
        tap("discover_sheet_apply")
        rule.waitUntil(5_000) { count("discover_sheet") == 0 }
        rule.waitUntil(10_000) { count("discover_active_chip") == 2 }
        results()
        log("GROUPW_B1 horror+7 posters=${gridPosters()} chips=${texts("discover_active")}")
        FirstRun.shot("groupw_b1_filtered")
        assertTrue("B1: horror with a score of 7+ found nothing", gridPosters() > 0)
        assertTrue("chips name the filters: ${texts("discover_active")}", texts("discover_active").any { "Horror" in it })

        tap("discover_clear")
        rule.waitUntil(10_000) { count("discover_active_chip") == 0 }
        results()
        assertTrue("Clear all brings results back", gridPosters() > 0)
    }

    @Test fun b_genresPageOpensResults() {
        var opened: DiscoverFilter? = null
        rule.setContent { MTPlayerTheme { Box(Modifier.fillMaxSize().background(LocalMtPalette.current.background)) {
            GenresScreen(GenreCatalog.Kind.ALL, onBack = {}, onOpen = { opened = it }, onSearch = {})
        } } }
        rule.waitUntil(10_000) { count("genre_card_action") > 0 }
        val all = count("genres_grid")
        FirstRun.shot("groupw_b2_genres")
        tap("genre_card_action")
        assertEquals("B2: the Action card opens Action results", listOf("action"), opened?.genres)

        tap("genres_kind_anime")
        rule.waitForIdle()
        scrollTo("genres_grid", "genre_card_isekai")
        assertTrue("B2: anime themes are offered for anime", count("genre_card_isekai") > 0)
        FirstRun.shot("groupw_b2_anime")
        tap("genre_card_isekai")
        assertEquals(GenreCatalog.Kind.ANIME, opened?.kind)
        assertEquals(listOf("isekai"), opened?.genres)

        rule.onAllNodesWithTag("genres_search", true)[0].performTextInput("sci")
        rule.waitForIdle()
        assertTrue("search keeps Science Fiction", count("genre_card_scifi") > 0)
        assertEquals("search hides the rest", 0, count("genre_card_action"))
        log("GROUPW_B2 grid=$all ok")
    }

    @Test fun c_servicesPageListsRealServicesWithFallback() {
        var opened: DiscoverFilter? = null
        rule.setContent { MTPlayerTheme { Box(Modifier.fillMaxSize().background(LocalMtPalette.current.background)) {
            ServicesScreen(onBack = {}, onOpen = { opened = it })
        } } }
        rule.waitUntil(10_000) { count("services_page") > 0 }
        rule.waitUntil(30_000) { count("services_error") > 0 || rule.onAllNodes(hasTestTag("services_grid"), true).fetchSemanticsNodes().isNotEmpty() && cards() > 0 }
        assertEquals("B3: services failed to load", 0, count("services_error"))
        FirstRun.shot("groupw_b3_services")
        // Bangladesh: TMDB lists no services there; the page shows India's with a note (not an empty page).
        if (count("services_region_bd") == 0) scrollTo("services_regions", "services_region_bd")
        if (count("services_region_bd") > 0) {
            tap("services_region_bd")
            rule.waitUntil(30_000) { count("services_fallback") > 0 && cards() > 0 }
            FirstRun.shot("groupw_b3_bd_fallback")
            log("GROUPW_B3 BD cards=${cards()} note=${texts("services_page").firstOrNull { "India" in it }}")
        }
        rule.waitUntil(30_000) { cards() > 0 }
        val n = cards()
        assertTrue("B3: no services", n > 3)
        rule.onAllNodes(serviceCard, true)[0].performClick(); rule.waitForIdle()
        assertTrue("B3: a service card opens its results: $opened", opened?.services?.size == 1)
        log("GROUPW_B3 cards=$n opened=${opened?.encode()}")
    }
    private val serviceCard = androidx.compose.ui.test.SemanticsMatcher("service card") { n ->
        n.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("service_card_") == true
    }
    private fun cards() = rule.onAllNodes(serviceCard, true).fetchSemanticsNodes().size

    @Test fun d_placeholdersKeepTheLoadedSize() {
        HubDiscover.baseForTest = BETA_HUB
        // Discover shelf (a real hub row): every height the shelf has from the first frame until its titles show.
        // One value only = nothing below it moves (0 then full height was the B12 jump).
        val row = Discover.shelves(Discover.Section.DISCOVER).filterIsInstance<Discover.Row>().first()
        val heights = java.util.Collections.synchronizedList(ArrayList<Int>())
        rule.setContent { MTPlayerTheme { Column(Modifier.fillMaxWidth().background(LocalMtPalette.current.background)) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().onGloballyPositioned { heights += it.size.height }) {
                DiscoverShelf(row, emptyList(), onOpen = {})
            }
        } } }
        rule.waitUntil(45_000) { rule.onAllNodes(hasTestTag("online_poster") and hasAnyAncestor(hasTestTag("discover_row_${row.key}")), true).fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
        val seen = synchronized(heights) { heights.distinct() }
        log("GROUPW_B12 shelf heights=$seen")
        assertEquals("B12: the Discover shelf changes height while it loads: $seen", 1, seen.size)
    }


    @Test fun e_homePlaceholdersMatchTheirRows() {
        val slides = (1..3).map { HeroSlide("k$it", "A long title for slide $it", null, emptyMap(), "Latest", false, "Anime", "Source", "Open") }
        val song = (1..4).map { YtItem("s$it", "https://music.youtube.com/watch?v=s$it", if (it == 1) "Short" else "A much longer song title that wraps onto two lines", "Artist") }
        rule.setContent { MTPlayerTheme { Column(Modifier.fillMaxWidth().background(LocalMtPalette.current.background)) {
            Box(Modifier.testTag("w_skel")) { HeroCarouselSkeleton(caption = false) }
            Box(Modifier.testTag("w_hero")) { HeroCarousel(slides, tag = "w_carousel") {} }
            Box(Modifier.testTag("w_song_skel")) { YtRowSkeleton(square = true) }
            Box(Modifier.testTag("w_song")) { LazyRow { items(song) { YtCard(it, square = true) {} } } }
            Box(Modifier.testTag("w_video_skel")) { YtRowSkeleton(square = false) }
            Box(Modifier.testTag("w_video")) { LazyRow { items(song) { YtCard(it.copy(id = "v" + it.id), square = false) {} } } }
        } } }
        rule.waitForIdle()
        FirstRun.shot("groupw_b12_placeholders")
        val pairs = listOf("w_skel" to "w_hero", "w_song_skel" to "w_song", "w_video_skel" to "w_video").map { (a, b) -> Triple(a, height(a), height(b)) }
        log("GROUPW_B12 home $pairs")
        pairs.forEach { (name, skel, real) -> assertEquals("B12: $name differs from its loaded row", real, skel) }
    }

    private companion object { const val BETA_HUB = "https://yumeiro-cloud-beta.yumeiro.workers.dev/v1/hub" }
}
