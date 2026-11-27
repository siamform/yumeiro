/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stats

/**
 * F-17: pure rules behind the profile statistics (unit-tested). One [Day] per calendar day with activity:
 * listening time, watching time and plays per [Kind]. At most [MAX_DAYS] days are kept (about a year).
 */
object StatsMath {
    enum class Kind(val label: String) { MUSIC("Songs"), DEVICE("Videos on this phone"), ONLINE("Yumix"), YOUTUBE("YouTube") }

    class Day(val epochDay: Long, val musicMs: Long, val videoMs: Long, val plays: IntArray) {
        val totalMs: Long get() = musicMs + videoMs
        val active: Boolean get() = totalMs > 0 || plays.any { it > 0 }
    }

    data class Streaks(val current: Int, val longest: Int)
    class Totals(val musicMs: Long, val videoMs: Long, val plays: IntArray, val activeDays: Int) {
        val allPlays: Int get() = plays.sum()
        val totalMs: Long get() = musicMs + videoMs
    }
    data class Milestone(val id: String, val title: String)

    const val MAX_DAYS = 371
    /** One reported span never counts more than this (a phone left playing overnight with the clock jumping). */
    const val MAX_DELTA_MS = 6 * 3_600_000L

    /** 0 = nothing, then up to 15 min, up to 1 h, up to 3 h, more. */
    fun level(ms: Long): Int = when {
        ms <= 0 -> 0
        ms <= 15 * 60_000L -> 1
        ms <= 60 * 60_000L -> 2
        ms <= 3 * 3_600_000L -> 3
        else -> 4
    }

    /** A day with plays but no measured time (a song skipped at once) still shows as the lightest step. */
    fun levelOf(d: Day?): Int = when {
        d == null -> 0
        d.totalMs > 0 -> level(d.totalMs)
        d.plays.any { it > 0 } -> 1
        else -> 0
    }

    fun streaks(days: List<Day>, today: Long): Streaks {
        val active = days.filter { it.active }.map { it.epochDay }.toSortedSet()
        var longest = 0; var run = 0; var prev = Long.MIN_VALUE
        for (d in active) { run = if (d == prev + 1) run + 1 else 1; longest = maxOf(longest, run); prev = d }
        var end = when { today in active -> today; (today - 1) in active -> today - 1; else -> return Streaks(0, longest) }
        var current = 0
        while (end in active) { current++; end-- }
        return Streaks(current, longest)
    }

    private fun upsert(days: List<Day>, epochDay: Long, change: (Day) -> Day): List<Day> {
        val i = days.indexOfFirst { it.epochDay == epochDay }
        val next = if (i >= 0) days.toMutableList().also { it[i] = change(it[i]) }
        else (days + change(Day(epochDay, 0, 0, IntArray(Kind.entries.size)))).sortedBy { it.epochDay }
        return if (next.size > MAX_DAYS) next.takeLast(MAX_DAYS) else next
    }

    fun addTime(days: List<Day>, epochDay: Long, music: Boolean, deltaMs: Long): List<Day> {
        val d = deltaMs.coerceIn(0L, MAX_DELTA_MS)
        return upsert(days, epochDay) { if (music) Day(it.epochDay, it.musicMs + d, it.videoMs, it.plays) else Day(it.epochDay, it.musicMs, it.videoMs + d, it.plays) }
    }

    fun addPlay(days: List<Day>, epochDay: Long, kind: Kind): List<Day> =
        upsert(days, epochDay) { Day(it.epochDay, it.musicMs, it.videoMs, it.plays.copyOf().also { p -> p[kind.ordinal]++ }) }

    fun totals(days: List<Day>, from: Long, to: Long): Totals {
        val inRange = days.filter { it.epochDay in from..to }
        val plays = IntArray(Kind.entries.size)
        inRange.forEach { d -> d.plays.forEachIndexed { i, n -> if (i < plays.size) plays[i] += n } }
        return Totals(inRange.sumOf { it.musicMs }, inRange.sumOf { it.videoMs }, plays, inRange.count { it.active })
    }

    /** First (Monday) day of a grid of [weeks] columns whose last column holds [today]. */
    fun gridStart(today: Long, weeks: Int): Long {
        val dow = java.time.LocalDate.ofEpochDay(today).dayOfWeek.value - 1 // Monday = 0
        return today - dow - (weeks - 1).coerceAtLeast(0) * 7L
    }

    fun milestones(days: List<Day>, today: Long): List<Milestone> {
        val t = totals(days, Long.MIN_VALUE, Long.MAX_VALUE)
        val s = streaks(days, today)
        val h = t.totalMs / 3_600_000L
        return buildList {
            if (t.activeDays >= 1) add(Milestone("first_day", "First day with Yumeiro"))
            if (s.longest >= 7) add(Milestone("streak_7", "7 days in a row"))
            if (s.longest >= 30) add(Milestone("streak_30", "30 days in a row"))
            if (h >= 10) add(Milestone("hours_10", "10 hours played"))
            if (h >= 100) add(Milestone("hours_100", "100 hours played"))
            if (t.allPlays >= 100) add(Milestone("plays_100", "100 plays"))
            if (t.allPlays >= 1000) add(Milestone("plays_1000", "1,000 plays"))
        }
    }

    /** One line per day: `epochDay,musicMs,videoMs,p0;p1;p2;p3`. */
    fun encode(days: List<Day>): String = days.joinToString("\n") { "${it.epochDay},${it.musicMs},${it.videoMs},${it.plays.joinToString(";")}" }

    fun decode(raw: String?): List<Day> = raw.orEmpty().lineSequence().mapNotNull { line ->
        val f = line.split(',')
        if (f.size != 4) return@mapNotNull null
        val day = f[0].toLongOrNull() ?: return@mapNotNull null
        val m = f[1].toLongOrNull() ?: return@mapNotNull null
        val v = f[2].toLongOrNull() ?: return@mapNotNull null
        val p = IntArray(Kind.entries.size)
        f[3].split(';').forEachIndexed { i, s -> if (i < p.size) p[i] = s.toIntOrNull() ?: 0 }
        Day(day, m.coerceAtLeast(0), v.coerceAtLeast(0), p)
    }.sortedBy { it.epochDay }.toList().takeLast(MAX_DAYS)

    fun duration(ms: Long): String {
        val min = ms / 60_000L
        return when { min < 1 -> if (ms > 0) "<1 min" else "0 min"; min < 60 -> "$min min"; else -> "${min / 60} h ${min % 60} min" }
    }
}
