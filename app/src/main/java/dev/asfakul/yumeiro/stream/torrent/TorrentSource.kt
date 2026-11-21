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
        // B-31 speed: the start first (deadline 0 via focus), the end right after it, so they don't split peers.
        val first = TorrentPlan.firstPiece(file, meta.pieceLength)
        val tail = TorrentPlan.headTail(file, meta.pieceLength).filter { it != first }.map { it to TAIL_DEADLINE_MS }
        focus(first)
        core.setDeadlines(meta.hash, tail)
    }

    override fun read(position: Long, buffer: ByteArray, offset: Int, count: Int): Int {
        if (position >= length) return -1
        touched()
        val piece = TorrentPlan.pieceAt(file, position, meta.pieceLength)
        if (!TorrentPlan.insideWindow(piece, windowStart, windowEnd)) focus(piece)
        // Up to the end of this piece (the next one may not be here yet).
        val pieceEnd = (piece + 1).toLong() * meta.pieceLength - file.offset
        var upTo = pieceEnd
        if (!core.have(meta.hash, piece)) {
            // B-31 speed: blocks of a partial piece are on disk before the piece is complete; read those.
            val inPiece = file.offset + position - piece.toLong() * meta.pieceLength
            val deadline = System.currentTimeMillis() + waitMs
            synchronized(core.signal) {
                while (!core.have(meta.hash, piece)) {
                    core.error(meta.hash)?.let { throw IOException("Torrent error: $it") }
                    val ready = TorrentPlan.readableInPiece(core.finishedBlocks(meta.hash, piece), inPiece, meta.pieceLength.toLong())
                    if (ready > 0) { upTo = position + ready; break }
                    val left = deadline - System.currentTimeMillis()
                    if (left <= 0) throw IOException("No peer sent this part in time")
                    core.askBlocks(meta.hash)
                    core.signal.wait(minOf(left, BLOCK_POLL_MS))
                }
            }
        }
        val n = minOf(count.toLong(), upTo - position, length - position).toInt()
        return readFile(position, buffer, offset, n)
    }

    private fun focus(piece: Int) {
        val d = TorrentPlan.deadlines(piece, last, meta.pieceLength, readAhead)
        val newEnd = d.lastOrNull()?.first ?: piece
        // B-31 seek: the abandoned window's deadlines are overdue now and would be fetched first.
        if (windowStart >= 0) {
            val old = (windowStart..windowEnd).filter { it < piece || it > newEnd }
            if (old.isNotEmpty()) core.dropDeadlines(meta.hash, old)
        }
        windowStart = piece; windowEnd = newEnd
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

    private companion object {
        /** While a piece is partial, how often a fresh block report is asked for (only while a read waits). */
        const val BLOCK_POLL_MS = 150L
        const val TAIL_DEADLINE_MS = 400
    }

    fun close() { synchronized(guard) { runCatching { raf?.close() }; raf = null } }
}
