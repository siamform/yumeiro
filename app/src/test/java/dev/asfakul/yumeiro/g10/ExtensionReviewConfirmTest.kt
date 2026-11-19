/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.g10

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import dev.asfakul.yumeiro.online.ExtensionReview
import dev.asfakul.yumeiro.online.ExtensionScanner
import dev.asfakul.yumeiro.ui.screens.online.ExtensionReviewHost
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * G10 ER-27: for an extension flagged Danger, "Install anyway" asks to be tapped twice, but the second
 * tap counted at once, so a double tap (or two quick OK presses on a TV remote, or a key repeat)
 * installed flagged code without the user ever seeing the warning line, which is drawn at the bottom of
 * the scrolling report. The confirmation must need a deliberate second tap: AOSP android-14.0.0_r1
 * PackageInstallerActivity enables OK only once the confirm text is shown (lines 158–162).
 * Old code: two taps 100 ms apart install.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExtensionReviewConfirmTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val danger = ExtensionScanner.Report(
        ExtensionScanner.Level.DANGER,
        listOf(ExtensionScanner.Finding(ExtensionScanner.Level.DANGER, "Runs shell commands", "Runtime.exec")),
    )

    private fun ask(): CompletableDeferred<Boolean> {
        val out = CompletableDeferred<Boolean>()
        CoroutineScope(Dispatchers.Main).launch {
            out.complete(ExtensionReview.decide(ExtensionReview.Mode.ALWAYS, "Flagged", 3, "test repo", false, danger))
        }
        return out
    }

    private fun tapInstallAnyway() = rule.onAllNodesWithTag("extension_review_install_anyway", true)[0].performClick()

    @Test fun aQuickDoubleTapDoesNotInstallDangerousCode() {
        rule.mainClock.autoAdvance = false
        rule.setContent { MTPlayerTheme { ExtensionReviewHost() } }
        rule.mainClock.advanceTimeBy(100)
        val answer = ask()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(500)
        tapInstallAnyway()
        rule.mainClock.advanceTimeBy(100)
        tapInstallAnyway()
        rule.mainClock.advanceTimeBy(100)
        // The answer reaches the caller through a coroutine on the main thread; drain it (the clock does
        // not move) or the check runs before an install could arrive (fail-before 44445709090 passed).
        rule.waitForIdle()
        assertTrue("ER-27: the dialog closed on a double tap", ExtensionReview.pending.value != null)
        assertTrue("ER-27: a double tap on Install anyway installed a Danger extension", !answer.isCompleted || answer.getCompleted() == false)
        // A deliberate second tap still installs.
        rule.mainClock.advanceTimeBy(1_500)
        tapInstallAnyway()
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()
        assertTrue("a deliberate second tap must still install", answer.isCompleted)
        assertEquals(true, answer.getCompleted())
    }
}
