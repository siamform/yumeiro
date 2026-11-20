/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.online.OnlineRepository
import dev.asfakul.yumeiro.online.OnlineSession
import dev.asfakul.yumeiro.player.PlaybackDiagnostics
import dev.asfakul.yumeiro.sources.drama.YumeiroDrama
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 2: "the drama banner appears but videos never play, and dramas often do not appear in the
 * feed at all". Through the same calls the app makes (the built-in drama source's home shelves →
 * [OnlineRepository.load] → [OnlineSession.play], what a banner or card tap does), dramas must
 * appear and an episode must reach its first frame. Skips only when onshort.net itself cannot be
 * reached from the machine (run b14 was an outage of the site, not of the app).
 */
@RunWith(AndroidJUnit4::class)
class DramaPlaybackDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun grant() {
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf("android.permission.POST_NOTIFICATIONS").forEach { runCatching { ui.grantRuntimePermission(ctx.packageName, it) } }
        PlaybackDiagnostics.reset("")
    }

    private fun siteUp(): Boolean = runCatching {
        okhttp3.OkHttpClient.Builder().callTimeout(java.time.Duration.ofSeconds(20)).build()
            .newCall(okhttp3.Request.Builder().url("https://onshort.net/").header("User-Agent", com.lagradost.cloudstream3.USER_AGENT).build())
            .execute().use { it.code < 500 }
    }.getOrDefault(false)

    @Test fun dramasAppearAndAnEpisodePlays() {
        assumeTrue("onshort.net is unreachable from this machine", siteUp())
        // A fresh install holds every source behind the welcome guide (OnboardingGate); release it
        // exactly as "Skip" does, then let the app's own start path publish the built-in source.
        rule.waitForIdle()
        dev.asfakul.yumeiro.online.OnboardingGate.release()
        ExtensionManager.start(ctx)
        assertTrue("built-in drama source is switched off", YumeiroDrama.enabled.value)
        val api = run {
            var p: com.lagradost.cloudstream3.MainAPI? = null
            rule.waitUntil(60_000) { p = ExtensionManager.provider(YumeiroDrama.NAME); p != null }
            p!!
        }
        // Dramas appear: the feed rows the app shows for the built-in source are not empty.
        val rows = runBlocking { OnlineRepository.home(api, force = true) }
        val items = rows.flatMap { it.items }
        Log.i("MTDeviceTest", "drama rows: ${rows.map { "${it.title}=${it.items.size}" }}")
        assertTrue("no dramas in the feed: ${rows.map { it.title }}", items.isNotEmpty())
        // An episode plays: the first title of each studio row first, then the next ones. Run
        // 43724760853 tried only DramaBox titles (the first row) while DramaBox's video host was
        // gone from DNS; one studio's outage is not "dramas don't play".
        val order = (0 until (rows.maxOfOrNull { it.items.size } ?: 0)).flatMap { i -> rows.mapNotNull { it.items.getOrNull(i) } }.distinctBy { it.url }
        val tried = ArrayList<String>()
        for (item in order.take(4)) {
            val loadAt = android.os.SystemClock.elapsedRealtime()
            val detail = runCatching { runBlocking { OnlineRepository.load(api, item.url) } }
                .onFailure { tried += "${item.name}: load ${it.javaClass.simpleName} ${it.message?.take(80)}" }.getOrNull() ?: continue
            val loadMs = android.os.SystemClock.elapsedRealtime() - loadAt
            PlaybackDiagnostics.reset("")
            val playAt = android.os.SystemClock.elapsedRealtime()
            rule.runOnUiThread { OnlineSession.play(ctx, detail) }
            // FINAL-11 (tv-api34 P1, run 44617190495: 10 615 ms, P2 7 017 ms): rule.waitUntil syncs with
            // Compose idling on the main thread at every poll, and the stall samples during that cold
            // start were mostly the test harness. FINAL-22 (run 44643868312, tv-api34 P1+P2): FINAL-11's
            // plain poll never let the Compose test clock advance, so the player page was never composed
            // (uri stayed "") and no episode could reach a frame. Now a background thread stamps the first
            // frame the moment the app reports it, and the test thread lets Compose run every 100 ms
            // (waitForIdle) instead of every poll. Same start point, same 10 s budget.
            val firstFrameStamp = java.util.concurrent.atomic.AtomicLong(0L)
            val stamper = Thread {
                while (!Thread.currentThread().isInterrupted) {
                    if (PlaybackDiagnostics.state.value.firstFrame) { firstFrameStamp.set(android.os.SystemClock.elapsedRealtime()); break }
                    try { Thread.sleep(5) } catch (_: InterruptedException) { break }
                }
            }.apply { isDaemon = true; start() }
            val deadline = android.os.SystemClock.elapsedRealtime() + 120_000
            while (android.os.SystemClock.elapsedRealtime() < deadline) {
                if (firstFrameStamp.get() > 0L) break
                if (OnlineSession.state.value?.phase == OnlineSession.Phase.FAILED) break
                rule.waitForIdle()
                android.os.SystemClock.sleep(100)
            }
            stamper.interrupt()
            val firstFrameAt = firstFrameStamp.get()
            val ok = firstFrameAt > 0L
            val playMs = (if (ok) firstFrameAt else android.os.SystemClock.elapsedRealtime()) - playAt
            Log.i("MTDeviceTest", "drama timing: ${item.name}: detail load ${loadMs}ms, play to first frame ${playMs}ms (ok=$ok)")
            val st = OnlineSession.state.value
            tried += "${item.name}: firstFrame=$ok phase=${st?.phase} links=${st?.links?.size} msg=${st?.message?.take(80)} uri=${PlaybackDiagnostics.state.value.uri.take(80)}"
            Log.i("MTDeviceTest", "drama try: ${tried.last()}")
            if (ok) {
                // R16 budget: was 34 s (run 43661715830) when the tap queued behind health probes.
                assertTrue("drama took ${playMs}ms from Play to the first frame (budget 10 s)", playMs <= 10_000)
                val d = PlaybackDiagnostics.state.value
                Log.i("MTDeviceTest", "drama plays: ${detail.name} ${d.width}x${d.height}")
                return
            }
        }
        throw AssertionError("no drama episode reached its first frame:\n" + tried.joinToString("\n"))
    }
}
