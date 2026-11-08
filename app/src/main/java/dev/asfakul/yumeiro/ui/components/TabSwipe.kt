/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Facebook-style tab switching: a horizontal swipe on a page moves to the neighbouring bottom-nav
 * tab (right → left = next tab, left → right = previous tab).
 *
 * It only reacts to swipes the page itself didn't use: carousels, shelves, pagers and sliders
 * consume their own drags first (children see pointer events before this modifier), so swiping a
 * poster row still scrolls the row. Mostly-vertical moves (list scrolling) are ignored.
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

fun Modifier.tabSwipe(enabled: Boolean, onSwipe: (direction: Int) -> Unit): Modifier = composed {
    val callback by rememberUpdatedState(onSwipe)
    if (!enabled) return@composed this
    pointerInput(Unit) {
        val distance = 96.dp.toPx()
        val fling = 900.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var usedByChild = false
            var last = down.position
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                // A row, pager or slider below took this drag: it isn't a tab swipe.
                if (change.isConsumed) usedByChild = true
                if (event.changes.size > 1) usedByChild = true // pinch / two fingers
                tracker.addPosition(change.uptimeMillis, change.position)
                last = change.position
                if (!change.pressed) break
            }
            if (usedByChild) return@awaitEachGesture
            val dx = last.x - down.position.x
            val dy = last.y - down.position.y
            val dir = TabSwipe.decide(dx, dy, tracker.calculateVelocity().x, distance, fling)
            if (dir != 0) callback(dir)
        }
    }
}
