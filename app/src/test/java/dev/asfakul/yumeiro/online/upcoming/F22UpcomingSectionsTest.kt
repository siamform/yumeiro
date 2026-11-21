/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.upcoming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Locale

/**
 * F-22: Upcoming date sections. Also a day bug found while building it: Simkl writes "date only" as midnight
 * New York time, and converting that put every movie (and date-only episode) on the day before for anyone west
 * of New York (Los Angeles: 21:00 the previous day).
 */
class F22UpcomingSectionsTest {
    private val today = LocalDate.of(2026, 11, 21) // a Saturday

    @Test fun dateOnlyKeepsItsCalendarDayInEveryZone() {
        val odt = OffsetDateTime.parse("2026-11-22T00:00:00-05:00")
        for (z in listOf("America/Los_Angeles", "Pacific/Honolulu", "Asia/Dhaka", "Pacific/Kiritimati", "UTC")) {
            val (day, time) = Upcoming.placeInZone(odt, Upcoming.Kind.MOVIES, ZoneId.of(z))
            assertEquals(z, LocalDate.of(2026, 11, 22), day); assertNull(z, time)
            val (sDay, sTime) = Upcoming.placeInZone(odt, Upcoming.Kind.SERIES, ZoneId.of(z))
            assertEquals(z, LocalDate.of(2026, 11, 22), sDay); assertNull(z, sTime)
        }
    }

    @Test fun timedEpisodesMoveToTheLocalDayAndTime() {
        // 20:00 New York = 07:00 next day in Dhaka.
        val (day, time) = Upcoming.placeInZone(OffsetDateTime.parse("2026-11-21T20:00:00-05:00"), Upcoming.Kind.SERIES, ZoneId.of("Asia/Dhaka"))
        assertEquals(LocalDate.of(2026, 11, 22), day); assertEquals(LocalTime.of(7, 0), time)
        // Anime 00:00 JST is a real late-night slot: 21:00 the day before in Dhaka.
        val (aDay, aTime) = Upcoming.placeInZone(OffsetDateTime.parse("2026-11-23T00:00:00+09:00"), Upcoming.Kind.ANIME, ZoneId.of("Asia/Dhaka"))
        assertEquals(LocalDate.of(2026, 11, 22), aDay); assertEquals(LocalTime.of(21, 0), aTime)
    }

    @Test fun parseUsesTheRuleAndCacheKeepsTheTime() {
        val json = """[{"title":"M","date":"2026-11-22T00:00:00-05:00","rank":1,"ids":{"simkl_id":1}},
            {"title":"S","date":"2026-11-21T20:00:00-05:00","rank":2,"ids":{"simkl_id":2},"episode":{"season":1,"episode":3}}]"""
        val la = ZoneId.of("America/Los_Angeles")
        val movies = Upcoming.parse(json.byteInputStream(), Upcoming.Kind.MOVIES, today, la)
        assertEquals(LocalDate.of(2026, 11, 22), movies.first { it.title == "M" }.day)
        val series = Upcoming.parse(json.byteInputStream(), Upcoming.Kind.SERIES, today, la)
        val s = series.first { it.title == "S" }
        assertEquals(LocalDate.of(2026, 11, 21), s.day); assertEquals(LocalTime.of(17, 0), s.time)
        val back = Upcoming.decodeCache(Upcoming.encodeCache(series).byteInputStream())
        assertEquals(series, back)
    }

    @Test fun headers() {
        val us = Locale.US
        assertEquals("Today • Sat, Nov 21", Upcoming.dayHeader(today, today, Upcoming.Kind.ANIME, us))
        assertEquals("Tomorrow • Sun, Nov 22", Upcoming.dayHeader(today.plusDays(1), today, Upcoming.Kind.ANIME, us))
        assertEquals("Anime • Mon, Nov 23, 2026", Upcoming.dayHeader(today.plusDays(2), today, Upcoming.Kind.ANIME, us))
        // Around new year the year is the section's own.
        assertEquals("Series • Fri, Jan 1, 2027", Upcoming.dayHeader(LocalDate.of(2027, 1, 1), LocalDate.of(2026, 12, 30), Upcoming.Kind.SERIES, us))
    }

    @Test fun timeLabelOnlyWhenKnown() {
        val e = Upcoming.Entry("T", today, 1, null, null, 1, null, 1, time = LocalTime.of(20, 0))
        assertEquals("8:00 PM", Upcoming.timeLabel(e, Locale.US)?.replace('\u202F', ' '))
        assertNull(Upcoming.timeLabel(e.copy(time = null), Locale.US))
    }
}
