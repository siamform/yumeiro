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
import dev.asfakul.yumeiro.player.PlaybackDiagnostics
import dev.asfakul.yumeiro.stream.CloudRecapture
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
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/**
 * F-03 (ADR-042): a TeraBox / DiskWala stream whose address expired (403) is found again on its
 * share page (opened off screen, its own player started) and plays from the saved position.
 * The share page is a local fixture whose player requests a fresh address on every load.
 * Old code: no refresher owns these streams, so the player showed "Access denied".
 */
@RunWith(AndroidJUnit4::class)
class CloudRecaptureDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var server: MockWebServer
    private val paths = Collections.synchronizedList(mutableListOf<String>())
    private val pageLoads = AtomicInteger()

    @Before fun setUp() {
        val clip = inst.context.assets.open("yumeiro_test_clip.mp4").use { it.readBytes() }
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val p = request.url.encodedPath
                paths += p + (request.url.encodedQuery?.let { "?$it" } ?: "")
                return when {
                    p == "/s/1yutest" -> {
                        val n = pageLoads.incrementAndGet()
                        MockResponse.Builder().addHeader("Content-Type", "text/html; charset=utf-8").body(
                            "<!DOCTYPE html><html><head><title>yutest.mp4</title></head><body>" +
                                "<video id='v' muted playsinline src='/stream/v.mp4?sign=fresh$n'></video></body></html>",
                        ).build()
                    }
                    p == "/stream/v.mp4" && (request.url.queryParameter("sign") ?: "").startsWith("fresh") ->
                        MockResponse.Builder().addHeader("Content-Type", "video/mp4").addHeader("Accept-Ranges", "none").body(Buffer().write(clip)).build()
                    p == "/stream/v.mp4" -> MockResponse.Builder().code(403).body("expired").build()
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
        CloudRecapture.testHosts = setOf(server.hostName)
        CloudRecapture.resetBudget()
        PlaybackDiagnostics.reset("")
    }

    @After fun tearDown() {
        CloudRecapture.testHosts = emptySet()
        CloudRecapture.resetBudget()
        IncomingMedia.consume()
        runCatching { server.close() }
    }

    @Test fun expiredCloudStreamIsFoundAgainAndPlays() {
        val base = "http://${server.hostName}:${server.port}"
        val stale = StreamRequest(
            url = "$base/stream/v.mp4?sign=expired", title = "yutest.mp4", format = StreamFormat.PROGRESSIVE,
            pageUrl = "$base/s/1yutest", referer = "$base/s/1yutest",
        )
        rule.runOnUiThread { IncomingMedia.open(ctx, stale, positionMs = 2_000L) }
        // FINAL-2: the first 403 sets error + IDLE before the re-capture starts (run 44593053433 ended the test
        // there and the scope was forgotten mid-refresh). Wait for the frame; the asserts report what happened.
        runCatching { rule.waitUntil(90_000) { PlaybackDiagnostics.state.value.firstFrame } }
        val s = PlaybackDiagnostics.state.value
        Log.i("YuCloud", "recapture -> $s pageLoads=${pageLoads.get()} requests=$paths")
        assertTrue("the share page was never opened again (no TeraBox / DiskWala refresher)", pageLoads.get() >= 1)
        assertTrue("the fresh stream was never requested: $paths", paths.any { it.startsWith("/stream/v.mp4?sign=fresh") })
        assertTrue("no first frame after the re-capture: $s", s.firstFrame)
    }
}
