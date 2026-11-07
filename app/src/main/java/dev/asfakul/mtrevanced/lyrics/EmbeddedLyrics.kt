/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.mtrevanced.lyrics

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.charset.Charset

/**
 * Reads lyrics embedded in audio tags without third-party libraries:
 *  - ID3v2.3/2.4 (MP3, also some WAV/AIFF): `USLT` (plain/LRC text) and `SYLT` (synced)
 *  - FLAC Vorbis comments: `LYRICS`, `UNSYNCEDLYRICS`, `SYNCEDLYRICS`
 *  - OGG Vorbis / Opus comment headers (first pages)
 *  - MP4 / M4A: `moov/udta/meta/ilst/©lyr`
 * Every parser is bounds-checked and returns null on anything unexpected (never throws).
 */
object EmbeddedLyrics {
    private const val MAX_TAG = 16 * 1024 * 1024

    fun read(channel: FileChannel): String? = try {
        val head = ByteArray(12)
        readAt(channel, 0, head)
        when {
            head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() && head[2] == '3'.code.toByte() -> readId3(channel)
            head.ascii(0, 4) == "fLaC" -> readFlac(channel)
            head.ascii(0, 4) == "OggS" -> readOgg(channel)
            head.ascii(4, 4) == "ftyp" -> readMp4(channel)
            else -> null
        }
    } catch (_: Throwable) { null }

    // ---- ID3v2 -------------------------------------------------------------------------------
    fun readId3(channel: FileChannel): String? {
        val h = ByteArray(10); readAt(channel, 0, h)
        val major = h[3].toInt()
        if (major !in 2..4) return null
        val flags = h[5].toInt()
        val size = syncsafe(h, 6)
        if (size <= 0 || size > MAX_TAG) return null
        var tag = ByteArray(size); readAt(channel, 10, tag)
        if (flags and 0x80 != 0 && major < 4) tag = deUnsync(tag)
        return parseId3Frames(tag, major)
    }

    fun parseId3Frames(tag: ByteArray, major: Int): String? {
        var p = 0
        var uslt: String? = null
        var sylt: String? = null
        val idLen = if (major == 2) 3 else 4
        val hdrLen = if (major == 2) 6 else 10
        while (p + hdrLen <= tag.size) {
            val id = tag.ascii(p, idLen)
            if (id.isBlank() || id[0] == '\u0000') break
            val fsize = when (major) {
                2 -> ((tag[p + 3].u() shl 16) or (tag[p + 4].u() shl 8) or tag[p + 5].u())
                4 -> syncsafe(tag, p + 4)
                else -> ((tag[p + 4].u() shl 24) or (tag[p + 5].u() shl 16) or (tag[p + 6].u() shl 8) or tag[p + 7].u())
            }
            val start = p + hdrLen
            if (fsize <= 0 || start + fsize > tag.size) break
            val body = tag.copyOfRange(start, start + fsize)
            when (id) {
                "USLT", "ULT" -> if (uslt == null) uslt = parseUslt(body)
                "SYLT", "SLT" -> if (sylt == null) sylt = parseSylt(body)
            }
            p = start + fsize
        }
        return sylt?.takeIf { it.isNotBlank() } ?: uslt?.takeIf { it.isNotBlank() }
    }

    private fun parseUslt(b: ByteArray): String? {
        if (b.size < 5) return null
        val enc = b[0].toInt()
        var p = 4 // encoding + 3-byte language
        p = skipTerminated(b, p, enc)
        if (p >= b.size) return null
        return decode(b, p, b.size - p, enc).trim('\u0000').trim()
    }

