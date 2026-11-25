/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.web

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** VERIFY-2 (from Zangetsu): a redirect into a passed site carries that site's pass and the agent that earned it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Verify2HopTest {
    private val a = MockWebServer()
    private val b = MockWebServer()
    private var seenUa: String? = null
    private var seenCookie: String? = null

    @After fun tearDown() { runCatching { a.close() }; runCatching { b.close() }; BrowserCheck.forget("127.0.0.1") }

    @Test fun redirectHopGetsThePassAndItsAgent() {
        b.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                seenUa = request.headers["User-Agent"]; seenCookie = request.headers["Cookie"]
                return MockResponse.Builder().body("ok").build()
            }
        }
        b.start()
        val target = "http://127.0.0.1:${b.port}/video"
        a.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse.Builder().code(302).addHeader("Location", target).build()
        }
        a.start()
        BrowserCheck.rememberForTest("127.0.0.1", BrowserCheck.Clearance(mapOf("cf_clearance" to "pass1"), "PassedAgent/1"))
        val client = OkHttpClient.Builder().addNetworkInterceptor(BrowserCheck.HopInterceptor).build()
        client.newCall(Request.Builder().url("http://localhost:${a.port}/go").header("User-Agent", "Desktop/9").build()).execute().use {
            assertEquals("ok", it.body.string())
        }
        assertEquals("PassedAgent/1", seenUa)
        assertTrue(seenCookie.orEmpty().contains("cf_clearance=pass1"))
    }
}
