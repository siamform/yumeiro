/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** BUG-4: the user's own quality picks and seeks never count as the network failing. */
class RebufferWindowTest {
    @Test fun threeNetworkRebuffersInAMinuteStepDown() {
        val w = RebufferWindow()
        assertFalse(w.rebuffer(10_000))
        assertFalse(w.rebuffer(20_000))
        assertTrue(w.rebuffer(30_000))
    }

    @Test fun rebuffersSpreadOverMoreThanAMinuteDoNot() {
        val w = RebufferWindow()
        assertFalse(w.rebuffer(10_000))
        assertFalse(w.rebuffer(50_000))
        assertFalse(w.rebuffer(75_000)) // the first fell out of the window
    }

    /** Device run 43857809658: 184p, 288p, 480p picked 5–10 s apart; the third reverted to Auto. */
    @Test fun threePicksInARowAreNotANetworkProblem() {
        val w = RebufferWindow()
        for (t in listOf(10_000L, 17_000L, 27_000L)) {
            w.userChange(t)
            assertFalse("buffering right after a pick at $t", w.rebuffer(t + 300))
        }
    }

    @Test fun seeksAreNotCountedEither() {
        val w = RebufferWindow()
        repeat(5) { i -> val t = 10_000L + i * 3_000; w.userChange(t); assertFalse(w.rebuffer(t + 100)) }
    }

    @Test fun aRebufferLongAfterThePickCounts() {
        val w = RebufferWindow()
        w.userChange(0)
        assertFalse(w.rebuffer(RebufferWindow.USER_GRACE_MS + 1_000))
        assertFalse(w.rebuffer(RebufferWindow.USER_GRACE_MS + 2_000))
        assertTrue(w.rebuffer(RebufferWindow.USER_GRACE_MS + 3_000))
    }

    @Test fun atMostOneStepDownPerMinute() {
        val w = RebufferWindow()
        w.rebuffer(1_000); w.rebuffer(2_000); assertTrue(w.rebuffer(3_000)); w.steppedDown(3_000)
        w.rebuffer(10_000); w.rebuffer(11_000)
        assertFalse(w.rebuffer(12_000))
        w.rebuffer(64_000); w.rebuffer(65_000)
        assertTrue(w.rebuffer(66_000))
    }
}
