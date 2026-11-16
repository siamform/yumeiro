/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.media.MediaCodecList
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.media3.ui.PlayerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import dev.asfakul.yumeiro.backup.AutoBackupWorker
import dev.asfakul.yumeiro.backup.BackupMeta
import dev.asfakul.yumeiro.player.PlaybackDiagnostics
import dev.asfakul.yumeiro.player.PlaybackSession
import dev.asfakul.yumeiro.player.VideoPrefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * G9 SE-6: real effect of the choice rows that had no test (Settings › Quality and audio,
 * Settings › Backup, storage & reset). Set through the setter the row calls, then read from the
 * real player opened on a device clip, or from WorkManager:
 *  a Default speed (`qa_speed`) 1.5x → the player starts at 1.5x; Normal → 1x;
 *  b Default quality (`qa_quality`) 480p → max video height 480; Auto → no cap;
 *  c Video renderer (`qa_renderer`) SurfaceView / TextureView → that surface in the PlayerView;
 *  d Video decoder (`qa_decoder`) Software → a software video decoder; Hardware → a hardware one
 *    when the device has one (read from the codec framework log);
 *  e Default player (`qa_player`) Ask every time → the player pauses and asks; "Yumeiro" plays on;
 *    Yumeiro → no question;
 *  f Back up "How often" (`backup_auto_days`) every 3 days → the periodic work repeats every 3 days.
 * Every value is put back. Log tag YuChoiceFx: CFX_CASE lines, CFX_RESULT PASS per test.
 * Unit side (cache, cookies, default audio, backup keep): ChoiceSettingsEffectTest.
 */
