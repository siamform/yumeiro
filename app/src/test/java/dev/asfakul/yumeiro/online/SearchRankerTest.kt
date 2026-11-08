/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRankerTest {
    private fun item(name: String, api: String, url: String = "/$api/$name", type: String? = "Anime") =
        OnlineItem(name = name, url = url, api = api, posterUrl = null, type = type)

    @Test fun scoring_exactBeatsPrefixBeatsWords() {
        val e = SearchRanker.score("One Piece", "One Piece")
        val p = SearchRanker.score("One Piece", "One Piece Film: Red")
        val w = SearchRanker.score("One Piece", "Piece of One")
        val n = SearchRanker.score("One Piece", "Naruto")
        assertTrue(e > p && p > w && w > n)
        assertEquals(100, SearchRanker.score("pokémon", "Pokemon"))
    }

    @Test fun mergesByRelevanceNotArrival() {
        val ranked = SearchRanker.rank(
            "naruto",
            mapOf(
                "B" to listOf(item("Boruto: Naruto Next Generations", "B"), item("Naruto", "B")),
                "A" to listOf(item("Naruto Shippuden", "A")),
            ),
            sourceOrder = listOf("A", "B"),
        )
        assertEquals("Naruto", ranked.first().item.name)
        assertEquals("Naruto Shippuden", ranked[1].item.name)
    }

    @Test fun duplicateKeysDropped_andCrossSourceCopiesCounted() {
        val ranked = SearchRanker.rank(
            "bleach",
            mapOf(
                "A" to listOf(item("Bleach", "A", url = "/x"), item("Bleach", "A", url = "/x")),
                "B" to listOf(item("BLEACH", "B")),
            ),
            sourceOrder = listOf("A", "B"),
        )
        assertEquals(2, ranked.size)
        assertEquals(listOf(1, 1), ranked.map { it.alsoOn })
        assertEquals(ranked.map { it.item.key }.toSet().size, ranked.size)
    }

    @Test fun stableForEqualScores_followsSourceOrder() {
        val ranked = SearchRanker.rank(
            "x",
            mapOf("B" to listOf(item("Something", "B")), "A" to listOf(item("Other", "A"))),
            sourceOrder = listOf("A", "B"),
        )
        assertEquals(listOf("A", "B"), ranked.map { it.item.api })
    }
}
