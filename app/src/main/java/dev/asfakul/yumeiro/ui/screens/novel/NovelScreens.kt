/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.novel

import androidx.compose.ui.res.stringResource
import dev.asfakul.yumeiro.R
import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.sources.manga.MangaTlPlan
import dev.asfakul.yumeiro.sources.manga.MangaTranslate
import dev.asfakul.yumeiro.sources.novel.NovelChapter
import dev.asfakul.yumeiro.sources.novel.NovelDetails
import dev.asfakul.yumeiro.sources.novel.NovelItem
import dev.asfakul.yumeiro.sources.novel.NovelLibrary
import dev.asfakul.yumeiro.sources.novel.NovelPlugin
import dev.asfakul.yumeiro.sources.novel.NovelSources
import dev.asfakul.yumeiro.sources.novel.NovelText
import dev.asfakul.yumeiro.ui.components.FilterPill
import dev.asfakul.yumeiro.ui.components.StateMessage
import dev.asfakul.yumeiro.ui.components.SubPageHeader
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.OnAccent
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import dev.asfakul.yumeiro.sources.reading.ReadingSpot
import dev.asfakul.yumeiro.sources.novel.NovelFeedMerge
import dev.asfakul.yumeiro.sources.reading.ReadingSaves
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.asfakul.yumeiro.ui.theme.yuRound

/*
 * NOVEL-1 (owner item 26): the novel pages — Novels (library, browse, search across sources), Novel sources
 * (repositories and plugins), a novel's page (details, chapters, resume) and the reader (text, size, translation,
 * Previous / Next, position kept). Same building blocks and look as the manga pages.
 */

private fun prefs(c: Context) = c.getSharedPreferences("novel_reader", Context.MODE_PRIVATE)

private enum class Feed { POPULAR, LATEST, SAVED, HISTORY }

@Composable
fun NovelHomeScreen(onBack: () -> Unit, onOpenNovel: (NovelItem) -> Unit, onOpenSources: () -> Unit, onResume: ((NovelLibrary.Entry) -> Unit)? = null) {
    val context = LocalContext.current
    val state by NovelSources.state.collectAsState()
    val library by NovelLibrary.entries.collectAsState()
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { NovelSources.ensureLoaded(context); NovelLibrary.ensureLoaded(context) }
        ready = true
    }
    var selectedId by rememberSaveable { mutableStateOf(prefs(context).getString("source", null)) }
    // Group X (B4): "All sources" is the default once more than one source is installed (and nothing was chosen).
    val choice = remember(selectedId, state.installed) { NovelFeedMerge.pick(selectedId, state.installed.map { it.id }) }
    // A chosen source that fails or lists nothing switches the page to All sources for this visit (said in a note);
    // "Try <source> again" goes back to it and does not switch again.
    var fellBackFrom by rememberSaveable { mutableStateOf<String?>(null) }
    var noFallback by rememberSaveable { mutableStateOf<String?>(null) }
    val all = choice == NovelFeedMerge.ALL || (fellBackFrom != null && fellBackFrom == choice && state.installed.size > 1)
    val plugin = if (choice == NovelFeedMerge.ALL) null else state.installed.firstOrNull { it.id == choice }
    fun choose(id: String) { selectedId = id; fellBackFrom = null; noFallback = null; prefs(context).edit().putString("source", id).apply() }
    val barHide = dev.asfakul.yumeiro.ui.components.rememberHideOnScrollState()
    var feed by rememberSaveable { mutableStateOf(Feed.POPULAR) }
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var sourceSheet by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.isEmpty()) { submitted = ""; return@LaunchedEffect }
        if (q.length < 2) return@LaunchedEffect
        kotlinx.coroutines.delay(700)
        submitted = q
    }

    Column(Modifier.fillMaxSize().testTag("novel_home")) {
        SubPageHeader(title = "Novels", onBack = onBack, subtitle = if (state.installed.isEmpty()) "Add a source to start reading" else if (choice == NovelFeedMerge.ALL) stringResource(R.string.nx_all_sources) else plugin?.name) {
            // GROUP C: search and the source choice are icons; the box and the sheet open on demand.
            if (ready && state.installed.isNotEmpty()) {
                dev.asfakul.yumeiro.ui.components.SearchIconButton(searchOpen || query.isNotEmpty(), {
                    if (searchOpen || query.isNotEmpty()) { searchOpen = false; query = "" } else searchOpen = true
                }, "novel_search_open", "Search all novel sources")
                if (state.installed.size > 1 && submitted.isEmpty() && (feed == Feed.POPULAR || feed == Feed.LATEST))
                    dev.asfakul.yumeiro.ui.components.FilterIconButton(0, { sourceSheet = true }, "novel_source_filter", "Choose source")
            }
            IconButton(onClick = onOpenSources, modifier = Modifier.testTag("novel_manage_sources")) { Icon(Icons.Default.Extension, stringResource(R.string.novel_sources), tint = TextPrimary) }
        }
        if (!ready) { Column(Modifier.fillMaxSize().padding(top = 8.dp)) { repeat(3) { dev.asfakul.yumeiro.ui.components.CoverRowSkeleton() } }; return@Column } // SKELETON-2
        if (state.installed.isEmpty()) {
            StateMessage(Icons.AutoMirrored.Filled.MenuBook, "No novel sources yet",
                "Add an LNReader plugin repository, then install a source. Yumeiro doesn't include any novel source.",
                primary = "Open novel sources" to onOpenSources, testTag = "novel_empty")
            return@Column
        }
        dev.asfakul.yumeiro.ui.components.HeaderSearchField(searchOpen || query.isNotEmpty(), query, { query = it.take(80) }, "Search all novel sources", "novel_search")
        if (sourceSheet) dev.asfakul.yumeiro.ui.components.FilterSheet(onDismiss = { sourceSheet = false }, onReset = null, resultCount = null, tag = "novel_source_sheet") {
            dev.asfakul.yumeiro.ui.components.FilterGroup("Source") {
                FilterPill(stringResource(R.string.nx_all_sources), choice == NovelFeedMerge.ALL, { choose(NovelFeedMerge.ALL) }, testTag = "novel_source_all")
                state.installed.forEach { p ->
                    FilterPill(p.name, p.id == choice, { choose(p.id) }, testTag = "novel_source_${p.id}")
                }
            }
        }
        if (submitted.isNotEmpty()) {
            SearchAllResults(submitted, onOpenNovel)
            return@Column
        }
        // Group X (B4): the feed chips (and the fallback note) slide away while scrolling down the list and come back
        // on the way up, like the Manga page; a new feed or source always shows them.
        dev.asfakul.yumeiro.ui.components.HideOnScrollColumn(Modifier.fillMaxSize(), state = barHide, showKey = Triple(feed, choice, all), header = {
            Column {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("novel_feeds")) {
                    item("f_pop") { FilterPill("Popular", feed == Feed.POPULAR, { feed = Feed.POPULAR }, testTag = "novel_feed_popular") }
                    item("f_new") { FilterPill("Latest", feed == Feed.LATEST, { feed = Feed.LATEST }, testTag = "novel_feed_latest") }
                    item("f_saved") { FilterPill("Saved", feed == Feed.SAVED, { feed = Feed.SAVED }, testTag = "novel_feed_saved") }
                    item("f_hist") { FilterPill("Continue", feed == Feed.HISTORY, { feed = Feed.HISTORY }, testTag = "novel_feed_history") }
                }
                val from = fellBackFrom?.let { id -> state.installed.firstOrNull { it.id == id } }
                if (from != null && all && (feed == Feed.POPULAR || feed == Feed.LATEST)) Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp).clip(yuRound(12)).background(GlassFill)
                        .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp).testTag("novel_fallback_note"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.nx_fallback_note, from.name), color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { noFallback = from.id; fellBackFrom = null }, modifier = Modifier.testTag("novel_fallback_retry")) {
                        Text(stringResource(R.string.nx_fallback_retry, from.name), color = SpotifyGreen, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }) {
            when (feed) {
                Feed.SAVED -> EntryGrid(library.values.filter { it.saved }.sortedByDescending { maxOf(it.readAt, it.addedAt) }, "Nothing saved yet", "Tap the bookmark on a novel to keep it here.", onOpenNovel)
                Feed.HISTORY -> EntryGrid(library.values.filter { it.readAt > 0 }.sortedByDescending { it.readAt }, "Nothing read yet", "Novels you open are listed here with the chapter you reached.", onOpenNovel)
                else -> {
                    // GROUP G3: the page opens on a banner: the novel you were reading, then this source's top picks.
                    val reading = remember(library) { library.values.filter { it.readAt > 0 }.maxByOrNull { it.readAt } }
                    // Group X (B5): Continue reading, newest first; a tap reopens the chapter at the exact line.
                    val continuing = remember(library) { library.values.filter { it.readAt > 0 && !it.chapterPath.isNullOrBlank() }.sortedByDescending { it.readAt }.take(10) }
                    val resume = onResume
                    if (all) BrowseAllGrid(feed == Feed.LATEST, onOpenNovel, reading, continuing, resume)
                    else if (plugin != null) BrowseGrid(plugin, feed == Feed.LATEST, onOpenNovel, reading,
                        onUnusable = if (state.installed.size > 1 && noFallback != plugin.id) ({ fellBackFrom = plugin.id }) else null,
                        continuing = continuing, onResume = resume)
                }
            }
        }
    }
}

