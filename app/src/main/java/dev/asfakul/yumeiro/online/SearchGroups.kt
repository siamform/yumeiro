/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

/**
 * F13 (Beta chapter, Group O): Yumix search grouped by extension.
 *
 * Studied (behaviour only): SkyStream shows one section per provider (its name and where it comes from), sorted so
 * the useful ones come first and the ones that cannot help sit at the end. Yumeiro's own rule:
 * - one row per extension that found something, its titles in match order (the ranker's order);
 * - rows sorted by [Sort]: best match first (the row holding the best-ranked title, ties: more results), most
 *   results, or A–Z;
 * - extensions that found nothing, failed, or are still searching are folded into one summary line each, never
 *   an empty row.
 * Pure; unit-tested.
 */
object SearchGroups {
    enum class Sort(val label: String) { BEST("Best match"), MOST("Most results"), AZ("A–Z") }
    enum class State { SEARCHING, DONE, FAILED }

    data class Group(val name: String, val items: List<OnlineItem>, val best: Int)
    data class Plan(val groups: List<Group>, val empty: List<String>, val failed: List<String>, val searching: List<String>)

    /**
     * @param ranked every result, best match first (de-duplicated, as [SearchRanker.rank] gives them)
     * @param states each extension's state
     */
    fun plan(ranked: List<OnlineItem>, states: Map<String, State>, sort: Sort): Plan {
        val byApi = LinkedHashMap<String, MutableList<OnlineItem>>()
        val best = HashMap<String, Int>()
        ranked.forEachIndexed { i, it ->
            byApi.getOrPut(it.api) { ArrayList() } += it
            best.putIfAbsent(it.api, i)
        }
        val groups = byApi.map { (n, l) -> Group(n, l, best[n] ?: Int.MAX_VALUE) }
        val sorted = when (sort) {
            Sort.BEST -> groups.sortedWith(compareBy<Group> { it.best }.thenByDescending { it.items.size }.thenBy { it.name.lowercase() })
            Sort.MOST -> groups.sortedWith(compareByDescending<Group> { it.items.size }.thenBy { it.best }.thenBy { it.name.lowercase() })
            Sort.AZ -> groups.sortedBy { it.name.lowercase() }
        }
        fun names(s: State) = states.filter { (n, st) -> st == s && n !in byApi }.keys.sortedBy { it.lowercase() }
        return Plan(sorted, names(State.DONE), names(State.FAILED), names(State.SEARCHING))
    }
}
