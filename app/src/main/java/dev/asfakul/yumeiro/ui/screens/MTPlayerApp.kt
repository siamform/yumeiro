/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens

import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import dev.asfakul.yumeiro.ui.components.Suggestion.Kind as SuggestionKind
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
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
import dev.asfakul.yumeiro.ui.screens.music.musicSourceTitle
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
    // Settings → Navigation bar decides which tabs the bar shows; tabs hidden from the bar stay
    // as pages after the shown ones, so Home's shortcuts into them keep working.
    val navContext = LocalContext.current
    remember(navContext) { dev.asfakul.yumeiro.ui.navigation.NavPrefs.init(navContext); 0 }
    val navConfig by dev.asfakul.yumeiro.ui.navigation.NavPrefs.config.collectAsState()
    val pageItems = remember(navConfig) {
        dev.asfakul.yumeiro.ui.navigation.NavPrefs.tabItems(navConfig) + bottomNavItems.filter { it.route !in navConfig.tabs }
    }
    val barItems = remember(navConfig) { dev.asfakul.yumeiro.ui.navigation.NavPrefs.barItems(navConfig) }
    val pageItemsNow by androidx.compose.runtime.rememberUpdatedState(pageItems)
    val tabPager = rememberPagerState { bottomNavItems.size }
    val tabScope = rememberCoroutineScope()
    // Fade-through state for far tab jumps (see navigateTop).
    val tabFade = remember { androidx.compose.animation.core.Animatable(1f) }
    val tabShift = remember { androidx.compose.animation.core.Animatable(0f) }
    val reducedMotionNow = remember { dev.asfakul.yumeiro.ui.components.isReducedMotion(navContext) }
    /** The visible top-level page: the pager's tab on the root, otherwise the navigation route. */
    val currentRoute = if (navRoute == Screen.Home.route) pageItems[tabPager.currentPage.coerceIn(0, pageItems.lastIndex)].route else navRoute
    val context = LocalContext.current

    // Splash is an overlay instead of a navigation destination, so the back stack always
    // starts at Home and can never be left in a broken state after navigation.
    var showSplash by rememberSaveable { mutableStateOf(true) }

    // One navigation entry point for every top-level destination (bottom bar and the quick
    // access cards). Home is always the root of the back stack: going "home" pops back to it
    // instead of restoring a saved state, which previously re-opened the last tab and made the
    // Home tab look dead.
    val navigateTop: (String) -> Unit = remember(navController, tabPager, tabFade, tabShift) {
        { route ->
            runCatching {
                if (navController.currentDestination?.route != Screen.Home.route &&
                    !navController.popBackStack(Screen.Home.route, inclusive = false)
                ) {
                    navController.navigate(Screen.Home.route) { launchSingleTop = true }
                }
            }
            val page = pageItemsNow.indexOfFirst { it.route == route }
            if (page >= 0 && page != tabPager.currentPage) tabScope.launch {
                // Neighbouring tab: the pager slides. Far tab (Home → Videos / Library, Spaces and
                // dock shortcuts): this used to be scrollToPage, an instant cut with no motion at all,
                // which is why Yumix, YouTube, Live TV and Library felt like "click and load" when
                // opened from Home. Now it fades through: the current page leaves fast, the pager
                // jumps while invisible (no page in between is composed), and the target fades in
                // with a short glide from the side it lives on.
                val dir = if (page > tabPager.currentPage) 1f else -1f
                if (kotlin.math.abs(page - tabPager.currentPage) == 1 || reducedMotionNow) {
                    tabPager.animateScrollToPage(page, animationSpec = androidx.compose.animation.core.tween(360, easing = dev.asfakul.yumeiro.ui.components.YuMotion.Emphasized))
                } else {
                    tabFade.animateTo(0f, androidx.compose.animation.core.tween(110, easing = dev.asfakul.yumeiro.ui.components.YuMotion.EmphasizedAccelerate))
                    tabPager.scrollToPage(page)
                    tabShift.snapTo(dir)
                    launch { tabShift.animateTo(0f, androidx.compose.animation.core.tween(320, easing = dev.asfakul.yumeiro.ui.components.YuMotion.Emphasized)) }
                    tabFade.animateTo(1f, androidx.compose.animation.core.tween(240, easing = dev.asfakul.yumeiro.ui.components.YuMotion.Standard))
                }
            }
        }
    }
    // A page that is already open further down the stack is brought back instead of stacked again:
    // Extensions → Market → "Manage" used to push a second Extensions page (and so on), so Back
    // kept re-opening Extensions as if it had been tapped again.
    val navigateSub: (String) -> Unit = remember(navController) {
        { route ->
            runCatching {
                if (!(dev.asfakul.yumeiro.ui.navigation.SubNav.reusable(route) && navController.popBackStack(route, inclusive = false))) {
                    navController.navigate(route) { launchSingleTop = true }
                }
            }
        }
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
    // Music modes: on device · online (YouTube Music) · mix of both. Saved as a key string.
    // Round 13: Online when there is internet, On device when not, switching by itself as the
    // connection changes. Once the user picks a mode it sticks, except that losing the connection
    // always leaves Online for On device (Online cannot play anything offline).
    var musicModePicked by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var musicModeKey by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(dev.asfakul.yumeiro.ui.screens.music.MusicMode.defaultFor(dev.asfakul.yumeiro.network.Connectivity.isOnline(context)).key)
    }
    LaunchedEffect(Unit) {
        dev.asfakul.yumeiro.network.Connectivity.online.collect { online ->
            musicModeKey = dev.asfakul.yumeiro.ui.screens.music.MusicMode.follow(
                dev.asfakul.yumeiro.ui.screens.music.MusicMode.of(musicModeKey), online, musicModePicked,
            ).key
        }
    }
    val musicMode = dev.asfakul.yumeiro.ui.screens.music.MusicMode.of(musicModeKey)
    val musicOnline = musicMode == dev.asfakul.yumeiro.ui.screens.music.MusicMode.ONLINE
    // Which tab's source menu is open (Music or Videos route), null = closed.
    var sourceMenuFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(currentRoute) { if (sourceMenuFor != null && sourceMenuFor != currentRoute) sourceMenuFor = null }
    fun openMusic(mode: dev.asfakul.yumeiro.ui.screens.music.MusicMode) { musicModeKey = mode.key; navigateTop(Screen.Music.route) }
    fun openVideos(source: dev.asfakul.yumeiro.ui.screens.online.VideoSource) { videoSourcePicked = true; videoSource = source; navigateTop(Screen.Videos.route) }
    // First run: extensions stay asleep until the welcome screen / tour is finished or skipped.
    // Decided before any screen can call ExtensionManager.start.
    val firstRunAtLaunch = remember { dev.asfakul.yumeiro.ui.screens.extras.SetupGuide.shouldAutoShow(context).also { dev.asfakul.yumeiro.online.OnboardingGate.begin(it) } }
    // Online extensions: load installed ones, then sync updates in the background (like stream playlists).
    LaunchedEffect(Unit) {
        // Round 12: tell the user what the last restore / reset did (applied before this screen).
        dev.asfakul.yumeiro.backup.BackupMeta.takeNote(context)?.let { dev.asfakul.yumeiro.ui.components.AppMessages.success(context, it) }
        ExtensionManager.start(context)
        if (Build.FINGERPRINT == "robolectric") return@LaunchedEffect
        // A new user first finishes (or skips) the welcome screen and tour: no installs, syncs or
        // "extension updated" messages over it.
        dev.asfakul.yumeiro.online.OnboardingGate.awaitOpen()
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
    val sourceExtCount = dev.asfakul.yumeiro.online.ExtensionManager.state.collectAsStateWithLifecycle().value.installed.count { it.enabled }
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

    val offlineExits = remember {
        dev.asfakul.yumeiro.ui.components.OfflineExits(
            deviceMusic = { openMusic(dev.asfakul.yumeiro.ui.screens.music.MusicMode.DEVICE) },
            deviceVideos = { openVideos(dev.asfakul.yumeiro.ui.screens.online.VideoSource.DEVICE) },
            downloads = { navigateSub(Screen.Downloads.route) },
            library = { navigateTop(Screen.Library.route) },
        )
    }
    val reducedMotionOn = remember { dev.asfakul.yumeiro.ui.components.isReducedMotion(context) }
    val ambientColor = dev.asfakul.yumeiro.ui.theme.rememberAmbientColor(playbackState.currentTrack?.albumArtUri)
    androidx.compose.runtime.CompositionLocalProvider(
        dev.asfakul.yumeiro.ui.components.LocalIsPlaying provides playbackState.isPlaying,
        dev.asfakul.yumeiro.ui.components.LocalCurrentTrackId provides playbackState.currentTrack?.id,
        dev.asfakul.yumeiro.ui.components.LocalReducedMotion provides reducedMotionOn,
        dev.asfakul.yumeiro.ui.components.LocalOfflineExits provides offlineExits,
        dev.asfakul.yumeiro.ui.theme.LocalAmbientColor provides ambientColor,
    ) {
    // The tab chrome (top bar + dock) belongs to the tab layer, not to the Scaffold: it slides out
    // together with the tab page when a sub-page opens and comes back with it on Back. Before, the
    // bars vanished the instant the route changed, so the outgoing tab (e.g. Videos → Yumix) lost
    // its header, jumped to full screen and only then gave way to Extensions. [tabRoute] freezes the
    // last tab so the leaving bars keep their title while they animate out.
    val lastTabRoute = remember { mutableStateOf(currentRoute) }
    if (!isSubpage && lastTabRoute.value != currentRoute) lastTabRoute.value = currentRoute
    val tabRoute = lastTabRoute.value
    var tabTopH by remember { mutableStateOf(0.dp) }
    var tabBottomH by remember { mutableStateOf(0.dp) }
    val tabTopBar: @Composable () -> Unit = {
                val kanaOn = dev.asfakul.yumeiro.ui.components.kanaEnabled()
                run {
                    MTTopBar(
                        // Home keeps the signature mark; Music and Videos show their source as the title.
                        title = when (tabRoute) {
                            Screen.Home.route -> greetingForNow()
                            Screen.Music.route -> "Music"
                            Screen.Videos.route -> "Videos"
                            else -> topBarTitle
                        },
                        sourceLabel = when (tabRoute) {
                            Screen.Music.route -> musicSourceTitle(musicMode)
                            Screen.Videos.route -> videoSource.label
                            else -> null
                        },
                        overline = when (tabRoute) {
                            Screen.Home.route -> "夢色 · YUMEIRO"
                            Screen.Music.route -> dev.asfakul.yumeiro.ui.components.Kana.overline("音楽", "LISTEN", kanaOn)
                            Screen.Videos.route -> dev.asfakul.yumeiro.ui.components.Kana.overline("映像", "WATCH", kanaOn)
                            else -> dev.asfakul.yumeiro.ui.components.Kana.overline(dev.asfakul.yumeiro.ui.components.Kana.forTitle(topBarTitle), null, kanaOn)
                        },
                        onTitleClick = if (tabRoute == Screen.Music.route || tabRoute == Screen.Videos.route) {
                            { sourceMenuFor = if (sourceMenuFor == tabRoute) null else tabRoute }
                        } else null,
                        titleExpanded = sourceMenuFor == tabRoute,
                        titleTag = when (tabRoute) { Screen.Music.route -> "music_source_switch"; Screen.Videos.route -> "video_source_switch"; else -> null },
                        searchQuery = searchQuery,
                        onSearchQueryChange = viewModel::onSearchQueryChange,
                        suggestions = topBarSuggestions,
                        onSubmit = { q -> dev.asfakul.yumeiro.ui.components.RecentSearches.add(context, "library", q); recentTick++ },
                        onSettingsClick = { navigateSub(Screen.Settings.route) },
                        onInboxClick = { dev.asfakul.yumeiro.ui.screens.extras.AppPages.open(dev.asfakul.yumeiro.ui.screens.extras.AppPages.Page.INBOX) },
                        unreadCount = inboxUnread,
                        // Videos (Hues / YouTube / device) and Stream have their own search box, so does online Music.
                        // One search entry per tab, always the magnifier up here (no fixed boxes on the page).
                        showSearch = when (tabRoute) {
                            Screen.Stream.route -> false
                            Screen.Videos.route -> videoSource != dev.asfakul.yumeiro.ui.screens.online.VideoSource.LIVE
                            Screen.Music.route -> true
                            else -> true
                        },
                        onSearchClick = when {
                            tabRoute == Screen.Videos.route && videoSource == dev.asfakul.yumeiro.ui.screens.online.VideoSource.ONLINE -> ({ navigateSub(Screen.OnlineSearch.route) })
                            tabRoute == Screen.Videos.route && videoSource == dev.asfakul.yumeiro.ui.screens.online.VideoSource.YOUTUBE -> ({ dev.asfakul.yumeiro.ui.components.TabSearch.toggle(dev.asfakul.yumeiro.ui.components.TabSearch.YT_VIDEO) })
                            tabRoute == Screen.Music.route && musicMode == dev.asfakul.yumeiro.ui.screens.music.MusicMode.ONLINE -> ({ dev.asfakul.yumeiro.ui.components.TabSearch.toggle(dev.asfakul.yumeiro.ui.components.TabSearch.YT_MUSIC) })
                            tabRoute == Screen.Music.route && musicMode == dev.asfakul.yumeiro.ui.screens.music.MusicMode.MIX -> ({ dev.asfakul.yumeiro.ui.components.TabSearch.toggle(dev.asfakul.yumeiro.ui.components.TabSearch.MUSIC_MIX) })
                            else -> null
                        },
                        searchDescription = when {
                            tabRoute == Screen.Videos.route && videoSource == dev.asfakul.yumeiro.ui.screens.online.VideoSource.ONLINE -> "Search every extension"
                            tabRoute == Screen.Videos.route && videoSource == dev.asfakul.yumeiro.ui.screens.online.VideoSource.YOUTUBE -> "Search YouTube"
                            tabRoute == Screen.Music.route && musicMode == dev.asfakul.yumeiro.ui.screens.music.MusicMode.ONLINE -> "Search YouTube Music"
                            tabRoute == Screen.Music.route && musicMode == dev.asfakul.yumeiro.ui.screens.music.MusicMode.MIX -> "Search your songs and YouTube Music"
                            tabRoute == Screen.Videos.route -> "Search videos on this phone"
                            else -> "Search your library"
                        },
                        offline = !isOnline,
                    )
                }
    }
    val tvLayout = dev.asfakul.yumeiro.ui.tv.LocalTvMode.current
    val onBarNavigate: (String) -> Unit = { route ->
        val shortcut = dev.asfakul.yumeiro.ui.navigation.NavPrefs.Shortcut.of(route.removePrefix(dev.asfakul.yumeiro.ui.navigation.NavPrefs.SHORTCUT_PREFIX))
        when (if (route.startsWith(dev.asfakul.yumeiro.ui.navigation.NavPrefs.SHORTCUT_PREFIX)) shortcut else null) {
            null -> navigateTop(route)
            dev.asfakul.yumeiro.ui.navigation.NavPrefs.Shortcut.MANGA -> navigateSub(Screen.Manga.route)
            dev.asfakul.yumeiro.ui.navigation.NavPrefs.Shortcut.LIVE -> openVideos(dev.asfakul.yumeiro.ui.screens.online.VideoSource.LIVE)
            dev.asfakul.yumeiro.ui.navigation.NavPrefs.Shortcut.DOWNLOADS -> navigateSub(Screen.Downloads.route)
            dev.asfakul.yumeiro.ui.navigation.NavPrefs.Shortcut.EXTENSIONS -> navigateSub(Screen.Extensions.route)
            dev.asfakul.yumeiro.ui.navigation.NavPrefs.Shortcut.SOURCES -> navigateSub(Screen.SourcesHub.route(0))
            dev.asfakul.yumeiro.ui.navigation.NavPrefs.Shortcut.YOUTUBE -> openVideos(dev.asfakul.yumeiro.ui.screens.online.VideoSource.YOUTUBE)
            dev.asfakul.yumeiro.ui.navigation.NavPrefs.Shortcut.SEARCH -> navigateSub(Screen.OnlineSearch.route)
            dev.asfakul.yumeiro.ui.navigation.NavPrefs.Shortcut.WEBSITES -> navigateSub(Screen.WebSites.route)
            dev.asfakul.yumeiro.ui.navigation.NavPrefs.Shortcut.YUMIX -> openVideos(dev.asfakul.yumeiro.ui.screens.online.VideoSource.ONLINE)
            dev.asfakul.yumeiro.ui.navigation.NavPrefs.Shortcut.YTMUSIC -> openMusic(dev.asfakul.yumeiro.ui.screens.music.MusicMode.ONLINE)
            dev.asfakul.yumeiro.ui.navigation.NavPrefs.Shortcut.MIX -> openMusic(dev.asfakul.yumeiro.ui.screens.music.MusicMode.MIX)
            dev.asfakul.yumeiro.ui.navigation.NavPrefs.Shortcut.DEVICE_VIDEOS -> openVideos(dev.asfakul.yumeiro.ui.screens.online.VideoSource.DEVICE)
            dev.asfakul.yumeiro.ui.navigation.NavPrefs.Shortcut.MY_LIST -> navigateSub(Screen.OnlineLibrary.route)
            dev.asfakul.yumeiro.ui.navigation.NavPrefs.Shortcut.HISTORY -> navigateSub(Screen.OnlineHistory.route)
            dev.asfakul.yumeiro.ui.navigation.NavPrefs.Shortcut.MARKET -> navigateSub(Screen.ExtensionMarket.route)
            dev.asfakul.yumeiro.ui.navigation.NavPrefs.Shortcut.HEALTH -> navigateSub(Screen.SourceHealth.route)
            dev.asfakul.yumeiro.ui.navigation.NavPrefs.Shortcut.EQUALIZER -> navigateSub(Screen.Equalizer.route)
            dev.asfakul.yumeiro.ui.navigation.NavPrefs.Shortcut.WATCH_TOGETHER -> navigateSub(Screen.WatchTogether.route)
            dev.asfakul.yumeiro.ui.navigation.NavPrefs.Shortcut.INBOX -> dev.asfakul.yumeiro.ui.screens.extras.AppPages.open(dev.asfakul.yumeiro.ui.screens.extras.AppPages.Page.INBOX)
            dev.asfakul.yumeiro.ui.navigation.NavPrefs.Shortcut.SETTINGS -> navigateSub(Screen.Settings.route)
        }
    }
    val tabBottomBar: @Composable () -> Unit = {
                run {
                    // One floating dock: the mini player joins the nav bar while music is playing.
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Column(modifier = Modifier.fillMaxWidth().widthIn(max = dev.asfakul.yumeiro.ui.components.DockMaxWidth)) {
                        // ArchiveTune-style dock: with music playing, the mini player sits right on
                        // the nav bar (small junction corners, rounded outer corners) as one piece.
                        // On the TV layout the rail is the navigation, so the mini player floats alone.
                        val dockJoined = playbackState.currentTrack != null && !tvLayout
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

                        if (!tvLayout) MTBottomBar(
                            currentRoute = tabRoute,
                            items = barItems,
                            onNavigateToRoute = onBarNavigate,
                            joinedWithMiniPlayer = dockJoined,
                        )
                    }
                    }
                }
    }
    Box(modifier = Modifier.fillMaxSize().pageBackground()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .testTag("app_root_scaffold"),
            containerColor = Color.Transparent,
        ) { innerPadding ->
            // Each layer keeps its own padding through the page transition. The bars disappear the
            // moment a sub-page opens; with one shared padding the outgoing tab (e.g. Yumix) jumped
            // to full screen for the length of the slide before Extensions / Sources appeared, and
            // the reverse on Back. Tabs keep the padding they had with bars, sub-pages theirs.
            val layoutDir = androidx.compose.ui.platform.LocalLayoutDirection.current
            val nowPad = PaddingValues(
                innerPadding.calculateStartPadding(layoutDir), innerPadding.calculateTopPadding(),
                innerPadding.calculateEndPadding(layoutDir), innerPadding.calculateBottomPadding(),
            )
            val density = androidx.compose.ui.platform.LocalDensity.current
            val tabNow = PaddingValues(
                innerPadding.calculateStartPadding(layoutDir) + (if (tvLayout) dev.asfakul.yumeiro.ui.tv.TvRailWidth else 0.dp), maxOf(tabTopH, innerPadding.calculateTopPadding()),
                innerPadding.calculateEndPadding(layoutDir), maxOf(tabBottomH, innerPadding.calculateBottomPadding()),
            )
            val tabPad = remember { mutableStateOf(tabNow) }
            val subPad = remember { mutableStateOf(nowPad) }
            if (subPad.value != nowPad) subPad.value = nowPad
            if (tabPad.value != tabNow) tabPad.value = tabNow
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // No tab swipes under the full-screen video or Now Playing.
                val tabSwipeEnabled = (activePlayingVideo == null || videoMinimized) && !isNowPlayingExpanded
                val tabPage: @Composable (Int) -> Unit = { page ->
                    when (pageItems[page].route) {
                        Screen.Home.route -> {
                            val huesEngine by dev.asfakul.yumeiro.online.ExtensionManager.state.collectAsState()
                            val onlineHistoryAll by dev.asfakul.yumeiro.online.OnlineHistory.entries.collectAsState()
                            val adultOn by dev.asfakul.yumeiro.online.OnlineSettings.adult.collectAsState()
                            val adultPrivate by dev.asfakul.yumeiro.online.OnlineSettings.adultPrivate.collectAsState()
                            val onlineHistory = remember(onlineHistoryAll, adultOn, adultPrivate) { dev.asfakul.yumeiro.online.OnlineHistory.forFeed(onlineHistoryAll, adultOn, adultPrivate) }
                            val ytHistoryItems by dev.asfakul.yumeiro.youtube.YtHistory.items.collectAsState()
                            val ytAt by dev.asfakul.yumeiro.player.LastPlayed.ytAt.collectAsState()
                            val homeResumeVideo = remember(recentlyWatchedVideos, continueWatchingVideos, onlineHistory, ytHistoryItems, ytAt) {
                                val device = (continueWatchingVideos + recentlyWatchedVideos).maxByOrNull { it.lastWatchedTimestamp }
                                    ?.takeIf { it.lastWatchedTimestamp > 0 }
                                val online = onlineHistory.firstOrNull()
                                val yt = ytHistoryItems.firstOrNull { !it.music }
                                // Round 13: whichever was really watched last wins (YouTube included),
                                // as in 1.5 where only device and online videos existed.
                                val deviceAt = device?.lastWatchedTimestamp ?: 0L
                                val onlineAt = online?.updatedAt ?: 0L
                                val ytLast = yt?.let { ytAt[it.id] } ?: 0L
                                when {
                                    yt != null && ytLast > 0 && ytLast > deviceAt && ytLast > onlineAt -> dev.asfakul.yumeiro.ui.screens.home.ResumeVideo(
                                        title = yt.title, subtitle = "YouTube · ${yt.channel}", thumbnail = yt.thumbnail,
                                        onClick = { dev.asfakul.yumeiro.youtube.YouTubePlayback.playVideo(context, yt) },
                                    )
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
                                onNavigateToMusic = { openMusic(dev.asfakul.yumeiro.ui.screens.music.MusicMode.DEVICE) },
                                onNavigateToVideos = { videoSourcePicked = true; videoSource = dev.asfakul.yumeiro.ui.screens.online.VideoSource.DEVICE; navigateTop(Screen.Videos.route) },
                                onOpenYouTubeMusic = { openMusic(dev.asfakul.yumeiro.ui.screens.music.MusicMode.ONLINE) },
                                onOpenMix = { openMusic(dev.asfakul.yumeiro.ui.screens.music.MusicMode.MIX) },
                                onOpenShortcut = { sc ->
                                    fun settings(c: dev.asfakul.yumeiro.ui.screens.settings.SettingsCatalog.Category) {
                                        dev.asfakul.yumeiro.ui.screens.settings.SettingsLaunch.request(c)
                                        navigateSub(Screen.Settings.route)
                                    }
                                    when (sc) {
                                        dev.asfakul.yumeiro.ui.screens.home.HomeShortcut.SEARCH -> navigateSub(Screen.OnlineSearch.route)
                                        dev.asfakul.yumeiro.ui.screens.home.HomeShortcut.MY_LIST -> navigateSub(Screen.OnlineLibrary.route)
                                        dev.asfakul.yumeiro.ui.screens.home.HomeShortcut.HISTORY -> navigateSub(Screen.OnlineHistory.route)
                                        dev.asfakul.yumeiro.ui.screens.home.HomeShortcut.MARKET -> navigateSub(Screen.ExtensionMarket.route)
                                        dev.asfakul.yumeiro.ui.screens.home.HomeShortcut.WEBSITES -> navigateSub(Screen.WebSites.route)
                                        dev.asfakul.yumeiro.ui.screens.home.HomeShortcut.MIX -> openMusic(dev.asfakul.yumeiro.ui.screens.music.MusicMode.MIX)
                                        dev.asfakul.yumeiro.ui.screens.home.HomeShortcut.EQUALIZER -> navigateSub(Screen.Equalizer.route)
                                        dev.asfakul.yumeiro.ui.screens.home.HomeShortcut.SOURCE_HEALTH -> navigateSub(Screen.SourceHealth.route)
                                        dev.asfakul.yumeiro.ui.screens.home.HomeShortcut.TOGETHER -> navigateSub(Screen.WatchTogether.route)
                                        dev.asfakul.yumeiro.ui.screens.home.HomeShortcut.THEME -> settings(dev.asfakul.yumeiro.ui.screens.settings.SettingsCatalog.Category.APPEARANCE)
                                        dev.asfakul.yumeiro.ui.screens.home.HomeShortcut.DNS, dev.asfakul.yumeiro.ui.screens.home.HomeShortcut.NETWORK -> settings(dev.asfakul.yumeiro.ui.screens.settings.SettingsCatalog.Category.NETWORK)
                                        dev.asfakul.yumeiro.ui.screens.home.HomeShortcut.TRACKING -> navigateSub(Screen.Tracking.route)
                                        dev.asfakul.yumeiro.ui.screens.home.HomeShortcut.PLAYER -> settings(dev.asfakul.yumeiro.ui.screens.settings.SettingsCatalog.Category.PLAYER)
                                        dev.asfakul.yumeiro.ui.screens.home.HomeShortcut.NAV_BAR -> settings(dev.asfakul.yumeiro.ui.screens.settings.SettingsCatalog.Category.NAVIGATION)
                                        dev.asfakul.yumeiro.ui.screens.home.HomeShortcut.NOTIFICATIONS -> settings(dev.asfakul.yumeiro.ui.screens.settings.SettingsCatalog.Category.NOTIFICATIONS)
                                        dev.asfakul.yumeiro.ui.screens.home.HomeShortcut.PRIVACY -> navigateSub(Screen.Privacy.route)
                                        dev.asfakul.yumeiro.ui.screens.home.HomeShortcut.BACKUP -> navigateSub(Screen.Backup.route)
                                        dev.asfakul.yumeiro.ui.screens.home.HomeShortcut.YOUTUBE_ACCOUNT -> navigateSub(Screen.YouTubeAccount.route)
                                        dev.asfakul.yumeiro.ui.screens.home.HomeShortcut.SETTINGS -> navigateSub(Screen.Settings.route)
                                    }
                                },
                                onOpenManga = { navigateSub(Screen.Manga.route) },
                                onOpenSources = { navigateSub(Screen.SourcesHub.route(0)) },
                                onOpenOnlineTitle = openOnline,
                                onOpenYouTube = { videoSourcePicked = true; videoSource = dev.asfakul.yumeiro.ui.screens.online.VideoSource.YOUTUBE; navigateTop(Screen.Videos.route) },
                                onOpenHues = { videoSourcePicked = true; videoSource = dev.asfakul.yumeiro.ui.screens.online.VideoSource.ONLINE; navigateTop(Screen.Videos.route) },
                                onOpenLive = { openVideos(dev.asfakul.yumeiro.ui.screens.online.VideoSource.LIVE) },
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
                                onResumeTrack = { t, at ->
                                    val q = recentlyPlayed.let { r -> if (r.any { it.id == t.id }) r else listOf(t) }
                                    viewModel.playTrack(t, q, at)
                                },
                                onPlayYouTube = { item, list ->
                                    if (item.music) {
                                        dev.asfakul.yumeiro.youtube.YtHistory.add(context, item)
                                        val queue = list.map { dev.asfakul.yumeiro.youtube.YouTubePlayback.toTrack(it) }
                                        viewModel.playTrack(dev.asfakul.yumeiro.youtube.YouTubePlayback.toTrack(item), queue)
                                    } else {
                                        dev.asfakul.yumeiro.youtube.YouTubePlayback.playVideo(context, item)
                                    }
                                },
                                onOpenRow = { row -> navigateSub(Screen.OnlineRow.route(row.api, row.title, row.pageName.orEmpty(), row.pageData.orEmpty())) },
                            )
                            }
                        }
                        Screen.Music.route -> {
                            OpaquePage {
                            Column(Modifier.fillMaxSize()) {
                            dev.asfakul.yumeiro.ui.components.FadeThrough(musicMode, Modifier.weight(1f), label = "music_mode") { mm ->
                            if (mm == dev.asfakul.yumeiro.ui.screens.music.MusicMode.MIX) {
                                dev.asfakul.yumeiro.ui.screens.music.MusicMixScreen(
                                    tracks = filteredTracks,
                                    mostPlayed = mostPlayed,
                                    recentlyPlayed = recentlyPlayed,
                                    currentTrackId = playbackState.currentTrack?.id,
                                    onPlayTrack = { track, queue -> viewModel.playTrack(track, queue) },
                                    onPlayOnline = { item, list ->
                                        dev.asfakul.yumeiro.youtube.YtHistory.add(context, item.copy(music = true))
                                        val queue = list.map { dev.asfakul.yumeiro.youtube.YouTubePlayback.toTrack(it) }
                                        viewModel.playTrack(dev.asfakul.yumeiro.youtube.YouTubePlayback.toTrack(item), queue)
                                    },
                                    onOpenDevice = { musicModeKey = dev.asfakul.yumeiro.ui.screens.music.MusicMode.DEVICE.key },
                                    onOpenOnline = { musicModeKey = dev.asfakul.yumeiro.ui.screens.music.MusicMode.ONLINE.key },
                                )
                            } else if (mm == dev.asfakul.yumeiro.ui.screens.music.MusicMode.ONLINE) {
                                Column(Modifier.fillMaxSize()) {
                                if (!isOnline) dev.asfakul.yumeiro.ui.components.OfflineNotice("YouTube Music", tag = "offline_notice_music")
                                dev.asfakul.yumeiro.ui.screens.youtube.YouTubeMusicScreen(onPlay = { item, list ->
                                    dev.asfakul.yumeiro.youtube.YtHistory.add(context, item.copy(music = true))
                                    val queue = list.map { dev.asfakul.yumeiro.youtube.YouTubePlayback.toTrack(it) }
                                    viewModel.playTrack(dev.asfakul.yumeiro.youtube.YouTubePlayback.toTrack(item), queue)
                                })
                                }
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
                        }
                        Screen.Videos.route -> {
                            OpaquePage {
                            Column(Modifier.fillMaxSize()) {
                            val extState by dev.asfakul.yumeiro.online.ExtensionManager.state.collectAsState()
                            if (!isOnline && videoSource != dev.asfakul.yumeiro.ui.screens.online.VideoSource.DEVICE) {
                                dev.asfakul.yumeiro.ui.components.OfflineNotice(
                                    when (videoSource) {
                                        dev.asfakul.yumeiro.ui.screens.online.VideoSource.LIVE -> "Live TV"
                                        dev.asfakul.yumeiro.ui.screens.online.VideoSource.YOUTUBE -> "YouTube"
                                        else -> "Yumix"
                                    },
                                    tag = "offline_notice_videos",
                                )
                            }
                            // Round 8: the source switch fades through instead of cutting to the new page.
                            dev.asfakul.yumeiro.ui.components.FadeThrough(videoSource, Modifier.weight(1f), label = "video_source") { vs ->
                            if (vs == dev.asfakul.yumeiro.ui.screens.online.VideoSource.LIVE) {
                                StreamScreen(
                                    onOpenPlaylist = { id -> navigateSub(Screen.PlaylistDetail.route(id)) },
                                    onOpenMarket = { navigateSub(Screen.Market.route) },
                                    onOpenWebsites = { navigateSub(Screen.WebSites.route) },
                                )
                            } else if (vs == dev.asfakul.yumeiro.ui.screens.online.VideoSource.YOUTUBE) {
                                dev.asfakul.yumeiro.ui.screens.youtube.YouTubeVideoScreen(
                                    onPlay = { dev.asfakul.yumeiro.youtube.YouTubePlayback.playVideo(context, it) },
                                )
                            } else if (vs == dev.asfakul.yumeiro.ui.screens.online.VideoSource.ONLINE) {
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
                        }
                        Screen.Library.route -> {
                            OpaquePage {
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
                                // Round 10: no more Music | Videos | Reading tabs — shortcuts open them.
                                onOpenVideos = { navigateSub(Screen.OnlineLibrary.route) },
                                onOpenReading = { navigateSub(Screen.ReadingLibrary.route) },
                                onOpenHistory = { navigateSub(Screen.OnlineHistory.route) },
                                onOpenDownloads = { navigateSub(Screen.Downloads.route) },
                                onOpenTogether = { navigateSub(Screen.WatchTogether.route) },
                            )
                            }
                        }
                    }
                }
                NavHost(
                    navController = navController,
                    startDestination = Screen.Home.route,
                    // Pages are opaque and slide (no cross-fade of two half-transparent pages, which
                    // read as two pages mixed together): a sub-page comes in from the right over the
                    // page below, which drifts a little to the left; Back reverses it. Switching
                    // tabs happens inside the pager, so it never runs these.
                    // Round 8: shared push / pop motion (PageMotion). 420 ms emphasized push with the page
                    // underneath drifting and dimming a little; Back is quicker (320 ms).
                    enterTransition = { dev.asfakul.yumeiro.ui.components.PageMotion.push(reducedMotionOn) },
                    exitTransition = { dev.asfakul.yumeiro.ui.components.PageMotion.pushExit(reducedMotionOn) },
                    popEnterTransition = { dev.asfakul.yumeiro.ui.components.PageMotion.popEnter(reducedMotionOn) },
                    popExitTransition = { dev.asfakul.yumeiro.ui.components.PageMotion.popExit(reducedMotionOn) },
                    sizeTransform = null
                ) {
                    // The five bottom-bar tabs live in one pager: swipe anywhere on a page (rows and
                    // carousels keep their own drags first), the page follows the finger like Facebook / Instagram.
                    composable(Screen.Home.route) {
                        // One opaque layer: page background, the tab pager and the tab chrome
                        // (top bar, dock), so the whole tab moves as one piece in page transitions.
                        Box(Modifier.fillMaxSize().pageBackground()) {
                            HorizontalPager(
                                state = tabPager,
                                userScrollEnabled = tabSwipeEnabled,
                                key = { pageItems[it].route },
                                beyondViewportPageCount = 0,
                                modifier = Modifier.fillMaxSize().padding(tabPad.value).consumeWindowInsets(tabPad.value)
                                    .graphicsLayer { alpha = tabFade.value; translationX = tabShift.value * 28.dp.toPx() }
                                    .testTag("tab_pager"),
                            ) { page -> tabPage(page) }
                            val railPad = if (tvLayout) dev.asfakul.yumeiro.ui.tv.TvRailWidth else 0.dp
                            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(start = railPad).onSizeChanged { with(density) { it.height.toDp() }.let { h -> if (h != tabTopH) tabTopH = h } }) { tabTopBar() }
                            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = railPad).onSizeChanged { with(density) { it.height.toDp() }.let { h -> if (h != tabBottomH) tabBottomH = h } }) { tabBottomBar() }
                            if (tvLayout) dev.asfakul.yumeiro.ui.tv.TvNavRail(
                                currentRoute = tabRoute,
                                items = barItems,
                                onNavigate = onBarNavigate,
                                onSettings = { navigateSub(Screen.Settings.route) },
                                modifier = Modifier.align(Alignment.CenterStart),
                            )
                        }
                    }
                    // Old direct routes of the tabs (deep links, saved state) land on the pager page.
                    composable(Screen.Music.route) { LaunchedEffect(Unit) { navigateTop(Screen.Music.route) } }
                    composable(Screen.Videos.route) { LaunchedEffect(Unit) { navigateTop(Screen.Videos.route) } }
                    composable(Screen.Stream.route) { LaunchedEffect(Unit) { openVideos(dev.asfakul.yumeiro.ui.screens.online.VideoSource.LIVE) } }
                    composable(Screen.Library.route) { LaunchedEffect(Unit) { navigateTop(Screen.Library.route) } }

                    page(subPad, Screen.OnlineSearch.route) {
                        OpaquePage {
                        OnlineSearchScreen(
                            onBack = popSub,
                            onOpenTitle = openOnline,
                            onOpenExtensions = { navigateSub(Screen.Extensions.route) },
                        )
                        }
                    }
                    page(
                        subPad,
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
                    page(
                        subPad,
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
                    page(subPad, Screen.OnlineHistory.route) {
                        OpaquePage {
                        dev.asfakul.yumeiro.ui.screens.online.OnlineHistoryScreen(onBack = popSub, onOpenTitle = openOnline)
                        }
                    }
                    page(subPad, Screen.Extensions.route) {
                        OpaquePage {
                        ExtensionsScreen(onBack = popSub, onOpenMarket = { navigateSub(Screen.ExtensionMarket.route) }, onOpenSources = { navigateSub(Screen.SourcesHub.route()) })
                        }
                    }
                    page(subPad, Screen.ExtensionMarket.route) {
                        OpaquePage {
                        ExtensionMarketScreen(onBack = popSub, onOpenExtensions = { navigateSub(Screen.Extensions.route) })
                        }
                    }

                    page(subPad, Screen.SourcesHub.route, arguments = listOf(navArgument(Screen.SourcesHub.ARG) { type = NavType.IntType; defaultValue = 0 })) { entry ->
                        OpaquePage { dev.asfakul.yumeiro.ui.screens.sources.SourcesHubScreen(onBack = popSub, initialTab = entry.arguments?.getInt(Screen.SourcesHub.ARG) ?: 0) }
                    }
                    page(subPad, Screen.SourceHealth.route) {
                        OpaquePage { dev.asfakul.yumeiro.ui.screens.sources.SourceHealthScreen(onBack = popSub) }
                    }
                    page(subPad, Screen.Manga.route) {
                        OpaquePage {
                            dev.asfakul.yumeiro.ui.screens.manga.MangaHomeScreen(
                                onBack = popSub,
                                onOpenTitle = { navigateSub(Screen.MangaTitle.route(it.providerId, it.url, it.title, it.cover)) },
                                onOpenSources = { navigateSub(Screen.SourcesHub.route(1)) },
                            )
                        }
                    }
                    page(subPad, Screen.WebSites.route) {
                        OpaquePage { dev.asfakul.yumeiro.ui.screens.sites.WebSitesScreen(onBack = popSub) }
                    }
                    page(subPad, Screen.MangaTitle.route, arguments = listOf("p", "u", "t", "i").map { a -> navArgument(a) { type = NavType.StringType; defaultValue = "" } }) { entry ->
                        val a = entry.arguments
                        OpaquePage {
                            dev.asfakul.yumeiro.ui.screens.manga.MangaDetailScreen(
                                providerId = a?.getString("p").orEmpty(), url = a?.getString("u").orEmpty(), title = a?.getString("t").orEmpty(),
                                cover = a?.getString("i")?.takeIf { it.isNotBlank() },
                                onBack = popSub,
                                onRead = { m, ch -> navigateSub(Screen.MangaReader.route(m.providerId, m.url, m.title, ch.url, ch.name)) },
                            )
                        }
                    }
                    page(subPad, Screen.MangaReader.route, arguments = listOf("p", "u", "t", "c", "n").map { a -> navArgument(a) { type = NavType.StringType; defaultValue = "" } }) { entry ->
                        val a = entry.arguments
                        OpaquePage {
                            dev.asfakul.yumeiro.ui.screens.manga.MangaReaderScreen(
                                providerId = a?.getString("p").orEmpty(), mangaUrl = a?.getString("u").orEmpty(), mangaTitle = a?.getString("t").orEmpty(),
                                chapterUrl = a?.getString("c").orEmpty(), chapterName = a?.getString("n").orEmpty(), onBack = popSub,
                            )
                        }
                    }
                    page(subPad, Screen.Privacy.route) {
                        OpaquePage { dev.asfakul.yumeiro.ui.screens.settings.PrivacyScreen(onBack = popSub) }
                    }
                    page(subPad, Screen.Tracking.route) {
                        OpaquePage { dev.asfakul.yumeiro.ui.screens.settings.TrackingScreen(onBack = popSub) }
                    }
                    page(subPad, Screen.YouTubeAccount.route) {
                        OpaquePage { dev.asfakul.yumeiro.ui.screens.settings.YouTubeAccountScreen(onBack = popSub) }
                    }
                    page(subPad, Screen.Backup.route) {
                        OpaquePage { dev.asfakul.yumeiro.ui.screens.settings.BackupScreen(onBack = popSub) }
                    }
                    page(subPad, Screen.WatchTogether.route) {
                        OpaquePage { dev.asfakul.yumeiro.ui.screens.watch.WatchTogetherScreen(onBack = popSub) }
                    }

                    page(subPad, Screen.Downloads.route) {
                        OpaquePage {
                            dev.asfakul.yumeiro.ui.screens.downloads.DownloadsScreen(
                                onBack = popSub,
                                onPlayVideo = viewModel::playVideo,
                                onBrowse = { videoSourcePicked = true; videoSource = dev.asfakul.yumeiro.ui.screens.online.VideoSource.ONLINE; navigateTop(Screen.Videos.route) },
                            )
                        }
                    }

                    page(subPad, Screen.OnlineLibrary.route) {
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
                                    // The old Library "Videos" tab header (stats + History / Downloads /
                                    // Watch together / Sources) moved here with the tab.
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
                                )
                            }
                        }
                    }

                    page(subPad, Screen.ReadingLibrary.route) {
                        OpaquePage {
                            Column(Modifier.fillMaxSize()) {
                                dev.asfakul.yumeiro.ui.screens.library.LibraryTopBar("Reading", onBack = popSub)
                                dev.asfakul.yumeiro.ui.screens.library.ReadingLibraryContent(
                                    onBrowse = { navigateSub(Screen.Manga.route) },
                                    onOpenTitle = { navigateSub(Screen.MangaTitle.route(it.providerId, it.url, it.title, it.cover)) },
                                    onOpenSources = { navigateSub(Screen.SourcesHub.route(1)) },
                                )
                            }
                        }
                    }

                    page(
                        subPad,
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

                    page(subPad, Screen.Market.route) {
                        OpaquePage {
                        PlaylistMarketScreen(
                            onBack = popSub,
                            onOpenPlaylist = { id -> navigateSub(Screen.PlaylistDetail.route(id)) },
                        )
                        }
                    }

                    page(subPad, Screen.Settings.route) {
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
                                    "youtube" -> Screen.YouTubeAccount.route
                                    else -> Screen.Privacy.route
                                })
                            },
                        )
                        }
                    }

                    page(subPad, Screen.Equalizer.route) {
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
                // Source menu of Music / Videos, dropping down from the tab title (round 3).
                val menuOpen = sourceMenuFor != null && sourceMenuFor == currentRoute
                val musicMenu = sourceMenuFor == Screen.Music.route || (sourceMenuFor == null && currentRoute == Screen.Music.route)
                Box(Modifier.fillMaxSize().padding(tabPad.value)) {
                dev.asfakul.yumeiro.ui.components.SourceMenu(
                    visible = menuOpen,
                    heading = if (musicMenu) "Listen from" else "Watch from",
                    options = if (musicMenu) dev.asfakul.yumeiro.ui.screens.music.musicSourceOptions(stats.audioCount, isOnline)
                    else dev.asfakul.yumeiro.ui.screens.online.videoSourceOptions(sourceExtCount, stats.videoCount, isOnline),
                    selectedKey = if (musicMenu) musicMode.key else videoSource.tag,
                    onSelect = { key ->
                        if (musicMenu) { musicModeKey = dev.asfakul.yumeiro.ui.screens.music.MusicMode.of(key).key; musicModePicked = true }
                        else dev.asfakul.yumeiro.ui.screens.online.VideoSource.entries.firstOrNull { it.tag == key }?.let { videoSourcePicked = true; videoSource = it }
                        sourceMenuFor = null
                    },
                    onDismiss = { sourceMenuFor = null },
                    menuTag = if (musicMenu) "music_source_menu" else "video_source_menu",
                )
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
                onShowDetails = { trackForDetailsDialog = track },
                onDownload = if (dev.asfakul.yumeiro.online.download.SongDownloads.fromTrack(track) != null) ({
                    dev.asfakul.yumeiro.online.download.SongDownloads.ask(track)
                }) else null,
            )
        }

        // One "Download song" sheet for every Music surface (Online, Mix, Now Playing options).
        val songDownload by dev.asfakul.yumeiro.online.download.SongDownloads.request.collectAsState()
        songDownload?.let { song ->
            dev.asfakul.yumeiro.ui.screens.online.YouTubeDownloadSheet(song) { dev.asfakul.yumeiro.online.download.SongDownloads.dismiss() }
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

        // One handler for in-app actions from the inbox and from tapped notifications (deep links).
        val runInboxAction: (String) -> Unit = { link ->
            when (link) {
                "update" -> dev.asfakul.yumeiro.update.UpdateManager.get(context).showDialog()
                "extensions" -> navigateSub(Screen.Extensions.route)
                "market" -> navigateSub(Screen.ExtensionMarket.route)
                "streams" -> navigateSub(Screen.Market.route)
                "downloads" -> dev.asfakul.yumeiro.ui.screens.downloads.DownloadsNav.open()
                "inbox" -> dev.asfakul.yumeiro.ui.screens.extras.AppPages.open(dev.asfakul.yumeiro.ui.screens.extras.AppPages.Page.INBOX)
                "support" -> dev.asfakul.yumeiro.ui.screens.extras.AppPages.open(dev.asfakul.yumeiro.ui.screens.extras.AppPages.Page.SUPPORT)
            }
        }
        val pendingAction by dev.asfakul.yumeiro.ui.screens.extras.AppPages.action.collectAsState()
        LaunchedEffect(pendingAction, showSplash) {
            if (pendingAction != null && !showSplash) dev.asfakul.yumeiro.ui.screens.extras.AppPages.consumeAction()?.let(runInboxAction)
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
                    onAction = { link -> closePage(); runInboxAction(link) },
                )
            }
        }

        // Offline: a slim banner under the status bar; local music and videos keep working.
        // Hidden over full-screen views (Now Playing, pages) so it never covers their top buttons.
        AnimatedVisibility(
            // Tabs show "Offline" inside their header; the floating pill is only for sub-pages.
            visible = !isOnline && isSubpage && !showSplash && activePlayingVideo == null && appPage == null &&
                !(isNowPlayingExpanded && playbackState.currentTrack != null),
            enter = fadeIn(tween(200)), exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) { dev.asfakul.yumeiro.ui.components.OfflineBanner() }

        // First-run setup guide (skippable; reopened from Home's setup card).
        val setupOpen by dev.asfakul.yumeiro.ui.screens.extras.SetupGuide.open.collectAsState()
        LaunchedEffect(showSplash) {
            if (!showSplash && dev.asfakul.yumeiro.ui.screens.extras.SetupGuide.shouldAutoShow(context)) dev.asfakul.yumeiro.ui.screens.extras.SetupGuide.show()
        }
        // Popups take turns: never during splash, video, PiP, the update dialog or an open page.
        val tourStep by dev.asfakul.yumeiro.ui.screens.extras.ExploreTour.step.collectAsState()
        // Extensions stay asleep while a first-run guide is on screen (see OnboardingGate).
        if (firstRunAtLaunch) {
            LaunchedEffect(showSplash, setupOpen, tourStep) {
                if (!dev.asfakul.yumeiro.online.OnboardingGate.isHeld) return@LaunchedEffect
                if (dev.asfakul.yumeiro.ui.screens.extras.OnboardingPlan.busy(showSplash, setupOpen, tourStep != null)) return@LaunchedEffect
                // Debounced: closing the welcome screen and starting the tour land in separate frames.
                // If the guide never appears (or the user left it), extensions are not blocked forever.
                kotlinx.coroutines.delay(dev.asfakul.yumeiro.ui.screens.extras.OnboardingPlan.RELEASE_DEBOUNCE_MS)
                dev.asfakul.yumeiro.online.OnboardingGate.release()
            }
        }
        val popupBlocked = showSplash || setupOpen || tourStep != null || activePlayingVideo != null || inPip || updateDialogOpen || appPage != null ||
            (isNowPlayingExpanded && playbackState.currentTrack != null)
        dev.asfakul.yumeiro.ui.screens.extras.NotificationPromptHost(blocked = popupBlocked || !startupPermissionsDone)
        dev.asfakul.yumeiro.ui.screens.extras.ReminderHost(blocked = popupBlocked || !startupPermissionsDone)

        // Styled in-app messages (falls back to system toasts when no host is attached).
        dev.asfakul.yumeiro.ui.screens.online.BrowserCheckHost()
        dev.asfakul.yumeiro.ui.screens.online.ExtensionReviewHost()
        dev.asfakul.yumeiro.ui.screens.sources.UniversalAddHost()
        dev.asfakul.yumeiro.ui.screens.downloads.DownloadPromptHost()
        AppMessageHost(bottomPadding = if (isSubpage) androidx.compose.ui.unit.Dp(24f) else androidx.compose.ui.unit.Dp(150f))

        // In-app updater popup (GitHub Releases). Hidden during splash and fullscreen video.
        UpdateHost(enabled = !showSplash && activePlayingVideo == null)

        AnimatedVisibility(visible = setupOpen && !showSplash, enter = fadeIn(tween(220)), exit = fadeOut(tween(200))) {
            Box(
                Modifier.fillMaxSize().clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null,
                ) {}
            ) {
                dev.asfakul.yumeiro.ui.screens.extras.SetupGuideOverlay(onAction = { step ->
                    when (step) {
                        dev.asfakul.yumeiro.ui.screens.extras.SetupStep.MEDIA -> {
                            permissionAskedAt = SystemClock.elapsedRealtime()
                            permissionsLauncher.launch(dev.asfakul.yumeiro.ui.screens.extras.PermissionCenter.mediaPermissions())
                        }
                        dev.asfakul.yumeiro.ui.screens.extras.SetupStep.EXTENSIONS -> navigateSub(Screen.ExtensionMarket.route)
                        dev.asfakul.yumeiro.ui.screens.extras.SetupStep.MANGA -> navigateSub(Screen.SourcesHub.route(1))
                        dev.asfakul.yumeiro.ui.screens.extras.SetupStep.SEARCH -> navigateSub(Screen.OnlineSearch.route)
                        dev.asfakul.yumeiro.ui.screens.extras.SetupStep.DNS -> {
                            dev.asfakul.yumeiro.ui.screens.settings.SettingsLaunch.request(dev.asfakul.yumeiro.ui.screens.settings.SettingsCatalog.Category.NETWORK)
                            navigateSub(Screen.Settings.route)
                        }
                        dev.asfakul.yumeiro.ui.screens.extras.SetupStep.NOTIFICATIONS -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                permissionAskedAt = SystemClock.elapsedRealtime()
                                permissionsLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                            } else {
                                dev.asfakul.yumeiro.ui.screens.settings.SettingsLaunch.request(dev.asfakul.yumeiro.ui.screens.settings.SettingsCatalog.Category.NOTIFICATIONS)
                                navigateSub(Screen.Settings.route)
                            }
                        }
                        dev.asfakul.yumeiro.ui.screens.extras.SetupStep.LIVE -> openVideos(dev.asfakul.yumeiro.ui.screens.online.VideoSource.LIVE)
                        dev.asfakul.yumeiro.ui.screens.extras.SetupStep.EQUALIZER -> navigateSub(Screen.Equalizer.route)
                    }
                })
            }
        }

        // Explore tour: spotlights the real controls; taps on the highlighted one go through.
        if (!showSplash && !setupOpen && activePlayingVideo == null && !(isNowPlayingExpanded && playbackState.currentTrack != null)) {
            dev.asfakul.yumeiro.ui.screens.extras.ExploreTourOverlay(
                currentRoute = currentRoute,
                sourceMenuOpen = sourceMenuFor != null,
                onPrepare = { place ->
                    when (place) {
                        dev.asfakul.yumeiro.ui.screens.extras.TourPlace.HOME -> navigateTop(Screen.Home.route)
                        dev.asfakul.yumeiro.ui.screens.extras.TourPlace.MUSIC -> navigateTop(Screen.Music.route)
                        dev.asfakul.yumeiro.ui.screens.extras.TourPlace.VIDEOS_YUMIX -> openVideos(dev.asfakul.yumeiro.ui.screens.online.VideoSource.ONLINE)
                        dev.asfakul.yumeiro.ui.screens.extras.TourPlace.LIBRARY -> navigateTop(Screen.Library.route)
                    }
                },
                onOpenSetup = { dev.asfakul.yumeiro.ui.screens.extras.SetupGuide.show(checklist = true) },
            )
        }

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
            videoForDetails != null || showManageHiddenFoldersDialog || appPage != null || setupOpen
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
                dev.asfakul.yumeiro.ui.components.AppMessages.show(context, "Press back again to exit")
            }
        }
    }
    }
}

private const val NAV_ANIM_MS = 340
private const val NAV_POP_MS = 280
private const val EXIT_WINDOW_MS = 2_000L

/** A sub-page destination padded by its own (frozen) insets; see the Scaffold content above. */
private fun androidx.navigation.NavGraphBuilder.page(
    pad: androidx.compose.runtime.State<PaddingValues>,
    route: String,
    arguments: List<androidx.navigation.NamedNavArgument> = emptyList(),
    content: @Composable (androidx.navigation.NavBackStackEntry) -> Unit,
) = composable(route, arguments) { entry ->
    Box(Modifier.fillMaxSize().pageBackground().padding(pad.value).consumeWindowInsets(pad.value)) { content(entry) }
}

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

/**
 * Every page that is not one of the bottom-bar tabs is a sub-page: it brings its own header (back,
 * kanji overline, title), so the tab top bar and dock must not stack on top of it. Listing the
 * tabs (not the sub-pages) means a new page can never get two headers again.
 */
private fun isSubRoute(route: String?): Boolean =
    route != null && route !in TabRoutes

private val TabRoutes = setOf(Screen.Home.route, Screen.Music.route, Screen.Videos.route, Screen.Stream.route, Screen.Library.route)