@Composable
private fun EntryGrid(entries: List<NovelLibrary.Entry>, emptyTitle: String, emptyText: String, onOpen: (NovelItem) -> Unit) {
    if (entries.isEmpty()) { StateMessage(Icons.Default.BookmarkBorder, emptyTitle, emptyText, testTag = "novel_entries_empty"); return }
    LazyVerticalGrid(GridCells.Adaptive(108.dp), modifier = Modifier.fillMaxSize().testTag("novel_entries_grid"),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 48.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        itemsIndexed(entries, key = { _, e -> e.key }) { _, e ->
            NovelCover(NovelItem(e.pluginId, e.name, e.path, e.cover), e.chapterName) { onOpen(it) }
        }
    }
}

/**
 * NOVEL-2: the error state of a novel page. A site check gets Yumeiro's sentence and "Open <site>" (Yumeiro's check
 * page; the person passes it), which loads again once passed; other errors keep their text and "Try again".
 */
@Composable
private fun NovelError(pluginId: String, title: String, error: String, testTag: String, onRetry: () -> Unit,
                       secondary: Pair<String, () -> Unit>? = null) {
    val p = NovelSources.plugin(pluginId)
    if (p != null && dev.asfakul.yumeiro.sources.novel.NovelSiteCheck.isSiteCheck(error)) {
        StateMessage(Icons.Default.ErrorOutline, title, dev.asfakul.yumeiro.sources.novel.NovelSiteCheck.message(p.name),
            primary = "Open ${p.name}" to {
                dev.asfakul.yumeiro.online.web.BrowserCheck.openSite(p.site.trimEnd('/') + "/") { ok -> if (ok) onRetry() }
            },
            secondary = "Try again" to onRetry, suggestions = false, testTag = "${testTag}_check")
    } else StateMessage(Icons.Default.ErrorOutline, title, error, primary = "Try again" to onRetry, secondary = secondary, testTag = testTag)
}

