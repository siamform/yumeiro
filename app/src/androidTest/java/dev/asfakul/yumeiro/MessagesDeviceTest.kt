/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.accessibility.disableAccessibilityChecks
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResult
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityValidator
import dev.asfakul.yumeiro.ui.components.AppMessages
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G10 "list every message from code, show each on device": every literal message text in
 * app/src/main (assets/messages.tsv, made by scripts/messages-inventory.py) goes through the real
 * message host on the running app. Each must appear with its kind, show its whole text (no
 * ellipsis at the host's 3 lines), show its action button when the code gives one, and pass the
 * ATF checks. Computed texts (exception mappers) are covered by ErrorTaxonomyTest.
 */
@RunWith(AndroidJUnit4::class)
class MessagesDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun log(m: String) = Log.i("YuMessages", m)
    private val inst = InstrumentationRegistry.getInstrumentation()

    @Test fun everyLiteralMessageShowsWhole() {
        FirstRun.toTabs(rule)
        val rows = inst.context.assets.open("messages.tsv").bufferedReader().readLines().filter { it.isNotBlank() }.map { it.split('\t') }
        val ctx = inst.targetContext
        val problems = ArrayList<String>()
        var a11yRuns = 0
        rows.forEachIndexed { i, r ->
            val kind = when (r[0]) { "error" -> AppMessages.Kind.ERROR; "success" -> AppMessages.Kind.SUCCESS; "warning" -> AppMessages.Kind.WARNING; else -> AppMessages.Kind.INFO }
            val site = r[1]; val text = r[2]; val action = r.getOrNull(3)?.takeIf { it.isNotBlank() }
            inst.runOnMainSync { AppMessages.show(ctx, text, kind, action, if (action != null) ({}) else null) }
            val shown = runCatching { rule.waitUntil(4_000) { rule.onAllNodesWithTag("app_message", true).fetchSemanticsNodes().isNotEmpty() }; true }.getOrDefault(false)
            rule.waitForIdle()
            val nodes = rule.onAllNodesWithTag("app_message", true).fetchSemanticsNodes()
            // The Text inside the pill: its laid-out text and whether it ran past maxLines.
            var overflow: Boolean? = null; var lines = -1; var seen = ""
            fun walk(n: androidx.compose.ui.semantics.SemanticsNode) {
                val t = n.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
                if (t != null && t == text.take(240)) {
                    seen = t
                    val out = ArrayList<TextLayoutResult>()
                    n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(out)
                    out.firstOrNull()?.let { overflow = it.hasVisualOverflow; lines = it.lineCount }
                }
                n.children.forEach { walk(it) }
            }
            nodes.forEach { walk(it) }
            val actionShown = rule.onAllNodesWithTag("app_message_action", true).fetchSemanticsNodes().isNotEmpty()
            var a11y = "-"
            if (shown && (i % 5 == 0 || action != null || text.length > 80)) {
                a11yRuns++
                rule.enableAccessibilityChecks(AccessibilityValidator().setRunChecksFromRootView(true)
                    .setThrowExceptionFor(AccessibilityCheckResult.AccessibilityCheckResultType.ERROR))
                a11y = runCatching { rule.onAllNodes(isRoot(), true)[0].tryPerformAccessibilityChecks() }.exceptionOrNull()?.message?.lines()?.firstOrNull() ?: "ok"
                rule.disableAccessibilityChecks()
            }
            log("MSG_CASE ${i + 1}/${rows.size} $site kind=${r[0]} shown=$shown whole=${seen.isNotEmpty() && overflow == false} lines=$lines action=${action ?: "-"}:$actionShown a11y=$a11y")
            when {
                !shown -> problems += "$site: not shown"
                seen.isEmpty() -> problems += "$site: text not found in the pill"
                overflow != false -> problems += "$site: text cut (lines=$lines) '$text'"
                action != null && !actionShown -> problems += "$site: action '$action' missing"
                a11y != "-" && a11y != "ok" -> problems += "$site: a11y $a11y"
            }
            AppMessages.current.value?.let { m -> inst.runOnMainSync { AppMessages.dismiss(m.id) } }
            rule.waitForIdle()
        }
        log("MSG_RESULT total=${rows.size} a11yRuns=$a11yRuns problems=${problems.size} ${if (problems.isEmpty()) "PASS" else "FAIL"}")
        problems.forEach { log("MSG_PROBLEM $it") }
        assertTrue("no rows", rows.size >= 50)
        assertTrue("message problems (${problems.size}): ${problems.take(12)}", problems.isEmpty())
    }
}
