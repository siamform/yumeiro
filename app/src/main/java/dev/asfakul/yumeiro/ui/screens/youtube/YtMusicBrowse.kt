/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.youtube

import androidx.compose.ui.zIndex
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.ui.components.SkeletonBlock
import dev.asfakul.yumeiro.ui.screens.online.PosterImage
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import dev.asfakul.yumeiro.ui.theme.Manrope
import dev.asfakul.yumeiro.ui.theme.OnAccent
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import dev.asfakul.yumeiro.ui.theme.rememberSignatureBrush
import dev.asfakul.yumeiro.youtube.YouTube
import dev.asfakul.yumeiro.youtube.YtHistory
import dev.asfakul.yumeiro.youtube.YtItem
import dev.asfakul.yumeiro.youtube.formatYtDuration
import dev.asfakul.yumeiro.youtube.music.YmBrowse
import dev.asfakul.yumeiro.youtube.music.YmChip
import dev.asfakul.yumeiro.youtube.music.YmItem
import dev.asfakul.yumeiro.youtube.music.YmPage
import dev.asfakul.yumeiro.youtube.music.YmShelf
import dev.asfakul.yumeiro.youtube.music.YtMusicApi
import dev.asfakul.yumeiro.youtube.music.toYt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One loaded YouTube Music page with the shelves/rows appended by "load more". */
private data class YmState(val page: YmPage? = null, val loading: Boolean = true, val error: String? = null, val more: Boolean = false) {
    val canLoadMore get() = page?.continuation != null && !more && !loading
}

private const val CHIP_HOME = "home"
private const val CHIP_EXPLORE = "explore"

/**
 * Music → YouTube Music, the way music.youtube.com looks: mood chips across the top, Quick picks
 * in swipeable columns, endless shelves of albums, mixes and videos, Explore (new releases,
 * charts, moods and genres), full album, playlist and artist pages, and search by type.
 *
 * Signed-out it is YouTube Music's public home; signed-in (Settings → YouTube account, "Always")
 * it is the account's own. Pages open inside this tab with a back arrow, never in a new screen.
 * If YouTube Music can't be reached the tab offers the simpler discovery feeds instead.
 */
