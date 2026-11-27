/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.groupw

import dev.asfakul.yumeiro.sources.hub.DiscoverFilter
import dev.asfakul.yumeiro.sources.hub.GenreCatalog
import dev.asfakul.yumeiro.sources.hub.GenreCatalog.Kind
import dev.asfakul.yumeiro.sources.hub.HubDiscover
import dev.asfakul.yumeiro.ui.screens.online.Discover
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Group W (B1 filters, B2 genres, B3 services): the pure parts. Device behaviour: GroupWDeviceTest. */
class GroupWDiscoverTest {
    @After fun reset() = GenreCatalog.clearLearned()

    // ---- B2: one genre list for the whole app ----------------------------------------------------------------

    @Test fun normalizeFoldsSpellings() {
        assertEquals("sci fi", GenreCatalog.normalize("Sci-Fi"))
        assertEquals("sci fi", GenreCatalog.normalize("  SCI   fi "))
        assertEquals("action and adventure", GenreCatalog.normalize("Action & Adventure"))
        assertEquals("comedie", GenreCatalog.normalize("Comédie"))
    }

    @Test fun aliasesMatchOneGenre() {
        assertEquals("scifi", GenreCatalog.match("Sci-Fi")?.key)
        assertEquals("scifi", GenreCatalog.match("Science Fiction")?.key)
        assertEquals("scifi", GenreCatalog.match("scifi")?.key)
        assertEquals("isekai", GenreCatalog.match("Isekai")?.key)
        assertEquals("horror", GenreCatalog.match("horror")?.key)
        assertNull(GenreCatalog.match("Banana Bread"))
    }

    @Test fun catalogKeysAndNamesAreUnique() {
        assertEquals(GenreCatalog.ALL.size, GenreCatalog.ALL.map { it.key }.distinct().size)
        assertEquals(GenreCatalog.ALL.size, GenreCatalog.ALL.map { GenreCatalog.normalize(it.name) }.distinct().size)
        // B2 "more genres": the catalog is far bigger than TMDB's 19 film genres, with an anime group.
        assertTrue(GenreCatalog.ALL.size >= 60)
        assertTrue(GenreCatalog.ALL.count { it.group == GenreCatalog.Group.ANIME } >= 10)
        // Every genre can be asked for somewhere.
        assertTrue(GenreCatalog.ALL.all { it.supports(Kind.ALL) })
    }

    @Test fun cleanDedupesByMeaningAndDropsJunk() {
        val got = GenreCatalog.clean(listOf("Sci-Fi", "Science Fiction", "action", "ACTION", "123", "https://x.y/z", "#Drama", "slice of life", "Hentai", "Adult", "Young Adult", "a"))
        assertEquals(listOf("Science Fiction", "Action", "Drama"), got.take(3))
        assertTrue("Young Adult" in got)
        assertFalse(got.any { it.equals("Hentai", true) || it == "Adult" || it == "123" || "://" in it })
        assertEquals(got.size, got.map { GenreCatalog.normalize(it) }.distinct().size)
        // Lists that come as one text are split too.
        assertEquals(listOf("Action", "Comedy"), GenreCatalog.clean(listOf("Action, Comedy")))
        assertEquals(2, GenreCatalog.clean(List(30) { "Genre $it" }, max = 2).size)
    }

    @Test fun forKindHidesWhatCannotBeAsked() {
        assertFalse(GenreCatalog.forKind(Kind.MOVIE).any { it.key == "reality" })
        assertTrue(GenreCatalog.forKind(Kind.TV).any { it.key == "reality" })
        assertFalse(GenreCatalog.forKind(Kind.MOVIE).any { it.group == GenreCatalog.Group.ANIME })
        assertTrue(GenreCatalog.forKind(Kind.ANIME).any { it.key == "isekai" })
        assertEquals("action", GenreCatalog.ofTmdb(28, "movie")?.key)
        assertEquals("scifi", GenreCatalog.ofTmdb(10765, "tv")?.key)
    }

    @Test fun unknownGenresSeenOftenBecomeGenres() {
        repeat(GenreCatalog.LEARN_AT - 1) { GenreCatalog.learn(listOf("Wuxia Cultivation", "Action")) }
        assertTrue(GenreCatalog.learned().isEmpty())
        GenreCatalog.learn(listOf("wuxia cultivation"))
        assertEquals(listOf("Wuxia Cultivation"), GenreCatalog.learned())
        // Known genres and junk are never learned.
        repeat(5) { GenreCatalog.learn(listOf("Action", "999", "Hentai")) }
        assertEquals(listOf("Wuxia Cultivation"), GenreCatalog.learned())
        // Saved and restored.
        val saved = GenreCatalog.exportLearned()
        GenreCatalog.clearLearned()
        assertTrue(GenreCatalog.learned().isEmpty())
        GenreCatalog.importLearned(saved + "\nbroken line\nAction\t9")
        assertEquals(listOf("Wuxia Cultivation"), GenreCatalog.learned())
    }

    // ---- B1: the Find something filter -----------------------------------------------------------------------

    @Test fun filterRoundTripsThroughTheRoute() {
        val f = DiscoverFilter(
            kind = Kind.ANIME, sort = HubDiscover.Sort.RATING, genres = listOf("isekai", "comedy"), services = listOf(8, 283),
            region = "IN", origin = "JP", lang = "ja", yearFrom = 2015, yearTo = 2026, minRating = 7, title = "Isekai & more", only = "tv",
        )
        assertEquals(f, DiscoverFilter.decode(f.encode()))
        assertEquals(DiscoverFilter(), DiscoverFilter.decode(DiscoverFilter().encode()))
        assertEquals(DiscoverFilter(shelf = "hot_today"), DiscoverFilter.decode("sh=hot_today"))
        assertEquals(8, f.activeCount)
        assertEquals(DiscoverFilter(kind = Kind.ANIME, sort = HubDiscover.Sort.RATING, region = "IN", only = "tv"), f.cleared())
    }

