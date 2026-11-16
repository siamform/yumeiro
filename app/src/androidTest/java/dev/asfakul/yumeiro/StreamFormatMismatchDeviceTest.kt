/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.player.PlaybackDiagnostics
import dev.asfakul.yumeiro.player.PlaybackSession
import dev.asfakul.yumeiro.stream.IncomingMedia
import dev.asfakul.yumeiro.stream.StreamFormat
import dev.asfakul.yumeiro.stream.StreamRequest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * BUG-7 7c (owner): Live → Play link with a format picked by hand (DASH, HLS, Smooth) that does not
 * match the URL froze the player and the whole app. A local server serves an MP4 and an HTML page;
 * each case opens one of them with the wrong format forced, exactly as Play does for a hand-picked
 * format (IncomingMedia.open, no inspection), then for 25 s:
 *  - a watcher thread posts to the main thread every ~50 ms and records the worst delay; any delay
 *    over 1.5 s dumps the main thread's stack (log tag YuFmtMismatch, FMT_STALL);
 *  - the player must end in an error the user can read (PlaybackDiagnostics error), not hang;
 *  - after that the app must still answer: the player closes and Home is back within 10 s.
 * Lines: FMT_CASE <name> maxLagMs=… error=… closed=…, FMT_RESULT.
 */
@RunWith(AndroidJUnit4::class)
class StreamFormatMismatchDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = inst.targetContext
    private fun log(s: String) = Log.i("YuFmtMismatch", s)
    private fun has(tag: String) = runCatching { rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    private lateinit var server: MockWebServer
    private val watch = MainStallWatch("YuFmtMismatch")

    @Before fun setUp() {
        val clip = inst.context.assets.open("yumeiro_test_clip.mp4").use { it.readBytes() }
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.url.encodedPath.endsWith(".mp4") ->
                    MockResponse.Builder().addHeader("Content-Type", "video/mp4").body(Buffer().write(clip)).build()
                else -> MockResponse.Builder().addHeader("Content-Type", "text/html")
                    .body("<!doctype html><html><head><title>Not a video</title></head><body>${"<p>filler</p>".repeat(200)}</body></html>").build()
            }
        }
        server.start()
        FirstRun.toTabs(rule)
        runCatching { rule.waitUntil(20_000) { has("home_screen_root") } }
        watch.start()
    }

    @After fun tearDown() {
        watch.stop()
        runCatching { rule.runOnUiThread { PlaybackSession.releaseNow() } }
        runCatching { server.close() }
    }

    private fun check(name: String, path: String, format: StreamFormat) {
        watch.reset()
        PlaybackDiagnostics.reset("")
        val url = server.url(path).toString()
        rule.runOnUiThread { IncomingMedia.open(ctx, StreamRequest(url = url, title = "Mismatch $name", format = format), remember = false) }
        val ended = runCatching {
            rule.waitUntil(25_000) { PlaybackDiagnostics.state.value.let { it.error != null || it.firstFrame } }; true
        }.getOrDefault(false)
        val snap = PlaybackDiagnostics.state.value
        val playerOpen = has("video_player_root")
        FirstRun.shot("fmt_$name")
        // The user leaves with Back and closes the floating window with a real tap; the app must answer.
        val (closed, how) = UiLiveness.leaveAndClose(rule)
        val lag = watch.maxLag.get()
        log("FMT_CASE $name format=$format path=$path ended=$ended playerOpen=$playerOpen state=${snap.state} firstFrame=${snap.firstFrame} error=${snap.error?.take(160)} maxLagMs=$lag $how stall=${watch.firstStall.get()?.take(600)}")
        assertTrue("7c $name: the main thread stopped for $lag ms (freeze): ${watch.firstStall.get()}", lag < 2_000)
        assertTrue("7c $name: the player neither played nor showed an error within 25 s: $snap", ended)
        assertTrue("7c $name: the app did not answer Back / the close button ($how)", closed)
        log("FMT_RESULT PASS $name")
    }

    @Test fun a_mp4ForcedAsDash() = check("mp4_as_dash", "/clip.mp4", StreamFormat.DASH)
    @Test fun b_mp4ForcedAsHls() = check("mp4_as_hls", "/clip.mp4", StreamFormat.HLS)
    @Test fun c_mp4ForcedAsSmooth() = check("mp4_as_smooth", "/clip.mp4", StreamFormat.SMOOTH)
    @Test fun d_htmlForcedAsDash() = check("html_as_dash", "/page", StreamFormat.DASH)
    @Test fun e_htmlForcedAsHls() = check("html_as_hls", "/page", StreamFormat.HLS)
}
