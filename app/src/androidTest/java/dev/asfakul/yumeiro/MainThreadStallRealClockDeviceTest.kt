/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.sources.SourceHub
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * GATE-1, real frame clock. Runs 44786829781 and 44789029823 showed every long wait of the Compose-rule
 * gate inside `AbstractMainTestClock.advanceTimeByFrame`: the test rule runs the app's frames from the
 * test thread, several in one main-thread message, and the control walk without providers also went
 * over the limit (638–1160 ms). That measures the harness, not the app.
 *
 * This pair measures the app as a user meets it: [GateOnePrepareDeviceTest] answers the first run with
 * the Compose rule (data is kept for the next class when REPEAT=1), then [MainThreadStallRealClockDeviceTest]
 * starts MainActivity with `am start` and no Compose rule (real Choreographer frames), publishes 300
 * providers, and walks Home → Videos → Yumix → Live TV → Home with real touches (`input tap`). The
 * watchdog posts to the main thread every 100 ms and fails over [MAX_STALL_MS]; waits that began while
 * the test itself was reading the screen (accessibility query) are not counted. Log tag YuGate1.
 * Run: test=dev.asfakul.yumeiro.GateOnePrepareDeviceTest,dev.asfakul.yumeiro.MainThreadStallRealClockDeviceTest
 */
@RunWith(AndroidJUnit4::class)
class GateOnePrepareDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun prepare() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        // No third-party starter extension may run in the gate (B-26 tracks that one on its own).
        ctx.getSharedPreferences("starter_extensions", android.content.Context.MODE_PRIVATE).edit().putBoolean("done_v1", true).commit()
        FirstRun.toTabs(rule)
        Log.i("YuGate1", "GATE1_PREPARED first run answered")
    }
}

@RunWith(AndroidJUnit4::class)
class MainThreadStallRealClockDeviceTest {
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private fun log(s: String) = Log.i("YuGate1", s)

    private val reading = AtomicBoolean(false)

    @Test fun threeHundredProvidersDoNotStallTheRealMainThread() {
        val ctx = inst.targetContext
        val prepared = ctx.getSharedPreferences("starter_extensions", android.content.Context.MODE_PRIVATE).getBoolean("done_v1", false)
        assertTrue("run GateOnePrepareDeviceTest first (same filter, before this class)", prepared)
        SourceHub.publishVideo(StallWalk.BRIDGE, SourceHub.Ecosystem.JAVASCRIPT, (1..300).map { RealFake(it) })
        try {
            FirstRun.shell("am start -W -n ${ctx.packageName}/dev.asfakul.yumeiro.MainActivity")
            if (!waitFor(30_000) { find("Home") != null }) throw AssertionError("tab bar never showed · on screen: ${texts()}")
            SystemClock.sleep(3_000) // first draw done; measuring starts below

            val worst = AtomicLong(0L)
            val running = AtomicBoolean(true)
            val main = Handler(Looper.getMainLooper())
            val dog = Thread({
                while (running.get()) {
                    val readingAtSend = reading.get()
                    val sent = SystemClock.uptimeMillis()
                    val ran = AtomicLong(0L)
                    main.post { ran.set(SystemClock.uptimeMillis()) }
                    var sampled = 0
                    while (running.get() && ran.get() == 0L) {
                        Thread.sleep(10)
                        val waited = SystemClock.uptimeMillis() - sent
                        if (waited >= 300 + 250L * sampled && sampled < 8) {
                            sampled++
                            val all = Looper.getMainLooper().thread.stackTrace
                            val keep = all.withIndex().filter { (i, f) -> i < 12 || KEEP.containsMatchIn(f.className) }
                            log("STALL_SAMPLE_REAL $waited ms reading=${reading.get()}:\n    at " + keep.joinToString("\n    at ") { (i, f) -> "#$i $f" })
                        }
                    }
                    if (ran.get() > 0L) {
                        val wait = ran.get() - sent
                        if (!readingAtSend && !reading.get() && wait > worst.get()) { worst.set(wait); log("real main thread waited $wait ms") }
                    }
                    Thread.sleep(100)
                }
            }, "yu-gate1-real").apply { start() }
            try {
                SystemClock.sleep(4_000) // Home
                tap("Videos"); SystemClock.sleep(6_000)
                tap("Source:"); SystemClock.sleep(1_500)
                tap("Yumix"); SystemClock.sleep(6_000)
                tap("Source:"); SystemClock.sleep(1_500)
                tap("Live TV"); SystemClock.sleep(6_000)
                tap("Home"); SystemClock.sleep(5_000)
            } finally {
                running.set(false); dog.join(2_000)
            }
            log("GATE1_REAL worst main-thread wait ${worst.get()} ms with 300 providers, real frame clock (limit $MAX_STALL_MS)")
            assertTrue("main thread stalled ${worst.get()} ms on the real frame clock (limit $MAX_STALL_MS ms)", worst.get() <= MAX_STALL_MS)
        } finally {
            SourceHub.remove(StallWalk.BRIDGE)
        }
    }

