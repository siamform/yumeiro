/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.g10

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.asfakul.yumeiro.ui.components.AppMessageHost
import dev.asfakul.yumeiro.ui.components.AppMessages
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * G10 ER-22: the in-app message pill (AppMessageHost) closed after a fixed 4.5 s (2.6 s without an
 * action), ignoring Android's "Time to take action" accessibility setting
 * (Settings › Accessibility › Timing controls, `accessibility_interactive_ui_timeout_ms`). A TalkBack or
 * switch-access user, and anyone who needs more time, lost the Retry / Open / Undo action before
 * reaching it. Material3 SnackbarHost (androidx-main SnackbarHost.kt line 311) and the View Snackbar
 * (material-components-android 1.12.0 Snackbar.java line 375) both ask the AccessibilityManager for
 * the recommended timeout.
 * Old code: the message with an action is gone 10 s later even though the user asked for 30 s.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppMessageTimeoutTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private class LongTimeout(val ms: Long) : AccessibilityManager {
        var asked = 0
        override fun calculateRecommendedTimeoutMillis(originalTimeoutMillis: Long, containsIcons: Boolean, containsText: Boolean, containsControls: Boolean): Long {
            asked++
            return maxOf(originalTimeoutMillis, ms)
        }
    }

    @Test fun messageWithActionHonoursTheAccessibilityTimeout() {
        val a11y = LongTimeout(30_000)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalAccessibilityManager provides a11y) {
                MTPlayerTheme { Box(Modifier.fillMaxSize()) { AppMessageHost() } }
            }
        }
        rule.mainClock.advanceTimeBy(500)
        AppMessages.error(rule.activity, "Couldn't load the page", "Retry") {}
        rule.mainClock.advanceTimeBy(10_000)
        assertTrue("ER-22: the message with Retry closed before the accessibility timeout (30 s)", AppMessages.current.value != null)
        rule.mainClock.advanceTimeBy(21_000)
        assertEquals("the message must still close after the accessibility timeout", null, AppMessages.current.value)
    }
}
