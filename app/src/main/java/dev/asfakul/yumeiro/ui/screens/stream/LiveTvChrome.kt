/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.stream

import dev.asfakul.yumeiro.l10n.tr
import androidx.compose.ui.res.stringResource
import dev.asfakul.yumeiro.R
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import dev.asfakul.yumeiro.ui.theme.yuRound

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
internal fun LiveCategoryBar(
    categories: List<LiveChannelFeed.Category>, total: Int, selected: String?,
    tag: String = "live_categories", prefix: String = "live_cat_", onPick: (String?) -> Unit,
) {
    LazyRow(Modifier.fillMaxWidth().testTag(tag), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item("all") { LiveChip("All · $total", selected == null, "${prefix}all") { onPick(null) } }
        items(categories, key = { "c_" + it.name }) { c ->
            LiveChip("${c.name} · ${c.count}", selected.equals(c.name, ignoreCase = true), "$prefix${c.name}") { onPick(if (selected.equals(c.name, ignoreCase = true)) null else c.name) }
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
        append(if (shown < total) tr(R.string.showing_of_more_load_as_you, "Showing %1\$s of %2\$s · more load as you scroll", shown, total) else tr(R.string.all_channels, "All %1\$s channels", total))
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
            Text(stringResource(R.string.tools), color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.links_files_and_playlists), color = TextSecondary, fontSize = 12.sp)
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


// ---- LIVE-1 (owner list R21, item 1): search and country, Yumix-style ------------------------------------

/**
 * A round 48 dp icon button for the Live TV header (search, country). [label] is shown next to the icon when the
 * filter is in use (the picked country's flag and name), so the header says what the list shows.
 */
@Composable
internal fun LiveIconButton(
    icon: ImageVector, description: String, tag: String, onClick: () -> Unit, active: Boolean = false, label: String? = null,
) {
    val pal = LocalMtPalette.current
    Row(
        Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).clip(RoundedCornerShape(50))
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(if (active) pal.accent.copy(alpha = 0.16f) else GlassFill)
                .padding(horizontal = if (label != null) 12.dp else 9.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = if (active) pal.accent else TextPrimary, modifier = Modifier.size(18.dp))
            if (label != null) {
                Spacer(Modifier.width(6.dp))
                Text(label, color = if (active) pal.accent else TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 110.dp))
            }
        }
    }
}

/** The search box under the Live TV header, with how many channels match. */
@Composable
internal fun LiveSearchBox(query: String, onQuery: (String) -> Unit, matches: Int?, onClose: () -> Unit) {
    val focus = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
    Column(Modifier.fillMaxWidth().testTag("live_search")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            dev.asfakul.yumeiro.ui.screens.watch.WatchSearchField(
                query = query, onQuery = { onQuery(it.take(60)) }, hint = tr(R.string.search_channels, "Search channels"), tag = "live_search_field",
                modifier = Modifier.weight(1f), fieldModifier = Modifier.focusRequester(focus),
            )
            LiveIconButton(Icons.Default.Close, tr(R.string.close_search, "Close search"), "live_search_close", onClick = onClose)
        }
        if (query.isNotBlank() && matches != null) Text(
            if (matches == 1) "1 channel" else "$matches channels", color = TextTertiary, fontSize = 12.sp,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp).testTag("live_search_count"),
        )
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { if (!dev.asfakul.yumeiro.Edition.isTv) runCatching { focus.requestFocus() } }
}

/** No channel matched: say what was searched and offer the ways out (never a blank page). */
@Composable
internal fun LiveSearchEmpty(query: String, scoped: String?, onClear: () -> Unit, onWiden: (() -> Unit)?) {
    val pal = LocalMtPalette.current
    Column(Modifier.fillMaxWidth().padding(top = 8.dp).clip(yuRound(16)).background(GlassFill).padding(16.dp).testTag("live_search_empty")) {
        Text("No channel called \"${query.trim()}\"", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(
            if (scoped != null) tr(R.string.only_is_searched_now_search_every, "Only %1\$s is searched now. Search every channel, or check the spelling.", scoped) else tr(R.string.check_the_spelling_or_try_fewer, "Check the spelling, or try fewer words (for example \"sports\")."),
            color = TextSecondary, fontSize = 12.sp,
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onWiden != null) Text(
                stringResource(R.string.search_every_channel), color = pal.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                modifier = Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(50)).clickable(role = Role.Button, onClick = onWiden).padding(horizontal = 8.dp, vertical = 14.dp).testTag("live_search_widen"),
            )
            Text(
                stringResource(R.string.clear_search), color = pal.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                modifier = Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(50)).clickable(role = Role.Button, onClick = onClear).padding(horizontal = 8.dp, vertical = 14.dp).testTag("live_search_clear"),
            )
        }
    }
}

