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

    private fun has(rule: AndroidComposeTestRule<*, *>, tag: String) =
        runCatching { rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)

    /** Gets to the tab bar; throws with what is on screen if it never shows. */
    fun toTabs(rule: AndroidComposeTestRule<*, *>, timeoutMs: Long = 60_000) {
        grant()
        val end = SystemClock.uptimeMillis() + timeoutMs
        var lastSkip = 0L
        while (SystemClock.uptimeMillis() < end) {
            val guide = has(rule, "setup_guide"); val tour = has(rule, "explore_tour")
            if (!guide && !tour && has(rule, "nav_tab_home")) { dev.asfakul.yumeiro.online.OnboardingGate.release(); return }
            if (guide && has(rule, "setup_skip") && SystemClock.uptimeMillis() - lastSkip > 2_000) {
                runCatching { rule.onAllNodesWithTag("setup_skip", true)[0].performClick() }; lastSkip = SystemClock.uptimeMillis()
                Log.i("MTDeviceTest", "first run: tapped Skip on the welcome guide")
            } else if (tour || (guide && !has(rule, "setup_skip"))) {
                // The tour or a later guide page: Back closes it, as it does for a user.
                InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent 4").close()
            }
            val t = SystemClock.uptimeMillis() + 700
            runCatching { rule.waitUntil(3_000) { SystemClock.uptimeMillis() >= t } }.onFailure { SystemClock.sleep(700) }
        }
        throw AssertionError("tab bar never showed after ${timeoutMs}ms · ${onScreen(rule)}")
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
        "roots=${roots.size} · tags=${tags.take(40)} · text=$texts"
    }.getOrElse { "(could not read: ${it.javaClass.simpleName}: ${it.message?.take(80)})" + " · resumed: " +
        runCatching { InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("dumpsys activity activities").let { pfd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { s -> s.readBytes().toString(Charsets.UTF_8) } }
            .lineSequence().filter { "ResumedActivity" in it }.joinToString(" | ").take(300) }.getOrDefault("?") }
}
