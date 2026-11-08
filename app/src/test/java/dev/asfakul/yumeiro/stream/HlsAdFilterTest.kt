/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsAdFilterTest {
    private fun seg(uri: String, d: Double = 10.0) = "#EXTINF:$d,\n$uri\n"
    private fun vod(body: String) = "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:10\n#EXT-X-PLAYLIST-TYPE:VOD\n$body#EXT-X-ENDLIST\n"
    private val main = (1..20).joinToString("") { seg("https://cdn1.video-host.net/hls/ep1/seg$it.ts") }

    @Test fun removesForeignAdBreakBehindDiscontinuity() {
        val ad = "#EXT-X-DISCONTINUITY\n" + seg("https://tracker.adnet.example/x/1.ts", 5.0) + seg("https://tracker.adnet.example/x/2.ts", 5.0) + "#EXT-X-DISCONTINUITY\n"
        val first = (1..5).joinToString("") { seg("https://cdn1.video-host.net/hls/ep1/a$it.ts") }
        val r = HlsAdFilter.clean(vod(first + ad + main), "https://cdn1.video-host.net/hls/ep1/index.m3u8")
        assertEquals(2, r.removedSegments)
        assertFalse(r.text.contains("adnet"))
        assertTrue(r.text.contains("#EXT-X-DISCONTINUITY")) // groups 0 → 2 still separated
        assertTrue(r.text.trimEnd().endsWith("#EXT-X-ENDLIST"))
    }

    @Test fun removesScte35CueOutBlock() {
        val body = seg("https://h.example/a.ts") + "#EXT-X-CUE-OUT:DURATION=20\n" + seg("https://h.example/promo1.ts") + seg("https://h.example/promo2.ts") +
            "#EXT-X-CUE-IN\n" + main
        val r = HlsAdFilter.clean(vod(body))
        assertEquals(2, r.removedSegments)
        assertFalse(r.text.contains("promo"))
    }

    @Test fun keepsLoadBalancedCdnHostsAndLegitDiscontinuities() {
        val other = "#EXT-X-DISCONTINUITY\n" + (1..3).joinToString("") { seg("https://s7.video-host.net/hls/ep1/r$it.ts") }
        val text = vod(main + other)
        assertEquals(text, HlsAdFilter.clean(text).text)
    }

    @Test fun neverTouchesLiveOrMasterPlaylists() {
        val live = "#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXT-X-MEDIA-SEQUENCE:100\n" + seg("https://ads.example/1.ts") + seg("https://h.example/2.ts") + seg("https://h.example/3.ts")
        assertEquals(0, HlsAdFilter.clean(live).removedSegments)
        val master = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nlow.m3u8\n"
        assertEquals(master, HlsAdFilter.clean(master).text)
    }

    @Test fun refusesToDropMostOfTheVideo() {
        val body = seg("https://h.example/a.ts") + (1..5).joinToString("") { seg("https://h.example/ads/$it.ts") }
        assertEquals(0, HlsAdFilter.clean(vod(body)).removedSegments)
    }

    @Test fun keepsKeyAndMapStateForSegmentsAfterRemovedOnes() {
        val body = "#EXT-X-KEY:METHOD=AES-128,URI=\"k1\"\n#EXT-X-MAP:URI=\"init.mp4\"\n" + seg("https://ads.example/pre.ts", 5.0) + main
        val r = HlsAdFilter.clean(vod(body))
        assertEquals(1, r.removedSegments)
        assertTrue(r.text.contains("#EXT-X-KEY:METHOD=AES-128"))
        assertTrue(r.text.contains("#EXT-X-MAP:URI=\"init.mp4\""))
    }

    @Test fun siteOf_groupsSubdomains() {
        assertEquals("video-host.net", HlsAdFilter.siteOf("s7.video-host.net"))
        assertEquals("example.co.uk", HlsAdFilter.siteOf("a.b.example.co.uk"))
        assertEquals("10.0.0.2", HlsAdFilter.siteOf("10.0.0.2"))
    }
}
