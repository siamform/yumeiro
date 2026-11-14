/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

/**
 * R15 (ADR-006 step 3b): the two "this source is dead" clocks, as pure state machines fed once a
 * second. They used to be loops written inline in the player composable, which could not be tested
 * and kept their counters in local variables.
 *
 * [StartWatch]: a source that never shows a picture. It gives up after [minWaitMs] of trying if
 * nothing new was downloaded for [stallMs], and always after [maxWaitMs]. Paused or resolving time
 * does not count.
 *
 * [StallWatch]: a source that started but then buffers forever (dead CDN, token expired mid-file).
 * It fires after [stuckMs] of buffering with the buffer not growing, only while online.
 */
class StartWatch(
    private val minWaitMs: Long = 12_000L,
    private val stallMs: Long = 10_000L,
    private val maxWaitMs: Long = 45_000L,
    private val tickMs: Long = 1_000L,
) {
    enum class Verdict { WAIT, STARTED, GIVE_UP }

    var waitedMs = 0L; private set
    private var stalledMs = 0L
    private var lastBuffered = -1L
    private var done = false

    fun tick(firstFrame: Boolean, failed: Boolean, counting: Boolean, bufferedMs: Long): Verdict {
        if (done) return Verdict.STARTED
        if (firstFrame || failed) { done = true; return Verdict.STARTED }
        if (!counting) return Verdict.WAIT
        waitedMs += tickMs
        if (bufferedMs > lastBuffered) { stalledMs = 0L; lastBuffered = bufferedMs } else stalledMs += tickMs
        if (waitedMs >= maxWaitMs || (waitedMs >= minWaitMs && stalledMs >= stallMs)) { done = true; return Verdict.GIVE_UP }
        return Verdict.WAIT
    }
}

class StallWatch(
    private val stuckMs: Long = 25_000L,
    private val growthMs: Long = 500L,
    private val tickMs: Long = 1_000L,
) {
    private var stuck = 0L
    private var lastBuffered = -1L

    /** True when the source should be abandoned (the counter starts over afterwards). */
    fun tick(buffering: Boolean, firstFrame: Boolean, online: Boolean, bufferedPositionMs: Long): Boolean {
        if (!buffering || !firstFrame || !online) { stuck = 0L; lastBuffered = -1L; return false }
        if (bufferedPositionMs > lastBuffered + growthMs) { lastBuffered = bufferedPositionMs; stuck = 0L; return false }
        stuck += tickMs
        if (stuck >= stuckMs) { stuck = 0L; return true }
        return false
    }
}
