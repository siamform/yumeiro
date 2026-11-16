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

    /**
     * R15: endless feeds are append-only. [candidates] is the full merged feed as it is now; only
     * entries not shown before (and not in [skip]) are returned, block-shuffled among themselves.
     * Entries already on screen never move, vanish or get replaced when a page arrives late, a
     * shorter row gets its next page, or a section above grows. [seen] is updated.
     */
    fun <T> appendNew(candidates: List<T>, seen: MutableSet<String>, skip: Set<String>, seed: Long, shuffle: Boolean = true, key: (T) -> String): List<T> {
        val fresh = ArrayList<T>()
        val local = HashSet<String>()
        for (c in candidates) {
            val k = key(c)
            if (k in seen || k in skip || !local.add(k)) continue
            fresh += c
        }
        seen.addAll(local)
        return if (shuffle) blockShuffle(fresh, seed, key = key) else fresh
    }

    /**
     * R16 G4: the rows an extension's section shows after a home fetch. [fresh] is null when the
     * fetch threw. A fetch that throws or comes back with no rows (offline: every row failed, so
     * the repository answers an empty list instead of an error) keeps [prev]. Before, an empty answer
     * replaced the section with nothing. A refresh with fewer rows (one row timed out) keeps the
     * rows it didn't bring back in their places. Returns null when there's nothing to show,
     * which the screen reports as an error with Retry.
     */
    fun homeAfterFetch(prev: List<OnlineRow>?, fresh: List<OnlineRow>?): List<OnlineRow>? {
        val old = prev.orEmpty().filter { it.items.isNotEmpty() }
        val new = fresh.orEmpty().filter { it.items.isNotEmpty() }
        if (new.isEmpty()) return old.takeIf { it.isNotEmpty() }
        if (old.isEmpty()) return new
        // R16 G4: rows stay where they were. A fresh row replaces its old copy in place and rows
        // only the refresh brought go last; before, every missing old row jumped below the fresh
        // ones, so a partial refresh moved the shelves on screen.
        val byKey = new.associateBy { it.title + "|" + it.pageData }
        val placed = HashSet<String>()
        val kept = old.map { o -> val k = o.title + "|" + o.pageData; placed += k; byKey[k] ?: o }
        return kept + new.filter { placed.add(it.title + "|" + it.pageData) }
    }

    /**
     * R16 G4: every extension that has rows has its section on screen, so "More to explore" may
     * start. It used to compare against every ready extension: one extension with no rows (no
     * section) kept the endless feed from ever appearing.
     */
    /**
     * R16 G4: true when [fresh] brings at least one title that [prev] did not have. Offline, the
     * drama catalog answers a refresh from its own shelf cache (stale-while-revalidate), so a
     * "successful" refresh can bring back exactly what is on screen. Counting that as an answer
     * reset "More to explore" (cards on screen moved) and hid the "Couldn't refresh" message.
     */
    fun broughtNew(prev: List<OnlineRow>?, fresh: List<OnlineRow>?): Boolean {
        val have = prev.orEmpty().flatMapTo(HashSet()) { r -> r.items.map { it.key } }
        return fresh.orEmpty().any { r -> r.items.any { it.key !in have } }
    }

    /** What pull to refresh does once every extension has answered. */
    enum class AfterRefresh { START_OVER, NOTHING_NEW, OFFLINE }

    /**
     * X9-8: offline the feed never starts over. A section still on its first load can fill from a
     * saved copy while offline (api28 43777524987: Yumeiro Drama, "1 answered" in 1.5 s); that
     * counted as an answer, so "More to explore" was reset and the offline message was hidden.
     * Offline, new rows are only added (syncExplore) and the offline message is shown.
     */
    fun afterRefresh(answered: Int, online: Boolean): AfterRefresh = when {
        !online -> AfterRefresh.OFFLINE
        answered > 0 -> AfterRefresh.START_OVER
        else -> AfterRefresh.NOTHING_NEW
    }

    fun sectionsDone(groupCount: Int, ready: List<Pair<String, List<OnlineRow>>>): Boolean {
        val withRows = ready.count { (_, rows) -> rows.any { it.items.isNotEmpty() } }
        return withRows > 0 && groupCount >= withRows
    }

    /** Keys of [shown] titles whose extension is no longer in [liveApis] (turned off or hidden). */
    fun goneFrom(shown: List<OnlineItem>, liveApis: Set<String>): Set<String> =
        shown.filter { it.api !in liveApis }.mapTo(HashSet()) { it.key }

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
