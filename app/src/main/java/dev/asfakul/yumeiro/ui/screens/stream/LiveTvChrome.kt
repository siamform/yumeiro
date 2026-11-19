/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.stream

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.stream.LiveChannelFeed
import dev.asfakul.yumeiro.stream.StreamPlaylist
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary

/*
 * NBUG-15 (a)-(c), ADR-028: the Live TV page chrome. Header buttons (Filter, Tools), pinned playlist chips,
 * category chips, the merged feed's rows and footer, and the Tools modal. The playlist management page is
 * StreamScreen's "playlists" pane (it shares the card actions and dialogs with the old feed).
 */

/** A labelled header button with a 48 dp target; [active] marks a filter in use. */
@Composable
internal fun LiveHeaderButton(text: String, icon: ImageVector, tag: String, onClick: () -> Unit, active: Boolean = false, description: String = text) {
    val pal = LocalMtPalette.current
    Row(
        Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(50))
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(if (active) pal.accent.copy(alpha = 0.16f) else GlassFill)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = if (active) pal.accent else TextPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text, color = if (active) pal.accent else TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 120.dp),
            )
        }
    }
}

/** A small chip with a 48 dp target (pinned playlists and categories). */
@Composable
private fun LiveChip(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    val pal = LocalMtPalette.current
    Box(
        Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(50))
            .clickable(role = Role.Tab, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label + if (selected) ", selected" else "" }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, color = if (selected) pal.background else TextSecondary, fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 200.dp).clip(RoundedCornerShape(50))
                .background(if (selected) TextPrimary else TextPrimary.copy(alpha = 0.08f)).padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

/** One-tap chips for the pinned playlists (like Yumix's pinned extensions). Tap again for all. */
@Composable
internal fun LivePinnedBar(pins: List<StreamPlaylist>, selected: String?, onPick: (String?) -> Unit) {
    if (pins.isEmpty()) return
    LazyRow(Modifier.fillMaxWidth().testTag("live_pinned_bar"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item("all") { LiveChip("All", selected == null, "live_pinned_all") { onPick(null) } }
        items(pins, key = { it.id }) { p -> LiveChip(p.name, selected == p.id, "live_pinned_${p.id}") { onPick(if (selected == p.id) null else p.id) } }
    }
}

/** Category chips: "All · N", then every category with its count (scrolls sideways, nothing clipped). */
@Composable
internal fun LiveCategoryBar(categories: List<LiveChannelFeed.Category>, total: Int, selected: String?, onPick: (String?) -> Unit) {
    LazyRow(Modifier.fillMaxWidth().testTag("live_categories"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item("all") { LiveChip("All · $total", selected == null, "live_cat_all") { onPick(null) } }
        items(categories, key = { "c_" + it.name }) { c ->
            LiveChip("${c.name} · ${c.count}", selected.equals(c.name, ignoreCase = true), "live_cat_${c.name}") { onPick(if (selected.equals(c.name, ignoreCase = true)) null else c.name) }
        }
    }
}

/** The merged feed's rows (category headings and tile rows) for a LazyColumn. */
internal fun LazyListScope.liveFeedItems(rows: List<LiveChannelFeed.Row>, columns: Int, onPlay: (LiveChannelFeed.Entry) -> Unit) {
    items(rows, key = { it.key }, contentType = { if (it is LiveChannelFeed.Row.Header) "live_cat_h" else "live_row" }) { r ->
        when (r) {
            is LiveChannelFeed.Row.Header -> Row(
                Modifier.fillMaxWidth().padding(top = 10.dp).semantics(mergeDescendants = true) { heading() }.testTag("live_cat_h_${r.name}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(r.name, color = TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(8.dp))
                Text("${r.count}", color = TextTertiary, fontSize = 12.sp, maxLines = 1)
            }
            is LiveChannelFeed.Row.Tiles -> Row(Modifier.fillMaxWidth().padding(top = 10.dp).testTag("live_grid_row"), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                r.entries.forEach { e -> LiveChannelTile(e.channel, onClick = { onPlay(e) }, modifier = Modifier.weight(1f)) }
                repeat(columns - r.entries.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Under the feed: how many are shown, and whether playlists are still loading. */
@Composable
internal fun LiveFeedFooter(shown: Int, total: Int, loading: Int) {
    val text = buildString {
        append(if (shown < total) "Showing $shown of $total · more load as you scroll" else "All $total channels")
        if (loading > 0) append(" · loading $loading more playlist" + if (loading == 1) "" else "s")
    }
    Text(text, color = TextTertiary, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("live_feed_footer"))
}

/** Every Live TV tool in one modal (full height on TV, where a remote cannot drag a sheet up). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiveToolsSheet(tools: List<LiveTool>, onDismiss: () -> Unit) {
    val pal = LocalMtPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = dev.asfakul.yumeiro.Edition.isTv)
    ModalBottomSheet(
        onDismissRequest = onDismiss, containerColor = pal.surface, sheetState = sheetState,
        shape = dev.asfakul.yumeiro.ui.theme.Yume.shapeSheet,
        dragHandle = dev.asfakul.yumeiro.ui.components.sheetDragHandle(),
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 20.dp).testTag("live_tools_sheet"),
        ) {
            Text("Tools", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            Text("Links, files and playlists", color = TextSecondary, fontSize = 12.sp)
            Spacer(Modifier.size(14.dp))
            LiveToolsGrid(tools.map { t -> t.copy(onClick = { onDismiss(); t.onClick() }) })
        }
    }
}

/** Section label on the Playlists page ("On", "Off"). */
@Composable
internal fun PlaylistsSectionLabel(text: String, tag: String) {
    Text(
        text, color = TextTertiary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp).semantics { heading() }.testTag(tag),
    )
}

