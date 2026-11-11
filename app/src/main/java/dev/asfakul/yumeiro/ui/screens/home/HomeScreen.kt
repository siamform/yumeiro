/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.asfakul.yumeiro.data.model.AudioTrack
import dev.asfakul.yumeiro.data.model.StorageStats
import dev.asfakul.yumeiro.data.model.VideoItem
import dev.asfakul.yumeiro.ui.components.EmptyMediaView
import dev.asfakul.yumeiro.ui.components.SkeletonBlock
import dev.asfakul.yumeiro.ui.components.TrackArtwork
import dev.asfakul.yumeiro.ui.components.VideoGridCard
import dev.asfakul.yumeiro.ui.components.formatFileSize
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import dev.asfakul.yumeiro.ui.theme.OverlineStyle
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import dev.asfakul.yumeiro.ui.theme.Unbounded
import dev.asfakul.yumeiro.ui.theme.glass
import dev.asfakul.yumeiro.ui.theme.rememberGlassBorder
import dev.asfakul.yumeiro.youtube.YouTube
import dev.asfakul.yumeiro.youtube.YtHistory
import dev.asfakul.yumeiro.youtube.YtItem

// ---- Spacing scale (ArchiveTune-like rhythm: 16dp gutters, 28dp between sections) -----------
private val Gutter = 16.dp
private val SectionGap = 28.dp
private val HeaderGap = 12.dp
private val TileGap = 10.dp
private val ListRowHeight = 72.dp
private val ListThumb = 56.dp

/** One box of the Home shortcut grid. */
@Immutable
data class HomeShortcut(
    val key: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    /** Big decorative kana / kanji watermark (anime vibe). */
    val glyph: String,
    val onClick: () -> Unit,
    val badge: String? = null,
    /** Two-word live status under the key name ("60 songs", "No login"). */
    val meta: String = subtitle,
)

/** The last thing you watched (device video, Hue title or YouTube), shown next to "last played". */
@Immutable
data class ResumeVideo(
    val title: String,
    val subtitle: String,
    /** Coil model for the thumbnail (URL, [dev.asfakul.yumeiro.ui.image.VideoThumb], …). */
    val thumbnail: Any?,
    val thumbnailHeaders: Map<String, String> = emptyMap(),
    val progress: Float = 0f,
    val onClick: () -> Unit,
)

/** Blocks of the endless "For you" feed. */
private sealed interface FeedBlock {
    val id: Int
    data class Tracks(override val id: Int, val title: String, val tracks: List<AudioTrack>) : FeedBlock
    data class Albums(override val id: Int, val title: String, val tracks: List<AudioTrack>) : FeedBlock
    data class Videos(override val id: Int, val title: String, val videos: List<VideoItem>) : FeedBlock
    data class YouTubeRow(override val id: Int, val title: String, val items: List<YtItem>, val music: Boolean) : FeedBlock
    data class OnlinePicks(override val id: Int) : FeedBlock
}

/**
 * Builds an endless, shuffled mix of device songs, device videos and (when you have YouTube
 * history) YouTube suggestions. Each pool is reshuffled with a new seed every time it runs out,
 * so scrolling never ends and consecutive blocks don't repeat the same items.
 */
