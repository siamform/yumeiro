/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.anticipation

import dev.asfakul.yumeiro.player.HeatGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** B23 ADR-B23-3: how far ahead the engine prepares, and when it stands back. */
class AnticipationPolicyTest {
    private val ok = AnticipationPolicy.Signals(enabled = true, heat = HeatGuard.Level.COOL, memoryLow = false, online = true, metered = false, lowEnd = false, playbackSteady = true)

    @Test fun threeAheadWhenEverythingIsFine() = assertEquals(3, AnticipationPolicy.depth(ok))

    @Test fun oneAheadOnMobileDataWarmOrLowEnd() {
        assertEquals(1, AnticipationPolicy.depth(ok.copy(metered = true)))
        assertEquals(1, AnticipationPolicy.depth(ok.copy(heat = HeatGuard.Level.WARM)))
        assertEquals(1, AnticipationPolicy.depth(ok.copy(lowEnd = true)))
    }

    @Test fun suspendedUnderPressureOrWhilePlaybackIsNotSteady() {
        assertEquals(AnticipationPolicy.Why.OFF, AnticipationPolicy.why(ok.copy(enabled = false)))
        assertEquals(AnticipationPolicy.Why.OFFLINE, AnticipationPolicy.why(ok.copy(online = false)))
        assertEquals(AnticipationPolicy.Why.HOT, AnticipationPolicy.why(ok.copy(heat = HeatGuard.Level.HOT)))
        assertEquals(AnticipationPolicy.Why.MEMORY, AnticipationPolicy.why(ok.copy(memoryLow = true)))
        assertEquals(AnticipationPolicy.Why.PLAYBACK, AnticipationPolicy.why(ok.copy(playbackSteady = false)))
        listOf(ok.copy(enabled = false), ok.copy(online = false), ok.copy(heat = HeatGuard.Level.HOT), ok.copy(memoryLow = true), ok.copy(playbackSteady = false))
            .forEach { assertEquals("$it", 0, AnticipationPolicy.depth(it)) }
    }

    @Test fun theWindowRollsForwardAndStopsAtTheLastEpisode() {
        assertEquals(listOf(1, 2, 3), AnticipationPolicy.window(0, 12, 3))
        assertEquals(listOf(5, 6, 7), AnticipationPolicy.window(4, 12, 3))
        assertEquals(listOf(10, 11), AnticipationPolicy.window(9, 12, 3))
        assertEquals(emptyList<Int>(), AnticipationPolicy.window(11, 12, 3))
        assertEquals(listOf(3), AnticipationPolicy.window(2, 12, 1))
        assertEquals(emptyList<Int>(), AnticipationPolicy.window(2, 12, 0))
        assertEquals("a film has no episodes", emptyList<Int>(), AnticipationPolicy.window(-1, 0, 3))
    }

    @Test fun preparedLinksExpire() {
        assertTrue(AnticipationPolicy.fresh(1_000, 1_000 + AnticipationPolicy.FRESH_MS - 1))
        assertFalse(AnticipationPolicy.fresh(1_000, 1_000 + AnticipationPolicy.FRESH_MS))
        assertFalse("clock moved back", AnticipationPolicy.fresh(10_000, 5_000))
    }

    @Test fun retriesAreSpacedAndLimited() {
        assertEquals(0L, AnticipationPolicy.retryAt(0, 0))
        assertEquals(130_000L, AnticipationPolicy.retryAt(1, 100_000))
        assertNull("two tries per episode per session", AnticipationPolicy.retryAt(AnticipationPolicy.MAX_TRIES, 0))
    }

    @Test fun steadyMeansAFrameNoBufferingAndSomeTimePlayed() {
        assertFalse(AnticipationPolicy.steady(firstFrame = false, buffering = false, positionMs = 60_000))
        assertFalse(AnticipationPolicy.steady(firstFrame = true, buffering = true, positionMs = 60_000))
        assertFalse(AnticipationPolicy.steady(firstFrame = true, buffering = false, positionMs = 5_000, durationMs = 24 * 60_000))
        assertTrue(AnticipationPolicy.steady(firstFrame = true, buffering = false, positionMs = 15_000, durationMs = 24 * 60_000))
        assertTrue("a 12 s clip counts halfway", AnticipationPolicy.steady(firstFrame = true, buffering = false, positionMs = 6_000, durationMs = 12_000))
    }
}
