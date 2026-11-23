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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B-29 / B-3. Before: a TeraBox share went to the WebView finder (auto = true), which caught the
 * player's anonymous `share/streaming` playlist: 3 × 10 s segments + ENDLIST, i.e. the 30-second
 * preview, so every TeraBox video stopped at 0:30. Fixtures are trimmed copies of the real answers
 * for 1024terabox.com/s/1qkXqTvPA9GYY7sUY6dOGeA measured on 2026-11-21.
 */
class TeraBoxApiTest {
    private val server = MockWebServer()
    private val share = "https://1024terabox.com/s/1qkXqTvPA9GYY7sUY6dOGeA"

    private val page = "<script>try {eval(decodeURIComponent(`function%20fn%28a%29%7Bwindow.jsToken%20%3D%20a%7D%3Bfn%28%22" +
        "9AAAE649FB35525962C415468056DFCF0A38D9AE1EF4E49E%22%29`))} catch(e){}</script>"
    private val info = """{"errno":0,"randsk":"KYGxnFHIr6o4%2BicZ%3D","sign":"bffe60e9","timestamp":1795209634,"shareid":1000916905929,"uk":81366184102199,
        |"list":[{"category":"1","dlink":"","fs_id":"993976246402096","isdir":"0","server_filename":"Ranabaali.2026.720p.NF.WEB-DL.HIN-TEL.AAC5.1.AV1-XDMovies.co.mkv","size":"1002246543"}]}""".trimMargin()
    private val preview = "#EXTM3U\n#EXT-X-TARGETDURATION:15\n#EXTINF:10,\nhttps://cdn/a\n#EXTINF:10,\nhttps://cdn/b\n#EXTINF:10,\nhttps://cdn/c\n#EXT-X-ENDLIST\n"
    private val full = "#EXTM3U\n" + (1..600).joinToString("") { "#EXTINF:10,\nhttps://cdn/$it\n" } + "#EXT-X-ENDLIST\n"

    @After fun tearDown() {
        TeraBoxApi.base = "https://www.terabox.app"
        TeraBoxApi.cookieSource = { null }
        runCatching { server.close() }
    }

