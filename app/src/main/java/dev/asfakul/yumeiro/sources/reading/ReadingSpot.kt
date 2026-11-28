/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.reading

/**
 * Group X (B5): the exact reading spot inside a chapter, kept as one number from 0 to 1 (no new storage format).
 *
 * Before, a novel kept only which paragraph was at the top (a long paragraph re-opened at its first line) and a
 * webtoon only which page (a page several screens tall re-opened at its top). The spot is now the item at the top
 * plus how far into that item the top edge is, as a share of the item's height, so it survives a new text size, a
 * turned phone or a page image that loads later. Pure.
 */
object ReadingSpot {
    /** [index] of the item at the top (0 until [count]), [offsetPx] of [sizePx] scrolled past its top → 0..1. */
    fun encode(index: Int, offsetPx: Int, sizePx: Int, count: Int): Float {
        if (count <= 0) return 0f
        val i = index.coerceIn(0, count - 1)
        val within = if (sizePx <= 0) 0f else (offsetPx.toFloat() / sizePx).coerceIn(0f, MAX_WITHIN)
        return ((i + within) / count).coerceIn(0f, 1f)
    }

    /** The item and the share of it scrolled past, from an [encode]d spot. 1 (finished) opens the last item. */
    fun decode(spot: Float, count: Int): Pair<Int, Float> {
        if (count <= 0 || spot.isNaN()) return 0 to 0f
        // A hair over, so float rounding never lands just before the item's start (2.9999 for 3).
        val at = spot.coerceIn(0f, 1f) * count + 1e-4f
        val i = at.toInt().coerceIn(0, count - 1)
        val within = if (spot >= 1f) 0f else (at - i).coerceIn(0f, MAX_WITHIN)
        return i to within
    }

    /** Pixels to scroll into an item of [sizePx] for a [within] share. */
    fun offsetPx(within: Float, sizePx: Int): Int = if (sizePx <= 0) 0 else (within.coerceIn(0f, MAX_WITHIN) * sizePx).toInt()

    /** Kept under 1 so a spot never rounds over into the next item. */
    private const val MAX_WITHIN = 0.999f
}

/** Group X (B5): saves that must finish after the reader is gone (leaving a chapter, the app going to the background). */
object ReadingSaves {
    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
}
