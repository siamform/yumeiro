/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.stream

import dev.asfakul.yumeiro.ui.components.TsukiDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.stream.PlaylistAnalysis
import dev.asfakul.yumeiro.stream.PlaylistStore
import dev.asfakul.yumeiro.stream.StreamPlaylist
import dev.asfakul.yumeiro.stream.StreamRequest
import dev.asfakul.yumeiro.ui.theme.DarkOutline
import dev.asfakul.yumeiro.ui.theme.DarkSurface
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.OnAccent
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun streamFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = SpotifyGreen, unfocusedBorderColor = DarkOutline,
    focusedLabelColor = SpotifyGreen, cursorColor = SpotifyGreen,
    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
)

internal fun relativeTime(ms: Long, now: Long = System.currentTimeMillis()): String {
    if (ms <= 0L) return "not downloaded yet"
    val m = ((now - ms) / 60_000L).coerceAtLeast(0)
    return when {
        m < 1 -> "just now"
        m < 60 -> "${m}m ago"
        m < 48 * 60 -> "${m / 60}h ago"
        else -> "${m / 1440}d ago"
    }
}

private fun isHttpUrl(s: String) = Regex("^https?://\\S+", RegexOption.IGNORE_CASE).matches(s.trim())

/** One saved playlist: tap to open, switch to enable/disable, menu for refresh / edit / export / delete. */
@Composable
internal fun PlaylistCard(
    playlist: StreamPlaylist,
    busy: Boolean,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(20.dp), color = GlassFill,
        modifier = Modifier.fillMaxWidth().testTag("playlist_${playlist.id}")
            .alpha(if (playlist.enabled) 1f else 0.55f)
            .clickable(enabled = playlist.enabled && !busy, onClick = onOpen),
    ) {
        Row(Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(SpotifyGreen.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                if (busy) CircularProgressIndicator(color = SpotifyGreen, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                else Icon(
                    when { playlist.isLocal -> Icons.Default.Folder; playlist.isDefault -> Icons.Default.LiveTv; else -> Icons.Default.Public },
                    null, tint = SpotifyGreen, modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(playlist.name, color = TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (playlist.isDefault) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Default", color = OnAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(SpotifyGreen).padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
                val count = if (playlist.channelCount > 0) "${playlist.channelCount} streams" else if (playlist.isLocal) "Empty" else "Tap to load"
                val parts = listOfNotNull(
                    playlist.category, count,
                    when { playlist.isLocal -> "on this device"; playlist.lastUpdated > 0 -> relativeTime(playlist.lastUpdated); else -> null },
                )
                Text(parts.joinToString(" · "), color = TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                playlist.lastError?.takeIf { playlist.enabled }?.let {
                    Text("Last update failed: $it", color = MaterialTheme.colorScheme.error, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Switch(
                checked = playlist.enabled, onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedThumbColor = OnAccent, checkedTrackColor = SpotifyGreen),
                modifier = Modifier.testTag("playlist_toggle_${playlist.id}"),
            )
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.testTag("playlist_menu_${playlist.id}")) {
                    Icon(Icons.Default.MoreVert, "Playlist options", tint = TextSecondary)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = DarkSurface) {
                    if (playlist.enabled) DropdownMenuItem(text = { Text("Open") }, leadingIcon = { Icon(Icons.Default.PlaylistPlay, null) }, onClick = { menu = false; onOpen() })
                    if (!playlist.isLocal) DropdownMenuItem(text = { Text("Refresh now") }, leadingIcon = { Icon(Icons.Default.Refresh, null) }, onClick = { menu = false; onRefresh() })
                    DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; onEdit() }, modifier = Modifier.testTag("playlist_edit"))
                    DropdownMenuItem(text = { Text("Export as .m3u") }, leadingIcon = { Icon(Icons.Default.FileDownload, null) }, onClick = { menu = false; onExport() })
                    DropdownMenuItem(
                        text = { Text(if (playlist.enabled) "Disable" else "Enable") },
                        leadingIcon = { Icon(if (playlist.enabled) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) },
                        onClick = { menu = false; onToggle(!playlist.enabled) },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onDelete() }, modifier = Modifier.testTag("playlist_delete"),
                    )
                }
            }
        }
    }
}

