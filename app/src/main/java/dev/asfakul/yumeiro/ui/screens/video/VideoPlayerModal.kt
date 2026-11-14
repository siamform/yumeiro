/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package dev.asfakul.yumeiro.ui.screens.video

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.navigationBarsPadding
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.IntentFilter
import android.provider.OpenableColumns
import androidx.compose.runtime.collectAsState
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dev.asfakul.yumeiro.data.model.VideoItem
import dev.asfakul.yumeiro.player.PipController
import dev.asfakul.yumeiro.player.PlayerSettings
import dev.asfakul.yumeiro.service.VideoPlaybackService
import dev.asfakul.yumeiro.service.VideoSessionBridge
import dev.asfakul.yumeiro.network.DnsSource
import dev.asfakul.yumeiro.network.NetworkSettings
import dev.asfakul.yumeiro.network.SmartDns
import dev.asfakul.yumeiro.player.ErrorFacts
import dev.asfakul.yumeiro.player.PlaybackHealthMonitor
import dev.asfakul.yumeiro.player.PlaybackRecovery
import dev.asfakul.yumeiro.player.RecoveryAction
import dev.asfakul.yumeiro.stream.Resolution
import dev.asfakul.yumeiro.stream.StreamFormat
import dev.asfakul.yumeiro.stream.StreamMediaFactory
import dev.asfakul.yumeiro.stream.StreamRepository
import dev.asfakul.yumeiro.stream.StreamRequest
import dev.asfakul.yumeiro.stream.StreamResolver
import dev.asfakul.yumeiro.subtitles.mimeFor
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
/**
 * The video player always renders on black, so its overlays use the dark Midnight palette (keeping
 * the user's accent) regardless of the app theme — light-theme text would be unreadable on video.
 */
@Composable
fun VideoPlayerModal(
    video: VideoItem,
    onClose: () -> Unit,
    onProgressUpdate: (String, Long, Long) -> Unit,
    modifier: Modifier = Modifier,
    nextVideo: VideoItem? = null,
    hasPreviousVideo: Boolean = false,
    onNextVideo: () -> Unit = {},
    onPreviousVideo: () -> Unit = {},
    /** Extra UI drawn over the video (online Episodes / Sources controls). */
    overlay: (@Composable BoxScope.(controlsVisible: Boolean, player: PlayerHandle) -> Unit)? = null,
    /** Called before an error card is shown; return true when another source takes over. */
    onFatalError: ((positionMs: Long) -> Boolean)? = null,
    /**
     * Watch-page content (HiAnime / YouTube style). When set, the portrait player is docked as a
     * 16:9 box at the top and this content (details, episodes, related) fills the rest. Fullscreen,
     * landscape and PiP still use the whole screen. Device videos pass null → classic full player.
     */
    watchPage: (@Composable ColumnScope.(player: PlayerHandle) -> Unit)? = null,
    /** In-app floating player (YouTube style): the video shrinks to a draggable window. */
    minimized: Boolean = false,
    onMinimize: (() -> Unit)? = null,
    onExpand: () -> Unit = {},
    /** Space kept free under the floating player (bottom dock + mini player). */
    minimizedBottomInset: androidx.compose.ui.unit.Dp = 96.dp,
    /** Streams: called instead of showing the final error card; true when another channel takes over. */
    onGiveUp: (() -> Boolean)? = null,
) {
    // Controls over the video are dark but keep the accent of the user's theme; the watch page
    // below the docked player switches back to the full app theme (see AppThemeLayer).
    dev.asfakul.yumeiro.ui.theme.ForceDarkTheme {
        // In system picture-in-picture the activity window *is* the video, so a floating
        // (minimized) player must fill it instead of staying a small box in the corner.
        val inSystemPip by PipController.inPip.collectAsState()
        val floating = minimized && !inSystemPip
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val miniWidth = (maxWidth * 0.56f).coerceIn(180.dp, 300.dp)
            val miniHeight = miniWidth * 9f / 16f
            var drag by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
            val maxDx = with(density) { (maxWidth - miniWidth - 24.dp).toPx() }.coerceAtLeast(0f)
            val maxDy = with(density) { (maxHeight - miniHeight - minimizedBottomInset - 64.dp).toPx() }.coerceAtLeast(0f)
            val slot = if (floating) {
                Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 12.dp, bottom = minimizedBottomInset)
                    .offset { androidx.compose.ui.unit.IntOffset(drag.x.toInt(), drag.y.toInt()) }
                    .size(miniWidth, miniHeight)
                    .shadow(18.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .pointerInput(maxDx, maxDy) {
                        detectDragGestures { change, d ->
                            change.consume()
                            drag = androidx.compose.ui.geometry.Offset(
                                (drag.x + d.x).coerceIn(-maxDx, 0f),
                                (drag.y + d.y).coerceIn(-maxDy, 0f),
                            )
                        }
                    }
                    .testTag("video_floating_player")
            } else modifier
            VideoPlayerModalContent(video, onClose, onProgressUpdate, slot, nextVideo, hasPreviousVideo, onNextVideo, onPreviousVideo, overlay, onFatalError, watchPage, floating, onMinimize, onExpand, onGiveUp = onGiveUp)
        }
    }
}

