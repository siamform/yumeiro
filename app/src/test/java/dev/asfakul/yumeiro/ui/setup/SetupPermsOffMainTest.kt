/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.setup

import android.os.Looper
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.asfakul.yumeiro.ui.screens.extras.PermissionCenter
import dev.asfakul.yumeiro.ui.screens.extras.SetupGuide
import dev.asfakul.yumeiro.ui.screens.extras.SetupStep
import dev.asfakul.yumeiro.ui.screens.extras.rememberSetupStatus
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Collections

/**
 * G12 X2-2: the setup checklist's permission checks are binder calls (2 s main-thread stall in
 * device run 43757544993, `PermissionCenter.mediaAllowed` from `rememberSetupStatus` in Home).
 * They must never run on the main thread, and the answer must still reach the checklist.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SetupPermsOffMainTest {
    @get:Rule val rule = createComposeRule()
    private val original = PermissionCenter.setupPermsReader

    @After fun tearDown() { PermissionCenter.setupPermsReader = original; SetupGuide.resetForTests() }

    @Test fun permissionChecksRunOffTheMainThread() {
        val onMain = Collections.synchronizedList(mutableListOf<Boolean>())
        PermissionCenter.setupPermsReader = {
            onMain += (Looper.myLooper() == Looper.getMainLooper())
            PermissionCenter.SetupPerms(media = true, notifications = true)
        }
        rule.setContent {
            val s = rememberSetupStatus()
            Text(if (SetupStep.MEDIA in s.done && SetupStep.NOTIFICATIONS in s.done) "perms-done" else "perms-pending")
        }
        rule.waitUntil(5_000) { onMain.isNotEmpty() }
        rule.waitUntil(5_000) { rule.onAllNodesWithTextCount("perms-done") == 1 }
        rule.onNodeWithText("perms-done").assertExists()
        assertTrue("reader was called", onMain.isNotEmpty())
        assertFalse("a permission check ran on the main thread: $onMain", onMain.any { it })
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(t: String) =
        onAllNodes(androidx.compose.ui.test.hasText(t)).fetchSemanticsNodes().size
}
