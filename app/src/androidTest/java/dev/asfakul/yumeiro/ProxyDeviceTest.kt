/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewFeature
import dev.asfakul.yumeiro.backup.Backup
import dev.asfakul.yumeiro.network.AppProxy
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.online.web.SafeWebView
import dev.asfakul.yumeiro.online.web.SafeWebViewClient
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Credentials
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * NBUG-13 (ADR-045) on the emulator. A MockWebServer on the device plays the user's HTTP proxy (with a login).
 *  a: the login is stored sealed (not in the backed-up settings file) and comes back after a reload.
 *  b: the shared client, the HttpURLConnection path and (where the WebView supports proxy override) a web page
 *     all reach the site through the proxy, and the proxy's login prompt is answered.
 *  c: turning it off: requests stop going to the proxy and WebView's override is cleared.
 * Log tag YuProxy: PROXY_… lines, PROXY_RESULT_A/B/C PASS.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ProxyDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val server = MockWebServer()
    private val seen = Collections.synchronizedList(mutableListOf<RecordedRequest>())
    private val login = Credentials.basic("yu-user", "yu-pass-123")
    private fun log(s: String) = Log.i("YuProxy", s)

    @Before fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                seen += request
                if (request.headers["Proxy-Authorization"] != login)
                    return MockResponse.Builder().code(407).addHeader("Proxy-Authenticate", "Basic realm=\"yu\"").build()
                val page = "<html><head><title>proxied-ok</title></head><body>via proxy</body></html>"
                return MockResponse.Builder().addHeader("Content-Type", "text/html").body(page).build()
            }
        }
        server.start()
    }

    @After fun tearDown() {
        AppProxy.save(ctx, AppProxy.Config())
        InstrumentationRegistry.getInstrumentation().runOnMainSync { AppProxy.applyWebView(ctx) }
        runCatching { server.close() }
    }

    private fun cfg() = AppProxy.Config(AppProxy.Type.HTTP, "127.0.0.1", server.port, "yu-user", "yu-pass-123")

    @Test fun a_loginIsSealedAndSurvivesAReload() {
        assertNull(AppProxy.save(ctx, cfg()))
        val plainFile = ctx.getSharedPreferences("mt_network", 0).all.values.joinToString()
        val sealed = ctx.getSharedPreferences(AppProxy.SECRET_PREFS, 0).getString("pass", null).orEmpty()
        log("PROXY_A sealed=${sealed.take(3)} len=${sealed.length}")
        assertFalse("password in the settings file", "yu-pass-123" in plainFile)
        assertTrue("password not sealed", sealed.startsWith("v1:") && "yu-pass-123" !in sealed)
        assertFalse("secret file must not be backed up", Backup.prefIncluded(AppProxy.SECRET_PREFS))
        AppProxy.resetForTests()
        val back = AppProxy.load(ctx)
        assertEquals(cfg(), back)
        log("PROXY_RESULT_A PASS")
    }

    @Test fun b_appRequestsAndWebPagesGoThroughTheProxy() {
        assertNull(AppProxy.save(ctx, cfg()))
        val body = NetworkClient.get(ctx).newCall(Request.Builder().url("http://yumeiro-proxy.test/ok").build()).execute().use { it.body.string() }
        assertTrue("shared client: $body", "via proxy" in body)
        assertTrue(seen.any { "http://yumeiro-proxy.test/ok" in it.requestLine && it.headers["Proxy-Authorization"] == login })
        log("PROXY_B okhttp requests=${seen.size}")

        seen.clear()
        val conn = AppProxy.open(java.net.URL("http://yumeiro-proxy.test/sub")) as java.net.HttpURLConnection
        val sub = conn.inputStream.bufferedReader().readText()
        assertTrue("HttpURLConnection: $sub", "via proxy" in sub)
        assertTrue(seen.any { "/sub" in it.requestLine && it.headers["Proxy-Authorization"] == login })
        log("PROXY_B urlconnection requests=${seen.size}")

        val supported = runCatching { WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE) }.getOrDefault(false)
        if (!supported) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { AppProxy.applyWebView(ctx) }
            assertEquals("unsupported WebView must be reported to the user", AppProxy.WebState.UNSUPPORTED, AppProxy.webState.value)
            log("PROXY_B webview UNSUPPORTED (reported in Settings)")
        } else {
            seen.clear()
            val title = CountDownLatch(1)
            var got = ""
            var web: WebView? = null
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                web = WebView(ctx).apply {
                    SafeWebView.harden(this)
                    webViewClient = object : SafeWebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            got = view?.title.orEmpty(); if (got == "proxied-ok") title.countDown()
                        }
                    }
                }
            }
            val t0 = System.currentTimeMillis()
            while (AppProxy.webState.value != AppProxy.WebState.ON && System.currentTimeMillis() - t0 < 15_000) Thread.sleep(100)
            assertEquals(AppProxy.WebState.ON, AppProxy.webState.value)
            InstrumentationRegistry.getInstrumentation().runOnMainSync { web!!.loadUrl("http://yumeiro-proxy.test/page") }
            val ok = title.await(30, TimeUnit.SECONDS)
            log("PROXY_B webview title=$got requests=${seen.map { it.requestLine + " auth=" + (it.headers["Proxy-Authorization"] != null) }}")
            InstrumentationRegistry.getInstrumentation().runOnMainSync { SafeWebView.dispose(web) }
            assertTrue("web page didn't load through the proxy (title '$got')", ok)
            assertTrue(seen.any { "yumeiro-proxy.test/page" in it.requestLine && it.headers["Proxy-Authorization"] == login })
        }
        log("PROXY_RESULT_B PASS")
    }

    @Test fun c_offStopsUsingTheProxy() {
        assertNull(AppProxy.save(ctx, cfg()))
        assertNull(AppProxy.save(ctx, AppProxy.Config()))
        InstrumentationRegistry.getInstrumentation().runOnMainSync { AppProxy.applyWebView(ctx) }
        seen.clear()
        val r = runCatching { NetworkClient.get(ctx).newCall(Request.Builder().url("http://yumeiro-proxy.test/off").build()).execute().use { it.code } }
        log("PROXY_C off result=${r.getOrNull() ?: r.exceptionOrNull()?.javaClass?.simpleName} proxyRequests=${seen.size}")
        assertTrue("off must not reach the proxy", seen.isEmpty())
        assertTrue("a made-up host can't load without the proxy", r.isFailure)
        assertEquals(java.net.Proxy.NO_PROXY, AppProxy.select(java.net.URI("https://example.com/")).first())
        val t0 = System.currentTimeMillis()
        fun webOff() = AppProxy.webState.value == AppProxy.WebState.NOT_USED || AppProxy.webState.value == AppProxy.WebState.UNSUPPORTED
        while (!webOff() && System.currentTimeMillis() - t0 < 10_000) Thread.sleep(100)
        assertTrue("WebView override not cleared: ${AppProxy.webState.value}", webOff())
        log("PROXY_RESULT_C PASS")
    }
}
