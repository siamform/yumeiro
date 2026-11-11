/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.manga

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
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.MenuBook as MenuBookMirrored
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ViewCarousel
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Feed { POPULAR, LATEST, SEARCH }

/** Manga home: pick a source (Mihon or JavaScript), browse popular / latest, or search. */
@Composable
fun MangaHomeScreen(onBack: () -> Unit, onOpenTitle: (MangaItem) -> Unit, onOpenSources: () -> Unit) {
    val context = LocalContext.current
    val hub by SourceHub.state.collectAsState()
    val adult by OnlineSettings.adult.collectAsState()
    LaunchedEffect(Unit) { dev.asfakul.yumeiro.sources.Bridges.start(context.applicationContext); MangaLibrary.load(context) }
    val providers = remember(hub.generation, adult) { SourceHub.mangaProviders().filter { adult || !it.nsfw }.sortedBy { it.name.lowercase() } }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val provider = providers.firstOrNull { it.id == selectedId } ?: providers.firstOrNull()
    var feed by rememberSaveable { mutableStateOf(Feed.POPULAR) }
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    val library by MangaLibrary.entries.collectAsState()

    Column(Modifier.fillMaxSize().testTag("manga_home")) {
        SubPageHeader(title = "Manga", onBack = onBack, subtitle = if (providers.isEmpty()) "Add a source to start reading" else "${providers.size} sources") {
            IconButton(onClick = onOpenSources) { Icon(Icons.Default.Extension, "Sources", tint = TextPrimary) }
        }
        if (providers.isEmpty()) {
            StateMessage(Icons.Default.MenuBook, "No manga sources yet",
                "Add a Mihon / Tachiyomi repository or a JavaScript (Mangayomi) repository, then install a source.",
                primary = "Open sources" to onOpenSources, testTag = "manga_empty")
            return@Column
        }
        val online by dev.asfakul.yumeiro.network.Connectivity.online.collectAsState()
        if (!online) dev.asfakul.yumeiro.ui.components.OfflineNotice("Reading from manga sources", tag = "offline_notice_manga")
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(providers, key = { it.id }) { p ->
                FilterPill(label = p.name + if (p.lang.isNotBlank() && p.lang != "all") " · ${p.lang.uppercase()}" else "",
                    selected = p.id == provider?.id, onClick = { selectedId = p.id; if (feed == Feed.SEARCH && submitted.isBlank()) feed = Feed.POPULAR })
            }
        }
        OutlinedTextField(
            query, { query = it }, singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) },
            placeholder = { Text("Search ${provider?.name.orEmpty()}") },
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { submitted = query.trim(); feed = if (submitted.isBlank()) Feed.POPULAR else Feed.SEARCH }),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("manga_search"),
        )
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterPill("Popular", feed == Feed.POPULAR, { feed = Feed.POPULAR })
            if (provider?.supportsLatest == true) FilterPill("Latest", feed == Feed.LATEST, { feed = Feed.LATEST })
        }
        val saved = library.filter { it.saved }
        if (provider != null) MangaGrid(provider, feed, submitted, saved, onOpenTitle)
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
    val grid = rememberLazyGridState()

    fun more() {
        if (loading || !hasNext) return
        loading = true; error = null
        scope.launch {
            runCatching {
                val next = page + 1
                when (feed) { Feed.POPULAR -> provider.popular(next); Feed.LATEST -> provider.latest(next); Feed.SEARCH -> provider.search(query, next) }
            }.onSuccess { r -> page++; hasNext = r.hasNext && r.items.isNotEmpty(); items.addAll(r.items.filter { n -> items.none { it.url == n.url } }) }
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
                Text("Continue reading", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(continueReading, key = { it.key }) { e ->
                        Cover(MangaItem(e.providerId, e.url, e.title, e.cover), subtitle = e.lastChapterName, width = 96.dp) { onOpen(it) }
                    }
                }
            }
        }
        items(items, key = { it.url }) { m -> Cover(m, null) { onOpen(it) } }
        if (loading) item(span = { GridItemSpan(maxLineSpan) }, key = "loading") {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = SpotifyGreen) }
        }
        error?.let { e -> item(span = { GridItemSpan(maxLineSpan) }, key = "error") {
            StateMessage(Icons.Default.ErrorOutline, "Couldn't load", e, primary = "Retry" to { more() }, testTag = "manga_error")
        } }
        if (!loading && error == null && items.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
            StateMessage(Icons.Default.MenuBook, "Nothing in this list", "This source returned no titles here. Try another list, another source, or search.", testTag = "manga_nothing")
        }
    }
}

