/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.manga

import androidx.compose.ui.res.stringResource
import dev.asfakul.yumeiro.R
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.semantics.selected
import androidx.compose.material.icons.filled.Translate
import dev.asfakul.yumeiro.sources.manga.MangaTlPlan
import dev.asfakul.yumeiro.sources.manga.MangaTranslate
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.MenuBook as MenuBookMirrored
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.wrapContentSize
import kotlinx.coroutines.ensureActive
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.asfakul.yumeiro.online.OnlineErrors
import dev.asfakul.yumeiro.online.OnlineSettings
import dev.asfakul.yumeiro.sources.SourceHub
import dev.asfakul.yumeiro.sources.manga.MangaChapter
import dev.asfakul.yumeiro.sources.manga.MangaDetails
import dev.asfakul.yumeiro.sources.manga.MangaItem
import dev.asfakul.yumeiro.sources.manga.MangaLibrary
import dev.asfakul.yumeiro.sources.manga.MangaOffline
import dev.asfakul.yumeiro.sources.manga.MangaPageRef
import dev.asfakul.yumeiro.sources.manga.MangaProvider
import dev.asfakul.yumeiro.ui.components.FilterPill
import dev.asfakul.yumeiro.ui.components.StateMessage
import dev.asfakul.yumeiro.ui.components.SubPageHeader
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.OnAccent
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.takeWhile
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.asfakul.yumeiro.ui.theme.yuRound

private enum class Feed { POPULAR, LATEST, SEARCH }

/**
 * Manga home, Round 13 redesign in the Yumix layout: a slim source strip on top (which source,
 * change it, manage sources) that slides away while scrolling down and returns on a small scroll
 * up; a search box that searches the chosen source as you type; Popular / Latest / Saved chips;
 * then the endless cover grid. Source choice is a sheet with search and a language filter, so
 * 50 installed sources stay easy to pick from.
 */
@Composable
fun MangaHomeScreen(onBack: () -> Unit, onOpenTitle: (MangaItem) -> Unit, onOpenSources: () -> Unit) {
    val context = LocalContext.current
    val hub by SourceHub.state.collectAsState()
    val adult by OnlineSettings.adult.collectAsState()
    // R16 (G7): the library file is read off the main thread (it was read on main at first open).
    LaunchedEffect(Unit) { dev.asfakul.yumeiro.sources.Bridges.start(context.applicationContext); kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { MangaLibrary.load(context) } }
    val providers = remember(hub.generation, adult) { SourceHub.mangaProviders().filter { adult || !it.nsfw }.sortedBy { it.name.lowercase() } }
    val prefs = remember { context.getSharedPreferences("manga_home", 0) }
    var selectedId by rememberSaveable { mutableStateOf(prefs.getString("source", null)) }
    val provider = providers.firstOrNull { it.id == selectedId } ?: providers.firstOrNull()
    var feed by rememberSaveable { mutableStateOf(Feed.POPULAR) }
    var savedOnly by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var submitted by rememberSaveable { mutableStateOf("") }
    var showSources by remember { mutableStateOf(false) }
    val library by MangaLibrary.entries.collectAsState()
    val pal = dev.asfakul.yumeiro.ui.theme.LocalMtPalette.current
    // Search as you type, after a short pause (each search is a network call to the source).
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.isEmpty()) { submitted = ""; if (feed == Feed.SEARCH) feed = Feed.POPULAR; return@LaunchedEffect }
        if (q.length < 2) return@LaunchedEffect
        kotlinx.coroutines.delay(650)
        submitted = q; feed = Feed.SEARCH; savedOnly = false
    }
    val barHide = dev.asfakul.yumeiro.ui.components.rememberHideOnScrollState()

    Column(Modifier.fillMaxSize().testTag("manga_home")) {
        SubPageHeader(
            title = "Manga", onBack = onBack,
            subtitle = if (providers.isEmpty()) "Add a source to start reading"
                else (provider?.name.orEmpty() + (provider?.lang?.takeIf { it.isNotBlank() && it != "all" }?.let { " · ${it.uppercase()}" } ?: "")),
        ) {
            // GROUP C catch-all (same pattern as the Novel home): search and the source choice are header icons.
            if (providers.isNotEmpty()) {
                dev.asfakul.yumeiro.ui.components.SearchIconButton(searchOpen || query.isNotEmpty(), {
                    if (searchOpen || query.isNotEmpty()) { searchOpen = false; query = "" } else searchOpen = true
                }, "manga_search_open", "Search ${provider?.name.orEmpty()}")
                if (providers.size > 1) dev.asfakul.yumeiro.ui.components.FilterIconButton(0, { showSources = true }, "manga_source_pick", "Change source")
            }
            IconButton(onClick = onOpenSources, modifier = Modifier.testTag("manga_manage_sources")) { Icon(Icons.Default.Extension, stringResource(R.string.manage_sources), tint = TextPrimary) }
        }
        if (providers.isEmpty()) {
            StateMessage(Icons.AutoMirrored.Filled.MenuBook, "No manga sources yet",
                "Add a Mihon / Tachiyomi repository or a JavaScript (Mangayomi) repository, then install a source.",
                primary = "Open sources" to onOpenSources, testTag = "manga_empty")
            return@Column
        }
        val online by dev.asfakul.yumeiro.network.Connectivity.online.collectAsState()
        if (!online) dev.asfakul.yumeiro.ui.components.OfflineNotice("Reading from manga sources", tag = "offline_notice_manga")
        // B2/B3: the search field and filter chips move with the scroll (no expand/shrink layout animation).
        dev.asfakul.yumeiro.ui.components.HideOnScrollColumn(Modifier.fillMaxSize(), state = barHide, enabled = query.isEmpty(), showKey = feed to savedOnly, header = {
                Column {
                    dev.asfakul.yumeiro.ui.components.HeaderSearchField(
                        searchOpen || query.isNotEmpty(), query, { query = it.take(80) }, "Search ${provider?.name.orEmpty()}", "manga_search",
                    )
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (feed == Feed.SEARCH) item("f_search") { FilterPill("Results for \"${dev.asfakul.yumeiro.ui.components.SearchHistoryCodec.oneLine(submitted)}\"", true, {}) }
                        item("f_pop") { FilterPill("Popular", feed == Feed.POPULAR && !savedOnly, { query = ""; feed = Feed.POPULAR; savedOnly = false }) }
                        if (provider?.supportsLatest == true) item("f_new") { FilterPill("Latest", feed == Feed.LATEST && !savedOnly, { query = ""; feed = Feed.LATEST; savedOnly = false }) }
                        item("f_saved") { FilterPill("Saved", savedOnly, { query = ""; savedOnly = true }) }
                    }
                }
            }) {
            if (showSources) MangaSourceSheet(providers, provider?.id, onPick = {
                selectedId = it; prefs.edit().putString("source", it).apply(); showSources = false
                if (feed == Feed.SEARCH && submitted.isBlank()) feed = Feed.POPULAR
            }, onDismiss = { showSources = false })
            val saved = library.filter { it.saved }
            when {
                savedOnly -> SavedGrid(saved, onOpenTitle)
                provider != null -> MangaGrid(provider, feed, submitted, saved, onOpenTitle)
            }
        }
    }
}

