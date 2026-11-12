/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.home

import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.asfakul.yumeiro.data.PlayCounts
import dev.asfakul.yumeiro.data.model.AudioTrack
import dev.asfakul.yumeiro.data.model.StorageStats
import dev.asfakul.yumeiro.data.model.VideoItem
import dev.asfakul.yumeiro.online.OnlineBookmarks
import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.online.OnlineRow
import dev.asfakul.yumeiro.ui.components.EmptyMediaView
import dev.asfakul.yumeiro.ui.components.GlassPill
import dev.asfakul.yumeiro.ui.components.KanjiMark
import dev.asfakul.yumeiro.ui.components.SkeletonBlock
import dev.asfakul.yumeiro.ui.components.TrackArtwork
import dev.asfakul.yumeiro.ui.components.VideoGridCard
import dev.asfakul.yumeiro.ui.components.YuSection
import dev.asfakul.yumeiro.ui.screens.extras.SetupGuide
import dev.asfakul.yumeiro.ui.screens.extras.tourTarget
import dev.asfakul.yumeiro.ui.screens.extras.rememberSetupStatus
import dev.asfakul.yumeiro.ui.screens.online.PosterCard
import dev.asfakul.yumeiro.ui.screens.online.PosterImage
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import dev.asfakul.yumeiro.ui.theme.SectionTitleStyle
import dev.asfakul.yumeiro.youtube.YouTube
import dev.asfakul.yumeiro.youtube.YtHistory
import dev.asfakul.yumeiro.youtube.YtItem
import kotlinx.coroutines.launch

// ---- Rhythm (8dp grid) ---------------------------------------------------------------------
private val Gutter = 16.dp
private val RowGap = 12.dp

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

/** One tile of the Spaces grid. */
@Immutable
private data class Feature(
    val tag: String,
    val title: String,
    val status: String,
    val icon: ImageVector,
    val kanji: String,
    /** The space's own colour (a traditional Japanese hue), used for its seal. */
    val hue: Color,
    val onClick: () -> Unit,
    val badge: String? = null,
)

/** One card of "Most played": a song (play count from the media database) or a video. */
@Immutable
private sealed interface Played {
    val count: Int
    val key: String
    data class Song(val track: AudioTrack) : Played { override val count get() = track.playCount; override val key get() = "s_${track.id}" }
    data class Video(val entry: PlayCounts.Entry, val device: VideoItem? = null) : Played { override val count get() = entry.count; override val key get() = "v_${entry.key}" }
}

/** Traditional Japanese colours, one per space: each seal is recognisable at a glance. */
private object SpaceHues {
    val Kobai = Color(0xFFD9668A)      // 紅梅 plum blossom
    val Shu = Color(0xFFD9553F)        // 朱 vermilion
    val Fuji = Color(0xFF8F6CC9)       // 藤 wisteria
    val Yamabuki = Color(0xFFE0A030)   // 山吹 kerria gold
    val Wakatake = Color(0xFF4FA87A)   // 若竹 young bamboo
    val Sora = Color(0xFF4C8FD6)       // 空 sky
    val Ai = Color(0xFF5866C2)         // 藍 indigo
    val Seiji = Color(0xFF3E9E94)      // 青磁 celadon
}

