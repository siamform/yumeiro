/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** BUG-3: an idle connection takes the second half of a running piece; nothing is written twice. */
class Bug3PieceTest {
    private val mb = 1024L * 1024

    @Test fun splitHandsOverTheSecondHalf() {
        val p = Piece(3, 0, 8 * mb - 1)
        val tail = p.split(mb)!!
        assertEquals(3, tail.chunk)
        assertEquals(0L, p.position()); assertEquals(4 * mb - 1, p.end())
        assertEquals(4 * mb, tail.position()); assertEquals(8 * mb - 1, tail.end())
    }

    @Test fun aSmallRestIsNotSplit() {
        val p = Piece(0, 0, 2 * mb - 2) // one byte under 2 × 1 MB
        assertNull(p.split(mb))
        assertEquals(2 * mb - 2, p.end())
    }

    @Test fun bytesPastAMovedEndAreDropped() {
        val p = Piece(0, 100, 199)
        val writes = ArrayList<Pair<Long, Int>>()
        assertEquals(60, p.accept(60) { at, n -> writes += at to n })
        // Another connection takes 160..199 (simulated); the reader still has 64 bytes in hand.
        val tail = p.split(10)!!
        assertEquals(180L, tail.position())
        assertEquals(20, p.accept(64) { at, n -> writes += at to n })
        assertEquals(0, p.accept(64) { at, n -> writes += at to n })
        assertEquals(listOf(100L to 60, 160L to 20), writes)
        assertEquals(0L, p.left())
    }

    @Test fun piecesCoverTheChunkExactlyOnce() {
        val p = Piece(1, 0, 10 * mb - 1)
        val all = mutableListOf(p)
        repeat(3) { all.maxByOrNull { it.left() }!!.split(mb)?.let { all += it } }
        val covered = all.sortedBy { it.position() }.map { it.position()..it.end() }
        covered.zipWithNext().forEach { (a, b) -> assertEquals(a.last + 1, b.first) }
        assertEquals(0L, covered.first().first); assertEquals(10 * mb - 1, covered.last().last)
    }
}
