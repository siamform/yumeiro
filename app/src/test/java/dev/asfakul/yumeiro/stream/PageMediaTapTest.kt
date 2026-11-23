/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import dev.asfakul.yumeiro.ui.screens.stream.PageStreamCapture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SD-1 (owner list 6): the stream finder missed the player on a5x2nt.movielinkbd.at. Its hls.js player,
 * in a same-site iframe, loads a master playlist from `https://watch.movielink.tv/m/<token>`: no
 * extension and `content-type: image/jpeg` (headers saved 2026-11-24). The `<video>` fallback file is
 * `https://fsl.movielink.tv/p/<token>`, also without an extension. The playlists below are the real
 * responses with the tokens cut out.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PageMediaTapTest {
    private val page = "https://a5x2nt.movielinkbd.at/movie/haiku-2026"
    private val master = "https://watch.movielink.tv/m/yvEXtk_P0LbydxieWrGAzYuECs5tCvsotyjD-MT68QXMivdtlDOoF6ik0scp"
    private val variant = "https://watch.movielink.tv/hls/13jCrbuDfada5ggR5qG818KE-CPvtQYy5k8KnsTGYMy3gc2GYaL3P6JoHfmgV3t1"
    private val file = "https://fsl.movielink.tv/p/a6fu5_ngwvCP_rYN.vsTrzXrUcHPLKuTHJOz274cRl-yGBht2"

    private fun fixture(name: String) = javaClass.classLoader!!.getResource("web/$name")!!.readText()

    @Test fun `the real responses are playlists the address does not reveal`() {
        val m = fixture("movielink_master.m3u8.txt")
        assertTrue(m.startsWith("#EXTM3U") && "#EXT-X-STREAM-INF" in m)
        assertTrue(fixture("movielink_variant.m3u8.txt").startsWith("#EXTM3U"))
        // The bug: by address alone none of these is media, so the finder offered nothing.
        val c = PageStreamCapture()
        assertNull(c.onRequest(master, "GET", emptyMap()))
        assertNull(c.onRequest(file, "GET", emptyMap()))
        assertTrue(c.all().isEmpty())
    }

    @Test fun `a playlist the page loaded is offered as HLS with the page's referer and origin`() {
        val c = PageStreamCapture()
        val got = c.onPageMedia(master, "hls-master")
        assertNotNull(got)
        assertEquals(MediaKind.HLS, got!!.kind)
        // Its variants are the same video: only the master is offered.
        assertNull(c.onPageMedia(variant, "hls"))
        assertEquals(1, c.all().size)
        val r = c.toRequest(got, page, "Haiku (2026)", userAgent = "UA", cookie = null, base = null)
        assertEquals(StreamFormat.HLS, r.format)
        // The host refuses the playlist without Referer or Origin (checked 2026-11-24).
        assertEquals(page, r.referer)
        assertEquals("https://a5x2nt.movielinkbd.at", r.origin)
        assertEquals(got, PageStreamCapture.bestForAuto(c.all()))
    }

    @Test fun `a lone media playlist and a DASH manifest are offered too`() {
        val c = PageStreamCapture()
        assertEquals(MediaKind.HLS, c.onPageMedia(variant, "hls")?.kind)
        assertEquals(MediaKind.DASH, c.onPageMedia("https://cdn.example.org/play/77", "dash")?.kind)
        assertNull("unknown kinds are ignored", c.onPageMedia("https://cdn.example.org/x", "json"))
        assertNull("seen once", c.onPageMedia(variant, "hls"))
    }

    @Test fun `a video element's own source is media whatever its address`() {
        val c = PageStreamCapture()
        assertEquals(MediaKind.VIDEO, c.onPageMedia(file, "element")?.kind)
        // A known extension still decides the kind.
        assertEquals(MediaKind.HLS, c.onPageMedia("https://cdn.example.org/live/index.m3u8", "element")?.kind)
        // An audio element is never auto-played as the video.
        val a = PageStreamCapture()
        assertEquals(MediaKind.AUDIO, a.onPageMedia("https://cdn.example.org/bgm/7", "audio")?.kind)
        assertNull(PageStreamCapture.bestForAuto(a.all()))
    }

    @Test fun `ads and non-http addresses stay out`() {
        val c = PageStreamCapture()
        assertNull(c.onPageMedia("blob:https://a5x2nt.movielinkbd.at/1", "hls-master"))
        assertNull(c.onPageMedia("https://pubads.g.doubleclick.net/gampad/ads?iu=1", "hls-master"))
    }

    @Test fun `the page tap reads only the start of a response and never changes it`() {
        val js = PageStreamCapture.MEDIA_TAP_JS
        assertTrue("reports through the finder bridge", "MTFinder.onManifest" in js)
        assertTrue("fetch bodies come from a cancelled clone", "clone()" in js && "rd.cancel()" in js)
        assertTrue("guarded against running twice per frame", "__yuTap" in js)
    }
}
