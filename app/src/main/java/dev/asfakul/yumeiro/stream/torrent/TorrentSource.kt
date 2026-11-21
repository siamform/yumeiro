/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream.torrent

import dev.asfakul.yumeiro.stream.local.ByteSource
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * B-31 / ADR-053: one file of a torrent as a [ByteSource] for the app's loopback server. A read at a piece the
 * engine doesn't have yet sends deadlines for that piece and the read-ahead after it (only when the position left
 * the last window, so steady playback sends few requests), then sleeps on [TorrentCore.signal] until a
 * piece finishes (event-driven, no timer polling). Bytes are read straight from libtorrent's file on disk.
 * Runs on the local server's threads, never the main thread.
 */
class TorrentSource(
    private val core: TorrentCore,
    private val meta: TorrentMeta,
    private val file: TorrentFile,
    private val readAhead: Long,
    private val waitMs: Long = 60_000,
    /** Called on every read, so the stream manager knows the torrent is in use. */
    private val touched: () -> Unit = {},
) : ByteSource {
    override val length: Long = file.size
    override val mimeType: String = TorrentPlan.mimeFor(file.path)
    private val last = TorrentPlan.lastPiece(file, meta.pieceLength)
    private val guard = Any()
    private var raf: RandomAccessFile? = null
    @Volatile private var windowStart = -1
    @Volatile private var windowEnd = -1

    /** First and last pieces at once (container headers / index), and the start of the file. */
    fun prime() {
        val head = TorrentPlan.headTail(file, meta.pieceLength).map { it to 0 }
        core.setDeadlines(meta.hash, head)
        focus(TorrentPlan.firstPiece(file, meta.pieceLength))
    }

    override fun read(position: Long, buffer: ByteArray, offset: Int, count: Int): Int {
        if (position >= length) return -1
        touched()
        val piece = TorrentPlan.pieceAt(file, position, meta.pieceLength)
        if (!TorrentPlan.insideWindow(piece, windowStart, windowEnd)) focus(piece)
        if (!core.have(meta.hash, piece)) {
            val deadline = System.currentTimeMillis() + waitMs
            synchronized(core.signal) {
                while (!core.have(meta.hash, piece)) {
                    core.error(meta.hash)?.let { throw IOException("Torrent error: $it") }
                    val left = deadline - System.currentTimeMillis()
                    if (left <= 0) throw IOException("No peer sent this part in time")
                    core.signal.wait(minOf(left, 1_000))
                }
            }
        }
        // Up to the end of this piece (the next one may not be here yet).
        val pieceEnd = (piece + 1).toLong() * meta.pieceLength - file.offset
        val n = minOf(count.toLong(), pieceEnd - position, length - position).toInt()
        return readFile(position, buffer, offset, n)
    }

    private fun focus(piece: Int) {
        val d = TorrentPlan.deadlines(piece, last, meta.pieceLength, readAhead)
        windowStart = piece; windowEnd = d.lastOrNull()?.first ?: piece
        core.setDeadlines(meta.hash, d)
    }

    private fun readFile(position: Long, buffer: ByteArray, offset: Int, count: Int): Int = synchronized(guard) {
        val r = raf ?: run {
            val dir = core.savePath(meta.hash) ?: throw IOException("Torrent was removed")
            RandomAccessFile(File(dir, file.path), "r").also { raf = it }
        }
        r.seek(position)
        val n = r.read(buffer, offset, count)
        if (n < 0) throw IOException("Torrent file is shorter than expected")
        n
    }

    fun close() { synchronized(guard) { runCatching { raf?.close() }; raf = null } }
}
