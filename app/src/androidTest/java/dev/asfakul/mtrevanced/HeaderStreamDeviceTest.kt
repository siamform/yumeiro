/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.mtrevanced

import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.mtrevanced.online.OnlineLink
import dev.asfakul.mtrevanced.online.OnlinePlayback
import dev.asfakul.mtrevanced.player.PlaybackDiagnostics
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A host like many extension sources: it refuses (403) any request without the right Referer —
 * the playlist, every segment and the subtitle. Proxies a public HLS test stream.
 */
@RunWith(AndroidJUnit4::class)
class HeaderStreamDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var server: MockWebServer
    @Volatile private var refused = 0

    @Before fun setUp() {
        PlaybackDiagnostics.reset("")
        val upstream = OkHttpClient()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                if (path.endsWith(".vtt")) {
                    if (request.headers["Referer"] != SUB_REFERER) { refused++; return MockResponse.Builder().code(403).build() }
                    return MockResponse.Builder().addHeader("Content-Type", "text/vtt")
                        .body("WEBVTT\n\n00:00:00.000 --> 00:00:30.000\nHeader test\n").build()
                }
                if (request.headers["Referer"] != REFERER) { refused++; return MockResponse.Builder().code(403).build() }
                upstream.newCall(Request.Builder().url(UPSTREAM + path.removePrefix("/")).build()).execute().use { r ->
                    val b = MockResponse.Builder().code(r.code).body(Buffer().write(r.body.bytes()))
                    r.header("Content-Type")?.let { b.addHeader("Content-Type", it) }
                    return b.build()
                }
            }
        }
        server.start()
    }
    @After fun tearDown() { server.close() }

    @Test fun refererProtectedHlsPlays() {
        val link = OnlineLink(
            name = "Header test", source = "Test", url = server.url("/x36xhzz.m3u8").toString(), referer = REFERER,
            quality = 720, isHls = true, isDash = false, headers = emptyMap(),
        )
        val subs = listOf(dev.asfakul.mtrevanced.online.OnlineSubtitle("English", server.url("/en.vtt").toString(), mapOf("Referer" to SUB_REFERER)))
        rule.runOnUiThread { OnlinePlayback.play(ctx, "Header test", link, subs) }
        runCatching { rule.waitUntil(75_000) { PlaybackDiagnostics.state.value.let { it.firstFrame || (it.error != null && it.state == "IDLE") } } }
        val s = PlaybackDiagnostics.state.value
        Log.i("MTDeviceTest", "header stream -> $s, refused=$refused")
        assertTrue("header-protected HLS: $s", s.firstFrame)
        assertTrue("requests sent without the right Referer: $refused", refused == 0)
    }

    companion object {
        const val REFERER = "https://protected.example/"
        const val SUB_REFERER = "https://subs.example/"
        const val UPSTREAM = "https://test-streams.mux.dev/x36xhzz/"
    }
}
