/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.classics

import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import dev.asfakul.yumeiro.FeatureFlags
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
 * OWN-9 (owner 2026-11-22: "there doesn't seem to be any default extension installed"): the official built-in
 * Yumeiro Classics source. Answers are shaped like the Internet Archive's real ones (checked 2026-11-22:
 * advancedsearch docs, metadata files with h.264 / 512Kb MPEG4 / SubRip).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Own9YumeiroClassicsTest {
    private val search = """{"response":{"numFound":3,"docs":[
        {"identifier":"charlie_chaplin_film_fest","title":"Charlie Chaplin Festival","year":1938},
        {"identifier":"sex_madness","title":"Sex Madness","year":1938},
        {"identifier":"suddenly","title":"Suddenly","year":"1954","subject":["Film Noir"]}]}}"""
    private val meta = """{"metadata":{"title":"Charlie Chaplin Festival","date":"1938","description":"<p>Four shorts</p>","subject":"comedy; silent"},
        "files":[{"name":"charlie_chaplin_film_fest.asr.srt","format":"SubRip"},
        {"name":"charlie_chaplin_film_fest.mp4","format":"h.264","height":"480"},
        {"name":"charlie_chaplin_film_fest.ogv","format":"Ogg Video","height":"304"},
        {"name":"charlie_chaplin_film_fest_512kb.mp4","format":"512Kb MPEG4","height":"240"}]}"""
    private val asked = mutableListOf<String>()
    private val api = YumeiroClassicsProvider { url -> asked += url; if ("advancedsearch" in url) search else meta }

    @Test fun onByDefaultForFreshInstalls() {
        assertTrue(FeatureFlags.Flag.OFFICIAL_CLASSICS.default)
    }

    @Test fun shelvesDropAdultTitlesInTheQueryAndAgain() = runBlocking {
        val home = api.getMainPage(1, MainPageRequest("Classic feature films", "feature_films", false))
        val names = home.items.first().list.map { it.name }
        assertEquals(listOf("Charlie Chaplin Festival", "Suddenly"), names)
        assertTrue("the adult filter is in the query", asked.first().contains("NOT%20title"))
        assertTrue(YumeiroClassicsProvider.adult("Sex Madness"))
        assertFalse(YumeiroClassicsProvider.adult("Sussex Story"))
    }

    @Test fun searchKeepsTheUsersWordsOutOfTheQuerySyntax() = runBlocking {
        assertEquals("chaplin or 1", YumeiroClassicsProvider.cleanQuery("chaplin) OR (1"))
        assertEquals("", YumeiroClassicsProvider.cleanQuery("\"():*"))
        val r = api.search("chaplin", 1)
        assertEquals(2, r.items.size)
        assertEquals("classics:charlie_chaplin_film_fest", r.items.first().url)
    }

    @Test fun loadAndPlayTheArchiveMp4s() = runBlocking {
        val d = api.load("classics:charlie_chaplin_film_fest")
        assertEquals("Charlie Chaplin Festival", d.name)
        assertEquals(1938, d.year)
        assertEquals("Four shorts", d.plot)
        val links = mutableListOf<ExtractorLink>()
        val subs = mutableListOf<SubtitleFile>()
        assertTrue(api.loadLinks("classics:charlie_chaplin_film_fest", false, { subs += it }, { links += it }))
        assertEquals(listOf("https://archive.org/download/charlie_chaplin_film_fest/charlie_chaplin_film_fest.mp4",
            "https://archive.org/download/charlie_chaplin_film_fest/charlie_chaplin_film_fest_512kb.mp4"), links.map { it.url })
        assertEquals(480, links.first().quality)
        assertEquals(1, subs.size)
    }

    @Test fun badIdsAreRefused() {
        assertNull(YumeiroClassicsProvider.idOf("classics:../../etc"))
        assertNull(YumeiroClassicsProvider.idOf("https://example.com/x"))
        assertEquals("suddenly", YumeiroClassicsProvider.idOf("classics:suddenly"))
    }
}
