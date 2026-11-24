/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addEpisodes
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import dev.asfakul.yumeiro.online.OnlineRepository
import dev.asfakul.yumeiro.online.OnlineSession
import dev.asfakul.yumeiro.online.OnlineSettings
import dev.asfakul.yumeiro.online.anime.AniSkip
import dev.asfakul.yumeiro.online.anime.AnimeSync
import dev.asfakul.yumeiro.player.PlaybackDiagnostics
import dev.asfakul.yumeiro.player.PlaybackSession
import dev.asfakul.yumeiro.sources.SourceHub
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * SKIP-3 (owner list 18): Skip Intro and Skip Outro in the real online player, end to end.
 *
 * A source (a CloudStream MainAPI, as an installed extension is) gives an anime episode with its
 * MyAnimeList id, or a TV episode with its IMDb id, whose video a local server streams with Range
 * support. The video is a plain clip whose length matches a real submission. Everything after the
 * source is the app's own path: OnlineSession.play -> the full-screen player -> AnimeSync asks the
 * live AniSkip (api.aniskip.com) or IntroDB (api.introdb.app) -> the player's Skip button. The test
 * moves the real ExoPlayer into the intro / credits, waits for the button the user sees, taps it and
 * reads the real player position. Screens go to shots/. Log tag YuSkipTest.
 *
 * Anime: Frieren episode 1 (MAL 52991). Its ending was submitted for a 1510.102 s cut, so the clip is
 * 1510 s and the ending "fits" (auto skip allowed); its openings were submitted for 1559.9 s and
 * 1470 s cuts, so the opening shows as a button only. TV: Stranger Things S1E1 (tt4574334), intro
 * confirmed by 3 people (auto skip allowed), credits by 1 (button). The expected times are computed
 * with the app's own AniSkip.pick from what the live API returned on the run, never typed in here.
 */
