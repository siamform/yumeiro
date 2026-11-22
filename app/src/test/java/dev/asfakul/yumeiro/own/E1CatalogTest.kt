/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.own

import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSearchResponseList
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.sources.hub.YumeiroCatalog
import dev.asfakul.yumeiro.sources.hub.YumeiroCatalogProvider
import dev.asfakul.yumeiro.sources.hub.YumeiroHubProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * E1-A / E1-B / E1-D / E1-E (owner 2026-11-22: "everything without installing a single extension, all in one").
 * The catalog part of the one "Yumeiro" source: shelves, search and title pages from Yumeiro Cloud, and play
 * through every installed source (resolver fan-out), best-answering sources first.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class E1CatalogTest {
    private val cloud = mapOf(
        "/catalog?shelf=kdrama&page=1" to """{"items":[{"id":"tv:1","type":"tv","title":"Queen of Tears","year":2024,"poster":"https://p/1.jpg"},{"id":"movie:2","type":"movie","title":"Parasite","year":2019}],"hasNext":true}""",
        "/title?type=movie&id=2" to """{"id":"movie:2","type":"movie","title":"Parasite","original":"기생충","year":2019,"imdb":"tt6751668","genres":["Thriller"],"rating":8.5,"runtime":132}""",
        "/title?type=tv&id=1" to """{"id":"tv:1","type":"tv","title":"Queen of Tears","year":2024,"seasons":[{"n":1,"count":16}]}""",
        "/search?q=parasite&page=1" to """{"items":[{"id":"movie:2","type":"movie","title":"Parasite","year":2019}],"hasNext":false}""",
    )
    private val asked = mutableListOf<String>()
    private fun fetch(url: String): String? { val p = url.substringAfter("/v1/hub"); asked += p; return cloud[p] }

    /** A fake installed source (any format looks like a MainAPI to the hub). */
    private class Src(override var name: String, private val films: Map<String, Int?>, private val slow: Boolean = false) : MainAPI() {
        override var mainUrl = "https://src.example"
        override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
        val searched = mutableListOf<String>()
        override suspend fun search(query: String, page: Int): SearchResponseList {
            searched += query
            if (slow) delay(120_000)
            return newSearchResponseList(films.map { (t, y) ->
                if (t.startsWith("S:")) newTvSeriesSearchResponse(t.removePrefix("S:"), "s/${t.removePrefix("S:")}", TvType.TvSeries, fix = false) { year = y }
                else newMovieSearchResponse(t, "m/$t", TvType.Movie, fix = false) { year = y }
            }, false)
        }
        override suspend fun search(query: String): List<SearchResponse> = search(query, 1).items
        override suspend fun load(url: String): LoadResponse =
            if (url.startsWith("s/")) newTvSeriesLoadResponse(url, url, TvType.TvSeries, (1..16).map { e -> newEpisode("$url#1x$e") { season = 1; episode = e } })
            else newMovieLoadResponse(url, url, TvType.Movie, "$url#play")
        override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
            callback(newExtractorLink(name, "$name 1080p", "https://cdn.example/${data.substringAfterLast('/')}", ExtractorLinkType.VIDEO))
            return true
        }
    }

    @Before fun reset() { YumeiroCatalog.Ranking.clear() }

    @Test fun onByDefault() {
        assertTrue(FeatureFlags.Flag.HUB_CATALOG.default)
        assertTrue(YumeiroCatalog.on())
    }

    @Test fun shelvesMatchTheWorkerKeys() {
        val worker = File("../server/src/hub.ts").takeIf { it.exists() } ?: File("server/src/hub.ts")
        val src = worker.readText()
        YumeiroCatalog.SHELVES.forEach { s -> assertTrue("worker has shelf ${s.key}", src.contains("  ${s.key}: { name: \"${s.name}\"")) }
    }

    @Test fun homeShelfAndSearchComeFromYumeiroCloud() = runBlocking {
        val p = YumeiroCatalogProvider(fetch = ::fetch, sources = { emptyList() }, base = "https://api.yumeiro.click/v1/hub")
        val home = p.getMainPage(1, MainPageRequest("Korean drama", "kdrama", false))!!
        val row = home.items.single()
        assertEquals(listOf("Queen of Tears", "Parasite"), row.list.map { it.name })
        assertEquals(listOf("catalog:tv:1", "catalog:movie:2"), row.list.map { it.url })
        assertTrue(home.hasNext)
        assertEquals(listOf("Parasite"), p.search("parasite", 1).items.map { it.name })
    }

    @Test fun titlePagesHaveIdsAndEveryEpisode() = runBlocking {
        val p = YumeiroCatalogProvider(fetch = ::fetch, sources = { emptyList() }, base = "")
        val film = p.load("catalog:movie:2") as MovieLoadResponse
        assertEquals("Parasite", film.name); assertEquals(2019, film.year); assertEquals(132, film.duration)
        assertEquals("tt6751668", with(LoadResponse) { film.getImdbId() })
        val show = p.load("https://yumeiro.click/catalog:tv:1") as TvSeriesLoadResponse
        assertEquals(16, show.episodes.size)
        assertEquals(1, show.episodes.last().season); assertEquals(16, show.episodes.last().episode)
        assertTrue(YumeiroCatalog.refOf(show.episodes.last().data) == YumeiroCatalog.Ref("tv", 1, 1, 16))
    }

    @Test fun playAsksEveryInstalledSourceAndPassesTheMatchingLinks() = runBlocking {
        val a = Src("Source A", mapOf("Parasite" to 2019, "Parasite 2" to 2030))
        val b = Src("Source B", mapOf("Parasite" to 1982)) // same name, other film: year too far
        val c = Src("Source C", mapOf("Okja" to 2017))
        val p = YumeiroCatalogProvider(fetch = ::fetch, sources = { listOf(a, b, c) }, base = "")
        val links = mutableListOf<ExtractorLink>()
        assertTrue(p.loadLinks("catalog:movie:2", false, {}) { links += it })
        assertEquals(listOf("https://cdn.example/Parasite#play"), links.map { it.url })
        assertEquals(listOf("Source A"), links.map { it.source })
        assertEquals(listOf("parasite"), a.searched.take(1))
        // E1-D: the source that answered is asked first next time.
        assertEquals("Source A", YumeiroCatalog.Ranking.order(listOf(b, c, a)).first().name)
    }

    @Test fun seriesEpisodesResolveBySeasonAndEpisode() = runBlocking {
        val a = Src("Source A", mapOf("S:Queen of Tears" to 2024))
        val p = YumeiroCatalogProvider(fetch = ::fetch, sources = { listOf(a) }, base = "")
        val links = mutableListOf<ExtractorLink>()
        assertTrue(p.loadLinks("https://yumeiro.click/catalog:tv:1:1:5", false, {}) { links += it })
        assertEquals(listOf("https://cdn.example/Queen of Tears#1x5"), links.map { it.url })
    }

    @Test fun aSlowSourceDoesNotBlockTheOthersAndNoSourceMeansNoLinks() = runBlocking {
        val slow = Src("Slow", mapOf("Parasite" to 2019), slow = true)
        val a = Src("Source A", mapOf("Parasite" to 2019))
        val p = YumeiroCatalogProvider(fetch = ::fetch, sources = { listOf(slow, a) }, base = "")
        val links = mutableListOf<ExtractorLink>()
        val t = System.currentTimeMillis()
        assertTrue(p.loadLinks("catalog:movie:2", false, {}) { links += it })
        assertTrue("finished within the total limit", System.currentTimeMillis() - t < YumeiroCatalogProvider.TOTAL_MS + 5_000)
        assertEquals(listOf("Source A"), links.map { it.source })
        assertFalse(YumeiroCatalogProvider(fetch = ::fetch, sources = { emptyList() }, base = "").loadLinks("catalog:movie:2", false, {}) {})
    }

    @Test fun builtInPartsAndTheHubAreNeverAskedThemselves() {
        val hub = YumeiroHubProvider(emptyList())
        val self = YumeiroCatalogProvider(fetch = { null }, sources = { emptyList() }, base = "")
        val other = Src("Other", emptyMap())
        assertEquals(listOf("Other"), YumeiroCatalog.eligible(listOf(hub, self, other)).map { it.name })
    }

    @Test fun matchingRules() {
        assertTrue(YumeiroCatalog.typeFits("movie", TvType.Movie)); assertFalse(YumeiroCatalog.typeFits("movie", TvType.TvSeries))
        assertTrue(YumeiroCatalog.typeFits("tv", TvType.AsianDrama)); assertTrue(YumeiroCatalog.typeFits("tv", null))
        assertEquals("Spider Man No Way Home", YumeiroCatalog.query("Spider-Man: No Way Home"))
        assertNull(YumeiroCatalog.refOf("classics:abc"))
        assertEquals(YumeiroCatalog.Ref("movie", 603), YumeiroCatalog.refOf("catalog:movie:603"))
    }
}
