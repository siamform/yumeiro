package dev.asfakul.yumeiro.online.upcoming

// F-08 (ledger F-08, docs/proposals/FEATURE-F-08-upcoming-movies-series.md, ADR-034): Upcoming movies, series
// and anime from Simkl's public calendar files, read as a stream (the TV file is ~2.5 MB / 5,600 entries).
// Fail-before: package online.upcoming does not exist.

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class F08UpcomingTest {
    private val zone: ZoneId = ZoneId.of("Asia/Dhaka")
    private val today: LocalDate = LocalDate.of(2026, 11, 19)

    private fun e(title: String, date: String, rank: Int, id: Int, season: Int? = null, ep: Int? = null, poster: String? = "21/abc") =
        """{"title":${q(title)},"poster":${poster?.let(::q) ?: "null"},"date":"$date","release_date":"${date.take(10)}","rank":$rank,
           "ratings":{"simkl":{"rating":null,"votes":null}},"url":"https://simkl.com/tv/$id/x","ids":{"simkl_id":$id,"slug":"x","tmdb":"1"}
           ${if (ep != null) ""","episode":{${if (season != null) "\"season\":$season," else ""}"episode":$ep,"url":"u"}""" else ""}}"""
    private fun q(s: String) = "\"" + s.replace("\"", "\\\"") + "\""
    private fun parse(vararg items: String, max: Int = 150, days: Int = 14) =
        Upcoming.parse(("[" + items.joinToString(",") + "]").byteInputStream(), Upcoming.Kind.SERIES, today, zone, days, max)

    @Test fun keepsTheNextTwoWeeksSortedByDayThenPopularity() {
        val out = parse(
            e("Old", "2026-11-10T00:00:00-05:00", 5, 1),
            e("Later", "2026-12-20T00:00:00-05:00", 5, 2),
            e("B", "2026-11-21T20:00:00-05:00", 50, 3, 1, 4),
            e("A", "2026-11-21T20:00:00-05:00", 7, 4, 2, 1),
            e("Today", "2026-11-19T12:00:00+06:00", 9, 5),
        )
        assertEquals(listOf("Today", "A", "B"), out.map { it.title })
        assertEquals("S2 E1", out[1].episodeLabel)
        assertEquals(LocalDate.of(2026, 11, 22), out[1].day) // 20:00 New York = next morning in Dhaka
    }

    @Test fun unrankedTitlesComeLastAndTheListIsCapped() {
        val items = (1..30).map { e("T$it", "2026-11-20T00:00:00+06:00", if (it % 2 == 0) 0 else it, it) }.toTypedArray()
        val out = parse(*items, max = 10)
        assertEquals(10, out.size)
        assertTrue(out.map { it.title }.toString(), out.all { it.rank > 0 })
    }

    @Test fun oneShowManyEpisodesTheSameDayIsOneEntry() {
        val out = parse(*(1..5).map { e("Formula 1", "2026-11-20T00:00:00+06:00", 12, 99, 2026, it) }.toTypedArray())
        assertEquals(1, out.size)
        assertEquals("S2026 E1–5", out[0].episodeLabel)
    }

    @Test fun brokenEntriesAreSkippedNotFatal() {
        val out = parse("""{"title":null}""", """{"foo":1}""", e("Ok", "2026-11-20T00:00:00+06:00", 3, 7), """{"title":"NoDate","rank":1}""")
        assertEquals(listOf("Ok"), out.map { it.title })
    }

    @Test fun posterUsesTheSmallCardSizeAndNoPosterStaysNull() {
        val out = parse(e("P", "2026-11-20T00:00:00+06:00", 3, 7), e("N", "2026-11-20T00:00:00+06:00", 4, 8, poster = null))
        assertEquals("https://simkl.in/posters/21/abc_ca.jpg", out[0].posterUrl)
        assertEquals(null, out[1].posterUrl)
    }

    @Test fun dayLabels() {
        assertEquals("Today", Upcoming.dayLabel(today, today))
        assertEquals("Tomorrow", Upcoming.dayLabel(today.plusDays(1), today))
        assertTrue(Upcoming.dayLabel(today.plusDays(3), today, java.util.Locale.US).startsWith("Sunday"))
    }
}