    @Test fun brokenRouteValuesAreDropped() {
        val f = DiscoverFilter.decode("k=planet;s=weird;g=nope,action,action;p=0,-3,8,x;r=india;o=jp;l=JAP;y=1800-3000;m=11;f=book;sh=a b;garbage")
        assertEquals(DiscoverFilter(genres = listOf("action"), services = listOf(8), origin = "JP"), f)
        assertEquals(DiscoverFilter(), DiscoverFilter.decode(null))
        assertEquals(DiscoverFilter(), DiscoverFilter.decode("   "))
        assertEquals(3, DiscoverFilter.decode("g=action,comedy,drama,crime").genres.size)
    }

    @Test fun allAsksFilmsAndSeries() {
        val qs = DiscoverFilter(genres = listOf("action"), services = listOf(8), region = "IN").queries()
        assertEquals(listOf("movie", "tv"), qs.map { it.kind })
        assertEquals(listOf(28), qs[0].genres)
        assertEquals(listOf(10759), qs[1].genres)
        assertTrue(qs.all { it.providers == listOf(8) && it.region == "IN" })
        // No services: the region is not sent (it only scopes services).
        assertTrue(DiscoverFilter(region = "IN").queries().all { it.region == null })
    }

    @Test fun genresWithoutAnIdUseTheirKeyword() {
        // TMDB has no series "Horror" genre: series ask for the horror keyword instead.
        val qs = DiscoverFilter(genres = listOf("horror")).queries()
        assertEquals(listOf(27), qs.first { it.kind == "movie" }.genres)
        val tv = qs.first { it.kind == "tv" }
        assertEquals(emptyList<Int>(), tv.genres)
        assertEquals(listOf(315058), tv.keywords)
        // A kind that cannot have the genre at all is left out.
        assertEquals(listOf("tv"), DiscoverFilter(genres = listOf("reality")).queries().map { it.kind })
    }

    @Test fun animeAsksSeriesFirstWithoutTheAnimationGenre() {
        val qs = DiscoverFilter(kind = Kind.ANIME, genres = listOf("animation", "isekai")).queries()
        assertEquals(listOf("tv", "movie"), qs.map { it.kind })
        assertTrue(qs.all { it.anime && 16 !in it.genres && it.keywords == listOf(237451) })
        assertEquals(listOf("movie"), DiscoverFilter(kind = Kind.ANIME, only = "movie").queries().map { it.kind })
        assertTrue(DiscoverFilter(shelf = "hot_today").queries().isEmpty())
    }

    @Test fun pathCarriesTheNewFilters() {
        val p = HubDiscover.path(HubDiscover.Query("tv", HubDiscover.Sort.RATING, keywords = listOf(237451, 237451, 207826), yearFrom = 2010, yearTo = 2026, minRating = 7), 1)
        assertEquals("/discover?type=tv&sort=rating&keyword=237451,207826&year_from=2010&year_to=2026&min_rating=7&page=1", p)
        val bad = HubDiscover.path(HubDiscover.Query("movie", keywords = listOf(0, -1), yearFrom = 1200, minRating = 10), 1)
        assertEquals("/discover?type=movie&sort=popular&page=1", bad)
    }

    @Test fun rowSeeAllOpensTheSameAsk() {
        val q = HubDiscover.Query("tv", HubDiscover.Sort.TOP, genres = listOf(10765), anime = true, origin = "JP")
        val f = DiscoverFilter.of(q, "Top anime")
        assertEquals(Kind.ANIME, f.kind)
        assertEquals(listOf("fantasy"), f.genres) // 10765 is "Sci-Fi & Fantasy": the first canonical genre
        assertEquals("tv", f.only)
        assertEquals("Top anime", f.title)
        assertEquals(listOf(10765), f.queries().single().genres)
    }

    @Test fun weaveAlternatesAndDropsDuplicates() {
        fun item(kind: String, id: Long) = HubDiscover.Item("T$id", kind, id, null, null, null, null, null, emptyList(), false)
        val a = HubDiscover.Page(listOf(item("movie", 1), item("movie", 2), item("movie", 3)), false)
        val b = HubDiscover.Page(listOf(item("tv", 1), item("movie", 2)), true)
        val w = DiscoverFilter.weave(listOf(a, b))
        assertEquals(listOf("catalog:movie:1", "catalog:tv:1", "catalog:movie:2", "catalog:movie:3"), w.items.map { it.url })
        assertTrue(w.hasNext)
        assertEquals(0, DiscoverFilter.weave(emptyList()).items.size)
    }

    // ---- B3: streaming services near you -------------------------------------------------------------------

    @Test fun regionsWithoutServicesBorrowANeighbour() {
        assertEquals("IN", HubDiscover.nearbyRegion("BD"))
        assertEquals("IN", HubDiscover.nearbyRegion("NP"))
        assertNull(HubDiscover.nearbyRegion("US"))
        assertEquals("US", HubDiscover.nearbyRegion("ZZ"))
    }

    // ---- B2 / B3: Discover and Home show both sections ---------------------------------------------------------

    @Test fun discoverHasServicesAndGenresEverywhereTheyFit() {
        val disc = Discover.shelves(Discover.Section.DISCOVER)
        assertTrue(disc.any { it is Discover.Services && it.kind == "all" })
        assertTrue(disc.any { it is Discover.Genres && it.kind == "all" })
        assertTrue(Discover.shelves(Discover.Section.ANIME).any { it is Discover.Genres && it.kind == "anime" })
    }
}
