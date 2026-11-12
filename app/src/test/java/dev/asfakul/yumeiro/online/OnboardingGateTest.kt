/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import dev.asfakul.yumeiro.ui.screens.extras.OnboardingPlan
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingGateTest {
    @After fun tearDown() = OnboardingGate.resetForTests()

    @Test fun returningUserIsNeverHeld() = runBlocking {
        OnboardingGate.resetForTests()
        OnboardingGate.begin(firstRun = false)
        assertFalse(OnboardingGate.isHeld)
        withTimeout(500) { OnboardingGate.awaitOpen() }
    }

    @Test fun firstRunHoldsExtensionsUntilReleased() = runBlocking {
        OnboardingGate.resetForTests()
        OnboardingGate.begin(firstRun = true)
        assertTrue(OnboardingGate.isHeld)
        val waiter = async { OnboardingGate.awaitOpen(); true }
        repeat(5) { yield() }
        assertFalse(waiter.isCompleted)
        OnboardingGate.release()
        assertTrue(withTimeout(500) { waiter.await() })
    }

    @Test fun gateIsALatchReplayingTheTourDoesNotReHold() {
        OnboardingGate.resetForTests()
        OnboardingGate.begin(true); OnboardingGate.release()
        OnboardingGate.begin(true)
        assertFalse(OnboardingGate.isHeld)
    }

    @Test fun planIsBusyWhileAnyGuideIsOnScreen() {
        assertTrue(OnboardingPlan.busy(splash = true, setupOpen = false, tourActive = false))
        assertTrue(OnboardingPlan.busy(false, true, false))
        assertTrue(OnboardingPlan.busy(false, false, true))
        assertFalse(OnboardingPlan.busy(false, false, false))
    }
}
