/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.data.model.AudioTrack
import dev.asfakul.yumeiro.player.LastPlayed
import dev.asfakul.yumeiro.service.PlaybackService
import dev.asfakul.yumeiro.ui.viewmodel.MainViewModel
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * G6-QP (docs/proposals/FEATURE-G6-QP-queue-persistence.md, ADR-019): queue five songs, kill the
 * app, reopen, Home Continue, all five come back in order with the second one current.
 *
 * Phase a (`-e phase a`, after `pm clear`): five songs on the device, plays them as one queue,
 * skips to song 2, pauses there (a pause saves Continue), kills its own process ("Process crashed" by design).
 * Phase b (`-e phase b`, a new process): Home → Continue → Play. The player must hold the same five
 * songs in the same order, on song 2. Before G6-QP the queue was rebuilt from Recently played,
 * so only songs 2 and 1 came back.
 * Uses only APIs that existed before G6-QP, so the same file runs before and after the fix.
 * `scripts/device-check.sh` runs both phases.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class QueuePersistenceDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx: Context get() = inst.targetContext
    private val phase: String? get() = InstrumentationRegistry.getArguments().getString("phase")
    private val notes get() = ctx.getSharedPreferences("yu_test_queue_persist", Context.MODE_PRIVATE)
    private fun log(s: String) = Log.i("YuQueuePersist", s)
    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun hasText(t: String) = rule.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun wait(what: String, ms: Long, cond: () -> Boolean) {
        val ok = runCatching { rule.waitUntil(ms) { cond() }; true }.getOrDefault(false)
        if (!ok) FirstRun.shot("qp_fail_${what.replace(' ', '_')}")
        assertTrue("$what (after ${ms}ms) · ${FirstRun.onScreen(rule)}", ok)
    }
    private fun pause(ms: Long) { val until = System.currentTimeMillis() + ms; runCatching { rule.waitUntil(ms + 2_000) { System.currentTimeMillis() > until } } }
    private fun playerIds(): List<String> {
        var out = emptyList<String>()
        rule.runOnUiThread { PlaybackService.sharedPlayer?.let { p -> out = (0 until p.mediaItemCount).map { p.getMediaItemAt(it).mediaId } } }
        return out
    }
    private fun current(): String? { var id: String? = null; rule.runOnUiThread { id = PlaybackService.sharedPlayer?.currentMediaItem?.mediaId }; return id }
    private fun playing(): Boolean { var p = false; rule.runOnUiThread { p = PlaybackService.sharedPlayer?.isPlaying == true }; return p }
    private fun vm(): MainViewModel { var vm: MainViewModel? = null; rule.runOnUiThread { vm = ViewModelProvider(rule.activity)[MainViewModel::class.java] }; return vm!! }

    private fun addSong(title: String): Long {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$title.m4a")
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/$FOLDER")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }
        val uri = ctx.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw AssertionError("MediaStore insert failed for $title")
        ctx.contentResolver.openOutputStream(uri)!!.use { o -> inst.context.assets.open("yt_audio_only.m4a").use { it.copyTo(o) } }
        if (Build.VERSION.SDK_INT >= 29) ctx.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        return android.content.ContentUris.parseId(uri)
    }

    private fun removeSongs() {
        notes.getString("ids", null)?.split(',')?.mapNotNull { it.toLongOrNull() }.orEmpty().forEach { id ->
            runCatching { ctx.contentResolver.delete(android.content.ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id), null, null) }
        }
    }

    @Test fun a_queueFiveThenDie() {
        assumeTrue("phase a runs from scripts/device-check.sh", phase == "a")
        val ids = (1..5).map { addSong("YuQP Song $it").toString() }
        notes.edit().putString("ids", ids.joinToString(",")).commit()
        log("QP_A songs=$ids")
        FirstRun.toTabs(rule)
        val vm = vm()
        rule.runOnUiThread { vm.scanMedia() }
        val tracks: List<AudioTrack> = kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.withTimeout(30_000) {
                vm.rawAudioTracks.first { l -> l.count { it.id in ids } == 5 }.filter { it.id in ids }.sortedBy { ids.indexOf(it.id) }
            }
        }
        rule.runOnUiThread { vm.playTrack(tracks[0], tracks); PlaybackService.sharedPlayer?.volume = 0f }
        wait("song 1 playing", 20_000) { current() == ids[0] && playing() }
        pause(2_000)
        rule.runOnUiThread { PlaybackService.sharedPlayer?.seekToNextMediaItem() }
        wait("song 2 playing", 20_000) { current() == ids[1] && playing() }
        pause(1_500)
        // Paused on song 2 (a pause saves Continue), so the 12 s clip cannot run on into song 3 on
        // a slow emulator (run 44251573024, api33-lowram: song 3 had started).
        rule.runOnUiThread { PlaybackService.sharedPlayer?.pause() }
        pause(3_000) // a person does not kill the app within 1 s of a pause
        log("QP_A queue=${playerIds()} continue=${LastPlayed.audio.value?.track?.id}")
        assertEquals("phase a queue", ids, playerIds())
        assertEquals("phase a Continue song", ids[1], LastPlayed.audio.value?.track?.id)
        log("QP_A pid=${android.os.Process.myPid()} kill")
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    @Test fun b_reopenContinueBringsAllFive() {
        assumeTrue("phase b runs from scripts/device-check.sh", phase == "b")
        val ids = notes.getString("ids", null)?.split(',').orEmpty()
        assertEquals("phase a did not finish", 5, ids.size)
        try {
            FirstRun.toTabs(rule)
            wait("Home Continue shows song 2", 30_000) { has("home_continue_play") && hasText("YuQP Song 2") }
            pause(3_000) // the saved queue is read on IO when the app opens
            FirstRun.tapVisible(rule, "home_continue_play")
            wait("song 2 playing from Continue", 20_000) { current() == ids[1] }
            rule.runOnUiThread { PlaybackService.sharedPlayer?.volume = 0f }
            val got = playerIds()
            log("QP_B queue=$got current=${current()} expected=$ids")
            assertEquals("G6-QP queue after kill + Continue", ids, got)
            assertEquals("G6-QP current song", ids[1], current())
            log("QP_RESULT PASS")
        } finally {
            rule.runOnUiThread { PlaybackService.sharedPlayer?.stop(); PlaybackService.sharedPlayer?.clearMediaItems() }
            removeSongs()
            pause(1_000)
        }
    }

    private companion object { const val FOLDER = "YuQueuePersist" }
}
