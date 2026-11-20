/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.os.Debug
import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.player.PlaybackDiagnostics
import dev.asfakul.yumeiro.player.PlaybackSession
import dev.asfakul.yumeiro.stream.IncomingMedia
import dev.asfakul.yumeiro.stream.StreamRequest
import dev.asfakul.yumeiro.ui.viewmodel.MainViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * R15 (ADR-006) on a device, through the real app: three videos in a row use one player (no
 * second player next to the first), closing releases it after the short grace, and reopening the
 * same video inside the grace resumes it without a reload.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackSessionDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val log = StringBuilder()

    @Before fun clean() {
        rule.runOnUiThread { PlaybackSession.releaseNow() }
        PlaybackDiagnostics.reset("")
    }

    private fun has(tag: String) = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun pssMb(): Int { val m = Debug.MemoryInfo(); Debug.getMemoryInfo(m); return m.totalPss / 1024 }
    private fun note(s: String) { val l = "$s pss=${pssMb()}MB created=${PlaybackSession.created} reused=${PlaybackSession.reused} resumed=${PlaybackSession.resumedCount} alive=${PlaybackSession.alive}"; Log.i("YuPlayerTest", l); log.append(l).append('\n') }

    private fun play(url: String, title: String) {
        PlaybackDiagnostics.reset("")
        rule.runOnUiThread { IncomingMedia.open(ctx, StreamRequest(url = url, title = title)) }
        rule.waitUntil(75_000) { PlaybackDiagnostics.state.value.firstFrame || PlaybackDiagnostics.state.value.error != null }
        assertTrue("first frame for $title: ${PlaybackDiagnostics.state.value}", PlaybackDiagnostics.state.value.firstFrame)
    }

    private fun closePlayer() = rule.runOnUiThread { ViewModelProvider(rule.activity)[MainViewModel::class.java].closeVideoPlayer() }

    @Test fun oneplayerForEveryVideoAndReleaseAfterClose() {
        // FINAL-23b (runs 44659067541, 44661962064): P1 starts each class from cleared app data, so the app
        // asks for media and notifications at launch and Android's dialog covers MainActivity ("No compose
        // hierarchies found"). FirstRun.toTabs grants them, closes the dialog and gets past first run.
        FirstRun.toTabs(rule)
        rule.waitUntil(30_000) { has("nav_tab_videos") || has("nav_tab_home") }
        val c0 = PlaybackSession.created; val r0 = PlaybackSession.reused
        note("start")
        play("https://storage.googleapis.com/exoplayer-test-media-1/mp4/frame-counter-one-hour.mp4", "Session A"); note("A playing")
        play("https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4", "Session B"); note("B playing")
        play("https://storage.googleapis.com/exoplayer-test-media-0/shortform_1.mp4", "Session C"); note("C playing")
        assertEquals("one player built for three videos", 1, PlaybackSession.created - c0)
        assertEquals("the next two reused it", 2, PlaybackSession.reused - r0)
        assertEquals(1, PlaybackSession.alive)

        // Close, then reopen the same video inside the grace: resumed, no reload.
        closePlayer()
        rule.waitUntil(5_000) { !has("video_player_root") }
        note("closed")
        assertEquals("kept during the grace", 1, PlaybackSession.alive)
        val res0 = PlaybackSession.resumedCount
        rule.runOnUiThread { IncomingMedia.open(ctx, StreamRequest(url = "https://storage.googleapis.com/exoplayer-test-media-0/shortform_1.mp4", title = "Session C")) }
        rule.waitUntil(15_000) { PlaybackSession.resumedCount > res0 }
        var playing = false
        rule.waitUntil(20_000) { rule.runOnUiThread { playing = PlaybackSession.isPlaying() }; playing }
        note("C resumed")
        assertEquals(1, PlaybackSession.created - c0)

        // Close and wait out the grace: the player is gone.
        closePlayer()
        rule.waitUntil(PlaybackSession.GRACE_MS + 5_000) { PlaybackSession.alive == 0 }
        note("released after grace")
        File(File(ctx.getExternalFilesDir(null), "stress").apply { mkdirs() }, "playback-session-report.txt").writeText(log.toString())
        Log.i("YuPlayerTest", "PlaybackSession PASS")
    }
}
