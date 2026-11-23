/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import androidx.compose.runtime.Immutable

/**
 * NBUG-15 (ADR-028): the Live TV feed as one list. Every enabled playlist's channels are merged (the order
 * given: pinned playlists first, then the user's order), the same stream URL shows once (first copy wins),
 * and channels are grouped by category across all playlists. Pure, so it runs off the main thread and is
 * unit tested.
 *
 * Category: the channel's `group-title`, first part of an `A;B` list (Kodi PVR IPTV Simple and iptv-org use
 * semicolons), trimmed, compared case-blind (the first spelling seen is shown); empty → [OTHER]. Categories
 * with more channels come first, ties in the order first seen, [OTHER] always last.
 */
object LiveChannelFeed {
    /** Channels shown first, and added each time the list nears its end (owner spec: 50). */
    const val PAGE = 50
    const val OTHER = "Other"

    @Immutable
    data class Entry(val playlistId: String, val channel: StreamRequest, val category: String)

    @Immutable
    data class Category(val name: String, val count: Int)

    @Immutable
    data class Feed(
        val entries: List<Entry>,
        val categories: List<Category>,
        val countries: List<Category> = emptyList(),
        /** UX-LIVE-1: the country chips would repeat the category chips (see [countriesRepeatCategories]). */
        val countriesAreCategories: Boolean = false,
    ) {
        val size get() = entries.size
        /** Country chips are worth showing: 2+ countries that are not already the categories. */
        val showCountryChips get() = countries.size > 1 && !countriesAreCategories
        companion object { val EMPTY = Feed(emptyList(), emptyList()) }
    }

    fun category(group: String?): String =
        group?.substringBefore(';')?.trim()?.takeIf { it.isNotEmpty() } ?: OTHER

    /** [order]: playlist ids in feed order; [lists]: each playlist's channels (missing = not loaded yet). */
    fun build(order: List<String>, lists: Map<String, List<StreamRequest>>): Feed {
        val seen = HashSet<String>()
        val display = LinkedHashMap<String, String>() // lower-case key → first spelling
        val counts = HashMap<String, Int>()
        val raw = ArrayList<Entry>()
        for (id in order) {
            val chans = lists[id] ?: continue
            for (ch in chans) {
                if (ch.url.isBlank() || !seen.add(ch.url)) continue
                val c = category(ch.group)
                val key = c.lowercase(java.util.Locale.ROOT)
                val name = display.getOrPut(key) { if (key == OTHER.lowercase(java.util.Locale.ROOT)) OTHER else c }
                counts[key] = (counts[key] ?: 0) + 1
                raw += Entry(id, ch, name)
            }
        }
        val otherKey = OTHER.lowercase(java.util.Locale.ROOT)
        val firstSeen = display.keys.withIndex().associate { it.value to it.index }
        val keys = display.keys.sortedWith(compareBy<String>({ it == otherKey }, { -(counts[it] ?: 0) }, { firstSeen[it] ?: 0 }))
        val rank = keys.withIndex().associate { display.getValue(it.value) to it.index }
        val entries = raw.withIndex().sortedWith(compareBy<IndexedValue<Entry>>({ rank[it.value.category] ?: 0 }, { it.index })).map { it.value }
        // Country filter (2.1.0): ISO codes with their channel counts, most channels first.
        val byCountry = raw.mapNotNull { it.channel.country }.groupingBy { it }.eachCount()
        val countries = byCountry.entries.sortedWith(compareBy({ -it.value }, { it.key })).map { Category(it.key, it.value) }
        val feed = Feed(entries, keys.map { Category(display.getValue(it), counts[it] ?: 0) }, countries)
        return if (countries.size > 1 && countriesRepeatCategories(feed)) feed.copy(countriesAreCategories = true) else feed
    }

    /**
     * UX-LIVE-1 (probe 45143962374, Yumeiro Live 7555 channels): that list's group-title is the country, so the
     * category chips ("United States · 1386", "India · 710") and the country chips ("United States · 1386",
     * "India · 708") were the same filter twice. True when at least 80 % of the channels that carry a country sit in a
     * category named after it (English or the phone's language); the country chips are then left out.
     */
    fun countriesRepeatCategories(feed: Feed, locale: java.util.Locale = java.util.Locale.getDefault()): Boolean {
        var withCountry = 0
        var same = 0
        for (e in feed.entries) {
            val code = e.channel.country ?: continue
            withCountry++
            if (e.category.equals(LiveCountry.name(code, java.util.Locale.ENGLISH), ignoreCase = true) ||
                e.category.equals(LiveCountry.name(code, locale), ignoreCase = true) ||
                e.category.equals(code, ignoreCase = true)
            ) same++
        }
        return withCountry > 0 && same * 5 >= withCountry * 4
    }

    /** The feed's entries in [category] and [country] (null = all), in feed order. */
    fun filter(feed: Feed, category: String?, country: String? = null): List<Entry> =
        if (category == null && country == null) feed.entries
        else feed.entries.filter { e ->
            (category == null || e.category.equals(category, ignoreCase = true)) &&
                (country == null || e.channel.country.equals(country, ignoreCase = true))
        }

    /** How many to show after the list neared its end: one more [PAGE], never past [total]. */
    fun nextLimit(limit: Int, total: Int): Int = if (limit >= total) limit else (limit + PAGE).coerceAtMost(total)

    /** A category heading or one grid row, as the LazyColumn shows them. */
    sealed interface Row {
        val key: String
        data class Header(val name: String, val count: Int) : Row { override val key get() = "live_cat_h_$name" }
        data class Tiles(val category: String, val first: Int, val entries: List<Entry>) : Row { override val key get() = "live_row_${category}_$first" }
    }

    /** The first [limit] of [shown], cut into category headings and rows of [columns] tiles. */
    fun rows(shown: List<Entry>, limit: Int, columns: Int, counts: Map<String, Int>, headers: Boolean = true): List<Row> {
        val out = ArrayList<Row>()
        val cols = columns.coerceAtLeast(1)
        var i = 0
        val visible = shown.take(limit.coerceAtLeast(0))
        while (i < visible.size) {
            val cat = visible[i].category
            var j = i
            while (j < visible.size && visible[j].category == cat) j++
            if (headers) out += Row.Header(cat, counts[cat] ?: (j - i))
            var k = i
            while (k < j) { val end = minOf(k + cols, j); out += Row.Tiles(cat, k, visible.subList(k, end).toList()); k = end }
            i = j
        }
        return out
    }
}
