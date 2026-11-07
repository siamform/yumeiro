/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.mtrevanced.ui.screens.stream

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.mtrevanced.stream.PlaylistMarket
import dev.asfakul.mtrevanced.stream.PlaylistStore
import dev.asfakul.mtrevanced.stream.StreamPlaylist
import dev.asfakul.mtrevanced.ui.components.AppMessages
import dev.asfakul.mtrevanced.ui.components.SkeletonBlock
import dev.asfakul.mtrevanced.ui.components.SkeletonRow
import dev.asfakul.mtrevanced.ui.components.StateMessage
import dev.asfakul.mtrevanced.ui.components.SubPageHeader
import dev.asfakul.mtrevanced.ui.theme.DarkSurface
import dev.asfakul.mtrevanced.ui.theme.GlassFill
import dev.asfakul.mtrevanced.ui.theme.OnAccent
import dev.asfakul.mtrevanced.ui.theme.SpotifyGreen
import dev.asfakul.mtrevanced.ui.theme.TextPrimary
import dev.asfakul.mtrevanced.ui.theme.TextSecondary
import dev.asfakul.mtrevanced.ui.theme.TextTertiary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val FILTER_INSTALLED = "__installed__"
private const val FILTER_UPDATES = "__updates__"

/**
 * Playlist Market (`stream/market`): IPTV playlists collected from GitHub, repaired and checked by
 * the `Playlist market` workflow. Install adds a playlist to the user's Stream playlists; installed
 * ones are compared with the catalog (sha256) and updated automatically or with Update / Update all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistMarketScreen(
    onBack: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext
    val scope = rememberCoroutineScope()
    val state by PlaylistMarket.state.collectAsState()
    val playlists by PlaylistStore.playlists.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var details by remember { mutableStateOf<PlaylistMarket.Entry?>(null) }
    var updatingAll by remember { mutableStateOf(false) }
    val busy = remember { mutableStateMapOf<String, Boolean>() }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { runCatching { PlaylistStore.init(app) }; PlaylistMarket.load(app) }

    fun reload() = scope.launch { PlaylistMarket.load(app, force = true) }

    val ready = state as? PlaylistMarket.State.Ready
    val catalog = ready?.catalog
    // The pre-installed default (Mrgify BDIX) is also listed in the market: show it as installed.
    val installedBy = remember(playlists) {
        playlists.filter { it.isDefault }.associateBy { it.id } + playlists.filter { it.isMarket }.associateBy { it.marketId!! }
    }
    val updates = remember(playlists, catalog) { catalog?.let { PlaylistMarket.updatesFor(playlists, it) }.orEmpty() }
    val updateIds = remember(updates) { updates.map { it.second.id }.toSet() }

    fun install(e: PlaylistMarket.Entry) {
        if (busy[e.id] == true) return
        busy[e.id] = true
        scope.launch {
            try {
                val p = PlaylistStore.installFromMarket(app, e)
                AppMessages.show(context, "Installed ${p.name} · ${p.channelCount} streams", AppMessages.Kind.SUCCESS, "Open") { onOpenPlaylist(p.id) }
            } catch (ex: CancellationException) { throw ex } catch (ex: Exception) {
                AppMessages.error(context, "Couldn't install ${e.name}: ${PlaylistMarket.friendlyError(ex)}", "Retry") { install(e) }
            } finally { busy.remove(e.id) }
        }
    }

    fun update(ids: Set<String>?) {
        val cat = catalog ?: return
        if (ids == null) updatingAll = true
        ids?.forEach { busy[it.removePrefix(PlaylistMarket.ID_PREFIX)] = true }
        scope.launch {
            try {
                val r = PlaylistStore.applyMarketUpdates(app, cat, onlyAuto = false, ids = ids)
                when {
                    r.updated.isEmpty() && r.failed.isEmpty() -> AppMessages.show(context, "Everything is up to date")
                    r.failed.isEmpty() -> AppMessages.success(context, "Updated ${r.updated.size} playlist${if (r.updated.size == 1) "" else "s"}")
                    else -> AppMessages.error(context, "Updated ${r.updated.size}, failed: ${r.failed.joinToString()}", "Retry") { update(ids) }
                }
            } catch (ex: CancellationException) { throw ex } catch (ex: Exception) {
                AppMessages.error(context, PlaylistMarket.friendlyError(ex))
            } finally {
                updatingAll = false
                ids?.forEach { busy.remove(it.removePrefix(PlaylistMarket.ID_PREFIX)) }
            }
        }
    }

    val visible = remember(catalog, query, filter, installedBy, updateIds) {
        val q = query.trim()
        catalog?.entries.orEmpty().filter { e ->
            when (filter) {
                null -> true
                FILTER_INSTALLED -> e.id in installedBy
                FILTER_UPDATES -> e.id in updateIds
                else -> e.category == filter
            } && (q.isEmpty() || e.name.contains(q, true) || e.category.contains(q, true) ||
                e.repo?.contains(q, true) == true || e.description?.contains(q, true) == true || e.country?.equals(q, true) == true)
        }
    }

    Column(modifier.fillMaxSize().testTag("market_screen")) {
        SubPageHeader(
            title = "Playlist market",
            subtitle = catalog?.let { c -> "${c.entries.size} playlists · catalog ${relativeTime(c.generated.takeIf { it > 0 } ?: c.fetchedAt)}" }
                ?: if (state is PlaylistMarket.State.Error) "Offline" else "Loading…",
            onBack = onBack,
        ) {
            val refreshing = ready?.refreshing == true || state is PlaylistMarket.State.Loading
            IconButton(onClick = { reload() }, enabled = !refreshing, modifier = Modifier.testTag("market_reload")) {
                if (refreshing) CircularProgressIndicator(color = SpotifyGreen, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                else Icon(Icons.Default.Refresh, "Check for updates", tint = TextSecondary)
            }
        }
        when (val s = state) {
            PlaylistMarket.State.Idle, PlaylistMarket.State.Loading -> Column(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("market_loading"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SkeletonBlock(null, 52.dp, shape = RoundedCornerShape(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { repeat(4) { SkeletonBlock(72.dp, 30.dp, shape = RoundedCornerShape(50)) } }
                repeat(6) { SkeletonRow(trailing = true) }
            }
            is PlaylistMarket.State.Error -> StateMessage(
                icon = Icons.Default.CloudOff, tint = MaterialTheme.colorScheme.error,
                title = "Market unavailable", message = s.message,
                primary = "Retry" to { reload() }, secondary = "Back" to onBack, testTag = "market_error",
            )
            is PlaylistMarket.State.Ready -> PullToRefreshBox(isRefreshing = s.refreshing, onRefresh = { reload() }, modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().testTag("market_list"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    s.warning?.let { w ->
                        item(key = "warning") {
                            Text(w, color = Color(0xFFFFB74D), fontSize = 12.sp, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(GlassFill).padding(10.dp).testTag("market_warning"))
                        }
                    }
                    if (updates.isNotEmpty()) item(key = "updates") {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SpotifyGreen.copy(alpha = 0.14f)).padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.SystemUpdate, null, tint = SpotifyGreen, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("${updates.size} update${if (updates.size == 1) "" else "s"} available", color = TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            PillButton(if (updatingAll) "Updating…" else "Update all", enabled = !updatingAll, tag = "market_update_all") { update(null) }
                        }
                    }
                    item(key = "search") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                query, { query = it }, singleLine = true, colors = streamFieldColors(),
                                leadingIcon = { Icon(Icons.Default.Search, null, tint = TextSecondary) },
                                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear search", tint = TextSecondary) } },
                                placeholder = { Text("Search playlists, countries, repos") },
                                modifier = Modifier.fillMaxWidth().testTag("market_search"),
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                item(key = "all") { Chip("All", filter == null) { filter = null } }
                                if (installedBy.isNotEmpty()) item(key = "installed") { Chip("Installed (${installedBy.size})", filter == FILTER_INSTALLED) { filter = FILTER_INSTALLED } }
                                if (updates.isNotEmpty()) item(key = "updates") { Chip("Updates (${updates.size})", filter == FILTER_UPDATES) { filter = FILTER_UPDATES } }
                                items(s.catalog.categories, key = { "c_$it" }) { c -> Chip(c, filter == c) { filter = if (filter == c) null else c } }
                            }
                        }
                    }
                    if (visible.isEmpty()) item(key = "none") {
                        StateMessage(
                            icon = Icons.Default.SearchOff, title = "No playlists found",
                            message = if (query.isNotBlank()) "Nothing matches \"${query.trim()}\"." else "Nothing in this section yet.",
                            primary = "Clear filters" to { query = ""; filter = null }, testTag = "market_empty",
                        )
                    } else items(visible, key = { "m_${it.id}" }, contentType = { "entry" }) { e ->
                        val installed = installedBy[e.id]
                        MarketCard(
                            entry = e, installed = installed, hasUpdate = e.id in updateIds, busy = busy[e.id] == true,
                            onClick = { details = e },
                            onInstall = { install(e) },
                            onOpen = { installed?.let { onOpenPlaylist(it.id) } },
                            onUpdate = { installed?.let { update(setOf(it.id)) } },
                        )
                    }
                    item(key = "note") {
                        Text(
                            "Playlists are collected from public GitHub repositories, repaired and checked every 6 hours. " +
                                "\"Responding\" is measured from GitHub's servers, so region-locked lists (e.g. BDIX) may work only in their country. " +
                                "Yumeiro does not host or bypass protection of any stream.",
                            color = TextTertiary, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }

    details?.let { e ->
        val installed = installedBy[e.id]
        AlertDialog(
            onDismissRequest = { details = null },
            containerColor = DarkSurface,
            title = { Text(e.name, color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    e.description?.let { Text(it, color = TextSecondary, fontSize = 13.sp) }
                    DetailLine("Category", e.category + (e.country?.let { " · $it" } ?: ""))
                    DetailLine("Streams", "${e.channels} in ${e.groups} groups")
                    e.health?.let { DetailLine("Responding", "${e.alive} of ${e.sampled} sampled streams") }
                    e.repo?.let { DetailLine("Repository", it + (e.stars?.let { s -> " · ★ $s" } ?: "")) }
                    if (e.updated > 0) DetailLine("Changed", relativeTime(e.updated))
                    if (e.topGroups.isNotEmpty()) DetailLine("Groups", e.topGroups.joinToString(", "))
                    if (!e.ok) Text("The source could not be reached on the last check; the last good copy is offered.", color = Color(0xFFFFB74D), fontSize = 12.sp)
                    e.repo?.let { r ->
                        TextButton(onClick = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/$r")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                .onFailure { AppMessages.error(context, "No browser available") }
                        }) { Text("View on GitHub", color = SpotifyGreen) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    details = null
                    if (installed != null) onOpenPlaylist(installed.id) else install(e)
                }, modifier = Modifier.testTag("market_details_action")) { Text(if (installed != null) "Open" else "Install", color = SpotifyGreen, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { details = null }) { Text("Close", color = TextSecondary) } },
        )
    }
}

@Composable
private fun DetailLine(k: String, v: String) {
    Row {
        Text(k, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.width(86.dp))
        Text(v, color = TextPrimary, fontSize = 12.sp)
    }
}

@Composable
private fun PillButton(label: String, enabled: Boolean = true, filled: Boolean = true, tag: String? = null, onClick: () -> Unit) {
    Text(
        label, color = if (filled) OnAccent else SpotifyGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(if (filled) SpotifyGreen.copy(alpha = if (enabled) 1f else 0.5f) else SpotifyGreen.copy(alpha = 0.14f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
            .let { if (tag != null) it.testTag(tag) else it },
    )
}

@Composable
private fun MarketCard(
    entry: PlaylistMarket.Entry,
    installed: StreamPlaylist?,
    hasUpdate: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    onInstall: () -> Unit,
    onOpen: () -> Unit,
    onUpdate: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(20.dp), color = GlassFill, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("market_${entry.id}")) {
        Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(SpotifyGreen.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Text(entry.country ?: entry.name.take(2).uppercase(), color = SpotifyGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.name, color = TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(entry.category, "${entry.channels} streams", entry.updated.takeIf { it > 0 }?.let { relativeTime(it) }).joinToString(" · "),
                    color = TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    entry.stars?.let {
                        Icon(Icons.Default.Star, null, tint = TextTertiary, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("$it", color = TextTertiary, fontSize = 11.sp)
                        Spacer(Modifier.width(6.dp))
                    }
                    entry.repo?.let { Text(it, color = TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)) }
                    entry.health?.let { h ->
                        Spacer(Modifier.width(6.dp))
                        val c = when { h >= 0.6f -> SpotifyGreen; h >= 0.25f -> Color(0xFFFFB74D); else -> TextTertiary }
                        Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(c))
                        Spacer(Modifier.width(3.dp))
                        Text("${(h * 100).toInt()}%", color = TextTertiary, fontSize = 11.sp)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            when {
                busy -> CircularProgressIndicator(color = SpotifyGreen, strokeWidth = 2.dp, modifier = Modifier.size(24.dp).testTag("market_busy_${entry.id}"))
                installed != null && hasUpdate -> PillButton("Update", tag = "market_update_${entry.id}", onClick = onUpdate)
                installed != null -> PillButton("Open", filled = false, tag = "market_open_${entry.id}", onClick = onOpen)
                else -> Row(
                    Modifier.clip(RoundedCornerShape(50)).background(SpotifyGreen).clickable(onClick = onInstall)
                        .padding(horizontal = 12.dp, vertical = 7.dp).testTag("market_install_${entry.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Download, null, tint = OnAccent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Install", color = OnAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}
