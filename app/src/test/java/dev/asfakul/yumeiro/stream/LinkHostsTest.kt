/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import dev.asfakul.yumeiro.online.web.SavedSites
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkHostsTest {
    @Test fun googleDriveShareLinksBecomeDirect() {
        val id = "1AbCdEfGhIjKlMnOpQrStUv"
        for (u in listOf(
            "https://drive.google.com/file/d/$id/view?usp=sharing",
            "https://drive.google.com/open?id=$id",
            "https://drive.google.com/uc?id=$id&export=download",
        )) {
            val r = LinkHosts.classify(u) as LinkHosts.Direct
            assertEquals("https://drive.usercontent.google.com/download?id=$id&export=download&confirm=t", r.url)
            assertEquals("Google Drive", r.host)
        }
    }

    @Test fun dropboxGetsRaw() {
        assertEquals("https://www.dropbox.com/s/abc/v.mp4?raw=1", LinkHosts.dropboxDirect("https://www.dropbox.com/s/abc/v.mp4?dl=0"))
        assertEquals("https://www.dropbox.com/scl/fi/x/v.mkv?rlkey=k&raw=1", LinkHosts.dropboxDirect("https://www.dropbox.com/scl/fi/x/v.mkv?rlkey=k&dl=0"))
    }

    @Test fun teraboxAndDiskwalaOpenInPageBrowser() {
        assertEquals("TeraBox", (LinkHosts.classify("https://www.1024terabox.com/s/1abc") as LinkHosts.Browser).host)
        assertEquals("TeraBox", (LinkHosts.classify("https://teraboxapp.com/s/1abc") as LinkHosts.Browser).host)
        assertEquals("DiskWala", (LinkHosts.classify("https://www.diskwala.com/app/xyz") as LinkHosts.Browser).host)
    }

    @Test fun torrentsDetected() {
        assertTrue(LinkHosts.isTorrent("magnet:?xt=urn:btih:abcdef"))
        assertTrue(LinkHosts.isTorrent("https://example.org/file.torrent?x=1"))
        assertFalse(LinkHosts.isTorrent("https://example.org/live.m3u8"))
        assertTrue(LinkHosts.classify("magnet:?xt=urn:btih:abc") is LinkHosts.Torrent)
    }

    @Test fun torrServerUrl() {
        val u = LinkHosts.torrServerUrl("192.168.1.5:8090/", "magnet:?xt=urn:btih:abc&dn=x y", "My Show")
        assertEquals("http://192.168.1.5:8090/stream/My+Show?link=magnet%3A%3Fxt%3Durn%3Abtih%3Aabc%26dn%3Dx+y&index=1&m3u&title=My+Show", u)
        assertTrue(LinkHosts.torrServerUrl("http://127.0.0.1:8090", "magnet:?a", m3u = false).contains("&play"))
    }

    @Test fun plainLinksUntouched() {
        assertNull(LinkHosts.classify("https://example.com/live/index.m3u8"))
        assertNull(LinkHosts.classify("https://docs.google.com/document/d/x"))
    }

    @Test fun torrServerAddressNormalized() {
        assertEquals("http://192.168.1.5:8090", TorrentSettings.normalize("192.168.1.5:8090"))
        assertEquals("https://ts.example.com", TorrentSettings.normalize("https://ts.example.com/"))
        assertNull(TorrentSettings.normalize("http://"))
    }

    @Test fun savedSitesNormalizeAndName() {
        assertEquals("https://archive.org", SavedSites.normalize(" archive.org "))
        assertEquals("http://example.com/watch", SavedSites.normalize("http://example.com/watch"))
        assertNull(SavedSites.normalize("not a site"))
        assertNull(SavedSites.normalize("ftp://x.com"))
        assertNull(SavedSites.normalize("hello"))
        assertEquals("My Site", SavedSites.defaultName("https://www.my-site.co.uk/x"))
        assertEquals("Archive", SavedSites.defaultName("https://archive.org"))
    }
}