@Composable
private fun BrowseGrid(plugin: NovelPlugin, latest: Boolean, onOpen: (NovelItem) -> Unit, reading: NovelLibrary.Entry? = null, onUnusable: (() -> Unit)? = null,
    continuing: List<NovelLibrary.Entry> = emptyList(), onResume: ((NovelLibrary.Entry) -> Unit)? = null) {
    val context = LocalContext.current
    val items = remember(plugin.id, latest) { mutableStateListOf<NovelItem>() }
    var page by remember(plugin.id, latest) { mutableIntStateOf(1) }
    var loading by remember(plugin.id, latest) { mutableStateOf(false) }
    var end by remember(plugin.id, latest) { mutableStateOf(false) }
    var error by remember(plugin.id, latest) { mutableStateOf<String?>(null) }
    var attempt by remember(plugin.id, latest) { mutableIntStateOf(0) }
    LaunchedEffect(plugin.id, latest, page, attempt) {
        if (end) return@LaunchedEffect
        loading = true; error = null
        try {
            val got = NovelSources.popular(context, plugin.id, page, latest)
            val fresh = got.filter { g -> items.none { it.path == g.path } }
            if (fresh.isEmpty()) end = true else items.addAll(fresh)
        } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message ?: "Couldn't load" }
        loading = false
    }
    // Group X (B4): the first page failed or was empty: with other sources installed, the page switches to them.
    val unusable = items.isEmpty() && !loading && (error != null || end)
    val switchAway by rememberUpdatedState(onUnusable)
    LaunchedEffect(unusable) { if (unusable) switchAway?.invoke() }
    if (items.isEmpty() && error != null) {
        NovelError(plugin.id, "Couldn't load ${plugin.name}", error!!, "novel_browse_error", onRetry = { attempt++ })
        return
    }
    if (items.isEmpty() && !loading && end) { StateMessage(Icons.AutoMirrored.Filled.MenuBook, "Nothing here", "${plugin.name} listed no novels.", testTag = "novel_browse_empty"); return }
    val grid = rememberLazyGridState()
    LaunchedEffect(grid, items.size) {
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
            if (!loading && !end && error == null && items.isNotEmpty() && last >= items.size - 6) page++
        }
    }
    LazyVerticalGrid(GridCells.Adaptive(108.dp), state = grid, modifier = Modifier.fillMaxSize().testTag("novel_browse_grid"),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 48.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val banner = NovelBanner.slides(items, reading, plugin.name, latest)
        if (banner.isNotEmpty()) item(key = "novel_banner", span = { GridItemSpan(maxLineSpan) }, contentType = "banner") {
            Box(Modifier.padding(bottom = 6.dp)) {
                dev.asfakul.yumeiro.ui.screens.home.HeroCarousel(banner.map { it.slide }, tag = "novel_banner") { i ->
                    val r = reading
                    if (i == 0 && r != null && onResume != null && !r.chapterPath.isNullOrBlank()) onResume(r) else onOpen(banner[i].item)
                }
            }
        }
        novelContinueRow(continuing, onResume)
        itemsIndexed(items, key = { _, it -> it.path }) { _, it -> NovelCover(it, null) { n -> onOpen(n) } }
        // SKELETON-2: cover placeholders in the grid's own shape instead of a spinner.
        if (loading) items(dev.asfakul.yumeiro.ui.components.skeletonCells(items.isNotEmpty()), key = { "sk_$it" }, contentType = { "skeleton" }) {
            dev.asfakul.yumeiro.ui.components.CoverSkeleton()
        }
        if (error != null && items.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.couldnt_load_more_try_again), color = SpotifyGreen) }
        }
    }
}

/**
 * Group X (B4): Popular / Latest from every installed source in one grid (taken in turns, the same novel once), each
 * cover saying its source. Sources that fail are named above the grid with "Try again"; one that runs out of pages
 * stops being asked.
 */
@Composable
private fun BrowseAllGrid(latest: Boolean, onOpen: (NovelItem) -> Unit, reading: NovelLibrary.Entry?, continuing: List<NovelLibrary.Entry> = emptyList(), onResume: ((NovelLibrary.Entry) -> Unit)? = null) {
    val context = LocalContext.current
    val state by NovelSources.state.collectAsState()
    val ids = state.installed.map { it.id }
    val items = remember(ids, latest) { mutableStateListOf<NovelItem>() }
    var page by remember(ids, latest) { mutableIntStateOf(1) }
    var loading by remember(ids, latest) { mutableStateOf(false) }
    var live by remember(ids, latest) { mutableStateOf(ids.toSet()) }
    var failed by remember(ids, latest) { mutableStateOf<List<NovelSources.SearchGroup>>(emptyList()) }
    var attempt by remember(ids, latest) { mutableIntStateOf(0) }
    LaunchedEffect(ids, latest, page, attempt) {
        if (live.isEmpty()) return@LaunchedEffect
        loading = true
        val groups = NovelSources.popularAll(context, page, latest, only = live)
        val ok = groups.filter { it.error == null }
        items.addAll(NovelFeedMerge.merge(items, ok.map { it.items }))
        // A source that answered with nothing has no more pages; a failing one stops being asked until "Try again".
        val bad = groups.filter { it.error != null }
        live = live - ok.filter { g -> g.items.isEmpty() }.map { it.plugin.id }.toSet() - bad.map { it.plugin.id }.toSet()
        failed = failed.filter { f -> groups.none { it.plugin.id == f.plugin.id } } + bad
        loading = false
    }
    val names = remember(state.installed) { state.installed.associate { it.id to it.name } }
    if (items.isEmpty() && loading) { Column(Modifier.fillMaxSize().padding(top = 8.dp).testTag("novel_all_loading")) { repeat(3) { dev.asfakul.yumeiro.ui.components.CoverRowSkeleton() } }; return }
    if (items.isEmpty()) {
        StateMessage(Icons.AutoMirrored.Filled.MenuBook, stringResource(R.string.nx_all_none_title),
            failed.joinToString("\n") { "${it.plugin.name}: ${it.error}" }.ifBlank { stringResource(R.string.nx_all_none_text) },
            primary = stringResource(R.string.nx_try_again) to { items.clear(); failed = emptyList(); live = ids.toSet(); page = 1; attempt++ }, testTag = "novel_all_empty")
        return
    }
    val grid = rememberLazyGridState()
    LaunchedEffect(grid, items.size) {
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
            if (!loading && live.isNotEmpty() && last >= items.size - 6) page++
        }
    }
    val label = stringResource(R.string.nx_all_sources)
    LazyVerticalGrid(GridCells.Adaptive(108.dp), state = grid, modifier = Modifier.fillMaxSize().testTag("novel_all_grid"),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 48.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (failed.isNotEmpty()) item(key = "novel_all_failed", span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth().clip(yuRound(12)).background(GlassFill).padding(start = 12.dp, end = 4.dp).testTag("novel_all_failed"), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.nx_some_failed, failed.joinToString(", ") { it.plugin.name }), color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = {
                    items.clear(); failed = emptyList(); live = ids.toSet(); page = 1; attempt++
                }) { Text(stringResource(R.string.nx_try_again), color = SpotifyGreen, fontSize = 12.sp) }
            }
        }
        val banner = NovelBanner.slides(items, reading, label, latest)
        if (banner.isNotEmpty()) item(key = "novel_banner", span = { GridItemSpan(maxLineSpan) }, contentType = "banner") {
            Box(Modifier.padding(bottom = 6.dp)) {
                dev.asfakul.yumeiro.ui.screens.home.HeroCarousel(banner.map { it.slide }, tag = "novel_banner") { i ->
                    val r = reading
                    if (i == 0 && r != null && onResume != null && !r.chapterPath.isNullOrBlank()) onResume(r) else onOpen(banner[i].item)
                }
            }
        }
        novelContinueRow(continuing, onResume)
        itemsIndexed(items, key = { _, it -> it.pluginId + "|" + it.path }) { _, it -> NovelCover(it, names[it.pluginId]) { n -> onOpen(n) } }
        if (loading) items(dev.asfakul.yumeiro.ui.components.skeletonCells(true), key = { "sk_$it" }, contentType = { "skeleton" }) {
            dev.asfakul.yumeiro.ui.components.CoverSkeleton()
        }
    }
}

