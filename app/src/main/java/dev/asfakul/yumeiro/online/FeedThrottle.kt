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
 * Limits: playing, HOT, or memory low (onTrimMemory RUNNING_LOW or worse confirmed by [confirmed], onLowMemory; kept [MEMORY_LOW_MS]) = 1;
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

    /**
     * FE-T1 (SpeedDeviceTest 44892710506, api33-lowram): a 2 GB phone gets RUNNING_CRITICAL / RUNNING_LOW trims right at
     * start with 800 MB free and an 8 MB heap (the level follows the count of cached apps, not our memory), so the
     * level alone threw the whole feed onto one lane (feed_half_shown 4.4 s -> 56.8 s). A trim level now throttles
     * only when memory is really short: our heap is at least [HEAP_TIGHT] full, or Android says the system is low.
     * Pure.
     */
    fun confirmed(level: Int, heapUsed: Long, heapMax: Long, systemLow: Boolean): Boolean =
        isPressure(level) && (systemLow || (heapMax > 0 && heapUsed.toDouble() / heapMax >= HEAP_TIGHT))

    const val HEAP_TIGHT = 0.75

    private val checker by lazy { java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "yu-feedthrottle").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } } }

    /**
     * Called from Application.onTrimMemory on the main thread. Heap numbers are read here (no I/O); the system check
     * (a binder call) runs on a background thread.
     */
    fun onTrim(context: android.content.Context, level: Int) {
        if (!isPressure(level)) return
        val rt = Runtime.getRuntime()
        val used = rt.totalMemory() - rt.freeMemory()
        val max = rt.maxMemory()
        if (confirmed(level, used, max, systemLow = false)) { memoryLow(); return }
        val app = context.applicationContext
        runCatching {
            checker.execute {
                val am = app.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                val mi = android.app.ActivityManager.MemoryInfo().also { m -> runCatching { am?.getMemoryInfo(m) } }
                if (confirmed(level, used, max, mi.lowMemory)) memoryLow()
            }
        }
    }

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
