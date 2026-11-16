/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.lyrics.LrcLib
import dev.asfakul.yumeiro.lyrics.LyricsSource
import dev.asfakul.yumeiro.lyrics.TrackDetailsRepository
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.online.OnlineDetail
import dev.asfakul.yumeiro.online.OnlineEpisode
import dev.asfakul.yumeiro.online.OnlineKind
import dev.asfakul.yumeiro.online.OnlineSettings
import dev.asfakul.yumeiro.online.anime.AnimeSync
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections

/**
 * G9 S1–S22: switches whose effect is a network lookup. The app's shared OkHttp client is swapped
 * (reflection on `NetworkClient.client`) for one whose interceptor records every URL and answers
 * LRCLIB and AniSkip with canned JSON, so nothing leaves the emulator and the answer is fixed:
 *  a Find lyrics online (`lyrics_online_toggle`): on → one LRCLIB request and ONLINE lyrics;
 *    off → no LRCLIB request, no lyrics;
 *  b Skip intro & outro (`setting_skip_intro`): on → an AniSkip request and the intro segment is
 *    loaded; off → no AniSkip request, no segments.
 * Every value and the client are put back. Log tag YuOnlineFx: OFX_CASE lines, OFX_RESULT PASS.
 */
@RunWith(AndroidJUnit4::class)
class OnlineSwitchesEffectDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private fun log(m: String) = Log.i("YuOnlineFx", m)
    private val seen = Collections.synchronizedList(ArrayList<String>())
    private val field = NetworkClient::class.java.getDeclaredField("client").apply { isAccessible = true }
    private var original: Any? = null
    private val run = System.currentTimeMillis()

    private val fake = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
        val url = chain.request().url
        seen += url.toString()
        val json = "application/json".toMediaType()
        val body = when (url.host) {
            "lrclib.net" -> """{"id":1,"trackName":"t","artistName":"a","albumName":"","duration":180,"instrumental":false,"plainLyrics":"Hello","syncedLyrics":"[00:01.00]Hello\n[00:03.00]World"}"""
            "api.aniskip.com" -> """{"found":true,"results":[{"interval":{"startTime":10.0,"endTime":95.0},"skipType":"op","skipId":"x","episodeLength":1440}],"message":"ok","statusCode":200}"""
            else -> null
        }
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
            .code(if (body != null) 200 else 404).message(if (body != null) "OK" else "Not Found")
            .body((body ?: "").toResponseBody(json)).build()
    }).build()

    private var lyricsWas = true
    private var skipWas = true

    @Before fun setUp() {
        NetworkClient.get(ctx)
        original = field.get(NetworkClient)
        field.set(NetworkClient, fake)
        LrcLib.init(ctx); OnlineSettings.init(ctx)
        lyricsWas = LrcLib.enabled.value
        skipWas = OnlineSettings.skipIntro.value
    }

    @After fun tearDown() {
        field.set(NetworkClient, original)
        LrcLib.setEnabled(ctx, lyricsWas)
        OnlineSettings.setSkipIntro(ctx, skipWas)
    }

    private fun lyricsFor(case: String) = runBlocking {
        TrackDetailsRepository.loadLyrics(ctx, "yu-ofx-$case-$run", "content://dev.asfakul.yumeiro.none/$case",
            title = "Yumeiro Test Song $case $run", artist = "Yumeiro Test Artist", durationMs = 180_000L, online = true)
    }
    private fun lrclibCalls() = seen.count { it.contains("lrclib.net") }

    @Test fun a_lyricsOnlineFollowsTheSwitch() {
        LrcLib.setEnabled(ctx, false)
        val off = lyricsFor("off")
        val offCalls = lrclibCalls()
        LrcLib.setEnabled(ctx, true)
        val on = lyricsFor("on")
        val onCalls = lrclibCalls() - offCalls
        log("OFX_CASE a lyrics off: result=${off?.source} lrclib=$offCalls · on: result=${on?.source} lines=${on?.lines?.size} lrclib=$onCalls")
        assertEquals("switch off: no LRCLIB request", 0, offCalls)
        assertEquals("switch off: no lyrics", null, off)
        assertTrue("switch on: LRCLIB is asked", onCalls >= 1)
        assertEquals("switch on: lyrics come from LRCLIB", LyricsSource.ONLINE, on?.source)
        log("OFX_RESULT PASS a")
    }

    private fun detail(mal: Int) = OnlineDetail(
        api = "yu-test-ofx", url = "u-ofx-$run-$mal", name = "Yumeiro Test Anime", kind = OnlineKind.ANIME, type = "Anime",
        posterUrl = null, backgroundUrl = null, posterHeaders = emptyMap(), plot = null, year = null, tags = emptyList(),
        durationMin = null, score = null, contentRating = null, playData = null, episodes = emptyMap(), seasonNames = emptyMap(),
        recommendations = emptyList(), malId = mal, aniListId = mal + 1,
    )
    private fun skipsFor(mal: Int, ep: Int): List<dev.asfakul.yumeiro.online.anime.AniSkip.Segment>? {
        AnimeSync.load(ctx, detail(mal), OnlineEpisode("d$ep", null, 1, ep, null, null, null), ep - 1, 1_440_000L)
        val until = System.currentTimeMillis() + 8_000
        while (System.currentTimeMillis() < until) {
            val s = AnimeSync.skips.value
            if (s != null && s.segments.isNotEmpty()) return s.segments
            Thread.sleep(150)
        }
        return AnimeSync.skips.value?.segments
    }
    private fun aniskipCalls() = seen.count { it.contains("api.aniskip.com") }

    @Test fun b_skipIntroFollowsTheSwitch() {
        // Unique MAL id per run: AniSkip caches real answers per (mal, episode) for the process.
        val mal = 900_000 + (run % 90_000).toInt()
        OnlineSettings.setSkipIntro(ctx, false)
        val off = skipsFor(mal, 1)
        val offCalls = aniskipCalls()
        OnlineSettings.setSkipIntro(ctx, true)
        val on = skipsFor(mal, 2)
        val onCalls = aniskipCalls() - offCalls
        log("OFX_CASE b skip intro off: segments=${off?.size} aniskip=$offCalls · on: segments=$on aniskip=$onCalls")
        assertEquals("switch off: no AniSkip request", 0, offCalls)
        assertTrue("switch off: no segments", off.isNullOrEmpty())
        assertTrue("switch on: AniSkip is asked", onCalls >= 1)
        assertTrue("switch on: the intro segment 10–95 s is loaded ($on)", on?.any { it.startMs == 10_000L && it.endMs == 95_000L } == true)
        log("OFX_RESULT PASS b")
    }
}
