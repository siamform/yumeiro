/*
 * Yumeiro — Copyright (C) 2026 siamform
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
class VolumeAccumulator(private val maxSteps: Int, private val boostAllowed: Boolean = false) {
    private var level: Float? = null
    private val top: Float get() = if (boostAllowed) 2f else 1f

    /**
     * Starts (or restarts) a gesture from the current system level. F-06: [currentBoost] (0..1 = 100..200 %) only
     * counts when the system volume is at its maximum (hardware buttons may have lowered it since).
     */
    fun begin(currentSteps: Int, currentBoost: Float = 0f) {
        val base = if (maxSteps <= 0) 0f else currentSteps.coerceIn(0, maxSteps).toFloat() / maxSteps
        level = if (boostAllowed && maxSteps > 0 && currentSteps >= maxSteps) base + currentBoost.coerceIn(0f, 1f) else base
    }

    val active: Boolean get() = level != null

    /** F-06: boost above the system maximum, 0..1 (0 = none, 1 = 200 %). */
    val boost: Float get() = ((level ?: 0f) - 1f).coerceIn(0f, 1f)

    /** Adds a swipe delta (1.0 = full range) and returns the system volume step to apply. */
    fun add(delta: Float, currentSteps: Int): Int {
        if (level == null) begin(currentSteps)
        val next = ((level ?: 0f) + delta).coerceIn(0f, top)
        level = next
        return (next.coerceAtMost(1f) * maxSteps).roundToInt().coerceIn(0, maxSteps)
    }

    fun end() { level = null }
}
