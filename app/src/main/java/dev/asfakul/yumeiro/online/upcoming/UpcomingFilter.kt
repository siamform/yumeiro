/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.upcoming

import java.text.Normalizer
import java.time.LocalDate

/**
 * OWN-16 (F-22 rest): search and filters of the Upcoming page, built from the loaded list instead
 * of fixed chips. Pure, unit-tested.
 *
 * - Genres, channel countries and days are the ones the current list really has (most common first
 *   for genres and countries, in date order for days). A filter with nothing to choose is not shown.
 * - The schedule country (which TVmaze list to load) keeps the supported ones, puts the phone's own
 *   country first and adds any other country seen in the loaded list.
 * - A choice that is no longer in the list (another category loaded) counts as "all".
 */
object UpcomingFilter {
    data class Options(val genres: List<String>, val countries: List<String>, val days: List<LocalDate>)

    data class Choice(val query: String = "", val genre: String? = null, val country: String? = null, val day: LocalDate? = null)

    fun options(list: List<Upcoming.Entry>): Options = Options(
        // Group W (B2): one name per genre ("Sci-Fi" and "Science Fiction" are one chip).
        genres = byCount(list.flatMap { e -> dev.asfakul.yumeiro.sources.hub.GenreCatalog.clean(e.genres, max = 24) }),
        countries = byCount(list.mapNotNull { it.country?.trim()?.uppercase()?.takeIf { c -> c.isNotEmpty() } }),
        days = list.map { it.day }.distinct().sorted(),
    )

    private fun byCount(values: List<String>): List<String> =
        values.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.lowercase() })
            .map { it.key }

    /** Which schedule countries to offer: the phone's country first, the supported list, then new ones from [seen]. */
    fun scheduleCountries(phoneCountry: String?, supported: List<String>, seen: List<String>, streaming: String): List<String> {
        val phone = phoneCountry?.trim()?.uppercase()?.takeIf { it.length == 2 && it.all { c -> c in 'A'..'Z' } }
        return (listOfNotNull(phone) + supported + seen.map { it.uppercase() }.filter { it.length == 2 } + streaming).distinct()
    }

    /** The choice with anything not in [o] dropped. */
    fun valid(c: Choice, o: Options): Choice = c.copy(
        genre = c.genre?.takeIf { g -> o.genres.any { it.equals(g, ignoreCase = true) } },
        country = c.country?.takeIf { it in o.countries },
        day = c.day?.takeIf { it in o.days },
    )

    fun apply(list: List<Upcoming.Entry>, choice: Choice): List<Upcoming.Entry> {
        val c = valid(choice, options(list))
        val q = fold(c.query)
        return list.filter { e ->
            (q.isEmpty() || fold(e.title).contains(q)) &&
                (c.genre == null || dev.asfakul.yumeiro.sources.hub.GenreCatalog.clean(e.genres, max = 24).any { it.equals(c.genre, ignoreCase = true) }) &&
                (c.country == null || e.country?.trim()?.uppercase() == c.country) &&
                (c.day == null || e.day == c.day)
        }
    }

    /** Filters that are on (the search is not counted; it has its own field). */
    fun activeCount(choice: Choice, o: Options): Int = valid(choice, o).let { v -> listOfNotNull(v.genre, v.country, v.day).size }

    /** Lower case, accents and punctuation removed, spaces collapsed: "Pokémon: Horizons" ~ "pokemon horizons". */
    fun fold(s: String): String {
        val base = Normalizer.normalize(s, Normalizer.Form.NFD)
        val sb = StringBuilder(base.length)
        var space = false
        for (ch in base) {
            when {
                Character.getType(ch) == Character.NON_SPACING_MARK.toInt() -> Unit
                ch.isLetterOrDigit() -> { sb.append(ch.lowercaseChar()); space = false }
                sb.isNotEmpty() && !space -> { sb.append(' '); space = true }
            }
        }
        return sb.toString().trim()
    }
}
