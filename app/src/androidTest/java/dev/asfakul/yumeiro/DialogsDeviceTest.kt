/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.accessibility.disableAccessibilityChecks
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResult
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityValidator
import dev.asfakul.yumeiro.ui.components.AppMessages
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G10: dialogs — Back closes each one without doing its action, the page under it stays, and the
 * open dialog passes the Accessibility Test Framework checks (ERROR level). The Settings → Privacy
 * "Clear …" confirmations (watch, listening, search, notifications, cache; 18+ when shown) share
 * the app's dialog (`TsukiDialog`), the same one most confirmations use.
 */
@RunWith(AndroidJUnit4::class)
class DialogsDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun log(m: String) = Log.i("YuDialogs", m)
    private fun has(tag: String) = runCatching { rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    private val validator get() = AccessibilityValidator().setRunChecksFromRootView(true)
        .setThrowExceptionFor(AccessibilityCheckResult.AccessibilityCheckResultType.ERROR)

    /** ATF on every root (the dialog is its own window); a short error summary or null. */
    private fun a11y(name: String): String? {
        rule.waitForIdle()
        val roots = runCatching { rule.onAllNodes(isRoot(), true).fetchSemanticsNodes().size }.getOrDefault(0)
        val errs = ArrayList<String>()
        rule.enableAccessibilityChecks(validator)
        for (i in 0 until roots) {
            runCatching { rule.onAllNodes(isRoot(), true)[i].tryPerformAccessibilityChecks() }.exceptionOrNull()?.let { e ->
                e.message.orEmpty().lines().filter { it.isNotBlank() }.forEach { log("DLG_A11Y $name root=$i $it"); errs += it }
            }
        }
        rule.disableAccessibilityChecks()
        return if (errs.isEmpty()) null else "$name: ${errs.size} lines"
    }

    @Test fun backClosesEachConfirmWithoutActing() {
        FirstRun.toTabs(rule)
        FirstRun.tapVisible(rule, "topbar_settings_action")
        runCatching { rule.waitUntil(20_000) { has("settings_home") } }
            .onFailure { throw AssertionError("Settings home never showed · ${FirstRun.onScreen(rule)}") }
        FirstRun.tapVisible(rule, "settings_cat_privacy"); SystemClock.sleep(1_200)
        val rows = listOf("privacy_clear_watch", "privacy_clear_adult", "privacy_clear_listening", "privacy_clear_search", "privacy_clear_inbox", "privacy_clear_cache")
        val problems = ArrayList<String>()
        var checked = 0
        for (row in rows) {
            if (!has(row)) { log("DLG_CASE $row not on this build/state, skipped"); continue }
            AppMessages.current.value?.let { m -> InstrumentationRegistryMain.run { AppMessages.dismiss(m.id) } }
            FirstRun.tapVisible(rule, row)
            val open = runCatching { rule.waitUntil(5_000) { has("privacy_confirm") }; true }.getOrDefault(false)
            if (!open) { problems += "$row: no dialog"; log("DLG_CASE $row no dialog · ${FirstRun.onScreen(rule)}"); continue }
            FirstRun.shot("dialog_$row")
            a11y(row)?.let { problems += it }
            FirstRun.shell("input keyevent 4")
            val closed = runCatching { rule.waitUntil(5_000) { !has("privacy_confirm") }; true }.getOrDefault(false)
            SystemClock.sleep(600)
            val stillHere = has(row)
            val acted = AppMessages.current.value?.text?.takeIf { "cleared" in it.lowercase() || "deleted" in it.lowercase() }
            log("DLG_CASE $row closedByBack=$closed pageKept=$stillHere acted='${acted.orEmpty()}'")
            if (!closed) { problems += "$row: Back did not close it"; FirstRun.shell("input keyevent 4") }
            if (!stillHere) problems += "$row: Back left the page"
            if (acted != null) problems += "$row: Back did the action ($acted)"
            checked++
        }
        log("DLG_RESULT checked=$checked ${if (problems.isEmpty()) "PASS" else "FAIL $problems"}")
        assertTrue("no dialog was checked", checked >= 4)
        assertTrue("dialog problems: $problems", problems.isEmpty())
    }
}

/** Runs [block] on the main thread (AppMessages state is set there in the app). */
private object InstrumentationRegistryMain {
    fun run(block: () -> Unit) = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
}
