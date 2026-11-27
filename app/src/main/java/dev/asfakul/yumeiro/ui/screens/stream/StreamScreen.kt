/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.stream

import dev.asfakul.yumeiro.l10n.tr
import androidx.compose.ui.res.stringResource
import dev.asfakul.yumeiro.R
import dev.asfakul.yumeiro.ui.theme.GlassFill
import android.net.Uri
import dev.asfakul.yumeiro.ui.components.AppMessages
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import dev.asfakul.yumeiro.stream.PlaylistStore
import dev.asfakul.yumeiro.stream.LiveChannelFeed
import dev.asfakul.yumeiro.stream.PinnedPlaylists
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import dev.asfakul.yumeiro.ui.screens.online.filterByTitle
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Apps
import dev.asfakul.yumeiro.stream.StreamPlaylist
import dev.asfakul.yumeiro.stream.guessTitle
import kotlinx.coroutines.CancellationException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Stream
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.asfakul.yumeiro.stream.DrmScheme
import dev.asfakul.yumeiro.stream.IncomingMedia
import dev.asfakul.yumeiro.stream.PlatformRules
import dev.asfakul.yumeiro.stream.Resolution
import dev.asfakul.yumeiro.stream.StreamResolver
import dev.asfakul.yumeiro.stream.StreamSniffer
import dev.asfakul.yumeiro.stream.StreamFormat
import dev.asfakul.yumeiro.stream.StreamRepository
import dev.asfakul.yumeiro.stream.StreamRequest
import dev.asfakul.yumeiro.stream.UserAgentPreset
import dev.asfakul.yumeiro.stream.looksLikeM3uPlaylist
import dev.asfakul.yumeiro.stream.parseHeaderLines
import dev.asfakul.yumeiro.ui.theme.DarkBackground
import dev.asfakul.yumeiro.ui.theme.DarkOutline
import dev.asfakul.yumeiro.ui.theme.DarkSurface
import dev.asfakul.yumeiro.ui.theme.DarkSurfaceContainerHigh
import dev.asfakul.yumeiro.ui.theme.OnAccent
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import kotlinx.coroutines.launch
import dev.asfakul.yumeiro.ui.theme.yuRound

// Precompiled once (R15 audit): these ran a regex compile on every call.
private val RX_STREAMSCREEN_HOT_1 = Regex("[^A-Za-z0-9 ._-]")

// Precompiled once (never during composition, scrolling or per call): see docs/STABILITY-R15.md.
private val RX_STREAMSCREEN_1 = Regex("^(https?|rtsps?)://.+", RegexOption.IGNORE_CASE)
private val RX_STREAMSCREEN_2 = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://\\S+")