@RunWith(AndroidJUnit4::class)
@org.junit.FixMethodOrder(org.junit.runners.MethodSorters.NAME_ASCENDING)
class ChoiceSettingsEffectDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val inst get() = InstrumentationRegistry.getInstrumentation()

    private fun log(s: String) = Log.i("YuChoiceFx", s)
    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun hasText(t: String) = rule.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun wait(what: String, ms: Long = 30_000, cond: () -> Boolean) {
        val ok = runCatching { rule.waitUntil(ms) { cond() }; true }.getOrDefault(false)
        if (!ok) FirstRun.shot("cfx_fail_" + what.filter { it.isLetterOrDigit() }.take(30))
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
    private data class Saved(val speed: Float, val q: VideoPrefs.Quality, val r: VideoPrefs.Renderer, val d: VideoPrefs.Decoder, val p: VideoPrefs.DefaultPlayer, val days: Int, val auto: Boolean)
    private var saved: Saved? = null

    @Before fun setUp() {
        listOf("android.permission.READ_MEDIA_VIDEO", "android.permission.READ_EXTERNAL_STORAGE", "android.permission.POST_NOTIFICATIONS")
            .forEach { runCatching { inst.uiAutomation.grantRuntimePermission(ctx.packageName, it) } }
        VideoPrefs.init(ctx)
        saved = Saved(VideoPrefs.speed.value, VideoPrefs.quality.value, VideoPrefs.renderer.value, VideoPrefs.decoder.value,
            VideoPrefs.defaultPlayer.value, BackupMeta.autoEveryDays(ctx), BackupMeta.autoEnabled(ctx))
        clip = TestClip.put(ctx, NAME, "yumeiro_test_clip.mp4")
        FirstRun.toTabs(rule)
        PlaybackDiagnostics.reset("")
    }

    @After fun tearDown() {
        runCatching { rule.runOnUiThread { PlaybackSession.releaseNow() } }
        saved?.let {
            VideoPrefs.setSpeed(ctx, it.speed); VideoPrefs.setQuality(ctx, it.q); VideoPrefs.setRenderer(ctx, it.r)
            VideoPrefs.setDecoder(ctx, it.d); VideoPrefs.setDefaultPlayer(ctx, it.p)
            BackupMeta.setAutoEveryDays(ctx, it.days); BackupMeta.setAutoEnabled(ctx, it.auto)
            AutoBackupWorker.schedule(ctx, replace = true)
        }
        clip?.let { TestClip.remove(ctx, it, NAME) }
    }

    /** Videos → Device → (rescan) → the clip, until the first frame. Same path as PlayerExtrasSettingsEffectDeviceTest. */
    private fun openClip(firstFrame: Boolean = true) {
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
        if (firstFrame) wait("first frame", 45_000) { PlaybackDiagnostics.state.value.firstFrame }
        else wait("player open", 30_000) { has("video_player_root") }
    }

    /** Back to the floating window, close it, wait past the session grace so the next open is a fresh player. */
    private fun closePlayer(case: String) {
        fun st() = "root=${has("video_player_root")} float=${has("video_floating_player")} close=${has("floating_close")} state=${PlaybackDiagnostics.state.value.state}"
        val t0 = System.currentTimeMillis()
        var presses = 0
        log("CFX_STEP $case close start ${st()}")
        while (presses < 3 && !has("floating_close")) {
            back(); presses++
            runCatching { rule.waitUntil(4_000) { has("floating_close") } }
            log("CFX_STEP $case back#$presses +${System.currentTimeMillis() - t0}ms ${st()}")
        }
        wait("floating player after back", 10_000) { has("floating_close") }
        var taps = 0
        while (taps < 3 && (has("video_player_root") || has("video_floating_player"))) {
            val b = runCatching { rule.onAllNodesWithTag("floating_close", true).fetchSemanticsNodes().map { it.boundsInRoot } }.getOrNull()
            tap("floating_close"); taps++
            runCatching { rule.waitUntil(5_000) { !has("video_player_root") && !has("video_floating_player") } }
            log("CFX_STEP $case closeTap#$taps bounds=$b +${System.currentTimeMillis() - t0}ms ${st()}")
        }
        wait("player closed", 5_000) { !has("video_player_root") && !has("video_floating_player") }
        // A close that needed more than one tap is an app defect, not test noise: fail on it.
        assertEquals("$case: one tap on the floating close button closes the player", 1, taps)
        pause(PlaybackSession.GRACE_MS + 1_500)
    }

    @Test fun a_defaultSpeed() {
        VideoPrefs.setSpeed(ctx, 1.5f)
        openClip()
        val fast = onPlayer { it.playbackParameters.speed }
        closePlayer("a")
        VideoPrefs.setSpeed(ctx, 1f)
        openClip()
        val normal = onPlayer { it.playbackParameters.speed }
        log("CFX_CASE a speed 1.5 → $fast · normal → $normal")
        assertEquals("default speed 1.5x", 1.5f, fast ?: -1f, 0.01f)
        assertEquals("default speed normal", 1f, normal ?: -1f, 0.01f)
        log("CFX_RESULT PASS a")
    }

    @Test fun b_defaultQuality() {
        VideoPrefs.setQuality(ctx, VideoPrefs.Quality.Q480)
        openClip()
        val capped = onPlayer { it.trackSelectionParameters.maxVideoHeight }
        closePlayer("b")
        VideoPrefs.setQuality(ctx, VideoPrefs.Quality.AUTO)
        openClip()
        val auto = onPlayer { it.trackSelectionParameters.maxVideoHeight }
        log("CFX_CASE b quality 480p → maxVideoHeight=$capped · auto → $auto")
        assertEquals("480p caps the height", 480, capped)
        assertEquals("Auto has no cap", Int.MAX_VALUE, auto)
        log("CFX_RESULT PASS b")
    }

    private fun surfaceKind(): String? {
        var kind: String? = null
        rule.runOnUiThread {
            fun find(v: View): PlayerView? = when (v) {
                is PlayerView -> v
                is ViewGroup -> (0 until v.childCount).firstNotNullOfOrNull { find(v.getChildAt(it)) }
                else -> null
            }
            val s = find(rule.activity.window.decorView)?.videoSurfaceView
            kind = when (s) { is SurfaceView -> "surface"; is TextureView -> "texture"; null -> null; else -> s.javaClass.simpleName }
        }
        return kind
    }

    @Test fun c_videoRenderer() {
        VideoPrefs.setRenderer(ctx, VideoPrefs.Renderer.SURFACE)
        openClip()
        val surface = surfaceKind()
        closePlayer("c")
        VideoPrefs.setRenderer(ctx, VideoPrefs.Renderer.TEXTURE)
        openClip()
        val texture = surfaceKind()
        log("CFX_CASE c renderer SurfaceView → $surface · TextureView → $texture")
        assertEquals("SurfaceView chosen", "surface", surface)
        assertEquals("TextureView chosen", "texture", texture)
        log("CFX_RESULT PASS c")
    }

    private val codecRx = Regex("""\b((?:c2|OMX)\.[A-Za-z0-9_.]+)""")
    /** The video decoder the framework allocated for the clip, from the codec log of this run. */
    private fun videoDecoderAfterOpen(): String? {
        val out = FirstRun.shell("logcat -d -v brief")
        return codecRx.findAll(out).map { it.groupValues[1] }
            .filter { n -> n.contains("decoder", true) && listOf("avc", "h264", "hevc", "h265", "vp8", "vp9", "av1", "mpeg4", "h263").any { n.contains(it, true) } }
            .lastOrNull()
    }
    private fun isSoftware(n: String) = n.startsWith("c2.android.") || n.startsWith("OMX.google.") || n.contains("ffmpeg", true)
    private fun hasHardwareAvc(): Boolean = Build.VERSION.SDK_INT >= 29 &&
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { !it.isEncoder && it.isHardwareAccelerated && it.supportedTypes.any { t -> t.equals("video/avc", true) } }

    @Test fun d_videoDecoder() {
        VideoPrefs.setDecoder(ctx, VideoPrefs.Decoder.SOFTWARE)
        FirstRun.shell("logcat -c")
        openClip()
        val sw = videoDecoderAfterOpen()
        closePlayer("d")
        VideoPrefs.setDecoder(ctx, VideoPrefs.Decoder.HARDWARE)
        FirstRun.shell("logcat -c")
        openClip()
        val hw = videoDecoderAfterOpen()
        val hwAvailable = hasHardwareAvc()
        log("CFX_CASE d decoder Software → $sw · Hardware → $hw · device has a hardware AVC decoder=$hwAvailable")
        assertTrue("Software: a software video decoder (got $sw)", sw != null && isSoftware(sw))
        if (hwAvailable) assertTrue("Hardware: a hardware video decoder (got $hw)", hw != null && !isSoftware(hw))
        log("CFX_RESULT PASS d")
    }

    @Test fun e_defaultPlayerAsk() {
        VideoPrefs.setDefaultPlayer(ctx, VideoPrefs.DefaultPlayer.ASK)
        openClip(firstFrame = false)
        wait("Ask every time: the question", 30_000) { has("ask_player_yumeiro") }
        val pausedWhileAsking = onPlayer { !it.playWhenReady } == true
        tap("ask_player_yumeiro")
        wait("Yumeiro chosen: plays", 15_000) { onPlayer { it.isPlaying } == true }
        closePlayer("e")
        VideoPrefs.setDefaultPlayer(ctx, VideoPrefs.DefaultPlayer.YUMEIRO)
        openClip()
        pause(2_000)
        val askedAgain = has("ask_player_yumeiro")
        log("CFX_CASE e ask → asked, paused=$pausedWhileAsking · yumeiro → asked=$askedAgain")
        assertTrue("while asking the video waits", pausedWhileAsking)
        assertFalse("Yumeiro: no question", askedAgain)
        log("CFX_RESULT PASS e")
    }

    @Test fun f_backupHowOften() {
        BackupMeta.setAutoEnabled(ctx, true)
        BackupMeta.setAutoEveryDays(ctx, 3)
        AutoBackupWorker.schedule(ctx, replace = true)
        val wm = WorkManager.getInstance(ctx)
        fun interval(): Long? = wm.getWorkInfosForUniqueWork("yumeiro_auto_backup").get(10, TimeUnit.SECONDS)
            .firstOrNull { !it.state.isFinished }?.periodicityInfo?.repeatIntervalMillis
        val three = interval()
        BackupMeta.setAutoEveryDays(ctx, 7)
        AutoBackupWorker.schedule(ctx, replace = true)
        val seven = interval()
        log("CFX_CASE f every 3 days → ${three?.let { TimeUnit.MILLISECONDS.toDays(it) }} d · weekly → ${seven?.let { TimeUnit.MILLISECONDS.toDays(it) }} d")
        assertEquals(TimeUnit.DAYS.toMillis(3), three)
        assertEquals(TimeUnit.DAYS.toMillis(7), seven)
        log("CFX_RESULT PASS f")
    }

    companion object { const val NAME = "yumeiro_choice_clip.mp4" }
}
