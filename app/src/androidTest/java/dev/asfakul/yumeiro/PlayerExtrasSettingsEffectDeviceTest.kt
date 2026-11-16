/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.media3.common.C
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.player.PipController
import dev.asfakul.yumeiro.player.PlaybackDiagnostics
import dev.asfakul.yumeiro.player.PlaybackSession
import dev.asfakul.yumeiro.player.PlayerSettings
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G9 S1–S22: real effect of the player switches in Settings › Player that had no test
 * (docs/evidence/round3/settings-effects.md). Set through the setters the rows call, then read from
 * the real player opened on a device clip (Videos → Device):
 *  a Captions on by default (`toggle_cc_default`): off → the player starts with the text track type
 *    disabled; on → not disabled (a fresh player for each, past the session grace);
 *  b Background play (`toggle_bg_play`), PiP off: off → Home pauses the video; on → it keeps playing;
 *  c Picture-in-picture (`toggle_auto_pip`), background play off: on → Home puts the playing video in
 *    PiP and it keeps playing; off → no PiP and it pauses.
 * Every value is put back. Log tag YuPlayerXFx: PXF_CASE lines, PXF_RESULT PASS per test.
 */
@RunWith(AndroidJUnit4::class)
@org.junit.FixMethodOrder(org.junit.runners.MethodSorters.NAME_ASCENDING)
class PlayerExtrasSettingsEffectDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val inst get() = InstrumentationRegistry.getInstrumentation()

    private fun log(s: String) = Log.i("YuPlayerXFx", s)
    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun hasText(t: String) = rule.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun wait(what: String, ms: Long = 30_000, cond: () -> Boolean) {
        val ok = runCatching { rule.waitUntil(ms) { cond() }; true }.getOrDefault(false)
        if (!ok) FirstRun.shot("pxf_fail_" + what.filter { it.isLetterOrDigit() }.take(30))
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
    /** Polls with plain sleeps: after Home the activity is stopped and Compose idling is not meaningful. */
    private fun pollOff(ms: Long, cond: () -> Boolean): Boolean {
        val until = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < until) { if (cond()) return true; Thread.sleep(250) }
        return cond()
    }
    private fun inPip(): Boolean {
        var pip = false
        rule.runOnUiThread { pip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && rule.activity.isInPictureInPictureMode }
        return pip
    }

    private var clip: Uri? = null
    private val title get() = NAME.substringBeforeLast('.')
    private var saved: Triple<Boolean, Boolean, Boolean>? = null

    @Before fun setUp() {
        listOf("android.permission.READ_MEDIA_VIDEO", "android.permission.READ_EXTERNAL_STORAGE", "android.permission.POST_NOTIFICATIONS")
            .forEach { runCatching { inst.uiAutomation.grantRuntimePermission(ctx.packageName, it) } }
        PlayerSettings.init(ctx)
        saved = Triple(PlayerSettings.captionsDefault.value, PlayerSettings.backgroundPlay.value, PlayerSettings.autoPip.value)
        clip = TestClip.put(ctx, NAME)
        FirstRun.toTabs(rule)
        PlaybackDiagnostics.reset("")
    }

    @After fun tearDown() {
        runCatching { rule.runOnUiThread { PlaybackSession.releaseNow() } }
        saved?.let { (cc, bg, pip) ->
            PlayerSettings.setCaptionsDefault(ctx, cc); PlayerSettings.setBackgroundPlay(ctx, bg); PlayerSettings.setAutoPip(ctx, pip)
        }
        clip?.let { TestClip.remove(ctx, it, NAME) }
    }

    /** Videos → Device → (rescan) → the clip, until the first frame. Same path as PlayerSettingsEffectDeviceTest. */
    private fun openClip() {
        if (!hasText(title)) {
            wait("Videos tab") { has("nav_tab_videos") }
            tap("nav_tab_videos")
            wait("video source switch", 20_000) { has("video_source_device") || has("video_source_switch") }
            if (!has("video_source_device")) tap("video_source_switch")
            wait("device source", 15_000) { has("video_source_device") }
            tap("video_source_device")
            repeat(4) {
                if (hasText(title)) return@repeat
                if (hasText("YumeiroTest")) runCatching { rule.onAllNodesWithText("YumeiroTest", substring = true)[0].performClick() }
                else if (has("video_rescan_btn")) runCatching { tap("video_rescan_btn") }
                runCatching { rule.waitUntil(10_000) { hasText(title) || hasText("YumeiroTest") } }
            }
        }
        wait("the clip in Videos → Device", 30_000) { hasText(title) }
        PlaybackDiagnostics.reset("")
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

    private fun textDisabled(): Boolean? = onPlayer { C.TRACK_TYPE_TEXT in it.trackSelectionParameters.disabledTrackTypes }

    @Test fun a_captionsDefaultSetsTheTextTrack() {
        PlayerSettings.setCaptionsDefault(ctx, false)
        openClip()
        val off = textDisabled()
        closePlayer()
        PlayerSettings.setCaptionsDefault(ctx, true)
        openClip()
        val on = textDisabled()
        log("PXF_CASE a captions default off → text disabled=$off · on → text disabled=$on")
        assertTrue("captions off by default: the text track must start disabled (got $off)", off == true)
        assertTrue("captions on by default: the text track must not be disabled (got $on)", on == false)
        log("PXF_RESULT PASS a")
    }

    /** Opens the clip, makes sure it plays, presses Home, and reports (playing, inPip) after [ms]. */
    private fun homeAndRead(ms: Long = 6_000): Pair<Boolean, Boolean> {
        openClip()
        rule.runOnUiThread { PlaybackSession.playerForTest?.playWhenReady = true }
        wait("playing before Home", 10_000) { onPlayer { it.isPlaying } == true }
        pause(1_000)
        FirstRun.shell("input keyevent 3")
        Thread.sleep(ms)
        val playing = onPlayer { it.isPlaying } == true
        val pip = inPip()
        FirstRun.shot("pxf_after_home")
        return playing to pip
    }

    @Test fun b1_backgroundPlayOffPausesOnHome() {
        PlayerSettings.setAutoPip(ctx, false)
        PlayerSettings.setBackgroundPlay(ctx, false)
        val (playing, pip) = homeAndRead()
        log("PXF_CASE b1 background play off, PiP off: after Home playing=$playing pip=$pip")
        assertFalse("PiP is off: Home must not enter PiP", pip)
        assertFalse("background play off: Home must pause the video", playing)
        log("PXF_RESULT PASS b1")
    }

    @Test fun b2_backgroundPlayOnKeepsPlayingOnHome() {
        PlayerSettings.setAutoPip(ctx, false)
        PlayerSettings.setBackgroundPlay(ctx, true)
        val (playing, pip) = homeAndRead()
        log("PXF_CASE b2 background play on, PiP off: after Home playing=$playing pip=$pip")
        assertFalse("PiP is off: Home must not enter PiP", pip)
        assertTrue("background play on: the video must keep playing after Home", playing)
        log("PXF_RESULT PASS b2")
    }

    @Test fun c1_autoPipOnEntersPipOnHome() {
        assumeTrue("this device has no picture-in-picture", PipController.isSupported(ctx))
        PlayerSettings.setBackgroundPlay(ctx, false)
        PlayerSettings.setAutoPip(ctx, true)
        val (playing, pip) = homeAndRead(8_000)
        val pipLate = pip || pollOff(4_000) { inPip() }
        log("PXF_CASE c1 PiP on, background play off: after Home pip=$pipLate playing=${onPlayer { it.isPlaying }} (first read playing=$playing)")
        assertTrue("PiP on: Home must put the playing video in picture-in-picture", pipLate)
        assertTrue("in PiP the video must keep playing", onPlayer { it.isPlaying } == true)
        log("PXF_RESULT PASS c1")
    }

    @Test fun c2_autoPipOffStaysOutOfPip() {
        PlayerSettings.setBackgroundPlay(ctx, false)
        PlayerSettings.setAutoPip(ctx, false)
        val (playing, pip) = homeAndRead()
        log("PXF_CASE c2 PiP off, background play off: after Home pip=$pip playing=$playing")
        assertFalse("PiP off: Home must not enter picture-in-picture", pip)
        assertFalse("PiP and background play off: the video pauses", playing)
        log("PXF_RESULT PASS c2")
    }

    companion object { const val NAME = "yumeiro_test_clip.mp4" }
}
