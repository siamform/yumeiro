/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ol

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import dev.asfakul.yumeiro.online.download.DownloadSettings
import dev.asfakul.yumeiro.stream.DrmScheme
import dev.asfakul.yumeiro.stream.StreamFormat
import dev.asfakul.yumeiro.stream.StreamRequest
import dev.asfakul.yumeiro.ui.screens.downloads.DownloadPrompt
import dev.asfakul.yumeiro.ui.screens.downloads.StreamDownloadAsk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** OL-4: website / link downloads show the Offline / Permanent popup before they start. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StreamDownloadAskTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun setUp() { DownloadSettings.setAskFirst(ctx, true); DownloadPrompt.hosts = 1 }
    @After fun tearDown() { DownloadPrompt.hosts = 0; DownloadPrompt.dismiss() }

    @Test fun aWebsiteVideoAsksBeforeItDownloads() {
        val req = StreamRequest(url = "https://cdn.example.com/clip.mp4", title = "Site clip", pageUrl = "https://site.example/watch/1")
        var refused: String? = null
        StreamDownloadAsk.ask(ctx, req, "https://site.example/watch/1") { refused = it }
        val p = DownloadPrompt.current.value
        assertEquals("no popup: the download would start at once", DownloadPrompt.Type.LINK, p?.type)
        assertEquals("Site clip", p?.title)
        assertEquals("Original file", p?.quality)
        assertNull(refused)
    }

    @Test fun aProtectedStreamSaysWhyAndAsksNothing() {
        val req = StreamRequest(url = "https://cdn.example.com/m.mpd", title = "DRM", format = StreamFormat.DASH, drmScheme = DrmScheme.WIDEVINE)
        var refused: String? = null
        StreamDownloadAsk.ask(ctx, req) { refused = it }
        assertNull(DownloadPrompt.current.value)
        assertEquals("Protected video, can't be saved. You can still play it.", refused)
    }

    @Test fun qualityLineNamesTheStreamKind() {
        assertEquals("Best of the stream (HLS)", StreamDownloadAsk.qualityText(StreamRequest(url = "https://x/a.m3u8", format = StreamFormat.HLS)))
        assertEquals("Best of the stream (DASH)", StreamDownloadAsk.qualityText(StreamRequest(url = "https://x/a.mpd", format = StreamFormat.DASH)))
    }
}
