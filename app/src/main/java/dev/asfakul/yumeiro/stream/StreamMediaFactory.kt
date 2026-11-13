/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.drm.DefaultDrmSessionManagerProvider
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import dev.asfakul.yumeiro.network.NetworkClient
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.util.Collections
import java.util.WeakHashMap
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds ExoPlayer instances and MediaItems that can play local files and every supported network
 * format (HLS, DASH, SmoothStreaming, RTSP, progressive incl. MPEG-TS) with per-stream headers & DRM.
 */
@OptIn(UnstableApi::class)
object StreamMediaFactory {
    /** Media source factory of each player, so separate audio tracks can be merged with the video. */
    private val factories: MutableMap<ExoPlayer, MediaSource.Factory> = Collections.synchronizedMap(WeakHashMap())
    private val adFilters: MutableMap<ExoPlayer, AdFilterDataSource.Factory> = Collections.synchronizedMap(WeakHashMap())

    /** True for links that are fetched over the network (as opposed to local files / content URIs). */
    fun isNetworkUri(uri: String): Boolean = uri.trim().lowercase().let {
        it.startsWith("http://") || it.startsWith("https://") || it.startsWith("rtsp://") || it.startsWith("rtmp://")
    }

    /** Media buffer budget for a heap of [memoryClassMb]: ~1/6 of it, kept between 16 and 64 MB. */
    fun bufferBudgetBytes(memoryClassMb: Int): Int = (memoryClassMb / 6).coerceIn(16, 64) * 1024 * 1024