/** One row per source, filled as each source answers; a failing source says so and doesn't hold up the rest. */
@Composable
private fun SearchAllResults(query: String, onOpen: (NovelItem) -> Unit) {
    val context = LocalContext.current
    var groups by remember(query) { mutableStateOf<List<NovelSources.SearchGroup>?>(null) }
    LaunchedEffect(query) { groups = null; groups = NovelSources.searchAll(context, query) }
    val g = groups
    if (g == null) { Column(Modifier.fillMaxSize().testTag("novel_search_loading")) { repeat(3) { dev.asfakul.yumeiro.ui.components.CoverRowSkeleton() } }; return } // SKELETON-2
    if (g.all { it.items.isEmpty() }) {
        StateMessage(Icons.AutoMirrored.Filled.MenuBook, "No novels found", g.filter { it.error != null }.joinToString("\n") { "${it.plugin.name}: ${it.error}" }.ifBlank { "No source has \"$query\"." }, testTag = "novel_search_empty")
        return
    }
    LazyColumn(Modifier.fillMaxSize().testTag("novel_search_results"), contentPadding = PaddingValues(bottom = 48.dp)) {
        items(g.sortedByDescending { it.items.size }, key = { it.plugin.id }) { grp ->
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(grp.plugin.name + if (grp.items.isNotEmpty()) " · ${grp.items.size}" else "", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 16.dp))
                if (grp.error != null) Text(grp.error, color = TextTertiary, fontSize = 12.sp, maxLines = 2, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                else if (grp.items.isEmpty()) Text(stringResource(R.string.no_results), color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                else LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(grp.items, key = { it.path }) { NovelCover(it, null, width = 104.dp) { n -> onOpen(n) } }
                }
            }
        }
    }
}

/** Group X (B5): "Continue reading" across the grid's width; each cover reopens its chapter at the saved line. */
private fun androidx.compose.foundation.lazy.grid.LazyGridScope.novelContinueRow(continuing: List<NovelLibrary.Entry>, onResume: ((NovelLibrary.Entry) -> Unit)?) {
    if (continuing.isEmpty() || onResume == null) return
    item(key = "novel_continue", span = { GridItemSpan(maxLineSpan) }, contentType = "continue") {
        Column(Modifier.testTag("novel_continue")) {
            Text(stringResource(R.string.continue_reading), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(continuing.size, key = { continuing[it].key }) { i ->
                    val e = continuing[i]
                    Box(Modifier.testTag("novel_continue_${e.path}")) {
                        NovelCover(NovelItem(e.pluginId, e.name, e.path, e.cover), e.chapterName, width = 96.dp) { onResume(e) }
                    }
                }
            }
        }
    }
}

