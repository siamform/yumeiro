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
import androidx.test.platform.app.InstrumentationRegistry

/**
 * A fresh install opens on the welcome guide (and may ask for media / notification permission).
 * Device tests that start from the tabs get past it the way a user does: grant the permissions,
 * tap "Skip", and wait for the tab bar with nothing on top of it.
 */
object FirstRun {
    private val PERMISSIONS = listOf("android.permission.READ_MEDIA_VIDEO", "android.permission.READ_MEDIA_AUDIO", "android.permission.POST_NOTIFICATIONS")

    fun grant() {
        val inst = InstrumentationRegistry.getInstrumentation()
        PERMISSIONS.forEach { runCatching { inst.uiAutomation.grantRuntimePermission(inst.targetContext.packageName, it) } }
    }

    /** Runs a shell command and waits for it to finish (closing the pipe early can kill it). */
    fun shell(cmd: String): String =
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd).let { pfd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes().toString(Charsets.UTF_8) }
        }

    private fun permissionDialogOnTop(): Boolean =
        shell("dumpsys activity activities").lineSequence().any { "topResumedActivity" in it && "permissioncontroller" in it }

    private fun has(rule: AndroidComposeTestRule<*, *>, tag: String) =
        runCatching { rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)

    /** Gets to the tab bar; throws with what is on screen if it never shows. */
    fun toTabs(rule: AndroidComposeTestRule<*, *>, timeoutMs: Long = 60_000) {
        grant()
        val end = SystemClock.uptimeMillis() + timeoutMs
        var lastSkip = 0L
        while (SystemClock.uptimeMillis() < end) {
            val guide = has(rule, "setup_guide"); val tour = has(rule, "explore_tour")
            if (!guide && !tour && has(rule, "nav_tab_home") && !permissionDialogOnTop()) { dev.asfakul.yumeiro.online.OnboardingGate.release(); return }
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

    /** Taps the first node with [tag] that is actually on screen (several screens can share a tag). */
    fun tapVisible(rule: AndroidComposeTestRule<*, *>, tag: String, timeoutMs: Long = 30_000) {
        fun visible(): Int {
            val root = rule.onAllNodes(isRoot(), true).fetchSemanticsNodes().maxByOrNull { it.size.height } ?: return -1
            return rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().indexOfFirst { n ->
                val b = n.boundsInRoot
                b.width > 1f && b.height > 1f && b.left >= -1f && b.top >= -1f && b.right <= root.size.width + 1f && b.bottom <= root.size.height + 1f
            }
        }
        val ok = runCatching { rule.waitUntil(timeoutMs) { runCatching { visible() >= 0 }.getOrDefault(false) }; true }.getOrDefault(false)
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