/**
 * The Country sheet: a search field, the countries picked lately, then every country in the loaded lists with
 * its flag and channel count (most channels first). Built from the data ([LiveFind.countries]), never a fixed
 * list; it replaces the long sideways row of country chips. Full height on every device (TV remotes cannot drag
 * a sheet up, and on phones the keyboard would cover matches).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiveCountrySheet(
    countries: List<dev.asfakul.yumeiro.stream.LiveFind.Country>, selected: String?, recent: List<String>, total: Int,
    onPick: (dev.asfakul.yumeiro.stream.LiveFind.Country?) -> Unit, onDismiss: () -> Unit,
) {
    val pal = LocalMtPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    val shown = androidx.compose.runtime.remember(countries, query) { dev.asfakul.yumeiro.stream.LiveFind.filterCountries(countries, query) }
    val recentRows = androidx.compose.runtime.remember(countries, recent, query) {
        if (query.isNotBlank()) emptyList() else recent.mapNotNull { k -> countries.firstOrNull { it.key.equals(k, ignoreCase = true) } }.take(5)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss, containerColor = pal.surface, sheetState = sheetState,
        shape = dev.asfakul.yumeiro.ui.theme.Yume.shapeSheet,
        dragHandle = dev.asfakul.yumeiro.ui.components.sheetDragHandle(),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().testTag("live_country_sheet")) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.country), color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.countries_in_your_playlists, countries.size), color = TextSecondary, fontSize = 12.sp)
                }
                if (selected != null) Text(
                    stringResource(R.string.clear), color = pal.accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    modifier = Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(50)).clickable(role = Role.Button) { onPick(null) }.padding(horizontal = 12.dp, vertical = 14.dp).testTag("live_country_clear"),
                )
            }
            if (countries.size > 6) dev.asfakul.yumeiro.ui.screens.watch.WatchSearchField(
                query = query, onQuery = { query = it.take(40) }, hint = tr(R.string.search_countries, "Search countries"), tag = "live_country_search",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            )
            androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)) {
                if (query.isBlank()) item("any") { LiveCountryRow(null, tr(R.string.every_country, "Every country"), tr(R.string.all_channels, "All %1\$s channels", total), selected == null) { onPick(null) } }
                if (recentRows.isNotEmpty()) {
                    item("h_recent") { LiveSheetHead("Recent") }
                    items(recentRows, key = { "r_" + it.key }) { c -> LiveCountryRow(c, c.label, "${c.count} channels", selected.equals(c.key, ignoreCase = true), tagPrefix = "live_country_recent") { onPick(c) } }
                    item("h_all") { LiveSheetHead(tr(R.string.all_countries, "All countries")) }
                }
                if (shown.isEmpty()) item("none") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp).testTag("live_country_empty")) {
                        Text("No country called \"${query.trim()}\"", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.your_playlists_have_channels_from_countries, countries.size), color = TextSecondary, fontSize = 12.sp)
                    }
                }
                items(shown, key = { "c_" + it.key }) { c -> LiveCountryRow(c, c.label, "${c.count} channels", selected.equals(c.key, ignoreCase = true)) { onPick(c) } }
            }
        }
    }
}

@Composable
private fun LiveSheetHead(title: String) {
    Text(
        title.uppercase(), color = TextTertiary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
private fun LiveCountryRow(
    c: dev.asfakul.yumeiro.stream.LiveFind.Country?, title: String, sub: String, sel: Boolean,
    tagPrefix: String = "live_country", onClick: () -> Unit,
) {
    val pal = LocalMtPalette.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)
            .background(if (sel) pal.accent.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent, yuRound(16))
            .clip(yuRound(16))
            .selectable(selected = sel, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(if (c == null) "${tagPrefix}_any" else "${tagPrefix}_${c.key}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(yuRound(12)).background(GlassFill), contentAlignment = Alignment.Center) {
            val flag = dev.asfakul.yumeiro.stream.LiveFind.flag(c?.code)
            if (flag != null) Text(flag, fontSize = 20.sp)
            else Icon(Icons.Default.Public, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
        }
        if (sel) Icon(Icons.Default.Check, stringResource(R.string.selected), tint = pal.accent, modifier = Modifier.size(20.dp))
    }
}
