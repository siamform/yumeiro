/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newSearchResponseList
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.toList
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
 * B17 (Beta Group N): other installed extensions are searched for the playing title, only a real match
 * (same cleaned name, compatible year, same kind, same episode and Sub / Dub version) joins, and its
 * links arrive marked with the extension they came from.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GroupNOtherSourcesTest {
    private val tag = System.nanoTime().toString()
    private fun item(name: String, type: String? = "TV series", api: String = "x") = OnlineItem(name, "https://e.invalid/$name", api, null, type = type)
    private fun ep(n: Int, season: Int? = 1) = OnlineEpisode("d$n", "Episode $n", season, n, null, null, null)

    private class Show(override var name: String, private val title: String, private val types: Set<TvType> = setOf(TvType.TvSeries)) : MainAPI() {
        override var mainUrl = "https://example.invalid"
        override val supportedTypes get() = types
        override suspend fun search(query: String, page: Int): SearchResponseList =
            newSearchResponseList(listOf(newTvSeriesSearchResponse(title, "$mainUrl/$name/show", TvType.TvSeries)), false)
        override suspend fun load(url: String): LoadResponse = newTvSeriesLoadResponse(title, url, TvType.TvSeries, listOf(
            newEpisode("$url|e1", { this.name = "One"; episode = 1; season = 1 }, false),
            newEpisode("$url|e2", { this.name = "Two"; episode = 2; season = 1 }, false),
        ))
        override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
            callback(newExtractorLink(name, name, "https://cdn.invalid/${name.hashCode()}/${data.substringAfterLast('|')}.m3u8"))
            return true
        }
        override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? = null
    }

    @Test fun onlyTheSameTitleMatches() {
        val items = listOf(item("Solo Leveling Season 2"), item("Solo Leveling 2"), item("https://site/search?q=Solo Leveling"), item("Solo Leveling", type = "Movie"), item("Solo Leveling (2024)"), item("Solo Leveling"))
        assertEquals("Solo Leveling (2024)", OtherSources.pick("Solo Leveling", 2024, true, items)?.name)
        assertEquals("a year that differs is another title", null, OtherSources.pick("Solo Leveling", 2019, true, listOf(item("Solo Leveling (2024)"))))
        assertEquals("a film is not the series", null, OtherSources.pick("Solo Leveling", null, true, listOf(item("Solo Leveling", type = "Movie"))))
        assertNull("a search page is never a title", OtherSources.pick("Solo Leveling", null, null, listOf(item("https://site/search?q=Solo Leveling"))))
        assertNull(OtherSources.pick("Solo Leveling", null, true, listOf(item("Solo Leveling 2"), item("Solo Leveling Season 2"))))
        assertEquals("tags and quality don't count", "Solo Leveling [Hindi] 1080p", OtherSources.pick("Solo Leveling", null, true, listOf(item("Solo Leveling [Hindi] 1080p")))?.name)
    }

    @Test fun theSameVersionAndEpisodeOrNothing() {
        assertEquals("Dub", OtherSources.versionFor("Dub", listOf("Sub", "Dub")))
        assertEquals("Dubbed", OtherSources.versionFor("Dub", listOf("Subbed", "Dubbed")))
        assertNull("never Sub for a Dub", OtherSources.versionFor("Dub", listOf("Sub")))
        assertNull("never Dub for a Sub", OtherSources.versionFor("Sub", listOf("Dub")))
        assertEquals("", OtherSources.versionFor("Sub", listOf("")))
        assertEquals("Sub", OtherSources.versionFor("", listOf("Dub", "Sub")))
        assertEquals("", OtherSources.versionFor("Dub", emptyList()))

        val other = OnlineRepository.map(Show("o$tag", "Show"), runBlocking { Show("o$tag", "Show").load("https://example.invalid/show")!! })
        assertEquals("https://example.invalid/show|e2", OtherSources.dataFor(other, "", ep(2)))
        assertNull("an episode it doesn't have", OtherSources.dataFor(other, "", ep(9)))
        assertNull("another season's episode", OtherSources.dataFor(other, "", ep(2, season = 3)))
    }

    @Test fun candidatesAreOtherVideoSources() {
        val own = Show("Own $tag", "Show")
        val clone = Show("Own $tag · 127.0.0.1", "Show")
        val music = Show("Music $tag", "Show", setOf(TvType.Music))
        val adult = Show("Adult $tag", "Show", setOf(TvType.NSFW))
        val films = Show("Films $tag", "Show", setOf(TvType.Movie))
        val lookup = Show("Lookup $tag", "Show")
        val others = (1..20).map { Show("S$it $tag", "Show") }
        val list = OtherSources.candidates(own.name, true, false, listOf(own, clone, music, adult, films, lookup) + others, { it === lookup })
        assertEquals(OtherSources.MAX_SOURCES, list.size)
        assertTrue(list.none { it === own || it === clone || it === music || it === adult || it === films || it === lookup })
        assertTrue("an 18+ title may ask 18+ sources", OtherSources.candidates(own.name, true, true, listOf(adult), { false }).contains(adult))
        assertTrue("a film asks film sources", OtherSources.candidates(own.name, false, false, listOf(films), { false }).contains(films))
    }

    @Test fun lowEndPhonesAskOnlyWhenNeeded() {
        assertEquals(OtherSources.Mode.WHEN_NEEDED, OtherSources.defaultMode(lowEnd = true))
        assertEquals(OtherSources.Mode.PARALLEL, OtherSources.defaultMode(lowEnd = false))
    }

    @Test fun theSweepBringsOnlyTheMatchingEpisodeFromOtherSources() = runBlocking {
        val own = Show("Own $tag", "Yu Show $tag")
        val detail = OnlineRepository.map(own, own.load("https://example.invalid/own")!!)
        val right = Show("Right $tag", "Yu Show $tag")
        val decoy = Show("Decoy $tag", "Yu Show $tag 2")
        val start = CompletableDeferred<Boolean>()
        start.complete(true)
        val hits = OtherSources.sweep(detail, "", OnlineSession.episodesOf(detail, "")[1], listOf(right, decoy), start).toList()
        val links = hits.filterIsInstance<OtherSources.Hit.Event>().mapNotNull { (it.event as? LinkEvent.Link)?.let { l -> it.api to l.link.url } }
        assertEquals(listOf(right.name), hits.filterIsInstance<OtherSources.Hit.Matched>().map { it.api })
        assertEquals(listOf(right.name to "https://cdn.invalid/${right.name.hashCode()}/e2.m3u8"), links)
        assertFalse("the decoy title never joins", hits.any { it.api == decoy.name && it !is OtherSources.Hit.Matched && (it as OtherSources.Hit.Event).event is LinkEvent.Link })
    }

    @Test fun whenNeededWaitsForTheSignal() = runBlocking {
        val own = Show("Own2 $tag", "Yu Wait $tag")
        val detail = OnlineRepository.map(own, own.load("https://example.invalid/own2")!!)
        val start = CompletableDeferred<Boolean>()
        start.complete(false)
        val hits = OtherSources.sweep(detail, "", OnlineSession.episodesOf(detail, "")[0], listOf(Show("R2 $tag", "Yu Wait $tag")), start).toList()
        assertTrue("the own extension worked, so nothing is searched: $hits", hits.isEmpty())
    }
}
