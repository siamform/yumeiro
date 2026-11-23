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
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette

// Precompiled once (never during composition, scrolling or per call): see docs/STABILITY-R15.md.
private val RX_YUMIXFILTER_1 = Regex("[\\s._-]+")
private val RX_MARKS = Regex("\\p{M}+")

internal sealed interface SourceStatus {
    data object Loading : SourceStatus
    data object Failed : SourceStatus
    data class Ready(val titles: Int) : SourceStatus
}

/**
 * Round 10: "Show videos from" — the extension filter. A single list (radio rows) replaces the
 * horizontal strip of round shortcuts, so 30 extensions stay readable and nothing is clipped.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun SourceFilterSheet(
    names: List<String>,
    selected: String?,
    status: (String) -> SourceStatus,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
    pins: List<String> = emptyList(),
    onTogglePin: ((String) -> Unit)? = null,
    texts: SourceFilterTexts = SourceFilterTexts.Extensions,
    label: ((String) -> String)? = null,
    /** F-21: language chip and "3 sources • CloudStream • Repo" for each extension row; null = plain rows. */
    details: ((String) -> dev.asfakul.yumeiro.online.ExtensionLabels.Label?)? = null,
    /** YF-2 (owner list 10): where each entry comes from (CloudStream, Vega, SkyStream…); null = no source chips. */
    group: ((String) -> String?)? = null,
    pinMax: Int = dev.asfakul.yumeiro.online.PinnedExtensions.MAX,
) {
    val p = LocalMtPalette.current
    val t = texts
    fun nameOf(id: String) = label?.invoke(id) ?: id
    // G9-8 (YF-TV-1): on TV the half-height sheet left every row below the second one off-screen,
    // and a remote cannot drag a sheet up. G15-1 (NBUG-10, ADR-031): phones too — a half-height sheet
    // stays put when the keyboard opens, so search matches sat under the keyboard. Every device opens
    // it fully (CloudStream's provider picker and Mihon's sheets do the same); the list scrolls inside it.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Round 12: with dozens of extensions the list got long; a search narrows it as you type (accent
    // and case blind, every word must match, in any order). YF-2 (owner list 10): it sits behind a
    // search icon in the header instead of a box that is always there, so the list starts higher.
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var source by rememberSaveable { mutableStateOf<String?>(null) }
    val groups = remember(names, group) { if (group == null) emptyList() else sourceGroups(names, group) }
    val fromSource = remember(names, group, source) { if (group == null) names else filterBySource(names, group, source) }
    ModalBottomSheet(
        onDismissRequest = onDismiss, containerColor = p.surface, sheetState = sheetState,
        shape = dev.asfakul.yumeiro.ui.theme.Yume.shapeSheet,
        dragHandle = dev.asfakul.yumeiro.ui.components.sheetDragHandle(),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().testTag("${t.tag}_sheet")) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t.title, color = p.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(t.subtitle(names.size), color = p.textSecondary, fontSize = 12.sp)
                }
                if (names.size >= SEARCH_FROM) IconButton(
                    onClick = { if (searching) { searching = false; query = "" } else searching = true },
                    modifier = Modifier.size(48.dp).testTag("${t.tag}-search-open"),
                ) {
                    Icon(if (searching) Icons.Default.Close else Icons.Default.Search, if (searching) "Close search" else t.searchHint, tint = if (searching) p.accent else p.textSecondary)
                }
                if (selected != null) TextButton(onClick = { onPick(null) }, modifier = Modifier.testTag("${t.tag}_clear")) {
                    Text("Clear", color = p.accent, fontWeight = FontWeight.SemiBold)
                }
            }
            // R15 Phase 5: pinned extensions first (in pin order), then the rest.
            val shown = remember(fromSource, query, pins) {
                val hits = if (label == null) filterExtensionNames(fromSource, query) else filterByTitle(fromSource, query) { nameOf(it) }
                dev.asfakul.yumeiro.online.PinnedExtensions.pinnedFirst(hits, pins)
            }
            if (searching) {
                val focus = remember { androidx.compose.ui.focus.FocusRequester() }
                dev.asfakul.yumeiro.ui.screens.watch.WatchSearchField(
                    query = query, onQuery = { query = it.take(60) }, hint = t.searchHint,
                    tag = "${t.tag}_search", modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    fieldModifier = Modifier.focusRequester(focus),
                )
                androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            }
            // YF-2: one chip per source that is really installed (counted from the list, nothing fixed),
            // wrapped so every label stays readable; shown only when there are two sources or more.
            if (groups.size >= 2) FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp).testTag("${t.tag}-sources"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SourceChip("All sources", names.size, source == null, "${t.tag}-source-all") { source = null }
                groups.forEach { (g, n) -> SourceChip(g, n, source == g, "${t.tag}-source-$g") { source = if (source == g) null else g } }
            }
            val pinnedShown = remember(shown, pins) { shown.count { it in pins } }
            val sectioned = onTogglePin != null && pinnedShown > 0
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                if (query.isBlank() && source == null) item("all") { FilterRow(null, t.allTitle, t.allSub, selected == null, tagPrefix = t.tag) { onPick(null) } }
                if (shown.isEmpty()) item("none") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp).testTag("${t.tag}_empty")) {
                        Text(if (query.isBlank()) "Nothing from this source" else "${t.noneCalled} \"${query.trim()}\"", color = p.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (query.isBlank()) "Pick another source, or show all ${names.size}." else "Check the spelling, or clear the search to see all ${names.size}.", color = p.textSecondary, fontSize = 12.sp)
                        TextButton(onClick = { query = ""; source = null }, modifier = Modifier.testTag("${t.tag}_search_clear")) { Text("Clear search", color = p.accent, fontWeight = FontWeight.SemiBold) }
                    }
                }
                // YF-2: with up to [pinMax] pins the favourites get their own heading (and how many are left),
                // the rest follow under "Others", so it is clear what a pin does.
                if (sectioned) item("h_pinned") { SectionHead("Pinned", "${pins.size} of $pinMax", "${t.tag}-head-pinned") }
                val row: @Composable (String) -> Unit = { n ->
                    val sub = when (val st = status(n)) {
                        SourceStatus.Loading -> "Loading…"
                        SourceStatus.Failed -> t.failed
                        is SourceStatus.Ready -> t.ready(st.titles)
                    }
                    val info = remember(n, details) { details?.invoke(n) }
                    FilterRow(
                        n, nameOf(n), sub, selected == n, details = info, loading = status(n) == SourceStatus.Loading, failed = status(n) == SourceStatus.Failed,
                        pinned = n in pins, onTogglePin = onTogglePin?.let { tp -> { tp(n) } }, tagPrefix = t.tag,
                    ) { onPick(n) }
                }
                if (sectioned) {
                    items(shown.take(pinnedShown), key = { it }) { n -> row(n) }
                    if (pinnedShown < shown.size) item("h_others") { SectionHead("Others", null, "${t.tag}-head-others") }
                    items(shown.drop(pinnedShown), key = { it }) { n -> row(n) }
                } else items(shown, key = { it }) { n -> row(n) }
            }
        }
    }
}

