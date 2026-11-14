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

    private fun shell(cmd: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd).close()
    }

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

    private fun tap(tag: String): Boolean = runCatching {
        rule.waitUntil(20_000) { rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty() }
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
            val alive = runCatching { rule.onAllNodes(androidx.compose.ui.test.isRoot(), true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
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
        log("main-thread samples: ${samples.get()} · max stall ${maxStall.get()}ms · stalls≥700ms ${stalls.get()} · navigation recoveries $recoveries")
        val appHits = regexHits.filterKeys { k -> k.startsWith("dev.asfakul") || k.startsWith("com.lagradost") || k.startsWith("eu.kanade") }
        regexHits.entries.sortedByDescending { it.value.get() }.forEach { log("regex-on-main x${it.value.get()}: ${it.key}") }
        if (regexHits.isEmpty()) log("regex-on-main: none")
        assertTrue("app code compiled a regex on the main thread:\n" + appHits.keys.joinToString("\n"), appHits.isEmpty())
        assertTrue("main thread stalled ${maxStall.get()}ms (ANR territory)", maxStall.get() < 4_000)
    }

    companion object { private const val MB = 1024L * 1024L }
}
