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

    // ---- Round 10 ------------------------------------------------------------------------------
    @Test fun knownFileHostsStartTheirPlayerAutomatically() {
        val t = LinkHosts.classify("https://www.terabox.com/sharing/link?surl=abc") as LinkHosts.Browser
        assertEquals(true, t.auto)
        assertEquals(true, (LinkHosts.classify("https://www.diskwala.com/app/xyz") as LinkHosts.Browser).auto)
        assertEquals("TeraBox", (LinkHosts.classify("https://dubox.com/s/1abc") as LinkHosts.Browser).host)
    }

    @Test fun pixeldrainFilePageBecomesTheFileApi() {
        val r = LinkHosts.classify("https://pixeldrain.com/u/AbC123xy") as LinkHosts.Direct
        assertEquals("https://pixeldrain.com/api/file/AbC123xy", r.url)
        assertEquals("Pixeldrain", r.host)
        // Lists aren't single files: left to the page resolver.
        assertEquals(null, LinkHosts.pixeldrainDirect("https://pixeldrain.com/l/AbC123xy"))
    }

    @Test fun oneDriveShareUsesTheDocumentedSharesEndpoint() {
        val link = "https://1drv.ms/v/s!AkFoo123?e=xyz"
        val b64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(link.toByteArray())
        assertEquals("https://api.onedrive.com/v1.0/shares/u!$b64/root/content", LinkHosts.oneDriveDirect(link))
        assertEquals(false, LinkHosts.oneDriveDirect(link)!!.contains("="))
        assertEquals("https://contoso.sharepoint.com/:v:/g/personal/a/EAbc?e=1&download=1",
            LinkHosts.oneDriveDirect("https://contoso.sharepoint.com/:v:/g/personal/a/EAbc?e=1"))
        assertEquals(null, LinkHosts.oneDriveDirect("https://contoso.sharepoint.com/sites/x/page.aspx"))
    }

    @Test fun mediaFirePageRevealsItsDownloadButton() {
        val html = """<a class="input popsok" aria-label="Download file" href="https://download2390.mediafire.com/abc/def/movie.mp4" id="downloadButton">"""
        assertEquals("https://download2390.mediafire.com/abc/def/movie.mp4", LinkHosts.mediaFireDownload(html))
        assertEquals(true, LinkHosts.isMediaFire("https://www.mediafire.com/file/abc/movie.mp4/file"))
        assertEquals(null, LinkHosts.mediaFireDownload("<html>no button</html>"))
    }

    @Test fun teraBoxWebPlayerStreamIsRecognisedAsHls() {
        val u = "https://www.terabox.com/share/streaming?uk=1&shareid=2&type=M3U8_AUTO_720&fid=3&sign=x&timestamp=4"
        assertEquals(MediaKind.HLS, StreamSniffer.fromUrl(u)?.kind)
    }
}
