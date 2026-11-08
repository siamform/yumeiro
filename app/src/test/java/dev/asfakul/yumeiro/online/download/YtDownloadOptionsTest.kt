/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.download

import dev.asfakul.yumeiro.online.download.YtDownloadOptions.Audio
import dev.asfakul.yumeiro.online.download.YtDownloadOptions.Video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDownloadOptionsTest {
    private val videos = listOf(
        Video(137, 1080, 30, "mp4", "avc1.640028", 100_000_000, true),
        Video(248, 1080, 30, "webm", "vp9", 80_000_000, true),
        Video(299, 1080, 60, "mp4", "avc1.64002a", 150_000_000, true),
        Video(399, 1080, 30, "mp4", "av01.0.08M.08", 60_000_000, true),
        Video(136, 720, 30, "mp4", "avc1.4d401f", 50_000_000, true),
        Video(18, 360, 30, "mp4", "avc1.42001E", 20_000_000, false),
    )
    private val audios = listOf(
        Audio(140, 128_000, "m4a", "mp4a.40.2", 5_000_000),
        Audio(251, 160_000, "webm", "opus", 6_000_000),
    )

    @Test fun oneChoicePerQualityPreferringH264() {
        val c = YtDownloadOptions.build(videos, audios, 36).filter { !it.audioOnly }
        assertEquals(listOf("1080p", "1080p60", "720p", "360p").sorted(), c.map { it.label }.sorted())
        val p1080 = c.first { it.label == "1080p" }
        assertEquals(137, p1080.videoItag)
        assertEquals(140, p1080.audioItag)
        assertEquals("mp4", p1080.container)
        assertEquals(105_000_000L, p1080.sizeBytes)
        val p360 = c.first { it.label == "360p" }
        assertEquals(-1, p360.audioItag)
        assertEquals(1080, c.first().height)
    }

    @Test fun audioChoicesForMusic() {
        val a = YtDownloadOptions.build(videos, audios, 36).filter { it.audioOnly }
        assertEquals(listOf(251, 140), a.map { it.audioItag })
        assertTrue(a.all { it.videoItag == -1 })
    }

    @Test fun av1NeedsAndroid14() {
        val av1 = videos.first { it.itag == 399 }
        assertFalse(YtDownloadOptions.muxable(av1, 33))
        assertTrue(YtDownloadOptions.muxable(av1, 34))
        assertTrue(YtDownloadOptions.muxable(videos.first { it.itag == 248 }, 28))
    }

    @Test fun sizeLabels() {
        assertEquals("", YtDownloadOptions.mb(0))
        assertEquals("<1 MB", YtDownloadOptions.mb(500_000))
        assertEquals("105 MB", YtDownloadOptions.mb(105_000_000))
        assertEquals("1.5 GB", YtDownloadOptions.mb(1_500_000_000))
    }
}