/** Saved titles from every source, newest read first. */
@Composable
private fun SavedGrid(saved: List<MangaLibrary.Entry>, onOpen: (MangaItem) -> Unit) {
    if (saved.isEmpty()) {
        StateMessage(Icons.Default.BookmarkBorder, "Nothing saved yet", "Tap the bookmark on a title to keep it here.", testTag = "manga_saved_empty")
        return
    }
    LazyVerticalGrid(GridCells.Adaptive(108.dp), modifier = Modifier.fillMaxSize().testTag("manga_saved_grid"),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 48.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items(saved, key = { it.key }, contentType = { "cover" }) { e ->
            Cover(MangaItem(e.providerId, e.url, e.title, e.cover), subtitle = e.lastChapterName) { onOpen(it) }
        }
    }
}

/** "Read from": every source in one searchable list, with a language filter. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun MangaSourceSheet(providers: List<MangaProvider>, selected: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val pal = dev.asfakul.yumeiro.ui.theme.LocalMtPalette.current
    var q by rememberSaveable { mutableStateOf("") }
    var lang by rememberSaveable { mutableStateOf<String?>(null) }
    val langs = remember(providers) { providers.map { it.lang.ifBlank { "all" } }.distinct().sorted() }
    val shown = remember(providers, q, lang) {
        val byLang = providers.filter { lang == null || it.lang.ifBlank { "all" } == lang }
        val names = dev.asfakul.yumeiro.ui.screens.online.filterExtensionNames(byLang.map { it.name }, q)
        val order = names.withIndex().associate { it.value to it.index }
        byLang.filter { it.name in order }.sortedBy { order[it.name] }
    }
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss, containerColor = pal.surface,
        shape = dev.asfakul.yumeiro.ui.theme.Yume.shapeSheet,
        dragHandle = dev.asfakul.yumeiro.ui.components.sheetDragHandle(),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().testTag("manga_source_sheet")) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp)) {
                Text(stringResource(R.string.read_from), color = pal.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.manga_sources_installed, providers.size), color = pal.textSecondary, fontSize = 12.sp)
            }
            if (providers.size >= 6) dev.asfakul.yumeiro.ui.screens.watch.WatchSearchField(
                query = q, onQuery = { q = it.take(60) }, hint = "Search ${providers.size} sources",
                tag = "manga_source_search", modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            )
            if (langs.size > 1) LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item("l_all") { FilterPill("All languages", lang == null, { lang = null }) }
                items(langs, key = { it }) { l -> FilterPill(if (l == "all") "Multi" else l.uppercase(), lang == l, { lang = l }) }
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                if (shown.isEmpty()) item("none") {
                    Text(stringResource(R.string.no_source_matches), color = pal.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(24.dp).testTag("manga_source_none"))
                }
                items(shown, key = { it.id }) { p ->
                    val sel = p.id == selected
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)
                            .background(if (sel) pal.accent.copy(alpha = 0.14f) else Color.Transparent, yuRound(16))
                            .clickable { onPick(p.id) }.padding(horizontal = 10.dp, vertical = 8.dp).testTag("manga_source_${p.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        dev.asfakul.yumeiro.ui.screens.online.ChannelAvatar(p.name, selected = sel, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, color = pal.textPrimary, fontSize = 15.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(p.lang.takeIf { it.isNotBlank() && it != "all" }?.uppercase() ?: "Multi-language", p.ecosystem.takeIf { it.isNotBlank() }, if (p.supportsLatest) "Latest" else null, if (p.nsfw) "18+" else null).joinToString(" · "),
                                color = pal.textSecondary, fontSize = 12.sp, maxLines = 1,
                            )
                        }
                        if (sel) Icon(Icons.Default.Check, stringResource(R.string.selected), tint = pal.accent, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MangaGrid(provider: MangaProvider, feed: Feed, query: String, saved: List<MangaLibrary.Entry>, onOpen: (MangaItem) -> Unit) {
    val items = remember(provider.id, feed, query) { mutableStateListOf<MangaItem>() }
    var page by remember(provider.id, feed, query) { mutableStateOf(0) }
    var hasNext by remember(provider.id, feed, query) { mutableStateOf(true) }
    var loading by remember(provider.id, feed, query) { mutableStateOf(false) }
    var error by remember(provider.id, feed, query) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val grid = dev.asfakul.yumeiro.ui.components.ScrollMemory.grid("manga_browse:" + provider.id + ":" + feed + ":" + query)

    fun more() {
        if (loading || !hasNext) return
        loading = true; error = null
        scope.launch {
            runCatching {
                val next = page + 1
                when (feed) { Feed.POPULAR -> provider.popular(next); Feed.LATEST -> provider.latest(next); Feed.SEARCH -> provider.search(query, next) }
            }.onSuccess { r -> page++; hasNext = r.hasNext && r.items.isNotEmpty(); // G7-MB1: a title repeated inside one page is added once too (the grid is keyed by URL).
                val seen = items.mapTo(HashSet()) { it.url }; items.addAll(r.items.filter { seen.add(it.url) }) }
                .onFailure { error = OnlineErrors.friendly(it) }
            loading = false
        }
    }
    LaunchedEffect(provider.id, feed, query) { more() }
    LaunchedEffect(grid) {
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.distinctUntilChanged().collect { last ->
            if (last >= items.size - 6) more()
        }
    }
    LazyVerticalGrid(GridCells.Adaptive(108.dp), state = grid, modifier = Modifier.fillMaxSize().testTag("manga_grid"),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 48.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val continueReading = saved.filter { it.lastChapterUrl != null }.take(10)
        if (continueReading.isNotEmpty() && feed != Feed.SEARCH) item(span = { GridItemSpan(maxLineSpan) }, key = "continue") {
            Column {
                Text(stringResource(R.string.continue_reading), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(continueReading, key = { it.key }) { e ->
                        Cover(MangaItem(e.providerId, e.url, e.title, e.cover), subtitle = e.lastChapterName, width = 96.dp) { onOpen(it) }
                    }
                }
            }
        }
        items(items, key = { it.url }, contentType = { "cover" }) { m -> Cover(m, null) { onOpen(it) } }
        // SKELETON-2: cover placeholders in the grid's own shape instead of a spinner.
        if (loading) items(dev.asfakul.yumeiro.ui.components.skeletonCells(items.isNotEmpty()), key = { "sk_$it" }, contentType = { "skeleton" }) {
            dev.asfakul.yumeiro.ui.components.CoverSkeleton()
        }
        error?.let { e -> item(span = { GridItemSpan(maxLineSpan) }, key = "error") {
            StateMessage(Icons.Default.ErrorOutline, "Couldn't load", e, primary = "Retry" to { more() }, testTag = "manga_error")
        } }
        if (!loading && error == null && items.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
            StateMessage(Icons.AutoMirrored.Filled.MenuBook, "Nothing in this list", "This source returned no titles here. Try another list, another source, or search.", testTag = "manga_nothing")
        }
    }
}

@Composable
private fun Cover(m: MangaItem, subtitle: String?, width: androidx.compose.ui.unit.Dp? = null, onClick: (MangaItem) -> Unit) {
    Column((if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()).clickable { onClick(m) }) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.7f).clip(yuRound(12)).background(GlassFill), contentAlignment = Alignment.Center) {
            MangaCoverImage(m.providerId, m.cover, Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(6.dp))
        Text(m.title, color = TextPrimary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
        if (subtitle != null) Text(subtitle, color = TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Title page: cover, description, save toggle and the chapter list. */
