/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import android.content.Context
import androidx.media3.common.Player
import dev.asfakul.yumeiro.data.model.AudioTrack
import dev.asfakul.yumeiro.data.model.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * R16 (G5/G6): what must keep working while music plays with no screen open.
 *
 * Scrobbling watched MusicPlayerManager, which belongs to the screen's ViewModel and is released
 * on Back-exit, while the shared player in PlaybackService keeps playing. Listens made after that
 * were never scrobbled. This watch is attached once to the shared player (process scope), builds
 * the same snapshot from the player itself, and ticks once a second only while playing.
 */
object BackgroundPlaybackWatch {
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()
    @Volatile private var attached = false

    /** Main thread. Safe to call more than once. */
    fun attach(context: Context, player: Player) {
        if (attached) return
        attached = true
        val app = context.applicationContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        var ticker: Job? = null
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                // G5-PC1: with no screen listening (swiped from recents), song changes are still
                // added to Recently played and play counts. A new queue is recorded by playTrack().
                if (mediaItem != null && reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                    dev.asfakul.yumeiro.data.MusicPlayHistory.recordWithoutScreen(app, mediaItem.mediaId)
                }
            }
            override fun onEvents(p: Player, events: Player.Events) {
                _state.value = snapshot(p)
                saveContinue(app, p)
                if (p.isPlaying && ticker?.isActive != true) {
                    ticker = scope.launch { while (isActive) { delay(1_000); _state.value = snapshot(p); saveContinue(app, p) } }
                } else if (!p.isPlaying) { ticker?.cancel(); ticker = null }
            }
        })
        _state.value = snapshot(player)
        dev.asfakul.yumeiro.scrobble.Scrobbler.watch(app, state, scope)
    }

    private var continueKey: Triple<String, Boolean, Long>? = null

    /**
     * G5-PC2: Home → Continue ("Jump back in") was saved only by the screen's ViewModel (every 5 s,
     * on pause and on song change). With no screen listening (swiped from recents) the watch saves it
     * on the same steps, so a later process death doesn't bring back an older song and position.
     */
    private fun saveContinue(context: Context, p: Player) {
        if (dev.asfakul.yumeiro.data.MusicPlayHistory.screenRecorders.get() > 0) { continueKey = null; return }
        val item = p.currentMediaItem ?: return
        if (item.mediaId.isBlank()) return
        val pos = p.currentPosition.coerceAtLeast(0L)
        val key = Triple(item.mediaId, p.isPlaying, pos / 5_000)
        if (key == continueKey) return
        continueKey = key
        val track = MusicPlayerManager.queuedTrack(item.mediaId) ?: snapshot(p).currentTrack ?: return
        val dur = p.duration.takeIf { it > 0 } ?: track.durationMs
        LastPlayed.saveAudio(context, track, pos, dur)
    }

    internal fun snapshot(p: Player): PlaybackState {
        val item = p.currentMediaItem
        val m = item?.mediaMetadata
        val dur = p.duration.takeIf { it > 0 } ?: 0L
        val track = item?.let {
            AudioTrack(
                id = it.mediaId, title = m?.title?.toString().orEmpty(), artist = m?.artist?.toString().orEmpty(),
                album = m?.albumTitle?.toString().orEmpty(), durationMs = dur, uri = it.localConfiguration?.uri?.toString().orEmpty(),
            )
        }
        return PlaybackState(
            currentTrack = track, isPlaying = p.isPlaying, currentPositionMs = p.currentPosition.coerceAtLeast(0L),
            durationMs = dur, currentQueueIndex = if (item == null) -1 else p.currentMediaItemIndex,
        )
    }
}
