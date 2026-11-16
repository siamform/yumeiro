/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.r15

import dev.asfakul.yumeiro.player.QualityEngine
import dev.asfakul.yumeiro.stream.StreamFormat
import dev.asfakul.yumeiro.stream.StreamMediaFactory
import dev.asfakul.yumeiro.stream.StreamRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * BUG-4 (F1): a YouTube video plays from a local single-representation DASH manifest. The player
 * built its quality list only for network URIs, so YouTube got no request and showed one real
 * quality while the download sheet showed every resolution.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Bug4QualityListTest {
    private val manifest = "file:///data/user/0/dev.asfakul.yumeiro/cache/yt/aqz-KE-bpKQ_137.mpd"
    private val yt = StreamRequest(
        url = manifest, title = "Big Buck Bunny", format = StreamFormat.DASH, formatFromSource = true,
        pageUrl = "https://www.youtube.com/watch?v=aqz-KE-bpKQ", group = "YouTube",
    )

    @Test fun youTubeManifestIsNotANetworkUri() {
        // The precondition that broke the old rule ("network URI only").
        assertFalse(StreamMediaFactory.isNetworkUri(manifest))
    }

    @Test fun youTubeManifestStillGetsItsRequest() {
        val fallback = StreamRequest(url = manifest, title = "Big Buck Bunny")
        val req = QualityEngine.requestForList(registered = yt, fallback = fallback, networkUri = false)
        assertSame(yt, req)
        assertEquals("aqz-KE-bpKQ", QualityEngine.youTubeId(req!!))
    }

    @Test fun localFileWithoutRegistrationGetsNoRequest() {
        // A local video must not borrow the sources of a stale Yumix session.
        val local = StreamRequest(url = "file:///sdcard/Movies/a.mp4", title = "a")
        assertNull(QualityEngine.requestForList(registered = null, fallback = local, networkUri = false))
    }

    @Test fun networkStreamFallsBackToTheOpeningRequest() {
        val net = StreamRequest(url = "https://example.com/v.m3u8", title = "v")
        assertSame(net, QualityEngine.requestForList(registered = null, fallback = net, networkUri = true))
    }

    private fun link(q: Int, url: String, hls: Boolean = false) =
        dev.asfakul.yumeiro.online.OnlineLink("S $q", "src", url, null, q, hls, false, emptyMap())

    /** F5: CloudStream's Qualities.Unknown (400) and Auto/unknown (0, -1) never become a height row. */
    @Test fun unknownQualityIsNotAnInventedHeight() {
        val links = listOf(
            link(1080, "https://a.example/1080.mp4"), link(720, "https://b.example/720.mp4"),
            link(400, "https://c.example/master.m3u8", hls = true), link(0, "https://d.example/auto.m3u8", hls = true),
            link(-1, "https://e.example/x.mp4"), link(718, "https://f.example/718.mp4"),
        )
        val by = QualityEngine.onlineHeights(links)
        assertEquals(listOf(1080, 720), by.keys.sortedDescending())
        assertEquals(listOf(1, 5), by.getValue(720).map { it.index })
    }
}
