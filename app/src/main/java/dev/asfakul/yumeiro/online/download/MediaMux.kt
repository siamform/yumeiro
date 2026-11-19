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
     * NF-2b-3: rewrites a one-file MPEG-TS download as MP4 without re-encoding (fast, lossless; galleries,
     * TVs and other players seek in it). Media3's reader and writer on every Android version (ADR-022:
     * pure Java, B-frames and Annex-B H.264 handled, same code on TV api24 and phones). Every video and
     * audio track is copied in file order; the video starts at its first key frame. Throws when the input
     * can't be read or a codec can't go into MP4 (for example AC-3 audio); [out] is then removed and the
     * caller keeps the TS file. [keepGoing] is checked every few hundred samples (a pause or delete).
     */
    fun remux(input: File, out: File, keepGoing: () -> Boolean = { true }) {
        out.delete()
        val ex = MediaExtractorCompat(DefaultExtractorsFactory(), FileDataSource.Factory())
        var muxer: MediaMuxerCompat? = null
        var ok = false
        try {
            ex.setDataSource(input.absolutePath)
            val w = MediaMuxerCompat(out.absolutePath, MediaMuxerCompat.OUTPUT_FORMAT_MP4).also { muxer = it }
            val ids = HashMap<Int, Int>()
            var video = -1
            var maxSize = 0
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                val isVideo = mime.startsWith("video/")
                if (!isVideo && !mime.startsWith("audio/")) continue // ID3 / captions stay out
                if (isVideo && video >= 0) continue // one picture track
                ids[i] = w.addTrack(f)
                ex.selectTrack(i)
                if (isVideo) video = i
                if (f.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) maxSize = maxOf(maxSize, f.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
            }
            if (ids.isEmpty()) throw IOException("No video or audio track in ${input.name}")
            w.start()
            val buffer = ByteBuffer.allocateDirect(maxSize.coerceAtLeast(4 * 1024 * 1024))
            val info = MediaCodec.BufferInfo()
            var videoStarted = video < 0
            var samples = 0
            while (true) {
                val track = ex.sampleTrackIndex
                if (track < 0) break
                buffer.clear()
                val n = ex.readSampleData(buffer, 0)
                if (n < 0) break
                val key = ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
                if (track == video && !videoStarted) { if (key) videoStarted = true else { ex.advance(); continue } }
                val id = ids[track]
                if (id != null && n > 0) {
                    info.set(0, n, ex.sampleTime, if (key) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                    buffer.position(0); buffer.limit(n)
                    w.writeSampleData(id, buffer, info)
                    samples++
                    if (samples % 400 == 0 && !keepGoing()) throw kotlinx.coroutines.CancellationException("Remux stopped")
                }
                ex.advance()
            }
            if (samples == 0 || !videoStarted) throw IOException("No samples to convert in ${input.name}")
            w.stop()
            ok = true
        } catch (t: Throwable) {
            throw if (t is IOException || t is kotlinx.coroutines.CancellationException) t else IOException("Couldn't convert to MP4: ${t.message}", t)
        } finally {
            muxer?.let { m -> runCatching { m.release() } }
            runCatching { ex.release() }
            if (!ok) out.delete()
        }
    }

    /**
     * NF-2b-4: after a TS HLS download, turns [part] into MP4 when [enabled] (kill switch `dl_ts_remux`)
     * and the free space ([freeBytes], below 0 = unknown) holds a second copy. Writes `part.remux`, then
     * replaces [part]. Returns the container of [part] afterwards: "mp4", or "ts" when it was kept as it
     * was (switch off, no space, or the remux failed). A pause or delete during it is rethrown.
     */
    fun tsToMp4(part: File, enabled: Boolean, freeBytes: Long, keepGoing: () -> Boolean = { true }): String {
        if (!enabled || !part.isFile) return "ts"
        if (freeBytes in 0 until part.length() + DiskFull.MARGIN) return "ts"
        val tmp = File(part.parentFile, part.name + ".remux")
        return try {
            remux(part, tmp, keepGoing)
            if (!tmp.renameTo(part)) { tmp.copyTo(part, overwrite = true); tmp.delete() }
            "mp4"
        } catch (e: kotlinx.coroutines.CancellationException) {
            tmp.delete(); throw e
        } catch (e: Throwable) {
            android.util.Log.w("MediaMux", "TS to MP4 failed, keeping TS: ${e.message}")
            tmp.delete(); "ts"
        }
    }
}