private class FeedGenerator(
    private val tracks: List<AudioTrack>,
    private val videos: List<VideoItem>,
    private val hasOnline: Boolean,
    private val seed: Long,
) {
    private var nextId = 0
    private var step = 0
    private var trackCycle = 0
    private var videoCycle = 0
    private var trackQueue = ArrayDeque<AudioTrack>()
    private var videoQueue = ArrayDeque<VideoItem>()
    private var onlinePlaced = false
    var yt: List<YtItem> = emptyList()
    var ytMusic: List<YtItem> = emptyList()
    private var ytCursor = 0
    private var ytMusicCursor = 0

    val isEmpty: Boolean get() = tracks.isEmpty() && videos.isEmpty() && !hasOnline

    private fun takeTracks(n: Int): List<AudioTrack> {
        if (tracks.isEmpty()) return emptyList()
        val out = ArrayList<AudioTrack>(n)
        while (out.size < n.coerceAtMost(tracks.size)) {
            if (trackQueue.isEmpty()) trackQueue = ArrayDeque(tracks.shuffled(java.util.Random(seed + 31L * trackCycle++)))
            val t = trackQueue.removeFirst()
            if (out.none { it.id == t.id }) out += t
        }
        return out
    }

    private fun takeVideos(n: Int): List<VideoItem> {
        if (videos.isEmpty()) return emptyList()
        val out = ArrayList<VideoItem>(n)
        while (out.size < n.coerceAtMost(videos.size)) {
            if (videoQueue.isEmpty()) videoQueue = ArrayDeque(videos.shuffled(java.util.Random(seed + 17L * videoCycle++)))
            val v = videoQueue.removeFirst()
            if (out.none { it.id == v.id }) out += v
        }
        return out
    }

    private fun takeYt(music: Boolean, n: Int): List<YtItem> {
        val pool = if (music) ytMusic else yt
        if (pool.isEmpty()) return emptyList()
        val start = if (music) ytMusicCursor else ytCursor
        val out = List(n.coerceAtMost(pool.size)) { pool[(start + it) % pool.size] }
        if (music) ytMusicCursor = (start + n) % pool.size else ytCursor = (start + n) % pool.size
        return out
    }

    /** Produces up to [count] blocks (fewer only when every pool is empty). */
    fun next(count: Int): List<FeedBlock> {
        val out = ArrayList<FeedBlock>(count)
        var guard = 0
        while (out.size < count && guard++ < count * 8) {
            val s = step++
            val block: FeedBlock? = when (s % 6) {
                0 -> takeTracks(4).takeIf { it.isNotEmpty() }?.let { FeedBlock.Tracks(nextId++, if (s == 0) "Quick picks" else trackTitle(s), it) }
                1 -> takeVideos(4).takeIf { it.isNotEmpty() }?.let { FeedBlock.Videos(nextId++, if (s == 1) "Watch next" else videoTitle(s), it) }
                2 -> if (hasOnline && !onlinePlaced) { onlinePlaced = true; FeedBlock.OnlinePicks(nextId++) }
                     else takeYt(false, 10).takeIf { it.isNotEmpty() }?.let { FeedBlock.YouTubeRow(nextId++, "On YouTube for you", it, music = false) }
                3 -> takeTracks(10).takeIf { it.size >= 3 }?.let { FeedBlock.Albums(nextId++, if (s == 3) "Mix for you" else "Another mix", it) }
                4 -> takeYt(true, 10).takeIf { it.isNotEmpty() }?.let { FeedBlock.YouTubeRow(nextId++, "Songs like yours", it, music = true) }
                else -> takeVideos(2).takeIf { it.isNotEmpty() }?.let { FeedBlock.Videos(nextId++, "From your videos", it) }
            }
            if (block != null) out += block
        }
        return out
    }

    private fun trackTitle(s: Int) = listOf("Play these next", "From your music", "Deep cuts", "Fresh ears")[(s / 6) % 4]
    private fun videoTitle(s: Int) = listOf("More to watch", "From your videos", "Rewatch", "Picked for tonight")[(s / 6) % 4]
}

