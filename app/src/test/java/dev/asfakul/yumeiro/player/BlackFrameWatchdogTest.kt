/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import dev.asfakul.yumeiro.player.BlackFrameWatchdog.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlackFrameWatchdogTest {
    @Test fun framesFlowing_neverActs() {
        val w = BlackFrameWatchdog()
        val actions = (0 until 30).map { w.observe(it * 1_000L, it * 24L, hasVideo = true, playing = true) }
        assertTrue(actions.all { it == Action.NONE })
    }

    @Test fun frozenFrames_nudgeThenFail() {
        val w = BlackFrameWatchdog(nudgeAfterMs = 4_000, failAfterMs = 7_000)
        val actions = (0..14).map { w.observe(10_000L + it * 1_000L, 500L, hasVideo = true, playing = true) }
        assertEquals(Action.NUDGE, actions[5]) // first frozen sample at 11s, nudge once 4s later
        assertEquals(1, actions.count { it == Action.NUDGE })
        assertEquals(1, actions.count { it == Action.FAIL })
    }

    @Test fun framesComingBack_resets() {
        val w = BlackFrameWatchdog(nudgeAfterMs = 2_000, failAfterMs = 2_000)
        (0..3).forEach { w.observe(it * 1_000L, 10, true, true) }
        assertEquals(Action.NONE, w.observe(4_000, 50, true, true))
        assertEquals(Action.NONE, w.observe(5_000, 50, true, true))
    }

    @Test fun pausedOrAudioOnly_ignored() {
        val w = BlackFrameWatchdog(nudgeAfterMs = 1_000, failAfterMs = 1_000)
        assertTrue((0..10).map { w.observe(it * 1_000L, 0, hasVideo = false, playing = true) }.all { it == Action.NONE })
        assertTrue((0..10).map { w.observe(5_000, 0, hasVideo = true, playing = false) }.all { it == Action.NONE })
    }

    @Test fun seekRestartsTheClock() {
        val w = BlackFrameWatchdog(nudgeAfterMs = 3_000, failAfterMs = 3_000)
        w.observe(0, 5, true, true); w.observe(1_000, 5, true, true); w.observe(2_000, 5, true, true)
        assertEquals(Action.NONE, w.observe(60_000, 5, true, true)) // user seeked far ahead
        assertEquals(Action.NONE, w.observe(61_000, 5, true, true))
    }
}
