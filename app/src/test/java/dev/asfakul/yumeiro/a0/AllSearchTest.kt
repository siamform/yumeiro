/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSearchResponseList
import dev.asfakul.yumeiro.sources.lookup.StreamLookupProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SEARCH-CAP-1 (A0): "All" asks every source at once, including the Nuvio and Stremio lookup sources, and each
 * answers on its own: results arrive progressively, a slow or broken source never empties the others, and every
 * source ends with its own status. Cinemeta answers are the real saved ones.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AllSearchTest {
    private fun fx(n: String) = requireNotNull(javaClass.classLoader!!.getResource("fixtures/a0/$n")).readText()
    private val tag = System.nanoTime().toString()

    private class Site(override var name: String, private val wait: Long, private val fail: Boolean = false, private val hits: Int = 2) : MainAPI() {
        override var mainUrl = "https://example.invalid"
        override suspend fun search(query: String, page: Int): SearchResponseList {
            delay(wait)
            if (fail) throw java.io.IOException("connection reset")
            return newSearchResponseList((1..hits).map { newMovieSearchResponse("$query $it", "https://example.invalid/$name/$it") }, false)
        }
        override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? = null
    }

    private val noCloud = object : MainAPI() {
        override var name = "cloud"
        override suspend fun search(query: String, page: Int): SearchResponseList = newSearchResponseList(emptyList(), false)
        override suspend fun search(query: String): List<SearchResponse> = emptyList()
        override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? = null
    }

    @Test fun allSourcesAnswerProgressivelyWithTheirOwnStatus() = runBlocking {
        val fetch: suspend (String) -> String? = { u -> if ("catalog/movie" in u) fx("cinemeta_search_movie_inception.json") else """{"metas":[]}""" }
        val nuvio = StreamLookupProvider("Nuvio $tag", "nuvio", noCloud, fetch)
        val stremio = StreamLookupProvider("Stremio streams $tag", "stremio", noCloud, fetch)
        val sites = (1..10).map { Site("Site$it $tag", wait = 20L * it) } +
            Site("Broken $tag", 5, fail = true) + Site("Empty $tag", 5, hits = 0)
        val apis = listOf(nuvio, stremio) + sites
        val updates = OnlineRepository.search("Inception", apis).toList()
        assertEquals("one answer per source", apis.size, updates.size)
        assertEquals(apis.map { it.name }.toSet(), updates.map { it.api }.toSet())
        val byName = updates.associateBy { it.api }
        assertTrue("Nuvio found titles by lookup", byName.getValue(nuvio.name).items.any { it.name == "Inception" })
        assertTrue(byName.getValue(stremio.name).items.isNotEmpty())
        assertNotNull(byName.getValue("Broken $tag").error)
        assertNull(byName.getValue("Empty $tag").error)
        assertTrue(byName.getValue("Empty $tag").items.isEmpty())
        val order = updates.map { it.api }
        assertTrue("the slowest site answers after the fast ones", order.indexOf("Site10 $tag") > order.indexOf("Site1 $tag"))
    }

    @Test fun waysAndWords() {
        val nuvio = StreamLookupProvider("Nuvio", "nuvio", noCloud) { null }
        assertEquals(SearchAdapters.Way.TITLE_LOOKUP, SearchAdapters.wayOf(nuvio))
        assertEquals(SearchAdapters.Way.NATIVE, SearchAdapters.wayOf(Site("x", 0)))
        assertNull(SearchAdapters.note(SearchAdapters.Way.NATIVE, "x"))
        assertTrue(SearchAdapters.note(SearchAdapters.Way.TITLE_LOOKUP, "Nuvio")!!.contains("looked the title up"))
        assertEquals("Searching 2 · 8 with results · 1 found nothing · 1 failed", SearchAdapters.summary(2, 8, 1, 1))
        assertNull(SearchAdapters.summary(0, 0, 0, 0))
    }
}
