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
 * The platform decoders first, then FFmpeg (NextLib) for audio formats the phone can't decode —
 * AC-3 / E-AC-3 / DTS / TrueHD on most phones, which played as video *without sound*. Video and
 * subtitles stay on the default renderers. On ABIs without the FFmpeg libraries it's the default.
 */
@OptIn(UnstableApi::class)
class YumeiroRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    init {
        setEnableDecoderFallback(true)
    }

    override fun buildAudioRenderers(
        context: Context, extensionRendererMode: Int, mediaCodecSelector: MediaCodecSelector, enableDecoderFallback: Boolean,
        audioSink: AudioSink, eventHandler: Handler, eventListener: AudioRendererEventListener, out: ArrayList<Renderer>,
    ) {
        super.buildAudioRenderers(context, extensionRendererMode, mediaCodecSelector, enableDecoderFallback, audioSink, eventHandler, eventListener, out)
        if (!ffmpegAvailable) return
        runCatching {
            out += io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegAudioRenderer(eventHandler, eventListener, audioSink)
        }.onFailure { Log.w(TAG, "FFmpeg audio renderer unavailable", it) }
    }

    companion object {
        private const val TAG = "YumeiroRenderers"
        /** True when the FFmpeg libraries are packaged for this ABI and load. */
        val ffmpegAvailable: Boolean by lazy {
            runCatching { io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegLibrary.isAvailable() }.getOrDefault(false)
        }
        fun ffmpegSupports(mime: String?): Boolean = mime != null && ffmpegAvailable &&
            runCatching { io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegLibrary.supportsFormat(mime) }.getOrDefault(false)
    }
}