/** Network stream player: URL / M3U8 / MPD / Smooth / RTSP / TS, IPTV playlists, headers & DRM. */
@Composable
fun StreamScreen(
    modifier: Modifier = Modifier,
    onOpenPlaylist: (String) -> Unit = {},
    onOpenMarket: () -> Unit = {},
    onOpenWebsites: () -> Unit = {},
    onOpenTelegram: () -> Unit = {},
    onOpenSports: () -> Unit = {},
) {
    androidx.compose.runtime.SideEffect { dev.asfakul.yumeiro.diagnostics.DrawCounter.hit("live") } // FREEZE-4
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    remember(context) { StreamRepository.init(context); StreamRepository }
    val history by StreamRepository.history.collectAsState()
    val liveRecent by StreamRepository.liveHistory.collectAsState()
    val channels by StreamRepository.playlist.collectAsState()
    val playlistName by StreamRepository.playlistName.collectAsState()
    val openPlaylistId by StreamRepository.playlistId.collectAsState()
    val playlists by PlaylistStore.playlists.collectAsState()
    val lastSync by PlaylistStore.lastSync.collectAsState()
    val listState = dev.asfakul.yumeiro.ui.components.ScrollMemory.list("stream")
    LaunchedEffect(Unit) {
        runCatching { PlaylistStore.init(context.applicationContext) }
        // Background market check (at most every 6 h). B11: silent; what it updated goes to the inbox, already read.
        val r = runCatching { PlaylistStore.autoUpdateMarket(context.applicationContext) }.getOrNull()
        if (r != null && r.updated.isNotEmpty()) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            dev.asfakul.yumeiro.online.BackgroundUpdates.fileQuietly(context.applicationContext, dev.asfakul.yumeiro.online.BackgroundUpdates.Outcome(playlistsUpdated = r.updated))
        }
    }

    var url by rememberSaveable { mutableStateOf("") }
    var format by rememberSaveable { mutableStateOf(StreamFormat.AUTO) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var uaPreset by rememberSaveable { mutableStateOf(UserAgentPreset.DEFAULT) }
    var customUa by rememberSaveable { mutableStateOf("") }
    var referer by rememberSaveable { mutableStateOf("") }
    var origin by rememberSaveable { mutableStateOf("") }
    var cookie by rememberSaveable { mutableStateOf("") }
    var extraHeaders by rememberSaveable { mutableStateOf("") }
    var drm by rememberSaveable { mutableStateOf(DrmScheme.NONE) }
    var license by rememberSaveable { mutableStateOf("") }
    var clearKeys by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pageFinder by remember { mutableStateOf<Resolution.NeedsBrowser?>(null) }
    var handoff by remember { mutableStateOf<Resolution.Handoff?>(null) }
    val busyIds = remember { mutableStateMapOf<String, Boolean>() }
    var syncing by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<StreamPlaylist?>(null) }
    var addingRemote by remember { mutableStateOf(false) }
    var creatingLocal by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<StreamPlaylist?>(null) }
    var addTarget by remember { mutableStateOf<StreamRequest?>(null) }
    var pendingExport by remember { mutableStateOf<String?>(null) }
    val app = context.applicationContext
    val activePlaylists = playlists.filter { it.enabled }
    val disabledPlaylists = playlists.filterNot { it.enabled }
    val localPlaylists = playlists.filter { it.isLocal }

    fun toast(msg: String) = AppMessages.show(context, msg)
    fun toastError(msg: String) = AppMessages.error(context, msg)

    /** Runs [block] off the main thread; any failure becomes a short message instead of a crash. */
    fun launchSafe(onError: (String) -> Unit = ::toastError, block: suspend () -> Unit) = scope.launch {
        try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { onError(dev.asfakul.yumeiro.online.OnlineErrors.friendly(e, tr(R.string.something_went_wrong_try_again, "Something went wrong. Try again."))) }
    }

    /** Opens a saved playlist on its own page; [force] re-downloads it first (card menu → Refresh now). */
    fun openSaved(p: StreamPlaylist, force: Boolean = false) {
        if (!force) { onOpenPlaylist(p.id); return }
        if (busyIds[p.id] == true) return
        busyIds[p.id] = true
        launchSafe(onError = { toastError("${p.name}: $it") }) {
            try {
                val n = PlaylistStore.refresh(app, p.id)
                if (openPlaylistId == p.id) StreamRepository.setPlaylist(p.name, PlaylistStore.channels(app, p.id), p.id)
                AppMessages.success(context, "${p.name} updated · $n streams")
            } finally { busyIds.remove(p.id) }
        }
    }

    fun refreshOpenIfShown(id: String) {
        if (openPlaylistId == id) launchSafe {
            val p = PlaylistStore.get(id) ?: return@launchSafe
            StreamRepository.setPlaylist(p.name, PlaylistStore.channels(app, id), id)
        }
    }

    fun addToLocal(target: StreamPlaylist, item: StreamRequest) = launchSafe {
        PlaylistStore.addChannels(app, target.id, listOf(item.copy(title = item.displayTitle)))
        toast("Saved to ${target.name}")
        refreshOpenIfShown(target.id)
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        val id = pendingExport; pendingExport = null
        if (uri != null) launchSafe {
            if (id == null || id == TEMP_EXPORT) {
                PlaylistStore.exportChannels(app, playlistName, StreamRepository.playlist.value, uri)
                toast("Exported ${StreamRepository.playlist.value.size} streams")
            } else {
                val n = PlaylistStore.export(app, id, uri)
                toast(tr(R.string.exported_streams, "Exported %1\$s streams", n))
            }
        }
    }

    fun exportPlaylist(id: String?, name: String?) {
        pendingExport = id ?: TEMP_EXPORT
        runCatching { exportLauncher.launch(fileNameFor(name)) }.onFailure { pendingExport = null; toast(tr(R.string.no_app_available_to_save_files, "No app available to save files")) }
    }

    fun sync() {
        if (syncing) return
        syncing = true
        launchSafe {
            try {
                val r = PlaylistStore.sync(app)
                toast(buildString {
                    append("Updated ${r.updated} playlist${if (r.updated == 1) "" else "s"}")
                    if (r.added > 0) append(", ${r.added} new")
                    if (r.failed > 0) append(", ${r.failed} failed")
                })
                openPlaylistId?.let { id -> if (PlaylistStore.get(id)?.isDefault == true) refreshOpenIfShown(id) }
            } finally { syncing = false }
        }
    }

    fun currentRequest() = StreamRequest(
        url = url.trim(),
        format = format,
        userAgent = customUa.ifBlank { null } ?: uaPreset.value,
        referer = referer.ifBlank { null },
        origin = origin.ifBlank { null },
        cookie = cookie.ifBlank { null },
        headers = parseHeaderLines(extraHeaders),
        drmScheme = drm,
        drmLicenseUrl = license.ifBlank { null },
        clearKeys = clearKeys.ifBlank { null },
    )

    fun loadPlaylist(source: String, name: String?) {
        loading = true; error = null
        scope.launch {
            runCatching { StreamRepository.loadPlaylist(context, source, currentRequest().takeIf { source.startsWith("http") }) }
                .onSuccess { StreamRepository.setPlaylist(name, it); onOpenPlaylist(OPENED_PLAYLIST_SLUG) }
                .onFailure { error = dev.asfakul.yumeiro.online.OnlineErrors.friendly(it, tr(R.string.couldnt_load_this_playlist, "Couldn't load this playlist.")) }
            loading = false
        }
    }

    fun play() {
        // T-1: a pasted info-hash plays as the magnet link it stands for.
        val u = dev.asfakul.yumeiro.stream.LinkHosts.magnetFromHash(url) ?: url.trim()
        error = null
        if (!RX_STREAMSCREEN_1.matches(u) && !dev.asfakul.yumeiro.stream.LinkHosts.isTorrent(u)) {
            error = "Enter a valid http(s)://, rtsp:// or magnet: link"
            return
        }
        handoff = null
        if (format == StreamFormat.AUTO && looksLikeM3uPlaylist(u)) { loadPlaylist(u, Uri.parse(u).lastPathSegment); return }
        // TOR-1: play what was checked above. Before, a pasted info-hash passed the check as its magnet link but the
        // request still carried the bare hash, so the resolver saw no torrent and the play failed.
        val request = currentRequest().copy(url = u)
        // A format picked by hand (or RTSP) is played exactly as before, without inspection.
        if (format != StreamFormat.AUTO || (!u.startsWith("http", ignoreCase = true) && !dev.asfakul.yumeiro.stream.LinkHosts.isTorrent(u))) { IncomingMedia.open(context, request, recent = IncomingMedia.Recent.LINK); return }
        // Otherwise look at what the link really returns (headers, MIME type, redirects, content).
        loading = true
        scope.launch {
            val r = runCatching { StreamResolver.resolve(context, request, thorough = true) }
                .getOrElse { Resolution.Failed(tr(R.string.couldnt_open_the_link, "Couldn't open the link"), dev.asfakul.yumeiro.online.OnlineErrors.friendly(it, tr(R.string.check_the_link_and_try_again, "Check the link and try again."))) }
            loading = false
            when (r) {
                is Resolution.Playable -> IncomingMedia.open(context, r.request, recent = IncomingMedia.Recent.LINK)
                is Resolution.Playlist -> {
                    StreamRepository.setPlaylist(r.name ?: Uri.parse(u).lastPathSegment ?: Uri.parse(u).host, r.channels)
                    onOpenPlaylist(OPENED_PLAYLIST_SLUG)
                }
                is Resolution.NeedsBrowser -> pageFinder = r
                is Resolution.Handoff -> { handoff = r; error = r.reason }
                is Resolution.Failed -> error = "${r.title}. ${r.message}"
            }
        }
    }

    /** Resolves the link like Play does, then saves it with the built-in downloader. */
    fun download() {
        val u = url.trim()
        error = null
        if (u.isBlank()) { error = tr(R.string.paste_a_link_first, "Paste a link first"); return }
        loading = true
        scope.launch {
            val r = runCatching { StreamResolver.resolve(context, currentRequest(), thorough = true) }.getOrNull()
            loading = false
            when (r) {
                // OL-4: the same Offline / Permanent popup as every other download.
                is Resolution.Playable -> dev.asfakul.yumeiro.ui.screens.downloads.StreamDownloadAsk.ask(context, r.request, u) { error = it }
                is Resolution.NeedsBrowser -> pageFinder = r
                is Resolution.Handoff -> { handoff = r; error = r.reason }
                is Resolution.Playlist -> error = tr(R.string.this_is_a_playlist_open_it, "This is a playlist. Open it and download a single channel or file.")
                is Resolution.Failed -> error = "${r.title}. ${r.message}"
                null -> error = tr(R.string.couldnt_open_the_link_2, "Couldn't open the link.")
            }
        }
    }

    // ---- Round 8: Live TV hub state -------------------------------------------------------------
    var pane by rememberSaveable { mutableStateOf("hub") }
    androidx.activity.compose.BackHandler(enabled = pane != "hub") { pane = "hub" }
    // H-12: the "Network stream" nav shortcut lands on "Play link" (one-shot request, read once).
    val linkAsked by StreamLaunch.link.collectAsState()
    androidx.compose.runtime.LaunchedEffect(linkAsked) { if (StreamLaunch.take()) { advanced = false; pane = "link" } }
    // NBUG-15 (ADR-028): liveSel = the playlist picked in "Show videos from" (null = all playlists);
    // liveGroup = the category chip (null = all categories).
    var liveSel by rememberSaveable { mutableStateOf<String?>(null) }
    var liveGroup by rememberSaveable(liveSel) { mutableStateOf<String?>(null) }
    // 2.1.0: country chip (null = every country), from tvg-country / the iptv-org tvg-id suffix.
    var liveCountry by rememberSaveable(liveSel) { mutableStateOf<String?>(null) }
    // LIVE-1 (owner list R21, item 1): search by channel name behind a header icon, and a Country sheet.
    var liveSearching by rememberSaveable { mutableStateOf(false) }
    var liveGenre by rememberSaveable(liveSel) { mutableStateOf<String?>(null) }
    var liveQuery by rememberSaveable { mutableStateOf("") }
    var countryOpen by rememberSaveable { mutableStateOf(false) }
    var liveLimit by rememberSaveable(liveSel, liveGroup, liveCountry, liveGenre, liveQuery) { mutableStateOf(LiveChannelFeed.PAGE) }
    var filterOpen by rememberSaveable { mutableStateOf(false) }
    var toolsOpen by rememberSaveable { mutableStateOf(false) }
    var playlistQuery by rememberSaveable { mutableStateOf("") }
    var playlistSearchOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { PinnedPlaylists.init(app) }
    val pinIds by PinnedPlaylists.pins.collectAsState()
    // R15 Phase 3 (ADR-008): the channel lists live for the process (LiveTvMemory), and loads run
    // in FeedScope. They were `remember {}` state: coming back to Live TV reloaded every playlist
    // (the list emptied, then refilled from the cache under the user's finger) and a load cut off
    // by leaving the tab stayed "loading" for good.
    val loads = LiveTvMemory.loads
    val loadedStamp = LiveTvMemory.stamps
    fun loadLive(p: StreamPlaylist, force: Boolean = false) {
        val g = (LiveTvMemory.gen[p.id] ?: 0) + 1
        LiveTvMemory.gen[p.id] = g
        loads[p.id] = LiveLoad(loading = true, channels = loads[p.id]?.channels.orEmpty())
        dev.asfakul.yumeiro.ui.feed.FeedScope.main.launch {
            try {
                val list = PlaylistStore.channels(app, p.id, forceRefresh = force)
                if (LiveTvMemory.gen[p.id] == g) loads[p.id] = LiveLoad(loading = false, channels = list)
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                if (LiveTvMemory.gen[p.id] == g) loads[p.id] = LiveLoad(loading = false, channels = loads[p.id]?.channels.orEmpty(), error = dev.asfakul.yumeiro.online.OnlineErrors.friendly(e, tr(R.string.couldnt_load_this_playlist, "Couldn't load this playlist.")))
            }
        }
    }
    val activeKey = activePlaylists.map { it.id to it.lastUpdated }
    LaunchedEffect(activeKey) {
        val ids = activePlaylists.map { it.id }.toSet()
        loads.keys.filter { it !in ids }.forEach { loads.remove(it); loadedStamp.remove(it) }
        if (liveSel != null && liveSel !in ids) liveSel = null
        // LIVE-SLOW-1: automatic downloads take turns, so start the small ones first (the BDIX list
        // shows in seconds on a slow link instead of sharing it with the big one). Before the first
        // download the channel count is unknown; Yumeiro Live is the known big default (1.7 MB).
        PlaylistStore.loadOrder(activePlaylists).forEach { p -> if (loadedStamp[p.id] != p.lastUpdated) { loadedStamp[p.id] = p.lastUpdated; loadLive(p) } }
    }
    fun playLive(p: StreamPlaylist, list: List<StreamRequest>, ch: StreamRequest) {
        // Same as the playlist page: the player gets the whole list, so channel up / down and
        // "Sources for this channel" keep working.
        StreamRepository.setPlaylist(p.name, list, p.id)
        runCatching { IncomingMedia.open(context, ch, recent = IncomingMedia.Recent.LIVE) }
            .onFailure { toastError("Can't play this stream. ${dev.asfakul.yumeiro.online.OnlineErrors.friendly(it, "Try another channel.")}") }
    }
    /**
     * NB15D-2: a recently played channel plays with its own playlist when that list is still loaded (so
     * channel up / down keep working), otherwise on its own.
     */
    fun playRecent(ch: StreamRequest) {
        val hit = activePlaylists.firstNotNullOfOrNull { p -> loads[p.id]?.channels?.takeIf { l -> l.any { it.url == ch.url } }?.let { p to it } }
        if (hit != null) playLive(hit.first, hit.second, hit.second.first { it.url == ch.url })
        else runCatching { IncomingMedia.open(context, ch, recent = IncomingMedia.Recent.LIVE) }
            .onFailure { toastError("Can't play this stream. ${dev.asfakul.yumeiro.online.OnlineErrors.friendly(it, "Try another channel.")}") }
    }
    // NBUG-15 (ADR-028): one feed merged from every enabled playlist (pinned first), grouped by category,
    // built off the main thread whenever a playlist's channel list changes (keyed by list identity, so no
    // deep compare of 20 000 channels on recomposition).
    val feedIds = liveSel?.let { s -> listOf(s) } ?: PinnedPlaylists.pinnedFirst(activePlaylists.map { it.id }, pinIds)
    val feedKey = feedIds.joinToString(",") { id -> id + ":" + System.identityHashCode(loads[id]?.channels) }
    val feed by androidx.compose.runtime.produceState(LiveChannelFeed.Feed.EMPTY, feedKey) {
        val lists = feedIds.associateWith { loads[it]?.channels.orEmpty() }
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { LiveChannelFeed.build(feedIds, lists) }
    }
    val feedLoading = feedIds.count { loads[it]?.loading != false }
    val feedFailed = feedIds.mapNotNull { id -> activePlaylists.firstOrNull { it.id == id }?.takeIf { loads[id]?.let { l -> !l.loading && l.error != null && l.channels.isEmpty() } == true } }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val feedFiltered = remember(feed, liveGroup, liveCountry, liveGenre) { LiveChannelFeed.filter(feed, liveGroup, liveCountry, liveGenre) }
    // LIVE-1: the name search runs off the main thread (Yumeiro Live has 7 500+ channels), a beat after typing.
    val feedShown by androidx.compose.runtime.produceState(feedFiltered, feedFiltered, liveQuery) {
        value = if (liveQuery.isBlank()) feedFiltered else {
            kotlinx.coroutines.delay(120)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { dev.asfakul.yumeiro.stream.LiveFind.search(feedFiltered, liveQuery) }
        }
    }
    val liveCountries = remember(feed) { dev.asfakul.yumeiro.stream.LiveFind.countries(feed) }
    val countriesByCategory = remember(feed) { dev.asfakul.yumeiro.stream.LiveFind.categoriesAreCountries(feed) }
    val countryKey = if (countriesByCategory) liveGroup else liveCountry
    val countryPicked = liveCountries.firstOrNull { it.key.equals(countryKey, ignoreCase = true) }
    val countryRecent by androidx.compose.runtime.produceState(emptyList<String>(), countryOpen) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { LiveCountryRecent.read(app) }
    }
    LaunchedEffect(feed) { if (liveGroup != null && feedLoading == 0 && feed.categories.none { it.name.equals(liveGroup, ignoreCase = true) }) liveGroup = null }
    LaunchedEffect(feed) { if (liveCountry != null && feedLoading == 0 && (!feed.showCountryChips || feed.countries.none { it.name.equals(liveCountry, ignoreCase = true) })) liveCountry = null }
    // Near the end = the footer or one of the last two shown tile rows is on screen. Read from the item
    // keys (not the item count). The footer's key carries the feed size, and a page is added only when the
    // list is still near its end after two fresh frames, so a layout from before a playlist finished
    // loading can never add a page (phones 44431459816: api36 showed 100 before any scroll).
    val liveColumns = (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp / 120).coerceIn(3, 7)
    val feedShownSize = remember { androidx.compose.runtime.mutableIntStateOf(0) }
    androidx.compose.runtime.SideEffect { feedShownSize.intValue = feedShown.size }
    val liveNearEnd by remember(liveColumns, liveSel, liveGroup) {
        androidx.compose.runtime.derivedStateOf {
            val vis = listState.layoutInfo.visibleItemsInfo
            vis.any { it.key == "live_feed_footer_${feedShownSize.intValue}" } ||
                vis.lastOrNull { (it.key as? String)?.startsWith("live_row_") == true }
                    ?.let { (it.key as String).substringAfterLast('_').toIntOrNull() }
                    ?.let { it >= liveLimit - 2 * liveColumns } == true
        }
    }
    LaunchedEffect(liveNearEnd, feedShown.size, liveLimit) {
        if (!liveNearEnd) return@LaunchedEffect
        androidx.compose.runtime.withFrameNanos { }
        androidx.compose.runtime.withFrameNanos { }
        if (liveNearEnd) liveLimit = LiveChannelFeed.nextLimit(liveLimit, feedShown.size)
    }
    val feedRows = remember(feed, feedShown, liveLimit, liveColumns, liveGroup, liveCountry, liveGenre, liveQuery) {
        LiveChannelFeed.rows(feedShown, liveLimit, liveColumns, feed.categories.associate { it.name to it.count }, headers = liveGroup == null && liveCountry == null && liveGenre == null && liveQuery.isBlank())
    }
    fun playEntry(e: LiveChannelFeed.Entry) {
        // LIVE-1c: the search keyboard closes when a channel starts (it stayed over the player).
        keyboard?.hide(); focusManager.clearFocus()
        val p = activePlaylists.firstOrNull { it.id == e.playlistId }
        val list = loads[e.playlistId]?.channels
        if (p != null && list != null) playLive(p, list, e.channel)
        else runCatching { IncomingMedia.open(context, e.channel, recent = IncomingMedia.Recent.LIVE) }
            .onFailure { toastError("Can't play this stream. ${dev.asfakul.yumeiro.online.OnlineErrors.friendly(it, "Try another channel.")}") }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) loadPlaylist(uri.toString(), uri.lastPathSegment?.substringAfterLast('/'))
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = SpotifyGreen, unfocusedBorderColor = DarkOutline,
        focusedLabelColor = SpotifyGreen, cursorColor = SpotifyGreen,
        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
    )

    pageFinder?.let { pf ->
        PageStreamFinderDialog(
            pageUrl = pf.pageUrl,
            reason = pf.reason,
            base = pf.base,
            onPlay = { req -> pageFinder = null; IncomingMedia.open(context, req, recent = IncomingMedia.Recent.LINK) },
            onDismiss = { pageFinder = null },
            auto = pf.auto,
        )
    }

    val linkCard: @Composable () -> Unit = {
            Surface(shape = yuRound(24), color = GlassFill, border = null) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(SpotifyGreen.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Stream, null, tint = SpotifyGreen, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.network_stream), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(stringResource(R.string.hls_dash_mp4_m3u_youtube_drive), color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it; error = null },
                        label = { Text(stringResource(R.string.stream_or_playlist_url)) },
                        placeholder = { Text(stringResource(R.string.https_example_com_live_index_m3u8)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        trailingIcon = {
                            if (url.isEmpty()) IconButton(onClick = { clipboard.getText()?.text?.let { url = it.trim() } }) {
                                Icon(Icons.Default.ContentPaste, stringResource(R.string.paste), tint = TextSecondary)
                            } else IconButton(onClick = { url = "" }) { Icon(Icons.Default.Close, stringResource(R.string.clear), tint = TextSecondary) }
                        },
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth().testTag("stream_url"),
                    )
                    if (url.isNotBlank() && format == StreamFormat.AUTO) {
                        val detected = when {
                            looksLikeM3uPlaylist(url) -> tr(R.string.m3u_playlist, "M3U playlist")
                            PlatformRules.ruleFor(url.trim()) is PlatformRules.Handoff -> "${PlatformRules.ruleFor(url.trim())?.platform} (opens in its app)"
                            PlatformRules.ruleFor(url.trim()) is PlatformRules.WebSession -> "${PlatformRules.ruleFor(url.trim())?.platform} web page"
                            else -> StreamSniffer.fromUrl(url.trim())?.label ?: "checked when you press Play"
                        }
                        Text(stringResource(R.string.detected, detected), color = TextTertiary, fontSize = 12.sp)
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(StreamFormat.entries, key = { it.name }) { f ->
                            Chip(f.label.substringBefore(" ("), selected = format == f) { format = f }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(yuRound(20)).clickable { advanced = !advanced }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.headers_drm), color = TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(stringResource(R.string.user_agent_referer_origin_cookie), color = TextTertiary, fontSize = 11.sp)
                        Icon(if (advanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = TextSecondary)
                    }
                    AnimatedVisibility(advanced) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(R.string.user_agent), color = TextSecondary, fontSize = 12.sp)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(UserAgentPreset.entries, key = { it.name }) { p -> Chip(p.label, uaPreset == p && customUa.isBlank()) { uaPreset = p; customUa = "" } }
                            }
                            OutlinedTextField(customUa, { customUa = it }, label = { Text(stringResource(R.string.custom_user_agent)) }, singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(referer, { referer = it }, label = { Text(stringResource(R.string.referer)) }, singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(origin, { origin = it }, label = { Text(stringResource(R.string.origin)) }, singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(cookie, { cookie = it }, label = { Text(stringResource(R.string.cookie)) }, singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(extraHeaders, { extraHeaders = it }, label = { Text(stringResource(R.string.extra_headers_key_value_per_line)) }, minLines = 2, colors = fieldColors, modifier = Modifier.fillMaxWidth())
                            Text(stringResource(R.string.drm), color = TextSecondary, fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { DrmScheme.entries.forEach { d -> Chip(d.label, drm == d) { drm = d } } }
                            if (drm != DrmScheme.NONE) {
                                OutlinedTextField(license, { license = it }, label = { Text(stringResource(R.string.license_server_url)) }, singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth())
                            }
                            if (drm == DrmScheme.CLEARKEY) {
                                OutlinedTextField(clearKeys, { clearKeys = it }, label = { Text(stringResource(R.string.clearkey_kid_key_hex)) }, colors = fieldColors, modifier = Modifier.fillMaxWidth())
                            }
                            Text(
                                stringResource(R.string.headers_are_sent_with_every_manifest),
                                color = TextTertiary, fontSize = 11.sp,
                            )
                        }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.testTag("stream_error")) }
                    handoff?.let { h ->
                        TextButton(onClick = {
                            runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(h.url))) }
                                .onFailure { error = "No app on this device can open ${h.platform} links." }
                        }) { Text(stringResource(R.string.open_in, h.platform), color = SpotifyGreen, fontWeight = FontWeight.SemiBold) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = ::play,
                            enabled = !loading,
                            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent),
                            modifier = Modifier.weight(1f).height(48.dp).testTag("stream_play"),
                        ) {
                            if (loading) CircularProgressIndicator(color = OnAccent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            else { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.play), fontWeight = FontWeight.Bold) }
                        }
                        OutlinedButton(
                            onClick = { filePicker.launch(arrayOf("audio/x-mpegurl", "application/x-mpegurl", "audio/mpegurl", "application/vnd.apple.mpegurl", "text/plain", "*/*")) },
                            border = null,
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = GlassFill, contentColor = TextPrimary),
                            modifier = Modifier.height(48.dp),
                        ) { Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.m3u_file)) }
                    }
                    if (url.isNotBlank()) OutlinedButton(
                        onClick = ::download, enabled = !loading, border = null,
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = GlassFill, contentColor = TextPrimary),
                        modifier = Modifier.fillMaxWidth().height(44.dp).testTag("stream_download"),
                    ) { Icon(Icons.Default.FileDownload, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.download)) }
                    if (url.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { loadPlaylist(url.trim(), Uri.parse(url.trim()).host) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = SpotifyGreen); Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.open_as_channel_list), color = SpotifyGreen, maxLines = 1)
                            }
                            TextButton(
                                onClick = {
                                    val u = url.trim()
                                    if (!RX_STREAMSCREEN_2.matches(u)) error = "Enter a valid stream URL"
                                    else addTarget = currentRequest().copy(title = guessTitle(u))
                                },
                                modifier = Modifier.testTag("stream_save"),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, tint = SpotifyGreen); Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.save), color = SpotifyGreen)
                            }
                        }
                    }
                }
            }
    }

    // Round 8: the hub (channels first, tools as shortcuts) and the link player on its own page.
    val tools = listOf(
        LiveTool("live_tool_playlists", "Playlists", "${playlists.size} lists", Icons.AutoMirrored.Filled.PlaylistPlay, liveHue("playlists")) { pane = "playlists" },
        LiveTool("live_tool_link", tr(R.string.play_link, "Play link"), tr(R.string.url_magnet, "URL · magnet"), Icons.Default.ContentPaste, liveHue("link")) { advanced = false; pane = "link" },
        LiveTool("live_tool_file", tr(R.string.m3u_file, "M3U file"), tr(R.string.from_storage, "From storage"), Icons.Default.FolderOpen, liveHue("file")) {
            runCatching { filePicker.launch(arrayOf("audio/x-mpegurl", "application/x-mpegurl", "audio/mpegurl", "application/vnd.apple.mpegurl", "text/plain", "*/*")) }
                .onFailure { toast(tr(R.string.no_app_available_to_pick_files, "No app available to pick files")) }
        },
        LiveTool("market_entry", "Market", "${playlists.count { it.isMarket }} installed", Icons.Default.Storefront, liveHue("market"), onOpenMarket),
        LiveTool("stream_websites", "Websites", tr(R.string.saved_sites, "Saved sites"), Icons.Default.Language, liveHue("websites"), onOpenWebsites),
        LiveTool("stream_telegram", "Telegram", tr(R.string.public_channels, "Public channels"), Icons.AutoMirrored.Filled.Send, liveHue("telegram"), onOpenTelegram),
        LiveTool("live_tool_sports", stringResource(R.string.sports_title), stringResource(R.string.sports_tool_status), Icons.Default.SportsSoccer, liveHue("sports"), onOpenSports),
        LiveTool("live_tool_add_url", tr(R.string.add_url, "Add URL"), tr(R.string.remote_list, "Remote list"), Icons.Default.AddLink, liveHue("addurl")) { addingRemote = true },
        LiveTool("live_tool_new", tr(R.string.new_list, "New list"), tr(R.string.your_own, "Your own"), Icons.AutoMirrored.Filled.PlaylistAdd, liveHue("newlist")) { creatingLocal = true },
        LiveTool("live_tool_sync", tr(R.string.update_all_2, "Update all"), if (syncing) "Updating…" else tr(R.string.every_playlist, "Every playlist"), Icons.Default.Sync, liveHue("sync")) { sync() },
        LiveTool("live_tool_recent", tr(R.string.recent_links, "Recent links"), if (history.isEmpty()) tr(R.string.headers_drm_2, "Headers · DRM") else "${history.size} streams", Icons.Default.History, liveHue("recent")) { pane = "link" },
    )
    dev.asfakul.yumeiro.ui.components.TabSwap(pane, { when (it) { "hub" -> 0; "playlists" -> 1; else -> 2 } }, modifier.fillMaxSize(), label = "live_pane") { pn ->
    if (pn == "playlists") {
        // NBUG-15 (c): playlist management, opened from Tools → Playlists.
        Column(Modifier.fillMaxSize().testTag("playlists_page")) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { pane = "hub" }, modifier = Modifier.testTag("playlists_back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back_to_live_tv), tint = TextPrimary) }
                Text(stringResource(R.string.playlists), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            }
            val q = playlistQuery
            fun match(p: StreamPlaylist) = q.isBlank() || filterByTitle(listOf(p), q) { it.name }.isNotEmpty()
            val on = activePlaylists.filter(::match)
            val off = disabledPlaylists.filter(::match)
            LazyColumn(
                Modifier.fillMaxSize().testTag("playlists_list"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "playlists_header") {
                    PlaylistsHeader(
                        lastSync = lastSync, syncing = syncing, onMarket = onOpenMarket,
                        onAddUrl = { addingRemote = true }, onNew = { creatingLocal = true }, onSync = ::sync,
                        onRestore = { launchSafe { val n = PlaylistStore.restoreDefaults(app); toast(if (n > 0) tr(R.string.restored_default_playlists, "Restored %1\$s default playlists", n) else tr(R.string.default_playlists_enabled, "Default playlists enabled")) } },
                        searchOpen = if (playlists.size >= PLAYLIST_SEARCH_FROM) playlistSearchOpen || playlistQuery.isNotEmpty() else null,
                        onToggleSearch = { if (playlistSearchOpen || playlistQuery.isNotEmpty()) { playlistSearchOpen = false; playlistQuery = "" } else playlistSearchOpen = true },
                    )
                }
                if (playlists.size >= PLAYLIST_SEARCH_FROM && (playlistSearchOpen || playlistQuery.isNotEmpty())) item(key = "playlists_search") {
                    dev.asfakul.yumeiro.ui.components.HeaderSearchField(true, playlistQuery, { playlistQuery = it.take(60) }, tr(R.string.search_playlists, "Search playlists"), "playlists_search", horizontalPadding = 0.dp)
                }
                if (channels.isNotEmpty() && openPlaylistId == null) item(key = "opened_list") {
                    OpenedListCard(name = playlistName, count = channels.size, onOpen = { onOpenPlaylist(OPENED_PLAYLIST_SLUG) }, onClose = { StreamRepository.setPlaylist(null, emptyList()) })
                }
                fun cardFor(p: StreamPlaylist): @Composable () -> Unit = {
                    PlaylistCard(
                        playlist = p, busy = busyIds[p.id] == true || (syncing && p.isDefault && p.enabled),
                        onOpen = { openSaved(p) },
                        onToggle = { v ->
                            launchSafe { PlaylistStore.setEnabled(app, p.id, v) }
                            if (!v && openPlaylistId == p.id) StreamRepository.setPlaylist(null, emptyList())
                        },
                        onRefresh = { openSaved(p, force = true) },
                        onEdit = { editing = p },
                        onExport = { exportPlaylist(p.id, p.name) },
                        onDelete = { deleting = p },
                    )
                }
                if (on.isNotEmpty()) item(key = "playlists_on_header") { PlaylistsSectionLabel("On · ${on.size}", "playlists_on_header") }
                items(on, key = { "pl_${it.id}" }) { p -> cardFor(p)() }
                if (off.isNotEmpty()) item(key = "disabled_header") { PlaylistsSectionLabel("Off · ${off.size}", "playlists_off_header") }
                items(off, key = { "pl_${it.id}" }) { p -> cardFor(p)() }
                if (q.isNotBlank() && on.isEmpty() && off.isEmpty()) item(key = "playlists_none") {
                    Column(Modifier.fillMaxWidth().padding(8.dp).testTag("playlists_no_match")) {
                        Text("No playlist called \"${q.trim()}\"", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        TextButton(onClick = { playlistQuery = "" }) { Text(stringResource(R.string.clear_search), color = SpotifyGreen, fontWeight = FontWeight.SemiBold) }
                    }
                }
                item(key = "bottom_space") { Spacer(Modifier.height(96.dp)) }
            }
        }
    } else if (pn == "link") {
        Column(Modifier.fillMaxSize().testTag("stream_link_page")) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { pane = "hub" }, modifier = Modifier.testTag("stream_link_back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back_to_live_tv), tint = TextPrimary) }
                Text(stringResource(R.string.play_a_link), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "input") { linkCard() }
        if (history.isNotEmpty()) {
            item(key = "history_header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.recent_streams), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { StreamRepository.clear(context) }) { Text(stringResource(R.string.clear), color = TextSecondary) }
                }
            }
            items(history, key = { "h_${it.url}" }) { h ->
                StreamRow(
                    h, subtitle = h.url,
                    onClick = { IncomingMedia.open(context, h, recent = IncomingMedia.Recent.LINK) },
                    onDelete = { StreamRepository.remove(context, h) },
                    onEdit = {
                        url = h.url; format = h.format; customUa = h.userAgent.orEmpty(); referer = h.referer.orEmpty()
                        origin = h.origin.orEmpty(); cookie = h.cookie.orEmpty()
                        extraHeaders = h.headers.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                        drm = h.drmScheme; license = h.drmLicenseUrl.orEmpty(); clearKeys = h.clearKeys.orEmpty()
                    },
                )
            }
        }

                if (history.isEmpty()) item(key = "link_tip") {
                    Text(stringResource(R.string.you_can_also_share_links_to), color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(4.dp))
                }
                item(key = "bottom_space") { Spacer(Modifier.height(96.dp)) }
            }
        }
    } else
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag("stream_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "live_header") {
            val total = activePlaylists.sumOf { p -> loads[p.id]?.channels?.size ?: p.channelCount }
            val selName = activePlaylists.firstOrNull { it.id == liveSel }?.name
            val shownNow = liveLimit.coerceAtMost(feedShown.size)
            Box(Modifier.semantics { stateDescription = if (shownNow < feedShown.size) "Showing $shownNow of ${feedShown.size} channels" else "All ${feedShown.size} channels" }.testTag("live_header")) {
            dev.asfakul.yumeiro.ui.components.YuSection(
                "生", "Live TV",
                // The merged feed's count once every list is in (a stream in two playlists counts once);
                // the saved counts while lists are still loading.
                // LIVE-1c: with a country or category picked the header counts those channels (probe 45247707490
                // showed "7555 …", cut short, over a 32-channel Bangladesh list).
                if (activePlaylists.isEmpty()) "Channels"
                else if ((liveGroup != null || liveCountry != null || liveGenre != null) && feed.size > 0) "${feedFiltered.size} channels"
                else if (liveSel != null || (feedLoading == 0 && feed.size > 0)) "${feed.size} channels" else "$total channels",
                top = 4.dp,
                trailing = {
                    // LIVE-1: Yumix-style icon header. Search and Country open on demand (no always-on bars);
                    // a filter in use shows its name, so the header says what the list holds.
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (activePlaylists.isNotEmpty()) {
                            LiveIconButton(
                                Icons.Default.Search, if (liveSearching) tr(R.string.close_channel_search, "Close channel search") else tr(R.string.search_channels, "Search channels"), "live_search_button",
                                onClick = { if (liveSearching) { liveSearching = false; liveQuery = "" } else liveSearching = true }, active = liveSearching,
                            )
                            if (liveCountries.size > 1) LiveIconButton(
                                Icons.Default.Public, countryPicked?.let { "Showing ${it.label}. Change country" } ?: tr(R.string.pick_a_country, "Pick a country"), "live_country_button",
                                onClick = { countryOpen = true }, active = countryPicked != null,
                                label = countryPicked?.let { c -> listOfNotNull(dev.asfakul.yumeiro.stream.LiveFind.flag(c.code), c.label).joinToString(" ") },
                            )
                            LiveIconButton(
                                Icons.Default.FilterList, if (selName != null) tr(R.string.showing_change_filter, "Showing %1\$s. Change filter", selName) else tr(R.string.show_videos_from, "Show videos from"), "live_filter_button",
                                onClick = { filterOpen = true }, active = selName != null, label = selName,
                            )
                        }
                        LiveIconButton(Icons.Default.Apps, tr(R.string.tools_and_playlists, "Tools and playlists"), "live_tools_button", onClick = { toolsOpen = true })
                    }
                },
            )
            }
        }
        val pinned = pinIds.mapNotNull { id -> activePlaylists.firstOrNull { it.id == id } }
        if (pinned.isNotEmpty()) item(key = "live_pins") { LivePinnedBar(pinned, liveSel) { liveSel = it } }
        if (liveSearching && activePlaylists.isNotEmpty()) item(key = "live_search") {
            LiveSearchBox(liveQuery, { liveQuery = it }, matches = if (liveQuery.isBlank()) null else feedShown.size, onClose = { liveSearching = false; liveQuery = "" })
        }
        if (liveSel == null && liveRecent.isNotEmpty() && liveQuery.isBlank()) item(key = "live_recent") {
            LiveRecentRow(liveRecent, onPlay = { playRecent(it) }, onClear = { StreamRepository.clearLive(context) })
        }
        if (activePlaylists.isNotEmpty()) {
            // LIVE-1: one chip row at most. When the categories are the countries (Yumeiro Live), they live in the
            // Country sheet instead of a second long row; real categories (Sports, News...) keep their chips.
            if (feed.categories.size > 1 && !countriesByCategory) item(key = "live_cats") { LiveCategoryBar(feed.categories, feed.size, liveGroup) { liveGroup = it } }
            else if (countriesByCategory && feed.genres.size > 1) item(key = "live_genres") {
                // The country already sits in the header; the one chip row shows the genres (News, Sports...) of
                // the channels the country filter leaves, with their counts.
                // "All" counts every channel in scope (probe 45243905968: it showed 7262, the channels with a genre,
                // under a 7555-channel header).
                val (inScope, scopeSize) = remember(feed, liveGroup) {
                    if (liveGroup == null) feed.genres to feed.size
                    else LiveChannelFeed.filter(feed, liveGroup).let { l -> l.mapNotNull { it.channel.genre }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { LiveChannelFeed.Category(it.key, it.value) } to l.size }
                }
                if (inScope.size > 1) LiveCategoryBar(inScope, scopeSize, liveGenre, tag = "live_genres", prefix = "live_genre_") { liveGenre = it }
            }
            if (feedFailed.isNotEmpty()) item(key = "live_failed") {
                LiveProblem(
                    if (feedFailed.size == 1) "${feedFailed[0].name} didn't load. ${loads[feedFailed[0].id]?.error.orEmpty()}".trim()
                    else "${feedFailed.size} playlists didn't load",
                    onRetry = { feedFailed.forEach { loadLive(it, force = true) } },
                )
            }
            when {
                feed.size == 0 && feedLoading > 0 -> items(3, key = { "live_skel_$it" }) {
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { repeat(liveColumns) { LiveTileSkeleton(Modifier.weight(1f)) } }
                }
                feed.size == 0 -> { if (feedFailed.isEmpty()) item(key = "live_empty") { LiveProblem(tr(R.string.no_channels_in_these_playlists_right, "No channels in these playlists right now"), onRetry = { feedIds.forEach { id -> activePlaylists.firstOrNull { it.id == id }?.let { loadLive(it, force = true) } } }) } }
                liveQuery.isNotBlank() && feedShown.isEmpty() -> item(key = "live_search_empty") {
                    val scoped = listOfNotNull(activePlaylists.firstOrNull { it.id == liveSel }?.name, countryPicked?.label, liveGroup?.takeIf { !countriesByCategory }, liveGenre)
                    LiveSearchEmpty(
                        liveQuery, scoped.takeIf { it.isNotEmpty() }?.joinToString(" · "),
                        onClear = { liveQuery = "" },
                        onWiden = if (scoped.isEmpty()) null else ({ liveSel = null; liveGroup = null; liveCountry = null; liveGenre = null }),
                    )
                }
                else -> {
                    liveFeedItems(feedRows, liveColumns) { playEntry(it) }
                    item(key = "live_feed_footer_${feedShown.size}") { LiveFeedFooter(liveLimit.coerceAtMost(feedShown.size), feedShown.size, feedLoading) }
                }
            }
        } else item(key = "live_none") {
            Column(Modifier.fillMaxWidth().clip(yuRound(20)).background(GlassFill).padding(18.dp).testTag("live_none")) {
                Text(if (playlists.isEmpty()) tr(R.string.no_playlists_yet, "No playlists yet") else tr(R.string.all_playlists_are_off, "All playlists are off"), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    if (playlists.isEmpty()) tr(R.string.get_channels_from_the_market_add, "Get channels from the market, add a playlist URL or play a link.") else tr(R.string.turn_a_playlist_on_in_tools, "Turn a playlist on in Tools → Playlists to see its channels here."),
                    color = TextSecondary, fontSize = 12.sp,
                )
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (playlists.isEmpty()) {
                        LiveHeaderButton("Market", Icons.Default.Storefront, "live_none_market", onClick = onOpenMarket)
                        LiveHeaderButton(tr(R.string.add_url, "Add URL"), Icons.Default.AddLink, "live_none_add", onClick = { addingRemote = true })
                    } else LiveHeaderButton("Playlists", Icons.AutoMirrored.Filled.PlaylistPlay, "live_none_playlists", onClick = { pane = "playlists" })
                }
            }
        }
        item(key = "bottom_space") { Spacer(Modifier.height(96.dp)) }
    }
    }

    if (toolsOpen) LiveToolsSheet(tools) { toolsOpen = false }
    if (countryOpen) LiveCountrySheet(
        liveCountries, countryKey, countryRecent, feed.size,
        onPick = { c ->
            if (countriesByCategory) liveGroup = c?.key else liveCountry = c?.key
            if (c != null) dev.asfakul.yumeiro.ui.feed.FeedScope.main.launch { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { LiveCountryRecent.add(app, c.key) } }
            countryOpen = false
        },
        onDismiss = { countryOpen = false },
    )
    if (filterOpen) dev.asfakul.yumeiro.ui.screens.online.SourceFilterSheet(
        names = activePlaylists.map { it.id },
        selected = liveSel,
        status = { id ->
            val l = loads[id]
            when {
                l == null || (l.loading && l.channels.isEmpty()) -> dev.asfakul.yumeiro.ui.screens.online.SourceStatus.Loading
                l.channels.isEmpty() && l.error != null -> dev.asfakul.yumeiro.ui.screens.online.SourceStatus.Failed
                else -> dev.asfakul.yumeiro.ui.screens.online.SourceStatus.Ready(l.channels.size)
            }
        },
        onPick = { liveSel = it; filterOpen = false },
        onDismiss = { filterOpen = false },
        pins = pinIds,
        onTogglePin = { id -> if (!PinnedPlaylists.toggle(app, id)) toast("You can pin up to ${PinnedPlaylists.MAX} playlists") },
        texts = dev.asfakul.yumeiro.ui.screens.online.SourceFilterTexts.Playlists,
        label = { id -> playlists.firstOrNull { it.id == id }?.name ?: id },
    )

    if (addingRemote || creatingLocal) {
        val remote = addingRemote
        PlaylistEditDialog(
            playlist = null, remote = remote,
            onDismiss = { addingRemote = false; creatingLocal = false },
            onSave = { name, u ->
                val p = if (remote) PlaylistStore.addRemote(app, name, u!!) else PlaylistStore.createLocal(app, name)
                toast(if (remote) "Added ${p.name} (${p.channelCount} streams)" else "Created ${p.name}")
            },
        )
    }
    editing?.let { p ->
        PlaylistEditDialog(
            playlist = p, remote = !p.isLocal,
            onDismiss = { editing = null },
            onSave = { name, u ->
                PlaylistStore.update(app, p.id, name, u)
                if (openPlaylistId == p.id) {
                    val updated = PlaylistStore.get(p.id)
                    if (updated != null && updated.url != p.url) StreamRepository.setPlaylist(updated.name, PlaylistStore.channels(app, p.id, forceRefresh = true), p.id)
                    else StreamRepository.setPlaylist(name.ifBlank { p.name }, StreamRepository.playlist.value, p.id)
                }
            },
        )
    }
    deleting?.let { p ->
        ConfirmDeleteDialog(p, onDismiss = { deleting = null }) {
            deleting = null
            if (openPlaylistId == p.id) StreamRepository.setPlaylist(null, emptyList())
            launchSafe { PlaylistStore.delete(app, p.id); toast("Deleted ${p.name}") }
        }
    }
    addTarget?.let { item ->
        AddToPlaylistDialog(
            item = item, localPlaylists = localPlaylists,
            onDismiss = { addTarget = null },
            onPick = { p -> addTarget = null; addToLocal(p, item) },
            onCreate = { name ->
                addTarget = null
                launchSafe { val p = PlaylistStore.createLocal(app, name, listOf(item.copy(title = item.displayTitle))); toast("Saved to ${p.name}") }
            },
        )
    }
}

