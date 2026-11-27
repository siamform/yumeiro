/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.youtube

import androidx.compose.ui.res.stringResource
import dev.asfakul.yumeiro.R
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import dev.asfakul.yumeiro.stream.StreamRequest
import dev.asfakul.yumeiro.youtube.YtItem
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * F-12 (ADR-018): which neighbours of the Short on screen get their link resolved ahead. Only links are
 * resolved (a small network call); no second player or decoder is ever built, so a 2 GB phone stays at one.
 */
object ShortsWindow {
    /** Pages to resolve after [index] settles, nearest first: 2 ahead + 1 behind; 1 ahead on low-RAM phones. */
    fun prefetch(index: Int, size: Int, lowEnd: Boolean): List<Int> {
        val wanted = if (lowEnd) listOf(index + 1) else listOf(index + 1, index + 2, index - 1)
        return wanted.filter { it in 0 until size && it != index }
    }

    /** Resolved links kept in memory; everything else is dropped so long sessions stay flat. */
    fun keep(index: Int, size: Int, lowEnd: Boolean): Set<Int> {
        val r = if (lowEnd) 1 else 2
        return ((index - r)..(index + r)).filter { it in 0 until size }.toSet()
    }

    /** The Shorts player opens for a Short on phones when the feature is on; TV keeps the normal player. */
    fun opens(isShort: Boolean, queueSize: Int, tv: Boolean, enabled: Boolean): Boolean =
        enabled && !tv && isShort && queueSize >= 1

    /** ADR-056: the top bar and title hide after this long of uninterrupted play. */
    const val CHROME_IDLE_MS = 3_500L

    /** Whether the chrome may hide now: only while playing, never while buffering, paused or on an error. */
    fun chromeMayHide(autohide: Boolean, playing: Boolean, buffering: Boolean, error: Boolean): Boolean =
        autohide && playing && !buffering && !error

    enum class Tap { SHOW_CHROME, TOGGLE_PLAY }

    /** A tap while the chrome is hidden only brings it back; with the chrome up it pauses or plays. */
    fun tap(chromeShown: Boolean): Tap = if (chromeShown) Tap.TOGGLE_PLAY else Tap.SHOW_CHROME

    /** F-12: 9:16 (portrait or square) fills the screen; a landscape clip is fitted so nothing is cropped. */
    @androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class]) // AspectRatioFrameLayout resize constants (Media3 pinned)
    fun resizeFor(width: Int, height: Int, fitLandscape: Boolean): Int =
        if (fitLandscape && width > 0 && height > 0 && width > height) AspectRatioFrameLayout.RESIZE_MODE_FIT
        else AspectRatioFrameLayout.RESIZE_MODE_ZOOM

    /** Device tests only: resolve a Short to a local clip instead of YouTube. Null in the app. */
    @Volatile internal var testResolver: (suspend (YtItem) -> StreamRequest)? = null

    /** F-12 evidence: ms from a page settling to its first rendered frame, per Short id (last 32). */
    internal val firstFrameMs = java.util.concurrent.ConcurrentHashMap<String, Long>()
    /** Identity of every player used by the pager since the last reset; one entry = one shared player. */
    @Volatile internal var lastResize: Int = -1
    /** F12-VIEW evidence: video views inflated since the last reset (1 with the shared view, one per page without). */
    internal val viewsCreated = java.util.concurrent.atomic.AtomicInteger()
    internal val playersUsed: MutableSet<Int> = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap())
}