@Composable
fun MangaDetailScreen(providerId: String, url: String, title: String, cover: String? = null, onBack: () -> Unit, onRead: (MangaItem, MangaChapter) -> Unit) {
    val context = LocalContext.current
    val provider = remember(providerId) { SourceHub.manga(providerId) }
    var details by remember { mutableStateOf<MangaDetails?>(null) }
    var chapters by remember { mutableStateOf<List<MangaChapter>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var newestFirst by rememberSaveable { mutableStateOf(true) }
    var attempt by remember { mutableStateOf(0) }
    val library by MangaLibrary.entries.collectAsState()
    LaunchedEffect(Unit) { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { MangaLibrary.load(context) } }
    LaunchedEffect(providerId, url, attempt) {
        if (provider == null) {
            error = "This source isn't installed any more"
            // F-13: chapters saved on the phone still open without the source.
            // MANGA-ORDER-1: in chapter order, not download order.
            chapters = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { MangaOffline.chapters(context, providerId, url) }.takeIf { it.isNotEmpty() }
            return@LaunchedEffect
        }
        error = null
        // R16 (G7): runCatching also caught the cancellation of an old load (Retry, or leaving the
        // page), which then put "Couldn't load" over the new one. Cancellation is rethrown now.
        // G7-MB1: the chapter list is keyed by URL; a chapter listed twice is shown once.
        try { details = provider.details(url); chapters = provider.chapters(url).distinctBy { it.url } }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) {
            // F-13: no connection (or the source broke): the chapters saved on this phone stay readable.
            val saved = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { MangaOffline.chapters(context, providerId, url) }
            if (saved.isNotEmpty()) chapters = saved
            error = OnlineErrors.friendly(e)
        }
    }
    val offlineGen by MangaOffline.changes.collectAsState()
    val offlineOn = remember { dev.asfakul.yumeiro.FeatureFlags.on(context, dev.asfakul.yumeiro.FeatureFlags.Flag.OFFLINE_MANGA) }
    val saving by MangaOffline.progress.collectAsState()
    val savedUrls by produceState(emptySet<String>(), offlineGen, chapters) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { MangaOffline.list(context) }
            .filter { it.providerId == providerId && it.mangaUrl == url }.map { it.chapterUrl }.toSet()
    }
    val entry = library.firstOrNull { it.providerId == providerId && it.url == url }
    val item = MangaItem(providerId, url, details?.title?.takeIf { it.isNotBlank() } ?: title,
        details?.cover?.takeIf { it.isNotBlank() } ?: entry?.cover ?: cover?.takeIf { it.isNotBlank() })
    // Saved before the details arrived (or saved from an older build without a cover): fill it in now.
    // FINAL-18: refreshMeta loads the library file when it is not loaded yet; with a fast source the
    // details arrived before the IO load above finished, so the file was read on the main thread
    // (MangaRetryDeviceTest.libraryLoadIsOffMain, run 44640073858). It runs on IO now.
    LaunchedEffect(details, entry?.key) {
        details?.let { d -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { MangaLibrary.refreshMeta(context, providerId, url, d.title, d.cover ?: cover) } }
    }
    val sorted = chapters?.let { if (newestFirst) it else it.reversed() }

    Column(Modifier.fillMaxSize().testTag("manga_detail")) {
        SubPageHeader(title = item.title, onBack = onBack, subtitle = provider?.name) {
            IconButton(onClick = { MangaLibrary.toggleSaved(context, item) }, modifier = Modifier.testTag("manga_save")) {
                Icon(if (entry?.saved == true) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, stringResource(R.string.save), tint = if (entry?.saved == true) SpotifyGreen else TextPrimary)
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 48.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item("head") {
                Row {
                    Box(Modifier.width(112.dp).aspectRatio(0.7f).clip(yuRound(14)).background(GlassFill), contentAlignment = Alignment.Center) {
                        MangaCoverImage(providerId, item.cover, Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        details?.author?.let { Text(it, color = TextSecondary, fontSize = 13.sp) }
                        details?.status?.let { Text(it, color = TextTertiary, fontSize = 12.sp) }
                        Spacer(Modifier.height(10.dp))
                        val resume = entry?.lastChapterUrl?.let { u -> chapters?.firstOrNull { it.url == u } }
                        val first = chapters?.lastOrNull()
                        (resume ?: first)?.let { ch ->
                            Button(onClick = { onRead(item, ch) }, shape = yuRound(20),
                                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent), modifier = Modifier.testTag("manga_read")) {
                                Text(if (resume != null) "Continue · ${ch.name}" else "Start reading", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            details?.genres?.takeIf { it.isNotEmpty() }?.let { raw -> item("genres") {
                // Group W (B2): one tidy name per genre, as everywhere else.
                val g = dev.asfakul.yumeiro.ui.components.rememberCleanGenres(raw)
                if (g.isNotEmpty()) Text(g.joinToString(" · "), color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp).testTag("manga_genres"))
            } }
            details?.description?.takeIf { it.isNotBlank() }?.let { d -> item("desc") {
                var open by remember { mutableStateOf(false) }
                Text(d, color = TextSecondary, fontSize = 13.sp, maxLines = if (open) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp).clickable { open = !open })
            } }
            error?.let { e -> item("err") { StateMessage(Icons.Default.ErrorOutline, "Couldn't load", e, primary = "Retry" to { attempt++ }) } }
            if (chapters == null && error == null) item("loading") {
                dev.asfakul.yumeiro.ui.components.SkeletonList(rows = 6, modifier = Modifier.padding(top = 12.dp))
            }
            sorted?.let { list ->
                item("ch_head") {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.chapters_2, list.size), color = TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { newestFirst = !newestFirst }) { Icon(Icons.Default.SwapVert, stringResource(R.string.sort), tint = TextSecondary) }
                    }
                }
                items(list, key = { it.url }) { ch ->
                    val read = entry?.readChapters?.contains(ch.url) == true
                    Surface(shape = yuRound(12), color = GlassFill, modifier = Modifier.fillMaxWidth().clickable { onRead(item, ch) }) {
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(ch.name, color = if (read) TextTertiary else TextPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val meta = listOfNotNull(
                                ch.dateUpload.takeIf { it > 0 }?.let { java.text.DateFormat.getDateInstance().format(java.util.Date(it)) },
                                ch.scanlator, if (entry?.lastChapterUrl == ch.url) "Page ${entry.lastPage + 1}" else null,
                            )
                            if (meta.isNotEmpty()) Text(meta.joinToString(" · "), color = TextTertiary, fontSize = 11.sp)
                        }
                        // F-13: save this chapter on the phone / saving (tap to stop) / saved (tap to remove).
                        val key = MangaOffline.folderName(providerId, ch.url)
                        val pct = saving[key]
                        val isSaved = ch.url in savedUrls
                        if (offlineOn || isSaved) IconButton(
                            onClick = {
                                when {
                                    pct != null -> MangaOffline.cancel(providerId, ch.url)
                                    isSaved -> { MangaOffline.delete(context, providerId, ch.url); android.widget.Toast.makeText(context, "Removed from this device", android.widget.Toast.LENGTH_SHORT).show() }
                                    provider != null -> MangaOffline.enqueue(context, provider, item, ch) { e ->
                                        android.widget.Toast.makeText(context, if (e == null) "Saved for offline reading" else "Couldn't save: ${OnlineErrors.friendly(e)}", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.testTag("manga_offline_${ch.url.hashCode()}"),
                        ) {
                            when {
                                pct != null -> CircularProgressIndicator(progress = { pct }, color = SpotifyGreen, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                                isSaved -> Icon(Icons.Default.DownloadDone, stringResource(R.string.saved_on_this_device_tap_to), tint = SpotifyGreen)
                                else -> Icon(Icons.Default.Download, stringResource(R.string.save_for_offline_reading), tint = TextSecondary)
                            }
                        }
                      }
                    }
                }
            }
        }
    }
}

/** Cover art with the headers its source needs; falls back to a book glyph when it can't load. */
@Composable
fun MangaCoverImage(providerId: String, cover: String?, modifier: Modifier = Modifier) {
    val hub by SourceHub.state.collectAsState()
    val headers = remember(providerId, hub.generation) { runCatching { SourceHub.manga(providerId)?.coverHeaders() }.getOrNull().orEmpty() }
    dev.asfakul.yumeiro.ui.screens.online.PosterImage(cover, headers, modifier, fallbackIcon = Icons.AutoMirrored.Filled.MenuBookMirrored)
}

/** A decoded page: one bitmap, or strips top to bottom for a tall page (G7-MR1, [MangaPagePlan]). */
internal class PageBitmap(val strips: List<Bitmap>, val width: Int, val height: Int) {
    val bytes: Int get() = strips.sumOf { it.byteCount }
}

private val pageCache = object : android.util.LruCache<String, PageBitmap>(64 * 1024 * 1024) {
    override fun sizeOf(key: String, value: PageBitmap) = value.bytes
}

private suspend fun loadPage(provider: MangaProvider, page: MangaPageRef, lowRam: Boolean): PageBitmap = withContext(Dispatchers.IO) {
    val key = provider.id + "|" + (page.imageUrl ?: page.url) + "|" + page.index
    pageCache.get(key)?.let { return@withContext it }
    val img = provider.image(page)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(img.bytes, 0, img.bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("This page isn't an image")
    val plan = dev.asfakul.yumeiro.sources.manga.MangaPagePlan.plan(bounds.outWidth, bounds.outHeight,
        if (lowRam) dev.asfakul.yumeiro.sources.manga.MangaPagePlan.PAGE_BUDGET_LOW_RAM else dev.asfakul.yumeiro.sources.manga.MangaPagePlan.PAGE_BUDGET)
    val opts = BitmapFactory.Options().apply { inSampleSize = plan.sample }
    val strips: List<Bitmap> = if (plan.bands.size == 1) {
        listOf(BitmapFactory.decodeByteArray(img.bytes, 0, img.bytes.size, opts) ?: error("This page isn't an image"))
    } else {
        @Suppress("DEPRECATION")
        val dec = runCatching { android.graphics.BitmapRegionDecoder.newInstance(img.bytes, 0, img.bytes.size, false) }.getOrNull()
        // A format that can't be split (GIF): one bitmap as before, now within the page budget (under the draw limit).
        if (dec == null) listOf(BitmapFactory.decodeByteArray(img.bytes, 0, img.bytes.size, opts) ?: error("This page isn't an image"))
        else try {
            plan.bands.map { b ->
                ensureActive()
                dec.decodeRegion(android.graphics.Rect(0, b.top, bounds.outWidth, b.bottom), opts) ?: error("This page isn't an image")
            }
        } finally { dec.recycle() }
    }
    PageBitmap(strips, strips.maxOf { it.width }, strips.sumOf { it.height }).also { pageCache.put(key, it) }
}

/**
 * Reader: vertical (webtoon) or paged (manga), pinch to zoom, progress saved per chapter.
 * F-18: previous / next chapter in the top bar, and a "Next chapter" card at the end of a chapter, so reading
 * goes on without going back to the title page. Each chapter gets fresh reader state ([key]).
 */
@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
fun MangaReaderScreen(providerId: String, mangaUrl: String, mangaTitle: String, chapterUrl: String, chapterName: String, onBack: () -> Unit) {
    var cur by rememberSaveable { mutableStateOf(chapterUrl to chapterName) }
    var chapters by remember { mutableStateOf<List<MangaChapter>>(emptyList()) }
    val readerContext = LocalContext.current
    LaunchedEffect(providerId, mangaUrl) {
        // MANGA-ORDER-1: no connection, a broken source or a removed one: Previous / Next move through the chapters
        // saved on the phone (in chapter order), with no network request for chapters that aren't there.
        // The saved list comes first (from disk, at once), so the buttons work while the source is still timing out.
        chapters = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { MangaOffline.chapters(readerContext, providerId, mangaUrl) }
        val online = SourceHub.manga(providerId)?.let { p ->
            try { p.chapters(mangaUrl) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
        }
        if (!online.isNullOrEmpty()) chapters = online
    }
    val (prev, next) = remember(chapters, cur.first) { dev.asfakul.yumeiro.sources.manga.MangaChapterNav.neighbours(chapters, cur.first) }
    androidx.compose.runtime.key(cur.first) {
        MangaReaderChapter(
            providerId, mangaUrl, mangaTitle, cur.first, cur.second, onBack,
            prev = prev, next = next, onGo = { c -> cur = c.url to c.name },
        )
    }
}

@Composable
private fun MangaReaderChapter(
    providerId: String, mangaUrl: String, mangaTitle: String, chapterUrl: String, chapterName: String, onBack: () -> Unit,
    prev: MangaChapter?, next: MangaChapter?, onGo: (MangaChapter) -> Unit,
) {
    val context = LocalContext.current
    val online = remember(providerId) { SourceHub.manga(providerId) }
    // F-13: a chapter saved on the phone reads from disk (no network, works with the source removed).
    val provider = remember(providerId, chapterUrl, online) { MangaOffline.provider(context, providerId, chapterUrl, online?.name ?: "Saved") ?: online }
    var pages by remember { mutableStateOf<List<MangaPageRef>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    var vertical by rememberSaveable { mutableStateOf(context.getSharedPreferences("manga_reader", 0).getBoolean("vertical", true)) }
    var chrome by remember { mutableStateOf(true) }
    var current by remember { mutableStateOf(0) }
    // F-11 (ADR-044): Translate pages (on-device). Off until the user turns it on in the reader.
    val tlAllowed = remember { dev.asfakul.yumeiro.FeatureFlags.on(context, dev.asfakul.yumeiro.FeatureFlags.Flag.MANGA_TRANSLATE) && MangaTranslate.allowedOn(context) }
    var tl by remember { mutableStateOf(MangaTranslate.settings(context)) }
    // TL-2: Auto reads the source's language (a Spanish source is translated from Spanish, not as English).
    val tlEff = remember(tl, provider) { MangaTranslate.resolve(tl, provider?.lang) }
    var tlSheet by remember { mutableStateOf(false) }
    var tlReady by remember { mutableStateOf(false) }
    var tlStatus by remember { mutableStateOf<String?>(null) }
    var tlTry by remember { mutableStateOf(0) }
    LaunchedEffect(tlAllowed, tlEff, tlTry) {
        tlReady = false
        if (!tlAllowed || !tlEff.on) { tlStatus = null; return@LaunchedEffect }
        val toName = MangaTlPlan.TARGETS[tlEff.dst] ?: tlEff.dst
        if (tlEff.from == tlEff.dst) {
            // The first version did nothing here (an English chapter on an English phone): say why.
            tlStatus = "This chapter is already in $toName. Tap to pick another language."
            return@LaunchedEffect
        }
        val pair = "${MangaTlPlan.FROM[tlEff.from] ?: tlEff.from} → $toName"
        tlStatus = if (MangaTranslate.ready(context, tlEff)) null else "Getting the $pair language pack…"
        MangaTranslate.prepare(context, tlEff)
            .onSuccess { tlReady = true; tlStatus = null }
            .onFailure { tlStatus = "Couldn't get the $pair language pack. Check the connection, then tap to try again." }
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { MangaTranslate.release() } }
    val tlUse = if (tlAllowed && tlEff.on && tlReady) tlEff else null
    val item = MangaItem(providerId, mangaUrl, mangaTitle, MangaLibrary.get(providerId, mangaUrl)?.cover)
    val chapter = MangaChapter(chapterUrl, chapterName)
    LaunchedEffect(attempt) {
        if (provider == null) { error = "This source isn't installed any more"; return@LaunchedEffect }
        error = null
        try { pages = provider.pages(chapterUrl) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { error = OnlineErrors.friendly(e) }
    }
    // Group X (B5): the exact spot: the page at the top and how far into it (a webtoon page is often several screens
    // tall; before, only the page was kept and a chapter re-opened at that page's top). [anchor] is where a new list
    // or pager opens: the saved spot first, then (after a switch between vertical and paged) where the reader was.
    var anchor by remember(pages) {
        mutableStateOf(MangaLibrary.get(providerId, mangaUrl)?.takeIf { it.lastChapterUrl == chapterUrl }?.let { it.lastPage to it.lastOffset } ?: (0 to 0f))
    }
    var within by remember { mutableFloatStateOf(0f) }
    // Nothing is saved until the saved spot is back on screen (it used to write page 0 first, then the real page).
    var restored by remember(pages, vertical) { mutableStateOf(false) }
    val latest by rememberUpdatedState(Triple(current, within, pages?.size ?: 0))
    // The leave / background save below outlives this state's re-creation (it is keyed on the page list), so it
    // reads the current one through this (run 46055991744: it read the first, never-restored one and saved nothing).
    val restoredNow by rememberUpdatedState(restored)
    LaunchedEffect(pages, restored) {
        if (!restored || (pages?.size ?: 0) == 0) return@LaunchedEffect
        snapshotFlow { latest }.debounce(400).collect { (page, w, n) ->
            if (n > 0) MangaLibrary.progress(context, item, chapter, page, finished = page >= n - 1, offset = w)
        }
    }
    // Leaving the chapter (or the app going to the background) keeps the very last spot, not one 0.4 s old.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val keep = { val (page, w, n) = latest; if (restoredNow && n > 0) MangaLibrary.progress(context, item, chapter, page, finished = page >= n - 1, offset = w) }
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_STOP) keep() }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); keep() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black).testTag("manga_reader")) {
        val list = pages
        when {
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                StateMessage(Icons.Default.ErrorOutline, "Couldn't open chapter", error, primary = "Retry" to { attempt++ })
            }
            list == null -> dev.asfakul.yumeiro.ui.components.YuLoader(Modifier.align(Alignment.Center))
            vertical -> {
                val startPage = anchor.first.coerceIn(0, (list.size - 1).coerceAtLeast(0))
                val state = rememberLazyListState(initialFirstVisibleItemIndex = startPage)
                LaunchedEffect(state) {
                    current = startPage; within = anchor.second
                    MangaSpot.restore(state, startPage, anchor.second)
                    restored = true
                    snapshotFlow { MangaSpot.read(state, list.size) }.collect { (page, w) -> current = page; within = w }
                }
                ZoomBox(onTap = { chrome = !chrome }) {
                    LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(list, key = { i, _ -> i }) { _, p -> PageImage(provider!!, p, fillWidth = true, tl = tlUse) }
                        if (next != null) item(key = "next_chapter") { NextChapterCard(next) { onGo(next) } }
                        else if (provider?.ecosystem == "offline") item(key = "saved_end") { SavedEndCard() }
                    }
                }
            }
            else -> {
                // F-18: one extra page after the last one offers the next chapter.
                val extra = if (next != null || provider?.ecosystem == "offline") 1 else 0
                val pager = rememberPagerState(initialPage = anchor.first.coerceIn(0, (list.size - 1).coerceAtLeast(0))) { list.size + extra }
                LaunchedEffect(pager) {
                    restored = true
                    // A paged reader shows a whole page: the share into it is kept as it was (so going back to
                    // vertical returns to the same line when the page did not change).
                    snapshotFlow { pager.currentPage }.collect { val p = it.coerceAtMost(list.size - 1); if (p != current) within = 0f; current = p }
                }
                HorizontalPager(pager, modifier = Modifier.fillMaxSize(), reverseLayout = false, beyondViewportPageCount = 1) { i ->
                    if (i >= list.size) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { next?.let { n -> NextChapterCard(n) { onGo(n) } } ?: SavedEndCard() }
                    else ZoomBox(onTap = { chrome = !chrome }) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { PageImage(provider!!, list[i], fillWidth = false, tl = tlUse) } }
                }
            }
        }
        AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.7f)).statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(mangaTitle, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(chapterName, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { prev?.let(onGo) }, enabled = prev != null, modifier = Modifier.testTag("reader_prev")) {
                    Icon(Icons.Default.SkipPrevious, stringResource(R.string.previous_chapter), tint = if (prev != null) Color.White else Color.White.copy(alpha = 0.3f))
                }
                IconButton(onClick = { next?.let(onGo) }, enabled = next != null, modifier = Modifier.testTag("reader_next")) {
                    Icon(Icons.Default.SkipNext, stringResource(R.string.next_chapter), tint = if (next != null) Color.White else Color.White.copy(alpha = 0.3f))
                }
                if (tlAllowed) IconButton(onClick = { tlSheet = true }, modifier = Modifier.testTag("reader_translate")) {
                    Icon(Icons.Default.Translate, if (tl.on) "Translate pages: on" else "Translate pages: off", tint = if (tl.on) SpotifyGreen else Color.White)
                }
                IconButton(onClick = { anchor = current to within; vertical = !vertical; context.getSharedPreferences("manga_reader", 0).edit().putBoolean("vertical", vertical).apply() }, modifier = Modifier.testTag("reader_mode")) {
                    Icon(if (vertical) Icons.Default.ViewCarousel else Icons.Default.SwapVert, if (vertical) "Paged mode" else "Vertical mode", tint = Color.White)
                }
            }
        }
        val total = pages?.size ?: 0
        if (total > 0) AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Text("${current + 1} / $total", color = Color.White, fontSize = 12.sp,
                modifier = Modifier.navigationBarsPadding().padding(12.dp).background(Color.Black.copy(alpha = 0.6f), yuRound(12)).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        tlStatus?.let { msg ->
            Text(msg, color = Color.White, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 44.dp)
                    .background(Color.Black.copy(alpha = 0.75f), yuRound(12)).clickable { if (tlEff.from == tlEff.dst) tlSheet = true else tlTry++ }
                    .padding(horizontal = 12.dp, vertical = 6.dp).testTag("tl_status"))
        }
        if (tlSheet) TranslateSheet(tl, MangaTlPlan.fromProvider(provider?.lang), onChange = { tl = it; MangaTranslate.save(context, it) }, onDismiss = { tlSheet = false })
        androidx.activity.compose.BackHandler(onBack = onBack)
    }
}