@Composable
private fun NovelCover(n: NovelItem, subtitle: String?, width: androidx.compose.ui.unit.Dp? = null, onClick: (NovelItem) -> Unit) {
    Column((if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()).clickable(onClickLabel = "Open ${n.name}") { onClick(n) }.testTag("novel_cover")) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.7f).clip(yuRound(12)).background(GlassFill), contentAlignment = Alignment.Center) {
            dev.asfakul.yumeiro.ui.screens.online.PosterImage(n.cover, dev.asfakul.yumeiro.sources.novel.NovelImageHeaders.of(n.pluginId), Modifier.fillMaxSize(), fallbackIcon = Icons.AutoMirrored.Filled.MenuBook)
        }
        Spacer(Modifier.height(6.dp))
        Text(n.name, color = TextPrimary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
        if (subtitle != null) Text(subtitle, color = TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Repositories and plugins: add an LNReader index, install / update / remove sources. */
@Composable
fun NovelSourcesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by NovelSources.state.collectAsState()
    var repoField by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var available by remember { mutableStateOf<List<NovelPlugin>?>(null) }
    var filter by rememberSaveable { mutableStateOf("") }
    var lang by rememberSaveable { mutableStateOf<String?>(null) }
    var addOpen by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var langSheet by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.repos) {
        withContext(Dispatchers.IO) { NovelSources.ensureLoaded(context) }
        available = if (NovelSources.state.value.repos.isEmpty()) emptyList() else NovelSources.available(context)
    }
    suspend fun addRepoNow(url: String): Boolean {
        busy = "repo"; message = null
        val r = NovelSources.addRepo(context, url)
        r.onSuccess { message = "Added: $it sources"; repoField = "" }.onFailure { message = it.message ?: "Couldn't add that link" }
        busy = null
        return r.isSuccess
    }
    fun addRepo(url: String) { scope.launch { addRepoNow(url) } }
    Column(Modifier.fillMaxSize().testTag("novel_sources")) {
        SubPageHeader(title = "Novel sources", onBack = onBack, subtitle = "${state.installed.size} installed · ${state.repos.size} repositor${if (state.repos.size == 1) "y" else "ies"}") {
            // GROUP C: + opens the add sheet, ? explains the page (no link box or paragraph in the page).
            dev.asfakul.yumeiro.ui.components.AddIconButton("Add a novel repository", "novel_repo_plus", { addOpen = true })
            dev.asfakul.yumeiro.ui.components.HelpIconButton("About novel sources",
                "Novel sources come from an LNReader plugin list (plugins.min.json). Add a list with +, then install the sources you want. Sources are made by others; Yumeiro only runs them.",
                "novel_sources_help")
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 48.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item("repo_status") {
                Column {
                    if (state.repos.isEmpty()) Text(stringResource(R.string.no_repositories_yet_tap_to_add), color = TextSecondary, fontSize = 13.sp, modifier = Modifier.testTag("novel_repo_empty"))
                    if (busy == "repo") LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp), color = SpotifyGreen)
                    message?.let { Text(it, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp).testTag("novel_sources_message")) }
                }
            }
            items(state.repos, key = { "r_$it" }) { r ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(r, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    TextButton(onClick = { scope.launch(Dispatchers.IO) { NovelSources.removeRepo(context, r) } }) { Text(stringResource(R.string.remove_2), color = TextTertiary) }
                }
            }
            if (state.installed.isNotEmpty()) {
                item("inst_h") { Text(stringResource(R.string.installed), color = TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp)) }
                items(state.installed, key = { "i_${it.id}" }) { p ->
                    val newer = available?.firstOrNull { it.id == p.id && it.version != p.version }
                    PluginRow(p, installed = true, busy = busy == p.id, update = newer != null, onInstall = {
                        busy = p.id; scope.launch { NovelSources.install(context, newer ?: p).onFailure { message = it.message }; busy = null }
                    }, onRemove = { scope.launch(Dispatchers.IO) { NovelSources.uninstall(context, p.id) } })
                }
            }
            val all = available
            if (all == null && state.repos.isNotEmpty()) item("loading") { dev.asfakul.yumeiro.ui.components.SkeletonList(rows = 4, trailing = true) } // SKELETON-2
            if (!all.isNullOrEmpty()) {
                item("avail_h") {
                    Column(Modifier.padding(top = 8.dp)) {
                        dev.asfakul.yumeiro.ui.components.YuSectionHeader("Available", lang?.let { "Language: $it" }) {
                            dev.asfakul.yumeiro.ui.components.SearchIconButton(searchOpen || filter.isNotEmpty(), {
                                if (searchOpen || filter.isNotEmpty()) { searchOpen = false; filter = "" } else searchOpen = true
                            }, "novel_plugin_search", "Find a source")
                            dev.asfakul.yumeiro.ui.components.FilterIconButton(if (lang != null) 1 else 0, { langSheet = true }, "novel_lang_filter", "Language")
                        }
                        dev.asfakul.yumeiro.ui.components.HeaderSearchField(searchOpen || filter.isNotEmpty(), filter, { filter = it.take(40) }, "Find a source", "novel_plugin_filter", horizontalPadding = 0.dp)
                    }
                }
                val shown = all.filter { p -> state.installed.none { it.id == p.id } && (lang == null || p.language == lang) && (filter.isBlank() || p.name.contains(filter, true)) }
                items(shown, key = { "a_${it.id}" }) { p ->
                    PluginRow(p, installed = false, busy = busy == p.id, update = false, onInstall = {
                        busy = p.id; scope.launch { NovelSources.install(context, p).onSuccess { message = "${p.name} installed" }.onFailure { message = it.message }; busy = null }
                    }, onRemove = {})
                }
            }
        }
    }
    if (addOpen) dev.asfakul.yumeiro.ui.components.AddLinkSheet(
        "Add a novel repository", "https://…/plugins.min.json", "An LNReader plugin list (plugins.min.json).", "novel_repo",
        onDismiss = { addOpen = false }, onAdd = { addRepoNow(it) },
    ) {
        if (NovelSources.LNREADER_INDEX !in state.repos) OutlinedButton(onClick = { addRepo(NovelSources.LNREADER_INDEX); addOpen = false }, enabled = busy == null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("novel_repo_lnreader")) { Text(stringResource(R.string.add_lnreaders_list), color = TextPrimary) }
    }
    val allForLang = available.orEmpty()
    if (langSheet) dev.asfakul.yumeiro.ui.components.FilterSheet(onDismiss = { langSheet = false }, onReset = { lang = null }, resultCount = null, tag = "novel_lang_sheet") {
        dev.asfakul.yumeiro.ui.components.FilterGroup("Language") {
            FilterPill("All", lang == null, { lang = null }, testTag = "novel_lang_all")
            allForLang.groupingBy { it.language }.eachCount().entries.sortedByDescending { it.value }.map { it.key }.forEach { l ->
                FilterPill(l, lang == l, { lang = if (lang == l) null else l }, testTag = "novel_lang_$l")
            }
        }
    }
}