@RunWith(AndroidJUnit4::class)
class SkipIntroOutroPlayerDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = inst.targetContext
    private lateinit var server: MockWebServer
    private lateinit var base: String
    private fun log(s: String) = Log.i(TAG, s)

    private class Anime(base: String) : MainAPI() {
        override var name = "YuSkip Anime"
        override var mainUrl = base
        override val supportedTypes = setOf(TvType.Anime)
        override suspend fun load(url: String): LoadResponse = newAnimeLoadResponse("Frieren: Beyond Journey's End", url, TvType.Anime) {
            addEpisodes(DubStatus.Subbed, listOf(newEpisode("$mainUrl/v/1510.mp4", { name = "The Journey's End"; episode = 1 }, false)))
            addMalId(52991); addAniListId(154587)
        }
        override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
            callback(newExtractorLink(name, name, data) { quality = Qualities.P720.value }); return true
        }
    }

    private class Series(base: String) : MainAPI() {
        override var name = "YuSkip Series"
        override var mainUrl = base
        override val supportedTypes = setOf(TvType.TvSeries)
        override suspend fun load(url: String): LoadResponse = newTvSeriesLoadResponse("Stranger Things", url, TvType.TvSeries, listOf(
            newEpisode("$mainUrl/v/2950.mp4", { name = "The Vanishing of Will Byers"; season = 1; episode = 1 }, false),
        )) { addImdbId("tt4574334") }
        override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
            callback(newExtractorLink(name, name, data) { quality = Qualities.P720.value }); return true
        }
    }

    @Before fun setUp() {
        val clips = mapOf(
            "/v/1510.mp4" to inst.context.assets.open("yumeiro_skip_1510.mp4").use { it.readBytes() },
            "/v/2950.mp4" to inst.context.assets.open("yumeiro_skip_2950.mp4").use { it.readBytes() },
        )
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val clip = clips[request.url.encodedPath] ?: return MockResponse.Builder().code(404).build()
                // Range support, as a real file host has, so the player can seek without reading from the start.
                val range = request.headers["Range"]?.removePrefix("bytes=")?.split("-")
                val from = range?.getOrNull(0)?.toLongOrNull() ?: 0L
                val to = range?.getOrNull(1)?.toLongOrNull()?.coerceAtMost(clip.size - 1L) ?: (clip.size - 1L)
                val b = MockResponse.Builder().addHeader("Content-Type", "video/mp4").addHeader("Accept-Ranges", "bytes")
                return if (range == null) b.code(200).body(Buffer().write(clip)).build()
                else b.code(206).addHeader("Content-Range", "bytes $from-$to/${clip.size}")
                    .body(Buffer().write(clip, from.toInt(), (to - from + 1).toInt())).build()
            }
        }
        server.start()
        base = "http://${server.hostName}:${server.port}"
        OnlineSettings.setSkipIntro(ctx, true)
        OnlineSettings.setAutoSkip(ctx, false)
        PlaybackDiagnostics.reset("")
    }

    @After fun tearDown() {
        OnlineSettings.setAutoSkip(ctx, false)
        rule.runOnUiThread { runCatching { OnlineSession.end() } }
        SourceHub.remove(BRIDGE)
        runCatching { server.close() }
    }

    private fun shot(name: String) {
        inst.waitForIdleSync()
        val bmp = inst.uiAutomation.takeScreenshot() ?: return
        val dir = File(ctx.getExternalFilesDir(null), "shots").apply { mkdirs() }
        File(dir, "skip_$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
    }

    private fun onMain(block: () -> Unit) = inst.runOnMainSync(block)
    private fun position(): Long { var p = -1L; onMain { p = PlaybackSession.playerForTest?.currentPosition ?: -1L }; return p }
    private fun duration(): Long { var d = -1L; onMain { d = PlaybackSession.playerForTest?.duration ?: -1L }; return d }
    private fun seek(ms: Long) = onMain { PlaybackSession.playerForTest?.let { it.seekTo(ms); it.play() } }
    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()

    /** Plays the source's first episode in the real player and returns the segments the player will use. */
    private fun play(api: MainAPI, title: String): List<AniSkip.Segment> {
        SourceHub.publishVideo(BRIDGE, SourceHub.Ecosystem.CLOUDSTREAM, listOf(api))
        OnlineRepository.clearCaches()
        FirstRun.toTabs(rule)
        val detail = kotlinx.coroutines.runBlocking { OnlineRepository.load(api, "$base/title/$title") }
        log("detail ${detail.name} kind=${detail.kind} mal=${detail.malId} imdb=${detail.imdbId}")
        rule.runOnUiThread { OnlineSession.play(ctx, detail, episodeIndex = 0) }
        rule.waitUntil(120_000) { PlaybackDiagnostics.state.value.firstFrame || OnlineSession.state.value?.phase == OnlineSession.Phase.FAILED }
        assertTrue("episode did not play: ${OnlineSession.state.value?.message} ${PlaybackDiagnostics.state.value}", PlaybackDiagnostics.state.value.firstFrame)
        rule.waitUntil(20_000) { duration() > 0 }
        // Live API answer (with the app's two retries on network failure).
        rule.waitUntil(60_000) { AnimeSync.skips.value?.segments?.isNotEmpty() == true }
        val all = AnimeSync.skips.value!!.segments
        val picked = AniSkip.pick(all, duration())
        log("CASE $title duration=${duration()} live=${all.map { "${it.kind}@${it.startMs}-${it.endMs}/${it.episodeLengthMs}" }} picked=${picked.map { "${it.kind}@${it.startMs}-${it.endMs} fits=${it.fits}" }}")
        return picked
    }

    /** Puts the player 2 s into [seg], waits for the button with [tag], taps it, and checks the player left the segment. */
    private fun tapSkip(seg: AniSkip.Segment, tag: String, label: String) {
        seek(seg.startMs + 2_000)
        rule.waitUntil(15_000) { has(tag) }
        shot("${label}_button")
        val before = position()
        rule.onNodeWithTag(tag, true).performClick()
        rule.waitUntil(10_000) { position() >= seg.endMs - 1_000 || PlaybackDiagnostics.state.value.state == "ENDED" }
        val after = position()
        log("CASE $label tapped at $before -> $after (segment ${seg.startMs}-${seg.endMs})")
        shot("${label}_after")
        assertTrue("$label: player at $after did not move past ${seg.endMs}", after >= seg.endMs - 1_000 || PlaybackDiagnostics.state.value.state == "ENDED")
    }

    @Test fun a_animeIntroAndOutroButtonsSkipInTheRealPlayer() {
        val segs = play(Anime(base), "frieren")
        val intro = segs.firstOrNull { it.kind == AniSkip.Kind.INTRO || it.kind == AniSkip.Kind.MIXED_INTRO }
        val outro = segs.firstOrNull { it.kind == AniSkip.Kind.OUTRO || it.kind == AniSkip.Kind.MIXED_OUTRO }
        assertNotNull("AniSkip gave no opening for Frieren 1: $segs", intro)
        assertNotNull("AniSkip gave no ending for Frieren 1: $segs", outro)
        tapSkip(intro!!, "online_skip", "anime_intro")
        tapSkip(outro!!, "online_skip_outro", "anime_outro")
        log("SKIP_RESULT a PASS")
    }

    @Test fun b_autoSkipJumpsOnlyOverConfirmedTimes() {
        val segs = play(Anime(base), "frieren_auto")
        val outro = segs.first { it.kind == AniSkip.Kind.OUTRO || it.kind == AniSkip.Kind.MIXED_OUTRO }
        val intro = segs.first { it.kind == AniSkip.Kind.INTRO || it.kind == AniSkip.Kind.MIXED_INTRO }
        OnlineSettings.setAutoSkip(ctx, true)
        // The opening was submitted for another cut: still a button, never a jump.
        assertTrue("precondition: the opening is from another cut", !intro.fits)
        seek(intro.startMs + 2_000)
        rule.waitUntil(15_000) { has("online_skip") }
        val p = position()
        shot("auto_intro_button")
        log("CASE auto intro stays at $p with the button")
        assertTrue("an unconfirmed opening was skipped automatically (at $p)", p in intro.startMs until intro.endMs)
        // The ending was submitted for this length: skipped with no tap.
        assertTrue("precondition: the ending fits this cut", outro.fits)
        seek(outro.startMs + 1_000)
        rule.waitUntil(10_000) { position() >= outro.endMs - 1_000 || PlaybackDiagnostics.state.value.state == "ENDED" }
        log("CASE auto outro -> ${position()} state=${PlaybackDiagnostics.state.value.state}")
        assertTrue("a confirmed ending was not skipped automatically", position() >= outro.endMs - 1_000 || PlaybackDiagnostics.state.value.state == "ENDED")
        log("SKIP_RESULT b PASS")
    }

    @Test fun c_tvIntroAndCreditsFromIntroDb() {
        val segs = play(Series(base), "stranger")
        val intro = segs.firstOrNull { it.kind == AniSkip.Kind.INTRO }
        val outro = segs.firstOrNull { it.kind == AniSkip.Kind.OUTRO }
        assertNotNull("IntroDB gave no intro for Stranger Things S1E1: $segs", intro)
        assertNotNull("IntroDB gave no credits for Stranger Things S1E1: $segs", outro)
        tapSkip(intro!!, "online_skip", "tv_intro")
        tapSkip(outro!!, "online_skip_outro", "tv_outro")
        assertEquals("skip lookups stay on", true, OnlineSettings.skipIntro.value)
        log("SKIP_RESULT c PASS")
    }

    companion object {
        const val TAG = "YuSkipTest"
        const val BRIDGE = "yu-skip-test"
    }
}
