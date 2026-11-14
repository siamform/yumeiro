/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import androidx.media3.common.C
import androidx.media3.common.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioCodecInfoTest {
    @Test fun namesAndLabels() {
        assertEquals("Opus", AudioCodecInfo.nameFor("audio/opus", null))
        assertEquals("AAC-LC", AudioCodecInfo.nameFor("audio/mp4a-latm", "mp4a.40.2"))
        assertEquals("HE-AAC", AudioCodecInfo.nameFor("audio/mp4a-latm", "mp4a.40.5"))
        assertEquals("HE-AAC v2", AudioCodecInfo.nameFor("audio/mp4a-latm", "mp4a.40.29"))
        assertEquals("AAC", AudioCodecInfo.nameFor("audio/mp4a-latm", null))
        assertEquals("Dolby Atmos", AudioCodecInfo.nameFor("audio/eac3-joc", null))
        assertEquals("MP3", AudioCodecInfo.nameFor("audio/mpeg", null))
        assertNull(AudioCodecInfo.nameFor(null, null))

        val opus = AudioCodecInfo("audio/opus", bitrate = 160_000, sampleRate = 48_000, channels = 2, decoder = "c2.android.opus.decoder")
        assertEquals("Opus \u00B7 160 kbps \u00B7 48 kHz \u00B7 Stereo", opus.summary)
        assertEquals("Software", opus.decoderKind)
        assertFalse(opus.lossless)

        val flac = AudioCodecInfo("audio/flac", sampleRate = 44_100, channels = 2, bitDepth = 24, decoder = "c2.qti.flac.decoder")
        assertTrue(flac.lossless)
        assertEquals("FLAC \u00B7 44.1 kHz \u00B7 24-bit \u00B7 Stereo", flac.summary)
        assertEquals("Hardware", flac.decoderKind)
        assertEquals("5.1 surround", AudioCodecInfo(channels = 6).channelsLabel)
        assertTrue(AudioCodecInfo().isEmpty)
    }

    @Test fun fromExoFormat() {
        val f = Format.Builder().setSampleMimeType("audio/mp4a-latm").setCodecs("mp4a.40.2")
            .setAverageBitrate(128_000).setSampleRate(44_100).setChannelCount(2).build()
        val i = AudioCodecInfo.from(f)!!
        assertEquals("AAC-LC \u00B7 128 kbps \u00B7 44.1 kHz \u00B7 Stereo", i.summary)
        assertEquals(16, AudioCodecInfo.bitDepthOf(C.ENCODING_PCM_16BIT))
        assertNull(AudioCodecInfo.from(null))
    }
}
