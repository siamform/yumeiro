/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.FrameMetrics
import android.view.Window
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.asfakul.yumeiro.ui.screens.splash.SplashGate
import dev.asfakul.yumeiro.ui.screens.splash.SplashProbe
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Collections

/**
 * B17 on the device, a real cold launch the way a launcher does it (animations on):
 * a) the in-app splash stays at least 1.5 s, really moves (the mark area changes from frame to frame), and once it
 *    has left no logo is left on screen (3 s of screenshots after it left the tree);
 * b) it renders at 60 fps (FrameMetrics of the app window while it animates, no screenshots taken meanwhile);
 * c) with Remove animations it is still (no change between frames) and still stays 1.5 s.
 * Times come from the app itself ([SplashProbe], System.nanoTime). Log tag YuGroupB17; GB17_RESULT_A/B/C PASS.
 */
@RunWith(AndroidJUnit4::class)
class GroupB17SplashDeviceTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(inst)
    private val ctx get() = inst.targetContext
    private val pkg get() = ctx.packageName
    private fun log(s: String) = Log.i("YuGroupB17", s)
    private val scales = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
    private val saved = HashMap<String, String>()

    @Before fun setUp() {
        FirstRun.grant()
        runCatching { dev.asfakul.yumeiro.notify.ReliabilityGuide.markPrompted(ctx) }
        for (k in scales) saved[k] = FirstRun.shell("settings get global $k").trim()
    }

    @After fun tearDown() {
        for ((k, v) in saved) FirstRun.shell("settings put global $k ${if (v == "null" || v.isBlank()) "1" else v}")
    }

    private fun animations(on: Boolean) { for (k in scales) FirstRun.shell("settings put global $k ${if (on) "1" else "0"}") }

    private fun save(name: String, b: Bitmap) = runCatching {
        val dir = File(ctx.getExternalFilesDir(null), "shots/GroupB17SplashDeviceTest").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 70, it) }
    }

    /** Cold start of the splash in a fresh task (the splash plays once per process, so the gate is reset). */
    private fun launch() {
        device.pressHome(); SystemClock.sleep(600)
        SplashGate.resetForTest(); SplashProbe.reset()
        ctx.startActivity(ctx.packageManager.getLaunchIntentForPackage(pkg)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }

    private fun waitProbe(what: String, ms: Long, ok: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + ms
        while (SystemClock.uptimeMillis() < end) { if (ok()) return; SystemClock.sleep(10) }
        throw AssertionError("timed out waiting for $what (shown=${SplashProbe.shownAtNs} finished=${SplashProbe.finishedAtNs} disposed=${SplashProbe.disposedAtNs})")
    }

    /** Share of the centre that is the mark's amber (as in GroupLSplashDeviceTest). */
    private fun markShare(b: Bitmap): Float {
        val cx = b.width / 2; val cy = b.height / 2; val r = minOf(b.width, b.height) / 6
        var n = 0; var amber = 0
        var y = cy - r
        while (y < cy + r) {
            var x = cx - r
            while (x < cx + r) {
                val c = b.getPixel(x, y)
                val red = (c shr 16) and 0xff; val g = (c shr 8) and 0xff; val bl = c and 0xff
                if (red > 200 && g in 120..215 && bl < 140 && red - bl > 90) amber++
                n++; x += 4
            }
            y += 4
        }
        return if (n == 0) 0f else amber.toFloat() / n
    }

    /** A coarse fingerprint of the mark area: changes when the mark rises or its glow breathes. */
    private fun centreSignature(b: Bitmap): Long {
        val cx = b.width / 2; val cy = b.height / 2; val r = minOf(b.width, b.height) / 4
        var h = 1125899906842597L
        var y = cy - r
        while (y < cy + r) {
            var x = cx - r
            while (x < cx + r) { h = 31 * h + (b.getPixel(x, y) and 0xF0F0F0); x += 6 }
            y += 6
        }
        return h
    }

    private data class Shot(val atNs: Long, val share: Float, val sig: Long)

    /** Screenshots until the splash has been gone for [afterMs]. */
    private fun watch(label: String, afterMs: Long): List<Shot> {
        val out = ArrayList<Shot>()
        val end = SystemClock.uptimeMillis() + 20_000
        var i = 0
        while (SystemClock.uptimeMillis() < end) {
            val d = SplashProbe.disposedAtNs
            if (d != 0L && (System.nanoTime() - d) / 1_000_000 > afterMs) break
            val at = System.nanoTime()
            val shot = inst.uiAutomation.takeScreenshot() ?: continue
            out += Shot(at, markShare(shot), centreSignature(shot))
            if (i % 3 == 0) save("gb17_${label}_${"%02d".format(i)}", shot)
            i++
        }
        return out
    }

    private fun ms(a: Long, b: Long) = (b - a) / 1_000_000

    /** An Android emulator (CI: SwiftShader, no GPU). */
    private fun emulator(): Boolean =
        android.os.Build.HARDWARE.let { it == "ranchu" || it == "goldfish" } || FirstRun.shell("getprop ro.kernel.qemu").trim() == "1"

    @Test fun a_lastsMovesAndLeavesNoLogo() {
        animations(true)
        launch()
        waitProbe("the splash", 30_000) { SplashProbe.shownAtNs != 0L }
        val shots = watch("a", 3_000)
        val shown = SplashProbe.shownAtNs; val finished = SplashProbe.finishedAtNs; val gone = SplashProbe.disposedAtNs
        val leave = SplashProbe.leaveAtNs.takeIf { it != 0L } ?: finished
        // Arrival (the mark moves in place), then the leave (it dissolves), then the hand-over to Home.
        val during = shots.filter { it.atNs in (shown + 150_000_000)..leave }
        // r27: stricter than before: no logo from 100 ms after the hand-over (was: 50 ms after the splash left the
        // tree, which is ~200 ms later), because the logo must be gone before Home takes over.
        val after = shots.filter { it.atNs > finished + 100_000_000 }
        val distinct = during.map { it.sig }.toSet().size
        log("GB17 a held=${ms(shown, finished)}ms arrival=${ms(shown, leave)}ms onScreen=${ms(shown, gone)}ms during=${during.size} distinct=$distinct " +
            "shareDuring=${during.map { "%.2f".format(it.share) }} after=${after.size} shareAfter=${after.take(8).map { "%.2f".format(it.share) }}")
        assertTrue("splash held only ${ms(shown, finished)} ms", ms(shown, finished) >= 1500)
        assertTrue("too few screenshots during the splash (${during.size})", during.size >= 3)
        assertTrue("the mark was not on screen during the splash", during.count { it.share > 0.12f } >= during.size / 2)
        assertTrue("the splash did not move ($distinct different frames of ${during.size})", distinct >= 3)
        assertTrue("too few screenshots after the splash (${after.size})", after.size >= 3)
        assertTrue("a logo was left on screen after the splash: ${after.map { it.share }}", after.none { it.share > 0.12f })
        log("GB17_RESULT_A PASS")
    }

    @Test fun b_rendersAtSixtyFps() {
        animations(true)
        val frames = Collections.synchronizedList(ArrayList<LongArray>()) // [vsync ns, total ns, UI-thread work ns]
        val thread = HandlerThread("gb17-frames").apply { start() }
        val listener = Window.OnFrameMetricsAvailableListener { _, m, _ ->
            val ui = m.getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION) + m.getMetric(FrameMetrics.INPUT_HANDLING_DURATION) +
                m.getMetric(FrameMetrics.ANIMATION_DURATION) + m.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION) +
                m.getMetric(FrameMetrics.DRAW_DURATION) + m.getMetric(FrameMetrics.SYNC_DURATION)
            frames += longArrayOf(m.getMetric(FrameMetrics.VSYNC_TIMESTAMP), m.getMetric(FrameMetrics.TOTAL_DURATION), ui)
        }
        val app = ctx.applicationContext as Application
        val cb = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(a: Activity, b: Bundle?) { if (a is MainActivity) a.window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper)) }
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        }
        app.registerActivityLifecycleCallbacks(cb)
        try {
            launch()
            waitProbe("the splash", 30_000) { SplashProbe.shownAtNs != 0L }
            waitProbe("the splash to finish", 20_000) { SplashProbe.finishedAtNs != 0L }
            SystemClock.sleep(300)
        } finally { app.unregisterActivityLifecycleCallbacks(cb) }
        // The animated part starts once start-up work is done (SplashTiming.calm), not when the splash appears.
        val shown = SplashProbe.motionAtNs.takeIf { it != 0L } ?: SplashProbe.shownAtNs
        log("GB17 b settle=${ms(SplashProbe.shownAtNs, shown)}ms")
        val from = shown; val to = shown + dev.asfakul.yumeiro.ui.screens.splash.SplashTiming.ANIM_MS * 1_000_000L
        val w = synchronized(frames) { frames.filter { it[0] in from..to }.sortedBy { it[0] } }
        val gaps = w.zipWithNext { x, y -> (y[0] - x[0]) / 1_000_000.0 }.sorted()
        val median = gaps.getOrNull(gaps.size / 2) ?: 999.0
        val seconds = (to - from) / 1e9
        val fps = w.size / seconds
        val slow = w.count { it[1] > 16_700_000L }
        // The app's own work per frame on the UI thread (waiting for the thread, input, animation, layout, draw,
        // sync), without the GPU part (command issue, swap, GPU), which on CI is SwiftShader on a 2-core runner.
        val work = w.map { it[2] / 1_000_000.0 }.sorted()
        val workP90 = work.getOrNull(work.size * 9 / 10) ?: 999.0
        val refresh = runCatching { (ctx.getSystemService(android.hardware.display.DisplayManager::class.java)).getDisplay(0).refreshRate }.getOrDefault(60f)
        val softwareGpu = emulator()
        log("GB17 b frames=${w.size} fps=${"%.1f".format(fps)} medianGap=${"%.2f".format(median)}ms slow=$slow p90Gap=${"%.2f".format(gaps.getOrNull(gaps.size * 9 / 10) ?: 0.0)}ms " +
            "appWorkP90=${"%.2f".format(workP90)}ms appWorkMax=${"%.2f".format(work.lastOrNull() ?: 0.0)}ms refresh=$refresh softwareGpu=$softwareGpu")
        thread.quitSafely()
        assertTrue("too few frames measured (${w.size})", w.size >= 8)
        if (softwareGpu) {
            // CI emulators have no GPU: the display rate there is the software renderer's, not the app's
            // (runs 46078131306: 14.7 fps api36 / 8.7 fps low-RAM with the main thread idle). What the app controls
            // is measured instead: 90 % of frames need at most one 60 fps frame of UI-thread work. Real-GPU
            // cadence: MANUAL_TESTS B17-FPS-REAL (Test Lab needs an IAM role, see the audit).
            assertTrue("app work p90 ${"%.2f".format(workP90)} ms is over one 60 fps frame", workP90 <= 1000.0 / 60)
        } else {
            assertTrue("frame cadence ${"%.2f".format(median)} ms is below 60 fps", median <= 1000.0 / 60 * 1.05)
            assertTrue("only ${"%.1f".format(fps)} fps while the splash animates", fps >= 54.0)
        }
        log("GB17_RESULT_B PASS")
    }

    @Test fun c_reducedMotionIsStillAndStillLasts() {
        animations(false)
        launch()
        waitProbe("the splash", 30_000) { SplashProbe.shownAtNs != 0L }
        val shots = watch("c", 1_000)
        val shown = SplashProbe.shownAtNs; val finished = SplashProbe.finishedAtNs
        val during = shots.filter { it.atNs in (shown + 150_000_000)..(finished - 50_000_000) }
        val distinct = during.map { it.sig }.toSet().size
        log("GB17 c held=${ms(shown, finished)}ms during=${during.size} distinct=$distinct")
        assertTrue("reduced-motion splash held only ${ms(shown, finished)} ms", ms(shown, finished) >= 1500)
        assertTrue("too few screenshots (${during.size})", during.size >= 3)
        assertTrue("the splash moved with Remove animations on ($distinct different frames)", distinct <= 1)
        device.wait(Until.hasObject(By.pkg(pkg).depth(0)), 5_000)
        log("GB17_RESULT_C PASS")
    }
}
