/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import androidx.media3.common.C
import dev.asfakul.yumeiro.service.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * G6-ST1: the music sleep timer for the whole process.
 *
 * It used to be built on the screen's `viewModelScope` and to read the screen's
 * `MusicPlayerManager`. A swipe from recents or a Back exit clears that ViewModel while
 * `PlaybackService` keeps the shared player going, so the timer was cancelled and the music played
 * on all night. Now the timer runs on a process scope (main thread, where ExoPlayer must be used)
 * and reads and pauses `PlaybackService.sharedPlayer` directly, like the service-owned timers in
 * InnerTune v0.5.10 and Metrolist v14.0.0 (idea only). The screen shows the same instance.
 */
object MusicSleepTimer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private fun player() = PlaybackService.sharedPlayer

    val timer: SleepTimer by lazy {
        SleepTimer(
            scope = scope,
            positionMs = { player()?.currentPosition ?: 0L },
            durationMs = { player()?.duration?.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L },
            trackId = { player()?.currentMediaItem?.mediaId },
            setVolume = { v -> player()?.volume = v.coerceIn(0f, 1f) },
            // playWhenReady = false even while buffering: a check on isPlaying skipped the pause
            // when the timer fired during a stall, and the song resumed afterwards.
            pause = { player()?.pause() },
        )
    }
}
