/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.upcoming

import java.time.LocalDate

/**
 * UP-BANNER-1 (owner list R21, item 16A): the titles of Yumix's rotating "Coming up" card: what comes out today,
 * tomorrow and in the next [DAYS] days, from the Upcoming calendars already loaded (no extra API, no fixed list).
 * Pure, unit-tested.
 */
object UpcomingSoon {
    const val DAYS = 3
    const val MAX = 10

    data class Pick(val entry: Upcoming.Entry, val kind: Upcoming.Kind)

    /**
     * Entries with a poster whose day is in [today, today + [days]), most popular first (rank 1 = top; 0 =
     * unranked, last), one title once (a show airing two days keeps its first day), kinds taken in turn so the
     * card is not all one kind, at most [max]; then shown soonest first.
     */
    fun picks(lists: Map<Upcoming.Kind, List<Upcoming.Entry>>, today: LocalDate, days: Int = DAYS, max: Int = MAX): List<Pick> {
        val end = today.plusDays(days.toLong())
        val byKind = Upcoming.Kind.entries.mapNotNull { k ->
            val l = lists[k].orEmpty()
                .filter { !it.posterUrl.isNullOrBlank() && !it.day.isBefore(today) && it.day.isBefore(end) }
                .sortedWith(compareBy<Upcoming.Entry> { if (it.rank > 0) 0 else 1 }.thenBy { it.rank }.thenBy { it.day })
            if (l.isEmpty()) null else l.map { Pick(it, k) }
        }
        val out = ArrayList<Pick>()
        val seen = HashSet<String>()
        var i = 0
        while (out.size < max && byKind.any { i < it.size }) {
            for (l in byKind) {
                val p = l.getOrNull(i) ?: continue
                if (out.size < max && seen.add(UpcomingFilter.fold(p.entry.title))) out += p
            }
            i++
        }
        return out.sortedWith(compareBy<Pick> { it.entry.day }.thenBy { it.entry.time ?: java.time.LocalTime.MAX })
    }

    /** "Today", "Tomorrow", "In 2 days". */
    fun whenLabel(day: LocalDate, today: LocalDate): String = when (val d = java.time.temporal.ChronoUnit.DAYS.between(today, day)) {
        0L -> "Today"
        1L -> "Tomorrow"
        else -> "In $d days"
    }
}