    /** SYLT → LRC text so the shared parser handles it. Only millisecond timestamps (format 2). */
    private fun parseSylt(b: ByteArray): String? {
        if (b.size < 7) return null
        val enc = b[0].toInt()
        val format = b[4].toInt()
        if (format != 2) return null
        var p = skipTerminated(b, 6, enc)
        val sb = StringBuilder()
        while (p < b.size) {
            val end = findTerminator(b, p, enc)
            if (end < 0 || end + termLen(enc) + 4 > b.size) break
            val text = decode(b, p, end - p, enc).trim('\n', '\r', '\u0000')
            val t = end + termLen(enc)
            val ms = (b[t].u().toLong() shl 24) or (b[t + 1].u().toLong() shl 16) or (b[t + 2].u().toLong() shl 8) or b[t + 3].u().toLong()
            val m = ms / 60000; val s = (ms / 1000) % 60; val cs = (ms % 1000) / 10
            sb.append(String.format(java.util.Locale.US, "[%02d:%02d.%02d]%s\n", m, s, cs, text))
            p = t + 4
        }
        return sb.toString().ifBlank { null }
    }

    // ---- FLAC --------------------------------------------------------------------------------
    fun readFlac(channel: FileChannel): String? {
        var pos = 4L
        repeat(64) {
            val h = ByteArray(4); readAt(channel, pos, h)
            val last = h[0].toInt() and 0x80 != 0
            val type = h[0].toInt() and 0x7F
            val len = (h[1].u() shl 16) or (h[2].u() shl 8) or h[3].u()
            if (type == 4) {
                if (len <= 0 || len > MAX_TAG) return null
                val body = ByteArray(len); readAt(channel, pos + 4, body)
                return parseVorbisComments(body, 0)
            }
            if (last) return null
            pos += 4 + len
        }
        return null
    }

    /** Vorbis comment block (little-endian lengths) starting at [offset]. */
    fun parseVorbisComments(b: ByteArray, offset: Int): String? {
        val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        var p = offset
        if (p + 4 > b.size) return null
        val vendorLen = bb.getInt(p); p += 4
        if (vendorLen < 0 || p + vendorLen > b.size) return null
        p += vendorLen
        if (p + 4 > b.size) return null
        val count = bb.getInt(p); p += 4
        var synced: String? = null
        var plain: String? = null
        for (i in 0 until count.coerceAtMost(10_000)) {
            if (p + 4 > b.size) break
            val l = bb.getInt(p); p += 4
            if (l < 0 || p + l > b.size) break
            val kv = String(b, p, l, Charsets.UTF_8); p += l
            val eq = kv.indexOf('=')
            if (eq <= 0) continue
            when (kv.substring(0, eq).uppercase()) {
                "SYNCEDLYRICS" -> synced = kv.substring(eq + 1)
                "LYRICS", "UNSYNCEDLYRICS", "UNSYNCED LYRICS" -> if (plain == null) plain = kv.substring(eq + 1)
            }
        }
        return synced?.takeIf { it.isNotBlank() } ?: plain?.takeIf { it.isNotBlank() }
    }

    // ---- OGG (Vorbis / Opus) -----------------------------------------------------------------
    private fun readOgg(channel: FileChannel): String? {
        val n = minOf(channel.size(), 2L * 1024 * 1024).toInt()
        val buf = ByteArray(n); readAt(channel, 0, buf)
        // Concatenate page payloads so a comment header split over pages is still contiguous.
        val payload = java.io.ByteArrayOutputStream()
        var p = 0
        while (p + 27 <= n && buf.ascii(p, 4) == "OggS") {
            val segs = buf[p + 26].u()
            if (p + 27 + segs > n) break
            var len = 0
            for (i in 0 until segs) len += buf[p + 27 + i].u()
            val start = p + 27 + segs
            if (start + len > n) break
            payload.write(buf, start, len)
            p = start + len
        }
        val data = payload.toByteArray()
        indexOf(data, "\u0003vorbis".toByteArray(Charsets.ISO_8859_1))?.let { return parseVorbisComments(data, it + 7) }
        indexOf(data, "OpusTags".toByteArray(Charsets.ISO_8859_1))?.let { return parseVorbisComments(data, it + 8) }
        return null
    }

