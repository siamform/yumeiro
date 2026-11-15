/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * R17 (G3 proposal): sleep timer for the video player (before, only music had one).
 *
 * It reuses [SleepTimer] (countdown with a volume fade, or "end of this video"). The timer is
 * process-wide, not tied to one player screen, because the next episode opens a new player screen:
 * a countdown keeps running across episodes, and "end of this video" pauses the next episode as
 * soon as it starts (that is the point of it: no autoplay into the night).
 *
 * The player screen calls [attach] when it shows a video and [detach] when it goes away. Every
 * player call happens on the main thread (ExoPlayer's application looper), because the timer's
 * scope uses [Dispatchers.Main]. With no player attached the timer still counts down; when it ends
 * there is nothing to pause, and it just clears.
 */
object VideoSleepTimer {
    /** "End of this video" is dropped when the next video comes later than this (the user moved on). */
    internal const val NEXT_VIDEO_WINDOW_MS = 15_000L

    private var player: Player? = null
    private var videoId: String? = null
    private var detachedAt = 0L

    val timer: SleepTimer by lazy {
        SleepTimer(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            positionMs = { player?.currentPosition ?: 0L },
            durationMs = { player?.duration?.takeIf { it > 0 } ?: 0L },
            trackId = { videoId },
            setVolume = { v -> player?.volume = v },
            pause = { player?.pause() },
        )
    }

    /** Main thread. [id] identifies the video, so "end of this video" sees the next one start. */
    fun attach(p: Player, id: String, now: Long = System.currentTimeMillis()) {
        val s = timer.state.value
        if (s is SleepTimer.State.EndOfTrack && player == null && detachedAt > 0 && now - detachedAt > NEXT_VIDEO_WINDOW_MS) {
            // The player was closed a while ago and this is a new choice by the user, not autoplay.
            timer.cancel()
        }
        player = p
        videoId = id
    }

    /** Main thread. Gives the volume back (a fade may be running) and forgets [p]. */
    fun detach(p: Player, now: Long = System.currentTimeMillis()) {
        if (player !== p) return
        runCatching { p.volume = 1f }
        player = null
        detachedAt = now
        // videoId is kept: "end of this video" compares it with the next video's id.
    }
}
