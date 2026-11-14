/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.video

import dev.asfakul.yumeiro.data.model.VideoItem
import dev.asfakul.yumeiro.player.PlaybackDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Round 12: "Video details" showed 0 MB, --:-- and no audio for every stream. */
class VideoDetailRowsTest {
    private fun rows(v: VideoItem, s: PlaybackDiagnostics.Snapshot) = videoDetailRows(v, s).toMap()
    private val stream = VideoItem(id = "s", title = "Big Buck Bunny", durationMs = 0, uri = "https://archive.org/bbb.mp4", sizeBytes = 0)

    @Test fun streamUsesWhatThePlayerDecoded() {
        val r = rows(stream, PlaybackDiagnostics.Snapshot(uri = stream.uri, state = "READY", firstFrame = true, width = 1920, height = 1080,
            durationMs = 596_000, sizeBytes = 276_134_947, audioLanguage = "en", audioChannels = 2))
        assertEquals("09:56", r["Duration"])
        assertEquals("263 MB", r["File size"])
        assertTrue(r["Resolution"]!!.startsWith("1920 × 1080"))
        assertEquals("English · stereo", r["Audio"])
        r.values.forEach { assertFalse(it, it.contains("0 MB") || it == "--:--") }
    }

    @Test fun estimatedSizeIsLabelled() {
        val r = rows(stream, PlaybackDiagnostics.Snapshot(uri = stream.uri, state = "READY", firstFrame = true, durationMs = 600_000, bitrate = 2_000_000))
        assertTrue(r["File size"]!!, r["File size"]!!.contains("estimated"))
    }

    @Test fun unknownSaysSoInsteadOfZero() {
        val r = rows(stream, PlaybackDiagnostics.Snapshot(uri = stream.uri, state = "READY", firstFrame = true))
        assertEquals("Not provided by the source", r["File size"])
        assertEquals("Not provided by the source", r["Duration"])
        assertEquals("Language not tagged in the stream", r["Audio"])
    }

    @Test fun liveStream() {
        val r = rows(stream, PlaybackDiagnostics.Snapshot(uri = stream.uri, state = "READY", firstFrame = true, live = true))
        assertEquals("Live", r["Duration"]); assertEquals("Live stream", r["File size"])
    }

    @Test fun libraryFileKeepsItsOwnNumbers() {
        val f = VideoItem(id = "f", title = "Clip", durationMs = 65_000, uri = "content://x/1", sizeBytes = 50L * 1024 * 1024, width = 1280, height = 720)
        val r = rows(f, PlaybackDiagnostics.Snapshot(uri = f.uri, state = "IDLE"))
        assertEquals("01:05", r["Duration"]); assertEquals("50.0 MB", r["File size"])
    }
}
