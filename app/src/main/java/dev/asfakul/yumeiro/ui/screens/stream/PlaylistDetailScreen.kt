/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.stream

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.stream.IncomingMedia
import dev.asfakul.yumeiro.stream.PlaylistMarket
import dev.asfakul.yumeiro.stream.PlaylistStore
import dev.asfakul.yumeiro.stream.StreamPlaylist
import dev.asfakul.yumeiro.stream.StreamRepository
import dev.asfakul.yumeiro.stream.StreamRequest
import dev.asfakul.yumeiro.ui.components.AppMessages
import dev.asfakul.yumeiro.ui.components.SkeletonBlock
import dev.asfakul.yumeiro.ui.components.SkeletonRow
import dev.asfakul.yumeiro.ui.components.StateMessage
import dev.asfakul.yumeiro.ui.components.SubPageHeader
import dev.asfakul.yumeiro.ui.theme.DarkSurface
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.OnAccent
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Route slug for the temporary (unsaved) list opened from a URL, an M3U file or another app. */
const val OPENED_PLAYLIST_SLUG = "opened"

private sealed interface LoadState {
    data object Loading : LoadState
    data object Ready : LoadState
    data object NotFound : LoadState
    data class Failed(val message: String) : LoadState
}

/**
 * One playlist on its own page (`stream/playlist/{slug}`): channel search, group filter,
 * pull-to-refresh, edit / export / delete, auto-update and market update status. Shows a skeleton
 * while loading, a not-found page for unknown slugs and an error page with retry when a download
 * fails without a cached copy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    slug: String,
    onBack: () -> Unit,
    onOpenMarket: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext
    val scope = rememberCoroutineScope()
    val isOpened = slug == OPENED_PLAYLIST_SLUG
    val playlists by PlaylistStore.playlists.collectAsState()
    val repoChannels by StreamRepository.playlist.collectAsState()
    val repoId by StreamRepository.playlistId.collectAsState()
    val repoName by StreamRepository.playlistName.collectAsState()
    val market by PlaylistMarket.state.collectAsState()
    var storeReady by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf<LoadState>(LoadState.Loading) }
    var refreshing by remember { mutableStateOf(false) }
    var query by rememberSaveable(slug) { mutableStateOf("") }
    var group by rememberSaveable(slug) { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var addTarget by remember { mutableStateOf<StreamRequest?>(null) }
    var editChannel by remember { mutableStateOf<Pair<Int, StreamRequest>?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val listState = dev.asfakul.yumeiro.ui.components.ScrollMemory.list("playlist:" + slug)

    val playlist: StreamPlaylist? = if (isOpened) null else playlists.firstOrNull { it.id == slug }
    val showing = if (isOpened) repoId == null else repoId == slug
    val channels = if (showing) repoChannels else emptyList()
    val title = if (isOpened) repoName ?: "Opened list" else playlist?.name ?: "Playlist"
    val editable = playlist?.isLocal == true
    val marketEntry = (market as? PlaylistMarket.State.Ready)?.catalog?.entries?.firstOrNull { it.id == playlist?.marketId }
    val updateAvailable = playlist != null && marketEntry?.sha256 != null && marketEntry.sha256 != playlist.marketSha

    fun msg(text: String) = AppMessages.show(context, text)
    fun fail(text: String) = AppMessages.error(context, text)
    fun launchSafe(onError: (String) -> Unit = ::fail, block: suspend () -> Unit) = scope.launch {
        try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { onError(dev.asfakul.yumeiro.online.OnlineErrors.friendly(e, "Something went wrong. Try again.")) }
    }

    LaunchedEffect(Unit) { runCatching { PlaylistStore.init(app) }; storeReady = true }

    // Load channels when the page opens (or on Retry). Cached lists appear instantly.
    LaunchedEffect(slug, storeReady, attempt) {
        if (!storeReady) return@LaunchedEffect
        if (isOpened) { state = if (repoId == null && repoChannels.isNotEmpty()) LoadState.Ready else LoadState.NotFound; return@LaunchedEffect }
        val p = PlaylistStore.get(slug) ?: run { state = LoadState.NotFound; return@LaunchedEffect }
        if (!p.enabled) { state = LoadState.Ready; return@LaunchedEffect }
        if (repoId == slug && repoChannels.isNotEmpty() && attempt == 0) { state = LoadState.Ready; return@LaunchedEffect }
        state = LoadState.Loading
        state = try {
            val list = PlaylistStore.channels(app, slug)
            StreamRepository.setPlaylist(p.name, list, p.id)
            group = null
            LoadState.Ready
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            LoadState.Failed(PlaylistMarket.friendlyError(e))
        }
    }
    // Deleted while open (e.g. from another screen): show not-found instead of stale content.
    LaunchedEffect(playlist == null, storeReady) { if (storeReady && !isOpened && playlist == null && state != LoadState.Loading) state = LoadState.NotFound }

    fun refresh(marketSha: String? = null) {
        val p = playlist ?: return
        if (p.isLocal || refreshing) { refreshing = false; return }
        refreshing = true
        launchSafe(onError = { fail("${p.name}: $it") }) {
            try {
                if (marketSha != null) PlaylistStore.refresh(app, p.id, marketSha) else PlaylistStore.refresh(app, p.id)
                val list = PlaylistStore.channels(app, p.id)
                StreamRepository.setPlaylist(p.name, list, p.id)
                state = LoadState.Ready
                AppMessages.success(context, "${p.name} updated · ${list.size} streams")
            } finally { refreshing = false }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        if (uri != null) launchSafe {
            val n = if (playlist != null) PlaylistStore.export(app, playlist.id, uri)
            else { PlaylistStore.exportChannels(app, repoName, channels, uri); channels.size }
            AppMessages.success(context, "Exported $n streams")
        }
    }

    val groups = remember(channels) { channels.mapNotNull { it.group?.trim()?.ifBlank { null } }.distinct().sortedBy { it.lowercase() } }
    val visible = remember(channels, query, group) {
        val q = query.trim()
        channels.withIndex().filter { (_, c) ->
            (group == null || c.group?.trim() == group) &&
                (q.isEmpty() || c.displayTitle.contains(q, true) || c.group?.contains(q, true) == true)
        }
    }
    val showScrollTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 12 } }

    Box(modifier.fillMaxSize().testTag("playlist_detail_screen")) {
        Column(Modifier.fillMaxSize()) {
            SubPageHeader(
                title = title,
                subtitle = when {
                    state == LoadState.Loading -> "Loading…"
                    playlist != null -> listOfNotNull(playlist.category, "${if (showing) channels.size else playlist.channelCount} streams",
                        if (playlist.isLocal) "on this device" else relativeTime(playlist.lastUpdated)).joinToString(" · ")
                    isOpened && channels.isNotEmpty() -> "${channels.size} streams · not saved"
                    else -> null
                },
                onBack = onBack,
            ) {
                if (playlist != null && !playlist.isLocal && state != LoadState.NotFound) {
                    IconButton(onClick = { refresh() }, enabled = !refreshing, modifier = Modifier.testTag("detail_refresh")) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = TextSecondary)
                    }
                }
                if (state != LoadState.NotFound) Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.testTag("detail_menu")) { Icon(Icons.Default.MoreVert, "Playlist options", tint = TextSecondary) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = DarkSurface) {
                        if (isOpened && channels.isNotEmpty()) DropdownMenuItem(
                            text = { Text("Save as playlist") }, leadingIcon = { Icon(Icons.Default.PlaylistAdd, null) },
                            modifier = Modifier.testTag("channels_save"),
                            onClick = {
                                menu = false
                                launchSafe {
                                    val p = PlaylistStore.createLocal(app, repoName ?: "Imported playlist", channels)
                                    StreamRepository.setPlaylist(p.name, channels, p.id)
                                    AppMessages.success(context, "Saved as ${p.name}")
                                }
                            },
                        )
                        if (playlist != null) DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; editing = true })
                        if (channels.isNotEmpty() || playlist != null) DropdownMenuItem(
                            text = { Text("Export as .m3u") }, leadingIcon = { Icon(Icons.Default.FileDownload, null) },
                            onClick = { menu = false; runCatching { exportLauncher.launch(fileNameFor(title)) }.onFailure { fail("No app available to save files") } },
                        )
                        if (playlist != null && playlist.enabled) DropdownMenuItem(
                            text = { Text("Disable") }, leadingIcon = { Icon(Icons.Default.VisibilityOff, null) },
                            onClick = { menu = false; launchSafe { PlaylistStore.setEnabled(app, playlist.id, false); StreamRepository.setPlaylist(null, emptyList()); onBack() } },
                        )
                        if (isOpened) DropdownMenuItem(
                            text = { Text("Close list") }, leadingIcon = { Icon(Icons.Default.Close, null) },
                            onClick = { menu = false; StreamRepository.setPlaylist(null, emptyList()); onBack() },
                        )
                        if (playlist != null) {
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; deleting = true }, modifier = Modifier.testTag("detail_delete"),
                            )
                        }
                    }
                }
            }

            when (val s = state) {
                LoadState.NotFound -> StateMessage(
                    icon = Icons.Default.SearchOff,
                    title = if (isOpened) "No list is open" else "Playlist not found",
                    message = if (isOpened) "Open a playlist link or an M3U file from Videos → Live TV." else "It may have been deleted or renamed. Your other playlists are still in Videos → Live TV.",
                    primary = "Back to Stream" to onBack,
                    secondary = "Browse playlist market" to onOpenMarket,
                    testTag = "playlist_not_found",
                )
                LoadState.Loading -> Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("playlist_loading"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SkeletonBlock(null, 52.dp, shape = RoundedCornerShape(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { repeat(4) { SkeletonBlock(64.dp, 30.dp, shape = RoundedCornerShape(50)) } }
                    repeat(7) { SkeletonRow() }
                }
                is LoadState.Failed -> StateMessage(
                    icon = Icons.Default.CloudOff, tint = MaterialTheme.colorScheme.error,
                    title = "Couldn't load ${playlist?.name ?: "this playlist"}",
                    message = "${s.message}. Check your connection; the mirror and the original source were both tried.",
                    primary = "Retry" to { attempt++ },
                    secondary = "Back" to onBack,
                    testTag = "playlist_error",
                )
                LoadState.Ready -> PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = { if (playlist != null && !playlist.isLocal) refresh() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().testTag("playlist_channels"),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (playlist != null) item(key = "info") {
                            PlaylistInfoCard(
                                playlist = playlist, updateAvailable = updateAvailable, refreshing = refreshing,
                                onAutoUpdate = { on -> launchSafe { PlaylistStore.setAutoUpdate(app, playlist.id, on); msg(if (on) "Auto-update on" else "Auto-update off — update manually") } },
                                onUpdate = { refresh(marketEntry?.sha256) },
                                onEnable = { launchSafe { PlaylistStore.setEnabled(app, playlist.id, true); attempt++ } },
                            )
                        }
                        if (playlist?.enabled != false && channels.isNotEmpty()) {
                            item(key = "filter") {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        query, { query = it }, singleLine = true, colors = streamFieldColors(),
                                        leadingIcon = { Icon(Icons.Default.Search, null, tint = TextSecondary) },
                                        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear search", tint = TextSecondary) } },
                                        placeholder = { Text("Search ${channels.size} streams") },
                                        modifier = Modifier.fillMaxWidth().testTag("channel_search"),
                                    )
                                    if (groups.size > 1) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        item(key = "all") { Chip("All", group == null) { group = null } }
                                        items(groups, key = { "g_$it" }) { g -> Chip(g, group == g) { group = if (group == g) null else g } }
                                    }
                                }
                            }
                        }
                        when {
                            playlist?.enabled == false -> Unit
                            channels.isEmpty() -> item(key = "empty") {
                                StateMessage(
                                    icon = Icons.Default.LiveTv, title = if (editable) "No streams yet" else "This playlist is empty",
                                    message = if (editable) "Save streams here from Videos → Live TV or from any other playlist." else "The source has no channels right now. Pull down to try again later.",
                                    primary = if (editable) null else "Refresh" to { refresh() },
                                    testTag = "playlist_empty",
                                )
                            }
                            visible.isEmpty() -> item(key = "no_match") {
                                StateMessage(
                                    icon = Icons.Default.SearchOff, title = "No matching streams",
                                    message = "Nothing matches \"${query.trim()}\"${group?.let { " in $it" } ?: ""}.",
                                    primary = "Clear filters" to { query = ""; group = null }, testTag = "playlist_no_match",
                                )
                            }
                            else -> itemsIndexed(visible, key = { _, v -> "ch_${v.index}" }, contentType = { _, _ -> "channel" }) { _, v ->
                                val (index, ch) = v
                                StreamRow(
                                    ch, subtitle = ch.group ?: ch.resolvedFormat().label,
                                    onClick = { runCatching { IncomingMedia.open(context, ch) }.onFailure { fail("Can't play this stream. ${dev.asfakul.yumeiro.online.OnlineErrors.friendly(it, "Try another channel.")}") } },
                                    onAdd = if (editable) null else ({ addTarget = ch }),
                                    onEditIcon = if (editable) ({ editChannel = index to ch }) else null,
                                    onDelete = if (editable) ({
                                        val id = playlist!!.id
                                        launchSafe {
                                            val next = PlaylistStore.removeChannel(app, id, index)
                                            StreamRepository.setPlaylist(playlist.name, next, id)
                                            AppMessages.show(context, "Removed ${ch.displayTitle}", actionLabel = "Undo", action = {
                                                launchSafe { val restored = PlaylistStore.addChannels(app, id, listOf(ch)); StreamRepository.setPlaylist(playlist.name, restored, id) }
                                            })
                                        }
                                    }) else null,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (showScrollTop && state == LoadState.Ready) {
            SmallFloatingActionButton(
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                containerColor = SpotifyGreen, contentColor = OnAccent,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 28.dp).testTag("scroll_top"),
            ) { Icon(Icons.Default.KeyboardArrowUp, "Back to top") }
        }
    }

    if (editing && playlist != null) {
        PlaylistEditDialog(
            playlist = playlist, remote = !playlist.isLocal, onDismiss = { editing = false },
            onSave = { name, u ->
                PlaylistStore.update(app, playlist.id, name, u)
                val updated = PlaylistStore.get(playlist.id)
                if (updated != null && updated.url != playlist.url) StreamRepository.setPlaylist(updated.name, PlaylistStore.channels(app, playlist.id, forceRefresh = true), playlist.id)
                else StreamRepository.setPlaylist(name.ifBlank { playlist.name }, StreamRepository.playlist.value, playlist.id)
            },
        )
    }
    if (deleting && playlist != null) {
        ConfirmDeleteDialog(playlist, onDismiss = { deleting = false }) {
            deleting = false
            StreamRepository.setPlaylist(null, emptyList())
            launchSafe { PlaylistStore.delete(app, playlist.id); AppMessages.show(context, "Deleted ${playlist.name}"); onBack() }
        }
    }
    addTarget?.let { item ->
        AddToPlaylistDialog(
            item = item, localPlaylists = playlists.filter { it.isLocal },
            onDismiss = { addTarget = null },
            onPick = { p -> addTarget = null; launchSafe { PlaylistStore.addChannels(app, p.id, listOf(item.copy(title = item.displayTitle))); AppMessages.success(context, "Saved to ${p.name}") } },
            onCreate = { name -> addTarget = null; launchSafe { val p = PlaylistStore.createLocal(app, name, listOf(item.copy(title = item.displayTitle))); AppMessages.success(context, "Saved to ${p.name}") } },
        )
    }
    editChannel?.let { (index, item) ->
        ChannelEditDialog(item, onDismiss = { editChannel = null }) { updated ->
            editChannel = null
            val p = playlist ?: return@ChannelEditDialog
            launchSafe { val next = PlaylistStore.updateChannel(app, p.id, index, updated); StreamRepository.setPlaylist(p.name, next, p.id) }
        }
    }
}

@Composable
private fun PlaylistInfoCard(
    playlist: StreamPlaylist,
    updateAvailable: Boolean,
    refreshing: Boolean,
    onAutoUpdate: (Boolean) -> Unit,
    onUpdate: () -> Unit,
    onEnable: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(20.dp), color = GlassFill, modifier = Modifier.fillMaxWidth().testTag("playlist_info")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            playlist.description?.let { Text(it, color = TextSecondary, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            val origin = when {
                playlist.isLocal -> "Your playlist · stored on this device"
                playlist.isMarket -> "From the playlist market" + (playlist.repo?.let { " · $it" } ?: "")
                playlist.isDefault -> "Default playlist · synced every 3 days"
                else -> "Added from a link"
            }
            Text(origin, color = TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            playlist.lastError?.let { Text("Last update failed: $it", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            if (!playlist.enabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("This playlist is disabled.", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(
                        "Enable", color = OnAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(SpotifyGreen).clickable(onClick = onEnable)
                            .padding(horizontal = 14.dp, vertical = 6.dp).testTag("detail_enable"),
                    )
                }
            }
            if (!playlist.isLocal) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Auto-update", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            if (playlist.autoUpdate) (if (playlist.isMarket) "Installs new versions automatically" else "Refreshes when older than 2 hours")
                            else "Only when you tap Refresh / Update",
                            color = TextTertiary, fontSize = 12.sp,
                        )
                    }
                    Switch(
                        checked = playlist.autoUpdate, onCheckedChange = onAutoUpdate,
                        colors = dev.asfakul.yumeiro.ui.theme.yumeSwitchColors(),
                        modifier = Modifier.testTag("detail_auto_update"),
                    )
                }
            }
            if (updateAvailable) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SpotifyGreen.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("A newer version is available", color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (refreshing) "Updating…" else "Update", color = OnAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(SpotifyGreen)
                            .clickable(enabled = !refreshing, onClick = onUpdate)
                            .padding(horizontal = 14.dp, vertical = 6.dp).testTag("detail_update"),
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(2.dp))
}
