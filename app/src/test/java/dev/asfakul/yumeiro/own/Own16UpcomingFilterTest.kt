/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.own

import dev.asfakul.yumeiro.online.upcoming.Upcoming
import dev.asfakul.yumeiro.online.upcoming.UpcomingFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** OWN-16: Upcoming search and filters come from the list itself (no fixed chips). */
class Own16UpcomingFilterTest {
    private val d1 = LocalDate.of(2026, 11, 22)
    private val d2 = d1.plusDays(1)
    private fun e(t: String, day: LocalDate, c: String? = null, vararg g: String) =
        Upcoming.Entry(t, day, 0, null, 1, 1, null, t.hashCode().toLong(), country = c, genres = g.toList())
    private val list = listOf(
        e("Pokémon: Horizons", d1, "JP", "Anime", "Adventure"),
        e("Frieren", d1, "JP", "Anime", "Fantasy"),
        e("The Bear", d2, "US", "Comedy", "Drama"),
        e("Slow Horses", d2, "GB", "Drama"),
    )

    @Test fun optionsAreWhatTheListHas() {
        val o = UpcomingFilter.options(list)
        assertEquals(listOf("Anime", "Drama", "Adventure", "Comedy", "Fantasy"), o.genres) // most common first
        assertEquals(listOf("JP", "GB", "US"), o.countries)
        assertEquals(listOf(d1, d2), o.days)
        assertEquals(UpcomingFilter.Options(emptyList(), emptyList(), emptyList()), UpcomingFilter.options(emptyList()))
    }

    @Test fun searchIgnoresCaseAccentsAndPunctuation() {
        assertEquals(listOf("Pokémon: Horizons"), UpcomingFilter.apply(list, UpcomingFilter.Choice(query = "pokemon horiz")).map { it.title })
        assertEquals(listOf("The Bear"), UpcomingFilter.apply(list, UpcomingFilter.Choice(query = "  BEAR ")).map { it.title })
        assertTrue(UpcomingFilter.apply(list, UpcomingFilter.Choice(query = "nothing like this")).isEmpty())
    }

    @Test fun filtersCombineAndStaleChoicesMeanAll() {
        assertEquals(listOf("The Bear", "Slow Horses"), UpcomingFilter.apply(list, UpcomingFilter.Choice(genre = "drama")).map { it.title })
        assertEquals(listOf("Slow Horses"), UpcomingFilter.apply(list, UpcomingFilter.Choice(genre = "Drama", country = "GB", day = d2)).map { it.title })
        // A genre from another category's list is not in this one: everything shows, and it is not counted.
        assertEquals(4, UpcomingFilter.apply(list, UpcomingFilter.Choice(genre = "Western")).size)
        assertEquals(0, UpcomingFilter.activeCount(UpcomingFilter.Choice(genre = "Western"), UpcomingFilter.options(list)))
        assertEquals(2, UpcomingFilter.activeCount(UpcomingFilter.Choice(genre = "Anime", day = d1, query = "x"), UpcomingFilter.options(list)))
    }

    @Test fun scheduleCountriesPutThePhoneFirstAndAddSeenOnes() {
        val c = UpcomingFilter.scheduleCountries("bd", listOf("US", "JP"), listOf("KR", "JP"), "WEB")
        assertEquals(listOf("BD", "US", "JP", "KR", "WEB"), c)
        assertEquals(listOf("US", "WEB"), UpcomingFilter.scheduleCountries("", listOf("US"), emptyList(), "WEB"))
        assertEquals(listOf("US", "WEB"), UpcomingFilter.scheduleCountries("419", listOf("US"), emptyList(), "WEB"))
    }

    @Test fun pageHasSearchAndAFilterIconInsteadOfFixedChipRows() {
        val ui = File("src/main/java/dev/asfakul/yumeiro/ui/screens/online/UpcomingScreen.kt").readText()
        assertTrue(ui.contains("\"upcoming_search\"") && ui.contains("\"upcoming_filter_button\"") && ui.contains("FilterSheet("))
        assertTrue("genres must come from the list", ui.contains("options.genres.forEach"))
        assertTrue("the old fixed 8-genre row is gone", !ui.contains("TvmazeSchedule.genresOf(list"))
    }
}
