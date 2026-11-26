/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.player.PlaybackDiagnostics
import dev.asfakul.yumeiro.player.PlaybackSession
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/**
 * B15 (owner): playback stutters and lags. While a video plays with its controls up, the position
 * ticker writes the shown time four times a second. That value was read in the body of the player's
 * whole content function, so every tick recomposed the entire player (surface host, gestures, chrome,
 * dialogs) instead of just the seek bar: main-thread work during playback, worst on low-RAM phones.
 *
 * The probe is Compose's own trace hook (debug builds carry the function names): it counts how
 * often `VideoPlayerModalContent` itself recomposes while the time label moves. Log tag YuGroupN:
 * GN_CASE / GN_RESULT lines.
 */
@OptIn(InternalComposeTracingApi::class)
@RunWith(AndroidJUnit4::class)
class GroupNPlayerSmoothnessDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = inst.targetContext
    private fun log(s: String) = Log.i("YuGroupN", s)
    private fun has(tag: String) = runCatching { rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    private var clip: Uri? = null
    private val watch = MainStallWatch("YuGroupN")

    private val content = AtomicInteger()
    private val bar = AtomicInteger()
    @Volatile private var counting = false
    private val all = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val tracer = object : CompositionTracer {
        override fun isTraceInProgress(): Boolean = true
        override fun traceEventStart(key: Int, dirty1: Int, dirty2: Int, info: String) {
            if (!counting) return
            all.merge(info, 1, Int::plus)
            if (info.contains("VideoPlayerModalContent (")) content.incrementAndGet()
            if (info.contains("VideoPlayerBottomBar (")) bar.incrementAndGet()
        }
        override fun traceEventEnd() {}
    }

    @Before fun setUp() {
        listOf("android.permission.READ_MEDIA_VIDEO", "android.permission.READ_EXTERNAL_STORAGE")
            .forEach { runCatching { inst.uiAutomation.grantRuntimePermission(ctx.packageName, it) } }
        FirstRun.toTabs(rule)
        runCatching { rule.waitUntil(20_000) { has("home_screen_root") } }
        Composer.setTracer(tracer)
        watch.start()
    }

    @After fun tearDown() {
        counting = false
        Composer.setTracer(object : CompositionTracer {
            override fun isTraceInProgress(): Boolean = false
            override fun traceEventStart(key: Int, dirty1: Int, dirty2: Int, info: String) {}
            override fun traceEventEnd() {}
        })
        watch.stop()
        runCatching { rule.runOnUiThread { PlaybackSession.releaseNow() } }
        clip?.let { TestClip.remove(ctx, it, NAME) }
    }

    @Test fun b15_positionTicksDoNotRecomposeThePlayer() {
        clip = TestClip.put(ctx, NAME)
        PlaybackDiagnostics.reset("")
        FirstRun.shell("am start -a android.intent.action.VIEW -d $clip -t video/mp4 -n ${ctx.packageName}/dev.asfakul.yumeiro.MainActivity")
        assertTrue("first frame", runCatching { rule.waitUntil(45_000) { PlaybackDiagnostics.state.value.firstFrame }; true }.getOrDefault(false))
        UiLiveness.pump(rule, 600)
        // Controls up (the ticker runs at 250 ms then), let the show animation settle, then count.
        if (!has("video_more_btn")) UiLiveness.tap(rule, "video_player_root")
        runCatching { rule.waitUntil(3_000) { has("video_more_btn") } }
        UiLiveness.pump(rule, 500)
        content.set(0); bar.set(0); all.clear()
        val t0 = UiLiveness.timeLabels(rule)
        counting = true
        UiLiveness.pump(rule, 2_000)
        counting = false
        val t1 = UiLiveness.timeLabels(rule)
        val c = content.get(); val b = bar.get()
        FirstRun.shot("gn_b15_ticks")
        log("GN_CASE B15 ticks time='$t0'→'$t1' contentRecompositions=$c bottomBarRecompositions=$b controls=${has("video_more_btn")} " +
            "state=${PlaybackDiagnostics.state.value.state} maxLagMs=${watch.maxLag.get()} total=${all.values.sum()}")
        val ours = all.entries.filter { it.key.startsWith("dev.asfakul") }.sortedByDescending { it.value }
        log("GN_CASE B15 recomposed (ours, 2 s): " + ours.take(25).joinToString { it.key.substringAfter("dev.asfakul.yumeiro.") + "=" + it.value })
        assertTrue("B15: the time label did not move ('$t0' → '$t1'), nothing was measured", t0.isNotBlank() && t0 != t1)
        assertTrue("B15: the seek bar never updated (bottom bar recompositions $b)", b > 0)
        assertTrue("B15: the whole player recomposed $c times in 2 s of position ticks (seek bar $b)", c <= 1)
        log("GN_RESULT PASS B15 ticks")
    }

    private companion object { const val NAME = "yumeiro_test_clip.mp4" }
}
