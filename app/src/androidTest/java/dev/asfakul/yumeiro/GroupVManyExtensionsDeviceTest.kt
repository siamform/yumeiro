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
    private var clipName = CLIP

    /**
     * Whose code held the main thread, per stall: an extension's own code (a class outside the app, CloudStream's
     * library, Android, AndroidX, Kotlin and the media/web stacks, e.g. an extension that opens a WebView or a
     * dialog while it loads), the app's (Yumeiro or Compose frames), or only the system (window drawing waits on
     * the emulator's software renderer, binder calls). Budgets apply to the app's stalls; the others are logged.
     */
    private enum class Owner { EXTENSION, APP, SYSTEM }
    private val worst = java.util.concurrent.ConcurrentHashMap<Owner, Long>()
    private val worstWhere = java.util.concurrent.ConcurrentHashMap<Owner, String>()
    private fun ownerOf(samples: List<Array<StackTraceElement>>): Pair<Owner, String> {
        for (st in samples) st.firstOrNull { e -> !platform(e) }?.let { return Owner.EXTENSION to it.className + "." + it.methodName }
        for (st in samples) st.firstOrNull { e -> appWork(e) }
            ?.let { e -> return Owner.APP to (st.firstOrNull { appWork(it) && it.className.startsWith("dev.asfakul.") } ?: e).let { it.className.substringAfterLast('.') + "." + it.methodName + ":" + it.lineNumber } }
        return Owner.SYSTEM to (samples.first().firstOrNull()?.let { it.className.substringAfterLast('.') + "." + it.methodName } ?: "?")
    }
    private fun resetOwners() { worst.clear(); worstWhere.clear() }
    private fun owners(): String = Owner.values().joinToString(" ") { o -> "${o.name.lowercase()}LagMs=${worst[o] ?: 0}" + (worstWhere[o]?.let { "($it)" } ?: "") }

    @Before fun setUp() {
        listOf("android.permission.READ_MEDIA_VIDEO", "android.permission.READ_EXTERNAL_STORAGE")
            .forEach { runCatching { inst.uiAutomation.grantRuntimePermission(ctx.packageName, it) } }
        FirstRun.toTabs(rule)
        runCatching { rule.waitUntil(20_000) { has("home_screen_root") } }
        watch.onStall = { lag, samples ->
            val (o, where) = ownerOf(samples)
            if (lag > (worst[o] ?: 0L)) { worst[o] = lag; worstWhere[o] = where }
        }
        watch.start()
    }

    @After fun tearDown() {
        watch.stop()
        runCatching { rule.runOnUiThread { PlaybackSession.releaseNow() } }
        clip?.let { TestClip.remove(ctx, it, clipName) }
    }

    private fun mem(): String {
        val pssMb = Debug.getPss() / 1024
        val heapMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024)
        return "pssMb=$pssMb heapMb=$heapMb threads=${Thread.getAllStackTraces().size}"
    }

    /** Live threads by name (digits dropped), most common first: where the threads per extension come from. */
    private fun threadGroups(): String = Thread.getAllStackTraces().keys.groupingBy { it.name.replace(RX_DIGITS, "#") }.eachCount()
        .entries.sortedByDescending { it.value }.take(6).joinToString(",", "threadsTop=") { "${it.key}=${it.value}" }

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
        watch.reset(); resetOwners()
        val t0 = SystemClock.uptimeMillis()
        val job = Thread { runBlocking(Dispatchers.IO) { block() } }.apply { start() }
        while (job.isAlive) UiLiveness.pump(rule, 250)
        // The app's own worst stall: what the budgets look at (see [Owner]).
        return (SystemClock.uptimeMillis() - t0) to (worst[Owner.APP] ?: minOf(watch.maxLag.get(), 400L))
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
            log("GV install n=$n want=$want ms=$installMs failed=$failed providers=$providers appLagMs=$installLag ${owners()} ${gfxJank()} ${mem()}")
            budget("install n=$n", installLag <= FREEZE_MS, "app code held the main thread $installLag ms (${worstWhere[Owner.APP]})")

            // 2. Relaunch-style load of every extension with Home on screen.
            runBlocking { ExtensionManager.unloadAllForSpeedTest() }
            val emissions = AtomicInteger()
            val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Default)
            val counter = ExtensionManager.state.onEach { emissions.incrementAndGet() }.launchIn(scope)
            gfxReset()
            val homeGen0 = dev.asfakul.yumeiro.ui.screens.home.HomeMemory.feed.gen
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
            UiLiveness.pump(rule, 500)
            val homeRestarts = dev.asfakul.yumeiro.ui.screens.home.HomeMemory.feed.gen - homeGen0
            log("GV load n=$n ms=$loadMs firstProviderMs=$firstMs emissions=${emissions.get()} homeRestarts=$homeRestarts providers=${ExtensionManager.providers().size} " +
                "appLagMs=$loadLag ${owners()} ${gfxJank()} ${mem()} ${threadGroups()}")
            // Each state change makes Home, Yumix, Search and Most played work everything out again.
            if (n >= 30) budget("load n=$n", emissions.get() <= n / 2, "${emissions.get()} extension state changes in one load")
            // Home's rows: extensions that finish loading must not restart the rows of the others.
            budget("load n=$n", homeRestarts <= 1, "Home restarted every extension's rows $homeRestarts times")
            budget("load n=$n", firstMs in 0..3_000, "first source usable after $firstMs ms")
            budget("load n=$n", loadLag <= FREEZE_MS, "app code held the main thread $loadLag ms (${worstWhere[Owner.APP]})")

            // 3. Search across every provider with Home on screen.
            val apis = ExtensionManager.providers()
            var answered = 0
            gfxReset()
            val (searchMs, searchLag) = measured {
                withTimeoutOrNull(90_000) { OnlineRepository.search(QUERY + want, apis).take(apis.size).collect { answered++ } }
            }
            log("GV search n=$n apis=${apis.size} answered=$answered ms=$searchMs appLagMs=$searchLag ${owners()} ${gfxJank()} ${mem()}")
            budget("search n=$n", searchLag <= FREEZE_MS, "app code held the main thread $searchLag ms (${worstWhere[Owner.APP]})")

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
        // A new file per size: the same link twice is the same item and is not opened again.
        clip?.let { TestClip.remove(ctx, it, clipName) }
        clipName = "yumeiro_test_clip_v$n.mp4"
        clip = TestClip.put(ctx, clipName, CLIP)
        PlaybackDiagnostics.reset("")
        FirstRun.shell("am start -a android.intent.action.VIEW -d $clip -t video/mp4 -n ${ctx.packageName}/dev.asfakul.yumeiro.MainActivity")
        val started = runCatching { rule.waitUntil(45_000) { PlaybackDiagnostics.state.value.firstFrame }; true }.getOrDefault(false)
        if (!started) { problems += "play n=$n: no first frame"; FirstRun.shot("groupv_noplay_$n"); return }
        // The clip is short: loop it so the busy part is measured while it really plays.
        rule.runOnUiThread { PlaybackSession.playerForTest?.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE }
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
            "appLagMs=$busyLag ${owners()} state=$state ${gfxJank()} ${mem()} ${threadGroups()}")
        FirstRun.shot("groupv_play_$n")
        budget("play n=$n", busyRend > 0, "no frames rendered while busy")
        budget("play n=$n", busyDrop * 10 <= busyRend + busyDrop, "$busyDrop of ${busyRend + busyDrop} frames dropped while extensions loaded")
        budget("play n=$n", busyLag <= FREEZE_MS, "app code held the main thread $busyLag ms during playback (${worstWhere[Owner.APP]})")
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
        val RX_DIGITS = Regex("\\d+")
        /** Not an extension: the app, its CloudStream library, Android and the libraries the app ships. */
        val PLATFORM = listOf(
            "dev.asfakul.", "com.lagradost.", "android.", "androidx.", "com.android.", "java.", "javax.", "kotlin.",
            "kotlinx.", "dalvik.", "libcore.", "sun.", "jdk.", "com.google.", "org.chromium.", "okhttp3.", "okio.",
            "org.json.", "org.jsoup.", "com.fasterxml.", "org.mozilla.",
        )
        /** WebView's own classes have no package or a one- or two-letter one (J9, N, J.N, Zs0 …). */
        fun platform(e: StackTraceElement) = e.className.substringBefore('.', "").length <= 2 || PLATFORM.any { e.className.startsWith(it) }
        /**
         * App frames that are not app work: ExtensionGuard runs the main looper inside its own handler (every main
         * stack has it), and the test's own helpers.
         */
        fun appWork(e: StackTraceElement) = (e.className.startsWith("dev.asfakul.") || e.className.startsWith("androidx.compose.")) &&
            !e.className.startsWith("dev.asfakul.yumeiro.online.ExtensionGuard") && !e.className.startsWith("androidx.compose.ui.test.") &&
            !e.className.startsWith("dev.asfakul.yumeiro.GroupV") && !e.className.startsWith("dev.asfakul.yumeiro.MainStallWatch")
    }
}