private const val TEMP_EXPORT = "__opened__"
/** The Playlists page shows a search box from this many playlists (NBUG-15 c). */
private const val PLAYLIST_SEARCH_FROM = 6

internal fun fileNameFor(name: String?): String =
    (name ?: "playlist").replace(RX_STREAMSCREEN_HOT_1, "").trim().replace(' ', '_').ifBlank { "playlist" }.take(60) + ".m3u"

@Composable
internal fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick,
        label = { Text(label, fontSize = 12.sp, maxLines = 1) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = GlassFill, labelColor = TextSecondary,
            selectedContainerColor = SpotifyGreen, selectedLabelColor = OnAccent,
        ),
        border = null,
    )
}

@Composable
internal fun StreamRow(
    item: StreamRequest,
    subtitle: String,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onAdd: (() -> Unit)? = null,
    onEditIcon: (() -> Unit)? = null,
) {
    Surface(shape = yuRound(20), color = GlassFill, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(yuRound(12)).background(GlassFill), contentAlignment = Alignment.Center) {
                if (item.logoUrl != null) AsyncImage(model = item.logoUrl, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(36.dp))
                else Icon(Icons.Default.LiveTv, null, tint = SpotifyGreen, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.displayTitle, color = TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (onEdit != null) TextButton(onClick = onEdit) { Text(stringResource(R.string.edit), color = TextSecondary, fontSize = 12.sp) }
            if (onAdd != null) IconButton(onClick = onAdd) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.save_to_playlist), tint = TextTertiary) }
            if (onEditIcon != null) IconButton(onClick = onEditIcon) { Icon(Icons.Default.Edit, stringResource(R.string.edit_stream), tint = TextTertiary) }
            if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, stringResource(R.string.remove_2), tint = TextTertiary) }
        }
    }
}

/** H-12: a one-shot "open Play link" request from the Network stream nav shortcut. */
object StreamLaunch {
    private val _link = kotlinx.coroutines.flow.MutableStateFlow(false)
    val link: kotlinx.coroutines.flow.StateFlow<Boolean> = _link
    fun openLink() { _link.value = true }
    /** True once per request. */
    fun take(): Boolean = if (_link.value) { _link.value = false; true } else false
}
