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

    /**
     * Shows the controls (they hide themselves) and taps [tag]. R17 PL-16: buttons that don't fit the
     * top bar are in the More menu as "<tag>_menu", so a person opens More and picks the item there.
     */
    private fun control(tag: String) {
        repeat(5) {
            // Only when no control is showing: the centre of the player is the play/pause button,
            // so tapping it while the controls are up pauses the video (43747147539 a and c).
            if (!has(tag) && !has("${tag}_menu") && !has("video_more_btn")) runCatching { rule.onAllNodesWithTag("video_player_root", true)[0].performClick() }
            if (runCatching { rule.waitUntil(3_000) { has(tag) || has("video_more_btn") }; true }.getOrDefault(false)) {
                if (has(tag)) { rule.onAllNodesWithTag(tag, true)[0].performClick(); return }
                if (!has("${tag}_menu")) runCatching { rule.onAllNodesWithTag("video_more_btn", true)[0].performClick() }
                if (runCatching { rule.waitUntil(3_000) { has("${tag}_menu") }; true }.getOrDefault(false)) {
                    log("control $tag is in the More menu")
                    rule.onAllNodesWithTag("${tag}_menu", true)[0].performClick(); return
                }
                if (has("video_more_menu")) back()
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
        clip?.let { u -> TestClip.remove(ctx, u, NAME) }
    }

    /** Copies the test clip into Movies/YumeiroTest (replacing an old copy); see [TestClip]. */
    private fun putClip(): Uri = TestClip.put(ctx, NAME).also { log("clip at $it") }

    /** Videos → Device → (rescan) → the clip. */
    private fun openClipFromDevice() {
        wait("Videos tab") { FirstRun.hasNav(rule, "nav_tab_videos") } // TV: rail
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
        FirstRun.openClip(rule, title)
        wait("first frame of the device clip", 45_000) { PlaybackDiagnostics.state.value.firstFrame }
        log("playing: ${PlaybackDiagnostics.state.value}")
    }

    /** The clip is 12 s; steps that take longer loop it so it never reaches the end screen. */
    private fun loopClip() = rule.runOnUiThread { PlaybackSession.playerForTest?.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE }

    @Test fun a_deviceClipPlaysWithTracksSubtitlesSpeedAndControls() {
        openClipFromDevice()
        loopClip()
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
        // Pause before closing: the clip is 12 s and a close past 95 % counts as finished (the
        // resume point is cleared, MediaRepository.updateVideoProgress), which api36 reached in
        // 43709093938 while walking to the floating window's Close button.
        rule.runOnUiThread { PlaybackSession.playerForTest?.playWhenReady = false }
        val left = onPlayer { it.currentPosition } ?: 0
        log("left at ${left}ms")
        // Back is stepped by design (VideoPlayerModal BackHandler): a key Back first hides the
        // controls (remote behaviour), then leaves full screen, then moves to the floating window.
        var presses = 0
        while (presses < 3 && !has("floating_close")) {
            back(); presses++
            runCatching { rule.waitUntil(4_000) { has("floating_close") } }
            log("back $presses → floating=${has("floating_close")}")
        }
        wait("floating player after back", 10_000) { has("floating_close") }
        tap("floating_close")
        wait("player closed", 15_000) { !has("video_player_root") && !has("video_floating_player") }
        // Past the session's release grace, so this is a fresh player.
        pause(PlaybackSession.GRACE_MS + 1_500)
        PlaybackDiagnostics.reset("")
        val title = NAME.substringBeforeLast('.')
        wait("clip still listed") { hasText(title) }
        FirstRun.openClip(rule, title)
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
        loopClip()
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
            // Battery saver cannot turn on while the emulator reports it is charging.
            val pm = ctx.getSystemService(android.os.PowerManager::class.java)
            // R16 G4: in full suite 43714635144 (api36) the system itself still reported
            // isPowerSaveMode=false after set-mode 1, so HeatGuard rightly stayed COOL and the test
            // failed on the environment. The precondition is now retried and checked first; when the
            // system won't turn battery saver on, that half is reported, not failed.
            var saverOn = false
            for (attempt in 1..3) {
                FirstRun.shell("dumpsys battery unplug")
                val out = FirstRun.shell("cmd power set-mode 1")
                saverOn = runCatching { rule.waitUntil(3_000) { pm.isPowerSaveMode }; true }.getOrDefault(false)
                log("P19 attempt $attempt: set-mode 1 → '${out.trim().take(80)}', isPowerSaveMode=${pm.isPowerSaveMode}")
                if (saverOn) break
            }
            if (saverOn) {
                wait("WARM on battery saver", 10_000) { g.level.value == dev.asfakul.yumeiro.player.HeatGuard.Level.WARM }
                log("P19 saver → ${g.level.value}")
            } else log("P19 SAVER_NOT_ENABLED: the system did not turn battery saver on; saver half not judged")
            FirstRun.shell("cmd power set-mode 0")
            wait("COOL after battery saver", 10_000) { g.level.value == dev.asfakul.yumeiro.player.HeatGuard.Level.COOL }
            // Thermal status is an API 29 platform feature (PowerManager.currentThermalStatus);
            // HeatGuard reads it only there. minSdk run 43768670627 (api28): "cmd thermalservice"
            // does not exist, so the thermal half does not apply below 29.
            if (android.os.Build.VERSION.SDK_INT < 29) { log("P19 THERMAL_NA: API ${android.os.Build.VERSION.SDK_INT} has no thermal status; thermal half not applicable"); return }
            val out = FirstRun.shell("cmd thermalservice override-status 3")
            log("thermal override: ${out.take(120)}")
            val hot = runCatching { rule.waitUntil(10_000) { g.level.value == dev.asfakul.yumeiro.player.HeatGuard.Level.HOT }; true }.getOrDefault(false)
            log("P19 thermal 3 → ${g.level.value} (reached HOT: $hot)")
            assertTrue("HOT on severe thermal status", hot)
        } finally {
            FirstRun.shell("cmd power set-mode 0"); FirstRun.shell("cmd thermalservice reset"); FirstRun.shell("dumpsys battery reset")
        }
        wait("COOL after reset", 10_000) { g.level.value == dev.asfakul.yumeiro.player.HeatGuard.Level.COOL }
        log("LOCAL_D_RESULT PASS")
    }

    /** P17: the Cast module loads without blocking (google_apis emulators have Play Services). Casting to a TV is script P/T. */
    @Test fun e_castModuleLoadsWithoutBlocking() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val t0 = android.os.SystemClock.uptimeMillis()
        var ready: kotlinx.coroutines.flow.StateFlow<Boolean>? = null
        // Called on the main thread, as the Cast button does.
        InstrumentationRegistry.getInstrumentation().runOnMainSync { ready = dev.asfakul.yumeiro.cast.CastSupport.ready(ctx) }
        val callMs = android.os.SystemClock.uptimeMillis() - t0
        log("P17 ready() returned in $callMs ms, value=${ready!!.value}")
        assertTrue("ready() must not block (was $callMs ms)", callMs < 500)
        // The Android TV image has no Cast sender: the module never loads there, and the app hides
        // the Cast button (castOk stays false), which is correct (pass-after 44526618412, tv-api34).
        if (dev.asfakul.yumeiro.Edition.isTv && !runCatching { rule.waitUntil(30_000) { ready!!.value }; true }.getOrDefault(false)) {
            log("P17 TV: Cast module not available, button stays hidden")
            org.junit.Assume.assumeTrue("no Cast sender on this TV image", false)
        }
        wait("Cast module loaded", 30_000) { ready!!.value }
        log("P17 cast ready after ${android.os.SystemClock.uptimeMillis() - t0} ms")
        assertTrue("ready() must not block (was $callMs ms)", callMs < 500)
        log("LOCAL_E_RESULT PASS")
    }

    /**
     * R17 PL-16 + PL-17: the top bar fits its width (no sideways scrolling, nothing clipped) and
     * More holds the sleep timer. A 5-minute timer shows its dot; a 1-minute timer really pauses
     * the video and gives the volume back.
     */
    @Test fun f_moreMenuAndVideoSleepTimer() {
        openClipFromDevice()
        loopClip()
        FirstRun.shot("pl16_top_bar_portrait")
        control("video_more_btn")
        wait("More menu", 5_000) { has("video_more_menu") && has("video_sleep_timer_menu") }
        FirstRun.shot("pl16_more_menu_open")
        rule.onAllNodesWithTag("video_sleep_timer_menu", true)[0].performClick()
        wait("sleep timer sheet", 5_000) { has("sleep_timer_sheet") && has("sleep_5") }
        FirstRun.shot("pl17_sleep_sheet")
        rule.onAllNodesWithTag("sleep_5", true)[0].performClick()
        wait("sleep dot on the More button", 10_000) {
            if (!has("video_sleep_dot") && !has("video_more_btn")) runCatching { rule.onAllNodesWithTag("video_player_root", true)[0].performClick() }
            has("video_sleep_dot")
        }
        val st = dev.asfakul.yumeiro.player.VideoSleepTimer.timer.state.value
        assertTrue("countdown running: $st", st is dev.asfakul.yumeiro.player.SleepTimer.State.Countdown)
        log("PL17 5 min timer: $st")
        // The real stop: 1 minute (the shortest a person can pick), then pause + full volume.
        rule.runOnUiThread { dev.asfakul.yumeiro.player.VideoSleepTimer.timer.start(1) }
        val t0 = System.currentTimeMillis()
        wait("video paused by the sleep timer", 80_000) { onPlayer { it.playWhenReady } == false }
        val took = System.currentTimeMillis() - t0
        val vol = onPlayer { it.volume }
        log("PL17 paused after ${took}ms, volume=$vol, state=${dev.asfakul.yumeiro.player.VideoSleepTimer.timer.state.value}")
        assertTrue("paused near 60 s (was ${took}ms)", took in 55_000..75_000)
        assertEquals("volume restored", 1f, vol ?: 0f, 0.01f)
        assertTrue("timer cleared", dev.asfakul.yumeiro.player.VideoSleepTimer.timer.state.value == null)
        log("LOCAL_F_RESULT PASS")
    }

    /** R17 PL-18: on a phone one Back leaves full screen and the player together (floating window). */
    @Test fun g_oneBackLeavesFullscreenOnPhone() {
        // Phone only: the TV player is always full screen and has no fullscreen button (showFullscreenButton = !tvPlayer).
        org.junit.Assume.assumeFalse("TV player has no fullscreen toggle", dev.asfakul.yumeiro.Edition.isTv)
        openClipFromDevice()
        loopClip()
        control("video_fullscreen_btn")
        pause(1_500)
        FirstRun.shot("pl18_fullscreen")
        // A touch Back (gesture or nav bar), not a key event: the app treats key Back as a remote.
        // LP-T1: an earlier class's key events can leave the display out of touch mode.
        FirstRun.touchMode()
        var touch = false; rule.runOnUiThread { touch = rule.activity.window.decorView.isInTouchMode }
        log("PL18 touchMode=$touch")
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        wait("floating player after one Back", 8_000) { has("floating_close") }
        log("PL18 one Back → floating player")
        tap("floating_close")
        log("LOCAL_G_RESULT PASS")
    }

    /** G3: when the system refuses picture-in-picture the user is told (app op forced to ignore). */
    @Test fun h_refusedPipSaysSo() {
        val pm = ctx.packageManager
        org.junit.Assume.assumeTrue("no PiP on this device", pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE))
        FirstRun.shell("appops set ${ctx.packageName} PICTURE_IN_PICTURE ignore")
        try {
            openClipFromDevice()
            loopClip()
            control("video_pip_btn")
            wait("refused-PiP message", 8_000) {
                dev.asfakul.yumeiro.ui.components.AppMessages.current.value?.text?.contains("Picture-in-picture isn't available") == true ||
                    hasText("Picture-in-picture isn't available")
            }
            var pip = true; rule.runOnUiThread { pip = rule.activity.isInPictureInPictureMode }
            assertTrue("not in PiP", !pip)
            log("LOCAL_H_RESULT PASS")
        } finally {
            FirstRun.shell("appops set ${ctx.packageName} PICTURE_IN_PICTURE allow")
        }
    }

    /**
     * R17 PL-19: an SRT is converted on the phone and served to the TV on the local network, with
     * the CORS header a Cast receiver needs. Fetched here over a real socket at the emulator's
     * Wi-Fi/Ethernet address (what a TV on the same network would use).
     */
    @Test fun i_castSubtitleServerServesConvertedSrt() {
        val srtFile = java.io.File(ctx.cacheDir, "pl19.srt").apply { writeText("1\r\n00:00:01,000 --> 00:00:02,000\r\nこんにちは\r\n") }
        val subs = kotlinx.coroutines.runBlocking {
            dev.asfakul.yumeiro.cast.CastSupport.prepareSubtitles(ctx, listOf(dev.asfakul.yumeiro.cast.CastSupport.Sub("Japanese", android.net.Uri.fromFile(srtFile).toString())))
        }
        log("PL19 prepared: $subs, address=${dev.asfakul.yumeiro.cast.CastSubtitleServer.localAddress()}")
        assertEquals(1, subs.size)
        val url = java.net.URI(subs[0].second)
        assertTrue("served from the LAN address: $url", url.scheme == "http" && url.path.endsWith(".vtt") && url.host != "127.0.0.1")
        val resp = java.net.Socket(url.host, url.port).use { s ->
            s.getOutputStream().write("GET ${url.path} HTTP/1.1\r\nHost: ${url.host}\r\n\r\n".toByteArray())
            s.getInputStream().readBytes().toString(Charsets.UTF_8)
        }
        log("PL19 response head: ${resp.substringBefore("\r\n\r\n").replace("\r\n", " | ")}")
        assertTrue(resp, resp.startsWith("HTTP/1.1 200") && resp.contains("Access-Control-Allow-Origin: *") && resp.contains("text/vtt"))
        assertTrue(resp, resp.contains("WEBVTT") && resp.contains("00:00:01.000 --> 00:00:02.000") && resp.contains("こんにちは"))
        dev.asfakul.yumeiro.cast.CastSubtitleServer.stop()
        log("LOCAL_I_RESULT PASS")
    }

    /** Screen bounds of the player's gesture area (window bounds + the window's place on screen). */
    private fun playerBox(): android.graphics.RectF {
        val b = rule.onAllNodesWithTag("video_player_root", true)[0].fetchSemanticsNode().boundsInWindow
        val at = IntArray(2); rule.runOnUiThread { rule.activity.window.decorView.getLocationOnScreen(at) }
        return android.graphics.RectF(b.left + at[0], b.top + at[1], b.right + at[0], b.bottom + at[1])
    }
    private fun swipe(x1: Float, y1: Float, x2: Float, y2: Float) {
        FirstRun.shell("input swipe ${x1.toInt()} ${y1.toInt()} ${x2.toInt()} ${y2.toInt()} 600"); pause(1_200)
    }
    private fun brightness(): Float { var v = 0f; rule.runOnUiThread { v = rule.activity.window.attributes.screenBrightness }; return v }

    /**
     * G3 P8 re-audit: the swipe gestures through real touch input (`input swipe`), not only the
     * VolumeAccumulator unit test. Right half up/down → media volume; left half up → window
     * brightness; a sideways swipe on a paused clip seeks back; closing the player gives the
     * window its brightness back.
     */
    @Test fun j_swipeGesturesChangeVolumeBrightnessAndPosition() {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val music = android.media.AudioManager.STREAM_MUSIC
        val max = am.getStreamMaxVolume(music)
        val oldVol = am.getStreamVolume(music)
        try {
            am.setStreamVolume(music, (max / 4).coerceAtLeast(1), 0)
            val brightBefore = brightness()
            openClipFromDevice()
            loopClip()
            pause(5_000) // let the controls hide so the swipes land on the gesture layer
            val r = playerBox()
            log("P8 player box $r, volume ${am.getStreamVolume(music)}/$max, brightness $brightBefore")
            val v0 = am.getStreamVolume(music)
            swipe(r.left + r.width() * 0.85f, r.top + r.height() * 0.70f, r.left + r.width() * 0.85f, r.top + r.height() * 0.30f)
            val v1 = am.getStreamVolume(music)
            swipe(r.left + r.width() * 0.85f, r.top + r.height() * 0.30f, r.left + r.width() * 0.85f, r.top + r.height() * 0.70f)
            val v2 = am.getStreamVolume(music)
            log("P8_VOLUME up $v0 → $v1, down → $v2 (max $max)")
            assertTrue("volume up by swipe ($v0 → $v1)", v1 > v0)
            assertTrue("volume down by swipe ($v1 → $v2)", v2 < v1)
            swipe(r.left + r.width() * 0.15f, r.top + r.height() * 0.70f, r.left + r.width() * 0.15f, r.top + r.height() * 0.30f)
            val b1 = brightness()
            log("P8_BRIGHTNESS $brightBefore → $b1")
            assertTrue("brightness set above the 0.5 start by an up swipe (was $brightBefore, now $b1)", b1 > 0.5f)
            rule.runOnUiThread { PlaybackSession.playerForTest?.let { it.pause(); it.seekTo(9_000) } }
            pause(1_500)
            val p0 = onPlayer { it.currentPosition } ?: -1L
            swipe(r.left + r.width() * 0.60f, r.top + r.height() * 0.5f, r.left + r.width() * 0.60f - 120f, r.top + r.height() * 0.5f)
            pause(1_000)
            val p1 = onPlayer { it.currentPosition } ?: -1L
            log("P8_SEEK paused at $p0 → $p1 after a 120 px left swipe")
            assertTrue("sideways swipe seeks back ($p0 → $p1)", p1 in 0 until p0 - 1_500)
            // A touch Back (as in g): the app treats key Back as a remote.
            rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
            wait("floating player", 8_000) { has("floating_close") }
            tap("floating_close")
            wait("player closed", 10_000) { !has("video_player_root") && !has("floating_close") }
            val b2 = brightness()
            log("P8_BRIGHTNESS after close $b2 (before $brightBefore)")
            assertEquals("window brightness given back on close", brightBefore, b2, 0.001f)
            log("LOCAL_J_RESULT PASS")
        } finally {
            am.setStreamVolume(music, oldVol, 0)
        }
    }

    companion object { const val NAME = "yumeiro_test_clip.mp4" }
}
