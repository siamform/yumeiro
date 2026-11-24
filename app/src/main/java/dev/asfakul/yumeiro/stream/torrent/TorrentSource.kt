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
    /** B31-SEEK3 kill switch (flag b31_stream_window). */
    private val window: Boolean = true,
    /** B31-SEEK5 kill switch (flag b31_target_first). */
    private val targetFirst: Boolean = true,
) : ByteSource {
    override val length: Long = file.size
    override val mimeType: String = TorrentPlan.mimeFor(file.path)
    private val last = TorrentPlan.lastPiece(file, meta.pieceLength)
    private val guard = Any()
    private var raf: RandomAccessFile? = null
    @Volatile private var windowStart = -1
    @Volatile private var windowEnd = -1
    /** B31-SEEK6: last piece of the window that has a deadline (the rest of the window is wanted without one). */
    @Volatile private var deadlineEnd = -1
    /** B31-SEEK3: container head / tail stay wanted at a low priority. */
    private val keep = TorrentPlan.headTail(file, meta.pieceLength)
    /** B31-SEEK6: how many pieces ahead of the read have a deadline at once. */
    private val urgent = TorrentPlan.urgentPieces(meta.pieceLength)
    /** B31-SEEK4: tail pieces that got a deadline in [prime] and have not lost it yet. */
    @Volatile private var tailAsked: List<Int> = emptyList()
    /** B31-SEEK5: the window (and tail) held back until the target piece has its first bytes; null = open. */
    @Volatile private var held: Held? = null
    /** TOR-CLOSE-1: set by [close]; a read still waiting gives up at once instead of after [waitMs]. */
    @Volatile private var closed = false
    private val plan = Any()
    private class Held(val window: List<Int>, val deadlines: List<Pair<Int, Int>>)

    /** First and last pieces at once (container headers / index), and the start of the file. */
    fun prime() {
        // B-31 speed: the start first (deadline 0 via focus), the end right after it, so they don't split peers.
        val first = TorrentPlan.firstPiece(file, meta.pieceLength)
        val tail = TorrentPlan.headTail(file, meta.pieceLength).filter { it != first }.map { it to TAIL_DEADLINE_MS }
        synchronized(plan) {
            focus(first)
            tailAsked = tail.map { it.first }
            val h = held
            if (h != null) held = Held(h.window, h.deadlines + tail) else core.setDeadlines(meta.hash, tail)
        }
    }

    override fun read(position: Long, buffer: ByteArray, offset: Int, count: Int): Int {
        if (position >= length) return -1
        touched()
        val piece = TorrentPlan.pieceAt(file, position, meta.pieceLength)
        if (!TorrentPlan.insideWindow(piece, windowStart, windowEnd, last)) synchronized(plan) { focus(piece) }
        else if (TorrentPlan.slideDeadlines(piece, deadlineEnd, windowEnd, urgent)) synchronized(plan) { slide(piece) }
        // Up to the end of this piece (the next one may not be here yet).
        val pieceEnd = (piece + 1).toLong() * meta.pieceLength - file.offset
        var upTo = pieceEnd
        if (!core.have(meta.hash, piece)) {
            // B-31 speed: blocks of a partial piece are on disk before the piece is complete; read those.
            val inPiece = file.offset + position - piece.toLong() * meta.pieceLength
            val started = System.currentTimeMillis()
            val deadline = started + waitMs
            var nextTrace = started
            synchronized(core.signal) {
                while (!core.have(meta.hash, piece)) {
                    if (closed) throw IOException("Torrent stream closed")
                    TorrentTrace.sink?.let { sink ->
                        val now = System.currentTimeMillis()
                        if (now >= nextTrace) { nextTrace = now + 1_000; sink(waitLine(piece, inPiece, now - started)) }
                    }
                    core.error(meta.hash)?.let { throw IOException("Torrent error: $it") }
                    val bits = core.finishedBlocks(meta.hash, piece)
                    // B31-SEEK6 (lowram run 45268091524): the piece had 6 of 8 blocks but not the one the read needs, so
                    // the held window never opened, nothing else was wanted and the peers left; open on any block.
                    if (held != null && bits != null && !bits.isEmpty) open()
                    val ready = TorrentPlan.readableInPiece(bits, inPiece, meta.pieceLength.toLong())
                    if (ready > 0) { upTo = position + ready; break }
                    val left = deadline - System.currentTimeMillis()
                    if (left <= 0) throw IOException("No peer sent this part in time")
                    core.askBlocks(meta.hash)
                    core.signal.wait(minOf(left, BLOCK_POLL_MS))
                }
            }
            TorrentTrace.sink?.invoke("piece $piece readable after ${System.currentTimeMillis() - started} ms (have=${core.have(meta.hash, piece)})")
        }
        open()
        val n = minOf(count.toLong(), upTo - position, length - position).toInt()
        return readFile(position, buffer, offset, n)
    }

    /** SPEED / B31-SEEK3 trace line: which block the read needs, how many blocks of the piece are in, peers, rate. */
    private fun waitLine(piece: Int, inPiece: Long, waitedMs: Long): String {
        val bits = core.finishedBlocks(meta.hash, piece)
        val (peers, rate) = core.stats(meta.hash)
        return "wait piece $piece block ${inPiece / TorrentPlan.BLOCK}/${meta.pieceLength / TorrentPlan.BLOCK} " +
            "finished ${bits?.cardinality() ?: -1} firstGap ${bits?.nextClearBit(0) ?: -1} peers $peers rate ${rate / 1024} KiB/s " +
            "after $waitedMs ms"
    }

    private fun focus(piece: Int) {
        val seek = windowStart >= 0
        val d = TorrentPlan.deadlines(piece, last, meta.pieceLength, readAhead, if (seek) TorrentPlan.SEEK_GAP_MS else 0)
        val newEnd = d.lastOrNull()?.first ?: piece
        // B31-SEEK6 (run 45268091524, seek 13.6 s on api36): every piece of the 64 MB read-ahead had a deadline, and
        // libtorrent raises each deadline piece to top priority, so ~500 pieces shared the peers with the one the
        // player waited for. Only the next [urgent] pieces get deadlines; [slide] moves them on during playback.
        val timed = d.take(urgent)
        // B-31 seek: the abandoned window's deadlines are overdue now and would be fetched first.
        // B31-SEEK4 (TV device run 44884971496): the tail deadlines from prime() are older than the seek target's, so
        // libtorrent's time-critical picker served the 4 MB tail first and no block of the target came for 9 s. They
        // go with the old window; the tail stays wanted at priority 2, and a read at the tail focuses it again.
        if (windowStart >= 0) {
            val timedEnd = timed.lastOrNull()?.first ?: piece
            val old = ((windowStart..maxOf(windowStart, deadlineEnd)) + tailAsked).distinct().filter { it < piece || it > timedEnd }
            tailAsked = emptyList()
            if (old.isNotEmpty()) core.dropDeadlines(meta.hash, old)
        }
        windowStart = piece; windowEnd = newEnd; deadlineEnd = timed.lastOrNull()?.first ?: piece
        // B31-SEEK3: only the window is downloaded; priorities are written after the old deadlines are dropped and
        // before the new ones, so a reset never demotes the new window (libtorrent leaves reset pieces at 1).
        // B31-SEEK5 (lowram run 44884969625: no block of the first piece for 16 s while 0.4-0.9 MB/s went to the tail
        // and read-ahead): until the target piece has its first bytes it is the only piece wanted and the only
        // deadline, so every peer's free request slot goes to it; then [open] lets the window and tail in.
        held = null
        if (targetFirst && window && d.size > 1 && !core.have(meta.hash, piece)) {
            core.streamWindow(meta.hash, TorrentPlan.windowPriorities(meta.numPieces, listOf(piece), emptyList()))
            core.setDeadlines(meta.hash, d.take(1))
            held = Held(d.map { it.first }, timed.drop(1))
            return
        }
        if (window) core.streamWindow(meta.hash, TorrentPlan.windowPriorities(meta.numPieces, d.map { it.first }, keep))
        core.setDeadlines(meta.hash, timed)
    }

    /** B31-SEEK6: playback passed the middle of the timed pieces; the next ones in the window get deadlines. */
    private fun slide(piece: Int) {
        if (!TorrentPlan.slideDeadlines(piece, deadlineEnd, windowEnd, urgent)) return
        val end = minOf(windowEnd, piece + urgent - 1)
        val next = (deadlineEnd + 1..end).map { it to (it - piece) * 120 }
        deadlineEnd = end
        val h = held
        if (h != null) held = Held(h.window, h.deadlines + next) else core.setDeadlines(meta.hash, next)
    }

    /** B31-SEEK5: the target piece has bytes; the held window and tail may download now. */
    private fun open() = synchronized(plan) {
        val h = held ?: return@synchronized
        held = null
        core.streamWindow(meta.hash, TorrentPlan.windowPriorities(meta.numPieces, h.window, keep))
        core.setDeadlines(meta.hash, h.deadlines)
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

    /**
     * TOR-CLOSE-1 (run 45268091524: a read of the previous torrent, paused when the next one opened, kept waiting
     * 20 s and more on a local server thread): wakes every waiting read so it fails now.
     */
    fun close() {
        closed = true
        synchronized(core.signal) { core.signal.notifyAll() }
        synchronized(guard) { runCatching { raf?.close() }; raf = null }
    }
}