/** MANGA-ORDER-1: the last saved chapter, read offline: say why there is no next one instead of a silent dead end. */
@Composable
private fun SavedEndCard() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp)
            .background(Color.White.copy(alpha = 0.08f), yuRound(16)).padding(20.dp).testTag("reader_saved_end"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.end_of_your_saved_chapters), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.connect_to_the_internet_or_save), color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
    }
}

/** F-18: end-of-chapter card that opens the next chapter. */
@Composable
private fun NextChapterCard(next: MangaChapter, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp)
            .background(Color.White.copy(alpha = 0.08f), yuRound(16))
            .clickable(onClick = onClick).padding(20.dp).testTag("reader_next_card"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.end_of_chapter), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.next_2, next.name), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.SkipNext, null, tint = SpotifyGreen)
            Text(stringResource(R.string.read_next_chapter), color = SpotifyGreen, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** F-11: reader's Translate pages settings (switch, language from / to, Wi-Fi only for the pack). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TranslateSheet(s: MangaTranslate.Settings, sourceLang: String?, onChange: (MangaTranslate.Settings) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var packs by remember { mutableStateOf<List<String>?>(null) }
    var packsTry by remember { mutableStateOf(0) }
    LaunchedEffect(packsTry) { packs = MangaTranslate.packs(context) }
    @Composable fun Chip(label: String, on: Boolean, tag: String, click: () -> Unit) {
        Text(label, color = if (on) OnAccent else TextPrimary, fontSize = 13.sp,
            modifier = Modifier.padding(end = 6.dp, bottom = 6.dp).background(if (on) SpotifyGreen else Color.White.copy(alpha = 0.08f), yuRound(16))
                .clickable(onClick = click).padding(horizontal = 12.dp, vertical = 7.dp).testTag(tag)
                .semantics { selected = on })
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss, modifier = Modifier.testTag("tl_done")) { Text(stringResource(R.string.done)) } },
        title = { Text(stringResource(R.string.translate_pages)) },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()).testTag("tl_sheet")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.translate_speech_bubbles), Modifier.weight(1f), color = TextPrimary)
                    androidx.compose.material3.Switch(s.on, { onChange(s.copy(on = it)) }, modifier = Modifier.testTag("tl_switch"))
                }
                Text(stringResource(R.string.read_on_this_phone_only_the), color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.from), color = TextSecondary, fontSize = 12.sp)
                androidx.compose.foundation.layout.FlowRow {
                    for ((code, label) in MangaTlPlan.FROM) {
                        val shown = if (code == MangaTlPlan.AUTO) sourceLang?.let { "Auto · ${MangaTlPlan.FROM[it]}" } ?: "Auto · from the page" else label
                        Chip(shown, s.from == code, "tl_src_$code") { onChange(s.copy(from = code)) }
                    }
                }
                Text(stringResource(R.string.to_2), color = TextSecondary, fontSize = 12.sp)
                androidx.compose.foundation.layout.FlowRow {
                    for ((code, label) in MangaTlPlan.TARGETS) Chip(label, s.dst == code, "tl_dst_$code") { onChange(s.copy(dst = code)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.download_language_packs_on_wi_fi), Modifier.weight(1f), color = TextPrimary, fontSize = 13.sp)
                    androidx.compose.material3.Switch(s.wifiOnly, { onChange(s.copy(wifiOnly = it)) }, modifier = Modifier.testTag("tl_wifi"))
                }
                Text(stringResource(R.string.long_press_a_translation_to_hide), color = TextSecondary, fontSize = 12.sp)
                // TL-2: packs are about 30 MB each; the reader shows which are on the phone and lets you remove them.
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.language_packs_on_this_phone), color = TextSecondary, fontSize = 12.sp)
                val list = packs
                when {
                    list == null -> Text(stringResource(R.string.checking), color = TextSecondary, fontSize = 12.sp)
                    list.isEmpty() -> Text(stringResource(R.string.none_yet_english_is_built_in), color = TextSecondary, fontSize = 12.sp, modifier = Modifier.testTag("tl_packs_none"))
                    else -> for (code in list) Row(Modifier.fillMaxWidth().testTag("tl_pack_$code"), verticalAlignment = Alignment.CenterVertically) {
                        Text(MangaTlPlan.TARGETS[code] ?: MangaTlPlan.FROM[code] ?: java.util.Locale.forLanguageTag(code).displayLanguage, Modifier.weight(1f), color = TextPrimary, fontSize = 13.sp)
                        androidx.compose.material3.TextButton(
                            onClick = { scope.launch { MangaTranslate.deletePack(context, code); packsTry++ } },
                            modifier = Modifier.testTag("tl_pack_delete_$code"),
                        ) { Text(stringResource(R.string.remove_2)) }
                    }
                }
            }
        },
    )
}

