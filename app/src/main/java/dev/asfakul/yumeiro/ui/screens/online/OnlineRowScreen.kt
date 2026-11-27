/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import dev.asfakul.yumeiro.R
import dev.asfakul.yumeiro.ui.components.TsukiDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.online.OnlineErrors
import dev.asfakul.yumeiro.online.OnlineHistory
import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.online.OnlineRepository
import dev.asfakul.yumeiro.ui.components.StateMessage
import dev.asfakul.yumeiro.ui.components.SubPageHeader
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary

/** "See all" for one home row: an endless poster grid that keeps loading pages as you scroll. */
@Composable
fun OnlineRowScreen(api: String, title: String, pageName: String, pageData: String, onBack: () -> Unit, onOpenTitle: (OnlineItem) -> Unit, onOpenExtensions: () -> Unit) {
    val context = LocalContext.current
    val engine by ExtensionManager.state.collectAsState()
    LaunchedEffect(Unit) { ExtensionManager.start(context) }
    val items = remember(api, pageData) { mutableStateListOf<OnlineItem>() }
    var page by remember(api, pageData) { mutableIntStateOf(0) }
    var hasNext by remember(api, pageData) { mutableStateOf(true) }
    var loading by remember(api, pageData) { mutableStateOf(false) }
    var error by remember(api, pageData) { mutableStateOf<String?>(null) }
    var missing by remember { mutableStateOf(false) }
    var request by remember { mutableIntStateOf(0) }
    val grid = dev.asfakul.yumeiro.ui.components.ScrollMemory.grid("row:" + api + "|" + pageData)

    // Start with what the home row already showed, so the page is never blank.
    LaunchedEffect(api, pageData) {
        if (items.isEmpty()) OnlineRepository.cachedRow(api, pageData)?.let { items.addAll(it); page = 1 }
    }
    // R16 G4 (Y6): the page fetch used to run inside this LaunchedEffect, keyed on [request]. A
    // second request (scroll trigger and the section search firing together) restarted the effect
    // and cancelled the fetch in flight; runCatching caught the cancellation and showed a false
    // "Couldn't load more", which also stopped paging. The fetch now runs in the screen's scope as
    // one job; a new request while it runs is ignored instead of cancelling it.
    val pageScope = androidx.compose.runtime.rememberCoroutineScope()
    val pageJob = remember(api, pageData) { arrayOfNulls<kotlinx.coroutines.Job>(1) }
    LaunchedEffect(api, pageData, request, engine.ready) {
        if (!engine.ready || !hasNext || pageJob[0]?.isActive == true) return@LaunchedEffect
        val provider = ExtensionManager.provider(api)
        if (provider == null) { missing = true; return@LaunchedEffect }
        loading = true; error = null
        pageJob[0] = pageScope.launch {
            try {
                val r = OnlineRepository.page(provider, pageName, pageData, page + 1)
                val known = items.map { it.url }.toHashSet()
                val fresh = r.items.filter { known.add(it.url) }
                items.addAll(fresh)
                page++
                // Stop when the source has no next page or keeps sending the same titles.
                hasNext = r.hasNext && fresh.isNotEmpty()
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                OnlineRowDiag.lastError = e
                error = OnlineErrors.friendly(e, "Couldn't load more")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                OnlineRowDiag.lastError = e
                error = OnlineErrors.friendly(e, "Couldn't load more")
            } finally { loading = false }
        }
    }
    val nearEnd by remember { derivedStateOf { val last = grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0; last >= grid.layoutInfo.totalItemsCount - 9 } }
    LaunchedEffect(nearEnd, loading, hasNext, error) { if (nearEnd && !loading && hasNext && error == null && page > 0) request++ }
    // Round 13: search inside this section (it can hold more than 1000 titles). While a search is
    // typed, pages keep loading one at a time so titles further down are found as well.
    var query by androidx.compose.runtime.saveable.rememberSaveable(api, pageData) { mutableStateOf("") }
    var searchOpen by androidx.compose.runtime.saveable.rememberSaveable(api, pageData) { mutableStateOf(false) }
    val shown by androidx.compose.runtime.produceState<List<OnlineItem>>(items.toList(), items.size, query) {
        val snapshot = items.toList()
        value = if (query.isBlank()) snapshot else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { filterByTitle(snapshot, query) { it.name } }
    }
    LaunchedEffect(query, loading, hasNext, error, items.size) {
        if (query.isNotBlank() && !loading && hasNext && error == null && page > 0 && items.size < SECTION_SEARCH_CAP) { kotlinx.coroutines.delay(250); request++ }
    }

    Column(Modifier.fillMaxSize().testTag("online_row_screen")) {
        SubPageHeader(title = title, subtitle = api, onBack = onBack) {
            // GROUP C catch-all: the section search is a header icon, not a box pinned over the grid.
            if (items.isNotEmpty()) dev.asfakul.yumeiro.ui.components.SearchIconButton(searchOpen || query.isNotEmpty(), {
                if (searchOpen || query.isNotEmpty()) { searchOpen = false; query = "" } else searchOpen = true
            }, "online_row_search_open", "Search $title")
        }
        if (items.isNotEmpty()) {
            dev.asfakul.yumeiro.ui.components.HeaderSearchField(
                searchOpen || query.isNotEmpty(), query, { query = it.take(80) },
                "Search ${items.size}${if (hasNext) "+" else ""} titles in $title", "online_row_search",
            )
            if (query.isNotBlank()) Text(
                "${shown.size} match${if (shown.size == 1) "" else "es"} in ${items.size} loaded" + if (loading) " · looking further…" else "",
                color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp).testTag("online_row_search_count"),
            )
        }
        when {
            missing -> StateMessage(Icons.Default.Extension, "Source not available", "The extension that provides “$api” isn't installed or is turned off.",
                primary = "Manage extensions" to onOpenExtensions, secondary = "Go back" to onBack, testTag = "online_row_missing")
            items.isEmpty() && loading -> PosterGridSkeleton(rows = 4)
            items.isEmpty() && error != null -> StateMessage(Icons.Default.ErrorOutline, "Couldn't load", error.orEmpty(), tint = ErrorColor,
                primary = "Retry" to { error = null; request++ }, secondary = "Go back" to onBack, testTag = "online_row_error")
            items.isEmpty() && !hasNext -> StateMessage(Icons.Default.ErrorOutline, "Nothing here", "This list is empty right now.", tint = TextSecondary, testTag = "online_row_empty")
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns()), state = grid,
                contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 48.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize().testTag("online_row_grid"),
            ) {
                items(if (query.isBlank()) items else shown, key = { it.url }) { item ->
                    PosterCard(item, onClick = { OnlineRepository.previews[item.key] = item; onOpenTitle(item) }, modifier = Modifier.fillMaxWidth())
                }
                item(span = { GridItemSpan(maxLineSpan) }, key = "footer") {
                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        when {
                            loading -> PosterRowSkeleton(count = gridColumns())
                            error != null -> TextButton(onClick = { error = null; request++ }) { Text(stringResource(R.string.couldnt_load_more_retry), color = SpotifyGreen) }
                            !hasNext -> Text(stringResource(R.string.thats_everything), color = TextTertiary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * "See all" for Continue watching. Group Y (B6): delete by time range (with a live count), select many titles and
 * delete them together, remove one, and Undo every delete for a few seconds (nothing is lost by a slip of the finger).
 */
@Composable
fun OnlineHistoryScreen(onBack: () -> Unit, onOpenTitle: (OnlineItem) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { OnlineHistory.ensureLoaded(context) }
    val all by OnlineHistory.entries.collectAsState()
    val adultOn by dev.asfakul.yumeiro.online.OnlineSettings.adult.collectAsState()
    val adultPrivate by dev.asfakul.yumeiro.online.OnlineSettings.adultPrivate.collectAsState()
    // 18+ titles are kept quietly: hidden here unless the user asks to see them.
    var showAdult by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val adultCount = remember(all) { all.count { OnlineHistory.isAdult(it) } }
    val entries = remember(all, adultOn, adultPrivate, showAdult) {
        if (showAdult) all.filter { OnlineHistory.isAdult(it) } else OnlineHistory.forFeed(all, adultOn, adultPrivate)
    }
    var selecting by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }
    // Titles that leave the list (deleted elsewhere) leave the selection too.
    LaunchedEffect(entries) { val keys = entries.map { it.key }.toHashSet(); selected.retainAll { it in keys }; if (entries.isEmpty()) selecting = false }
    var sheet by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var undo by remember { mutableStateOf<OnlineHistory.Removed?>(null) }
    // Wall-clock wait (not the frame clock), so Undo stays exactly this long on every device and in tests.
    LaunchedEffect(undo) { if (undo != null) { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { kotlinx.coroutines.delay(HISTORY_UNDO_MS) }; undo = null } }
    val removed: (OnlineHistory.Removed) -> Unit = { r -> if (r.count > 0) undo = r }
    val exitSelect = { selecting = false; selected.clear() }
    if (sheet) HistoryDeleteSheet(entries, adultOnly = showAdult, onDismiss = { sheet = false }) { r -> sheet = false; removed(r) }
    androidx.activity.compose.BackHandler(enabled = selecting) { exitSelect() }
    Box(Modifier.fillMaxSize().testTag("online_history_screen")) {
        Column(Modifier.fillMaxSize()) {
            if (selecting) SubPageHeader(title = stringResource(R.string.hx_selected, selected.size), onBack = exitSelect) {
                val everything = selected.size == entries.size && entries.isNotEmpty()
                TextButton(onClick = { if (everything) selected.clear() else { selected.clear(); selected.addAll(entries.map { it.key }) } }, modifier = Modifier.testTag("history_select_all")) {
                    Text(stringResource(if (everything) R.string.hx_select_none else R.string.hx_select_all), color = TextSecondary, fontSize = 12.sp)
                }
                IconButton(onClick = {
                    val keys = selected.toHashSet()
                    removed(OnlineHistory.removeWhere(context) { it.key in keys }); exitSelect()
                }, enabled = selected.isNotEmpty(), modifier = Modifier.testTag("history_delete_selected")) {
                    Icon(Icons.Default.Delete, stringResource(R.string.hx_delete_selected), tint = if (selected.isNotEmpty()) ErrorColor else TextTertiary)
                }
            } else SubPageHeader(title = if (showAdult) "18+ history" else "Continue watching", subtitle = "${entries.size} title${if (entries.size == 1) "" else "s"}", onBack = { if (showAdult) showAdult = false else onBack() }) {
                if (adultCount > 0 && adultOn) TextButton(onClick = { showAdult = !showAdult }, modifier = Modifier.testTag("online_history_adult")) {
                    Text(if (showAdult) "All" else "18+ ($adultCount)", color = TextSecondary, fontSize = 12.sp)
                }
                if (entries.isNotEmpty()) {
                    IconButton(onClick = { selecting = true }, modifier = Modifier.testTag("history_select")) { Icon(Icons.Default.Checklist, stringResource(R.string.hx_select), tint = TextPrimary) }
                    IconButton(onClick = { sheet = true }, modifier = Modifier.testTag("online_history_clear")) { Icon(Icons.Default.DeleteSweep, stringResource(R.string.hx_delete_history), tint = TextPrimary) }
                }
            }
            if (entries.isEmpty()) StateMessage(Icons.Default.History, "Nothing yet", "Titles you watch online show up here so you can pick up where you left off.", tint = TextSecondary, testTag = "online_history_empty")
            else LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns()), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize().testTag("history_grid"),
            ) {
                items(entries, key = { it.key }) { e ->
                    val on = e.key in selected
                    Column(Modifier.testTag("history_item_${e.url}")) {
                        Box {
                            PosterCard(e.toItem(), onClick = { onOpenTitle(e.toItem()) }, modifier = Modifier.fillMaxWidth(), progress = e.fraction, showSource = true, actions = !selecting)
                            if (selecting) HistoryPickOverlay(on, e.name, Modifier.matchParentSize().testTag("history_pick_${e.url}")) { if (on) selected.remove(e.key) else selected.add(e.key) }
                        }
                        e.episodeLabel?.let { Text(it, color = TextTertiary, fontSize = 10.sp, maxLines = 1, modifier = Modifier.padding(top = 2.dp)) }
                        if (!selecting) TextButton(
                            onClick = { removed(OnlineHistory.removeWhere(context) { it.key == e.key }) },
                            modifier = Modifier.height(30.dp).testTag("history_remove_${e.url}").semantics { contentDescription = context.getString(R.string.hx_remove_title, e.name) },
                            contentPadding = PaddingValues(0.dp),
                        ) { Text(stringResource(R.string.remove_2), color = TextSecondary, fontSize = 11.sp) }
                    }
                }
            }
        }
        undo?.let { r -> HistoryUndoBar(r.count, Modifier.align(Alignment.BottomCenter)) { OnlineHistory.restore(context, r); undo = null } }
    }
}

/** How long Undo stays on screen after a history delete. */
internal const val HISTORY_UNDO_MS = 8_000L

@Composable
private fun HistoryPickOverlay(on: Boolean, name: String, modifier: Modifier, onToggle: () -> Unit) {
    val p = dev.asfakul.yumeiro.ui.theme.LocalMtPalette.current
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (on) p.accent.copy(alpha = 0.28f) else androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.18f))
            .then(if (on) Modifier.border(2.dp, p.accent, shape) else Modifier)
            .toggleable(value = on, role = androidx.compose.ui.semantics.Role.Checkbox, onValueChange = { onToggle() })
            .semantics { contentDescription = name },
        contentAlignment = Alignment.TopEnd,
    ) {
        Icon(
            if (on) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null,
            tint = if (on) p.accent else androidx.compose.ui.graphics.Color.White, modifier = Modifier.padding(6.dp).size(22.dp),
        )
    }
}

