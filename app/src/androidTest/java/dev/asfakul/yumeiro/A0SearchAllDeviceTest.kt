/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSearchResponseList
import dev.asfakul.yumeiro.online.LinkEvent
import dev.asfakul.yumeiro.online.OnlineRepository
import dev.asfakul.yumeiro.online.StreamAddons
import dev.asfakul.yumeiro.online.StreamRequest
import dev.asfakul.yumeiro.sources.SourceHub
import dev.asfakul.yumeiro.sources.ecosystem.EcosystemRegistry
import dev.asfakul.yumeiro.sources.ecosystem.NuvioAdapter
import dev.asfakul.yumeiro.sources.ecosystem.NuvioStreams
import dev.asfakul.yumeiro.sources.lookup.StreamLookup
import dev.asfakul.yumeiro.sources.lookup.StreamLookupProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * SEARCH-CAP (A0) on a real device, real network: "All" search asks the Nuvio / Stremio title lookup together with
 * ten other sources (fast, slow, broken), the lookup's answer arrives without waiting for the slow ones, the title it
 * finds carries both an IMDb and a TMDB id (real Cinemeta / Yumeiro Cloud), and twelve Nuvio scrapers on the real
 * QuickJS engine get that TMDB id through one shared bridge. Fail-before (main 131a2681): no lookup provider existed,
 * so "All" never asked Nuvio scrapers or stream-only Stremio add-ons and the search showed nothing from them.
 *
 * Run: Device check workflow, test = dev.asfakul.yumeiro.A0SearchAllDeviceTest. Output tag: YuA0.
 */
@RunWith(AndroidJUnit4::class)
class A0SearchAllDeviceTest {
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private fun log(s: String) = Log.w("YuA0", s)

    private class Site(override var name: String, private val wait: Long, private val fail: Boolean = false) : MainAPI() {
        override var mainUrl = "https://example.invalid"
        override suspend fun search(query: String, page: Int): SearchResponseList {
            delay(wait)
            if (fail) error("site changed")
            return newSearchResponseList(listOf(newMovieSearchResponse("$query ($name)", "$mainUrl/$name/1")), false)
        }
        override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? = null
    }

    @Test fun allSearchAsksTheLookupWithTenOtherSources() = runBlocking {
        val lookup = StreamLookupProvider("Nuvio", "nuvio")
        val others = (1..10).map { i -> Site("Site$i", wait = if (i == 4) 20_000 else 50L * i, fail = i == 7) }
        val t0 = System.currentTimeMillis()
        var lookupAt = -1L
        val updates = OnlineRepository.search("Inception", listOf(lookup) + others).toList().onEach {
            if (it.api == "Nuvio" && lookupAt < 0) lookupAt = System.currentTimeMillis() - t0
            log("A0 ${it.api}: ${it.items.size} items, error=${it.error}")
        }
        val total = System.currentTimeMillis() - t0
        log("A0 lookup answered after $lookupAt ms; all done after $total ms")
        val nuvio = updates.first { it.api == "Nuvio" }
        assertTrue("the lookup found Inception: ${nuvio.items.map { it.name }} ${nuvio.error}", nuvio.items.any { it.name.contains("Inception", true) })
        assertTrue("lookup not held behind the 20 s site ($lookupAt ms)", lookupAt in 0..15_000)
        assertEquals("every source reported", 11, updates.map { it.api }.toSet().size)
        assertNotNull("the broken source says why", updates.first { it.api == "Site7" }.error)

        // The title page carries both ids, so Stremio (IMDb) and Nuvio (TMDB) add-ons can be asked.
        val hit = nuvio.items.first { it.name.contains("Inception", true) }
        val load = lookup.load(hit.url)!!
        val imdb = with(LoadResponse) { load.getImdbId() }
        val tmdb = with(LoadResponse) { load.getTMDbId() }
        log("A0 load ${load.name}: imdb=$imdb tmdb=$tmdb")
        assertEquals("tt1375666", imdb)
        assertEquals("27205", tmdb)
    }

    @Test fun twelveNuvioScrapersGetTheBridgedTmdbId() = runBlocking {
        // An IMDb-only title (what a Stremio catalogue gives): the bridge looks the TMDB id up once, on the network.
        val imdbOnly = StreamRequest(null, "tt1375666", "movie", null, null, "Inception", 2010)
        val bridged = StreamAddons.bridgeOnce(imdbOnly)()
        log("A0 bridged: $bridged")
        assertEquals(27205, bridged.tmdbId)
        val code = """module.exports = { getStreams: async (id, t) => [{ name: "S", url: "https://s.example/" + t + "/" + id + ".m3u8" }] };"""
        val crash = """module.exports = { getStreams: async () => { throw new Error("site changed"); } };"""
        val plugins = (1..12).map { i ->
            val id = "scraper$i"
            val p = EcosystemRegistry.Installed("https://fixture.example/manifest.json", SourceHub.Ecosystem.NUVIO, id, "Scraper $i", "1", null, emptyMap(), JsonObject(emptyMap()))
            val dir = File(ctx.cacheDir, "a0-nuvio/$id").apply { deleteRecursively(); mkdirs() }
            File(dir, NuvioAdapter.FILE).writeText(if (i == 3) crash else code)
            p to dir
        }
        val ev = NuvioStreams.streamsOf(ctx, plugins, bridged).toList()
        val links = ev.filterIsInstance<LinkEvent.Link>().map { it.link }
        log("A0 nuvio: ${links.size} links, ${ev.filterIsInstance<LinkEvent.Skipped>().map { it.reason }}")
        assertEquals("11 working scrapers answered with the bridged id", 11, links.size)
        assertTrue(links.all { it.url.endsWith("/movie/27205.m3u8") })
    }

    @Test fun lookupIsPublishedOnlyWhenSomethingNeedsIt() {
        StreamLookup.start(ctx)
        val plan = StreamLookup.plan(0, true, 0, true, true)
        assertEquals(StreamLookup.Plan(false, false), plan)
    }
}