/** TL-2: one page's translation state. */
private sealed interface TlPage {
    data object Off : TlPage
    data object Working : TlPage
    data class Done(val bubbles: List<MangaTlPlan.Bubble>) : TlPage
    data class Same(val language: String) : TlPage
    data object Failed : TlPage
}

/** TL-2: small corner note on a page: translating, failed (tap to retry), or already in the target language. */
@Composable
private fun TlPill(state: TlPage, pageIndex: Int, modifier: Modifier, onRetry: () -> Unit) {
    val text = when (state) {
        TlPage.Working -> "Translating…"
        TlPage.Failed -> "Couldn't translate · tap to retry"
        is TlPage.Same -> "Already in ${state.language}"
        else -> return
    }
    Text(text, color = Color.White, fontSize = 11.sp,
        modifier = modifier.padding(8.dp).background(Color.Black.copy(alpha = 0.7f), yuRound(10))
            .then(if (state == TlPage.Failed) Modifier.clickable(onClick = onRetry) else Modifier)
            .padding(horizontal = 8.dp, vertical = 4.dp).testTag("tl_page_${pageIndex}_state"))
}

/** F-11: translated bubbles drawn over one page, placed with the same scaling as the page image. */
@Composable
private fun TlOverlay(bubbles: List<MangaTlPlan.Bubble>, pageIndex: Int, w: Int, h: Int, fillWidth: Boolean, modifier: Modifier) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        val (k, dx, dy) = MangaTlPlan.fit(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), w, h, fillWidth)
        val density = androidx.compose.ui.platform.LocalDensity.current
        var hidden by remember(bubbles) { mutableStateOf(emptySet<Int>()) }
        bubbles.forEachIndexed { i, b ->
            if (i in hidden) return@forEachIndexed
            val (l, t, r, bt) = MangaTlPlan.layout(b, w).toList()
            Box(
                Modifier.offset { androidx.compose.ui.unit.IntOffset((dx + l * k).toInt(), (dy + t * k).toInt()) }
                    .size(with(density) { ((r - l) * k).toDp() }, with(density) { ((bt - t) * k).toDp() })
                    .background(Color.White.copy(alpha = 0.94f), yuRound(6))
                    .pointerInput(b) { detectTapGestures(onLongPress = { hidden = hidden + i }) }
                    .semantics { contentDescription = b.text }
                    .testTag("tl_box_${pageIndex}_$i"),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.text.BasicText(
                    b.text, Modifier.padding(2.dp),
                    style = androidx.compose.ui.text.TextStyle(color = Color.Black, fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                    autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 6.sp, maxFontSize = 20.sp, stepSize = 0.5.sp),
                )
            }
        }
    }
}

