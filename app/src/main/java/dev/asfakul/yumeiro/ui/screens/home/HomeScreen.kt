/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmarks
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

    val tiles = remember(stats.audioCount, stats.videoCount, huesCount) {
        mapOf(
            "music" to HomeShortcut("music", "Music", "${stats.audioCount} songs on device", Icons.Default.LibraryMusic, "音", onNavigateToMusic),
            "ytmusic" to HomeShortcut("ytmusic", "YT Music", "Stream any song", Icons.Default.Album, "歌", onOpenYouTubeMusic),
            "eq" to HomeShortcut("eq", "Equalizer", "Bass · 3D · presets", Icons.Default.GraphicEq, "響", onNavigateToEqualizer),
            "hues" to HomeShortcut("hues", "Hues", "Your extension channels · Spectrum feed", Icons.Default.AutoAwesome, "彩", onOpenHues,
                badge = if (huesCount > 0) "$huesCount live" else "Add"),
            "videos" to HomeShortcut("videos", "Videos", "${stats.videoCount} on device", Icons.Default.VideoLibrary, "映", onNavigateToVideos),
            "youtube" to HomeShortcut("youtube", "YouTube", "No login", Icons.Default.SmartDisplay, "動", onOpenYouTube),
            "live" to HomeShortcut("live", "Live TV", "IPTV · M3U", Icons.Default.LiveTv, "生", onOpenLive),
            "library" to HomeShortcut("library", "Library", "Playlists · favorites · stats", Icons.Default.Bookmarks, "棚", onNavigateToLibrary),
            "store" to HomeShortcut("store", "Hue store", "Find more channels", Icons.Default.Storefront, "店", onOpenHueStore),
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
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag("home_list"),
            contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 8.dp, bottom = 170.dp),
        ) {
            // ---- Continue listening & watching ------------------------------------------------
            item(key = "continue_header", contentType = "header") {
                SectionTitle("Continue listening & watching", overline = "つづき · Pick up where you left off")
            }
            item(key = "continue_row", contentType = "continue") {
                Row(
                    Modifier.fillMaxWidth().padding(top = HeaderGap).height(if (wide) 250.dp else 228.dp),
                    horizontalArrangement = Arrangement.spacedBy(TileGap)
                ) {
                    val isCurrent = continueTrack != null && selectedTrack?.id == continueTrack.id
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
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    ResumeVideoCard(resumeVideo, onEmptyClick = onNavigateToVideos, modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }

            // ---- Shortcuts ----------------------------------------------------------------
            item(key = "bento_label", contentType = "header") {
                SectionTitle("Shortcuts", overline = "すべて · Everything in Yumeiro", modifier = Modifier.padding(top = SectionGap))
            }
            item(key = "bento_listen", contentType = "bento") {
                Row(Modifier.fillMaxWidth().padding(top = HeaderGap).height(if (wide) 236.dp else 216.dp), horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                    BentoTile(tiles.getValue("music"), Modifier.weight(1.15f).fillMaxHeight(), big = true, testTag = "quick_card_music")
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(TileGap)) {
                        BentoTile(tiles.getValue("ytmusic"), Modifier.fillMaxWidth().weight(1f), testTag = "quick_card_ytmusic")
                        BentoTile(tiles.getValue("eq"), Modifier.fillMaxWidth().weight(1f), testTag = "quick_card_audio_engine")
                    }
                }
            }
            item(key = "bento_hues", contentType = "bento") {
                BentoTile(tiles.getValue("hues"), Modifier.fillMaxWidth().padding(top = TileGap).height(96.dp), wideLayout = true, testTag = "quick_card_hues")
            }
            item(key = "bento_watch", contentType = "bento") {
                Row(Modifier.fillMaxWidth().padding(top = TileGap).height(if (wide) 140.dp else 124.dp), horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                    BentoTile(tiles.getValue("videos"), Modifier.weight(1f).fillMaxHeight(), testTag = "quick_card_videos")
                    BentoTile(tiles.getValue("youtube"), Modifier.weight(1f).fillMaxHeight(), testTag = "quick_card_youtube")
                    BentoTile(tiles.getValue("live"), Modifier.weight(1f).fillMaxHeight(), testTag = "quick_card_live")
                }
            }
            item(key = "bento_more", contentType = "bento") {
                Row(Modifier.fillMaxWidth().padding(top = TileGap).height(84.dp), horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                    BentoTile(tiles.getValue("library"), Modifier.weight(1.3f).fillMaxHeight(), compact = true, testTag = "quick_card_library")
                    BentoTile(tiles.getValue("store"), Modifier.weight(1f).fillMaxHeight(), compact = true, testTag = "quick_card_store")
                }
            }

            // ---- For you (endless) ----------------------------------------------------------
            item(key = "feed_header", contentType = "header") {
                Row(Modifier.fillMaxWidth().padding(top = SectionGap), verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("For you", overline = "おすすめ · Endless mix of your music & videos", modifier = Modifier.weight(1f))
                    Box(
                        Modifier.size(40.dp).glass(CircleShape)
                            .clickable(role = Role.Button, onClickLabel = "Shuffle feed") { seed = System.nanoTime() }
                            .testTag("home_feed_shuffle"),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Shuffle, contentDescription = "Shuffle feed", tint = TextPrimary, modifier = Modifier.size(20.dp)) }
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
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (onSeeAll != null) {
            Text(
                "See all",
                style = MaterialTheme.typography.labelLarge,
                color = SpotifyGreen,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClick = onSeeAll)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}

// ---- Continue cards -------------------------------------------------------------------------------

@Composable
private fun ResumeCardFrame(modifier: Modifier, onClick: () -> Unit, tag: String, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val palette = LocalMtPalette.current
    Box(
        modifier
            .clip(shape)
            .background(palette.surfaceContainerHigh.copy(alpha = if (palette.isDark) 0.72f else 1f))
            .border(1.dp, rememberGlassBorder(), shape)
            .clickable(onClick = onClick)
            .testTag(tag)
    ) { content() }
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
    val accent = SpotifyGreen
    val track20 = TextPrimary.copy(alpha = 0.16f)
    ResumeCardFrame(modifier, onClick, "home_continue_card") {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                if (track != null) {
                    Box(Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true).align(Alignment.CenterStart)) {
                        TrackArtwork(artUri = track.albumArtUri, key = track.id, size = 200.dp, cornerRadius = 16.dp, contentDescription = track.album, modifier = Modifier.fillMaxSize())
                    }
                } else {
                    EmptyArt(Icons.Default.MusicNote)
                }
                Box(
                    Modifier.align(Alignment.BottomEnd).size(44.dp).clip(CircleShape).background(accent)
                        .clickable(role = Role.Button, onClick = onPlayPause).testTag("home_continue_play"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = LocalMtPalette.current.onAccent, modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("MUSIC · LAST PLAYED", style = OverlineStyle, color = TextTertiary, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(track?.title ?: "Nothing yet", style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track?.artist ?: "Play a song to see it here", style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.fillMaxWidth().height(3.dp).drawBehind {
                    val r = CornerRadius(size.height / 2)
                    drawRoundRect(track20, cornerRadius = r)
                    val p = if (isCurrent && durationMs > 0) (positionMs().toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                    if (p > 0f) drawRoundRect(accent, size = Size(size.width * p, size.height), cornerRadius = r)
                }
            )
        }
    }
}

@Composable
private fun ResumeVideoCard(video: ResumeVideo?, onEmptyClick: () -> Unit, modifier: Modifier) {
    val accent = SpotifyGreen
    val track20 = TextPrimary.copy(alpha = 0.16f)
    ResumeCardFrame(modifier, video?.onClick ?: onEmptyClick, "home_continue_video") {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.35f))) {
                if (video?.thumbnail != null) {
                    if (video.thumbnailHeaders.isNotEmpty() && video.thumbnail is String) {
                        dev.asfakul.yumeiro.ui.screens.online.PosterImage(video.thumbnail, video.thumbnailHeaders, Modifier.fillMaxSize())
                    } else {
                        AsyncImage(model = video.thumbnail, contentDescription = video.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                } else {
                    EmptyArt(Icons.Default.Videocam)
                }
                Box(
                    Modifier.align(Alignment.Center).size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))
                        .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp)) }
            }
            Spacer(Modifier.height(10.dp))
            Text("VIDEO · LAST WATCHED", style = OverlineStyle, color = TextTertiary, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(video?.title ?: "Nothing yet", style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(video?.subtitle ?: "Watch a video to see it here", style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            val p = video?.progress ?: 0f
            Box(
                Modifier.fillMaxWidth().height(3.dp).drawBehind {
                    val r = CornerRadius(size.height / 2)
                    drawRoundRect(track20, cornerRadius = r)
                    if (p > 0f) drawRoundRect(accent, size = Size(size.width * p.coerceIn(0f, 1f), size.height), cornerRadius = r)
                }
            )
        }
    }
}

@Composable
private fun EmptyArt(icon: ImageVector) {
    Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(TextPrimary.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(34.dp)) }
}

// ---- Shortcut tiles --------------------------------------------------------------------------------

/**
 * A "boxy" shortcut. All tiles share one calm, tonal style (neutral surface + accent icon chip +
 * faint kanji watermark); only the primary Music tile is filled with the accent so the grid has
 * one clear focal point instead of a rainbow. [big] = tall hero tile, [wideLayout] = banner,
 * [compact] = slim one-line tile.
 */
@Composable
private fun BentoTile(
    tile: HomeShortcut,
    modifier: Modifier,
    big: Boolean = false,
    wideLayout: Boolean = false,
    compact: Boolean = false,
    testTag: String,
) {
    val palette = LocalMtPalette.current
    val shape = RoundedCornerShape(if (compact) 20.dp else 24.dp)
    val filled = big
    val accent = palette.accent
    val accent2 = palette.accent2
    val surface = palette.surfaceContainerHigh.copy(alpha = if (palette.isDark) 0.72f else 1f)
    val content = if (filled) palette.onAccent else palette.textPrimary
    val sub = if (filled) palette.onAccent.copy(alpha = 0.78f) else palette.textSecondary
    val glyphColor = if (filled) palette.onAccent.copy(alpha = 0.12f) else palette.textPrimary.copy(alpha = 0.05f)
    Box(
        modifier
            .clip(shape)
            .drawBehind {
                if (filled) {
                    drawRect(Brush.linearGradient(listOf(accent, accent2), start = Offset.Zero, end = Offset(size.width, size.height)))
                    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), center = Offset(size.width * 0.1f, 0f), radius = size.maxDimension * 0.7f), radius = size.maxDimension * 0.7f, center = Offset(size.width * 0.1f, 0f))
                } else {
                    drawRect(surface)
                }
            }
            .border(1.dp, if (filled) Brush.linearGradient(listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.05f))) else rememberGlassBorder(), shape)
            .clickable(role = Role.Button, onClickLabel = "Open ${tile.title}", onClick = tile.onClick)
            .semantics { contentDescription = tile.title }
            .testTag(testTag)
    ) {
        Text(
            tile.glyph,
            color = glyphColor,
            fontSize = if (big) 120.sp else if (compact) 52.sp else 76.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = 10.dp, y = if (big) 26.dp else 16.dp),
        )
        if (compact) {
            Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                IconChip(tile.icon, 40.dp, filled)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(tile.title, style = MaterialTheme.typography.titleMedium, color = content, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(tile.subtitle, style = MaterialTheme.typography.bodySmall, color = sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        } else if (wideLayout) {
            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconChip(tile.icon, 50.dp, filled)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(tile.title, style = DisplayTitle, color = content, maxLines = 1)
                    Spacer(Modifier.height(2.dp))
                    Text(tile.subtitle, style = MaterialTheme.typography.bodyMedium, color = sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                tile.badge?.let { Badge(it, filled) }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(if (big) 16.dp else 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconChip(tile.icon, if (big) 48.dp else 34.dp, filled)
                    Spacer(Modifier.weight(1f))
                    tile.badge?.let { Badge(it, filled) }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    tile.title,
                    style = if (big) DisplayTitle else MaterialTheme.typography.titleMedium,
                    color = content, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(tile.subtitle, style = MaterialTheme.typography.bodySmall, color = sub, maxLines = if (big) 2 else 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private val DisplayTitle: TextStyle
    @Composable get() = MaterialTheme.typography.headlineSmall.copy(fontFamily = Unbounded, fontWeight = FontWeight.Bold)

@Composable
private fun IconChip(icon: ImageVector, size: Dp, onFilled: Boolean) {
    val palette = LocalMtPalette.current
    val bg = if (onFilled) Color.Black.copy(alpha = 0.14f) else palette.accent.copy(alpha = if (palette.isDark) 0.16f else 0.12f)
    val tint = if (onFilled) palette.onAccent else if (palette.isDark) palette.accentBright else palette.accent
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(bg),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.52f)) }
}

@Composable
private fun Badge(text: String, onFilled: Boolean) {
    val palette = LocalMtPalette.current
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (onFilled) Color.Black.copy(alpha = 0.16f) else palette.textPrimary.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) { Text(text, style = MaterialTheme.typography.labelMedium, color = if (onFilled) palette.onAccent else palette.textPrimary, fontWeight = FontWeight.Bold, maxLines = 1) }
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
