/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.stream

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.asfakul.yumeiro.stream.StreamPlaylist
import dev.asfakul.yumeiro.stream.StreamRequest
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary

/*
 * Round 8 — Live TV home.
 *
 * Before: the page opened on a URL form and a list of playlist *cards*; the channels of the
 * default playlist were two taps away (open the card → its own page). Now Live TV starts like
 * Yumix: a strip of playlists ("All" first), then the channels themselves as a logo grid. The
 * tools (play a link, M3U file, market, websites, add / new playlist, sync, headers & DRM) are
 * organised shortcuts, and the link player is its own page.
 */

/** Channels of one playlist as the hub sees them. */
@Immutable
internal data class LiveLoad(val loading: Boolean = true, val channels: List<StreamRequest> = emptyList(), val error: String? = null)

/** One shortcut tile in the Live TV "Tools" grid. */
@Immutable
internal data class LiveTool(val tag: String, val title: String, val status: String, val icon: ImageVector, val hue: Color, val onClick: () -> Unit)

/** Stable per-playlist colour for its avatar. */
internal fun liveHue(name: String): Color {
    val hues = listOf(0xFFD9668A, 0xFFD9553F, 0xFF8F6CC9, 0xFFE0A030, 0xFF4FA87A, 0xFF4C8FD6, 0xFF5866C2, 0xFF3E9E94)
    return Color(hues[(name.hashCode() and 0x7fffffff) % hues.size])
}

/** "Stories"-like row of playlists: All first, then one avatar per enabled playlist. */
@Composable
internal fun LivePlaylistStrip(playlists: List<StreamPlaylist>, loads: Map<String, LiveLoad>, selected: String?, onSelect: (String?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.testTag("live_playlist_strip"),
    ) {
        item("all") { LiveAvatarItem("All", null, selected == null, loading = false, all = true) { onSelect(null) } }
        items(playlists, key = { "pl_strip_" + it.id }) { p ->
            LiveAvatarItem(p.name, p.id, selected == p.id, loading = loads[p.id]?.loading == true) { onSelect(if (selected == p.id) null else p.id) }
        }
    }
}

@Composable
private fun LiveAvatarItem(name: String, id: String?, selected: Boolean, loading: Boolean, all: Boolean = false, onClick: () -> Unit) {
    val pal = LocalMtPalette.current
    val ring = if (selected) Brush.linearGradient(listOf(pal.accent, pal.accent2)) else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.04f)))
    Column(
        Modifier.width(66.dp).clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Tab, onClickLabel = "Show $name", onClick = onClick)
            .semantics { contentDescription = name + if (selected) ", selected" else "" }
            .testTag(if (id == null) "live_strip_all" else "live_strip_$id"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(58.dp).clip(CircleShape).background(ring).padding(if (selected) 2.5.dp else 1.dp), contentAlignment = Alignment.Center) {
            val hue = if (all) pal.accent else liveHue(name)
            Box(
                Modifier.fillMaxSize().clip(CircleShape).background(Brush.linearGradient(listOf(hue, hue.copy(alpha = 0.65f)))),
                contentAlignment = Alignment.Center,
            ) {
                if (all) Icon(Icons.Default.LiveTv, null, tint = Color.White, modifier = Modifier.size(24.dp))
                else Text(name.trim().take(2).uppercase().ifBlank { "TV" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1)
            }
            if (loading) Box(Modifier.align(Alignment.BottomEnd).size(12.dp).clip(CircleShape).background(pal.accent).border(2.dp, pal.background, CircleShape))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            name, color = if (selected) TextPrimary else TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

/** One channel: logo on a glass tile (logos are fitted, never cropped), a live dot and the name. */
@Composable
internal fun LiveChannelTile(ch: StreamRequest, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).clickable(onClickLabel = "Play ${ch.displayTitle}", onClick = onClick)
            .padding(bottom = 6.dp).testTag("live_channel"),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 11f).clip(RoundedCornerShape(16.dp)).background(GlassFill)
                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                ch.displayTitle.trim().take(2).uppercase(), color = TextTertiary, fontWeight = FontWeight.Black, fontSize = 18.sp, maxLines = 1,
            )
            if (!ch.logoUrl.isNullOrBlank()) AsyncImage(
                model = ch.logoUrl, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Row(
                Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 5.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(5.dp).clip(CircleShape).background(Color(0xFFFF4D5E)))
                Spacer(Modifier.width(3.dp))
                Text("LIVE", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, lineHeight = 10.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(ch.displayTitle, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp))
        Text(ch.group?.takeIf { it.isNotBlank() } ?: " ", color = TextTertiary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp))
    }
}

@Composable
private fun LiveTileSkeleton(modifier: Modifier) {
    Column(modifier) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 11f).clip(RoundedCornerShape(16.dp)).background(GlassFill))
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth(0.7f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(GlassFill))
    }
}

/** Slim header for one playlist on "All": avatar, name, channel count and "View". */
@Composable
internal fun LiveSectionHeader(p: StreamPlaylist, count: Int?, onView: () -> Unit) {
    val pal = LocalMtPalette.current
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(14.dp))
            .clickable(onClickLabel = "Show ${p.name}", onClick = onView).padding(vertical = 4.dp).testTag("live_section_header"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val hue = liveHue(p.name)
        Box(Modifier.size(30.dp).clip(CircleShape).background(hue), contentAlignment = Alignment.Center) {
            Text(p.name.trim().take(2).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, color = TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(count?.let { "$it channels" } ?: "Loading…", color = TextTertiary, fontSize = 11.sp, maxLines = 1)
        }
        Text("View", color = pal.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp))
    }
}

