/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.mtrevanced.stream

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StreamDetectionTest {
    private lateinit var server: MockWebServer
    private val client = OkHttpClient.Builder().build()
    private val ua = "Yumeiro-Test"

    @Before fun start() { server = MockWebServer(); server.start() }
    @After fun stop() { server.close() }

    private fun resolve(path: String, request: StreamRequest? = null) =
        StreamResolver.resolveBlocking(request ?: StreamRequest(url = server.url(path).toString()), ua, client)

    // --- Sniffer ---------------------------------------------------------------------------

    @Test fun sniffer_urlHints() {
        assertEquals(MediaKind.HLS, StreamSniffer.fromUrl("https://a.b/live/index.m3u8?token=1")?.kind)
        assertEquals(MediaKind.DASH, StreamSniffer.fromUrl("https://a.b/x/manifest.mpd")?.kind)
        assertEquals(MediaKind.VIDEO, StreamSniffer.fromUrl("https://a.b/ch1.ts")?.kind)
        assertEquals(MediaKind.AUDIO, StreamSniffer.fromUrl("https://a.b/song.mp3")?.kind)
        assertEquals(MediaKind.IPTV_PLAYLIST, StreamSniffer.fromUrl("https://a.b/list.m3u")?.kind)
        assertNull(StreamSniffer.fromUrl("https://a.b/play?id=42"))
    }

    @Test fun sniffer_contentBeatsExtension() {
        val ts = ByteArray(376).also { it[0] = 0x47; it[188] = 0x47 }
        assertEquals(MediaKind.VIDEO, StreamSniffer.classify("application/octet-stream", "https://a.b/stream", ts).kind)
        val hls = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=800000\nlow.m3u8\n".toByteArray()
        assertEquals(MediaKind.HLS, StreamSniffer.classify("text/plain", "https://a.b/get.php", hls).kind)
        val iptv = "#EXTM3U\n#EXTINF:-1 tvg-id=\"x\",News\nhttp://a.b/news.m3u8\n".toByteArray()
        assertEquals(MediaKind.IPTV_PLAYLIST, StreamSniffer.classify("application/x-mpegurl", "https://a.b/live.m3u8", iptv).kind)
        val mpd = "<?xml version=\"1.0\"?><MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\">".toByteArray()
        assertEquals(MediaKind.DASH, StreamSniffer.classify(null, "https://a.b/x", mpd).kind)
        val mp4 = byteArrayOf(0, 0, 0, 0x18) + "ftypisom".toByteArray() + ByteArray(8)
        assertEquals(MediaKind.VIDEO, StreamSniffer.classify(null, "https://a.b/x", mp4).kind)
        assertEquals(MediaKind.WEB_PAGE, StreamSniffer.classify("text/html; charset=utf-8", "https://a.b/watch", "<!doctype html><html>".toByteArray()).kind)
        assertEquals(MediaKind.AUDIO, StreamSniffer.classify("audio/mpeg", "https://a.b/radio", ByteArray(4)).kind)
    }

    // --- Web page extraction ---------------------------------------------------------------

    @Test fun pageExtractor_findsManifestsAndYoutube() {
        val html = """
            <html><head><title>Live TV</title><meta property="og:video" content="https://cdn.x/og.mp4"></head>
            <body><video src="/hls/main.m3u8"></video>
            <script>var cfg = {"file":"https:\/\/cdn.x\/live\/ch.m3u8?t=9"};</script>
            <iframe src="https://www.youtube.com/embed/abc"></iframe></body></html>
        """.trimIndent()
        val a = WebPageExtractor.analyze(html, "https://site.example/watch/1")
        assertEquals("Live TV", a.title)
        val urls = a.candidates.map { it.url }
        assertTrue(urls.toString(), "https://site.example/hls/main.m3u8" in urls)
        assertTrue(urls.toString(), urls.any { it.startsWith("https://cdn.x/live/ch.m3u8") })
        assertTrue(urls.any { "youtube.com/embed" in it })
    }

    @Test fun platformRules() {
        assertTrue(PlatformRules.ruleFor("https://youtu.be/xyz") is PlatformRules.Handoff)
        assertTrue(PlatformRules.ruleFor("https://www.netflix.com/watch/1") is PlatformRules.Handoff)
        assertTrue(PlatformRules.ruleFor("https://toffeelive.com/en/live/somoy") is PlatformRules.WebSession)
        assertNull(PlatformRules.ruleFor("https://example.com/a.m3u8"))
    }

    // --- Probe / resolver (real HTTP) ------------------------------------------------------

    @Test fun resolver_extensionlessHlsDetectedFromContent() {
        server.enqueue(MockResponse.Builder().addHeader("Content-Type", "text/plain")
            .body("#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\nseg1.ts\n").build())
        val r = resolve("/play?id=1")
        assertTrue(r.toString(), r is Resolution.Playable)
        assertEquals(StreamFormat.HLS, (r as Resolution.Playable).request.format)
    }

    @Test fun resolver_followsRedirectAndSendsHeaders() {
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", server.url("/real.bin").toString()).build())
        server.enqueue(MockResponse.Builder().addHeader("Content-Type", "video/mp4")
            .body(Buffer().write(byteArrayOf(0, 0, 0, 0x18) + "ftypmp42".toByteArray() + ByteArray(64))).build())
        val r = resolve("/go", StreamRequest(url = server.url("/go").toString(), referer = "https://ref.example/", cookie = "sid=1"))
        assertTrue(r.toString(), r is Resolution.Playable)
        val first = server.takeRequest()
        assertEquals("https://ref.example/", first.headers["Referer"])
        assertEquals("sid=1", first.headers["Cookie"])
        assertEquals("/real.bin", server.takeRequest().url.encodedPath)
    }

    @Test fun resolver_iptvPlaylistBecomesChannelList() {
        server.enqueue(MockResponse.Builder().addHeader("Content-Type", "application/vnd.apple.mpegurl")
            .body("#EXTM3U\n#EXTINF:-1 group-title=\"News\",Channel A\nhttp://x/a.m3u8\n#EXTINF:-1,Channel B\nhttp://x/b.ts\n").build())
        val r = resolve("/tv.m3u8")
        assertTrue(r.toString(), r is Resolution.Playlist)
        assertEquals(2, (r as Resolution.Playlist).channels.size)
    }

    @Test fun resolver_pageWithManifestResolvedWithRefererAndOrigin() {
        val page = "<html><title>Ch</title><script>player.load('${server.url("/live/x.m3u8")}')</script></html>"
        server.enqueue(MockResponse.Builder().addHeader("Content-Type", "text/html").body(page).build())
        server.enqueue(MockResponse.Builder().addHeader("Content-Type", "application/x-mpegURL").body("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\na.m3u8\n").build())
        val r = resolve("/watch")
        assertTrue(r.toString(), r is Resolution.Playable)
        val req = (r as Resolution.Playable).request
        assertEquals(StreamFormat.HLS, req.format)
        assertEquals(server.url("/watch").toString(), req.pageUrl)
        server.takeRequest()
        assertEquals(server.url("/watch").toString(), server.takeRequest().headers["Referer"])
    }

    @Test fun resolver_pageWithoutStreamNeedsBrowser() {
        server.enqueue(MockResponse.Builder().addHeader("Content-Type", "text/html").body("<html><div id=app></div><script src=app.js></script></html>").build())
        assertTrue(resolve("/spa") is Resolution.NeedsBrowser)
    }

    @Test fun resolver_httpErrorsAreActionable() {
        // The resolver now retries refused links with other header sets, so answer every probe.
        server.dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: mockwebserver3.RecordedRequest): MockResponse =
                if (request.url.encodedPath == "/x") MockResponse.Builder().code(403).build()
                else MockResponse.Builder().code(429).addHeader("Retry-After", "12").build()
        }
        val f = resolve("/x") as Resolution.Failed
        assertTrue(f.title.contains("403"))
        assertTrue(f.message.contains("Referer"))
        val r = resolve("/y") as Resolution.Failed
        assertTrue(r.message, r.message.contains("12s"))
    }

    @Test fun resolver_youtubeHandedOff_unsupportedProtocolFails() {
        assertTrue(StreamResolver.resolveBlocking(StreamRequest("https://www.youtube.com/watch?v=1"), ua, client) is Resolution.Handoff)
        assertTrue(StreamResolver.resolveBlocking(StreamRequest("udp://239.0.0.1:1234"), ua, client) is Resolution.Failed)
    }

    @Test fun retryAfter_parsesSecondsAndDates() {
        assertEquals(30L, RetryAfter.parseSeconds("30"))
        val now = 1_700_000_000_000L
        val date = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("GMT") }.format(java.util.Date(now + 45_000))
        assertEquals(45L, RetryAfter.parseSeconds(date, now))
        assertEquals(5L, RetryAfter.fromHeaders(mapOf("retry-after" to listOf("5"))))
        assertNull(RetryAfter.fromHeaders(Headers.headersOf().toMultimap()))
    }

    @Test fun pageCapture_buildsSessionRequest() {
        val cap = dev.asfakul.mtrevanced.ui.screens.stream.PageStreamCapture()
        assertNull(cap.onRequest("https://cdn.x/app.js", "GET", emptyMap()))
        val c = cap.onRequest("https://cdn.x/live/master.m3u8?tok=1", "GET", mapOf("Referer" to "https://site/", "Authorization" to "Bearer t", "Accept" to "*/*"))
        assertNotNull(c)
        assertNull(cap.onRequest("https://cdn.x/live/master.m3u8?tok=1", "GET", emptyMap()))
        assertNull(cap.onRequest("https://lic.x/widevine/license", "POST", emptyMap()))
        cap.drmSystem = "com.widevine.alpha"
        val req = cap.toRequest(c!!, "https://site/watch", "Ch", "UA", "a=1", null)
        assertEquals(StreamFormat.HLS, req.format)
        assertEquals("https://site/", req.referer)
        assertEquals("https://site", req.origin)
        assertEquals("a=1", req.cookie)
        assertEquals("Bearer t", req.headers["Authorization"])
        assertNull(req.headers["Accept"])
        assertEquals(DrmScheme.WIDEVINE, req.drmScheme)
        assertEquals("https://lic.x/widevine/license", req.drmLicenseUrl)
    }
}
