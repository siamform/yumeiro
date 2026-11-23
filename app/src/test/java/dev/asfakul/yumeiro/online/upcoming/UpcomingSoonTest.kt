/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.upcoming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** UP-BANNER-1 (owner list R21, item 16A): what Yumix's rotating "Coming up" card shows. */
class UpcomingSoonTest {
    private val today = LocalDate.of(2026, 11, 22)
    private fun e(t: String, day: Long, rank: Int, poster: String? = "p") =
        Upcoming.Entry(t, today.plusDays(day), rank, poster, null, 1, null, t.hashCode().toLong())

    @Test fun onlyTheNextThreeDaysWithPostersMostPopularFirstAndMixedKinds() {
        val lists = mapOf(
            Upcoming.Kind.SERIES to listOf(e("S far", 3, 1), e("S1", 0, 5), e("S2", 1, 2), e("S no poster", 0, 1, null), e("S past", -1, 1)),
            Upcoming.Kind.MOVIES to listOf(e("M1", 2, 9), e("M2", 0, 0)),
            Upcoming.Kind.ANIME to listOf(e("A1", 1, 3)),
        )
        val p = UpcomingSoon.picks(lists, today, max = 4)
        val titles = p.map { it.entry.title }
        assertEquals(4, p.size)
        assertTrue("outside 3 days / no poster / past left out: $titles", titles.none { it in setOf("S far", "S no poster", "S past") })
        // Kinds in turn by popularity: S2 (rank 2), M1, A1, then S1. Shown soonest first.
        assertEquals(setOf("S2", "M1", "A1", "S1"), titles.toSet())
        assertEquals(titles, p.sortedBy { it.entry.day }.map { it.entry.title })
        assertEquals(setOf(Upcoming.Kind.SERIES, Upcoming.Kind.MOVIES, Upcoming.Kind.ANIME), p.map { it.kind }.toSet())
    }

    @Test fun oneTitleOnce() {
        val lists = mapOf(Upcoming.Kind.SERIES to listOf(e("Show", 0, 1), e("Show", 1, 1)), Upcoming.Kind.ANIME to listOf(e("show", 2, 4)))
        assertEquals(1, UpcomingSoon.picks(lists, today).size)
    }

    @Test fun realSimklAnimeFile() {
        val input = requireNotNull(javaClass.classLoader!!.getResource("upcoming/simkl_anime_sample.json")).openStream()
        val anime = Upcoming.parse(input, Upcoming.Kind.ANIME, today, ZoneId.of("Asia/Dhaka"))
        assertTrue(anime.isNotEmpty())
        val p = UpcomingSoon.picks(mapOf(Upcoming.Kind.ANIME to anime), today)
        assertTrue(p.isNotEmpty() && p.size <= UpcomingSoon.MAX)
        assertTrue(p.all { it.entry.posterUrl!!.startsWith("https://simkl.in/posters/") && it.entry.day < today.plusDays(3) })
    }

    @Test fun labels() {
        assertEquals("Today", UpcomingSoon.whenLabel(today, today))
        assertEquals("Tomorrow", UpcomingSoon.whenLabel(today.plusDays(1), today))
        assertEquals("In 2 days", UpcomingSoon.whenLabel(today.plusDays(2), today))
    }
}
