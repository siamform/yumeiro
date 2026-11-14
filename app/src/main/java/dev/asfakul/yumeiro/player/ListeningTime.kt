/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Round 2, order 5: music play time measured from real listening.
 *
 * Library used to show the total length of every song on the phone as "playtime". Now the clock
 * runs only while music is actually playing (monotonic clock, so changing the phone's time does
 * nothing) and is saved every 30 s and on every pause.
 */
object ListeningTime {
    private const val PREFS = "yumeiro_listening"
    private const val KEY = "music_ms"
    private val _total = MutableStateFlow(0L)
    val total: StateFlow<Long> = _total.asStateFlow()
    private var loaded = false
    private var job: Job? = null

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        _total.value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY, 0L)
    }

    /** Pure accumulator → unit-tested. Returns the new total after [deltaMs] of real playback. */
    internal fun add(total: Long, deltaMs: Long): Long = total + deltaMs.coerceIn(0L, 60_000L)

    fun track(context: Context, scope: CoroutineScope, playing: Flow<Boolean>, clock: () -> Long = SystemClock::elapsedRealtime) {
        load(context)
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        fun save() = prefs.edit().putLong(KEY, _total.value).apply()
        scope.launch {
            playing.distinctUntilChanged().collect { on ->
                job?.cancel()
                if (!on) return@collect
                job = scope.launch {
                    var last = clock()
                    try {
                        while (true) {
                            delay(30_000L)
                            val now = clock(); _total.value = add(_total.value, now - last); last = now; save()
                        }
                    } finally {
                        _total.value = add(_total.value, clock() - last); save()
                    }
                }
            }
        }
    }
}
