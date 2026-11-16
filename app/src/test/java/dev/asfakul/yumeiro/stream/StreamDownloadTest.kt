/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import dev.asfakul.yumeiro.online.download.OnlineDownloads.Kind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** BUG-7 7a: which links the player's Download action offers to save (same rule as the Play link page). */
class StreamDownloadTest {
    @Test fun progressiveAndHlsCanBeSaved() {
        assertTrue(StreamDownload.canSave(StreamRequest(url = "https://example.com/a.mp4")))
        assertTrue(StreamDownload.canSave(StreamRequest(url = "https://example.com/live/index.m3u8")))
        assertTrue(StreamDownload.canSave(StreamRequest(url = "https://example.com/x", format = StreamFormat.PROGRESSIVE)))
    }

    @Test fun dashSmoothAndDrmAreRefused() {
        assertFalse(StreamDownload.canSave(StreamRequest(url = "https://example.com/manifest.mpd")))
        assertFalse(StreamDownload.canSave(StreamRequest(url = "https://example.com/a.mp4", format = StreamFormat.DASH)))
        assertFalse(StreamDownload.canSave(StreamRequest(url = "https://example.com/a.mp4", format = StreamFormat.SMOOTH)))
        assertFalse(StreamDownload.canSave(StreamRequest(url = "https://example.com/a.mp4", drmScheme = DrmScheme.WIDEVINE)))
    }
}

/** BUG-7 7b: which finished downloads go to the gallery as Play links, and with which MIME type. */
class PlayLinkPublishTest {
    private fun item(kind: dev.asfakul.yumeiro.online.download.OnlineDownloads.Kind, api: String) =
        dev.asfakul.yumeiro.online.download.OnlineDownloads.Item("id", "t", api, "u", null, null, emptyMap(), "https://x/a.mp4", false, emptyMap(), "t-id.mp4", kind = kind)

    @Test fun onlyNetworkLinksArePlayLinks() {
        val y = dev.asfakul.yumeiro.online.download.YtDownloads
        assertTrue(y.isPlayLink(item(Kind.LINK, "Network")))
        assertFalse(y.isPlayLink(item(Kind.LINK, "SomeExtension")))
        assertFalse(y.isPlayLink(item(Kind.EPISODE, "Network")))
        assertFalse(y.isPlayLink(item(Kind.YT_VIDEO, "Network")))
    }

    @Test fun mimeFollowsTheSavedExtension() {
        val y = dev.asfakul.yumeiro.online.download.YtDownloads
        org.junit.Assert.assertEquals("video/mp2t", y.linkMime("ts"))
        org.junit.Assert.assertEquals("video/x-matroska", y.linkMime("MKV"))
        org.junit.Assert.assertEquals("video/mp4", y.linkMime("mp4"))
        org.junit.Assert.assertEquals("video/mp4", y.linkMime("bin"))
    }
}