@Composable
private fun HistoryUndoBar(count: Int, modifier: Modifier, onUndo: () -> Unit) {
    val p = dev.asfakul.yumeiro.ui.theme.LocalMtPalette.current
    androidx.compose.material3.Surface(
        color = p.chipSelected, contentColor = p.onChipSelected, shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp), shadowElevation = 6.dp,
        modifier = modifier.navigationBarsPadding().padding(16.dp).fillMaxWidth().testTag("history_undo_bar")
            .semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
    ) {
        Row(Modifier.padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.hx_removed, count), modifier = Modifier.weight(1f).padding(vertical = 12.dp), fontSize = 14.sp)
            TextButton(onClick = onUndo, modifier = Modifier.testTag("history_undo")) { Text(stringResource(R.string.hx_undo), color = p.onChipSelected, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
        }
    }
}

/** Group Y (B6): delete by time, with the number of titles each choice removes shown before anything happens. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun HistoryDeleteSheet(entries: List<OnlineHistory.Entry>, adultOnly: Boolean, onDismiss: () -> Unit, onDeleted: (OnlineHistory.Removed) -> Unit) {
    val context = LocalContext.current
    val p = dev.asfakul.yumeiro.ui.theme.LocalMtPalette.current
    val now = remember { System.currentTimeMillis() }
    var range by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(dev.asfakul.yumeiro.online.HistoryRange.HOUR) }
    val counts = remember(entries) { dev.asfakul.yumeiro.online.HistoryRange.entries.associateWith { dev.asfakul.yumeiro.online.HistoryRange.count(entries, it, now) } }
    val n = counts[range] ?: 0
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = p.surfaceContainerHigh, dragHandle = dev.asfakul.yumeiro.ui.components.sheetDragHandle(), modifier = Modifier.testTag("history_delete_sheet"),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp).selectableGroup()) {
            Text(stringResource(R.string.hx_delete_history), color = TextPrimary, fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            Text(stringResource(R.string.hx_sheet_note), color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
            dev.asfakul.yumeiro.online.HistoryRange.entries.forEach { r ->
                val label = stringResource(when (r) {
                    dev.asfakul.yumeiro.online.HistoryRange.HOUR -> R.string.hx_range_hour
                    dev.asfakul.yumeiro.online.HistoryRange.DAY -> R.string.hx_range_day
                    dev.asfakul.yumeiro.online.HistoryRange.WEEK -> R.string.hx_range_week
                    dev.asfakul.yumeiro.online.HistoryRange.MONTH -> R.string.hx_range_month
                    dev.asfakul.yumeiro.online.HistoryRange.ALL -> R.string.hx_range_all
                })
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .selectable(selected = r == range, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = { range = r })
                        .padding(horizontal = 8.dp).testTag("history_range_${r.name.lowercase()}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.RadioButton(selected = r == range, onClick = null)
                    Text(label, color = TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
                    Text(stringResource(R.string.hx_range_count, counts[r] ?: 0), color = TextSecondary, fontSize = 13.sp, modifier = Modifier.testTag("history_range_count_${r.name.lowercase()}"))
                }
            }
            if (n == 0) Text(stringResource(R.string.hx_nothing_in_range), color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp).testTag("history_range_none"))
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                androidx.compose.material3.Button(
                    onClick = {
                        val keys = entries.filter { range.contains(it.updatedAt, now) }.map { it.key }.toHashSet()
                        // Time ranges also forget episode marks recorded in them; the 18+ view only touches 18+ titles.
                        val since = if (adultOnly) null else range.since(now) ?: Long.MIN_VALUE
                        onDeleted(OnlineHistory.removeWhere(context, marksSince = since) { it.key in keys })
                    },
                    enabled = n > 0,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = ErrorColor, contentColor = androidx.compose.ui.graphics.Color.White),
                    modifier = Modifier.testTag("history_range_delete"),
                ) { Text(stringResource(R.string.hx_delete_count, n)) }
            }
        }
    }
}

/**
 * YX-T1 (api36 run 44079680572): the cause of the last "Couldn't load more", so the device test can
 * tell a real network failure (correctly shown, Retry offered) from the false error it guards
 * against. One reference, no history; read only by tests.
 */
internal object OnlineRowDiag {
    @Volatile var lastError: Throwable? = null
}
