/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.lookup

import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.newSearchResponseList
import com.lagradost.cloudstream3.newMovieSearchResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SEARCH-CAP-1 (A0): stream-only sources are searchable. Fixtures are real answers saved from Cinemeta and
 * Yumeiro Cloud on 2026-11-25 (app/src/test/resources/fixtures/a0).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StreamLookupTest {
    private fun fx(n: String) = requireNotNull(javaClass.classLoader!!.getResource("fixtures/a0/$n")).readText()

    /** The cloud catalog answering nothing (down, or no key on this build): Cinemeta must take over. */
    private class EmptyCatalog : MainAPI() {
        override var name = "empty"
        var asked = 0
        override suspend fun search(query: String, page: Int): SearchResponseList { asked++; return newSearchResponseList(emptyList(), false) }
        override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? = null
    }

    private fun fetcher(log: MutableList<String>): suspend (String) -> String? = { url ->
        log += url
        val url = url.lowercase() // Cinemeta search ignores case; the app sends the query as typed
        when {
            "catalog/movie/top/search=inception" in url -> fx("cinemeta_search_movie_inception.json")
            "catalog/series/top/search=inception" in url -> """{"metas":[]}"""
            "catalog/series/top/search=breaking%20bad" in url -> fx("cinemeta_search_series_breaking_bad.json")
            "catalog/movie/top/search=breaking%20bad" in url -> """{"metas":[]}"""
            "meta/movie/tt1375666" in url -> fx("cinemeta_meta_movie_tt1375666.json")
            "meta/series/tt0903747" in url -> fx("cinemeta_meta_series_tt0903747.json")
            else -> null
        }
    }

    @Test fun planPublishesOnlyWhatIsInstalledAndOn() {
        assertEquals(StreamLookup.Plan(nuvio = true, stremio = false), StreamLookup.plan(12, true, 0, true, true))
        assertEquals(StreamLookup.Plan(nuvio = false, stremio = true), StreamLookup.plan(0, true, 2, true, true))
        assertEquals(StreamLookup.Plan(nuvio = false, stremio = false), StreamLookup.plan(12, false, 2, false, true))
        assertEquals("kill switch", StreamLookup.Plan(false, false), StreamLookup.plan(12, true, 2, true, false))
    }

    @Test fun cinemetaAnswersBecomeHitsWithImdbIds() {
        val hits = StreamLookup.cinemetaHits(fx("cinemeta_search_movie_inception.json"), "movie")
        assertTrue(hits.size >= 5)
        assertEquals("tt1375666", hits.first().imdb)
        assertEquals("Inception", hits.first().name)
        assertEquals(2010, hits.first().year)
        assertTrue(hits.all { it.imdb.startsWith("tt") })
        assertTrue(StreamLookup.cinemetaHits("not json", "movie").isEmpty())
        assertTrue(StreamLookup.cinemetaHits(null, "movie").isEmpty())
    }

    @Test fun cinemetaMetaGivesTheTmdbId() {
        assertEquals(27205, StreamLookup.tmdbOfCinemeta(fx("cinemeta_meta_movie_tt1375666.json")))
        assertEquals(1396, StreamLookup.tmdbOfCinemeta(fx("cinemeta_meta_series_tt0903747.json")))
        assertNull(StreamLookup.tmdbOfCinemeta("""{"meta":{}}"""))
    }

    @Test fun cineRefsRoundTrip() {
        assertEquals(StreamLookup.CineRef("series", "tt0903747", 2, 3), StreamLookup.cineRefOf("cinemeta:series:tt0903747:2:3"))
        assertEquals(StreamLookup.CineRef("movie", "tt1375666"), StreamLookup.cineRefOf("https://yumeiro.click/cinemeta:movie:tt1375666"))
        assertNull(StreamLookup.cineRefOf("catalog:movie:27205"))
        assertNull(StreamLookup.cineRefOf("cinemeta:anime:tt1"))
    }

    @Test fun searchFallsBackToCinemetaWhenTheCloudFindsNothing() = runBlocking {
        val log = mutableListOf<String>()
        val cat = EmptyCatalog()
        val p = StreamLookupProvider("Nuvio", "nuvio", cat, fetcher(log))
        assertFalse(p.hasMainPage)
        val items = p.search("Inception", 1).items
        assertEquals(1, cat.asked)
        assertTrue(items.isNotEmpty())
        assertEquals("Inception", items.first().name)
        assertTrue(items.first().url.endsWith("cinemeta:movie:tt1375666"))
        assertTrue("page 2 asks nothing", p.search("Inception", 2).items.isEmpty())
    }

    @Test fun aCinemetaTitleCarriesBothIdsSoStreamAddonsAreAsked() = runBlocking {
        val p = StreamLookupProvider("Nuvio", "nuvio", EmptyCatalog(), fetcher(mutableListOf()))
        val movie = p.load("cinemeta:movie:tt1375666")!!
        assertEquals("Inception", movie.name)
        assertEquals("tt1375666", with(LoadResponse) { movie.getImdbId() })
        assertEquals("27205", with(LoadResponse) { movie.getTMDbId() })

        val show = p.load("cinemeta:series:tt0903747") as TvSeriesLoadResponse
        assertEquals("1396", with(LoadResponse) { show.getTMDbId() })
        assertTrue(show.episodes.size >= 60)
        assertTrue("no specials (season 0)", show.episodes.all { (it.season ?: 0) > 0 })
        val first = show.episodes.first()
        assertEquals(1, first.season); assertEquals(1, first.episode)
        assertFalse("links come from the add-ons, not from here", p.loadLinks(first.data, false, {}, {}))
    }

    @Test fun cloudResultsAreUsedFirst() = runBlocking {
        val log = mutableListOf<String>()
        val cloud = object : MainAPI() {
            override var name = "cloud"
            override suspend fun search(query: String, page: Int): SearchResponseList =
                newSearchResponseList(listOf(newMovieSearchResponse("Inception", "catalog:movie:27205")), false)
            override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? = null
        }
        val items = StreamLookupProvider("Nuvio", "nuvio", cloud, fetcher(log)).search("inception", 1).items
        assertEquals(listOf("Inception"), items.map(SearchResponse::name))
        assertTrue("Cinemeta not asked when the cloud answered", log.isEmpty())
    }
}
