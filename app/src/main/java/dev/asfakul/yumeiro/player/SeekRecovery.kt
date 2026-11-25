/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

/**
 * SEEK-1 (owner item 27): an error right after a seek is almost always about the new request the seek made (a
 * dropped or refused range request, an expired keep-alive connection, a segment the CDN hasn't cached yet), not
 * about the source. Before, it went through the same steps as a broken source: one quick retry for the whole video,
 * then the next source or an error card, which the user cleared by reloading the source by hand.
 *
 * Now: reopen the same link at the seek target (a fresh media source, so loads from the earlier position can't land
 * on the new one), at most [MAX_PER_SEEK] times per seek, the second time after a short pause. A new seek starts its
 * own count. Offline, DRM, decoder and format errors are not seek errors and keep their own handling.
 */
object SeekRecovery {
    /** An error this long after a seek still counts as caused by it (slow CDNs need a few seconds). */
    const val WINDOW_MS = 20_000L
    const val MAX_PER_SEEK = 2

    sealed interface Decision {
        /** Reopen the same link at [positionMs] after [delayMs]. */
        data class Reopen(val positionMs: Long, val delayMs: Long, val attempt: Int) : Decision
        data object NotASeekError : Decision
    }

    fun decide(
        errorCode: Int,
        networkStream: Boolean,
        online: Boolean,
        seekTargetMs: Long?,
        msSinceSeek: Long,
        attemptsForThisSeek: Int,
        durationMs: Long,
    ): Decision {
        if (!networkStream || !online || seekTargetMs == null) return Decision.NotASeekError
        if (msSinceSeek !in 0..WINDOW_MS || attemptsForThisSeek >= MAX_PER_SEEK) return Decision.NotASeekError
        // I/O (2xxx) and the container / playlist parse errors a cut-off range answer causes (3001 / 3002).
        val ioOrCutOff = errorCode in 2000..2999 || errorCode == 3001 || errorCode == 3002
        if (!ioOrCutOff) return Decision.NotASeekError
        // 2008 (read position out of range) at the very end: the length the server gave was a little long; reopen
        // a moment before the end instead of past it.
        val target = if (durationMs > 0 && seekTargetMs > durationMs - 1_500L) (durationMs - 1_500L).coerceAtLeast(0L) else seekTargetMs
        val attempt = attemptsForThisSeek + 1
        return Decision.Reopen(target, if (attempt == 1) 0L else 1_200L, attempt)
    }
}