@Composable
internal fun YtMusicBrowse(onPlay: (YtItem, List<YtItem>) -> Unit, onBasic: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val stack = remember { mutableStateListOf<YmBrowse>() }
    var chip by rememberSaveable { mutableStateOf(CHIP_HOME) }
    var chips by remember { mutableStateOf<List<YmChip>>(emptyList()) }
    var home by remember { mutableStateOf(YmState()) }

    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf<String?>(null) }
    var filter by rememberSaveable { mutableStateOf(YtMusicApi.Filter.ALL) }
    var results by remember { mutableStateOf(YmState(loading = false)) }
    var suggestions by remember { mutableStateOf<List<dev.asfakul.yumeiro.ui.components.Suggestion>>(emptyList()) }

    LaunchedEffect(Unit) { YtHistory.load(context) }
    val history by YtHistory.items.collectAsState()
    val listened = remember(history) { history.filter { it.music }.take(20) }
    val netOnline by dev.asfakul.yumeiro.network.Connectivity.online.collectAsState()

    fun play(song: YmItem.Song, all: List<YmItem>, art: String? = null) {
        val list = all.filterIsInstance<YmItem.Song>().map { (if (it.thumbnail == null && art != null) it.copy(thumbnail = art) else it).toYt() }
        val first = (if (song.thumbnail == null && art != null) song.copy(thumbnail = art) else song).toYt()
        onPlay(first, list.ifEmpty { listOf(first) })
    }

    fun loadHome(force: Boolean = false) {
        home = YmState(loading = true)
        scope.launch {
            val target = when (chip) {
                CHIP_HOME -> YmBrowse(YtMusicApi.HOME)
                CHIP_EXPLORE -> YmBrowse(YtMusicApi.EXPLORE)
                else -> chips.firstOrNull { it.browse.params == chip }?.browse ?: YmBrowse(YtMusicApi.HOME)
            }
            home = runCatching { YtMusicApi.browse(context, target, force) }.fold(
                { p ->
                    if (target.browseId == YtMusicApi.HOME && p.chips.isNotEmpty() && chip == CHIP_HOME) chips = p.chips
                    YmState(p, loading = false)
                },
                { YmState(loading = false, error = friendly(it)) },
            )
        }
    }
    fun moreHome() {
        val cur = home
        val token = cur.page?.continuation ?: return
        if (!cur.canLoadMore) return
        home = cur.copy(more = true)
        scope.launch {
            home = runCatching { YtMusicApi.more(context, "browse", token) }.fold(
                { p -> cur.copy(page = cur.page.copy(shelves = cur.page.shelves + p.shelves, continuation = p.continuation), more = false) },
                { cur.copy(more = false, page = cur.page.copy(continuation = null)) },
            )
        }
    }
    LaunchedEffect(chip) { loadHome() }
    LaunchedEffect(netOnline) { if (netOnline && home.error != null) loadHome() }

    fun search(q: String, f: YtMusicApi.Filter = filter) {
        val text = q.trim().takeIf { it.isNotEmpty() } ?: return
        query = text; submitted = text; filter = f; suggestions = emptyList(); focus.clearFocus()
        dev.asfakul.yumeiro.ui.components.RecentSearches.add(context, "ytmusic", text)
        results = YmState(loading = true)
        scope.launch {
            results = runCatching { YtMusicApi.search(context, text, f) }.fold(
                { YmState(it, loading = false) }, { YmState(loading = false, error = friendly(it)) },
            )
        }
    }
    fun moreResults() {
        val cur = results
        val token = cur.page?.continuation ?: return
        if (!cur.canLoadMore) return
        results = cur.copy(more = true)
        scope.launch {
            results = runCatching { YtMusicApi.more(context, "search", token) }.fold(
                { p -> cur.copy(page = cur.page.copy(shelves = mergeRows(cur.page.shelves, p.shelves), continuation = p.continuation), more = false) },
                { cur.copy(more = false, page = cur.page.copy(continuation = null)) },
            )
        }
    }
    LaunchedEffect(query) {
        if (query.isBlank() || query == submitted) { suggestions = emptyList(); return@LaunchedEffect }
        val local = dev.asfakul.yumeiro.ui.components.SearchSuggest.rank(
            query, dev.asfakul.yumeiro.ui.components.RecentSearches.asSuggestions(context, "ytmusic"), limit = 3,
        )
        suggestions = local
        delay(200)
        val remote = runCatching { YouTube.suggestions(context, query) }.getOrDefault(emptyList())
            .map { dev.asfakul.yumeiro.ui.components.Suggestion(it, dev.asfakul.yumeiro.ui.components.Suggestion.Kind.QUERY) }
        suggestions = (local + remote).distinctBy { dev.asfakul.yumeiro.ui.components.SearchSuggest.fold(it.text) }.take(8)
    }

    val open: (YmBrowse) -> Unit = { stack.add(it) }
    BackHandler(enabled = stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }
    BackHandler(enabled = stack.isEmpty() && submitted != null) { submitted = null; query = ""; results = YmState(loading = false) }

    AnimatedContent(
        targetState = stack.lastOrNull(),
        transitionSpec = { fadeIn(dev.asfakul.yumeiro.ui.components.YuMotion.enter(false, 220)) togetherWith fadeOut(dev.asfakul.yumeiro.ui.components.YuMotion.exit(false, 160)) },
        label = "ym_page",
        modifier = modifier.fillMaxSize(),
    ) { top ->
        if (top != null) {
            YmDetail(top, onBack = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }, onOpen = open, onPlay = ::play)
            return@AnimatedContent
        }
        Box(Modifier.fillMaxSize()) {

            val asked by dev.asfakul.yumeiro.ui.components.TabSearch.open.collectAsState()
            val key = dev.asfakul.yumeiro.ui.components.TabSearch.YT_MUSIC
            val barOpen = asked == key || query.isNotEmpty() || submitted != null
            Column(Modifier.fillMaxSize().padding(top = if (submitted != null) YT_SEARCH_BAR_H else 0.dp)) {
            val sub = submitted
            if (sub != null) {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("ytmusic_search_filters")) {
                    itemsIndexed(YtMusicApi.Filter.entries, key = { _, f -> f.name }) { _, f -> YtChip(f.label, f == filter) { if (f != filter) search(sub, f) } }
                }
                val state = dev.asfakul.yumeiro.ui.components.ScrollMemory.list("ytm_results:" + sub + ":" + filter.name)
                YtEndlessTrigger(state, results.canLoadMore) { moreResults() }
                LazyColumn(Modifier.fillMaxSize().testTag("ytmusic_results"), state = state, contentPadding = PaddingValues(bottom = 160.dp)) {
                    pageBody(results, home = false, onOpen = open, onPlay = ::play, onRetry = { search(sub) })
                    val p = results.page
                    if (p != null && p.shelves.isEmpty() && !results.loading) item("none") { YtProblem("No results for “$sub”.", null) }
                }
                return@Column
            }

            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("ytmusic_chips")) {
                item("c_home") { YtChip("Home", chip == CHIP_HOME, modifier = Modifier.testTag("ytmusic_chip_home")) { chip = CHIP_HOME } }
                item("c_explore") { YtChip("Explore", chip == CHIP_EXPLORE, modifier = Modifier.testTag("ytmusic_chip_explore")) { chip = CHIP_EXPLORE } }
                itemsIndexed(chips, key = { i, c -> "c$i${c.title}" }) { _, c ->
                    YtChip(c.title, chip == c.browse.params) { chip = c.browse.params ?: CHIP_HOME }
                }
            }
            val state = dev.asfakul.yumeiro.ui.components.ScrollMemory.list("ytm_home:" + chip)
            YtEndlessTrigger(state, home.canLoadMore) { moreHome() }
            LazyColumn(Modifier.fillMaxSize().testTag("youtube_music_list"), state = state, contentPadding = PaddingValues(bottom = 160.dp)) {
                if (chip == CHIP_EXPLORE) item("explore_tiles") { ExploreTiles(open) }
                if (chip == CHIP_HOME && listened.isNotEmpty()) item("listen_again") {
                    YmShelfHeader("Listen again", "From your history", Icons.Default.History, null)
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(listened, key = { i, it -> "la$i${it.id}" }) { _, it ->
                            YmCover(it.title, it.channel.removeSuffix(" - Topic"), it.thumbnail, 132.dp, round = false) { onPlay(it, listened) }
                        }
                    }
                }
                pageBody(home, home = true, onOpen = open, onPlay = ::play, onRetry = { loadHome(true) })
                if (home.error != null) item("basic") {
                    TextButton(onClick = onBasic, modifier = Modifier.padding(horizontal = 12.dp).testTag("ytmusic_basic")) {
                        Text("Use simple discovery instead", color = SpotifyGreen)
                    }
                }
            }
                    }
            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().zIndex(2f)) {
            androidx.compose.animation.AnimatedVisibility(
                barOpen,
                enter = androidx.compose.animation.slideInVertically(dev.asfakul.yumeiro.ui.components.YuMotion.enter(dev.asfakul.yumeiro.ui.components.reducedMotion(), 240)) { -it / 2 } + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.slideOutVertically(dev.asfakul.yumeiro.ui.components.YuMotion.exit(dev.asfakul.yumeiro.ui.components.reducedMotion(), 180)) { -it / 2 } + androidx.compose.animation.fadeOut(),
            ) {
                YtSearchBar(
                    query, "Songs, albums, artists, podcasts", music = true, autoFocus = asked == key, floating = true,
                    onChange = { query = it; if (it.isBlank()) { submitted = null; results = YmState(loading = false) } },
                    onSearch = { search(query) },
                    onClear = { query = ""; submitted = null; results = YmState(loading = false); dev.asfakul.yumeiro.ui.components.TabSearch.close(key) },
                )
            }
            dev.asfakul.yumeiro.ui.components.SuggestionList(
                query = query, suggestions = suggestions, onPick = { search(it.text) }, onFill = { query = it.text }, tag = "ytmusic_suggestions",
            )
            }
        }
    }
}

