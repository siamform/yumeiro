/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import dev.asfakul.yumeiro.ui.components.Suggestion.Kind as SuggestionKind
import androidx.compose.ui.unit.dp
import dev.asfakul.yumeiro.ui.theme.pageBackground
import dev.asfakul.yumeiro.ui.components.greetingForNow
import androidx.compose.ui.graphics.Color
import android.Manifest
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.asfakul.yumeiro.data.model.AudioTrack
import dev.asfakul.yumeiro.ui.components.AddToPlaylistDialog
import dev.asfakul.yumeiro.ui.components.MTBottomBar
import dev.asfakul.yumeiro.ui.components.MTTopBar
import dev.asfakul.yumeiro.ui.components.ManageHiddenFoldersDialog
import dev.asfakul.yumeiro.ui.components.MiniPlayerBar
import dev.asfakul.yumeiro.ui.components.SongActionBottomSheet
import dev.asfakul.yumeiro.ui.components.SongDetailsDialog
import dev.asfakul.yumeiro.ui.components.VideoDetailsDialog
import dev.asfakul.yumeiro.ui.navigation.Screen
import dev.asfakul.yumeiro.ui.navigation.bottomNavItems
import dev.asfakul.yumeiro.ui.screens.stream.StreamScreen
import dev.asfakul.yumeiro.ui.screens.stream.PlaylistDetailScreen
import dev.asfakul.yumeiro.ui.screens.stream.PlaylistMarketScreen
import dev.asfakul.yumeiro.ui.components.AppMessageHost
import androidx.navigation.NavType
import androidx.navigation.navArgument
import dev.asfakul.yumeiro.stream.IncomingMedia
import dev.asfakul.yumeiro.stream.toVideoItem
import dev.asfakul.yumeiro.ui.theme.ForceDarkTheme
import dev.asfakul.yumeiro.ui.screens.equalizer.EqualizerScreen
import dev.asfakul.yumeiro.ui.screens.home.HomeScreen
import dev.asfakul.yumeiro.ui.screens.library.LibraryScreen
import dev.asfakul.yumeiro.ui.screens.music.MusicScreen
import dev.asfakul.yumeiro.ui.screens.player.NowPlayingScreen
import dev.asfakul.yumeiro.ui.screens.settings.SettingsScreen
import dev.asfakul.yumeiro.ui.screens.splash.SplashScreen
import dev.asfakul.yumeiro.ui.screens.video.VideoPlayerModal
import dev.asfakul.yumeiro.ui.screens.video.VideoScreen
import dev.asfakul.yumeiro.ui.screens.online.ExtensionMarketScreen
import dev.asfakul.yumeiro.ui.screens.online.ExtensionsScreen
import dev.asfakul.yumeiro.ui.screens.online.OnlineDetailScreen
import dev.asfakul.yumeiro.ui.screens.online.OnlineHomeScreen
import dev.asfakul.yumeiro.ui.screens.online.OnlineSearchScreen
import dev.asfakul.yumeiro.ui.screens.online.OnlinePlayerOverlay
import dev.asfakul.yumeiro.ui.screens.online.VideoSourceSwitch
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.ui.theme.DarkBackground
import dev.asfakul.yumeiro.ui.viewmodel.MainViewModel
import dev.asfakul.yumeiro.update.ui.UpdateHost

