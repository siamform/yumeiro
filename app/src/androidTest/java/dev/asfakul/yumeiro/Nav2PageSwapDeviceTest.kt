/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * NAV-2 (owner list 2) on a real device: "a Settings sub-page shows the old page for about 2 s".
 *
 * The app runs on its own clock (no Compose test rule, which drives animations with a test clock), the test taps a
 * Settings category the way a finger does and takes system screenshots as fast as the device allows for 2.5 s. Each
 * frame is compared with the settled sub-page (taken at 3 s) and with Settings home taken before the tap. The page
 * swap is 420 ms ([dev.asfakul.yumeiro.ui.components.PageMotion.PUSH_MS]); the test fails when any frame later than
 * 1 s after the tap still looks more like Settings home than like the new page, or when the new page is not settled
 * by 1.2 s. The same is checked for Back. Frames and numbers go to files/shots and log tag YuNav2.
 */
@RunWith(AndroidJUnit4::class)
class Nav2PageSwapDeviceTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(inst)
    private val pkg = inst.targetContext.packageName
    private fun log(s: String) = Log.i("YuNav2", s)

    private fun shot(): Bitmap? = inst.uiAutomation.takeScreenshot()

    private fun save(name: String, b: Bitmap) = runCatching {
        val dir = File(inst.targetContext.getExternalFilesDir(null), "shots").apply { mkdirs() }
        File(dir, "nav2_$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 80, it) }
    }

    /** Share of pixels (on a 90×200 grid, below the status bar) that match within 28 per channel. */
    private fun same(a: Bitmap, b: Bitmap): Double {
        val w = minOf(a.width, b.width); val h = minOf(a.height, b.height)
        val top = h / 20
        var ok = 0; var n = 0
        for (gy in 0 until 200) for (gx in 0 until 90) {
            val x = gx * (w - 1) / 89; val y = top + gy * (h - 1 - top) / 199
            val p = a.getPixel(x, y); val q = b.getPixel(x, y)
            val d = maxOf(kotlin.math.abs((p shr 16 and 255) - (q shr 16 and 255)),
                kotlin.math.abs((p shr 8 and 255) - (q shr 8 and 255)), kotlin.math.abs((p and 255) - (q and 255)))
            if (d <= 28) ok++
            n++
        }
        return ok.toDouble() / n
    }

    private fun launch() {
        FirstRun.grant()
        val i = inst.targetContext.packageManager.getLaunchIntentForPackage(pkg)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        inst.targetContext.startActivity(i)
        device.wait(Until.hasObject(By.pkg(pkg).depth(0)), 60_000)
        // A fresh install opens on the welcome guide (several pages); one-time prompts may follow. Tap past them
        // the way a user does until the Home top bar (its Settings button) is there.
        val end = SystemClock.uptimeMillis() + 120_000
        while (SystemClock.uptimeMillis() < end && device.findObject(By.desc("Open Settings")) == null) {
            val o = PAST.firstNotNullOfOrNull { device.findObject(By.text(it)) }
            if (o != null) runCatching { o.click() } else device.pressBack().also { SystemClock.sleep(400) }
            SystemClock.sleep(1_500)
            if (device.currentPackageName != pkg) inst.targetContext.startActivity(i)
        }
        if (device.findObject(By.desc("Open Settings")) == null) {
            val out = java.io.ByteArrayOutputStream(); runCatching { device.dumpWindowHierarchy(out) }
            log("no Settings button; screen: " + out.toString().take(3_000))
        }
    }

    /** Buttons that close the welcome guide and the one-time prompts (the probe's list). */
    private val PAST = listOf("Skip", "Skip for now", "Skip tour", "Not now", "Maybe later", "Later", "Remind me later",
        "No thanks", "Close", "All set", "Got it", "Allow", "Done", "Get started")

    /** Opens Settings home from Home; closes a prompt that pops up over Home first (they come a few seconds late). */
    private fun openSettings(): Boolean {
        val end = SystemClock.uptimeMillis() + 90_000
        while (SystemClock.uptimeMillis() < end) {
            if (device.hasObject(By.text("Look & feel"))) return true
            val gear = device.findObject(By.desc("Open Settings"))
            if (gear != null) {
                runCatching { gear.click() }
                if (device.wait(Until.hasObject(By.text("Look & feel")), 6_000) == true) return true
            }
            PAST.firstNotNullOfOrNull { device.findObject(By.text(it)) }?.let { runCatching { it.click() } }
            SystemClock.sleep(1_000)
        }
        val out = java.io.ByteArrayOutputStream(); runCatching { device.dumpWindowHierarchy(out) }
        log("Settings did not open; screen: " + out.toString().take(3_000))
        return false
    }

    private class Swap(val settledMs: Long, val lastLikeOldMs: Long, val frames: Int, val maxGapMs: Long)

    /** Longest gap between two app frames (Choreographer, app main thread) while [block] runs. */
    private fun <T> frameGaps(block: () -> T): Pair<T, Long> {
        val run = java.util.concurrent.atomic.AtomicBoolean(true)
        val max = java.util.concurrent.atomic.AtomicLong(0)
        val last = java.util.concurrent.atomic.AtomicLong(0)
        val cb = object : android.view.Choreographer.FrameCallback {
            override fun doFrame(nanos: Long) {
                val prev = last.getAndSet(nanos)
                if (prev != 0L) max.accumulateAndGet((nanos - prev) / 1_000_000) { a, b -> maxOf(a, b) }
                if (run.get()) android.view.Choreographer.getInstance().postFrameCallback(this)
            }
        }
        inst.runOnMainSync { android.view.Choreographer.getInstance().postFrameCallback(cb) }
        val r = block()
        run.set(false)
        return r to max.get()
    }

    /** Takes frames for 2.5 s after [action] and compares them with [old] and the page settled at 3 s. */
    private fun measure(name: String, old: Bitmap, action: () -> Unit): Swap {
        val frames = ArrayList<Pair<Long, Bitmap>>()
        val t0 = SystemClock.uptimeMillis()
        val (_, gap) = frameGaps {
            action()
            while (SystemClock.uptimeMillis() - t0 < 2_500) shot()?.let { frames += (SystemClock.uptimeMillis() - t0) to it }
        }
        SystemClock.sleep((3_000 - (SystemClock.uptimeMillis() - t0)).coerceAtLeast(0))
        val fin = shot()!!
        save("${name}_old", old); save("${name}_final", fin)
        var settled = -1L; var lastOld = -1L
        frames.forEachIndexed { i, (t, b) ->
            val toNew = same(b, fin); val toOld = same(b, old)
            log("$name frame $i t=$t ms like-new=${"%.3f".format(toNew)} like-old=${"%.3f".format(toOld)}")
            if (toOld > toNew + 0.02) lastOld = t
            if (settled < 0 && toNew >= 0.985) settled = t
            if (i % 3 == 0 || t in 300..1_300) save("${name}_${t}ms", b)
        }
        log("$name: ${frames.size} frames, settled at $settled ms, last frame like the old page at $lastOld ms, longest app frame gap $gap ms, old~new ${"%.3f".format(same(old, fin))}")
        return Swap(settled, lastOld, frames.size, gap)
    }

    @Test fun settingsSubPagesReplaceTheOldPageAtOnce() {
        launch()
        assertTrue("Settings never opened", openSettings())
        // A just-booted emulator is still busy with Play services' boot work; let it settle like a phone in use.
        SystemClock.sleep(20_000)
        val fails = ArrayList<String>()
        for (round in 1..2) for (label in listOf("Look & feel", "Video quality & playback", "Profiles")) {
            device.wait(Until.hasObject(By.text(label)), 10_000)
            SystemClock.sleep(1_000)
            val home = shot()!!
            val key = "r${round}_" + label.filter { it.isLetter() }.lowercase()
            val open = measure("open_$key", home) { device.findObject(By.text(label))?.click() }
            if (open.settledMs !in 0..1_200) fails += "$label (round $round): new page settled at ${open.settledMs} ms (app frame gap ${open.maxGapMs} ms)"
            if (open.lastLikeOldMs > 1_000) fails += "$label (round $round): Settings home still showing at ${open.lastLikeOldMs} ms"
            val page = shot()!!
            val back = measure("back_$key", page) { device.pressBack() }
            if (back.settledMs !in 0..1_200) fails += "$label Back (round $round): home settled at ${back.settledMs} ms (app frame gap ${back.maxGapMs} ms)"
            if (back.lastLikeOldMs > 1_000) fails += "$label Back (round $round): the closing page still showing at ${back.lastLikeOldMs} ms"
            assertTrue("Back from $label did not reach Settings home", device.wait(Until.hasObject(By.text("Look & feel")), 10_000))
            log("round $round $label: open settled ${open.settledMs} ms (${open.frames} frames, app gap ${open.maxGapMs} ms), back settled ${back.settledMs} ms (${back.frames} frames, app gap ${back.maxGapMs} ms)")
        }
        assertTrue(fails.joinToString("; "), fails.isEmpty())
    }
}
