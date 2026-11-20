/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.service.PlaybackService
import dev.asfakul.yumeiro.ui.viewmodel.MainViewModel
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * G6-NC: the media notification and the session controls other apps use (lock screen, Bluetooth,
 * watch, Android Auto, Assistant). Two device songs, played; then, with the screen closed, an outside
 * MediaController (as System UI uses) must see the session and pause / play / skip next / skip back,
 * and a media notification with transport actions must be posted while playing and stay after pause.
 *
 * Logs `YuLibraryTest NC_…`.
 */
@RunWith(AndroidJUnit4::class)
class MusicNotificationControlsDeviceTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx: Context get() = inst.targetContext
    private fun log(s: String) = Log.i("YuLibraryTest", s)
    private fun <T> onMain(f: () -> T): T { var r: T? = null; inst.runOnMainSync { r = f() }; @Suppress("UNCHECKED_CAST") return r as T }
    private fun playing() = onMain { PlaybackService.sharedPlayer?.isPlaying == true }
    private fun index() = onMain { PlaybackService.sharedPlayer?.currentMediaItemIndex ?: -1 }
    private fun waitFor(ms: Long, cond: () -> Boolean): Boolean { val t = SystemClock.elapsedRealtime(); while (!cond() && SystemClock.elapsedRealtime() - t < ms) Thread.sleep(100); return cond() }

    private fun mediaNotification(): Notification? = ctx.getSystemService(NotificationManager::class.java).activeNotifications
        .map { it.notification }
        .firstOrNull { it.extras.containsKey(Notification.EXTRA_MEDIA_SESSION) || it.category == Notification.CATEGORY_TRANSPORT }

    private fun addSong(title: String): Long {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$title.m4a")
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            if (Build.VERSION.SDK_INT >= 29) { put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/YuNotif"); put(MediaStore.Audio.Media.IS_PENDING, 1) } else put(MediaStore.Audio.Media.DATA, LegacyMedia.path("Music", "YuNotif", "$title.m4a")) // FINAL-24
        }
        val uri = ctx.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: throw AssertionError("MediaStore insert failed")
        ctx.contentResolver.openOutputStream(uri)!!.use { o -> inst.context.assets.open("yt_audio_only.m4a").use { it.copyTo(o) } }
        if (Build.VERSION.SDK_INT >= 29) ctx.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        return android.content.ContentUris.parseId(uri)
    }

    @Test fun notificationAndOutsideControlsWorkWithoutTheScreen() {
        listOf("android.permission.READ_MEDIA_AUDIO", "android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE", "android.permission.POST_NOTIFICATIONS")
            .forEach { runCatching { inst.uiAutomation.grantRuntimePermission(ctx.packageName, it) } }
        val ids = listOf(addSong("YuNC One").toString(), addSong("YuNC Two").toString())
        var controller: MediaController? = null
        try {
            val sc = ActivityScenario.launch(MainActivity::class.java)
            var vm: MainViewModel? = null
            sc.onActivity { vm = ViewModelProvider(it)[MainViewModel::class.java] }
            val model = vm!!
            inst.runOnMainSync { model.scanMedia() }
            val tracks = kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.withTimeout(30_000) { model.rawAudioTracks.first { l -> ids.all { id -> l.any { it.id == id } } } }
            }.filter { it.id in ids }.sortedBy { ids.indexOf(it.id) }
            inst.runOnMainSync { model.playTrack(tracks[0], tracks); PlaybackService.sharedPlayer?.volume = 0f }
            assertTrue("song did not start", waitFor(20_000) { playing() })

            val shown = waitFor(10_000) { mediaNotification() != null }
            val n = mediaNotification()
            val actions = n?.actions?.size ?: 0
            log("NC_A notification=$shown actions=$actions titles=${n?.actions?.joinToString("|") { it.title.toString() }} ongoing=${n?.flags?.and(Notification.FLAG_ONGOING_EVENT) != 0}")

            sc.close() // controls must not depend on the screen
            Thread.sleep(1_000)
            val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
            val future = onMain { MediaController.Builder(ctx, token).setApplicationLooper(Looper.getMainLooper()).buildAsync() }
            controller = future.get(15, TimeUnit.SECONDS)
            val c = controller!!
            val cmds = onMain { c.availableCommands }
            val hasCmds = listOf(Player.COMMAND_PLAY_PAUSE, Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_PREVIOUS).all { cmds.contains(it) }
            log("NC_B connected=true commandsOk=$hasCmds controllerPlaying=${onMain { c.isPlaying }}")

            inst.runOnMainSync { c.pause() }
            val paused = waitFor(5_000) { !playing() }
            Thread.sleep(1_000)
            val keptAfterPause = mediaNotification() != null
            inst.runOnMainSync { c.play() }
            val resumed = waitFor(5_000) { playing() }
            inst.runOnMainSync { c.seekToNext() }
            val next = waitFor(5_000) { index() == 1 }
            inst.runOnMainSync { c.seekToPrevious() }
            val prevOk = waitFor(5_000) { index() == 0 }
            log("NC_C paused=$paused notificationAfterPause=$keptAfterPause resumed=$resumed next=$next previous=$prevOk")

            val pass = shown && actions >= 2 && hasCmds && paused && keptAfterPause && resumed && next && prevOk
            log("NC_RESULT ${if (pass) "PASS" else "FAIL"} shown=$shown actions=$actions cmds=$hasCmds pause=$paused kept=$keptAfterPause play=$resumed next=$next prev=$prevOk sdk=${Build.VERSION.SDK_INT}")
            assertTrue("no media notification while playing", shown)
            assertTrue("media notification has $actions actions", actions >= 2)
            assertTrue("session lacks play/pause/next/previous", hasCmds)
            assertTrue("outside pause did not pause", paused)
            assertTrue("notification gone after pause (can't resume from the shade)", keptAfterPause)
            assertTrue("outside play did not resume", resumed)
            assertTrue("outside next did not skip", next)
            assertTrue("outside previous did not go back", prevOk)
        } finally {
            inst.runOnMainSync { controller?.release(); PlaybackService.sharedPlayer?.let { it.pause(); it.volume = 1f } }
            ids.forEach { id -> runCatching { ctx.contentResolver.delete(android.content.ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toLong()), null, null) } }
        }
    }
}