@Composable
private fun SectionHead(title: String, end: String?, tag: String) {
    val p = LocalMtPalette.current
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 4.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        Text(title.uppercase(), color = p.textTertiary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
        if (end != null) Text(end, color = p.textTertiary, fontSize = 11.sp)
    }
}

@Composable
private fun SourceChip(label: String, count: Int, sel: Boolean, tag: String, onClick: () -> Unit) {
    val p = LocalMtPalette.current
    val bg by animateColorAsState(if (sel) p.accent.copy(alpha = 0.18f) else p.textPrimary.copy(alpha = 0.06f), label = "src_bg")
    Box(Modifier.heightIn(min = 48.dp).selectable(selected = sel, role = Role.RadioButton, onClick = onClick).testTag(tag), contentAlignment = Alignment.Center) {
        Row(
            Modifier.heightIn(min = 32.dp).clip(RoundedCornerShape(10.dp)).background(bg)
                .border(1.dp, if (sel) p.accent.copy(alpha = 0.6f) else Color.Transparent, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (sel) { Icon(Icons.Default.Check, null, tint = p.accent, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)) }
            Text(label, color = p.textPrimary, fontSize = 13.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
            Spacer(Modifier.width(6.dp))
            Text("$count", color = p.textSecondary, fontSize = 12.sp)
        }
    }
}

/**
 * YF-2 (owner list 10): the sources the entries in [names] come from, most entries first (ties by name).
 * Entries without a known source count as "Other" once at least one entry has a source. Pure.
 */
internal fun sourceGroups(names: List<String>, group: (String) -> String?): List<Pair<String, Int>> {
    val counts = LinkedHashMap<String, Int>()
    var unknown = 0
    names.forEach { n -> val g = group(n)?.trim()?.takeIf { it.isNotEmpty() }; if (g == null) unknown++ else counts[g] = (counts[g] ?: 0) + 1 }
    if (counts.isEmpty()) return emptyList()
    val out = counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.lowercase() }).map { it.key to it.value }
    return if (unknown > 0) out + (OTHER_SOURCE to unknown) else out
}

