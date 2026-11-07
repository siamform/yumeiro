/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.video

import kotlin.math.roundToInt

/**
 * Turns a continuous swipe into whole system-volume steps.
 *
 * A swipe reports tiny deltas (a fraction of the screen height per frame). Converting each delta to
 * steps on its own rounds every frame down to 0, so the volume never moved. This keeps the exact
 * fractional level across the whole gesture and only rounds when writing to the system.
 */
class VolumeAccumulator(private val maxSteps: Int) {
    private var level: Float? = null

    /** Starts (or restarts) a gesture from the current system level. */
    fun begin(currentSteps: Int) {
        level = if (maxSteps <= 0) 0f else currentSteps.coerceIn(0, maxSteps).toFloat() / maxSteps
    }

    val active: Boolean get() = level != null

    /** Adds a swipe delta (1.0 = full range) and returns the system volume step to apply. */
    fun add(delta: Float, currentSteps: Int): Int {
        if (level == null) begin(currentSteps)
        val next = ((level ?: 0f) + delta).coerceIn(0f, 1f)
        level = next
        return (next * maxSteps).roundToInt().coerceIn(0, maxSteps)
    }

    fun end() { level = null }
}