@Composable
private fun ZoomBox(onTap: () -> Unit, content: @Composable () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }, onDoubleTap = { if (scale > 1f) { scale = 1f; offsetX = 0f; offsetY = 0f } else scale = 2f }) }
            .pointerInput(Unit) {
                detectTransformGestures(panZoomLock = true) { _, pan, zoom, _ ->
                    if (zoom != 1f || scale > 1f) {
                        scale = (scale * zoom).coerceIn(1f, 4f)
                        if (scale > 1f) { offsetX += pan.x; offsetY += pan.y } else { offsetX = 0f; offsetY = 0f }
                    }
                }
            }
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY),
    ) { content() }
}

@Composable
private fun PageImage(provider: MangaProvider, page: MangaPageRef, fillWidth: Boolean, tl: MangaTranslate.Settings? = null) {
    var attempt by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val lowRam = remember { (context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager).isLowRamDevice }
    val bmp by produceState<Result<PageBitmap>?>(null, page, attempt) {
        value = try { Result.success(loadPage(provider, page, lowRam)) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Throwable) { Result.failure(e) }
    }
    val r = bmp
    val loaded = r?.getOrNull()
    // TL-2: the page says what translation is doing instead of staying silent (the first version showed nothing
    // while working, and nothing when a translation failed).
    var tlTry by remember { mutableStateOf(0) }
    val tlState by produceState<TlPage>(TlPage.Off, loaded, tl, tlTry) {
        value = TlPage.Off
        if (loaded == null || tl == null) return@produceState
        value = TlPage.Working
        value = try {
            TlPage.Done(MangaTranslate.page(context, provider.id + "|" + (page.imageUrl ?: page.url) + "|" + page.index, loaded.strips, tl))
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: MangaTranslate.SameLanguage) { TlPage.Same(MangaTlPlan.TARGETS[e.lang] ?: e.lang) }
        catch (e: Throwable) { TlPage.Failed }
    }
    val bubbles = (tlState as? TlPage.Done)?.bubbles
    when {
        r == null -> Box(Modifier.fillMaxWidth().aspectRatio(0.7f), contentAlignment = Alignment.Center) {
            Text("${page.index + 1}", color = Color.White.copy(alpha = 0.4f), fontSize = 28.sp)
        }
        r.isFailure -> Box(Modifier.fillMaxWidth().aspectRatio(0.7f).clickable { attempt++ }, contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.page_failed_tap_to_retry, page.index + 1), color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
        }
        else -> {
            val p = r.getOrThrow()
            if (p.strips.size == 1) Box(if (fillWidth) Modifier.fillMaxWidth() else Modifier.fillMaxSize()) {
                Image(p.strips[0].asImageBitmap(), "Page ${page.index + 1}", contentScale = if (fillWidth) ContentScale.FillWidth else ContentScale.Fit,
                    modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
                bubbles?.let { TlOverlay(it, page.index, p.width, p.height, fillWidth, Modifier.matchParentSize()) }
                TlPill(tlState, page.index, Modifier.align(Alignment.TopEnd)) { tlTry++ }
            } else {
                // A tall page: each strip is drawn on its own, so no single bitmap passes the draw or texture limit.
                val images = remember(p) { p.strips.map { it.asImageBitmap() } }
                val ratio = p.width.toFloat() / p.height
                val box = if (fillWidth) Modifier.fillMaxWidth().aspectRatio(ratio) else Modifier.fillMaxSize().wrapContentSize().aspectRatio(ratio)
                Box(box) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Page ${page.index + 1}" }) {
                    val k = size.width / p.width
                    var top = 0f
                    for (img in images) {
                        val h = img.height * k
                        drawImage(img, dstOffset = androidx.compose.ui.unit.IntOffset(0, top.toInt()),
                            dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), (top + h).toInt() - top.toInt()))
                        top += h
                    }
                }
                bubbles?.let { TlOverlay(it, page.index, p.width, p.height, true, Modifier.matchParentSize()) }
                TlPill(tlState, page.index, Modifier.align(Alignment.TopEnd)) { tlTry++ }
                }
            }
        }
    }
}

