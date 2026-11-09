/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Consumer
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.text.ssa.SsaParser
import androidx.media3.extractor.text.subrip.SubripParser
import androidx.media3.extractor.text.webvtt.WebvttParser
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Collections

/** What a subtitle file really is, judged by its content (sites mislabel SRT as VTT and vice versa). */
enum class SubtitleKind { WEBVTT, SUBRIP, SSA, VTT_NO_HEADER, UNKNOWN }

object SubtitleSniffer {
    private val SRT_TIME = Regex("\\d{1,2}:\\d{2}:\\d{2},\\d{1,3}\\s*-->")
    private val DOT_TIME = Regex("(\\d{1,2}:)?\\d{2}:\\d{2}\\.\\d{1,3}\\s*-->")

    /** Pure → unit tested. */
    fun kind(text: String): SubtitleKind {
        val t = text.removePrefix("\uFEFF").trimStart()
        return when {
            t.startsWith("WEBVTT") -> SubtitleKind.WEBVTT
            t.startsWith("[Script Info]", ignoreCase = true) || t.contains("\nDialogue:") || t.contains("[Events]") -> SubtitleKind.SSA
            SRT_TIME.containsMatchIn(t.take(4096)) -> SubtitleKind.SUBRIP
            DOT_TIME.containsMatchIn(t.take(4096)) -> SubtitleKind.VTT_NO_HEADER
            else -> SubtitleKind.UNKNOWN
        }
    }

    /** Bytes ready for the parser chosen for [kind]. */
    fun prepare(bytes: ByteArray, offset: Int, length: Int, kind: SubtitleKind): ByteArray {
        var b = bytes.copyOfRange(offset, offset + length)
        // UTF-16 files (some old SRTs) → UTF-8.
        if (b.size >= 2 && ((b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte()) || (b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte()))) {
            b = String(b, Charsets.UTF_16).toByteArray(Charsets.UTF_8)
        }
        if (kind == SubtitleKind.VTT_NO_HEADER) {
            // "1\n00:00:01.000 --> …" style: valid WebVTT once it has the header.
            b = "WEBVTT\n\n".toByteArray() + b.toString(Charsets.UTF_8).removePrefix("\uFEFF").toByteArray()
        }
        return b
    }

    fun decode(bytes: ByteArray, offset: Int, length: Int): String {
        val head = bytes.copyOfRange(offset, offset + minOf(length, 8192))
        return if (head.size >= 2 && ((head[0] == 0xFF.toByte() && head[1] == 0xFE.toByte()) || (head[0] == 0xFE.toByte() && head[1] == 0xFF.toByte())))
            String(head, Charsets.UTF_16) else String(head, Charsets.UTF_8)
    }
}

/** User subtitle delay, applied when cues are parsed (positive = later). */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
object SubtitleTiming {
    @Volatile var offsetUs: Long = 0

    /** Pure → unit tested. */
    fun shift(c: CuesWithTiming, offsetUs: Long): CuesWithTiming =
        if (offsetUs == 0L || c.startTimeUs == C.TIME_UNSET) c
        else CuesWithTiming(c.cues, (c.startTimeUs + offsetUs).coerceAtLeast(0), c.durationUs)
}

/**
 * Picks the parser by content instead of trusting the declared type: an SRT served as "vtt" (or
 * without an extension, which defaulted to VTT) showed no subtitles at all. Applies the subtitle
 * delay. Other formats go to Media3's default parsers.
 */
@OptIn(UnstableApi::class)
class SniffingSubtitleParserFactory(private val fallback: SubtitleParser.Factory = DefaultSubtitleParserFactory()) : SubtitleParser.Factory {
    private val sniffable = setOf(MimeTypes.TEXT_VTT, MimeTypes.APPLICATION_SUBRIP, MimeTypes.TEXT_SSA, MimeTypes.TEXT_UNKNOWN, "text/plain", "application/x-subtitle")

    private fun sniffs(format: Format) = format.sampleMimeType in sniffable || format.containerMimeType in sniffable

    override fun supportsFormat(format: Format): Boolean = sniffs(format) || fallback.supportsFormat(format)

    override fun getCueReplacementBehavior(format: Format): Int =
        if (sniffs(format)) Format.CUE_REPLACEMENT_BEHAVIOR_MERGE else fallback.getCueReplacementBehavior(format)

