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
 * GATE-1-ANR: the walk also opens YouTube, and [GateOneExitCheckDeviceTest] (a later class, so a later
 * process) fails when Android recorded an ANR or crash of the app since [GateOnePrepareDeviceTest] ran
 * (ApplicationExitInfo, API 30+). An ANR kills the process that had it, so its record can only be read
 * from the next one.
 * Run: test=dev.asfakul.yumeiro.GateOnePrepareDeviceTest,dev.asfakul.yumeiro.MainThreadStallRealClockDeviceTest,dev.asfakul.yumeiro.GateOneExitCheckDeviceTest
 */
@RunWith(AndroidJUnit4::class)
class GateOnePrepareDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun prepare() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        // No third-party starter extension may run in the gate (B-26 tracks that one on its own).
        ctx.getSharedPreferences("starter_extensions", android.content.Context.MODE_PRIVATE).edit().putBoolean("done_v1", true).commit()
        GateOneExitCheckDeviceTest.markStart(ctx)
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
                // GATE-1-ANR-T4: each step checks it landed and taps once more if the tap was lost
                // (44940680034: Videos tapped during a 1.5 s wait, Home stayed on screen).
                step("Videos") { find("Source:") != null }; SystemClock.sleep(6_000)
                step("Source:") { menuOpen() }; SystemClock.sleep(1_500)
                step("Yumix") { pillSays("Yumix") }; SystemClock.sleep(6_000)
                step("Source:") { menuOpen() }; SystemClock.sleep(1_500)
                step("YouTube") { pillSays("YouTube") }; SystemClock.sleep(6_000)
                step("Source:") { menuOpen() }; SystemClock.sleep(1_500)
                step("Live TV") { pillSays("Live TV") }; SystemClock.sleep(6_000)
                step("Home") { find("Source:") == null }; SystemClock.sleep(5_000)
                // DETECTOR CHECK (ab/gate1anr-before only): block the main thread 12 s while an input waits.
                main.post { log("GATE1_FORCED_BLOCK start"); SystemClock.sleep(12_000) }
                SystemClock.sleep(500); FirstRun.shell("input tap 20 300"); SystemClock.sleep(16_000)
            } finally {
                running.set(false); dog.join(2_000)
            }
            log("GATE1_REAL worst main-thread wait ${worst.get()} ms with 300 providers, real frame clock (limit $MAX_STALL_MS)")
            // GATE-1-ANR: a live "not responding" state of this process (the exit record only exists after it dies).
            val am = ctx.getSystemService(android.app.ActivityManager::class.java)
            val anr = am.processesInErrorState.orEmpty().filter { it.processName.startsWith(ctx.packageName) }
                .map { "condition=${it.condition} ${it.processName}: ${it.shortMsg}" }
            log("GATE1_ANR_STATE ${anr.size} ${anr.joinToString(" | ")}")
            assertTrue("the app is in an error / not-responding state: ${anr.joinToString(" | ")}", anr.isEmpty())
            assertTrue("main thread stalled ${worst.get()} ms on the real frame clock (limit $MAX_STALL_MS ms)", worst.get() <= MAX_STALL_MS)
        } finally {
            SourceHub.remove(StallWalk.BRIDGE)
        }
    }

    @Volatile private var lastCandidates = ""

    /** Reads the screen through accessibility; the watchdog does not count waits that overlap it. */
    private fun find(text: String): Rect? {
        reading.set(true)
        try {
            // GATE-1-ANR-T2: the source menu is a popup window; rootInActiveWindow can stay on the main window
            // (run 44937327148: "no 'YouTube' on screen" with the menu open). Search every window's tree.
            val roots = allRoots().ifEmpty { listOfNotNull(inst.uiAutomation.rootInActiveWindow) }
            if (roots.isEmpty()) return null
            // Compose's node provider does not answer findAccessibilityNodeInfosByText (run 44791259898:
            // "tab bar never showed" with MainActivity displayed); walk the tree like UiAutomator does.
            val nodes = ArrayList<AccessibilityNodeInfo>()
            val queue = ArrayDeque<AccessibilityNodeInfo>().apply { addAll(roots) }
            var seen = 0
            while (queue.isNotEmpty() && seen < 5_000) {
                val n = queue.removeFirst(); seen++
                if (!live(n)) continue
                val t = n.text?.toString().orEmpty(); val d = n.contentDescription?.toString().orEmpty()
                if (t == text || d.startsWith(text) || (text.endsWith(":") && d.startsWith(text))) nodes.add(n)
                for (i in 0 until n.childCount) n.getChild(i)?.let(queue::add)
            }
            val screenH = inst.targetContext.resources.displayMetrics.heightPixels
            val menuRow = text != "Source:" && text != "Home" && text != "Videos"
            // A menu row is never the pill itself nor the pill's own label (an ancestor says "Source: …";
            // run 44939433464 tapped the label at the pill's x/y and closed the menu).
            lastCandidates = nodes.joinToString("; ") { n -> val b = Rect().also { n.getBoundsInScreen(it) }; "'${(n.contentDescription ?: n.text)?.toString()?.take(30)}' $b vis=${n.isVisibleToUser} pill=${underPill(n)}" } +
                " screenH=$screenH seen=$seen windows=${windowsInfo()}"
            val hits = nodes.filter { n -> !menuRow || !underPill(n) }.mapNotNull { n -> Rect().also { n.getBoundsInScreen(it) }.takeIf { it.width() > 1 && it.height() > 1 && it.top >= 0 && it.bottom <= screenH + 1 } }
            // Bottom-bar labels ("Home", "Videos") and menu rows: the lowest match is the tab, a menu row is unique.
            return if (text == "Home" || text == "Videos") hits.maxByOrNull { it.top } else hits.firstOrNull()
        } finally {
            SystemClock.sleep(300) // let the main thread finish the accessibility work before counting again
            reading.set(false)
        }
    }

    /** API 33 has no UiAutomation.clearCache: re-read the node from the app; a node that is gone is skipped. */
    private fun live(n: AccessibilityNodeInfo): Boolean = android.os.Build.VERSION.SDK_INT >= 34 || n.refresh()

    private fun windowsInfo(): String = runCatching {
        inst.uiAutomation.windows.joinToString(",") { w -> val b = Rect().also { w.getBoundsInScreen(it) }; "${w.title}/${w.root?.packageName}/t${w.type}/L${w.layer}/$b/a${w.isActive}/f${w.isFocused}" }
    }.getOrElse { "ERR $it" }

    /** Roots of every window on screen, top-most first (popups, then the app). */
    private fun allRoots(): List<AccessibilityNodeInfo> = runCatching {
        val ua = inst.uiAutomation
        val info = ua.serviceInfo
        if (info != null && (info.flags and android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS) == 0) {
            info.flags = info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            ua.serviceInfo = info
        }
        // GATE-1-ANR-T7: the accessibility cache kept page 0's nodes after the pager settled on Videos
        // (run 44943971093: TabPagerProbe.page 2 while the read tree still listed Home for 18 s).
        // Read the live tree: clear the cache (API 34+), otherwise refresh every node as it is read.
        if (android.os.Build.VERSION.SDK_INT >= 34) ua.clearCache()
        // Only the app's own windows: the system navigation bar also has a "Home" button.
        ua.windows.sortedByDescending { it.layer }.mapNotNull { it.root }.filter { it.packageName?.toString() == inst.targetContext.packageName }
    }.getOrDefault(emptyList())

    private fun underPill(n: AccessibilityNodeInfo): Boolean {
        var cur: AccessibilityNodeInfo? = n
        repeat(6) {
            val c = cur ?: return false
            if (c.contentDescription?.startsWith("Source:") == true) return true
            cur = c.parent
        }
        return false
    }

    private fun texts(): String = runCatching {
        val root = inst.uiAutomation.rootInActiveWindow ?: return "no active window"
        val out = ArrayList<String>(); val q = ArrayDeque<AccessibilityNodeInfo>().apply { addAll(allRoots().ifEmpty { listOf(root) }) }
        while (q.isNotEmpty() && out.size < 60) {
            val n = q.removeFirst()
            if (!live(n)) continue
            (n.text ?: n.contentDescription)?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it.take(40)) }
            for (i in 0 until n.childCount) n.getChild(i)?.let(q::add)
        }
        "${root.packageName}: " + out.joinToString(" | ")
    }.getOrElse { "read failed: $it" }

    /** Taps [text]; when [landed] is not true within 8 s, taps once more and waits 10 s. */
    private fun step(text: String, landed: () -> Boolean) {
        tap(text)
        if (waitFor(8_000, landed)) return
        FirstRun.shot("gate1-miss-${text.trim(':')}-1")
        log("step '$text' did not land (pager page ${dev.asfakul.yumeiro.ui.screens.TabPagerProbe.page}, candidates [$lastCandidates]); tapping again · ${texts()}")
        tap(text)
        if (!waitFor(10_000, landed)) {
            FirstRun.shot("gate1-miss-${text.trim(':')}-2")
            throw AssertionError("tapping '$text' twice did not land (pager page ${dev.asfakul.yumeiro.ui.screens.TabPagerProbe.page}, candidates [$lastCandidates]) · ${texts()}")
        }
    }

    /** The Source pill's description, e.g. "Source: Yumix. Menu open". */
    private fun pillDesc(): String? {
        reading.set(true)
        try {
            val q = ArrayDeque<AccessibilityNodeInfo>().apply { addAll(allRoots().ifEmpty { listOfNotNull(inst.uiAutomation.rootInActiveWindow) }) }
            var seen = 0
            while (q.isNotEmpty() && seen < 5_000) {
                val n = q.removeFirst(); seen++
                if (!live(n)) continue
                n.contentDescription?.toString()?.takeIf { it.startsWith("Source:") }?.let { return it }
                for (i in 0 until n.childCount) n.getChild(i)?.let(q::add)
            }
            return null
        } finally {
            SystemClock.sleep(300)
            reading.set(false)
        }
    }
    private fun menuOpen(): Boolean = pillDesc()?.endsWith("Menu open") == true
    private fun pillSays(label: String): Boolean = pillDesc().let { it != null && it.startsWith("Source: $label.") && !it.endsWith("Menu open") }

    private fun tap(text: String) {
        val r = find(text) ?: run { waitFor(10_000) { find(text) != null }; find(text) } ?: throw AssertionError("no '$text' on screen · candidates [$lastCandidates] · ${texts()}")
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

/**
 * GATE-1-ANR: fails when Android recorded an ANR (or a Java / native crash) of the app after
 * [GateOnePrepareDeviceTest] started. Reads ActivityManager.getHistoricalProcessExitReasons (API 30+;
 * older images skip by assumption). Normal ends of the earlier test processes (force-stop by the runner)
 * are other reasons and do not count.
 */
@RunWith(AndroidJUnit4::class)
class GateOneExitCheckDeviceTest {
    @Test fun noAnrOrCrashSinceTheGateStarted() {
        org.junit.Assume.assumeTrue("ApplicationExitInfo needs API 30", android.os.Build.VERSION.SDK_INT >= 30)
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val since = ctx.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getLong(KEY, 0L)
        assertTrue("run GateOnePrepareDeviceTest first (same filter)", since > 0L)
        val bad = badExits(ctx, since)
        bad.forEach { Log.i("YuGate1", "GATE1_EXIT $it") }
        Log.i("YuGate1", "GATE1_EXITS ${bad.size} ANR / crash records since $since")
        assertTrue("Android recorded ${bad.size} ANR / crash exits during the gate: ${bad.joinToString(" | ")}", bad.isEmpty())
    }

    companion object {
        private const val PREFS = "yu_gate1"
        private const val KEY = "since"

        fun markStart(ctx: android.content.Context) {
            ctx.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().putLong(KEY, System.currentTimeMillis()).commit()
        }

        @androidx.annotation.RequiresApi(30)
        fun badExits(ctx: android.content.Context, since: Long): List<String> {
            val am = ctx.getSystemService(android.app.ActivityManager::class.java)
            val bad = setOf(
                android.app.ApplicationExitInfo.REASON_ANR,
                android.app.ApplicationExitInfo.REASON_CRASH,
                android.app.ApplicationExitInfo.REASON_CRASH_NATIVE,
            )
            return am.getHistoricalProcessExitReasons(ctx.packageName, 0, 0)
                .filter { it.timestamp >= since && it.reason in bad }
                .map { "reason=${it.reason} at ${it.timestamp} process=${it.processName} · ${it.description.orEmpty().take(160)}" }
        }
    }
}
