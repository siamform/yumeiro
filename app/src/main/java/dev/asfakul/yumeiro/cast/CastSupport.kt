/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.cast

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
    @Volatile private var starting = false
    private val executor by lazy { Executors.newSingleThreadExecutor() }
    private val _ready = kotlinx.coroutines.flow.MutableStateFlow(false)

    /**
     * Starts loading the Cast module once, without blocking. `getSharedInstance(context, executor)`
     * returns a Task that completes on [executor]; reading `.result` right away threw "Task is not
     * yet complete", which was caught and marked Cast unavailable for the whole process.
     */
    fun warm(ctx: Context) {
        if (context != null || unavailable || starting) return
        starting = true
        runCatching {
            if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(ctx) != ConnectionResult.SUCCESS) { unavailable = true; starting = false; return }
            CastContext.getSharedInstance(ctx.applicationContext, executor).addOnCompleteListener { t ->
                val cc = if (t.isSuccessful) t.result else null
                if (cc != null) { context = cc; _ready.value = true }
                else { Log.w(TAG, "cast unavailable", t.exception); unavailable = true }
                starting = false
            }
        }.onFailure { Log.w(TAG, "cast unavailable", it); unavailable = true; starting = false }
    }

    /** True once the Cast module is loaded; starts loading it. For the Cast button. */
    fun ready(ctx: Context): kotlinx.coroutines.flow.StateFlow<Boolean> { warm(ctx); return _ready }

    fun available(ctx: Context): Boolean = get(ctx) != null

    /** The loaded CastContext, or null while it is still loading (or Cast is unavailable). */
    fun get(ctx: Context): CastContext? {
        context?.let { return it }
        warm(ctx)
        return context
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
        // Cast receivers show only TTML, WebVTT and CEA-608/708 (developers.google.com/cast/docs/media);
        // SRT tracks were sent as application/x-subrip and never appeared. Send the formats it can show.
        val tracks = castableSubtitles(media.subtitles).mapIndexed { i, (label, url, type) ->
            MediaTrack.Builder((i + 1).toLong(), MediaTrack.TYPE_TEXT).setName(label).setSubtype(MediaTrack.SUBTYPE_SUBTITLES)
                .setContentId(url).setContentType(type).build()
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

    /** Subtitles a Cast receiver can show, with their MIME type (pure; unit-tested). */
    internal fun castableSubtitles(subs: List<Pair<String, String>>): List<Triple<String, String, String>> = subs.mapNotNull { (label, url) ->
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        when {
            path.endsWith(".vtt") || path.endsWith(".webvtt") -> Triple(label, url, "text/vtt")
            path.endsWith(".ttml") || path.endsWith(".dfxp") || path.endsWith(".xml") -> Triple(label, url, "application/ttml+xml")
            else -> null
        }
    }
}
