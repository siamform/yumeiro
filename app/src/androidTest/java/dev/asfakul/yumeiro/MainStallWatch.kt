/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onAllNodesWithTag
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Freeze probe for BUG-7: a daemon thread posts to the main thread every ~50 ms and records the worst
 * delay. A delay over [dumpAfterMs] logs the main thread's stack once per stall (FMT_STALL under [tag])
 * and keeps the first one in [firstStall].
 */
class MainStallWatch(private val tag: String, private val dumpAfterMs: Long = 1_500) {
    val maxLag = AtomicLong()
    val firstStall = AtomicReference<String?>(null)
    @Volatile private var on = false

    fun reset() { maxLag.set(0); firstStall.set(null) }

    fun start() {
        on = true
        val h = Handler(Looper.getMainLooper())
        val main = Looper.getMainLooper().thread
        Thread({
            while (on) {
                val t0 = SystemClock.uptimeMillis(); val ran = AtomicLong()
                h.post { ran.set(SystemClock.uptimeMillis()) }
                var dumped = false
                while (ran.get() == 0L && on) {
                    SystemClock.sleep(5)
                    if (!dumped && SystemClock.uptimeMillis() - t0 > dumpAfterMs) {
                        dumped = true
                        val st = main.stackTrace.take(40).joinToString(" <- ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
                        firstStall.compareAndSet(null, st)
                        Log.i(tag, "FMT_STALL main ${SystemClock.uptimeMillis() - t0} ms: $st")
                    }
                }
                val lag = (ran.get().takeIf { it > 0 } ?: SystemClock.uptimeMillis()) - t0
                if (lag > maxLag.get()) maxLag.set(lag)
                SystemClock.sleep(45)
            }
        }, "main-stall-watch").apply { isDaemon = true }.start()
    }

    fun stop() { on = false }
}

/**
 * BUG-7 run 43920651380: the main thread never stalled, yet after an Anime4K level the Quality dialog
 * stayed open and the time label stayed at 00:00 while the player moved 4–6 s. These probes tell
 * which layer stopped: frames the app's window really drew (gfxinfo), the time label the user sees
 * (semantics, needs recomposition), and real touches (input tap at a node's centre).
 */
object UiLiveness {
    private val totalFrames = Regex("Total frames rendered: (\\d+)")

    /** Frames HWUI drew for the app since it started (dumpsys gfxinfo), -1 if unknown. */
    fun framesDrawn(pkg: String): Long = runCatching {
        totalFrames.find(FirstRun.shell("dumpsys gfxinfo $pkg"))?.groupValues?.get(1)?.toLong() ?: -1L
    }.getOrDefault(-1L)

    /** The player's "-mm:ss  /  mm:ss" label and the elapsed label, as the semantics tree has them. */
    fun timeLabels(rule: androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>): String = runCatching {
        rule.onAllNodes(androidx.compose.ui.test.hasText(":", substring = true), true).fetchSemanticsNodes()
            .mapNotNull { n -> n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.joinToString("") { it.text } }
            .filter { Regex("^-?\\d{1,2}:\\d{2}").containsMatchIn(it) }
            .joinToString(" | ")
    }.getOrDefault("?")

    /**
     * Waits [ms] while the Compose test clock keeps producing frames. Run 43920651380: a plain
     * SystemClock.sleep on the test thread stops the test clock, so the screen (time label, an open
     * dialog) looked frozen while the app was fine. Every wait in the BUG-7 tests goes through here.
     */
    fun pump(rule: androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>, ms: Long) {
        runCatching { rule.waitUntil(ms) { false } }
    }

    /**
     * Leaves the player as a user does and checks the app answers: Back (up to three presses) must
     * leave the full player (closed, or the floating window as set in Settings), then a real tap on
     * the floating window's close button must close it. Returns a short report and success.
     */
    fun leaveAndClose(rule: androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>): Pair<Boolean, String> {
        fun has(t: String) = runCatching { rule.onAllNodesWithTag(t, true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        fun left() = !has("video_player_root") || has("floating_close")
        var presses = 0
        while (!left() && presses < 3) { FirstRun.shell("input keyevent KEYCODE_BACK"); presses++; pump(rule, 900) }
        val leftOk = left()
        val floating = has("floating_close")
        if (floating) { tap(rule, "floating_close"); runCatching { rule.waitUntil(5_000) { !has("video_player_root") } } }
        val closed = !has("video_player_root")
        return (leftOk && closed) to "backPresses=$presses left=$leftOk floating=$floating closed=$closed"
    }

    /** A real touch (input tap) at the centre of the first node tagged [tag]; false if it isn't there. */
    fun tap(rule: androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>, tag: String): Boolean {
        val b = runCatching { rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().firstOrNull()?.boundsInWindow }.getOrNull() ?: return false
        FirstRun.shell("input tap ${b.center.x.toInt()} ${b.center.y.toInt()}")
        return true
    }
}
