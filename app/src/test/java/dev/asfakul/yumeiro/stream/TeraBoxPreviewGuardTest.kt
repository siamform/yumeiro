/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * TB-PREVIEW-1 (owner list 19): a stream caught on a TeraBox page is never played when it is the 30-second preview.
 * The fixture is TeraBox's real anonymous answer of 2026-11-23 for share 1qkXqTvPA9GYY7sUY6dOGeA (a 1.0 GB MKV):
 * 3 x 10 s and ENDLIST. The signed segment queries were removed.
 */
class TeraBoxPreviewGuardTest {
    private val preview = javaClass.classLoader!!.getResource("terabox/anonymous-preview-480.m3u8")!!.readText()
    private val full = buildString {
        append("#EXTM3U\n#EXT-X-TARGETDURATION:10\n")
        repeat(600) { append("#EXTINF:10,\nhttps://v5-dm.freeterabox.com/seg$it.ts\n") }
        append("#EXT-X-ENDLIST\n")
    }
    private val share = "https://www.1024terabox.com/s/1qkXqTvPA9GYY7sUY6dOGeA"
    private val captured = "https://www.1024terabox.com/share/streaming?uk=1&shareid=2&type=M3U8_AUTO_480&fid=3&sign=x&timestamp=4"
    private val client = OkHttpClient()
    private var savedCookies: (String) -> String? = { null }

    @Before fun noSignIn() { savedCookies = TeraBoxApi.cookieSource; TeraBoxApi.cookieSource = { null } }
    @After fun restore() { TeraBoxApi.cookieSource = savedCookies }

    @Test fun realPreviewIsRecognised() {
        assertTrue(TeraBoxApi.isPreviewCapture(preview))
        assertEquals(30.0, TeraBoxApi.playlistSeconds(preview), 0.01)
        assertFalse("a full transcode is not a preview", TeraBoxApi.isPreviewCapture(full))
        assertFalse("not a playlist", TeraBoxApi.isPreviewCapture("{\"errno\":130}"))
    }

    @Test fun onlyTeraBoxPlayerStreamsAreChecked() {
        assertTrue(TeraBoxApi.isWebStream(captured))
        assertTrue(TeraBoxApi.isWebStream("https://www.terabox.app/share/streaming?type=M3U8_AUTO_720"))
        assertFalse(TeraBoxApi.isWebStream("https://d.terabox.app/file/abc?fid=1"))
        assertFalse(TeraBoxApi.isWebStream("https://example.com/share/streaming?x=1"))
    }

    @Test fun shareLinkIsFoundAmongThePageAddresses() {
        assertEquals(share, TeraBoxApi.shareIn(null, "https://example.com/s/1abcdef", share))
        assertNull(TeraBoxApi.shareIn("https://www.terabox.app/wap/outlogin/login", null))
    }

    @Test fun previewNeverPlaysAndAsksForSignIn() {
        val req = StreamRequest(url = captured, format = StreamFormat.HLS, pageUrl = share)
        val r = StreamResolver.checkCapture(req, client, share) { preview }
        assertTrue("got $r", r is Resolution.NeedsBrowser)
        assertEquals(StreamResolver.TERABOX_SIGN_IN, (r as Resolution.NeedsBrowser).pageUrl)
        assertEquals("the share is kept for the resume after sign-in", share, r.base.url)
    }

    @Test fun unreadablePlaylistIsTreatedAsPreview() {
        val r = StreamResolver.checkCapture(StreamRequest(url = captured, format = StreamFormat.HLS), client, share) { null }
        assertFalse(r is Resolution.Playable)
    }

    @Test fun fullStreamAndOtherSitesPlayUnchanged() {
        val req = StreamRequest(url = captured, format = StreamFormat.HLS, pageUrl = share)
        val r = StreamResolver.checkCapture(req, client, share) { full }
        assertEquals(captured, (r as Resolution.Playable).request.url)
        var asked = false
        val other = StreamRequest(url = "https://cdn.example.com/v/master.m3u8", format = StreamFormat.HLS)
        val o = StreamResolver.checkCapture(other, client, null) { asked = true; preview }
        assertEquals(other, (o as Resolution.Playable).request)
        assertFalse("other sites are not fetched", asked)
    }
}
