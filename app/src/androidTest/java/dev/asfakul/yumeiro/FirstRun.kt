/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry

/**
 * A fresh install opens on the welcome guide (and may ask for media / notification permission).
 * Device tests that start from the tabs get past it the way a user does: grant the permissions,
 * tap "Skip", and wait for the tab bar with nothing on top of it.
 */
object FirstRun {
    // Android 13+ media and notifications, plus the storage pair the app asks for on Android 7–12
    // (run 43757547383, TV API 24: only the 13+ names were granted and the storage dialog stayed up).
    private val PERMISSIONS = listOf(
        "android.permission.READ_MEDIA_VIDEO", "android.permission.READ_MEDIA_AUDIO", "android.permission.POST_NOTIFICATIONS",
        "android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE",
    )

    fun grant() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val pkg = inst.targetContext.packageName
        // UiAutomation.grantRuntimePermission is API 28+; older Android (the TV app) uses pm grant.
        PERMISSIONS.forEach {
            if (android.os.Build.VERSION.SDK_INT >= 28) runCatching { inst.uiAutomation.grantRuntimePermission(pkg, it) }
            else runCatching { shell("pm grant $pkg $it") }
        }
    }

    /** Runs a shell command and waits for it to finish (closing the pipe early can kill it). */
    fun shell(cmd: String): String {
        if (cmd.startsWith("input keyevent")) awaitKeyFocus(cmd)
        return InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd).let { pfd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes().toString(Charsets.UTF_8) }
        }
    }

    /**
     * A key goes to the window that has input focus, not to Compose. Runs 43765039266 (api33-lowram,
     * Settings) and 43765162241 (TV api24, Inbox): a Back sent just after a new test activity
     * started went to the previous test's activity window, still focused while it was being torn
     * down ("input event receiver has already been disposed"), and the page under test never saw
     * it. Wait until our resumed activity has window focus (the old one can already report
     * DESTROYED while its window still holds focus, so lifecycle alone is not enough); a dialog of
     * ours on top is fine, it should get the key. At most 10 s, then send anyway and say so.
     */
    internal fun awaitKeyFocus(cmd: String) {
        val inst = InstrumentationRegistry.getInstrumentation()
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) return
        val start = SystemClock.uptimeMillis()
        var ready = false
        val pkg = inst.targetContext.packageName
        while (SystemClock.uptimeMillis() - start < 10_000) {
            inst.runOnMainSync {
                val reg = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                ready = reg.getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).any { it.hasWindowFocus() }
            }
            // Our own dialog or popup on top: its window is named by the package alone, no "/Activity".
            if (!ready) ready = focusedWindow().let { isOwnNonActivityWindow(it, pkg) }
            if (ready) break
            Thread.sleep(150)
        }
        val waited = SystemClock.uptimeMillis() - start
        if (waited >= 100 || !ready) Log.i("YuKeyFocus", "'$cmd' waited ${waited}ms for window focus · ready=$ready · ${if (ready) "" else focusedWindow()}")
    }

    private fun focusedWindow(): String = runCatching {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("dumpsys window windows").let { pfd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes().toString(Charsets.UTF_8) }
        }.lineSequence().firstOrNull { "mCurrentFocus" in it }?.trim().orEmpty()
    }.getOrDefault("")

    /** `mCurrentFocus=Window{1a2b u0 dev.app}` (a dialog of ours) → true; `…dev.app/dev.app.MainActivity}` → false. */
    internal fun isOwnNonActivityWindow(line: String, pkg: String): Boolean {
        val name = line.substringAfter("mCurrentFocus=", "").substringAfterLast(' ').trimEnd('}')
        return name == pkg
    }

    /** API 29+ names the top activity `topResumedActivity` (permissioncontroller); API 24–28 says
     *  `mResumedActivity` and the dialog lives in packageinstaller's GrantPermissionsActivity. */
    internal fun isPermissionDialogLine(line: String): Boolean =
        ("topResumedActivity" in line || "mResumedActivity" in line) &&
            ("permissioncontroller" in line || "GrantPermissionsActivity" in line)

    private fun permissionDialogOnTop(): Boolean =
        shell("dumpsys activity activities").lineSequence().any(::isPermissionDialogLine)

    private fun settled(rule: AndroidComposeTestRule<*, *>, guide: Boolean, tour: Boolean): Boolean {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val ui = dev.asfakul.yumeiro.ui.screens.extras.SetupGuide
        return !guide && !tour && !ui.open.value && !ui.shouldAutoShow(ctx) && !has(rule, "splash_screen_root") &&
            (has(rule, "nav_tab_home") || has(rule, "tv_nav_tab_home")) && !permissionDialogOnTop()
    }

    private fun has(rule: AndroidComposeTestRule<*, *>, tag: String) =
        runCatching { rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)

    /** Gets to the tab bar; throws with what is on screen if it never shows. */
    fun toTabs(rule: AndroidComposeTestRule<*, *>, timeoutMs: Long = 60_000) {
        grant()
        val end = SystemClock.uptimeMillis() + timeoutMs
        var lastSkip = 0L
        while (SystemClock.uptimeMillis() < end) {
            val guide = has(rule, "setup_guide"); val tour = has(rule, "explore_tour")
            // The welcome guide opens only after the splash (run 43667108811: the helper returned
            // during the splash and the guide then covered every later tap). Settled means: the
            // guide was answered (or seen), it is closed, no tour, the tab bar shows, for 1.5 s.
            if (settled(rule, guide, tour)) {
                SystemClock.sleep(1_500)
                if (settled(rule, has(rule, "setup_guide"), has(rule, "explore_tour"))) {
                    dev.asfakul.yumeiro.online.OnboardingGate.release(); Log.i("MTDeviceTest", "first run: on the tabs"); return
                }
                continue
            }
            if (permissionDialogOnTop()) {
                // Already granted above; the system dialog can stay up anyway. Back closes it.
                Log.i("MTDeviceTest", "first run: closing the system permission dialog")
                shell("input keyevent 4")
                SystemClock.sleep(800); continue
            }
            if (guide && has(rule, "setup_skip") && SystemClock.uptimeMillis() - lastSkip > 2_000) {
                runCatching { rule.onAllNodesWithTag("setup_skip", true)[0].performClick() }; lastSkip = SystemClock.uptimeMillis()
                Log.i("MTDeviceTest", "first run: tapped Skip on the welcome guide")
            } else if (tour || (guide && !has(rule, "setup_skip"))) {
                // The tour or a later guide page: Back closes it, as it does for a user.
                shell("input keyevent 4")
            }
            val t = SystemClock.uptimeMillis() + 700
            runCatching { rule.waitUntil(3_000) { SystemClock.uptimeMillis() >= t } }.onFailure { SystemClock.sleep(700) }
        }
        throw AssertionError("tab bar never showed after ${timeoutMs}ms · ${onScreen(rule)}")
    }

    /** Screenshot into files/shots (pulled with the CI artifacts). */
    fun shot(name: String) = runCatching {
        val inst = InstrumentationRegistry.getInstrumentation()
        val bmp = inst.uiAutomation.takeScreenshot() ?: return@runCatching
        val dir = java.io.File(inst.targetContext.getExternalFilesDir(null), "shots").apply { mkdirs() }
        java.io.File(dir, "$name.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, it) }
    }

    /**
     * One-time prompts the app opens by itself a few seconds after launch (the "Keep notifications
     * on time" guide, the follow card) cover the screen; a user closes them, so do the tests.
     */
    fun dismissPrompts(rule: AndroidComposeTestRule<*, *>) {
        dismissSystemAnrDialog()
        for (t in listOf("guide_close", "reminder_follow_later")) if (has(rule, t)) {
            Log.i("MTDeviceTest", "closing the app prompt '$t'")
            runCatching { rule.onAllNodesWithTag(t, true)[0].performClick(); rule.waitForIdle() }
            SystemClock.sleep(400)
        }
    }

    /**
     * Another process's "isn't responding" dialog (run 43676915487, api33: "System UI isn't
     * responding") sits above the app and takes every swipe, so a feed test saw 0 of 16 swipes
     * scroll. device-check.sh turns these dialogs off (`hide_error_dialogs`); this is the fallback
     * when one still shows. Back on that dialog means "Wait". Our own ANR is never dismissed: it
     * is logged and fails the test through the stall checks and the ANR list.
     * Returns the window title it closed, or null.
     */
    fun dismissSystemAnrDialog(): String? {
        val focus = shell("dumpsys window").lineSequence().firstOrNull { "mCurrentFocus" in it } ?: return null
        if ("Application Not Responding" !in focus) return null
        val ours = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        if (ours in focus) return null
        Log.w("MTDeviceTest", "closing a system ANR dialog that covered the app: ${focus.trim()}")
        shell("input keyevent 4")
        SystemClock.sleep(600)
        return focus.trim()
    }

    /** Scrolls any list on screen until a node with [tag] is composed (cards below the fold). */
    private fun scrollTo(rule: AndroidComposeTestRule<*, *>, tag: String) {
        // Vertical lists only: the tab pager also scrolls to a node, and asking it would flip tabs.
        val vertical = androidx.compose.ui.test.hasScrollToNodeAction() and
            androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        val lists = runCatching { rule.onAllNodes(vertical, true).fetchSemanticsNodes().size }.getOrDefault(0)
        for (i in 0 until lists) runCatching {
            rule.onAllNodes(vertical, true)[i]
                .performScrollToNode(androidx.compose.ui.test.hasTestTag(tag))
        }
        rule.waitForIdle()
    }

    /** G12 X9-1: Yumeiro TV shows a side rail (`tv_nav_tab_*`, `tv_nav_shortcut_*`) instead of the
     *  bottom bar (`nav_tab_*`, `nav_shortcut_*`). */
    fun navTag(rule: AndroidComposeTestRule<*, *>, tag: String): String =
        if ((tag.startsWith("nav_tab_") || tag.startsWith("nav_shortcut_")) && !has(rule, tag) && has(rule, "tv_nav_rail")) "tv_$tag" else tag

    /** True if the bar item [tag] (or its TV rail twin) is on screen. */
    fun hasNav(rule: AndroidComposeTestRule<*, *>, tag: String): Boolean = has(rule, navTag(rule, tag))

    /** Taps the first node with [tag] that is actually on screen (several screens can share a tag). */

    fun tapVisible(rule: AndroidComposeTestRule<*, *>, tagIn: String, timeoutMs: Long = 30_000) {
        val tag = navTag(rule, tagIn)
        fun visible(): Int {
            val root = rule.onAllNodes(isRoot(), true).fetchSemanticsNodes().maxByOrNull { it.size.height } ?: return -1
            return rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().indexOfFirst { n ->
                val b = n.boundsInRoot
                b.width > 1f && b.height > 1f && b.left >= -1f && b.top >= -1f && b.right <= root.size.width + 1f && b.bottom <= root.size.height + 1f
            }
        }
        dismissPrompts(rule)
        var ok = runCatching { rule.waitUntil(minOf(timeoutMs, 8_000)) { runCatching { visible() >= 0 }.getOrDefault(false) }; true }.getOrDefault(false)
        if (!ok) {
            dismissPrompts(rule); scrollTo(rule, tag)
            ok = runCatching { rule.waitUntil(timeoutMs) { runCatching { visible() >= 0 }.getOrDefault(false) }; true }.getOrDefault(false)
        }
        if (!ok) { shot("missing_$tag"); throw AssertionError("no visible '$tag' after ${timeoutMs}ms · ${onScreen(rule)}") }
        rule.onAllNodesWithTag(tag, true)[visible()].performClick()
        rule.waitForIdle()
    }

    fun onScreen(rule: AndroidComposeTestRule<*, *>): String = runCatching {
        val roots = rule.onAllNodes(isRoot(), true).fetchSemanticsNodes()
        val tags = LinkedHashSet<String>(); val texts = LinkedHashSet<String>()
        fun walk(n: SemanticsNode) {
            n.config.getOrElseNullable(SemanticsProperties.TestTag) { null }?.let { tags += it }
            n.config.getOrElseNullable(SemanticsProperties.Text) { null }?.firstOrNull()?.text?.take(30)?.let { if (texts.size < 15) texts += it }
            n.children.forEach { walk(it) }
        }
        roots.forEach { walk(it) }
        "roots=${roots.size} · tags=${tags.take(400)} · text=$texts"
    }.getOrElse { "(could not read: ${it.javaClass.simpleName}: ${it.message?.take(80)})" + " · resumed: " +
        runCatching { shell("dumpsys activity activities").lineSequence().filter { "ResumedActivity" in it }.joinToString(" | ").take(300) }.getOrDefault("?") }
}
