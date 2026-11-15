/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.diagnostics.CrashLog
import dev.asfakul.yumeiro.ui.navigation.NavPrefs
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G12 X6: a real crash is saved, and the user can read, share and delete it in Settings.
 *
 * Two phases, run by `scripts/device-check.sh`:
 *  - `a` throws on a background thread, so the app really dies through Yumeiro's crash handler.
 *  - `b` (only with `-e crashed 1`) opens Settings → Crash reports, finds the report, reads it,
 *    opens the system share sheet, checks Keep leaves it and Delete removes it.
 */
@RunWith(AndroidJUnit4::class)
class CrashUiDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(s: String) = Log.i("YuCrashUi", s)
    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun hasText(t: String) = rule.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitUntil(what: String, ms: Long = 20_000, c: () -> Boolean) {
        val ok = runCatching { rule.waitUntil(ms) { c() }; true }.getOrDefault(false)
        if (!ok) FirstRun.shot("crashui_$what".replace(' ', '_'))
        assertTrue("$what · ${FirstRun.onScreen(rule)}", ok)
    }
    private fun topActivity() = FirstRun.shell("dumpsys activity activities").lineSequence()
        .firstOrNull { "topResumedActivity" in it }.orEmpty().trim()

    @Test fun a_crashForReal() {
        assumeTrue("phase a runs only from device-check.sh", InstrumentationRegistry.getArguments().getString("crash") == "1")
        CrashLog.clear(ctx)
        log("CRASH_A throwing")
        Thread({ throw IllegalStateException("YuCrashUi marker ${System.currentTimeMillis()}") }, "yu-crash-test").start()
        Thread.sleep(10_000) // the process dies before this ends
        throw AssertionError("the app did not die")
    }

    @Test fun b_readShareAndDelete() {
        assumeTrue("phase b runs only from device-check.sh after phase a", InstrumentationRegistry.getArguments().getString("crashed") == "1")
        val saved = CrashLog.list(ctx)
        log("reports on disk: ${saved.map { it.kind + ": " + it.headline.take(60) }}")
        assertTrue("the crash was saved", saved.any { "YuCrashUi marker" in it.headline })

        NavPrefs.setShortcut(ctx, NavPrefs.Shortcut.SETTINGS, true)
        try {
            FirstRun.toTabs(rule)
            FirstRun.tapVisible(rule, "nav_shortcut_settings")
            FirstRun.tapVisible(rule, "settings_cat_crashes")
            waitUntil("the report is listed") { has("crash_item_0") && hasText("YuCrashUi marker") }

            // Read
            FirstRun.tapVisible(rule, "crash_item_0")
            waitUntil("the report text loads") { has("crash_text") && hasText("IllegalStateException") }
            assertTrue("thread named", hasText("yu-crash-test"))
            FirstRun.tapVisible(rule, "crash_close")
            waitUntil("dialog closed") { !has("crash_text") }

            // Share: the system share sheet opens over the app.
            FirstRun.tapVisible(rule, "crash_share_all")
            var top = ""
            val chooser = (0 until 40).any { Thread.sleep(250); top = topActivity(); "Chooser" in top || "Resolver" in top || "intentresolver" in top }
            log("share top activity: $top")
            assertTrue("share sheet opened: $top", chooser)
            FirstRun.shell("input keyevent 4")
            waitUntil("back in the app", 10_000) { ctx.packageName in topActivity() && "Chooser" !in topActivity() }

            // Delete asks first; Keep keeps it.
            FirstRun.tapVisible(rule, "crash_clear")
            waitUntil("confirm dialog") { has("crash_clear_confirm") }
            FirstRun.tapVisible(rule, "crash_clear_cancel")
            waitUntil("kept") { !has("crash_clear_confirm") && has("crash_item_0") }
            assertTrue("Keep did not delete", CrashLog.list(ctx).isNotEmpty())

            FirstRun.tapVisible(rule, "crash_clear")
            FirstRun.tapVisible(rule, "crash_clear_confirm")
            waitUntil("deleted") { has("crash_empty") }
            assertTrue("files gone", CrashLog.list(ctx).isEmpty())
            FirstRun.shot("crashui_after_delete")
            log("CRASHUI_RESULT PASS")
        } finally {
            NavPrefs.setShortcut(ctx, NavPrefs.Shortcut.SETTINGS, false)
        }
    }
}
