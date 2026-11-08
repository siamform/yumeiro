/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

import kotlin.math.abs

/**
 * Tab-swipe rules. The tabs themselves are a `HorizontalPager` in MTPlayerApp (the page follows the
 * finger from anywhere; rows and carousels keep their own drags via nested scrolling). These pure
 * helpers stay for tests and for any custom gesture that needs the same thresholds.
 */
object TabSwipe {
    /** Pure decision → unit-testable. Returns -1 (previous tab), +1 (next tab) or 0. */
    fun decide(dx: Float, dy: Float, velocityX: Float, distancePx: Float, flingPx: Float): Int {
        if (abs(dx) < abs(dy) * 1.6f) return 0
        val far = abs(dx) >= distancePx
        val flung = abs(velocityX) >= flingPx && abs(dx) >= distancePx / 3f
        if (!far && !flung) return 0
        return if (dx < 0) 1 else -1
    }

    /** Index of the tab to show after a swipe [direction] from [current], or null at either end. */
    fun target(current: Int, direction: Int, count: Int): Int? =
        if (current < 0 || direction == 0) null else (current + direction).takeIf { it in 0 until count }
}
