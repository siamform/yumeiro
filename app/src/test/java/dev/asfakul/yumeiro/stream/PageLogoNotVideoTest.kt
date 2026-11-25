package dev.asfakul.yumeiro.stream

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * SD-2: a movie page shared to Yumeiro (field probe 45251110253, a5x2nt.movielinkbd.at) played its site logo: the
 * page's JSON-LD names `favicon.ico` as an ImageObject `contentUrl`, the icon answered as an unknown binary and was
 * handed to the player ("Unsupported stream format"). Fixture: the real page's title and JSON-LD block.
 */
@RunWith(RobolectricTestRunner::class)
class PageLogoNotVideoTest {
    private val server = MockWebServer()
    private val html = javaClass.classLoader!!.getResource("pages/movielinkbd-movie-jsonld.html")!!.readText()

    @After fun tearDown() { runCatching { server.close() } }

    @Test fun theSiteLogoIsNotACandidate() {
        val page = WebPageExtractor.analyze(html, "https://mk84bp.movielinkbd.at/movie/x")
        assertTrue("fixture really names the icon", "favicon.ico" in html)
        assertFalse("candidates: ${page.candidates}", page.candidates.any { "favicon" in it.url })
        assertTrue(WebPageExtractor.notMedia("https://a.b/favicon.ico"))
        assertFalse("extensionless and video addresses stay", WebPageExtractor.notMedia("https://a.b/stream/abc") || WebPageExtractor.notMedia("https://a.b/v.mp4"))
    }

    @Test fun aPageWhoseOnlyAddressIsAPictureOpensTheFinderInsteadOfPlaying() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
                "/movie/x" -> MockResponse.Builder().addHeader("Content-Type", "text/html; charset=UTF-8")
                    .body(html.replace("https://mk84bp.movielinkbd.at/favicon.ico", server.url("/logo").toString())).build()
                // An extensionless picture, so only its Content-Type tells.
                "/logo" -> MockResponse.Builder().addHeader("Content-Type", "image/x-icon").body(Buffer().write(ByteArray(2048) { if (it == 2) 1 else 0 })).build()
                else -> MockResponse.Builder().code(404).build()
            }
        }
        server.start()
        val r = StreamResolver.resolveBlocking(StreamRequest(url = server.url("/movie/x").toString()), "Yumeiro-test", OkHttpClient(), thorough = false)
        assertTrue("got $r", r is Resolution.NeedsBrowser)
    }
}
