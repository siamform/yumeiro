/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.mtrevanced.service

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dev.asfakul.mtrevanced.MainActivity

/** Shares the on-screen video player with [VideoPlaybackService] for background playback. */
object VideoSessionBridge {
    @Volatile var player: Player? = null
}

/**
 * Keeps a video playing (audio + media notification + lock-screen controls) while the app is in
 * the background. It wraps the same ExoPlayer the video screen uses; it never owns or releases it.
 */
@OptIn(UnstableApi::class)
class VideoPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = VideoSessionBridge.player ?: run { stopSelf(); return }
        val open = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player).setId("mt_video").setSessionActivity(open).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        VideoSessionBridge.player?.pause()
        stopSelf()
    }

    override fun onDestroy() {
        session?.release() // the player itself belongs to the video screen
        session = null
        super.onDestroy()
    }
}
