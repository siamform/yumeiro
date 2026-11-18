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
import dev.asfakul.yumeiro.player.HeatGuard
import dev.asfakul.yumeiro.player.PlaybackDiagnostics
import dev.asfakul.yumeiro.player.PlaybackSession
import dev.asfakul.yumeiro.player.VideoPrefs
import dev.asfakul.yumeiro.stream.StreamMediaFactory
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R17 G9 (master handover: "Player info overlay", "Quality and audio", real effect of each setting).
 * The switches are set through the same setters the settings rows call, then the real player is
 * opened on a device clip from Videos → Device and the effect is read from the player:
 *  a Player info overlay on → the "player_info_overlay" panel shows with the controls; off → gone
 *    while the video keeps playing (the row says it applies at once);
 *  b Resume playback off → a clip left at 7 s reopens from the start; on → reopens near 7 s;
 *  c Anime4K enhancement on → the player got the Anime4K video effect; off → a fresh player has none.
 * Log tag YuPlayerFx: PFX_CASE lines, PFX_RESULT PASS per test.
 */
@RunWith(AndroidJUnit4::class)
@org.junit.FixMethodOrder(org.junit.runners.MethodSorters.NAME_ASCENDING)
class PlayerSettingsEffectDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val inst get() = InstrumentationRegistry.getInstrumentation()

    private fun log(s: String) = Log.i("YuPlayerFx", s)
    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun hasText(t: String) = rule.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun wait(what: String, ms: Long = 30_000, cond: () -> Boolean) {
        val ok = runCatching { rule.waitUntil(ms) { cond() }; true }.getOrDefault(false)
        if (!ok) FirstRun.shot("pfx_fail_" + what.filter { it.isLetterOrDigit() }.take(30))
        assertTrue("$what (after ${ms}ms) · playback=${PlaybackDiagnostics.state.value} · ${FirstRun.onScreen(rule)}", ok)
    }
    private fun pause(ms: Long) { val until = System.currentTimeMillis() + ms; runCatching { rule.waitUntil(ms + 2_000) { System.currentTimeMillis() > until } } }
    private fun tap(tag: String) = FirstRun.tapVisible(rule, tag)
    private fun back() = FirstRun.shell("input keyevent 4")
    private fun <T> onPlayer(read: (androidx.media3.exoplayer.ExoPlayer) -> T): T? {
        var out: T? = null
        rule.runOnUiThread { PlaybackSession.playerForTest?.let { out = read(it) } }
        return out
    }

    private var clip: Uri? = null
    private val title get() = NAME.substringBeforeLast('.')
    private var saved: Triple<Boolean, Boolean, Boolean>? = null

    @Before fun setUp() {
        listOf("android.permission.READ_MEDIA_VIDEO", "android.permission.READ_EXTERNAL_STORAGE", "android.permission.POST_NOTIFICATIONS")
            .forEach { runCatching { inst.uiAutomation.grantRuntimePermission(ctx.packageName, it) } }
        saved = Triple(VideoPrefs.infoOverlay.value, VideoPrefs.resume.value, VideoPrefs.anime4k.value)
        clip = TestClip.put(ctx, NAME)
        FirstRun.toTabs(rule)
        PlaybackDiagnostics.reset("")
    }

    @After fun tearDown() {
        runCatching { rule.runOnUiThread { PlaybackSession.releaseNow() } }
        saved?.let { (o, r, a) -> VideoPrefs.setInfoOverlay(ctx, o); VideoPrefs.setResume(ctx, r); VideoPrefs.setAnime4k(ctx, a) }
        clip?.let { TestClip.remove(ctx, it, NAME) }
    }

    /** Videos → Device → (rescan) → the clip, until the first frame. */
    private fun openClip() {
        if (!hasText(title)) {
            wait("Videos tab") { FirstRun.hasNav(rule, "nav_tab_videos") } // TV: rail
            tap("nav_tab_videos")
            wait("video source switch", 20_000) { has("video_source_device") || has("video_source_switch") }
            if (!has("video_source_device")) tap("video_source_switch")
            wait("device source", 15_000) { has("video_source_device") }
            tap("video_source_device")
            repeat(4) {
                if (hasText(title)) return@repeat
                if (hasText("YumeiroTest")) runCatching { FirstRun.leaveRail(rule); rule.onAllNodesWithText("YumeiroTest", substring = true)[0].performClick() }
                else if (has("video_rescan_btn")) runCatching { tap("video_rescan_btn") }
                runCatching { rule.waitUntil(10_000) { hasText(title) || hasText("YumeiroTest") } }
            }
        }
        wait("the clip in Videos → Device", 30_000) { hasText(title) }
        PlaybackDiagnostics.reset("")
        FirstRun.leaveRail(rule) // TV: the open rail covers the grid; Right moves focus to the page
        rule.onAllNodesWithText(title, substring = true)[0].performClick()
        wait("first frame", 45_000) { PlaybackDiagnostics.state.value.firstFrame }
        rule.runOnUiThread { PlaybackSession.playerForTest?.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE }
    }

    /** Back to the floating window, close it, wait past the session grace so the next open is a fresh player. */
    private fun closePlayer() {
        var presses = 0
        while (presses < 3 && !has("floating_close")) {
            back(); presses++
            runCatching { rule.waitUntil(4_000) { has("floating_close") } }
        }
        wait("floating player after back", 10_000) { has("floating_close") }
        tap("floating_close")
        wait("player closed", 15_000) { !has("video_player_root") && !has("video_floating_player") }
        pause(PlaybackSession.GRACE_MS + 1_500)
    }

    /** Taps the player (not its centre button) until the controls show. */
    private fun showControls() {
        repeat(4) {
            if (has("video_more_btn") || has("video_speed_btn")) return
            runCatching { rule.onAllNodesWithTag("video_player_root", true)[0].performClick() }
            runCatching { rule.waitUntil(3_000) { has("video_more_btn") || has("video_speed_btn") } }
        }
    }

    @Test fun a_infoOverlayFollowsTheSwitch() {
        VideoPrefs.setInfoOverlay(ctx, true)
        openClip()
        showControls()
        wait("info overlay with the controls (switch on)", 10_000) { has("player_info_overlay") }
        FirstRun.shot("pfx_info_overlay_on")
        log("PFX_CASE overlay on: shown")
        VideoPrefs.setInfoOverlay(ctx, false)
        showControls()
        wait("info overlay gone (switch off)", 5_000) { !has("player_info_overlay") }
        assertTrue("still playing", onPlayer { it.playWhenReady } == true)
        log("PFX_CASE overlay off: hidden, still playing")
        log("PFX_RESULT PASS a")
    }

    /** Leaves the clip at 7 s (paused, so the close is not "finished"), closes, reopens; returns the reopen position. */
    private fun leaveAt7sAndReopen(): Pair<Long, Boolean> {
        rule.runOnUiThread { PlaybackSession.playerForTest?.seekTo(7_000) }
        wait("past 7 s", 10_000) { (onPlayer { it.currentPosition } ?: 0) >= 7_000 }
        pause(1_500)
        rule.runOnUiThread { PlaybackSession.playerForTest?.playWhenReady = false }
        closePlayer()
        openClip()
        pause(800)
        return (onPlayer { it.currentPosition } ?: 0L) to has("video_resume_pill")
    }

    @Test fun b_resumePlaybackFollowsTheSwitch() {
        VideoPrefs.setResume(ctx, false)
        openClip()
        val (offPos, offPill) = leaveAt7sAndReopen()
        log("PFX_CASE resume off: reopened at ${offPos}ms pill=$offPill")
        assertTrue("resume off must start from the beginning (at ${offPos}ms, pill=$offPill)", offPos < 4_000 && !offPill)
        VideoPrefs.setResume(ctx, true)
        val (onPos, onPill) = leaveAt7sAndReopen()
        log("PFX_CASE resume on: reopened at ${onPos}ms pill=$onPill")
        assertTrue("resume on must continue near 7 s (at ${onPos}ms, pill=$onPill)", onPos >= 4_000 || onPill)
        log("PFX_RESULT PASS b")
    }

    @Test fun c_anime4kFollowsTheSwitch() {
        assumeTrue("HeatGuard blocks GPU upscaling on this run (${HeatGuard.level.value})", HeatGuard.allowGpuUpscale())
        VideoPrefs.setAnime4k(ctx, true)
        openClip()
        wait("Anime4K effect attached to the player", 10_000) { onPlayer { StreamMediaFactory.usedEffects(it) } == true }
        pause(2_000)
        assertTrue("still rendering with the effect · ${PlaybackDiagnostics.state.value}", PlaybackDiagnostics.state.value.firstFrame && onPlayer { it.playerError } == null)
        FirstRun.shot("pfx_anime4k_on")
        log("PFX_CASE anime4k on: effect attached, no player error")
        closePlayer()
        VideoPrefs.setAnime4k(ctx, false)
        openClip()
        pause(1_500)
        assertFalse("a fresh player must have no effect with the switch off", onPlayer { StreamMediaFactory.usedEffects(it) } == true)
        log("PFX_CASE anime4k off: no effect")
        log("PFX_RESULT PASS c")
    }

    companion object { const val NAME = "yumeiro_test_clip.mp4" }
}
