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
import dev.asfakul.yumeiro.online.telegram.TelegramPublic
import dev.asfakul.yumeiro.player.PlaybackDiagnostics
import dev.asfakul.yumeiro.stream.IncomingMedia
import dev.asfakul.yumeiro.stream.Resolution
import dev.asfakul.yumeiro.stream.StreamRequest
import dev.asfakul.yumeiro.stream.StreamResolver
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/**
 * F-02 (ADR-042): a pasted public post link (t.me/<channel>/<post>) plays in Yumeiro's player, and
 * when the preview's temporary file address is refused (403) the post is read again and playback
 * continues. The preview and the file are served locally (fixture page + the test clip).
 * Old code: the post link went to the generic page probe and never reached a first frame.
 */
@RunWith(AndroidJUnit4::class)
class TelegramPlaybackDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var server: MockWebServer
    private val paths = Collections.synchronizedList(mutableListOf<String>())
    private val postReads = AtomicInteger()

    @Before fun setUp() {
        val clip = inst.context.assets.open("yumeiro_test_clip.mp4").use { it.readBytes() }
        val page = inst.context.assets.open("telegram/channel.html").use { it.readBytes().decodeToString() }
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val p = request.url.encodedPath
                paths += p + (request.url.encodedQuery?.let { "?$it" } ?: "")
                val base = "http://${server.hostName}:${server.port}"
                return when {
                    // Each read of the post hands out a new token, as Telegram does.
                    p == "/yutest/6" -> {
                        val n = postReads.incrementAndGet()
                        MockResponse.Builder().addHeader("Content-Type", "text/html; charset=utf-8")
                            .body(page.replace("https://cdn.example.test/file/6.mp4?token=abc", "$base/file/6.mp4?token=t$n")).build()
                    }
                    p == "/file/6.mp4" && request.url.queryParameter("token") == "fresh" || p == "/file/6.mp4" && (request.url.queryParameter("token") ?: "").startsWith("t") ->
                        MockResponse.Builder().addHeader("Content-Type", "video/mp4").addHeader("Accept-Ranges", "none").body(Buffer().write(clip)).build()
                    p == "/file/6.mp4" -> MockResponse.Builder().code(403).body("expired").build()
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
        TelegramPublic.base = server.url("/").toString()
        TelegramPublic.clearCache()
        PlaybackDiagnostics.reset("")
    }

    @After fun tearDown() {
        TelegramPublic.base = "https://t.me"
        IncomingMedia.consume()
        runCatching { server.close() }
    }

    private fun waitFrame(ms: Long = 75_000): PlaybackDiagnostics.Snapshot {
        // FINAL-2: the first 403 sets error + IDLE before the refresh starts; waiting on it ended the test
        // ~0.1 s into the recovery (run 44593053433). Wait for the frame; the asserts report what happened.
        runCatching { rule.waitUntil(ms) { PlaybackDiagnostics.state.value.firstFrame } }
        return PlaybackDiagnostics.state.value
    }

    @Test fun pastedPostLinkPlays() {
        val r = runBlocking { StreamResolver.resolve(ctx, StreamRequest("https://t.me/yutest/6")) }
        assertTrue("post link didn't resolve to the preview's video: $r (requests $paths)", r is Resolution.Playable)
        val req = (r as Resolution.Playable).request
        assertEquals("https://t.me/yutest/6", req.pageUrl)
        rule.runOnUiThread { IncomingMedia.open(ctx, req) }
        val s = waitFrame()
        Log.i("YuTelegram", "post link -> $s requests=$paths")
        assertTrue("no first frame: $s", s.firstFrame)
    }

    @Test fun expiredPreviewFileIsReadAgainAndPlays() {
        val base = "http://${server.hostName}:${server.port}"
        // An address whose token has expired (the server refuses it), as after a long pause.
        val stale = StreamRequest(
            url = "$base/file/6.mp4?token=expired", title = "Episode 1", format = dev.asfakul.yumeiro.stream.StreamFormat.PROGRESSIVE,
            pageUrl = "https://t.me/yutest/6", group = StreamResolver.TELEGRAM_GROUP,
        )
        rule.runOnUiThread { IncomingMedia.open(ctx, stale) }
        val s = waitFrame()
        Log.i("YuTelegram", "expired -> $s postReads=${postReads.get()} requests=$paths")
        assertTrue("the post was never read again (no Telegram refresher)", postReads.get() >= 1)
        assertTrue("no first frame after the refresh: $s", s.firstFrame)
    }
}
