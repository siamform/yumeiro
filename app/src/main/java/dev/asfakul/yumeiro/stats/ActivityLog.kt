/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stats

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * F-17: per-day activity for the profile statistics. Stored only on this device (prefs `activity_log`, about one
 * year, a few KB), never sent anywhere, not written while incognito is on, wiped by "Clear statistics".
 * Writes are batched: the in-memory list updates at once, the file at most every 30 s or when playback stops.
 */
object ActivityLog {
    private const val PREFS = "activity_log"
    private const val KEY = "days"
    private val _days = MutableStateFlow<List<StatsMath.Day>>(emptyList())
    val days: StateFlow<List<StatsMath.Day>> = _days.asStateFlow()
    @Volatile private var loaded = false
    @Volatile private var lastSave = 0L

    fun today(): Long = java.time.LocalDate.now().toEpochDay()

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            _days.value = runCatching { StatsMath.decode(prefs(context).getString(KEY, null)) }.getOrDefault(emptyList())
            loaded = true
        }
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun off(context: Context): Boolean =
        dev.asfakul.yumeiro.security.Privacy.incognito.value ||
            !dev.asfakul.yumeiro.FeatureFlags.on(context, dev.asfakul.yumeiro.FeatureFlags.Flag.PROFILE_STATS)

    private fun save(context: Context, force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastSave < 30_000L) return
        lastSave = now
        runCatching { prefs(context).edit().putString(KEY, StatsMath.encode(_days.value)).apply() }
    }

    fun addTime(context: Context, music: Boolean, deltaMs: Long, flush: Boolean = false) {
        if (deltaMs <= 0 || off(context)) return
        ensureLoaded(context)
        synchronized(this) { _days.value = StatsMath.addTime(_days.value, today(), music, deltaMs) }
        save(context, flush)
    }

    fun addPlay(context: Context, kind: StatsMath.Kind) {
        if (off(context)) return
        ensureLoaded(context)
        synchronized(this) { _days.value = StatsMath.addPlay(_days.value, today(), kind) }
        save(context, false)
    }

    fun flush(context: Context) { if (loaded) save(context, true) }

    fun clear(context: Context) {
        synchronized(this) { _days.value = emptyList() }
        runCatching { prefs(context).edit().remove(KEY).apply() }
    }

    /** Watching time of the shared video player (PlaybackSession): counted between play and pause / release. */
    object VideoClock {
        @Volatile private var since = 0L
        @Volatile private var app: Context? = null
        /** The player is going away without a pause event (release): count what played. */
        fun stop() { app?.let { playing(it, false) } }
        fun playing(context: Context, on: Boolean) {
            val now = SystemClock.elapsedRealtime()
            app = context.applicationContext
            if (on) { if (since == 0L) since = now; return }
            val s = since
            since = 0L
            if (s > 0) addTime(context, music = false, deltaMs = now - s, flush = true)
        }
    }
}
