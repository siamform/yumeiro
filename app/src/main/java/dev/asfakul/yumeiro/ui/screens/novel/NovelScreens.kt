/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.novel

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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * NOVEL-1 (owner item 26): the novel pages — Novels (library, browse, search across sources), Novel sources
 * (repositories and plugins), a novel's page (details, chapters, resume) and the reader (text, size, translation,
 * Previous / Next, position kept). Same building blocks and look as the manga pages.
 */

private fun prefs(c: Context) = c.getSharedPreferences("novel_reader", Context.MODE_PRIVATE)

private enum class Feed { POPULAR, LATEST, SAVED, HISTORY }

@Composable
fun NovelHomeScreen(onBack: () -> Unit, onOpenNovel: (NovelItem) -> Unit, onOpenSources: () -> Unit) {
    val context = LocalContext.current
    val state by NovelSources.state.collectAsState()
    val library by NovelLibrary.entries.collectAsState()
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { NovelSources.ensureLoaded(context); NovelLibrary.ensureLoaded(context) }
        ready = true
    }
    var selectedId by rememberSaveable { mutableStateOf(prefs(context).getString("source", null)) }
    val plugin = state.installed.firstOrNull { it.id == selectedId } ?: state.installed.firstOrNull()
    var feed by rememberSaveable { mutableStateOf(Feed.POPULAR) }
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.isEmpty()) { submitted = ""; return@LaunchedEffect }
        if (q.length < 2) return@LaunchedEffect
        kotlinx.coroutines.delay(700)
        submitted = q
    }

    Column(Modifier.fillMaxSize().testTag("novel_home")) {
        SubPageHeader(title = "Novels", onBack = onBack, subtitle = if (state.installed.isEmpty()) "Add a source to start reading" else plugin?.name) {
            IconButton(onClick = onOpenSources, modifier = Modifier.testTag("novel_manage_sources")) { Icon(Icons.Default.Extension, "Novel sources", tint = TextPrimary) }
        }
        if (!ready) { Column(Modifier.fillMaxSize().padding(top = 8.dp)) { repeat(3) { dev.asfakul.yumeiro.ui.components.CoverRowSkeleton() } }; return@Column } // SKELETON-2
        if (state.installed.isEmpty()) {
            StateMessage(Icons.AutoMirrored.Filled.MenuBook, "No novel sources yet",
                "Add an LNReader plugin repository, then install a source. Yumeiro doesn't include any novel source.",
                primary = "Open novel sources" to onOpenSources, testTag = "novel_empty")
            return@Column
        }
        dev.asfakul.yumeiro.ui.screens.watch.WatchSearchField(
            query = query, onQuery = { query = it.take(80) }, hint = "Search all novel sources",
            tag = "novel_search", modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        if (submitted.isNotEmpty()) {
            SearchAllResults(submitted, onOpenNovel)
            return@Column
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("novel_feeds")) {
            item("f_pop") { FilterPill("Popular", feed == Feed.POPULAR, { feed = Feed.POPULAR }, testTag = "novel_feed_popular") }
            item("f_new") { FilterPill("Latest", feed == Feed.LATEST, { feed = Feed.LATEST }, testTag = "novel_feed_latest") }
            item("f_saved") { FilterPill("Saved", feed == Feed.SAVED, { feed = Feed.SAVED }, testTag = "novel_feed_saved") }
            item("f_hist") { FilterPill("Continue", feed == Feed.HISTORY, { feed = Feed.HISTORY }, testTag = "novel_feed_history") }
        }
        if (feed == Feed.POPULAR || feed == Feed.LATEST) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("novel_source_bar")) {
                items(state.installed, key = { it.id }) { p ->
                    FilterPill(p.name, p.id == plugin?.id, { selectedId = p.id; prefs(context).edit().putString("source", p.id).apply() }, testTag = "novel_source_${p.id}")
                }
            }
        }
        when (feed) {
            Feed.SAVED -> EntryGrid(library.values.filter { it.saved }.sortedByDescending { maxOf(it.readAt, it.addedAt) }, "Nothing saved yet", "Tap the bookmark on a novel to keep it here.", onOpenNovel)
            Feed.HISTORY -> EntryGrid(library.values.filter { it.readAt > 0 }.sortedByDescending { it.readAt }, "Nothing read yet", "Novels you open are listed here with the chapter you reached.", onOpenNovel)
            else -> if (plugin != null) BrowseGrid(plugin, feed == Feed.LATEST, onOpenNovel)
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
private fun BrowseGrid(plugin: NovelPlugin, latest: Boolean, onOpen: (NovelItem) -> Unit) {
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
        itemsIndexed(items, key = { _, it -> it.path }) { _, it -> NovelCover(it, null) { n -> onOpen(n) } }
        // SKELETON-2: cover placeholders in the grid's own shape instead of a spinner.
        if (loading) items(dev.asfakul.yumeiro.ui.components.skeletonCells(items.isNotEmpty()), key = { "sk_$it" }, contentType = { "skeleton" }) {
            dev.asfakul.yumeiro.ui.components.CoverSkeleton()
        }
        if (error != null && items.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            TextButton(onClick = { attempt++ }) { Text("Couldn't load more. Try again", color = SpotifyGreen) }
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
                else if (grp.items.isEmpty()) Text("No results", color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                else LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(grp.items, key = { it.path }) { NovelCover(it, null, width = 104.dp) { n -> onOpen(n) } }
                }
            }
        }
    }
}

