/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.data

/**
 * BUG-1: when a resume position is written, kept or cleared, and where a player starts.
 * See docs/proposals/FEATURE-BUG-1-watch-progress.md.
 *
 * - D2: a player that never loaded reports position 0 and an unknown duration (TIME_UNSET is
 *   negative). That used to delete the saved position; now nothing is written.
 * - D1: an item opened without a position (YouTube, links) starts at its saved position.
 */
object ResumeRules {
    enum class Write { SKIP, SAVE, CLEAR }

    /** Watched to here (or more) counts as finished and leaves Continue watching. */
    const val DONE_FRACTION = 0.95

    fun decide(positionMs: Long, durationMs: Long): Write = when {
        durationMs <= 0L -> Write.SKIP // nothing loaded, or a live stream: keep what is saved
        positionMs >= (durationMs * DONE_FRACTION).toLong() -> Write.CLEAR
        positionMs <= 0L -> Write.SKIP // not started (or not loaded yet): never wipes a resume point
        else -> Write.SAVE
    }

    /** Online titles carry their own position from OnlineHistory (ids include the link). */
    fun usesVideoStore(videoId: String): Boolean = !videoId.startsWith("online:")

    /** Where an item starts: its own position, else the saved one. */
    fun startPosition(videoId: String, itemPositionMs: Long, saved: (String) -> Long?): Long =
        when {
            itemPositionMs > 0L -> itemPositionMs
            !usesVideoStore(videoId) -> 0L
            else -> saved(videoId)?.takeIf { it > 0L } ?: 0L
        }
}
