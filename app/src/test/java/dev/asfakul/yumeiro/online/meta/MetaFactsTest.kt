/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.meta

import dev.asfakul.yumeiro.online.OnlineKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * META-1 (owner list 13). The fixtures are real answers saved on 2026-11-23: Cinemeta for tt0111161 (The Shawshank
 * Redemption), Simkl for tt0903747 (Breaking Bad), and Wikidata for the exact query TitleFacts sends.
 */
class MetaFactsTest {
    private fun res(name: String) = javaClass.classLoader!!.getResource("meta/$name")!!.readText()

    @Test fun cinemetaGivesCrewCastDateAndTrailers() {
        val f = TitleFacts.parseCinemeta(res("cinemeta-tt0111161.json"))!!
        assertEquals("1994-10-14", f.released)
        assertEquals(142, f.runtimeMin)
        assertEquals(9.3, f.rating!!, 0.001)
        assertEquals(listOf("Frank Darabont"), f.directors)
        assertEquals(listOf("Stephen King", "Frank Darabont"), f.writers)
        assertEquals(listOf("United States"), f.countries)
        assertTrue(f.cast.take(2).map { it.name } == listOf("Tim Robbins", "Morgan Freeman"))
        assertEquals("https://www.youtube.com/watch?v=PLl99DlL6b4", f.trailers.first().url)
        assertNull("Cinemeta has no vote count", f.votes)
    }

    @Test fun simklGivesVotesCertificateLanguageNetwork() {
        val f = TitleFacts.parseSimkl(res("simkl-tv-tt0903747.json"))!!
        assertEquals(9.5, f.rating!!, 0.001)
        assertEquals(2_708_205L, f.votes)
        assertEquals("TV-MA", f.certification)
        assertEquals(listOf("United States"), f.countries)
        assertEquals(listOf("English"), f.languages)
        assertEquals("AMC", f.network)
        assertEquals("2008-01-21", f.released)
        assertEquals(47, f.runtimeMin)
        assertEquals("https://www.youtube.com/watch?v=VFkjBy2b50Q", f.trailers.single().url)
    }

    @Test fun wikidataGivesCompanies() {
        val f = TitleFacts.parseWikidata(res("wikidata-tt0111161.json"))!!
        assertEquals(listOf("Castle Rock Entertainment", "Columbia Pictures"), f.companies)
        assertEquals(listOf("English"), f.languages)
        assertNull(TitleFacts.parseWikidata("""{"results":{"bindings":[]}}"""))
    }

    @Test fun onlyExistingRowsAreShown() {
        val c = TitleFacts.parseCinemeta(res("cinemeta-tt0111161.json"))!!
        val w = TitleFacts.parseWikidata(res("wikidata-tt0111161.json"))!!
        val rows = TitleFacts.rows(c.or(w), OnlineKind.MOVIE).toMap()
        assertEquals("14 October 1994", rows["Release date"])
        assertEquals("Frank Darabont", rows["Director"])
        assertEquals("Stephen King, Frank Darabont", rows["Writers"])
        assertEquals("Castle Rock Entertainment, Columbia Pictures", rows["Production companies"])
        assertEquals("English", rows["Language"])
        assertTrue("no empty rows", "Network" !in rows && "Certificate" !in rows)
        assertTrue(TitleFacts.rows(TitleFacts.Facts(), OnlineKind.SERIES).isEmpty())
    }

    @Test fun mergeKeepsTheFirstAndPairsVotesWithTheirRating() {
        val a = TitleFacts.Facts(rating = 8.0, directors = listOf("A"))
        val b = TitleFacts.Facts(rating = 7.0, votes = 99, directors = listOf("B"), companies = listOf("C"))
        val m = a.or(b)
        assertEquals(8.0, m.rating!!, 0.0)
        assertNull("votes of another rating are not borrowed", m.votes)
        assertEquals(listOf("A"), m.directors)
        assertEquals(listOf("C"), m.companies)
    }

    @Test fun smallFormatters() {
        assertEquals(142, TitleFacts.minutes("142 min"))
        assertEquals(142, TitleFacts.minutes("2h 22m"))
        assertEquals(60, TitleFacts.minutes("1h"))
        assertNull(TitleFacts.minutes("n/a"))
        assertEquals("2.7M votes", TitleFacts.votesText(2_708_205))
        assertEquals("442K votes", TitleFacts.votesText(442_637))
        assertEquals("3.4K votes", TitleFacts.votesText(3_400))
        assertEquals("1 vote", TitleFacts.votesText(1))
        assertEquals("PLl99DlL6b4", TitleFacts.youTubeId("https://www.youtube.com/watch?v=PLl99DlL6b4"))
        assertEquals("PLl99DlL6b4", TitleFacts.youTubeId("https://youtu.be/PLl99DlL6b4?t=3"))
        assertNull(TitleFacts.youTubeId("https://vimeo.com/1"))
        assertEquals("Japanese", TitleFacts.languageName("ja"))
    }
}
