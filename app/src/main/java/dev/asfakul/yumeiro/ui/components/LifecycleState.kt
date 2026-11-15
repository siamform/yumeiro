/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.currentStateAsState

/**
 * R16 (G4 Y6): true only while the screen is at least STARTED. Carousels key their auto-advance
 * on it, so the timer stops when the app goes to the background or the screen is off, instead
 * of waking every few seconds and queueing a scroll for when frames return.
 */
@androidx.compose.runtime.Composable
fun rememberIsStarted(): Boolean {
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val state by lifecycle.currentStateAsState()
    return state.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
}
