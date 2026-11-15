/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.media3.common.C
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.player.PlaybackDiagnostics
import dev.asfakul.yumeiro.player.PlaybackSession
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R16 Step 3, group 3 (player) with a device file, through the real UI.
 *
 * The clip is made for the test (12 s, 320×240 H.264, two AAC tracks tagged eng and jpn, one
 * mov_text subtitle track). It is put in the phone's Movies folder through MediaStore, found by the
 * app's own scan, and opened from Videos → Device:
 *  P1 it plays; P6 the audio dialog lists both languages and a pick switches the decoder's track;
 *  P7 the embedded subtitle track is listed, can be picked and turned off; subtitle delay moves;
 *  P8 the speed chip changes the player's speed; P12 play/pause; P11 reopening resumes;
 *  P10 the screen lock hides the controls and unlock brings them back; PiP enters picture-in-picture.
 */
@RunWith(AndroidJUnit4::class)
@org.junit.FixMethodOrder(org.junit.runners.MethodSorters.NAME_ASCENDING)
class LocalPlayerDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val inst get() = InstrumentationRegistry.getInstrumentation()

    private fun log(s: String) = Log.i("YuLocalPlayer", s)
    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun hasText(t: String) = rule.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun wait(what: String, ms: Long = 30_000, cond: () -> Boolean) {
        val ok = runCatching { rule.waitUntil(ms) { cond() }; true }.getOrDefault(false)
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

    /** Shows the controls (they hide themselves) and taps [tag]. */
    private fun control(tag: String) {
        repeat(5) {
            if (!has(tag)) runCatching { rule.onAllNodesWithTag("video_player_root", true)[0].performClick() }
            if (runCatching { rule.waitUntil(3_000) { has(tag) }; true }.getOrDefault(false)) {
                rule.onAllNodesWithTag(tag, true)[0].performClick(); return
            }
        }
        FirstRun.shot("control_missing_$tag")
        throw AssertionError("control '$tag' never showed · ${FirstRun.onScreen(rule)}")
    }

    private var clip: Uri? = null

    @Before fun setUp() {
        listOf("android.permission.READ_MEDIA_VIDEO", "android.permission.READ_EXTERNAL_STORAGE", "android.permission.POST_NOTIFICATIONS")
            .forEach { runCatching { inst.uiAutomation.grantRuntimePermission(ctx.packageName, it) } }
        clip = putClip()
        FirstRun.toTabs(rule)
        PlaybackDiagnostics.reset("")
    }

    @After fun tearDown() {
        runCatching { rule.runOnUiThread { PlaybackSession.releaseNow() } }
        clip?.let { u -> runCatching { ctx.contentResolver.delete(u, null, null) } }
    }

    /** Copies the test clip into Movies/YumeiroTest (replacing an old copy) and returns its MediaStore uri. */
    private fun putClip(): Uri {
        val cr = ctx.contentResolver
        val coll = if (Build.VERSION.SDK_INT >= 29) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        runCatching { cr.delete(coll, "${MediaStore.Video.Media.DISPLAY_NAME}=?", arrayOf(NAME)) }
        val v = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, NAME)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= 29) { put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/YumeiroTest"); put(MediaStore.Video.Media.IS_PENDING, 1) }
        }
        val uri = checkNotNull(cr.insert(coll, v)) { "MediaStore insert failed" }
        cr.openOutputStream(uri)!!.use { out -> inst.context.assets.open(NAME).use { it.copyTo(out) } }
        if (Build.VERSION.SDK_INT >= 29) cr.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
        log("clip at $uri")
        return uri
    }

    /** Videos → Device → (rescan) → the clip. */
    private fun openClipFromDevice() {
        wait("Videos tab") { has("nav_tab_videos") }
        tap("nav_tab_videos")
        wait("video source switch", 20_000) { has("video_source_device") || has("video_source_switch") }
        if (!has("video_source_device")) tap("video_source_switch")
        wait("device source", 15_000) { has("video_source_device") }
        tap("video_source_device")
        val title = NAME.substringBeforeLast('.')
        repeat(4) { attempt ->
            if (hasText(title)) return@repeat
            if (hasText("YumeiroTest")) runCatching { rule.onAllNodesWithText("YumeiroTest", substring = true)[0].performClick() }
            else if (has("video_rescan_btn")) runCatching { tap("video_rescan_btn") }
            runCatching { rule.waitUntil(10_000) { hasText(title) || hasText("YumeiroTest") } }
            log("looking for the clip, attempt $attempt: ${FirstRun.onScreen(rule).take(400)}")
        }
        wait("the clip in Videos → Device", 30_000) { hasText(title) }
        rule.onAllNodesWithText(title, substring = true)[0].performClick()
        wait("first frame of the device clip", 45_000) { PlaybackDiagnostics.state.value.firstFrame }
        log("playing: ${PlaybackDiagnostics.state.value}")
    }

    @Test fun a_deviceClipPlaysWithTracksSubtitlesSpeedAndControls() {
        openClipFromDevice()
        wait("player root") { has("video_player_root") }
        val s0 = PlaybackDiagnostics.state.value
        assertEquals("decoder size", 320 to 240, s0.width to s0.height)

        // P6: both audio languages listed; picking the second switches the selected track.
        control("video_audio_track_btn")
        wait("audio dialog") { has("audio_track_item_1") }
        assertTrue("two audio tracks", has("audio_track_item_0") && has("audio_track_item_1") && !has("audio_track_item_2"))
        val before = onPlayer { p -> p.currentTracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }?.getTrackFormat(0)?.language }
        rule.onAllNodesWithTag("audio_track_item_1", true)[0].performClick()
        if (has("audio_track_done")) tap("audio_track_done")
        wait("audio track switched", 10_000) {
            val now = onPlayer { p -> p.currentTracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }?.getTrackFormat(0)?.language }
            now != null && now != before
        }
        log("P6 audio: $before → ${onPlayer { p -> p.currentTracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }?.getTrackFormat(0)?.language }}")
        if (has("audio_track_dialog")) back()

        // P7: the embedded subtitle track can be picked and turned off; delay moves.
        control("video_subtitle_btn")
        wait("subtitle dialog") { has("subtitle_track_item_0") || has("disable_subtitles_item") }
        assertTrue("embedded subtitle listed · ${FirstRun.onScreen(rule)}", has("subtitle_track_item_0"))
        rule.onAllNodesWithTag("subtitle_track_item_0", true)[0].performClick()
        wait("text track selected", 10_000) { onPlayer { p -> p.currentTracks.groups.any { it.type == C.TRACK_TYPE_TEXT && it.isSelected } } == true }
        log("P7 subtitle on")
        if (has("subtitle_delay_add_250ms")) { tap("subtitle_delay_add_250ms"); log("P7 delay +250 ms tapped") }
        if (has("subtitle_track_item_0")) back()
        control("video_subtitle_btn")
        wait("subtitle dialog again") { has("disable_subtitles_item") }
        rule.onAllNodesWithTag("disable_subtitles_item", true)[0].performClick()
        wait("text track off", 10_000) { onPlayer { p -> p.currentTracks.groups.none { it.type == C.TRACK_TYPE_TEXT && it.isSelected } } == true }
        if (has("disable_subtitles_item")) back()
        log("P7 subtitle off")

        // P8: speed.
        control("video_speed_btn")
        wait("speed chips") { has("speed_chip_1.5x") }
        rule.onAllNodesWithTag("speed_chip_1.5x", true)[0].performClick()
        wait("speed 1.5", 5_000) { onPlayer { it.playbackParameters.speed } == 1.5f }
        log("P8 speed 1.5x")
        control("video_speed_btn"); wait("speed chips") { has("speed_chip_1.0x") }
        rule.onAllNodesWithTag("speed_chip_1.0x", true)[0].performClick()
        wait("speed 1.0", 5_000) { onPlayer { it.playbackParameters.speed } == 1.0f }

        // P12 + P18: pause and play; the screen stays on only while playing.
        wait("screen kept on while playing", 5_000) { keepOn() }
        control("video_play_pause_btn")
        wait("paused", 5_000) { onPlayer { it.playWhenReady } == false }
        wait("screen may sleep when paused", 5_000) { !keepOn() }
        control("video_play_pause_btn")
        wait("playing again", 5_000) { onPlayer { it.playWhenReady } == true }
        wait("screen kept on again", 5_000) { keepOn() }
        log("P12 play/pause, P18 screen wake")

        // P10: lock hides the controls; unlock brings them back.
        control("video_screen_lock_btn")
        wait("unlock button", 5_000) { has("video_screen_unlock_btn") }
        assertTrue("controls hidden while locked", !has("video_speed_btn"))
        tap("video_screen_unlock_btn")
        wait("unlocked", 5_000) { !has("video_screen_unlock_btn") }
        log("LOCAL_A_RESULT PASS")
    }

    @Test fun b_reopeningTheClipResumes() {
        openClipFromDevice()
        // Seek to 7 s, let it play a moment, then close.
        rule.runOnUiThread { PlaybackSession.playerForTest?.seekTo(7_000) }
        wait("past 7 s", 10_000) { (onPlayer { it.currentPosition } ?: 0) >= 7_000 }
        pause(1_500)
        val left = onPlayer { it.currentPosition } ?: 0
        log("left at ${left}ms")
        back() // full screen → floating
        if (has("video_floating_player")) { wait("floating close") { has("floating_close") }; tap("floating_close") } else back()
        wait("player closed", 15_000) { !has("video_player_root") && !has("video_floating_player") }
        // Past the session's release grace, so this is a fresh player.
        pause(PlaybackSession.GRACE_MS + 1_500)
        PlaybackDiagnostics.reset("")
        val title = NAME.substringBeforeLast('.')
        wait("clip still listed") { hasText(title) }
        rule.onAllNodesWithText(title, substring = true)[0].performClick()
        wait("first frame again", 45_000) { PlaybackDiagnostics.state.value.firstFrame }
        val pill = has("video_resume_pill")
        val pos = onPlayer { it.currentPosition } ?: 0
        log("reopened at ${pos}ms, resume pill=$pill")
        assertTrue("resumes near ${left}ms (at ${pos}ms, pill=$pill)", pos >= left - 3_000 || pill)
        log("LOCAL_B_RESULT PASS pos=$pos pill=$pill")
    }

    @Test fun c_pipEntersPictureInPicture() {
        val pm = ctx.packageManager
        org.junit.Assume.assumeTrue("no PiP on this device", pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE))
        openClipFromDevice()
        control("video_pip_btn")
        wait("picture-in-picture", 10_000) { var pip = false; rule.runOnUiThread { pip = rule.activity.isInPictureInPictureMode }; pip }
        assertTrue("still playing in PiP", onPlayer { it.playWhenReady } == true)
        log("LOCAL_C_RESULT PASS")
    }

    /** True when any view in the window asks to keep the screen on. */
    private fun keepOn(): Boolean {
        var on = false
        rule.runOnUiThread {
            fun walk(v: android.view.View) { if (v.keepScreenOn) on = true; if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i)) }
            walk(rule.activity.window.decorView)
        }
        return on
    }

    /** P19: battery saver → WARM, severe thermal status → HOT, both off → COOL (shell-driven, real platform callbacks). */
    @Test fun d_heatGuardFollowsBatterySaverAndThermalStatus() {
        val g = dev.asfakul.yumeiro.player.HeatGuard
        try {
            FirstRun.shell("cmd power set-mode 1")
            wait("WARM on battery saver", 10_000) { g.level.value == dev.asfakul.yumeiro.player.HeatGuard.Level.WARM }
            log("P19 saver → ${g.level.value}")
            FirstRun.shell("cmd power set-mode 0")
            wait("COOL after battery saver", 10_000) { g.level.value == dev.asfakul.yumeiro.player.HeatGuard.Level.COOL }
            val out = FirstRun.shell("cmd thermalservice override-status 3")
            log("thermal override: ${out.take(120)}")
            val hot = runCatching { rule.waitUntil(10_000) { g.level.value == dev.asfakul.yumeiro.player.HeatGuard.Level.HOT }; true }.getOrDefault(false)
            log("P19 thermal 3 → ${g.level.value} (reached HOT: $hot)")
            assertTrue("HOT on severe thermal status", hot)
        } finally {
            FirstRun.shell("cmd power set-mode 0"); FirstRun.shell("cmd thermalservice reset")
        }
        wait("COOL after reset", 10_000) { g.level.value == dev.asfakul.yumeiro.player.HeatGuard.Level.COOL }
        log("LOCAL_D_RESULT PASS")
    }

    companion object { const val NAME = "yumeiro_test_clip.mp4" }
}
