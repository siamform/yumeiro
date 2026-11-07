/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
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
