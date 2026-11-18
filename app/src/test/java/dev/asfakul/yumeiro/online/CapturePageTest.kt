/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** NBUG-12: the headless capture must load a web page, never the refused stream file. */
class CapturePageTest {
    private val site = "https://www.porn4fans.com"
    private val file = "$site/get_file/5/abc/12000/12934/12934_720p.mp4/"
    private val title = "$site/video/12934/some-title/"

    @Test fun kvsDirectFileFallsThroughToTheTitlePage() {
        // Porn4fans: data = ld+json contentUrl = the link URL, referer = bare main URL.
        assertEquals(title, CapturePage.pick(listOf(site), listOf(file), file, title))
    }

    @Test fun mediaDataIsSkippedEvenWhenNoLinkCarriesIt() {
        assertEquals(title, CapturePage.pick(emptyList(), emptyList(), "https://cdn.x/v/1/master.m3u8?t=1", title))
        assertEquals(title, CapturePage.pick(emptyList(), emptyList(), "https://cdn.x/a.MPD", title))
    }

    @Test fun embedRefererStillWins() {
        assertEquals("https://embed.x/e/42", CapturePage.pick(listOf(null, site, "https://embed.x/e/42"), listOf(file), "https://site.x/watch/1", title))
    }

    @Test fun pageDataStillComesBeforeTheTitle() {
        assertEquals("https://site.x/watch/1?ep=2", CapturePage.pick(emptyList(), listOf(file), "https://site.x/watch/1?ep=2", title))
    }

    @Test fun nothingUsable() {
        assertNull(CapturePage.pick(listOf(site), listOf(file), file, "rk:42"))
        assertNull(CapturePage.pick(emptyList(), emptyList(), "{\"id\":1}", null))
    }

    @Test fun mediaDetection() {
        assertTrue(CapturePage.looksLikeMedia(file))
        assertTrue(CapturePage.looksLikeMedia("https://h/x.ism/Manifest"))
        assertFalse(CapturePage.looksLikeMedia("https://h/video/12934/title/"))
        assertFalse(CapturePage.looksLikeMedia("https://h/watch?v=a.mp4"))
        assertFalse(CapturePage.looksLikeMedia("https://h.mp4.com/"))
    }
}
