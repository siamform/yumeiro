/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import dev.asfakul.yumeiro.sources.SourceHub
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicLong

/**
 * GATE-1: nothing in CI caught the 2.0.0 freeze (FREEZE-1: Home froze for 8 s with many extensions).
 * With 300 fake providers, the test walks Home → Videos → Yumix → Live TV → Home while a watchdog
 * posts to the main thread every 100 ms. It fails when one post waits more than [MAX_STALL_MS] after
 * the first screen is up, or when Android records a new ANR for the app. Log tag YuGate1.
 */
@RunWith(AndroidJUnit4::class)
class MainThreadStallDeviceTest : StallWalk() {
    @Test fun threeHundredProvidersDoNotStallTheMainThread() {
        val anrBefore = lastAnr()
        SourceHub.publishVideo(StallWalk.BRIDGE, SourceHub.Ecosystem.JAVASCRIPT, (1..300).map { StallWalk.Fake(it) })
        val worst = walk()
        log("GATE1_RESULT worst main-thread wait $worst ms with 300 providers (limit ${StallWalk.MAX_STALL_MS})")
        assertTrue("main thread stalled $worst ms (limit ${StallWalk.MAX_STALL_MS} ms)", worst <= StallWalk.MAX_STALL_MS)
        assertTrue("a new ANR was recorded", lastAnr() <= anrBefore)
    }

}

/**
 * GATE-1 control: the same walk with no fake providers, in its own class so it starts from cleared data
 * like the gate. Logs only; it tells a stall the providers cause from the first-draw cost of a debug
 * build on a CI emulator. Run both: test=dev.asfakul.yumeiro.MainThreadStallDeviceTest,dev.asfakul.yumeiro.MainThreadStallControlDeviceTest
 */
@RunWith(AndroidJUnit4::class)
class MainThreadStallControlDeviceTest : StallWalk() {
    @Test fun controlWalkWithoutProviders() {
        val worst = walk()
        log("GATE1_CONTROL worst main-thread wait $worst ms with no fake providers")
    }
}

abstract class StallWalk {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    protected val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    protected fun log(s: String) = Log.i("YuGate1", s)
    private fun has(tag: String) = rule.onAllNodesWithTag(FirstRun.navTag(rule, tag), true).fetchSemanticsNodes().isNotEmpty()

    protected class Fake(n: Int) : MainAPI() {
        override var name = "YuGate1 fake $n"
        override var mainUrl = "https://example.invalid/$n"
        override val supportedTypes = setOf(TvType.Movie)
        override val hasMainPage = false
    }

    @After fun tearDown() { SourceHub.remove(BRIDGE) }

    protected fun lastAnr(): Long {
        if (Build.VERSION.SDK_INT < 30) return 0L
        val am = ctx.getSystemService(ActivityManager::class.java) ?: return 0L
        return am.getHistoricalProcessExitReasons(ctx.packageName, 0, 20)
            .filter { it.reason == ApplicationExitInfo.REASON_ANR }.maxOfOrNull { it.timestamp } ?: 0L
    }

    /** Home → Videos → Yumix → Live TV → Home with the watchdog on; returns the longest main-thread wait. */
    protected fun walk(): Long {
        FirstRun.toTabs(rule)
        rule.waitUntil(30_000) { has("home_screen_root") }

        val worst = AtomicLong(0L)
        val main = Handler(Looper.getMainLooper())
        val running = java.util.concurrent.atomic.AtomicBoolean(true)
        val watchdog = Thread({
            while (running.get()) {
                val sent = SystemClock.uptimeMillis()
                val ran = AtomicLong(0L)
                main.post { ran.set(SystemClock.uptimeMillis()) }
                var sampled = 0
                while (running.get() && ran.get() == 0L) {
                    Thread.sleep(10)
                    // While the main thread is stuck, log what it is doing (every 250 ms, at most 8 samples).
                    val waited = SystemClock.uptimeMillis() - sent
                    if (waited >= 300 + 250L * sampled && sampled < 8) {
                        sampled++
                        val st = Looper.getMainLooper().thread.stackTrace.take(40).joinToString("\n    at ")
                        log("STALL_SAMPLE ${waited} ms:\n    at $st")
                    }
                }
                if (ran.get() > 0L) {
                    val wait = ran.get() - sent
                    if (wait > worst.get()) { worst.set(wait); log("main thread waited ${wait} ms") }
                }
                Thread.sleep(100)
            }
        }, "yu-gate1").apply { start() }
        try {
            Thread.sleep(5_000) // Home
            FirstRun.tapVisible(rule, "nav_tab_videos")
            rule.waitUntil(20_000) { has("video_source_switch") || has("video_source_online") }
            if (!has("video_source_online")) FirstRun.tapVisible(rule, "video_source_switch")
            FirstRun.tapVisible(rule, "video_source_online"); Thread.sleep(6_000)
            if (!has("video_source_live")) FirstRun.tapVisible(rule, "video_source_switch")
            FirstRun.tapVisible(rule, "video_source_live"); Thread.sleep(6_000)
            FirstRun.tapVisible(rule, "nav_tab_home"); Thread.sleep(5_000)
        } finally {
            running.set(false)
            watchdog.join(2_000)
        }
        return worst.get()
    }

    companion object {
        const val BRIDGE = "gate1"
        const val MAX_STALL_MS = 700L
    }
}
