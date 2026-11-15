/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.compose.ui.test.junit4.accessibility.disableAccessibilityChecks
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResult
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityValidator
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G12 X8: the Accessibility Test Framework checks (touch target size, text and image contrast,
 * missing labels, duplicate descriptions, clickable spans) on the four main tabs, through the
 * real UI. Every finding is logged as `A11Y <screen> <check> <element>` so it can be fixed one by
 * one; the test fails on ERROR-level findings, the same rule Google's pre-launch report uses.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun log(s: String) = Log.i("YuA11y", s)

    @Test fun mainTabsPassAccessibilityChecks() {
        FirstRun.toTabs(rule)
        val validator = AccessibilityValidator().setRunChecksFromRootView(true)
            .setThrowExceptionFor(AccessibilityCheckResult.AccessibilityCheckResultType.ERROR)
        rule.enableAccessibilityChecks(validator)
        val errors = ArrayList<String>()
        for (tab in listOf("nav_tab_home", "nav_tab_videos", "nav_tab_music", "nav_tab_library")) {
            rule.disableAccessibilityChecks()
            FirstRun.tapVisible(rule, tab)
            rule.waitForIdle()
            Thread.sleep(2_500) // let the first page of content arrive
            rule.enableAccessibilityChecks(validator)
            val r = runCatching { rule.onRoot().tryPerformAccessibilityChecks() }
            val e = r.exceptionOrNull()
            if (e == null) log("A11Y $tab clean")
            else {
                // The exception text lists every ERROR result with its check and element.
                e.message.orEmpty().lines().filter { it.isNotBlank() }.forEach { log("A11Y $tab $it") }
                errors += "$tab: ${e.message.orEmpty().lines().count { it.isNotBlank() }} lines"
                FirstRun.shot("a11y_$tab")
            }
        }
        rule.disableAccessibilityChecks()
        log("A11Y_RESULT ${if (errors.isEmpty()) "PASS" else "FAIL $errors"}")
        assertTrue("accessibility errors: $errors (see YuA11y lines)", errors.isEmpty())
    }
}
