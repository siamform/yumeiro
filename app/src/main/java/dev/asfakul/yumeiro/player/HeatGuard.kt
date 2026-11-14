/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import android.content.Context
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Round 2, order 6: one place that knows how warm the phone is and slows Yumeiro down.
 *
 * Inputs: the thermal status (Android 10+), battery saver and whether the app is on screen.
 * Outputs: how often polling loops may run, how many extension calls may run at once, whether
 * the GPU upscaler is allowed and how many backup links to keep collecting once a video plays.
 */
object HeatGuard {
    enum class Level { COOL, WARM, HOT }

    private val _level = MutableStateFlow(Level.COOL)
    val level: StateFlow<Level> = _level.asStateFlow()

    @Volatile private var thermal = 0
    @Volatile private var saver = false
    @Volatile private var installed = false

    /** Maps the platform values to a level (pure, unit tested). */
    fun levelOf(thermalStatus: Int, powerSave: Boolean): Level = when {
        thermalStatus >= 3 -> Level.HOT // THERMAL_STATUS_SEVERE and above
        thermalStatus >= 2 || powerSave -> Level.WARM // MODERATE, or battery saver
        else -> Level.COOL
    }

    fun install(context: Context) {
        if (installed) return
        installed = true
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        saver = runCatching { pm.isPowerSaveMode }.getOrDefault(false)
        if (Build.VERSION.SDK_INT >= 29) runCatching {
            thermal = pm.currentThermalStatus
            pm.addThermalStatusListener(context.mainExecutor) { s -> thermal = s; publish() }
        }
        runCatching {
            val f = android.content.IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            val r = object : android.content.BroadcastReceiver() {
                override fun onReceive(c: Context?, i: android.content.Intent?) { saver = runCatching { pm.isPowerSaveMode }.getOrDefault(false); publish() }
            }
            androidx.core.content.ContextCompat.registerReceiver(context.applicationContext, r, f, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        }
        publish()
    }

    private fun publish() { _level.value = levelOf(thermal, saver) }

    /** For tests. */
    fun set(level: Level) { _level.value = level }

    /**
     * Interval for a loop that normally runs every [baseMs]: ×2 when warm, ×4 when hot, and at
     * least [backgroundMs] while the app isn't on screen (nobody is looking at the numbers).
     */
    fun interval(baseMs: Long, foreground: Boolean = true, backgroundMs: Long = 2_000L, level: Level = _level.value): Long {
        val scaled = when (level) { Level.COOL -> baseMs; Level.WARM -> baseMs * 2; Level.HOT -> baseMs * 4 }
        return if (foreground) scaled else maxOf(scaled, backgroundMs)
    }

    /** Extension calls allowed at the same time. */
    fun parallel(level: Level = _level.value): Int = when (level) { Level.COOL -> 6; Level.WARM -> 4; Level.HOT -> 2 }

    /** The Anime4K GPU pass is skipped while the phone is hot. */
    fun allowGpuUpscale(level: Level = _level.value): Boolean = level != Level.HOT

    /** Once a video plays, collecting more backup links stops at this many. */
    fun linkCap(level: Level = _level.value): Int = when (level) { Level.COOL -> 12; Level.WARM -> 8; Level.HOT -> 5 }

    /** Decorative motion (hero auto-advance) pauses when warm or hot. */
    fun allowDecorativeMotion(level: Level = _level.value): Boolean = level == Level.COOL

    private val base = Semaphore(6)
    private val warmGate = Semaphore(4)
    private val hotGate = Semaphore(2)

    /** Runs [block] under the extension-call limit for the current level (6 / 4 / 2 at once). */
    suspend fun <T> limited(level: Level = _level.value, block: suspend () -> T): T = base.withPermit {
        when (level) {
            Level.COOL -> block()
            Level.WARM -> warmGate.withPermit { block() }
            Level.HOT -> hotGate.withPermit { block() }
        }
    }
}
