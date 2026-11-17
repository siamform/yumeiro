/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.data.model.AudioTrack
import dev.asfakul.yumeiro.player.PlaybackSession
import dev.asfakul.yumeiro.ui.viewmodel.MainViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G1 H11 (moved to G6; re-audit): the mini player bar in the real app with a real playing queue.
 * Three tracks (the test clip as audio) play, Now Playing is closed, and the bar is used the way a
 * person does: play/pause, Next, swipe left (next), swipe right (previous), Previous, the
 * accessibility "Next track" action, a tap that opens Now Playing, and Close that stops playback
 * and hides the bar. Each step checks the player itself, not only the screen.
 * Log tag YuMiniTest, MINI_CASE / MINI_RESULT.
 */
@RunWith(AndroidJUnit4::class)
class MiniPlayerDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(s: String) = Log.i("YuMiniTest", s)
    private fun has(tag: String) = runCatching { rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    private fun vm() = ViewModelProvider(rule.activity)[MainViewModel::class.java]
    private fun state() = vm().playbackState.value
    private fun current() = state().currentTrack?.id
    private var clip: android.net.Uri? = null

    @Before fun setUp() {
        listOf("android.permission.READ_MEDIA_AUDIO", "android.permission.READ_MEDIA_VIDEO", "android.permission.READ_EXTERNAL_STORAGE")
            .forEach { runCatching { InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(ctx.packageName, it) } }
        FirstRun.toTabs(rule)
    }

    @After fun tearDown() {
        runCatching { rule.runOnUiThread { vm().stopPlayback() } }
        runCatching { rule.runOnUiThread { PlaybackSession.releaseNow() } }
        clip?.let { TestClip.remove(ctx, it, NAME) }
    }

    private fun waitTrack(id: String, step: String) {
        runCatching { rule.waitUntil(10_000) { current() == id } }
        log("MINI_CASE $step current=${current()} playing=${state().isPlaying}")
        assertEquals("$step: wrong track", id, current())
    }

    private fun swipeBar(fromFrac: Float, toFrac: Float) {
        rule.onNodeWithTag("mini_player_bar", true).performTouchInput {
            swipe(Offset(width * fromFrac, centerY), Offset(width * toFrac, centerY), durationMillis = 300)
        }
        rule.waitForIdle()
    }

    @Test fun miniPlayerControlsThePlayingQueue() {
        runCatching { rule.waitUntil(20_000) { has("home_screen_root") } }
        assumeTrue("Home did not show", has("home_screen_root"))
        clip = TestClip.put(ctx, NAME)
        val tracks = (1..3).map { AudioTrack(id = "mini_t$it", title = "Yumeiro mini $it", artist = "Yumeiro", album = "Test", durationMs = 12_000, uri = clip.toString()) }
        rule.runOnUiThread { vm().playTrack(tracks[0], tracks) }
        runCatching { rule.waitUntil(20_000) { has("now_playing_screen_root") } }
        rule.runOnUiThread { vm().closeNowPlaying() }
        rule.waitUntil(10_000) { has("mini_player_bar") && !has("now_playing_screen_root") }
        runCatching { rule.waitUntil(10_000) { state().isPlaying } }
        log("MINI_CASE a_shown current=${current()} playing=${state().isPlaying}")
        assertEquals("mini_t1", current())
        assertTrue("the queue did not start playing", state().isPlaying)

        // Play / pause.
        rule.onNodeWithTag("mini_player_play_pause_button", true).performClick()
        rule.waitUntil(5_000) { !state().isPlaying }
        rule.onNodeWithTag("mini_player_play_pause_button", true).performClick()
        rule.waitUntil(5_000) { state().isPlaying }
        log("MINI_CASE b_play_pause ok playing=${state().isPlaying}")

        // Next button, swipe left (next), swipe right (previous), Previous button.
        rule.onNodeWithTag("mini_player_next_button", true).performClick()
        waitTrack("mini_t2", "c_next_button")
        // Run 44059862262: a swipe that started at 3 % of the bar began in the bar's start padding (outside the
        // draggable row) and in the system back-gesture edge (a back reached the app), so nothing moved.
        // Both swipes now start and end inside the row, ~40 % of the width (> the 64 dp threshold).
        swipeBar(0.60f, 0.20f)
        waitTrack("mini_t3", "d_swipe_left")
        swipeBar(0.20f, 0.60f)
        waitTrack("mini_t2", "e_swipe_right")
        rule.onNodeWithTag("mini_player_previous_button", true).performClick()
        waitTrack("mini_t1", "f_previous_button")

        // Accessibility custom action "Next track" (TalkBack users).
        val actions = rule.onNodeWithTag("mini_player_bar", true).fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty()
        log("MINI_CASE g_a11y actions=${actions.map { it.label }}")
        val nextAction = actions.firstOrNull { it.label == "Next track" }
        assertTrue("no 'Next track' accessibility action", nextAction != null)
        rule.runOnUiThread { nextAction!!.action() }
        waitTrack("mini_t2", "g_a11y_next")

        // Tap opens Now Playing; closing it brings the bar back with the same track.
        rule.onNodeWithTag("mini_player_bar", true).performClick()
        rule.waitUntil(10_000) { has("now_playing_screen_root") }
        log("MINI_CASE h_open nowPlaying=true current=${current()}")
        rule.runOnUiThread { vm().closeNowPlaying() }
        rule.waitUntil(10_000) { has("mini_player_bar") && !has("now_playing_screen_root") }
        assertEquals("mini_t2", current())

        // Close stops playback and hides the bar.
        rule.onNodeWithTag("mini_player_close_button", true).performClick()
        rule.waitUntil(10_000) { !has("mini_player_bar") }
        Thread.sleep(500)
        log("MINI_CASE i_close bar=${has("mini_player_bar")} current=${current()} playing=${state().isPlaying}")
        assertTrue("still playing after Close", !state().isPlaying)
        FirstRun.shot("mini_after_close")
        log("MINI_RESULT PASS")
    }

    companion object { const val NAME = "yumeiro_test_clip.mp4" }
}
