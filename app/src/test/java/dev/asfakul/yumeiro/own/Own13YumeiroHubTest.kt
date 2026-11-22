/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.own

import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSearchResponseList
import com.lagradost.cloudstream3.utils.ExtractorLink
import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.sources.classics.YumeiroClassicsProvider
import dev.asfakul.yumeiro.sources.hub.YumeiroHub
import dev.asfakul.yumeiro.sources.hub.YumeiroHubProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * OWN-13 (owner 2026-11-22: "it should provide everything without installing a single extension, all in one"):
 * the built-in parts are one source, "Yumeiro": one home, one search, titles open through their own part,
 * old saved names still open, one card in the market.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Own13YumeiroHubTest {
    private class Part(key: String, override var name: String, private val hits: List<String>) : MainAPI() {
        override var mainUrl = "https://yumeiro.click"
        override val hasMainPage = true
        override val supportedTypes = setOf(if (key == "drama") TvType.AsianDrama else TvType.Movie)
        val asked = mutableListOf<String>()
        init { sourcePlugin = "yumeiro-builtin:$key" }
        override val mainPage = listOf(MainPageData("$name shelf", "$key-shelf"))
        override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
            asked += "home:${request.data}"
            return newHomePageResponse(request.name, hits.map { newMovieSearchResponse(it, it, TvType.Movie, fix = false) })
        }
        override suspend fun search(query: String, page: Int): SearchResponseList = newSearchResponseList(hits.map { newMovieSearchResponse(it, it, TvType.Movie, fix = false) }, false)
        override suspend fun load(url: String): LoadResponse { asked += "load:$url"; return newMovieLoadResponse(url, url, TvType.Movie, url) }
        override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean { asked += "links:$data"; return true }
    }

    private val drama = Part("drama", "Yumeiro Drama", listOf("drama:onshort|a|0", "drama:onshort|b|0"))
    private val classics = Part("classics", "Yumeiro Classics", listOf("classics:suddenly"))
    private val hub = YumeiroHubProvider(listOf(drama, classics))

    @Test fun onByDefault() {
        assertTrue(FeatureFlags.Flag.YUMEIRO_HUB.default)
        assertTrue(YumeiroHub.on())
    }

    @Test fun oneSourceNamedYumeiroWithEveryShelf() = runBlocking {
        assertEquals("Yumeiro", hub.name)
        assertEquals(listOf("Yumeiro Drama shelf", "Yumeiro Classics shelf"), hub.mainPage.map { it.name })
        assertTrue(TvType.AsianDrama in hub.supportedTypes && TvType.Movie in hub.supportedTypes)
        hub.getMainPage(1, MainPageRequest(hub.mainPage[1].name, hub.mainPage[1].data, false))
        assertEquals(listOf("home:classics-shelf"), classics.asked)
        assertTrue(drama.asked.isEmpty())
        assertNull(hub.getMainPage(1, MainPageRequest("x", "no-part", false)))
    }

    @Test fun searchCoversEveryPartTakingTurns() = runBlocking {
        val r = hub.search("x", 1).items.map { it.url }
        assertEquals(listOf("drama:onshort|a|0", "classics:suddenly", "drama:onshort|b|0"), r)
    }

    @Test fun titlesOpenAndPlayThroughTheirOwnPart() = runBlocking {
        hub.load("classics:suddenly")
        hub.load("https://yumeiro.click/drama/drama:onshort|a|0") // a saved id rewritten by fixUrl()
        hub.loadLinks("drama:onshort|a|3", false, {}, {})
        assertEquals(listOf("load:classics:suddenly"), classics.asked)
        assertEquals(listOf("load:https://yumeiro.click/drama/drama:onshort|a|0", "links:drama:onshort|a|3"), drama.asked)
        assertSame(drama, YumeiroHubProvider(listOf(drama)).partFor("anything"))
    }

    @Test fun oldSavedNamesOpenWithTheHub() {
        assertEquals("Yumeiro", YumeiroHub.aliasOf("Yumeiro Drama"))
        assertEquals("Yumeiro", YumeiroHub.aliasOf("Yumeiro Classics"))
        assertNull(YumeiroHub.aliasOf("Internet Archive"))
    }

    @Test fun classicsHasDocumentariesCartoonsAndCountryShelves() {
        val shelves = YumeiroClassicsProvider().mainPage.map { it.name }
        listOf("Documentaries and newsreels", "Classic cartoons", "Indian classics (Hindi)", "French classics", "Japanese classics").forEach {
            assertTrue("$it missing from $shelves", it in shelves)
        }
    }

    @Test fun marketShowsOneYumeiroCardUnderTheHub() {
        val market = File("src/main/java/dev/asfakul/yumeiro/ui/screens/online/ExtensionMarketScreen.kt").readText()
        val cards = File("src/main/java/dev/asfakul/yumeiro/ui/screens/online/DramaBuiltinCard.kt").readText()
        assertTrue(market.contains("item(\"builtin_yumeiro\") { YumeiroHubCard() }"))
        assertTrue(market.contains("if (!officialHub() && tab == MarketTab.INSTALLED"))
        assertTrue(cards.contains("tag = \"market_builtin_yumeiro\""))
        assertTrue(cards.contains("if (officialHub()) { YumeiroHubCard(modifier); return }"))
    }
}
