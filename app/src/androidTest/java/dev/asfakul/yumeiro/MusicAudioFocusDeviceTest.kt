/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.ContentValues
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.Player
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.service.PlaybackService
import dev.asfakul.yumeiro.ui.viewmodel.MainViewModel
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G6-AF: music and other apps' audio focus. Another app (here: a second focus client in the test)
 * takes focus the way a call / navigation prompt / other player does:
 *  - transient (call, voice prompt): music pauses and comes back by itself when focus returns;
 *  - may-duck (notification sound): music keeps playing;
 *  - permanent (another music app): music pauses and stays paused when that app lets go.
 * Then the becoming-noisy broadcast (headset unplugged) is tried from the shell; it is a protected
 * broadcast, so a refusal is logged, not counted (hardware → owner).
 *
 * Logs `YuLibraryTest AF_…`.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 26)
class MusicAudioFocusDeviceTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx: Context get() = inst.targetContext
    private val audio: AudioManager get() = ctx.getSystemService(AudioManager::class.java)
    private fun log(s: String) = Log.i("YuLibraryTest", s)
    private fun <T> onMain(f: (androidx.media3.exoplayer.ExoPlayer?) -> T): T { var r: T? = null; inst.runOnMainSync { r = f(PlaybackService.sharedPlayer) }; @Suppress("UNCHECKED_CAST") return r as T }
    private fun playing() = onMain { it?.isPlaying == true }
    private fun wants() = onMain { it?.playWhenReady == true }
    private fun suppression() = onMain { it?.playbackSuppressionReason ?: -1 }
    private fun waitFor(ms: Long, cond: () -> Boolean): Boolean { val t = SystemClock.elapsedRealtime(); while (!cond() && SystemClock.elapsedRealtime() - t < ms) Thread.sleep(100); return cond() }
    private fun shell(cmd: String): String = inst.uiAutomation.executeShellCommand(cmd).let { pfd ->
        android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
    }

    private fun request(gain: Int): AudioFocusRequest {
        val req = AudioFocusRequest.Builder(gain)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(
                if (gain == AudioManager.AUDIOFOCUS_GAIN) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
            ).build())
            .setOnAudioFocusChangeListener { }
            .build()
        val r = audio.requestAudioFocus(req)
        assertTrue("other client did not get focus ($r)", r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        return req
    }

    private fun addSong(title: String): Long {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$title.m4a")
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            if (Build.VERSION.SDK_INT >= 29) { put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/YuFocus"); put(MediaStore.Audio.Media.IS_PENDING, 1) }
        }
        val uri = ctx.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: throw AssertionError("MediaStore insert failed")
        ctx.contentResolver.openOutputStream(uri)!!.use { o -> inst.context.assets.open("yt_audio_only.m4a").use { it.copyTo(o) } }
        if (Build.VERSION.SDK_INT >= 29) ctx.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        return android.content.ContentUris.parseId(uri)
    }

    @Test fun musicFollowsOtherAppsAudioFocus() {
        listOf("android.permission.READ_MEDIA_AUDIO", "android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE", "android.permission.POST_NOTIFICATIONS")
            .forEach { runCatching { inst.uiAutomation.grantRuntimePermission(ctx.packageName, it) } }
        val id = addSong("YuAF Song").toString()
        val sc = ActivityScenario.launch(MainActivity::class.java)
        try {
            var vm: MainViewModel? = null
            sc.onActivity { vm = ViewModelProvider(it)[MainViewModel::class.java] }
            val model = vm!!
            inst.runOnMainSync { model.scanMedia() }
            val track = kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.withTimeout(30_000) { model.rawAudioTracks.first { l -> l.any { it.id == id } }.first { it.id == id } }
            }
            inst.runOnMainSync { model.playTrack(track, listOf(track)); PlaybackService.sharedPlayer?.repeatMode = Player.REPEAT_MODE_ONE }
            assertTrue("song did not start", waitFor(20_000) { playing() })

            // 1. Transient loss (call / navigation prompt): pause, then resume by itself.
            val t = request(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            val tPaused = waitFor(5_000) { !playing() }
            val tReason = suppression(); val tWants = wants()
            audio.abandonAudioFocusRequest(t)
            val tResumed = waitFor(5_000) { playing() }
            log("AF_TRANSIENT paused=$tPaused suppression=$tReason wantsToPlay=$tWants resumed=$tResumed")

            // 2. May duck (notification sound): keep playing.
            val d = request(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            Thread.sleep(1_500)
            val dPlaying = playing()
            audio.abandonAudioFocusRequest(d)
            Thread.sleep(500)
            log("AF_DUCK stillPlaying=$dPlaying after=${playing()}")

            // 3. Permanent loss (another music app): pause, stay paused when it lets go.
            assertTrue("not playing before the permanent loss", waitFor(5_000) { playing() })
            val p = request(AudioManager.AUDIOFOCUS_GAIN)
            val pPaused = waitFor(5_000) { !playing() }
            val pWants = wants()
            audio.abandonAudioFocusRequest(p)
            Thread.sleep(2_000)
            val pStayed = !playing()
            log("AF_PERMANENT paused=$pPaused wantsToPlay=$pWants stayedPaused=$pStayed")

            // 4. Becoming noisy (headset out): protected broadcast; refusal → hardware (owner).
            inst.runOnMainSync { PlaybackService.sharedPlayer?.play() }
            val nStarted = waitFor(10_000) { playing() }
            val out = shell("am broadcast -a android.media.AUDIO_BECOMING_NOISY").trim().replace('\n', ' ')
            val nPaused = waitFor(3_000) { !playing() }
            log("AF_NOISY started=$nStarted paused=$nPaused shell=[${out.take(200)}]")

            val pass = tPaused && tResumed && dPlaying && pPaused && !pWants && pStayed
            log("AF_RESULT ${if (pass) "PASS" else "FAIL"} transient=$tPaused/$tResumed duck=$dPlaying permanent=$pPaused/$pStayed noisy=$nPaused sdk=${Build.VERSION.SDK_INT}")
            assertTrue("transient focus loss did not pause", tPaused)
            assertTrue("music did not resume after transient focus came back", tResumed)
            assertTrue("music stopped on a may-duck request", dPlaying)
            assertTrue("permanent focus loss did not pause", pPaused && !pWants)
            assertTrue("music resumed by itself after a permanent loss", pStayed)
        } finally {
            inst.runOnMainSync { PlaybackService.sharedPlayer?.let { it.repeatMode = Player.REPEAT_MODE_OFF; it.pause() } }
            sc.close()
            runCatching { ctx.contentResolver.delete(android.content.ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toLong()), null, null) }
        }
    }
}
