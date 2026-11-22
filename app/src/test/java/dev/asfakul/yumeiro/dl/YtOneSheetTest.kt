/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.dl

import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.online.download.DownloadTracks
import dev.asfakul.yumeiro.online.download.YtDownloadOptions.Choice
import dev.asfakul.yumeiro.online.download.YtOneSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** DL-ONE: YouTube downloads go through the one shared download popup; its choices map onto YouTube's list. */
class YtOneSheetTest {
    private val v1080 = Choice("1080p", "MP4 · H.264", 137, 140, "mp4", 300_000_000, false, 1080)
    private val v720 = Choice("720p", "MP4 · H.264", 136, 140, "mp4", 150_000_000, false, 720)
    private val v360 = Choice("360p", "MP4", 18, -1, "mp4", 40_000_000, false, 360)
    private val a160 = Choice("Audio 160 kbps", "Opus", -1, 251, "webm", 5_000_000, true)
    private val a128 = Choice("Audio 128 kbps", "AAC", -1, 140, "m4a", 4_000_000, true)
    private val all = listOf(v1080, v720, v360, a160, a128)

    @Test fun videoOfferHasHeightsAndAudioOnly() {
        val o = YtOneSheet.offer(all, music = false)
        assertEquals(listOf(1080, 720, 360), o.heights)
        assertEquals(listOf("Audio only · 160 kbps", "Audio only · 128 kbps"), o.audios)
        assertTrue(o.hasChoices)
    }

    @Test fun songOfferHasNoVideoHeights() {
        val o = YtOneSheet.offer(all, music = true)
        assertTrue(o.heights.isEmpty())
        assertEquals(2, o.audios.size)
    }

    @Test fun bestAndCappedHeights() {
        assertEquals(v1080, YtOneSheet.choose(all, false, DownloadTracks.Pick()))
        assertEquals(v1080, YtOneSheet.choose(all, false, DownloadTracks.Pick(maxHeight = 0)))
        assertEquals(v720, YtOneSheet.choose(all, false, DownloadTracks.Pick(maxHeight = 720)))
        assertEquals(v720, YtOneSheet.choose(all, false, DownloadTracks.Pick(maxHeight = 1000)))
        // Below every height: the smallest video, never nothing.
        assertEquals(v360, YtOneSheet.choose(all, false, DownloadTracks.Pick(maxHeight = 144)))
    }

    @Test fun audioPickWinsAndSongsGetBestAudio() {
        assertEquals(a160, YtOneSheet.choose(all, false, DownloadTracks.Pick(maxHeight = 720, audio = "Audio only · 160 kbps")))
        // A song with Default: best audio, M4A first.
        assertEquals(a128, YtOneSheet.choose(all, true, DownloadTracks.Pick()))
        assertEquals(null, YtOneSheet.choose(emptyList(), false, DownloadTracks.Pick()))
    }

    @Test fun flagIsOn() { assertTrue(FeatureFlags.Flag.DL_ONE_SHEET.default) }
}
