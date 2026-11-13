/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Last known state of the video player (state, first frame, size, error). Logged under the
 * "MTPlayback" tag so playback problems can be diagnosed from logcat, and read by device tests.
 */
object PlaybackDiagnostics {
    data class Snapshot(
        val uri: String = "",
        val state: String = "IDLE",
        val firstFrame: Boolean = false,
        val width: Int = 0,
        val height: Int = 0,
        val positionMs: Long = 0,
        val error: String? = null,
        /** Round 10: what the watch page shows under the player (see PlaybackFacts). */
        val durationMs: Long = 0,
        val live: Boolean = false,
        /** BCP-47 / ISO code of the selected audio track, or null when the stream doesn't say. */
        val audioLanguage: String? = null,
        val audioLabel: String? = null,
        val audioTracks: Int = 0,
        val audioChannels: Int = 0,
        /** Bits per second of the selected video + audio, 0 when unknown (used for a size estimate). */
        val bitrate: Int = 0,
        /** Exact size when the source knows it (device files), else 0. */
        val sizeBytes: Long = 0,
    )

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    fun reset(uri: String, sizeBytes: Long = 0) = update { Snapshot(uri = uri, sizeBytes = sizeBytes.coerceAtLeast(0)) }

    fun update(change: (Snapshot) -> Snapshot) {
        val next = change(_state.value)
        if (next != _state.value) {
            _state.value = next
            Log.i("MTPlayback", "${next.state} frame=${next.firstFrame} ${next.width}x${next.height} err=${next.error} uri=${next.uri.take(160)}")
        }
    }
}
