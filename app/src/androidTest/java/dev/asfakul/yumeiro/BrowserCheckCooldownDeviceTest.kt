/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.asfakul.yumeiro.online.web.BrowserCheck
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * G10 A5 on a device: a site that always answers with a Cloudflare challenge the silent check
 * cannot pass (no clearance cookie ever comes). The first extension request waits for the hidden
 * WebView check (up to 20 s) and gets the challenge page back. Before the fix, every later
 * request, and every request queued behind it on the host's lock, opened its own WebView and
 * waited the same again, so three requests took about a minute. Pass: after one failed check the
 * next requests come back at once. Log tag YuCheckTest, CHECK_CASE / CHECK_RESULT.
 */
@RunWith(AndroidJUnit4::class)
class BrowserCheckCooldownDeviceTest {
    private lateinit var server: MockWebServer
    private val apiHits = AtomicInteger()
    private val pageHits = AtomicInteger()
    private val client = OkHttpClient.Builder().addInterceptor(BrowserCheck.Interceptor)
        .readTimeout(60, TimeUnit.SECONDS).callTimeout(90, TimeUnit.SECONDS).build()

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.url.encodedPath.startsWith("/api/")) apiHits.incrementAndGet() else pageHits.incrementAndGet()
                return MockResponse.Builder().code(403).addHeader("cf-mitigated", "challenge").addHeader("Server", "cloudflare")
                    .addHeader("Content-Type", "text/html").body("<html><head><title>Just a moment...</title></head><body>cf_chl_opt</body></html>").build()
            }
        }
        server.start()
        BrowserCheck.init(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext)
        BrowserCheck.forget(server.hostName)
    }

    @After fun tearDown() { runCatching { server.close() } }

    private fun call(path: String): Pair<Int, Long> {
        val t0 = SystemClock.elapsedRealtime()
        val code = client.newCall(Request.Builder().url(server.url(path)).build()).execute().use { it.code }
        return code to SystemClock.elapsedRealtime() - t0
    }

    @Test fun failedCheckIsNotRepeatedForEveryRequest() {
        val (code1, ms1) = call("/api/search?q=a")
        Log.i(TAG, "CHECK_CASE first code=$code1 ms=$ms1 pageHits=${pageHits.get()}")
        assertEquals("challenge page passed back", 403, code1)

        // Three more requests at once, as a source page does (search, details, poster list).
        val pool = Executors.newFixedThreadPool(3)
        val times = Collections.synchronizedList(ArrayList<Long>())
        val t0 = SystemClock.elapsedRealtime()
        (1..3).map { i -> pool.submit { times += call("/api/item/$i").second } }.forEach { it.get(120, TimeUnit.SECONDS) }
        val total = SystemClock.elapsedRealtime() - t0
        pool.shutdown()
        Log.i(TAG, "CHECK_CASE next three total=${total}ms each=$times apiHits=${apiHits.get()} pageHits=${pageHits.get()}")
        assertTrue("next requests fail fast after a failed check: total ${total} ms, each $times", total < 5_000)
        Log.i(TAG, "CHECK_RESULT PASS")
    }

    private companion object { const val TAG = "YuCheckTest" }
}