/** Continuation rows (search, playlists) arrive as an untitled list: add them to the last list. */
private fun mergeRows(old: List<YmShelf>, add: List<YmShelf>): List<YmShelf> {
    val last = old.lastOrNull()
    val first = add.firstOrNull()
    if (last != null && first != null && first.title.isEmpty() && last.style == YmShelf.Style.LIST) {
        return old.dropLast(1) + last.copy(items = (last.items + first.items).distinctBy { it.key }) + add.drop(1)
    }
    return old + add
}

private fun LazyListScope.pageBody(
    state: YmState, home: Boolean, onOpen: (YmBrowse) -> Unit, onPlay: (YmItem.Song, List<YmItem>, String?) -> Unit, onRetry: () -> Unit,
    art: String? = null,
) {
    val page = state.page
    if (page == null && state.loading) {
        item("sk_head") { SkeletonBlock(null, 22.dp, Modifier.padding(start = 18.dp, end = 120.dp, top = 18.dp, bottom = 10.dp)) }
        itemsIndexed(List(5) { it }, key = { i, _ -> "sk$i" }) { _, _ -> SongRowSkeleton() }
        item("sk_row") { Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { repeat(3) { SkeletonBlock(140.dp, 140.dp, shape = RoundedCornerShape(14.dp)) } } }
        return
    }
    state.error?.let { msg -> item("err") { YtProblem(msg, onRetry) }; return }
    page ?: return
    page.shelves.forEachIndexed { i, shelf ->
        val all = shelf.items
        when {
            shelf.style == YmShelf.Style.GRID -> item("g$i") { MoodGrid(shelf, home, onOpen) }
            shelf.style == YmShelf.Style.LIST && home -> item("q$i") { QuickPicks(shelf, onOpen) { s -> onPlay(s, all, art) } }
            shelf.style == YmShelf.Style.LIST -> {
                if (shelf.title.isNotBlank()) item("h$i") { YmShelfHeader(shelf.title, shelf.strapline, null, shelf.more?.let { m -> { onOpen(m) } }) }
                itemsIndexed(all, key = { j, it -> "r$i-$j-${it.key}" }) { j, it ->
                    YmRow(it, index = if (art != null) j + 1 else null, art = art, onOpen = onOpen) { s -> onPlay(s, all, art) }
                }
            }
            else -> item("c$i") { Carousel(shelf, onOpen) { s -> onPlay(s, all, art) } }
        }
    }
    if (state.more) item("more") { YtLoadingMore() }
}

