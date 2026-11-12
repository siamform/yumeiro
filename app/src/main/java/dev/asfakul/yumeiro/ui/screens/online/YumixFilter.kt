/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette

internal sealed interface SourceStatus {
    data object Loading : SourceStatus
    data object Failed : SourceStatus
    data class Ready(val titles: Int) : SourceStatus
}

/**
 * Round 10: "Show videos from" — the extension filter. A single list (radio rows) replaces the
 * horizontal strip of round shortcuts, so 30 extensions stay readable and nothing is clipped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SourceFilterSheet(
    names: List<String>,
    selected: String?,
    status: (String) -> SourceStatus,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalMtPalette.current
    ModalBottomSheet(
        onDismissRequest = onDismiss, containerColor = p.surface,
        shape = dev.asfakul.yumeiro.ui.theme.Yume.shapeSheet,
        dragHandle = { dev.asfakul.yumeiro.ui.components.MoonSheetHandle() },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().testTag("online_filter_sheet")) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Show videos from", color = p.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("${names.size} extensions on · pick one to filter Yumix", color = p.textSecondary, fontSize = 12.sp)
                }
                if (selected != null) TextButton(onClick = { onPick(null) }, modifier = Modifier.testTag("online_filter_clear")) {
                    Text("Clear", color = p.accent, fontWeight = FontWeight.SemiBold)
                }
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                item("all") { FilterRow(null, "All extensions", "Everything mixed in one feed", selected == null) { onPick(null) } }
                items(names, key = { it }) { n ->
                    val sub = when (val st = status(n)) {
                        SourceStatus.Loading -> "Loading…"
                        SourceStatus.Failed -> "Didn't load — open it to retry"
                        is SourceStatus.Ready -> if (st.titles == 0) "Search only" else "${st.titles} titles on its home"
                    }
                    FilterRow(n, n, sub, selected == n, loading = status(n) == SourceStatus.Loading, failed = status(n) == SourceStatus.Failed) { onPick(n) }
                }
            }
        }
    }
}

@Composable
private fun FilterRow(name: String?, title: String, sub: String, sel: Boolean, loading: Boolean = false, failed: Boolean = false, onClick: () -> Unit) {
    val p = LocalMtPalette.current
    val bg by animateColorAsState(if (sel) p.accent.copy(alpha = 0.14f) else Color.Transparent, label = "filter_bg")
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp).background(bg, RoundedCornerShape(16.dp))
            .selectable(selected = sel, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag(if (name == null) "online_filter_all" else "online_filter_$name"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelAvatar(name ?: "All", selected = sel, size = 40.dp, mix = name == null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = p.textPrimary, fontSize = 15.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = if (failed) WarnColor else p.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when {
            sel -> Icon(Icons.Default.Check, "Selected", tint = p.accent, modifier = Modifier.size(22.dp))
            loading -> CircularProgressIndicator(Modifier.size(16.dp), color = p.textTertiary, strokeWidth = 2.dp)
        }
    }
}

/** Shown on top of a filtered (single-extension) page: what's active, change it, or clear it. */
@Composable
internal fun ActiveFilterBar(name: String, onChange: () -> Unit, onClear: () -> Unit) {
    val p = LocalMtPalette.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.clip(CircleShape).background(p.accent.copy(alpha = 0.14f)).border(1.dp, p.accent.copy(alpha = 0.45f), CircleShape)
                .clickable(onClickLabel = "Change filter", onClick = onChange).padding(start = 12.dp, end = 2.dp)
                .testTag("online_filter_active"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.FilterList, null, tint = p.accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Showing: $name", color = p.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            IconButton(onClick = onClear, modifier = Modifier.size(36.dp).testTag("online_filter_active_clear")) {
                Icon(Icons.Default.Close, "Show all extensions", tint = p.textSecondary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** Category chips (Anime, Movies, Drama, Romance, Sports…) with the picked category's titles below. */
@Composable
internal fun CategoryShelf(cats: List<Pair<YumixFeed.Category, List<OnlineItem>>>, picked: String?, onPick: (String) -> Unit, onClick: (OnlineItem) -> Unit) {
    val p = LocalMtPalette.current
    val current = cats.firstOrNull { it.first.name == picked } ?: cats.first()
    Column(Modifier.testTag("online_categories")) {
        SectionHeader("Browse by category", "From every extension you have on")
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(cats, key = { it.first.name }) { (c, list) ->
                val sel = c == current.first
                val bg by animateColorAsState(if (sel) p.accent else p.surfaceContainerHigh, label = "cat_bg")
                Box(
                    Modifier.clip(CircleShape).background(bg)
                        .selectable(selected = sel, role = Role.Tab) { onPick(c.name) }
                        .padding(horizontal = 14.dp, vertical = 8.dp).testTag("online_category_${c.name}"),
                ) {
                    Text("${c.label}  ${list.size}", color = if (sel) p.onAccent else p.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.padding(top = 2.dp))
        LazyRow(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(current.second, key = { current.first.name + it.key }) { PosterCard(it, onClick = { onClick(it) }, width = 112.dp, showSource = true) }
        }
    }
}
