/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.grouph

import dev.asfakul.yumeiro.online.upcoming.MovieReleases
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.TimeUnit

/** H-9: a slow or busy Wikidata answer gets one more try; a 4xx does not. */
class MovieReleasesRetryTest {
    private val server = MockWebServer().also { it.start() }
    @After fun stop() = server.close()
    private fun req() = Request.Builder().url(server.url("/sparql")).build()

    @Test fun aTimeoutIsRetriedOnce() {
        server.enqueue(MockResponse.Builder().body("late").headersDelay(2, TimeUnit.SECONDS).build())
        server.enqueue(MockResponse.Builder().body("ok").build())
        val c = OkHttpClient.Builder().readTimeout(500, TimeUnit.MILLISECONDS).build()
        assertEquals("ok", MovieReleases.fetchWithRetry(c, req(), pauseMs = 10))
        assertEquals(2, server.requestCount)
    }

    @Test fun busyIsRetriedOnce() {
        server.enqueue(MockResponse.Builder().code(429).build())
        server.enqueue(MockResponse.Builder().body("ok").build())
        assertEquals("ok", MovieReleases.fetchWithRetry(OkHttpClient(), req(), pauseMs = 10))
    }

    @Test fun twoFailuresStillFail() {
        server.enqueue(MockResponse.Builder().code(503).build())
        server.enqueue(MockResponse.Builder().code(503).build())
        try { MovieReleases.fetchWithRetry(OkHttpClient(), req(), pauseMs = 10); fail("must throw") } catch (e: java.io.IOException) {
            assertEquals("HTTP 503", e.message)
        }
    }

    @Test fun aClientErrorIsNotRetried() {
        server.enqueue(MockResponse.Builder().code(400).build())
        try { MovieReleases.fetchWithRetry(OkHttpClient(), req(), pauseMs = 10); fail("must throw") } catch (e: java.io.IOException) {}
        assertEquals(1, server.requestCount)
    }
}
