/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.player.PlaybackDiagnostics
import dev.asfakul.yumeiro.player.PlaybackSession
import dev.asfakul.yumeiro.stream.DecoderFaults
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G12 decoder fault recovery, through the real UI with the debug fault hook ([DecoderFaults]).
 *  a: the first video decoder fails to start → the player falls back to the next decoder and plays.
 *  b: every video decoder fails → the error card says the format can't be decoded; the app lives.
 *  c: after b, with the fault cleared, the same clip plays again (nothing stuck).
 * Log tag YuDecoderFault; DECODER_FAULT_<x>_RESULT PASS lines are the evidence.
 */
@RunWith(AndroidJUnit4::class)
@org.junit.FixMethodOrder(org.junit.runners.MethodSorters.NAME_ASCENDING)
class DecoderFaultDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val inst get() = InstrumentationRegistry.getInstrumentation()

    private fun log(s: String) = Log.i("YuDecoderFault", s)
    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun hasText(t: String) = rule.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun wait(what: String, ms: Long = 30_000, cond: () -> Boolean) {
        val ok = runCatching { rule.waitUntil(ms) { cond() }; true }.getOrDefault(false)
        if (!ok) FirstRun.shot("decoder_fault_${what.replace(' ', '_')}")
        assertTrue("$what (after ${ms}ms) · playback=${PlaybackDiagnostics.state.value} · ${FirstRun.onScreen(rule)}", ok)
    }
    private fun tap(tag: String) = FirstRun.tapVisible(rule, tag)
    private var clip: Uri? = null

    @Before fun setUp() {
        listOf("android.permission.READ_MEDIA_VIDEO", "android.permission.READ_EXTERNAL_STORAGE", "android.permission.POST_NOTIFICATIONS")
            .forEach { runCatching { inst.uiAutomation.grantRuntimePermission(ctx.packageName, it) } }
        // The fault clip is the shared test clip under its own name (there is no separate asset).
        clip = TestClip.put(ctx, NAME, asset = "yumeiro_test_clip.mp4")
        // A fresh player per test: the fault mode is read when the player is built.
        rule.runOnUiThread { PlaybackSession.releaseNow() }
        FirstRun.toTabs(rule)
        PlaybackDiagnostics.reset("")
        DecoderFaults.offered.set(0)
    }

    @After fun tearDown() {
        DecoderFaults.mode = DecoderFaults.Mode.NONE
        runCatching { rule.runOnUiThread { PlaybackSession.releaseNow() } }
        clip?.let { TestClip.remove(ctx, it, NAME) }
    }

    /** Videos → Device → (rescan) → the clip; returns without waiting for a frame. */
    private fun openClip() {
        wait("Videos tab") { has("nav_tab_videos") }
        tap("nav_tab_videos")
        wait("video source switch", 20_000) { has("video_source_device") || has("video_source_switch") }
        if (!has("video_source_device")) tap("video_source_switch")
        wait("device source", 15_000) { has("video_source_device") }
        tap("video_source_device")
        val title = NAME.substringBeforeLast('.')
        repeat(4) {
            if (hasText(title)) return@repeat
            if (hasText("YumeiroTest")) runCatching { rule.onAllNodesWithText("YumeiroTest", substring = true)[0].performClick() }
            else if (has("video_rescan_btn")) runCatching { tap("video_rescan_btn") }
            runCatching { rule.waitUntil(10_000) { hasText(title) || hasText("YumeiroTest") } }
        }
        wait("the clip in Videos → Device", 30_000) { hasText(title) }
        rule.onAllNodesWithText(title, substring = true)[0].performClick()
    }

    private fun decoderLog(): String = FirstRun.shell("logcat -d -t 400 -s MediaCodecRenderer:* YumeiroRenderers:*")
        .lines().filter { "fault" in it || "Failed to initialize" in it || "decoder" in it.lowercase() }.takeLast(8).joinToString(" | ")

    @Test fun a_firstDecoderFailsAndThePlayerFallsBack() {
        DecoderFaults.mode = DecoderFaults.Mode.FIRST_VIDEO_FAILS
        openClip()
        wait("first frame after decoder fallback", 45_000) { PlaybackDiagnostics.state.value.firstFrame }
        val counters = onPlayer { it.videoDecoderCounters?.decoderInitCount } ?: -1
        log("a: offered=${DecoderFaults.offered.get()} inits=$counters state=${PlaybackDiagnostics.state.value} log=${decoderLog()}")
        assertTrue("the fake decoder was offered", DecoderFaults.offered.get() > 0)
        assertTrue("no error card", PlaybackDiagnostics.state.value.error == null && !hasText("can't decode"))
        log("DECODER_FAULT_A_RESULT PASS")
    }

    @Test fun b_everyDecoderFailsAndTheErrorCardSaysSo() {
        DecoderFaults.mode = DecoderFaults.Mode.ALL_VIDEO_FAIL
        openClip()
        wait("decode error card", 45_000) { hasText("can't decode") }
        log("b: offered=${DecoderFaults.offered.get()} state=${PlaybackDiagnostics.state.value} log=${decoderLog()}")
        assertTrue("the fake decoder was offered", DecoderFaults.offered.get() > 0)
        assertTrue("no frame was drawn", !PlaybackDiagnostics.state.value.firstFrame)
        assertTrue("app still running", FirstRun.shell("pidof ${ctx.packageName}").isNotBlank())
        log("DECODER_FAULT_B_RESULT PASS")
    }

    @Test fun c_afterAFaultTheSameClipPlaysAgain() {
        DecoderFaults.mode = DecoderFaults.Mode.ALL_VIDEO_FAIL
        openClip()
        wait("decode error card", 45_000) { hasText("can't decode") }
        // The card's own Close, as a user taps it. A shell `input keyevent 4` switches Compose to
        // keyboard input mode, where Back is stepped like a remote (PlayerBackRule: a fullscreen
        // player first leaves fullscreen), so one key press left the card on screen (run 43851896291).
        tap("error_close_btn")
        rule.runOnUiThread { PlaybackSession.releaseNow() }
        DecoderFaults.mode = DecoderFaults.Mode.NONE
        PlaybackDiagnostics.reset("")
        wait("player closed", 15_000) { !hasText("can't decode") }
        val title = NAME.substringBeforeLast('.')
        wait("clip still listed", 20_000) { hasText(title) }
        rule.onAllNodesWithText(title, substring = true)[0].performClick()
        wait("first frame after the fault cleared", 45_000) { PlaybackDiagnostics.state.value.firstFrame }
        log("c: state=${PlaybackDiagnostics.state.value}")
        log("DECODER_FAULT_C_RESULT PASS")
    }

    private fun <T> onPlayer(read: (androidx.media3.exoplayer.ExoPlayer) -> T): T? {
        var out: T? = null
        rule.runOnUiThread { PlaybackSession.playerForTest?.let { out = read(it) } }
        return out
    }

    companion object { const val NAME = "yumeiro_fault_clip.mp4" }
}
