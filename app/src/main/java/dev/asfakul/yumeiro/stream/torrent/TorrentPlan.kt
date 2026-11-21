/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream.torrent

import java.net.URLDecoder
import java.net.URLEncoder

/** One file inside a torrent: [offset] is where it starts in the torrent's byte stream. */
data class TorrentFile(val index: Int, val path: String, val size: Long, val offset: Long, val pad: Boolean = false)

data class TorrentMeta(val hash: String, val name: String, val pieceLength: Int, val numPieces: Int, val files: List<TorrentFile>)

/**
 * B-31 / ADR-053: the pure decisions of the built-in engine (no native code, unit-tested): which file plays, which
 * pieces are urgent for a read position, how far to read ahead, the info-hash of a link and the extra trackers.
 */
object TorrentPlan {
    private val VIDEO = setOf("mkv", "mp4", "m4v", "webm", "avi", "mov", "ts", "m2ts", "wmv", "flv", "mpg", "mpeg", "3gp", "ogv")
    private val AUDIO = setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav")
    private val HEX40 = Regex("^[0-9a-fA-F]{40}$")
    private val B32 = Regex("^[A-Za-z2-7]{32}$")
    private val EPISODE = Regex("(?i)\\bS(\\d{1,2})\\s*E(\\d{1,3})\\b")

    /**
     * Public trackers added to every magnet (more peers sooner = faster start). Only open, no-login trackers;
     * private torrents ignore them (libtorrent never announces a private torrent elsewhere).
     */
    val TRACKERS = listOf(
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.demonii.com:1337/announce",
        "udp://open.stealth.si:80/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://exodus.desync.com:6969/announce",
        "udp://explodie.org:6969/announce",
        "udp://tracker.theoks.net:6969/announce",
        "udp://tracker.qu.ax:6969/announce",
        "udp://opentracker.io:6969/announce",
        "udp://tracker.dler.org:6969/announce",
        "https://tracker.opentrackr.org:443/announce",
        "http://tracker.opentrackr.org:1337/announce",
    )

    /** Lower-case hex v1 info-hash of a magnet (hex or base32 btih) or a bare hash; null otherwise. */
    fun infoHash(link: String): String? {
        val t = link.trim()
        val raw = if (t.startsWith("magnet:?", true)) {
            param(t, "xt").firstOrNull { it.startsWith("urn:btih:", true) }?.substring(9) ?: return null
        } else t.removePrefix("urn:btih:")
        return when {
            HEX40.matches(raw) -> raw.lowercase()
            B32.matches(raw) -> base32ToHex(raw.uppercase())
            else -> null
        }
    }

    /** BEP 53 `so=` (select only): the first file index asked for, if any. */
    fun selectOnly(link: String): Int? =
        param(link, "so").firstOrNull()?.split(',')?.firstOrNull()?.substringBefore('-')?.trim()?.toIntOrNull()

    /** [magnet] plus [TRACKERS] it doesn't list yet. */
    fun withTrackers(magnet: String, extra: List<String> = TRACKERS): String {
        val have = param(magnet, "tr").toSet()
        val sb = StringBuilder(magnet)
        for (tr in extra) if (tr !in have) sb.append("&tr=").append(URLEncoder.encode(tr, "UTF-8"))
        return sb.toString()
    }

    /**
     * The file to play: [selectOnly] when it names a real, non-pad file; else the video whose name has the episode
     * in [title] (S01E02); else the largest video; else the largest audio; else the largest file.
     */
    fun pickFile(files: List<TorrentFile>, selectOnly: Int? = null, title: String? = null): TorrentFile? {
        val real = files.filter { !it.pad && it.size > 0 }
        if (real.isEmpty()) return null
        selectOnly?.let { i -> real.firstOrNull { it.index == i }?.let { return it } }
        val videos = real.filter { ext(it.path) in VIDEO && !isSample(it.path) }.ifEmpty { real.filter { ext(it.path) in VIDEO } }
        title?.let { EPISODE.find(it) }?.let { m ->
            val s = m.groupValues[1].toInt(); val e = m.groupValues[2].toInt()
            videos.filter { f -> EPISODE.findAll(f.path).any { it.groupValues[1].toInt() == s && it.groupValues[2].toInt() == e } }
                .maxByOrNull { it.size }?.let { return it }
        }
        return videos.maxByOrNull { it.size }
            ?: real.filter { ext(it.path) in AUDIO }.maxByOrNull { it.size }
            ?: real.maxByOrNull { it.size }
    }

    fun mimeFor(path: String): String = when (ext(path)) {
        "mkv" -> "video/x-matroska"; "mp4", "m4v" -> "video/mp4"; "webm" -> "video/webm"; "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"; "ts", "m2ts" -> "video/mp2t"; "wmv" -> "video/x-ms-wmv"; "flv" -> "video/x-flv"
        "mpg", "mpeg" -> "video/mpeg"; "3gp" -> "video/3gpp"; "ogv" -> "video/ogg"
        "mp3" -> "audio/mpeg"; "flac" -> "audio/flac"; "m4a", "aac" -> "audio/mp4"; "ogg", "opus" -> "audio/ogg"; "wav" -> "audio/wav"
        else -> "application/octet-stream"
    }

    /** Piece that holds byte [position] of [file]. */
    fun pieceAt(file: TorrentFile, position: Long, pieceLength: Int): Int = ((file.offset + position) / pieceLength).toInt()

    fun firstPiece(file: TorrentFile, pieceLength: Int): Int = (file.offset / pieceLength).toInt()
    fun lastPiece(file: TorrentFile, pieceLength: Int): Int = ((file.offset + file.size - 1).coerceAtLeast(file.offset) / pieceLength).toInt()

