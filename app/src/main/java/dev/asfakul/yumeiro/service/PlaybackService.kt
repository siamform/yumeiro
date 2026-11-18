/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dev.asfakul.yumeiro.MainActivity
import dev.asfakul.yumeiro.player.equalizer.EqualizerManager

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    companion object {
        @Volatile
        var sharedPlayer: ExoPlayer? = null
            private set

        /**
         * G6-WL: wake mode for the item about to play. Streams (YouTube Music `mtyt:`, http/https/…)
         * need `WAKE_MODE_NETWORK` (wake lock + Wi-Fi lock) or Wi-Fi may enter power save with the
         * screen off and the song stalls; device files keep `WAKE_MODE_LOCAL` (no Wi-Fi lock, less
         * battery). Same rule as the video/stream player (StreamMediaFactory).
         */
        internal fun wakeModeFor(uri: String?): Int =
            if (uri != null && (uri.trim().startsWith(dev.asfakul.yumeiro.youtube.YouTubeAudioDataSource.SCHEME + ":", ignoreCase = true) ||
                    dev.asfakul.yumeiro.stream.StreamMediaFactory.isNetworkUri(uri))) C.WAKE_MODE_NETWORK else C.WAKE_MODE_LOCAL

        /**
         * Tests only: forget the process player so a test can start from a cold process. Not
         * released: ExoPlayer.release() waits for its playback thread against SystemClock, which
         * Robolectric's paused clock never advances (CI 43831093449 hung in the unit tests).
         */
        @androidx.annotation.VisibleForTesting
        internal fun forgetSharedPlayerForTest() = synchronized(this) { sharedPlayer = null }

        fun getOrCreatePlayer(context: android.content.Context): ExoPlayer {
            val existing = sharedPlayer
            if (existing != null) return existing
            synchronized(this) {
                val current = sharedPlayer
                if (current != null) return current
                // mtyt:// (YouTube Music) URIs resolve to fresh stream URLs on the loader thread.
                val sources = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                    dev.asfakul.yumeiro.youtube.YouTubeAudioDataSource.Factory(context.applicationContext)
                )
                val player = ExoPlayer.Builder(context.applicationContext).setMediaSourceFactory(sources).build().apply {
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build()
                    setAudioAttributes(audioAttributes, true)
                    setHandleAudioBecomingNoisy(true)
                    setWakeMode(C.WAKE_MODE_LOCAL)
                }

                player.addAnalyticsListener(object : AnalyticsListener {
                    override fun onAudioSessionIdChanged(
                        eventTime: AnalyticsListener.EventTime,
                        audioSessionId: Int
                    ) {
                        if (audioSessionId > 0) {
                            EqualizerManager.getInstance(context).attachAudioSession(audioSessionId)
                        }
                    }
                })

                val app = context.applicationContext
                player.addListener(object : androidx.media3.common.Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        dev.asfakul.yumeiro.player.ListeningTime.playerIsPlaying(app, isPlaying)
                    }

                    override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                        player.setWakeMode(wakeModeFor(mediaItem?.localConfiguration?.uri?.toString()))
                    }
                })

                // R16: scrobbling follows the shared player, not the screen (see BackgroundPlaybackWatch).
                dev.asfakul.yumeiro.player.BackgroundPlaybackWatch.attach(app, player)

                sharedPlayer = player
                return player
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val player = getOrCreatePlayer(this)

        // Attach audio session if already initialized
        val sessionId = player.audioSessionId
        if (sessionId > 0) {
            EqualizerManager.getInstance(this).attachAudioSession(sessionId)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            // Nothing is playing: stop the paused player and the service when the app is swiped away.
            runCatching { player?.stop() }
            stopSelf()
        }
    }

    override fun onDestroy() {
        // Only the session is released. The ExoPlayer is process-wide and still referenced by
        // MusicPlayerManager; releasing it here left the UI driving a dead player (silent
        // failures and crashes after the service was recreated).
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