@Composable
private fun PluginRow(p: NovelPlugin, installed: Boolean, busy: Boolean, update: Boolean, onInstall: () -> Unit, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(yuRound(12)).background(GlassFill).padding(horizontal = 12.dp, vertical = 8.dp).testTag("novel_plugin_${p.id}"), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(yuRound(8))) { dev.asfakul.yumeiro.ui.screens.online.PosterImage(p.iconUrl, emptyMap(), Modifier.fillMaxSize(), fallbackIcon = Icons.AutoMirrored.Filled.MenuBook) }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(p.name, color = TextPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.v, p.language, p.version), color = TextTertiary, fontSize = 12.sp, maxLines = 1)
        }
        when {
            busy -> CircularProgressIndicator(color = SpotifyGreen, modifier = Modifier.size(22.dp))
            installed -> Row {
                if (update) TextButton(onClick = onInstall) { Text(stringResource(R.string.update), color = SpotifyGreen) }
                TextButton(onClick = onRemove, modifier = Modifier.testTag("novel_plugin_remove")) { Text(stringResource(R.string.remove_2), color = TextTertiary) }
            }
            else -> TextButton(onClick = onInstall, modifier = Modifier.testTag("novel_plugin_install")) { Text(stringResource(R.string.install), color = SpotifyGreen, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** A novel: cover, author, status, summary, save, resume, chapter list (with more pages when the site splits it). */
@Composable
fun NovelDetailScreen(pluginId: String, path: String, name: String, cover: String?, onBack: () -> Unit, onRead: (NovelItem, NovelChapter) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var details by remember { mutableStateOf(NovelSources.cachedDetails(pluginId, path)) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var morePage by remember { mutableIntStateOf(1) }
    var loadingMore by remember { mutableStateOf(false) }
    var newestFirst by rememberSaveable { mutableStateOf(false) }
    val library by NovelLibrary.entries.collectAsState()
    val entry = library[NovelLibrary.key(pluginId, path)]
    LaunchedEffect(pluginId, path, attempt) {
        withContext(Dispatchers.IO) { NovelSources.ensureLoaded(context); NovelLibrary.ensureLoaded(context) }
        if (details != null && attempt == 0) return@LaunchedEffect
        error = null
        try { details = NovelSources.details(context, pluginId, path); morePage = 1 }
        catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message ?: "Couldn't load this novel" }
    }
    val d = details
    val item = NovelItem(pluginId, d?.name ?: name, path, d?.cover ?: cover)
    Column(Modifier.fillMaxSize().testTag("novel_detail")) {
        SubPageHeader(title = d?.name ?: name, onBack = onBack, subtitle = NovelSources.plugin(pluginId)?.name) {
            val saved = entry?.saved == true
            IconButton(onClick = { scope.launch(Dispatchers.IO) { NovelLibrary.setSaved(context, item, !saved) } }, modifier = Modifier.testTag("novel_save")) {
                Icon(if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, if (saved) "Remove from saved" else "Save", tint = if (saved) SpotifyGreen else TextPrimary)
            }
        }
        if (d == null) {
            val e = error
            if (e != null) NovelError(pluginId, "Couldn't open this novel", e, "novel_detail_error", onRetry = { attempt++ })
            else dev.asfakul.yumeiro.ui.components.CoverDetailSkeleton() // SKELETON-2
            return@Column
        }
        val chapters = if (newestFirst) d.chapters.asReversed() else d.chapters
        LazyColumn(Modifier.fillMaxSize().testTag("novel_chapters"), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 48.dp)) {
            item("head") {
                Row {
                    Box(Modifier.width(108.dp).aspectRatio(0.7f).clip(yuRound(12)).background(GlassFill)) {
                        dev.asfakul.yumeiro.ui.screens.online.PosterImage(d.cover, dev.asfakul.yumeiro.sources.novel.NovelImageHeaders.of(d.pluginId), Modifier.fillMaxSize(), fallbackIcon = Icons.AutoMirrored.Filled.MenuBook)
                    }
                    Column(Modifier.padding(start = 14.dp)) {
                        d.author?.let { Text(it, color = TextSecondary, fontSize = 13.sp, maxLines = 2) }
                        d.status?.let { Text(it, color = TextTertiary, fontSize = 12.sp) }
                        Text("${d.chapters.size}${if (d.totalPages > morePage) "+" else ""} chapters", color = TextTertiary, fontSize = 12.sp)
                        val cleanGenres = dev.asfakul.yumeiro.ui.components.rememberCleanGenres(d.genres, 6)
                        if (cleanGenres.isNotEmpty()) Text(cleanGenres.joinToString(" · "), color = TextTertiary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                d.summary?.let { s ->
                    var open by remember { mutableStateOf(false) }
                    Text(s, color = TextSecondary, fontSize = 14.sp, maxLines = if (open) Int.MAX_VALUE else 5, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 12.dp).clickable(onClickLabel = if (open) "Show less" else "Show more") { open = !open })
                }
                val resume = entry?.chapterPath?.let { p -> d.chapters.firstOrNull { it.path == p } }
                val start = resume ?: d.chapters.firstOrNull()
                if (start != null) Button(
                    onClick = { onRead(item, start) }, colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent),
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp).heightIn(min = 48.dp).testTag("novel_read"),
                ) { Text(if (resume != null) "Continue · ${resume.name}" else "Start reading", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold) }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.chapters), color = TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { newestFirst = !newestFirst }, modifier = Modifier.testTag("novel_order")) { Text(if (newestFirst) "Newest first" else "First first", color = TextSecondary) }
                }
            }
            if (d.chapters.isEmpty()) item("none") { Text(stringResource(R.string.this_source_listed_no_chapters), color = TextTertiary, modifier = Modifier.padding(vertical = 12.dp)) }
            items(chapters, key = { it.path }) { ch ->
                val current = entry?.chapterPath == ch.path
                Row(Modifier.fillMaxWidth().clickable { onRead(item, ch) }.padding(vertical = 12.dp).testTag("novel_chapter"), verticalAlignment = Alignment.CenterVertically) {
                    Text(ch.name, color = if (current) SpotifyGreen else TextPrimary, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    ch.released?.let { Text(it.take(16), color = TextTertiary, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp)) }
                }
            }
            if (d.totalPages > morePage) item("more") {
                TextButton(onClick = {
                    if (loadingMore) return@TextButton
                    loadingMore = true
                    scope.launch {
                        runCatching { NovelSources.chapterPage(context, pluginId, path, morePage + 1) }
                            .onSuccess { more ->
                                val merged = (d.chapters + more.filter { m -> d.chapters.none { it.path == m.path } })
                                details = d.copy(chapters = merged).also(NovelSources::rememberDetails); morePage++
                            }.onFailure { error = it.message }
                        loadingMore = false
                    }
                }, modifier = Modifier.fillMaxWidth().testTag("novel_more_chapters")) { Text(if (loadingMore) "Loading…" else "Load more chapters (${morePage}/${d.totalPages})", color = SpotifyGreen) }
            }
        }
    }
}