@Composable
fun MTPlayerApp(
    viewModel: MainViewModel,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val navRoute = navBackStackEntry?.destination?.route ?: Screen.Home.route
    // Bottom-bar tabs are pages of one pager on the Home destination (finger-following swipe).
    val tabPager = rememberPagerState { bottomNavItems.size }
    val tabScope = rememberCoroutineScope()
    /** The visible top-level page: the pager's tab on the root, otherwise the navigation route. */
    val currentRoute = if (navRoute == Screen.Home.route) bottomNavItems[tabPager.currentPage.coerceIn(0, bottomNavItems.lastIndex)].route else navRoute
    val context = LocalContext.current

    // Splash is an overlay instead of a navigation destination, so the back stack always
    // starts at Home and can never be left in a broken state after navigation.
    var showSplash by rememberSaveable { mutableStateOf(true) }

    // One navigation entry point for every top-level destination (bottom bar and the quick
    // access cards). Home is always the root of the back stack: going "home" pops back to it
    // instead of restoring a saved state, which previously re-opened the last tab and made the
    // Home tab look dead.
    val navigateTop: (String) -> Unit = remember(navController, tabPager) {
        { route ->
            runCatching {
                if (navController.currentDestination?.route != Screen.Home.route &&
                    !navController.popBackStack(Screen.Home.route, inclusive = false)
                ) {
                    navController.navigate(Screen.Home.route) { launchSingleTop = true }
                }
            }
            val page = bottomNavItems.indexOfFirst { it.route == route }
            if (page >= 0 && page != tabPager.currentPage) tabScope.launch {
                // Neighbouring tab: slide; far tab: jump (no flash of every page in between).
                if (kotlin.math.abs(page - tabPager.currentPage) == 1) tabPager.animateScrollToPage(page)
                else tabPager.scrollToPage(page)
            }
        }
    }
    val navigateSub: (String) -> Unit = remember(navController) {
        { route -> runCatching { navController.navigate(route) { launchSingleTop = true } } }
    }
    val popSub: () -> Unit = remember(navController) {
        { if (navController.previousBackStackEntry != null) navController.popBackStack() }
    }
    // Tap on an online title: movies play straight away; series open their episode list.
    val openOnline: (dev.asfakul.yumeiro.online.OnlineItem) -> Unit = remember(navigateSub) {
        { item -> dev.asfakul.yumeiro.online.OnlineSession.open(context, item) { navigateSub(Screen.OnlineTitle.route(it.api, it.url)) } }
    }

    // Extensions that open another title (CloudStream's loadResult) land on our title page.
    androidx.compose.runtime.DisposableEffect(navigateSub) {
        com.lagradost.cloudstream3.utils.AppContextUtils.opener = { url, api, _ -> navigateSub(Screen.OnlineTitle.route(api, url)) }
        onDispose { com.lagradost.cloudstream3.utils.AppContextUtils.opener = null }
    }
    // "View" after starting a download, the download notification, Home and Library open the Downloads page.
    LaunchedEffect(navigateSub) {
        dev.asfakul.yumeiro.ui.screens.downloads.DownloadsNav.requests.collect {
            if (it != 0L) { navigateSub(Screen.Downloads.route); dev.asfakul.yumeiro.ui.screens.downloads.DownloadsNav.consumed() }
        }
    }
    // "Resume" on the home-screen widget.
    LaunchedEffect(openOnline) {
        dev.asfakul.yumeiro.widget.TitleNav.pending.collect { item ->
            if (item != null) { dev.asfakul.yumeiro.widget.TitleNav.consumed(); openOnline(item) }
        }
    }

    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val positionState = viewModel.playbackPositionMs.collectAsStateWithLifecycle()
    val positionProvider: () -> Long = remember(positionState) { { positionState.value } }
    val equalizerState by viewModel.equalizerState.collectAsStateWithLifecycle()
    val isNowPlayingExpanded by viewModel.isNowPlayingExpanded.collectAsStateWithLifecycle()
    val actionTrack by viewModel.actionTrack.collectAsStateWithLifecycle()
    val showAddToPlaylistDialog by viewModel.showAddToPlaylistDialog.collectAsStateWithLifecycle()
    val pendingPlaylistTrackIds by viewModel.pendingPlaylistTrackIds.collectAsStateWithLifecycle()

    val stats by viewModel.storageStats.collectAsStateWithLifecycle()
    val filteredTracks by viewModel.filteredAudioTracks.collectAsStateWithLifecycle()
    val rawTracks by viewModel.rawAudioTracks.collectAsStateWithLifecycle()
    val albums by viewModel.filteredAlbums.collectAsStateWithLifecycle()
    val artists by viewModel.filteredArtists.collectAsStateWithLifecycle()
    val folders by viewModel.filteredFolders.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val recentlyPlayed by viewModel.recentlyPlayedTracks.collectAsStateWithLifecycle()
    val mostPlayed by viewModel.mostPlayedTracks.collectAsStateWithLifecycle()

    val filteredVideos by viewModel.filteredVideoItems.collectAsStateWithLifecycle()
    val videoFolders by viewModel.videoFolders.collectAsStateWithLifecycle()
    val continueWatchingVideos by viewModel.continueWatchingVideos.collectAsStateWithLifecycle()
    val recentlyWatchedVideos by viewModel.recentlyWatchedVideos.collectAsStateWithLifecycle()
    val favoriteVideos by viewModel.favoriteVideos.collectAsStateWithLifecycle()
    val hiddenVideoFolderPaths by viewModel.hiddenVideoFolderPaths.collectAsStateWithLifecycle()
    val videoSort by viewModel.videoSort.collectAsStateWithLifecycle()
    val isVideoGridView by viewModel.isVideoGridView.collectAsStateWithLifecycle()
    val selectedVideoFolder by viewModel.selectedVideoFolder.collectAsStateWithLifecycle()
    val activePlayingVideo by viewModel.activePlayingVideo.collectAsStateWithLifecycle()
    var videoMinimized by rememberSaveable { mutableStateOf(false) }
    val videoForDetails by viewModel.videoForDetails.collectAsStateWithLifecycle()
    val showManageHiddenFoldersDialog by viewModel.showManageHiddenFoldersDialog.collectAsStateWithLifecycle()

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val trackSort by viewModel.trackSort.collectAsStateWithLifecycle()
    val activeMusicTab by viewModel.activeMusicTab.collectAsStateWithLifecycle()
    val activeVideoTab by viewModel.activeVideoTab.collectAsStateWithLifecycle()

    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    val selectedTrackIds by viewModel.selectedTrackIds.collectAsStateWithLifecycle()

    var trackForDetailsDialog by remember { mutableStateOf<AudioTrack?>(null) }

    // Permissions launcher for Android media access & notifications
    var permissionAskedAt by remember { mutableLongStateOf(0L) }
    var startupPermissionsDone by rememberSaveable { mutableStateOf(false) }
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // A result that comes back almost instantly means Android didn't show its dialog (asked
        // before / blocked); then our own "Turn on notifications" card may explain. If the user
        // really saw the system dialog, don't ask again in this version.
        if (Manifest.permission.POST_NOTIFICATIONS in results && SystemClock.elapsedRealtime() - permissionAskedAt > 600) {
            dev.asfakul.yumeiro.ui.screens.extras.PermissionCenter.markNotificationsPrompted(context)
        }
        startupPermissionsDone = true
        val mediaGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            results[Manifest.permission.READ_MEDIA_AUDIO] == true ||
                results[Manifest.permission.READ_MEDIA_VIDEO] == true ||
                hasMediaPermission(context)
        } else {
            results[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }
        viewModel.onPermissionResult(mediaGranted, false)
    }

    // Videos opens on Hues when there is a network, otherwise on device videos. It follows the
    // connection until the user picks a source themselves; from then on their choice sticks.
    var videoSourcePicked by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var videoSource by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(dev.asfakul.yumeiro.ui.screens.online.VideoSource.defaultFor(dev.asfakul.yumeiro.network.Connectivity.isOnline(context)))
    }
    LaunchedEffect(Unit) {
        dev.asfakul.yumeiro.network.Connectivity.online.collect { online ->
            if (!videoSourcePicked) videoSource = dev.asfakul.yumeiro.ui.screens.online.VideoSource.defaultFor(online)
        }
    }
    var musicOnline by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    // Online extensions: load installed ones, then sync updates in the background (like stream playlists).
    LaunchedEffect(Unit) {
        ExtensionManager.start(context)
        if (Build.FINGERPRINT == "robolectric") return@LaunchedEffect
        kotlinx.coroutines.delay(4_000)
        // First run: install one top-rated Movies, Anime and Sports extension so online video works right away.
        if (dev.asfakul.yumeiro.network.Connectivity.isOnline(context)) {
            val starters = runCatching { dev.asfakul.yumeiro.online.StarterExtensions.ensure(context) }.getOrDefault(emptyList())
            if (starters.isNotEmpty()) {
                dev.asfakul.yumeiro.online.OnlineRepository.clearCaches()
                dev.asfakul.yumeiro.ui.components.AppMessages.success(context, "Ready to watch: ${starters.joinToString()} installed")
            }
        }
        val result = ExtensionManager.sync(context)
        val changed = result.installed + result.updated
        if (changed.isNotEmpty()) dev.asfakul.yumeiro.online.OnlineRepository.clearCaches()
        if (result.updated.isNotEmpty()) {
            dev.asfakul.yumeiro.ui.components.AppMessages.success(context, if (result.updated.size == 1) "${result.updated[0]} extension updated" else "${result.updated.size} extensions updated")
        }
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (hasMediaPermission(context)) {
            // Already granted: scan immediately instead of flashing the system dialog every launch.
            viewModel.onPermissionResult(true, false)
        }
        if (missing.isNotEmpty()) {
            permissionAskedAt = SystemClock.elapsedRealtime()
            runCatching { permissionsLauncher.launch(missing.toTypedArray()) }.onFailure { startupPermissionsDone = true }
        } else startupPermissionsDone = true
    }

    val isSettings = currentRoute == Screen.Settings.route
    LaunchedEffect(Unit) { dev.asfakul.yumeiro.notify.Inbox.ensureLoaded(context) }
    val inboxUnread by dev.asfakul.yumeiro.notify.Inbox.unread.collectAsStateWithLifecycle()
    val appPage by dev.asfakul.yumeiro.ui.screens.extras.AppPages.page.collectAsStateWithLifecycle()
    val isOnline by dev.asfakul.yumeiro.network.Connectivity.online.collectAsStateWithLifecycle()
    val updateDialogOpen by remember { dev.asfakul.yumeiro.update.UpdateManager.get(context).dialogVisible }.collectAsStateWithLifecycle()
    val inPip by dev.asfakul.yumeiro.player.PipController.inPip.collectAsStateWithLifecycle()
    val isEqualizer = currentRoute == Screen.Equalizer.route
    val isSubpage = isSettings || isEqualizer || isSubRoute(currentRoute)

    val topBarTitle = when (currentRoute) {
        Screen.Home.route -> "Yumeiro"
        Screen.Music.route -> "Music"
        Screen.Videos.route -> "Videos"
        Screen.Library.route -> "Library"
        Screen.Stream.route -> "Stream"
        Screen.Settings.route -> "Settings"
        Screen.Equalizer.route -> "Equalizer"
        else -> "Yumeiro"
    }

    // Typeahead for the top-bar search: recent searches plus matching songs, artists, albums and videos.
    var recentTick by remember { mutableStateOf(0) }
    val topBarSuggestions = remember(searchQuery, filteredTracks, albums, artists, filteredVideos, recentTick) {
        if (searchQuery.isBlank()) emptyList() else {
            val c = buildList {
                addAll(dev.asfakul.yumeiro.ui.components.RecentSearches.asSuggestions(context, "library"))
                filteredTracks.take(200).forEach { add(dev.asfakul.yumeiro.ui.components.Suggestion(it.title, SuggestionKind.SONG, it.artist.takeIf { a -> a.isNotBlank() && !a.startsWith("<") })) }
                artists.take(100).forEach { add(dev.asfakul.yumeiro.ui.components.Suggestion(it.name, SuggestionKind.ARTIST, "Artist")) }
                albums.take(100).forEach { add(dev.asfakul.yumeiro.ui.components.Suggestion(it.name, SuggestionKind.ALBUM, "Album · ${it.artist}")) }
                filteredVideos.take(200).forEach { add(dev.asfakul.yumeiro.ui.components.Suggestion(it.title, SuggestionKind.VIDEO, it.folderName.takeIf { f -> f.isNotBlank() })) }
            }
            dev.asfakul.yumeiro.ui.components.SearchSuggest.rank(searchQuery, c, limit = 7)
        }
    }

    val ambientColor = dev.asfakul.yumeiro.ui.theme.rememberAmbientColor(playbackState.currentTrack?.albumArtUri)
    androidx.compose.runtime.CompositionLocalProvider(
        dev.asfakul.yumeiro.ui.components.LocalIsPlaying provides playbackState.isPlaying,
        dev.asfakul.yumeiro.ui.theme.LocalAmbientColor provides ambientColor,
    ) {
    Box(modifier = Modifier.fillMaxSize().pageBackground()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .testTag("app_root_scaffold"),
            containerColor = Color.Transparent,
            topBar = {
                if (!isSubpage) {
                    MTTopBar(
                        title = if (currentRoute == Screen.Home.route) greetingForNow() else topBarTitle,
                        overline = when (currentRoute) {
                            Screen.Home.route -> "夢色 · Yumeiro"
                            Screen.Music.route -> "ミュージック · Listen"
                            Screen.Videos.route -> "ビデオ · Watch"
                            Screen.Stream.route -> "ストリーム · Live"
                            Screen.Library.route -> "ライブラリ · Yours"
                            else -> null
                        },
                        searchQuery = searchQuery,
                        onSearchQueryChange = viewModel::onSearchQueryChange,
                        suggestions = topBarSuggestions,
                        onSubmit = { q -> dev.asfakul.yumeiro.ui.components.RecentSearches.add(context, "library", q); recentTick++ },
                        onSettingsClick = { navigateSub(Screen.Settings.route) },
                        onInboxClick = { dev.asfakul.yumeiro.ui.screens.extras.AppPages.open(dev.asfakul.yumeiro.ui.screens.extras.AppPages.Page.INBOX) },
                        unreadCount = inboxUnread,
                        // Videos (Hues / YouTube / device) and Stream have their own search box, so does online Music.
                        showSearch = when (currentRoute) {
                            Screen.Videos.route, Screen.Stream.route -> false
                            Screen.Music.route -> !musicOnline
                            else -> true
                        },
                    )
                }
            },
            bottomBar = {
                if (!isSubpage) {
                    // One floating dock: the mini player joins the nav bar while music is playing.
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Column(modifier = Modifier.fillMaxWidth().widthIn(max = dev.asfakul.yumeiro.ui.components.DockMaxWidth)) {
                        // ArchiveTune-style dock: with music playing, the mini player sits right on
                        // the nav bar (small junction corners, rounded outer corners) as one piece.
                        val dockJoined = playbackState.currentTrack != null
                        MiniPlayerBar(
                            attachedToNav = dockJoined,
                            playbackState = playbackState,
                            onTogglePlayPause = viewModel::togglePlayPause,
                            onNext = viewModel::next,
                            onPrevious = viewModel::previous,
                            onClick = viewModel::openNowPlaying,
                            onClose = viewModel::stopPlayback,
                            positionMs = positionProvider
                        )

                        MTBottomBar(
                            currentRoute = currentRoute,
                            onNavigateToRoute = { navigateTop(it) },
                            joinedWithMiniPlayer = dockJoined,
                        )
                    }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
            ) {
                // No tab swipes under the full-screen video or Now Playing.
                val tabSwipeEnabled = (activePlayingVideo == null || videoMinimized) && !isNowPlayingExpanded
                val tabPage: @Composable (Int) -> Unit = { page ->
                    when (bottomNavItems[page].route) {
                        Screen.Home.route -> {
                            val huesEngine by dev.asfakul.yumeiro.online.ExtensionManager.state.collectAsState()
                            val onlineHistoryAll by dev.asfakul.yumeiro.online.OnlineHistory.entries.collectAsState()
                            val adultOn by dev.asfakul.yumeiro.online.OnlineSettings.adult.collectAsState()
                            val adultPrivate by dev.asfakul.yumeiro.online.OnlineSettings.adultPrivate.collectAsState()
                            val onlineHistory = remember(onlineHistoryAll, adultOn, adultPrivate) { dev.asfakul.yumeiro.online.OnlineHistory.forFeed(onlineHistoryAll, adultOn, adultPrivate) }
                            val ytHistoryItems by dev.asfakul.yumeiro.youtube.YtHistory.items.collectAsState()
                            val homeResumeVideo = remember(recentlyWatchedVideos, continueWatchingVideos, onlineHistory, ytHistoryItems) {
                                val device = (continueWatchingVideos + recentlyWatchedVideos).maxByOrNull { it.lastWatchedTimestamp }
                                    ?.takeIf { it.lastWatchedTimestamp > 0 }
                                val online = onlineHistory.firstOrNull()
                                val yt = ytHistoryItems.firstOrNull { !it.music }
                                when {
                                    device != null && (online == null || device.lastWatchedTimestamp >= online.updatedAt) ->
                                        dev.asfakul.yumeiro.ui.screens.home.ResumeVideo(
                                            title = device.title, subtitle = "On device · ${device.folderName}",
                                            thumbnail = dev.asfakul.yumeiro.ui.image.VideoThumb(device.uri),
                                            progress = if (device.durationMs > 0) device.playbackPositionMs.toFloat() / device.durationMs else 0f,
                                            onClick = { viewModel.playVideo(device, continueWatchingVideos.ifEmpty { listOf(device) }) },
                                        )
                                    online != null -> dev.asfakul.yumeiro.ui.screens.home.ResumeVideo(
                                        title = online.name, subtitle = listOfNotNull(online.episodeLabel, online.api).joinToString(" · "),
                                        thumbnail = online.posterUrl, thumbnailHeaders = online.posterHeaders, progress = online.fraction,
                                        onClick = { openOnline(online.toItem()) },
                                    )
                                    yt != null -> dev.asfakul.yumeiro.ui.screens.home.ResumeVideo(
                                        title = yt.title, subtitle = "YouTube · ${yt.channel}", thumbnail = yt.thumbnail,
                                        onClick = { dev.asfakul.yumeiro.youtube.YouTubePlayback.playVideo(context, yt) },
                                    )
                                    else -> null
                                }
                            }
                            OpaquePage {
                            HomeScreen(
                                stats = stats,
                                allTracks = filteredTracks,
                                recentlyPlayed = recentlyPlayed,
                                mostPlayed = mostPlayed,
                                recentVideos = filteredVideos,
                                selectedTrack = playbackState.currentTrack,
                                onTrackClick = { track, queue ->
                                    viewModel.playTrack(track, queue)
                                },
                                onTrackOptionsClick = viewModel::openTrackOptions,
                                onVideoClick = viewModel::playVideo,
                                onToggleVideoFavorite = viewModel::toggleVideoFavorite,
                                onVideoOptionsClick = viewModel::showVideoDetails,
                                onToggleFavorite = viewModel::onToggleFavorite,
                                onRescanClick = viewModel::scanMedia,
                                onNavigateToMusic = { musicOnline = false; navigateTop(Screen.Music.route) },
                                onNavigateToVideos = { videoSourcePicked = true; videoSource = dev.asfakul.yumeiro.ui.screens.online.VideoSource.DEVICE; navigateTop(Screen.Videos.route) },
                                onOpenYouTubeMusic = { musicOnline = true; navigateTop(Screen.Music.route) },
                                onOpenYouTube = { videoSourcePicked = true; videoSource = dev.asfakul.yumeiro.ui.screens.online.VideoSource.YOUTUBE; navigateTop(Screen.Videos.route) },
                                onOpenHues = { videoSourcePicked = true; videoSource = dev.asfakul.yumeiro.ui.screens.online.VideoSource.ONLINE; navigateTop(Screen.Videos.route) },
                                onOpenLive = { navigateTop(Screen.Stream.route) },
                                onOpenSearch = { navigateSub(Screen.OnlineSearch.route) },
                                onOpenHueStore = { navigateSub(Screen.Extensions.route) },
                                huesCount = huesEngine.installed.count { it.enabled },
                                onNavigateToLibrary = { navigateTop(Screen.Library.route) },
                                onNavigateToSettings = { navigateSub(Screen.Settings.route) },
                                onNavigateToEqualizer = { navigateSub(Screen.Equalizer.route) },
                                onOpenDownloads = { navigateSub(Screen.Downloads.route) },
                                isPlaying = playbackState.isPlaying,
                                durationMs = playbackState.durationMs,
                                positionMs = positionProvider,
                                onTogglePlayPause = viewModel::togglePlayPause,
                                onOpenNowPlaying = viewModel::openNowPlaying,
                                resumeVideo = homeResumeVideo,
                                onPlayYouTube = { item, list ->
                                    if (item.music) {
                                        dev.asfakul.yumeiro.youtube.YtHistory.add(context, item)
                                        val queue = list.map { dev.asfakul.yumeiro.youtube.YouTubePlayback.toTrack(it) }
                                        viewModel.playTrack(dev.asfakul.yumeiro.youtube.YouTubePlayback.toTrack(item), queue)
                                    } else {
                                        dev.asfakul.yumeiro.youtube.YouTubePlayback.playVideo(context, item)
                                    }
                                },
                                onlinePicks = { header ->
                                    dev.asfakul.yumeiro.ui.screens.online.OnlineHomePicks(
                                        onOpenTitle = openOnline,
                                        onSeeAll = { videoSourcePicked = true; videoSource = dev.asfakul.yumeiro.ui.screens.online.VideoSource.ONLINE; navigateTop(Screen.Videos.route) },
                                        header = header,
                                    )
                                }
                            )
                            }
                        }
                        Screen.Music.route -> {
                            OpaquePage {
                            Column(Modifier.fillMaxSize()) {
                            dev.asfakul.yumeiro.ui.screens.music.MusicSourceSwitch(online = musicOnline, onChange = { musicOnline = it })
                            if (musicOnline) {
                                dev.asfakul.yumeiro.ui.screens.youtube.YouTubeMusicScreen(onPlay = { item, list ->
                                    dev.asfakul.yumeiro.youtube.YtHistory.add(context, item.copy(music = true))
                                    val queue = list.map { dev.asfakul.yumeiro.youtube.YouTubePlayback.toTrack(it) }
                                    viewModel.playTrack(dev.asfakul.yumeiro.youtube.YouTubePlayback.toTrack(item), queue)
                                })
                            } else
                            MusicScreen(
                                tracks = filteredTracks,
                                albums = albums,
                                artists = artists,
                                folders = folders,
                                stats = stats,
                                currentPlayingTrack = playbackState.currentTrack,
                                isPlaying = playbackState.isPlaying,
                                activeTab = activeMusicTab,
                                currentSort = trackSort,
                                isSelectionMode = isSelectionMode,
                                selectedTrackIds = selectedTrackIds,
                                onTabChange = viewModel::onMusicTabChange,
                                onSortChange = viewModel::onSortChange,
                                onTrackClick = { track, queue ->
                                    viewModel.playTrack(track, queue)
                                },
                                onToggleFavorite = viewModel::onToggleFavorite,
                                onTrackOptionsClick = viewModel::openTrackOptions,
                                onToggleSelectionMode = viewModel::toggleSelectionMode,
                                onToggleTrackSelection = viewModel::toggleTrackSelection,
                                onSelectAll = viewModel::selectAllTracks,
                                onClearSelection = viewModel::clearSelection,
                                onPlaySelected = viewModel::playSelectedTracks,
                                onAddSelectedToQueue = viewModel::addSelectedToQueue,
                                onAddSelectedToFavorites = viewModel::addSelectedToFavorites,
                                onAddSelectedToPlaylist = {
                                    viewModel.openAddToPlaylist(selectedTrackIds.toList())
                                },
                                onRescanClick = viewModel::scanMedia,
                                onRequestPermission = {
                                    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
                                    } else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                                    permissionsLauncher.launch(permissions)
                                }
                            )
                            }
                            }
                        }
                        Screen.Videos.route -> {
                            OpaquePage {
                            Column(Modifier.fillMaxSize()) {
                            VideoSourceSwitch(source = videoSource, onChange = { videoSourcePicked = true; videoSource = it })
                            if (videoSource == dev.asfakul.yumeiro.ui.screens.online.VideoSource.YOUTUBE) {
                                dev.asfakul.yumeiro.ui.screens.youtube.YouTubeVideoScreen(
                                    onPlay = { dev.asfakul.yumeiro.youtube.YouTubePlayback.playVideo(context, it) },
                                )
                            } else if (videoSource == dev.asfakul.yumeiro.ui.screens.online.VideoSource.ONLINE) {
                                OnlineHomeScreen(
                                    onOpenSearch = { navigateSub(Screen.OnlineSearch.route) },
                                    onOpenTitle = openOnline,
                                    onOpenExtensions = { navigateSub(Screen.Extensions.route) },
                                    onOpenMarket = { navigateSub(Screen.ExtensionMarket.route) },
                                    onOpenRow = { row -> navigateSub(Screen.OnlineRow.route(row.api, row.title, row.pageName.orEmpty(), row.pageData.orEmpty())) },
                                    onOpenHistory = { navigateSub(Screen.OnlineHistory.route) },
                                    onOpenInfo = { navigateSub(Screen.OnlineTitle.route(it.api, it.url)) },
                                    onOpenLibrary = { navigateSub(Screen.OnlineLibrary.route) },
                                )
                            } else {
                            VideoScreen(
                                videos = filteredVideos,
                                folders = videoFolders,
                                continueWatching = continueWatchingVideos,
                                recentlyWatched = recentlyWatchedVideos,
                                favorites = favoriteVideos,
                                hiddenFolderPaths = hiddenVideoFolderPaths,
                                activeTab = activeVideoTab,
                                currentSort = videoSort,
                                isGridView = isVideoGridView,
                                selectedFolder = selectedVideoFolder,
                                searchQuery = searchQuery,
                                onTabChange = viewModel::onVideoTabChange,
                                onSortChange = viewModel::onVideoSortChange,
                                onToggleLayout = viewModel::toggleVideoLayout,
                                onSearchQueryChange = viewModel::onSearchQueryChange,
                                onVideoClick = viewModel::playVideo,
                                onToggleFavorite = viewModel::toggleVideoFavorite,
                                onOptionsClick = viewModel::showVideoDetails,
                                onFolderClick = viewModel::selectVideoFolder,
                                onBackFromFolder = { viewModel.selectVideoFolder(null) },
                                onHideFolder = viewModel::hideVideoFolder,
                                onManageHiddenFoldersClick = { viewModel.setShowManageHiddenFolders(true) },
                                onRescanClick = viewModel::scanMedia
                            )
                            }
                            }
                            }
                        }
                        Screen.Stream.route -> {
                            OpaquePage {
                            StreamScreen(
                                onOpenPlaylist = { id -> navigateSub(Screen.PlaylistDetail.route(id)) },
                                onOpenMarket = { navigateSub(Screen.Market.route) },
                            )
                            }
                        }
                        Screen.Library.route -> {
                            OpaquePage {
                            var libTab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
                            Column(Modifier.fillMaxSize()) {
                            dev.asfakul.yumeiro.ui.screens.library.LibraryTabs(libTab) { libTab = it }
                            if (libTab == 1) dev.asfakul.yumeiro.ui.screens.library.VideoLibraryContent(
                                deviceContinue = continueWatchingVideos,
                                onOpenTitle = openOnline,
                                onPlayVideo = viewModel::playVideo,
                                onBrowseOnline = { videoSourcePicked = true; videoSource = dev.asfakul.yumeiro.ui.screens.online.VideoSource.ONLINE; navigateTop(Screen.Videos.route) },
                                onOpenHistory = { navigateSub(Screen.OnlineHistory.route) },
                                onOpenDownloads = { navigateSub(Screen.Downloads.route) },
                                header = {
                                    val h by dev.asfakul.yumeiro.online.OnlineHistory.entries.collectAsState()
                                    val b by dev.asfakul.yumeiro.online.OnlineBookmarks.items.collectAsState()
                                    val d by dev.asfakul.yumeiro.online.download.OnlineDownloads.items.collectAsState()
                                    dev.asfakul.yumeiro.ui.screens.library.VideoLibraryHero(
                                        historyCount = h.size, savedCount = b.size, downloadCount = d.size,
                                        onHistory = { navigateSub(Screen.OnlineHistory.route) },
                                        onDownloads = { navigateSub(Screen.Downloads.route) },
                                        onTogether = { navigateSub(Screen.WatchTogether.route) },
                                        onSources = { navigateSub(Screen.SourcesHub.route()) },
                                    )
                                },
                            ) else if (libTab == 2) dev.asfakul.yumeiro.ui.screens.library.ReadingLibraryContent(
                                onBrowse = { navigateSub(Screen.Manga.route) },
                                onOpenTitle = { navigateSub(Screen.MangaTitle.route(it.providerId, it.url, it.title)) },
                                onOpenSources = { navigateSub(Screen.SourcesHub.route(1)) },
                            ) else
                            LibraryScreen(
                                stats = stats,
                                playlists = playlists,
                                allTracks = rawTracks,
                                recentlyPlayed = recentlyPlayed,
                                mostPlayed = mostPlayed,
                                currentPlayingTrack = playbackState.currentTrack,
                                onCreatePlaylist = { name ->
                                    viewModel.createPlaylist(name)
                                },
                                onRenamePlaylist = viewModel::renamePlaylist,
                                onDeletePlaylist = viewModel::deletePlaylist,
                                onRemoveTrackFromPlaylist = viewModel::removeTrackFromPlaylist,
                                onReorderPlaylist = viewModel::reorderPlaylist,
                                onTrackClick = { track, queue ->
                                    viewModel.playTrack(track, queue)
                                },
                                onToggleFavorite = viewModel::onToggleFavorite,
                                onNavigateToMusic = { navigateTop(Screen.Music.route) },
                                onNavigateToSettings = { navigateSub(Screen.Settings.route) },
                                onNavigateToEqualizer = { navigateSub(Screen.Equalizer.route) },
                                onTrackOptionsClick = viewModel::openTrackOptions,
                                huesCount = dev.asfakul.yumeiro.online.ExtensionManager.state.collectAsState().value.installed.count { it.enabled },
                                onRescanClick = viewModel::scanMedia,
                            )
                            }
                            }
                        }
                    }
                }
                NavHost(
                    navController = navController,
                    startDestination = Screen.Home.route,
                    // Bottom-bar destinations switch instantly (no cross-fade of two
                    // half-transparent pages). Settings / Equalizer slide in from the right over
                    // an opaque page and slide back out on pop; nothing fades or scales.
                    enterTransition = {
                        if (isSubRoute(targetState.destination.route)) {
                            slideInHorizontally(tween(NAV_ANIM_MS, easing = FastOutSlowInEasing)) { it }
                        } else EnterTransition.None
                    },
                    exitTransition = {
                        if (isSubRoute(targetState.destination.route)) {
                            slideOutHorizontally(tween(NAV_ANIM_MS, easing = FastOutSlowInEasing)) { -it / 4 }
                        } else ExitTransition.None
                    },
                    popEnterTransition = {
                        if (isSubRoute(initialState.destination.route)) {
                            slideInHorizontally(tween(NAV_ANIM_MS, easing = FastOutSlowInEasing)) { -it / 4 }
                        } else EnterTransition.None
                    },
                    popExitTransition = {
                        if (isSubRoute(initialState.destination.route)) {
                            slideOutHorizontally(tween(NAV_ANIM_MS, easing = FastOutSlowInEasing)) { it }
                        } else ExitTransition.None
                    },
                    sizeTransform = null
                ) {
                    // The five bottom-bar tabs live in one pager: swipe anywhere on a page (rows and
                    // carousels keep their own drags first), the page follows the finger like Facebook / Instagram.
                    composable(Screen.Home.route) {
                        HorizontalPager(
                            state = tabPager,
                            userScrollEnabled = tabSwipeEnabled,
                            key = { bottomNavItems[it].route },
                            modifier = Modifier.fillMaxSize().testTag("tab_pager"),
                        ) { page -> tabPage(page) }
                    }
                    // Old direct routes of the tabs (deep links, saved state) land on the pager page.
                    composable(Screen.Music.route) { LaunchedEffect(Unit) { navigateTop(Screen.Music.route) } }
                    composable(Screen.Videos.route) { LaunchedEffect(Unit) { navigateTop(Screen.Videos.route) } }
                    composable(Screen.Stream.route) { LaunchedEffect(Unit) { navigateTop(Screen.Stream.route) } }
                    composable(Screen.Library.route) { LaunchedEffect(Unit) { navigateTop(Screen.Library.route) } }

                    composable(Screen.OnlineSearch.route) {
                        OpaquePage {
                        OnlineSearchScreen(
                            onBack = popSub,
                            onOpenTitle = openOnline,
                            onOpenExtensions = { navigateSub(Screen.Extensions.route) },
                        )
                        }
                    }
                    composable(
                        Screen.OnlineTitle.route,
                        arguments = listOf(
                            navArgument(Screen.OnlineTitle.ARG_API) { type = NavType.StringType; defaultValue = "" },
                            navArgument(Screen.OnlineTitle.ARG_URL) { type = NavType.StringType; defaultValue = "" },
                        ),
                    ) { entry ->
                        OpaquePage {
                        OnlineDetailScreen(
                            api = entry.arguments?.getString(Screen.OnlineTitle.ARG_API).orEmpty(),
                            url = entry.arguments?.getString(Screen.OnlineTitle.ARG_URL).orEmpty(),
                            onBack = popSub,
                            onOpenTitle = openOnline,
                            onOpenExtensions = { navigateSub(Screen.Extensions.route) },
                        )
                        }
                    }
                    composable(
                        Screen.OnlineRow.route,
                        arguments = listOf(Screen.OnlineRow.ARG_API, Screen.OnlineRow.ARG_TITLE, Screen.OnlineRow.ARG_NAME, Screen.OnlineRow.ARG_DATA)
                            .map { a -> navArgument(a) { type = NavType.StringType; defaultValue = "" } },
                    ) { entry ->
                        OpaquePage {
                        dev.asfakul.yumeiro.ui.screens.online.OnlineRowScreen(
                            api = entry.arguments?.getString(Screen.OnlineRow.ARG_API).orEmpty(),
                            title = entry.arguments?.getString(Screen.OnlineRow.ARG_TITLE).orEmpty(),
                            pageName = entry.arguments?.getString(Screen.OnlineRow.ARG_NAME).orEmpty(),
                            pageData = entry.arguments?.getString(Screen.OnlineRow.ARG_DATA).orEmpty(),
                            onBack = popSub,
                            onOpenTitle = openOnline,
                            onOpenExtensions = { navigateSub(Screen.Extensions.route) },
                        )
                        }
                    }
                    composable(Screen.OnlineHistory.route) {
                        OpaquePage {
                        dev.asfakul.yumeiro.ui.screens.online.OnlineHistoryScreen(onBack = popSub, onOpenTitle = openOnline)
                        }
                    }
                    composable(Screen.Extensions.route) {
                        OpaquePage {
                        ExtensionsScreen(onBack = popSub, onOpenMarket = { navigateSub(Screen.ExtensionMarket.route) }, onOpenSources = { navigateSub(Screen.SourcesHub.route()) })
                        }
                    }
                    composable(Screen.ExtensionMarket.route) {
                        OpaquePage {
                        ExtensionMarketScreen(onBack = popSub, onOpenExtensions = { navigateSub(Screen.Extensions.route) })
                        }
                    }

                    composable(Screen.SourcesHub.route, arguments = listOf(navArgument(Screen.SourcesHub.ARG) { type = NavType.IntType; defaultValue = 0 })) { entry ->
                        OpaquePage { dev.asfakul.yumeiro.ui.screens.sources.SourcesHubScreen(onBack = popSub, initialTab = entry.arguments?.getInt(Screen.SourcesHub.ARG) ?: 0) }
                    }
                    composable(Screen.SourceHealth.route) {
                        OpaquePage { dev.asfakul.yumeiro.ui.screens.sources.SourceHealthScreen(onBack = popSub) }
                    }
                    composable(Screen.Manga.route) {
                        OpaquePage {
                            dev.asfakul.yumeiro.ui.screens.manga.MangaHomeScreen(
                                onBack = popSub,
                                onOpenTitle = { navigateSub(Screen.MangaTitle.route(it.providerId, it.url, it.title)) },
                                onOpenSources = { navigateSub(Screen.SourcesHub.route(1)) },
                            )
                        }
                    }
                    composable(Screen.MangaTitle.route, arguments = listOf("p", "u", "t").map { a -> navArgument(a) { type = NavType.StringType; defaultValue = "" } }) { entry ->
                        val a = entry.arguments
                        OpaquePage {
                            dev.asfakul.yumeiro.ui.screens.manga.MangaDetailScreen(
                                providerId = a?.getString("p").orEmpty(), url = a?.getString("u").orEmpty(), title = a?.getString("t").orEmpty(),
                                onBack = popSub,
                                onRead = { m, ch -> navigateSub(Screen.MangaReader.route(m.providerId, m.url, m.title, ch.url, ch.name)) },
                            )
                        }
                    }
                    composable(Screen.MangaReader.route, arguments = listOf("p", "u", "t", "c", "n").map { a -> navArgument(a) { type = NavType.StringType; defaultValue = "" } }) { entry ->
                        val a = entry.arguments
                        OpaquePage {
                            dev.asfakul.yumeiro.ui.screens.manga.MangaReaderScreen(
                                providerId = a?.getString("p").orEmpty(), mangaUrl = a?.getString("u").orEmpty(), mangaTitle = a?.getString("t").orEmpty(),
                                chapterUrl = a?.getString("c").orEmpty(), chapterName = a?.getString("n").orEmpty(), onBack = popSub,
                            )
                        }
                    }
                    composable(Screen.Privacy.route) {
                        OpaquePage { dev.asfakul.yumeiro.ui.screens.settings.PrivacyScreen(onBack = popSub) }
                    }
                    composable(Screen.Tracking.route) {
                        OpaquePage { dev.asfakul.yumeiro.ui.screens.settings.TrackingScreen(onBack = popSub) }
                    }
                    composable(Screen.Backup.route) {
                        OpaquePage { dev.asfakul.yumeiro.ui.screens.settings.BackupScreen(onBack = popSub) }
                    }
                    composable(Screen.WatchTogether.route) {
                        OpaquePage { dev.asfakul.yumeiro.ui.screens.watch.WatchTogetherScreen(onBack = popSub) }
                    }

                    composable(Screen.Downloads.route) {
                        OpaquePage {
                            dev.asfakul.yumeiro.ui.screens.downloads.DownloadsScreen(
                                onBack = popSub,
                                onPlayVideo = viewModel::playVideo,
                                onBrowse = { videoSourcePicked = true; videoSource = dev.asfakul.yumeiro.ui.screens.online.VideoSource.ONLINE; navigateTop(Screen.Videos.route) },
                            )
                        }
                    }

                    composable(Screen.OnlineLibrary.route) {
                        OpaquePage {
                            Column(Modifier.fillMaxSize()) {
                                dev.asfakul.yumeiro.ui.screens.library.LibraryTopBar("My videos", onBack = popSub)
                                dev.asfakul.yumeiro.ui.screens.library.VideoLibraryContent(
                                    deviceContinue = continueWatchingVideos,
                                    onOpenTitle = openOnline,
                                    onPlayVideo = viewModel::playVideo,
                                    onBrowseOnline = popSub,
                                    onOpenHistory = { navigateSub(Screen.OnlineHistory.route) },
                                    onOpenDownloads = { navigateSub(Screen.Downloads.route) },
                                )
                            }
                        }
                    }

                    composable(
                        Screen.PlaylistDetail.route,
                        arguments = listOf(navArgument(Screen.PlaylistDetail.ARG) { type = NavType.StringType }),
                    ) { entry ->
                        OpaquePage {
                        PlaylistDetailScreen(
                            slug = entry.arguments?.getString(Screen.PlaylistDetail.ARG).orEmpty(),
                            onBack = popSub,
                            onOpenMarket = { navigateSub(Screen.Market.route) },
                        )
                        }
                    }

                    composable(Screen.Market.route) {
                        OpaquePage {
                        PlaylistMarketScreen(
                            onBack = popSub,
                            onOpenPlaylist = { id -> navigateSub(Screen.PlaylistDetail.route(id)) },
                        )
                        }
                    }

                    composable(Screen.Settings.route) {
                        OpaquePage {
                        SettingsScreen(
                            onBackClick = popSub,
                            onRescanClick = viewModel::scanMedia,
                            onNavigateToEqualizer = { navigateSub(Screen.Equalizer.route) },
                            onClearWatchHistory = viewModel::clearWatchHistory,
                            onClearListeningHistory = viewModel::clearListeningHistory,
                            onOpenPage = { page ->
                                navigateSub(when (page) {
                                    "sources" -> Screen.SourcesHub.route()
                                    "tracking" -> Screen.Tracking.route
                                    "backup" -> Screen.Backup.route
                                    "together" -> Screen.WatchTogether.route
                                    "health" -> Screen.SourceHealth.route
                                    else -> Screen.Privacy.route
                                })
                            },
                        )
                        }
                    }

                    composable(Screen.Equalizer.route) {
                        OpaquePage {
                        EqualizerScreen(
                            state = equalizerState,
                            onBackClick = popSub,
                            onToggleEnabled = viewModel::setEqualizerEnabled,
                            onBandLevelChange = viewModel::setEqualizerBandLevel,
                            onPresetSelect = viewModel::applyEqualizerPreset,
                            onBassBoostChange = viewModel::setBassBoostStrength,
                            onVirtualizerChange = viewModel::setVirtualizerStrength,
                            onResetToDefault = viewModel::resetEqualizerToDefault
                        )
                        }
                    }
                }
            }
        }

        // Full Now Playing Screen Overlay
        AnimatedVisibility(
            visible = isNowPlayingExpanded && playbackState.currentTrack != null,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(280, easing = FastOutSlowInEasing)),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(240, easing = FastOutSlowInEasing))
        ) {
            NowPlayingScreen(
                playbackState = playbackState,
                positionMs = positionProvider,
                onClose = viewModel::closeNowPlaying,
                onTogglePlayPause = viewModel::togglePlayPause,
                onNext = viewModel::next,
                onPrevious = viewModel::previous,
                onSeek = viewModel::seekTo,
                onToggleShuffle = viewModel::toggleShuffle,
                onCycleRepeat = viewModel::cycleRepeatMode,
                onToggleFavorite = viewModel::onToggleFavorite,
                onPlayQueueIndex = viewModel::playQueueIndex,
                onDismissError = viewModel::clearPlaybackError,
                onOpenEqualizer = {
                    viewModel.closeNowPlaying()
                    navigateSub(Screen.Equalizer.route)
                },
                onMoreOptions = { playbackState.currentTrack?.let(viewModel::openTrackOptions) },
                sleepTimer = viewModel.sleepTimer,
            )
        }

        // Song Options Bottom Sheet
        actionTrack?.let { track ->
            SongActionBottomSheet(
                track = track,
                onDismiss = viewModel::closeTrackOptions,
                onPlayNext = { viewModel.playNext(track) },
                onAddToQueue = { viewModel.addToQueue(track) },
                onAddToPlaylist = { viewModel.openAddToPlaylist(listOf(track.id)) },
                onToggleFavorite = { viewModel.onToggleFavorite(track.id) },
                onShowDetails = { trackForDetailsDialog = track }
            )
        }

        // Add to Playlist Dialog
        if (showAddToPlaylistDialog) {
            AddToPlaylistDialog(
                playlists = playlists,
                onSelectPlaylist = { playlistId ->
                    viewModel.addTracksToPlaylist(playlistId, pendingPlaylistTrackIds)
                },
                onCreateNewPlaylist = { name ->
                    viewModel.createPlaylist(name, pendingPlaylistTrackIds)
                    viewModel.closeAddToPlaylist()
                },
                onDismiss = viewModel::closeAddToPlaylist
            )
        }

        // Song Details Dialog
        trackForDetailsDialog?.let { track ->
            SongDetailsDialog(
                track = track,
                onDismiss = { trackForDetailsDialog = null }
            )
        }

        // Video Details Dialog
        videoForDetails?.let { video ->
            VideoDetailsDialog(
                video = video,
                onDismiss = viewModel::dismissVideoDetails,
                onPlay = { viewModel.playVideo(video) },
                onToggleFavorite = { viewModel.toggleVideoFavorite(video.id) },
                onHideFolder = { viewModel.hideVideoFolder(video.folderPath) }
            )
        }

        // Manage Hidden Folders Dialog
        if (showManageHiddenFoldersDialog) {
            ManageHiddenFoldersDialog(
                hiddenFolderPaths = hiddenVideoFolderPaths,
                folders = videoFolders,
                onDismiss = { viewModel.setShowManageHiddenFolders(false) },
                onUnhideFolder = viewModel::unhideVideoFolder,
                onUnhideAll = viewModel::unhideAllVideoFolders
            )
        }

        // Full-screen Video Player Modal
        // Media opened from the Stream tab or from other apps ("Open with" / "Share")
        val incoming by IncomingMedia.pending.collectAsStateWithLifecycle()
        LaunchedEffect(activePlayingVideo == null) { if (activePlayingVideo == null) videoMinimized = false }
        LaunchedEffect(incoming) {
            incoming?.let { viewModel.playVideo(it); IncomingMedia.consume() }
        }

        activePlayingVideo?.let { video ->
            // A newly opened video always starts expanded.
            LaunchedEffect(video.id) { videoMinimized = false }
            ForceDarkTheme {
                val videoQueue by viewModel.videoQueue.collectAsStateWithLifecycle()
                val nextVideo = remember(video.id, videoQueue) { viewModel.nextVideoInQueue() }
                val hasPreviousVideo = remember(video.id, videoQueue) { viewModel.previousVideoInQueue() != null }
                val onlineState by dev.asfakul.yumeiro.online.OnlineSession.state.collectAsStateWithLifecycle()
                // Online title: episodes, sources, Sub/Dub and next episode come from the session.
                val online = onlineState?.takeIf { video.id.startsWith("online:") }
                val nextEpisode = remember(online?.index, online?.episodes) { if (online != null) dev.asfakul.yumeiro.online.OnlineSession.nextItem() else null }
                val closePlayer: () -> Unit = { viewModel.closeVideoPlayer(); dev.asfakul.yumeiro.online.OnlineSession.end() }
                // YouTube videos and online titles open on a watch page (player docked on top,
                // details / episodes / related below). Device videos keep the classic full player.
                val isYt = video.id.startsWith("yt:")
                // Stream tab: previous / next channel of the open playlist, like episodes.
                val isStream = video.id.startsWith("stream:")
                val streamChannels by dev.asfakul.yumeiro.stream.StreamRepository.playlist.collectAsStateWithLifecycle()
                val streamNext = remember(video.uri, streamChannels, isStream) { if (isStream) dev.asfakul.yumeiro.ui.screens.watch.StreamWatch.neighbour(streamChannels, video.uri, 1) else null }
                val streamNextItem = remember(streamNext) { streamNext?.toVideoItem() }
                val streamPrev = remember(video.uri, streamChannels, isStream) { if (isStream) dev.asfakul.yumeiro.ui.screens.watch.StreamWatch.neighbour(streamChannels, video.uri, -1) else null }
                val closeYt: () -> Unit = { viewModel.closeVideoPlayer(); dev.asfakul.yumeiro.youtube.YtWatch.end() }
                val watchPage: (@Composable androidx.compose.foundation.layout.ColumnScope.(dev.asfakul.yumeiro.ui.screens.video.PlayerHandle) -> Unit)? = when {
                    online != null -> { handle ->
                        dev.asfakul.yumeiro.ui.screens.online.OnlineWatchPanel(player = handle, onOpenItem = { item -> closePlayer(); openOnline(item) })
                    }
                    isYt -> { _ ->
                        dev.asfakul.yumeiro.ui.screens.watch.YouTubeWatchPanel(onPlay = { dev.asfakul.yumeiro.youtube.YouTubePlayback.playVideo(context, it) })
                    }
                    // Stream tab (IPTV / links): same docked watch page with the playlist's channels.
                    isStream -> { _ ->
                        dev.asfakul.yumeiro.ui.screens.watch.StreamWatchPanel(currentUrl = video.uri, title = video.title)
                    }
                    else -> null
                }
                VideoPlayerModal(
                    video = video,
                    onClose = if (online != null) closePlayer else if (isYt) closeYt else viewModel::closeVideoPlayer,
                    onProgressUpdate = if (online != null) { id, pos, dur -> dev.asfakul.yumeiro.online.OnlineSession.onProgress(context, id, pos, dur) } else viewModel::updateVideoPlaybackProgress,
                    nextVideo = when { online != null -> nextEpisode; streamNextItem != null -> streamNextItem; else -> nextVideo },
                    hasPreviousVideo = online?.hasPrevious ?: (streamPrev != null || hasPreviousVideo),
                    onNextVideo = {
                        when {
                            online != null -> dev.asfakul.yumeiro.online.OnlineSession.next(context)
                            streamNext != null -> dev.asfakul.yumeiro.ui.screens.watch.StreamWatch.play(context, streamNext)
                            else -> viewModel.playNextVideo()
                        }
                    },
                    onPreviousVideo = {
                        when {
                            online != null -> dev.asfakul.yumeiro.online.OnlineSession.previous(context)
                            streamPrev != null -> dev.asfakul.yumeiro.ui.screens.watch.StreamWatch.play(context, streamPrev)
                            else -> viewModel.playPreviousVideo()
                        }
                    },
                    onGiveUp = if (isStream && streamNext != null) ({ dev.asfakul.yumeiro.ui.screens.watch.StreamWatch.autoSwitch(context, video.uri) }) else null,
                    overlay = if (online != null) { visible, player -> OnlinePlayerOverlay(visible, player, closePlayer) } else null,
                    onFatalError = if (online != null) { pos -> dev.asfakul.yumeiro.online.OnlineSession.failover(context, pos, video.id) } else null,
                    watchPage = watchPage,
                    minimized = videoMinimized,
                    onMinimize = { videoMinimized = true },
                    onExpand = { videoMinimized = false },
                    minimizedBottomInset = when {
                        isSubpage -> 16.dp
                        playbackState.currentTrack != null -> 170.dp
                        else -> 100.dp
                    },
                )
            }
        }

        // Online: "Opening…" / "Finding the best source…" before the player opens, or why nothing played.
        ForceDarkTheme {
        dev.asfakul.yumeiro.ui.screens.online.OnlineResolveLayer(
            playerOpen = activePlayingVideo != null,
            onOpenTitle = { api, url -> navigateSub(Screen.OnlineTitle.route(api, url)) },
            onOpenItem = { item -> dev.asfakul.yumeiro.online.OnlineSession.end(); openOnline(item) },
        )
        }

        // YouTube: the watch page opens instantly (skeleton + details) while the stream resolves.
        ForceDarkTheme {
            dev.asfakul.yumeiro.ui.screens.watch.YouTubeWatchLayer(
                playerOpen = activePlayingVideo != null,
                onPlay = { dev.asfakul.yumeiro.youtube.YouTubePlayback.playVideo(context, it) },
            )
        }

        // Notification inbox / Support page, opened from the bell, a notification, a reminder or Settings.
        AnimatedVisibility(
            visible = appPage != null,
            enter = slideInVertically(initialOffsetY = { it / 6 }, animationSpec = tween(NAV_ANIM_MS)) + fadeIn(tween(NAV_ANIM_MS)),
            exit = fadeOut(tween(160)),
        ) {
            val closePage = { dev.asfakul.yumeiro.ui.screens.extras.AppPages.close() }
            when (appPage) {
                dev.asfakul.yumeiro.ui.screens.extras.AppPages.Page.SUPPORT ->
                    dev.asfakul.yumeiro.ui.screens.extras.SupportScreen(onBack = closePage)
                else -> dev.asfakul.yumeiro.ui.screens.extras.InboxScreen(
                    onBack = closePage,
                    onAction = { link ->
                        closePage()
                        when (link) {
                            "update" -> dev.asfakul.yumeiro.update.UpdateManager.get(context).showDialog()
                            "extensions" -> navigateSub(Screen.Extensions.route)
                            "market" -> navigateSub(Screen.ExtensionMarket.route)
                            "streams" -> navigateSub(Screen.Market.route)
                            "support" -> dev.asfakul.yumeiro.ui.screens.extras.AppPages.open(dev.asfakul.yumeiro.ui.screens.extras.AppPages.Page.SUPPORT)
                        }
                    },
                )
            }
        }

        // Offline: a slim banner under the status bar; local music and videos keep working.
        // Hidden over full-screen views (Now Playing, pages) so it never covers their top buttons.
        AnimatedVisibility(
            visible = !isOnline && !showSplash && activePlayingVideo == null && appPage == null &&
                !(isNowPlayingExpanded && playbackState.currentTrack != null),
            enter = fadeIn(tween(200)), exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) { dev.asfakul.yumeiro.ui.components.OfflineBanner() }

        // Popups take turns: never during splash, video, PiP, the update dialog or an open page.
        val popupBlocked = showSplash || activePlayingVideo != null || inPip || updateDialogOpen || appPage != null ||
            (isNowPlayingExpanded && playbackState.currentTrack != null)
        dev.asfakul.yumeiro.ui.screens.extras.NotificationPromptHost(blocked = popupBlocked || !startupPermissionsDone)
        dev.asfakul.yumeiro.ui.screens.extras.ReminderHost(blocked = popupBlocked || !startupPermissionsDone)

        // Styled in-app messages (falls back to system toasts when no host is attached).
        dev.asfakul.yumeiro.ui.screens.online.BrowserCheckHost()
        dev.asfakul.yumeiro.ui.screens.downloads.DownloadPromptHost()
        AppMessageHost(bottomPadding = if (isSubpage) androidx.compose.ui.unit.Dp(24f) else androidx.compose.ui.unit.Dp(150f))

        // In-app updater popup (GitHub Releases). Hidden during splash and fullscreen video.
        UpdateHost(enabled = !showSplash && activePlayingVideo == null)

        // Splash overlay (fades out once, then never shown again for this task).
        AnimatedVisibility(
            visible = showSplash,
            enter = fadeIn(tween(0)),
            exit = fadeOut(tween(250))
        ) {
            SplashScreen(onSplashFinished = { showSplash = false })
        }

        // Double back press to exit. Only active on the Home root when no overlay (player,
        // sheet, dialog) is open; those register their own back handlers.
        val overlayOpen = showSplash || (activePlayingVideo != null && !videoMinimized) ||
            (isNowPlayingExpanded && playbackState.currentTrack != null) ||
            actionTrack != null || showAddToPlaylistDialog || trackForDetailsDialog != null ||
            videoForDetails != null || showManageHiddenFoldersDialog || appPage != null
        var lastBackPressAt by remember { mutableLongStateOf(0L) }
        // Back on another tab returns to Home first (like Instagram), then double back exits.
        BackHandler(enabled = navRoute == Screen.Home.route && tabPager.currentPage != 0 && !overlayOpen) {
            tabScope.launch { tabPager.animateScrollToPage(0) }
        }
        BackHandler(enabled = currentRoute == Screen.Home.route && !overlayOpen) {
            val now = SystemClock.elapsedRealtime()
            if (lastBackPressAt != 0L && now - lastBackPressAt <= EXIT_WINDOW_MS) {
                context.findActivity()?.finish()
            } else {
                lastBackPressAt = now
                Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
            }
        }
    }
    }
}

private const val NAV_ANIM_MS = 220
private const val EXIT_WINDOW_MS = 2_000L

/** Every destination draws its own opaque background so pages never show through each other. */
@Composable
private fun OpaquePage(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pageBackground()
    ) { content() }
}

private fun hasMediaPermission(context: Context): Boolean {
    val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    return perms.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun isSubRoute(route: String?): Boolean =
    route == Screen.Settings.route || route == Screen.Equalizer.route ||
        route == Screen.PlaylistDetail.route || route == Screen.Market.route ||
        route == Screen.OnlineSearch.route || route == Screen.OnlineTitle.route ||
        route == Screen.Extensions.route || route == Screen.ExtensionMarket.route ||
        route == Screen.OnlineRow.route || route == Screen.OnlineHistory.route ||
        route == Screen.OnlineLibrary.route || route == Screen.Downloads.route