/** YF-2: [names] from [source] only (null = all). "Other" holds the entries with no known source. Pure. */
internal fun filterBySource(names: List<String>, group: (String) -> String?, source: String?): List<String> = when (source) {
    null -> names
    OTHER_SOURCE -> names.filter { group(it)?.trim().isNullOrEmpty() }
    else -> names.filter { group(it)?.trim() == source }
}

internal const val OTHER_SOURCE = "Other"

/**
 * NBUG-15 (ADR-028): the words of [SourceFilterSheet], so Live TV reuses the same sheet for playlists.
 * [tag] prefixes every test tag; [Extensions] keeps Yumix exactly as it was.
 */
internal data class SourceFilterTexts(
    val tag: String,
    val title: String,
    val subtitle: (Int) -> String,
    val allTitle: String,
    val allSub: String,
    val searchHint: String,
    val noneCalled: String,
    val failed: String,
    val ready: (Int) -> String,
) {
    companion object {
        val Extensions = SourceFilterTexts(
            tag = "online_filter", title = "Show videos from", subtitle = { "$it extensions on · pick one to filter Yumix" },
            allTitle = "All extensions", allSub = "Everything mixed in one feed", searchHint = "Search extensions",
            noneCalled = "No extension called", failed = "Didn't load. Open it to retry",
            ready = { if (it == 0) "Search only" else "$it titles on its home" },
        )
        val Playlists = SourceFilterTexts(
            tag = "live_filter", title = "Show videos from", subtitle = { "$it playlists on · pick one to filter Live TV" },
            allTitle = "All playlists", allSub = "Every channel, grouped by category", searchHint = "Search playlists",
            noneCalled = "No playlist called", failed = "Didn't load. Retry from Playlists",
            ready = { "$it channels" },
        )
    }
}

/** Round 2, order 4: search is offered from one entry. YF-2: behind the header search icon, not an always-open box. */
internal const val SEARCH_FROM = 1

/** Lower-case, accent-free form used to compare extension names with what was typed. */
private fun foldName(s: String): String =
    java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(RX_MARKS, "").lowercase(java.util.Locale.ROOT)

/**
 * Extensions whose name holds every word of [query] (accent and case blind). Names that start
 * with the query come first, then the rest in their original order. Blank query = all names.
 */
internal fun filterExtensionNames(names: List<String>, query: String): List<String> {
    val words = foldName(query).split(RX_YUMIXFILTER_1).filter { it.isNotBlank() }
    if (words.isEmpty()) return names
    val hits = names.filter { n -> val f = foldName(n); words.all { it in f } }
    val first = words.first()
    return hits.sortedBy { n -> if (foldName(n).startsWith(first)) 0 else 1 }
}

/**
 * Round 13: search inside a section ("Show videos from" an extension, or a row's "See all"),
 * which can hold more than 1000 titles. Same matching as the extension search. Pure.
 */
internal fun <T> filterByTitle(items: List<T>, query: String, title: (T) -> String): List<T> {
    val words = foldName(query).split(RX_YUMIXFILTER_1).filter { it.isNotBlank() }
    if (words.isEmpty()) return items
    val first = words.first()
    return items.filter { val f = foldName(title(it)); words.all { w -> w in f } }
        .sortedBy { if (foldName(title(it)).startsWith(first)) 0 else 1 }
}