/**
 * Group X (B5): reading and restoring the exact spot in the vertical (webtoon) reader. A page shows a 0.7 placeholder
 * until its image is decoded, then takes the image's height, so the spot is applied again each time the page's
 * height changes, until the person scrolls or the page settles (5 s at most).
 */
internal object MangaSpot {
    /** The page at the top of [state] and the share of it scrolled past. */
    fun read(state: androidx.compose.foundation.lazy.LazyListState, pages: Int): Pair<Int, Float> {
        val i = state.firstVisibleItemIndex
        if (i >= pages) return (pages - 1).coerceAtLeast(0) to 0.999f
        val size = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == i }?.size ?: 0
        val spot = dev.asfakul.yumeiro.sources.reading.ReadingSpot.encode(i, state.firstVisibleItemScrollOffset, size, pages)
        return dev.asfakul.yumeiro.sources.reading.ReadingSpot.decode(spot, pages).let { (p, w) -> if (p != i) i to 0f else p to w }
    }

    suspend fun restore(state: androidx.compose.foundation.lazy.LazyListState, page: Int, within: Float) {
        if (within <= 0f) return
        var touched = false
        kotlinx.coroutines.coroutineScope {
            val watch = launch { state.interactionSource.interactions.collect { if (it is androidx.compose.foundation.interaction.DragInteraction.Start) touched = true } }
            kotlinx.coroutines.withTimeoutOrNull(5_000) {
                snapshotFlow { state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == page }?.size ?: 0 }
                    .filter { it > 0 }.distinctUntilChanged()
                    .takeWhile { !touched }
                    .collect { h -> state.scrollToItem(page, dev.asfakul.yumeiro.sources.reading.ReadingSpot.offsetPx(within, h)) }
            }
            watch.cancel()
        }
    }
}