/** The reader: the chapter's text, sized and spaced for reading, translated on request, position kept. */
@OptIn(FlowPreview::class)
@Composable
fun NovelReaderScreen(pluginId: String, path: String, name: String, chapterPath: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var details by remember { mutableStateOf(NovelSources.cachedDetails(pluginId, path)) }
    var current by rememberSaveable { mutableStateOf(chapterPath) }
    var paras by remember { mutableStateOf<List<NovelText.Para>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val p = prefs(context)
    var size by remember { mutableIntStateOf(p.getInt("size", 18).coerceIn(14, 28)) }
    var showSize by remember { mutableStateOf(false) }
    var translated by remember { mutableStateOf<List<String>?>(null) }
    var translating by remember { mutableStateOf(false) }
    var tlOn by remember { mutableStateOf(false) }
    var tlNote by remember { mutableStateOf<String?>(null) }
    val list = rememberLazyListState()

    LaunchedEffect(pluginId, path) {
        withContext(Dispatchers.IO) { NovelSources.ensureLoaded(context); NovelLibrary.ensureLoaded(context) }
        if (details == null) details = runCatching { NovelSources.details(context, pluginId, path) }.getOrNull()
    }
    val chapters = details?.chapters.orEmpty()
    val index = chapters.indexOfFirst { it.path == current }
    val chapter = chapters.getOrNull(index) ?: NovelChapter(name, current, null, null)
    val item = NovelItem(pluginId, details?.name ?: name, path, details?.cover)
    // The chapter loads before the novel's details may arrive: progress always uses the latest of both.
    val where by rememberUpdatedState(Triple(item, chapter, index))

    var spot by remember { mutableFloatStateOf(0f) }
    // The spot when the text size was changed (taken at the tap, before the paragraphs change height).
    var resizeSpot by remember { mutableStateOf<Float?>(null) }
    // The chapter whose saved spot is back on screen; nothing is saved before (it used to write the top first).
    var restoredFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(current, attempt) {
        paras = null; error = null; translated = null; tlNote = null; restoredFor = null
        try {
            val html = NovelSources.chapterHtml(context, pluginId, current)
            val ps = withContext(Dispatchers.Default) { NovelText.paragraphs(html) }
            if (ps.isEmpty()) error("This chapter has no text")
            paras = ps
            withContext(Dispatchers.IO) { NovelLibrary.ensureLoaded(context) }
            val saved = NovelLibrary.get(pluginId, path)
            // Group X (B5): back to the exact line: the paragraph and how far into it (a long paragraph re-opened at
            // its first line before, and the spot was then rewritten without the part inside the paragraph).
            val (at, inPara) = if (saved?.chapterPath == current) ReadingSpot.decode(saved.position, ps.size) else (0 to 0f)
            NovelSpot.restore(list, at, inPara)
            spot = if (saved?.chapterPath == current) saved.position.coerceIn(0f, 1f) else 0f
            restoredFor = current
        } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message ?: "Couldn't load this chapter" }
    }
    // The spot follows the reading (also while the text grows or shrinks), saved 0.5 s after the list settles.
    LaunchedEffect(current, paras, restoredFor) {
        val ps = paras ?: return@LaunchedEffect
        if (restoredFor != current) return@LaunchedEffect
        snapshotFlow { NovelSpot.read(list, ps.size) }.collect { spot = it }
    }
    LaunchedEffect(current, restoredFor) {
        if (restoredFor != current) return@LaunchedEffect
        snapshotFlow { spot }.debounce(500).collect { s ->
            val (it0, ch0, ix0) = where
            withContext(Dispatchers.IO) { NovelLibrary.progress(context, it0, ch0, ix0, s) }
        }
    }
    // A new text size keeps the same line at the top (the paragraphs change height; the share into one does not).
    LaunchedEffect(size) {
        val keep = resizeSpot ?: return@LaunchedEffect
        val ps = paras
        if (ps == null || restoredFor != current) { resizeSpot = null; return@LaunchedEffect }
        restoredFor = null
        val (at, inPara) = ReadingSpot.decode(keep, ps.size)
        NovelSpot.restore(list, at, inPara)
        spot = keep; resizeSpot = null; restoredFor = current
    }
    // Leaving the chapter or the app keeps the very last spot (the 0.5 s save could still be waiting).
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val keep = {
            if (restoredFor == current && paras != null) {
                val (it0, ch0, ix0) = where; val s = spot
                ReadingSaves.scope.launch { NovelLibrary.progress(context, it0, ch0, ix0, s) }
            }
        }
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_STOP) keep() }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); keep() }
    }
    LaunchedEffect(tlOn, paras) {
        val ps = paras ?: return@LaunchedEffect
        if (!tlOn) { translated = null; return@LaunchedEffect }
        if (!MangaTranslate.allowedOn(context)) { tlNote = "Translation isn't available on this device"; tlOn = false; return@LaunchedEffect }
        val s = MangaTranslate.settings(context)
        val from = NovelSources.plugin(pluginId)?.languageCode ?: MangaTlPlan.detect(ps.take(20).joinToString(" ") { it.text })
        if (from == s.dst) { tlNote = "This chapter is already in your language"; tlOn = false; return@LaunchedEffect }
        translating = true; tlNote = null
        try {
            val out = ArrayList<String>(ps.size)
            for (chunk in ps.chunked(40)) {
                out += MangaTranslate.texts(context, chunk.map { it.text }, from, s.dst, s.wifiOnly)
                translated = out.toList() + ps.drop(out.size).map { it.text }
            }
            if (out.all { it.isBlank() }) { tlNote = "Translation failed. Check the language pack in Manga translation settings."; translated = null; tlOn = false }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { tlNote = "Translation failed: ${e.message ?: "error"}"; translated = null; tlOn = false }
        translating = false
    }
    DisposableEffect(Unit) { onDispose { p.edit().putInt("size", size).apply() } }

    fun go(to: Int) { chapters.getOrNull(to)?.let { current = it.path } }

    Column(Modifier.fillMaxSize().testTag("novel_reader")) {
        SubPageHeader(title = chapter.name, onBack = onBack, subtitle = (details?.name ?: name) + if (index >= 0) " · ${index + 1}/${chapters.size}" else "") {
            IconButton(onClick = { showSize = !showSize }, modifier = Modifier.testTag("novel_text_size")) { Icon(Icons.Default.FormatSize, stringResource(R.string.text_size), tint = TextPrimary) }
            IconButton(onClick = { tlOn = !tlOn }, modifier = Modifier.testTag("novel_translate")) { Icon(Icons.Default.Translate, if (tlOn) "Show original" else "Translate", tint = if (tlOn) SpotifyGreen else TextPrimary) }
        }
        if (showSize) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { resizeSpot = resizeSpot ?: spot; size = (size - 1).coerceAtLeast(14) }, modifier = Modifier.testTag("novel_size_down")) { Text(stringResource(R.string.a), color = TextPrimary, fontSize = 15.sp) }
            Text("$size", color = TextSecondary, modifier = Modifier.padding(horizontal = 8.dp))
            TextButton(onClick = { resizeSpot = resizeSpot ?: spot; size = (size + 1).coerceAtMost(28) }, modifier = Modifier.testTag("novel_size_up")) { Text(stringResource(R.string.a_2), color = TextPrimary, fontSize = 19.sp) }
        }
        if (translating) LinearProgressIndicator(Modifier.fillMaxWidth(), color = SpotifyGreen)
        tlNote?.let { Text(it, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag("novel_tl_note")) }
        val ps = paras
        when {
            error != null -> NovelError(pluginId, "Couldn't load this chapter", error!!, "novel_reader_error", onRetry = { attempt++ },
                secondary = NovelSources.webUrl(context, pluginId, current)?.let { url -> "Open in browser" to { runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }; Unit } })
            ps == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { dev.asfakul.yumeiro.ui.components.YuLoader() }
            else -> LazyColumn(Modifier.fillMaxSize().testTag("novel_text"), state = list, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
                item("top") { Spacer(Modifier.height(4.dp)) }
                itemsIndexed(ps, key = { i, _ -> "$current#$i" }) { i, para ->
                    val text = translated?.getOrNull(i)?.takeIf { it.isNotBlank() } ?: para.text
                    Text(text, color = TextPrimary, fontSize = (if (para.heading) size + 3 else size).sp, lineHeight = (size * 1.6f).sp,
                        fontWeight = if (para.heading) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.fillMaxWidth().padding(vertical = (size / 3).dp))
                }
                item("nav") {
                    Row(Modifier.fillMaxWidth().padding(top = 24.dp).navigationBarsPadding(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { go(index - 1) }, enabled = index > 0, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("novel_prev")) {
                            Icon(Icons.Default.SkipPrevious, null, tint = TextPrimary); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.previous), color = TextPrimary)
                        }
                        Button(onClick = { go(index + 1) }, enabled = index >= 0 && index < chapters.size - 1, colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("novel_next")) {
                            Text(stringResource(R.string.next), fontWeight = FontWeight.Bold); Spacer(Modifier.width(6.dp)); Icon(Icons.Default.SkipNext, null)
                        }
                    }
                    if (index == chapters.size - 1 && chapters.isNotEmpty()) Text(stringResource(R.string.youve_reached_the_last_chapter_this), color = TextTertiary, fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).widthIn(max = 420.dp))
                }
            }
        }
    }
}

