/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import java.util.Locale

/**
 * What the music player is actually decoding right now (from ExoPlayer's selected audio format
 * and decoder), for the Now Playing codec line and Song info. Works for songs on the device and
 * online songs alike. Unknown values are null and simply not shown.
 */
data class AudioCodecInfo(
    val mime: String? = null,
    val codecs: String? = null,
    val bitrate: Int? = null,
    val sampleRate: Int? = null,
    val channels: Int? = null,
    /** PCM bit depth when known (16, 24, 32). */
    val bitDepth: Int? = null,
    val decoder: String? = null,
    val language: String? = null,
) {
    /** "Opus", "AAC-LC", "FLAC", "Dolby Digital Plus"... */
    val codecName: String? get() = nameFor(mime, codecs)

    val lossless: Boolean get() = mime?.lowercase() in LOSSLESS

    val bitrateLabel: String? get() = bitrate?.takeIf { it > 0 }?.let { "${(it + 500) / 1000} kbps" }

    val sampleRateLabel: String? get() = sampleRate?.takeIf { it > 0 }?.let {
        val k = it / 1000.0
        if (k == Math.floor(k)) "${k.toInt()} kHz" else String.format(Locale.US, "%.1f kHz", k)
    }

    val channelsLabel: String? get() = when (channels) {
        null, 0, -1 -> null
        1 -> "Mono"
        2 -> "Stereo"
        3 -> "2.1"
        6 -> "5.1 surround"
        8 -> "7.1 surround"
        else -> "$channels channels"
    }

    val bitDepthLabel: String? get() = bitDepth?.takeIf { it > 0 }?.let { "$it-bit" }

    /** "Hardware" or "Software" decoder, from the codec name Android reports. */
    val decoderKind: String? get() = decoder?.let {
        val d = it.lowercase()
        when {
            d.startsWith("c2.android.") || d.startsWith("omx.google.") || d.contains("ffmpeg") || d.contains("libopus") || d.contains("libflac") || d.contains("sw") -> "Software"
            else -> "Hardware"
        }
    }

    /** The short line under the title: "Opus · 160 kbps · 48 kHz · Stereo". */
    val summary: String get() = listOfNotNull(codecName, bitrateLabel, sampleRateLabel, bitDepthLabel?.takeIf { lossless }, channelsLabel)
        .joinToString(" \u00B7 ")

    val isEmpty: Boolean get() = summary.isBlank()

    companion object {
        private val LOSSLESS = setOf("audio/flac", "audio/alac", "audio/raw", "audio/true-hd", "audio/wav", "audio/x-wav")

        fun nameFor(mime: String?, codecs: String?): String? {
            val m = mime?.lowercase() ?: return codecs?.takeIf { it.isNotBlank() }?.uppercase()
            val c = codecs?.lowercase().orEmpty()
            return when (m) {
                "audio/mp4a-latm", "audio/aac" -> when {
                    c.startsWith("mp4a.40.29") -> "HE-AAC v2"
                    c.startsWith("mp4a.40.5") -> "HE-AAC"
                    c.startsWith("mp4a.40.42") -> "xHE-AAC"
                    c.startsWith("mp4a.40.2") -> "AAC-LC"
                    else -> "AAC"
                }
                "audio/opus" -> "Opus"
                "audio/vorbis" -> "Vorbis"
                "audio/mpeg" -> "MP3"
                "audio/mpeg-l2" -> "MP2"
                "audio/flac" -> "FLAC"
                "audio/alac" -> "ALAC"
                "audio/raw", "audio/wav", "audio/x-wav" -> "PCM"
                "audio/ac3" -> "Dolby Digital"
                "audio/eac3" -> "Dolby Digital Plus"
                "audio/eac3-joc" -> "Dolby Atmos"
                "audio/ac4" -> "Dolby AC-4"
                "audio/true-hd" -> "Dolby TrueHD"
                "audio/vnd.dts", "audio/dts" -> "DTS"
                "audio/vnd.dts.hd" -> "DTS-HD"
                "audio/vnd.dts.uhd;profile=p2" -> "DTS:X"
                "audio/3gpp" -> "AMR-NB"
                "audio/amr-wb" -> "AMR-WB"
                "audio/mha1", "audio/mhm1" -> "MPEG-H"
                "audio/x-ape", "audio/ape" -> "APE"
                "audio/wma", "audio/x-ms-wma" -> "WMA"
                else -> m.removePrefix("audio/").removePrefix("x-").uppercase()
            }
        }

        /** ExoPlayer's PCM encoding constant to a bit depth. */
        fun bitDepthOf(pcmEncoding: Int): Int? = when (pcmEncoding) {
            androidx.media3.common.C.ENCODING_PCM_8BIT -> 8
            androidx.media3.common.C.ENCODING_PCM_16BIT -> 16
            androidx.media3.common.C.ENCODING_PCM_24BIT -> 24
            androidx.media3.common.C.ENCODING_PCM_32BIT, androidx.media3.common.C.ENCODING_PCM_FLOAT -> 32
            else -> null
        }

        fun from(f: androidx.media3.common.Format?, decoder: String? = null): AudioCodecInfo? {
            f ?: return null
            val br = listOf(f.averageBitrate, f.bitrate, f.peakBitrate).firstOrNull { it > 0 }
            return AudioCodecInfo(
                mime = f.sampleMimeType,
                codecs = f.codecs,
                bitrate = br,
                sampleRate = f.sampleRate.takeIf { it > 0 },
                channels = f.channelCount.takeIf { it > 0 },
                bitDepth = bitDepthOf(f.pcmEncoding),
                decoder = decoder,
                language = f.language,
            )
        }
    }
}
