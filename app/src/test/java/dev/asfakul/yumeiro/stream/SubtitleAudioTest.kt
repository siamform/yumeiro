/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import androidx.media3.common.C
import androidx.media3.extractor.text.CuesWithTiming
import dev.asfakul.yumeiro.player.TrackPicks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleAudioTest {
    @Test fun sniffsKinds() {
        assertEquals(SubtitleKind.WEBVTT, SubtitleSniffer.kind("\uFEFFWEBVTT\n\n00:01.000 --> 00:02.000\nhi"))
        assertEquals(SubtitleKind.SUBRIP, SubtitleSniffer.kind("1\n00:00:01,000 --> 00:00:02,000\nhi"))
        assertEquals(SubtitleKind.SSA, SubtitleSniffer.kind("[Script Info]\nTitle: x"))
        assertEquals(SubtitleKind.VTT_NO_HEADER, SubtitleSniffer.kind("1\n00:00:01.000 --> 00:00:02.000\nhi"))
        assertEquals(SubtitleKind.UNKNOWN, SubtitleSniffer.kind("<html>404</html>"))
    }
    @Test fun addsMissingVttHeader() {
        val b = "00:01.000 --> 00:02.000\nhi".toByteArray()
        val out = String(SubtitleSniffer.prepare(b, 0, b.size, SubtitleKind.VTT_NO_HEADER))
        assertTrue(out.startsWith("WEBVTT\n\n"))
    }
    @Test fun utf16IsDecoded() {
        val b = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "1\n00:00:01,000 --> x".toByteArray(Charsets.UTF_16LE)
        assertEquals(SubtitleKind.SUBRIP, SubtitleSniffer.kind(SubtitleSniffer.decode(b, 0, b.size)))
    }
    @Test fun shiftsCues() {
        val c = CuesWithTiming(emptyList(), 1_000_000, 500_000)
        assertEquals(1_500_000, SubtitleTiming.shift(c, 500_000).startTimeUs)
        assertEquals(0, SubtitleTiming.shift(c, -3_000_000).startTimeUs)
        val unset = CuesWithTiming(emptyList(), C.TIME_UNSET, C.TIME_UNSET)
        assertEquals(C.TIME_UNSET, SubtitleTiming.shift(unset, 5).startTimeUs)
    }
    @Test fun picksPreferredLanguageThenEnglish() {
        val t = listOf(TrackPicks.Text(0, 0, "es", null), TrackPicks.Text(1, 0, null, "English"), TrackPicks.Text(2, 0, "bn", null))
        assertEquals(2, TrackPicks.bestText(t, listOf("bn-BD"))!!.group)
        assertEquals(1, TrackPicks.bestText(t, listOf("fr"))!!.group)
    }
    @Test fun dedupesVariantAudio() {
        val a = listOf(
            TrackPicks.Audio(0, 0, "en", "English", 2, "audio/mp4a-latm", true, false),
            TrackPicks.Audio(1, 0, "en", "English", 2, "audio/mp4a-latm", true, true),
            TrackPicks.Audio(2, 0, "ja", "Japanese", 2, "audio/mp4a-latm", true, false),
        )
        val out = TrackPicks.dedupeAudio(a)
        assertEquals(2, out.size); assertTrue(out.first { it.language == "en" }.selected)
    }
    @Test fun detectsSeparateAudioPlaylists() {
        val master = "#EXTM3U\n#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"a\",NAME=\"en\",URI=\"a/en.m3u8\"\n#EXT-X-STREAM-INF:BANDWIDTH=1,AUDIO=\"a\"\nv.m3u8\n"
        assertTrue(AdFilterDataSource.hasSeparateAudio(master))
        assertFalse(AdFilterDataSource.hasSeparateAudio("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nv.m3u8\n"))
    }
}
