/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.groupo

import dev.asfakul.yumeiro.online.upcoming.Upcoming
import dev.asfakul.yumeiro.online.upcoming.UpcomingHub
import dev.asfakul.yumeiro.online.upcoming.TvmazeSchedule
import dev.asfakul.yumeiro.sources.hub.HubDiscover
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Group O B14 / F5 / F6 / F9: the hub discovery client and the Upcoming source plan (pure parts). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GroupODiscoverTest {
    private val today = LocalDate.of(2026, 11, 27)

    @Test fun pathKeepsOnlyCheckedValues() {
        val p = HubDiscover.path(HubDiscover.Query("movie", HubDiscover.Sort.UPCOMING, genres = listOf(28, 35, 18, 99), providers = listOf(8), region = "IN", origin = "IN", lang = "hi"), 2)
        assertEquals("/discover?type=movie&sort=upcoming&genre=28,35,18&provider=8&region=IN&origin=IN&lang=hi&page=2", p)
        val bad = HubDiscover.path(HubDiscover.Query("tv", region = "india", origin = "in&x=1", lang = "HIN", anime = true), 99)
        assertEquals("/discover?type=tv&sort=popular&anime=1&page=20", bad)
    }

    @Test fun parseSkipsBrokenRowsAndKeepsRealRatingsOnly() {
        val body = """{"items":[
            {"id":"catalog:movie:603","type":"movie","title":"The Matrix","year":1999,"date":"1999-03-30","rating":8.2,"votes":26000,"genres":["Action","Science Fiction"],"poster":"https://image.tmdb.org/t/p/w342/x.jpg"},
            {"id":"catalog:tv:1","type":"tv","title":"No rating","date":"2026-12-01"},
            {"id":"bad","type":"movie","title":"Broken id"},
            {"id":"catalog:movie:5","type":"person","title":"Wrong type"},
            {"id":"catalog:movie:6","type":"movie","title":"  "}
        ],"hasNext":true}"""
        val page = HubDiscover.parsePage(body)!!
        assertEquals(2, page.items.size)
        assertTrue(page.hasNext)
        val m = page.items[0]
        assertEquals("catalog:movie:603", m.url)
        assertEquals(8.2, m.rating!!, 0.0001)
        assertEquals(listOf("Action", "Science Fiction"), m.genres)
        assertEquals(LocalDate.of(1999, 3, 30), m.date)
        assertNull(page.items[1].rating)
        assertNull(page.items[1].poster)
        assertNull(HubDiscover.parsePage("not json"))
    }

    @Test fun providersParse() {
        val p = HubDiscover.parseProviders("""{"providers":[{"id":8,"name":"Netflix","logo":"https://image.tmdb.org/t/p/w92/n.png"},{"id":0,"name":"x"},{"id":119,"name":"Prime Video","logo":"http://insecure"}]}""")!!
        assertEquals(listOf(8, 119), p.map { it.id })
        assertNull(p[1].logo)
    }

    @Test fun countryAsksTheHubFirstThenTheOldSchedule() {
        // B14: Series + India used TVmaze only, which has no Indian channels → empty list.
        assertEquals(listOf(UpcomingHub.From.HUB, UpcomingHub.From.TVMAZE), UpcomingHub.plan(Upcoming.Kind.SERIES, "IN", hubOn = true))
        assertEquals(listOf(UpcomingHub.From.HUB, UpcomingHub.From.WIKIDATA), UpcomingHub.plan(Upcoming.Kind.MOVIES, "IN", hubOn = true))
        assertEquals(listOf(UpcomingHub.From.SIMKL), UpcomingHub.plan(Upcoming.Kind.SERIES, null, hubOn = true))
        assertEquals(listOf(UpcomingHub.From.TVMAZE), UpcomingHub.plan(Upcoming.Kind.SERIES, TvmazeSchedule.STREAMING, hubOn = true))
        assertEquals(listOf(UpcomingHub.From.TVMAZE), UpcomingHub.plan(Upcoming.Kind.SERIES, "IN", hubOn = false))
        assertEquals("IN", UpcomingHub.query(Upcoming.Kind.SERIES, "IN").origin)
        assertEquals(HubDiscover.Sort.UPCOMING, UpcomingHub.query(Upcoming.Kind.MOVIES, "IN").sort)
    }

    @Test fun hubEntriesCarryDayGenresAndCountryInTheWindow() {
        fun item(id: Long, date: LocalDate?) = HubDiscover.Item("T$id", "movie", id, null, null, date, null, null, listOf("Drama"), false)
        val list = UpcomingHub.entries(
            listOf(item(1, today.plusDays(10)), item(2, today.minusDays(1)), item(3, null), item(4, today), item(1, today.plusDays(10)), item(5, today.plusDays(60))),
            "IN", today,
        )
        assertEquals(listOf(4L, 1L), list.map { it.simklId })
        assertTrue(list.all { it.country == "IN" && it.genres == listOf("Drama") })
        assertFalse(list.any { it.day.isBefore(today) })
    }

    // F5 / F6 / F9: the section bar swaps the shelves; every section has real content, Discover has all the kinds.
    @Test fun sectionsAndShelves() {
        val d = dev.asfakul.yumeiro.ui.screens.online.Discover
        val disc = d.shelves(d.Section.DISCOVER)
        assertTrue(disc.any { it is dev.asfakul.yumeiro.ui.screens.online.Discover.IfYouLike })
        assertTrue(disc.any { it is dev.asfakul.yumeiro.ui.screens.online.Discover.Services })
        assertTrue(disc.any { it is dev.asfakul.yumeiro.ui.screens.online.Discover.Genres })
        assertEquals("hot_today", (disc.first() as dev.asfakul.yumeiro.ui.screens.online.Discover.Row).shelf)
        d.Section.entries.forEach { s -> assertTrue(s.name, d.shelves(s).isNotEmpty()); assertEquals(s.name, d.shelves(s).size, d.shelves(s).map { it.key }.distinct().size) }
        val anime = d.shelves(d.Section.ANIME).filterIsInstance<dev.asfakul.yumeiro.ui.screens.online.Discover.Row>()
        assertTrue(anime.all { it.query?.anime == true })
        assertEquals(listOf("Discover", "Movies", "TV Shows", "Anime", "Top Rated", "Hot Right Now"), d.Section.entries.map { it.label })
    }

    @Test fun seedsAreCatalogTitlesWatchedFirst() {
        fun it(u: String) = dev.asfakul.yumeiro.online.OnlineItem("T$u", u, "Yumeiro", null)
        val s = dev.asfakul.yumeiro.ui.screens.online.Discover.seeds(listOf(it("https://x/1"), it("catalog:tv:5")), listOf(it("catalog:movie:1"), it("catalog:tv:5")))
        assertEquals(listOf("catalog:tv:5", "catalog:movie:1"), s.map { it.url })
        assertEquals("IN", dev.asfakul.yumeiro.ui.screens.online.Discover.region(java.util.Locale("en", "IN")))
        assertEquals("US", dev.asfakul.yumeiro.ui.screens.online.Discover.region(java.util.Locale("en")))
    }

    // F8: only real scores, each named by its provider.
    @Test fun ratingChipsAreRealOnly() {
        val tf = dev.asfakul.yumeiro.online.meta.TitleFacts
        val facts = tf.Facts(rating = 8.7, votes = 2_100_000, ratings = listOf(tf.Rating("Simkl", 8.4, 10, 900)))
        val chips = tf.ratingChips(8.2, "TMDB", facts)
        assertEquals(listOf("TMDB", "IMDb", "Simkl"), chips.map { it.source })
        assertEquals(2_100_000L, chips[1].votes)
        assertEquals(emptyList<String>(), tf.ratingChips(0.0, "X", tf.Facts()).map { it.source })
        assertEquals(listOf("IMDb"), tf.ratingChips(null, "X", tf.Facts(rating = 7.0)).map { it.source })
        val simkl = tf.parseSimkl("""{"title":"A","ratings":{"imdb":{"rating":8.1,"votes":10},"simkl":{"rating":7.9,"votes":55},"mal":{"rating":8.9},"rt":{"rating":0}}}""")!!
        assertEquals(listOf("Simkl", "MyAnimeList"), simkl.ratings.map { it.source })
        assertEquals(8.1, simkl.rating!!, 0.0001)
    }

    // F13: grouped search.
    @Test fun searchGroupsSortAndFold() {
        val g = dev.asfakul.yumeiro.online.SearchGroups
        fun it(api: String, n: Int) = dev.asfakul.yumeiro.online.OnlineItem("T$n", "u$api$n", api, null)
        val ranked = listOf(it("B", 1), it("A", 2), it("A", 3), it("C", 4), it("A", 5), it("C", 6))
        val st = mapOf("A" to g.State.DONE, "B" to g.State.DONE, "C" to g.State.DONE, "D" to g.State.DONE, "E" to g.State.FAILED, "F" to g.State.SEARCHING)
        assertEquals(listOf("B", "A", "C"), g.plan(ranked, st, g.Sort.BEST).groups.map { it.name })
        assertEquals(listOf("A", "C", "B"), g.plan(ranked, st, g.Sort.MOST).groups.map { it.name })
        assertEquals(listOf("A", "B", "C"), g.plan(ranked, st, g.Sort.AZ).groups.map { it.name })
        val p = g.plan(ranked, st, g.Sort.BEST)
        assertEquals(listOf("T2", "T3", "T5"), p.groups[1].items.map { it.name })
        assertEquals(listOf("D"), p.empty); assertEquals(listOf("E"), p.failed); assertEquals(listOf("F"), p.searching)
    }
}
