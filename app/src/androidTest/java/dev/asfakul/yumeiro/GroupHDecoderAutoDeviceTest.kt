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
import dev.asfakul.yumeiro.player.VideoPrefs
import dev.asfakul.yumeiro.stream.DecoderAuto
import dev.asfakul.yumeiro.stream.DecoderFaults
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * GROUP H-3 (owner item 3): the "Auto" video decoder, through the real UI with the debug fault hook
 * ([DecoderFaults.Mode.PHONE_ORDER_FAILS]: the phone's own decoder order fails like a broken vendor decoder, the
 * software-first order works).
 *  a: Auto → the first try fails, Auto switches once to the software decoder and the video draws frames.
 *  b: Hardware+ (the old default) → the same video ends on the "can't decode" card. This is the fail-before:
 *     before H-3 the default was Hardware+, so this clip did not play.
 * Log tag YuGroupH; GROUPH_DECODER_<x>_RESULT PASS lines are the evidence.
 */
@RunWith(AndroidJUnit4::class)
@org.junit.FixMethodOrder(org.junit.runners.MethodSorters.NAME_ASCENDING)
class GroupHDecoderAutoDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val inst get() = InstrumentationRegistry.getInstrumentation()

    private fun log(s: String) = Log.i("YuGroupH", s)
    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun hasText(t: String) = rule.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun wait(what: String, ms: Long = 30_000, cond: () -> Boolean) {
        val ok = runCatching { rule.waitUntil(ms) { cond() }; true }.getOrDefault(false)
        if (!ok) FirstRun.shot("grouph_decoder_${what.replace(' ', '_')}")
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
        before = VideoPrefs.decoder.value
    }
    private var before: VideoPrefs.Decoder = VideoPrefs.Decoder.AUTO

    @After fun tearDown() {
        DecoderFaults.mode = DecoderFaults.Mode.NONE
        VideoPrefs.setDecoder(ctx, before)
        runCatching { rule.runOnUiThread { PlaybackSession.releaseNow() } }
        clip?.let { TestClip.remove(ctx, it, NAME) }
    }

    /** Videos → Device → (rescan) → the clip; returns without waiting for a frame. */
    private fun openClip() {
        wait("Videos tab") { FirstRun.hasNav(rule, "nav_tab_videos") } // TV: rail
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
        FirstRun.openClip(rule, title)
    }

    private fun decoderLog(): String = FirstRun.shell("logcat -d -t 400 -s MediaCodecRenderer:* YumeiroRenderers:*")
        .lines().filter { "fault" in it || "Failed to initialize" in it || "decoder" in it.lowercase() }.takeLast(8).joinToString(" | ")

    @Test fun a_autoSwitchesToSoftwareAndPlays() {
        VideoPrefs.setDecoder(ctx, VideoPrefs.Decoder.AUTO)
        DecoderFaults.mode = DecoderFaults.Mode.PHONE_ORDER_FAILS
        val sw0 = DecoderAuto.switches
        openClip()
        wait("first frame after Auto switched to software", 60_000) { PlaybackDiagnostics.state.value.firstFrame }
        val name = onPlayer { it.videoFormat?.sampleMimeType } ?: "?"
        log("a: offered=${DecoderFaults.offered.get()} switches=${DecoderAuto.switches - sw0} used=${DecoderAuto.used} mime=$name state=${PlaybackDiagnostics.state.value} log=${decoderLog()}")
        FirstRun.shot("grouph_decoder_auto")
        assertTrue("the failing phone decoder was offered", DecoderFaults.offered.get() > 0)
        assertTrue("Auto switched exactly once (${DecoderAuto.switches - sw0})", DecoderAuto.switches - sw0 == 1)
        assertTrue("software order used", DecoderAuto.used > 0)
        assertTrue("no error card", PlaybackDiagnostics.state.value.error == null && !hasText("can't decode"))
        log("GROUPH_DECODER_A_RESULT PASS")
    }

    @Test fun b_hardwarePlusOnTheSameFaultEndsOnTheErrorCard() {
        VideoPrefs.setDecoder(ctx, VideoPrefs.Decoder.HARDWARE_PLUS)
        DecoderFaults.mode = DecoderFaults.Mode.PHONE_ORDER_FAILS
        val sw0 = DecoderAuto.switches
        openClip()
        wait("decode error card", 60_000) { hasText("can't decode") }
        log("b: offered=${DecoderFaults.offered.get()} switches=${DecoderAuto.switches - sw0} state=${PlaybackDiagnostics.state.value}")
        assertTrue("Hardware+ never switches", DecoderAuto.switches == sw0)
        assertTrue("no frame was drawn", !PlaybackDiagnostics.state.value.firstFrame)
        log("GROUPH_DECODER_B_RESULT PASS")
    }

    private fun <T> onPlayer(read: (androidx.media3.exoplayer.ExoPlayer) -> T): T? {
        var out: T? = null
        rule.runOnUiThread { PlaybackSession.playerForTest?.let { out = read(it) } }
        return out
    }

    companion object { const val NAME = "yumeiro_grouph_clip.mp4" }
}
