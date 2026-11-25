/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.hub

import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSearchResponseList
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/**
 * FIND-2 (item 26, ADR-066): the V2 sweep beside V1, run on the same fake sources with real waits, measured.
 * The numbers print as FIND2_MEASURE lines (requests asked and wall time, V1 vs V2).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Find2Test {
    private val cloud = mapOf("/title?type=movie&id=2" to """{"id":"movie:2","type":"movie","title":"Parasite","year":2019}""")
    private fun fetch(url: String): String? = cloud[url.substringAfter("/v1/hub")]

    private class Src(override var name: String, val has: Boolean, val waitMs: Long = 150, val links: Boolean = true, val boom: String? = null) : MainAPI() {
        override var mainUrl = "https://src.example"
        override val supportedTypes = setOf(TvType.Movie)
        val searches = AtomicInteger(); val loads = AtomicInteger()
        override suspend fun search(query: String, page: Int): SearchResponseList {
            searches.incrementAndGet(); delay(waitMs)
            boom?.let { error(it) }
            return newSearchResponseList(if (has) listOf(newMovieSearchResponse("Parasite", "m/$name", TvType.Movie, fix = false) { year = 2019 }) else emptyList(), false)
        }
        override suspend fun search(query: String): List<SearchResponse> = search(query, 1).items
        override suspend fun load(url: String): LoadResponse { loads.incrementAndGet(); delay(waitMs / 2); return newMovieLoadResponse(url, url, TvType.Movie, "$url#play") }
        override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
            if (links) callback(newExtractorLink(name, "$name 1080p", "https://cdn.example/$name", ExtractorLinkType.VIDEO))
            return links
        }
    }

    @Before fun reset() { YumeiroCatalog.Ranking.clear(); SourceFinder2.resetForTests() }

    private fun provider(v2: Boolean, srcs: List<MainAPI>) =
        YumeiroCatalogProvider(fetch = ::fetch, sources = { srcs }, base = "", builtins = { emptyList() }, findV2 = { v2 })

    @Test fun wavesPutBuiltinsThenTheRememberedSourceFirst() {
        assertEquals(listOf(listOf("own"), listOf("c"), listOf("a", "b", "d")), SourceFinder2.waves(listOf("own"), listOf("a", "b", "c", "d"), "c"))
        assertEquals(listOf(listOf("a", "b")), SourceFinder2.waves(emptyList(), listOf("a", "b"), "gone"))
        assertTrue(SourceFinder2.waves(emptyList<String>(), emptyList(), null).isEmpty())
    }

    @Test fun reasonsAndTheSentenceTheUserReads() {
        assertEquals(SourceFinder2.Reason.TIMED_OUT, SourceFinder2.reasonOf(null))
        assertEquals(SourceFinder2.Reason.BLOCKED, SourceFinder2.reasonOf(RuntimeException("HTTP 403 Cloudflare")))
        assertEquals(SourceFinder2.Reason.FAILED, SourceFinder2.reasonOf(IllegalStateException("parse")))
        val o = listOf(SourceFinder2.Outcome("A", SourceFinder2.Reason.NO_TITLE_MATCH, 1), SourceFinder2.Outcome("B", SourceFinder2.Reason.TIMED_OUT, 1))
        assertEquals("B took too long to answer. Try again, or open the title in that source.", SourceFinder2.detail(o, 2))
        assertEquals("None of your 1 sources has this title.", SourceFinder2.detail(o.take(1), 1))
        assertTrue(SourceFinder2.detail(emptyList(), 0).startsWith("Install a video source"))
    }

    @Test fun measuredV1AgainstV2OnNineSources() = runBlocking {
        fun nine() = (1..9).map { Src("S$it", has = it == 2) }
        val v1 = nine()
        var t = System.currentTimeMillis()
        assertTrue(provider(false, v1).loadLinks("catalog:movie:2", false, {}) {})
        val v1Ms = System.currentTimeMillis() - t; val v1Asked = v1.sumOf { it.searches.get() }
        YumeiroCatalog.Ranking.clear()
        val v2 = nine(); val links = mutableListOf<ExtractorLink>()
        t = System.currentTimeMillis()
        assertTrue(provider(true, v2).loadLinks("catalog:movie:2", false, {}) { links += it })
        val v2Ms = System.currentTimeMillis() - t; val v2Asked = v2.sumOf { it.searches.get() }
        assertEquals(listOf("S2"), links.map { it.source })
        assertEquals("V1 asks every source", 9, v1Asked)
        assertEquals("V2 asks the first wave only", 3, v2Asked)
        // Second play of the same title: the remembered source alone, with no search.
        val again = nine(); t = System.currentTimeMillis()
        assertTrue(provider(true, again).loadLinks("catalog:movie:2", false, {}) {})
        val v2RepeatMs = System.currentTimeMillis() - t
        assertEquals("remembered: no search at all", 0, again.sumOf { it.searches.get() })
        assertEquals(1, again.single { it.name == "S2" }.loads.get())
        println("FIND2_MEASURE v1 asked=$v1Asked ms=$v1Ms | v2 asked=$v2Asked ms=$v2Ms | v2 repeat asked=0 ms=$v2RepeatMs")
    }

    @Test fun aSlowSourceInTheWinningWaveIsCutAfterTheGrace() = runBlocking {
        val slow = Src("Slow", has = true, waitMs = 60_000); val fast = Src("Fast", has = true)
        val t = System.currentTimeMillis()
        assertTrue(provider(true, listOf(slow, fast)).loadLinks("catalog:movie:2", false, {}) {})
        val ms = System.currentTimeMillis() - t
        assertTrue("ended about the grace after the hit ($ms ms)", ms < SourceFinder2.GRACE_MS + 2_000)
    }

    @Test fun aRememberedPageThatStoppedWorkingIsForgotten() = runBlocking {
        SourceFinder2.remember("movie:2", "S1", "m/S1")
        val s1 = Src("S1", has = true, links = false)
        try { provider(true, listOf(s1)).loadLinks("catalog:movie:2", false, {}) {}; fail("no links") }
        catch (e: SourceFinder2.NotFound) { assertEquals("S1 has this title but gave no playable links.", e.message) }
        assertEquals(null, SourceFinder2.recall("movie:2"))
    }

    @Test fun nothingInstalledOrNothingFoundSaysWhy() = runBlocking {
        try { provider(true, emptyList()).loadLinks("catalog:movie:2", false, {}) {}; fail() }
        catch (e: SourceFinder2.NotFound) { assertTrue(e.message!!.startsWith("Install a video source")) }
        try { provider(true, listOf(Src("A", has = false), Src("B", has = false, boom = "HTTP 403 Cloudflare"))).loadLinks("catalog:movie:2", false, {}) {}; fail() }
        catch (e: SourceFinder2.NotFound) { assertEquals("B is blocking this phone for now (site security). Try again later.", e.message) }
    }

    @Test fun timeoutsAreReportedAsSlowNotAsMissing() = runBlocking {
        val out = SourceFinder2.sweep(listOf(listOf("x", "y")), { it }, { false },
            ask = { s -> if (s == "x") { delay(5_000); SourceFinder2.Answer(SourceFinder2.Reason.FOUND) } else SourceFinder2.Answer(SourceFinder2.Reason.NO_TITLE_MATCH) },
            onFound = { _, _ -> }, budgetMs = 300)
        assertEquals(setOf("x" to SourceFinder2.Reason.TIMED_OUT, "y" to SourceFinder2.Reason.NO_TITLE_MATCH), out.map { it.source to it.reason }.toSet())
        assertFalse(out.any { it.reason == SourceFinder2.Reason.FOUND })
    }
}
