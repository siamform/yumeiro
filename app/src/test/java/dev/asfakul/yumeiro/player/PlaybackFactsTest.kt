/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaybackFactsTest {
    private val ready = PlaybackDiagnostics.Snapshot(uri = "x", state = "READY", firstFrame = true)

    private fun group(selected: Boolean, vararg f: Format) =
        Tracks.Group(TrackGroup(*f), false, IntArray(f.size) { C.FORMAT_HANDLED }, BooleanArray(f.size) { selected && it == 0 })

    @Test fun readsSelectedAudioLanguageChannelsAndBitrate() {
        val video = Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).setWidth(1920).setHeight(1080).setAverageBitrate(4_000_000).build()
        val ja = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).setLanguage("ja").setChannelCount(2).setAverageBitrate(128_000).build()
        val en = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).setLanguage("en").setChannelCount(6).build()
        val tracks = Tracks(listOf(group(true, video), group(true, ja), group(false, en)))
        val s = PlaybackFacts.fromTracks(ready.copy(durationMs = 24 * 60_000L), tracks)
        val f = PlaybackFacts.of(s)
        assertEquals("1080p · 1920×1080", f.resolution)
        assertEquals("24:00", f.duration)
        assertEquals("Japanese · stereo · 2 tracks", f.audio)
        assertTrue(f.size!!.startsWith("≈ ") && f.sizeEstimated)
        assertTrue(f.missing.isEmpty())
    }

    @Test fun missingDetailsAreListedNotHidden() {
        val f = PlaybackFacts.of(ready.copy(width = 1280, height = 720))
        assertEquals(listOf("duration", "audio language", "file size"), f.missing)
    }

    @Test fun undeterminedLanguageFallsBackToTheSourceLabel() {
        val und = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).setLanguage("und").build()
        val s = PlaybackFacts.fromTracks(ready, Tracks(listOf(group(true, und))))
        assertNull(s.audioLanguage)
        assertEquals("Dub · English", PlaybackFacts.of(s, "Dub · English").audio)
    }

    @Test fun liveHasNoSizeAndDeviceFilesUseTheRealSize() {
        val live = PlaybackFacts.of(ready.copy(live = true))
        assertEquals("Live", live.duration)
        assertTrue("file size" !in live.missing)
        val file = PlaybackFacts.of(ready.copy(sizeBytes = 700L * 1024 * 1024, durationMs = 90_000))
        assertEquals("700 MB", file.size)
    }

    @Test fun nothingDecodedYetIsPendingNotMissing() {
        val f = PlaybackFacts.of(PlaybackDiagnostics.Snapshot(uri = "x", state = "BUFFERING"))
        assertTrue(f.pending && f.missing.isEmpty())
    }
}
