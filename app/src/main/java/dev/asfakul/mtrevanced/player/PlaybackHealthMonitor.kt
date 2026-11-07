/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.mtrevanced.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import java.io.IOException

/** Snapshot of stream / playback health shown in the video info dialog. */
data class PlaybackHealth(
    val bandwidthBps: Long = 0,
    val videoBitrate: Int = Format.NO_VALUE,
    val width: Int = 0,
    val height: Int = 0,
    val bufferedMs: Long = 0,
    val liveOffsetMs: Long? = null,
    val droppedFrames: Int = 0,
    val rebuffers: Int = 0,
    val loadErrors: Int = 0,
    val qualitySwitches: Int = 0,
    val host: String? = null,
)

/**
 * Watches the player for bandwidth, buffering, dropped frames and load errors, and — when the
 * network can't sustain the current quality (≥3 rebuffers within a minute) — steps quality down,
 * but only when the stream actually has several variants.
 */
@OptIn(UnstableApi::class)
class PlaybackHealthMonitor(
    private val player: ExoPlayer,
    private val onLowBandwidth: (LowBandwidthAction) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) : AnalyticsListener {

    enum class LowBandwidthAction { REVERTED_TO_AUTO, CAPPED_BITRATE }

    @Volatile var health = PlaybackHealth(); private set
    private val rebufferTimes = ArrayDeque<Long>()
    private var lastStepDownAt = 0L
    private var wasReady = false

    fun attach() = player.addAnalyticsListener(this)
    fun detach() = player.removeAnalyticsListener(this)

    /** Fresh snapshot including the live values read from the player. */
    fun snapshot(): PlaybackHealth {
        val offset = if (player.isCurrentMediaItemLive) player.currentLiveOffset.takeIf { it != C.TIME_UNSET } else null
        val vf = player.videoFormat
        return health.copy(
            bufferedMs = (player.bufferedPosition - player.currentPosition).coerceAtLeast(0),
            liveOffsetMs = offset,
            videoBitrate = vf?.bitrate ?: health.videoBitrate,
            width = vf?.width?.takeIf { it > 0 } ?: health.width,
            height = vf?.height?.takeIf { it > 0 } ?: health.height,
        ).also { health = it }
    }

    fun reset() { health = PlaybackHealth(); rebufferTimes.clear(); wasReady = false }

    override fun onBandwidthEstimate(eventTime: AnalyticsListener.EventTime, totalLoadTimeMs: Int, totalBytesLoaded: Long, bitrateEstimate: Long) {
        health = health.copy(bandwidthBps = bitrateEstimate)
    }

    override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
        health = health.copy(droppedFrames = health.droppedFrames + droppedFrames)
    }

    override fun onDownstreamFormatChanged(eventTime: AnalyticsListener.EventTime, mediaLoadData: MediaLoadData) {
        val f = mediaLoadData.trackFormat ?: return
        if (mediaLoadData.trackType == C.TRACK_TYPE_VIDEO || (f.height > 0 && mediaLoadData.trackType == C.TRACK_TYPE_DEFAULT)) {
            val switched = health.height != 0 && f.height != health.height
            health = health.copy(
                videoBitrate = f.bitrate, width = f.width.coerceAtLeast(0), height = f.height.coerceAtLeast(0),
                qualitySwitches = health.qualitySwitches + if (switched) 1 else 0,
            )
        }
    }

    override fun onLoadError(eventTime: AnalyticsListener.EventTime, loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData, error: IOException, wasCanceled: Boolean) {
        health = health.copy(loadErrors = health.loadErrors + 1, host = loadEventInfo.uri.host ?: health.host)
    }

    override fun onLoadCompleted(eventTime: AnalyticsListener.EventTime, loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData) {
        loadEventInfo.uri.host?.let { if (it != health.host) health = health.copy(host = it) }
    }

    override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, state: Int) {
        when (state) {
            Player.STATE_READY -> wasReady = true
            Player.STATE_BUFFERING -> if (wasReady && player.playWhenReady) onRebuffer()
            Player.STATE_IDLE, Player.STATE_ENDED -> wasReady = false
        }
    }

    private fun onRebuffer() {
        val t = now()
        health = health.copy(rebuffers = health.rebuffers + 1)
        rebufferTimes.addLast(t)
        while (rebufferTimes.isNotEmpty() && t - rebufferTimes.first() > 60_000L) rebufferTimes.removeFirst()
        if (rebufferTimes.size >= 3 && t - lastStepDownAt > 60_000L) stepDown(t)
    }

    private fun stepDown(t: Long) {
        val videoTracks = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }.sumOf { it.length }
        if (videoTracks < 2) return // single-quality stream: nothing to adapt
        val params = player.trackSelectionParameters
        lastStepDownAt = t
        rebufferTimes.clear()
        if (params.overrides.values.any { it.type == C.TRACK_TYPE_VIDEO }) {
            player.trackSelectionParameters = params.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO).build()
            onLowBandwidth(LowBandwidthAction.REVERTED_TO_AUTO)
        } else {
            val estimate = health.bandwidthBps.takeIf { it > 0 } ?: return
            val current = health.videoBitrate.takeIf { it > 0 }?.toLong() ?: estimate
            val cap = (minOf(estimate, current) * 3 / 4).coerceIn(150_000L, Int.MAX_VALUE.toLong()).toInt()
            if (params.maxVideoBitrate <= cap) return
            player.trackSelectionParameters = params.buildUpon().setMaxVideoBitrate(cap).build()
            onLowBandwidth(LowBandwidthAction.CAPPED_BITRATE)
        }
    }
}
