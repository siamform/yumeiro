/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

/**
 * One place for seek and progress-bar arithmetic, shared by the video player, Now Playing and the
 * mini player. Pure → unit tested (SeekMathTest).
 *
 * The bugs it fixes: screens kept a placeholder duration of `1L` while ExoPlayer still reported
 * `C.TIME_UNSET` (streams before READY, live-ish HLS, YouTube songs). Code then did
 * `(pos + 10 s).coerceAtMost(duration)` → fast forward jumped to 0:00, and `pos / duration` → a full
 * progress bar. A duration is only trusted when it is real ([known]); otherwise forward is not
 * capped, the bar stays empty and slider seeks are ignored.
 */
object SeekMath {
    /** Placeholder screens use while the duration is unknown. */
    const val UNKNOWN = 1L

    /** A trustworthy duration from the player's value, else the screen's last known one, else null. */
    fun known(playerDurationMs: Long, fallbackMs: Long = 0L): Long? =
        playerDurationMs.takeIf { it > UNKNOWN } ?: fallbackMs.takeIf { it > UNKNOWN }

    fun forward(positionMs: Long, stepMs: Long, durationMs: Long?): Long {
        val raw = positionMs.coerceAtLeast(0L) + stepMs
        return if (durationMs != null) raw.coerceAtMost(durationMs) else raw
    }

    fun rewind(positionMs: Long, stepMs: Long): Long = (positionMs - stepMs).coerceAtLeast(0L)

    /** Clamps a drag/scrub target; with no known duration only the lower bound applies. */
    fun clamp(targetMs: Long, durationMs: Long?): Long =
        if (durationMs != null) targetMs.coerceIn(0L, durationMs) else targetMs.coerceAtLeast(0L)

    /** Bar fraction 0..1; 0 while the duration is unknown (never a falsely "full" bar). */
    fun fraction(positionMs: Long, durationMs: Long?): Float =
        if (durationMs == null || durationMs <= UNKNOWN) 0f
        else (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    /** Seek target for a slider fraction, or null when the duration is unknown (don't seek to 0). */
    fun positionFor(fraction: Float, durationMs: Long?): Long? =
        if (durationMs == null || durationMs <= UNKNOWN) null
        else (fraction.coerceIn(0f, 1f) * durationMs).toLong()

    /**
     * After a seek the player can report the old position for a frame or two (and the UI ticker
     * lags). While a seek is pending the bar shows the target, until the player is within
     * [toleranceMs] of it or [timeoutMs] passes — this removes the "snap back" flicker.
     */
    fun displayed(playerPositionMs: Long, pendingTargetMs: Long?, pendingSinceMs: Long, nowMs: Long,
                  toleranceMs: Long = 1_500L, timeoutMs: Long = 2_000L): Long {
        if (pendingTargetMs == null) return playerPositionMs
        val caughtUp = kotlin.math.abs(playerPositionMs - pendingTargetMs) <= toleranceMs
        return if (caughtUp || nowMs - pendingSinceMs > timeoutMs) playerPositionMs else pendingTargetMs
    }

    /** True when [displayed] has let go of the pending target. */
    fun settled(playerPositionMs: Long, pendingTargetMs: Long?, pendingSinceMs: Long, nowMs: Long,
                toleranceMs: Long = 1_500L, timeoutMs: Long = 2_000L): Boolean =
        pendingTargetMs == null || kotlin.math.abs(playerPositionMs - pendingTargetMs) <= toleranceMs ||
            nowMs - pendingSinceMs > timeoutMs
}
