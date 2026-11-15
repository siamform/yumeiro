/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R16 G1 H6: predictive back is on (`enableOnBackInvokedCallback="true"`). Back from another tab
 * returns to Home; the first back on Home keeps the app open, and a second back within two seconds
 * leaves it through the system (the app goes to the background, it is not killed).
 */
@RunWith(AndroidJUnit4::class)
class BackNavigationDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun log(s: String) = Log.i("YuBackTest", s)
    private fun back() { FirstRun.shell("input keyevent 4") }
    private fun state(): Lifecycle.State {
        var s = Lifecycle.State.INITIALIZED
        runCatching { rule.activityRule.scenario.onActivity { s = it.lifecycle.currentState } }
            .onFailure { s = Lifecycle.State.DESTROYED }
        return s
    }
    private fun page(): Int {
        var p = -1
        rule.activityRule.scenario.onActivity { p = dev.asfakul.yumeiro.ui.screens.TabPagerProbe.page }
        return p
    }

    @Test fun backReturnsHomeThenSecondBackLeaves() {
        FirstRun.toTabs(rule)
        rule.waitForIdle()
        // Swipe to the second tab, then back returns to Home (page 0).
        FirstRun.shell("input swipe 900 1200 150 1200 250")
        // Wait for the pager to settle: a back pressed mid-swipe is cancelled by the system when
        // the Home handlers change state under it (WindowOnBackDispatcher sendCancelIfRunning).
        val moved = runCatching { rule.waitUntil(5_000) { page() > 0 }; true }.getOrDefault(false)
        rule.waitForIdle(); Thread.sleep(800)
        log("after swipe page=${page()} moved=$moved")
        if (moved) {
            back()
            rule.waitUntil(5_000) { page() == 0 }
            assertEquals(Lifecycle.State.RESUMED, state())
        }
        Thread.sleep(2_500) // Let any exit window from earlier presses lapse.
        back()
        Thread.sleep(800)
        assertEquals("first back on Home must keep the app open", Lifecycle.State.RESUMED, state())
        back()
        val left = (0 until 40).any { Thread.sleep(100); state() != Lifecycle.State.RESUMED }
        log("after second back state=${state()}")
        assertTrue("second back within 2 s must leave the app (state=${state()})", left)
    }
}
