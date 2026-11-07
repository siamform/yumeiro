/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.mtrevanced.cast

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaTrack
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.images.WebImage
import java.util.concurrent.Executors

/** Cast options: Google's default media receiver (no custom receiver app needed). */
class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions = CastOptions.Builder()
        .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
        .setStopReceiverApplicationWhenEndingSession(true)
        .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}

/**
 * Chromecast for online videos. Optional: devices without Google Play services simply don't show
 * the Cast button. Casting hands the stream URL to the TV, so sources that need special request
 * headers (Referer, cookies) may not play there — the app says so before casting.
 */
object CastSupport {
    private const val TAG = "CastSupport"
    @Volatile private var context: CastContext? = null
    @Volatile private var unavailable = false

    fun available(ctx: Context): Boolean = get(ctx) != null

    fun get(ctx: Context): CastContext? {
        context?.let { return it }
        if (unavailable) return null
        return runCatching {
            if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(ctx) != ConnectionResult.SUCCESS) { unavailable = true; return null }
            CastContext.getSharedInstance(ctx.applicationContext, Executors.newSingleThreadExecutor()).result
        }.onFailure { Log.w(TAG, "cast unavailable", it); unavailable = true }.getOrNull()?.also { context = it }
    }

    /** Runs [block] once when a cast session starts (or right away if one is running). */
    fun whenConnected(ctx: Context, block: () -> Unit) {
        val cc = get(ctx) ?: return
        if (connected(ctx)) { block(); return }
        val mgr = cc.sessionManager
        val l = object : com.google.android.gms.cast.framework.SessionManagerListener<com.google.android.gms.cast.framework.CastSession> {
            override fun onSessionStarted(session: com.google.android.gms.cast.framework.CastSession, sessionId: String) { mgr.removeSessionManagerListener(this, com.google.android.gms.cast.framework.CastSession::class.java); block() }
            override fun onSessionResumed(session: com.google.android.gms.cast.framework.CastSession, wasSuspended: Boolean) { mgr.removeSessionManagerListener(this, com.google.android.gms.cast.framework.CastSession::class.java); block() }
            override fun onSessionStartFailed(session: com.google.android.gms.cast.framework.CastSession, error: Int) { mgr.removeSessionManagerListener(this, com.google.android.gms.cast.framework.CastSession::class.java) }
            override fun onSessionStarting(session: com.google.android.gms.cast.framework.CastSession) {}
            override fun onSessionEnding(session: com.google.android.gms.cast.framework.CastSession) {}
            override fun onSessionEnded(session: com.google.android.gms.cast.framework.CastSession, error: Int) {}
            override fun onSessionResuming(session: com.google.android.gms.cast.framework.CastSession, sessionId: String) {}
            override fun onSessionResumeFailed(session: com.google.android.gms.cast.framework.CastSession, error: Int) {}
            override fun onSessionSuspended(session: com.google.android.gms.cast.framework.CastSession, reason: Int) {}
        }
        mgr.addSessionManagerListener(l, com.google.android.gms.cast.framework.CastSession::class.java)
    }

    fun connected(ctx: Context): Boolean = runCatching { get(ctx)?.sessionManager?.currentCastSession?.isConnected == true }.getOrDefault(false)

    /** Opens the system device picker (Chromecast / Google TV). */
    fun pickDevice(activity: Activity) {
        val cc = get(activity) ?: return
        runCatching {
            androidx.mediarouter.app.MediaRouteChooserDialog(activity).apply { routeSelector = cc.mergedSelector ?: return }.show()
        }.onFailure { Log.w(TAG, "chooser", it) }
    }

    data class Media(
        val url: String, val title: String, val subtitle: String?, val posterUrl: String?, val isHls: Boolean, val isDash: Boolean,
        val positionMs: Long, val subtitles: List<Pair<String, String>> = emptyList(),
    )

    /** Starts [media] on the connected device. Returns false when nothing is connected. */
    fun play(ctx: Context, media: Media): Boolean {
        val session = runCatching { get(ctx)?.sessionManager?.currentCastSession }.getOrNull() ?: return false
        val client = session.remoteMediaClient ?: return false
        val meta = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, media.title)
            media.subtitle?.let { putString(MediaMetadata.KEY_SUBTITLE, it) }
            media.posterUrl?.let { addImage(WebImage(Uri.parse(it))) }
        }
        val tracks = media.subtitles.mapIndexed { i, (label, url) ->
            MediaTrack.Builder((i + 1).toLong(), MediaTrack.TYPE_TEXT).setName(label).setSubtype(MediaTrack.SUBTYPE_SUBTITLES)
                .setContentId(url).setContentType(if (url.contains(".vtt", true)) "text/vtt" else "application/x-subrip").build()
        }
        val info = MediaInfo.Builder(media.url)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(when { media.isHls -> "application/x-mpegURL"; media.isDash -> "application/dash+xml"; else -> "video/mp4" })
            .setMetadata(meta)
            .apply { if (tracks.isNotEmpty()) setMediaTracks(tracks) }
            .build()
        return runCatching {
            client.load(MediaLoadRequestData.Builder().setMediaInfo(info).setAutoplay(true).setCurrentTime(media.positionMs).build())
            true
        }.onFailure { Log.w(TAG, "load", it) }.getOrDefault(false)
    }
}