/** Header of the playlists section with Add, New and Sync actions. */
@Composable
internal fun PlaylistsHeader(
    lastSync: Long,
    syncing: Boolean,
    onMarket: () -> Unit = {},
    onAddUrl: () -> Unit,
    onNew: () -> Unit,
    onSync: () -> Unit,
    onRestore: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PlaylistPlay, null, tint = SpotifyGreen, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Playlists", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(if (lastSync > 0) "Synced ${relativeTime(lastSync)}" else "Default updates every 3 days", color = TextTertiary, fontSize = 11.sp)
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.testTag("playlists_more")) { Icon(Icons.Default.MoreVert, "More playlist actions", tint = TextSecondary) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = DarkSurface) {
                    DropdownMenuItem(text = { Text("Playlist market") }, leadingIcon = { Icon(Icons.Default.Storefront, null) }, onClick = { menu = false; onMarket() }, modifier = Modifier.testTag("playlists_market"))
                    DropdownMenuItem(text = { Text("Restore default playlist") }, leadingIcon = { Icon(Icons.Default.Refresh, null) }, onClick = { menu = false; onRestore() }, modifier = Modifier.testTag("playlists_restore"))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionPill("Add URL", Icons.Default.Add, Modifier.weight(1f).testTag("playlist_add_url"), onAddUrl)
            ActionPill("New", Icons.Default.PlaylistAdd, Modifier.weight(1f).testTag("playlist_new"), onNew)
            ActionPill(if (syncing) "Syncing" else "Sync latest", Icons.Default.CloudSync, Modifier.weight(1.2f).testTag("playlist_sync"), onSync, busy = syncing)
        }
    }
}

