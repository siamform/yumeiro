/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.player.PlaybackDiagnostics
import dev.asfakul.yumeiro.player.PlaybackSession
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * GROUP K (owner: "Aspect ratio isn't working. The video screen size can't be increased or decreased."),
 * through the real UI on the shared 320×240 (4:3) test clip in the full player:
 *  a: every Aspect ratio choice changes the picture the person sees, measured on the real video view
 *     (laid-out size × scale): Fit = whole 4:3 picture inside the screen, Crop = 4:3 picture covering
 *     the screen, Stretch = the screen size, Original = the clip's own pixel size (smaller than Fit).
 *  b: the picture size can be made bigger and smaller from the Aspect ratio dialog's Zoom − / + buttons
 *     (works with a TV remote, no pinch needed), and Reset brings it back to 100 %. On a SurfaceView
 *     (TV) view scale is not drawn, so only the laid-out size is measured there.
 * Log tag YuGroupK; GROUPK_<x>_RESULT PASS lines are the evidence.
 */
@RunWith(AndroidJUnit4::class)
@org.junit.FixMethodOrder(org.junit.runners.MethodSorters.NAME_ASCENDING)
class GroupKAspectDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val inst get() = InstrumentationRegistry.getInstrumentation()

    private fun log(s: String) = Log.i("YuGroupK", s)
    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun hasText(t: String) = rule.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun wait(what: String, ms: Long = 30_000, cond: () -> Boolean) {
        val ok = runCatching { rule.waitUntil(ms) { cond() }; true }.getOrDefault(false)
        if (!ok) FirstRun.shot("groupk_${what.replace(' ', '_')}")
        assertTrue("$what (after ${ms}ms) · playback=${PlaybackDiagnostics.state.value} · ${FirstRun.onScreen(rule)}", ok)
    }
    private fun pause(ms: Long) { val until = System.currentTimeMillis() + ms; runCatching { rule.waitUntil(ms + 2_000) { System.currentTimeMillis() > until } } }
    /** The aspect dialog scrolls (a plain verticalScroll Column, so FirstRun's lazy-list scrollToNode can't reach
     *  its last row on a landscape phone): bring a dialog row on screen the way a finger would, then tap it. */
    private fun tap(tag: String) {
        if (tag.startsWith("aspect_")) runCatching { rule.waitUntil(8_000) { has(tag) }; rule.onAllNodesWithTag(tag, true)[0].performScrollTo(); rule.waitForIdle() }
        FirstRun.tapVisible(rule, tag)
    }
    private fun back() = FirstRun.shell("input keyevent 4")
    private var clip: Uri? = null

    @Before fun setUp() {
        listOf("android.permission.READ_MEDIA_VIDEO", "android.permission.READ_EXTERNAL_STORAGE", "android.permission.POST_NOTIFICATIONS")
            .forEach { runCatching { inst.uiAutomation.grantRuntimePermission(ctx.packageName, it) } }
        clip = TestClip.put(ctx, NAME, asset = "yumeiro_test_clip.mp4")
        rule.runOnUiThread { PlaybackSession.releaseNow() }
        FirstRun.toTabs(rule)
        PlaybackDiagnostics.reset("")
    }

    @After fun tearDown() {
        runCatching { rule.runOnUiThread { PlaybackSession.releaseNow() } }
        clip?.let { TestClip.remove(ctx, it, NAME) }
    }

    /** Shows the controls and taps [tag], from the More menu when it isn't on the bar. */
    private fun control(tag: String) {
        repeat(5) {
            if (!has(tag) && !has("${tag}_menu") && !has("video_more_btn")) runCatching { rule.onAllNodesWithTag("video_player_root", true)[0].performClick() }
            if (runCatching { rule.waitUntil(3_000) { has(tag) || has("video_more_btn") }; true }.getOrDefault(false)) {
                if (has(tag)) { rule.onAllNodesWithTag(tag, true)[0].performClick(); return }
                if (!has("${tag}_menu")) runCatching { rule.onAllNodesWithTag("video_more_btn", true)[0].performClick() }
                if (runCatching { rule.waitUntil(3_000) { has("${tag}_menu") }; true }.getOrDefault(false)) {
                    rule.onAllNodesWithTag("${tag}_menu", true)[0].performClick(); return
                }
                if (has("video_more_menu")) back()
            }
        }
        FirstRun.shot("groupk_control_missing_$tag")
        throw AssertionError("control '$tag' never showed · ${FirstRun.onScreen(rule)}")
    }

    /** Videos → Device → the clip, then the full player with a frame on screen. */
    private fun openFullPlayer() {
        wait("Videos tab") { FirstRun.hasNav(rule, "nav_tab_videos") }
        tap("nav_tab_videos")
        wait("video source switch", 20_000) { has("video_source_device") || has("video_source_switch") }
        if (!has("video_source_device")) tap("video_source_switch")
        wait("device source", 15_000) { has("video_source_device") }
        tap("video_source_device")
        val title = NAME.substringBeforeLast('.')
        repeat(4) {
            if (hasText(title)) return@repeat
            if (hasText("YumeiroTest")) runCatching { rule.onAllNodesWithText("YumeiroTest", substring = true)[0].performClick() }
            else if (has("video_rescan_btn")) runCatching { tap("video_rescan_btn") }
            runCatching { rule.waitUntil(10_000) { hasText(title) || hasText("YumeiroTest") } }
        }
        wait("the clip in Videos → Device", 30_000) { hasText(title) }
        FirstRun.openClip(rule, title)
        wait("first frame", 60_000) { PlaybackDiagnostics.state.value.firstFrame }
        rule.runOnUiThread { PlaybackSession.playerForTest?.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE }
        // Phones open docked; the TV player is always full screen.
        if (!Edition.isTv && !has("video_vertical_mode")) {
            control("video_fullscreen_btn")
            pause(2_000)
        }
    }

    data class Pic(val boxW: Int, val boxH: Int, val w: Float, val h: Float, val mode: Int) {
        val aspect get() = if (h > 0f) w / h else 0f
        override fun toString() = "box=${boxW}x$boxH pic=${w.toInt()}x${h.toInt()} (aspect ${"%.3f".format(aspect)}) mode=$mode"
    }

    /** The picture as drawn: the video view's laid-out size × its scale, inside the player view. */
    private fun picture(): Pic {
        var out: Pic? = null
        rule.runOnUiThread {
            val pv = findPlayerView(rule.activity.window.decorView) ?: return@runOnUiThread
            val v = pv.videoSurfaceView ?: return@runOnUiThread
            // A SurfaceView (the TV renderer) is composited by the system: view scale is not drawn,
            // so only its laid-out size counts there.
            val sx = if (v is android.view.SurfaceView) 1f else v.scaleX
            val sy = if (v is android.view.SurfaceView) 1f else v.scaleY
            out = Pic(pv.width, pv.height, v.width * sx, v.height * sy, pv.resizeMode)
        }
        return out ?: throw AssertionError("no video view · ${FirstRun.onScreen(rule)}")
    }

    private fun findPlayerView(v: View): PlayerView? {
        if (v is PlayerView && v.isShown && v.width > 0) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findPlayerView(v.getChildAt(i))?.let { return it }
        return null
    }

    private fun pick(mode: Int) {
        control("video_aspect_ratio_btn")
        wait("aspect ratio dialog", 8_000) { has("aspect_ratio_item_$mode") }
        tap("aspect_ratio_item_$mode")
        wait("aspect dialog closed", 5_000) { !has("aspect_ratio_item_$mode") }
        pause(1_500)
    }

    private fun near(a: Float, b: Float, tol: Float = 0.04f) = abs(a - b) <= tol * maxOf(abs(b), 1f)

    @Test fun a_everyAspectChoiceChangesThePicture() {
        openFullPlayer()
        val start = picture()
        log("a start $start")
        val clipAspect = 320f / 240f

        pick(AspectRatioFrameLayout.RESIZE_MODE_FIT)
        val fit = picture(); log("a fit $fit"); FirstRun.shot("groupk_fit")
        pick(AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
        val crop = picture(); log("a crop $crop"); FirstRun.shot("groupk_crop")
        pick(AspectRatioFrameLayout.RESIZE_MODE_FILL)
        val fill = picture(); log("a stretch $fill"); FirstRun.shot("groupk_stretch")
        pick(AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH)
        val orig = picture(); log("a original $orig"); FirstRun.shot("groupk_original")

        assertTrue("Fit keeps 4:3 and fits inside: $fit", near(fit.aspect, clipAspect) && fit.w <= fit.boxW + 2 && fit.h <= fit.boxH + 2)
        assertTrue("Fit touches one edge: $fit", near(fit.w, fit.boxW.toFloat()) || near(fit.h, fit.boxH.toFloat()))
        assertTrue("Crop keeps 4:3 and covers the screen: $crop", near(crop.aspect, clipAspect) && crop.w >= crop.boxW - 2 && crop.h >= crop.boxH - 2)
        assertTrue("Stretch fills the screen: $fill", near(fill.w, fill.boxW.toFloat()) && near(fill.h, fill.boxH.toFloat()))
        assertTrue("Crop is bigger than Fit: fit=$fit crop=$crop", crop.w * crop.h > fit.w * fit.h * 1.05f)
        assertTrue("Original keeps 4:3 and differs from Fit: fit=$fit orig=$orig", near(orig.aspect, clipAspect) && !near(orig.w * orig.h, fit.w * fit.h, 0.05f))
        log("GROUPK_A_RESULT PASS")
    }

    @Test fun b_zoomButtonsMakeThePictureBiggerAndSmaller() {
        openFullPlayer()
        pick(AspectRatioFrameLayout.RESIZE_MODE_FIT)
        val base = picture(); log("b base $base")
        control("video_aspect_ratio_btn")
        wait("zoom in button", 8_000) { has("aspect_zoom_in") }
        repeat(2) { tap("aspect_zoom_in"); pause(400) }
        val bigger = picture(); log("b bigger $bigger"); FirstRun.shot("groupk_zoom_in")
        assertTrue("Zoom + makes the picture bigger: base=$base bigger=$bigger", bigger.w > base.w * 1.1f && near(bigger.aspect, base.aspect))
        tap("aspect_zoom_out"); pause(400)
        val smaller = picture(); log("b smaller $smaller")
        assertTrue("Zoom − makes it smaller again: bigger=$bigger smaller=$smaller", smaller.w < bigger.w * 0.97f)
        assertTrue("never below 100 %", smaller.w >= base.w * 0.99f)
        tap("aspect_zoom_reset"); pause(400)
        val reset = picture(); log("b reset $reset")
        assertTrue("Reset = 100 %: base=$base reset=$reset", near(reset.w, base.w, 0.01f))
        back()
        log("GROUPK_B_RESULT PASS")
    }

    companion object { const val NAME = "yumeiro_groupk_clip.mp4" }
}