    fun createPlayer(context: Context, uri: String): ExoPlayer {
        val appContext = context.applicationContext
        val defaultUa = Util.getUserAgent(appContext, "Yumeiro")
        // Headers are looked up on every connection so a stream resolved (or refreshed) after the
        // player was created still gets its Referer / Cookie / token headers.
        val http = RequestAwareHttpFactory(appContext, defaultUa) { StreamRepository.requestFor(uri) }
        // HLS playlists pass through the ad-break filter (the mid-stream "black screen" ads).
        val adFilter = AdFilterDataSource.Factory(DefaultDataSource.Factory(appContext, http))
        val dataSourceFactory: DataSource.Factory = SafeSubtitleDataSource.Factory(adFilter)
        val defaultDrm = DefaultDrmSessionManagerProvider().apply { setDrmHttpDataSourceFactory(http) }
        // Progressive files without a seek index (MP3/AAC/AMR streams, some TS) used to be
        // "unseekable": duration showed --:-- and every jump restarted the file at 0:00. Constant
        // bitrate seeking estimates both from the byte length, so those sources skip like any other.
        val extractors = androidx.media3.extractor.DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setConstantBitrateSeekingAlwaysEnabled(true)
        val msf = DefaultMediaSourceFactory(dataSourceFactory, extractors)
            .setSubtitleParserFactory(SniffingSubtitleParserFactory())
            .setLoadErrorHandlingPolicy(dev.asfakul.yumeiro.player.StreamLoadErrorPolicy())
            .setLiveMinSpeed(0.97f)
            .setLiveMaxSpeed(1.03f)
            .setDrmSessionManagerProvider(DrmSessionManagerProvider { item ->
                val request = StreamRepository.requestFor(uri)
                if (request != null && request.drmScheme == DrmScheme.CLEARKEY && !request.clearKeys.isNullOrBlank()) {
                    DefaultDrmSessionManager.Builder()
                        .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                        .build(LocalMediaDrmCallback(clearKeyJson(request.clearKeys).toByteArray()))
                } else defaultDrm.get(item)
            })
        val trackSelector = DefaultTrackSelector(appContext).apply {
            setParameters(
                buildUponParameters()
                    // Let ABR switch between e.g. H.264 and HEVC variants of the same ladder.
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .setAllowVideoNonSeamlessAdaptiveness(true)
                    .setExceedVideoConstraintsIfNecessary(true)
                    .setExceedRendererCapabilitiesIfNecessary(true)
            )
        }
        val builder = ExoPlayer.Builder(appContext, YumeiroRenderersFactory(appContext))
            .setMediaSourceFactory(msf as MediaSource.Factory)
            .setTrackSelector(trackSelector)
            .setBandwidthMeter(DefaultBandwidthMeter.getSingletonInstance(appContext))
            .setHandleAudioBecomingNoisy(true)
            // Request audio focus so music pauses while a video plays (and vice versa).
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true
            )
        // Hold a CPU wake lock (+ Wi-Fi lock for network streams) while playing or buffering, so
        // Doze/screen-off never starves the decoder. The screen itself is kept on by the player UI.
        builder.setWakeMode(if (isNetworkUri(uri)) C.WAKE_MODE_NETWORK else C.WAKE_MODE_LOCAL)
        if (isNetworkUri(uri)) {
            // Network streams: a deeper buffer rides out jitter; quick start & rebuffer recovery.
            // The byte budget is capped by the app's heap so 1080p/4K extension streams can't
            // grow the buffer until the process is killed (the "freeze then force close" bug).
            val memoryClass = (appContext.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager)?.memoryClass ?: 192
            builder.setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        /* minBufferMs = */ 20_000,
                        /* maxBufferMs = */ dev.asfakul.yumeiro.Edition.maxBufferMs,
                        /* bufferForPlaybackMs = */ 1_500,
                        /* bufferForPlaybackAfterRebufferMs = */ 3_000,
                    )
                    .setTargetBufferBytes(bufferBudgetBytes(memoryClass))
                    .setPrioritizeTimeOverSizeThresholds(false)
                    .build()
            )
        }
        return builder.build().also { factories[it] = msf; adFilters[it] = adFilter }
    }

    /**
     * Loads [uri] into [player] with its subtitles and, when the source provides separate audio
     * tracks, merges them with the video so the picture plays with sound.
     */
    fun setMedia(
        player: ExoPlayer,
        uri: String,
        subtitles: List<MediaItem.SubtitleConfiguration> = emptyList(),
        title: String? = null,
        startPositionMs: Long = C.TIME_UNSET,
    ) {
        val item = buildMediaItem(uri, subtitles, title)
        val audio = StreamRepository.requestFor(uri)?.audioTracks.orEmpty()
        // Separate audio files must stay in step with the video: no per-playlist ad cutting.
        adFilters[player]?.disabled = audio.isNotEmpty()
        val factory = factories[player]
        if (audio.isEmpty() || factory == null) {
            if (startPositionMs == C.TIME_UNSET) player.setMediaItem(item) else player.setMediaItem(item, startPositionMs)
            return
        }
        val sources = buildList {
            add(factory.createMediaSource(item))
            audio.forEach { a ->
                val b = MediaItem.Builder().setUri(Uri.parse(a.url))
                detectFormat(a.url).mimeType?.let { b.setMimeType(it) }
                add(factory.createMediaSource(b.build()))
            }
        }
        player.setMediaSource(MergingMediaSource(/* adjustPeriodTimeOffsets = */ true, *sources.toTypedArray()), startPositionMs)
    }

    fun buildMediaItem(
        uri: String,
        subtitles: List<MediaItem.SubtitleConfiguration> = emptyList(),
        title: String? = null,
    ): MediaItem {
        val request = StreamRepository.requestFor(uri)
        // A link resolved from a page / redirect plays from its resolved URL but stays keyed by the original.
        subtitles.forEach { SafeSubtitleDataSource.register(it.uri) }
        val builder = MediaItem.Builder().setUri(Uri.parse(request?.url ?: uri)).setSubtitleConfigurations(subtitles)
        if (isNetworkUri(request?.url ?: uri)) {
            // Applies only when the stream turns out to be live: keep close to the edge, adjust gently.
            builder.setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder().setMinPlaybackSpeed(0.97f).setMaxPlaybackSpeed(1.03f).build()
            )
        }
        // Title shown in the media notification / lock screen during background play.
        if (!title.isNullOrBlank()) {
            builder.setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder().setTitle(title).setDisplayTitle(title).build()
            )
        }
        if (request != null) {
            request.resolvedFormat().mimeType?.let { builder.setMimeType(it) }
            when (request.drmScheme) {
                DrmScheme.WIDEVINE -> request.drmLicenseUrl?.let { license ->
                    builder.setDrmConfiguration(
                        MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                            .setLicenseUri(license)
                            .setLicenseRequestHeaders(request.requestHeaders())
                            .build(),
                    )
                }
                DrmScheme.CLEARKEY -> if (request.clearKeys.isNullOrBlank()) request.drmLicenseUrl?.let { license ->
                    builder.setDrmConfiguration(
                        MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID).setLicenseUri(license)
                            .setLicenseRequestHeaders(request.requestHeaders()).build(),
                    )
                } else {
                    builder.setDrmConfiguration(MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID).build())
                }
                DrmScheme.NONE -> Unit
            }
        }
        return builder.build()
    }

    /** "kid:key" hex pairs → ClearKey JSON Web Key set. */
    internal fun clearKeyJson(pairs: String): String {
        val keys = JSONArray()
        pairs.split(',', '\n', ';', ' ').map { it.trim() }.filter { it.contains(':') }.forEach { pair ->
            val (kid, key) = pair.split(':', limit = 2).map { it.trim() }
            keys.put(JSONObject().put("kty", "oct").put("kid", hexToB64Url(kid)).put("k", hexToB64Url(key)))
        }
        return JSONObject().put("keys", keys).put("type", "temporary").toString()
    }

    private fun hexToB64Url(hex: String): String {
        val clean = hex.replace("-", "")
        val bytes = ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}

