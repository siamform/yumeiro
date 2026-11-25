/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.media3.ui.PlayerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.player.PlaybackDiagnostics
import dev.asfakul.yumeiro.player.PlaybackSession
import dev.asfakul.yumeiro.youtube.YouTubePlayback
import dev.asfakul.yumeiro.youtube.YouTubeStreams
import dev.asfakul.yumeiro.youtube.YtItem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * H-7 (owner item 7): "YouTube videos don't play properly in full-screen mode". A YouTube video is opened the
 * way the YouTube tab opens it (watch page under a docked player), then put full screen with the player's own
 * button. Checked in full screen, for 6 s: the player box fills the screen, the video surface fills the box
 * (no small picture in a corner), the picture really moves (two screenshots of the surface differ), the
 * position advances, and no error. Then back to the watch page, still playing.
 */
@RunWith(AndroidJUnit4::class)
class GroupHYouTubeFullscreenDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = inst.targetContext
    private val id = "aqz-KE-bpKQ" // Big Buck Bunny (Blender Foundation, CC-BY)

    private fun log(s: String) = Log.i("YuGroupH", s)

    @Before fun setUp() {
        runCatching { inst.uiAutomation.grantRuntimePermission(ctx.packageName, "android.permission.POST_NOTIFICATIONS") }
        FirstRun.toTabs(rule)
        PlaybackDiagnostics.reset("")
    }

    @After fun tearDown() { runCatching { inst.runOnMainSync { PlaybackSession.releaseNow() } } }

    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun position(): Long { var p = -1L; inst.runOnMainSync { p = PlaybackSession.playerForTest?.currentPosition ?: -1L }; return p }
    private fun playing(): Boolean { var p = false; inst.runOnMainSync { p = PlaybackSession.playerForTest?.isPlaying == true }; return p }

    private fun findPlayerView(v: View): PlayerView? {
        if (v is PlayerView) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findPlayerView(v.getChildAt(i))?.let { return it }
        return null
    }

    /** Screen rects of the window, the PlayerView and its video surface. */
    private data class Boxes(val window: Rect, val box: Rect, val surface: Rect)

    private fun boxes(): Boxes? {
        var out: Boxes? = null
        inst.runOnMainSync {
            rule.activityRule.scenario.onActivity { a ->
                val root = a.window.decorView
                val pv = findPlayerView(root) ?: return@onActivity
                fun r(v: View) = IntArray(2).let { xy -> v.getLocationOnScreen(xy); Rect(xy[0], xy[1], xy[0] + v.width, xy[1] + v.height) }
                val sv = pv.videoSurfaceView ?: return@onActivity
                // The surface may be scaled (aspect fit); its drawn size is width × scaleX.
                val s = r(sv).let { b ->
                    val w = (sv.width * sv.scaleX).toInt(); val h = (sv.height * sv.scaleY).toInt()
                    val cx = b.centerX(); val cy = b.centerY(); Rect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
                }
                out = Boxes(r(root), r(pv), s)
            }
        }
        return out
    }

    /** Mean absolute difference (0-255) of a 24 × 14 grid sampled inside [r] between two screenshots. */
    private fun motion(r: Rect): Pair<Double, Double> {
        fun grab(): Bitmap? = inst.uiAutomation.takeScreenshot()
        val a = grab() ?: return 0.0 to 0.0
        Thread.sleep(900)
        val b = grab() ?: return 0.0 to 0.0
        var diff = 0.0; var light = 0.0; var n = 0
        for (i in 1..24) for (j in 1..14) {
            val x = (r.left + r.width() * i / 25).coerceIn(0, a.width - 1)
            val y = (r.top + r.height() * j / 15).coerceIn(0, a.height - 1)
            val p = a.getPixel(x, y); val q = b.getPixel(x, y)
            fun ch(c: Int, s: Int) = (c shr s) and 0xff
            diff += (Math.abs(ch(p, 16) - ch(q, 16)) + Math.abs(ch(p, 8) - ch(q, 8)) + Math.abs(ch(p, 0) - ch(q, 0))) / 3.0
            light += (ch(q, 16) + ch(q, 8) + ch(q, 0)) / 3.0
            n++
        }
        return diff / n to light / n
    }

    private fun toggleFullscreen() {
        repeat(4) {
            if (!has("video_fullscreen_btn")) runCatching { rule.onAllNodesWithTag("video_player_root", true)[0].performClick() }
            if (runCatching { rule.waitUntil(3_000) { has("video_fullscreen_btn") }; true }.getOrDefault(false)) {
                rule.onAllNodesWithTag("video_fullscreen_btn", true)[0].performClick(); return
            }
        }
        throw AssertionError("no full-screen button · ${FirstRun.onScreen(rule)}")
    }

    @Test fun a_youTubeFullscreenKeepsPlayingAndFillsTheScreen() {
        val reachable = runCatching { runBlocking { YouTubeStreams.videoRequest(ctx, id, maxHeight = 360) }; true }
            .onFailure { log("RESULT_H7 SKIP youtube unreachable: $it") }.getOrDefault(false)
        assumeTrue("YouTube unreachable from this emulator", reachable)
        val item = YtItem(id = id, url = "https://www.youtube.com/watch?v=$id", title = "Big Buck Bunny", channel = "Blender")
        rule.runOnUiThread { YouTubePlayback.playVideo(ctx, item, maxHeight = 360) }
        val ok = runCatching { rule.waitUntil(90_000) { PlaybackDiagnostics.state.value.firstFrame }; true }.getOrDefault(false)
        assertTrue("no first frame · ${PlaybackDiagnostics.state.value}", ok)
        Thread.sleep(2_000)
        val docked = boxes()
        log("docked $docked")
        FirstRun.shot("grouph_yt_docked")
        val tv = has("tv_nav_rail")

        if (!tv) toggleFullscreen()
        // Rotation (phones) and the layout change settle.
        Thread.sleep(3_000)
        rule.waitForIdle()
        val p0 = position(); val t0 = System.currentTimeMillis()
        val full = boxes()
        val (moved, light) = full?.let { motion(it.surface) } ?: (0.0 to 0.0)
        Thread.sleep(4_000)
        val p1 = position(); val dt = System.currentTimeMillis() - t0
        val s = PlaybackDiagnostics.state.value
        FirstRun.shot("grouph_yt_fullscreen")
        log("RESULT_H7 full=$full pos $p0->$p1 in ${dt}ms motion=${"%.1f".format(moved)} light=${"%.1f".format(light)} playing=${playing()} err=${s.error} decoder=${s.width}x${s.height}")
        assertTrue("player view found", full != null)
        val f = full!!
        assertTrue("full screen: the player fills the window (box ${f.box} window ${f.window})",
            f.box.width() >= f.window.width() * 0.97 && f.box.height() >= f.window.height() * 0.9)
        assertTrue("full screen: the picture fills the box on one side (surface ${f.surface} box ${f.box})",
            f.surface.width() >= f.box.width() * 0.95 || f.surface.height() >= f.box.height() * 0.95)
        assertTrue("full screen: no error (${s.error})", s.error == null)
        assertTrue("full screen: playback advances ($p0 -> $p1 in $dt ms)", p1 - p0 >= 2_000)
        assertTrue("full screen: the picture moves (diff $moved)", moved > 1.0)
        assertTrue("full screen: the picture is not black (light $light)", light > 12.0)

        if (!tv) {
            toggleFullscreen()
            Thread.sleep(3_000)
            val p2 = position(); Thread.sleep(2_500); val p3 = position()
            log("RESULT_H7 back on the watch page ${boxes()} pos $p2->$p3")
            assertTrue("back on the watch page: still playing ($p2 -> $p3)", p3 - p2 >= 1_000)
        }
    }
}
