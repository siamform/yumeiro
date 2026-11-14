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
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dev.asfakul.yumeiro.MainActivity

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
        // No video screen (for example the system restarted the service after the process died):
        // nothing to play. onStartCommand stops the service safely.
        val player = VideoSessionBridge.player ?: return
        val open = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player).setId("mt_video").setSessionActivity(open).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /**
     * R15 (device run 43626650174): `ForegroundServiceDidNotStartInTimeException` killed the app.
     * Media3 returns START_STICKY, so after the process dies while a video plays in the background
     * the system restarts this service and expects startForeground() within seconds. The old code
     * called stopSelf() from onCreate without it, and the system then crashed the app.
     * A video cannot continue without its screen, so the service is never sticky. A start
     * without a session is answered with the required foreground call and then stopped.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (session == null) {
            stopSafely()
            return START_NOT_STICKY
        }
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    private fun stopSafely() {
        try {
            val nm = getSystemService(android.app.NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm?.getNotificationChannel(SHUTDOWN_CHANNEL) == null) {
                nm?.createNotificationChannel(
                    android.app.NotificationChannel(SHUTDOWN_CHANNEL, "Video playback", android.app.NotificationManager.IMPORTANCE_MIN),
                )
            }
            val n = androidx.core.app.NotificationCompat.Builder(this, SHUTDOWN_CHANNEL)
                .setSmallIcon(dev.asfakul.yumeiro.R.drawable.ic_stat_yumeiro)
                .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_SECRET)
                .setForegroundServiceBehavior(androidx.core.app.NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
                .setOnlyAlertOnce(true)
                .build()
            androidx.core.app.ServiceCompat.startForeground(
                this, SHUTDOWN_ID, n,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
            )
            androidx.core.app.ServiceCompat.stopForeground(this, androidx.core.app.ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
            // Not started as a foreground service, or not allowed now: nothing is owed to the system.
        } finally {
            stopSelf()
        }
    }

    private companion object {
        const val SHUTDOWN_CHANNEL = "video_playback_stop"
        const val SHUTDOWN_ID = 4107
    }

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