/**
 * Home — one calm editorial page, top to bottom, nothing folds:
 *  context line → setup (until done) → つづき Continue (two cards) → 新着 New episodes (auto
 *  carousel from My List reminders, else the extensions' latest) → 空間 Spaces (8 tiles + Show all)
 *  → 気分 Mood of the hour (picked by time, filled from YouTube Music / YouTube / extensions) →
 *  新作 New arrivals (from extensions) → 人気 Most played (songs and videos) → YouTube for you →
 *  乱 Yumix random (endless, grouped by extension). "Customize home" switches sections off/on.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    /**
     * Round 8: the extra Spaces shortcuts behind "See more" (see [HomeShortcut]). The host maps
     * each one to its page or Settings category.
     */
    onOpenShortcut: (HomeShortcut) -> Unit = {},
    onOpenOnlineTitle: (OnlineItem) -> Unit = {},
    /** Opens one extension row on its own page (paged "See all"). */
    onOpenRow: (OnlineRow) -> Unit = {},
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val p = LocalMtPalette.current
    val scope = rememberCoroutineScope()
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
    val onboarding by SetupGuide.onboarding.collectAsState()
    remember { HomeLayout.load(context); true }
    val hidden by HomeLayout.hidden.collectAsState()
    val isOnline by dev.asfakul.yumeiro.network.Connectivity.online.collectAsState()
    val adultOn by dev.asfakul.yumeiro.online.OnlineSettings.adult.collectAsState()
    val adultPrivate by dev.asfakul.yumeiro.online.OnlineSettings.adultPrivate.collectAsState()

    // ---- New episodes: My List reminders first; checked again when Home opens ----------------
    LaunchedEffect(Unit) { dev.asfakul.yumeiro.notify.Inbox.ensureLoaded(context) }
    val inbox by dev.asfakul.yumeiro.notify.Inbox.items.collectAsState()
    val episodeCards = remember(bookmarks, inbox) { episodeCards(bookmarks, inbox) }
    val freshSeries = episodeCards.count { it.fresh }
    val following = remember(bookmarks) { bookmarks.any { it.notify } }
    LaunchedEffect(following) {
        if (following) runCatching { dev.asfakul.yumeiro.online.NewEpisodesWorker.checkSoon(context) }
    }

    // ---- Online: extension home rows (shared cache with the Yumix tab) -----------------------
    val feed = rememberExtensionFeed()
    val channels = feed.ready
    val arrivals = remember(channels) { newArrivals(channels) }

    // ---- Most played: songs + videos (device, Yumix, YouTube) ---------------------------------
    remember { PlayCounts.ensureLoaded(context); true }
    val videoPlays by PlayCounts.entries.collectAsState()
    val played = remember(mostPlayed, videoPlays, recentVideos, adultOn, adultPrivate) {
        val byId = recentVideos.associateBy { it.id }
        val songs = mostPlayed.take(12).map { Played.Song(it) }
        val videos = videoPlays.asSequence()
            .filterNot { it.kind == PlayCounts.Kind.ONLINE && (!adultOn || adultPrivate) && dev.asfakul.yumeiro.online.AdultContent.isAdultApi(it.api.orEmpty()) }
            .mapNotNull { e -> if (e.kind == PlayCounts.Kind.DEVICE) byId[e.id]?.let { Played.Video(e, it) } else Played.Video(e) }
            .take(12).toList()
        (songs + videos).sortedByDescending { it.count }.take(16)
    }

    // ---- YouTube "because you watched" (only if you have YouTube history) ---------------------
    val ytHistory by YtHistory.items.collectAsState()
    var ytRelated by remember { mutableStateOf<List<YtItem>>(emptyList()) }
    LaunchedEffect(ytHistory.firstOrNull { !it.music }?.id) {
        YtHistory.load(context)
        val last = YtHistory.items.value.firstOrNull { !it.music } ?: return@LaunchedEffect
        ytRelated = runCatching { YouTube.related(context, last.id, music = false).filterNot { it.live }.distinctBy { it.id } }.getOrDefault(emptyList()).take(12)
    }

    // ---- Yumix random (endless, grouped by extension) / device fallback -----------------------
    var shuffleSeed by rememberSaveable { mutableStateOf(System.nanoTime()) }
    val random = remember(shuffleSeed) { YumixRandom(shuffleSeed) }
    LaunchedEffect(random, channels.size) {
        if (channels.isNotEmpty() && random.groups.size < 3) random.more(channels, feed.providers, count = 3)
    }
    var deviceShown by rememberSaveable(shuffleSeed) { mutableIntStateOf(6) }
    val devicePool = remember(recentVideos, shuffleSeed) { recentVideos.distinctBy { it.id }.shuffled(java.util.Random(shuffleSeed)) }

    var customizing by rememberSaveable { mutableStateOf(false) }

    val features = remember(stats.audioCount, stats.videoCount, huesCount, activeDownloads, mangaCount) {
        listOf(
            Feature("quick_card_hues", "Yumix", if (huesCount > 0) "$huesCount sources" else "Add sources", dev.asfakul.yumeiro.ui.components.YumixLogo.Icon, "彩", SpaceHues.Kobai, onOpenHues),
            Feature("quick_card_youtube", "YouTube", "Videos", Icons.Default.SmartDisplay, "映", SpaceHues.Shu, onOpenYouTube),
            Feature("quick_card_ytmusic", "YT Music", "Songs", Icons.Default.Album, "歌", SpaceHues.Fuji, onOpenYouTubeMusic),
            Feature("quick_card_store", "Extensions", if (huesCount > 0) "$huesCount on" else "Get some", Icons.Default.Extension, "拡", SpaceHues.Yamabuki, onOpenHueStore),
            Feature("quick_card_manga", "Manga", if (mangaCount > 0) "$mangaCount sources" else "Add sources", Icons.AutoMirrored.Filled.MenuBook, "読", SpaceHues.Wakatake, onOpenManga),
            Feature("quick_card_live", "Live TV", "IPTV · links", Icons.Default.LiveTv, "生", SpaceHues.Sora, onOpenLive),
            Feature("quick_card_sources", "Sources", "All sources", Icons.Default.Hub, "源", SpaceHues.Ai, onOpenSources),
            Feature("quick_card_downloads", "Downloads", if (activeDownloads > 0) "$activeDownloads active" else "Offline", Icons.Default.DownloadForOffline, "保", SpaceHues.Seiji, onOpenDownloads,
                badge = activeDownloads.takeIf { it > 0 }?.toString()),
        )
    }
    // Round 8: everything else worth one tap (settings people look for most, plus pages that
    // used to be three taps deep), in two labelled groups behind "See more".
    val moreGo = remember {
        listOf(
            Feature("quick_card_search", "Search", "All sources", Icons.Default.Search, "探", SpaceHues.Sora, onClick = { onOpenShortcut(HomeShortcut.SEARCH) }),
            Feature("quick_card_mylist", "My List", "Saved titles", Icons.Default.Bookmarks, "栞", SpaceHues.Kobai, onClick = { onOpenShortcut(HomeShortcut.MY_LIST) }),
            Feature("quick_card_history", "History", "Keep watching", Icons.Default.History, "歴", SpaceHues.Fuji, onClick = { onOpenShortcut(HomeShortcut.HISTORY) }),
            Feature("quick_card_market", "Market", "New extensions", Icons.Default.Storefront, "市", SpaceHues.Yamabuki, onClick = { onOpenShortcut(HomeShortcut.MARKET) }),
            Feature("quick_card_websites", "Websites", "Saved sites", Icons.Default.Language, "網", SpaceHues.Ai, onClick = { onOpenShortcut(HomeShortcut.WEBSITES) }),
            Feature("quick_card_mix", "Music mix", "Songs + YT Music", Icons.Default.Shuffle, "混", SpaceHues.Shu, onClick = { onOpenShortcut(HomeShortcut.MIX) }),
            Feature("quick_card_equalizer", "Equalizer", "Sound presets", Icons.Default.Equalizer, "響", SpaceHues.Seiji, onClick = { onOpenShortcut(HomeShortcut.EQUALIZER) }),
            Feature("quick_card_health", "Source health", "What works now", Icons.Default.MonitorHeart, "診", SpaceHues.Wakatake, onClick = { onOpenShortcut(HomeShortcut.SOURCE_HEALTH) }),
            Feature("quick_card_together", "Together", "Watch in sync", Icons.Default.Groups, "共", SpaceHues.Kobai, onClick = { onOpenShortcut(HomeShortcut.TOGETHER) }),
        )
    }
    val moreSettings = remember {
        listOf(
            Feature("quick_card_theme", "Theme", "Colours · look", Icons.Default.Palette, "色", SpaceHues.Fuji, onClick = { onOpenShortcut(HomeShortcut.THEME) }),
            Feature("quick_card_dns", "DNS", "Smart DNS · unblock", Icons.Default.Dns, "護", SpaceHues.Ai, onClick = { onOpenShortcut(HomeShortcut.DNS) }),
            Feature("quick_card_network", "Network", "VPN · blocked sites", Icons.Default.Wifi, "通", SpaceHues.Sora, onClick = { onOpenShortcut(HomeShortcut.NETWORK) }),
            Feature("quick_card_tracking", "Tracking", "AniList · MAL", Icons.Default.TrackChanges, "記", SpaceHues.Shu, onClick = { onOpenShortcut(HomeShortcut.TRACKING) }),
            Feature("quick_card_player", "Player", "PiP · captions", Icons.Default.PlayCircle, "奏", SpaceHues.Wakatake, onClick = { onOpenShortcut(HomeShortcut.PLAYER) }),
            Feature("quick_card_navbar", "Nav bar", "Tabs · shortcuts", Icons.Default.SpaceDashboard, "道", SpaceHues.Yamabuki, onClick = { onOpenShortcut(HomeShortcut.NAV_BAR) }),
            Feature("quick_card_alerts", "Alerts", "Notifications", Icons.Default.Notifications, "知", SpaceHues.Kobai, onClick = { onOpenShortcut(HomeShortcut.NOTIFICATIONS) }),
            Feature("quick_card_privacy", "Privacy", "Lock · incognito", Icons.Default.Lock, "錠", SpaceHues.Seiji, onClick = { onOpenShortcut(HomeShortcut.PRIVACY) }),
            Feature("quick_card_backup", "Backup", "Save · restore", Icons.Default.Backup, "蔵", SpaceHues.Ai, onClick = { onOpenShortcut(HomeShortcut.BACKUP) }),
            Feature("quick_card_yt_account", "YT account", "Optional sign-in", Icons.Default.AccountCircle, "鍵", SpaceHues.Shu, onClick = { onOpenShortcut(HomeShortcut.YOUTUBE_ACCOUNT) }),
            Feature("quick_card_settings", "Settings", "Every option", Icons.Default.Settings, "設", SpaceHues.Sora, onClick = { onOpenShortcut(HomeShortcut.SETTINGS) }),
        )
    }
    var spacesExpanded by rememberSaveable { mutableStateOf(false) }
    val contextLine = when {
        freshSeries == 1 -> "${episodeCards.first { it.fresh }.bookmark.name} has a new episode waiting."
        freshSeries > 1 -> "$freshSeries series you follow have new episodes."
        !isOnline -> "You're offline. Music and videos on this phone still play."
        else -> null
    }

    BoxWithConstraints(modifier.fillMaxSize().testTag("home_screen_root")) {
        val wide = maxWidth >= 600.dp
        val spaceColumns = if (wide) 6 else 4
        val randomColumns = if (wide) 5 else 3
        val listState = dev.asfakul.yumeiro.ui.components.ScrollMemory.list("home")
        // Endless Yumix random: grow it as the end of the page comes into view.
        val nearEnd by remember { derivedStateOf { val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0; last >= listState.layoutInfo.totalItemsCount - 4 } }
        LaunchedEffect(nearEnd, random.groups.size) {
            if (nearEnd && "random" !in hidden && channels.isNotEmpty() && !random.finished) random.more(channels, feed.providers)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag("home_list"),
            contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 2.dp, bottom = 170.dp),
        ) {
            contextLine?.let { line -> item(key = "context", contentType = "context") { ContextLine(line) } }

            // ---- Setup progress (until everything is ready or the card is dismissed) -----------
            if (!setup.complete && !setupCardHidden) item(key = "setup", contentType = "setup") {
                SetupCard(
                    setup.count, setup.total, onOpen = { SetupGuide.show(checklist = true) }, onHide = { SetupGuide.hideCard(context) },
                    onExplore = if (onboarding != SetupGuide.Onboarding.EXPLORED) ({ dev.asfakul.yumeiro.ui.screens.extras.ExploreTour.start() }) else null,
                )
            }

            // ---- 空間 Spaces: every feature in one grid ---------------------------------------------
            if ("spaces" !in hidden) {
                item(key = "spaces_header", contentType = "header") {
                    YuSection("空間", "Spaces", "Everything in Yumeiro")
                }
                features.chunked(spaceColumns).forEachIndexed { i, row ->
                    item(key = "spaces_$i", contentType = "spaces_row") {
                        Row(Modifier.fillMaxWidth().then(if (i == 0) Modifier.tourTarget("home_spaces_grid") else Modifier).padding(bottom = 14.dp).testTag("home_spaces"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { f -> FeatureTile(f, Modifier.weight(1f)) }
                            repeat(spaceColumns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                // Round 9: the extra groups live in ONE item that grows with expandVertically, so
                // the rows below glide down/up with it instead of items popping in one by one.
                item(key = "spaces_more_block", contentType = "spaces_more_block") {
                    val reducedMore = dev.asfakul.yumeiro.ui.components.reducedMotion()
                    androidx.compose.animation.AnimatedVisibility(
                        visible = spacesExpanded,
                        enter = if (reducedMore) androidx.compose.animation.fadeIn(androidx.compose.animation.core.snap()) else
                            androidx.compose.animation.expandVertically(androidx.compose.animation.core.spring(dampingRatio = 0.9f, stiffness = 260f), expandFrom = Alignment.Top) +
                                androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(260, delayMillis = 60)),
                        exit = if (reducedMore) androidx.compose.animation.fadeOut(androidx.compose.animation.core.snap()) else
                            androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = 320f), shrinkTowards = Alignment.Top) +
                                androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(140)),
                        label = "spacesMore",
                    ) {
                        Column(Modifier.fillMaxWidth().testTag("home_spaces_more_block")) {
                            listOf("go" to ("移" to "Go to"), "settings" to ("設" to "Quick settings")).forEach { (gk, label) ->
                                val list = if (gk == "go") moreGo else moreSettings
                                SpacesGroupLabel(label.first, label.second)
                                list.chunked(spaceColumns).forEach { row ->
                                    Row(Modifier.fillMaxWidth().padding(bottom = 14.dp).testTag("home_spaces_more_row"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        row.forEach { f -> FeatureTile(f, Modifier.weight(1f)) }
                                        repeat(spaceColumns - row.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                            }
                        }
                    }
                }
                item(key = "spaces_more", contentType = "spaces_more") {
                    SpacesMoreButton(
                        expanded = spacesExpanded,
                        count = moreGo.size + moreSettings.size,
                        preview = moreSettings.take(4),
                        onClick = { spacesExpanded = !spacesExpanded },
                    )
                }
            }

            // ---- つづき Continue -----------------------------------------------------------------
            item(key = "continue_header", contentType = "header") {
                YuSection("つづき", "Continue", "Jump back in")
            }
            item(key = "continue_row", contentType = "continue") {
                val isCurrent = continueTrack != null && selectedTrack?.id == continueTrack.id
                val playMusic = {
                    when {
                        continueTrack == null -> onNavigateToMusic()
                        isCurrent -> onTogglePlayPause()
                        else -> onTrackClick(continueTrack, recentlyPlayed.ifEmpty { allTracks })
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RowGap)) {
                    ContinueCard(
                        modifier = Modifier.weight(1f), tag = "home_continue_card",
                        eyebrow = if (isCurrent && isPlaying) "Music · playing" else "Music · last played",
                        title = continueTrack?.title ?: "Nothing yet",
                        subtitle = continueTrack?.artist ?: "Play a song to see it here",
                        progress = { if (isCurrent && durationMs > 0) positionMs().toFloat() / durationMs else 0f },
                        onClick = {
                            when {
                                continueTrack == null -> onNavigateToMusic()
                                isCurrent -> onOpenNowPlaying()
                                else -> onTrackClick(continueTrack, recentlyPlayed.ifEmpty { allTracks })
                            }
                        },
                        kanji = "音",
                        playIcon = if (isCurrent && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        playLabel = if (isCurrent && isPlaying) "Pause" else if (continueTrack == null) "Open music" else "Play",
                        playTag = "home_continue_play", onPlay = playMusic,
                    ) {
                        if (continueTrack != null) TrackArtwork(artUri = continueTrack.albumArtUri, key = continueTrack.id, size = 400.dp, cornerRadius = 0.dp, contentDescription = continueTrack.album, modifier = Modifier.fillMaxSize())
                        else EmptyArt(Icons.Default.MusicNote)
                    }
                    ContinueCard(
                        modifier = Modifier.weight(1f), tag = "home_continue_video",
                        eyebrow = "Video · last watched",
                        title = resumeVideo?.title ?: "Nothing yet",
                        subtitle = resumeVideo?.subtitle ?: "Watch a video to see it here",
                        progress = { resumeVideo?.progress ?: 0f },
                        onClick = resumeVideo?.onClick ?: onNavigateToVideos,
                        kanji = "映",
                        playIcon = if (resumeVideo == null) Icons.Default.VideoLibrary else Icons.Default.PlayArrow,
                        playLabel = if (resumeVideo == null) "Open videos" else "Resume",
                        playTag = "home_continue_video_play", onPlay = resumeVideo?.onClick ?: onNavigateToVideos,
                    ) {
                        val v = resumeVideo
                        when {
                            v?.thumbnail == null -> EmptyArt(Icons.Default.Videocam)
                            v.thumbnailHeaders.isNotEmpty() && v.thumbnail is String -> PosterImage(v.thumbnail, v.thumbnailHeaders, Modifier.fillMaxSize())
                            else -> { EmptyArt(Icons.Default.Videocam); AsyncImage(model = v.thumbnail, contentDescription = v.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                        }
                    }
                }
            }

            // ---- 新着 New episodes -----------------------------------------------------------------
            if ("episodes" !in hidden) {
                val latest = episodeCards.isEmpty() && arrivals.isNotEmpty()
                item(key = "episodes_header", contentType = "header") {
                    YuSection(
                        "新着", if (latest) "Latest from your extensions" else "New episodes",
                        when {
                            freshSeries > 0 -> "New episodes are out"
                            episodeCards.isNotEmpty() -> "Up next on your list"
                            latest -> "Just released"
                            else -> "New episodes"
                        },
                        action = if (episodeCards.isNotEmpty() || latest) "See all" else null, onAction = onOpenHues,
                    )
                }
                item(key = "episodes", contentType = "episodes") {
                    when {
                        episodeCards.isNotEmpty() -> EpisodeCarousel(episodeCards) { onOpenOnlineTitle(it.bookmark.toItem()) }
                        latest -> Column {
                            val picks = arrivals.take(24)
                            val slides = remember(picks) {
                                picks.map { HeroSlide(it.key, it.name, it.posterUrl, it.posterHeaders, "Latest", fresh = false, line = it.type, meta = it.api, cta = "Open") }
                            }
                            HeroCarousel(slides, tag = "home_next_episodes") { i -> onOpenOnlineTitle(picks[i]) }
                            Text(
                                "Follow a series with reminders on and its new episodes show here first.",
                                style = MaterialTheme.typography.bodySmall, color = p.textTertiary,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 2.dp),
                            )
                        }
                        feed.loading && huesCount > 0 -> SkeletonBlock(null, 184.dp, shape = RoundedCornerShape(24.dp))
                        else -> EpisodesEmpty(hasSources = huesCount > 0, onFind = onOpenHues, onAddSources = onOpenHueStore)
                    }
                }
            }

            // ---- 気分 Mood of the hour --------------------------------------------------------------
            if ("moods" !in hidden) item(key = "moods", contentType = "moods") {
                MoodSection(
                    onPlay = onPlayYouTube, online = isOnline, onOffline = onNavigateToMusic,
                    channels = channels, onOpenTitle = onOpenOnlineTitle,
                )
            }

            // ---- 新作 New arrivals (from extensions) -------------------------------------------------
            if ("new" !in hidden && arrivals.isNotEmpty()) {
                item(key = "new_header", contentType = "header") {
                    YuSection("新作", "New arrivals", "Fresh from your extensions", action = "See all", onAction = onOpenHues)
                }
                item(key = "new_row", contentType = "row") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(RowGap), modifier = Modifier.fillMaxWidth().testTag("home_new_arrivals")) {
                        items(arrivals, key = { "na_" + it.key }) { item -> PosterCard(item, onClick = { onOpenOnlineTitle(item) }, width = 108.dp, showSource = true) }
                    }
                }
            } else if ("new" !in hidden && channels.isEmpty() && !feed.loading && (allTracks.isNotEmpty() || recentVideos.isNotEmpty())) {
                // No extensions yet: the newest files on this phone stand in.
                val local = (allTracks.sortedByDescending { it.dateAdded }.take(10).map { it.dateAdded to (it as Any) } +
                    recentVideos.sortedByDescending { it.dateAdded }.take(6).map { it.dateAdded to (it as Any) }).sortedByDescending { it.first }.take(12).map { it.second }
                item(key = "new_header", contentType = "header") { YuSection("新作", "New arrivals", "Newest on this phone") }
                item(key = "new_row", contentType = "row") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(RowGap), modifier = Modifier.fillMaxWidth().testTag("home_new_arrivals")) {
                        items(local, key = { a -> if (a is AudioTrack) "nt_${a.id}" else "nv_${(a as VideoItem).id}" }) { a ->
                            if (a is AudioTrack) SquareCard(a.title, a.artist, "home_added_${a.id}", onClick = { onTrackClick(a, local.filterIsInstance<AudioTrack>()) }) {
                                TrackArtwork(artUri = a.albumArtUri, key = a.id, size = 400.dp, cornerRadius = 0.dp, contentDescription = a.album, modifier = Modifier.fillMaxSize())
                            } else if (a is VideoItem) SquareCard(a.title, a.folderName, "home_small_video_${a.id}", video = true, onClick = { onVideoClick(a, local.filterIsInstance<VideoItem>()) }) {
                                TrackArtwork(artUri = null, key = a.id, size = 400.dp, cornerRadius = 0.dp, icon = Icons.Default.Movie, modifier = Modifier.fillMaxSize())
                                AsyncImage(model = dev.asfakul.yumeiro.ui.image.VideoThumb(a.uri), contentDescription = a.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }

            // ---- 人気 Most played --------------------------------------------------------------------
            if ("most" !in hidden && played.isNotEmpty()) {
                item(key = "most_header", contentType = "header") {
                    YuSection("人気", "Most played", "Your top songs and videos", action = "See all", onAction = onNavigateToLibrary)
                }
                item(key = "most_row", contentType = "row") {
                    val songs = played.filterIsInstance<Played.Song>().map { it.track }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(RowGap), modifier = Modifier.fillMaxWidth().testTag("home_most_played")) {
                        items(played, key = { "most_" + it.key }) { item ->
                            when (item) {
                                is Played.Song -> SquareCard(
                                    item.track.title, plays(item.count, item.track.artist), "home_most_${item.track.id}",
                                    highlight = selectedTrack?.id == item.track.id, onClick = { onTrackClick(item.track, songs) },
                                ) { TrackArtwork(artUri = item.track.albumArtUri, key = item.track.id, size = 400.dp, cornerRadius = 0.dp, contentDescription = item.track.album, modifier = Modifier.fillMaxSize()) }
                                is Played.Video -> {
                                    val e = item.entry
                                    SquareCard(e.title, plays(e.count, e.subtitle), "home_most_${e.key}", video = true, onClick = {
                                        when (e.kind) {
                                            PlayCounts.Kind.DEVICE -> item.device?.let { onVideoClick(it, listOf(it)) }
                                            PlayCounts.Kind.ONLINE -> if (e.api != null && e.url != null) onOpenOnlineTitle(OnlineItem(e.title, e.url, e.api, e.thumbnail, e.headers))
                                            PlayCounts.Kind.YOUTUBE -> YtItem(e.id, "https://www.youtube.com/watch?v=${e.id}", e.title, e.subtitle, thumbnail = e.thumbnail).let { onPlayYouTube(it, listOf(it)) }
                                        }
                                    }) {
                                        // Painted art underneath, so a missing or slow thumbnail never leaves a blank tile.
                                        TrackArtwork(artUri = null, key = e.key, size = 400.dp, cornerRadius = 0.dp, icon = Icons.Default.Movie, modifier = Modifier.fillMaxSize())
                                        when (e.kind) {
                                            PlayCounts.Kind.DEVICE -> AsyncImage(model = dev.asfakul.yumeiro.ui.image.VideoThumb(e.thumbnail.orEmpty()), contentDescription = e.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                            PlayCounts.Kind.ONLINE -> PosterImage(e.thumbnail, e.headers, Modifier.fillMaxSize())
                                            PlayCounts.Kind.YOUTUBE -> AsyncImage(model = e.thumbnail, contentDescription = e.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ---- YouTube for you -------------------------------------------------------------------
            if ("yt" !in hidden && ytRelated.isNotEmpty()) {
                item(key = "yt_header", contentType = "header") {
                    YuSection("映像", "On YouTube", "Because of what you watched", action = "See all", onAction = onOpenYouTube)
                }
                item(key = "yt_row", contentType = "row") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(RowGap), modifier = Modifier.fillMaxWidth()) {
                        items(ytRelated, key = { "yt_" + it.id }) { yt -> YtCard(yt, square = false) { onPlayYouTube(yt, ytRelated) } }
                    }
                }
            }

            // ---- 乱 Yumix random: endless, grouped by extension ----------------------------------------
            if ("random" !in hidden && (channels.isNotEmpty() || devicePool.isNotEmpty())) {
                val online = channels.isNotEmpty()
                item(key = "random_header", contentType = "header") {
                    YuSection(
                        "乱", "Yumix random", if (online) "Endless picks, by extension" else "Shuffled from this phone",
                        modifier = Modifier.testTag("home_feed"),
                        trailing = {
                            GlassPill("Shuffle", onClick = { shuffleSeed = System.nanoTime() }, leading = Icons.Default.Shuffle, tag = "home_feed_shuffle", clickLabel = "Shuffle")
                        },
                    )
                }
                if (online) {
                    random.groups.forEach { g ->
                        item(key = "rg_h_${shuffleSeed}_${g.key}", contentType = "random_group_header") {
                            RandomGroupHeader(g.api, onSeeAll = g.row?.let { r -> { onOpenRow(r) } })
                        }
                        g.items.chunked(randomColumns).forEachIndexed { i, rowItems ->
                            item(key = "rg_${shuffleSeed}_${g.key}_$i", contentType = "random_row") {
                                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    rowItems.forEach { item -> PosterCard(item, onClick = { onOpenOnlineTitle(item) }, modifier = Modifier.weight(1f)) }
                                    repeat(randomColumns - rowItems.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                    item(key = "random_more", contentType = "more") {
                        when {
                            random.loadingMore -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                repeat(randomColumns) { SkeletonBlock(null, 150.dp, Modifier.weight(1f), RoundedCornerShape(14.dp)) }
                            }
                            random.finished -> EndNote("That's everything your extensions have for now. Shuffle for a new mix.")
                            else -> MoreButton("Load more") { scope.launch { random.more(channels, feed.providers) } }
                        }
                    }
                } else {
                    val shown = devicePool.take(deviceShown)
                    val cols = if (wide) 4 else 2
                    shown.chunked(cols).forEachIndexed { i, rowVideos ->
                        item(key = "rv_${shuffleSeed}_$i", contentType = "video_row") {
                            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("home_video_grid_row"), horizontalArrangement = Arrangement.spacedBy(RowGap)) {
                                rowVideos.forEach { v ->
                                    VideoGridCard(
                                        video = v, onVideoClick = { onVideoClick(v, shown) },
                                        onToggleFavorite = { onToggleVideoFavorite(v.id) }, onOptionsClick = { onVideoOptionsClick(v) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                repeat(cols - rowVideos.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                    item(key = "random_more", contentType = "more") {
                        if (deviceShown < devicePool.size) MoreButton("Show ${minOf(6, devicePool.size - deviceShown)} more") { deviceShown += 6 }
                        else EndNote("That's every video on this phone. Add a Yumix source for endless picks.")
                    }
                }
            }

            if (allTracks.isEmpty() && recentVideos.isEmpty() && channels.isEmpty()) item(key = "empty_device") {
                Box(Modifier.padding(top = 24.dp)) {
                    EmptyMediaView(
                        title = "Nothing on this device yet",
                        subtitle = "Put music or videos in local storage and tap Scan — or use Yumix, YouTube or Online music above.",
                        buttonText = "Scan storage",
                        onButtonClick = onRescanClick
                    )
                }
            }
            item(key = "end") {
                Column(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    GlassPill("Customize home", onClick = { customizing = true }, leading = Icons.Default.Tune, tag = "home_customize")
                    Text(
                        "夢色 · You're all caught up.", style = MaterialTheme.typography.bodySmall, color = p.textTertiary,
                        modifier = Modifier.padding(top = 12.dp).testTag("home_end"),
                    )
                }
            }
        }
    }

    if (customizing) {
        ModalBottomSheet(onDismissRequest = { customizing = false }, containerColor = p.surfaceContainerHigh) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp).testTag("home_customize_sheet")) {
                Text("Customize home", style = SectionTitleStyle, color = p.textPrimary)
                Text("Turn sections on or off. Continue always stays at the top.", style = MaterialTheme.typography.bodySmall, color = p.textSecondary, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
                HomeLayout.sections.forEach { (key, label) ->
                    val on = key !in hidden
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(12.dp))
                            .clickable(role = Role.Switch) { HomeLayout.toggle(context, key) }.testTag("home_customize_$key").padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, style = MaterialTheme.typography.bodyLarge, color = p.textPrimary, modifier = Modifier.weight(1f))
                        Switch(checked = on, onCheckedChange = { HomeLayout.toggle(context, key) }, colors = SwitchDefaults.colors(checkedTrackColor = p.accent, checkedThumbColor = p.onAccent))
                    }
                }
            }
        }
    }
}

private fun plays(n: Int, who: String): String = when {
    n <= 0 -> who
    who.isBlank() -> if (n == 1) "1 play" else "$n plays"
    else -> (if (n == 1) "1 play" else "$n plays") + " · " + who
}

// ---- Setup card (one compact row) -------------------------------------------------------------

@Composable
private fun SetupCard(done: Int, total: Int, onOpen: () -> Unit, onHide: () -> Unit, onExplore: (() -> Unit)? = null) {
    val p = LocalMtPalette.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(shape)
            .background(p.accent.copy(alpha = if (p.isDark) 0.12f else 0.09f))
            .border(1.dp, p.accent.copy(alpha = 0.28f), shape)
            .clickable(role = Role.Button, onClick = onOpen)
            .testTag("home_setup_card")
            .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val accent = p.accent
        val track = p.textPrimary.copy(alpha = 0.12f)
        Box(Modifier.size(36.dp).drawBehind {
            val w = 4.dp.toPx()
            drawArc(track, 0f, 360f, false, style = Stroke(w))
            drawArc(accent, -90f, 360f * (done.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f), false, style = Stroke(w, cap = StrokeCap.Round))
        }, contentAlignment = Alignment.Center) {
            Text("$done", color = p.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Finish setting up", style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
            Text("$done of $total ready · sources, DNS, alerts", style = MaterialTheme.typography.bodySmall, color = p.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onExplore != null) {
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(p.accent.copy(alpha = 0.16f))
                    .clickable(role = Role.Button, onClickLabel = "Take the 1-minute tour", onClick = onExplore).testTag("home_setup_explore")
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Explore, null, tint = p.accent, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Tour", color = p.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
        Box(
            Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Hide setup card", onClick = onHide).testTag("home_setup_hide"),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Close, "Hide", tint = p.textTertiary, modifier = Modifier.size(18.dp)) }
    }
}

// ---- Continue cards (the v1.5 two-up layout, tightened) ---------------------------------------

@Composable
private fun ContinueCard(
    modifier: Modifier,
    tag: String,
    eyebrow: String,
    title: String,
    subtitle: String,
    progress: () -> Float,
    onClick: () -> Unit,
    kanji: String,
    playIcon: ImageVector,
    playLabel: String,
    playTag: String,
    onPlay: () -> Unit,
    art: @Composable BoxScope.() -> Unit,
) {
    val p = LocalMtPalette.current
    val shape = RoundedCornerShape(20.dp)
    val track = p.textPrimary.copy(alpha = 0.10f)
    val accent = p.accent
    Column(
        modifier.clip(shape).background(tileFill()).border(1.dp, p.textPrimary.copy(alpha = 0.06f), shape)
            .clickable(onClick = onClick).testTag(tag).padding(10.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.25f).clip(RoundedCornerShape(14.dp)).background(p.textPrimary.copy(alpha = 0.05f))) {
            art()
            KanjiMark(kanji, Modifier.align(Alignment.BottomStart).padding(start = 6.dp), size = 44.sp, alpha = 0.10f)
            Box(
                Modifier.align(Alignment.BottomEnd).padding(8.dp).size(42.dp).clip(CircleShape).background(p.accent)
                    .clickable(role = Role.Button, onClick = onPlay).semantics { contentDescription = playLabel }.testTag(playTag),
                contentAlignment = Alignment.Center,
            ) { Icon(playIcon, null, tint = p.onAccent, modifier = Modifier.size(22.dp)) }
        }
        Spacer(Modifier.height(10.dp))
        Text(eyebrow.uppercase(), color = p.textTertiary, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = p.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = p.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(3.dp).drawBehind {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(track, cornerRadius = r)
            val v = progress().coerceIn(0f, 1f)
            if (v > 0f) drawRoundRect(accent, size = Size(size.width * v, size.height), cornerRadius = r)
        })
    }
}

@Composable
private fun EmptyArt(icon: ImageVector) {
    val p = LocalMtPalette.current
    Box(Modifier.fillMaxSize().background(p.textPrimary.copy(alpha = 0.04f)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = p.textTertiary, modifier = Modifier.size(28.dp))
    }
}

// ---- Spaces ---------------------------------------------------------------------------------

/**
 * A space as a seal (印): a squircle in the space's own Japanese colour with the icon in
 * white, its kanji faintly stamped behind, and the name underneath. No card around it, so
 * the grid breathes; a press sinks the seal a little.
 */
@Composable
private fun FeatureTile(f: Feature, modifier: Modifier) {
    val p = LocalMtPalette.current
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduced = dev.asfakul.yumeiro.ui.components.reducedMotion()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed && !reduced) 0.92f else 1f,
        androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 600f), label = "seal",
    )
    val seal = RoundedCornerShape(20.dp)
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = "Open ${f.title}", onClick = f.onClick)
            .semantics(mergeDescendants = true) { contentDescription = "${f.title}, ${f.status}" }
            .padding(vertical = 4.dp)
            .testTag(f.tag),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(58.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .shadow(if (p.isDark) 10.dp else 6.dp, seal, ambientColor = f.hue, spotColor = f.hue)
                .clip(seal)
                .background(Brush.linearGradient(listOf(lerp(f.hue, Color.White, 0.18f), f.hue, lerp(f.hue, Color.Black, 0.28f)))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                f.kanji, color = Color.White.copy(alpha = 0.20f), fontSize = 34.sp, lineHeight = 34.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 8.dp).clearAndSetSemantics { },
            )
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.22f), 0.45f to Color.Transparent)))
            Icon(f.icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
            f.badge?.let {
                Text(
                    it, color = f.hue, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 5.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(f.title, color = p.textPrimary, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(f.status, color = p.textTertiary, fontSize = 10.sp, lineHeight = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

/** Round 8: the extra Spaces shortcuts (mapped to pages / Settings categories by the host). */
enum class HomeShortcut { SEARCH, MY_LIST, HISTORY, MARKET, WEBSITES, MIX, EQUALIZER, SOURCE_HEALTH, TOGETHER, THEME, DNS, NETWORK, TRACKING, PLAYER, NAV_BAR, NOTIFICATIONS, PRIVACY, BACKUP, YOUTUBE_ACCOUNT, SETTINGS }

@Composable
private fun SpacesGroupLabel(kanji: String, title: String, modifier: Modifier = Modifier) {
    val p = LocalMtPalette.current
    Row(modifier.fillMaxWidth().padding(start = 4.dp, top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(kanji, color = p.accent, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.clearAndSetSemantics { })
        Spacer(Modifier.width(8.dp))
        Text(title.uppercase(), color = p.textTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, modifier = Modifier.semantics { heading() })
    }
}

/**
 * "See more / See less" for Spaces: a full-width quiet pill (48 dp+ touch target) that previews
 * what's inside (a few of the hidden glyphs) and says how many shortcuts it holds. The chevron
 * turns on toggle.
 */
@Composable
private fun SpacesMoreButton(expanded: Boolean, count: Int, preview: List<Feature>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalMtPalette.current
    val reduced = dev.asfakul.yumeiro.ui.components.reducedMotion()
    val turn by androidx.compose.animation.core.animateFloatAsState(
        if (expanded) 180f else 0f,
        if (reduced) androidx.compose.animation.core.snap() else androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 420f), label = "spacesChevron",
    )
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(p.textPrimary.copy(alpha = if (p.isDark) 0.06f else 0.05f))
            .border(1.dp, p.textPrimary.copy(alpha = 0.08f), shape)
            .clickable(role = Role.Button, onClickLabel = if (expanded) "Show fewer spaces" else "Show all spaces", onClick = onClick)
            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("home_spaces_more")
            .tourTarget("home_spaces_more"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = !expanded,
            enter = if (reduced) androidx.compose.animation.fadeIn(androidx.compose.animation.core.snap()) else androidx.compose.animation.expandHorizontally(androidx.compose.animation.core.tween(260)) + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200, 80)),
            exit = if (reduced) androidx.compose.animation.fadeOut(androidx.compose.animation.core.snap()) else androidx.compose.animation.shrinkHorizontally(androidx.compose.animation.core.tween(220)) + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)),
            label = "spacesPreview",
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                preview.forEach { f ->
                    Box(
                        Modifier.size(26.dp).clip(RoundedCornerShape(9.dp)).background(f.hue).border(1.5.dp, p.background, RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(f.icon, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                }
            }
            Spacer(Modifier.width(12.dp))
            }
        }
        androidx.compose.animation.AnimatedContent(
            expanded, Modifier.weight(1f),
            transitionSpec = {
                if (reduced) androidx.compose.animation.fadeIn(androidx.compose.animation.core.snap()) togetherWith androidx.compose.animation.fadeOut(androidx.compose.animation.core.snap())
                else (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220, 60)) + androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(260)) { if (targetState) it / 3 else -it / 3 }) togetherWith
                    androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(110))
            },
            label = "spacesMoreText",
        ) { open ->
            Column {
                Text(if (open) "See less" else "See more", color = p.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    if (open) "Fold the extra shortcuts away" else "$count more · theme, DNS, tracking, network…",
                    color = p.textTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(Icons.Default.ExpandMore, null, tint = p.textSecondary, modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = turn })
    }
}

// ---- 気分 Mood of the hour ----------------------------------------------------------------------

@Composable
private fun MoodSection(
    onPlay: (YtItem, List<YtItem>) -> Unit,
    online: Boolean,
    onOffline: () -> Unit,
    channels: List<Pair<String, List<OnlineRow>>>,
    onOpenTitle: (OnlineItem) -> Unit,
) {
    val context = LocalContext.current
    val p = LocalMtPalette.current
    val topics = remember { dev.asfakul.yumeiro.youtube.YtTopics.load(context, music = true).take(8) }
    val hourMood = remember { HomeMoods.forHour(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) }
    // The mood picks itself: the hour's mood, or now and then one of your topics, chosen once each
    // time Home opens. The songs inside are shuffled too, so Home never looks the same twice.
    var moodKey by rememberSaveable { mutableStateOf(HomeMoods.autoPick(hourMood, topics, java.util.Random(System.nanoTime())).key) }
    val mood = remember(moodKey) { HomeMoods.all.firstOrNull { it.key == moodKey } ?: moodKey.removePrefix("topic:").let(HomeMoods::fromTopic) }
    var seed by rememberSaveable { mutableStateOf(System.nanoTime()) }
    var tick by remember { mutableIntStateOf(0) }
    var songs by remember { mutableStateOf<List<YtItem>?>(null) }
    var videos by remember { mutableStateOf<List<YtItem>>(emptyList()) }
    var failed by remember { mutableStateOf(false) }
    // Unit tests have no network: don't start a real request there.
    val autoLoad = remember { android.os.Build.FINGERPRINT != "robolectric" }
    LaunchedEffect(mood, tick) {
        if (!autoLoad && tick == 0) { songs = emptyList(); return@LaunchedEffect }
        songs = null; failed = false; videos = emptyList()
        val r = kotlinx.coroutines.withTimeoutOrNull(20_000) {
            runCatching {
                YouTube.feed(context, dev.asfakul.yumeiro.youtube.YtFeed(dev.asfakul.yumeiro.youtube.YtFeed.Kind.QUERY, mood.musicQuery, mood.title, "Mood", true)).items
            }.getOrNull()
        }
        if (r == null) { failed = true; songs = emptyList() } else songs = r.distinctBy { it.id }.map { it.copy(music = true) }.shuffled(java.util.Random(seed)).take(14)
        videos = kotlinx.coroutines.withTimeoutOrNull(20_000) {
            runCatching {
                YouTube.feed(context, dev.asfakul.yumeiro.youtube.YtFeed(dev.asfakul.yumeiro.youtube.YtFeed.Kind.QUERY, mood.videoQuery, mood.title, "Mood", false)).items
            }.getOrNull()
        }.orEmpty().filterNot { it.live }.distinctBy { it.id }.shuffled(java.util.Random(seed)).take(10)
    }
    val picks = remember(mood, channels, seed) { HomeMoods.extensionPicks(mood, channels, seed) }

    Column(Modifier.fillMaxWidth().testTag("home_moods")) {
        YuSection(
            "気分", if (mood.key == hourMood.key) "Mood of the hour" else "Mood", mood.title,
        )
        // Mood card: the kanji and a one-line description.
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(tileFill()).padding(horizontal = 14.dp, vertical = 10.dp)
                .testTag("home_mood_${mood.key.substringBefore(':')}"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(mood.kanji, color = p.accent, fontSize = 30.sp, lineHeight = 30.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(mood.line, style = MaterialTheme.typography.bodyMedium, color = p.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Songs · videos · picks from your extensions", style = MaterialTheme.typography.bodySmall, color = p.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (!online) {
            HintRow(Icons.Default.CloudOff, "Moods need the internet", "Your own songs still play offline.", "My music", onOffline, tag = "home_moods_offline")
            return@Column
        }
        val list = songs
        when {
            list == null -> Row(horizontalArrangement = Arrangement.spacedBy(RowGap)) { repeat(3) { SkeletonBlock(120.dp, 120.dp, shape = RoundedCornerShape(14.dp)) } }
            list.isEmpty() && videos.isEmpty() && picks.isEmpty() -> HintRow(
                Icons.Default.Album,
                if (failed) "Couldn't load ${mood.title}" else "Tap to load this mood",
                if (failed) "Check your connection, then try again." else "Songs and videos come from YouTube.",
                if (failed) "Try again" else "Load", onClick = { tick++ }, tag = "home_moods_retry",
            )
            else -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (list.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(RowGap), modifier = Modifier.fillMaxWidth().testTag("home_mood_songs")) {
                    items(list, key = { "ms_" + it.id }) { yt -> YtCard(yt, square = true) { onPlay(yt, list) } }
                }
                if (list.isEmpty()) HintRow(Icons.Default.Album, if (failed) "Songs didn't load" else "Load songs for this mood", "From YouTube Music.", if (failed) "Try again" else "Load", onClick = { tick++ }, tag = "home_moods_retry")
                if (videos.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(RowGap), modifier = Modifier.fillMaxWidth()) {
                    items(videos, key = { "mv_" + it.id }) { yt -> YtCard(yt, square = false) { onPlay(yt, videos) } }
                }
                if (picks.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(RowGap), modifier = Modifier.fillMaxWidth().testTag("home_mood_extensions")) {
                    items(picks, key = { "mx_" + it.key }) { item -> PosterCard(item, onClick = { onOpenTitle(item) }, width = 96.dp, showSource = true) }
                }
            }
        }
    }
}

@Composable
private fun HintRow(icon: ImageVector, title: String, text: String, action: String, onClick: () -> Unit, tag: String) {
    val p = LocalMtPalette.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(tileFill())
            .clickable(role = Role.Button, onClick = onClick).testTag(tag).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(p.accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = p.accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
            Text(text, style = MaterialTheme.typography.bodySmall, color = p.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(action, color = p.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
    }
}

// ---- Yumix random -----------------------------------------------------------------------------

@Composable
private fun RandomGroupHeader(api: String, onSeeAll: (() -> Unit)?) {
    val p = LocalMtPalette.current
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp).testTag("home_random_group"), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(p.accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Text(api.take(1).uppercase(), color = p.accent, fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(10.dp))
        Text(api, style = MaterialTheme.typography.titleSmall, color = p.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (onSeeAll != null) Text(
            "More", color = p.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp,
            modifier = Modifier.clip(RoundedCornerShape(50)).clickable(role = Role.Button, onClickLabel = "More from $api", onClick = onSeeAll).padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun MoreButton(label: String, onClick: () -> Unit) {
    val p = LocalMtPalette.current
    Box(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp))
            .border(1.dp, p.textPrimary.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick).testTag("home_random_more"),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
}

@Composable
private fun EndNote(text: String) {
    val p = LocalMtPalette.current
    Text(text, style = MaterialTheme.typography.bodySmall, color = p.textTertiary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
}

// ---- Cards ----------------------------------------------------------------------------------

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "—"
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/** A 120dp square card (songs, device videos, most-played videos). */
@Composable
private fun SquareCard(
    title: String, subtitle: String, tag: String, video: Boolean = false, highlight: Boolean = false,
    onClick: () -> Unit, art: @Composable BoxScope.() -> Unit,
) {
    val p = LocalMtPalette.current
    Column(Modifier.width(120.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).testTag(tag).padding(bottom = 10.dp)) {
        Box(Modifier.size(120.dp).clip(RoundedCornerShape(14.dp)).background(p.textPrimary.copy(alpha = 0.06f))) {
            art()
            Box(
                Modifier.align(Alignment.BottomStart).padding(6.dp).size(22.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) { Icon(if (video) Icons.Default.PlayArrow else Icons.Default.MusicNote, if (video) "Video" else "Song", tint = Color.White, modifier = Modifier.size(14.dp)) }
        }
        Spacer(Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = if (highlight) p.accent else p.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = p.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun YtCard(item: YtItem, square: Boolean, onClick: () -> Unit) {
    val p = LocalMtPalette.current
    val width = if (square) 120.dp else 200.dp
    Column(Modifier.width(width).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).testTag("home_feed_yt_${item.id}").padding(bottom = 10.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(if (square) 1f else 16f / 9f).clip(RoundedCornerShape(14.dp)).background(p.textPrimary.copy(alpha = 0.06f))) {
            TrackArtwork(artUri = null, key = item.id, size = 400.dp, cornerRadius = 0.dp, icon = if (square) Icons.Default.MusicNote else Icons.Default.SmartDisplay, modifier = Modifier.fillMaxSize())
            AsyncImage(model = item.thumbnail, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (!square && item.durationSec > 0) Text(
                formatMs(item.durationSec * 1000), color = Color.White, fontSize = 10.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(item.title, style = MaterialTheme.typography.titleSmall, color = p.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(item.channel, style = MaterialTheme.typography.bodySmall, color = p.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Kept public for other callers: a plain section header with an optional "See all". */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, onSeeAll: (() -> Unit)? = null) {
    YuSection(dev.asfakul.yumeiro.ui.components.Kana.forTitle(title), title, title, modifier, action = if (onSeeAll != null) "See all" else null, onAction = onSeeAll)
}

/** Kept for callers/tests that referenced the old Home helpers. */
@Composable
fun rememberGreeting(): String = dev.asfakul.yumeiro.ui.components.greetingForNow()
