/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.adchain

import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64

/** ADCHAIN-3 (item 28): the plain-HTTP chain walk against real HTTP servers (MockWebServer). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdChain28Test {
    private val server = MockWebServer()
    private val waits = mutableListOf<Long>()
    private val ua = "Mozilla/5.0 (Linux; Android 16; K; wv) AppleWebKit/537.36 Chrome/140.0.0.0 Mobile Safari/537.36"
    private lateinit var base: String

    @Before fun setUp() { AdChainDestinations.allowLoopbackForTest = true }
    @After fun tearDown() { AdChainDestinations.allowLoopbackForTest = false; runCatching { server.close() } }

    private fun serve(f: (RecordedRequest) -> MockResponse) {
        server.dispatcher = object : Dispatcher() { override fun dispatch(request: RecordedRequest) = f(request) }
        server.start()
        base = server.url("/").toString().trimEnd('/')
    }

    private fun html(s: String, code: Int = 200) = MockResponse.Builder().code(code).addHeader("Content-Type", "text/html; charset=utf-8").body(s).build()
    private fun walk(url: String) = runBlocking { AdChainHttp(OkHttpClient(), ua, wait = { waits += it }).walk(url) }

    @Test fun fullChainGateSafelinkAdLinkFlyReachesTheFileHostWithSession() {
        var goCalls = 0
        var goOk = false
        serve { r ->
            when (r.url.encodedPath) {
                "/s/abc" -> MockResponse.Builder().code(302).addHeader("Set-Cookie", "sess=one; Path=/").addHeader("Location", "/safelink/a1").build()
                "/safelink/a1" -> {
                    val b64 = Base64.getEncoder().encodeToString("""{"linkr":"$base/alf/step3","delay":3}""".toByteArray())
                    html("""<form name="tp" method="POST" action="/safelink/a2"><input type="hidden" name="newwpsafelink" value="$b64"></form>""")
                }
                "/alf/step3" -> MockResponse.Builder().code(200).addHeader("Content-Type", "text/html").addHeader("Set-Cookie", "csrfToken=xyz; Path=/")
                    .body("""<script>var counter = 5;</script><form id="go-link" method="post" action="/links/go"><input type="hidden" name="ad_form_data" value="sig99"><input type="hidden" name="_csrfToken" value="xyz"><button type="submit">Get Link</button></form>""").build()
                "/links/go" -> {
                    goCalls++
                    val c = r.headers["Cookie"].orEmpty()
                    val body = r.body?.utf8().orEmpty()
                    goOk = "sess=one" in c && "csrfToken=xyz" in c && r.headers["X-Requested-With"] == "XMLHttpRequest" &&
                        r.headers["User-Agent"] == ua && "ad_form_data=sig99" in body && r.method == "POST"
                    if (goOk) MockResponse.Builder().addHeader("Content-Type", "application/json").body("""{"status":"success","url":"https://pixeldrain.com/u/AbCdEf12"}""").build()
                    else MockResponse.Builder().code(403).body("""{"status":"error"}""").build()
                }
                else -> MockResponse.Builder().code(404).build()
            }
        }
        val out = walk("$base/s/abc")
        assertTrue("$out", out is AdChainHttp.Outcome.Reached)
        assertEquals("https://pixeldrain.com/u/AbCdEf12", (out as AdChainHttp.Outcome.Reached).url)
        assertTrue(goOk); assertEquals(1, goCalls)
        // The site's own waits were honoured (3 s safelink, 5 s + 1 AdLinkFly), never skipped.
        assertEquals(listOf(3000L, 6000L), waits)
    }

    @Test fun adLinkFlyRefusalIsAHandOverNotADestination() {
        serve { r ->
            if (r.url.encodedPath == "/links/go") MockResponse.Builder().addHeader("Content-Type", "application/json").body("""{"status":"error","message":"Bad Request."}""").build()
            else html("""<form id="go-link" method="post" action="/links/go"><input name="ad_form_data" value="x"></form>""")
        }
        val out = walk("$base/x")
        assertTrue("$out", out is AdChainHttp.Outcome.NeedsBrowser)
        assertEquals("$base/x", (out as AdChainHttp.Outcome.NeedsBrowser).url)
    }

    @Test fun captchaAndTurnstileStepsStopAndHandOverWithTheirKind() {
        serve { r ->
            when (r.url.encodedPath) {
                "/r" -> html("""<title>Health Shield</title><p>Please click on below captcha box to proceed</p><div class="g-recaptcha" data-sitekey="k"></div>""")
                else -> html("""<div class="cf-turnstile" data-sitekey="k"></div>""")
            }
        }
        val a = walk("$base/r") as AdChainHttp.Outcome.NeedsBrowser
        assertEquals(AdChainPages.Check.RECAPTCHA, a.check)
        val b = walk("$base/t") as AdChainHttp.Outcome.NeedsBrowser
        assertEquals(AdChainPages.Check.TURNSTILE, b.check)
    }

    @Test fun mediaAtTheEndIsNotDownloadedByTheWalk() {
        val big = okio.Buffer().write(ByteArray(8 * 1024 * 1024))
        serve { r ->
            if (r.url.encodedPath == "/go") MockResponse.Builder().code(302).addHeader("Location", "/file/movie").build()
            else MockResponse.Builder().addHeader("Content-Type", "video/mp4").body(big).build()
        }
        val t0 = System.nanoTime()
        val out = walk("$base/go")
        assertTrue("$out", out is AdChainHttp.Outcome.Reached)
        assertEquals("$base/file/movie", (out as AdChainHttp.Outcome.Reached).url)
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 5_000)
    }

    @Test fun metaRefreshAndJsRedirectAreFollowedAndLoopsStop() {
        serve { r ->
            when (r.url.encodedPath) {
                "/m" -> html("""<meta http-equiv="refresh" content="0; url=/j">""")
                "/j" -> html("""<p>Redirecting...</p><script>window.location.href = "$base/loop1";</script>""")
                "/loop1" -> MockResponse.Builder().code(302).addHeader("Location", "/loop2").build()
                else -> MockResponse.Builder().code(302).addHeader("Location", "/loop1").build()
            }
        }
        val out = walk("$base/m")
        assertTrue("$out", out is AdChainHttp.Outcome.NeedsBrowser)
        assertTrue((out as AdChainHttp.Outcome.NeedsBrowser).reason.contains("circle"))
    }

    @Test fun unsafeHopsAreRefused() {
        serve { r ->
            when (r.url.encodedPath) {
                "/lan" -> MockResponse.Builder().code(302).addHeader("Location", "http://192.168.1.1/admin").build()
                "/user" -> MockResponse.Builder().code(302).addHeader("Location", "https://pixeldrain.com@evil.example/x").build()
                "/ad" -> MockResponse.Builder().code(302).addHeader("Location", "https://www.exoclick.com/click").build()
                else -> MockResponse.Builder().code(302).addHeader("Location", "intent://x#Intent;end").build()
            }
        }
        assertEquals("local address", (walk("$base/lan") as AdChainHttp.Outcome.Failed).reason)
        assertEquals("address hides its real host", (walk("$base/user") as AdChainHttp.Outcome.Failed).reason)
        assertEquals("ad", (walk("$base/ad") as AdChainHttp.Outcome.Failed).reason)
        assertEquals("not a web address", (walk("$base/i") as AdChainHttp.Outcome.Failed).reason)
        AdChainDestinations.allowLoopbackForTest = false
        assertEquals("local address", AdChainDestinations.refusal("http://127.0.0.1:8080/"))
        assertEquals("local address", AdChainDestinations.refusal("http://[::1]/"))
        assertNull(AdChainDestinations.refusal("https://shrinkme.click/alwf74z"))
    }

    @Test fun plainPageIsNotAChainAndCookiesFollowTheirPath() {
        serve { r ->
            when (r.url.encodedPath) {
                "/plain" -> html("<h1>Hello</h1><p>Just a page.</p>")
                "/a/set" -> MockResponse.Builder().code(302).addHeader("Set-Cookie", "only_a=1; Path=/a").addHeader("Location", "/b/page").build()
                else -> html("<p>landing</p>")
            }
        }
        assertEquals(AdChainHttp.Outcome.NotAChain, walk("$base/plain"))
        val walker = AdChainHttp(OkHttpClient(), ua, wait = {})
        val out = runBlocking { walker.walk("$base/a/set") }
        assertTrue("$out", out is AdChainHttp.Outcome.Reached)
        assertNull(walker.jar.header("$base/b/page"))
        assertEquals("only_a=1", walker.jar.header("$base/a/x"))
    }

    @Test fun wrappersUnwrap() {
        assertEquals(
            "https://www.mirrored.to/files/F2A10447/[TPX]-Moribito.Guardian.of.the.Spirit.S01.E01-HSub-FHD.mp4_links",
            AdChainPages.unwrap("https://links.tpxsub.com/redirect/main2.php?url=f41616t5k40314s2v44646s2r5p5m4q4i4l4c444s216l434b4m5l494p4v2n4y2b3u2r2t2x2x2n244v31595a5m2j3t5n4h4j5q5q4t524f3t444l4b49474v5s2v5b4z3t4c4b4u205o4f4n4c4l4r2p3o2y2t2q45424m2e315q4a424r4i3m424h4o4v224j494k4s5p4"),
        )
        assertEquals("https://pixeldrain.com/u/T1", AdChainDestinations.unwrapTracking("https://www.google.com/url?q=https%3A%2F%2Fpixeldrain.com%2Fu%2FT1"))
        val b64 = Base64.getUrlEncoder().withoutPadding().encodeToString("https://gofile.io/d/abc".toByteArray())
        assertEquals("https://gofile.io/d/abc", AdChainPages.unwrap("https://safe.example/?safe=$b64"))
        assertTrue(AdChainDestinations.looksLikeChain("https://srnky.com/0T1EnbeuLf"))
        assertTrue(AdChainDestinations.looksLikeChain("https://shrinkme.click/alwf74z"))
        assertTrue(!AdChainDestinations.looksLikeChain("https://example.com/video/1"))
    }
}
