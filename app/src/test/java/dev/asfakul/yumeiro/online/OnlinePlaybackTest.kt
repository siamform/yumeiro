/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import dev.asfakul.yumeiro.stream.StreamFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlinePlaybackTest {
    private fun link(url: String, hls: Boolean = false, dash: Boolean = false, audio: List<Pair<String, Map<String, String>>> = emptyList()) =
        OnlineLink("n", "src", url, "https://site.example/", 720, hls, dash, mapOf("X-Test" to "1"), audioTracks = audio)

    @Test fun declaredTypesWin() {
        assertEquals(StreamFormat.HLS, OnlinePlayback.formatOf(link("https://cdn.example/v", hls = true)))
        assertEquals(StreamFormat.DASH, OnlinePlayback.formatOf(link("https://cdn.example/v", dash = true)))
    }

    @Test fun plainVideoIsProgressive_unlessUrlIsAManifest() {
        assertEquals(StreamFormat.PROGRESSIVE, OnlinePlayback.formatOf(link("https://cdn.example/file.mp4?token=a.m3u8")))
        assertEquals(StreamFormat.PROGRESSIVE, OnlinePlayback.formatOf(link("https://cdn.example/get?id=5")))
        assertEquals(StreamFormat.HLS, OnlinePlayback.formatOf(link("https://cdn.example/master.M3U8?sig=1")))
        assertEquals(StreamFormat.DASH, OnlinePlayback.formatOf(link("https://cdn.example/a/manifest.mpd#t=1")))
    }

    @Test fun requestCarriesEverythingTheExtensionGave() {
        val req = OnlinePlayback.request(
            "Title", link("https://cdn.example/v.mp4", audio = listOf("https://cdn.example/a.m4a" to mapOf("Referer" to "https://a.example/"))),
            listOf(OnlineSubtitle("English", "https://subs.example/en.vtt", mapOf("Referer" to "https://subs.example/"))),
        )
        assertTrue(req.formatFromSource)
        assertEquals(StreamFormat.PROGRESSIVE, req.format)
        assertEquals("https://site.example/", req.referer)
        assertEquals("1", req.headers["X-Test"])
        assertEquals(mapOf("Referer" to "https://subs.example/"), req.subtitles.single().headers)
        assertEquals("https://cdn.example/a.m4a", req.audioTracks.single().url)
        assertEquals(mapOf("Referer" to "https://a.example/"), req.audioTracks.single().headers)
    }
}
