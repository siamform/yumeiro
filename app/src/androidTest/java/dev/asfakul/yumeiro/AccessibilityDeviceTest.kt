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
    /**
     * Touch-target size only means something where there is touch. The TV app is driven by the
     * remote (D-pad focus is covered by the TV focus tests; the TV emulator still reports a
     * touchscreen feature, pass-after 44526618412, so the TV edition is the signal), and its
     * expanded rail floats over the left of every page, so ATF measures the visible slice of chips
     * under it (gate run 44523673246, tv-api34: nav_tab_videos 3 dp, nav_tab_music 43 dp). Every
     * other check still runs on TV, and phones keep the full set.
     */
    private val touch get() = !dev.asfakul.yumeiro.Edition.isTv
    private val validator get() = AccessibilityValidator().setRunChecksFromRootView(true)
        .setThrowExceptionFor(AccessibilityCheckResult.AccessibilityCheckResultType.ERROR)
        .also { v ->
            if (!touch) {
                log("A11Y TV edition: TouchTargetSizeCheck suppressed, all other checks run")
                v.setSuppressingResultMatcher(
                    com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResultUtils
                        .matchesCheckNames(org.hamcrest.Matchers.equalTo("TouchTargetSizeCheck")),
                )
            }
        }
    private fun has(tag: String) = runCatching { rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    private fun hasText(t: String) = runCatching { rule.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    private fun waitFor(what: String, ms: Long = 30_000, cond: () -> Boolean) {
        runCatching { rule.waitUntil(ms) { cond() } }.onFailure { throw AssertionError("$what never showed · ${FirstRun.onScreen(rule)}") }
    }

    /** Runs the ATF checks on what is on screen now; returns a short error summary or null. */
    private fun check(screen: String): String? {
        // A passing app message (e.g. "Ready to watch: … installed") floats over the bottom of the
        // page and cuts the touch area of whatever is under it; ATF then reports full-size chips
        // as 38 dp (43763558754, settings_downloads, cut exactly at the banner's top edge).
        // Check the page itself, not the transient overlay.
        dev.asfakul.yumeiro.ui.components.AppMessages.current.value?.let { m ->
            log("A11Y $screen dismissed app message first: ${m.text.take(60)}")
            inst.runOnMainSync { dev.asfakul.yumeiro.ui.components.AppMessages.dismiss(m.id) }
        }
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
            // 43765039266 api33-lowram: once, Back from Downloads never reached Settings home (it
            // passed on the same phone in 43763558754). Record what was on screen, then try once more
            // so the other categories are still checked; a second Back is still counted as an error.
            val home = runCatching { rule.waitUntil(15_000) { has("settings_cat_appearance") }; true }.getOrDefault(false)
            if (!home) {
                FirstRun.shot("a11y_back_stuck_$cat")
                val ime = FirstRun.shell("dumpsys input_method").lines().filter { "mInputShown" in it || "mShowRequested" in it }.joinToString(" ").trim()
                val focus = FirstRun.shell("dumpsys window").lines().filter { "mCurrentFocus" in it || "mFocusedApp" in it }.joinToString(" ").trim()
                log("A11Y settings_$cat BACK_STUCK ime=[$ime] focus=[$focus] · ${FirstRun.onScreen(rule)}")
                errors += "settings_$cat: Back needed twice"
                FirstRun.shell("input keyevent 4")
                waitFor("settings home after $cat (second Back)", 15_000) { has("settings_cat_appearance") }
            }
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
            FirstRun.openClip(rule, title)
            waitFor("first frame", 45_000) { dev.asfakul.yumeiro.player.PlaybackDiagnostics.state.value.firstFrame }
            rule.runOnUiThread { dev.asfakul.yumeiro.player.PlaybackSession.playerForTest?.pause() }
            // Controls show on a tap and hide after a few seconds; check while they are up.
            FirstRun.shell("input tap ${ctx.resources.displayMetrics.widthPixels / 2} ${ctx.resources.displayMetrics.heightPixels / 2}")
            Thread.sleep(600)
            finish("player", listOfNotNull(check("player")))
        } finally {
            runCatching { rule.runOnUiThread { dev.asfakul.yumeiro.player.PlaybackSession.releaseNow() } }
            TestClip.remove(ctx, uri, CLIP)
        }
    }

    private fun putClip(ctx: android.content.Context): android.net.Uri = TestClip.put(ctx, CLIP)

    companion object { private const val CLIP = LocalPlayerDeviceTest.NAME }
}
