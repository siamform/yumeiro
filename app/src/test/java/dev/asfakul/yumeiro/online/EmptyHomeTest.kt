/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.mainPageOf
import dev.asfakul.yumeiro.online.EmptyHome.Kind
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * B-32: an extension whose home answers with no rows and no error. Before, the screen only said
 * "Nothing came back from this extension"; now the site is checked and the reason is named.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EmptyHomeTest {
    private val server = MockWebServer()
    private val other = MockWebServer()
    private val http = OkHttpClient()

    /** Like HindiSubAnime: every row parses to nothing. */
    private inner class Empty(override var mainUrl: String) : MainAPI() {
        override var name = "YuB32 empty"
        override val hasMainPage = true
        override val mainPage = mainPageOf("/category/shounen/" to "Shounen", "/serie/" to "Series")
        override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? = null
    }

    @Before fun up() { server.start(); other.start() }
    @After fun down() { server.close(); other.close() }

    private fun site() = server.url("/").toString().trimEnd('/')

    @Test fun workingSiteWithNoTitlesBlamesTheExtension() = runBlocking {
        server.enqueue(MockResponse.Builder().code(200).body("<html><title>Hindi Sub Anime</title><article class=\"post\">x</article></html>").build())
        val api = Empty(site())
        assertTrue(OnlineRepository.home(api, force = true).isEmpty())
        val d = EmptyHome.probe(api, http)
        assertEquals(Kind.NOTHING_PARSED, d.kind)
        assertTrue(d.message, d.message.contains("works, but this extension found no titles"))
    }

    @Test fun challengePageAsksForABrowserCheck() = runBlocking {
        server.enqueue(MockResponse.Builder().code(403).addHeader("Server", "cloudflare").addHeader("cf-mitigated", "challenge")
            .body("<html><title>Just a moment...</title><script>window._cf_chl_opt={}</script></html>").build())
        val d = EmptyHome.probe(Empty(site()), http)
        assertEquals(Kind.BROWSER_CHECK, d.kind)
        assertTrue(d.message, d.message.contains("browser check"))
    }

    @Test fun firewallBlockIsNotACaptcha() = runBlocking {
        server.enqueue(MockResponse.Builder().code(403).addHeader("Server", "cloudflare").addHeader("cf-ray", "1")
            .body("<div class=\"cf-error-details\">Sorry, you have been blocked</div>").build())
        assertEquals(Kind.BLOCKED, EmptyHome.probe(Empty(site()), http).kind)
    }

    @Test fun movedSiteOffersTheNewAddress() = runBlocking {
        val target = other.url("/").toString().replace("127.0.0.1", "localhost")
        server.enqueue(MockResponse.Builder().code(301).addHeader("Location", target).build())
        other.enqueue(MockResponse.Builder().code(200).body("<html>new home</html>").build())
        val d = EmptyHome.probe(Empty(site().replace("localhost", "127.0.0.1")), http)
        assertEquals(Kind.MOVED, d.kind)
        assertEquals("http://localhost:${other.port}", d.movedTo)
    }

    @Test fun serverErrorSaysTheSiteIsDown() = runBlocking {
        server.enqueue(MockResponse.Builder().code(502).body("bad gateway").build())
        val d = EmptyHome.probe(Empty(site()), http)
        assertEquals(Kind.SITE_ERROR, d.kind)
        assertTrue(d.message, d.message.contains("error 502"))
    }

    @Test fun noConnectionSaysSo() = runBlocking {
        assertEquals(Kind.UNREACHABLE, EmptyHome.probe(Empty("http://127.0.0.1:1"), http).kind)
    }

    @Test fun pureRulesIgnoreWww() {
        val d = EmptyHome.explain("https://example.com", 200, "https://www.example.com/", dev.asfakul.yumeiro.online.web.BrowserCheck.Verdict.NONE, null)
        assertEquals(Kind.NOTHING_PARSED, d.kind)
    }
}
