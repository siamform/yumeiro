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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.asfakul.yumeiro.data.model.AudioTrack
import dev.asfakul.yumeiro.data.model.StorageStats
import dev.asfakul.yumeiro.data.model.VideoItem
import dev.asfakul.yumeiro.online.OnlineBookmarks
import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.ui.components.EmptyMediaView
import dev.asfakul.yumeiro.ui.components.SkeletonBlock
import dev.asfakul.yumeiro.ui.components.TrackArtwork
import dev.asfakul.yumeiro.ui.components.VideoGridCard
import dev.asfakul.yumeiro.ui.screens.extras.SetupGuide
import dev.asfakul.yumeiro.ui.screens.extras.rememberSetupStatus
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import dev.asfakul.yumeiro.youtube.YouTube
import dev.asfakul.yumeiro.youtube.YtHistory
import dev.asfakul.yumeiro.youtube.YtItem
import dev.asfakul.yumeiro.youtube.YtShelf

// ---- Rhythm --------------------------------------------------------------------------------
private val Gutter = 16.dp
private val SectionTop = 26.dp

/** The last thing you watched (device video, Yumix title or YouTube), shown next to "last played". */
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

/** One tile of the Explore grid. */
@Immutable
private data class Feature(
    val tag: String,
    val title: String,
    val status: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val badge: String? = null,
)

/** Mood shelves (real YouTube / YouTube Music lists, loaded on demand). */
private enum class Mood(val label: String, val shelf: YtShelf) {
    CHILL("Chill", YtShelf.LOFI),
    ENERGY("Energy", YtShelf.WORKOUT),
    ANIME_SONGS("Anime songs", YtShelf.JPOP),
    HITS("Top hits", YtShelf.TOP_HITS),
    TRENDING("Trending", YtShelf.TRENDING_MUSIC),
    ANIME_CLIPS("Anime clips", YtShelf.ANIME),
    SHOWS("Movies & shows", YtShelf.TRENDING_MOVIES),
}