@Composable
fun HomeScreen(
    stats: StorageStats,
    allTracks: List<AudioTrack>,
    recentlyPlayed: List<AudioTrack>,
    mostPlayed: List<AudioTrack>,
    recentVideos: List<VideoItem>,
    selectedTrack: AudioTrack?,
    onTrackClick: (AudioTrack, List<AudioTrack>) -> Unit,
    onTrackOptionsClick: (AudioTrack) -> Unit,
    onVideoClick: (VideoItem, List<VideoItem>) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleVideoFavorite: (String) -> Unit = {},
    onVideoOptionsClick: (VideoItem) -> Unit = {},
    onRescanClick: () -> Unit,
    onNavigateToMusic: () -> Unit,
    onNavigateToVideos: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToEqualizer: () -> Unit = {},
    onOpenYouTubeMusic: () -> Unit = onNavigateToMusic,
    onOpenYouTube: () -> Unit = onNavigateToVideos,
    onOpenHues: () -> Unit = onNavigateToVideos,
    onOpenLive: () -> Unit = onNavigateToLibrary,
    onOpenSearch: () -> Unit = onNavigateToVideos,
    onOpenHueStore: () -> Unit = onNavigateToSettings,
    onOpenDownloads: () -> Unit = onNavigateToLibrary,
    /** Number of installed + working extensions ("Hues"). */
    huesCount: Int = 0,
    isPlaying: Boolean = false,
    durationMs: Long = 0L,
    positionMs: () -> Long = { 0L },
    onTogglePlayPause: () -> Unit = {},
    onOpenNowPlaying: () -> Unit = {},
    /** Last watched video of any source (null → "nothing yet" card). */
    resumeVideo: ResumeVideo? = null,
    /** Plays a YouTube suggestion from the feed (music → as a song, otherwise as a video). */
    onPlayYouTube: (YtItem, List<YtItem>) -> Unit = { _, _ -> },
    /** Optional "Popular online" rows from installed extensions (woven into the feed). */
    onlinePicks: (@Composable (header: @Composable (String, () -> Unit) -> Unit) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val continueTrack = selectedTrack ?: recentlyPlayed.firstOrNull()

    remember { dev.asfakul.yumeiro.online.download.OnlineDownloads.ensureLoaded(context); true }
    val downloadItems by dev.asfakul.yumeiro.online.download.OnlineDownloads.items.collectAsStateCompat()
    val dlSummary = remember(downloadItems) {
        val split = dev.asfakul.yumeiro.online.download.DownloadGroups.split(downloadItems)
        when {
            split.active.isNotEmpty() -> "${split.active.size} in progress" to "${split.active.size}"
            split.isEmpty -> "Save for offline" to null
            else -> "${split.permanentCount} permanent · ${split.offlineCount} offline" to null
        }
    }
    val tiles = remember(stats.audioCount, stats.videoCount, huesCount, dlSummary) {
        mapOf(
            "music" to HomeShortcut("music", "Music", "${stats.audioCount} songs on device", Icons.Default.LibraryMusic, "音", onNavigateToMusic, meta = "${stats.audioCount} songs"),
            "ytmusic" to HomeShortcut("ytmusic", "YT Music", "Stream any song", Icons.Default.Album, "歌", onOpenYouTubeMusic, meta = "Stream"),
            "eq" to HomeShortcut("eq", "Equalizer", "Bass · 3D · presets", Icons.Default.GraphicEq, "響", onNavigateToEqualizer, meta = "Presets"),
            "hues" to HomeShortcut("hues", "Hues", "Your extension channels · Spectrum feed", Icons.Default.AutoAwesome, "彩", onOpenHues,
                badge = if (huesCount > 0) "$huesCount live" else null, meta = if (huesCount > 0) "$huesCount live" else "Add one"),
            "videos" to HomeShortcut("videos", "Videos", "${stats.videoCount} on device", Icons.Default.VideoLibrary, "映", onNavigateToVideos, meta = "${stats.videoCount} files"),
            "youtube" to HomeShortcut("youtube", "YouTube", "No login", Icons.Default.SmartDisplay, "動", onOpenYouTube, meta = "No login"),
            "live" to HomeShortcut("live", "Live TV", "IPTV · M3U", Icons.Default.LiveTv, "生", onOpenLive, meta = "IPTV"),
            "library" to HomeShortcut("library", "Library", "Playlists · favorites · stats", Icons.Default.Bookmarks, "棚", onNavigateToLibrary, meta = "Yours"),
            "store" to HomeShortcut("store", "Store", "Hue store · find more channels", Icons.Default.Storefront, "店", onOpenHueStore, meta = "Browse"),
            "downloads" to HomeShortcut("downloads", "Downloads", "Downloads · ${dlSummary.first}", Icons.Default.DownloadForOffline, "保", onOpenDownloads, badge = dlSummary.second,
                meta = if (dlSummary.second != null) "${dlSummary.second} active" else "Offline"),
        )
    }

    // ---- Endless feed state -----------------------------------------------------------------
    var seed by remember { mutableStateOf(System.nanoTime()) }
    val ytHistory by YtHistory.items.collectAsStateCompat()
    val generator = remember(allTracks, recentVideos, onlinePicks != null, seed) {
        FeedGenerator(allTracks, recentVideos, hasOnline = onlinePicks != null, seed = seed)
    }
    val blocks = remember(generator) { mutableStateListOf<FeedBlock>().apply { addAll(generator.next(6)) } }
    var ytLoaded by remember(generator) { mutableIntStateOf(0) }
    // YouTube suggestions: related to what you last played / watched there (only if you have history).
    LaunchedEffect(generator, ytHistory.isNotEmpty()) {
        YtHistory.load(context)
        val history = YtHistory.items.value
        if (history.isEmpty()) return@LaunchedEffect
        val lastVideo = history.firstOrNull { !it.music }
        val lastSong = history.firstOrNull { it.music }
        runCatching {
            if (lastVideo != null) generator.yt = YouTube.related(context, lastVideo.id, music = false).filterNot { it.live }
            if (lastSong != null) generator.ytMusic = YouTube.related(context, lastSong.id, music = true)
        }
        ytLoaded++
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen_root")
    ) {
        val wide = maxWidth >= 600.dp
        val videoColumns = when {
            maxWidth >= 1000.dp -> 4
            maxWidth >= 560.dp -> 3
            else -> 2
        }
        val listState = dev.asfakul.yumeiro.ui.components.ScrollMemory.list("home")
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag("home_list"),
            contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 8.dp, bottom = 170.dp),
        ) {
            // ---- Tonight: one resume panel (music above, video below) ------------------------
            item(key = "continue_header", contentType = "header") {
                dev.asfakul.yumeiro.ui.components.MoonSectionHeader(
                    "Pick up where you left off", overline = "つづき · Tonight",
                )
            }
            item(key = "continue_row", contentType = "continue") {
                val isCurrent = continueTrack != null && selectedTrack?.id == continueTrack.id
                dev.asfakul.yumeiro.ui.components.YumePanel(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    shape = RoundedCornerShape(dev.asfakul.yumeiro.ui.theme.Yume.radiusLg),
                ) {
                    ResumeMusicCard(
                        track = continueTrack,
                        isCurrent = isCurrent,
                        isPlaying = isCurrent && isPlaying,
                        durationMs = if (isCurrent) durationMs else 0L,
                        positionMs = positionMs,
                        onClick = {
                            when {
                                continueTrack == null -> onNavigateToMusic()
                                isCurrent -> onOpenNowPlaying()
                                else -> onTrackClick(continueTrack, recentlyPlayed.ifEmpty { allTracks })
                            }
                        },
                        onPlayPause = {
                            when {
                                continueTrack == null -> onNavigateToMusic()
                                isCurrent -> onTogglePlayPause()
                                else -> onTrackClick(continueTrack, recentlyPlayed.ifEmpty { allTracks })
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(TextPrimary.copy(alpha = 0.07f)))
                    ResumeVideoCard(resumeVideo, onEmptyClick = onNavigateToVideos, modifier = Modifier.fillMaxWidth())
                }
            }

            // ---- Spaces: every corner of Yumeiro as a kana key -----------------------------------
            item(key = "bento_label", contentType = "header") {
                dev.asfakul.yumeiro.ui.components.MoonSectionHeader(
                    "Spaces", overline = "すべて · Everything in Yumeiro", modifier = Modifier.padding(top = SectionGap - 8.dp),
                )
            }
            item(key = "bento_listen", contentType = "bento") {
                val cols = if (wide) 10 else 5
                val keys = listOf(
                    "music" to "quick_card_music", "ytmusic" to "quick_card_ytmusic", "eq" to "quick_card_audio_engine",
                    "hues" to "quick_card_hues", "store" to "quick_card_store",
                    "videos" to "quick_card_videos", "youtube" to "quick_card_youtube", "live" to "quick_card_live",
                    "library" to "quick_card_library", "downloads" to "quick_card_downloads",
                )
                Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(TileGap)) {
                    keys.chunked(cols).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (k, tag) -> KanaKey(tiles.getValue(k), Modifier.weight(1f), primary = k == "music", testTag = tag) }
                        }
                    }
                }
            }

            // ---- For you (endless) ----------------------------------------------------------
            item(key = "feed_header", contentType = "header") {
                Row(Modifier.fillMaxWidth().padding(top = SectionGap - 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    dev.asfakul.yumeiro.ui.components.MoonSectionHeader(
                        "For you", overline = "おすすめ · Your music & videos, endlessly", modifier = Modifier.weight(1f),
                    )
                    dev.asfakul.yumeiro.ui.components.YumeIconButton(
                        Icons.Default.Shuffle, "Shuffle feed", onClick = { seed = System.nanoTime() },
                        modifier = Modifier.testTag("home_feed_shuffle"),
                    )
                }
            }

            if (generator.isEmpty) {
                item(key = "empty_feed") {
                    Box(Modifier.padding(top = HeaderGap)) {
                        EmptyMediaView(
                            title = "Nothing on this device yet",
                            subtitle = "Put music or videos in local storage and tap Scan — or open YT Music, YouTube or a Hue above.",
                            buttonText = "Scan storage",
                            onButtonClick = onRescanClick
                        )
                    }
                }
            }

            blocks.forEach { block ->
                when (block) {
                    is FeedBlock.Tracks -> {
                        item(key = "f${block.id}_h", contentType = "feed_header") { FeedHeader(block.title) }
                        items(block.tracks, key = { "f${block.id}_${it.id}" }, contentType = { "feed_track" }) { track ->
                            FeedTrackRow(
                                track = track,
                                isCurrent = selectedTrack?.id == track.id,
                                onClick = { onTrackClick(track, block.tracks) },
                                onMore = { onTrackOptionsClick(track) },
                            )
                        }
                    }
                    is FeedBlock.Albums -> {
                        item(key = "f${block.id}_h", contentType = "feed_header") { FeedHeader(block.title, onSeeAll = onNavigateToMusic) }
                        item(key = "f${block.id}_row", contentType = "feed_albums") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                                items(block.tracks, key = { "f${block.id}_a_${it.id}" }) { track ->
                                    AlbumTile(track = track, isCurrent = selectedTrack?.id == track.id) { onTrackClick(track, block.tracks) }
                                }
                            }
                        }
                    }
                    is FeedBlock.Videos -> {
                        item(key = "f${block.id}_h", contentType = "feed_header") { FeedHeader(block.title, onSeeAll = onNavigateToVideos) }
                        val rows = block.videos.chunked(videoColumns)
                        rows.forEachIndexed { rowIndex, rowVideos ->
                            item(key = "f${block.id}_r$rowIndex", contentType = "video_row") {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("home_video_grid_row"),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    rowVideos.forEach { video ->
                                        VideoGridCard(
                                            video = video,
                                            onVideoClick = { onVideoClick(video, block.videos) },
                                            onToggleFavorite = { onToggleVideoFavorite(video.id) },
                                            onOptionsClick = { onVideoOptionsClick(video) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    repeat(videoColumns - rowVideos.size) { Spacer(modifier = Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                    is FeedBlock.YouTubeRow -> {
                        item(key = "f${block.id}_h", contentType = "feed_header") { FeedHeader(block.title, onSeeAll = if (block.music) onOpenYouTubeMusic else onOpenYouTube) }
                        item(key = "f${block.id}_row", contentType = "feed_yt") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                items(block.items, key = { "f${block.id}_y_${it.id}" }) { yt ->
                                    YtFeedCard(yt, square = block.music) { onPlayYouTube(yt, block.items) }
                                }
                            }
                        }
                    }
                    is FeedBlock.OnlinePicks -> if (onlinePicks != null) {
                        item(key = "f${block.id}_online", contentType = "online") {
                            Column(Modifier.padding(top = SectionGap - 8.dp)) {
                                onlinePicks { title, seeAll -> SectionHeader(title, onSeeAll = seeAll) }
                            }
                        }
                    }
                }
            }

            if (!generator.isEmpty && blocks.size < MAX_FEED_BLOCKS) {
                item(key = "feed_loader_${blocks.size}", contentType = "loader") {
                    // Composed only when scrolled near the end → append the next page of blocks.
                    LaunchedEffect(blocks.size, ytLoaded) {
                        val more = generator.next(4)
                        if (more.isNotEmpty()) blocks.addAll(more)
                    }
                    FeedLoadingSkeleton()
                }
            } else if (!generator.isEmpty) {
                item(key = "feed_end") {
                    Text(
                        "You reached the bottom of the dream — shuffle for a new mix.",
                        style = MaterialTheme.typography.bodySmall, color = TextTertiary,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    )
                }
            }
        }
    }
}

private const val MAX_FEED_BLOCKS = 240

@Composable
private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAsStateCompat(): androidx.compose.runtime.State<T> = this.collectAsState()

// ---- Section titles ---------------------------------------------------------------------------

@Composable
private fun SectionTitle(title: String, overline: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        if (overline != null) {
            Text(overline.uppercase(), style = OverlineStyle, color = SpotifyGreen, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FeedHeader(title: String, onSeeAll: (() -> Unit)? = null) {
    SectionHeader(title, modifier = Modifier.padding(top = SectionGap - 8.dp, bottom = HeaderGap - 4.dp), onSeeAll = onSeeAll)
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, onSeeAll: (() -> Unit)? = null) {
    dev.asfakul.yumeiro.ui.components.MoonSectionHeader(title, modifier = modifier, onAction = onSeeAll)
}

// ---- Continue cards -------------------------------------------------------------------------------

/** One row of the Tonight panel: art · eyebrow/title/subtitle · action, with a moon-path progress line. */
@Composable
private fun ResumeRow(
    modifier: Modifier,
    tag: String,
    onClick: () -> Unit,
    eyebrow: String,
    title: String,
    subtitle: String,
    progress: () -> Float,
    art: @Composable () -> Unit,
    action: @Composable () -> Unit,
) {
    val track20 = TextPrimary.copy(alpha = 0.10f)
    val p = LocalMtPalette.current
    Row(
        modifier.clickable(onClick = onClick).testTag(tag).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        art()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(eyebrow, style = OverlineStyle, color = if (p.isDark) p.accentBright.copy(alpha = 0.9f) else p.accent, maxLines = 1)
            Spacer(Modifier.height(3.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            val accent = p.accent
            val accent2 = p.accent2
            Box(
                Modifier.fillMaxWidth().height(3.dp).drawBehind {
                    val r = CornerRadius(size.height / 2)
                    drawRoundRect(track20, cornerRadius = r)
                    val v = progress().coerceIn(0f, 1f)
                    if (v > 0f) drawRoundRect(
                        Brush.horizontalGradient(listOf(accent2, accent)),
                        size = Size(size.width * v, size.height), cornerRadius = r,
                    )
                }
            )
        }
        Spacer(Modifier.width(10.dp))
        action()
    }
}

@Composable
private fun ResumeMusicCard(
    track: AudioTrack?,
    isCurrent: Boolean,
    isPlaying: Boolean,
    durationMs: Long,
    positionMs: () -> Long,
    onClick: () -> Unit,
    onPlayPause: () -> Unit,
    modifier: Modifier,
) {
    ResumeRow(
        modifier = modifier,
        tag = "home_continue_card",
        onClick = onClick,
        eyebrow = if (isPlaying) "音 · NOW PLAYING" else "音 · LAST PLAYED",
        title = track?.title ?: "Nothing played yet",
        subtitle = track?.artist ?: "Start a song and it waits for you here",
        progress = { if (isCurrent && durationMs > 0) positionMs().toFloat() / durationMs else 0f },
        art = {
            Box(Modifier.size(64.dp)) {
                if (track != null) {
                    TrackArtwork(artUri = track.albumArtUri, key = track.id, size = 64.dp, cornerRadius = 12.dp, contentDescription = track.album)
                } else EmptyArt(Icons.Default.MusicNote)
            }
        },
        action = {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(SpotifyGreen)
                    .clickable(role = Role.Button, onClick = onPlayPause).testTag("home_continue_play"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else if (track == null) "Open Music" else "Play",
                    tint = LocalMtPalette.current.onAccent, modifier = Modifier.size(24.dp)
                )
            }
        },
    )
}

@Composable
private fun ResumeVideoCard(video: ResumeVideo?, onEmptyClick: () -> Unit, modifier: Modifier) {
    ResumeRow(
        modifier = modifier,
        tag = "home_continue_video",
        onClick = video?.onClick ?: onEmptyClick,
        eyebrow = "映 · LAST WATCHED",
        title = video?.title ?: "Nothing watched yet",
        subtitle = video?.subtitle ?: "Videos you start will resume from here",
        progress = { video?.progress ?: 0f },
        art = {
            Box(Modifier.width(64.dp).height(64.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.35f))) {
                    if (video?.thumbnail != null) {
                        if (video.thumbnailHeaders.isNotEmpty() && video.thumbnail is String) {
                            dev.asfakul.yumeiro.ui.screens.online.PosterImage(video.thumbnail, video.thumbnailHeaders, Modifier.fillMaxSize())
                        } else {
                            AsyncImage(model = video.thumbnail, contentDescription = video.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    } else EmptyArt(Icons.Default.Videocam)
                }
            }
        },
        action = {
            Box(
                Modifier.size(48.dp).clip(CircleShape).border(1.dp, SpotifyGreen.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (video == null) Icons.Default.VideoLibrary else Icons.Default.PlayArrow,
                    contentDescription = null, tint = SpotifyGreen, modifier = Modifier.size(22.dp),
                )
            }
        },
    )
}

@Composable
private fun EmptyArt(icon: ImageVector) {
    Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(TextPrimary.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(26.dp)) }
}

// ---- Spaces (kana keys) ---------------------------------------------------------------------------

/**
 * A kana key: each corner of Yumeiro is one compact key with its kana (音 歌 響 彩 店 映 動 生 棚 保)
 * as the symbol, the name below and a short live status (song count, "3 live", downloads in
 * progress). Ten keys in two rows replace the old ~600dp bento wall, so the feed starts sooner,
 * while every destination stays one tap away and always visible. Only Music is filled gold.
 */
@Composable
private fun KanaKey(tile: HomeShortcut, modifier: Modifier, primary: Boolean, testTag: String) {
    val palette = LocalMtPalette.current
    val shape = RoundedCornerShape(dev.asfakul.yumeiro.ui.theme.Yume.radiusMd)
    val accent = palette.accent
    val accent2 = palette.accent2
    val surface = palette.surfaceContainerHigh.copy(alpha = if (palette.isDark) 0.66f else 1f)
    val glyphColor = if (primary) palette.onAccent else if (palette.isDark) palette.accentBright else palette.accent
    val titleColor = if (primary) palette.onAccent else palette.textPrimary
    val subColor = if (primary) palette.onAccent.copy(alpha = 0.74f) else palette.textTertiary
    Column(
        modifier
            .height(96.dp)
            .clip(shape)
            .drawBehind {
                if (primary) drawRect(Brush.linearGradient(listOf(accent, accent2), start = Offset.Zero, end = Offset(size.width, size.height)))
                else drawRect(surface)
            }
            .border(1.dp, if (primary) Brush.linearGradient(listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.04f))) else rememberGlassBorder(), shape)
            .clickable(role = Role.Button, onClickLabel = "Open ${tile.title}", onClick = tile.onClick)
            .semantics(mergeDescendants = true) { contentDescription = "${tile.title}, ${tile.subtitle}" + (tile.badge?.let { ", $it" } ?: "") }
            .testTag(testTag)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(34.dp), contentAlignment = Alignment.Center) {
            Text(tile.glyph, color = glyphColor, fontSize = 26.sp, fontWeight = FontWeight.Black, maxLines = 1)
            tile.badge?.let { b ->
                Box(
                    Modifier.align(Alignment.TopEnd).padding(end = 2.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (primary) Color.Black.copy(alpha = 0.18f) else palette.accent.copy(alpha = if (palette.isDark) 0.22f else 0.14f))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) { Text(b.substringBefore(' '), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (primary) palette.onAccent else glyphColor, maxLines = 1) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            tile.title,
            style = MaterialTheme.typography.labelMedium.let { if (tile.title.length > 8) it.copy(fontSize = 10.5.sp, letterSpacing = 0.sp) else it },
            fontWeight = FontWeight.Bold, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(tile.meta, style = MaterialTheme.typography.labelSmall, color = subColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---- Feed items ------------------------------------------------------------------------------------

@Composable
private fun FeedTrackRow(track: AudioTrack, isCurrent: Boolean, onClick: () -> Unit, onMore: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(ListRowHeight)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp)
            .testTag("home_feed_track_${track.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackArtwork(artUri = track.albumArtUri, key = track.id, size = ListThumb, cornerRadius = 12.dp, contentDescription = track.album)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.titleMedium, color = if (isCurrent) SpotifyGreen else TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text("${track.artist} · ${formatMs(track.durationMs)}", style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onMore),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = TextTertiary, modifier = Modifier.size(20.dp)) }
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "—"
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun AlbumTile(track: AudioTrack, isCurrent: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(132.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("home_added_${track.id}")
    ) {
        TrackArtwork(artUri = track.albumArtUri, key = track.id, size = 132.dp, cornerRadius = 16.dp, contentDescription = track.album)
        Spacer(Modifier.height(8.dp))
        Text(track.title, style = MaterialTheme.typography.titleSmall, color = if (isCurrent) SpotifyGreen else TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(track.artist, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun YtFeedCard(item: YtItem, square: Boolean, onClick: () -> Unit) {
    val width = if (square) 132.dp else 232.dp
    Column(Modifier.width(width).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).testTag("home_feed_yt_${item.id}")) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(if (square) 1f else 16f / 9f).clip(RoundedCornerShape(16.dp))
                .background(TextPrimary.copy(alpha = 0.06f))
        ) {
            AsyncImage(model = item.thumbnail, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (!square && item.durationSec > 0) {
                Text(
                    formatMs(item.durationSec * 1000), style = MaterialTheme.typography.labelSmall, color = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(item.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(item.channel, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FeedLoadingSkeleton() {
    Column(Modifier.fillMaxWidth().padding(top = SectionGap - 8.dp).testTag("home_feed_loading")) {
        SkeletonBlock(width = 160.dp, height = 20.dp)
        Spacer(Modifier.height(HeaderGap))
        repeat(2) {
            Row(Modifier.fillMaxWidth().height(ListRowHeight), verticalAlignment = Alignment.CenterVertically) {
                SkeletonBlock(width = ListThumb, height = ListThumb, shape = RoundedCornerShape(12.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    SkeletonBlock(width = null, height = 14.dp, modifier = Modifier.fillMaxWidth(0.7f))
                    Spacer(Modifier.height(8.dp))
                    SkeletonBlock(width = null, height = 12.dp, modifier = Modifier.fillMaxWidth(0.45f))
                }
            }
        }
    }
}

// ---- Dream deck (shown at the top of Library) --------------------------------------------------------

/** Overview of everything you have: songs, videos, Hues and storage, with a rescan button. */
@Composable
fun DreamDeckCard(stats: StorageStats, huesCount: Int, onRescanClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalMtPalette.current
    val shape = RoundedCornerShape(24.dp)
    val accent = palette.accent
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.surfaceContainerHigh.copy(alpha = if (palette.isDark) 0.72f else 1f))
            .drawBehind {
                drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.18f), Color.Transparent), center = Offset(size.width, 0f), radius = size.width * 0.6f), radius = size.width * 0.6f, center = Offset(size.width, 0f))
            }
            .border(1.dp, rememberGlassBorder(), shape)
            .testTag("storage_stat_banner"),
    ) {
        Text(
            "夢", color = palette.textPrimary.copy(alpha = 0.05f), fontSize = 120.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.CenterEnd).offset(x = 8.dp),
        )
        Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 16.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("YOUR DREAM DECK", style = OverlineStyle, color = SpotifyGreen)
                    Spacer(Modifier.height(4.dp))
                    Text("Music, video, live & Hues — all in one", style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 2)
                }
                if (stats.isScanning) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = SpotifyGreen, strokeWidth = 2.5.dp)
                } else {
                    Box(
                        Modifier.size(40.dp).glass(CircleShape)
                            .clickable(role = Role.Button, onClick = onRescanClick)
                            .testTag("banner_rescan_button"),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Refresh, contentDescription = "Rescan media", tint = TextPrimary, modifier = Modifier.size(20.dp)) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill("${stats.audioCount}", "songs", Modifier.weight(1f))
                StatPill("${stats.videoCount}", "videos", Modifier.weight(1f))
                StatPill("$huesCount", "Hues", Modifier.weight(1f))
                StatPill(formatFileSize(stats.totalStorageUsedBytes), "stored", Modifier.weight(1.25f))
            }
        }
    }
}

@Composable
private fun StatPill(value: String, label: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(TextPrimary.copy(alpha = 0.06f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1)
    }
}

/** Kept for callers/tests that referenced the old Home helpers. */
@Composable
fun rememberGreeting(): String = dev.asfakul.yumeiro.ui.components.greetingForNow()
