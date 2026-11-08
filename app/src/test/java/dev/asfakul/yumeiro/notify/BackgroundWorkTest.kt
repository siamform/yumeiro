/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.notify

import dev.asfakul.yumeiro.ui.screens.extras.PermissionCenter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundWorkTest {
    private val h = 3_600_000L

    @Test fun catchUp_isThrottledTo15Minutes() {
        assertTrue(BackgroundWork.catchUpDue(nowMs = 100 * h, lastMs = 0))
        assertFalse(BackgroundWork.catchUpDue(nowMs = 100 * h, lastMs = 100 * h - 5 * 60_000))
        assertTrue(BackgroundWork.catchUpDue(nowMs = 100 * h, lastMs = 100 * h - 16 * 60_000))
        assertTrue(BackgroundWork.catchUpDue(nowMs = 10 * h, lastMs = 100 * h)) // clock moved back
    }

    @Test fun stalled_whenNoRunForThreePeriods() {
        assertFalse(BackgroundHealth.stalled(nowMs = 10 * h, lastRunMs = 9 * h, firstSeenMs = 1 * h, periodHours = 2))
        assertTrue(BackgroundHealth.stalled(nowMs = 20 * h, lastRunMs = 9 * h, firstSeenMs = 1 * h, periodHours = 2))
        assertTrue(BackgroundHealth.stalled(nowMs = 20 * h, lastRunMs = 0, firstSeenMs = 1 * h, periodHours = 2))
        assertFalse(BackgroundHealth.stalled(nowMs = 20 * h, lastRunMs = 0, firstSeenMs = 0, periodHours = 2))
    }

    @Test fun vendorBatteryPages_knownForAggressiveOems() {
        assertTrue(PermissionCenter.vendorBatteryIntents("Xiaomi").isNotEmpty())
        assertTrue(PermissionCenter.vendorBatteryIntents("samsung").isNotEmpty())
        assertTrue(PermissionCenter.vendorBatteryIntents("Google").isEmpty())
    }
}
