/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.Allocator

/**
 * R15 (device run 43626650174, api33 low-RAM): switching a stream's audio from AAC to AC3 failed
 * with `StuckPlayerException: Player stuck buffering and not loading`. The byte budget that keeps
 * 1080p/4K streams from exhausting the heap was already used by the video buffered ahead. The new
 * audio track had less than half a second buffered and, with size taking priority over time, the
 * load control refused to load it, so playback could never resume.
 *
 * The byte budget stays, but when the playable buffer (the shortest track) is below
 * [STARVING_US] loading is always allowed. The player loads the track that is behind first, so this
 * only lets the starving track catch up: a few megabytes at most, never an unbounded buffer.
 */
@OptIn(UnstableApi::class)
class StarvationSafeLoadControl(private val delegate: LoadControl) : LoadControl {
    // Every call ExoPlayer makes is forwarded explicitly: Kotlin's `by` delegation does not forward
    // Java default methods, which left onPrepared / onTracksSelected / back buffer on the
    // interface's empty or throwing defaults (StarvationLoadControlTest caught it).
    override fun onPrepared(playerId: PlayerId) = delegate.onPrepared(playerId)
    override fun onTracksSelected(parameters: LoadControl.Parameters, trackGroups: TrackGroupArray, trackSelections: Array<out ExoTrackSelection?>) =
        delegate.onTracksSelected(parameters, trackGroups, trackSelections)
    override fun onStopped(playerId: PlayerId) = delegate.onStopped(playerId)
    override fun onReleased(playerId: PlayerId) = delegate.onReleased(playerId)
    override fun getAllocator(playerId: PlayerId): Allocator = delegate.getAllocator(playerId)
    override fun getBackBufferDurationUs(playerId: PlayerId): Long = delegate.getBackBufferDurationUs(playerId)
    override fun retainBackBufferFromKeyframe(playerId: PlayerId): Boolean = delegate.retainBackBufferFromKeyframe(playerId)
    override fun shouldContinuePreloading(playerId: PlayerId, timeline: Timeline, mediaPeriodId: MediaSource.MediaPeriodId, bufferedDurationUs: Long): Boolean =
        delegate.shouldContinuePreloading(playerId, timeline, mediaPeriodId, bufferedDurationUs)
    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean = delegate.shouldStartPlayback(parameters)

    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean =
        delegate.shouldContinueLoading(parameters) ||
            (starving(parameters.bufferedDurationUs, parameters.playbackSpeed) && heapHeadroomMb() >= MIN_HEADROOM_MB)

    companion object {
        const val STARVING_US = 2_000_000L
        /** A starving track still never loads when the heap is nearly full (the OOM the budget prevents). */
        const val MIN_HEADROOM_MB = 16L

        private fun heapHeadroomMb(): Long {
            val rt = Runtime.getRuntime()
            return (rt.maxMemory() - (rt.totalMemory() - rt.freeMemory())) / (1024 * 1024)
        }

        /** Below this much playable media (at the current speed) a refusal would stall playback for good. */
        fun starving(bufferedDurationUs: Long, speed: Float): Boolean {
            val s = if (speed > 0f) speed else 1f
            return bufferedDurationUs < (STARVING_US * s).toLong()
        }
    }
}
