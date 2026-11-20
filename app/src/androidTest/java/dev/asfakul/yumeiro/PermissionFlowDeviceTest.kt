/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.ui.components.AppMessages
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * G10 ER-2: the notification permission flow on Android 13+, through the real system dialog.
 *
 * Phase a (`-e phase a`) gets past first run. scripts/device-check.sh then revokes
 * POST_NOTIFICATIONS and clears its "user-set/user-fixed" flags (revoking kills the app, so it
 * cannot run inside a test). Phase b (`-e phase b`, a new process):
 *  1. Settings → Privacy → Notifications; "Don't allow" in the system dialog →
 *     the app says "not allowed. Tap again…" and stays in front (before ER-2: Android settings opened).
 *  2. Tap again, deny again (Android now stops asking) → "blocked for Yumeiro… allow it in Android
 *     settings" with an "Open settings" action.
 *  3. The action opens Android's settings (com.android.settings in front).
 * The script grants the permission back afterwards.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PermissionFlowDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val phase = InstrumentationRegistry.getArguments().getString("phase").orEmpty()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun log(m: String) = Log.i("YuPermFlow", m)
    private fun has(tag: String) = runCatching { rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    private fun pause(ms: Long) {
        val t = SystemClock.uptimeMillis() + ms
        runCatching { rule.waitUntil(ms + 2_000) { SystemClock.uptimeMillis() >= t } }.onFailure { SystemClock.sleep(ms) }
    }

    @Test fun a_seedFirstRun() {
        assumeTrue("phase a only", phase == "a")
        assumeTrue("Android 13+ only", Build.VERSION.SDK_INT >= 33)
        FirstRun.toTabs(rule)
        log("PERM_SEED on the tabs")
    }

    @Test fun b_deniedOnceThenBlocked() {
        assumeTrue("phase b only", phase == "b")
        assumeTrue("Android 13+ only", Build.VERSION.SDK_INT >= 33)
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        log("PERM_PRE granted=$granted")
        assertTrue("ERROR_PRECONDITION: the script did not revoke the permission", !granted)
        // First run was finished in phase a; only the tabs are needed (no grant here). The app asks
        // for missing permissions at every start (MTPlayerApp), so with the permission revoked the
        // system dialog comes up over the tabs first (run 43880703410). Back closes it without a choice.
        val upTo = SystemClock.uptimeMillis() + 60_000
        var startupDialog = false
        while (SystemClock.uptimeMillis() < upTo) {
            if ("GrantPermissionsActivity" in topActivity()) { startupDialog = true; break }
            if (runCatching { FirstRun.hasNav(rule, "nav_tab_home") }.getOrDefault(false) && SystemClock.uptimeMillis() > upTo - 50_000) break
            SystemClock.sleep(500)
        }
        if (startupDialog) {
            FirstRun.shot("perm_startup_dialog")
            FirstRun.shell("input keyevent 4"); SystemClock.sleep(1_500)
            log("PERM_CASE startup system dialog shown; Back -> top='${topActivity().takeLast(90)}'")
        }
        runCatching { rule.waitUntil(30_000) { FirstRun.hasNav(rule, "nav_tab_home") } }
            .onFailure { throw AssertionError("tabs never showed · ${FirstRun.onScreen(rule)} · top=${topActivity()}") }
        pause(1_500); FirstRun.dismissPrompts(rule)
        FirstRun.tapVisible(rule, "topbar_settings_action")
        runCatching { rule.waitUntil(20_000) { has("settings_home") } }
            .onFailure { throw AssertionError("Settings home never showed · ${FirstRun.onScreen(rule)}") }
        // Run 43891331959 api36: one tap on the category left Settings home showing (a second start-up
        // permission dialog took the tap). Retry, closing any system dialog in between.
        var opened = false
        for (attempt in 1..4) {
            if ("GrantPermissionsActivity" in topActivity()) { FirstRun.shell("input keyevent 4"); SystemClock.sleep(1_500) }
            if (!has("settings_cat_privacy") && has("perm_notifications")) { opened = true; break }
            runCatching { FirstRun.tapVisible(rule, "settings_cat_privacy", 8_000) }
            opened = runCatching { rule.waitUntil(6_000) { has("perm_notifications") }; true }.getOrDefault(false)
            log("PERM_CASE open Privacy attempt=$attempt opened=$opened top='${topActivity().takeLast(90)}'")
            if (opened) break
        }
        assertTrue("Settings › Privacy never opened · ${FirstRun.onScreen(rule)} · top=${topActivity()}", opened)
        pause(1_200)

        // 1: deny once.
        FirstRun.tapVisible(rule, "perm_notifications")
        val b1 = denySystemDialog()
        val m1 = awaitMessage("not allowed")
        val front1 = appInFront()
        log("PERM_CASE deny1 button=$b1 message='${m1?.text}' kind=${m1?.kind} inFront=$front1")
        FirstRun.shot("perm_deny1")
        assertTrue("deny 1: expected 'not allowed. Tap again…', got '${m1?.text}' (startupDialog=$startupDialog)", m1 != null && "Tap again" in m1.text)
        assertTrue("deny 1: the app left the front (Android settings opened by itself?)", front1)
        pause(3_500) // let the message go before the next one

        // 2: deny again; Android stops asking.
        // FINAL-39 (runs 44662694767 and 44666813073, tv-api34): the TV permission controller never offers
        // "Don't allow and don't ask again" and keeps asking after every "Don't allow" (logcat: three
        // POST_NOTIFICATIONS results 6 = USER_DENIED, dialog shown each time, no result 7). The app then
        // correctly says "Tap again" (Android will ask again), and the old check waited for "blocked".
        // The app's message must match Android's own state: shouldShowRequestPermissionRationale true =
        // "not allowed. Tap again", false = "blocked" with "Open settings". Up to three more denials are
        // tried; where Android never blocks, step 3 cannot happen and is skipped with that reason.
        var m2: AppMessages.Message? = null
        var blocked = false
        for (round in 2..5) {
            FirstRun.tapVisible(rule, "perm_notifications")
            val b2 = denySystemDialog()
            var rationale = true
            rule.runOnUiThread { rationale = androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(rule.activity, Manifest.permission.POST_NOTIFICATIONS) }
            m2 = awaitMessage("Notifications")
            log("PERM_CASE deny$round button=$b2 rationale=$rationale message='${m2?.text}' action=${m2?.actionLabel} inFront=${appInFront()}")
            FirstRun.shot("perm_deny$round")
            if (rationale) {
                assertTrue("deny $round: Android will ask again, expected 'not allowed. Tap again…', got '${m2?.text}'", m2 != null && "Tap again" in m2.text)
                assertTrue("deny $round: the app left the front", appInFront())
                pause(3_500)
                continue
            }
            assertTrue("deny $round: expected 'blocked… allow it in Android settings', got '${m2?.text}'", m2 != null && "blocked for Yumeiro" in m2.text && m2.actionLabel == "Open settings")
            blocked = true
            break
        }
        log("PERM_CASE blocked=$blocked")
        assumeTrue("Android on this device keeps asking after every 'Don't allow' (no don't-ask-again state), so the 'Open settings' step cannot occur", blocked)

        // 3: the action opens Android settings.
        FirstRun.tapVisible(rule, "app_message_action", 8_000)
        val end = SystemClock.uptimeMillis() + 10_000
        var top = ""
        while (SystemClock.uptimeMillis() < end) {
            top = topActivity(); if ("com.android.settings" in top) break
            SystemClock.sleep(400)
        }
        log("PERM_CASE action top='$top'")
        val ok = "com.android.settings" in top
        log("PERM_RESULT ${if (ok) "PASS" else "FAIL"}")
        FirstRun.shell("am start -n ${ctx.packageName}/dev.asfakul.yumeiro.MainActivity")
        assertTrue("Open settings did not open Android settings (top: $top)", ok)
    }

    /**
     * Waits for the system permission dialog and taps its deny button; returns the id it tapped.
     * Reads the dialog through this instrumentation's own UiAutomation: `uiautomator dump` from the
     * shell is a second UiAutomation client, and on api33-lowram it died with "Bad file descriptor"
     * while the dialog was up (run 43891331959), so the dialog was never seen.
     */
    private fun denySystemDialog(): String {
        val ua = InstrumentationRegistry.getInstrumentation().uiAutomation
        runCatching { ua.serviceInfo = ua.serviceInfo.apply { flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS } }
        val end = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < end) {
            val roots = buildList {
                runCatching { ua.rootInActiveWindow }.getOrNull()?.let { add(it) }
                runCatching { ua.windows.mapNotNull { it.root } }.getOrNull()?.let { addAll(it) }
            }
            for (id in DENY_IDS) {
                val node = roots.firstNotNullOfOrNull { r -> r.findAccessibilityNodeInfosByViewId(id).firstOrNull { it.isVisibleToUser } } ?: continue
                val clicked = node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                if (!clicked) {
                    val b = android.graphics.Rect().also { node.getBoundsInScreen(it) }
                    FirstRun.shell("input tap ${b.centerX()} ${b.centerY()}")
                }
                SystemClock.sleep(1_000)
                return id.substringAfter(":id/") + if (clicked) "" else " (tap)"
            }
            SystemClock.sleep(500)
        }
        FirstRun.shot("perm_no_dialog")
        throw AssertionError("the system permission dialog never showed · top=${topActivity()}")
    }

    /** The first in-app message containing [part] within 10 s (read from the message state). */
    private fun awaitMessage(part: String): AppMessages.Message? {
        var hit: AppMessages.Message? = null
        runCatching {
            rule.waitUntil(10_000) {
                AppMessages.current.value?.takeIf { part in it.text }?.let { hit = it }
                hit != null
            }
        }
        if (hit == null) log("PERM_NO_MESSAGE '$part' now='${AppMessages.current.value?.text}' · ${FirstRun.onScreen(rule)}")
        return hit
    }

    private fun topActivity(): String =
        FirstRun.shell("dumpsys activity activities").lineSequence().firstOrNull { "topResumedActivity" in it }?.trim().orEmpty()

    private fun appInFront(): Boolean = "${ctx.packageName}/" in topActivity()

    companion object {
        private val DENY_IDS = listOf(
            "com.android.permissioncontroller:id/permission_deny_and_dont_ask_again_button",
            "com.android.permissioncontroller:id/permission_deny_button",
        )
    }
}
