/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * ADCHAIN-2 (owner list 23): a shared shrinkme.click link answered 403 with Cloudflare's visitor check and the
 * player said "Access denied (HTTP 403)" (field probe 45399365323). A check page opens the page browser; a plain
 * refusal (an expired CDN token) still says why it failed.
 */
@RunWith(RobolectricTestRunner::class)
class Adchain2CheckPageTest {
    private val server = MockWebServer()
    @After fun tearDown() { runCatching { server.close() } }

    private val check = "<!DOCTYPE html><html><head><title>Just a moment...</title></head><body>" +
        "<div id=\"challenge-running\">Verify you are human</div><script src=\"/cdn-cgi/challenge-platform/h/b/orchestrate/chl_page/v1\"></script></body></html>"

    private fun serve() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
                "/alwf74z" -> MockResponse.Builder().code(403).addHeader("Content-Type", "text/html; charset=UTF-8")
                    .addHeader("cf-mitigated", "challenge").addHeader("Server", "cloudflare").addHeader("cf-ray", "a5f8-LHR").body(check).build()
                "/video.mp4" -> MockResponse.Builder().code(403).addHeader("Content-Type", "text/html").body("<html><body>Token expired</body></html>").build()
                else -> MockResponse.Builder().code(404).build()
            }
        }
        server.start()
    }

    @Test fun aVisitorCheckOpensThePageBrowser() {
        serve()
        val url = server.url("/alwf74z").toString()
        val r = StreamResolver.resolveBlocking(StreamRequest(url = url), "Yumeiro-test", OkHttpClient(), thorough = false)
        assertTrue("got $r", r is Resolution.NeedsBrowser)
        r as Resolution.NeedsBrowser
        assertEquals("the chain starts where the user's link starts", url, r.pageUrl)
        assertEquals(StreamResolver.CHECK_PAGE_REASON, r.reason)
    }

    @Test fun aPlainRefusalStillFails() {
        serve()
        val r = StreamResolver.resolveBlocking(StreamRequest(url = server.url("/video.mp4").toString()), "Yumeiro-test", OkHttpClient(), thorough = true)
        assertTrue("got $r", r is Resolution.Failed)
    }
}
