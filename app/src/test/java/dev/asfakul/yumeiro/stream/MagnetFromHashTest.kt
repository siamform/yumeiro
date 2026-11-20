/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T-1 (owner list): a raw torrent info-hash (pasted in Play Link, or sent by a Stremio torrent add-on as
 * `infoHash` without a URL) must become a magnet link; before, Play Link rejected it and Stremio dropped it.
 */
class MagnetFromHashTest {
    private val hex = "08ada5a7a6183aae1e09d831df6748d566095a10"

    @Test fun hexHashBecomesMagnet() {
        assertEquals("magnet:?xt=urn:btih:$hex", LinkHosts.magnetFromHash(hex.uppercase()))
        assertTrue(LinkHosts.isTorrent(LinkHosts.magnetFromHash(" $hex ")!!))
    }

    @Test fun base32AndUrnFormsAreAccepted() {
        val b32 = "BCW2LJ5GDA5K4HQJ3AY56Z2I2VTASWQQ"
        assertEquals("magnet:?xt=urn:btih:$b32", LinkHosts.magnetFromHash(b32.lowercase()))
        assertEquals("magnet:?xt=urn:btih:$hex", LinkHosts.magnetFromHash("urn:btih:$hex"))
    }

    @Test fun nameAndTrackersAreEncoded() {
        val m = LinkHosts.magnetFromHash(hex, "Sintel 1080p", listOf("udp://tracker.example:1337/announce", "udp://tracker.example:1337/announce"))!!
        assertEquals("magnet:?xt=urn:btih:$hex&dn=Sintel+1080p&tr=udp%3A%2F%2Ftracker.example%3A1337%2Fannounce", m)
    }

    @Test fun otherTextIsLeftAlone() {
        assertNull(LinkHosts.magnetFromHash("https://example.com/video.mp4"))
        assertNull(LinkHosts.magnetFromHash("magnet:?xt=urn:btih:$hex"))
        assertNull(LinkHosts.magnetFromHash(hex.dropLast(1)))
        assertNull(LinkHosts.magnetFromHash("zzzz5a7a6183aae1e09d831df6748d566095a10"))
    }
}
