package dev.asfakul.yumeiro.notify

import dev.asfakul.yumeiro.notify.Reminders.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemindersTest {
    private val h = 60L * 60 * 1000
    private val t0 = 1_000_000_000_000L
    private fun state(last: Map<Kind, Long> = emptyMap(), follows: Int = 0, stars: Int = 0) =
        Reminders.State(t0, last.values.maxOrNull() ?: 0L, last, follows, stars)

    @Test fun nothingDuringGrace() = assertNull(Reminders.next(state(), t0 + 10 * 60 * 1000))

    @Test fun freshInstallIsStaggered() {
        assertEquals(Kind.FOLLOW, Reminders.next(state(), t0 + Reminders.GRACE_MS))
        // Follow shown: star waits for the minimum gap, then comes next.
        val s = state(mapOf(Kind.FOLLOW to t0 + Reminders.GRACE_MS))
        assertNull(Reminders.next(s, t0 + Reminders.GRACE_MS + 30 * 60 * 1000))
        assertEquals(Kind.STAR, Reminders.next(s, t0 + Reminders.GRACE_MS + Reminders.MIN_GAP_MS))
    }

    @Test fun neverTwoWithinMinGap() {
        val now = t0 + 100 * h
        val s = state(mapOf(Kind.FOLLOW to now - 10 * h, Kind.STAR to now - 10 * h, Kind.SUPPORT to now - 20 * 60 * 1000))
        assertNull(Reminders.next(s, now))
    }

    @Test fun oldestDueGoesFirst() {
        val now = t0 + 100 * h
        val s = state(mapOf(Kind.FOLLOW to now - 5 * h, Kind.STAR to now - 9 * h, Kind.SUPPORT to now - 2 * h))
        assertEquals(Kind.STAR, Reminders.next(s, now))
    }

    @Test fun followBecomesDailyAfterThreeFollows() {
        val now = t0 + 100 * h
        val last = mapOf(Kind.FOLLOW to now - 5 * h, Kind.STAR to now - 2 * h, Kind.SUPPORT to now - 2 * h)
        assertEquals(Kind.FOLLOW, Reminders.next(state(last, follows = 2), now))
        assertNull(Reminders.next(state(last, follows = 3), now))
        assertEquals(Kind.FOLLOW, Reminders.next(state(last.plus(Kind.FOLLOW to now - 25 * h), follows = 3), now))
    }

    @Test fun starBecomesDailyAfterAStar() {
        val now = t0 + 100 * h
        val last = mapOf(Kind.FOLLOW to now - 2 * h, Kind.STAR to now - 5 * h, Kind.SUPPORT to now - 2 * h)
        assertEquals(Kind.STAR, Reminders.next(state(last), now))
        assertNull(Reminders.next(state(last, stars = 1), now))
    }

    @Test fun firstOpenOfTheDayAlwaysShowsOne() {
        val now = t0 + 100 * h
        // Everything was shown 23 h ago (yesterday): not due by interval, but it's a new day.
        val last = mapOf(Kind.FOLLOW to now - 23 * h, Kind.STAR to now - 23 * h + 1, Kind.SUPPORT to now - 23 * h + 2)
        val s = state(last, follows = 3, stars = 1)
        assertEquals(Kind.FOLLOW, Reminders.next(s, now))
        // Once shown today, normal timing again.
        val after = state(last + (Kind.FOLLOW to now), follows = 3, stars = 1)
        assertNull(Reminders.next(after, now + 30 * 60 * 1000))
    }
}
