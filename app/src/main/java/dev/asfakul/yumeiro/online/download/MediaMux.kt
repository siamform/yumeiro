/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.download

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Joins a video-only and an audio-only file into one playable file with Android's MediaMuxer
 * (no re-encoding, so it is fast and lossless). Samples are interleaved by timestamp.
 */
object MediaMux {
    fun merge(video: File, audio: File, out: File, webm: Boolean, onProgress: (Float) -> Unit = {}) {
        out.delete()
        val sources = listOf(video to "video/", audio to "audio/").map { (f, prefix) ->
            val ex = MediaExtractor()
            ex.setDataSource(f.absolutePath)
            val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith(prefix) == true }
                ?: run { ex.release(); throw IOException("No ${prefix.trimEnd('/')} track in ${f.name}") }
            ex.selectTrack(track)
            ex to ex.getTrackFormat(track)
        }
        val muxer = MediaMuxer(out.absolutePath, if (webm) MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM else MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            val ids = sources.map { (_, format) -> muxer.addTrack(format) }
            val durationUs = sources.maxOf { (_, f) -> if (f.containsKey(MediaFormat.KEY_DURATION)) f.getLong(MediaFormat.KEY_DURATION) else 0L }
            muxer.start()
            val size = sources.maxOf { (_, f) -> if (f.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) f.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 0 }
                .coerceAtLeast(4 * 1024 * 1024)
            val buffer = ByteBuffer.allocateDirect(size)
            val info = MediaCodec.BufferInfo()
            val done = BooleanArray(2)
            var lastReport = 0L
            while (!done[0] || !done[1]) {
                // Next sample: the source whose timestamp is behind (keeps audio and video interleaved).
                val i = (0..1).filter { !done[it] }.minByOrNull { sources[it].first.sampleTime } ?: break
                val ex = sources[i].first
                buffer.clear()
                val n = ex.readSampleData(buffer, 0)
                if (n < 0) { done[i] = true; continue }
                info.offset = 0; info.size = n
                info.presentationTimeUs = ex.sampleTime.coerceAtLeast(0)
                info.flags = if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                muxer.writeSampleData(ids[i], buffer, info)
                ex.advance()
                if (durationUs > 0 && info.presentationTimeUs - lastReport > 2_000_000) {
                    lastReport = info.presentationTimeUs
                    onProgress((info.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f))
                }
            }
            muxer.stop()
        } catch (t: Throwable) {
            out.delete()
            throw if (t is IOException) t else IOException("Couldn't join video and audio: ${t.message}", t)
        } finally {
            runCatching { muxer.release() }
            sources.forEach { runCatching { it.first.release() } }
        }
    }
}