    /** Reads the screen through accessibility; the watchdog does not count waits that overlap it. */
    private fun find(text: String): Rect? {
        reading.set(true)
        try {
            val root: AccessibilityNodeInfo = inst.uiAutomation.rootInActiveWindow ?: return null
            // Compose's node provider does not answer findAccessibilityNodeInfosByText (run 44791259898:
            // "tab bar never showed" with MainActivity displayed); walk the tree like UiAutomator does.
            val nodes = ArrayList<AccessibilityNodeInfo>()
            val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
            var seen = 0
            while (queue.isNotEmpty() && seen < 5_000) {
                val n = queue.removeFirst(); seen++
                val t = n.text?.toString().orEmpty(); val d = n.contentDescription?.toString().orEmpty()
                if (t == text || d.startsWith(text) || (text.endsWith(":") && d.startsWith(text))) nodes.add(n)
                for (i in 0 until n.childCount) n.getChild(i)?.let(queue::add)
            }
            val screenH = inst.targetContext.resources.displayMetrics.heightPixels
            val menuRow = text != "Source:" && text != "Home" && text != "Videos"
            val hits = nodes.filter { n -> !menuRow || n.contentDescription?.startsWith("Source:") != true }.mapNotNull { n -> Rect().also { n.getBoundsInScreen(it) }.takeIf { it.width() > 1 && it.height() > 1 && it.top >= 0 && it.bottom <= screenH + 1 } }
            // Bottom-bar labels ("Home", "Videos") and menu rows: the lowest match is the tab, a menu row is unique.
            return if (text == "Home" || text == "Videos") hits.maxByOrNull { it.top } else hits.firstOrNull()
        } finally {
            SystemClock.sleep(300) // let the main thread finish the accessibility work before counting again
            reading.set(false)
        }
    }

    private fun texts(): String = runCatching {
        val root = inst.uiAutomation.rootInActiveWindow ?: return "no active window"
        val out = ArrayList<String>(); val q = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        while (q.isNotEmpty() && out.size < 40) {
            val n = q.removeFirst()
            (n.text ?: n.contentDescription)?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it.take(40)) }
            for (i in 0 until n.childCount) n.getChild(i)?.let(q::add)
        }
        "${root.packageName}: " + out.joinToString(" | ")
    }.getOrElse { "read failed: $it" }

    private fun tap(text: String) {
        val r = find(text) ?: run { waitFor(10_000) { find(text) != null }; find(text) } ?: throw AssertionError("no '$text' on screen · ${texts()}")
        FirstRun.shell("input tap ${r.centerX()} ${r.centerY()}")
        log("tapped '$text' at ${r.centerX()},${r.centerY()}")
    }

    private fun waitFor(ms: Long, ok: () -> Boolean): Boolean {
        val end = SystemClock.uptimeMillis() + ms
        while (SystemClock.uptimeMillis() < end) { if (runCatching(ok).getOrDefault(false)) return true; SystemClock.sleep(500) }
        return false
    }

    private class RealFake(n: Int) : com.lagradost.cloudstream3.MainAPI() {
        override var name = "YuGate1 fake $n"
        override var mainUrl = "https://example.invalid/$n"
        override val supportedTypes = setOf(com.lagradost.cloudstream3.TvType.Movie)
        override val hasMainPage = false
    }

    companion object {
        const val MAX_STALL_MS = 700L
        private val KEEP = Regex("^(dev\\.asfakul|com\\.|org\\.chromium\\.android_webview|androidx\\.compose\\.ui\\.test\\.(Main|Abstract|AndroidCompose))")
    }
}
