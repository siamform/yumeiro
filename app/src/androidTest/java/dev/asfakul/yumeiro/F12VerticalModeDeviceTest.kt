/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import android.util.Log
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.player.PlaybackDiagnostics
import dev.asfakul.yumeiro.player.PlaybackSession
import dev.asfakul.yumeiro.ui.viewmodel.MainViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F-12 vertical mode (ADR-056), the real app path: two 9:16 clips (180 x 320) in Movies/YumeiroTest,
 * opened from Videos → Device in the normal player.
 *
 * Phones: the player switches to vertical mode by itself (full screen, portrait). A swipe in the
 * centre third goes to the other clip of the queue (up = next, down = previous); a swipe on the
 * left third does not change the video (brightness). Back leaves vertical mode and the same clip
 * keeps playing in the normal player, which does not switch back by itself.
 * TV: vertical mode never starts (TV screens do not rotate; ADR-056).
 * Old code: no vertical mode. Log tag YuF12V.
 */
@RunWith(AndroidJUnit4::class)
class F12VerticalModeDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val names = listOf("yumeiro_f12v_a.mp4", "yumeiro_f12v_b.mp4")
    private val clips = mutableListOf<Uri>()

    private fun log(s: String) = Log.i("YuF12V", s)
    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun hasText(t: String) = rule.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun ok(ms: Long, cond: () -> Boolean) = runCatching { rule.waitUntil(ms) { cond() }; true }.getOrDefault(false)
    private fun wait(what: String, ms: Long = 30_000, cond: () -> Boolean) =
        assertTrue("$what (after ${ms}ms) · playback=${PlaybackDiagnostics.state.value} · ${FirstRun.onScreen(rule)}", ok(ms, cond))
    private fun tap(tag: String) = FirstRun.tapVisible(rule, tag)
    private fun back() = FirstRun.shell("input keyevent 4")
    private fun <T> onPlayer(read: (androidx.media3.exoplayer.ExoPlayer) -> T): T? {
        var out: T? = null
        rule.runOnUiThread { PlaybackSession.playerForTest?.let { out = read(it) } }
        return out
    }
    private fun playingUri(): String? = onPlayer { it.currentMediaItem?.localConfiguration?.uri?.toString() }
    private fun vm() = ViewModelProvider(rule.activity)[MainViewModel::class.java]

    @Before fun setUp() {
        listOf("android.permission.READ_MEDIA_VIDEO", "android.permission.READ_EXTERNAL_STORAGE", "android.permission.POST_NOTIFICATIONS")
            .forEach { runCatching { inst.uiAutomation.grantRuntimePermission(ctx.packageName, it) } }
        FeatureFlags.set(ctx, FeatureFlags.Flag.SHORTS_VERTICAL, true)
        FeatureFlags.set(ctx, FeatureFlags.Flag.VERTICAL_MODE, true)
        names.forEach { clips += TestClip.put(ctx, it, asset = "yumeiro_portrait_clip.mp4") }
        FirstRun.toTabs(rule)
        PlaybackDiagnostics.reset("")
    }

    @After fun tearDown() {
        runCatching { rule.runOnUiThread { PlaybackSession.releaseNow() } }
        clips.forEachIndexed { i, u -> TestClip.remove(ctx, u, names[i]) }
        FeatureFlags.set(ctx, FeatureFlags.Flag.SHORTS_VERTICAL, FeatureFlags.Flag.SHORTS_VERTICAL.default)
        FeatureFlags.set(ctx, FeatureFlags.Flag.VERTICAL_MODE, FeatureFlags.Flag.VERTICAL_MODE.default)
    }

    private fun openFirstClip() {
        wait("Videos tab") { FirstRun.hasNav(rule, "nav_tab_videos") }
        tap("nav_tab_videos")
        wait("video source switch", 20_000) { has("video_source_device") || has("video_source_switch") }
        if (!has("video_source_device")) tap("video_source_switch")
        wait("device source", 15_000) { has("video_source_device") }
        tap("video_source_device")
        val title = names[0].substringBeforeLast('.')
        repeat(4) {
            if (hasText(title)) return@repeat
            if (hasText("YumeiroTest")) runCatching { rule.onAllNodesWithText("YumeiroTest", substring = true)[0].performTouchInput { click(center) } }
            else if (has("video_rescan_btn")) runCatching { tap("video_rescan_btn") }
            ok(10_000) { hasText(title) || hasText("YumeiroTest") }
        }
        wait("the portrait clip in Videos → Device", 30_000) { hasText(title) }
        FirstRun.openClip(rule, title)
        wait("first frame of the portrait clip", 45_000) { PlaybackDiagnostics.state.value.firstFrame }
        onPlayer { it.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE }
        log("playing: ${PlaybackDiagnostics.state.value} uri=${playingUri()}")
    }

    /** A centre swipe (swipeUp / swipeDown use the centre x). */
    private fun swipeCentre(up: Boolean) {
        rule.onAllNodesWithTag("video_player_root", true)[0].performTouchInput {
            if (up) swipeUp(startY = height * 0.75f, endY = height * 0.25f, durationMillis = 250)
            else swipeDown(startY = height * 0.25f, endY = height * 0.75f, durationMillis = 250)
        }
    }

    private fun sideSwipeUp() {
        rule.onAllNodesWithTag("video_player_root", true)[0].performTouchInput {
            val x = width * 0.12f
            down(androidx.compose.ui.geometry.Offset(x, height * 0.75f))
            repeat(10) { k -> moveTo(androidx.compose.ui.geometry.Offset(x, height * (0.75f - 0.05f * (k + 1)))) }
            up()
        }
    }

    @Test fun portraitClipOpensInVerticalModeSwipesTheQueueAndBackReturnsToTheNormalPlayer() {
        openFirstClip()
        wait("player root") { has("video_player_root") }
        val s0 = PlaybackDiagnostics.state.value
        assertEquals("decoder size", 180 to 320, s0.width to s0.height)

        if (Edition.isTv) {
            val entered = ok(5_000) { has("video_vertical_mode") }
            log("F12V_RESULT tv entered=$entered")
            assertTrue("TV must not enter vertical mode", !entered)
            return
        }

        wait("vertical mode on a portrait clip", 8_000) { has("video_vertical_mode") }
        rule.waitForIdle()
        val orient = rule.activity.requestedOrientation
        log("vertical mode on, requestedOrientation=$orient")
        assertEquals("vertical mode stays in portrait", ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT, orient)
        FirstRun.shot("f12v_vertical_mode")

        val firstUri = playingUri()
        val hasNext = vm().nextVideoInQueue() != null
        val hasPrev = vm().previousVideoInQueue() != null
        log("queue next=${vm().nextVideoInQueue()?.title} prev=${vm().previousVideoInQueue()?.title}")
        assertTrue("the second portrait clip is not in the queue", hasNext || hasPrev)

        // A drag on the left third is brightness, never the queue.
        sideSwipeUp()
        Thread.sleep(1_500)
        assertEquals("a left-side drag changed the video", firstUri, playingUri())

        // Centre swipe: up for the next clip (or down for the previous one when this is the last).
        val t0 = android.os.SystemClock.elapsedRealtime()
        swipeCentre(up = hasNext)
        wait("a centre swipe ${if (hasNext) "up" else "down"} moved to the other clip", 15_000) { playingUri() != null && playingUri() != firstUri }
        wait("first frame of the other clip", 20_000) { PlaybackDiagnostics.state.value.firstFrame }
        val swipeMs = android.os.SystemClock.elapsedRealtime() - t0
        wait("still in vertical mode on the other clip", 8_000) { has("video_vertical_mode") }
        log("swiped to ${playingUri()} in ${swipeMs}ms")

        // The other way back to the first clip.
        swipeCentre(up = !hasNext)
        wait("a swipe the other way came back to the first clip", 15_000) { playingUri() == firstUri }
        wait("still in vertical mode", 8_000) { has("video_vertical_mode") }

        // Back: out of vertical mode, the same clip keeps playing in the normal player.
        onPlayer { it.play() }
        Thread.sleep(800)
        val posBefore = onPlayer { it.currentPosition } ?: 0L
        back()
        wait("Back left vertical mode", 5_000) { !has("video_vertical_mode") }
        assertTrue("Back closed the player instead of leaving vertical mode", has("video_player_root"))
        assertEquals("Back changed the clip", firstUri, playingUri())
        val posAfter = onPlayer { it.currentPosition } ?: 0L
        log("back: pos $posBefore -> $posAfter playing=${onPlayer { it.isPlaying }}")
        assertTrue("Back restarted the clip ($posBefore -> $posAfter ms)", posAfter + 1_500 >= posBefore || posBefore > 10_000)
        Thread.sleep(3_000)
        assertTrue("the player went back into vertical mode by itself", !has("video_vertical_mode"))
        FirstRun.shot("f12v_after_back")
        log("F12V_RESULT pass swipeMs=$swipeMs next=$hasNext prev=$hasPrev")
    }
}
