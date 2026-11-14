/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

/**
 * Round 13: a bar that hides after a real scroll down and shows again after a small scroll up
 * (YouTube's bottom navigation). It only reads scroll deltas, nothing is consumed.
 */
class HideOnScroll(private val hideAfterPx: Float = 48f, private val showAfterPx: Float = 12f) : NestedScrollConnection {
    var visible by mutableStateOf(true)
        private set
    private var down = 0f
    private var up = 0f

    /** Feeds one scroll delta (negative y = content moves up = user scrolls down). Pure state. */
    fun onDelta(dy: Float) {
        if (dy < 0) { down += -dy; up = 0f; if (visible && down > hideAfterPx) visible = false }
        else if (dy > 0) { up += dy; down = 0f; if (!visible && up > showAfterPx) visible = true }
    }

    fun show() { visible = true; down = 0f; up = 0f }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        onDelta(consumed.y)
        // Pulled at the very top (nothing left to scroll): always show.
        if (available.y > 0f) show()
        return Offset.Zero
    }
}