@Composable
private fun NovelCover(n: NovelItem, subtitle: String?, width: androidx.compose.ui.unit.Dp? = null, onClick: (NovelItem) -> Unit) {
    Column((if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()).clickable(onClickLabel = "Open ${n.name}") { onClick(n) }.testTag("novel_cover")) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.7f).clip(RoundedCornerShape(12.dp)).background(GlassFill), contentAlignment = Alignment.Center) {
            dev.asfakul.yumeiro.ui.screens.online.PosterImage(n.cover, emptyMap(), Modifier.fillMaxSize(), fallbackIcon = Icons.AutoMirrored.Filled.MenuBook)
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
    LaunchedEffect(state.repos) {
        withContext(Dispatchers.IO) { NovelSources.ensureLoaded(context) }
        available = if (NovelSources.state.value.repos.isEmpty()) emptyList() else NovelSources.available(context)
    }
    fun addRepo(url: String) {
        busy = "repo"; message = null
        scope.launch {
            NovelSources.addRepo(context, url).onSuccess { message = "Added: $it sources"; repoField = "" }.onFailure { message = it.message ?: "Couldn't add that link" }
            busy = null
        }
    }
    Column(Modifier.fillMaxSize().testTag("novel_sources")) {
        SubPageHeader(title = "Novel sources", onBack = onBack, subtitle = "${state.installed.size} installed · ${state.repos.size} repositor${if (state.repos.size == 1) "y" else "ies"}")
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 48.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item("repo_add") {
                Column {
                    Text("Repository", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text("An LNReader plugin list (plugins.min.json). Sources are made by others; Yumeiro only runs them.", color = TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    dev.asfakul.yumeiro.ui.screens.watch.WatchSearchField(query = repoField, onQuery = { repoField = it.trim().take(400) }, hint = "https://…/plugins.min.json", tag = "novel_repo_field", modifier = Modifier.fillMaxWidth())
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { addRepo(repoField) }, enabled = repoField.length > 8 && busy == null, colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent),
                            modifier = Modifier.heightIn(min = 44.dp).testTag("novel_repo_add")) { Text("Add") }
                        if (NovelSources.LNREADER_INDEX !in state.repos) OutlinedButton(onClick = { addRepo(NovelSources.LNREADER_INDEX) }, enabled = busy == null,
                            modifier = Modifier.heightIn(min = 44.dp).testTag("novel_repo_lnreader")) { Text("Add LNReader's list", color = TextPrimary) }
                    }
                    if (busy == "repo") LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp), color = SpotifyGreen)
                    message?.let { Text(it, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp).testTag("novel_sources_message")) }
                }
            }
            items(state.repos, key = { "r_$it" }) { r ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(r, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    TextButton(onClick = { scope.launch(Dispatchers.IO) { NovelSources.removeRepo(context, r) } }) { Text("Remove", color = TextTertiary) }
                }
            }
            if (state.installed.isNotEmpty()) {
                item("inst_h") { Text("Installed", color = TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp)) }
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
                        Text("Available", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        dev.asfakul.yumeiro.ui.screens.watch.WatchSearchField(query = filter, onQuery = { filter = it.take(40) }, hint = "Find a source", tag = "novel_plugin_filter", modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                        val langs = all.groupingBy { it.language }.eachCount().entries.sortedByDescending { it.value }.take(12).map { it.key }
                        LazyRow(contentPadding = PaddingValues(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { FilterPill("All", lang == null, { lang = null }) }
                            items(langs) { l -> FilterPill(l, lang == l, { lang = if (lang == l) null else l }) }
                        }
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
}

@Composable
private fun PluginRow(p: NovelPlugin, installed: Boolean, busy: Boolean, update: Boolean, onInstall: () -> Unit, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(GlassFill).padding(horizontal = 12.dp, vertical = 8.dp).testTag("novel_plugin_${p.id}"), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))) { dev.asfakul.yumeiro.ui.screens.online.PosterImage(p.iconUrl, emptyMap(), Modifier.fillMaxSize(), fallbackIcon = Icons.AutoMirrored.Filled.MenuBook) }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(p.name, color = TextPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${p.language} · v${p.version}", color = TextTertiary, fontSize = 12.sp, maxLines = 1)
        }
        when {
            busy -> CircularProgressIndicator(color = SpotifyGreen, modifier = Modifier.size(22.dp))
            installed -> Row {
                if (update) TextButton(onClick = onInstall) { Text("Update", color = SpotifyGreen) }
                TextButton(onClick = onRemove, modifier = Modifier.testTag("novel_plugin_remove")) { Text("Remove", color = TextTertiary) }
            }
            else -> TextButton(onClick = onInstall, modifier = Modifier.testTag("novel_plugin_install")) { Text("Install", color = SpotifyGreen, fontWeight = FontWeight.SemiBold) }
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
            error?.let { e -> NovelError(pluginId, "Couldn't open this novel", e, "novel_detail_error", onRetry = { attempt++ }) }
            else dev.asfakul.yumeiro.ui.components.CoverDetailSkeleton() // SKELETON-2
            return@Column
        }
        val chapters = if (newestFirst) d.chapters.asReversed() else d.chapters
        LazyColumn(Modifier.fillMaxSize().testTag("novel_chapters"), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 48.dp)) {
            item("head") {
                Row {
                    Box(Modifier.width(108.dp).aspectRatio(0.7f).clip(RoundedCornerShape(12.dp)).background(GlassFill)) {
                        dev.asfakul.yumeiro.ui.screens.online.PosterImage(d.cover, emptyMap(), Modifier.fillMaxSize(), fallbackIcon = Icons.AutoMirrored.Filled.MenuBook)
                    }
                    Column(Modifier.padding(start = 14.dp)) {
                        d.author?.let { Text(it, color = TextSecondary, fontSize = 13.sp, maxLines = 2) }
                        d.status?.let { Text(it, color = TextTertiary, fontSize = 12.sp) }
                        Text("${d.chapters.size}${if (d.totalPages > morePage) "+" else ""} chapters", color = TextTertiary, fontSize = 12.sp)
                        if (d.genres.isNotEmpty()) Text(d.genres.take(6).joinToString(" · "), color = TextTertiary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                    Text("Chapters", color = TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { newestFirst = !newestFirst }, modifier = Modifier.testTag("novel_order")) { Text(if (newestFirst) "Newest first" else "First first", color = TextSecondary) }
                }
            }
            if (d.chapters.isEmpty()) item("none") { Text("This source listed no chapters.", color = TextTertiary, modifier = Modifier.padding(vertical = 12.dp)) }
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

    LaunchedEffect(current, attempt) {
        paras = null; error = null; translated = null; tlNote = null
        try {
            val html = NovelSources.chapterHtml(context, pluginId, current)
            val ps = withContext(Dispatchers.Default) { NovelText.paragraphs(html) }
            if (ps.isEmpty()) error("This chapter has no text")
            paras = ps
            withContext(Dispatchers.IO) { NovelLibrary.ensureLoaded(context) }
            val saved = NovelLibrary.get(pluginId, path)
            val at = if (saved?.chapterPath == current) (saved.position * ps.size).toInt().coerceIn(0, ps.size - 1) else 0
            list.scrollToItem(at + 1)
            val (it0, ch0, ix0) = where
            withContext(Dispatchers.IO) { NovelLibrary.progress(context, it0, ch0, ix0, at.toFloat() / ps.size) }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message ?: "Couldn't load this chapter" }
    }
    LaunchedEffect(current, paras) {
        val ps = paras ?: return@LaunchedEffect
        snapshotFlow { list.firstVisibleItemIndex }.debounce(800).collect { i ->
            val pos = ((i - 1).coerceAtLeast(0).toFloat() / ps.size).coerceIn(0f, 1f)
            val (it0, ch0, ix0) = where
            withContext(Dispatchers.IO) { NovelLibrary.progress(context, it0, ch0, ix0, if (!list.canScrollForward) 1f else pos) }
        }
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
            IconButton(onClick = { showSize = !showSize }, modifier = Modifier.testTag("novel_text_size")) { Icon(Icons.Default.FormatSize, "Text size", tint = TextPrimary) }
            IconButton(onClick = { tlOn = !tlOn }, modifier = Modifier.testTag("novel_translate")) { Icon(Icons.Default.Translate, if (tlOn) "Show original" else "Translate", tint = if (tlOn) SpotifyGreen else TextPrimary) }
        }
        if (showSize) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { size = (size - 1).coerceAtLeast(14) }, modifier = Modifier.testTag("novel_size_down")) { Text("A−", color = TextPrimary, fontSize = 15.sp) }
            Text("$size", color = TextSecondary, modifier = Modifier.padding(horizontal = 8.dp))
            TextButton(onClick = { size = (size + 1).coerceAtMost(28) }, modifier = Modifier.testTag("novel_size_up")) { Text("A+", color = TextPrimary, fontSize = 19.sp) }
        }
        if (translating) LinearProgressIndicator(Modifier.fillMaxWidth(), color = SpotifyGreen)
        tlNote?.let { Text(it, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag("novel_tl_note")) }
        val ps = paras
        when {
            error != null -> NovelError(pluginId, "Couldn't load this chapter", error!!, "novel_reader_error", onRetry = { attempt++ },
                secondary = NovelSources.webUrl(context, pluginId, current)?.let { url -> "Open in browser" to { runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }; Unit } })
            ps == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = SpotifyGreen) }
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
                            Icon(Icons.Default.SkipPrevious, null, tint = TextPrimary); Spacer(Modifier.width(6.dp)); Text("Previous", color = TextPrimary)
                        }
                        Button(onClick = { go(index + 1) }, enabled = index >= 0 && index < chapters.size - 1, colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("novel_next")) {
                            Text("Next", fontWeight = FontWeight.Bold); Spacer(Modifier.width(6.dp)); Icon(Icons.Default.SkipNext, null)
                        }
                    }
                    if (index == chapters.size - 1 && chapters.isNotEmpty()) Text("You've reached the last chapter this source lists.", color = TextTertiary, fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).widthIn(max = 420.dp))
                }
            }
        }
    }
}
