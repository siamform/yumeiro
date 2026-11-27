/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.net.Uri
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.online.ExtensionReview
import dev.asfakul.yumeiro.online.ExtensionSources
import dev.asfakul.yumeiro.online.OnlineRepository
import dev.asfakul.yumeiro.online.OnlineRuntime
import dev.asfakul.yumeiro.player.PlaybackDiagnostics
import dev.asfakul.yumeiro.player.PlaybackSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/**
 * B20-PERF-EXT (GitHub report, High): with 60+ extensions the app froze and video stuttered.
 *
 * Group V benchmark, real extensions from the market on a real emulator with the app's UI on screen.
 * For 10, 30 and 60 installed extensions it measures, while the user is on Home:
 *  1. install: the main-thread freeze (worst delay of a posted task) while extensions are added;
 *  2. relaunch load: time to first provider / all loaded, extension state emissions, freeze, janky frames;
 *  3. search fan-out across every provider: freeze and time;
 *  4. playback of a local clip while the extensions reload and search in the background: dropped frames
 *     and freeze;
 *  5. memory (PSS) and thread count.
 * Log tag YuGroupV: GV lines (one per measure) and GV_RESULT. Budgets are asserted at the end so every
 * size is measured even when an earlier one is over budget.
 */
@RunWith(AndroidJUnit4::class)
class GroupVManyExtensionsDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = inst.targetContext
    private fun log(s: String) = Log.i(TAG, s)
    private fun has(tag: String) = runCatching { rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    private val watch = MainStallWatch(TAG, dumpAfterMs = 400)
    private val problems = mutableListOf<String>()
    private var clip: Uri? = null

    @Before fun setUp() {
        listOf("android.permission.READ_MEDIA_VIDEO", "android.permission.READ_EXTERNAL_STORAGE")
            .forEach { runCatching { inst.uiAutomation.grantRuntimePermission(ctx.packageName, it) } }
        FirstRun.toTabs(rule)
        runCatching { rule.waitUntil(20_000) { has("home_screen_root") } }
        watch.start()
    }

    @After fun tearDown() {
        watch.stop()
        runCatching { rule.runOnUiThread { PlaybackSession.releaseNow() } }
        clip?.let { TestClip.remove(ctx, it, CLIP) }
    }

    private fun mem(): String {
        val pssMb = Debug.getPss() / 1024
        val heapMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024)
        return "pssMb=$pssMb heapMb=$heapMb threads=${Thread.getAllStackTraces().size}"
    }

    private fun gfxReset() { runCatching { FirstRun.shell("dumpsys gfxinfo ${ctx.packageName} reset") } }
    private fun gfxJank(): String = runCatching {
        val out = FirstRun.shell("dumpsys gfxinfo ${ctx.packageName}")
        val total = RX_TOTAL.find(out)?.groupValues?.get(1) ?: "?"
        val janky = RX_JANKY.find(out)?.groupValues?.let { "${it[1]} (${it[2]})" } ?: "?"
        val p90 = RX_P90.find(out)?.groupValues?.get(1) ?: "?"
        "frames=$total janky=$janky p90=${p90}ms"
    }.getOrDefault("frames=?")

    private fun budget(what: String, ok: Boolean, detail: String) { if (!ok) problems += "$what: $detail" }

    /** Runs [block] off the main thread while the UI keeps drawing; returns its time and the worst main delay. */
    private fun measured(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit): Pair<Long, Long> {
        watch.reset()
        val t0 = SystemClock.uptimeMillis()
        val job = Thread { runBlocking(Dispatchers.IO) { block() } }.apply { start() }
        while (job.isAlive) UiLiveness.pump(rule, 250)
        return (SystemClock.uptimeMillis() - t0) to watch.maxLag.get()
    }

    @Test fun manyExtensions_stayResponsive_andPlaybackStaysSmooth() {
        runBlocking { ExtensionManager.start(ctx); ExtensionManager.ensureLoaded(ctx) }
        val market = runBlocking {
            ExtensionSources.all(ctx, force = true).items.filter {
                it.primary && !it.isNsfw && !it.isDown && it.apiVersion <= OnlineRuntime.API_VERSION
            }
        }
        log("GV market candidates=${market.size} installed=${ExtensionManager.state.value.installed.size} ${mem()}")
        val queue = market.iterator()
        for (want in SIZES) {
            // 1. Install up to [want] with Home on screen.
            var failed = 0
            gfxReset()
            val (installMs, installLag) = measured {
                while (ExtensionManager.state.value.installed.count { e -> e.enabled } < want && queue.hasNext()) {
                    val c = queue.next()
                    if (ExtensionManager.state.value.installed.any { e -> e.internalName == c.internalName }) continue
                    runCatching { ExtensionManager.install(ctx, c, ExtensionReview.Mode.AUTO) }.onFailure { failed++ }
                }
            }
            val n = ExtensionManager.state.value.installed.count { e -> e.enabled }
            val providers = ExtensionManager.providers().size
            log("GV install n=$n want=$want ms=$installMs failed=$failed providers=$providers maxLagMs=$installLag ${gfxJank()} ${mem()}")
            budget("install n=$n", installLag <= FREEZE_MS, "main thread blocked $installLag ms (stall: ${watch.firstStall.get()?.take(600)})")

            // 2. Relaunch-style load of every extension with Home on screen.
            runBlocking { ExtensionManager.unloadAllForSpeedTest() }
            val emissions = AtomicInteger()
            val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Default)
            val counter = ExtensionManager.state.onEach { emissions.incrementAndGet() }.launchIn(scope)
            gfxReset()
            var firstMs = -1L
            val (loadMs, loadLag) = measured {
                val t0 = SystemClock.uptimeMillis()
                val first = scope.launch {
                    while (ExtensionManager.providers().isEmpty()) kotlinx.coroutines.delay(20)
                    firstMs = SystemClock.uptimeMillis() - t0
                }
                ExtensionManager.ensureLoaded(ctx)
                first.cancel()
            }
            counter.cancel()
            log("GV load n=$n ms=$loadMs firstProviderMs=$firstMs emissions=${emissions.get()} providers=${ExtensionManager.providers().size} " +
                "maxLagMs=$loadLag ${gfxJank()} ${mem()}")
            budget("load n=$n", loadLag <= FREEZE_MS, "main thread blocked $loadLag ms (stall: ${watch.firstStall.get()?.take(600)})")

            // 3. Search across every provider with Home on screen.
            val apis = ExtensionManager.providers()
            var answered = 0
            gfxReset()
            val (searchMs, searchLag) = measured {
                withTimeoutOrNull(90_000) { OnlineRepository.search(QUERY + want, apis).take(apis.size).collect { answered++ } }
            }
            log("GV search n=$n apis=${apis.size} answered=$answered ms=$searchMs maxLagMs=$searchLag ${gfxJank()} ${mem()}")
            budget("search n=$n", searchLag <= FREEZE_MS, "main thread blocked $searchLag ms (stall: ${watch.firstStall.get()?.take(600)})")

            // 4. Playback while the extensions reload and search in the background.
            playWhileBusy(n)
        }
        log("GV summary problems=${problems.size}: ${problems.joinToString(" | ")}")
        FirstRun.shot("groupv_after")
        assertTrue("B20-PERF-EXT over budget:\n" + problems.joinToString("\n"), problems.isEmpty())
        log("GV_RESULT PASS")
    }

    private fun counters(): Pair<Int, Int> {
        var r = 0 to 0
        rule.runOnUiThread { PlaybackSession.playerForTest?.videoDecoderCounters?.let { c -> c.ensureUpdated(); r = c.droppedBufferCount to c.renderedOutputBufferCount } }
        return r
    }

    private fun playWhileBusy(n: Int) {
        if (clip == null) clip = TestClip.put(ctx, CLIP)
        PlaybackDiagnostics.reset("")
        FirstRun.shell("am start -a android.intent.action.VIEW -d $clip -t video/mp4 -n ${ctx.packageName}/dev.asfakul.yumeiro.MainActivity")
        val started = runCatching { rule.waitUntil(45_000) { PlaybackDiagnostics.state.value.firstFrame }; true }.getOrDefault(false)
        if (!started) { problems += "play n=$n: no first frame"; return }
        UiLiveness.pump(rule, 1_500)
        // Idle baseline, 4 s.
        val (d0, r0) = counters(); watch.reset(); UiLiveness.pump(rule, 4_000)
        val (d1, r1) = counters(); val idleLag = watch.maxLag.get()
        // Busy: reload every extension and search all of them, while the clip plays.
        gfxReset()
        val (busyMs, busyLag) = measured {
            ExtensionManager.unloadAllForSpeedTest()
            val apisLater = async { ExtensionManager.ensureLoaded(ctx); ExtensionManager.providers() }
            val apis = apisLater.await()
            withTimeoutOrNull(30_000) { OnlineRepository.search(QUERY + "p$n", apis).take(apis.size).collect { } }
        }
        val (d2, r2) = counters()
        val idleDrop = d1 - d0; val idleRend = r1 - r0; val busyDrop = d2 - d1; val busyRend = r2 - r1
        val state = PlaybackDiagnostics.state.value.state
        log("GV play n=$n idle rendered=$idleRend dropped=$idleDrop maxLagMs=$idleLag | busy ms=$busyMs rendered=$busyRend dropped=$busyDrop " +
            "maxLagMs=$busyLag state=$state ${gfxJank()} ${mem()}")
        FirstRun.shot("groupv_play_$n")
        budget("play n=$n", busyRend > 0, "no frames rendered while busy")
        budget("play n=$n", busyDrop * 10 <= busyRend + busyDrop, "$busyDrop of ${busyRend + busyDrop} frames dropped while extensions loaded")
        budget("play n=$n", busyLag <= FREEZE_MS, "main thread blocked $busyLag ms during playback (stall: ${watch.firstStall.get()?.take(600)})")
        rule.runOnUiThread { PlaybackSession.releaseNow() }
        inst.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        UiLiveness.pump(rule, 1_500)
    }

    private companion object {
        const val TAG = "YuGroupV"
        const val CLIP = "yumeiro_test_clip.mp4"
        const val QUERY = "love"
        /** A task held off the main thread longer than this is a visible freeze (a dropped half-second). */
        const val FREEZE_MS = 700L
        val SIZES = listOf(10, 30, 60)
        val RX_TOTAL = Regex("Total frames rendered: (\\d+)")
        val RX_JANKY = Regex("Janky frames: (\\d+) \\(([\\d.]+%)\\)")
        val RX_P90 = Regex("90th percentile: (\\d+)ms")
    }
}
