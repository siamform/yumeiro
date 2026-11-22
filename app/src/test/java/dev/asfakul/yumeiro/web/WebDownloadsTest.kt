/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.web

import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.online.web.WebDownloads
import dev.asfakul.yumeiro.stream.StreamFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** DL-WEB: a file link inside an in-app web page goes to the shared download popup. */
class WebDownloadsTest {
    @Test fun aFileLinkBecomesADownloadWithThePageCookieAndReferer() {
        val r = WebDownloads.requestFor(
            "https://files.example.org/get/Movie%20Night.mp4?token=1", "UA/1", null, "video/mp4",
            "https://example.org/watch/1", "sid=abc",
        )
        assertNotNull(r)
        assertEquals("Movie Night.mp4", r!!.title)
        assertEquals("UA/1", r.userAgent)
        assertEquals("https://example.org/watch/1", r.referer)
        assertEquals("sid=abc", r.cookie)
        assertEquals(StreamFormat.AUTO, r.format)
    }

    @Test fun contentDispositionNamesTheFileAndFilenameStarWins() {
        assertEquals("Ep 01.mkv", WebDownloads.fileName("https://x.org/d?id=9", "attachment; filename=\"Ep 01.mkv\""))
        assertEquals("Ep 01.mkv", WebDownloads.fileName("https://x.org/d?id=9", "attachment; filename=Ep 01.mkv"))
        assertEquals("アニメ.mp4", WebDownloads.fileName("https://x.org/d", "attachment; filename=\"a.mp4\"; filename*=UTF-8''%E3%82%A2%E3%83%8B%E3%83%A1.mp4"))
    }

    @Test fun pathPartsCannotEscapeTheDownloadFolder() {
        val n = WebDownloads.fileName("https://x.org/d", "attachment; filename=\"../../etc/passwd\"")
        assertTrue(n, !n.contains('/') && !n.startsWith("."))
        assertEquals("download", WebDownloads.fileName("https://x.org/", null))
    }

    @Test fun hlsAndDashByMimeType() {
        assertEquals(StreamFormat.HLS, WebDownloads.requestFor("https://x.org/a", null, null, "application/vnd.apple.mpegurl", null, null)!!.format)
        assertEquals(StreamFormat.DASH, WebDownloads.requestFor("https://x.org/a", null, null, "application/dash+xml; charset=utf-8", null, null)!!.format)
    }

    @Test fun blobAndDataLinksAreNotFetchedButExplained() {
        assertNull(WebDownloads.requestFor("blob:https://x.org/1234", null, null, null, null, null))
        assertNull(WebDownloads.requestFor("data:text/plain;base64,aGk=", null, null, null, null, null))
        assertTrue(WebDownloads.PAGE_MADE_FILE.isNotBlank())
    }

    @Test fun theListenerIsOnByDefault() {
        assertTrue(FeatureFlags.Flag.WEB_DOWNLOAD_LISTENER.default)
    }
}