    /** Bytes to fetch ahead of the play position: about 1-2 minutes of 1080p; less on a 2 GB phone or TV. */
    fun readAheadBytes(lowEnd: Boolean): Long = if (lowEnd) 24L shl 20 else 64L shl 20

    /**
     * Deadlines (piece, ms) for a read at [piece]: the piece itself now, then each next piece 120 ms later, up to
     * [readAhead] bytes (at least 4 pieces), never past the file's last piece. libtorrent asks the fastest peers
     * for the earliest deadlines, so playback start and seeks wait for the few pieces they need, not the swarm.
     */
    fun deadlines(piece: Int, lastPiece: Int, pieceLength: Int, readAhead: Long, seekGapMs: Int = 0): List<Pair<Int, Int>> {
        val count = maxOf(4L, readAhead / pieceLength.coerceAtLeast(1)).toInt()
        val end = minOf(lastPiece, piece + count - 1)
        // B31-SEEK2: after a seek the target piece is alone at the front; the read-ahead starts [seekGapMs] later so
        // the peers' first requests all go to the piece the player waits for.
        return (piece..end).mapIndexed { i, p -> p to if (i == 0) 0 else seekGapMs + i * 120 }
    }

    /**
     * B31-SEEK3 (device runs 44883372732 / 44883375160): piece priorities for a stream window. Before, the whole file
     * kept libtorrent's default priority 4, so rarest-first pieces anywhere in a 5.8 GB file filled every peer's
     * request queue; libtorrent's time-critical picker skips peers with too many outstanding requests
     * (https://libtorrent.org/streaming.html), and after a seek no block of the target piece arrived for 7 s at
     * 2 MB/s (17 s on TV). Now only the window is wanted: the target 7, the next piece 6, the rest of the window 5,
     * [keep] (container head / tail) 2, everything else 0. Same gradient idea as libtorrent_flutter (NOW 7 / NEXT 6 /
     * READAHEAD 5 / SKIP 0, https://pub.dev/packages/libtorrent_flutter/versions/1.9.9/changelog) and TorrServer's
     * reader tiers; our own code. One byte per piece of the torrent.
     */
    fun windowPriorities(numPieces: Int, window: List<Int>, keep: Collection<Int>): ByteArray {
        val p = ByteArray(numPieces.coerceAtLeast(0))
        for (k in keep) if (k in p.indices) p[k] = 2
        window.forEachIndexed { i, w -> if (w in p.indices) p[w] = when (i) { 0 -> 7; 1 -> 6; else -> 5 } }
        return p
    }

    /** B31-SEEK2: read-ahead offset after a seek (see [deadlines]). */
    const val SEEK_GAP_MS = 500

    /**
     * Pieces asked for at once when a file opens: the first and last ~2 MB (MP4 `moov` and MKV cues often sit at
     * the end; players read them before the first frame).
     */
    fun headTail(file: TorrentFile, pieceLength: Int, edge: Long = 2L shl 20): List<Int> {
        val first = firstPiece(file, pieceLength); val last = lastPiece(file, pieceLength)
        val n = maxOf(1L, (edge + pieceLength - 1) / pieceLength).toInt()
        return ((first until minOf(last + 1, first + n)) + (maxOf(first, last - n + 1)..last)).distinct()
    }

    /** libtorrent's block size. */
    const val BLOCK = 16 * 1024L

    /**
     * B-31 speed, pure: bytes readable from offset [inPiece] of a partial piece whose finished blocks are
     * [finished] (contiguous finished blocks from there, capped at [pieceLength]); 0 = wait.
     */
    fun readableInPiece(finished: java.util.BitSet?, inPiece: Long, pieceLength: Long): Long {
        if (finished == null || inPiece < 0 || inPiece >= pieceLength) return 0
        val b0 = (inPiece / BLOCK).toInt()
        if (!finished[b0]) return 0
        val end = finished.nextClearBit(b0).toLong() * BLOCK
        return minOf(end, pieceLength) - inPiece
    }

    /** Is [position] still inside the window last asked for (no need to send new deadlines)? */
    fun insideWindow(piece: Int, windowStart: Int, windowEnd: Int, lastPiece: Int = Int.MAX_VALUE): Boolean =
        windowStart >= 0 && piece >= windowStart &&
            // B31-SEEK-T (CI 44882251682): a window cut short by the file's end already holds everything left, so
            // reading on inside it must not re-focus (that dropped the deadlines of the piece being played).
            (piece <= windowStart + (windowEnd - windowStart) / 2 || (windowEnd >= lastPiece && piece <= windowEnd))

    private fun isSample(path: String): Boolean = path.substringAfterLast('/').contains("sample", true)
    private fun ext(path: String): String = path.substringAfterLast('/').substringAfterLast('.', "").lowercase()

    private fun param(link: String, key: String): List<String> =
        link.substringAfter('?', "").split('&').mapNotNull { kv ->
            val k = kv.substringBefore('=')
            if (k.equals(key, true) || k.startsWith("$key.", true)) runCatching { URLDecoder.decode(kv.substringAfter('=', ""), "UTF-8") }.getOrNull() else null
        }

    private fun base32ToHex(s: String): String? {
        val alpha = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var buffer = 0L; var bits = 0
        val out = StringBuilder(40)
        for (ch in s) {
            val v = alpha.indexOf(ch); if (v < 0) return null
            buffer = (buffer shl 5) or v.toLong(); bits += 5
            while (bits >= 8) { bits -= 8; out.append("%02x".format(((buffer shr bits) and 0xff).toInt())) }
        }
        return out.toString().takeIf { it.length == 40 }
    }
}
