/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.Player
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.service.PlaybackService
import dev.asfakul.yumeiro.ui.viewmodel.MainViewModel
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G6-ST1: the music sleep timer lived in the screen's ViewModel scope. A swipe from recents (or a
 * Back exit) clears the ViewModel while PlaybackService plays on, so the timer was cancelled and the
 * music never stopped. Here: one song on repeat, a 1-minute timer, the screen closed (ViewModel
 * cleared) — the shared player must pause by itself within the timer + fade + margin.
 *
 * Logs `YuLibraryTest ST_…`.
 */
@RunWith(AndroidJUnit4::class)
class SleepTimerWithoutScreenDeviceTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx: Context get() = inst.targetContext
    private fun log(s: String) = Log.i("YuLibraryTest", s)
    private fun wantsToPlay(): Boolean { var p = false; inst.runOnMainSync { p = PlaybackService.sharedPlayer?.playWhenReady == true }; return p }
    private fun playing(): Boolean { var p = false; inst.runOnMainSync { p = PlaybackService.sharedPlayer?.isPlaying == true }; return p }

    private fun addSong(title: String): Long {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$title.m4a")
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            if (Build.VERSION.SDK_INT >= 29) { put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/YuSleepTimer"); put(MediaStore.Audio.Media.IS_PENDING, 1) }
        }
        val uri = ctx.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: throw AssertionError("MediaStore insert failed")
        ctx.contentResolver.openOutputStream(uri)!!.use { o -> inst.context.assets.open("yt_audio_only.m4a").use { it.copyTo(o) } }
        if (Build.VERSION.SDK_INT >= 29) ctx.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        return android.content.ContentUris.parseId(uri)
    }

    @Test fun timerStopsMusicAfterTheScreenIsGone() {
        listOf("android.permission.READ_MEDIA_AUDIO", "android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE", "android.permission.POST_NOTIFICATIONS")
            .forEach { runCatching { inst.uiAutomation.grantRuntimePermission(ctx.packageName, it) } }
        val id = addSong("YuST Song").toString()
        try {
            val sc = ActivityScenario.launch(MainActivity::class.java)
            var vm: MainViewModel? = null
            sc.onActivity { vm = ViewModelProvider(it)[MainViewModel::class.java] }
            val model = vm!!
            inst.runOnMainSync { model.scanMedia() }
            val track = kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.withTimeout(30_000) { model.rawAudioTracks.first { l -> l.any { it.id == id } }.first { it.id == id } }
            }
            inst.runOnMainSync {
                model.playTrack(track, listOf(track))
                PlaybackService.sharedPlayer?.repeatMode = Player.REPEAT_MODE_ONE
                PlaybackService.sharedPlayer?.volume = 0f
            }
            val t0 = SystemClock.elapsedRealtime()
            while (!playing() && SystemClock.elapsedRealtime() - t0 < 20_000) Thread.sleep(200)
            assertTrue("song did not start", playing())

            inst.runOnMainSync { model.sleepTimer.start(1) }
            val armedAt = SystemClock.elapsedRealtime()
            Thread.sleep(1_000)
            sc.close() // ViewModel cleared, as after a swipe from recents; the service plays on
            Thread.sleep(2_000)
            log("ST_A screen closed; playing=${playing()} wantsToPlay=${wantsToPlay()}")
            assertTrue("music stopped when the screen closed (precondition)", wantsToPlay())

            // 60 s timer (fade in the last 20 s) + 40 s margin for a slow emulator.
            while (wantsToPlay() && SystemClock.elapsedRealtime() - armedAt < 100_000) Thread.sleep(500)
            val after = SystemClock.elapsedRealtime() - armedAt
            val stillPlaying = wantsToPlay()
            var volume = -1f
            inst.runOnMainSync { volume = PlaybackService.sharedPlayer?.volume ?: -1f }
            log("ST_RESULT ${if (stillPlaying) "FAIL" else "PASS"} pausedAfterMs=$after stillPlaying=$stillPlaying volume=$volume sdk=${Build.VERSION.SDK_INT}")
            inst.runOnMainSync { PlaybackService.sharedPlayer?.let { it.repeatMode = Player.REPEAT_MODE_OFF; it.pause() } }
            assertTrue("sleep timer did not stop the music after the screen was gone (${after} ms)", !stillPlaying)
            assertTrue("paused too early: $after ms", after >= 58_000)
        } finally {
            runCatching { ctx.contentResolver.delete(android.content.ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toLong()), null, null) }
        }
    }
}
