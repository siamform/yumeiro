/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import java.text.Normalizer

/**
 * Merges search results from many Hues into one list ordered by how well each title matches the
 * query, not by which extension answered first. Ordering is stable: results that score the same
 * keep the source order (the user's extension order), then the order the extension returned.
 * Results are de-duplicated by `api|url` so the grid never sees two items with the same key.
 */
object SearchRanker {
    data class Ranked(val item: OnlineItem, val score: Int, val alsoOn: Int)

    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

    private fun tokens(s: String) = normalize(s).split(' ').filter { it.isNotEmpty() }

    /** 0..100: exact > prefix > all words > some words > nothing. */
    fun score(query: String, title: String): Int {
        val q = normalize(query); val t = normalize(title)
        if (q.isEmpty() || t.isEmpty()) return 0
        if (t == q) return 100
        if (t.startsWith("$q ") || t.startsWith(q)) return 85
        val qt = tokens(query); val tt = tokens(title).toSet()
        if (qt.isEmpty()) return 0
        val hits = qt.count { w -> w in tt || tt.any { it.startsWith(w) && w.length >= 3 } }
        if (t.contains(q)) return 70
        if (hits == qt.size) return 60
        return (hits * 40) / qt.size
    }

    /** Same show on several Hues: name (normalized) + type. */
    fun groupKey(item: OnlineItem): String = normalize(item.name) + "|" + (item.type ?: "")

    /**
     * @param perSource results per extension name
     * @param sourceOrder extension names in the user's priority order
     */
    fun rank(query: String, perSource: Map<String, List<OnlineItem>>, sourceOrder: List<String>): List<Ranked> {
        val order = sourceOrder.withIndex().associate { it.value to it.index }
        val seen = HashSet<String>()
        val flat = ArrayList<Triple<OnlineItem, Int, Int>>() // item, sourceRank, index
        for ((src, items) in perSource.entries.sortedBy { order[it.key] ?: Int.MAX_VALUE }) {
            items.forEachIndexed { i, it -> if (seen.add(it.key)) flat += Triple(it, order[src] ?: Int.MAX_VALUE, i) }
        }
        val groups = flat.groupingBy { groupKey(it.first) }.eachCount()
        return flat
            .map { (item, s, i) -> Triple(Ranked(item, score(query, item.name), (groups[groupKey(item)] ?: 1) - 1), s, i) }
            .sortedWith(compareByDescending<Triple<Ranked, Int, Int>> { it.first.score }.thenBy { it.second }.thenBy { it.third })
            .map { it.first }
    }
}