@Composable
private fun Cover(m: MangaItem, subtitle: String?, width: androidx.compose.ui.unit.Dp? = null, onClick: (MangaItem) -> Unit) {
    Column((if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()).clickable { onClick(m) }) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.7f).clip(RoundedCornerShape(12.dp)).background(GlassFill), contentAlignment = Alignment.Center) {
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
    LaunchedEffect(Unit) { MangaLibrary.load(context) }
    LaunchedEffect(providerId, url, attempt) {
        if (provider == null) { error = "This source isn't installed any more"; return@LaunchedEffect }
        error = null
        runCatching { details = provider.details(url); chapters = provider.chapters(url) }.onFailure { error = OnlineErrors.friendly(it) }
    }
    val entry = library.firstOrNull { it.providerId == providerId && it.url == url }
    val item = MangaItem(providerId, url, details?.title?.takeIf { it.isNotBlank() } ?: title,
        details?.cover?.takeIf { it.isNotBlank() } ?: entry?.cover ?: cover?.takeIf { it.isNotBlank() })
    // Saved before the details arrived (or saved from an older build without a cover): fill it in now.
    LaunchedEffect(details, entry?.key) { details?.let { d -> MangaLibrary.refreshMeta(context, providerId, url, d.title, d.cover ?: cover) } }
    val sorted = chapters?.let { if (newestFirst) it else it.reversed() }

    Column(Modifier.fillMaxSize().testTag("manga_detail")) {
        SubPageHeader(title = item.title, onBack = onBack, subtitle = provider?.name) {
            IconButton(onClick = { MangaLibrary.toggleSaved(context, item) }, modifier = Modifier.testTag("manga_save")) {
                Icon(if (entry?.saved == true) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, "Save", tint = if (entry?.saved == true) SpotifyGreen else TextPrimary)
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 48.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item("head") {
                Row {
                    Box(Modifier.width(112.dp).aspectRatio(0.7f).clip(RoundedCornerShape(14.dp)).background(GlassFill), contentAlignment = Alignment.Center) {
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
                            Button(onClick = { onRead(item, ch) }, shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent), modifier = Modifier.testTag("manga_read")) {
                                Text(if (resume != null) "Continue · ${ch.name}" else "Start reading", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            details?.genres?.takeIf { it.isNotEmpty() }?.let { g -> item("genres") {
                Text(g.joinToString(" · "), color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            } }
            details?.description?.takeIf { it.isNotBlank() }?.let { d -> item("desc") {
                var open by remember { mutableStateOf(false) }
                Text(d, color = TextSecondary, fontSize = 13.sp, maxLines = if (open) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp).clickable { open = !open })
            } }
            error?.let { e -> item("err") { StateMessage(Icons.Default.ErrorOutline, "Couldn't load", e, primary = "Retry" to { attempt++ }) } }
            if (chapters == null && error == null) item("loading") {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = SpotifyGreen) }
            }
            sorted?.let { list ->
                item("ch_head") {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${list.size} chapters", color = TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { newestFirst = !newestFirst }) { Icon(Icons.Default.SwapVert, "Sort", tint = TextSecondary) }
                    }
                }
                items(list, key = { it.url }) { ch ->
                    val read = entry?.readChapters?.contains(ch.url) == true
                    Surface(shape = RoundedCornerShape(12.dp), color = GlassFill, modifier = Modifier.fillMaxWidth().clickable { onRead(item, ch) }) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(ch.name, color = if (read) TextTertiary else TextPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val meta = listOfNotNull(
                                ch.dateUpload.takeIf { it > 0 }?.let { java.text.DateFormat.getDateInstance().format(java.util.Date(it)) },
                                ch.scanlator, if (entry?.lastChapterUrl == ch.url) "Page ${entry.lastPage + 1}" else null,
                            )
                            if (meta.isNotEmpty()) Text(meta.joinToString(" · "), color = TextTertiary, fontSize = 11.sp)
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

private val pageCache = object : android.util.LruCache<String, Bitmap>(64 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount
}

private suspend fun loadPage(provider: MangaProvider, page: MangaPageRef): Bitmap = withContext(Dispatchers.IO) {
    val key = provider.id + "|" + (page.imageUrl ?: page.url) + "|" + page.index
    pageCache.get(key)?.let { return@withContext it }
    val img = provider.image(page)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(img.bytes, 0, img.bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / sample > 1600) sample *= 2
    val bmp = BitmapFactory.decodeByteArray(img.bytes, 0, img.bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: error("This page isn't an image")
    pageCache.put(key, bmp)
    bmp
}

/** Reader: vertical (webtoon) or paged (manga), pinch to zoom, progress saved per chapter. */
@Composable
fun MangaReaderScreen(providerId: String, mangaUrl: String, mangaTitle: String, chapterUrl: String, chapterName: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val provider = remember(providerId) { SourceHub.manga(providerId) }
    var pages by remember { mutableStateOf<List<MangaPageRef>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    var vertical by rememberSaveable { mutableStateOf(context.getSharedPreferences("manga_reader", 0).getBoolean("vertical", true)) }
    var chrome by remember { mutableStateOf(true) }
    var current by remember { mutableStateOf(0) }
    val item = MangaItem(providerId, mangaUrl, mangaTitle, MangaLibrary.get(providerId, mangaUrl)?.cover)
    val chapter = MangaChapter(chapterUrl, chapterName)
    LaunchedEffect(attempt) {
        if (provider == null) { error = "This source isn't installed any more"; return@LaunchedEffect }
        error = null
        runCatching { pages = provider.pages(chapterUrl) }.onFailure { error = OnlineErrors.friendly(it) }
    }
    val start = remember(pages) { MangaLibrary.get(providerId, mangaUrl)?.takeIf { it.lastChapterUrl == chapterUrl }?.lastPage ?: 0 }
    LaunchedEffect(current, pages) {
        val n = pages?.size ?: return@LaunchedEffect
        if (n > 0) MangaLibrary.progress(context, item, chapter, current, finished = current >= n - 1)
    }

    Box(Modifier.fillMaxSize().background(Color.Black).testTag("manga_reader")) {
        val list = pages
        when {
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                StateMessage(Icons.Default.ErrorOutline, "Couldn't open chapter", error, primary = "Retry" to { attempt++ })
            }
            list == null -> CircularProgressIndicator(color = SpotifyGreen, modifier = Modifier.align(Alignment.Center))
            vertical -> {
                val state = rememberLazyListState(initialFirstVisibleItemIndex = start.coerceIn(0, (list.size - 1).coerceAtLeast(0)))
                LaunchedEffect(state) { snapshotFlow { state.firstVisibleItemIndex }.collect { current = it } }
                ZoomBox(onTap = { chrome = !chrome }) {
                    LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(list, key = { i, _ -> i }) { _, p -> PageImage(provider!!, p, fillWidth = true) }
                    }
                }
            }
            else -> {
                val pager = rememberPagerState(initialPage = start.coerceIn(0, (list.size - 1).coerceAtLeast(0))) { list.size }
                LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { current = it } }
                HorizontalPager(pager, modifier = Modifier.fillMaxSize(), reverseLayout = false, beyondViewportPageCount = 1) { i ->
                    ZoomBox(onTap = { chrome = !chrome }) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { PageImage(provider!!, list[i], fillWidth = false) } }
                }
            }
        }
        AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.7f)).statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(mangaTitle, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(chapterName, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { vertical = !vertical; context.getSharedPreferences("manga_reader", 0).edit().putBoolean("vertical", vertical).apply() }, modifier = Modifier.testTag("reader_mode")) {
                    Icon(if (vertical) Icons.Default.ViewCarousel else Icons.Default.SwapVert, if (vertical) "Paged mode" else "Vertical mode", tint = Color.White)
                }
            }
        }
        val total = pages?.size ?: 0
        if (total > 0) AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Text("${current + 1} / $total", color = Color.White, fontSize = 12.sp,
                modifier = Modifier.navigationBarsPadding().padding(12.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        androidx.activity.compose.BackHandler(onBack = onBack)
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
private fun PageImage(provider: MangaProvider, page: MangaPageRef, fillWidth: Boolean) {
    var attempt by remember { mutableStateOf(0) }
    val bmp by produceState<Result<Bitmap>?>(null, page, attempt) { value = runCatching { loadPage(provider, page) } }
    val r = bmp
    when {
        r == null -> Box(Modifier.fillMaxWidth().aspectRatio(0.7f), contentAlignment = Alignment.Center) {
            Text("${page.index + 1}", color = Color.White.copy(alpha = 0.4f), fontSize = 28.sp)
        }
        r.isFailure -> Box(Modifier.fillMaxWidth().aspectRatio(0.7f).clickable { attempt++ }, contentAlignment = Alignment.Center) {
            Text("Page ${page.index + 1} failed · tap to retry", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
        }
        else -> {
            val b = r.getOrThrow()
            Image(b.asImageBitmap(), "Page ${page.index + 1}", contentScale = if (fillWidth) ContentScale.FillWidth else ContentScale.Fit,
                modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
        }
    }
}
