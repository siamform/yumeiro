/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import androidx.compose.runtime.Immutable
import java.util.Locale

// Precompiled once (never during composition or per call): see docs/STABILITY-R15.md and RegexWarmup.
private val RX_LIVEFIND_MARKS = Regex("\\p{M}+")
private val RX_LIVEFIND_SPLIT = Regex("[^\\p{L}\\p{N}+]+")
private val RX_LIVEFIND_PAREN = Regex("\\([^)]*\\)")
private val RX_LIVEFIND_ST = Regex("\\bst\\.\\s*")

/**
 * LIVE-1 (owner list R21, item 1): finding a channel and a country on Live TV. Pure, so it runs off the main
 * thread and is unit tested.
 *
 * Search: every typed word must appear in the channel name (case and accent blind, any order). Quality words
 * ("HD", "FHD", "4K", "SD", "1080p"...) in the name are ignored so "star sports" finds "Star Sports 1 HD".
 * Names that start with the first word come first, then the rest in feed order. The category and the
 * country name also match, so "bangla" finds channels grouped under "Bangladesh".
 *
 * Countries: one list for the Country sheet, built from the data, never a fixed list:
 * - when the channels carry an ISO country (tvg-country / iptv-org tvg-id) and that is not already the
 *   category, from those codes;
 * - when the playlist's categories are the countries (Yumeiro Live: group-title = "Bangladesh"), from the
 *   categories that name a country, so the long country chip row is replaced by the sheet.
 */
object LiveFind {
    private val QUALITY = setOf("hd", "fhd", "uhd", "sd", "4k", "8k", "hq", "1080p", "720p", "480p", "2160p", "1080", "720", "hevc", "h265")

    fun fold(s: String): String =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(RX_LIVEFIND_MARKS, "").lowercase(Locale.ROOT)

    /** The words of [s] that count for search (quality words dropped). */
    fun words(s: String): List<String> = fold(s).split(RX_LIVEFIND_SPLIT).filter { it.isNotBlank() && it !in QUALITY }

    /** [entries] whose name (or category / country name) holds every word of [query]. Blank query = all. */
    fun search(entries: List<LiveChannelFeed.Entry>, query: String): List<LiveChannelFeed.Entry> {
        val q = words(query)
        if (q.isEmpty()) return entries
        val first = q.first()
        val starts = ArrayList<LiveChannelFeed.Entry>()
        val rest = ArrayList<LiveChannelFeed.Entry>()
        for (e in entries) {
            val name = fold(e.channel.displayTitle)
            val hay = buildString {
                append(name); append(' '); append(fold(e.category))
                e.channel.country?.let { append(' '); append(fold(LiveCountry.name(it, Locale.ENGLISH))); append(' '); append(it.lowercase(Locale.ROOT)) }
            }
            if (q.all { it in hay }) { if (name.startsWith(first)) starts += e else rest += e }
        }
        return starts + rest
    }

    /** One row of the Country sheet. [key] is what the filter compares; [byCategory] = it filters the category. */
    @Immutable
    data class Country(val key: String, val label: String, val code: String?, val count: Int, val byCategory: Boolean)

    /** The Country sheet's rows for [feed], most channels first (ties by name). Empty = no country filter. */
    fun countries(feed: LiveChannelFeed.Feed, locale: Locale = Locale.getDefault()): List<Country> {
        if (feed.showCountryChips) return feed.countries.map { Country(it.name, LiveCountry.name(it.name, locale), it.name, it.count, byCategory = false) }
        if (!feed.countriesAreCategories) return emptyList()
        // The few categories that are not a country ("International", "Other") stay reachable: after the
        // countries, with no flag, because their chip row is gone.
        val rows = feed.categories.map { c ->
            val code = isoForName(c.name)
            Country(c.name, code?.let { LiveCountry.name(it, locale).takeIf { n -> n != it } } ?: c.name, code, c.count, byCategory = true)
        }
        return rows.sortedWith(compareBy({ it.code == null }, { -it.count }, { it.label }))
    }

    /** True when the category chips are really countries (they are then left out: the Country sheet has them). */
    fun categoriesAreCountries(feed: LiveChannelFeed.Feed): Boolean = feed.countriesAreCategories

    /** Countries whose name or code holds every word of [query], accent blind. */
    fun filterCountries(list: List<Country>, query: String): List<Country> {
        val q = words(query)
        if (q.isEmpty()) return list
        return list.filter { c -> val h = fold(c.label) + " " + fold(c.key) + " " + (c.code?.lowercase(Locale.ROOT) ?: ""); q.all { it in h } }
            .sortedBy { if (fold(it.label).startsWith(q.first())) 0 else 1 }
    }

    /** "BD" → 🇧🇩 (regional indicator letters); null for anything that is not two letters A–Z. */
    fun flag(code: String?): String? {
        val c = code?.uppercase(Locale.ROOT) ?: return null
        if (c.length != 2 || !c.all { it in 'A'..'Z' }) return null
        val sb = StringBuilder()
        c.forEach { ch -> sb.appendCodePoint(0x1F1E6 + (ch - 'A')) }
        return sb.toString()
    }

    private val byName: Map<String, String> by lazy {
        val m = HashMap<String, String>()
        for (iso in Locale.getISOCountries()) {
            val l = Locale("", iso)
            m[canon(l.getDisplayCountry(Locale.ENGLISH))] = iso
            m[canon(l.getDisplayCountry(Locale.getDefault()))] = iso
            m[iso.lowercase(Locale.ROOT)] = iso
        }
        // Names playlists use that Java spells differently.
        mapOf(
            "usa" to "US", "united states of america" to "US", "uk" to "GB", "england" to "GB", "great britain" to "GB",
            "south korea" to "KR", "korea" to "KR", "north korea" to "KP", "russia" to "RU", "vietnam" to "VN", "iran" to "IR",
            "syria" to "SY", "turkey" to "TR", "turkiye" to "TR", "czech republic" to "CZ", "czechia" to "CZ", "uae" to "AE",
            "ivory coast" to "CI", "laos" to "LA", "macedonia" to "MK", "moldova" to "MD", "palestine" to "PS", "taiwan" to "TW",
            "hong kong" to "HK", "macau" to "MO", "bolivia" to "BO", "venezuela" to "VE", "tanzania" to "TZ", "congo" to "CG",
            "dr congo" to "CD", "kosovo" to "XK", "democratic republic of the congo" to "CD", "republic of the congo" to "CG",
            "bonaire" to "BQ", "burma" to "MM", "vatican" to "VA", "vatican city" to "VA", "cape verde" to "CV",
        ).forEach { (k, v) -> m[k] = v }
        m
    }

    /** The ISO code for a country name as playlists write it ("Bangladesh", "USA"); null when it is not a country. */
    fun isoForName(name: String): String? = byName[canon(name)]

    /** "St. Kitts & Nevis", "Myanmar (Burma)", "Congo - Kinshasa" → "saint kitts and nevis", "myanmar", "congo kinshasa". */
    private fun canon(name: String): String =
        fold(name).replace(RX_LIVEFIND_PAREN, " ").replace("&", " and ").replace(RX_LIVEFIND_ST, "saint ")
            .split(RX_LIVEFIND_SPLIT).filter { it.isNotBlank() }.joinToString(" ")
}
