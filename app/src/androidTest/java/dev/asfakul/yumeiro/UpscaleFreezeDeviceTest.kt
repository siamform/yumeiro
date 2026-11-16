/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.player.HeatGuard
import dev.asfakul.yumeiro.player.PlaybackDiagnostics
import dev.asfakul.yumeiro.player.PlaybackSession
import dev.asfakul.yumeiro.player.VideoPrefs
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * BUG-7 7d (owner): turning on or using Yumeiro's resolution scaling froze the app (buttons dead).
 *  a Anime4K on (Settings → Quality and audio → Anime4K enhancement, `VideoPrefs.setAnime4k`), then
 *    a video opens: first frame, playback moves, main thread never stalls ≥ 2 s, Back closes it.
 *  b Upscaled level picked in the player's Quality list ("Upscaled by Yumeiro (Anime4K)", the 240p
 *    clip offers 360p): same checks after the pick, then back to the original level.
 * Freeze probe: [MainStallWatch] (FMT_STALL lines with the main stack). Log tag YuUpscale:
 * UPS_CASE / UPS_RESULT lines, screenshots ups_*.
 */
@RunWith(AndroidJUnit4::class)
class UpscaleFreezeDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = inst.targetContext
    private fun log(s: String) = Log.i("YuUpscale", s)
    private fun has(tag: String) = runCatching { rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    private val watch = MainStallWatch("YuUpscale")
    private var clip: Uri? = null
    private val anime4kBefore = VideoPrefs.anime4k.value
    private val factoryBefore = PlaybackSession.factory
    @Volatile private var player: androidx.media3.exoplayer.ExoPlayer? = null

    @Before fun setUp() {
        listOf("android.permission.READ_MEDIA_VIDEO", "android.permission.READ_EXTERNAL_STORAGE")
            .forEach { runCatching { inst.uiAutomation.grantRuntimePermission(ctx.packageName, it) } }
        FirstRun.toTabs(rule)
        runCatching { rule.waitUntil(20_000) { has("home_screen_root") } }
        assumeTrue("HeatGuard blocks GPU upscaling on this run (${HeatGuard.level.value})", HeatGuard.allowGpuUpscale())
        // The player the app creates, to read its real position (PlaybackDiagnostics.positionMs only
        // changes with the playback state).
        PlaybackSession.factory = { c, uri, now -> factoryBefore(c, uri, now).also { player = it } }
        watch.start()
    }

    @After fun tearDown() {
        watch.stop()
        runCatching { rule.runOnUiThread { PlaybackSession.releaseNow() } }
        PlaybackSession.factory = factoryBefore
        VideoPrefs.setAnime4k(ctx, anime4kBefore)
        clip?.let { TestClip.remove(ctx, it, NAME) }
    }

    private fun openClip() {
        clip = TestClip.put(ctx, NAME)
        PlaybackDiagnostics.reset("")
        FirstRun.shell("am start -a android.intent.action.VIEW -d $clip -t video/mp4 -n ${ctx.packageName}/dev.asfakul.yumeiro.MainActivity")
    }

    private fun positionMs(): Long {
        var pos = -1L
        runCatching { rule.runOnUiThread { pos = player?.currentPosition ?: -1L } }
        return pos
    }

    /**
     * Main thread never stalled, playback moved over [ms], the window kept drawing and the time label
     * kept moving (test clock pumped, see [UiLiveness.pump]), a real tap on More opens its menu, and
     * the player can be left and closed.
     */
    private fun checkAlive(name: String, ms: Long) {
        val f0 = UiLiveness.framesDrawn(ctx.packageName)
        val p0 = positionMs()
        UiLiveness.pump(rule, ms)
        val p1 = positionMs()
        val f1 = UiLiveness.framesDrawn(ctx.packageName)
        val snap = PlaybackDiagnostics.state.value
        val dialogOpen = rule.onAllNodes(hasText("Upscaled by Yumeiro", substring = true)).fetchSemanticsNodes().isNotEmpty()
        FirstRun.shot("ups_$name")
        // Real touches: show the controls if hidden (auto-hide is 3.5 s), read the time label twice
        // 2 s apart, then More must open its menu.
        if (!has("video_more_btn")) { UiLiveness.tap(rule, "video_player_root"); UiLiveness.pump(rule, 500) }
        val t0 = UiLiveness.timeLabels(rule); UiLiveness.pump(rule, 2_000); val t1 = UiLiveness.timeLabels(rule)
        // Run 43935805199: the controls auto-hid (3.5 s) between the check and the tap, so the tap
        // landed on a fading button. Show them fresh, wait for More, tap; one retry.
        var tappedMore = false; var menu = false
        repeat(2) {
            if (menu) return@repeat
            if (!has("video_more_btn")) { UiLiveness.tap(rule, "video_player_root"); runCatching { rule.waitUntil(2_000) { has("video_more_btn") } } }
            tappedMore = UiLiveness.tap(rule, "video_more_btn")
            menu = tappedMore && runCatching { rule.waitUntil(3_000) { has("video_more_menu") }; true }.getOrDefault(false)
        }
        if (menu) { FirstRun.shell("input keyevent KEYCODE_BACK"); UiLiveness.pump(rule, 600) }
        val (closed, how) = UiLiveness.leaveAndClose(rule)
        val lag = watch.maxLag.get()
        log("UPS_CASE $name pos=$p0→$p1 frames=$f0→$f1 time='$t0'→'$t1' dialogOpen=$dialogOpen moreMenu=$menu (tapped=$tappedMore) state=${snap.state} firstFrame=${snap.firstFrame} ${snap.width}x${snap.height} error=${snap.error?.take(160)} maxLagMs=$lag $how stall=${watch.firstStall.get()?.take(600)}")
        assertTrue("7d $name: the main thread stopped for $lag ms (freeze): ${watch.firstStall.get()}", lag < 2_000)
        assertTrue("7d $name: playback did not move ($p0 → $p1, ${snap.state}, error=${snap.error})", p1 > p0 || snap.state == "ENDED")
        assertTrue("7d $name: the window drew no frame in ${ms} ms ($f0 → $f1)", f0 < 0 || f1 > f0)
        assertTrue("7d $name: the time label did not move ('$t0' → '$t1')", t0.isNotBlank() && t0 != t1)
        assertTrue("7d $name: the Quality list stayed open after picking a level", !dialogOpen)
        assertTrue("7d $name: a real tap on More did not open its menu", menu)
        assertTrue("7d $name: the app did not answer Back / the close button ($how)", closed)
        log("UPS_RESULT PASS $name")
    }

    @Test fun a0_plainPlaybackControl() {
        VideoPrefs.setAnime4k(ctx, false)
        watch.reset()
        openClip()
        assertTrue("first frame", runCatching { rule.waitUntil(45_000) { PlaybackDiagnostics.state.value.firstFrame }; true }.getOrDefault(false))
        checkAlive("plain_control", 4_000)
    }

    @Test fun a_anime4kOnThenOpenVideo() {
        VideoPrefs.setAnime4k(ctx, true)
        watch.reset()
        openClip()
        val ok = runCatching { rule.waitUntil(45_000) { PlaybackDiagnostics.state.value.let { it.firstFrame || it.error != null } }; true }.getOrDefault(false)
        log("UPS_STEP a firstFrame=${PlaybackDiagnostics.state.value.firstFrame} error=${PlaybackDiagnostics.state.value.error} lag=${watch.maxLag.get()}")
        assertTrue("7d a: no first frame and no error within 45 s with Anime4K on (${PlaybackDiagnostics.state.value})", ok)
        checkAlive("anime4k_on", 4_000)
    }

    @Test fun b_upscaledLevelPickedInPlayer() {
        VideoPrefs.setAnime4k(ctx, false)
        openClip()
        assertTrue("first frame", runCatching { rule.waitUntil(45_000) { PlaybackDiagnostics.state.value.firstFrame }; true }.getOrDefault(false))
        UiLiveness.pump(rule, 800)
        if (!has("video_quality_btn")) { FirstRun.shell("input keyevent KEYCODE_DPAD_CENTER"); UiLiveness.pump(rule, 600) }
        if (!has("video_quality_btn")) runCatching { rule.onNodeWithTag("video_player_root", true).performClick() }
        runCatching { rule.waitUntil(5_000) { has("video_quality_btn") } }
        rule.onNodeWithTag("video_quality_btn", true).performClick()
        val upscaled = hasClickAction() and hasText("Upscaled by Yumeiro", substring = true)
        val listed = runCatching { rule.waitUntil(15_000) { rule.onAllNodes(upscaled).fetchSemanticsNodes().isNotEmpty() }; true }.getOrDefault(false)
        log("UPS_STEP b upscaledOffered=$listed screen=${FirstRun.onScreen(rule).take(400)}")
        assumeTrue("the Quality list offered no upscaled level for this clip", listed)
        watch.reset()
        rule.onAllNodes(upscaled)[0].performClick()
        checkAlive("upscaled_level", 6_000)
    }

    companion object { const val NAME = "yumeiro_test_clip.mp4" }
}
