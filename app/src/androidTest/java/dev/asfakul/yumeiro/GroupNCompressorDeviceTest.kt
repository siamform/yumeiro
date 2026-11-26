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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F15 (owner): CloudStream-style compressor controls in the video player, through the real UI. The test clip
 * plays, the person opens Audio, picks Light; the threshold / make-up sliders show Light's values; moving them
 * changes the playing sound at once (frames counted in the audio sink, the video keeps playing), the values are
 * saved, and Reset returns to Night. Log tag YuGroupN; GROUPN_F15_RESULT PASS is the evidence.
 */
@RunWith(AndroidJUnit4::class)
class GroupNCompressorDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val inst get() = InstrumentationRegistry.getInstrumentation()

    private fun log(s: String) = Log.i("YuGroupN", s)
    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun hasText(t: String) = rule.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun wait(what: String, ms: Long = 30_000, cond: () -> Boolean) {
        val ok = runCatching { rule.waitUntil(ms) { cond() }; true }.getOrDefault(false)
        if (!ok) FirstRun.shot("groupn_drc_${what.replace(' ', '_')}")
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
        ctx.getSharedPreferences("yumeiro_drc", Context.MODE_PRIVATE).edit().clear().commit()
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

    private fun slide(tag: String, v: Float) {
        rule.onAllNodesWithTag(tag, true)[0].performSemanticsAction(SemanticsActions.SetProgress) { it(v) }
        rule.waitForIdle()
    }
    private fun text(tag: String): String = runCatching {
        rule.onAllNodesWithTag(tag, true)[0].fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text }
    }.getOrDefault("?")

    @Test fun compressorControlsChangeTheSoundLiveAreSavedAndReset() {
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
        pause(1_000)

        control("video_audio_track_btn")
        wait("compressor row in Audio", 8_000) { has("drc_preset_light") }
        tap("drc_preset_light")
        val light = DynamicRangeCompressor.Preset.LIGHT.settings!!
        // Fail-before: the player showed presets only; the controls lived in Settings.
        wait("compressor controls in the player", 6_000) { has("drc_threshold") && has("drc_makeup") && has("drc_reset") }
        log("F15 light threshold label=${text("drc_threshold_value")} makeup label=${text("drc_makeup_value")}")
        assertTrue("the sliders show Light's values (${text("drc_threshold_value")})", text("drc_threshold_value").startsWith("${light.thresholdDb.toInt()} dB"))
        FirstRun.shot("groupn_drc_controls")

        val pos0 = position()
        val f0 = DynamicRangeCompressor.framesCompressed
        slide("drc_threshold", -40f)
        slide("drc_makeup", 10f)
        val live = DynamicRangeCompressor.active
        log("F15 after slide active=$live preset=${DynamicRangeCompressor.preset.value}")
        assertTrue("threshold live (-40): $live", live != null && live.thresholdDb == -40f)
        assertTrue("make-up live (+10): $live", live != null && live.makeupDb == 10f)
        assertTrue("the rest stays Light's: $live", live != null && live.ratio == light.ratio && live.releaseMs == light.releaseMs)
        wait("the playing sound goes through the new values", 10_000) { DynamicRangeCompressor.framesCompressed > f0 + 24_000 }
        wait("labels follow", 3_000) { text("drc_threshold_value").startsWith("-40 dB") && text("drc_makeup_value").startsWith("+10 dB") }
        val pos1 = position()
        assertTrue("the video kept playing ($pos0 -> $pos1)", playing() && pos1 != pos0)
        val prefs = ctx.getSharedPreferences("yumeiro_drc", Context.MODE_PRIVATE)
        assertTrue("saved: ${prefs.all}", prefs.getString("preset", null) == "CUSTOM" && prefs.getFloat("threshold", 0f) == -40f && prefs.getFloat("makeup", 0f) == 10f)
        FirstRun.shot("groupn_drc_custom")

        tap("drc_reset")
        rule.waitForIdle()
        val night = DynamicRangeCompressor.Preset.NIGHT.settings!!
        log("F15 after reset active=${DynamicRangeCompressor.active} label=${text("drc_threshold_value")}")
        assertTrue("Reset: Night again", DynamicRangeCompressor.preset.value == DynamicRangeCompressor.Preset.NIGHT && DynamicRangeCompressor.active == night)
        wait("Reset label", 3_000) { text("drc_threshold_value").startsWith("${night.thresholdDb.toInt()} dB") }
        assertTrue("Reset saved", prefs.getString("preset", null) == "NIGHT" && prefs.getFloat("threshold", 0f) == night.thresholdDb)
        assertTrue("still playing", playing())
        back()
        log("GROUPN_F15_RESULT PASS")
    }

    companion object { const val NAME = "yumeiro_groupn_drc_clip.mp4" }
}
