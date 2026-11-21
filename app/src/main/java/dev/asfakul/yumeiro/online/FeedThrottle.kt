/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import dev.asfakul.yumeiro.player.HeatGuard

/**
 * E4 part 2 (docs/MAJOR-BUGS-AND-FEATURES-2.x.md E4: "throttle on low RAM or heat"): background feed work (Yumix
 * background homes, the shared extension-call limit's BACKGROUND lane) steps back when the phone is hot or short
 * of memory, like it already does while something plays (FE-P). Picked, on-screen and search work is not limited
 * here; only the background lane. Before, the 3 background home slots and 6 background calls ran at full width on a
 * hot phone (HeatGuard only narrowed the total) and right after Android asked the app to free memory.
 *
 * Limits: playing, HOT, or memory low (onTrimMemory RUNNING_LOW or worse, onLowMemory; kept [MEMORY_LOW_MS]) = 1;
 * WARM (moderate heat or battery saver) = normal - 1 (at least 1); else normal.
 */
object FeedThrottle {
    const val MEMORY_LOW_MS = 60_000L
    @Volatile private var memoryLowUntil = 0L

    /** Called from Application.onTrimMemory (RUNNING_LOW and worse) and onLowMemory. Main thread, no I/O. */
    fun memoryLow(now: Long = System.currentTimeMillis()) { memoryLowUntil = now + MEMORY_LOW_MS }

    /**
     * Pure: whether an onTrimMemory [level] means memory pressure. RUNNING_LOW (10) and RUNNING_CRITICAL (15) while on
     * screen, MODERATE (60) and COMPLETE (80) while cached; RUNNING_MODERATE (5), UI_HIDDEN (20) and BACKGROUND (40)
     * are routine (the app was just hidden, or the system is fine) and do not throttle.
     */
    fun isPressure(level: Int): Boolean = level in 10..15 || level >= 60

    fun isMemoryLow(now: Long = System.currentTimeMillis()): Boolean = now < memoryLowUntil

    /** Pure: background jobs allowed at once. */
    fun limit(normal: Int, playing: Boolean, heat: HeatGuard.Level, memoryLow: Boolean): Int = when {
        playing || heat == HeatGuard.Level.HOT || memoryLow -> 1
        heat == HeatGuard.Level.WARM -> (normal - 1).coerceAtLeast(1)
        else -> normal
    }.coerceAtMost(normal.coerceAtLeast(1))

    /** For tests. */
    internal fun reset() { memoryLowUntil = 0L }
}
