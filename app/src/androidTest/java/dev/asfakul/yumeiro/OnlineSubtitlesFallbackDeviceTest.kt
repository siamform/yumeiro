/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.asfakul.yumeiro.subtitles.MovieHash
import dev.asfakul.yumeiro.subtitles.OpenSubtitlesProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * IG-7 (G11): the local player's "Find subtitles" searches OpenSubtitles by the file's hash first,
 * then by its title. Before the fix, when the title search failed (HTTP 429 rate limit, timeout)
 * the whole search threw and the exact hash match the first request had already found was lost:
 * the user saw "Subtitle search failed" instead of the right file. Here a local server finds one
 * file by hash and answers the title search with 429.
 */
@RunWith(AndroidJUnit4::class)
class OnlineSubtitlesFallbackDeviceTest {
    private lateinit var server: MockWebServer

    private val hit = """[{"MatchedBy":"moviehash","IDSubtitleFile":"77","SubFileName":"clip.srt","LanguageName":"English",
        "ISO639":"en","SubFormat":"srt","SubDownloadsCnt":"5","MovieReleaseName":"clip","MovieName":"Clip",
        "SubDownloadLink":"https://dl.example/77.gz","SubEncoding":"UTF-8"}]"""

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val p = request.url.encodedPath
                return when {
                    p.contains("/moviehash-") -> MockResponse.Builder().code(200).body(hit).build()
                    p.contains("/query-") -> MockResponse.Builder().code(429).body("Too many requests").build()
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
    }

    @After fun tearDown() { runCatching { server.close() } }

    @Test fun hashMatchSurvivesFailedTitleSearch() {
        val provider = OpenSubtitlesProvider("http://${server.hostName}:${server.port}/search")
        val result = runCatching {
            runBlocking { withTimeout(30_000) { provider.search("Clip.2024.1080p.mkv", "eng", MovieHash("0123456789abcdef", 1_000_000)) } }
        }
        Log.i(TAG, "SUBS_CASE fallback ok=${result.isSuccess} n=${result.getOrNull()?.size} err=${result.exceptionOrNull()?.message}")
        assertTrue("search threw: ${result.exceptionOrNull()}", result.isSuccess)
        val list = result.getOrThrow()
        assertEquals(1, list.size)
        assertEquals("77", list[0].id)
        assertTrue(list[0].hashMatch)
        Log.i(TAG, "SUBS_RESULT PASS fallback")
    }

    @Test fun titleOnlyFailureStillReported() {
        // No hash (online stream): nothing was found, so the 429 must still reach the user.
        val provider = OpenSubtitlesProvider("http://${server.hostName}:${server.port}/search")
        val result = runCatching { runBlocking { withTimeout(30_000) { provider.search("Clip", "eng", null) } } }
        Log.i(TAG, "SUBS_CASE titleOnly failed=${result.isFailure} err=${result.exceptionOrNull()?.message}")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("429"))
        Log.i(TAG, "SUBS_RESULT PASS titleOnly")
    }

    private companion object { const val TAG = "YuSubsTest" }
}
