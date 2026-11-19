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
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.FileDataSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.inspector.MediaExtractorCompat
import androidx.media3.muxer.MediaMuxerCompat
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Joins a video-only and an audio-only file into one playable file (no re-encoding, so it is fast
 * and lossless). Samples are interleaved by timestamp.
 *
 * ADR-022: on Android 9+ (all phones, newer TVs) this uses the platform MediaExtractor and
 * MediaMuxer. Below Android 9 (TV builds on Android 7–8.1) MP4 output uses Media3's own reader and
 * writer instead: the old platform MPEG4Writer rejects B-frame video ("do not support out of order
 * frames", G8-9) and the old platform MediaExtractor can't open a joined DASH SegmentTemplate fMP4
 * track ("Failed to instantiate extractor", G8-10). WebM output always uses the platform muxer.
 */
@OptIn(markerClass = [UnstableApi::class]) // ADR-022: Media3 compat muxer/extractor, pinned version
object MediaMux {
    /** True when this device needs Media3's reader and writer for an MP4 join (ADR-022). */
    fun useMedia3(sdk: Int = Build.VERSION.SDK_INT, webm: Boolean): Boolean = !webm && sdk < Build.VERSION_CODES.P

    /** The few MediaExtractor calls the join needs, so both readers can drive it. */
    private interface Reader {
        val trackCount: Int
        fun format(i: Int): MediaFormat
        fun select(i: Int)
        fun read(buffer: ByteBuffer): Int
        val time: Long
        val isSync: Boolean
        fun advance()
        fun release()
    }

    private class PlatformReader(path: String) : Reader {
        private val ex = MediaExtractor()
        init { try { ex.setDataSource(path) } catch (t: Throwable) { ex.release(); throw t } }
        override val trackCount get() = ex.trackCount
        override fun format(i: Int): MediaFormat = ex.getTrackFormat(i)
        override fun select(i: Int) = ex.selectTrack(i)
        override fun read(buffer: ByteBuffer) = ex.readSampleData(buffer, 0)
        override val time get() = ex.sampleTime
        override val isSync get() = ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
        override fun advance() { ex.advance() }
        override fun release() = ex.release()
    }

    private class Media3Reader(path: String) : Reader {
        private val ex = MediaExtractorCompat(DefaultExtractorsFactory(), FileDataSource.Factory())
        init { try { ex.setDataSource(path) } catch (t: Throwable) { ex.release(); throw t } }
        override val trackCount get() = ex.trackCount
        override fun format(i: Int): MediaFormat = ex.getTrackFormat(i)
        override fun select(i: Int) = ex.selectTrack(i)
        override fun read(buffer: ByteBuffer) = ex.readSampleData(buffer, 0)
        override val time get() = ex.sampleTime
        override val isSync get() = ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
        override fun advance() { ex.advance() }
        override fun release() = ex.release()
    }

    /** The few MediaMuxer calls the join needs. */
    private interface Writer {
        fun addTrack(format: MediaFormat): Int
        fun start()
        fun write(track: Int, buffer: ByteBuffer, info: MediaCodec.BufferInfo)
        fun stop()
        fun release()
    }

    private class PlatformWriter(path: String, webm: Boolean) : Writer {
        private val m = MediaMuxer(path, if (webm) MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM else MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        override fun addTrack(format: MediaFormat) = m.addTrack(format)
        override fun start() = m.start()
        override fun write(track: Int, buffer: ByteBuffer, info: MediaCodec.BufferInfo) = m.writeSampleData(track, buffer, info)
        override fun stop() = m.stop()
        override fun release() = m.release()
    }

    private class Media3Writer(path: String) : Writer {
        // Sample batching is off by default, so each sample is written before writeSampleData
        // returns and the one shared buffer can be reused.
        private val m = MediaMuxerCompat(path, MediaMuxerCompat.OUTPUT_FORMAT_MP4)
        override fun addTrack(format: MediaFormat) = m.addTrack(format)
        override fun start() = m.start()
        override fun write(track: Int, buffer: ByteBuffer, info: MediaCodec.BufferInfo) = m.writeSampleData(track, buffer, info)
        override fun stop() = m.stop()
        override fun release() = m.release()
    }

    fun merge(video: File, audio: File, out: File, webm: Boolean, onProgress: (Float) -> Unit = {}) {
        out.delete()
        val media3 = useMedia3(webm = webm)
        val sources = ArrayList<Pair<Reader, MediaFormat>>(2)
        try {
            for ((f, prefix) in listOf(video to "video/", audio to "audio/")) {
                val ex: Reader = if (media3) Media3Reader(f.absolutePath) else PlatformReader(f.absolutePath)
                val track = (0 until ex.trackCount).firstOrNull { ex.format(it).getString(MediaFormat.KEY_MIME)?.startsWith(prefix) == true }
                    ?: run { ex.release(); throw IOException("No ${prefix.trimEnd('/')} track in ${f.name}") }
                ex.select(track)
                sources += ex to ex.format(track)
            }
        } catch (t: Throwable) {
            sources.forEach { runCatching { it.first.release() } }
            throw t
        }
        var muxer: Writer? = null
        try {
            val w: Writer = if (media3) Media3Writer(out.absolutePath) else PlatformWriter(out.absolutePath, webm)
            muxer = w
            val ids = sources.map { (_, format) -> w.addTrack(format) }
            val durationUs = sources.maxOf { (_, f) -> if (f.containsKey(MediaFormat.KEY_DURATION)) f.getLong(MediaFormat.KEY_DURATION) else 0L }
            w.start()
            val size = sources.maxOf { (_, f) -> if (f.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) f.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 0 }
                .coerceAtLeast(4 * 1024 * 1024)
            val buffer = ByteBuffer.allocateDirect(size)
            val info = MediaCodec.BufferInfo()
            val done = BooleanArray(2)
            var lastReport = 0L
            while (!done[0] || !done[1]) {
                // Next sample: the source whose timestamp is behind (keeps audio and video interleaved).
                val i = (0..1).filter { !done[it] }.minByOrNull { sources[it].first.time } ?: break
                val ex = sources[i].first
                buffer.clear()
                val n = ex.read(buffer)
                if (n < 0) { done[i] = true; continue }
                info.offset = 0; info.size = n
                info.presentationTimeUs = ex.time.coerceAtLeast(0)
                info.flags = if (ex.isSync) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                buffer.position(0); buffer.limit(n)
                w.write(ids[i], buffer, info)
                ex.advance()
                if (durationUs > 0 && info.presentationTimeUs - lastReport > 2_000_000) {
                    lastReport = info.presentationTimeUs
                    onProgress((info.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f))
                }
            }
            w.stop()
        } catch (t: Throwable) {
            out.delete()
            throw if (t is IOException) t else IOException("Couldn't join video and audio: ${t.message}", t)
        } finally {
            muxer?.let { m -> runCatching { m.release() } }
            sources.forEach { runCatching { it.first.release() } }
        }
    }

    /**
     * NF-2b: rewrites a one-file MPEG-TS download as MP4 (no re-encoding). Not supported yet: throws, so
     * callers keep the TS file.
     */
    fun remux(input: File, out: File) {
        throw IOException("TS to MP4 isn't supported yet (${input.name} -> ${out.name})")
    }

    /**
     * NF-2b: after a TS HLS download, turns [part] into MP4 when [enabled] and [freeBytes] allow it.
     * Returns the container of [part] afterwards ("mp4" or "ts"). Today TS stays TS.
     */
    fun tsToMp4(part: File, enabled: Boolean, freeBytes: Long): String = "ts"
}
