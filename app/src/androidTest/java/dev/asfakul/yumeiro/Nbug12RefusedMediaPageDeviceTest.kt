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
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import dev.asfakul.yumeiro.online.AccessLadder
import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.online.OnlineRepository
import dev.asfakul.yumeiro.online.OnlineSession
import dev.asfakul.yumeiro.player.PlaybackDiagnostics
import dev.asfakul.yumeiro.sources.SourceHub
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

/**
 * NBUG-12 (owner bug 2, 2026-11-18): Porn4fans-style titles fail with "Access denied" and the player
 * cycles media player → VLC → host web player without ever playing.
 *
 * Reproduced from the real extension (phisher98/CXXX Porn4fans.kt): `load()` gives the page's
 * ld+json `contentUrl` (a direct `/get_file/…mp4/` link) as the movie's data and `loadLinks()` hands
 * that same URL out with `referer = mainUrl`. The site now refuses that URL (HTTP 403) and only
 * serves the `?v-acctoken=` link its own player builds on the title page.
 *
 * Every header variant and the fresh `loadLinks` must fail (the URL is dead), so the last resort —
 * playing what the site's own page plays — is the only way to recover. Here a local server is that
 * site: the extension's `/get_file/…` → 403; `/video/1/` → a KVS page with no `<video>` element, only
 * the player config, whose 480p address is scrambled (`function/0/`, de-scrambled with the page's
 * `license_code`) and carries `?v-acctoken=…`; that real address → 200, a real MP4. The title must end
 * up playing it (state PLAYING with the page id, first frame drawn). Log tag YuNb12Test.
 */