/**
 * GROUP G3: the Novel page's banner. The novel being read comes first ("Continue reading" with its chapter), then the
 * first few novels of the source's list that have a cover. Built only from real data; empty → no banner. Pure.
 */
internal object NovelBanner {
    data class Slide(val item: NovelItem, val slide: dev.asfakul.yumeiro.ui.screens.home.HeroSlide)
    const val PICKS = 5

    fun slides(items: List<NovelItem>, reading: NovelLibrary.Entry?, source: String, latest: Boolean): List<Slide> {
        val out = ArrayList<Slide>(PICKS + 1)
        if (reading != null) {
            val it = NovelItem(reading.pluginId, reading.name, reading.path, reading.cover)
            out += Slide(it, dev.asfakul.yumeiro.ui.screens.home.HeroSlide(
                key = "read:" + reading.key, name = reading.name, poster = reading.cover, headers = dev.asfakul.yumeiro.sources.novel.NovelImageHeaders.of(reading.pluginId), badge = "Continue reading",
                fresh = false, line = reading.chapterName, meta = NovelSources.plugin(reading.pluginId)?.name.orEmpty(), cta = "Continue",
            ))
        }
        items.asSequence().filter { !it.cover.isNullOrBlank() && (reading == null || it.path != reading.path) }.take(PICKS).forEach {
            out += Slide(it, dev.asfakul.yumeiro.ui.screens.home.HeroSlide(
                key = "pick:" + it.path, name = it.name, poster = it.cover, headers = dev.asfakul.yumeiro.sources.novel.NovelImageHeaders.of(it.pluginId), badge = if (latest) "Latest" else "Popular",
                fresh = latest, line = null, meta = source, cta = "Read",
            ))
        }
        return out
    }
}

/**
 * Group X (B5): the novel reader's exact spot. The list is a top spacer (0), the paragraphs (1..n) and the chapter
 * buttons (n+1); the spot is the paragraph at the top and the share of it scrolled past ([ReadingSpot]).
 */
internal object NovelSpot {
    fun read(list: androidx.compose.foundation.lazy.LazyListState, paras: Int): Float {
        if (paras <= 0) return 0f
        if (!list.canScrollForward && list.firstVisibleItemIndex > 0) return 1f
        val i = list.firstVisibleItemIndex
        if (i == 0) return 0f
        if (i > paras) return 1f
        val size = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == i }?.size ?: 0
        return ReadingSpot.encode(i - 1, list.firstVisibleItemScrollOffset, size, paras)
    }

    suspend fun restore(list: androidx.compose.foundation.lazy.LazyListState, para: Int, within: Float) {
        list.scrollToItem(para + 1)
        if (within <= 0f) return
        // The paragraph's height is known once it is laid out (the frame after the jump).
        val h = kotlinx.coroutines.withTimeoutOrNull(2_000) {
            snapshotFlow { list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == para + 1 }?.size ?: 0 }.first { it > 0 }
        } ?: return
        list.scrollToItem(para + 1, ReadingSpot.offsetPx(within, h))
    }
}
