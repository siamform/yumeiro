/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.upcoming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * F-22-C: the Upcoming Country and Genre filters. Simkl's calendar has no country or genre, so Series by
 * country come from TVmaze's day schedule (show embedded as "show" on /schedule, "_embedded.show" on
 * /schedule/web). Samples are trimmed copies of real responses (2026-11-22).
 */
class F22CountryGenreTest {
    private val today = LocalDate.of(2026, 11, 22)
    private val dhaka = ZoneId.of("Asia/Dhaka")

    private val jpDay = """[
      {"id":1,"name":"Episode 8","season":1,"number":8,"airdate":"2026-11-23","airtime":"00:00","airstamp":"2026-11-22T15:00:00+00:00",
       "show":{"id":77,"name":"From Far Away","genres":["Anime","Fantasy","Romance"],"weight":80,
               "network":{"name":"TV Tokyo","country":{"name":"Japan","code":"JP"}},"image":{"medium":"https://static.tvmaze.com/a.jpg"}}},
      {"id":2,"name":"Episode 9","season":1,"number":9,"airdate":"2026-11-23","airtime":"00:30","airstamp":"2026-11-22T15:30:00+00:00",
       "show":{"id":77,"name":"From Far Away","genres":["Anime","Fantasy","Romance"],"weight":80,
               "network":{"name":"TV Tokyo","country":{"name":"Japan","code":"JP"}}}},
      {"id":3,"name":"News","season":2026,"number":null,"airdate":"2026-11-23","airtime":"","airstamp":"2026-11-22T15:00:00+00:00",
       "show":{"id":90,"name":"Morning News","genres":["News"],"weight":30,"network":{"name":"NHK","country":{"code":"JP"}}}},
      {"broken":true}
    ]"""

    private val webDay = """[
      {"id":5,"season":1,"number":3,"airdate":"2026-11-24","airtime":"19:30","airstamp":"2026-11-24T11:30:00+00:00",
       "_embedded":{"show":{"id":500,"name":"River of No Return","genres":["Drama","History"],"weight":65,
                            "webChannel":{"name":"iQIYI","country":null}}}}
    ]"""

    @Test fun dayScheduleGivesCountryGenresAndLocalTime() {
        val e = TvmazeSchedule.parseDay(jpDay, dhaka)
        assertEquals("broken item skipped", 3, e.size)
        val first = e[0]
        assertEquals("From Far Away", first.title)
        assertEquals("JP", first.country)
        assertEquals(listOf("Anime", "Fantasy", "Romance"), first.genres)
        // 15:00 UTC = 21:00 in Dhaka, same day.
        assertEquals(LocalDate.of(2026, 11, 22), first.day); assertEquals(LocalTime.of(21, 0), first.time)
        assertEquals("https://static.tvmaze.com/a.jpg", first.posterUrl)
        // No air time: calendar date kept, no time.
        val news = e[2]; assertEquals(LocalDate.of(2026, 11, 23), news.day); assertNull(news.time)
    }

    @Test fun streamingScheduleReadsTheEmbeddedShow() {
        val e = TvmazeSchedule.parseDay(webDay, dhaka).single()
        assertEquals("River of No Return", e.title); assertNull(e.country)
        assertEquals(listOf("Drama", "History"), e.genres)
        assertEquals("Drama, History", TvmazeSchedule.badge(e))
    }

    @Test fun episodesOfOneShowMergeAndPopularComesFirstWithinTheTime() {
        val list = TvmazeSchedule.merge(listOf(TvmazeSchedule.parseDay(jpDay, dhaka), TvmazeSchedule.parseDay(webDay, dhaka)), today)
        val far = list.single { it.title == "From Far Away" }
        assertEquals("E8–9", far.episodeLabel?.substringAfter(' '))
        assertEquals(LocalTime.of(21, 0), far.time)
        assertEquals(listOf("From Far Away", "Morning News", "River of No Return"), list.map { it.title })
        assertEquals("JP • Anime, Fantasy", TvmazeSchedule.badge(far))
    }

    @Test fun genreChipsAndFilter() {
        val list = TvmazeSchedule.merge(listOf(TvmazeSchedule.parseDay(jpDay, dhaka), TvmazeSchedule.parseDay(webDay, dhaka)), today)
        val genres = TvmazeSchedule.genresOf(list)
        assertTrue(genres.containsAll(listOf("Anime", "Drama", "News")))
        assertEquals(listOf("River of No Return"), TvmazeSchedule.filterGenre(list, "drama").map { it.title })
        assertEquals(list, TvmazeSchedule.filterGenre(list, null))
    }

    @Test fun urlsAndCacheKeepCountryAndGenres() {
        assertEquals("https://api.tvmaze.com/schedule?country=GB&date=2026-11-22", TvmazeSchedule.url("GB", today))
        assertEquals("https://api.tvmaze.com/schedule/web?country=&date=2026-11-22", TvmazeSchedule.url(TvmazeSchedule.STREAMING, today))
        val list = TvmazeSchedule.parseDay(jpDay, dhaka)
        val back = Upcoming.decodeCache(ByteArrayInputStream(Upcoming.encodeCache(list).toByteArray()))
        assertEquals(list.map { it.country to it.genres }, back.map { it.country to it.genres })
    }

    @Test fun flagIsOn() {
        assertTrue(dev.asfakul.yumeiro.FeatureFlags.Flag.F22_COUNTRY_GENRE.default)
    }
}