/**
 * Adds the channel hub to a LazyColumn: "All" = one shelf per playlist; a selected playlist = its
 * group chips and an endless logo grid ([limit] grows as the list nears its end).
 */
internal fun LazyListScope.liveHubItems(
    playlists: List<StreamPlaylist>,
    loads: Map<String, LiveLoad>,
    selected: String?,
    group: String?,
    limit: Int,
    columns: Int,
    onSelect: (String?) -> Unit,
    onGroup: (String?) -> Unit,
    onPlay: (StreamPlaylist, List<StreamRequest>, StreamRequest) -> Unit,
    onOpenFull: (StreamPlaylist) -> Unit,
    onRetry: (StreamPlaylist) -> Unit,
) {
    val sel = playlists.firstOrNull { it.id == selected }
    if (sel == null) {
        playlists.forEach { p ->
            val load = loads[p.id]
            item(key = "live_sec_h_${p.id}", contentType = "live_header") { LiveSectionHeader(p, load?.channels?.size?.takeIf { load.loading.not() }) { onSelect(p.id) } }
            item(key = "live_sec_${p.id}", contentType = "live_shelf") {
                when {
                    load == null || (load.loading && load.channels.isEmpty()) -> Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        repeat(3) { LiveTileSkeleton(Modifier.weight(1f)) }
                    }
                    load.channels.isEmpty() -> LiveProblem(load.error ?: "No channels right now", onRetry = { onRetry(p) })
                    else -> LazyRow(Modifier.padding(top = 8.dp).testTag("live_shelf"), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(load.channels.take(16), key = { i, c -> "${i}_${c.url}" }) { _, ch ->
                            LiveChannelTile(ch, onClick = { onPlay(p, load.channels, ch) }, modifier = Modifier.width(124.dp))
                        }
                        if (load.channels.size > 16) item("more") {
                            Box(
                                Modifier.width(96.dp).aspectRatio(16f / 11f).clip(RoundedCornerShape(16.dp)).background(GlassFill)
                                    .clickable(onClickLabel = "All channels of ${p.name}") { onSelect(p.id) }.testTag("live_shelf_more"),
                                contentAlignment = Alignment.Center,
                            ) { Text("+${load.channels.size - 16}\nmore", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
                        }
                    }
                }
            }
        }
        return
    }
    val load = loads[sel.id]
    val all = load?.channels.orEmpty()
    val groups = all.mapNotNull { it.group?.takeIf { g -> g.isNotBlank() } }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(24).map { it.key }
    if (groups.size > 1) item(key = "live_groups_${sel.id}", contentType = "live_groups") {
        LazyRow(Modifier.padding(top = 4.dp).testTag("live_groups"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item("all") { Chip("All · ${all.size}", selected = group == null) { onGroup(null) } }
            items(groups, key = { "g_$it" }) { g -> Chip(g, selected = group == g) { onGroup(if (group == g) null else g) } }
        }
    }
    val shown = if (group == null) all else all.filter { it.group == group }
    when {
        load == null || (load.loading && all.isEmpty()) -> items(3, key = { "live_skel_$it" }) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { repeat(columns) { LiveTileSkeleton(Modifier.weight(1f)) } }
        }
        all.isEmpty() -> item(key = "live_problem_${sel.id}") { LiveProblem(load.error ?: "This playlist has no channels right now", onRetry = { onRetry(sel) }) }
        else -> {
            val rows = shown.take(limit).withIndex().chunked(columns)
            items(rows, key = { r -> "live_row_${sel.id}_${group}_${r.first().index}" }, contentType = { "live_row" }) { r ->
                Row(Modifier.fillMaxWidth().padding(top = 10.dp).testTag("live_grid_row"), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    r.forEach { (_, ch) -> LiveChannelTile(ch, onClick = { onPlay(sel, all, ch) }, modifier = Modifier.weight(1f)) }
                    repeat(columns - r.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            item(key = "live_full_${sel.id}") {
                Box(
                    Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp)).background(GlassFill)
                        .clickable(onClickLabel = "Open ${sel.name}") { onOpenFull(sel) }.testTag("live_open_full"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (shown.size > limit) "Showing ${limit.coerceAtMost(shown.size)} of ${shown.size} · search, edit and save on the playlist page"
                        else "Search, edit and save on the playlist page",
                        color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveProblem(message: String, onRetry: () -> Unit) {
    val pal = LocalMtPalette.current
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).background(GlassFill).padding(horizontal = 14.dp, vertical = 10.dp).testTag("live_problem"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("Retry", color = pal.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onRetry).padding(horizontal = 12.dp, vertical = 10.dp))
    }
}

/** The organised Live TV tools: a 4-column grid of small seals with a name and a status. */
@Composable
internal fun LiveToolsGrid(tools: List<LiveTool>, columns: Int = 4) {
    Column(Modifier.fillMaxWidth().testTag("live_tools"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tools.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { t ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                            .clickable(role = Role.Button, onClickLabel = t.title, onClick = t.onClick)
                            .semantics(mergeDescendants = true) { contentDescription = "${t.title}, ${t.status}" }
                            .padding(vertical = 4.dp).testTag(t.tag),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(50.dp).clip(RoundedCornerShape(17.dp)).background(Brush.linearGradient(listOf(t.hue, t.hue.copy(alpha = 0.72f)))),
                            contentAlignment = Alignment.Center,
                        ) { Icon(t.icon, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
                        Spacer(Modifier.height(6.dp))
                        Text(t.title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        Text(t.status, color = TextTertiary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