@Composable
private fun VideoPlayerModalContent(
    video: VideoItem,
    onClose: () -> Unit,
    onProgressUpdate: (String, Long, Long) -> Unit,
    modifier: Modifier = Modifier,
    nextVideo: VideoItem? = null,
    hasPreviousVideo: Boolean = false,
    onNextVideo: () -> Unit = {},
    onPreviousVideo: () -> Unit = {},
    /** Extra UI drawn over the video (online Episodes / Sources controls). */
    overlay: (@Composable BoxScope.(controlsVisible: Boolean, player: PlayerHandle) -> Unit)? = null,
    /** Called before an error card is shown; return true when another source takes over. */
    onFatalError: ((positionMs: Long) -> Boolean)? = null,
    watchPage: (@Composable ColumnScope.(player: PlayerHandle) -> Unit)? = null,
    minimized: Boolean = false,
    onMinimize: (() -> Unit)? = null,
    onExpand: () -> Unit = {},
    onGiveUp: (() -> Boolean)? = null,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    remember { dev.asfakul.yumeiro.ui.screens.watch.WatchPrefs.init(context); true }
    val autoNextOn by dev.asfakul.yumeiro.ui.screens.watch.WatchPrefs.autoNext.collectAsState()
    // Watch pages (Hues / YouTube / Stream) honour the Auto Play switch; device videos always start.
    val autoPlayStart = remember(video.id) { watchPage == null || dev.asfakul.yumeiro.ui.screens.watch.WatchPrefs.autoPlay.value }
    var startedOnce by remember(video.id) { mutableStateOf(false) }
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    val volumeAccumulator = remember(maxVolume) { VolumeAccumulator(maxVolume) }

    // Playback state
    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember(video.id) { mutableLongStateOf(video.playbackPositionMs) }
    var durationMs by remember(video.id) { mutableLongStateOf(if (video.durationMs > 0) video.durationMs else 1L) }
    var isBuffering by remember(video.id) { mutableStateOf(false) }
    var isLiveStream by remember(video.id) { mutableStateOf(false) }
    var isControlsVisible by remember { mutableStateOf(true) }
    // Window y of the seek bar row's top; captions are lifted above it while the controls show.
    var seekBarTopPx by remember { mutableStateOf<Int?>(null) }
    var isUserSeeking by remember { mutableStateOf(false) }
    var sliderProgress by remember(video.id) { mutableFloatStateOf(0f) }
    // Target of the last slider seek, held on screen until the player reports it (no snap back).
    var pendingSeekMs by remember(video.id) { mutableStateOf<Long?>(null) }
    var pendingSeekAt by remember(video.id) { mutableLongStateOf(0L) }
    var playerError by remember(video.id) { mutableStateOf<String?>(null) }

    // Audio & Video Formats
    var activeVideoFormat by remember(video.id) { mutableStateOf<Format?>(null) }
    var activeAudioFormat by remember(video.id) { mutableStateOf<Format?>(null) }

    // Screen adjustments
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    remember(context) { dev.asfakul.yumeiro.player.SubtitleStyleSettings.init(context); 0 }
    val subtitleStyle by dev.asfakul.yumeiro.player.SubtitleStyleSettings.style.collectAsState()
    var showSubtitleStyleDialog by remember { mutableStateOf(false) }
    // Pinch-to-zoom of the picture only (subtitles and controls stay put). Resets per video.
    var videoZoom by remember(video.id) { mutableStateOf(dev.asfakul.yumeiro.player.VideoZoom.State()) }
    var zoomHintVisible by remember { mutableStateOf(false) }
    var zoomHintJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var playerAreaSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var currentSpeed by remember { mutableFloatStateOf(1.0f) }
    var isScreenLocked by remember { mutableStateOf(false) }
    var isLockedUnlockPillVisible by remember { mutableStateOf(false) }
    // The player opens in the normal (portrait) layout with system bars visible. Only the
    // fullscreen button switches to landscape + immersive mode.
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    var isInPipMode by remember { mutableStateOf(false) }
    // Floating (minimised) or system PiP: only the picture is shown, no controls or watch page.
    val hideChrome = isInPipMode || minimized
    var swipeDownPx by remember { mutableFloatStateOf(0f) }
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current

    // Audio tracks & Subtitles state
    var audioTracksList by remember(video.id) { mutableStateOf<List<AudioTrackInfo>>(emptyList()) }
    var subtitleTracksList by remember(video.id) { mutableStateOf<List<SubtitleTrackInfo>>(emptyList()) }
    // Must match the player: captions start off when the setting says so (the CC button used to
    // think they were on, so its first tap "turned them off" and nothing ever showed).
    var isSubtitlesDisabled by remember(video.id) { mutableStateOf(!PlayerSettings.captionsDefault.value) }
    var autoPickedText by remember(video.id) { mutableStateOf(false) }
    var audioSwitchJob by remember(video.id) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var audioSwitchRevert by remember(video.id) { mutableStateOf<(() -> Unit)?>(null) }
    var subtitleReloadJob by remember(video.id) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    remember(video.id) { dev.asfakul.yumeiro.stream.SubtitleTiming.offsetUs = 0L; 0 }
    var externalSubtitleName by remember(video.id) { mutableStateOf<String?>(null) }
    var subtitleDelayMs by remember(video.id) { mutableLongStateOf(0L) }

    // HUD States
    var isBrightnessHUDVisible by remember { mutableStateOf(false) }
    var currentBrightness by remember {
        val initialWin = activity?.window?.attributes?.screenBrightness ?: -1.0f
        mutableFloatStateOf(if (initialWin > 0f) initialWin else 0.5f)
    }

    var isVolumeHUDVisible by remember { mutableStateOf(false) }
    var currentVolumeLevel by remember {
        mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
    }

    var isScrubHUDVisible by remember { mutableStateOf(false) }
    var scrubTargetTimeMs by remember { mutableLongStateOf(0L) }
    var scrubDeltaMs by remember { mutableLongStateOf(0L) }
    var scrubStartTimeMs by remember { mutableLongStateOf(0L) }

    var isDoubleTapRewindVisible by remember { mutableStateOf(false) }
    var isDoubleTapForwardVisible by remember { mutableStateOf(false) }
    var doubleTapHideJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    var toastMessage by remember { mutableStateOf("") }
    var isToastVisible by remember { mutableStateOf(false) }

    // Playback finished: play button becomes Replay and the next video starts after a countdown.
    var isEnded by remember(video.id) { mutableStateOf(false) }
    var autoNextCountdown by remember(video.id) { mutableIntStateOf(-1) }
    var autoNextCancelled by remember(video.id) { mutableStateOf(false) }
    var showResumePill by remember(video.id) { mutableStateOf(video.playbackPositionMs > 5000L) }

    // Dialog sheets
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showAspectRatioDialog by remember { mutableStateOf(false) }
    var showAudioTrackDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showVideoInfoDialog by remember { mutableStateOf(false) }

    val isPipSupported = remember {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    var toastJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun showToast(msg: String) {
        toastMessage = msg
        isToastVisible = true
        // One timer: an older toast's timer must not hide a newer message early.
        toastJob?.cancel()
        toastJob = coroutineScope.launch {
            delay(2200L)
            isToastVisible = false
        }
    }

    // Initialize ExoPlayer
    remember(context) { PlayerSettings.init(context); PlayerSettings }
    val backgroundPlayEnabled by PlayerSettings.backgroundPlay.collectAsState()
    val autoPipEnabled by PlayerSettings.autoPip.collectAsState()
    // Subtitles that came with an online source are offered in the captions menu right away.
    var externalSubs by remember(video.id) {
        mutableStateOf(
            StreamRepository.requestFor(video.uri)?.subtitles.orEmpty().mapNotNull { sub ->
                runCatching {
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                        .setMimeType(sub.mimeType ?: mimeFor(sub.url.substringBefore('?').substringAfterLast('/').substringAfterLast('.', "srt")))
                        .setLanguage(sub.language ?: "und")
                        .setLabel(sub.label)
                        .build()
                }.getOrNull()
            }
        )
    }
    var showOnlineSubtitleDialog by remember { mutableStateOf(false) }
    // Extensions keep finding subtitles after the video starts; offer those in the captions menu.
    var lateSubs by remember(video.id) { mutableStateOf<List<dev.asfakul.yumeiro.online.OnlineSubtitle>>(emptyList()) }
    LaunchedEffect(video.id) {
        if (!video.id.startsWith("online:")) return@LaunchedEffect
        dev.asfakul.yumeiro.online.OnlineSession.state.collect { st ->
            if (st?.videoId != video.id) return@collect
            val have = externalSubs.map { it.uri.toString() }.toSet()
            lateSubs = st.subtitles.filter { it.url !in have }.distinctBy { it.url }.take(40)
        }
    }

    // Network links whose type can't be told from the URL (no extension, redirects, IPTV lists,
    // web pages) are inspected first; everything else starts immediately as before.
    val isNetworkStream = remember(video.id) { StreamMediaFactory.isNetworkUri(video.uri) }
    val originalRequest = remember(video.id) { StreamRepository.requestFor(video.uri) ?: StreamRequest(url = video.uri, title = video.title) }
    var refreshAttempts by remember(video.id) { mutableStateOf(0) }
    /** Header variant being tried after a refusal; learned for the host once playback starts. */
    var ladderStep by remember(video.id) { mutableStateOf<dev.asfakul.yumeiro.online.AccessLadder.Step?>(null) }
    val needsResolve = remember(video.id) { isNetworkStream && !StreamResolver.isConfident(originalRequest) }
    var resolving by remember(video.id) { mutableStateOf(needsResolve) }
    var errorTitle by remember(video.id) { mutableStateOf<String?>(null) }
    var errorDetail by remember(video.id) { mutableStateOf<String?>(null) }
    var errorAction by remember(video.id) { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var pageFinder by remember(video.id) { mutableStateOf<Resolution.NeedsBrowser?>(null) }
    var waitingForNetwork by remember(video.id) { mutableStateOf(false) }
    var errorCanRetry by remember(video.id) { mutableStateOf(true) }
    var onlineQuickRetries by remember(video.id) { mutableStateOf(0) }
    var qualityList by remember(video.id) { mutableStateOf<List<QualityOption>>(emptyList()) }
    var selectedQuality by remember(video.id) { mutableStateOf<QualityOption?>(null) }
    var showQualityDialog by remember { mutableStateOf(false) }
    val recovery = remember(video.id) {
        PlaybackRecovery(
            alternativeDns = { NetworkSettings.dns.value.usesAlternative },
            dnsLabel = { NetworkSettings.dns.value.provider.label },
        )
    }
    var retryJob by remember(video.id) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var stableJob by remember(video.id) { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val exoPlayer = remember(video.id) {
        StreamMediaFactory.createPlayer(context, video.uri).apply {
            if (!PlayerSettings.captionsDefault.value) {
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
            }
            if (!needsResolve) {
                // Same media as every other path: the source's subtitles and separate audio come along.
                StreamMediaFactory.setMedia(this, video.uri, externalSubs, video.title)
                prepare()
                if (video.playbackPositionMs > 0L) {
                    seekTo(video.playbackPositionMs)
                }
            }
            playWhenReady = autoPlayStart
        }
    }
    /**
     * Every user seek goes through here. A source ExoPlayer reports as unseekable (a progressive
     * file without any seek index) restarts at 0:00 on any seekTo — that was the "jump 10 min
     * forward and it plays from 00" bug — so such a seek is refused with a hint instead. While the
     * stream is still opening the target is kept and applied once the player knows the media.
     */
    var queuedSeekMs by remember(video.id) { mutableStateOf<Long?>(null) }
    fun seekPlayer(target: Long) {
        val t = target.coerceAtLeast(0L)
        val prepared = !exoPlayer.currentTimeline.isEmpty && exoPlayer.playbackState != Player.STATE_IDLE
        if (!prepared) {
            queuedSeekMs = t; currentPositionMs = t
            pendingSeekMs = t; pendingSeekAt = System.currentTimeMillis()
            return
        }
        if (!exoPlayer.isCurrentMediaItemSeekable && !exoPlayer.isCurrentMediaItemLive) {
            showToast("This source can't skip ahead — try another source to jump")
            currentPositionMs = exoPlayer.currentPosition
            return
        }
        exoPlayer.seekTo(t)
        currentPositionMs = t
        pendingSeekMs = t
        pendingSeekAt = System.currentTimeMillis()
    }
    androidx.compose.runtime.DisposableEffect(exoPlayer) {
        dev.asfakul.yumeiro.watch.WatchTogether.bind(exoPlayer)
        onDispose { dev.asfakul.yumeiro.watch.WatchTogether.bind(null) }
    }
    val healthMonitor = remember(exoPlayer) {
        PlaybackHealthMonitor(exoPlayer, onLowBandwidth = { action ->
            showToast(
                when (action) {
                    PlaybackHealthMonitor.LowBandwidthAction.REVERTED_TO_AUTO -> "Slow connection — quality set to Auto"
                    PlaybackHealthMonitor.LowBandwidthAction.CAPPED_BITRATE -> "Slow connection — lowering quality"
                }
            )
        })
    }

    fun showErrorCard(title: String, detail: String, canRetry: Boolean = true, action: Pair<String, () -> Unit>? = null) {
        if (onFatalError != null && canRetry && onFatalError(exoPlayer.currentPosition.coerceAtLeast(video.playbackPositionMs))) return
        // Streams with Auto Next on: a channel that can't be opened hands over to the next one.
        if (onFatalError == null && onGiveUp?.invoke() == true) return
        // Online sources: the header hints are for hand-entered streams; give advice that fits.
        val shown = if (onFatalError != null && ("Headers & DRM" in detail)) onlineRefusedDetail(title) else detail
        errorTitle = title; errorDetail = shown; errorCanRetry = canRetry; errorAction = action
        playerError = shown
        isBuffering = false
        resolving = false
    }

    fun clearErrorCard() {
        playerError = null; errorTitle = null; errorDetail = null; errorAction = null; errorCanRetry = true
    }

    /** Plays [request] (the resolved form of the link the player was opened with). */
    fun startResolved(request: StreamRequest, positionMs: Long) {
        StreamRepository.register(video.uri, request)
        resolving = false
        StreamMediaFactory.setMedia(exoPlayer, video.uri, externalSubs, video.title)
        exoPlayer.prepare()
        if (positionMs > 0L && !exoPlayer.isCurrentMediaItemLive) exoPlayer.seekTo(positionMs)
        // First start follows Auto Play; recoveries keep playing.
        exoPlayer.playWhenReady = if (startedOnce) true else autoPlayStart
        startedOnce = true
    }

    fun handleResolution(r: Resolution, positionMs: Long) {
        when (r) {
            is Resolution.Playable -> startResolved(r.request, positionMs)
            is Resolution.Playlist -> {
                StreamRepository.setPlaylist(r.name, r.channels)
                showErrorCard(
                    "Channel list opened",
                    "This link is an IPTV playlist with ${r.channels.size} channel${if (r.channels.size == 1) "" else "s"}, not a single stream. The channels are now listed in Videos → Live TV under Opened list — pick one there.",
                    canRetry = false,
                )
            }
            // Round 10: the link finder opens right here (it used to send the user to Live TV or out
            // to a browser). Known file hosts start straight away in auto mode.
            is Resolution.NeedsBrowser -> {
                if (r.auto) pageFinder = r
                showErrorCard(
                    "This is a web page",
                    r.reason,
                    canRetry = false,
                    action = "Find the video" to { pageFinder = r },
                )
            }
            is Resolution.Handoff -> showErrorCard(
                "Open in ${r.platform}",
                r.reason,
                canRetry = false,
                action = "Open in app" to {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        .onFailure { showToast("No app can open this link") }
                    Unit
                },
            )
            is Resolution.Failed -> showErrorCard(r.title, r.message)
        }
    }

    fun resolveAndPlay(request: StreamRequest, positionMs: Long, thorough: Boolean) {
        resolving = true
        isBuffering = true
        // The player only accepts calls on the main thread, so the result is always handled there.
        coroutineScope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) {
            val r = runCatching { StreamResolver.resolve(context, request, thorough) }
                .getOrElse { Resolution.Failed("Couldn't open the link", dev.asfakul.yumeiro.online.OnlineErrors.friendly(it, "Check the link and try again.")) }
            handleResolution(r, positionMs)
        }
    }

    LaunchedEffect(video.id) {
        if (needsResolve) resolveAndPlay(originalRequest, video.playbackPositionMs, thorough = false)
    }

    // Online sources: a link that never shows a picture (stuck loading, or audio without video)
    // hands over to the next source instead of leaving a black screen.
    if (onFatalError != null) {
        LaunchedEffect(video.id) {
            // Gives up only on a source that is really stuck: no picture after 30 s *and* nothing
            // new downloaded for the last 15 s (a slow but working source keeps its chance, up to 90 s).
            var waited = 0L
            var stalled = 0L
            var lastBuffered = -1L
            // Round 13: was 30 s / 15 s (up to 90 s). A source that sends nothing for 10 s after
            // 12 s of trying now hands over, so rotation feels automatic instead of stuck.
            while (waited < 45_000L && !(waited >= 12_000L && stalled >= 10_000L)) {
                delay(1_000L); waited += 1_000L
                val d = dev.asfakul.yumeiro.player.PlaybackDiagnostics.state.value
                if (d.firstFrame || playerError != null) return@LaunchedEffect
                if (!exoPlayer.playWhenReady || resolving) { waited -= 1_000L; continue } // paused / still resolving: don't count
                val buffered = exoPlayer.totalBufferedDuration
                if (buffered > lastBuffered) { stalled = 0L; lastBuffered = buffered } else stalled += 1_000L
            }
            val d = dev.asfakul.yumeiro.player.PlaybackDiagnostics.state.value
            if (!d.firstFrame && playerError == null) {
                val tracks = exoPlayer.currentTracks
                val audioOnly = tracks.groups.any { it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO } && tracks.groups.none { it.type == androidx.media3.common.C.TRACK_TYPE_VIDEO }
                showToast(if (audioOnly) "This source has no picture — trying another" else "This source isn't responding — trying another")
                if (!onFatalError(exoPlayer.currentPosition.coerceAtLeast(video.playbackPositionMs))) {
                    if (audioOnly) showToast("Only an audio version is available")
                    else showErrorCard("Couldn't start this video", "The source didn't send any picture. Try another source or episode.", canRetry = false)
                }
            }
        }
        // Round 13: a source that started but then buffers forever (dead CDN, expired token
        // mid-file) also moves on to the next source, at the same position.
        LaunchedEffect(video.id) {
            var stuck = 0L
            var lastBuffered = -1L
            while (true) {
                delay(1_000L)
                val buffering = exoPlayer.playbackState == Player.STATE_BUFFERING && exoPlayer.playWhenReady && !resolving && !waitingForNetwork
                val firstFrame = dev.asfakul.yumeiro.player.PlaybackDiagnostics.state.value.firstFrame
                if (!buffering || !firstFrame || !dev.asfakul.yumeiro.network.Connectivity.online.value) { stuck = 0L; lastBuffered = -1L; continue }
                val buffered = exoPlayer.bufferedPosition
                if (buffered > lastBuffered + 500L) { lastBuffered = buffered; stuck = 0L } else stuck += 1_000L
                if (stuck >= 25_000L) {
                    stuck = 0L
                    showToast("This source stopped sending video — trying another")
                    if (onFatalError(exoPlayer.currentPosition)) return@LaunchedEffect
                }
            }
        }
    }

    /** Applies error-specific, bounded recovery for network streams. */
    fun recoverFrom(error: PlaybackException) {
        ladderStep = null
        stableJob?.cancel()
        val facts = ErrorFacts.from(error)
        if (!isNetworkStream) {
            if (onFatalError?.invoke(exoPlayer.currentPosition) == true) return
            playerError = when {
                error.errorCodeName.contains("FILE_NOT_FOUND") -> "This file was moved or deleted."
                error.errorCodeName.contains("NO_PERMISSION") -> "Yumeiro can't read this file. Allow media access in Settings."
                error.errorCodeName.contains("DECOD") || error.errorCodeName.contains("FORMAT") -> "This phone can't decode this video's format. It may play in another app or after converting to MP4."
                else -> "This video couldn't play (${error.errorCodeName.removePrefix("ERROR_CODE_").lowercase().replace('_', ' ')})."
            }
            isBuffering = false
            return
        }
        val current = StreamRepository.requestFor(video.uri) ?: originalRequest
        // Where the viewer actually is (they may have rewound before the resume point); the
        // resume point only stands in while the player hasn't reported a position yet.
        val pos0 = queuedSeekMs ?: exoPlayer.currentPosition.takeIf { it > 0L } ?: video.playbackPositionMs
        val refused = facts.httpStatus in dev.asfakul.yumeiro.online.AccessLadder.ACCESS_CODES
        // Round 10: the connection is gone (not the source). Retrying, refreshing or failing over
        // now would only burn the attempts and end on an error card; wait for the network instead
        // and pick up at the same position the moment it is back.
        if (facts.isNetwork && !dev.asfakul.yumeiro.network.Connectivity.online.value) {
            waitingForNetwork = true
            isBuffering = true
            showToast("You're offline — playback resumes when the connection is back")
            return
        }
        // Refreshable sources (YouTube): signed URLs expire → fetch a fresh one, keep the position.
        if (refused || facts.isNetwork) {
            val refresher = dev.asfakul.yumeiro.stream.StreamRefreshers.find(originalRequest)
            if (refresher != null && refreshAttempts < 2) {
                refreshAttempts++
                isBuffering = true
                showToast("Refreshing the stream link")
                coroutineScope.launch {
                    val fresh = runCatching { dev.asfakul.yumeiro.stream.StreamRefreshers.refresh(context, originalRequest) }.getOrNull()
                    if (fresh != null) startResolved(fresh, pos0) else showErrorCard("Couldn't refresh the stream", "The video's link expired and a new one couldn't be fetched. Check your connection and retry.")
                }
                return
            }
        }
        // Online extension links refused by the host: try header variants (browser fetch metadata,
        // site / host referer, no referer, player UA) before failing over to the next source.
        // Re-probing the same URL (the old Refresh path) only burned the token's remaining life.
        // Stream tab links (IPTV, direct links) get the same header ladder; when it is exhausted the
        // normal recovery re-resolves the link (which probes the ladder again on a fresh token).
        if (refused && onFatalError == null && facts.httpStatus != 451) {
            dev.asfakul.yumeiro.online.AccessLadder.hint(originalRequest.url, originalRequest.pageUrl)
            val attempt = dev.asfakul.yumeiro.online.AccessLadder.next(originalRequest)
            if (attempt != null) {
                showToast("Access denied — ${attempt.step.label.lowercase()}")
                ladderStep = attempt.step
                startResolved(attempt.request, pos0)
                return
            }
        }
        if (refused && onFatalError != null) {
            val attempt = dev.asfakul.yumeiro.online.AccessLadder.next(originalRequest)
            if (attempt != null) {
                android.util.Log.i("VideoPlayer", "HTTP ${facts.httpStatus} → ${attempt.step}")
                showToast("Access denied — ${attempt.step.label.lowercase()}")
                ladderStep = attempt.step
                startResolved(attempt.request, pos0)
                return
            }
            if (onFatalError(pos0)) return
            showErrorCard("Access denied (HTTP ${facts.httpStatus})", onlineRefusedDetail("Access denied (HTTP ${facts.httpStatus})"), canRetry = false)
            return
        }
        // Round 13: online sources rotate right away. One quick retry for a network blip, then the
        // next source; the slow retry / refresh chain below used to keep the first source for
        // minutes, so it looked like rotation never happened.
        if (onFatalError != null) {
            if (facts.isNetwork && onlineQuickRetries < 1) {
                onlineQuickRetries++
                isBuffering = true
                retryJob?.cancel()
                retryJob = coroutineScope.launch { delay(800L); exoPlayer.prepare(); exoPlayer.playWhenReady = true }
                return
            }
            showToast("This source failed — trying the next one")
            if (onFatalError(pos0)) return
        }
        val action = recovery.decide(facts, canRefresh = true, autoFormat = originalRequest.format == StreamFormat.AUTO || originalRequest.formatFromSource)
        val pos = exoPlayer.currentPosition
        when (action) {
            is RecoveryAction.Retry -> {
                isBuffering = true
                showToast("${action.reason} (attempt ${action.attempt} of ${action.maxAttempts})")
                retryJob?.cancel()
                retryJob = coroutineScope.launch {
                    delay(action.delayMs)
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                }
            }
            is RecoveryAction.Refresh -> {
                showToast(action.reason)
                // A page-based stream is re-resolved from its page; otherwise the original link is re-opened.
                val source = current.pageUrl?.takeIf { originalRequest.pageUrl == null }?.let { current.copy(url = it, format = StreamFormat.AUTO) } ?: originalRequest
                resolveAndPlay(source, pos, thorough = true)
            }
            RecoveryAction.JumpToLive -> {
                showToast("Rejoining the live stream")
                exoPlayer.seekToDefaultPosition()
                exoPlayer.prepare()
            }
            RecoveryAction.Reprobe -> resolveAndPlay(current.copy(format = StreamFormat.AUTO), pos, thorough = true)
            is RecoveryAction.Fail -> showErrorCard(action.title, action.message)
        }
    }

    LaunchedEffect(waitingForNetwork) {
        if (!waitingForNetwork) return@LaunchedEffect
        dev.asfakul.yumeiro.network.Connectivity.online.first { it }
        delay(800L) // let DNS / routes settle after a Wi-Fi ↔ mobile switch
        waitingForNetwork = false
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    /** Adds a side-loaded subtitle, keeps the current position and turns captions on. */
    fun addExternalSubtitle(uri: Uri, mimeType: String, language: String, label: String) {
        externalSubtitleName = label
        val config = MediaItem.SubtitleConfiguration.Builder(uri)
            .setMimeType(mimeType)
            .setLanguage(language)
            .setLabel(label)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_FORCED)
            .build()
        externalSubs = externalSubs + config
        val curPos = exoPlayer.currentPosition
        val curPlaying = exoPlayer.playWhenReady
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setPreferredTextLanguage(language)
            .build()
        isSubtitlesDisabled = false
        StreamMediaFactory.setMedia(exoPlayer, video.uri, externalSubs, video.title, curPos)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = curPlaying
    }

    /**
     * Turns on a caption track explicitly. Just re-enabling the text type wasn't enough: the track
     * selector only auto-picks tracks flagged default/forced or in the phone's language, so most
     * sources' subtitles never appeared.
     */
    fun pickBestTextTrack(tracks: Tracks = exoPlayer.currentTracks): Boolean {
        val candidates = buildList {
            tracks.groups.forEachIndexed { gi, g ->
                if (g.type != C.TRACK_TYPE_TEXT) return@forEachIndexed
                for (i in 0 until g.length) {
                    if (!g.isTrackSupported(i, /* allowExceedsCapabilities = */ true)) continue
                    val f = g.getTrackFormat(i)
                    add(dev.asfakul.yumeiro.player.TrackPicks.Text(gi, i, f.language, f.label,
                        forced = f.selectionFlags and C.SELECTION_FLAG_FORCED != 0, isDefault = f.selectionFlags and C.SELECTION_FLAG_DEFAULT != 0))
                }
            }
        }
        val prefs = listOfNotNull(
            androidx.core.os.LocaleListCompat.getAdjustedDefault().takeIf { it.size() > 0 }?.get(0)?.toLanguageTag(),
            java.util.Locale.getDefault().toLanguageTag(),
        )
        val best = dev.asfakul.yumeiro.player.TrackPicks.bestText(candidates, prefs) ?: return false
        val group = tracks.groups[best.group]
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, best.index))
            .build()
        return true
    }

    fun setCaptionsEnabled(enabled: Boolean) {
        isSubtitlesDisabled = !enabled
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
            .build()
        if (enabled) {
            val hasSelected = exoPlayer.currentTracks.groups.any { it.type == C.TRACK_TYPE_TEXT && it.isSelected }
            val picked = hasSelected || pickBestTextTrack()
            showToast(if (picked) "Captions on" else "No captions in this video — load a file or search online")
        } else showToast("Captions off")
    }

    /**
     * Switches audio and keeps it in step with the picture: a seek to the current position flushes
     * the decoders so the new track starts aligned. If the track doesn't start within 10 s (or
     * fails), the previous one comes back instead of leaving a frozen black screen.
     */
    fun switchAudio(groupIndex: Int, trackIndex: Int) {
        val tracks = exoPlayer.currentTracks
        if (groupIndex !in 0 until tracks.groups.size) return
        val group = tracks.groups[groupIndex]
        val format = group.getTrackFormat(trackIndex)
        if (!group.isTrackSupported(trackIndex) && !dev.asfakul.yumeiro.stream.YumeiroRenderersFactory.ffmpegSupports(format.sampleMimeType)) {
            showToast("This audio format (${format.sampleMimeType?.substringAfter('/') ?: "unknown"}) can't play on this phone")
            return
        }
        val previous = exoPlayer.trackSelectionParameters
        exoPlayer.trackSelectionParameters = previous.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
            .apply { format.language?.let { setPreferredAudioLanguage(it) } }
            .build()
        if (!exoPlayer.isCurrentMediaItemLive && exoPlayer.isCurrentMediaItemSeekable) exoPlayer.seekTo(exoPlayer.currentPosition)
        val revert = {
            exoPlayer.trackSelectionParameters = previous
            if (exoPlayer.playbackState == Player.STATE_IDLE) exoPlayer.prepare()
            if (!exoPlayer.isCurrentMediaItemLive && exoPlayer.isCurrentMediaItemSeekable) exoPlayer.seekTo(exoPlayer.currentPosition)
            showToast("That audio track didn't load — switched back")
        }
        audioSwitchRevert = revert
        audioSwitchJob?.cancel()
        audioSwitchJob = coroutineScope.launch {
            delay(10_000)
            val stuck = exoPlayer.playbackState == Player.STATE_BUFFERING && exoPlayer.playWhenReady
            if (stuck && audioSwitchRevert === revert) revert()
            audioSwitchRevert = null
        }
        showToast("Switched audio track")
    }

    /** Round 13: language picks from the source list land here (same video, other audio track). */
    fun selectAudioByKey(key: String): Boolean {
        val groups = exoPlayer.currentTracks.groups
        val audio = groups.withIndex().filter { it.value.type == C.TRACK_TYPE_AUDIO }
            .flatMap { (gi, g) -> (0 until g.length).map { ti -> Triple(gi, ti, g.getTrackFormat(ti).language) } }
        if (audio.size < 2) return false
        val hit = dev.asfakul.yumeiro.online.AudioLanguages.trackFor(key, audio.map { it.third }).takeIf { it >= 0 } ?: return false
        val (gi, ti) = audio[hit].let { it.first to it.second }
        if (groups[gi].isTrackSelected(ti)) return true
        switchAudio(gi, ti)
        return true
    }


    /** Re-reads subtitle files with the new delay (cues are timed when they're parsed). */
    fun applySubtitleDelay(ms: Long) {
        dev.asfakul.yumeiro.stream.SubtitleTiming.offsetUs = ms * 1000
        subtitleReloadJob?.cancel()
        subtitleReloadJob = coroutineScope.launch {
            delay(700)
            if (externalSubs.isEmpty() || resolving || playerError != null) return@launch
            val pos = exoPlayer.currentPosition
            val playing = exoPlayer.playWhenReady
            StreamMediaFactory.setMedia(exoPlayer, video.uri, externalSubs, video.title, pos)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = playing
        }
    }

    // External Subtitle Picker
    val subtitlePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}

            val filename = queryDisplayName(context, uri) ?: uri.lastPathSegment ?: "subtitle"
            addExternalSubtitle(uri, mimeFor(filename.substringAfterLast('.', "srt")), "und", filename)
            showToast("Subtitle loaded: $filename")
        }
    }

    // Function to parse current tracks
    fun updateTracksList(tracks: Tracks) {
        val audios = mutableListOf<AudioTrackInfo>()
        val subs = mutableListOf<SubtitleTrackInfo>()

        for (groupIndex in 0 until tracks.groups.size) {
            val group = tracks.groups[groupIndex]
            val trackType = group.type

            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                val isSelected = group.isTrackSelected(trackIndex)

                if (trackType == C.TRACK_TYPE_AUDIO) {
                    audios.add(
                        AudioTrackInfo(
                            groupIndex = groupIndex,
                            trackIndex = trackIndex,
                            language = format.language,
                            label = format.label,
                            channels = format.channelCount,
                            sampleRate = format.sampleRate,
                            isSelected = isSelected,
                            supported = group.isTrackSupported(trackIndex) || dev.asfakul.yumeiro.stream.YumeiroRenderersFactory.ffmpegSupports(format.sampleMimeType),
                            codec = codecName(format.sampleMimeType ?: format.codecs),
                        )
                    )
                    if (isSelected) {
                        activeAudioFormat = format
                    }
                } else if (trackType == C.TRACK_TYPE_TEXT) {
                    subs.add(
                        SubtitleTrackInfo(
                            groupIndex = groupIndex,
                            trackIndex = trackIndex,
                            language = format.language,
                            label = format.label,
                            isSelected = isSelected
                        )
                    )
                } else if (trackType == C.TRACK_TYPE_VIDEO && isSelected) {
                    activeVideoFormat = format
                }
            }
        }
        val kept = dev.asfakul.yumeiro.player.TrackPicks.dedupeAudio(audios.map {
            dev.asfakul.yumeiro.player.TrackPicks.Audio(it.groupIndex, it.trackIndex, it.language, it.label, it.channels, it.codec, it.supported, it.isSelected)
        }).map { it.group to it.index }.toSet()
        audioTracksList = audios.filter { (it.groupIndex to it.trackIndex) in kept }
        subtitleTracksList = subs
    }

    // Activity window and orientation lifecycle
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val originalBrightness = activity?.window?.attributes?.screenBrightness ?: -1.0f

        val originalLightStatusBars = activity?.window?.let { win ->
            WindowCompat.getInsetsController(win, win.decorView).isAppearanceLightStatusBars
        } ?: false
        activity?.window?.let { win ->
            // Player UI is always dark: use light status bar icons while it is open.
            WindowCompat.getInsetsController(win, win.decorView).isAppearanceLightStatusBars = false
        }

        // PiP Listener
        val compActivity = activity as? ComponentActivity
        val pipListener = Consumer<PictureInPictureModeChangedInfo> { info ->
            isInPipMode = info.isInPictureInPictureMode
            PipController.inPip.value = info.isInPictureInPictureMode
        }
        compActivity?.addOnPictureInPictureModeChangedListener(pipListener)

        onDispose {
            compActivity?.removeOnPictureInPictureModeChangedListener(pipListener)
            PipController.inPip.value = false
            activity?.requestedOrientation = originalOrientation
            activity?.window?.let { win ->
                val insetsController = WindowCompat.getInsetsController(win, win.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                insetsController.isAppearanceLightStatusBars = originalLightStatusBars
                win.attributes = win.attributes.apply {
                    screenBrightness = originalBrightness
                }
            }
        }
    }

    // Fullscreen: landscape (or immersive portrait for vertical videos) with hidden system bars.
    // Not fullscreen: portrait on phones (tablets keep their current orientation), bars visible.
    val isTablet = androidx.compose.ui.platform.LocalConfiguration.current.smallestScreenWidthDp >= 600
    LaunchedEffect(isFullscreen, isInPipMode) {
        if (isInPipMode) return@LaunchedEffect
        val act = activity ?: return@LaunchedEffect
        val win = act.window
        val insetsController = WindowCompat.getInsetsController(win, win.decorView)
        runCatching {
            if (isFullscreen) {
                val format = activeVideoFormat
                val isVerticalVideo = format != null && format.height > format.width && format.width > 0
                act.requestedOrientation = if (isVerticalVideo) {
                    ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                act.requestedOrientation = if (isTablet) {
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
                }
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Behave like a real player: keep the screen awake while a video plays or is about to resume
    // after buffering, and let it sleep again once paused, ended or closed.
    val hostView = androidx.compose.ui.platform.LocalView.current
    var wantsPlay by remember(exoPlayer) { mutableStateOf(exoPlayer.playWhenReady) }
    val keepAwake = dev.asfakul.yumeiro.player.ScreenWake.shouldKeepOn(isPlaying, isBuffering, wantsPlay, isEnded)
    DisposableEffect(hostView, keepAwake) {
        hostView.keepScreenOn = keepAwake
        onDispose { hostView.keepScreenOn = false }
    }

    // ExoPlayer listener lifecycle
    val currentOnProgressUpdate by rememberUpdatedState(onProgressUpdate)
    DisposableEffect(exoPlayer) {
        dev.asfakul.yumeiro.player.PlaybackDiagnostics.reset(video.uri, video.sizeBytes)
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                dev.asfakul.yumeiro.player.PlaybackDiagnostics.update { it.copy(firstFrame = true) }
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                dev.asfakul.yumeiro.player.PlaybackDiagnostics.update { it.copy(width = videoSize.width, height = videoSize.height) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                dev.asfakul.yumeiro.player.PlaybackDiagnostics.update {
                    it.copy(state = when (playbackState) { Player.STATE_BUFFERING -> "BUFFERING"; Player.STATE_READY -> "READY"; Player.STATE_ENDED -> "ENDED"; else -> "IDLE" },
                        positionMs = exoPlayer.currentPosition)
                }
                isBuffering = playbackState == Player.STATE_BUFFERING
                isEnded = playbackState == Player.STATE_ENDED
                if (isEnded) {
                    currentPositionMs = durationMs
                    currentOnProgressUpdate(video.id, durationMs, durationMs)
                }
                if (playbackState == Player.STATE_READY) {
                    // The header variant that got past the refusal is remembered for this host.
                    ladderStep?.let { dev.asfakul.yumeiro.online.AccessLadder.learn(video.uri, it) }
                    ladderStep = null
                    clearErrorCard()
                    stableJob?.cancel()
                    stableJob = coroutineScope.launch {
                        delay(PlaybackRecovery.STABLE_RESET_MS)
                        recovery.onStablePlayback()
                    }
                    if (exoPlayer.duration > 0) {
                        durationMs = exoPlayer.duration
                    }
                    queuedSeekMs?.let { q -> queuedSeekMs = null; seekPlayer(q) }
                }
            }

            // The duration is known as soon as the media is parsed, well before READY: the bar
            // shows the real length instead of a placeholder while the first frames buffer.
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                if (exoPlayer.duration > 0) durationMs = exoPlayer.duration
                isLiveStream = exoPlayer.isCurrentMediaItemLive
                dev.asfakul.yumeiro.player.PlaybackDiagnostics.update {
                    it.copy(durationMs = exoPlayer.duration.takeIf { d -> d > 0 && d != C.TIME_UNSET } ?: it.durationMs, live = exoPlayer.isCurrentMediaItemLive)
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                wantsPlay = playWhenReady
            }

            override fun onTracksChanged(tracks: Tracks) {
                updateTracksList(tracks)
                dev.asfakul.yumeiro.player.PlaybackDiagnostics.update { dev.asfakul.yumeiro.player.PlaybackFacts.fromTracks(it, tracks) }
                // Captions on by default: make sure one is actually shown.
                if (!isSubtitlesDisabled && !autoPickedText && tracks.groups.any { it.type == C.TRACK_TYPE_TEXT }) {
                    autoPickedText = true
                    if (tracks.groups.none { it.type == C.TRACK_TYPE_TEXT && it.isSelected }) pickBestTextTrack(tracks)
                }
                // The new audio track is playing: no need to switch back.
                if (audioSwitchRevert != null && exoPlayer.playbackState == Player.STATE_READY) { audioSwitchRevert = null; audioSwitchJob?.cancel() }
                qualityList = qualityOptions(tracks)
                // Override removed (new stream, or the health monitor went back to Auto).
                if (exoPlayer.trackSelectionParameters.overrides.values.none { it.type == C.TRACK_TYPE_VIDEO }) selectedQuality = null
            }

            override fun onPlayerError(error: PlaybackException) {
                dev.asfakul.yumeiro.player.PlaybackDiagnostics.update { it.copy(error = "${error.errorCodeName}: ${error.cause?.message ?: error.message}") }
                // The audio track the user just picked broke playback: go back to the one that worked.
                audioSwitchRevert?.let { revert -> audioSwitchRevert = null; audioSwitchJob?.cancel(); revert(); return }
                recoverFrom(error)
            }
        }
        exoPlayer.addListener(listener)
        healthMonitor.attach()

        VideoSessionBridge.player = exoPlayer
        onDispose {
            exoPlayer.removeListener(listener)
            healthMonitor.detach()
            retryJob?.cancel()
            stableJob?.cancel()
            if (VideoSessionBridge.player === exoPlayer) VideoSessionBridge.player = null
            context.stopService(Intent(context, VideoPlaybackService::class.java))
            val finalPos = exoPlayer.currentPosition
            val finalDur = exoPlayer.duration
            currentOnProgressUpdate(video.id, finalPos, finalDur)
            exoPlayer.release()
        }
    }

    // Background play (media notification) and picture-in-picture
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentBackgroundPlay by rememberUpdatedState(backgroundPlayEnabled)
    val currentIsInPip by rememberUpdatedState(isInPipMode)
    // Media session + notification for background play. Android 12+ forbids promoting a
    // service to foreground once the app is already in the background, so the session is
    // connected while the player is visible (as soon as playback starts) and kept until the
    // player closes or background play is turned off in Settings.
    DisposableEffect(exoPlayer, backgroundPlayEnabled) {
        var controllerFuture: ListenableFuture<MediaController>? = null
        val connect = {
            if (controllerFuture == null && android.os.Build.FINGERPRINT != "robolectric") {
                controllerFuture = runCatching {
                    val token = SessionToken(context, ComponentName(context, VideoPlaybackService::class.java))
                    MediaController.Builder(context, token).buildAsync()
                }.getOrNull()
            }
        }
        val sessionListener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying && backgroundPlayEnabled) connect()
            }
        }
        if (backgroundPlayEnabled) {
            if (exoPlayer.isPlaying || exoPlayer.playWhenReady) connect()
            exoPlayer.addListener(sessionListener)
        }
        onDispose {
            exoPlayer.removeListener(sessionListener)
            controllerFuture?.let { runCatching { MediaController.releaseFuture(it) } }
            controllerFuture = null
            runCatching { context.stopService(Intent(context, VideoPlaybackService::class.java)) }
        }
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                // Save the position now: after ON_STOP the system may kill the process at any time
                // (low memory, force-stop, battery saver) and the 5 s tick would lose the last seconds.
                val pos = exoPlayer.currentPosition
                if (pos > 0L && !exoPlayer.currentTimeline.isEmpty) currentOnProgressUpdate(video.id, pos, exoPlayer.duration.takeIf { it > 0L } ?: durationMs)
                val inPip = activity?.let { Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && it.isInPictureInPictureMode } == true
                // Background play disabled: pause when the app leaves the screen (unless in PiP).
                if (!inPip && !currentIsInPip && exoPlayer.isPlaying && !currentBackgroundPlay) {
                    exoPlayer.pause()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // PiP: auto-enter on Home (Android 12+ natively, older via onUserLeaveHint) + remote actions
    val pipSupported = remember { PipController.isSupported(context) }
    LaunchedEffect(isPlaying, autoPipEnabled, activeVideoFormat, pipSupported) {
        val act = activity ?: return@LaunchedEffect
        if (!pipSupported || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return@LaunchedEffect
        val w = activeVideoFormat?.width?.takeIf { it > 0 } ?: video.width
        val h = activeVideoFormat?.height?.takeIf { it > 0 } ?: video.height
        runCatching { act.setPictureInPictureParams(PipController.params(context, w, h, isPlaying, autoPipEnabled && isPlaying)) }
        PipController.onUserLeaveHint = if (autoPipEnabled && isPlaying && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            { isControlsVisible = false; PipController.enter(act, w, h, true) }
        } else null
    }
    DisposableEffect(exoPlayer) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                when (intent?.getIntExtra(PipController.EXTRA_CONTROL, 0)) {
                    PipController.CONTROL_PLAY_PAUSE -> if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                    PipController.CONTROL_REWIND -> seekPlayer((exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L))
                    PipController.CONTROL_FORWARD -> seekPlayer(
                        dev.asfakul.yumeiro.player.SeekMath.forward(exoPlayer.currentPosition, 10_000L, dev.asfakul.yumeiro.player.SeekMath.known(exoPlayer.duration, durationMs)),
                    )
                }
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(PipController.ACTION_PIP_CONTROL), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
            PipController.onUserLeaveHint = null
            if (pipSupported && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching { activity?.setPictureInPictureParams(PipController.params(context, 16, 9, false, false)) }
            }
        }
    }

    // Black-screen guard: audio playing but no new video frames → flush the decoder, then fail over.
    val blackWatch = remember(exoPlayer) { dev.asfakul.yumeiro.player.BlackFrameWatchdog() }
    LaunchedEffect(exoPlayer, isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        while (true) {
            delay(1_000L)
            val counters = exoPlayer.videoDecoderCounters
            counters?.ensureUpdated()
            val action = blackWatch.observe(
                positionMs = exoPlayer.currentPosition,
                renderedFrames = counters?.renderedOutputBufferCount?.toLong() ?: -1L,
                hasVideo = exoPlayer.videoFormat != null && counters != null,
                playing = exoPlayer.isPlaying,
            )
            when (action) {
                dev.asfakul.yumeiro.player.BlackFrameWatchdog.Action.NUDGE -> {
                    android.util.Log.i("VideoPlayer", "No video frames while playing: flushing decoder")
                    exoPlayer.seekTo(exoPlayer.currentPosition)
                }
                dev.asfakul.yumeiro.player.BlackFrameWatchdog.Action.FAIL -> {
                    android.util.Log.w("VideoPlayer", "Video stayed black: trying another source")
                    val pos = exoPlayer.currentPosition
                    if (onFatalError?.invoke(pos) != true) {
                        recoverFrom(PlaybackException("The picture stopped (black screen)", null, PlaybackException.ERROR_CODE_DECODING_FAILED))
                    }
                }
                dev.asfakul.yumeiro.player.BlackFrameWatchdog.Action.NONE -> Unit
            }
        }
    }

    // Progress updates ticker
    LaunchedEffect(isPlaying, isUserSeeking) {
        var ticks = 0
        while (true) {
            // Duration also while paused/buffering (it used to wait for the first playing tick).
            if (exoPlayer.duration > 0 && exoPlayer.duration != durationMs) durationMs = exoPlayer.duration
            if (isPlaying && !isUserSeeking) {
                val now = System.currentTimeMillis()
                val pos = exoPlayer.currentPosition
                currentPositionMs = dev.asfakul.yumeiro.player.SeekMath.displayed(pos, pendingSeekMs, pendingSeekAt, now)
                if (dev.asfakul.yumeiro.player.SeekMath.settled(pos, pendingSeekMs, pendingSeekAt, now)) pendingSeekMs = null
                if (exoPlayer.duration > 0) {
                    durationMs = exoPlayer.duration
                }
                // Persist resume position every ~5 s instead of every tick: saving on every
                // tick recomposed the whole app (video lists) four times a second.
                if (++ticks % 20 == 0) {
                    currentOnProgressUpdate(video.id, currentPositionMs, durationMs)
                }
            }
            // 250 ms (was 500 ms): the thumb moved in visible half-second steps.
            delay(250L)
        }
    }

    // Auto-hide controls timer (3.5 seconds)
    val isAnyDialogOpen = showQualityDialog || showSpeedDialog || showAspectRatioDialog || showAudioTrackDialog || showSubtitleDialog || showSubtitleStyleDialog || showVideoInfoDialog || playerError != null
    // Remote / keyboard (TV, Fire TV, Bluetooth keyboards): every key press keeps the controls up
    // a little longer, so walking along the buttons never races the auto-hide.
    val tvLayout = dev.asfakul.yumeiro.ui.tv.LocalTvMode.current
    val inputModeManager = androidx.compose.ui.platform.LocalInputModeManager.current
    val keyDriven = tvLayout || inputModeManager.inputMode == androidx.compose.ui.input.InputMode.Keyboard
    var keyTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(isControlsVisible, isPlaying, isAnyDialogOpen, isScreenLocked, keyTick) {
        if (isControlsVisible && isPlaying && !isAnyDialogOpen && !isScreenLocked) {
            delay(if (keyDriven) 5000L else 3500L)
            isControlsVisible = false
        }
    }

    // Video finished: keep the controls visible and count down to the next video of the queue.
    val currentOnNextVideo by rememberUpdatedState(onNextVideo)
    LaunchedEffect(isEnded) { if (isEnded) { isControlsVisible = true; showResumePill = false } }
    LaunchedEffect(isEnded, nextVideo?.id, autoNextCancelled, autoNextOn) {
        if (isEnded && nextVideo != null && !autoNextCancelled && autoNextOn) {
            for (sec in 3 downTo 1) {
                autoNextCountdown = sec
                delay(1000L)
            }
            autoNextCountdown = -1
            currentOnNextVideo()
        } else {
            autoNextCountdown = -1
        }
    }

    fun replayFromStart() {
        autoNextCancelled = true
        exoPlayer.seekTo(0L)
        currentPositionMs = 0L
        isEnded = false
        exoPlayer.play()
    }

    // Dismiss Resume Pill after 6 seconds
    LaunchedEffect(showResumePill) {
        if (showResumePill) {
            delay(6000L)
            showResumePill = false
        }
    }

    val backAction by PlayerSettings.backAction.collectAsState()
    // Back never just kills the video. Default (Floating player): it shrinks into an in-app
    // window over the pages so the app stays usable (browse, search, scroll). Leaving the app
    // (Home / recents) then turns that window into Android's picture-in-picture over everything.
    val leavePlayer: () -> Unit = {
        val w = activeVideoFormat?.width?.takeIf { it > 0 } ?: video.width
        val h = activeVideoFormat?.height?.takeIf { it > 0 } ?: video.height
        when {
            backAction == PlayerSettings.BackAction.PIP && isPipSupported && activity != null && isPlaying -> {
                isControlsVisible = false
                if (!PipController.enter(activity, w, h, true)) { if (onMinimize != null) onMinimize() else onClose() }
            }
            backAction != PlayerSettings.BackAction.CLOSE && onMinimize != null -> { isControlsVisible = false; onMinimize() }
            else -> onClose()
        }
    }
    BackHandler(enabled = !minimized) {
        if (keyDriven && isControlsVisible && !isScreenLocked && !isEnded && playerError == null && !hideChrome) {
            // Remote Back first closes the controls (as on every TV player), the next Back leaves.
            isControlsVisible = false
        } else if (isFullscreen && !isScreenLocked) {
            isFullscreen = false
        } else if (isScreenLocked) {
            isLockedUnlockPillVisible = true
            coroutineScope.launch {
                delay(3000L)
                isLockedUnlockPillVisible = false
            }
        } else {
            leavePlayer()
        }
    }
    pageFinder?.let { pf ->
        dev.asfakul.yumeiro.ui.screens.stream.PageStreamFinderDialog(
            pageUrl = pf.pageUrl,
            reason = pf.reason,
            base = pf.base,
            // The found stream replaces this item in the same player (one player, never two).
            onPlay = { req -> pageFinder = null; dev.asfakul.yumeiro.stream.IncomingMedia.open(context, req) },
            onDismiss = { pageFinder = null },
            auto = pf.auto,
        )
    }
    // D-pad / remote: the player surface holds focus while the controls are hidden.
    //  OK / Enter → play-pause and show the controls (focus on play-pause) · Left / Right → −/+10 s
    //  Up / Down → show the controls · media keys work at any time · Back closes the controls first.
    val rootFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val playFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    var rootFocused by remember { mutableStateOf(false) }
    // With a remote, the player opens with focus on play-pause (nothing focused = keys go nowhere).
    var focusPlayPending by remember { mutableStateOf(keyDriven) }
    LaunchedEffect(isControlsVisible, focusPlayPending, minimized) {
        if (minimized || !keyDriven) return@LaunchedEffect
        androidx.compose.runtime.withFrameNanos { }
        if (isControlsVisible && focusPlayPending) {
            // The controls may still be composing (first frame, loading spinner): retry briefly.
            for (i in 0 until 12) {
                if (runCatching { playFocus.requestFocus() }.isSuccess) break
                delay(80)
            }
            focusPlayPending = false
        }
        else if (!isControlsVisible) runCatching { rootFocus.requestFocus() }
    }
    fun keyRewind() {
        seekPlayer(dev.asfakul.yumeiro.player.SeekMath.rewind(exoPlayer.currentPosition, SEEK_STEP_MS))
        isDoubleTapForwardVisible = false; isDoubleTapRewindVisible = true
        doubleTapHideJob?.cancel(); doubleTapHideJob = coroutineScope.launch { delay(700L); isDoubleTapRewindVisible = false }
    }
    fun keyForward() {
        seekPlayer(dev.asfakul.yumeiro.player.SeekMath.forward(exoPlayer.currentPosition, SEEK_STEP_MS,
            dev.asfakul.yumeiro.player.SeekMath.known(exoPlayer.duration, durationMs)))
        isDoubleTapRewindVisible = false; isDoubleTapForwardVisible = true
        doubleTapHideJob?.cancel(); doubleTapHideJob = coroutineScope.launch { delay(700L); isDoubleTapForwardVisible = false }
    }
    fun keyPlayPause() { if (isEnded) replayFromStart() else if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() }
    val onPlayerKey: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = handler@{ e ->
        if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown || minimized || hideChrome) return@handler false
        val k = e.key
        val K = androidx.compose.ui.input.key.Key
        // Media keys (remote transport buttons, headset) whatever has focus.
        when (k) {
            K.MediaPlayPause, K.HeadsetHook -> { keyPlayPause(); return@handler true }
            K.MediaPlay -> { if (isEnded) replayFromStart() else exoPlayer.play(); return@handler true }
            K.MediaPause -> { exoPlayer.pause(); return@handler true }
            K.MediaFastForward -> { keyForward(); return@handler true }
            K.MediaRewind -> { keyRewind(); return@handler true }
            K.MediaNext -> { if (nextVideo != null) onNextVideo() else showToast("No next video"); return@handler true }
            K.MediaPrevious -> { if (hasPreviousVideo) onPreviousVideo() else { exoPlayer.seekTo(0L); currentPositionMs = 0L }; return@handler true }
        }
        if (isAnyDialogOpen) return@handler false
        val dpad = k == K.DirectionCenter || k == K.Enter || k == K.NumPadEnter || k == K.DirectionLeft ||
            k == K.DirectionRight || k == K.DirectionUp || k == K.DirectionDown
        if (!dpad) return@handler false
        if (isScreenLocked) {
            isLockedUnlockPillVisible = true
            coroutineScope.launch { delay(3000L); isLockedUnlockPillVisible = false }
            return@handler false // let focus reach the unlock button
        }
        keyTick++
        if (!rootFocused) return@handler false // focus is on a control: normal D-pad navigation
        when (k) {
            K.DirectionLeft -> keyRewind()
            K.DirectionRight -> keyForward()
            K.DirectionUp, K.DirectionDown -> { isControlsVisible = true; focusPlayPending = true }
            else -> { keyPlayPause(); isControlsVisible = true; focusPlayPending = true }
        }
        true
    }
    // An error while floating opens the full player so the suggestions are readable.
    LaunchedEffect(playerError, minimized) { if (minimized && playerError != null) onExpand() }

    val isLandscapeNow = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val docked = watchPage != null && !isFullscreen && !hideChrome && !isLandscapeNow
    // The user's own theme (Settings → Appearance) for everything that is not drawn over video.
    val appPalette = dev.asfakul.yumeiro.ui.theme.LocalAppPalette.current ?: dev.asfakul.yumeiro.ui.theme.LocalMtPalette.current
    // Docked on a light theme the status bar sits on the light page: use dark icons there.
    LaunchedEffect(docked, appPalette.isDark) {
        activity?.window?.let { win ->
            val c = WindowCompat.getInsetsController(win, win.decorView)
            c.isAppearanceLightStatusBars = docked && !appPalette.isDark
            c.isAppearanceLightNavigationBars = docked && !appPalette.isDark
        }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(if (docked) appPalette.background else Color.Black)
            .then(if (!isFullscreen && !hideChrome) Modifier.systemBarsPadding() else Modifier)
    ) {
    if (docked) {
        // Watch page order: app bar (Yumeiro) → player → details / episodes.
        dev.asfakul.yumeiro.ui.theme.AppThemeLayer {
            dev.asfakul.yumeiro.ui.screens.watch.PlayerBrandBar(
                onMinimize = leavePlayer,
                onClose = onClose,
                minimizeLabel = if (backAction == PlayerSettings.BackAction.CLOSE || onMinimize == null) "Close player" else "Minimize player",
            )
        }
    }
    Box(
        modifier = (if (docked) Modifier.fillMaxWidth().aspectRatio(16f / 9f) else Modifier.fillMaxSize())
            .graphicsLayer {
                if (swipeDownPx > 0f) {
                    translationY = swipeDownPx * 0.55f
                    val k = (1f - swipeDownPx / (size.height * 1.6f)).coerceIn(0.82f, 1f)
                    scaleX = k; scaleY = k
                }
            }
            .background(Color.Black)
            .onSizeChanged { playerAreaSize = it }
            .onPreviewKeyEvent(onPlayerKey)
            .onFocusChanged { rootFocused = it.isFocused }
            .focusRequester(rootFocus)
            .focusable()
            .testTag("video_player_root")
    ) {
        // Video Surface via Media3 PlayerView
        AndroidView(
            factory = { ctx ->
                // Inflated so the surface type can be a TextureView (only settable from XML).
                (android.view.LayoutInflater.from(ctx).inflate(dev.asfakul.yumeiro.R.layout.yumeiro_player_view, null) as PlayerView).apply {
                    player = exoPlayer
                    useController = false
                    this.resizeMode = resizeMode
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                if (playerView.player !== exoPlayer) playerView.player = exoPlayer
                playerView.resizeMode = resizeMode
                // Zoom only applies to the full player; docked, floating and PiP always show 100 %.
                val z = if (docked || hideChrome) dev.asfakul.yumeiro.player.VideoZoom.State() else videoZoom
                playerView.videoSurfaceView?.apply {
                    // Pan limits use the picture's own size (letterboxed video is smaller than the screen).
                    val c = if (width > 0 && height > 0) dev.asfakul.yumeiro.player.VideoZoom.clampPan(z, width.toFloat(), height.toFloat()) else z
                    scaleX = c.scale; scaleY = c.scale
                    translationX = c.panX; translationY = c.panY
                }
                val controlsUp = isControlsVisible && !isScreenLocked && !hideChrome
                dev.asfakul.yumeiro.player.SubtitleStyleSettings.applyTo(playerView, subtitleStyle, if (controlsUp) seekBarTopPx else null)
            },
            onRelease = { playerView -> playerView.player = null },
            modifier = Modifier.fillMaxSize()
        )

        // Gesture Overlay Layer (handles Taps, Double Taps, Horizontal Scrub, Vertical Brightness & Volume)
        if (!hideChrome) {
            VideoGestureOverlay(
                // Swipe down from the centre: leave fullscreen, or shrink to the floating player.
                onSwipeDown = when {
                    isScreenLocked -> null
                    isFullscreen -> ({ isFullscreen = false })
                    onMinimize != null -> ({ isControlsVisible = false; onMinimize() })
                    else -> null
                },
                onSwipeDownProgress = { swipeDownPx = it },
                // YouTube style: touch and hold the video, then slide down → picture-in-picture
                // (the in-app floating player where system PiP isn't available).
                onHoldSwipeDown = when {
                    isScreenLocked -> null
                    isPipSupported && activity != null -> ({
                        val w = activeVideoFormat?.width?.takeIf { it > 0 } ?: video.width
                        val h = activeVideoFormat?.height?.takeIf { it > 0 } ?: video.height
                        isControlsVisible = false
                        if (!PipController.enter(activity, w, h, true)) onMinimize?.invoke()
                    })
                    onMinimize != null -> ({ isControlsVisible = false; onMinimize() })
                    else -> null
                },
                onHoldStart = { haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress) },
                onPinch = if (isScreenLocked || docked) null else ({ zoomBy, pan ->
                    videoZoom = dev.asfakul.yumeiro.player.VideoZoom.step(videoZoom, zoomBy, pan.x, pan.y,
                        playerAreaSize.width.toFloat(), playerAreaSize.height.toFloat())
                    zoomHintJob?.cancel()
                    zoomHintVisible = true
                }),
                onPinchEnd = {
                    val before = videoZoom.zoomed
                    videoZoom = dev.asfakul.yumeiro.player.VideoZoom.settle(videoZoom)
                    if (before && !videoZoom.zoomed) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    zoomHintJob?.cancel()
                    zoomHintJob = coroutineScope.launch { delay(900L); zoomHintVisible = false }
                },
                isScreenLocked = isScreenLocked,
                onSingleTap = {
                    isControlsVisible = !isControlsVisible
                },
                onDoubleTapLeft = {
                    seekPlayer((exoPlayer.currentPosition - SEEK_STEP_MS).coerceAtLeast(0L))
                    isDoubleTapForwardVisible = false
                    isDoubleTapRewindVisible = true
                    doubleTapHideJob?.cancel()
                    doubleTapHideJob = coroutineScope.launch {
                        delay(700L)
                        isDoubleTapRewindVisible = false
                    }
                },
                onDoubleTapRight = {
                    val target = dev.asfakul.yumeiro.player.SeekMath.forward(exoPlayer.currentPosition, SEEK_STEP_MS,
                        dev.asfakul.yumeiro.player.SeekMath.known(exoPlayer.duration, durationMs))
                    seekPlayer(target)
                    isDoubleTapRewindVisible = false
                    isDoubleTapForwardVisible = true
                    doubleTapHideJob?.cancel()
                    doubleTapHideJob = coroutineScope.launch {
                        delay(700L)
                        isDoubleTapForwardVisible = false
                    }
                },
                onDoubleTapCenter = {
                    if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                },
                onScrubStart = {
                    scrubStartTimeMs = exoPlayer.currentPosition
                    scrubTargetTimeMs = scrubStartTimeMs
                    scrubDeltaMs = 0L
                    isScrubHUDVisible = true
                },
                onScrubDelta = { deltaX ->
                    // 1 pixel = ~120ms of scrub
                    val deltaMs = (deltaX * 120f).toLong()
                    val newDelta = scrubDeltaMs + deltaMs
                    val newTarget = dev.asfakul.yumeiro.player.SeekMath.clamp(scrubStartTimeMs + newDelta,
                        dev.asfakul.yumeiro.player.SeekMath.known(exoPlayer.duration, durationMs))
                    scrubDeltaMs = newTarget - scrubStartTimeMs
                    scrubTargetTimeMs = newTarget
                },
                onScrubCommit = {
                    seekPlayer(scrubTargetTimeMs)
                },
                onBrightnessDelta = { delta ->
                    val newBrightness = (currentBrightness + delta).coerceIn(0.02f, 1.0f)
                    currentBrightness = newBrightness
                    activity?.window?.attributes = activity?.window?.attributes?.apply {
                        screenBrightness = newBrightness
                    }
                    isBrightnessHUDVisible = true
                },
                onVolumeDelta = { delta ->
                    if (!volumeAccumulator.active) {
                        // Sync with hardware-button changes made since the last swipe.
                        currentVolumeLevel = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        volumeAccumulator.begin(currentVolumeLevel)
                    }
                    val newVol = volumeAccumulator.add(delta, currentVolumeLevel)
                    if (newVol != currentVolumeLevel) {
                        currentVolumeLevel = newVol
                        runCatching { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0) }
                    }
                    isVolumeHUDVisible = true
                },
                onGestureEnd = {
                    volumeAccumulator.end()
                    coroutineScope.launch {
                        delay(900L)
                        isBrightnessHUDVisible = false
                        isVolumeHUDVisible = false
                        isScrubHUDVisible = false
                    }
                },
                onLockedTap = {
                    isLockedUnlockPillVisible = true
                    coroutineScope.launch {
                        delay(3000L)
                        isLockedUnlockPillVisible = false
                    }
                }
            )
        }

        // Pinch-to-zoom: live percentage while pinching, and a reset pill while zoomed.
        if (!hideChrome && !docked) {
            androidx.compose.animation.AnimatedVisibility(
                visible = zoomHintVisible,
                enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp),
            ) {
                Text(
                    dev.asfakul.yumeiro.player.VideoZoom.label(videoZoom.scale),
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 16.dp, vertical = 8.dp).testTag("video_zoom_hint"),
                )
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = videoZoom.zoomed && isControlsVisible && !isScreenLocked && !zoomHintVisible,
                enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp),
            ) {
                Text(
                    "Reset zoom · ${dev.asfakul.yumeiro.player.VideoZoom.label(videoZoom.scale)}",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.6f))
                        .clickable(role = androidx.compose.ui.semantics.Role.Button) { videoZoom = dev.asfakul.yumeiro.player.VideoZoom.State() }
                        .heightIn(min = 40.dp).padding(horizontal = 16.dp, vertical = 10.dp).testTag("video_zoom_reset"),
                )
            }
        }

        // Buffering Indicator
        if (isBuffering && playerError == null) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(54.dp)
                    .align(Alignment.Center),
                color = SpotifyGreen,
                strokeWidth = 3.dp
            )
        }

        // On-screen HUD Overlays
        BrightnessHUD(
            brightness = currentBrightness,
            isVisible = isBrightnessHUDVisible,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 20.dp, top = 28.dp, bottom = 28.dp)
                .fillMaxHeight(0.62f)
                .heightIn(max = 220.dp)
        )

        VolumeHUD(
            volumeLevel = currentVolumeLevel,
            maxVolume = maxVolume,
            isVisible = isVolumeHUDVisible,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 20.dp, top = 28.dp, bottom = 28.dp)
                .fillMaxHeight(0.62f)
                .heightIn(max = 220.dp)
        )

        ScrubSeekHUD(
            targetTimeMs = scrubTargetTimeMs,
            durationMs = durationMs,
            deltaMs = scrubDeltaMs,
            isVisible = isScrubHUDVisible,
            modifier = Modifier.align(Alignment.Center)
        )

        DoubleTapFeedback(
            isForward = false,
            isVisible = isDoubleTapRewindVisible,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 64.dp)
        )

        DoubleTapFeedback(
            isForward = true,
            isVisible = isDoubleTapForwardVisible,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 64.dp)
        )

        // App-wide rule: messages sit at the bottom, never the top. Lifted above the seek bar.
        ToastHUD(
            message = toastMessage,
            isVisible = isToastVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = if (isControlsVisible) 132.dp else 40.dp)
        )

        // Locked Unlock Button
        LockedFloatingUnlockButton(
            isVisible = isScreenLocked && isLockedUnlockPillVisible,
            onUnlock = {
                isScreenLocked = false
                isLockedUnlockPillVisible = false
                isControlsVisible = true
                showToast("Screen unlocked")
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 24.dp, top = 24.dp)
        )

        // Overlay Playback Controls (fades in/out)
        androidx.compose.animation.AnimatedVisibility(
            visible = isControlsVisible && !isScreenLocked && !hideChrome,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.80f),
                                Color.Black.copy(alpha = 0.20f),
                                Color.Black.copy(alpha = 0.88f)
                            )
                        )
                    )
            ) {
                // Top App Bar
                VideoPlayerTopBar(
                    video = video,
                    currentSpeed = currentSpeed,
                    isPipSupported = isPipSupported && autoPipEnabled,
                    onBackClick = onClose,
                    onAudioTrackClick = { showAudioTrackDialog = true },
                    onSubtitleClick = { showSubtitleDialog = true },
                    onSpeedClick = { showSpeedDialog = true },
                    captionsOn = !isSubtitlesDisabled,
                    onCaptionsToggle = { setCaptionsEnabled(isSubtitlesDisabled) },
                    onAspectRatioClick = { showAspectRatioDialog = true },
                    onPipClick = {
                        isControlsVisible = false
                        val w = activeVideoFormat?.width?.takeIf { it > 0 } ?: video.width
                        val h = activeVideoFormat?.height?.takeIf { it > 0 } ?: video.height
                        activity?.let { PipController.enter(it, w, h, isPlaying) }
                    },
                    onInfoClick = { showVideoInfoDialog = true },
                    qualityLabel = if (qualityList.size > 1) {
                        selectedQuality?.label ?: activeVideoFormat?.let { f ->
                            val h = minOf(f.width, f.height).takeIf { f.width > 0 && f.height > 0 }
                            if (h != null) "Auto · ${h}p" else null
                        } ?: "Auto"
                    } else null,
                    onQualityClick = { showQualityDialog = true },
                    onLockClick = {
                        isScreenLocked = true
                        isControlsVisible = false
                        isLockedUnlockPillVisible = true
                        coroutineScope.launch {
                            delay(3000L)
                            isLockedUnlockPillVisible = false
                        }
                    },
                    modifier = Modifier.align(Alignment.TopCenter),
                    compact = docked,
                )

                // Center playback controls (Rewind, Play/Pause, Forward). Not under an error card:
                // the dimmed buttons showed through it and could take D-pad focus.
                if (playerError == null) VideoPlayerCenterControls(
                    isPlaying = isPlaying,
                    isEnded = isEnded,
                    hasPrevious = hasPreviousVideo,
                    hasNext = nextVideo != null,
                    onPrevious = {
                        if (hasPreviousVideo) onPreviousVideo()
                        else { exoPlayer.seekTo(0L); currentPositionMs = 0L; showToast("Start of the list") }
                    },
                    onNext = {
                        if (nextVideo != null) onNextVideo() else showToast("No next video")
                    },
                    onRewind10s = {
                        seekPlayer(dev.asfakul.yumeiro.player.SeekMath.rewind(exoPlayer.currentPosition, SEEK_STEP_MS))
                    },
                    onPlayPause = {
                        if (isEnded) {
                            replayFromStart()
                        } else if (isPlaying) {
                            exoPlayer.pause()
                        } else {
                            exoPlayer.play()
                        }
                    },
                    onForward10s = {
                        // Was coerceAtMost(durationMs): with the 1L "unknown" placeholder that sent
                        // fast forward back to 0:00. SeekMath only caps at a real duration.
                        val target = dev.asfakul.yumeiro.player.SeekMath.forward(exoPlayer.currentPosition, SEEK_STEP_MS,
                            dev.asfakul.yumeiro.player.SeekMath.known(exoPlayer.duration, durationMs))
                        seekPlayer(target)
                    },
                    modifier = Modifier.align(Alignment.Center),
                    compact = docked,
                    playFocus = playFocus,
                )

                // Bottom Scrubber Bar
                VideoPlayerBottomBar(
                    currentPositionMs = currentPositionMs,
                    durationMs = durationMs,
                    sliderProgress = sliderProgress,
                    isLive = isLiveStream,
                    isLoading = isBuffering || resolving,
                    isUserSeeking = isUserSeeking,
                    onSeekProgressChange = { newProgress ->
                        isUserSeeking = true
                        sliderProgress = newProgress
                    },
                    isFullscreen = isFullscreen,
                    onToggleFullscreen = { isFullscreen = !isFullscreen },
                    onSeekProgressFinish = {
                        val seekPos = dev.asfakul.yumeiro.player.SeekMath.positionFor(sliderProgress,
                            dev.asfakul.yumeiro.player.SeekMath.known(exoPlayer.duration, durationMs))
                        if (seekPos != null) seekPlayer(seekPos)
                        isUserSeeking = false
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .trackWindowTop { seekBarTopPx = it },
                    compact = docked,
                )
            }
        }

        // Resume notice: centered, fully opaque, drawn above the controls overlay and lifted
        // over the seek bar while controls are visible so nothing overlaps or dims it.
        if (showResumePill && !isScreenLocked && !hideChrome) {
            val pillBottom by androidx.compose.animation.core.animateDpAsState(
                if (isControlsVisible) 132.dp else 40.dp, label = "resumePillLift"
            )
            ResumePill(
                playbackPositionMs = video.playbackPositionMs,
                onRestart = {
                    exoPlayer.seekTo(0L)
                    currentPositionMs = 0L
                    showResumePill = false
                    showToast("Restarted from beginning")
                },
                onDismiss = { showResumePill = false },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = pillBottom, start = 16.dp, end = 16.dp)
            )
        }

        val handle = remember(exoPlayer) {
            PlayerHandle(positionMs = { exoPlayer.currentPosition }, durationMs = { exoPlayer.duration.takeIf { it > 0 } ?: 0L }, seekTo = { exoPlayer.seekTo(it) }, pause = { exoPlayer.pause() }, selectAudio = { k -> selectAudioByKey(k) })
        }
        androidx.compose.runtime.CompositionLocalProvider(LocalPlayerDocked provides docked) {
            overlay?.invoke(this, isControlsVisible && !isScreenLocked && !hideChrome, handle)
        }

        if (minimized) {
            FloatingPlayerChrome(
                isPlaying = isPlaying,
                progress = if (durationMs > 1L) (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
                onExpand = onExpand,
                onPlayPause = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() },
                onClose = onClose,
            )
        }

        // Up next countdown after the video finishes
        if (isEnded && nextVideo != null && autoNextCountdown > 0 && !isScreenLocked && !hideChrome) {
            UpNextCard(
                next = nextVideo,
                secondsLeft = autoNextCountdown,
                onPlayNow = { autoNextCountdown = -1; onNextVideo() },
                onCancel = { autoNextCancelled = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 132.dp, start = 16.dp, end = 16.dp)
            )
        }

        // Unsupported Format / Playback Error Banner
        playerError?.let { err ->
            PlaybackErrorOverlay(
                errorMessage = err,
                onRetry = {
                    clearErrorCard()
                    recovery.onStablePlayback() // a manual retry gets a fresh retry budget
                    val pos = exoPlayer.currentPosition
                    if (exoPlayer.mediaItemCount == 0) {
                        resolveAndPlay(originalRequest, video.playbackPositionMs, thorough = true)
                    } else {
                        exoPlayer.prepare()
                        exoPlayer.seekTo(pos)
                        exoPlayer.play()
                    }
                },
                onClose = onClose,
                onNextVideo = if (nextVideo != null) onNextVideo else null,
                modifier = Modifier.align(Alignment.Center),
                title = errorTitle ?: "Can't play this video",
                detail = errorDetail,
                actionLabel = errorAction?.first,
                onAction = errorAction?.second,
                showRetry = errorCanRetry,
            )
        }

        // Round 9: the pickers follow the user's theme (light or dark) instead of a fixed dark
        // sheet, so the player's popups match the rest of the app.
        dev.asfakul.yumeiro.ui.theme.AppThemeLayer {
        if (showQualityDialog) {
            QualityDialog(
                options = qualityList,
                selected = selectedQuality,
                autoLabel = "Auto",
                onSelect = { option ->
                    val builder = exoPlayer.trackSelectionParameters.buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                        .setMaxVideoBitrate(Int.MAX_VALUE)
                    val group = option?.let { exoPlayer.currentTracks.groups.getOrNull(it.groupIndex) }
                    if (option != null && group != null) {
                        builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
                    }
                    exoPlayer.trackSelectionParameters = builder.build()
                    selectedQuality = if (group != null) option else null
                    showToast("Quality: ${selectedQuality?.label ?: "Auto"}")
                },
                onDismiss = { showQualityDialog = false },
            )
        }

        // Dialogs & Modals
        if (showSpeedDialog) {
            PlaybackSpeedDialog(
                currentSpeed = currentSpeed,
                onSpeedSelect = { speed ->
                    currentSpeed = speed
                    exoPlayer.playbackParameters = PlaybackParameters(speed)
                    showToast("Speed: ${speed}x")
                },
                onDismiss = { showSpeedDialog = false }
            )
        }

        if (showAspectRatioDialog) {
            AspectRatioDialog(
                currentResizeMode = resizeMode,
                onResizeModeSelect = { mode, label ->
                    resizeMode = mode
                    videoZoom = dev.asfakul.yumeiro.player.VideoZoom.State()
                    showToast("Aspect: $label")
                },
                onDismiss = { showAspectRatioDialog = false }
            )
        }

        if (showAudioTrackDialog) {
            AudioTrackDialog(
                audioTracks = audioTracksList,
                onSelectTrack = { groupIndex, trackIndex -> switchAudio(groupIndex, trackIndex) },
                onDismiss = { showAudioTrackDialog = false }
            )
        }

        if (showSubtitleDialog) {
            SubtitleOptionsDialog(
                subtitleTracks = subtitleTracksList,
                isSubtitlesDisabled = isSubtitlesDisabled,
                externalSubtitleName = externalSubtitleName,
                subtitleDelayMs = subtitleDelayMs,
                onPickExternalFile = {
                    subtitlePickerLauncher.launch(
                        arrayOf("*/*", "text/plain", "application/x-subrip", "text/vtt")
                    )
                },
                onSearchOnline = {
                    showSubtitleDialog = false
                    showOnlineSubtitleDialog = true
                },
                onSelectInternalTrack = { groupIndex, trackIndex ->
                    isSubtitlesDisabled = false
                    val tracks = exoPlayer.currentTracks
                    if (groupIndex in 0 until tracks.groups.size) {
                        val group = tracks.groups[groupIndex]
                        val override = TrackSelectionOverride(group.mediaTrackGroup, trackIndex)
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setOverrideForType(override)
                            .build()
                        showToast("Selected subtitle track")
                    }
                },
                onDisableSubtitles = {
                    isSubtitlesDisabled = true
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                    showToast("Subtitles turned off")
                },
                moreFromSource = lateSubs.map { sub ->
                    sub.language to {
                        lateSubs = lateSubs - sub
                        addExternalSubtitle(Uri.parse(sub.url), dev.asfakul.yumeiro.online.OnlinePlayback.subtitleMime(sub.url),
                            dev.asfakul.yumeiro.online.OnlinePlayback.languageTag(sub.language) ?: "und", sub.language)
                    }
                },
                onAdjustDelay = { newDelayMs ->
                    subtitleDelayMs = newDelayMs
                    applySubtitleDelay(newDelayMs)
                    showToast("Subtitle delay: ${if (newDelayMs >= 0) "+$newDelayMs" else "$newDelayMs"} ms")
                },
                onOpenStyle = { showSubtitleDialog = false; showSubtitleStyleDialog = true },
                onDismiss = { showSubtitleDialog = false }
            )
        }

        if (showSubtitleStyleDialog) {
            SubtitleStyleDialog(onDismiss = { showSubtitleStyleDialog = false })
        }

        if (showOnlineSubtitleDialog) {
            OnlineSubtitleDialog(
                videoTitle = video.title,
                videoUri = video.uri,
                onLoaded = { sub ->
                    showOnlineSubtitleDialog = false
                    addExternalSubtitle(sub.uri, sub.mimeType, sub.language, sub.label)
                    showToast("Subtitle loaded: ${sub.label}")
                },
                onDismiss = { showOnlineSubtitleDialog = false }
            )
        }

        if (showVideoInfoDialog) {
            VideoInfoDialog(
                video = video,
                videoFormat = activeVideoFormat,
                audioFormat = activeAudioFormat,
                onDismiss = { showVideoInfoDialog = false },
                health = if (isNetworkStream) ({ healthMonitor.snapshot() }) else null,
                dnsSource = { host ->
                    when (SmartDns.lastSourceFor(host)) {
                        DnsSource.SYSTEM -> "System DNS"
                        DnsSource.ALTERNATIVE -> NetworkSettings.dns.value.let { "${it.provider.label} · ${it.transport.label}" }
                        null -> null
                    }
                },
            )
        }
        }
    }
    if (docked) {
        val dockHandle = remember(exoPlayer) {
            PlayerHandle(positionMs = { exoPlayer.currentPosition }, durationMs = { exoPlayer.duration.takeIf { it > 0 } ?: 0L }, seekTo = { exoPlayer.seekTo(it) }, pause = { exoPlayer.pause() }, selectAudio = { k -> selectAudioByKey(k) })
        }
        Column(Modifier.fillMaxWidth().weight(1f).testTag("watch_page")) {
            dev.asfakul.yumeiro.ui.theme.AppThemeLayer {
                Column(Modifier.fillMaxSize().background(appPalette.background)) { watchPage?.invoke(this, dockHandle) }
            }
        }
    }
    }
}

