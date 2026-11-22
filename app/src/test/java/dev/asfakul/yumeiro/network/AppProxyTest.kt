/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.network

import androidx.test.core.app.ApplicationProvider
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Credentials
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.Proxy
import java.net.URI
import java.util.Collections

/**
 * NBUG-13 (ADR-045): there was no way to send the app's traffic through a proxy (IP / SNI blocks can't be
 * fixed by DNS). These fail on the old code (no AppProxy) and cover routing, the login answer and the
 * shared client actually going through the proxy.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppProxyTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val server = MockWebServer()
    private val seen = Collections.synchronizedList(mutableListOf<RecordedRequest>())

    @Before fun setUp() { AppProxy.resetForTests() }
    @After fun tearDown() { AppProxy.resetForTests(); runCatching { server.close() } }

    private fun proxyServer(needLogin: String? = null) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                seen += request
                val auth = request.headers["Proxy-Authorization"]
                return if (needLogin != null && auth != needLogin)
                    MockResponse.Builder().code(407).addHeader("Proxy-Authenticate", "Basic realm=\"yu\"").build()
                else MockResponse.Builder().body("via-proxy").build()
            }
        }
        server.start()
    }

    @Test fun offLeavesTheSystemChoiceAlone() {
        assertEquals(listOf(Proxy.NO_PROXY), AppProxy.select(URI("https://example.com/")))
        assertFalse(AppProxy.config.value.on)
    }

    @Test fun onRoutesPublicHostsAndKeepsLocalOnesDirect() {
        AppProxy.setForTests(AppProxy.Config(AppProxy.Type.SOCKS, "proxy.example", 1080))
        val p = AppProxy.select(URI("https://cdn.example.org/a.m3u8")).single()
        assertEquals(Proxy.Type.SOCKS, p.type())
        assertEquals("proxy.example:1080", (p.address() as java.net.InetSocketAddress).let { "${it.hostString}:${it.port}" })
        for (h in listOf("http://127.0.0.1:8090/", "http://localhost/", "http://192.168.1.4/", "http://10.0.0.2/", "http://172.20.1.1/", "http://[::1]/", "http://tv.local/"))
            assertEquals(h, listOf(Proxy.NO_PROXY), AppProxy.select(URI(h)))
        assertTrue(AppProxy.select(URI("http://172.32.0.1/")).single().type() == Proxy.Type.SOCKS)
    }

    @Test fun badAddressesAreRefusedWithAReason() {
        assertNotNull(AppProxy.validate(AppProxy.Type.HTTP, "", 8080))
        assertNotNull(AppProxy.validate(AppProxy.Type.HTTP, "http://proxy.example", 8080))
        assertNotNull(AppProxy.validate(AppProxy.Type.HTTP, "proxy.example/path", 8080))
        assertNotNull(AppProxy.validate(AppProxy.Type.HTTP, "proxy.example", 0))
        assertNotNull(AppProxy.validate(AppProxy.Type.HTTP, "proxy.example", 70000))
        assertNull(AppProxy.validate(AppProxy.Type.HTTP, "proxy.example", 8080))
        assertFalse("an invalid config is never on", AppProxy.Config(AppProxy.Type.HTTP, "", 8080).on)
        assertFalse("the login never prints", AppProxy.Config(AppProxy.Type.HTTP, "h", 1, "alice", "s3cret").toString().contains("s3cret"))
    }

    @Test fun sharedClientGoesThroughTheProxyAndAnswersItsLoginOnce() {
        val login = Credentials.basic("alice", "s3cret")
        proxyServer(needLogin = login)
        AppProxy.setForTests(AppProxy.Config(AppProxy.Type.HTTP, server.hostName, server.port, "alice", "s3cret"))
        val body = NetworkClient.get(ctx).newCall(Request.Builder().url("http://yumeiro-proxy-test.example/hello").build()).execute().use { it.body.string() }
        assertEquals("via-proxy", body)
        assertEquals(2, seen.size)
        assertTrue("absolute target at the proxy: ${seen[0].requestLine}", seen[0].requestLine.contains("http://yumeiro-proxy-test.example/hello"))
        assertNull(seen[0].headers["Proxy-Authorization"])
        assertEquals(login, seen[1].headers["Proxy-Authorization"])
    }

    @Test fun wrongLoginStopsAfterOneRetry() {
        proxyServer(needLogin = "never")
        AppProxy.setForTests(AppProxy.Config(AppProxy.Type.HTTP, server.hostName, server.port, "alice", "wrong"))
        val code = NetworkClient.get(ctx).newCall(Request.Builder().url("http://yumeiro-proxy-test.example/x").build()).execute().use { it.code }
        assertEquals(407, code)
        assertEquals("one try without, one with the login, then stop", 2, seen.size)
    }

    @Test fun urlConnectionCallersUseTheProxyToo() {
        proxyServer()
        AppProxy.setForTests(AppProxy.Config(AppProxy.Type.HTTP, server.hostName, server.port))
        val conn = AppProxy.open(java.net.URL("http://yumeiro-proxy-test.example/sub")) as java.net.HttpURLConnection
        assertEquals("via-proxy", conn.inputStream.bufferedReader().readText())
        assertTrue(seen.single().requestLine.contains("http://yumeiro-proxy-test.example/sub"))
    }

    @Test fun testButtonReportsSuccessAndAnUnreachableProxy() {
        proxyServer()
        AppProxy.TEST_URL = "http://yumeiro-proxy-test.example/generate_204"
        try {
            val ok = AppProxy.test(ctx, AppProxy.Config(AppProxy.Type.HTTP, server.hostName, server.port))
            assertTrue(ok, ok.startsWith("Works"))
            val dead = java.net.ServerSocket(0).use { it.localPort }
            val bad = AppProxy.test(ctx, AppProxy.Config(AppProxy.Type.HTTP, "127.0.0.1", dead))
            assertTrue(bad, bad.startsWith("Failed") && bad.contains("couldn't reach the proxy"))
        } finally { AppProxy.TEST_URL = "https://www.gstatic.com/generate_204" }
    }

    /**
     * NBUG-13-PORT: CI 44914717596 failed testButtonReportsSuccessAndAnUnreachableProxy with "Failed · the proxy
     * wants a login" for a closed local port: the free port it got held the digits 407, and the message check
     * looked for "407" anywhere in "Failed to connect to /127.0.0.1:<port>". Fixed ports, so it never depends on luck.
     */
    @Test fun anUnreachableProxyIsNeverReadAsALoginAnswer() {
        val cfg = AppProxy.Config(AppProxy.Type.HTTP, "127.0.0.1", 40712)
        listOf(40712, 14070, 4071, 54407, 407).forEach { port ->
            val m = AppProxy.message(java.net.ConnectException("Failed to connect to /127.0.0.1:$port"), cfg.copy(port = port))
            assertTrue("port $port: $m", m.startsWith("couldn't reach the proxy"))
        }
        val t = AppProxy.message(java.net.SocketTimeoutException("Connect timed out to proxy.example:8407"), cfg)
        assertTrue(t, t.startsWith("couldn't reach the proxy"))
        // OkHttp's real login answers still read as a login answer.
        listOf("Unexpected response code for CONNECT: 407", "Failed to authenticate with proxy", "HTTP 407 Proxy Authentication Required").forEach { msg ->
            val m = AppProxy.message(java.io.IOException(msg), cfg)
            assertTrue("$msg -> $m", m.startsWith("the proxy wants a login"))
        }
    }
}
