/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import android.content.Context
import android.os.Handler
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/**
 * Audio and video renderers for every video Yumeiro plays.
 *
 * Round 13, the "video plays with no sound" fix. Two causes were found:
 *  1. Phones that report Dolby / DTS passthrough (or a surround setting left on "Auto") made the
 *     platform renderer send the AC-3 / E-AC-3 / DTS *bitstream* to the phone's speaker instead
 *     of decoding it, which plays as silence. Phones now get a PCM-only audio sink, so surround
 *     audio is always decoded; TVs (and the "Dolby Atmos" audio tier) keep passthrough for
 *     receivers and soundbars.
 *  2. The FFmpeg renderer sat after the platform one, so a platform decoder that claimed a
 *     surround format and then produced nothing (common with E-AC-3 JOC on phones without a
 *     Dolby licence) always won. On phones FFmpeg now goes first, but only for the surround
 *     codecs it is better at; everything else stays on the platform decoders.
 *
 * Settings → Audio → Quality and audio → Video decoder: Hardware+ (above, recommended),
 * Hardware (platform decoders only) or Software (FFmpeg first for every format it knows,
 * and software video decoders preferred).
 */
@OptIn(UnstableApi::class)
class YumeiroRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    private val mode = dev.asfakul.yumeiro.player.VideoPrefs.decoder.value
    private val passthrough = dev.asfakul.yumeiro.Edition.isTv ||
        dev.asfakul.yumeiro.player.AudioQuality.current() == dev.asfakul.yumeiro.player.AudioQuality.Tier.ATMOS

    init {
        setEnableDecoderFallback(true)
        if (mode == dev.asfakul.yumeiro.player.VideoPrefs.Decoder.SOFTWARE) {
            setMediaCodecSelector(SoftwareFirstSelector)
        }
    }

    override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink? {
        if (passthrough) return super.buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams)
        @Suppress("DEPRECATION")
        return androidx.media3.exoplayer.audio.DefaultAudioSink.Builder()
            .setAudioCapabilities(androidx.media3.exoplayer.audio.AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .build()
    }

    override fun buildAudioRenderers(
        context: Context, extensionRendererMode: Int, mediaCodecSelector: MediaCodecSelector, enableDecoderFallback: Boolean,
        audioSink: AudioSink, eventHandler: Handler, eventListener: AudioRendererEventListener, out: ArrayList<Renderer>,
    ) {
        super.buildAudioRenderers(context, extensionRendererMode, mediaCodecSelector, enableDecoderFallback, audioSink, eventHandler, eventListener, out)
        if (!ffmpegAvailable || mode == dev.asfakul.yumeiro.player.VideoPrefs.Decoder.HARDWARE) return
        runCatching {
            val ffmpeg = io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegAudioRenderer(eventHandler, eventListener, audioSink)
            when {
                mode == dev.asfakul.yumeiro.player.VideoPrefs.Decoder.SOFTWARE -> out.add(0, ffmpeg)
                // Phones: FFmpeg first for surround codecs only, then the platform renderer, then
                // FFmpeg again as the fallback for anything else the phone can't decode.
                !passthrough -> {
                    out.add(0, OnlyFormats(ffmpeg, SURROUND))
                    out += io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegAudioRenderer(eventHandler, eventListener, audioSink)
                }
                else -> out += ffmpeg
            }
        }.onFailure { Log.w(TAG, "FFmpeg audio renderer unavailable", it) }
    }

    /** A renderer that only offers to play [mimes]; for everything else it steps aside. */
    private class OnlyFormats(renderer: Renderer, private val mimes: Set<String>) : androidx.media3.exoplayer.ForwardingRenderer(renderer) {
        private val inner = renderer.capabilities
        private val caps = object : androidx.media3.exoplayer.RendererCapabilities by inner {
            override fun supportsFormat(format: androidx.media3.common.Format): Int =
                if (format.sampleMimeType in mimes) inner.supportsFormat(format)
                else androidx.media3.exoplayer.RendererCapabilities.create(androidx.media3.common.C.FORMAT_UNSUPPORTED_TYPE)
        }
        override fun getCapabilities(): androidx.media3.exoplayer.RendererCapabilities = caps
    }

    /** Software video decoders before hardware ones (Settings → Video decoder → Software). */
    private object SoftwareFirstSelector : MediaCodecSelector {
        override fun getDecoderInfos(mimeType: String, requiresSecureDecoder: Boolean, requiresTunnelingDecoder: Boolean) =
            MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
                .sortedBy { if (it.softwareOnly) 0 else 1 }
    }

    companion object {
        private const val TAG = "YumeiroRenderers"
        private val SURROUND = setOf(
            androidx.media3.common.MimeTypes.AUDIO_AC3, androidx.media3.common.MimeTypes.AUDIO_E_AC3,
            androidx.media3.common.MimeTypes.AUDIO_E_AC3_JOC, androidx.media3.common.MimeTypes.AUDIO_DTS,
            androidx.media3.common.MimeTypes.AUDIO_DTS_HD, androidx.media3.common.MimeTypes.AUDIO_DTS_EXPRESS,
            androidx.media3.common.MimeTypes.AUDIO_TRUEHD,
        )
        /** True when the FFmpeg libraries are packaged for this ABI and load. */
        val ffmpegAvailable: Boolean by lazy {
            runCatching { io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegLibrary.isAvailable() }.getOrDefault(false)
        }
        fun ffmpegSupports(mime: String?): Boolean = mime != null && ffmpegAvailable &&
            runCatching { io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegLibrary.supportsFormat(mime) }.getOrDefault(false)
    }
}
