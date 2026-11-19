package dev.asfakul.yumeiro.stats

// F-17 (docs/proposals/FEATURE-F-17-profile-statistics.md): on-device activity map, streaks, totals and milestones.
// Fail-before: the stats package does not exist.

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class F17StatsTest {
    private fun day(d: Long, musicMin: Long = 0, videoMin: Long = 0, plays: Int = 0) =
        StatsMath.Day(d, musicMs = musicMin * 60_000, videoMs = videoMin * 60_000, plays = intArrayOf(plays, 0, 0, 0))

    @Test fun activityLevelsHaveFiveStepsAndZeroMeansNothing() {
        assertEquals(0, StatsMath.level(0))
        assertEquals(1, StatsMath.level(5 * 60_000))
        assertEquals(2, StatsMath.level(30 * 60_000))
        assertEquals(3, StatsMath.level(90 * 60_000))
        assertEquals(4, StatsMath.level(4 * 3_600_000L))
    }

    @Test fun streaksCountConsecutiveActiveDays() {
        val days = listOf(day(100, 10), day(101, 5), day(102, plays = 1), day(105, 1), day(106, 2))
        val s = StatsMath.streaks(days, today = 106)
        assertEquals(2, s.current)
        assertEquals(3, s.longest)
        // Yesterday still counts as the current streak until today ends.
        assertEquals(2, StatsMath.streaks(days, today = 107).current)
        assertEquals(0, StatsMath.streaks(days, today = 108).current)
    }

    @Test fun codecRoundTripsAndDropsBrokenLines() {
        val days = listOf(StatsMath.Day(20000, 1000, 2000, intArrayOf(1, 2, 3, 4)), StatsMath.Day(20001, 5, 0, intArrayOf(0, 0, 0, 1)))
        val back = StatsMath.decode(StatsMath.encode(days) + "\ngarbage\n1,2")
        assertEquals(2, back.size)
        assertEquals(2000, back[0].videoMs)
        assertEquals(listOf(1, 2, 3, 4), back[0].plays.toList())
    }

    @Test fun addingKeepsAtMostOneYearAndCapsOneDelta() {
        var days = emptyList<StatsMath.Day>()
        for (d in 0L until 500L) days = StatsMath.addTime(days, d, music = true, deltaMs = 60_000)
        assertEquals(StatsMath.MAX_DAYS, days.size)
        assertEquals(500L - StatsMath.MAX_DAYS, days.first().epochDay)
        val one = StatsMath.addTime(emptyList(), 1, music = false, deltaMs = 99 * 3_600_000L)
        assertEquals(StatsMath.MAX_DELTA_MS, one.single().videoMs)
        assertEquals(0, StatsMath.addTime(emptyList(), 1, music = false, deltaMs = -5).single().videoMs)
    }

    @Test fun playsAreCountedPerKind() {
        var days = StatsMath.addPlay(emptyList(), 7, StatsMath.Kind.YOUTUBE)
        days = StatsMath.addPlay(days, 7, StatsMath.Kind.YOUTUBE)
        days = StatsMath.addPlay(days, 7, StatsMath.Kind.MUSIC)
        val t = StatsMath.totals(days, from = 0, to = 10)
        assertEquals(2, t.plays[StatsMath.Kind.YOUTUBE.ordinal])
        assertEquals(1, t.plays[StatsMath.Kind.MUSIC.ordinal])
        assertEquals(3, t.allPlays)
    }

    @Test fun gridEndsOnTodayAndStartsOnAMonday() {
        // 2026-11-19 is a Thursday: epochDay 20776.
        val today = java.time.LocalDate.of(2026, 11, 19).toEpochDay()
        val g = StatsMath.gridStart(today, weeks = 4)
        assertEquals(java.time.DayOfWeek.MONDAY, java.time.LocalDate.ofEpochDay(g).dayOfWeek)
        assertTrue(today - g in 21..27)
    }

    @Test fun milestonesFollowTotals() {
        val days = (0L until 7L).map { day(it, musicMin = 120) }
        val m = StatsMath.milestones(days, today = 6).map { it.id }
        assertTrue("first_day" in m)
        assertTrue("streak_7" in m)
        assertTrue("hours_10" in m)
        assertTrue("hours_100" !in m)
    }
}
