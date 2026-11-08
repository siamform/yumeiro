/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import dev.asfakul.yumeiro.stream.StreamMediaFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenWakeTest {
    @Test fun playing_keepsScreenOn() = assertTrue(ScreenWake.shouldKeepOn(isPlaying = true, isBuffering = false, playWhenReady = true, isEnded = false))
    @Test fun rebuffering_keepsScreenOn() = assertTrue(ScreenWake.shouldKeepOn(false, true, true, false))
    @Test fun paused_letsScreenSleep() = assertFalse(ScreenWake.shouldKeepOn(false, false, false, false))
    @Test fun pausedWhileBuffering_letsScreenSleep() = assertFalse(ScreenWake.shouldKeepOn(false, true, false, false))
    @Test fun ended_letsScreenSleep() = assertFalse(ScreenWake.shouldKeepOn(true, false, true, true))

    @Test fun bufferBudget_scalesWithHeapAndIsCapped() {
        assertEquals(16 * 1024 * 1024, StreamMediaFactory.bufferBudgetBytes(64))
        assertEquals(32 * 1024 * 1024, StreamMediaFactory.bufferBudgetBytes(192))
        assertEquals(64 * 1024 * 1024, StreamMediaFactory.bufferBudgetBytes(1024))
    }
}
