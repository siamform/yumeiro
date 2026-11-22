/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream.torrent

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.BitSet

/**
 * B-31 speed (device run 44877648670: first 64 KB in 13.9 / 19.9 s, budget 15 s; seek 5.3 s, budget 5 s). Cause:
 * a read waited for its whole piece (several MB on a 5.8 GB file) although the first 16 KiB blocks were already
 * on disk. Now a read returns the finished blocks of a partial piece at once.
 */
class TorrentBlocksTest {
    @get:Rule val tmp = TemporaryFolder()
    private val pl = 4 * 16 * 1024 // 4 blocks per piece
    private val data = ByteArray(3 * pl) { (it % 251).toByte() }

    @Test fun readReturnsFinishedBlocksBeforeThePieceIsComplete() {
        val dir = tmp.newFolder()
        val f = TorrentFile(0, "movie.mkv", data.size.toLong(), 0)
        File(dir, f.path).writeBytes(data)
        val meta = TorrentMeta("cd".repeat(20), "movie", pl, 3, listOf(f))
        val blocks = BitSet().apply { set(0); set(1) } // blocks 0 and 1 of piece 0 are written; 2 and 3 are not
        var asked = 0
        val core = object : TorrentCore by FakeCore(dir, meta) {
            override fun finishedBlocks(hash: String, piece: Int): BitSet? = if (piece == 0) blocks else null
            override fun askBlocks(hash: String) { asked++ }
        }
        val src = TorrentSource(core, meta, f, readAhead = pl.toLong(), waitMs = 2_000)
        val buf = ByteArray(pl)
        val start = System.currentTimeMillis()
        val n = src.read(0, buf, 0, buf.size)
        assertTrue("waited ${System.currentTimeMillis() - start} ms for blocks that were already there", System.currentTimeMillis() - start < 1_000)
        assertEquals(2 * 16 * 1024, n)
        assertArrayEquals(data.copyOfRange(0, n), buf.copyOfRange(0, n))
    }

    @Test fun readableInPieceCountsContiguousBlocksOnly() {
        val b = BitSet().apply { set(0); set(1); set(3) }
        val blk = TorrentPlan.BLOCK
        assertEquals(2 * blk, TorrentPlan.readableInPiece(b, 0, 4 * blk))
        assertEquals(2 * blk - 100, TorrentPlan.readableInPiece(b, 100, 4 * blk))
        assertEquals(0L, TorrentPlan.readableInPiece(b, 2 * blk, 4 * blk))
        assertEquals(blk - 10, TorrentPlan.readableInPiece(b, 3 * blk + 10, 4 * blk))
        assertEquals(0L, TorrentPlan.readableInPiece(null, 0, 4 * blk))
        // A short last piece: never past its end.
        assertEquals(1000L, TorrentPlan.readableInPiece(BitSet().apply { set(0) }, 0, 1000))
    }

    @Test fun primePutsTheStartBeforeTheEnd() {
        val dir = tmp.newFolder()
        val f = TorrentFile(0, "movie.mkv", data.size.toLong(), 0)
        val meta = TorrentMeta("ef".repeat(20), "movie", pl, 3, listOf(f))
        File(dir, f.path).writeBytes(data)
        val core = FakeCore(dir, meta)
        val src = TorrentSource(core, meta, f, readAhead = pl.toLong(), waitMs = 2_000)
        src.prime()
        // B31-SEEK5 (flag b31_target_first): until the start has bytes it is the only deadline; the end waits.
        assertTrue("end asked before the start had bytes: ${core.deadlines.toList()}", core.deadlines.none { it.first == 2 })
        core.arrive(0); src.read(0, ByteArray(10), 0, 10)
        val first = core.deadlines.first { it.first == 0 }.second
        val tail = core.deadlines.first { it.first == 2 }.second
        assertTrue("start deadline $first must be before the end's $tail", first < tail)
    }
}