@Composable
private fun YmShelfHeader(title: String, strapline: String?, icon: androidx.compose.ui.graphics.vector.ImageVector?, onMore: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, tint = SpotifyGreen, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Column(Modifier.weight(1f)) {
            if (!strapline.isNullOrBlank()) Text(strapline.uppercase(), color = TextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(title, color = TextPrimary, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onMore != null) Row(
            Modifier.clip(CircleShape).background(GlassFill).clickable(onClick = onMore).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { Text("More", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
    }
}

/** Quick picks: columns of four song rows that snap one column at a time, like YouTube Music. */
@Composable
private fun QuickPicks(shelf: YmShelf, onOpen: (YmBrowse) -> Unit, onSong: (YmItem.Song) -> Unit) {
    Column(Modifier.testTag("ytmusic_quick_picks")) {
        YmShelfHeader(shelf.title.ifBlank { "Quick picks" }, shelf.strapline, null, shelf.more?.let { m -> { onOpen(m) } })
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val colW = maxWidth * 0.86f
            val st = rememberLazyListState()
            LazyRow(state = st, flingBehavior = rememberSnapFlingBehavior(st), contentPadding = PaddingValues(end = 16.dp)) {
                itemsIndexed(shelf.items.chunked(4), key = { i, _ -> "qc$i" }) { _, col ->
                    Column(Modifier.width(colW)) { col.forEach { YmRow(it, compact = true, onOpen = onOpen, onSong = onSong) } }
                }
            }
        }
    }
}

@Composable
private fun Carousel(shelf: YmShelf, onOpen: (YmBrowse) -> Unit, onSong: (YmItem.Song) -> Unit) {
    Column {
        YmShelfHeader(shelf.title, shelf.strapline, null, shelf.more?.let { m -> { onOpen(m) } })
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(shelf.items, key = { i, it -> "$i${it.key}" }) { _, it ->
                when (it) {
                    is YmItem.Song -> if (it.video) YmVideoCover(it) { onSong(it) }
                    else YmCover(it.title, it.artistLine, it.thumbnail, 144.dp, round = false) { onSong(it) }
                    is YmItem.Collection -> YmCover(
                        it.title, it.subtitle, it.thumbnail, if (it.kind == YmItem.Collection.Kind.ARTIST) 128.dp else 144.dp,
                        round = it.kind == YmItem.Collection.Kind.ARTIST,
                    ) { onOpen(it.browse) }
                    is YmItem.Mood -> MoodButton(it, Modifier.width(160.dp)) { onOpen(it.browse) }
                }
            }
        }
    }
}

@Composable
private fun YmCover(title: String, subtitle: String, art: String?, size: Dp, round: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(size).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).testTag("ytmusic_cover"), horizontalAlignment = if (round) Alignment.CenterHorizontally else Alignment.Start) {
        Box(Modifier.size(size).clip(if (round) CircleShape else RoundedCornerShape(12.dp))) {
            PosterImage(art, emptyMap(), Modifier.fillMaxSize(), if (round) Icons.Default.Person else Icons.Default.Album)
        }
        Spacer(Modifier.height(8.dp))
        Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (subtitle.isNotBlank()) Text(subtitle, color = TextTertiary, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun YmVideoCover(song: YmItem.Song, onClick: () -> Unit) {
    Column(Modifier.width(236.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).testTag("ytmusic_video")) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))) {
            PosterImage(song.thumbnail, emptyMap(), Modifier.fillMaxSize(), Icons.Default.MusicNote)
        }
        Spacer(Modifier.height(8.dp))
        Text(song.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(listOfNotNull(song.artistLine.takeIf { it.isNotBlank() }, song.note).joinToString(" · "), color = TextTertiary, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** One row: a song (tap plays) or a collection (tap opens). */
@Composable
private fun YmRow(item: YmItem, compact: Boolean = false, index: Int? = null, art: String? = null, onOpen: (YmBrowse) -> Unit, onSong: (YmItem.Song) -> Unit) {
    val pad = if (compact) 5.dp else 7.dp
    when (item) {
        is YmItem.Song -> Row(
            Modifier.fillMaxWidth().clickable { onSong(item) }.padding(horizontal = 16.dp, vertical = pad).testTag("youtube_song"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val thumb = item.thumbnail ?: art
            if (index != null && item.thumbnail == null) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Text("$index", color = TextTertiary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            } else Box(Modifier.size(if (compact) 50.dp else 52.dp).clip(RoundedCornerShape(8.dp))) {
                PosterImage(thumb, emptyMap(), Modifier.fillMaxSize(), Icons.Default.MusicNote)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val line = listOfNotNull(
                    if (item.video) "Video" else null,
                    item.artistLine.takeIf { it.isNotBlank() },
                    item.album?.name?.takeIf { !compact && it.isNotBlank() },
                    item.note?.takeIf { compact.not() && item.durationSec <= 0 },
                ).joinToString(" • ")
                if (line.isNotBlank()) Text(line, color = TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (item.durationSec > 0 && !compact) Text(formatYtDuration(item.durationSec), color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
            SongDownloadButton { dev.asfakul.yumeiro.online.download.SongDownloads.ask((if (item.thumbnail == null && art != null) item.copy(thumbnail = art) else item).toYt()) }
        }
        is YmItem.Collection -> Row(
            Modifier.fillMaxWidth().clickable { onOpen(item.browse) }.padding(horizontal = 16.dp, vertical = pad).testTag("ytmusic_collection_row"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val round = item.kind == YmItem.Collection.Kind.ARTIST
            Box(Modifier.size(52.dp).clip(if (round) CircleShape else RoundedCornerShape(8.dp))) {
                PosterImage(item.thumbnail, emptyMap(), Modifier.fillMaxSize(), if (round) Icons.Default.Person else Icons.Default.Album)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (item.subtitle.isNotBlank()) Text(item.subtitle, color = TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ChevronRight, null, tint = TextTertiary)
        }
        is YmItem.Mood -> MoodButton(item, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) { onOpen(item.browse) }
    }
}

@Composable
private fun MoodButton(mood: YmItem.Mood, modifier: Modifier, onClick: () -> Unit) {
    val stripe = mood.color?.let { Color(it.toInt()) } ?: SpotifyGreen
    Row(
        modifier.height(52.dp).clip(RoundedCornerShape(10.dp)).background(GlassFill).clickable(onClick = onClick).testTag("ytmusic_mood"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(6.dp).fillMaxSize().background(stripe))
        Text(mood.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 12.dp))
    }
}

/** Moods & genres: two rows that scroll sideways on Home/Explore, a full grid on its own page. */
@Composable
private fun MoodGrid(shelf: YmShelf, sideways: Boolean, onOpen: (YmBrowse) -> Unit) {
    val moods = shelf.items.filterIsInstance<YmItem.Mood>()
    Column {
        if (shelf.title.isNotBlank()) YmShelfHeader(shelf.title, null, null, null)
        if (sideways) LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(moods.chunked(2), key = { i, _ -> "mc$i" }) { _, pair ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { pair.forEach { MoodButton(it, Modifier.width(168.dp)) { onOpen(it.browse) } } }
            }
        } else Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            moods.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { MoodButton(it, Modifier.weight(1f)) { onOpen(it.browse) } }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ExploreTiles(onOpen: (YmBrowse) -> Unit) {
    val tiles = listOf(
        Triple("New releases", Icons.Default.Album, YtMusicApi.NEW_RELEASES),
        Triple("Charts", Icons.AutoMirrored.Filled.QueueMusic, YtMusicApi.CHARTS),
        Triple("Moods & genres", Icons.Default.MusicNote, YtMusicApi.MOODS),
    )
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.forEach { (label, icon, id) ->
            Row(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(12.dp)).background(GlassFill).clickable { onOpen(YmBrowse(id)) }
                    .padding(horizontal = 14.dp).testTag("ytmusic_explore_$id"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = SpotifyGreen, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(label, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight, null, tint = TextTertiary)
            }
        }
    }
}

/** An album, playlist, artist, chart, mood or "More" page, inside the tab with a back arrow. */
@Composable
private fun YmDetail(target: YmBrowse, onBack: () -> Unit, onOpen: (YmBrowse) -> Unit, onPlay: (YmItem.Song, List<YmItem>, String?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember(target) { mutableStateOf(YmState()) }
    fun load(force: Boolean = false) {
        state = YmState()
        scope.launch {
            state = runCatching { YtMusicApi.browse(context, target, force) }.fold({ YmState(it, loading = false) }, { YmState(loading = false, error = friendly(it)) })
        }
    }
    fun more() {
        val cur = state
        val token = cur.page?.continuation ?: return
        if (!cur.canLoadMore) return
        state = cur.copy(more = true)
        scope.launch {
            state = runCatching { YtMusicApi.more(context, "browse", token) }.fold(
                { p -> cur.copy(page = cur.page.copy(shelves = mergeRows(cur.page.shelves, p.shelves), continuation = p.continuation), more = false) },
                { cur.copy(more = false, page = cur.page.copy(continuation = null)) },
            )
        }
    }
    LaunchedEffect(target) { load() }
    val header = state.page?.header
    val songs = state.page?.shelves?.flatMap { it.items }?.filterIsInstance<YmItem.Song>().orEmpty()
    val art = header?.thumbnail?.takeIf { header.artist.not() }
    val listState = dev.asfakul.yumeiro.ui.components.ScrollMemory.list("ytm_detail:" + target.browseId + ":" + target.params)
    YtEndlessTrigger(listState, state.canLoadMore) { more() }
    LazyColumn(Modifier.fillMaxSize().testTag("ytmusic_detail"), state = listState, contentPadding = PaddingValues(bottom = 160.dp)) {
        item("bar") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("ytmusic_back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary) }
                if (header == null) Text(state.page?.shelves?.firstOrNull()?.title.orEmpty(), color = TextPrimary, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (header != null) item("header") {
            DetailHeader(header, canPlay = songs.isNotEmpty(),
                onPlay = { songs.firstOrNull()?.let { onPlay(it, songs, art) } },
                onShuffle = { val s = songs.shuffled(); s.firstOrNull()?.let { onPlay(it, s, art) } })
        }
        pageBody(state, home = false, onOpen = onOpen, onPlay = onPlay, onRetry = { load(true) }, art = art)
    }
}

@Composable
private fun DetailHeader(h: dev.asfakul.yumeiro.youtube.music.YmHeader, canPlay: Boolean, onPlay: () -> Unit, onShuffle: () -> Unit) {
    val palette = LocalMtPalette.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (h.artist) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(20.dp))) {
                PosterImage(h.thumbnail, emptyMap(), Modifier.fillMaxSize(), Icons.Default.Person)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)))))
                Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                    Text(h.title, color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    h.subscribers?.let { Text(it, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp) }
                }
            }
        } else {
            Box(Modifier.size(208.dp).clip(RoundedCornerShape(16.dp))) { PosterImage(h.thumbnail, emptyMap(), Modifier.fillMaxSize(), Icons.Default.Album) }
            Spacer(Modifier.height(14.dp))
            Text(h.title, color = TextPrimary, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            if (h.subtitle.isNotBlank()) Text(h.subtitle, color = TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (h.secondSubtitle.isNotBlank()) Text(h.secondSubtitle, color = TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (canPlay) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val brush = rememberSignatureBrush()
                Row(
                    Modifier.height(44.dp).clip(CircleShape).background(brush).clickable(onClick = onPlay).padding(horizontal = 22.dp).testTag("ytmusic_play_all"),
                    verticalAlignment = Alignment.CenterVertically,
                ) { Icon(Icons.Default.PlayArrow, null, tint = OnAccent); Spacer(Modifier.width(6.dp)); Text("Play", color = OnAccent, fontWeight = FontWeight.Bold) }
                Row(
                    Modifier.height(44.dp).clip(CircleShape).background(GlassFill).clickable(onClick = onShuffle).padding(horizontal = 20.dp).testTag("ytmusic_shuffle_all"),
                    verticalAlignment = Alignment.CenterVertically,
                ) { Icon(Icons.Default.Shuffle, null, tint = palette.textPrimary); Spacer(Modifier.width(6.dp)); Text("Shuffle", color = TextPrimary, fontWeight = FontWeight.SemiBold) }
            }
        }
        if (h.description.isNotBlank()) {
            var open by remember { mutableStateOf(false) }
            Text(
                h.description, color = TextSecondary, fontSize = 12.5.sp, maxLines = if (open) 40 else 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 14.dp).clickable { open = !open },
            )
        }
    }
}


/** Trailing "download this song" action on a song row (opens the shared audio download sheet). */
@Composable
internal fun SongDownloadButton(onClick: () -> Unit) {
    androidx.compose.material3.IconButton(onClick = onClick, modifier = Modifier.size(40.dp).testTag("song_download")) {
        Icon(Icons.Default.Download, contentDescription = "Download song", tint = TextTertiary, modifier = Modifier.size(20.dp))
    }
}
