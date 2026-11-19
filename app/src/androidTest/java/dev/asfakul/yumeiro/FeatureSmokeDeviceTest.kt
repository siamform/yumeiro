/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.player.PlaybackDiagnostics
import dev.asfakul.yumeiro.stream.IncomingMedia
import dev.asfakul.yumeiro.stream.PlaylistStore
import dev.asfakul.yumeiro.stream.Resolution
import dev.asfakul.yumeiro.stream.StreamRequest
import dev.asfakul.yumeiro.stream.StreamResolver
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tenth-cycle feature smoke (§10), the two flows no other device test plays end to end: a Live TV
 * channel from a playlist reaches its first frame, and a Web URL (an HTML page with a `<video>`) is
 * read, resolved to its video and played. Everything is served from a local server (the androidTest
 * clip), so the result does not depend on the internet. The other smoke areas (Home, Library,
 * Music, Manga, Downloads, Settings, Profiles, Yumix) are covered by their own device classes,
 * listed with run ids in the tenth handover report.
 */
@RunWith(AndroidJUnit4::class)
class FeatureSmokeDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var server: MockWebServer
    private var playlistId: String? = null
    private val run = (System.currentTimeMillis() % 100_000).toString()
    private val channel = "YuSmoke Channel $run"
    private val clip: ByteArray by lazy {
        InstrumentationRegistry.getInstrumentation().context.assets.open("yumeiro_test_clip.mp4").use { it.readBytes() }
    }

    private fun log(s: String) = Log.i("YuSmoke", s)

    private fun awaitFirstFrame(what: String) {
        runCatching { compose.waitUntil(60_000) { PlaybackDiagnostics.state.value.let { it.firstFrame || (it.error != null && it.state == "IDLE") } } }
        val s = PlaybackDiagnostics.state.value
        log("SMOKE $what -> $s")
        if (!s.firstFrame) FirstRun.shot("smoke_fail_" + what.filter { it.isLetterOrDigit() })
        assertTrue("$what: no first frame ($s)", s.firstFrame)
    }

    @Before fun setUp() {
        FirstRun.grant()
        PlaybackDiagnostics.reset("")
        server = MockWebServer()
        server.dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: mockwebserver3.RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                return when {
                    path.endsWith(".mp4") -> MockResponse.Builder().addHeader("Content-Type", "video/mp4").body(Buffer().write(clip)).build()
                    path.endsWith(".m3u") -> MockResponse.Builder().addHeader("Content-Type", "audio/x-mpegurl")
                        .body("#EXTM3U\n#EXTINF:-1 group-title=\"Smoke\",$channel\n${server.url("/live-$run.mp4")}\n").build()
                    path.endsWith(".html") -> MockResponse.Builder().addHeader("Content-Type", "text/html")
                        .body("<!doctype html><html><head><title>Smoke page</title></head><body><video controls src=\"/page-$run.mp4\"></video></body></html>").build()
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
    }

    @After fun tearDown() {
        playlistId?.let { p -> runCatching { runBlocking { PlaylistStore.delete(ctx, p) } } }
        runCatching { server.close() }
    }

    @Test fun a_liveTvChannelFromAPlaylistPlays() {
        FirstRun.toTabs(compose)
        val p = runBlocking { PlaylistStore.init(ctx); PlaylistStore.addRemote(ctx, "YuSmoke list $run", server.url("/list.m3u").toString()) }
        playlistId = p.id
        // The channel list as Live TV loads it (download + M3U parse); the tap itself is covered by
        // Nbug15RecentsDeviceTest, which opens the same IncomingMedia path with Recent.LIVE.
        val ch = runBlocking { PlaylistStore.channels(ctx, p.id, forceRefresh = true) }.firstOrNull { it.title == channel }
        log("SMOKE playlist ${p.id} -> $ch")
        assertTrue("the playlist's channel is listed", ch != null)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { IncomingMedia.open(ctx, ch!!, recent = IncomingMedia.Recent.LIVE) }
        awaitFirstFrame("Live TV channel")
    }

    @Test fun b_webUrlIsReadAndItsVideoPlays() {
        FirstRun.toTabs(compose)
        val page = server.url("/watch-$run.html").toString()
        val r = runBlocking { StreamResolver.resolve(ctx, StreamRequest(page, title = "Smoke page")) }
        log("SMOKE web url $page -> $r")
        assertTrue("a web page with a <video> resolves to that video: $r", r is Resolution.Playable && r.request.url.endsWith("/page-$run.mp4"))
        InstrumentationRegistry.getInstrumentation().runOnMainSync { IncomingMedia.open(ctx, (r as Resolution.Playable).request) }
        awaitFirstFrame("Web URL video")
    }
}
