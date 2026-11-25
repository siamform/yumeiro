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
import dev.asfakul.yumeiro.player.DynamicRangeCompressor
import dev.asfakul.yumeiro.player.PlaybackDiagnostics
import dev.asfakul.yumeiro.player.PlaybackSession
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * GROUP H-1 (owner item 1): the dynamic range compressor in the video player, through the real UI:
 * the test clip plays, the person opens Audio and taps Night; the compressor starts working on the
 * playing sound at once (frames counted in the audio sink), the video keeps playing (position moves,
 * no re-prepare), and Off stops it again. The choice is kept for the next video.
 * Log tag YuGroupH; GROUPH_DRC_RESULT PASS is the evidence.
 */
@RunWith(AndroidJUnit4::class)
class GroupHDrcDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val inst get() = InstrumentationRegistry.getInstrumentation()

    private fun log(s: String) = Log.i("YuGroupH", s)
    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun hasText(t: String) = rule.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun wait(what: String, ms: Long = 30_000, cond: () -> Boolean) {
        val ok = runCatching { rule.waitUntil(ms) { cond() }; true }.getOrDefault(false)
        if (!ok) FirstRun.shot("grouph_drc_${what.replace(' ', '_')}")
        assertTrue("$what (after ${ms}ms) · playback=${PlaybackDiagnostics.state.value} · ${FirstRun.onScreen(rule)}", ok)
    }
    private fun pause(ms: Long) { val until = System.currentTimeMillis() + ms; runCatching { rule.waitUntil(ms + 2_000) { System.currentTimeMillis() > until } } }
    private fun tap(tag: String) = FirstRun.tapVisible(rule, tag)
    private fun back() = FirstRun.shell("input keyevent 4")
    private var clip: Uri? = null

    @Before fun setUp() {
        listOf("android.permission.READ_MEDIA_VIDEO", "android.permission.READ_EXTERNAL_STORAGE", "android.permission.POST_NOTIFICATIONS")
            .forEach { runCatching { inst.uiAutomation.grantRuntimePermission(ctx.packageName, it) } }
        clip = TestClip.put(ctx, NAME, asset = "yumeiro_test_clip.mp4")
        rule.runOnUiThread { PlaybackSession.releaseNow() }
        DynamicRangeCompressor.setPreset(ctx, DynamicRangeCompressor.Preset.OFF)
        FirstRun.toTabs(rule)
        PlaybackDiagnostics.reset("")
    }

    @After fun tearDown() {
        DynamicRangeCompressor.setPreset(ctx, DynamicRangeCompressor.Preset.OFF)
        runCatching { rule.runOnUiThread { PlaybackSession.releaseNow() } }
        clip?.let { TestClip.remove(ctx, it, NAME) }
    }

    private fun control(tag: String) {
        repeat(5) {
            if (!has(tag) && !has("${tag}_menu") && !has("video_more_btn")) runCatching { rule.onAllNodesWithTag("video_player_root", true)[0].performClick() }
            if (runCatching { rule.waitUntil(3_000) { has(tag) || has("video_more_btn") }; true }.getOrDefault(false)) {
                if (has(tag)) { rule.onAllNodesWithTag(tag, true)[0].performClick(); return }
                if (!has("${tag}_menu")) runCatching { rule.onAllNodesWithTag("video_more_btn", true)[0].performClick() }
                if (runCatching { rule.waitUntil(3_000) { has("${tag}_menu") }; true }.getOrDefault(false)) {
                    rule.onAllNodesWithTag("${tag}_menu", true)[0].performClick(); return
                }
                if (has("video_more_menu")) back()
            }
        }
        throw AssertionError("control '$tag' never showed · ${FirstRun.onScreen(rule)}")
    }

    private fun position(): Long { var p = -1L; rule.runOnUiThread { p = PlaybackSession.playerForTest?.currentPosition ?: -1L }; return p }
    private fun playing(): Boolean { var p = false; rule.runOnUiThread { p = PlaybackSession.playerForTest?.isPlaying == true }; return p }

    @Test fun nightCompressesThePlayingSoundAndOffStopsIt() {
        wait("Videos tab") { FirstRun.hasNav(rule, "nav_tab_videos") }
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
        wait("first frame", 60_000) { PlaybackDiagnostics.state.value.firstFrame }
        rule.runOnUiThread { PlaybackSession.playerForTest?.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE }
        pause(1_500)
        val offFrames = DynamicRangeCompressor.framesCompressed
        pause(1_500)
        assertTrue("Off: nothing is compressed", DynamicRangeCompressor.framesCompressed == offFrames)

        control("video_audio_track_btn")
        wait("compressor row in Audio", 8_000) { has("drc_preset_night") }
        FirstRun.shot("grouph_drc_audio_dialog")
        tap("drc_preset_night")
        val pos0 = position()
        val f0 = DynamicRangeCompressor.framesCompressed
        wait("Night compresses the playing sound", 10_000) { DynamicRangeCompressor.framesCompressed > f0 + 24_000 }
        val pos1 = position()
        log("drc night frames=${DynamicRangeCompressor.framesCompressed - f0} reduction=${DynamicRangeCompressor.lastReductionDb} pos $pos0 -> $pos1 playing=${playing()}")
        assertTrue("Night is saved", DynamicRangeCompressor.preset.value == DynamicRangeCompressor.Preset.NIGHT)
        assertTrue("the video kept playing ($pos0 -> $pos1)", playing() && (pos1 != pos0))
        FirstRun.shot("grouph_drc_night_on")

        tap("drc_preset_off")
        pause(800)
        val f1 = DynamicRangeCompressor.framesCompressed
        pause(1_500)
        log("drc off frames after=${DynamicRangeCompressor.framesCompressed - f1} playing=${playing()}")
        assertTrue("Off stops the compressor", DynamicRangeCompressor.framesCompressed == f1)
        assertTrue("still playing", playing())
        back()
        log("GROUPH_DRC_RESULT PASS")
    }

    companion object { const val NAME = "yumeiro_grouph_drc_clip.mp4" }
}
