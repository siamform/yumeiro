/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

/**
 * DEDUP-1 (owner list 14): the same film from three extensions showed three times on Yumix's "All" feed
 * ("Dune: Part Two (2024) [Hindi + English] 1080p", "Dune Part Two", "Dune: Part Two 2024 WEB-DL"). Cards were
 * told apart by api|url only, which is always different across extensions.
 *
 * A title's identity here is its name without release noise (quality, rip, audio, brackets, punctuation, case)
 * plus its year when the name has one. Two cards from different extensions are the same title when the names
 * match and the years don't contradict each other (a missing year matches any year), and their kinds don't
 * contradict (a film and a series of the same name stay apart). Within one extension nothing is removed: its
 * own shelves (Popular, Latest) may share titles on purpose. The extension's own page is never filtered.
 */
object TitleDedupe {
    private val YEAR = Regex("(?<![0-9])(19[0-9]{2}|20[0-9]{2})(?![0-9])")
    private val BRACKETS = Regex("[\\[(（【{][^\\])）】}]*[\\])）】}]")
    private val NOISE = Regex(
        "\\b(" +
            "2160p|1080p|720p|480p|360p|4k|uhd|fhd|hdr10|hdr|10bit|x26[45]|h\\.?26[45]|hevc|avc|" +
            "web[ ._-]?dl|web[ ._-]?rip|webrip|hdrip|brrip|bluray|blu[ ._-]?ray|dvdrip|hdtc|hdcam|camrip|hdts|pre[ ._-]?dvd|" +
            "dual[ ._-]?audio|multi[ ._-]?audio|hindi[ ._-]?dubbed|dubbed|dub|subbed|esubs?|msubs?|" +
            "full[ ._-]?movie|watch[ ._-]?online|free[ ._-]?download" +
            ")\\b",
        RegexOption.IGNORE_CASE,
    )
    private val NOT_WORD = Regex("[^\\p{L}\\p{N}]+")

    data class Key(val base: String, val year: Int?, val series: Boolean?)

    fun key(item: OnlineItem): Key = item.titleKey

    internal fun compute(name: String, type: String?): Key {
        val year = YEAR.findAll(name).lastOrNull()?.value?.toIntOrNull()
        var s = BRACKETS.replace(name, " ")
        s = YEAR.replace(s, " ")
        s = NOISE.replace(s, " ")
        s = NOT_WORD.replace(s.lowercase(), " ").trim()
        // A name that was all noise ("[Hindi] 1080p") keeps its plain lower-case form, so it never matches by accident.
        if (s.isEmpty()) s = "\u0000" + name.lowercase()
        return Key(s, year, seriesOf(type))
    }

    private fun seriesOf(type: String?): Boolean? = when (type?.lowercase()) {
        null, "" -> null
        "movie", "animemovie", "torrent" -> false
        "tvseries", "anime", "ova", "asiandrama", "cartoon", "documentary" -> true
        else -> null
    }

    /** Remembers titles already shown, and which extension showed them. */
    class Seen {
        private val byBase = HashMap<String, MutableList<Pair<String, Key>>>()

        /** True when another extension already showed this title. */
        fun dupe(item: OnlineItem, k: Key = key(item)): Boolean =
            byBase[k.base]?.any { (api, o) -> api != item.api && compatible(o, k) } == true

        fun add(item: OnlineItem, k: Key = key(item)) {
            val l = byBase.getOrPut(k.base) { ArrayList(2) }
            if (l.none { it.first == item.api && it.second == k }) l += item.api to k
        }

        /** Adds [item] unless another extension showed it; returns whether it was new. */
        fun offer(item: OnlineItem): Boolean {
            val k = key(item)
            if (dupe(item, k)) return false
            add(item, k)
            return true
        }
    }

    private fun compatible(a: Key, b: Key): Boolean =
        (a.year == null || b.year == null || a.year == b.year) &&
            (a.series == null || b.series == null || a.series == b.series)

    /**
     * The feed's channels with every title another (earlier) extension already holds taken out. Channel order is
     * the user's extension order, so the first extension keeps the card. Rows left empty are dropped, and a channel
     * left with no rows is dropped too (its section would be blank).
     */
    fun acrossExtensions(channels: List<Pair<String, List<OnlineRow>>>): List<Pair<String, List<OnlineRow>>> {
        val seen = Seen()
        val out = ArrayList<Pair<String, List<OnlineRow>>>(channels.size)
        for ((name, rows) in channels) {
            val mine = ArrayList<Pair<OnlineItem, Key>>()
            val kept = rows.mapNotNull { r ->
                val items = r.items.filter { i ->
                    val k = key(i)
                    if (seen.dupe(i, k)) false else { mine += i to k; true }
                }
                when {
                    items.isEmpty() -> null
                    items.size == r.items.size -> r
                    else -> r.copy(items = items)
                }
            }
            // Added after the whole channel: its own shelves never knock out each other's cards.
            mine.forEach { (i, k) -> seen.add(i, k) }
            if (kept.isNotEmpty()) out += name to kept
        }
        return out
    }

    /**
     * "More to explore" candidates without titles another extension already has on screen: [above] is every card of
     * the de-duplicated sections, [shown] the explore cards already placed. Candidates of the same extension stay.
     */
    fun <T> exploreCandidates(candidates: List<T>, above: List<OnlineItem>, shown: List<OnlineItem>, item: (T) -> OnlineItem): List<T> {
        val seen = Seen()
        above.forEach { seen.add(it) }
        shown.forEach { seen.add(it) }
        return candidates.filter { seen.offer(item(it)) }
    }
}