private const val SEEK_STEP_MS = 10_000L

private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull()

/** Error text when every link of an online title was refused (401/403). */
internal fun onlineRefusedDetail(title: String): String =
    "Every source for this video refused playback ($title). Yumeiro already retried each link with browser, site and player " +
        "headers, re-extracted fresh links and tried the site's own page. The host is blocking this title right now: retry later, " +
        "pick another episode, or update the extension under Online → Extensions."

/** True while the player is docked at the top of a watch page (not fullscreen / landscape / PiP). */
val LocalPlayerDocked = androidx.compose.runtime.staticCompositionLocalOf { false }

/** What overlays (online controls, skip buttons) may read from / do to the player. */
class PlayerHandle(
    val positionMs: () -> Long,
    val durationMs: () -> Long,
    val seekTo: (Long) -> Unit,
    val pause: () -> Unit = {},
    /** Round 13: switches the audio track of the playing video to filter key / language; false when it has none. */
    val selectAudio: (String) -> Boolean = { false },
)


/** Tap-to-expand layer, play/pause + close and a thin progress line for the floating player. */
@Composable
private fun BoxScope.FloatingPlayerChrome(
    isPlaying: Boolean,
    progress: Float,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onClose: () -> Unit,
) {
    Box(
        Modifier.matchParentSize()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent, Color.Black.copy(alpha = 0.35f))))
            .clickable(onClick = onExpand)
            .testTag("floating_expand")
    )
    Row(Modifier.align(Alignment.TopEnd).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        IconButton(onClick = onPlayPause, modifier = Modifier.size(34.dp).testTag("floating_play_pause")) {
            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (isPlaying) "Pause" else "Play", tint = Color.White)
        }
        IconButton(onClick = onClose, modifier = Modifier.size(34.dp).testTag("floating_close")) {
            Icon(Icons.Default.Close, "Close", tint = Color.White)
        }
    }
    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.25f))) {
        Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(dev.asfakul.yumeiro.ui.theme.LocalMtPalette.current.accent))
    }
}

/** Short codec name for the audio menu. */
internal fun codecName(mime: String?): String? {
    val m = mime?.lowercase() ?: return null
    return when {
        "mp4a" in m || "aac" in m -> "AAC"
        "eac3-joc" in m -> "Dolby Atmos"
        "eac3" in m || "ec-3" in m -> "E-AC-3"
        "ac3" in m || "ac-3" in m -> "AC-3"
        "true-hd" in m || "truehd" in m -> "TrueHD"
        "dts" in m -> "DTS"
        "opus" in m -> "Opus"
        "vorbis" in m -> "Vorbis"
        "flac" in m -> "FLAC"
        "mpeg" in m -> "MP3"
        else -> m.substringAfter('/').uppercase().take(10)
    }
}

/** Reports the window y of this element's top edge (rounded), only when it changes. */
private fun Modifier.trackWindowTop(onTop: (Int) -> Unit): Modifier =
    this.then(Modifier.onGloballyPositioned { onTop(it.positionInWindow().y.toInt()) })
