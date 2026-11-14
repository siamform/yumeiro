/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dev.asfakul.yumeiro.data.model.AudioTrack
import dev.asfakul.yumeiro.data.model.PlaybackState
import dev.asfakul.yumeiro.data.model.PlayerRepeatMode
import dev.asfakul.yumeiro.service.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class MusicPlayerManager(
    private val context: Context,
    private val onTrackChanged: ((String) -> Unit)? = null
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null

    private val player: ExoPlayer = PlaybackService.getOrCreatePlayer(context)

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var currentQueue: List<AudioTrack> = emptyList()

    /**
     * A MediaController bound to [PlaybackService]. Binding keeps the media session alive and
     * lets Media3 promote the service to foreground itself when playback starts. The old code
     * called startForegroundService() manually, which crashed with
     * ForegroundServiceDidNotStartInTimeException whenever playback did not start (bad file,
     * pause, error) within the system deadline.
     */
    private var controllerFuture: ListenableFuture<MediaController>? = null

    /** Decoder name from the analytics callback (the Format alone doesn't carry it). */
    private var audioDecoderName: String? = null

    private fun refreshCodec() {
        val selected = runCatching {
            player.currentTracks.groups.firstOrNull { it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO && it.isSelected }
                ?.let { g -> (0 until g.length).firstOrNull { g.isTrackSelected(it) }?.let { g.getTrackFormat(it) } }
        }.getOrNull()
        val decoded = runCatching { player.audioFormat }.getOrNull()
        // The decoded format knows the real sample rate and PCM depth; the track format the bitrate.
        val a = AudioCodecInfo.from(selected, audioDecoderName)
        val b = AudioCodecInfo.from(decoded, audioDecoderName)
        val info = when {
            a == null -> b
            b == null -> a
            else -> a.copy(
                mime = a.mime ?: b.mime, codecs = a.codecs ?: b.codecs, bitrate = a.bitrate ?: b.bitrate,
                sampleRate = a.sampleRate ?: b.sampleRate, channels = a.channels ?: b.channels, bitDepth = a.bitDepth ?: b.bitDepth,
            )
        }
        if (info != _playbackState.value.audioCodec) _playbackState.update { it.copy(audioCodec = info) }
    }

    private val analyticsListener = object : androidx.media3.exoplayer.analytics.AnalyticsListener {
        override fun onAudioDecoderInitialized(
            eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
            decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long,
        ) { audioDecoderName = decoderName; refreshCodec() }

        override fun onAudioInputFormatChanged(
            eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
            format: androidx.media3.common.Format,
            decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?,
        ) { refreshCodec() }
    }

    private val playerListener = object : Player.Listener {
        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) { refreshCodec() }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playbackState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                startProgressTracker()
            } else {
                stopProgressTracker()
                updateCurrentPosition()
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_READY -> {
                    _playbackState.update {
                        it.copy(
                            durationMs = player.duration.coerceAtLeast(0L),
                            bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L),
                            errorMessage = null
                        )
                    }
                }
                Player.STATE_ENDED -> {
                    _playbackState.update {
                        it.copy(
                            isPlaying = false,
                            currentPositionMs = it.durationMs
                        )
                    }
                }
                Player.STATE_BUFFERING -> {}
                Player.STATE_IDLE -> {}
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val currentIndex = player.currentMediaItemIndex
            if (currentIndex in currentQueue.indices) {
                val track = currentQueue[currentIndex]
                _playbackState.update {
                    it.copy(
                        currentTrack = track,
                        currentQueueIndex = currentIndex,
                        currentPositionMs = 0L,
                        durationMs = if (track.durationMs > 0) track.durationMs else 0L,
                        errorMessage = null,
                        audioCodec = null,
                    )
                }
                // playTrack() already records the first track of a newly loaded queue.
                if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                    onTrackChanged?.invoke(track.id)
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val message = when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "Audio file not found on device"
                PlaybackException.ERROR_CODE_DECODING_FAILED -> "Unsupported audio format"
                else -> "Playback error: ${error.localizedMessage ?: "File unplayable"}"
            }
            _playbackState.update {
                it.copy(
                    isPlaying = false,
                    errorMessage = message
                )
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _playbackState.update { it.copy(isShuffleEnabled = shuffleModeEnabled) }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            val mode = when (repeatMode) {
                Player.REPEAT_MODE_ONE -> PlayerRepeatMode.ONE
                Player.REPEAT_MODE_ALL -> PlayerRepeatMode.ALL
                else -> PlayerRepeatMode.OFF
            }
            _playbackState.update { it.copy(repeatMode = mode) }
        }
    }

    init {
        player.addListener(playerListener)
        runCatching { player.addAnalyticsListener(analyticsListener) }
        refreshCodec()
        // If music is already playing (activity recreated), reconnect to the session now;
        // otherwise connect lazily on first playback.
        if (player.mediaItemCount > 0) connectSession()
        // Restore playback state if player was already playing in background
        restoreExistingState()
        // Last.fm scrobbling (no-op unless signed in, enabled and not incognito).
        dev.asfakul.yumeiro.scrobble.Scrobbler.watch(context, playbackState, scope)
        scope.launch(Dispatchers.IO) { runCatching { dev.asfakul.yumeiro.scrobble.Scrobbler.flush(context) } }
    }

    private fun connectSession() {
        if (controllerFuture != null) return
        // Robolectric's bindService shadow hands Media3 a null ComponentName; unit tests run
        // without the session service (real devices always bind).
        if (android.os.Build.FINGERPRINT == "robolectric") return
        controllerFuture = runCatching {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            MediaController.Builder(context, token).buildAsync()
        }.onFailure { Log.w(TAG, "Unable to connect media session", it) }.getOrNull()
    }

    /** Runs a player command, never letting an exception reach the UI thread. */
    private inline fun safely(action: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "Player command failed: $action", e)
            _playbackState.update { it.copy(errorMessage = "Playback error: ${e.localizedMessage ?: action}") }
        }
    }

    private fun restoreExistingState() {
        val isPlaying = player.isPlaying
        val duration = player.duration.coerceAtLeast(0L)
        val position = player.currentPosition.coerceAtLeast(0L)
        val repeat = when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> PlayerRepeatMode.ONE
            Player.REPEAT_MODE_ALL -> PlayerRepeatMode.ALL
            else -> PlayerRepeatMode.OFF
        }

        _playbackState.update {
            it.copy(
                isPlaying = isPlaying,
                durationMs = duration,
                currentPositionMs = position,
                isShuffleEnabled = player.shuffleModeEnabled,
                repeatMode = repeat
            )
        }

        if (isPlaying) {
            startProgressTracker()
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                updateCurrentPosition()
                delay(300)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun updateCurrentPosition() {
        val pos = player.currentPosition.coerceAtLeast(0L)
        val dur = player.duration.let { if (it > 0) it else _playbackState.value.durationMs }
        val buf = player.bufferedPosition.coerceAtLeast(0L)
        _playbackState.update {
            it.copy(
                currentPositionMs = pos,
                durationMs = dur,
                bufferedPositionMs = buf
            )
        }
    }

    private fun createMediaItem(track: AudioTrack): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .apply {
                track.albumArtUri?.let { artUri ->
                    setArtworkUri(Uri.parse(artUri))
                }
            }
            .build()

        return MediaItem.Builder()
            .setUri(Uri.parse(track.uri))
            .setMediaId(track.id)
            .setMediaMetadata(metadata)
            .build()
    }

    fun playTrack(track: AudioTrack, queue: List<AudioTrack> = listOf(track)) {
        connectSession()
        currentQueue = if (queue.isNotEmpty()) queue else listOf(track)
        val startIndex = currentQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)

        val mediaItems = currentQueue.map { createMediaItem(it) }

        _playbackState.update {
            it.copy(
                currentTrack = track,
                queue = currentQueue,
                currentQueueIndex = startIndex,
                currentPositionMs = 0L,
                durationMs = track.durationMs,
                errorMessage = null
            )
        }

        try {
            player.setMediaItems(mediaItems, startIndex, 0L)
            player.prepare()
            player.play()
            onTrackChanged?.invoke(track.id)
        } catch (e: Exception) {
            _playbackState.update {
                it.copy(errorMessage = "Failed to load audio: ${e.localizedMessage}")
            }
        }
    }

    fun playNext(track: AudioTrack) = safely("play next") {
        if (currentQueue.isEmpty() || player.mediaItemCount == 0) {
            playTrack(track, listOf(track))
            return@safely
        }
        val insertIndex = (player.currentMediaItemIndex + 1).coerceIn(0, currentQueue.size)
        currentQueue = currentQueue.toMutableList().apply { add(insertIndex, track) }
        player.addMediaItem(insertIndex.coerceAtMost(player.mediaItemCount), createMediaItem(track))
        _playbackState.update { it.copy(queue = currentQueue) }
    }

    fun addToQueue(track: AudioTrack) = addMultipleToQueue(listOf(track))

    fun addMultipleToQueue(tracks: List<AudioTrack>) = safely("add to queue") {
        if (tracks.isEmpty()) return@safely
        if (currentQueue.isEmpty() || player.mediaItemCount == 0) {
            playTrack(tracks.first(), tracks)
            return@safely
        }
        currentQueue = currentQueue + tracks
        player.addMediaItems(tracks.map { createMediaItem(it) })
        _playbackState.update { it.copy(queue = currentQueue) }
    }

    fun removeFromQueue(index: Int) = safely("remove from queue") {
        if (index in currentQueue.indices && index < player.mediaItemCount) {
            currentQueue = currentQueue.toMutableList().apply { removeAt(index) }
            player.removeMediaItem(index)
            _playbackState.update { it.copy(queue = currentQueue) }
        }
    }

    fun togglePlayPause() = safely("play/pause") {
        if (player.playbackState == Player.STATE_IDLE && currentQueue.isNotEmpty()) {
            val track = _playbackState.value.currentTrack ?: currentQueue.first()
            playTrack(track, currentQueue)
            return@safely
        }
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(player.currentMediaItemIndex, 0L)
            connectSession()
            player.play()
        }
    }

    /** Stops playback, clears the queue and resets the UI state (mini player disappears). */
    fun stop() = safely("stop") {
        stopProgressTracker()
        player.stop()
        player.clearMediaItems()
        currentQueue = emptyList()
        val keep = _playbackState.value
        _playbackState.value = PlaybackState(
            isShuffleEnabled = keep.isShuffleEnabled,
            repeatMode = keep.repeatMode
        )
    }

    /** Player volume 0..1 (used by the sleep timer fade-out; separate from the system volume). */
    fun setPlayerVolume(v: Float) = safely("volume") { player.volume = v.coerceIn(0f, 1f) }

    fun pause() {
        if (player.isPlaying) {
            player.pause()
        }
    }

    fun seekTo(positionMs: Long) = safely("seek") {
        player.seekTo(positionMs.coerceAtLeast(0L))
        _playbackState.update { it.copy(currentPositionMs = positionMs) }
    }

    fun next() = safely("next") {
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        } else if (_playbackState.value.repeatMode == PlayerRepeatMode.ALL && currentQueue.isNotEmpty()) {
            player.seekTo(0, 0L)
        }
    }

    fun previous() = safely("previous") {
        if (player.currentPosition > 3000L) {
            player.seekTo(0L)
        } else if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        } else {
            player.seekTo(0L)
        }
    }

    fun toggleShuffle() = safely("shuffle") {
        val newShuffle = !player.shuffleModeEnabled
        player.shuffleModeEnabled = newShuffle
        _playbackState.update { it.copy(isShuffleEnabled = newShuffle) }
    }

    fun cycleRepeatMode() = safely("repeat") {
        val nextMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        player.repeatMode = nextMode
    }

    fun playQueueIndex(index: Int) = safely("queue index") {
        if (index in currentQueue.indices && index < player.mediaItemCount) {
            player.seekTo(index, 0L)
            player.play()
        }
    }

    fun clearError() {
        _playbackState.update { it.copy(errorMessage = null) }
    }

    fun release() {
        stopProgressTracker()
        player.removeListener(playerListener)
        runCatching { player.removeAnalyticsListener(analyticsListener) }
        scope.cancel()
        controllerFuture?.let { future -> runCatching { MediaController.releaseFuture(future) } }
        controllerFuture = null
        // The shared player itself is not released so background playback continues.
    }

    private companion object {
        const val TAG = "MusicPlayerManager"
    }
}
