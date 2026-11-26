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
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newSearchResponseList
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import dev.asfakul.yumeiro.online.AccessLadder
import dev.asfakul.yumeiro.online.OnlineRepository
import dev.asfakul.yumeiro.online.OnlineSession
import dev.asfakul.yumeiro.player.PlaybackDiagnostics
import dev.asfakul.yumeiro.sources.SourceHub
import kotlinx.coroutines.delay
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

/**
 * B17 (Beta Group N, MAJOR): a title plays from whichever installed extension has a working source.
 *
 * Three local CloudStream-style sources on one server, each reading its site over HTTP:
 * - "own": the extension the title was opened in. Per case it gives no link, a dead link (404), a
 *   working link only after 9 s, or a working link at once.
 * - "other": has the same series; its episode 1 link is a real MP4 on the server.
 * - "decoy": has "<title> 2", a different show with a working link that must never be played.
 * Each case plays episode 1 in the real player and waits for the first drawn frame. Log tag YuGroupN.
 */
@RunWith(AndroidJUnit4::class)
class GroupNOtherSourcesDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = inst.targetContext
    private lateinit var server: MockWebServer
    private lateinit var base: String
    private val paths = Collections.synchronizedList(mutableListOf<String>())
    private val tag = System.nanoTime().toString().takeLast(6)
    private val title = "Yu B17 Show $tag"
    private fun log(s: String) = Log.i("YuGroupN", s)

    enum class Own { NONE, DEAD, SLOW, OK }

    /** One site: search and title pages are read from the server, so the test sees who was asked. */
    open class Site(override var name: String, override var mainUrl: String, private val show: String, private val file: String, private val wait: () -> Long = { 0L }, private val links: () -> Boolean = { true }) : MainAPI() {
        override val supportedTypes = setOf(TvType.TvSeries)
        override suspend fun search(query: String, page: Int): SearchResponseList {
            app.get("$mainUrl/$file/search?q=$query")
            return newSearchResponseList(listOf(newTvSeriesSearchResponse(show, "$mainUrl/$file/show", TvType.TvSeries)), false)
        }
        override suspend fun search(query: String): List<SearchResponse> = search(query, 1).items
        override suspend fun load(url: String): LoadResponse {
            app.get(url)
            return newTvSeriesLoadResponse(show, url, TvType.TvSeries, listOf(
                newEpisode("$mainUrl/$file/e1.mp4", { name = "One"; episode = 1; season = 1 }, false),
                newEpisode("$mainUrl/$file/e2.mp4", { name = "Two"; episode = 2; season = 1 }, false),
            ))
        }
        override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
            app.get("$mainUrl/$file/links")
            delay(wait())
            if (links()) callback(newExtractorLink(name, name, data) { referer = mainUrl; quality = Qualities.P720.value })
            return true
        }
    }

    private var ownMode = Own.OK

    @Before fun setUp() {
        val clip = inst.context.assets.open("yumeiro_test_clip.mp4").use { it.readBytes() }
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val p = request.url.encodedPath
                paths += p
                return when {
                    p.endsWith(".mp4") && p.startsWith("/dead/") -> MockResponse.Builder().code(404).build()
                    p.endsWith(".mp4") -> MockResponse.Builder().code(200).addHeader("Content-Type", "video/mp4").body(Buffer().write(clip)).build()
                    else -> MockResponse.Builder().code(200).addHeader("Content-Type", "text/html").body("<html><body>ok</body></html>").build()
                }
            }
        }
        server.start()
        base = "http://${server.hostName}:${server.port}"
        AccessLadder.forgetLearned()
        dev.asfakul.yumeiro.online.OtherSources.init(ctx)
    }

    @After fun tearDown() {
        rule.runOnUiThread { runCatching { OnlineSession.end() } }
        SourceHub.remove(BRIDGE)
        dev.asfakul.yumeiro.online.OtherSources.setMode(ctx, dev.asfakul.yumeiro.online.OtherSources.defaultMode(DeviceClass.lowEnd))
        runCatching { server.close() }
    }

    private fun publish(): Triple<MainAPI, MainAPI, MainAPI> {
        val ownApi = object : Site("YuB17 Own $tag", base, title, "own", { if (ownMode == Own.SLOW) 9_000L else 0L }, { ownMode != Own.NONE }) {
            override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean =
                super.loadLinks(if (ownMode == Own.DEAD) data.replace("/own/", "/dead/") else data, isCasting, subtitleCallback, callback)
        }
        val other = Site("YuB17 Other $tag", base, title, "other")
        val decoy = Site("YuB17 Decoy $tag", base, "$title 2", "decoy")
        SourceHub.publishVideo(BRIDGE, SourceHub.Ecosystem.CLOUDSTREAM, listOf(ownApi, other, decoy))
        OnlineRepository.clearCaches()
        return Triple(ownApi, other, decoy)
    }

    /** Plays episode 1 and waits for a drawn frame (or failure). Returns the link that is playing, its extension and the time. */
    private fun play(case: String, own: MainAPI, mode: dev.asfakul.yumeiro.online.OtherSources.Mode): Triple<String?, String?, Long> {
        dev.asfakul.yumeiro.online.OtherSources.setMode(ctx, mode)
        OnlineRepository.clearCaches()
        paths.clear()
        PlaybackDiagnostics.reset("")
        val detail = runBlocking { OnlineRepository.load(own, "$base/own/show", force = true) }
        val t0 = System.currentTimeMillis()
        rule.runOnUiThread { OnlineSession.play(ctx, detail, version = "", episodeIndex = 0, resumeMs = 0L) }
        runCatching {
            rule.waitUntil(90_000) {
                val s = OnlineSession.state.value
                (PlaybackDiagnostics.state.value.firstFrame && s?.phase == OnlineSession.Phase.PLAYING) || s?.phase == OnlineSession.Phase.FAILED
            }
        }
        val ms = System.currentTimeMillis() - t0
        val s = OnlineSession.state.value
        val d = PlaybackDiagnostics.state.value
        val seen = synchronized(paths) { paths.toList() }
        log("GN_CASE B17 $case phase=${s?.phase} via=${s?.link?.via} url=${s?.link?.url} firstFrame=${d.firstFrame} ms=$ms " +
            "links=${s?.links?.map { (it.via ?: "own") + ":" + it.url.substringAfter(base) }} others=${s?.others} message=${s?.message} paths=$seen")
        val playing = if (d.firstFrame && s?.phase == OnlineSession.Phase.PLAYING) s.link?.url?.substringAfter(base) else null
        // A fresh player per case: the same link again must not be taken as "already playing".
        rule.runOnUiThread { OnlineSession.end(); dev.asfakul.yumeiro.player.PlaybackSession.releaseNow() }
        // Let the player screen leave the composition, so the next case opens a new one (same link included).
        runCatching { rule.waitUntil(10_000) { OnlineSession.state.value == null && dev.asfakul.yumeiro.player.PlaybackSession.playerForTest == null } }
        rule.waitForIdle()
        val until = System.currentTimeMillis() + 800
        runCatching { rule.waitUntil(3_000) { System.currentTimeMillis() > until } }
        return Triple(playing, s?.link?.via, ms)
    }

    @Test fun anotherExtensionPlaysWhenTheOwnOneHasNothing() {
        val (own, other, decoy) = publish()
        ownMode = Own.NONE
        val (url, via, _) = play("none", own, dev.asfakul.yumeiro.online.OtherSources.Mode.PARALLEL)
        assertEquals("played from the other extension (own has no link)", "/other/e1.mp4", url)
        assertEquals(other.name, via)
        assertTrue("the decoy show was never played", synchronized(paths) { paths.none { it == "/decoy/e1.mp4" } })
        log("GN_RESULT B17 none PASS")
    }

    @Test fun aDeadOwnLinkFailsOverToAnotherExtension() {
        val (own, other, _) = publish()
        ownMode = Own.DEAD
        val (url, via, _) = play("dead", own, dev.asfakul.yumeiro.online.OtherSources.Mode.PARALLEL)
        assertEquals("failed over to the other extension's working link", "/other/e1.mp4", url)
        assertEquals(other.name, via)
        log("GN_RESULT B17 dead PASS")
    }

    @Test fun theFastestWorkingSourceWins() {
        val (own, other, _) = publish()
        ownMode = Own.SLOW
        val (url, via, ms) = play("slow", own, dev.asfakul.yumeiro.online.OtherSources.Mode.PARALLEL)
        assertEquals("the other extension answered first and plays", "/other/e1.mp4", url)
        assertEquals(other.name, via)
        assertTrue("started before the slow own extension answered (9 s): $ms ms", ms < 9_000L)
        log("GN_RESULT B17 slow PASS")
    }

    @Test fun theOwnExtensionPlaysFirstWhenItWorks() {
        val (own, _, _) = publish()
        ownMode = Own.OK
        val (url, via, _) = play("ok", own, dev.asfakul.yumeiro.online.OtherSources.Mode.PARALLEL)
        assertEquals("/own/e1.mp4", url)
        assertEquals(null, via)
        // When needed: a working own extension means nobody else is even searched.
        val (url2, _, _) = play("ok-when-needed", own, dev.asfakul.yumeiro.online.OtherSources.Mode.WHEN_NEEDED)
        assertEquals("/own/e1.mp4", url2)
        assertTrue("no other site was asked: ${paths.toList()}", synchronized(paths) { paths.none { it.startsWith("/other/") || it.startsWith("/decoy/") } })
        // Off: the own extension only, even with nothing to play.
        ownMode = Own.NONE
        val (url3, _, _) = play("off", own, dev.asfakul.yumeiro.online.OtherSources.Mode.OFF)
        assertEquals(null, url3)
        assertTrue(synchronized(paths) { paths.none { it.startsWith("/other/") } })
        log("GN_RESULT B17 ok PASS")
    }

    private companion object { const val BRIDGE = "yu-groupn-b17" }
}
