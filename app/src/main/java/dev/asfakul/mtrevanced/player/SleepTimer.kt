/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.mtrevanced.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Music sleep timer: pauses after a set time (fading the volume out over the last [FADE_MS]) or at
 * the end of the current song. The state is observable so the UI can show a live countdown.
 */
class SleepTimer(
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val positionMs: () -> Long,
    private val durationMs: () -> Long,
    private val trackId: () -> String?,
    private val setVolume: (Float) -> Unit,
    private val pause: () -> Unit,
) {
    sealed interface State {
        data class Countdown(val endsAtMs: Long, val totalMs: Long) : State
        data class EndOfTrack(val trackId: String?) : State
    }

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state.asStateFlow()
    private var job: Job? = null

    fun start(minutes: Int) {
        val total = minutes.coerceIn(1, 24 * 60) * 60_000L
        val s = State.Countdown(now() + total, total)
        launch(s)
    }

    fun endOfTrack() = launch(State.EndOfTrack(trackId()))

    /** Adds [minutes] to a running countdown (or starts one). */
    fun extend(minutes: Int) {
        val s = _state.value
        if (s is State.Countdown) launch(s.copy(endsAtMs = s.endsAtMs + minutes * 60_000L, totalMs = s.totalMs + minutes * 60_000L))
        else start(minutes)
    }

    fun cancel() {
        job?.cancel(); job = null
        _state.value = null
        setVolume(1f)
    }

    private fun launch(s: State) {
        job?.cancel()
        setVolume(1f)
        _state.value = s
        job = scope.launch {
            while (isActive) {
                when (s) {
                    is State.Countdown -> {
                        val left = s.endsAtMs - now()
                        if (left <= 0) break
                        if (left < FADE_MS) setVolume((left.toFloat() / FADE_MS).coerceIn(0.05f, 1f))
                    }
                    is State.EndOfTrack -> {
                        val d = durationMs()
                        val changed = s.trackId != null && trackId() != null && trackId() != s.trackId
                        if (changed || (d > 0 && positionMs() >= d - END_MARGIN_MS)) break
                    }
                }
                delay(TICK_MS)
            }
            if (isActive) {
                pause()
                setVolume(1f)
                _state.value = null
            }
        }
    }

    companion object {
        const val FADE_MS = 20_000L
        const val END_MARGIN_MS = 450L
        const val TICK_MS = 250L
        val PRESETS = listOf(5, 10, 15, 30, 45, 60, 90)
    }
}
