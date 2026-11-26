package dev.asfakul.yumeiro.notify

import dev.asfakul.yumeiro.notify.Reminders.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** B5 (Beta chapter): reminders are rare, never at launch, and remember "Maybe later". */
class RemindersTest {
    private val d = Reminders.DAILY_MS
    private val t0 = 1_000_000_000_000L
    private val warm = Reminders.SESSION_WARMUP_MS
    private fun state(
        last: Map<Kind, Long> = emptyMap(), follows: Int = 0, stars: Int = 0,
        dismissed: Map<Kind, Int> = emptyMap(), support: Int = 0, shown: Boolean = false,
    ) = Reminders.State(t0, last.values.maxOrNull() ?: 0L, last, follows, stars, dismissed, support, shown)

    @Test fun nothingDuringTheFirstThreeDays() {
        assertNull(Reminders.next(state(), t0 + 10 * 60 * 1000, warm))
        assertNull(Reminders.next(state(), t0 + Reminders.GRACE_MS - 1, warm))
        assertEquals(Kind.FOLLOW, Reminders.next(state(), t0 + Reminders.GRACE_MS, warm))
    }

    @Test fun neverAtLaunch() {
        // Long-time user, everything due: still nothing in the first minutes of a visit.
        val now = t0 + 400 * d
        assertNull(Reminders.next(state(), now, 0L))
        assertNull(Reminders.next(state(), now, 20_000L))
        assertNull(Reminders.next(state(), now, warm - 1))
        assertTrue(Reminders.next(state(), now, warm) != null)
    }

    @Test fun atMostOnePerSession() {
        assertNull(Reminders.next(state(shown = true), t0 + 400 * d, 10 * warm))
    }

    @Test fun threeDaysBetweenAnyTwo() {
        val now = t0 + 100 * d
        val s = state(mapOf(Kind.FOLLOW to now - 20 * d, Kind.STAR to now - 20 * d, Kind.SUPPORT to now - 2 * d))
        assertNull(Reminders.next(s, now, warm))
        assertEquals(Kind.FOLLOW, Reminders.next(s, now + d + 1, warm))
    }

    @Test fun noLongerEveryOpenOfTheDay() {
        // The old rule showed one on the first open of every day; a day later nothing is due now.
        val now = t0 + 100 * d
        val last = mapOf(Kind.FOLLOW to now - 1 * d - 1, Kind.STAR to now - 1 * d, Kind.SUPPORT to now - 1 * d + 1)
        assertNull(Reminders.next(state(last), now, warm))
    }

    @Test fun supportEveryTwoWeeksAtMost() {
        val now = t0 + 100 * d
        val last = mapOf(Kind.FOLLOW to now - 5 * d, Kind.STAR to now - 5 * d, Kind.SUPPORT to now - 13 * d)
        assertNull(Reminders.next(state(last, follows = 3, stars = 1), now, warm))
        assertEquals(Kind.SUPPORT, Reminders.next(state(last + (Kind.SUPPORT to now - 14 * d), follows = 3, stars = 1), now, warm))
    }

    @Test fun maybeLaterIsRememberedAndWaitsLonger() {
        val s0 = state()
        assertEquals(Reminders.SUPPORT_MS, Reminders.interval(Kind.SUPPORT, s0))
        assertEquals(2 * Reminders.SUPPORT_MS, Reminders.interval(Kind.SUPPORT, state(dismissed = mapOf(Kind.SUPPORT to 1))))
        assertEquals(4 * Reminders.SUPPORT_MS, Reminders.interval(Kind.SUPPORT, state(dismissed = mapOf(Kind.SUPPORT to 2))))
        assertEquals(Reminders.QUIET_MS, Reminders.interval(Kind.SUPPORT, state(dismissed = mapOf(Kind.SUPPORT to 3))))
        // A dismissed card is not due at its normal interval any more.
        val now = t0 + 100 * d
        val last = mapOf(Kind.FOLLOW to now - 50 * d, Kind.STAR to now - 50 * d, Kind.SUPPORT to now - 15 * d)
        assertEquals(Kind.SUPPORT, Reminders.next(state(last, follows = 3, stars = 1), now, warm))
        assertNull(Reminders.next(state(last, follows = 3, stars = 1, dismissed = mapOf(Kind.SUPPORT to 1)), now, warm))
    }

    @Test fun doneActionsStopOrSlowTheirKind() {
        assertNull("starred: never again", Reminders.interval(Kind.STAR, state(stars = 1)))
        assertNull("followed three times: never again", Reminders.interval(Kind.FOLLOW, state(follows = 3)))
        assertEquals("opened the support page: two months", Reminders.AFTER_ACTION_MS, Reminders.interval(Kind.SUPPORT, state(support = 1)))
    }

    @Test fun oldestDueGoesFirst() {
        val now = t0 + 200 * d
        val s = state(mapOf(Kind.FOLLOW to now - 30 * d, Kind.STAR to now - 40 * d, Kind.SUPPORT to now - 35 * d))
        assertEquals(Kind.STAR, Reminders.next(s, now, warm))
    }

    @Test fun aVisitIsTheTimeOnScreen() {
        Reminders.resetSessionForTest()
        val v = Reminders.Visit
        v.resumed(1_000L)
        assertEquals(4_000L, v.onScreen(5_000L))
        v.paused(5_000L)
        assertEquals("the clock stops in the background", 4_000L, v.onScreen(60_000L))
        v.resumed(60_000L)
        assertEquals(6_000L, v.onScreen(62_000L))
        v.paused(62_000L)
        v.resumed(62_000L + Reminders.Visit.NEW_VISIT_AFTER_MS)
        assertEquals("back after half an hour: a new visit", 0L, v.onScreen(62_000L + Reminders.Visit.NEW_VISIT_AFTER_MS))
        Reminders.resetSessionForTest()
    }
}