    override fun create(format: Format): SubtitleParser {
        if (!sniffs(format)) return OffsetParser(fallback.create(format))
        val declared = runCatching { if (fallback.supportsFormat(format)) fallback.create(format) else null }.getOrNull()
        return SniffingParser(format, declared)
    }

    private class OffsetParser(private val inner: SubtitleParser) : SubtitleParser {
        override fun parse(data: ByteArray, offset: Int, length: Int, outputOptions: SubtitleParser.OutputOptions, output: Consumer<CuesWithTiming>) {
            val shift = SubtitleTiming.offsetUs
            inner.parse(data, offset, length, outputOptions) { output.accept(SubtitleTiming.shift(it, shift)) }
        }
        override fun reset() = inner.reset()
        override fun getCueReplacementBehavior(): Int = inner.cueReplacementBehavior
    }

    private class SniffingParser(private val format: Format, private val declared: SubtitleParser?) : SubtitleParser {
        override fun parse(data: ByteArray, offset: Int, length: Int, outputOptions: SubtitleParser.OutputOptions, output: Consumer<CuesWithTiming>) {
            val kind = SubtitleSniffer.kind(SubtitleSniffer.decode(data, offset, length))
            val parser: SubtitleParser = when (kind) {
                SubtitleKind.WEBVTT, SubtitleKind.VTT_NO_HEADER -> WebvttParser()
                SubtitleKind.SUBRIP -> SubripParser()
                SubtitleKind.SSA -> if (format.initializationData.isNotEmpty()) SsaParser(format.initializationData) else SsaParser()
                SubtitleKind.UNKNOWN -> declared ?: return
            }
            val bytes = SubtitleSniffer.prepare(data, offset, length, kind)
            val shift = SubtitleTiming.offsetUs
            runCatching { parser.parse(bytes, 0, bytes.size, outputOptions) { output.accept(SubtitleTiming.shift(it, shift)) } }
                .onFailure { android.util.Log.w("Subtitles", "couldn't parse ${format.sampleMimeType} as $kind", it) }
        }
        override fun getCueReplacementBehavior(): Int = Format.CUE_REPLACEMENT_BEHAVIOR_MERGE
    }
}

/**
 * Sideloaded subtitle files are read whole and, when the download fails, replaced by an empty
 * subtitle: one dead subtitle link stopped the whole video with a playback error.
 */
@OptIn(UnstableApi::class)
class SafeSubtitleDataSource(private val upstream: DataSource) : DataSource {
    class Factory(private val upstream: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = SafeSubtitleDataSource(upstream.createDataSource())
    }

    private var data: ByteArray? = null
    private var pos = 0
    private var uri: Uri? = null

    override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        if (!isSubtitle(dataSpec.uri)) return upstream.open(dataSpec)
        uri = dataSpec.uri
        val bytes = try {
            upstream.open(dataSpec)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            while (true) {
                val n = upstream.read(buf, 0, buf.size)
                if (n == C.RESULT_END_OF_INPUT) break
                out.write(buf, 0, n)
                if (out.size() > MAX_BYTES) break
            }
            out.toByteArray()
        } catch (e: IOException) {
            android.util.Log.w("Subtitles", "subtitle download failed — showing none: ${e.message}")
            EMPTY
        } finally { runCatching { upstream.close() } }
        data = if (bytes.isEmpty()) EMPTY else bytes
        pos = 0
        return data!!.size.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val d = data ?: return upstream.read(buffer, offset, length)
        if (length == 0) return 0
        if (pos >= d.size) return C.RESULT_END_OF_INPUT
        val n = minOf(length, d.size - pos)
        System.arraycopy(d, pos, buffer, offset, n); pos += n
        return n
    }

    override fun getUri(): Uri? = if (data != null) uri else upstream.uri
    override fun getResponseHeaders(): Map<String, List<String>> = if (data != null) emptyMap() else upstream.responseHeaders
    override fun close() { if (data != null) { data = null; uri = null } else upstream.close() }

    companion object {
        private const val MAX_BYTES = 8 * 1024 * 1024
        private val EMPTY = "WEBVTT\n\n".toByteArray()
        private val registered: MutableSet<String> = Collections.synchronizedSet(LinkedHashSet())

        /** Marks [uri] as a sideloaded subtitle file. */
        fun register(uri: Uri) { if (registered.size > 500) registered.clear(); registered += uri.toString() }
        fun isSubtitle(uri: Uri): Boolean = uri.toString() in registered
    }
}
