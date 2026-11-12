/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pinch-to-zoom maths for the video picture (pure; unit-tested). Only the video surface is scaled,
 * so subtitles and controls stay sharp and in place.
 *
 * - 100 % – 400 %. Spreading past 400 % or pinching below 100 % stops at the edge.
 * - The picture can be panned with two fingers, but never so far that a black gap opens.
 * - Letting go close to 100 % snaps back to exactly 100 %.
 */
object VideoZoom {
    const val MIN = 1f
    const val MAX = 4f
    /** Within this of 100 % on release counts as "back to normal". */
    const val SNAP = 0.06f

    data class State(val scale: Float = MIN, val panX: Float = 0f, val panY: Float = 0f) {
        val zoomed: Boolean get() = scale > MIN + 0.001f
    }

    /** Applies one pinch step ([zoom] factor, [dx]/[dy] pan in px) inside a [width]×[height] view. */
    fun step(s: State, zoom: Float, dx: Float, dy: Float, width: Float, height: Float): State {
        val z = if (zoom.isFinite() && zoom > 0f) zoom else 1f
        val scale = (s.scale * z).coerceIn(MIN, MAX)
        return clampPan(State(scale, s.panX + dx, s.panY + dy), width, height)
    }

    /** Keeps the scaled picture covering the view: pan at most half of the extra size each way. */
    fun clampPan(s: State, width: Float, height: Float): State {
        val maxX = ((s.scale - 1f) * width / 2f).coerceAtLeast(0f)
        val maxY = ((s.scale - 1f) * height / 2f).coerceAtLeast(0f)
        return s.copy(panX = s.panX.coerceIn(-maxX, maxX), panY = s.panY.coerceIn(-maxY, maxY))
    }

    /** On release: snap to 100 % when close. */
    fun settle(s: State): State = if (abs(s.scale - MIN) <= SNAP) State() else s

    /** "150%" for the on-screen hint. */
    fun label(scale: Float): String = "${(scale * 100f).roundToInt()}%"
}
