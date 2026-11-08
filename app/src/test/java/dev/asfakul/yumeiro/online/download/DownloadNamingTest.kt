/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.download

import dev.asfakul.yumeiro.online.OnlineLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadNamingTest {
    private fun link(q: Int, url: String = "https://x.test/v$q.mp4", hls: Boolean = false, dash: Boolean = false) =
        OnlineLink("n$q", "src", url, null, q, hls, dash, emptyMap())

    @Test fun folderHasTitleExtensionAndVersion() {
        assertEquals("Solo Leveling-(HiAnime-(Sub))", DownloadNaming.seriesFolder("Solo Leveling", "HiAnime", "Sub"))
        assertEquals("Solo Leveling-(HiAnime-(Dub))", DownloadNaming.seriesFolder("Solo Leveling", "HiAnime", "Dub"))
        assertEquals("Movie-(Ext)", DownloadNaming.seriesFolder("Movie", "Ext", null))
        assertEquals("Movie-(Ext)", DownloadNaming.seriesFolder("Movie", "Ext", "Original"))
    }

    @Test fun cleanRemovesIllegalCharacters() {
        assertEquals("Re Zero Part 2", DownloadNaming.clean("Re:Zero/Part 2?"))
        assertEquals("video", DownloadNaming.clean("???"))
        assertTrue(DownloadNaming.clean("a".repeat(200)).length <= 80)
    }

    @Test fun episodeNames() {
        assertEquals("S01E03 - Arise", DownloadNaming.episodeBase(1, 3, "Arise", 2))
        assertEquals("E07", DownloadNaming.episodeBase(null, 7, "Episode 7", 6))
        assertEquals("Episode 5", DownloadNaming.episodeBase(null, null, null, 4))
    }

    @Test fun pickLinkRespectsQualityAndSkipsDash() {
        val links = listOf(link(1080), link(720), link(480), link(2160, dash = true))
        assertEquals(1080, DownloadNaming.pickLink(links, 0)!!.quality)
        assertEquals(720, DownloadNaming.pickLink(links, 720)!!.quality)
        assertEquals(480, DownloadNaming.pickLink(links, 360)!!.quality)
        assertNull(DownloadNaming.pickLink(listOf(link(1080, dash = true)), 0))
    }

    @Test fun extensionFollowsLink() {
        assertEquals("ts", DownloadNaming.extensionFor(link(720, "https://x.test/a.m3u8", hls = true)))
        assertEquals("mkv", DownloadNaming.extensionFor(link(720, "https://x.test/a.mkv?t=1")))
        assertEquals("mp4", DownloadNaming.extensionFor(link(720, "https://x.test/play")))
    }

    @Test fun autoDeleteTimer() {
        val day = 86_400_000L
        val now = 100 * day
        assertTrue(DownloadNaming.expired(now - 3 * day, keep = false, days = 3, now = now))
        assertFalse(DownloadNaming.expired(now - 2 * day, keep = false, days = 3, now = now))
        assertFalse(DownloadNaming.expired(now - 30 * day, keep = true, days = 3, now = now))
        assertFalse(DownloadNaming.expired(now - 30 * day, keep = false, days = 0, now = now))
        assertFalse(DownloadNaming.expired(0, keep = false, days = 1, now = now))
    }
}
