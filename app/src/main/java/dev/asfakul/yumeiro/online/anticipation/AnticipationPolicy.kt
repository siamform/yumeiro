/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.anticipation

import dev.asfakul.yumeiro.player.HeatGuard

/**
 * B23 (docs: audits/AUDIT_B23.md ADR-B23-3): how many upcoming episodes the Anticipation Engine prepares right now,
 * and when it must stand back. Pure, so every case is unit-tested.
 */
object AnticipationPolicy {
    /** The owner's default: the next three episodes. */
    const val DEPTH = 3
    /** Preparation starts once the playing episode has run this long (its own start always comes first). */
    const val START_AFTER_MS = 15_000L
    /** Resolved links are signed and expire: a prepared set older than this is not used (ADR-B23-1). */
    const val FRESH_MS = 20 * 60_000L
    /** An episode no source had is not searched again for this long (also after a restart). */
    const val MISS_MS = 6 * 60 * 60_000L
    /** A series not watched for this long loses its preparation data (never downloads, history or bookmarks). */
    const val INACTIVE_MS = 7L * 24 * 60 * 60_000L
    /** Timeouts per step: the episode's own extension, then the other installed ones. */
    const val OWN_MS = 45_000L
    const val OTHERS_MS = 60_000L
    /** Tries per episode in one session, with these gaps after a failure (not after a clean "nothing"). */
    const val MAX_TRIES = 2
    private val RETRY_GAPS = longArrayOf(30_000L, 120_000L)
    /** The engine looks at the session this often (cheap: no extension work unless something is due). */
    const val TICK_MS = 2_000L

    data class Signals(
        val enabled: Boolean,
        val heat: HeatGuard.Level,
        val memoryLow: Boolean,
        val online: Boolean,
        val metered: Boolean,
        val lowEnd: Boolean,
        /** The playing episode drew its first frame, is not buffering and has run [START_AFTER_MS]. */
        val playbackSteady: Boolean,
    )

    enum class Why { OFF, OFFLINE, HOT, MEMORY, PLAYBACK, OK }

    /** Why preparation is held back (or [Why.OK]); the first reason wins. */
    fun why(s: Signals): Why = when {
        !s.enabled -> Why.OFF
        !s.online -> Why.OFFLINE
        s.heat == HeatGuard.Level.HOT -> Why.HOT
        s.memoryLow -> Why.MEMORY
        !s.playbackSteady -> Why.PLAYBACK
        else -> Why.OK
    }

    /** Episodes to keep prepared ahead: 3 normally, 1 on mobile data, a warm phone or a low-end device, 0 when held back. */
    fun depth(s: Signals): Int = when {
        why(s) != Why.OK -> 0
        s.metered || s.lowEnd || s.heat == HeatGuard.Level.WARM -> 1
        else -> DEPTH
    }

    /** The rolling window: the next [depth] episode indexes after [index] in a list of [size], nearest first. */
    fun window(index: Int, size: Int, depth: Int): List<Int> {
        if (index < 0 || depth <= 0) return emptyList()
        val last = minOf(index + depth, size - 1)
        return if (last <= index) emptyList() else (index + 1..last).toList()
    }

    fun fresh(preparedAt: Long, now: Long): Boolean = preparedAt > 0 && now - preparedAt in 0 until FRESH_MS

    /** When a failed episode may be tried again, or null when it has had its tries. */
    fun retryAt(tries: Int, lastAt: Long): Long? = when {
        tries <= 0 -> 0L
        tries >= MAX_TRIES -> null
        else -> lastAt + RETRY_GAPS[(tries - 1).coerceAtMost(RETRY_GAPS.lastIndex)]
    }

    /** Short videos (trailers, test clips) count as steady halfway through instead of after [START_AFTER_MS]. */
    fun steady(firstFrame: Boolean, buffering: Boolean, positionMs: Long, durationMs: Long = 0L): Boolean =
        firstFrame && !buffering && positionMs >= (if (durationMs > 0) minOf(START_AFTER_MS, durationMs / 2) else START_AFTER_MS)
}