/**
 * Home — "everything at a glance", in a fixed order so people always know where to look:
 *  search → setup progress → continue → next episodes → Explore (every feature, with status)
 *  → moods → new arrivals → most played / watched recently → random device videos → online, by
 *  extension (one banner per extension). Nothing repeats: a device video appears at most once on
 *  the page, and the random section never shows a video from the rows above it.
 */
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
    onOpenManga: () -> Unit = onNavigateToLibrary,
    onOpenSources: () -> Unit = onOpenHueStore,
    onOpenMix: () -> Unit = onNavigateToMusic,
    onOpenOnlineTitle: (OnlineItem) -> Unit = {},
    /** Number of installed + working Yumix extensions. */
    huesCount: Int = 0,
    isPlaying: Boolean = false,
    durationMs: Long = 0L,
    positionMs: () -> Long = { 0L },
    onTogglePlayPause: () -> Unit = {},
    onOpenNowPlaying: () -> Unit = {},
    /** Last watched video of any source (null → "nothing yet" card). */
    resumeVideo: ResumeVideo? = null,
    /** Plays a YouTube item (music → as a song, otherwise as a video). */
    onPlayYouTube: (YtItem, List<YtItem>) -> Unit = { _, _ -> },
    /** Online rows from installed extensions, one banner per extension. */
    onlinePicks: (@Composable (header: @Composable (String, () -> Unit) -> Unit) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val p = LocalMtPalette.current
    val continueTrack = selectedTrack ?: recentlyPlayed.firstOrNull()

    remember { dev.asfakul.yumeiro.online.download.OnlineDownloads.ensureLoaded(context); true }
    val downloadItems by dev.asfakul.yumeiro.online.download.OnlineDownloads.items.collectAsState()
    val activeDownloads = remember(downloadItems) { dev.asfakul.yumeiro.online.download.DownloadGroups.split(downloadItems).active.size }
    LaunchedEffect(Unit) { OnlineBookmarks.ensureLoaded(context) }
    val bookmarks by OnlineBookmarks.items.collectAsState()
    val hub by dev.asfakul.yumeiro.sources.SourceHub.state.collectAsState()
    val mangaCount = remember(hub.generation) { dev.asfakul.yumeiro.sources.SourceHub.mangaProviders().size }
    val setup = rememberSetupStatus()
    val setupCardHidden by SetupGuide.cardHidden.collectAsState()

    // ---- New episodes: series with new episodes first, then what you are watching --------------
    LaunchedEffect(Unit) { dev.asfakul.yumeiro.notify.Inbox.ensureLoaded(context) }
    val inbox by dev.asfakul.yumeiro.notify.Inbox.items.collectAsState()
    val episodeCards = remember(bookmarks, inbox) { episodeCards(bookmarks, inbox) }
    val freshSeries = episodeCards.count { it.fresh }
    remember { HomeLayout.load(context); true }
    val folded by HomeLayout.collapsed.collectAsState()
    val fold: (String) -> Unit = { HomeLayout.toggle(context, it) }
    val isOnline by dev.asfakul.yumeiro.network.Connectivity.online.collectAsState()

    // ---- Device rows, de-duplicated top to bottom ---------------------------------------------
    val newArrivals = remember(allTracks, recentVideos) {
        val t = allTracks.sortedByDescending { it.dateAdded }.take(10).map { Arrival(it.dateAdded, track = it) }
        val v = recentVideos.sortedByDescending { it.dateAdded }.take(6).map { Arrival(it.dateAdded, video = it) }
        (t + v).sortedByDescending { it.at }.take(12)
    }
    val watchedRecently = remember(recentVideos, newArrivals) {
        val shown = newArrivals.mapNotNull { it.video?.id }.toSet()
        recentVideos.filter { it.lastWatchedTimestamp > 0 && it.id !in shown }.sortedByDescending { it.lastWatchedTimestamp }.take(8)
    }
    var shuffleSeed by rememberSaveable { mutableStateOf(System.nanoTime()) }
    var randomShown by rememberSaveable(shuffleSeed) { mutableIntStateOf(6) }
    val randomPool = remember(recentVideos, newArrivals, watchedRecently, shuffleSeed) {
        val shown = (newArrivals.mapNotNull { it.video?.id } + watchedRecently.map { it.id }).toSet()
        recentVideos.filter { it.id !in shown }.distinctBy { it.id }.shuffled(java.util.Random(shuffleSeed))
    }

    // ---- YouTube "because you watched" (only if you have YouTube history) ---------------------
    val ytHistory by YtHistory.items.collectAsState()
    var ytRelated by remember { mutableStateOf<List<YtItem>>(emptyList()) }
    LaunchedEffect(ytHistory.firstOrNull { !it.music }?.id) {
        YtHistory.load(context)
        val last = YtHistory.items.value.firstOrNull { !it.music } ?: return@LaunchedEffect
        ytRelated = runCatching { YouTube.related(context, last.id, music = false).filterNot { it.live }.distinctBy { it.id } }.getOrDefault(emptyList()).take(12)
    }

    val features = remember(stats.audioCount, stats.videoCount, huesCount, activeDownloads, mangaCount) {
        listOf(
            Feature("quick_card_hues", "Yumix", if (huesCount > 0) "$huesCount sources" else "Add sources", Icons.Default.AutoAwesome, onOpenHues),
            Feature("quick_card_youtube", "YouTube", "No login", Icons.Default.SmartDisplay, onOpenYouTube),
            Feature("quick_card_live", "Live TV", "IPTV · M3U", Icons.Default.LiveTv, onOpenLive),
            Feature("quick_card_ytmusic", "Online music", "YouTube Music", Icons.Default.Album, onOpenYouTubeMusic),
            Feature("quick_card_music", "Device music", "${stats.audioCount} songs", Icons.Default.LibraryMusic, onNavigateToMusic),
            Feature("quick_card_videos", "Device videos", "${stats.videoCount} files", Icons.Default.VideoLibrary, onNavigateToVideos),
            Feature("quick_card_manga", "Manga", if (mangaCount > 0) "$mangaCount sources" else "Add sources", Icons.AutoMirrored.Filled.MenuBook, onOpenManga),
            Feature("quick_card_mix", "Music mix", "Device + online", Icons.Default.Shuffle, onOpenMix),
            Feature("quick_card_downloads", "Downloads", if (activeDownloads > 0) "$activeDownloads active" else "Offline", Icons.Default.DownloadForOffline, onOpenDownloads,
                badge = activeDownloads.takeIf { it > 0 }?.toString()),
            Feature("quick_card_audio_engine", "Equalizer", "Bass · 3D", Icons.Default.GraphicEq, onNavigateToEqualizer),
            Feature("quick_card_library", "Library", "Playlists", Icons.Default.Bookmarks, onNavigateToLibrary),
            Feature("quick_card_store", "Get sources", "Extension store", Icons.Default.Storefront, onOpenHueStore),
        )
    }
    val byTag = remember(features) { features.associateBy { it.tag.removePrefix("quick_card_") } }
    // Spaces: every shortcut stays, grouped by what you want to do. Listening comes first while
    // music is loaded in the player; otherwise watching does.
    val spaces = remember(stats.audioCount, stats.videoCount, huesCount, mangaCount, activeDownloads, selectedTrack != null) {
        val watch = Space("watch", "Watch", "$huesCount sources · ${stats.videoCount} videos", Icons.Default.SmartDisplay) to listOf("hues", "youtube", "live", "videos")
        val listen = Space("listen", "Listen", if (stats.audioCount == 1) "1 song" else "${stats.audioCount} songs", Icons.Default.LibraryMusic) to listOf("music", "ytmusic", "mix", "audio_engine")
        val keep = Space("keep", "Read & keep", buildString {
            append(if (mangaCount == 1) "1 manga source" else "$mangaCount manga sources")
            if (activeDownloads > 0) append(" · $activeDownloads downloading")
        }, Icons.Default.Bookmarks) to listOf("manga", "library", "downloads", "store")
        if (selectedTrack != null) listOf(listen, watch, keep) else listOf(watch, listen, keep)
    }
    val contextLine = when {
        freshSeries == 1 -> "${episodeCards.first { it.fresh }.bookmark.name} has a new episode waiting."
        freshSeries > 1 -> "$freshSeries series you follow have new episodes."
        continueTrack != null && selectedTrack != null -> "Still playing \u201c${continueTrack.title}\u201d."
        continueTrack != null -> "Pick up \u201c${continueTrack.title}\u201d where you left off."
        resumeVideo != null -> "Continue \u201c${resumeVideo.title}\u201d."
        !isOnline -> "You're offline. Music and videos on this phone still play."
        else -> "Play something from this phone, or explore online."
    }

    BoxWithConstraints(modifier.fillMaxSize().testTag("home_screen_root")) {
        val columns = if (maxWidth >= 600.dp) 6 else 3
        val videoColumns = when {
            maxWidth >= 1000.dp -> 4
            maxWidth >= 560.dp -> 3
            else -> 2
        }
        val listState = dev.asfakul.yumeiro.ui.components.ScrollMemory.list("home")
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag("home_list"),
            contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 4.dp, bottom = 170.dp),
        ) {
            // ---- Context: what is waiting, in one quiet line (real data only) -------------------
            item(key = "context", contentType = "context") { ContextLine(contextLine) }
            // ---- Search ----------------------------------------------------------------------
            item(key = "search", contentType = "search") {
                Row(
                    Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp))
                        .background(p.surfaceContainerHigh.copy(alpha = if (p.isDark) 0.8f else 1f))
                        .clickable(role = Role.Button, onClickLabel = "Search", onClick = onOpenSearch)
                        .testTag("home_search").padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Search, null, tint = p.accent, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Search anime, movies, shows…", color = p.textTertiary, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                }
            }

            // ---- Setup progress (until everything is ready or the card is dismissed) -----------
            if (!setup.complete && !setupCardHidden) item(key = "setup", contentType = "setup") {
                SetupCard(setup.count, setup.total, onOpen = { SetupGuide.show() }, onHide = { SetupGuide.hideCard(context) })
            }

            // ---- Continue ------------------------------------------------------------------
            item(key = "continue_header", contentType = "header") { HomeHeader("Continue") }
            item(key = "continue_row", contentType = "continue") {
                val isCurrent = continueTrack != null && selectedTrack?.id == continueTrack.id
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                        .background(p.surfaceContainerHigh.copy(alpha = if (p.isDark) 0.7f else 1f))
                ) {
                    ResumeMusicCard(
                        track = continueTrack, isCurrent = isCurrent, isPlaying = isCurrent && isPlaying,
                        durationMs = if (isCurrent) durationMs else 0L, positionMs = positionMs,
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
                    Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(p.textPrimary.copy(alpha = 0.07f)))
                    ResumeVideoCard(resumeVideo, onEmptyClick = onNavigateToVideos, modifier = Modifier.fillMaxWidth())
                }
            }

            // ---- New episodes: a hero carousel ------------------------------------------------
            item(key = "episodes_header", contentType = "header") {
                FoldHeader(
                    "episodes", "New episodes",
                    when {
                        freshSeries > 0 -> "From series you follow"
                        episodeCards.isNotEmpty() -> "Series you are watching"
                        else -> "Reminders from My List"
                    },
                    collapsed = "episodes" in folded, onToggle = { fold("episodes") },
                    onMore = if (episodeCards.isNotEmpty()) onOpenHues else null,
                )
            }
            if ("episodes" !in folded) item(key = "episodes", contentType = "episodes") {
                Box(Modifier.animateItem()) {
                    if (episodeCards.isEmpty()) EpisodesEmpty(hasSources = huesCount > 0, onFind = onOpenHues, onAddSources = onOpenHueStore)
                    else EpisodeCarousel(episodeCards) { onOpenOnlineTitle(it.bookmark.toItem()) }
                }
            }

            // ---- Spaces: every feature, grouped, each group folds -----------------------------
            item(key = "explore_header", contentType = "header") {
                FoldHeader("spaces", "Your spaces", "Everything Yumeiro does, grouped", collapsed = "spaces" in folded, onToggle = { fold("spaces") })
            }
            if ("spaces" !in folded) spaces.forEach { (space, tags) ->
                item(key = "space_${space.key}", contentType = "space") {
                    SpaceGroup(
                        space = space,
                        collapsed = "space_${space.key}" in folded,
                        onToggle = { fold("space_${space.key}") },
                        columns = 4,
                        tiles = tags.mapNotNull { byTag[it] }.map { f -> { m: Modifier -> FeatureTile(f, m) } },
                    )
                }
            }
            item(key = "sources_banner", contentType = "banner") {
                SourcesBanner(huesCount, mangaCount, onOpenSources)
            }

            // ---- Moods ---------------------------------------------------------------------
            item(key = "moods", contentType = "moods") { MoodSection(onPlayYouTube, collapsed = "moods" in folded, onToggle = { fold("moods") }, online = isOnline, onOffline = onNavigateToMusic) }

            // ---- New arrivals ----------------------------------------------------------------
            if (newArrivals.isNotEmpty()) {
                item(key = "new_header", contentType = "header") { FoldHeader("new", "New arrivals", "Newest songs and videos on this phone", "new" in folded, { fold("new") }) }
                if ("new" !in folded) item(key = "new_row", contentType = "row") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().testTag("home_new_arrivals")) {
                        items(newArrivals, key = { a -> a.track?.let { "new_t_${it.id}" } ?: "new_v_${a.video?.id}" }) { a ->
                            a.track?.let { t -> AlbumTile(t, selectedTrack?.id == t.id) { onTrackClick(t, newArrivals.mapNotNull { it.track }) } }
                            a.video?.let { v -> SmallVideoCard(v) { onVideoClick(v, newArrivals.mapNotNull { it.video }) } }
                        }
                    }
                }
            }

            // ---- Most played / watched recently ---------------------------------------------
            if (mostPlayed.isNotEmpty()) {
                item(key = "most_header", contentType = "header") { FoldHeader("most", "Most played", "Your top songs", "most" in folded, { fold("most") }, onMore = onNavigateToMusic) }
                if ("most" !in folded) item(key = "most_row", contentType = "row") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().testTag("home_most_played")) {
                        items(mostPlayed.take(12), key = { "most_" + it.id }) { t -> AlbumTile(t, selectedTrack?.id == t.id, plays = t.playCount) { onTrackClick(t, mostPlayed) } }
                    }
                }
            }
            if (watchedRecently.isNotEmpty()) {
                item(key = "watched_header", contentType = "header") { FoldHeader("watched", "Watched recently", "Videos on this phone", "watched" in folded, { fold("watched") }, onMore = onNavigateToVideos) }
                if ("watched" !in folded) item(key = "watched_row", contentType = "row") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        items(watchedRecently, key = { "w_" + it.id }) { v -> SmallVideoCard(v) { onVideoClick(v, watchedRecently) } }
                    }
                }
            }

            // ---- Random device videos (never a repeat) ----------------------------------------
            if (randomPool.isNotEmpty()) {
                item(key = "random_header", contentType = "header") {
                    Row(Modifier.fillMaxWidth().testTag("home_feed"), verticalAlignment = Alignment.Bottom) {
                        FoldHeader("random", "Random videos", "From this phone · no repeats", "random" in folded, { fold("random") }, modifier = Modifier.weight(1f))
                        if ("random" !in folded) Spacer(Modifier.width(8.dp))
                        if ("random" !in folded) Box(Modifier.padding(bottom = 12.dp)) {
                        Row(
                            Modifier.clip(RoundedCornerShape(50)).background(p.accent.copy(alpha = 0.14f))
                                .clickable(role = Role.Button, onClickLabel = "Shuffle") { shuffleSeed = System.nanoTime() }
                                .testTag("home_feed_shuffle").padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Shuffle, "Shuffle videos", tint = p.accent, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Shuffle", color = p.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        }
                    }
                }
                val shown = if ("random" in folded) emptyList() else randomPool.take(randomShown)
                shown.chunked(videoColumns).forEachIndexed { i, rowVideos ->
                    item(key = "rv_${shuffleSeed}_$i", contentType = "video_row") {
                        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("home_video_grid_row"), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            rowVideos.forEach { v ->
                                VideoGridCard(
                                    video = v, onVideoClick = { onVideoClick(v, shown) },
                                    onToggleFavorite = { onToggleVideoFavorite(v.id) }, onOptionsClick = { onVideoOptionsClick(v) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(videoColumns - rowVideos.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                if ("random" !in folded) item(key = "random_more", contentType = "more") {
                    if (randomShown < randomPool.size) {
                        Box(
                            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp))
                                .border(1.dp, p.textPrimary.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                                .clickable { randomShown += 6 }.testTag("home_random_more"),
                            contentAlignment = Alignment.Center,
                        ) { Text("Show ${minOf(6, randomPool.size - randomShown)} more", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                    } else {
                        Text(
                            "That's every video on this phone. Shuffle for a new order.",
                            style = MaterialTheme.typography.bodySmall, color = p.textTertiary,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        )
                    }
                }
            }

            // ---- Online, by extension -------------------------------------------------------
            if (onlinePicks != null) item(key = "online", contentType = "online") {
                Column(Modifier.padding(top = SectionTop - 10.dp)) {
                    onlinePicks { title, seeAll -> HomeHeader(title, onMore = seeAll) }
                }
            }
            if (ytRelated.isNotEmpty()) {
                item(key = "yt_header", contentType = "header") { FoldHeader("yt", "On YouTube for you", "Because of what you watched", "yt" in folded, { fold("yt") }, onMore = onOpenYouTube) }
                if ("yt" !in folded) item(key = "yt_row", contentType = "row") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        items(ytRelated, key = { "yt_" + it.id }) { yt -> YtCard(yt, square = false) { onPlayYouTube(yt, ytRelated) } }
                    }
                }
            }

            if (allTracks.isEmpty() && recentVideos.isEmpty()) item(key = "empty_device") {
                Box(Modifier.padding(top = SectionTop)) {
                    EmptyMediaView(
                        title = "Nothing on this device yet",
                        subtitle = "Put music or videos in local storage and tap Scan — or use Yumix, YouTube or Online music above.",
                        buttonText = "Scan storage",
                        onButtonClick = onRescanClick
                    )
                }
            }
            item(key = "end") {
                Text(
                    "You're all caught up.", style = MaterialTheme.typography.bodySmall, color = p.textTertiary,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp).testTag("home_end"),
                )
            }
        }
    }
}

/** One item of the New arrivals row (a song or a video). */
@Immutable
private data class Arrival(val at: Long, val track: AudioTrack? = null, val video: VideoItem? = null)

// ---- Headers ------------------------------------------------------------------------------

@Composable
private fun HomeHeader(title: String, subtitle: String? = null, onMore: (() -> Unit)? = null) {
    val p = LocalMtPalette.current
    Row(Modifier.fillMaxWidth().padding(top = SectionTop, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = p.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = p.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onMore != null) Text(
            "See all", color = p.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp,
            modifier = Modifier.clip(RoundedCornerShape(50)).clickable(role = Role.Button, onClick = onMore).padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

/** Kept public for other callers: a plain section header with an optional "See all". */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, onSeeAll: (() -> Unit)? = null) {
    Box(modifier) { HomeHeader(title, onMore = onSeeAll) }
}

// ---- Setup card ---------------------------------------------------------------------------

@Composable
private fun SetupCard(done: Int, total: Int, onOpen: () -> Unit, onHide: () -> Unit) {
    val p = LocalMtPalette.current
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.fillMaxWidth().padding(top = 14.dp).clip(shape)
            .background(p.accent.copy(alpha = if (p.isDark) 0.14f else 0.10f))
            .border(1.dp, p.accent.copy(alpha = 0.35f), shape)
            .clickable(role = Role.Button, onClick = onOpen)
            .testTag("home_setup_card")
            .padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Finish setting up", style = MaterialTheme.typography.titleMedium, color = p.textPrimary, fontWeight = FontWeight.ExtraBold)
                Text("$done of $total ready · sources, manga, DNS, alerts", style = MaterialTheme.typography.bodySmall, color = p.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Hide setup card", onClick = onHide).testTag("home_setup_hide"),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Close, "Hide", tint = p.textTertiary, modifier = Modifier.size(18.dp)) }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.padding(end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            val accent = p.accent
            val track = p.textPrimary.copy(alpha = 0.10f)
            Box(Modifier.weight(1f).height(6.dp).drawBehind {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(track, cornerRadius = r)
                drawRoundRect(accent, size = Size(size.width * (done.toFloat() / total).coerceIn(0f, 1f), size.height), cornerRadius = r)
            })
            Spacer(Modifier.width(12.dp))
            Text("Continue", color = p.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

// ---- Continue cards -----------------------------------------------------------------------

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
    val p = LocalMtPalette.current
    val track20 = p.textPrimary.copy(alpha = 0.10f)
    Row(
        modifier.clickable(onClick = onClick).testTag(tag).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        art()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(eyebrow, style = MaterialTheme.typography.labelMedium, color = p.textTertiary, maxLines = 1)
            Text(title, style = MaterialTheme.typography.titleMedium, color = p.textPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = p.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            val accent = p.accent
            Box(
                Modifier.fillMaxWidth().height(3.dp).drawBehind {
                    val r = CornerRadius(size.height / 2)
                    drawRoundRect(track20, cornerRadius = r)
                    val v = progress().coerceIn(0f, 1f)
                    if (v > 0f) drawRoundRect(accent, size = Size(size.width * v, size.height), cornerRadius = r)
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
    val p = LocalMtPalette.current
    ResumeRow(
        modifier = modifier,
        tag = "home_continue_card",
        onClick = onClick,
        eyebrow = if (isPlaying) "Playing now" else "Last played",
        title = track?.title ?: "Nothing played yet",
        subtitle = track?.artist ?: "Start a song and it waits for you here",
        progress = { if (isCurrent && durationMs > 0) positionMs().toFloat() / durationMs else 0f },
        art = {
            Box(Modifier.size(60.dp)) {
                if (track != null) TrackArtwork(artUri = track.albumArtUri, key = track.id, size = 60.dp, cornerRadius = 12.dp, contentDescription = track.album)
                else EmptyArt(Icons.Default.MusicNote)
            }
        },
        action = {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(p.accent)
                    .clickable(role = Role.Button, onClick = onPlayPause).testTag("home_continue_play"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else if (track == null) "Open Music" else "Play",
                    tint = p.onAccent, modifier = Modifier.size(24.dp)
                )
            }
        },
    )
}

@Composable
private fun ResumeVideoCard(video: ResumeVideo?, onEmptyClick: () -> Unit, modifier: Modifier) {
    val p = LocalMtPalette.current
    ResumeRow(
        modifier = modifier,
        tag = "home_continue_video",
        onClick = video?.onClick ?: onEmptyClick,
        eyebrow = "Last watched",
        title = video?.title ?: "Nothing watched yet",
        subtitle = video?.subtitle ?: "Videos you start will resume from here",
        progress = { video?.progress ?: 0f },
        art = {
            Box(Modifier.width(60.dp).height(60.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.35f))) {
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
                Modifier.size(48.dp).clip(CircleShape).border(1.5.dp, p.accent.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(if (video == null) Icons.Default.VideoLibrary else Icons.Default.PlayArrow, contentDescription = null, tint = p.accent, modifier = Modifier.size(22.dp))
            }
        },
    )
}

@Composable
private fun EmptyArt(icon: ImageVector) {
    val p = LocalMtPalette.current
    Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(p.textPrimary.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = null, tint = p.textTertiary, modifier = Modifier.size(24.dp)) }
}

// ---- Next episodes --------------------------------------------------------------------------

@Composable
private fun EpisodePoster(name: String, poster: String?, headers: Map<String, String>, badge: String, fresh: Boolean, source: String, onClick: () -> Unit) {
    val p = LocalMtPalette.current
    Column(Modifier.width(112.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).testTag("home_episode_$name")) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp)).background(p.textPrimary.copy(alpha = 0.06f))) {
            dev.asfakul.yumeiro.ui.screens.online.PosterImage(poster, headers, Modifier.fillMaxSize())
            Text(
                badge, color = if (fresh) p.onAccent else Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(50))
                    .background(if (fresh) p.accent else Color.Black.copy(alpha = 0.6f)).padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(name, style = MaterialTheme.typography.titleSmall, color = p.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(source, style = MaterialTheme.typography.bodySmall, color = p.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HintRow(icon: ImageVector, title: String, text: String, action: String, onClick: () -> Unit, tag: String) {
    val p = LocalMtPalette.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.surfaceContainerHigh.copy(alpha = if (p.isDark) 0.6f else 1f))
            .clickable(role = Role.Button, onClick = onClick).testTag(tag).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(p.accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = p.accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = p.textPrimary, fontWeight = FontWeight.Bold)
            Text(text, style = MaterialTheme.typography.bodySmall, color = p.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(action, color = p.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
    }
}

// ---- Explore ------------------------------------------------------------------------------

@Composable
private fun FeatureTile(f: Feature, modifier: Modifier) {
    val p = LocalMtPalette.current
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .heightIn(min = 92.dp)
            .clip(shape)
            .background(p.background.copy(alpha = if (p.isDark) 0.55f else 0.9f))
            .clickable(role = Role.Button, onClickLabel = "Open ${f.title}", onClick = f.onClick)
            .semantics(mergeDescendants = true) { contentDescription = "${f.title}, ${f.status}" }
            .testTag(f.tag)
            .padding(horizontal = 9.dp, vertical = 10.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(p.accent.copy(alpha = if (p.isDark) 0.16f else 0.12f)), contentAlignment = Alignment.Center) {
                Icon(f.icon, null, tint = p.accent, modifier = Modifier.size(20.dp))
            }
            f.badge?.let {
                Text(
                    it, color = p.onAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(50)).background(p.accent).padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(f.title, color = p.textPrimary, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(f.status, color = p.textTertiary, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SourcesBanner(videoSources: Int, mangaSources: Int, onClick: () -> Unit) {
    val p = LocalMtPalette.current
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(shape)
            .background(p.surfaceContainerHigh.copy(alpha = if (p.isDark) 0.7f else 1f))
            .border(1.dp, p.textPrimary.copy(alpha = 0.06f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("home_sources_banner")
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(p.accent), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Extension, null, tint = p.onAccent, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Sources & extensions", style = MaterialTheme.typography.titleMedium, color = p.textPrimary, fontWeight = FontWeight.ExtraBold)
            Text(
                "$videoSources video · $mangaSources manga · Aniyomi, Mihon, Stremio, home servers",
                style = MaterialTheme.typography.bodySmall, color = p.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text("Manage", color = p.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

// ---- Moods --------------------------------------------------------------------------------

@Composable
private fun MoodSection(onPlay: (YtItem, List<YtItem>) -> Unit, collapsed: Boolean = false, onToggle: () -> Unit = {}, online: Boolean = true, onOffline: () -> Unit = {}) {
    val context = LocalContext.current
    val p = LocalMtPalette.current
    var mood by rememberSaveable { mutableStateOf(Mood.CHILL) }
    var tick by remember { mutableIntStateOf(0) }
    var items by remember { mutableStateOf<List<YtItem>?>(null) }
    var failed by remember { mutableStateOf(false) }
    // Unit tests have no network: don't start a real request there (the chips still render).
    val autoLoad = remember { android.os.Build.FINGERPRINT != "robolectric" }
    LaunchedEffect(mood, tick) {
        if (!autoLoad && tick == 0) { items = emptyList(); return@LaunchedEffect }
        items = null; failed = false
        val r = kotlinx.coroutines.withTimeoutOrNull(20_000) { runCatching { YouTube.shelf(context, mood.shelf) }.getOrNull() }
        if (r == null) { failed = true; items = emptyList() } else items = r.distinctBy { it.id }.map { it.copy(music = mood.shelf.music) }.take(16)
    }
    Column(Modifier.fillMaxWidth().testTag("home_moods")) {
        FoldHeader("moods", "Moods", "Pick a feeling — songs and clips to match", collapsed, onToggle)
        if (collapsed) return@Column
        if (!online) {
            HintRow(
                Icons.Default.CloudOff, "Moods need the internet",
                "They come from YouTube. Your own songs still play offline.",
                "Device music", onOffline, tag = "home_moods_offline",
            )
            return@Column
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            items(Mood.entries.toList(), key = { it.name }) { m ->
                val sel = m == mood
                Text(
                    m.label, color = if (sel) p.onAccent else p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50))
                        .background(if (sel) p.accent else p.surfaceContainerHigh.copy(alpha = if (p.isDark) 0.8f else 1f))
                        .clickable(role = Role.Tab) { if (sel) tick++ else { mood = m; tick++ } }
                        .testTag("home_mood_${m.name.lowercase()}")
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        val list = items
        when {
            list == null -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(3) { SkeletonBlock(132.dp, 132.dp, shape = RoundedCornerShape(16.dp)) }
            }
            list.isEmpty() -> HintRow(
                Icons.Default.Album,
                if (failed) "Couldn't load ${mood.label}" else "Tap a mood to load it",
                if (failed) "Check your connection, then try again." else "Moods come from YouTube and YouTube Music.",
                if (failed) "Try again" else "Load", onClick = { tick++ }, tag = "home_moods_retry",
            )
            else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                items(list, key = { "m_" + it.id }) { yt -> YtCard(yt, square = mood.shelf.music) { onPlay(yt, list) } }
            }
        }
    }
}

// ---- Cards ----------------------------------------------------------------------------------

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "—"
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun AlbumTile(track: AudioTrack, isCurrent: Boolean, plays: Int = 0, onClick: () -> Unit) {
    val p = LocalMtPalette.current
    Column(Modifier.width(128.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).testTag("home_added_${track.id}")) {
        TrackArtwork(artUri = track.albumArtUri, key = track.id, size = 128.dp, cornerRadius = 14.dp, contentDescription = track.album)
        Spacer(Modifier.height(6.dp))
        Text(track.title, style = MaterialTheme.typography.titleSmall, color = if (isCurrent) p.accent else p.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            if (plays > 0) "${track.artist} · $plays plays" else track.artist,
            style = MaterialTheme.typography.bodySmall, color = p.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SmallVideoCard(video: VideoItem, onClick: () -> Unit) {
    val p = LocalMtPalette.current
    Column(Modifier.width(200.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).testTag("home_small_video_${video.id}")) {
        Box(Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.35f))) {
            AsyncImage(model = dev.asfakul.yumeiro.ui.image.VideoThumb(video.uri), contentDescription = video.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (video.durationMs > 0) Text(
                formatMs(video.durationMs), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = 5.dp, vertical = 1.dp),
            )
            if (video.durationMs > 0 && video.playbackPositionMs > 0) {
                val accent = p.accent
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).drawBehind {
                    drawRect(accent, size = Size(size.width * (video.playbackPositionMs.toFloat() / video.durationMs).coerceIn(0f, 1f), size.height))
                })
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(video.title, style = MaterialTheme.typography.titleSmall, color = p.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(video.folderName, style = MaterialTheme.typography.bodySmall, color = p.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun YtCard(item: YtItem, square: Boolean, onClick: () -> Unit) {
    val p = LocalMtPalette.current
    val width = if (square) 132.dp else 220.dp
    Column(Modifier.width(width).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).testTag("home_feed_yt_${item.id}")) {
        Box(Modifier.fillMaxWidth().aspectRatio(if (square) 1f else 16f / 9f).clip(RoundedCornerShape(14.dp)).background(p.textPrimary.copy(alpha = 0.06f))) {
            AsyncImage(model = item.thumbnail, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (!square && item.durationSec > 0) Text(
                formatMs(item.durationSec * 1000), color = Color.White, fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(item.title, style = MaterialTheme.typography.titleSmall, color = p.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(item.channel, style = MaterialTheme.typography.bodySmall, color = p.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Kept for callers/tests that referenced the old Home helpers. */
@Composable
fun rememberGreeting(): String = dev.asfakul.yumeiro.ui.components.greetingForNow()
