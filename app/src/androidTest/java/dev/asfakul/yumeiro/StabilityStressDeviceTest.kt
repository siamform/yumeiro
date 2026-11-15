/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.online.ExtensionSources
import dev.asfakul.yumeiro.online.OnlineRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * R15 Phase 1 evidence on a real (emulated) device — not a simulation.
 *
 * While the test swipes between tabs and pager pages, flings feeds and moves D-pad focus (the
 * Xiaomi ANR was a focus event), two background threads watch the main thread:
 *  - a stack sampler (every 3 ms) that records any frame inside `java.util.regex.Pattern`
 *    compilation, with the app frame that caused it;
 *  - a stall meter that measures how late a main-thread message runs (an ANR is ≥ 5 s for input).
 * Heap, native heap, PSS and thread families are logged at each step. Everything lands in
 * `files/stress/report.txt` (pulled as a workflow artifact) and in logcat (tag `YuStress`).
 *
 * Pass criteria (Phase 1 acceptance): zero regex compilations on the main thread caused by app
 * code, no main-thread stall ≥ 4 s, and the process survives the whole run (no OOM).
 */
@RunWith(AndroidJUnit4::class)
class StabilityStressDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val report = StringBuilder()
    @Volatile private var watching = false
    private val regexHits = ConcurrentHashMap<String, AtomicInteger>()
    private val samples = AtomicLong()
    private val maxStall = AtomicLong()
    private val stalls = AtomicInteger()

    private fun log(line: String) { Log.i("YuStress", line); synchronized(report) { report.append(line).append('\n') } }

    @Before fun setUp() {
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf("android.permission.READ_MEDIA_VIDEO", "android.permission.READ_MEDIA_AUDIO", "android.permission.POST_NOTIFICATIONS")
            .forEach { runCatching { ui.grantRuntimePermission(ctx.packageName, it) } }
        // A real extension so the video home has posters, rows and paging to stress.
        // Each step is logged and the whole setup is bounded: a slow extension repo must not use up
        // the class time limit before the stress itself starts (run 43622442191).
        log("setup: extensions")
        runCatching {
            runBlocking {
                kotlinx.coroutines.withTimeout(180_000) {
                    ExtensionManager.start(ctx); ExtensionManager.ensureLoaded(ctx); log("setup: extensions loaded")
                    if (ExtensionManager.provider("Internet Archive") == null) {
                        val remote = ExtensionSources.all(ctx, force = true).items.first { it.internalName == "InternetArchiveProvider" }
                        log("setup: installing ${remote.name}")
                        ExtensionManager.install(ctx, remote, dev.asfakul.yumeiro.online.ExtensionReview.Mode.AUTO) // unattended: no review dialog to tap
                    }
                    OnlineRepository.clearCaches()
                }
            }
        }.onFailure { log("extension setup failed (network?): $it"); HangDumpRule.dump("stress setup failed: $it") }
        log("setup: done")
        // Past the welcome guide that opens after the splash (run 43667108811 showed it covering the
        // app; until then this test's taps could land on it instead of the tabs).
        FirstRun.toTabs(rule)
        FirstRun.shot("stress_start")
        val am = ctx.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        log("device ${android.os.Build.MODEL} API ${android.os.Build.VERSION.SDK_INT} totalMem=${mi.totalMem / MB}M memoryClass=${am.memoryClass}M lowRam=${am.isLowRamDevice} lowEnd=${DeviceClass.lowEnd}")
        startWatchers()
    }

    @After fun tearDown() {
        watching = false
        val dir = File(ctx.getExternalFilesDir(null), "stress").apply { mkdirs() }
        File(dir, "report.txt").writeText(synchronized(report) { report.toString() })
        if (InstrumentationRegistry.getArguments().getString("hprof") == "true") {
            runCatching { Debug.dumpHprofData(File(dir, "end.hprof").path); log("heap dump written") }
        }
    }

    private fun startWatchers() {
        watching = true
        val main = Looper.getMainLooper().thread
        Thread({
            while (watching) {
                val st = main.stackTrace
                samples.incrementAndGet()
                val rx = st.indexOfFirst { it.className.startsWith("java.util.regex.Pattern") && (it.methodName == "compile" || it.methodName == "<init>") }
                if (rx >= 0) {
                    // Who asked for it: the first frame outside the JDK / Kotlin stdlib.
                    val cause = st.drop(rx).firstOrNull { f -> listOf("java.", "kotlin.", "android.", "com.android.", "libcore.", "dalvik.").none { f.className.startsWith(it) } }
                    val key = (cause?.toString() ?: "unknown") + " <- " + st.drop(rx).take(14).joinToString(" < ") { it.className.substringAfterLast('.') + "." + it.methodName }
                    regexHits.getOrPut(key) { AtomicInteger() }.incrementAndGet()
                }
                SystemClock.sleep(3)
            }
        }, "stress-sampler").apply { isDaemon = true; priority = Thread.MAX_PRIORITY }.start()
        val handler = Handler(Looper.getMainLooper())
        Thread({
            while (watching) {
                val posted = SystemClock.uptimeMillis()
                val ran = AtomicLong(0)
                handler.post { ran.set(SystemClock.uptimeMillis()) }
                while (ran.get() == 0L && watching) SystemClock.sleep(5)
                val lag = (ran.get().takeIf { it > 0 } ?: SystemClock.uptimeMillis()) - posted
                if (lag > maxStall.get()) maxStall.set(lag)
                if (lag >= 700) { stalls.incrementAndGet(); log("main thread late by ${lag}ms") }
                SystemClock.sleep(50)
            }
        }, "stress-stall").apply { isDaemon = true }.start()
    }

    private fun shell(cmd: String) { FirstRun.shell(cmd) }

    private fun shellOut(cmd: String): String =
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd).let { pfd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes().toString(Charsets.UTF_8) }
        }

    private fun idle(ms: Long) {
        val until = SystemClock.uptimeMillis() + ms
        runCatching { rule.waitUntil(ms + 2_000) { SystemClock.uptimeMillis() >= until } }
    }

    private fun memory(step: String) {
        val rt = Runtime.getRuntime()
        val mi = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
        val threads = Thread.getAllStackTraces().keys.groupingBy { dev.asfakul.yumeiro.diagnostics.MemoryWatch.groupOf(it.name) }.eachCount()
            .entries.sortedByDescending { it.value }.take(6).joinToString { "${it.key} ${it.value}" }
        log("mem[$step] javaHeap=${(rt.totalMemory() - rt.freeMemory()) / MB}/${rt.maxMemory() / MB}M native=${Debug.getNativeHeapAllocatedSize() / MB}M pss=${mi.totalPss / 1024}M threads=${Thread.activeCount()} ($threads)")
    }

    private fun tap(tag: String): Boolean = tapOnce(tag, 12_000) || run {
        // A fling that ended as a tap can open a detail page just after backToTabs looked
        // (run 43664241241): come back to the tabs once and try again.
        if (tag.startsWith("nav_tab_")) { backToTabs("retry $tag"); tapOnce(tag, 12_000) } else false
    }

    private fun tapOnce(tag: String, ms: Long): Boolean = runCatching {
        rule.waitUntil(ms) { rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag(tag, true).performClick(); true
    }.getOrElse { log("no node $tag: ${it.javaClass.simpleName}: ${it.message?.take(120)} · on screen: ${onScreen()}"); false }

    private fun hasNode(tag: String, ms: Long): Boolean = runCatching {
        rule.waitUntil(ms) { rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty() }; true
    }.getOrDefault(false)

    /**
     * Swipes and D-pad keys can open a detail page or Settings (where the bottom bar is hidden by
     * design), and Back on the home root leaves the app. Device run 43632313418 showed both, so later
     * taps found no tab. Come back to the tab bar before each round; if that fails, the app really
     * is stuck, and the test says so.
     */
    private fun backToTabs(where: String) {
        repeat(4) { attempt ->
            if (hasNode("nav_tab_home", 3_000)) { if (attempt > 0) recoveries++; return }
            val probe = runCatching { rule.onAllNodes(androidx.compose.ui.test.isRoot(), true).fetchSemanticsNodes().isNotEmpty() }
            val busy = probe.exceptionOrNull()?.takeIf { it.javaClass.simpleName == "ComposeNotIdleException" }
            if (busy != null) {
                composeSpins++
                // The screen is there but never settles: say exactly what is busy and what main is doing.
                log("recover[$where]: compose never idle (${focusLine()}): ${busy.message?.replace('\n', ' ')?.take(900)}")
                repeat(5) { i ->
                    val st = Looper.getMainLooper().thread.stackTrace.take(14).joinToString(" < ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
                    log("main sample $i: $st"); SystemClock.sleep(400)
                }
                log("busy states: " + changingStates(3_000))
                // R16: 43697447466 spun in a LazyList bring-into-view (ContentInViewNode) with no
                // way to tell which list. Accessibility does not wait for Compose to go idle, so it
                // can name the focused node and its scrollable ancestors; plus a screenshot.
                log("spin focus: " + focusChain())
                FirstRun.shot("spin_${where.replace(' ', '_')}")
                log("gfx: " + shellOut("dumpsys gfxinfo ${ctx.packageName}").lineSequence().filter { "Total frames" in it || "Janky" in it }.joinToString(" | "))
                shell("input keyevent 4"); idle(1_000); return@repeat
            }
            val alive = probe.getOrDefault(false)
            if (alive) {
                log("recover[$where]: tab bar hidden, pressing Back · ${onScreen()}")
                shell("input keyevent 4")
            } else {
                log("recover[$where]: app left the screen (${focusLine()}), relaunching")
                shell("am start -W -n ${ctx.packageName}/dev.asfakul.yumeiro.MainActivity")
            }
            idle(1_000)
        }
        throw AssertionError("tab bar never came back at $where · ${onScreen()}")
    }
    private var recoveries = 0
    /** Times Compose never went idle (an endless recomposition). Any is a failure, even if Back cleared it. */
    private var composeSpins = 0

    /** Which snapshot states keep changing (an endless recomposition is a state written every frame). */
    private fun changingStates(ms: Long): String = stateChanges(ms).second

    /** Snapshot applies in [ms] (≈ frames with a state change) and the states that changed most. */
    private fun stateChanges(ms: Long): Pair<Int, String> {
        val hits = ConcurrentHashMap<String, AtomicInteger>()
        var applies = 0
        val handle = androidx.compose.runtime.snapshots.Snapshot.registerApplyObserver { changed, _ ->
            applies++
            for (o in changed) {
                // Runs on main: no regex here (the stress test itself flags regex on main).
                // Never toString() a derived state: that computes it, and doing so while Compose
                // applies a snapshot inside a layout pass threw "performMeasureAndLayout called
                // during measure layout" (run 43664241241).
                val d = (if (o.javaClass.name.contains("Derived")) "DerivedState" else runCatching { o.toString() }.getOrDefault(o.javaClass.name)).take(110) + " #" + System.identityHashCode(o).toString(16)
                hits.getOrPut(d) { AtomicInteger() }.incrementAndGet()
            }
        }
        SystemClock.sleep(ms)
        handle.dispose()
        return applies to ("applies=$applies · " + hits.entries.sortedByDescending { it.value.get() }.take(12).joinToString(" | ") { "${it.value.get()}× ${it.key}" })
    }

    /** Frames rendered since the last `dumpsys gfxinfo reset` (-1 when unreadable). */
    private fun renderedFrames(): Int = runCatching {
        shellOut("dumpsys gfxinfo ${ctx.packageName}").lineSequence().first { "Total frames rendered" in it }
            .substringAfter(':').trim().toInt()
    }.getOrDefault(-1)

    /** Focused node (input focus, then accessibility focus) and its ancestors, from accessibility. */
    private fun focusChain(): String = runCatching {
        val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow ?: return@runCatching "no window"
        var n = root.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT)
            ?: root.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_ACCESSIBILITY) ?: return@runCatching "nothing focused"
        val parts = mutableListOf<String>()
        var depth = 0
        while (n != null && depth < 14) {
            val r = android.graphics.Rect().also { n!!.getBoundsInScreen(it) }
            val label = listOfNotNull(n.viewIdResourceName, n.text?.toString()?.take(30), n.contentDescription?.toString()?.take(30)).joinToString("/")
            parts += "${n.className?.toString()?.substringAfterLast('.')}${if (n.isScrollable) "[scroll]" else ""}${if (n.isFocused) "[focused]" else ""} $label ${r.toShortString()}"
            n = n.parent; depth++
        }
        parts.joinToString(" < ")
    }.getOrElse { "focus chain failed: $it" }

    private fun focusLine(): String = runCatching {
        shellOut("dumpsys window").lineSequence().firstOrNull { "mCurrentFocus" in it }?.trim().orEmpty()
    }.getOrDefault("?")

    /** What is actually shown when a node is missing: window focus, test tags and visible text. */
    private fun onScreen(): String = focusLine() + " · " + runCatching {
        val nodes = rule.onAllNodes(androidx.compose.ui.test.isRoot(), true).fetchSemanticsNodes()
        val tags = LinkedHashSet<String>(); val texts = LinkedHashSet<String>()
        fun walk(n: androidx.compose.ui.semantics.SemanticsNode) {
            n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.TestTag) { null }?.let { tags += it }
            n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }?.firstOrNull()?.text?.take(24)?.let { if (texts.size < 12) texts += it }
            n.children.forEach { walk(it) }
        }
        nodes.forEach { walk(it) }
        "roots=${nodes.size} · tags=${tags.take(30)} · text=$texts"
    }.getOrElse { "(could not read: ${it.javaClass.simpleName})" }

    /**
     * D-pad focus must settle. Runs 43641330758 and 43643387375 ended in an endless recomposition
     * right after the D-pad traversal (ComposeNotIdle, 99–100 % janky frames, samples in
     * PagerBringIntoViewSpec / FocusableNode / LazyLayoutBeyondBounds). That is what a TV or
     * keyboard user would hit. Move focus into Home and Yumix, stop, and require the state changes
     * to die down (an idle screen has ~0 snapshot applies; the hero auto-advance adds a short burst
     * every 6.5 s).
     */
    @Test fun dpadFocusSettles() {
        val spins = ArrayList<String>()
        for (where in listOf("home", "yumix")) {
            backToTabs("focus $where")
            if (where == "home") assertTrue(tap("nav_tab_home"))
            else {
                assertTrue(tap("nav_tab_videos")); idle(800)
                if (tap("video_source_switch")) idle(600)
                tap("video_source_online")
            }
            idle(5_000)
            log("focus[$where] before keys: ${changingStates(2_000)}")
            for (pattern in listOf(listOf(20, 20, 20, 22, 22, 22), listOf(22, 22, 22, 22, 20, 21), listOf(20, 20, 22, 19, 22, 22))) {
                for (k in pattern) { shell("input keyevent $k"); SystemClock.sleep(150) }
                SystemClock.sleep(1_500)
                // Frames actually rendered per 2 s (gfxinfo): catches loops that never write a snapshot
                // state (bring-into-view / scroll animations on the frame clock), which the earlier
                // spin reported as hadSnapshotChanges=false. Snapshot applies are logged alongside.
                val windows = List(3) {
                    shellOut("dumpsys gfxinfo ${ctx.packageName} reset")
                    val st = stateChanges(2_000)
                    renderedFrames() to st
                }
                val frames = windows.map { it.first }
                val counts = windows.map { it.second.first }
                log("focus[$where] after ${pattern.joinToString(",")}: frames per 2 s $frames · applies per 2 s $counts · ${windows.last().second.second.take(900)}")
                if (frames.min() > 40 || counts.min() > 40) {
                    spins += "$where after keys $pattern: frames $frames applies $counts · ${windows.last().second.second.take(600)}"
                    repeat(4) {
                        log("focus[$where] SPIN: main ${Looper.getMainLooper().thread.stackTrace.take(24).joinToString(" < ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }}")
                        SystemClock.sleep(97)
                    }
                    break
                }
            }
            shell("input keyevent 4"); idle(800)
        }
        assertTrue("screen never settled after D-pad focus:\n" + spins.joinToString("\n"), spins.isEmpty())
    }

    /**
     * Run 43650046635 (both devices): the stress test's spin came back as 1,717 snapshot applies in
     * 3 s, with main inside DefaultPagerNestedScrollConnection.onPreScroll → PagerState.dispatchRawDelta
     * and a pager offset stuck at -0.48. The tab pager takes every UserInput nested pre-scroll while it
     * sits between pages, and a focused card's bring-into-view is a UserInput scroll. So a tab swipe
     * still settling when D-pad keys move focus leaves bring-into-view and the pager pulling against
     * each other. Recreate that: swipe the tab pager and send D-pad keys while it settles, at several
     * delays, and require the screen to stop drawing.
     */
    @Test fun swipeThenDpadSettles() {
        val dm = ctx.resources.displayMetrics
        val w = dm.widthPixels; val h = dm.heightPixels
        backToTabs("swipe+dpad")
        assertTrue(tap("nav_tab_home")); idle(3_000)
        shell("input keyevent 20"); SystemClock.sleep(300); shell("input keyevent 20"); SystemClock.sleep(600)
        val spins = ArrayList<String>()
        for ((i, gap) in listOf(0L, 120L, 250L, 400L, 600L, 0L, 120L, 250L).withIndex()) {
            val toLeft = i % 2 == 0
            if (toLeft) shell("input swipe ${w * 3 / 4} ${h / 2} ${w / 3} ${h / 2} 120")
            else shell("input swipe ${w / 3} ${h / 2} ${w * 3 / 4} ${h / 2} 120")
            SystemClock.sleep(gap)
            shell("input keyevent 22 22 21 22 20 22")
            SystemClock.sleep(2_000)
            val windows = List(3) {
                shellOut("dumpsys gfxinfo ${ctx.packageName} reset")
                val st = stateChanges(2_000)
                renderedFrames() to st
            }
            val frames = windows.map { it.first }
            val counts = windows.map { it.second.first }
            log("swipe+dpad[$i gap=$gap]: frames per 2 s $frames · applies per 2 s $counts · ${windows.last().second.second.take(500)}")
            if (frames.min() > 40 || counts.min() > 40) {
                spins += "attempt $i (gap $gap ms): frames $frames applies $counts · ${windows.last().second.second.take(500)}"
                repeat(3) {
                    log("swipe+dpad SPIN: main ${Looper.getMainLooper().thread.stackTrace.take(24).joinToString(" < ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }}")
                    SystemClock.sleep(97)
                }
                break
            }
        }
        shell("input keyevent 4"); idle(800)
        assertTrue("screen never settled after a tab swipe + D-pad:\n" + spins.joinToString("\n"), spins.isEmpty())
    }

    @Test fun pagerFocusAndScrollStress() {
        val dm = ctx.resources.displayMetrics
        val w = dm.widthPixels; val h = dm.heightPixels
        val rounds = InstrumentationRegistry.getArguments().getString("rounds")?.toIntOrNull() ?: 6
        memory("start")
        repeat(rounds) { r ->
            for (tab in listOf("nav_tab_home", "nav_tab_videos", "nav_tab_music", "nav_tab_library")) {
                backToTabs("round ${r + 1} $tab")
                assertTrue("tab $tab did not respond", tap(tab)); idle(1_200)
                // Fling the feed down and back (pager prefetch + lazy item prefetch + image loads).
                repeat(5) { shell("input swipe ${w / 2} ${h * 3 / 4} ${w / 2} ${h / 5} 120"); idle(250) }
                repeat(2) { shell("input swipe ${w / 2} ${h / 5} ${w / 2} ${h * 3 / 4} 120"); idle(250) }
            }
            // Horizontal swipes: the home pager pages (where the ANR stack showed Pager prefetch).
            repeat(6) { shell("input swipe ${w * 4 / 5} ${h / 2} ${w / 6} ${h / 2} 150"); idle(400) }
            repeat(6) { shell("input swipe ${w / 6} ${h / 2} ${w * 4 / 5} ${h / 2} 150"); idle(400) }
            // Focus traversal (the Xiaomi ANR was dispatching a FocusEvent): D-pad through the UI.
            repeat(25) { shell("input keyevent ${listOf(20, 20, 22, 22, 21, 19)[it % 6]}"); idle(80) }
            shell("input keyevent 4"); idle(500)
            memory("round ${r + 1}")
        }
        // Video sources: Yumix (online), YouTube and the device list each have their own feed.
        for (src in listOf("video_source_online", "video_source_youtube", "video_source_device")) {
            backToTabs("source $src")
            assertTrue("videos tab did not respond", tap("nav_tab_videos")); idle(800)
            if (tap("video_source_switch") || tap("video_source_menu")) idle(600)
            if (tap(src)) {
                idle(3_000)
                repeat(10) { shell("input swipe ${w / 2} ${h * 3 / 4} ${w / 2} ${h / 6} 100"); idle(300) }
                memory("source $src")
            }
            shell("input keyevent 4"); idle(400)
        }
        memory("end")
        watching = false
        SystemClock.sleep(50)
        log("main-thread samples: ${samples.get()} · max stall ${maxStall.get()}ms · stalls≥700ms ${stalls.get()} · navigation recoveries $recoveries · compose spins $composeSpins")
        val appHits = regexHits.filterKeys { k -> k.startsWith("dev.asfakul") || k.startsWith("com.lagradost") || k.startsWith("eu.kanade") }
        regexHits.entries.sortedByDescending { it.value.get() }.forEach { log("regex-on-main x${it.value.get()}: ${it.key}") }
        if (regexHits.isEmpty()) log("regex-on-main: none")
        assertTrue("app code compiled a regex on the main thread:\n" + appHits.keys.joinToString("\n"), appHits.isEmpty())
        assertTrue("main thread stalled ${maxStall.get()}ms (ANR territory)", maxStall.get() < 4_000)
        log("compose spins: $composeSpins")
        assertTrue("Compose never went idle $composeSpins time(s): an endless recomposition (see busy states above)", composeSpins == 0)
    }

    companion object { private const val MB = 1024L * 1024L }
}
