/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodeMatcherTest {
    private fun ep(n: Int?, season: Int? = null, name: String? = null) = OnlineEpisode("d$n$season$name", name, season, n, null, null, null)

    @Test fun sameNumber_matchesEvenWhenListsAreOffset() {
        val sub = ep(7)
        val dub = listOf(ep(1), ep(2), ep(3), ep(4), ep(5), ep(6), ep(7))
        assertEquals(6, EpisodeMatcher.match(sub, dub))
    }

    @Test fun missingDubEpisode_doesNotFallBackToSameIndex() {
        // Sub has 12 episodes, dub only 5: the old code played dub E5 when asked for E9.
        val dub = (1..5).map { ep(it) }
        assertEquals(-1, EpisodeMatcher.match(ep(9), dub))
    }

    @Test fun nullSeasonEqualsSeasonOne() {
        assertEquals(1, EpisodeMatcher.match(ep(2, season = null), listOf(ep(1, 1), ep(2, 1))))
    }

    @Test fun differentSeasonIsNotAMatch() {
        assertEquals(-1, EpisodeMatcher.match(ep(2, season = 2), listOf(ep(1, 1), ep(2, 1), ep(3, 3))))
    }

    @Test fun unnumberedEpisodes_matchByNormalizedName() {
        val cur = ep(null, name = "Episode 3 - The Return (Sub)")
        val list = listOf(ep(null, name = "Arrival"), ep(null, name = "The Return [Dub]"))
        assertEquals(1, EpisodeMatcher.match(cur, list))
    }

    @Test fun nothingToMatch() {
        assertEquals(-1, EpisodeMatcher.match(null, listOf(ep(1))))
        assertEquals(-1, EpisodeMatcher.match(ep(1), emptyList()))
    }
}
