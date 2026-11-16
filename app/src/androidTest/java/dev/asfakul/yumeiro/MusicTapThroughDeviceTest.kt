/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.data.model.AudioTrack
import dev.asfakul.yumeiro.player.PlaybackSession
import dev.asfakul.yumeiro.ui.screens.TabPagerProbe
import dev.asfakul.yumeiro.ui.viewmodel.MainViewModel
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * BUG-6 (owner report, docs/proposals/FEATURE-BUG-6-music-player-tap-through.md): with the full
 * Now Playing screen open, a tap on an empty part of it must not reach the Home screen underneath.
 *
 * Opens Home, notes where its clickable cards are, plays a song (the test clip as an audio track)
 * so Now Playing covers Home, then taps every card centre that no Now Playing button covers. After
 * each tap Home must still be the page under the player with no screen opened, and Now Playing
 * still open. Log tag YuTapThrough: TAP_CASE / TAP_RESULT lines, screenshot tap_*.
 */
@RunWith(AndroidJUnit4::class)
class MusicTapThroughDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(s: String) = Log.i("YuTapThrough", s)
    private fun has(tag: String) = runCatching { rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    private fun vm() = ViewModelProvider(rule.activity)[MainViewModel::class.java]
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

    /** Bounds of every clickable node, with its tag or text. */
    private fun clickables(): List<Pair<String, Rect>> =
        rule.onAllNodes(androidx.compose.ui.test.hasClickAction(), true).fetchSemanticsNodes().map { n ->
            val name = n.config.getOrNull(SemanticsProperties.TestTag)
                ?: n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ")?.take(20) ?: "node${n.id}"
            name to n.boundsInRoot
        }

    /** Bounds of the Now Playing screen's own clickable nodes (inside its root). */
    private fun playerButtons(): List<Rect> {
        val root = rule.onAllNodesWithTag("now_playing_screen_root", true).fetchSemanticsNodes().firstOrNull() ?: return emptyList()
        val out = ArrayList<Rect>()
        fun walk(n: androidx.compose.ui.semantics.SemanticsNode) {
            if (n.config.getOrNull(SemanticsActions.OnClick) != null || n.config.getOrNull(SemanticsActions.SetProgress) != null) out += n.boundsInRoot
            n.children.forEach(::walk)
        }
        walk(root)
        return out
    }

    @Test fun tapOnFullNowPlayingNeverReachesHome() {
        runCatching { rule.waitUntil(20_000) { has("home_screen_root") } }
        assumeTrue("Home did not show", has("home_screen_root"))
        val homeCards = clickables().filter { it.first.startsWith("quick_card_") || it.first.startsWith("home_") }
        log("TAP_STEP home cards=${homeCards.map { it.first }}")
        assumeTrue("no clickable Home cards to aim at", homeCards.isNotEmpty())
        val pageBefore = TabPagerProbe.page

        clip = TestClip.put(ctx, NAME)
        val track = AudioTrack(id = "tap_through_test", title = "Yumeiro tap test", artist = "Yumeiro", album = "Test", durationMs = 12_000, uri = clip.toString())
        rule.runOnUiThread { vm().playTrack(track, listOf(track)) }
        rule.waitUntil(20_000) { has("now_playing_screen_root") }
        Thread.sleep(1_200) // slide-in animation done
        rule.waitForIdle()
        val buttons = playerButtons()
        val targets = homeCards.map { it.first to it.second.center }.filter { (_, c) -> buttons.none { it.contains(c) } }
        log("TAP_STEP nowPlaying buttons=${buttons.size} uncovered targets=${targets.map { it.first }}")
        FirstRun.shot("tap_before")
        assumeTrue("every Home card is under a Now Playing button", targets.isNotEmpty())

        val leaked = ArrayList<String>()
        for ((name, c) in targets.take(6)) {
            rule.onRoot(true).performTouchInput { click(Offset(c.x, c.y)) }
            Thread.sleep(1_500)
            rule.waitForIdle()
            val homeStill = has("home_screen_root")
            val npStill = has("now_playing_screen_root")
            val page = TabPagerProbe.page
            log("TAP_CASE target=$name at=${c.x.toInt()},${c.y.toInt()} homeStill=$homeStill nowPlayingStill=$npStill page=$page")
            if (!homeStill || page != pageBefore) {
                leaked += "$name→${FirstRun.onScreen(rule).take(300)}"
                FirstRun.shot("tap_leak_${leaked.size}")
                // Back to Home under the player for the next target.
                rule.runOnUiThread { vm().closeNowPlaying() }
                FirstRun.shell("input keyevent KEYCODE_BACK"); Thread.sleep(800)
                runCatching { rule.waitUntil(10_000) { has("home_screen_root") } }
                rule.runOnUiThread { vm().openNowPlaying() }
                runCatching { rule.waitUntil(10_000) { has("now_playing_screen_root") } }
                Thread.sleep(1_200)
            }
            if (!has("now_playing_screen_root")) { rule.runOnUiThread { vm().openNowPlaying() }; Thread.sleep(1_200) }
        }
        log("TAP_RESULT ${if (leaked.isEmpty()) "PASS" else "FAIL"} tapped=${minOf(6, targets.size)} leaked=${leaked.size} $leaked")
        assertTrue("BUG-6: taps on the full Now Playing screen reached the screen underneath: $leaked", leaked.isEmpty())
    }

    companion object { const val NAME = "yumeiro_test_clip.mp4" }
}