/** Most titles a section search keeps loading pages for before it asks the user to search the source. */
internal const val SECTION_SEARCH_CAP = 2000

@Composable
private fun FilterRow(
    name: String?, title: String, sub: String, sel: Boolean, loading: Boolean = false, failed: Boolean = false,
    pinned: Boolean = false, onTogglePin: (() -> Unit)? = null, tagPrefix: String = "online_filter",
    details: dev.asfakul.yumeiro.online.ExtensionLabels.Label? = null, onClick: () -> Unit,
) {
    val p = LocalMtPalette.current
    val bg by animateColorAsState(if (sel) p.accent.copy(alpha = 0.14f) else Color.Transparent, label = "filter_bg")
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp).background(bg, RoundedCornerShape(16.dp))
            .selectable(selected = sel, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag(if (name == null) "${tagPrefix}_all" else "${tagPrefix}_$name"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelAvatar(if (name == null) "All" else title, selected = sel, size = 40.dp, mix = name == null)
        Spacer(Modifier.width(12.dp))
        if (details != null) ExtensionLabelText(
            details.copy(title = title), titleColor = p.textPrimary, lineColor = if (failed) WarnColor else p.textSecondary,
            status = sub, titleWeight = if (sel) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.weight(1f),
        ) else Column(Modifier.weight(1f)) {
            Text(title, color = p.textPrimary, fontSize = 15.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = if (failed) WarnColor else p.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when {
            sel -> Icon(Icons.Default.Check, "Selected", tint = p.accent, modifier = Modifier.size(22.dp))
            loading -> CircularProgressIndicator(Modifier.size(16.dp), color = p.textTertiary, strokeWidth = 2.dp)
        }
        // R15 Phase 5: pin a favourite. A 48 dp toggle of its own, so it never selects the row.
        if (onTogglePin != null && name != null) IconToggleButton(
            checked = pinned, onCheckedChange = { onTogglePin() },
            modifier = Modifier.size(48.dp).testTag("${tagPrefix}_pin_$name"),
        ) {
            Icon(
                if (pinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                if (pinned) "Unpin $title" else "Pin $title",
                tint = if (pinned) p.accent else p.textTertiary, modifier = Modifier.size(20.dp),
            )
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

/**
 * Round 11c: compact category chips at the top of Yumix, under the header (like YouTube's chip
 * bar). "All" shows the normal feed; any other chip switches the page to that category's titles.
 * Chips are small (32 dp tall, 13 sp) but each keeps a 48 dp touch target (G12 X8: 44 dp measured
 * 46 dp in the accessibility check, run 43754318750 api36).
 */
@Composable
internal fun CategoryBar(cats: List<Pair<YumixFeed.Category, List<OnlineItem>>>, picked: String?, onPick: (String?) -> Unit) {
    val current = cats.firstOrNull { it.first.name == picked }?.first
    LazyRow(
        Modifier.fillMaxWidth().testTag("online_categories"),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item("all") { CategoryChip("All", current == null, "online_category_all") { onPick(null) } }
        items(cats, key = { it.first.name }) { (c, _) ->
            CategoryChip(c.label, c == current, "online_category_${c.name}") { onPick(if (c == current) null else c.name) }
        }
    }
}

@Composable
private fun CategoryChip(label: String, sel: Boolean, tag: String, onClick: () -> Unit) {
    val p = LocalMtPalette.current
    val bg by animateColorAsState(if (sel) p.textPrimary else p.textPrimary.copy(alpha = 0.08f), label = "cat_bg")
    Box(
        Modifier.heightIn(min = 48.dp).selectable(selected = sel, role = Role.Tab, onClick = onClick).testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.heightIn(min = 32.dp).clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(label, color = if (sel) p.background else p.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

/** The picked category's titles as a three-column grid (rows of [PosterCard]). */
internal fun categoryGridRows(items: List<OnlineItem>): List<List<OnlineItem>> = items.chunked(3)

@Composable
internal fun CategoryGridRow(row: List<OnlineItem>, onClick: (OnlineItem) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).testTag("online_category_grid_row"), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        row.forEach { item -> PosterCard(item, onClick = { onClick(item) }, showSource = true, modifier = Modifier.weight(1f)) }
        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
    }
}
