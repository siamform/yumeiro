/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * Keeps extensions asleep while a brand-new user is still in the first-run welcome screen or the
 * explore tour. Nothing is loaded, installed (starter picks), synced or allowed to show messages
 * until the user finishes, skips or leaves the guide. Returning users are never held.
 *
 * The gate only ever opens once per process (a latch): replaying the tour later from Settings does
 * not unload anything that is already running.
 */
object OnboardingGate {
    private val _held = MutableStateFlow(false)
    /** True while a first-run guide is on screen and extensions must stay inactive. */
    val held: StateFlow<Boolean> = _held.asStateFlow()
    @Volatile private var released = false

    /** Called once at launch: holds the gate when this is a first run. */
    fun begin(firstRun: Boolean) {
        if (released) return
        if (firstRun) _held.value = true else release()
    }

    /** Opens the gate for good (guide finished, skipped, or never shown). */
    fun release() {
        released = true
        _held.value = false
    }

    val isHeld: Boolean get() = _held.value

    /** Suspends until the gate is open. Returns immediately for returning users. */
    suspend fun awaitOpen() { if (_held.value) _held.first { !it } }

    internal fun resetForTests() { released = false; _held.value = false }
}