/**
 * HTTP data source factory backed by the shared OkHttp client (smart DNS fallback, redirects incl.
 * cross-protocol, session cookies). Per-stream User-Agent and headers are read at connection time.
 */
@OptIn(UnstableApi::class)
class RequestAwareHttpFactory(
    private val context: Context,
    private val defaultUserAgent: String,
    private val request: () -> StreamRequest?,
) : HttpDataSource.Factory {
    private val extra = HttpDataSource.RequestProperties()

    override fun createDataSource(): HttpDataSource {
        val r = request()
        val client = StreamInterceptors.clientFor(r?.url, NetworkClient.get(context))
        val delegate = OkHttpDataSource.Factory(client)
            .setUserAgent(r?.userAgent ?: defaultUserAgent)
            .setDefaultRequestProperties(extra.snapshot + (r?.requestHeaders() ?: emptyMap()))
            .createDataSource()
        val base = if (r == null || (r.subtitles.none { it.headers.isNotEmpty() } && r.audioTracks.none { it.headers.isNotEmpty() })) delegate
        else PerUrlHeadersDataSource(delegate) { uri -> r.headersFor(uri) }
        return SizeProbeDataSource(base, r?.url)
    }

    override fun setDefaultRequestProperties(defaultRequestProperties: Map<String, String>): HttpDataSource.Factory {
        extra.clearAndSet(defaultRequestProperties); return this
    }
}

/**
 * Round 12: reads the real file size from the response of the video's own URL (progressive MP4 /
 * MKV links from extensions), so "Video details" and the facts under the player stop saying 0.
 * Costs nothing: the headers are already there. Only the main URL counts, never subtitles,
 * audio tracks, playlists or segments.
 */
@OptIn(UnstableApi::class)
internal class SizeProbeDataSource(private val delegate: HttpDataSource, private val resolvedUrl: String? = null) : HttpDataSource by delegate {
    override fun open(dataSpec: DataSpec): Long {
        val n = delegate.open(dataSpec)
        runCatching {
            val snap = dev.asfakul.yumeiro.player.PlaybackDiagnostics.state.value
            val u = dataSpec.uri.toString()
            if (snap.sizeBytes <= 0 && snap.uri.isNotBlank() && (u == snap.uri || u == resolvedUrl)) {
                val total = dev.asfakul.yumeiro.player.PlaybackFacts.totalSizeFromHeaders(delegate.responseHeaders, dataSpec.position == 0L)
                if (total > 0) dev.asfakul.yumeiro.player.PlaybackDiagnostics.update { if (it.uri == snap.uri && it.sizeBytes <= 0) it.copy(sizeBytes = total) else it }
            }
        }
        return n
    }
    override fun addTransferListener(transferListener: TransferListener) = delegate.addTransferListener(transferListener)
}

/** Sends a subtitle's / audio track's own headers (they can differ from the video's). */
@OptIn(UnstableApi::class)
internal class PerUrlHeadersDataSource(
    private val delegate: HttpDataSource,
    private val headersFor: (String) -> Map<String, String>?,
) : HttpDataSource by delegate {
    override fun open(dataSpec: DataSpec): Long {
        val own = headersFor(dataSpec.uri.toString())
        return delegate.open(if (own.isNullOrEmpty()) dataSpec else dataSpec.withAdditionalHeaders(own))
    }
    override fun addTransferListener(transferListener: TransferListener) = delegate.addTransferListener(transferListener)
}