/** Full-screen vertical pager over [items]; swipe up / down for the next / previous Short. */
@Composable
fun ShortsPlayerDialog(items: List<YtItem>, startIndex: Int, onDismiss: () -> Unit, onFullPlayer: (YtItem) -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        ShortsPlayer(items, startIndex, onDismiss, onFullPlayer)
    }
}

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class]) // PlayerView.resizeMode (Media3 pinned)
@Composable
private fun ShortsPlayer(items: List<YtItem>, startIndex: Int, onClose: () -> Unit, onFullPlayer: (YtItem) -> Unit) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val lowEnd = dev.asfakul.yumeiro.DeviceClass.lowEnd
    val height = if (lowEnd) 480 else 720
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))) { items.size }
    val resolved = remember { mutableStateMapOf<String, StreamRequest>() }
    val failed = remember { mutableStateMapOf<String, String>() }
    val inFlight = remember { HashSet<String>() }
    var lease by remember { mutableStateOf<dev.asfakul.yumeiro.player.PlaybackSession.Lease?>(null) }
    var playing by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    var fast by remember { mutableStateOf(false) }
    var settledAt by remember { mutableStateOf(0L) }
    val framed = remember { mutableStateMapOf<String, Boolean>() }
    val resize = remember { mutableStateMapOf<String, Int>() }
    val oneView = remember(context) { dev.asfakul.yumeiro.FeatureFlags.on(context, dev.asfakul.yumeiro.FeatureFlags.Flag.SHORTS_ONE_VIEW) }
    val fitLandscape = remember(context) { dev.asfakul.yumeiro.FeatureFlags.on(context, dev.asfakul.yumeiro.FeatureFlags.Flag.SHORTS_VERTICAL) }
    val autohide = remember(context) { fitLandscape && dev.asfakul.yumeiro.FeatureFlags.on(context, dev.asfakul.yumeiro.FeatureFlags.Flag.SHORTS_AUTOHIDE) }
    var chrome by remember { mutableStateOf(true) }
    var touched by remember { mutableStateOf(0) }

    fun resolve(i: Int) {
        val item = items.getOrNull(i) ?: return
        if (item.id in resolved || item.id in inFlight) return
        inFlight += item.id
        scope.launch {
            runCatching { ShortsWindow.testResolver?.invoke(item) ?: dev.asfakul.yumeiro.youtube.YouTubeStreams.videoRequest(context, item.id, height) }
                .onSuccess { resolved[item.id] = it; failed.remove(item.id) }
                .onFailure { e -> if (e !is kotlinx.coroutines.CancellationException) failed[item.id] = dev.asfakul.yumeiro.online.OnlineErrors.friendly(e) }
            inFlight -= item.id
        }
    }

    // The page on screen plays in the one shared player (ADR-006); neighbours only get their links.
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { page ->
            settledAt = android.os.SystemClock.elapsedRealtime()
            items.getOrNull(page)?.let { framed.remove(it.id) }
            resolve(page)
            ShortsWindow.prefetch(page, items.size, lowEnd).forEach { resolve(it) }
            val keep = ShortsWindow.keep(page, items.size, lowEnd).mapNotNull { items.getOrNull(it)?.id }.toSet()
            resolved.keys.filterNot { it in keep }.forEach { resolved.remove(it) }
        }
    }
    val current = items.getOrNull(pager.settledPage)
    val currentReq = current?.let { resolved[it.id] }
    // ADR-056: a new page shows its title again; idle play hides the chrome after 3.5 s.
    LaunchedEffect(pager.settledPage) { chrome = true }
    val currentError = current?.let { failed[it.id] } != null
    LaunchedEffect(chrome, playing, buffering, currentError, touched, pager.settledPage) {
        if (chrome && ShortsWindow.chromeMayHide(autohide, playing, buffering, currentError)) {
            kotlinx.coroutines.delay(ShortsWindow.CHROME_IDLE_MS)
            chrome = false
        } else if (!chrome && !ShortsWindow.chromeMayHide(autohide, playing, buffering, currentError)) chrome = true
    }
    LaunchedEffect(current?.id, currentReq) {
        val item = current ?: return@LaunchedEffect
        val req = currentReq ?: return@LaunchedEffect
        val l = dev.asfakul.yumeiro.player.PlaybackSession.acquire(context, "yt:${item.id}", req.url)
        lease = l
        ShortsWindow.playersUsed += System.identityHashCode(l.player)
        buffering = true
        dev.asfakul.yumeiro.stream.StreamRepository.register(req)
        dev.asfakul.yumeiro.stream.StreamMediaFactory.setMedia(l.player, req.url, title = item.title)
        // A4K-3: Shorts use the main (all videos) picture preset and controls; no Anime4K on short clips.
        dev.asfakul.yumeiro.player.PictureLook.setTitle(null)
        val fx = dev.asfakul.yumeiro.player.PictureChain.effects(anime4k = false, hdr = false, withAnime4k = false)
        if (fx.isNotEmpty() || dev.asfakul.yumeiro.stream.StreamMediaFactory.usedEffects(l.player))
            runCatching { dev.asfakul.yumeiro.stream.StreamMediaFactory.setEffects(l.player, fx) }
        l.player.repeatMode = Player.REPEAT_MODE_ONE
        l.player.setPlaybackSpeed(1f)
        l.player.prepare()
        l.player.playWhenReady = true
        playing = true
        dev.asfakul.yumeiro.youtube.YtHistory.record(context, item)
    }
    val currentNow = androidx.compose.runtime.rememberUpdatedState(current)
    DisposableEffect(lease) {
        val l = lease ?: return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { buffering = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE }
            override fun onIsPlayingChanged(isPlaying: Boolean) { if (isPlaying) buffering = false }
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                val id = currentNow.value?.id ?: return
                resize[id] = ShortsWindow.resizeFor(videoSize.width, videoSize.height, fitLandscape)
                ShortsWindow.lastResize = resize[id]!!
            }
            override fun onRenderedFirstFrame() {
                val id = currentNow.value?.id ?: return
                if (ShortsWindow.firstFrameMs.size > 32) ShortsWindow.firstFrameMs.clear()
                ShortsWindow.firstFrameMs[id] = android.os.SystemClock.elapsedRealtime() - settledAt
                framed[id] = true
            }
            override fun onPlayerError(error: PlaybackException) {
                currentNow.value?.let { failed[it.id] = "This Short couldn't play." }
            }
        }
        l.addListener(listener)
        onDispose { l.removeListener(listener) }
    }
    // Closing the pager hands the player back (paused, released after the session's grace time).
    DisposableEffect(Unit) { onDispose { lease?.let { dev.asfakul.yumeiro.player.PlaybackSession.detach(it) } } }
    // App to background: pause. Coming back does not auto-play (same as the full player).
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) { lease?.player?.pause(); playing = false } }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    // One video view for the whole pager (F12-VIEW): the same view and surface for every Short, so a swipe never
    // inflates a PlayerView or builds a new TextureView (a 700+ ms main-thread stall on api36, run 44910278641).
    @Composable
    fun VideoView(item: YtItem, page: Int, modifier: Modifier) {
        AndroidView(
            factory = { ctx ->
                ShortsWindow.viewsCreated.incrementAndGet()
                (android.view.LayoutInflater.from(ctx).inflate(dev.asfakul.yumeiro.R.layout.yumeiro_player_view, null) as PlayerView).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
            },
            update = { v ->
                if (v.player !== lease?.player) v.player = lease?.player
                val m = resize[item.id] ?: AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                if (v.resizeMode != m) v.resizeMode = m
            },
            onRelease = { v -> v.player = null },
            modifier = modifier.testTag(if (framed[item.id] == true) "shorts_frame_$page" else "shorts_video_$page"),
        )
    }

    Box(Modifier.fillMaxSize().background(Color.Black).testTag("shorts_player")) {
        val settledItem = items.getOrNull(pager.settledPage)
        if (oneView && lease != null && settledItem != null && resolved[settledItem.id] != null) {
            // Under the pages; follows the settled page while the user drags (TextureView follows Compose transforms).
            VideoView(settledItem, pager.settledPage, Modifier.fillMaxSize().graphicsLayer {
                translationY = (pager.settledPage - (pager.currentPage + pager.currentPageOffsetFraction)) * size.height
            })
        }
        VerticalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 0, key = { items[it].id }) { page ->
            val item = items[page]
            Box(Modifier.fillMaxSize().testTag("shorts_page_$page")) {
                val onScreen = page == pager.settledPage && lease != null && resolved[item.id] != null
                when {
                    // The shared view below shows through once this Short has its first frame; the poster covers
                    // the previous Short's last frame until then.
                    oneView && onScreen && framed[item.id] == true -> Unit
                    !oneView && onScreen -> VideoView(item, page, Modifier.fillMaxSize())
                    else -> dev.asfakul.yumeiro.ui.screens.online.PosterImage(item.thumbnail, emptyMap(), Modifier.fillMaxSize(), Icons.Default.SmartDisplay)
                }
                // Tap: pause / play. Hold: 2x while held.
                Box(
                    Modifier.fillMaxSize().testTag("shorts_touch").pointerInput(item.id) {
                        detectTapGestures(
                            onTap = {
                                touched++
                                if (ShortsWindow.tap(chrome) == ShortsWindow.Tap.SHOW_CHROME) { chrome = true; return@detectTapGestures }
                                val p = lease?.player ?: return@detectTapGestures
                                if (p.isPlaying) { p.pause(); playing = false } else { p.play(); playing = true }
                            },
                            onPress = {
                                val p = lease?.player
                                val held = kotlinx.coroutines.withTimeoutOrNull(450) { tryAwaitRelease() }
                                if (held == null && p != null && p.isPlaying) {
                                    p.setPlaybackSpeed(2f); fast = true
                                    tryAwaitRelease()
                                    p.setPlaybackSpeed(1f); fast = false
                                }
                            },
                        )
                    }
                )
                val err = failed[item.id]
                when {
                    err != null -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(err, color = Color.White)
                        TextButton(onClick = { failed.remove(item.id); resolved.remove(item.id); resolve(page) }, modifier = Modifier.testTag("shorts_retry")) { Text(stringResource(R.string.retry)) }
                    }
                    page == pager.settledPage && (resolved[item.id] == null || buffering) ->
                        CircularProgressIndicator(Modifier.align(Alignment.Center).size(40.dp), color = Color.White)
                    page == pager.settledPage && !playing ->
                        Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(64.dp).testTag("shorts_paused"))
                }
                if (fast && page == pager.settledPage) {
                    Text(stringResource(R.string.t_2x), color = Color.White, modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 16.dp)
                        .background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 10.dp, vertical = 4.dp).testTag("shorts_fast"))
                }
                if (chrome) Column(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().testTag("shorts_title")
                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                        .safeDrawingPadding().padding(16.dp)
                ) {
                    Text(item.title, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (item.channel.isNotBlank()) Text(item.channel, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
                }
            }
        }
        if (chrome) Row(Modifier.fillMaxWidth().safeDrawingPadding().padding(4.dp).testTag("shorts_chrome"), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, modifier = Modifier.testTag("shorts_close")) { Icon(Icons.Default.ArrowBack, stringResource(R.string.close_shorts), tint = Color.White) }
            TextButton(
                onClick = { current?.let { lease?.player?.pause(); onFullPlayer(it) } },
                modifier = Modifier.testTag("shorts_full_player").semantics { contentDescription = "Full player" },
            ) {
                Icon(Icons.Default.OpenInFull, null, tint = Color.White)
                Text(stringResource(R.string.full_player), color = Color.White)
            }
        }
    }
}
