/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.network.Connectivity
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.online.OnlineErrors
import okhttp3.Request
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.util.concurrent.TimeUnit

/**
 * G10: no network, slow network and a refused port, on the device's real network stack, with the
 * text the user is shown (the shared mapper `OnlineErrors.friendly`).
 *  a. Wi-Fi and mobile data off → the app's online signal goes false, the offline pill shows, a real
 *     request fails as "No internet connection"; back on → online again and the pill goes away.
 *  b. A server that answers after 25 s (the shared client reads for 20 s) → "The server took too
 *     long to answer"; a slow but steady body (32 KB/s) still completes (no false timeout).
 *  c. A closed local port → "Your network refused the connection…".
 *  d. VPN: logs what the app reads (`Connectivity.vpnActive`); an emulator cannot raise a VPN
 *     without a VPN app and the user's consent dialog, so the VPN case is an owner test.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class NetworkStateDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(m: String) = Log.i("YuNetState", m)
    private fun has(tag: String) = runCatching { rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)

    private fun call(url: String, client: okhttp3.OkHttpClient = NetworkClient.get(ctx)): Throwable? = runCatching {
        client.newCall(Request.Builder().url(url).build()).execute().use { it.body?.bytes() }
    }.exceptionOrNull()

    private fun waitFor(ms: Long, what: () -> Boolean): Boolean {
        val end = SystemClock.uptimeMillis() + ms
        while (SystemClock.uptimeMillis() < end) { if (what()) return true; SystemClock.sleep(300) }
        return what()
    }

    @Test fun a_offlineThenBack() {
        org.junit.Assume.assumeFalse("Ethernet stays up when Wi-Fi and data go off (TV emulator)", FirstRun.hasEthernet())
        FirstRun.toTabs(rule)
        val t0 = SystemClock.uptimeMillis()
        try {
            FirstRun.shell("svc wifi disable"); FirstRun.shell("svc data disable")
            val off = waitFor(20_000) { !Connectivity.online.value }
            val offMs = SystemClock.uptimeMillis() - t0
            rule.waitForIdle()
            val pill = waitFor(5_000) { has("offline_banner") }
            val e = call("https://example.com/")
            val text = e?.let { OnlineErrors.friendly(it) }
            log("NET_CASE offline signal=$off after ${offMs}ms pill=$pill error=${e?.javaClass?.simpleName} text='$text'")
            FirstRun.shot("net_offline")
            assertTrue("online signal stayed true 20 s after Wi-Fi and data went off", off)
            assertTrue("no offline pill · ${FirstRun.onScreen(rule)}", pill)
            assertEquals("No internet connection", text)
        } finally {
            FirstRun.shell("svc wifi enable"); FirstRun.shell("svc data enable")
        }
        val t1 = SystemClock.uptimeMillis()
        val back = waitFor(45_000) { Connectivity.online.value }
        rule.waitForIdle()
        val pillGone = waitFor(5_000) { !has("offline_banner") }
        log("NET_CASE back signal=$back after ${SystemClock.uptimeMillis() - t1}ms pillGone=$pillGone")
        assertTrue("online signal never came back", back)
        assertTrue("offline pill stayed after the network came back", pillGone)
        log("NET_RESULT a PASS")
    }

    @Test fun b_slowServer() {
        val server = MockWebServer()
        // By path, not a queue: an interceptor retry of /late must not take /slow's answer.
        var lateHits = 0
        server.dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: mockwebserver3.RecordedRequest): MockResponse = when (request.url.encodedPath) {
                "/late" -> { lateHits++; MockResponse.Builder().body("late").headersDelay(25, TimeUnit.SECONDS).build() }
                else -> MockResponse.Builder().body(okio.Buffer().write(ByteArray(256 * 1024))).throttleBody(32 * 1024, 1, TimeUnit.SECONDS).build()
            }
        }
        server.start()
        try {
            val t0 = SystemClock.uptimeMillis()
            val late = call(server.url("/late").toString())
            val text = late?.let { OnlineErrors.friendly(it) }
            log("NET_CASE late error=${late?.javaClass?.simpleName} requests=$lateHits after ${SystemClock.uptimeMillis() - t0}ms text='$text'")
            assertEquals("The server took too long to answer", text)
            val t1 = SystemClock.uptimeMillis()
            val slow = call(server.url("/slow").toString())
            log("NET_CASE slow-steady error=${slow?.javaClass?.simpleName} after ${SystemClock.uptimeMillis() - t1}ms")
            assertTrue("a slow but steady body failed: $slow", slow == null)
        } finally { runCatching { server.close() } }
        log("NET_RESULT b PASS")
    }

    @Test fun c_refusedPort() {
        val e = call("http://127.0.0.1:1/")
        val text = e?.let { OnlineErrors.friendly(it) }
        log("NET_CASE refused error=${e?.javaClass?.simpleName} text='$text'")
        assertEquals("Your network refused the connection. The site may be blocked here — try a VPN", text)
        log("NET_RESULT c PASS")
    }

    @Test fun d_vpnRead() {
        log("NET_CASE vpn active=${Connectivity.vpnActive(ctx)} online=${Connectivity.isOnline(ctx)} (no VPN can be raised on CI; owner test)")
    }
}
