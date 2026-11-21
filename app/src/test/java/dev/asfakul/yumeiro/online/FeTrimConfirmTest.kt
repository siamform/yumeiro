/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FE-T1: SpeedDeviceTest 44892710506 on the 2 GB phone logged trim:running_critical at start with sysFree=823M,
 * heap 9/192M, low=false; FeedThrottle took it as pressure and the feed ran on one lane (half shown 56.8 s).
 */
class FeTrimConfirmTest {
    private val mb = 1024L * 1024

    @Test fun aStartupTrimWithFreeMemoryDoesNotThrottle() {
        assertFalse(FeedThrottle.confirmed(15, 9 * mb, 192 * mb, systemLow = false))
        assertFalse(FeedThrottle.confirmed(10, 50 * mb, 192 * mb, systemLow = false))
    }

    @Test fun aTrimWithAFullHeapOrALowSystemThrottles() {
        assertTrue(FeedThrottle.confirmed(15, 150 * mb, 192 * mb, systemLow = false))
        assertTrue(FeedThrottle.confirmed(10, 9 * mb, 192 * mb, systemLow = true))
        assertTrue(FeedThrottle.confirmed(80, 9 * mb, 192 * mb, systemLow = true))
    }

    @Test fun routineLevelsNeverThrottle() {
        assertFalse(FeedThrottle.confirmed(5, 190 * mb, 192 * mb, systemLow = true))
        assertFalse(FeedThrottle.confirmed(20, 190 * mb, 192 * mb, systemLow = true))
        assertFalse(FeedThrottle.confirmed(40, 190 * mb, 192 * mb, systemLow = true))
    }
}
