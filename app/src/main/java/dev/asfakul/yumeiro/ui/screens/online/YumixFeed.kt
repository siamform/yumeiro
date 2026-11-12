/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.online.OnlineRow

/**
 * Round 10 Yumix "All" feed: pure helpers for the new order —
 * Resume + Spotlight + new-episode banner → Continue → Fresh from your sources → category chips →
 * Latest episodes → one section per extension → endless shuffled mix of everything.
 */
internal object YumixFeed {
    /** Rows that extensions use for fresh episodes ("Latest Episodes", "Recently Updated", "New Releases"…). */
    private val LATEST = Regex(
        "(latest|recent(ly)?|new(est)?\\s*(ep|episode|release|upload|added|on)|just\\s*(added|released)|updated|airing|ongoing|today|this\\s*week|最新|新着)",
        RegexOption.IGNORE_CASE,
    )

    fun isLatestRow(title: String): Boolean = LATEST.containsMatchIn(title)

    /** Latest episodes across every extension, round-robin so one source can't take the whole row. */
    fun latest(channels: List<Pair<String, List<OnlineRow>>>, max: Int = 24, skip: Set<String> = emptySet()): List<OnlineItem> {
        val lists = channels.map { (_, rows) -> rows.filter { isLatestRow(it.title) }.flatMap { it.items } }.filter { it.isNotEmpty() }
        return roundRobin(lists, max, skip)
    }

    enum class Category(val label: String, val types: Set<String>, val words: Regex) {
        ANIME("Anime", setOf("anime", "animemovie", "ova"), Regex("anime|donghua|アニメ", RegexOption.IGNORE_CASE)),
        MOVIES("Movies", setOf("movie", "animemovie"), Regex("\\bmovies?\\b|\\bfilms?\\b|cinema", RegexOption.IGNORE_CASE)),
        SERIES("Series", setOf("tvseries"), Regex("\\b(tv\\s*)?(series|shows?)\\b", RegexOption.IGNORE_CASE)),
        DRAMA("Drama", setOf("asiandrama"), Regex("drama", RegexOption.IGNORE_CASE)),
        ROMANCE("Romance", emptySet(), Regex("romance|romantic|love", RegexOption.IGNORE_CASE)),
        ACTION("Action", emptySet(), Regex("action|adventure|martial", RegexOption.IGNORE_CASE)),
        COMEDY("Comedy", emptySet(), Regex("comedy|funny|sitcom", RegexOption.IGNORE_CASE)),
        HORROR("Horror", emptySet(), Regex("horror|thriller|scary", RegexOption.IGNORE_CASE)),
        SCIFI("Sci-fi & Fantasy", emptySet(), Regex("sci-?fi|science fiction|fantasy|isekai", RegexOption.IGNORE_CASE)),
        KIDS("Kids", setOf("cartoon"), Regex("kids|cartoon|family|children", RegexOption.IGNORE_CASE)),
        DOCS("Documentary", setOf("documentary"), Regex("documentar", RegexOption.IGNORE_CASE)),
        SPORTS("Sports", emptySet(), Regex("sport|football|soccer|cricket|nba|nfl|ufc|wwe|f1\\b|racing|\\bmatch(es)?\\b", RegexOption.IGNORE_CASE)),
        LIVE("Live", setOf("live"), Regex("\\blive\\b|\\bchannels?\\b", RegexOption.IGNORE_CASE)),
    }

    /**
     * Titles per category: by the extension's own type, or by the row they came in ("Romance",
     * "Top Football"). Categories with fewer than [min] titles are left out so no chip is a dead end.
     */
    fun categories(channels: List<Pair<String, List<OnlineRow>>>, min: Int = 4, maxPer: Int = 30): List<Pair<Category, List<OnlineItem>>> {
        val out = LinkedHashMap<Category, LinkedHashMap<String, OnlineItem>>()
        channels.forEach { (_, rows) ->
            rows.forEach { row ->
                val byRow = Category.entries.filter { it.words.containsMatchIn(row.title) }
                row.items.forEach { item ->
                    val t = item.type?.lowercase()?.replace(" ", "")
                    val cats = (byRow + Category.entries.filter { t != null && t in it.types }).distinct()
                    cats.forEach { c -> out.getOrPut(c) { LinkedHashMap() }.putIfAbsent(item.key, item) }
                }
            }
        }
        return Category.entries.mapNotNull { c -> out[c]?.values?.toList()?.takeIf { it.size >= min }?.let { c to it.take(maxPer) } }
    }

    /**
     * Banner slides after the Resume card: Spotlight picks with a fresh episode every second slide.
     * Returns the slides and the keys that came from "latest" rows (they get a NEW EPISODE label).
     */
    fun bannerSlides(spotlight: List<OnlineItem>, latest: List<OnlineItem>, max: Int = 10, latestCount: Int = 3): Pair<List<OnlineItem>, Set<String>> {
        val fresh = latest.filter { !it.posterUrl.isNullOrBlank() }.take(latestCount)
        val out = LinkedHashMap<String, OnlineItem>()
        val newKeys = HashSet<String>()
        val s = spotlight.iterator(); val l = fresh.iterator()
        var turn = 0
        while (out.size < max && (s.hasNext() || l.hasNext())) {
            val fromLatest = (turn % 2 == 1 && l.hasNext()) || !s.hasNext()
            val next = if (fromLatest) l.next() else s.next()
            if (out.putIfAbsent(next.key, next) == null && fromLatest) newKeys += next.key
            turn++
        }
        return out.values.toList() to newKeys
    }

    /**
     * Shuffles in fixed blocks with a per-session seed: the endless mix feels random, but titles
     * already on screen never jump around when the next page arrives.
     */
    fun <T> blockShuffle(items: List<T>, seed: Long, block: Int = 30, key: (T) -> String): List<T> =
        items.chunked(block).flatMap { chunk -> chunk.sortedBy { mix(key(it).hashCode().toLong() xor seed) } }

    private fun mix(x: Long): Long {
        var z = x + -0x61c8864680b583ebL
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    private fun roundRobin(lists: List<List<OnlineItem>>, max: Int, skip: Set<String>): List<OnlineItem> {
        val out = LinkedHashMap<String, OnlineItem>()
        var i = 0
        while (out.size < max && lists.any { i < it.size }) {
            lists.forEach { l -> l.getOrNull(i)?.let { if (out.size < max && it.key !in skip) out.putIfAbsent(it.key, it) } }
            i++
        }
        return out.values.toList()
    }
}
