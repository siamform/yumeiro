/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

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