@RunWith(AndroidJUnit4::class)
class Nbug12RefusedMediaPageDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = inst.targetContext
    private lateinit var server: MockWebServer
    private lateinit var base: String
    private val paths = Collections.synchronizedList(mutableListOf<String>())
    private fun log(s: String) = Log.i(TAG, s)

    private class Kvs(base: String) : MainAPI() {
        override var name = "YuNB12 KVS"
        override var mainUrl = base
        override val supportedTypes = setOf(TvType.Movie)
        override suspend fun load(url: String): LoadResponse =
            newMovieLoadResponse("Yu NB12 Film", url, TvType.Movie, "$mainUrl/get_file/5/deadbeef/12000/12934/12934_720p.mp4/")
        override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
            callback(newExtractorLink(name, name, data) { this.referer = mainUrl; this.quality = Qualities.Unknown.value })
            return true
        }
    }

    /** NB12-5: the same site as a two-episode series; both episodes' links are refused files. */
    private class KvsSeries(base: String) : MainAPI() {
        override var name = "YuNB12 KVS Series"
        override var mainUrl = base
        override val supportedTypes = setOf(TvType.TvSeries)
        override suspend fun load(url: String): LoadResponse = newTvSeriesLoadResponse("Yu NB12 Show", url, TvType.TvSeries, listOf(
            newEpisode("$mainUrl/get_file/5/deadbeef/1/e1.mp4/", { name = "E1"; episode = 1 }, false),
            newEpisode("$mainUrl/get_file/5/deadbeef/1/e2.mp4/", { name = "E2"; episode = 2 }, false),
        ))
        override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
            callback(newExtractorLink(name, name, data) { this.referer = mainUrl; this.quality = Qualities.Unknown.value })
            return true
        }
    }

    @Before fun setUp() {
        val clip = inst.context.assets.open("yumeiro_test_clip.mp4").use { it.readBytes() }
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                paths += request.url.encodedPath + (request.url.encodedQuery?.let { "?$it" } ?: "")
                val p = request.url.encodedPath
                return when {
                    p == "/video/1/" -> MockResponse.Builder().code(200).addHeader("Content-Type", "text/html; charset=UTF-8")
                        .body(kvsPage(base)).build()
                    p == REAL_PATH && request.url.queryParameter("v-acctoken") == TOKEN ->
                        MockResponse.Builder().code(200).addHeader("Content-Type", "video/mp4").addHeader("Accept-Ranges", "none")
                            .body(Buffer().write(clip)).build()
                    // Everything else under /get_file/ (the extension's contentUrl, the scrambled address, no token): refused.
                    p.startsWith("/get_file/") -> MockResponse.Builder().code(403)
                        .addHeader("Content-Type", "text/html; charset=UTF-8").body("<html><body>Access denied</body></html>").build()
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
        base = "http://${server.hostName}:${server.port}"
        AccessLadder.forgetLearned()
        PlaybackDiagnostics.reset("")
    }

    @After fun tearDown() {
        rule.runOnUiThread { runCatching { OnlineSession.end() } }
        SourceHub.remove(BRIDGE); SourceHub.remove(BRIDGE_SERIES)
        AccessLadder.forgetLearned()
        runCatching { server.close() }
    }

    @Test fun refusedDirectFileRecoversFromTheTitlePage() {
        val api = Kvs(base)
        SourceHub.publishVideo(BRIDGE, SourceHub.Ecosystem.CLOUDSTREAM, listOf(api))
        OnlineRepository.clearCaches()
        val item = OnlineItem(name = "Yu NB12 Film", url = "$base/video/1/", api = api.name, posterUrl = null, type = "Movie")
        var showedTitle = false
        rule.runOnUiThread { OnlineSession.open(ctx, item) { showedTitle = true } }

        val start = System.currentTimeMillis()
        var phase: OnlineSession.Phase? = null
        runCatching {
            rule.waitUntil(240_000) {
                val s = OnlineSession.state.value
                phase = s?.phase
                PlaybackDiagnostics.state.value.firstFrame && s?.videoId?.endsWith(":web") == true || s?.phase == OnlineSession.Phase.FAILED || showedTitle
            }
        }
        val s = OnlineSession.state.value
        val d = PlaybackDiagnostics.state.value
        val seen = synchronized(paths) { paths.toList() }
        log("NB12_CASE a phase=${s?.phase} videoId=${s?.videoId} message=${s?.message} firstFrame=${d.firstFrame} error=${d.error} " +
            "showedTitle=$showedTitle ms=${System.currentTimeMillis() - start} lastPhase=$phase")
        log("NB12_CASE a requests=${seen.joinToString(" ")}")
        assertTrue("the title page was never loaded (capture picked another page): $seen", seen.any { it.startsWith("/video/1/") })
        assertTrue("the page's de-scrambled, tokenised stream was never fetched: $seen", seen.any { it == "$REAL_PATH?v-acctoken=$TOKEN" })
        assertTrue("did not play from the title page: phase=${s?.phase} videoId=${s?.videoId} message=${s?.message} diag=$d",
            s?.phase == OnlineSession.Phase.PLAYING && s.videoId?.endsWith(":web") == true && d.firstFrame)
        log("NB12_RESULT PASS")
    }

    /** Waits until the current episode plays from the title page, fails, or [ms] pass. */
    private fun waitWeb(ms: Long, index: Int): Boolean = runCatching {
        rule.waitUntil(ms) {
            val s = OnlineSession.state.value
            (s?.index == index && PlaybackDiagnostics.state.value.firstFrame && s.videoId?.endsWith(":web") == true) || s?.phase == OnlineSession.Phase.FAILED
        }; true
    }.getOrDefault(false)

    /**
     * NB12-5: episode 1 recovers from the title page; then episode 2 (also a refused file) must get
     * its own refresh and page step. Before, `playEpisode` kept `refreshed` and `captured` from
     * episode 1, so episode 2 skipped both and stayed on the player error.
     */
    @Test fun nextEpisodeGetsItsOwnRecovery() {
        val api = KvsSeries(base)
        SourceHub.publishVideo(BRIDGE_SERIES, SourceHub.Ecosystem.CLOUDSTREAM, listOf(api))
        OnlineRepository.clearCaches()
        // A series opens its title screen; a tap on episode 1 there calls OnlineSession.play.
        val detail = kotlinx.coroutines.runBlocking { OnlineRepository.load(api, "$base/video/1/") }
        assertTrue("precondition: the series has two episodes", OnlineSession.episodesOf(detail, OnlineSession.defaultVersion(detail.versions)).size == 2)
        rule.runOnUiThread { OnlineSession.play(ctx, detail, episodeIndex = 0) }
        val e1 = waitWeb(240_000, 0)
        val s1 = OnlineSession.state.value
        log("NB12_CASE b ep1 ok=$e1 index=${s1?.index} phase=${s1?.phase} videoId=${s1?.videoId} firstFrame=${PlaybackDiagnostics.state.value.firstFrame}")
        assertTrue("precondition: episode 1 did not play from the title page: phase=${s1?.phase} videoId=${s1?.videoId}",
            s1?.index == 0 && s1.videoId?.endsWith(":web") == true)
        val pagesBefore = synchronized(paths) { paths.count { it.startsWith("/video/1/") } }
        PlaybackDiagnostics.reset("")
        rule.runOnUiThread { OnlineSession.playEpisode(ctx, 1) }
        val e2 = waitWeb(240_000, 1)
        val s2 = OnlineSession.state.value
        val d = PlaybackDiagnostics.state.value
        val seen = synchronized(paths) { paths.toList() }
        val pagesAfter = seen.count { it.startsWith("/video/1/") }
        log("NB12_CASE b ep2 ok=$e2 index=${s2?.index} phase=${s2?.phase} videoId=${s2?.videoId} message=${s2?.message} firstFrame=${d.firstFrame} error=${d.error} pageLoads=$pagesBefore->$pagesAfter")
        log("NB12_CASE b requests=${seen.joinToString(" ")}")
        assertTrue("episode 2's link was never requested: $seen", seen.any { it.endsWith("/e2.mp4/") })
        assertTrue("episode 2 never reached the title page again (pageLoads $pagesBefore -> $pagesAfter)", pagesAfter > pagesBefore)
        assertTrue("episode 2 did not play from the title page: phase=${s2?.phase} videoId=${s2?.videoId} message=${s2?.message} diag=$d",
            s2?.index == 1 && s2.phase == OnlineSession.Phase.PLAYING && s2.videoId?.endsWith(":web") == true && d.firstFrame)
        log("NB12_RESULT b PASS")
    }

    /**
     * A KVS title page: no <video> element, only the player config (as on the real site). The 480p
     * address is scrambled (`function/0/`); the real hash below is what yt-dlp's
     * `_kvs_get_real_url` gives for it with this licence code. 1080p needs a sign-in.
     */
    private fun kvsPage(base: String) = """<html><head><title>Yu NB12 Film</title>
        <script type="application/ld+json">{"name":"Yu NB12 Film","contentUrl":"$base/get_file/5/deadbeef/12000/12934/12934_720p.mp4/"}</script>
        </head><body><div id="kt_player"></div><script>
        var t5cab = { video_id: '1', license_code: '${'$'}546156418018655', rnd: '1795031175',
          video_url: 'function/0/$base$SCRAMBLED_PATH?v-acctoken=$TOKEN', video_url_text: '480p',
          video_alt_url2: '$base/login-required/', video_alt_url2_text: '1080p', video_alt_url2_redirect: '1',
          timeline_screens_url: '$base/contents/1/timelines/{time}.jpg' };
        kt_player('kt_player', '$base/player/kt_player.swf?v=2.7.5', '100%', '100%', t5cab);
        </script></body></html>"""

    private companion object {
        const val SCRAMBLED_PATH = "/get_file/5/0123456789abcdefghijklmnopqrstuvXY/1000/1/1.mp4/"
        const val REAL_PATH = "/get_file/5/idom0nc892us75lk6thgv3j4fpqreab1XY/1000/1/1.mp4/"
        const val TOKEN = "YUNB12TOKEN"
        const val TAG = "YuNb12Test"
        const val BRIDGE = "yu-nb12-test"
        const val BRIDGE_SERIES = "yu-nb12-series"
    }
}
