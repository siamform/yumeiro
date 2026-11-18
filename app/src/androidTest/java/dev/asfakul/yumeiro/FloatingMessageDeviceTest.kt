/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.player.PlaybackDiagnostics
import dev.asfakul.yumeiro.player.PlaybackSession
import dev.asfakul.yumeiro.ui.components.AppMessages
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ST-21: an in-app message (AppMessages pill, e.g. "AnimeDekhoProvider has a message · Show") was drawn
 * over the in-app floating video window and took the tap meant for its close button (run 43965355950,
 * both phones: the first tap never reached the window, shot cfx_b_before_tap1).
 *
 * Opens a device clip, leaves it in the floating window, shows a message with an action, then:
 *  - the message and the floating window must not overlap;
 *  - one tap on the floating close button closes the player.
 * Log tag YuFloatMsg: FMSG_CASE / FMSG_RESULT PASS.
 */
@RunWith(AndroidJUnit4::class)
class FloatingMessageDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private var clip: Uri? = null
    private val title get() = NAME.substringBeforeLast('.')

    private fun log(s: String) = Log.i("YuFloatMsg", s)
    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun hasText(t: String) = rule.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun bounds(tag: String): Rect? = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot
    private fun wait(what: String, ms: Long = 30_000, cond: () -> Boolean) {
        val ok = runCatching { rule.waitUntil(ms) { cond() }; true }.getOrDefault(false)
        if (!ok) FirstRun.shot("fmsg_fail_" + what.filter { it.isLetterOrDigit() }.take(30))
        assertTrue("$what (after ${ms}ms) · playback=${PlaybackDiagnostics.state.value} · ${FirstRun.onScreen(rule)}", ok)
    }

    @Before fun setUp() {
        listOf("android.permission.READ_MEDIA_VIDEO", "android.permission.READ_EXTERNAL_STORAGE", "android.permission.POST_NOTIFICATIONS")
            .forEach { runCatching { inst.uiAutomation.grantRuntimePermission(ctx.packageName, it) } }
        clip = TestClip.put(ctx, NAME, "yumeiro_test_clip.mp4")
        FirstRun.toTabs(rule)
    }

    @After fun tearDown() {
        runCatching { rule.runOnUiThread { PlaybackSession.releaseNow() } }
        clip?.let { TestClip.remove(ctx, it, NAME) }
    }

    @Test fun messageNeverCoversTheFloatingPlayer() {
        wait("Videos tab") { FirstRun.hasNav(rule, "nav_tab_videos") } // TV: rail
        FirstRun.tapVisible(rule, "nav_tab_videos")
        wait("video source switch", 20_000) { has("video_source_device") || has("video_source_switch") }
        if (!has("video_source_device")) FirstRun.tapVisible(rule, "video_source_switch")
        wait("device source", 15_000) { has("video_source_device") }
        FirstRun.tapVisible(rule, "video_source_device")
        repeat(4) {
            if (hasText(title)) return@repeat
            if (hasText("YumeiroTest")) runCatching { rule.onAllNodesWithText("YumeiroTest", substring = true)[0].performClick() }
            else if (has("video_rescan_btn")) runCatching { FirstRun.tapVisible(rule, "video_rescan_btn") }
            runCatching { rule.waitUntil(10_000) { hasText(title) || hasText("YumeiroTest") } }
        }
        wait("the clip in Videos → Device") { hasText(title) }
        rule.onAllNodesWithText(title, substring = true)[0].performClick()
        wait("first frame", 45_000) { PlaybackDiagnostics.state.value.firstFrame }
        var presses = 0
        while (presses < 3 && !has("floating_close")) {
            FirstRun.shell("input keyevent 4"); presses++
            runCatching { rule.waitUntil(4_000) { has("floating_close") } }
        }
        wait("floating player after back", 10_000) { has("floating_close") }

        // A message with an action, the kind an extension card or a download prompt posts.
        rule.runOnUiThread { AppMessages.show(ctx, "YumeiroTest has a message", actionLabel = "Show", action = {}) }
        wait("message shown", 10_000) { has("app_message") }
        rule.waitForIdle()
        val msg = bounds("app_message")
        val win = bounds("video_floating_player")
        val close = bounds("floating_close")
        FirstRun.shot("fmsg_message_and_window")
        log("FMSG_CASE message=$msg window=$win close=$close")
        assertTrue("both bounds read", msg != null && win != null && close != null)
        assertFalse("the message covers the floating window: message=$msg window=$win", msg!!.overlaps(win!!))

        FirstRun.tapVisible(rule, "floating_close")
        wait("one tap closes the floating player", 5_000) { !has("video_floating_player") && !has("video_player_root") }
        log("FMSG_RESULT PASS")
    }

    private companion object { const val NAME = "yumeiro_floatmsg_clip.mp4" }
}