    // ---- MP4 / M4A ---------------------------------------------------------------------------
    private fun readMp4(channel: FileChannel): String? {
        val moov = findAtom(channel, 0, channel.size(), "moov") ?: return null
        val udta = findAtom(channel, moov.first, moov.second, "udta") ?: return null
        val meta = findAtom(channel, udta.first, udta.second, "meta") ?: return null
        val ilst = findAtom(channel, meta.first + 4, meta.second, "ilst") ?: return null // meta has 4-byte version/flags
        val lyr = findAtom(channel, ilst.first, ilst.second, "\u00A9lyr") ?: return null
        val data = findAtom(channel, lyr.first, lyr.second, "data") ?: return null
        val len = (data.second - data.first - 8).toInt()
        if (len <= 0 || len > MAX_TAG) return null
        val b = ByteArray(len); readAt(channel, data.first + 8, b)
        return String(b, Charsets.UTF_8).ifBlank { null }
    }

    /** Returns (contentStart, end) of the first child atom [type] within [start, end). */
    private fun findAtom(channel: FileChannel, start: Long, end: Long, type: String): Pair<Long, Long>? {
        var p = start
        val h = ByteArray(16)
        var guard = 0
        while (p + 8 <= end && guard++ < 4096) {
            readAt(channel, p, h, 8)
            var size = ((h[0].u().toLong() shl 24) or (h[1].u().toLong() shl 16) or (h[2].u().toLong() shl 8) or h[3].u().toLong())
            val t = String(h, 4, 4, Charsets.ISO_8859_1)
            var header = 8L
            if (size == 1L) {
                readAt(channel, p + 8, h, 8)
                size = ByteBuffer.wrap(h, 0, 8).long
                header = 16L
            } else if (size == 0L) size = end - p
            if (size < header || p + size > end) return null
            if (t == type) return (p + header) to (p + size)
            p += size
        }
        return null
    }

    // ---- helpers -----------------------------------------------------------------------------
    private fun readAt(ch: FileChannel, pos: Long, dst: ByteArray, len: Int = dst.size) {
        val bb = ByteBuffer.wrap(dst, 0, len)
        var p = pos
        while (bb.hasRemaining()) {
            val r = ch.read(bb, p)
            if (r <= 0) break
            p += r
        }
    }

    private fun syncsafe(b: ByteArray, o: Int) =
        ((b[o].u() and 0x7F) shl 21) or ((b[o + 1].u() and 0x7F) shl 14) or ((b[o + 2].u() and 0x7F) shl 7) or (b[o + 3].u() and 0x7F)

    private fun deUnsync(b: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream(b.size)
        var i = 0
        while (i < b.size) {
            out.write(b[i].toInt())
            if (b[i] == 0xFF.toByte() && i + 1 < b.size && b[i + 1] == 0.toByte()) i++
            i++
        }
        return out.toByteArray()
    }

    private fun termLen(enc: Int) = if (enc == 1 || enc == 2) 2 else 1

    private fun findTerminator(b: ByteArray, from: Int, enc: Int): Int {
        if (termLen(enc) == 1) {
            for (i in from until b.size) if (b[i] == 0.toByte()) return i
        } else {
            var i = from
            while (i + 1 < b.size) { if (b[i] == 0.toByte() && b[i + 1] == 0.toByte()) return i; i += 2 }
        }
        return -1
    }

    private fun skipTerminated(b: ByteArray, from: Int, enc: Int): Int {
        val e = findTerminator(b, from, enc)
        return if (e < 0) b.size else e + termLen(enc)
    }

    private fun decode(b: ByteArray, off: Int, len: Int, enc: Int): String {
        if (len <= 0) return ""
        val cs: Charset = when (enc) {
            1 -> Charsets.UTF_16 // with BOM
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.ISO_8859_1
        }
        return String(b, off, len, cs)
    }

    private fun indexOf(data: ByteArray, pat: ByteArray): Int? {
        outer@ for (i in 0..data.size - pat.size) {
            for (j in pat.indices) if (data[i + j] != pat[j]) continue@outer
            return i
        }
        return null
    }

    private fun Byte.u() = toInt() and 0xFF
    private fun ByteArray.ascii(o: Int, n: Int) = if (o + n <= size) String(this, o, n, Charsets.ISO_8859_1) else ""
}
