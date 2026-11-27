/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.groups

import dev.asfakul.yumeiro.online.blacksource.BlackSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** F14 (Group U): BlackSource's request, answer parsing and title / genre matching. */
@RunWith(RobolectricTestRunner::class)
class GroupUBlackSourceTest {
    @After fun reset() { BlackSource.baseForTest = null }

    private val frieren = BlackSource.Title(
        154587, "Sousou no Frieren", "Frieren: Beyond Journey's End", "葬送のフリーレン", listOf("Frieren"),
        2023, 28, "TV", null, listOf("Adventure", "Fantasy"), listOf("Elf"), "Shounen",
    )

    @Test fun u_searchUrlSendsValuesOnly() {
        BlackSource.baseForTest = "http://x/v1/blacksource"
        val u = BlackSource.searchUrl(BlackSource.Filter(words = " frieren ", genres = setOf("Fantasy", "Drama"), tags = listOf("Elf", " ", "Elf", "Time Travel"), demographic = "Shounen"))
        assertEquals("http://x/v1/blacksource/search?q=frieren&genres=Drama%2CFantasy&tags=Elf%2CTime+Travel&demographic=Shounen&sort=popular", u)
        assertTrue(BlackSource.searchUrl(BlackSource.Filter(genres = setOf("Action")), page = 3).endsWith("&page=3"))
        assertTrue(BlackSource.Filter().empty)
        assertFalse(BlackSource.Filter(demographic = "Seinen").empty)
    }

    @Test fun u_parseSkipsBrokenRows() {
        val p = BlackSource.parse("""{"items":[{"id":154587,"title":"Sousou no Frieren","english":"Frieren","synonyms":["F"],"year":2023,
            "genres":["Fantasy"],"tags":["Elf"],"demographic":"Shounen","cover":"http://plain"},{"id":0,"title":"x"},{"title":"no id"},{"id":5}],"more":true}""")
        assertEquals(1, p.items.size); assertTrue(p.more)
        val t = p.items[0]
        assertEquals("Frieren", t.english); assertEquals(null, t.cover); assertEquals("Shounen", t.demographic); assertEquals(listOf("F"), t.synonyms)
    }

    @Test fun u_matchesSourceNames() {
        assertTrue(BlackSource.matches("Sousou no Frieren", frieren))
        assertTrue(BlackSource.matches("Frieren: Beyond Journey’s End (Dub)", frieren))
        assertTrue(BlackSource.matches("Sousou no Frieren Season 2", frieren))
        assertTrue(BlackSource.matches("葬送のフリーレン", frieren))
        assertFalse(BlackSource.matches("Frieren Fan Recap Kids", frieren.copy(synonyms = emptyList(), english = null)))
        assertFalse(BlackSource.matches("Naruto", frieren))
        assertFalse(BlackSource.matches("", frieren))
    }

    @Test fun u_providerGenresMustCoverTheFilter() {
        val f = BlackSource.Filter(words = "yu", genres = setOf("Fantasy"), demographic = "Seinen")
        assertTrue(BlackSource.providerTagsMatch(listOf("fantasy", "SEINEN", "Drama"), f))
        assertFalse(BlackSource.providerTagsMatch(listOf("Fantasy"), f))
        assertFalse(BlackSource.providerTagsMatch(listOf("Fantasy"), BlackSource.Filter(words = "only words")))
    }
}
