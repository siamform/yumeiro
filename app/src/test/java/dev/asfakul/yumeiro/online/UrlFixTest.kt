/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlFixTest {
    @Test fun keepsGoodUrls() = assertEquals("https://a.com/x.m3u8", UrlFix.normalize("https://a.com/x.m3u8"))
    @Test fun protocolRelative() = assertEquals("https://cdn.a.com/v.mp4", UrlFix.normalize("//cdn.a.com/v.mp4"))
    @Test fun escapedSlashesAndQuotes() = assertEquals("https://a.com/p/v.m3u8", UrlFix.normalize(" \"https:\\/\\/a.com\\/p\\/v.m3u8\" "))
    @Test fun schemeTypos() {
        assertEquals("https://a.com/x", UrlFix.normalize("https:/a.com/x"))
        assertEquals("http://a.com/x", UrlFix.normalize("http:a.com/x"))
    }
    @Test fun relativeAgainstBase() {
        assertEquals("https://site.to/embed/1", UrlFix.normalize("/embed/1", "https://site.to/watch/abc"))
        assertEquals("https://site.to/watch/seg.ts", UrlFix.normalize("seg.ts", "https://site.to/watch/abc"))
    }
    @Test fun bareHost() = assertEquals("https://cdn.example.com/x.m3u8", UrlFix.normalize("cdn.example.com/x.m3u8"))
    @Test fun unfixableStaysUnchanged() = assertEquals("javascript:void(0)", UrlFix.normalize("javascript:void(0)"))
    @Test fun isHttp() {
        assertTrue(UrlFix.isHttp("HTTPS://a.com")); assertFalse(UrlFix.isHttp("ftp://a")); assertFalse(UrlFix.isHttp(null))
    }
    @Test fun badLinkIsNotAnOutdatedExtension() {
        val e = IllegalArgumentException("Expected URL scheme 'http' or 'https' but no scheme was found for /embed")
        assertTrue(OnlineErrors.isBadLink(e))
        assertFalse(OnlineErrors.extensionMayBeOutdated(e))
        assertFalse(OnlineErrors.isBadLink(IllegalStateException("x")))
    }
}
