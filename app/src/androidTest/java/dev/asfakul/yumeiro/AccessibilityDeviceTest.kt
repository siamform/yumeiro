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
import android.provider.MediaStore
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
 * missing labels, duplicate descriptions, clickable spans) through the real UI: the four main
 * tabs, Settings and its main categories, search with suggestions, the video player, and the
 * tabs and Settings again at 130 % font size (X8-2). Every finding is logged as `A11Y <screen> <check> <element>` so it can be fixed one by
 * one; the test fails on ERROR-level findings, the same rule Google's pre-launch report uses.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun log(s: String) = Log.i("YuA11y", s)

    private val inst get() = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
    private val validator get() = AccessibilityValidator().setRunChecksFromRootView(true)
        .setThrowExceptionFor(AccessibilityCheckResult.AccessibilityCheckResultType.ERROR)
    private fun has(tag: String) = runCatching { rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    private fun hasText(t: String) = runCatching { rule.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    private fun waitFor(what: String, ms: Long = 30_000, cond: () -> Boolean) {
        runCatching { rule.waitUntil(ms) { cond() } }.onFailure { throw AssertionError("$what never showed · ${FirstRun.onScreen(rule)}") }
    }

    /** Runs the ATF checks on what is on screen now; returns a short error summary or null. */
    private fun check(screen: String): String? {
        rule.waitForIdle()
        rule.enableAccessibilityChecks(validator)
        val e = runCatching { rule.onAllNodes(isRoot(), true)[0].tryPerformAccessibilityChecks() }.exceptionOrNull()
        rule.disableAccessibilityChecks()
        if (e == null) { log("A11Y $screen clean"); return null }
        // The exception text lists every ERROR result with its check and element.
        val lines = e.message.orEmpty().lines().filter { it.isNotBlank() }
        lines.forEach { log("A11Y $screen $it") }
        FirstRun.shot("a11y_$screen")
        return "$screen: ${lines.size} lines"
    }

    private fun finish(name: String, errors: List<String>) {
        log("A11Y_RESULT $name ${if (errors.isEmpty()) "PASS" else "FAIL $errors"}")
        assertTrue("accessibility errors: $errors (see YuA11y lines)", errors.isEmpty())
    }

    private fun tabs(prefix: String): List<String> = listOf("nav_tab_home", "nav_tab_videos", "nav_tab_music", "nav_tab_library").mapNotNull { tab ->
        FirstRun.tapVisible(rule, tab)
        rule.waitForIdle()
        Thread.sleep(2_500) // let the first page of content arrive
        check("$prefix$tab")
    }

    @Test fun mainTabsPassAccessibilityChecks() {
        FirstRun.toTabs(rule)
        finish("tabs", tabs(""))
    }

    /** X8-2: Settings home and the main categories. */
    @Test fun settingsPassAccessibilityChecks() {
        FirstRun.toTabs(rule)
        val errors = ArrayList<String>()
        FirstRun.tapVisible(rule, "topbar_settings_action")
        waitFor("settings", 20_000) { has("settings_cat_appearance") }
        check("settings_home")?.let { errors += it }
        for (cat in listOf("appearance", "player", "audio", "online", "downloads", "library", "tracking", "system", "about")) {
            if (!has("settings_cat_$cat")) { log("A11Y settings_$cat not on this build, skipped"); continue }
            FirstRun.tapVisible(rule, "settings_cat_$cat")
            Thread.sleep(1_500)
            check("settings_$cat")?.let { errors += it }
            FirstRun.shell("input keyevent 4")
            waitFor("settings home after $cat", 15_000) { has("settings_cat_appearance") }
        }
        finish("settings", errors)
    }

    /** X8-2: top-bar search with suggestions open. */
    @Test fun searchPassesAccessibilityChecks() {
        dev.asfakul.yumeiro.ui.components.RecentSearches.add(inst.targetContext, "library", "yumeiro nightfall")
        FirstRun.toTabs(rule)
        FirstRun.tapVisible(rule, "topbar_search_action")
        waitFor("search field", 15_000) { has("topbar_search_input") }
        rule.onNodeWithTag("topbar_search_input", true).performTextInput("yumei")
        runCatching { rule.waitUntil(10_000) { has("topbar_suggestions") } }
        Thread.sleep(800)
        finish("search", listOfNotNull(check("search")))
    }

    /** X8-2: the four tabs and Settings at 130 % font size (Android's large font). */
    @Test fun largeFontPassesAccessibilityChecks() {
        val errors = ArrayList<String>()
        try {
            FirstRun.shell("settings put system font_scale 1.3")
            rule.activityRule.scenario.recreate()
            FirstRun.toTabs(rule)
            log("A11Y font_scale=${inst.targetContext.resources.configuration.fontScale}")
            errors += tabs("font130_")
            FirstRun.tapVisible(rule, "topbar_settings_action")
            waitFor("settings", 20_000) { has("settings_cat_appearance") }
            check("font130_settings_home")?.let { errors += it }
        } finally {
            FirstRun.shell("settings put system font_scale 1.0")
        }
        finish("large_font", errors)
    }

    /** X8-2: the video player with its controls showing (device clip, as in LocalPlayerDeviceTest). */
    @Test fun videoPlayerPassesAccessibilityChecks() {
        val ctx = inst.targetContext
        val uri = putClip(ctx)
        try {
            FirstRun.toTabs(rule)
            FirstRun.tapVisible(rule, "nav_tab_videos")
            waitFor("video source switch", 20_000) { has("video_source_device") || has("video_source_switch") }
            if (!has("video_source_device")) FirstRun.tapVisible(rule, "video_source_switch")
            waitFor("device source", 15_000) { has("video_source_device") }
            FirstRun.tapVisible(rule, "video_source_device")
            val title = CLIP.substringBeforeLast('.')
            repeat(4) {
                if (hasText(title)) return@repeat
                if (hasText("YumeiroTest")) runCatching { rule.onAllNodesWithText("YumeiroTest", substring = true)[0].performClick() }
                else if (has("video_rescan_btn")) runCatching { FirstRun.tapVisible(rule, "video_rescan_btn") }
                runCatching { rule.waitUntil(10_000) { hasText(title) || hasText("YumeiroTest") } }
            }
            waitFor("the clip", 30_000) { hasText(title) }
            rule.onAllNodesWithText(title, substring = true)[0].performClick()
            waitFor("first frame", 45_000) { dev.asfakul.yumeiro.player.PlaybackDiagnostics.state.value.firstFrame }
            rule.runOnUiThread { dev.asfakul.yumeiro.player.PlaybackSession.playerForTest?.pause() }
            // Controls show on a tap and hide after a few seconds; check while they are up.
            FirstRun.shell("input tap ${ctx.resources.displayMetrics.widthPixels / 2} ${ctx.resources.displayMetrics.heightPixels / 2}")
            Thread.sleep(600)
            finish("player", listOfNotNull(check("player")))
        } finally {
            runCatching { rule.runOnUiThread { dev.asfakul.yumeiro.player.PlaybackSession.releaseNow() } }
            runCatching { ctx.contentResolver.delete(uri, null, null) }
        }
    }

    private fun putClip(ctx: android.content.Context): android.net.Uri {
        val cr = ctx.contentResolver
        val coll = if (android.os.Build.VERSION.SDK_INT >= 29) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        runCatching { cr.delete(coll, "${MediaStore.Video.Media.DISPLAY_NAME}=?", arrayOf(CLIP)) }
        val v = android.content.ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, CLIP)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (android.os.Build.VERSION.SDK_INT >= 29) { put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/YumeiroTest"); put(MediaStore.Video.Media.IS_PENDING, 1) }
        }
        val uri = checkNotNull(cr.insert(coll, v)) { "MediaStore insert failed" }
        cr.openOutputStream(uri)!!.use { out -> inst.context.assets.open(CLIP).use { it.copyTo(out) } }
        if (android.os.Build.VERSION.SDK_INT >= 29) cr.update(uri, android.content.ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
        return uri
    }

    companion object { private const val CLIP = LocalPlayerDeviceTest.NAME }
}
