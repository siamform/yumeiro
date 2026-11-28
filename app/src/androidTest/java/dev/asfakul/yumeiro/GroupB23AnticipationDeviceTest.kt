/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
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
import dev.asfakul.yumeiro.online.OtherSources
import dev.asfakul.yumeiro.online.anticipation.AnticipationPrefs
import dev.asfakul.yumeiro.online.anticipation.Anticipator
import dev.asfakul.yumeiro.online.anticipation.PrepStore
import dev.asfakul.yumeiro.player.PlaybackDiagnostics
import dev.asfakul.yumeiro.sources.SourceHub
import dev.asfakul.yumeiro.ui.screens.watch.WatchPrefs
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections

/**
 * B23 (VERY HIGH): the Anticipation Engine prepares the next episodes while one plays, and offers an alternative
 * source when the episode's own extension has none.
 *
 * Local CloudStream-style sources on one MockWebServer: "own" (the title's extension; per case it has links for
 * every episode, or for episode 1 only) and "other" (the same series, every episode). Each loadLinks call reads
 * `/<site>/links/<episode file>` from the server, so the test sees exactly which episode was asked and when.
 * Episode 1 plays in the real player (a 12 s clip); Next then must start from what was prepared. Log tag YuB23.
 */
@RunWith(AndroidJUnit4::class)
class GroupB23AnticipationDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = inst.targetContext
    private lateinit var server: MockWebServer
    private lateinit var base: String
    private val paths = Collections.synchronizedList(mutableListOf<String>())
    private val tag = System.nanoTime().toString().takeLast(6)
    private val title = "Yu B23 Show $tag"
    private fun log(s: String) = Log.i("YuB23", s)
    private var autoNextBefore = true

    open class Site(override var name: String, override var mainUrl: String, private val show: String, private val file: String, private val has: (String) -> Boolean = { true }) : MainAPI() {
        override val supportedTypes = setOf(TvType.TvSeries)
        override suspend fun search(query: String, page: Int): SearchResponseList {
            app.get("$mainUrl/$file/search?q=$query")
            return newSearchResponseList(listOf(newTvSeriesSearchResponse(show, "$mainUrl/$file/show", TvType.TvSeries)), false)
        }
        override suspend fun search(query: String): List<SearchResponse> = search(query, 1).items
        override suspend fun load(url: String): LoadResponse {
            app.get(url)
            return newTvSeriesLoadResponse(show, url, TvType.TvSeries, (1..4).map { n ->
                newEpisode("$mainUrl/$file/e$n.mp4", { name = "Ep $n"; episode = n; season = 1 }, false)
            })
        }
        override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
            val ep = data.substringAfterLast('/')
            app.get("$mainUrl/$file/links/$ep")
            if (has(ep)) callback(newExtractorLink(name, name, data) { referer = mainUrl; quality = Qualities.P720.value })
            return true
        }
    }

    @Before fun setUp() {
        val clip = inst.context.assets.open("yumeiro_test_clip.mp4").use { it.readBytes() }
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val p = request.url.encodedPath
                paths += p
                return when {
                    p.endsWith(".mp4") -> MockResponse.Builder().code(200).addHeader("Content-Type", "video/mp4").body(Buffer().write(clip)).build()
                    else -> MockResponse.Builder().code(200).addHeader("Content-Type", "text/html").body("<html><body>ok</body></html>").build()
                }
            }
        }
        server.start()
        base = "http://${server.hostName}:${server.port}"
        AccessLadder.forgetLearned()
        OtherSources.init(ctx)
        OtherSources.setMode(ctx, OtherSources.Mode.WHEN_NEEDED)
        WatchPrefs.init(ctx)
        autoNextBefore = WatchPrefs.autoNext.value
        WatchPrefs.setAutoNext(ctx, false) // the 12 s clip ends quickly; Next is pressed by the test
        AnticipationPrefs.init(ctx)
        AnticipationPrefs.setEnabled(ctx, true)
        AnticipationPrefs.setAskBeforeAlternative(ctx, true)
        Anticipator.clear()
    }

    @After fun tearDown() {
        close()
        SourceHub.remove(BRIDGE)
        WatchPrefs.setAutoNext(ctx, autoNextBefore)
        AnticipationPrefs.setAskBeforeAlternative(ctx, true)
        AnticipationPrefs.setEnabled(ctx, true)
        OtherSources.setMode(ctx, OtherSources.defaultMode(DeviceClass.lowEnd))
        runCatching { server.close() }
    }

    private fun publish(ownHas: (String) -> Boolean): Pair<MainAPI, MainAPI> {
        val own = Site("YuB23 Own $tag", base, title, "own", ownHas)
        val other = Site("YuB23 Other $tag", base, title, "other")
        SourceHub.publishVideo(BRIDGE, SourceHub.Ecosystem.CLOUDSTREAM, listOf(own, other))
        OnlineRepository.clearCaches()
        return own to other
    }

    private fun seen() = synchronized(paths) { paths.toList() }

    private fun waitFrame(ms: Long = 90_000): Boolean = runCatching {
        rule.waitUntil(ms) {
            val s = OnlineSession.state.value
            (PlaybackDiagnostics.state.value.firstFrame && s?.phase == OnlineSession.Phase.PLAYING) || s?.phase == OnlineSession.Phase.FAILED
        }
        PlaybackDiagnostics.state.value.firstFrame && OnlineSession.state.value?.phase == OnlineSession.Phase.PLAYING
    }.getOrDefault(false)

    private fun startEpisodeOne(own: MainAPI) {
        PlaybackDiagnostics.reset("")
        val detail = runBlocking { OnlineRepository.load(own, "$base/own/show", force = true) }
        rule.runOnUiThread { OnlineSession.play(ctx, detail, version = "", episodeIndex = 0, resumeMs = 0L) }
        assertTrue("episode 1 played", waitFrame())
    }

    /** Waits (polling the server log) until [path] was requested, or [ms] passed. */
    private fun waitPath(path: String, ms: Long): Boolean = runCatching { rule.waitUntil(ms) { seen().any { it == path } }; true }.getOrDefault(false)

    private fun next(): Long {
        PlaybackDiagnostics.reset("")
        val t0 = System.currentTimeMillis()
        rule.runOnUiThread { OnlineSession.next(ctx) }
        return t0
    }

    private fun close() {
        log("B23_CLOSE ${UiLiveness.leaveAndClose(rule).second}")
        rule.runOnUiThread {
            androidx.lifecycle.ViewModelProvider(rule.activity)[dev.asfakul.yumeiro.ui.viewmodel.MainViewModel::class.java].closeVideoPlayer()
            OnlineSession.end(); dev.asfakul.yumeiro.player.PlaybackSession.releaseNow()
        }
        runCatching { rule.waitUntil(10_000) { OnlineSession.state.value == null && dev.asfakul.yumeiro.player.PlaybackSession.playerForTest == null } }
        rule.waitForIdle()
    }

    private fun waitTag(tagName: String) = rule.waitUntil(20_000) { rule.onAllNodesWithTag(tagName, true).fetchSemanticsNodes().isNotEmpty() }

    private fun click(tagName: String) {
        waitTag(tagName)
        rule.onAllNodesWithTag(tagName, true)[0].performClick()
    }

    /** a: while episode 1 plays, episode 2 is prepared; Next starts it without asking the extension again. */
    @Test fun a_nextEpisodeStartsFromWhatWasPrepared() {
        val (own, _) = publish { true }
        startEpisodeOne(own)
        val prepared = waitPath("/own/links/e2.mp4", 60_000)
        log("B23_CASE a prepared=$prepared lowEnd=${DeviceClass.lowEnd} paths=${seen()}")
        assertTrue("episode 2's links were fetched in the background while episode 1 played", prepared)
        if (!DeviceClass.lowEnd) assertTrue("three episodes ahead on a normal phone", waitPath("/own/links/e3.mp4", 30_000) && waitPath("/own/links/e4.mp4", 30_000))
        rule.waitUntil(15_000) { Anticipator.isReady(own.name, "$base/own/show", "", "$base/own/e2.mp4") }
        val before = seen().count { it == "/own/links/e2.mp4" }
        val t0 = next()
        val ok = waitFrame()
        val ms = System.currentTimeMillis() - t0
        val after = seen().count { it == "/own/links/e2.mp4" }
        log("B23_CASE a next ok=$ok ms=$ms e2-links before=$before after=$after url=${OnlineSession.state.value?.link?.url}")
        assertTrue("episode 2 played", ok)
        assertEquals("/own/e2.mp4", OnlineSession.state.value?.link?.url?.substringAfter(base))
        assertEquals("Next used the prepared links (no second loadLinks for episode 2)", before, after)
        log("B23_RESULT a PASS ms=$ms")
    }

    /** b: the own extension has nothing for episode 2; the other one was prepared and is offered, not swapped in. */
    @Test fun b_anAlternativeIsOfferedAndPlaysOnlyWhenChosen() {
        val (own, other) = publish { it == "e1.mp4" }
        startEpisodeOne(own)
        assertTrue("the other extension's episode 2 was prepared", waitPath("/other/links/e2.mp4", 90_000))
        rule.waitUntil(20_000) { Anticipator.isReady(own.name, "$base/own/show", "", "$base/own/e2.mp4") }
        next()
        waitTag("anticipation_offer")
        val s = OnlineSession.state.value
        log("B23_CASE b offer=${s?.offer} phase=${s?.phase} firstFrame=${PlaybackDiagnostics.state.value.firstFrame}")
        assertEquals(other.name, s?.offer?.via)
        assertFalse("nothing plays before the person chooses", PlaybackDiagnostics.state.value.firstFrame)
        click("anticipation_play_alternative")
        assertTrue("the alternative played", waitFrame())
        assertEquals("/other/e2.mp4", OnlineSession.state.value?.link?.url?.substringAfter(base))
        assertEquals(other.name, OnlineSession.state.value?.link?.via)
        log("B23_RESULT b PASS")
    }

    /** c: "Never show again" plays this one and the next alternatives without the card (each with a note). */
    @Test fun c_neverShowAgainPlaysAlternativesDirectly() {
        val (own, other) = publish { it == "e1.mp4" }
        startEpisodeOne(own)
        assertTrue(waitPath("/other/links/e2.mp4", 90_000))
        rule.waitUntil(20_000) { Anticipator.isReady(own.name, "$base/own/show", "", "$base/own/e2.mp4") }
        next()
        click("anticipation_never")
        assertTrue("the alternative played", waitFrame())
        assertFalse(AnticipationPrefs.askBeforeAlternative.value)
        // Episode 3: prepared from the other source too, now played with no card.
        assertTrue(waitPath("/other/links/e3.mp4", 90_000))
        rule.waitUntil(20_000) { Anticipator.isReady(own.name, "$base/own/show", "", "$base/own/e3.mp4") }
        next()
        assertTrue("episode 3 played straight from the alternative", waitFrame())
        assertEquals(null, OnlineSession.state.value?.offer)
        assertEquals(other.name, OnlineSession.state.value?.link?.via)
        log("B23_RESULT c PASS")
    }

    /** d: an episode no source has is remembered; after a restart of the engine it is not searched again. */
    @Test fun d_aMissIsNotSearchedAgainAfterARestart() {
        val (own, _) = publish { it == "e1.mp4" }
        SourceHub.publishVideo(BRIDGE, SourceHub.Ecosystem.CLOUDSTREAM, listOf(own)) // no other source at all
        OnlineRepository.clearCaches()
        startEpisodeOne(own)
        assertTrue(waitPath("/own/links/e2.mp4", 60_000))
        rule.waitUntil(30_000) { PrepStore.get(ctx).isMiss(Anticipator.episodeKey(own.name, "$base/own/show", "", "$base/own/e2.mp4"), System.currentTimeMillis()) }
        close()
        // "Restart": memory gone, the store read again from the file.
        Anticipator.clear(); PrepStore.resetForTest()
        paths.clear()
        startEpisodeOne(own)
        Thread.sleep(20_000)
        log("B23_CASE d paths after restart=${seen()}")
        assertTrue("episode 2 was not asked again", seen().none { it == "/own/links/e2.mp4" })
        log("B23_RESULT d PASS")
    }

    /** e: with "Prepare upcoming episodes" off nothing is fetched ahead, and Next still plays the normal way. */
    @Test fun e_switchedOffMeansTheNormalPath() {
        AnticipationPrefs.setEnabled(ctx, false)
        val (own, _) = publish { true }
        startEpisodeOne(own)
        Thread.sleep(20_000)
        assertTrue("nothing prepared ahead", seen().none { it == "/own/links/e2.mp4" })
        next()
        assertTrue("Next still plays", waitFrame())
        assertEquals("/own/e2.mp4", OnlineSession.state.value?.link?.url?.substringAfter(base))
        log("B23_RESULT e PASS")
    }

    private companion object { const val BRIDGE = "yu-b23-anticipation" }
}
