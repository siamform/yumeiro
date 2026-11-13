/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekMathTest {
    private val unset = Long.MIN_VALUE + 1 // C.TIME_UNSET

    @Test fun knownIgnoresPlaceholderAndUnset() {
        assertNull(SeekMath.known(unset, SeekMath.UNKNOWN))
        assertNull(SeekMath.known(0L, 0L))
        assertEquals(90_000L, SeekMath.known(unset, 90_000L))
        assertEquals(120_000L, SeekMath.known(120_000L, 90_000L))
    }
    @Test fun forwardWithUnknownDurationMovesForward() {
        // Regression: used to be coerceAtMost(1L) → 0:00.
        assertEquals(40_000L, SeekMath.forward(30_000L, 10_000L, SeekMath.known(unset, SeekMath.UNKNOWN)))
    }
    @Test fun forwardCapsAtKnownDuration() {
        assertEquals(60_000L, SeekMath.forward(55_000L, 10_000L, 60_000L))
        assertEquals(20_000L, SeekMath.forward(10_000L, 10_000L, 60_000L))
    }
    @Test fun rewindStopsAtZero() {
        assertEquals(0L, SeekMath.rewind(4_000L, 10_000L))
        assertEquals(5_000L, SeekMath.rewind(15_000L, 10_000L))
    }
    @Test fun clampWithoutDurationOnlyKeepsLowerBound() {
        assertEquals(70_000L, SeekMath.clamp(70_000L, null))
        assertEquals(0L, SeekMath.clamp(-5L, null))
        assertEquals(60_000L, SeekMath.clamp(70_000L, 60_000L))
    }
    @Test fun fractionIsEmptyWhileDurationUnknown() {
        // Regression: pos / 1L coerced to 1f drew a full bar.
        assertEquals(0f, SeekMath.fraction(30_000L, SeekMath.UNKNOWN), 0f)
        assertEquals(0f, SeekMath.fraction(30_000L, null), 0f)
        assertEquals(0.5f, SeekMath.fraction(30_000L, 60_000L), 0.0001f)
        assertEquals(1f, SeekMath.fraction(90_000L, 60_000L), 0f)
    }
    @Test fun sliderSeekNeedsDuration() {
        assertNull(SeekMath.positionFor(0.5f, SeekMath.UNKNOWN))
        assertEquals(30_000L, SeekMath.positionFor(0.5f, 60_000L))
        assertEquals(60_000L, SeekMath.positionFor(1.4f, 60_000L))
    }
    @Test fun pendingSeekHoldsTargetUntilPlayerCatchesUp() {
        assertEquals(50_000L, SeekMath.displayed(10_000L, 50_000L, pendingSinceMs = 0L, nowMs = 300L))
        assertFalse(SeekMath.settled(10_000L, 50_000L, 0L, 300L))
        assertEquals(50_400L, SeekMath.displayed(50_400L, 50_000L, 0L, 600L))
        assertTrue(SeekMath.settled(50_400L, 50_000L, 0L, 600L))
        // Timeout: never stuck on a target the player refused.
        assertEquals(10_000L, SeekMath.displayed(10_000L, 50_000L, 0L, 2_500L))
        assertEquals(12_000L, SeekMath.displayed(12_000L, null, 0L, 0L))
    }
}