@Composable
private fun ActionPill(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit, busy: Boolean = false) {
    Surface(shape = RoundedCornerShape(50), color = GlassFill, modifier = modifier.clip(RoundedCornerShape(50)).clickable(enabled = !busy, onClick = onClick)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (busy) CircularProgressIndicator(color = SpotifyGreen, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            else Icon(icon, null, tint = SpotifyGreen, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/**
 * Add (playlist == null) or edit a playlist. Remote playlists can be analysed first: the link is
 * followed through redirects and the result (status, type, size, channels, groups) is shown.
 */
@Composable
internal fun PlaylistEditDialog(
    playlist: StreamPlaylist?,
    remote: Boolean,
    onDismiss: () -> Unit,
    onSave: suspend (name: String, url: String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(playlist?.name.orEmpty()) }
    var url by remember { mutableStateOf(playlist?.url.orEmpty()) }
    var analysis by remember { mutableStateOf<PlaylistAnalysis?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val colors = streamFieldColors()
    val title = when { playlist != null -> "Edit playlist"; remote -> "Add playlist from URL"; else -> "New playlist" }

    fun run(block: suspend () -> Unit) {
        busy = true; error = null
        scope.launch {
            try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message ?: "Something went wrong" }
            busy = false
        }
    }

    TsukiDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = DarkSurface,
        title = { Text(title, color = TextPrimary) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, colors = colors, modifier = Modifier.fillMaxWidth().testTag("playlist_name_field"))
                if (remote) {
                    OutlinedTextField(
                        url, { url = it; analysis = null; error = null }, label = { Text("Playlist URL (.m3u)") }, singleLine = true,
                        placeholder = { Text("https://raw.githubusercontent.com/…/playlist.m3u") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), colors = colors,
                        modifier = Modifier.fillMaxWidth().testTag("playlist_url_field"),
                    )
                    TextButton(onClick = { run { analysis = PlaylistStore.analyze(url) } }, enabled = !busy && isHttpUrl(url)) {
                        Text("Analyse link", color = SpotifyGreen)
                    }
                    analysis?.let { AnalysisSummary(it) }
                    if (playlist?.isDefault == true) Text("Default playlist: changing the URL stops it following the 2-hour sync.", color = TextTertiary, fontSize = 11.sp)
                }
                if (busy) CircularProgressIndicator(color = SpotifyGreen, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (remote && !isHttpUrl(url)) { error = "Enter a valid http(s):// link"; return@TextButton }
                    run { onSave(name.trim(), if (remote) url.trim() else null); onDismiss() }
                },
                enabled = !busy, modifier = Modifier.testTag("playlist_save"),
            ) { Text(if (playlist == null) (if (remote) "Add" else "Create") else "Save", color = SpotifyGreen) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel", color = TextSecondary) } },
    )
}

@Composable
private fun AnalysisSummary(a: PlaylistAnalysis) {
    Surface(shape = RoundedCornerShape(14.dp), color = GlassFill) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            fun kb(b: Int) = if (b > 1_048_576) "%.1f MB".format(b / 1_048_576f) else "${b / 1024} KB"
            Line("Result", if (a.isHls) "Single HLS stream (not a channel list)" else "${a.channels} streams in ${a.groups.size}${if (a.groups.size >= 12) "+" else ""} groups")
            Line("HTTP", "${a.status} · ${a.contentType ?: "unknown type"} · ${kb(a.bytes)}")
            Line("Redirects", if (a.redirects.isEmpty()) "none (direct)" else "${a.redirects.size} → ${a.finalUrl}")
            if (a.drmChannels > 0 || a.headerChannels > 0) Line("Extras", "${a.drmChannels} DRM · ${a.headerChannels} with custom headers")
            a.lastUpdatedNote?.let { Line("Source", it) }
            a.epgUrl?.let { Line("EPG", it) }
            if (a.groups.isNotEmpty()) Line("Top groups", a.groups.take(6).joinToString(", ") { "${it.first} (${it.second})" })
        }
    }
}

@Composable
private fun Line(k: String, v: String) {
    Row {
        Text(k, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.width(78.dp))
        Text(v, color = TextPrimary, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

/** Pick one of the user's own playlists (or create a new one) to save [item] into. */
@Composable
internal fun AddToPlaylistDialog(
    item: StreamRequest,
    localPlaylists: List<StreamPlaylist>,
    onDismiss: () -> Unit,
    onPick: (StreamPlaylist) -> Unit,
    onCreate: (String) -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    TsukiDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { Text("Save to playlist", color = TextPrimary) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.displayTitle, color = TextSecondary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                localPlaylists.forEach { p ->
                    Surface(shape = RoundedCornerShape(14.dp), color = GlassFill, modifier = Modifier.fillMaxWidth().clickable { onPick(p) }.testTag("pick_${p.id}")) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Folder, null, tint = SpotifyGreen, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(p.name, color = TextPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${p.channelCount}", color = TextTertiary, fontSize = 12.sp)
                        }
                    }
                }
                OutlinedTextField(
                    newName, { newName = it }, label = { Text("New playlist name") }, singleLine = true,
                    colors = streamFieldColors(), modifier = Modifier.fillMaxWidth().testTag("new_playlist_name"),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(newName.trim().ifBlank { "My playlist" }) }, modifier = Modifier.testTag("create_and_add")) {
                Text("Create & add", color = SpotifyGreen)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } },
    )
}

/** Edit a channel of one of the user's own playlists. */
@Composable
internal fun ChannelEditDialog(item: StreamRequest, onDismiss: () -> Unit, onSave: (StreamRequest) -> Unit) {
    var title by remember { mutableStateOf(item.title) }
    var url by remember { mutableStateOf(item.url) }
    var group by remember { mutableStateOf(item.group.orEmpty()) }
    var logo by remember { mutableStateOf(item.logoUrl.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    val colors = streamFieldColors()
    TsukiDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { Text("Edit stream", color = TextPrimary) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, colors = colors, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(url, { url = it; error = null }, label = { Text("Stream URL") }, singleLine = true, colors = colors, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(group, { group = it }, label = { Text("Group") }, singleLine = true, colors = colors, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(logo, { logo = it }, label = { Text("Logo URL") }, singleLine = true, colors = colors, modifier = Modifier.fillMaxWidth())
                Text("Headers and DRM are kept as they are.", color = TextTertiary, fontSize = 11.sp)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (!Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://\\S+").matches(url.trim())) { error = "Enter a valid stream URL"; return@TextButton }
                onSave(item.copy(title = title.trim(), url = url.trim(), group = group.trim().ifBlank { null }, logoUrl = logo.trim().ifBlank { null }))
            }) { Text("Save", color = SpotifyGreen) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } },
    )
}

@Composable
internal fun ConfirmDeleteDialog(playlist: StreamPlaylist, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    TsukiDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { Text("Delete ${playlist.name}?", color = TextPrimary) },
        text = {
            Text(
                if (playlist.isDefault) "This default playlist will be removed. You can bring it back with Restore default playlist."
                else "The playlist and its saved streams will be removed from this device.",
                color = TextSecondary,
            )
        },
        confirmButton = { TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm_delete")) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } },
    )
}

/** Entry point to the Playlist Market from the Stream tab. */
@Composable
internal fun MarketEntryCard(installed: Int, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp), color = SpotifyGreen.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).testTag("market_entry"),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(SpotifyGreen), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Storefront, null, tint = OnAccent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Playlist market", color = TextPrimary, fontWeight = FontWeight.Bold)
                Text(
                    if (installed > 0) "$installed installed · find more IPTV playlists from GitHub" else "Install IPTV playlists collected from GitHub",
                    color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TextSecondary)
        }
    }
}

/** The temporary list opened from a link or file (not saved yet). */
@Composable
internal fun OpenedListCard(name: String?, count: Int, onOpen: () -> Unit, onClose: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = GlassFill, modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).testTag("opened_list")) {
        Row(Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(SpotifyGreen.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlaylistPlay, null, tint = SpotifyGreen, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name ?: "Opened list", color = TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$count streams · opened, not saved", color = TextTertiary, fontSize = 12.sp)
            }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close list", tint = TextSecondary) }
        }
    }
}