    private fun serve(list: String, stream: String = preview): MutableList<RecordedRequest> {
        val seen = mutableListOf<RecordedRequest>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                synchronized(seen) { seen += request }
                val body = when (request.url.encodedPath) {
                    "/wap/share/filelist" -> page
                    "/api/shorturlinfo" -> info
                    "/share/list" -> list
                    "/share/streaming" -> stream
                    else -> return MockResponse.Builder().code(404).build()
                }
                return MockResponse.Builder().body(body).build()
            }
        }
        server.start()
        TeraBoxApi.base = server.url("/").toString().trimEnd('/')
        return seen
    }

    @Test fun parsesShareKeyTokenAndPreview() {
        assertEquals("1qkXqTvPA9GYY7sUY6dOGeA", TeraBoxApi.shortUrl(share))
        assertEquals("1qkXqTvPA9GYY7sUY6dOGeA", TeraBoxApi.shortUrl("https://www.terabox.app/wap/share/filelist?surl=qkXqTvPA9GYY7sUY6dOGeA"))
        assertEquals("9AAAE649FB35525962C415468056DFCF0A38D9AE1EF4E49E", TeraBoxApi.jsToken(page))
        assertEquals(30.0, TeraBoxApi.playlistSeconds(preview), 0.01)
        assertTrue(TeraBoxApi.isPreview(preview, 1_002_246_543L))
        assertFalse(TeraBoxApi.isPreview(full, 1_002_246_543L))
        assertFalse("a genuinely short clip is not a preview", TeraBoxApi.isPreview(preview, 5_000_000L))
        assertEquals("KYGxnFHIr6o4+icZ=", TeraBoxApi.sekey("KYGxnFHIr6o4%2BicZ%3D"))
        assertTrue(TeraBoxApi.signedIn("browserid=x; ndus=Yabc"))
        assertFalse(TeraBoxApi.signedIn("browserid=x; csrfToken=y"))
    }

    @Test fun withoutSignInThePreviewIsNeverPlayed() {
        val seen = serve(list = """{"errno":105}""")
        val r = StreamResolver.resolveBlocking(StreamRequest(share), "ua", OkHttpClient())
        assertTrue("got $r", r is Resolution.NeedsBrowser)
        r as Resolution.NeedsBrowser
        assertEquals(StreamResolver.TERABOX_SIGN_IN, r.pageUrl)
        assertFalse("the finder must not auto-capture the 30 s preview", r.auto)
        assertTrue("no network before a sign-in exists", seen.isEmpty())
    }

    @Test fun signedInPlaysTheOriginalFile() {
        TeraBoxApi.cookieSource = { "ndus=Ytest; browserid=b" }
        val seen = serve(list = """{"errno":0,"list":[{"fs_id":"993976246402096","dlink":"https://d.terabox.app/file/abc?fid=1","server_filename":"Ranabaali.2026.720p.mkv"}]}""")
        val r = StreamResolver.resolveBlocking(StreamRequest(share), "ua", OkHttpClient())
        assertTrue("got $r", r is Resolution.Playable)
        val req = (r as Resolution.Playable).request
        assertEquals("https://d.terabox.app/file/abc?fid=1", req.url)
        assertEquals(StreamFormat.PROGRESSIVE, req.format)
        assertTrue(req.cookie!!.contains("ndus=Ytest"))
        assertEquals(TeraBoxApi.DESKTOP_UA, req.userAgent)
        val list = seen.first { it.url.encodedPath == "/share/list" }.url
        assertEquals("KYGxnFHIr6o4+icZ=", list.queryParameter("sekey"))
        assertEquals("1000916905929", list.queryParameter("shareid"))
    }

    @Test fun signedInFallsBackToFullHlsButRefusesAPreview() {
        TeraBoxApi.cookieSource = { "ndus=Ytest" }
        serve(list = """{"errno":105}""", stream = full)
        val ok = TeraBoxApi.resolve(OkHttpClient(), share)
        assertTrue("got $ok", ok is TeraBoxApi.Result.Ok && ok.hls)
    }

    @Test fun av1OriginalWithoutDecoderPlaysTheTranscode() {
        TeraBoxApi.cookieSource = { "ndus=Ytest" }
        TeraBoxApi.canPlayAv1 = { false }
        try {
            serve(list = """{"errno":0,"list":[{"fs_id":"993976246402096","dlink":"https://d.terabox.app/file/abc"}]}""", stream = full)
            val r = TeraBoxApi.resolve(OkHttpClient(), share)
            assertTrue("got $r", r is TeraBoxApi.Result.Ok && r.hls)
            assertTrue(TeraBoxApi.preferTranscode("Ranabaali.2026.720p.NF.WEB-DL.HIN-TEL.AAC5.1.AV1-XDMovies.co.mkv"))
            assertFalse(TeraBoxApi.preferTranscode("Movie.2026.x264.mkv"))
        } finally { TeraBoxApi.canPlayAv1 = { true } }
    }

    @Test fun expiredSignInThatStillGetsThePreviewAsksToSignInAgain() {
        TeraBoxApi.cookieSource = { "ndus=Yexpired" }
        serve(list = """{"errno":105}""", stream = preview)
        assertEquals(TeraBoxApi.Result.NeedsSignIn, TeraBoxApi.resolve(OkHttpClient(), share))
    }

    /**
     * OWN-10 (owner list, 2.1.0: "Terabox is not working"): on 2026-11-22 TeraBox answered shorturlinfo with
     * errno 400210 "need verify_v2" and 460030 "dragdrop" (its human check). Before, that ended in "TeraBox refused
     * the share (error 400210)" with no way forward; now the share page opens in the app's browser so the user can
     * do the check, and nothing is auto-captured.
     */
    @Test fun ownTenAHumanCheckOpensTheSharePageForTheUser() {
        TeraBoxApi.cookieSource = { "ndus=Ytest; browserid=b" }
        for (errno in listOf("400210", "460030")) {
            runCatching { server.close() }
            val s = MockWebServer()
            s.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
                    "/wap/share/filelist" -> MockResponse.Builder().body(page).build()
                    "/api/shorturlinfo" -> MockResponse.Builder().body("""{"errno":$errno,"errmsg":"need verify_v2"}""").build()
                    else -> MockResponse.Builder().code(404).build()
                }
            }
            s.start()
            try {
                TeraBoxApi.base = s.url("/").toString().trimEnd('/')
                val r = StreamResolver.resolveBlocking(StreamRequest(share), "ua", OkHttpClient())
                assertTrue("errno $errno: got $r", r is Resolution.NeedsBrowser)
                r as Resolution.NeedsBrowser
                assertTrue("the user's own check happens on the share page: ${r.pageUrl}", "1qkXqTvPA9GYY7sUY6dOGeA" in r.pageUrl && r.pageUrl != StreamResolver.TERABOX_SIGN_IN)
                assertFalse("nothing is captured automatically", r.auto)
            } finally { s.close() }
        }
    }

    /** OWN-10 (2): Hikari-like session handling: the share's own origin, a browserid cookie, one fresh retry. */
    @Test fun ownTenUsesTheShareOriginAndABrowserId() {
        assertEquals("https://1024terabox.com", TeraBoxApi.originFor(share))
        assertEquals("https://www.terabox.app", TeraBoxApi.originFor("https://example.com/s/1abc"))
        assertEquals(64, TeraBoxApi.freshBrowserId().length)
        TeraBoxApi.cookieSource = { "ndus=Ytest" }
        val seen = serve(list = """{"errno":0,"list":[{"fs_id":"993976246402096","dlink":"https://d.terabox.app/file/abc"}]}""")
        TeraBoxApi.canPlayAv1 = { true }
        val r = TeraBoxApi.resolve(OkHttpClient(), share)
        assertTrue("got $r", r is TeraBoxApi.Result.Ok)
        val api = seen.first { it.url.encodedPath == "/api/shorturlinfo" }
        assertTrue("browserid sent: ${api.headers["Cookie"]}", api.headers["Cookie"]!!.contains("browserid="))
    }

    @Test fun ownTenRetriesOnceWithAFreshSessionAfterAVerifyAnswer() {
        TeraBoxApi.cookieSource = { "ndus=Ytest" }
        var infoCalls = 0
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
                "/wap/share/filelist" -> MockResponse.Builder().body(page).build()
                "/api/shorturlinfo" -> MockResponse.Builder().body(if (++infoCalls == 1) """{"errno":400210}""" else info).build()
                "/share/list" -> MockResponse.Builder().body("""{"errno":0,"list":[{"fs_id":"993976246402096","dlink":"https://d.terabox.app/file/ok"}]}""").build()
                else -> MockResponse.Builder().code(404).build()
            }
        }
        server.start()
        TeraBoxApi.base = server.url("/").toString().trimEnd('/')
        val r = TeraBoxApi.resolve(OkHttpClient(), share)
        assertTrue("got $r", r is TeraBoxApi.Result.Ok)
        assertEquals(2, infoCalls)
    }

    @Test fun ownTenLockedShareSaysSo() {
        TeraBoxApi.cookieSource = { "ndus=Ytest" }
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
                "/wap/share/filelist" -> MockResponse.Builder().body(page).build()
                "/api/shorturlinfo" -> MockResponse.Builder().body("""{"errno":-9}""").build()
                else -> MockResponse.Builder().code(404).build()
            }
        }
        server.start()
        TeraBoxApi.base = server.url("/").toString().trimEnd('/')
        val r = TeraBoxApi.resolve(OkHttpClient(), share)
        assertTrue("got $r", r is TeraBoxApi.Result.Failed && "extraction code" in r.reason)
    }

    // TB-PREVIEW-2: a sign-in made on terabox.app (the app's sign-in page) is used on terabox.app for a 1024terabox.com
    // share, instead of sending terabox.app's cookie to 1024terabox.com (which then gave only the 30 s preview).
    @Test fun signInHostIsAskedFirstWhenTheShareHostHasNoSignIn() {
        val cookies = { o: String -> if (o == "https://www.terabox.app") "ndus=Yapp" else "browserid=x" }
        assertEquals(listOf("https://www.terabox.app"), TeraBoxApi.origins("https://1024terabox.com/s/1abc", cookies))
    }

    @Test fun shareHostGoesFirstWhenBothAreSignedIn() {
        val cookies = { _: String -> "ndus=Y" }
        val o = TeraBoxApi.origins("https://www.1024terabox.com/s/1abc", cookies)
        assertEquals("https://www.1024terabox.com", o.first())
        assertTrue("https://www.terabox.app" in o)
    }

    @Test fun noSignInAnywhereKeepsTheShareHost() {
        assertEquals(listOf("https://www.1024tera.com"), TeraBoxApi.origins("https://www.1024tera.com/s/1abc") { null })
    }
}
