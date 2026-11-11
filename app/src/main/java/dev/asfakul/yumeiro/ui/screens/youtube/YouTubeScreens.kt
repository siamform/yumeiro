/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.youtube

import dev.asfakul.yumeiro.youtube.YtAccount

import dev.asfakul.yumeiro.ui.components.Suggestion.Kind as SuggestionKind
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.ui.components.SkeletonBlock
import dev.asfakul.yumeiro.ui.screens.online.PosterImage
import dev.asfakul.yumeiro.ui.theme.AccentSecondary
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.OnAccent
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import dev.asfakul.yumeiro.ui.theme.Unbounded
import dev.asfakul.yumeiro.ui.theme.rememberGlassBorder
import dev.asfakul.yumeiro.ui.theme.rememberSignatureBrush
import dev.asfakul.yumeiro.youtube.YouTube
import dev.asfakul.yumeiro.youtube.YtHistory
import dev.asfakul.yumeiro.youtube.YtItem
import dev.asfakul.yumeiro.youtube.YtDiscovery
import dev.asfakul.yumeiro.youtube.YtFeed
import dev.asfakul.yumeiro.youtube.YtTopics
import org.schabi.newpipe.extractor.Page
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Shuffle
import dev.asfakul.yumeiro.youtube.formatYtDuration
import dev.asfakul.yumeiro.youtube.formatYtViews
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

private fun friendly(t: Throwable): String = when {
    YtAccount.accountCanHelp(t) -> YtAccount.blockedHint()
    t.javaClass.simpleName.contains("ReCaptcha") -> "YouTube is rate-limiting this network. Try again in a few minutes or switch networks."
    !dev.asfakul.yumeiro.network.Connectivity.online.value -> "You're offline. Your downloads and songs on this phone still play."
    t is java.io.IOException -> "Couldn't reach YouTube. Check your connection and try again."
    else -> readable(dev.asfakul.yumeiro.online.OnlineErrors.friendly(t, "YouTube didn't answer. Try again in a moment."))
}

/** Internal library or resource messages are never shown to people as-is. */
private fun readable(m: String): String {
    val internal = listOf("resource", "Exception", "null", "PublicSuffix", "java.", "kotlin.", "Unable to load")
    return if (m.isBlank() || internal.any { m.contains(it, ignoreCase = false) }) "YouTube didn't answer. Try again in a moment." else m
}

/** Videos → YouTube: a login-free home planned from what you watch, pin and search. */
@Composable
fun YouTubeVideoScreen(onPlay: (YtItem) -> Unit, modifier: Modifier = Modifier) {
    YtBrowse(
        music = false,
        searchFilter = YouTube.Filter.VIDEOS,
        placeholder = "Search YouTube",
        onPlay = { item, _ -> onPlay(item) },
        modifier = modifier.testTag("youtube_video_home"),
    )
}

/** Music → YouTube Music: songs planned from what you play, your topics and today's mix. */
@Composable
fun YouTubeMusicScreen(onPlay: (YtItem, List<YtItem>) -> Unit, modifier: Modifier = Modifier) {
    YtBrowse(
        music = true,
        searchFilter = YouTube.Filter.SONGS,
        placeholder = "Songs, artists, anime OSTs…",
        onPlay = onPlay,
        modifier = modifier.testTag("youtube_music_home"),
    )
}

/** A paged list: items so far, the token for more, and whether a page is loading. */
private data class Paged(val items: List<YtItem> = emptyList(), val next: Page? = null, val loading: Boolean = false, val error: String? = null) {
    val canLoadMore get() = next != null && !loading
}

private const val FOR_YOU = "for_you"

/**
 * Dynamic discovery (replaces the fixed category list):
 *  - "For you" mixes shelves planned by [YtDiscovery.plan] from your plays, favourite channels, your
 *    own topics, recent searches, a live chart and a daily-rotating explore mix;
 *  - every chip opens that feed as an endless list (searches keep paging);
 *  - any search or feed can be pinned as a topic, topics can be removed (long-press or the topic
 *    editor), and "Shuffle" re-rolls the explore mix;
 *  - search results page endlessly and suggest related searches to jump to.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun YtBrowse(
    music: Boolean,
    searchFilter: YouTube.Filter,
    placeholder: String,
    onPlay: (YtItem, List<YtItem>) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf<String?>(null) }
    var suggestions by remember { mutableStateOf<List<dev.asfakul.yumeiro.ui.components.Suggestion>>(emptyList()) }
    val recentScope = if (music) "ytmusic" else "youtube"
    var results by remember { mutableStateOf<Paged?>(null) }
    var relatedSearches by remember { mutableStateOf<List<String>>(emptyList()) }
    var chip by rememberSaveable { mutableStateOf(FOR_YOU) }
    var topics by remember { mutableStateOf(YtTopics.load(context, music)) }
    var shuffle by remember { mutableStateOf(YtTopics.shuffleCount(context)) }
    var editingTopics by remember { mutableStateOf(false) }
    var addingTopic by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { YtHistory.load(context) }
    val history by YtHistory.items.collectAsState()
    // Offline, the page banner already explains what still works; empty skeleton shelves would only look broken.
    val netOnline by dev.asfakul.yumeiro.network.Connectivity.online.collectAsState()
    val mine = remember(history) { history.filter { it.music == music } }
    // Re-plan only when the inputs change; history is reduced to what the planner reads so a new
    // play doesn't reshuffle the page under the user's finger.
    val historySeed = remember(mine) { mine.take(24) }
    val searches = remember(submitted) { dev.asfakul.yumeiro.ui.components.RecentSearches.load(context, recentScope) }
    val feeds = remember(music, historySeed, topics, searches, shuffle) {
        YtDiscovery.plan(YtDiscovery.Input(music, historySeed, topics, searches, java.time.LocalDate.now().toEpochDay() + shuffle * 101))
    }
    val shelves = remember { mutableStateMapOf<String, Load<List<YtItem>>>() }
    val pagedFeeds = remember { mutableStateMapOf<String, Paged>() }

    fun loadShelves(force: Boolean) {
        val gate = Semaphore(2)
        feeds.forEach { f ->
            if (!force && shelves[f.id] is Load.Ready) return@forEach
            shelves[f.id] = Load.Loading
            scope.launch {
                shelves[f.id] = gate.withPermit {
                    runCatching { YouTube.feed(context, f, force = force) }.fold(
                        { if (it.items.isEmpty()) Load.Failed("Nothing here right now.") else Load.Ready(it.items) },
                        { Load.Failed(friendly(it)) },
                    )
                }
            }
        }
    }
    LaunchedEffect(feeds) { loadShelves(false) }
    LaunchedEffect(netOnline) { if (netOnline && shelves.values.any { it is Load.Failed }) loadShelves(false) }

    fun loadFeedPage(f: YtFeed, reset: Boolean = false) {
        val cur = if (reset) Paged() else pagedFeeds[f.id] ?: Paged()
        if (cur.loading || (!reset && cur.items.isNotEmpty() && cur.next == null)) return
        pagedFeeds[f.id] = cur.copy(loading = true, error = null)
        scope.launch {
            pagedFeeds[f.id] = runCatching { YouTube.feed(context, f, cur.next) }.fold(
                { p -> Paged((cur.items + p.items).distinctBy { it.id }, p.next) },
                { cur.copy(loading = false, error = friendly(it)) },
            )
        }
    }

    LaunchedEffect(query) {
        if (query.isBlank() || query == submitted) { suggestions = emptyList(); return@LaunchedEffect }
        // Instant local typeahead first (recent searches + what you played), then YouTube's own suggestions.
        val local = dev.asfakul.yumeiro.ui.components.SearchSuggest.rank(
            query,
            dev.asfakul.yumeiro.ui.components.RecentSearches.asSuggestions(context, recentScope) +
                topics.map { dev.asfakul.yumeiro.ui.components.Suggestion(it, SuggestionKind.RECENT, "Your topic") } +
                mine.take(80).map { dev.asfakul.yumeiro.ui.components.Suggestion(it.title, SuggestionKind.YOUTUBE, if (music) "Listened" else "Watched") },
            limit = 3,
        )
        suggestions = local
        delay(200)
        val remote = runCatching { YouTube.suggestions(context, query) }.getOrDefault(emptyList())
            .map { dev.asfakul.yumeiro.ui.components.Suggestion(it, SuggestionKind.QUERY) }
        suggestions = (local + remote).distinctBy { dev.asfakul.yumeiro.ui.components.SearchSuggest.fold(it.text) }.take(8)
    }
    fun searchMore(text: String) {
        val cur = results ?: Paged()
        if (cur.loading) return
        results = cur.copy(loading = true, error = null)
        scope.launch {
            results = runCatching { YouTube.search(context, text, searchFilter, cur.next) }.fold(
                { p -> Paged((cur.items + p.items).distinctBy { it.id }, p.next) },
                { cur.copy(loading = false, error = friendly(it)) },
            )
        }
    }
    fun search(q: String) {
        val text = q.trim().takeIf { it.isNotEmpty() } ?: return
        query = text; submitted = text; suggestions = emptyList(); focus.clearFocus()
        dev.asfakul.yumeiro.ui.components.RecentSearches.add(context, recentScope, text)
        results = Paged()
        relatedSearches = emptyList()
        searchMore(text)
        scope.launch {
            val s = runCatching { YouTube.suggestions(context, text) }.getOrDefault(emptyList())
            relatedSearches = YtDiscovery.relatedChips(text, s, topics)
        }
    }
    fun pin(topic: String) {
        val t = YtDiscovery.cleanTopic(topic) ?: return
        topics = YtDiscovery.withTopic(topics, t)
        YtTopics.save(context, music, topics)
        dev.asfakul.yumeiro.ui.components.AppMessages.success(context, "“$t” added to your topics")
    }
    fun unpin(topic: String) {
        topics = YtDiscovery.withoutTopic(topics, topic)
        YtTopics.save(context, music, topics)
        if (chip.endsWith(YtDiscovery.fold(topic))) chip = FOR_YOU
        dev.asfakul.yumeiro.ui.components.AppMessages.show(context, "“$topic” removed from your topics", actionLabel = "Undo", action = {
            topics = YtDiscovery.withTopic(topics, topic); YtTopics.save(context, music, topics)
        })
    }
    fun isTopic(text: String) = topics.any { YtDiscovery.fold(it) == YtDiscovery.fold(text) }

    Column(modifier.fillMaxSize()) {
        val searchKey = if (music) dev.asfakul.yumeiro.ui.components.TabSearch.YT_MUSIC else dev.asfakul.yumeiro.ui.components.TabSearch.YT_VIDEO
        val asked by dev.asfakul.yumeiro.ui.components.TabSearch.open.collectAsState()
        val barOpen = asked == searchKey || query.isNotEmpty() || submitted != null
        androidx.compose.animation.AnimatedVisibility(
            visible = barOpen,
            enter = androidx.compose.animation.expandVertically(dev.asfakul.yumeiro.ui.components.YuMotion.enter(dev.asfakul.yumeiro.ui.components.reducedMotion(), 260)) + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically(dev.asfakul.yumeiro.ui.components.YuMotion.exit(dev.asfakul.yumeiro.ui.components.reducedMotion(), 200)) + androidx.compose.animation.fadeOut(),
        ) {
            YtSearchBar(
                query, placeholder, music, autoFocus = asked == searchKey,
                onChange = { query = it; if (it.isBlank()) { submitted = null; results = null } }, onSearch = { search(query) },
                onClear = { query = ""; submitted = null; results = null; dev.asfakul.yumeiro.ui.components.TabSearch.close(searchKey) },
            )
        }
        dev.asfakul.yumeiro.ui.components.SuggestionList(
            query = query, suggestions = suggestions,
            onPick = { search(it.text) }, onFill = { query = it.text },
            tag = "youtube_suggestions",
        )
        val res = results
        val sub = submitted
        if (sub != null && res != null) {
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            YtEndlessTrigger(listState, res.canLoadMore) { searchMore(sub) }
            LazyColumn(Modifier.fillMaxSize().testTag("youtube_results"), state = listState, contentPadding = PaddingValues(bottom = 160.dp)) {
                item("res_head") {
                    YtFeedHeader(
                        title = sub, overline = if (music) "Songs · 検索" else "Results · 検索",
                        pinned = isTopic(sub), onPin = { pin(sub) }, onUnpin = { unpin(sub) },
                    )
                }
                if (relatedSearches.isNotEmpty()) item("res_related") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("youtube_related_searches")) {
                        itemsIndexed(relatedSearches, key = { i, s -> "rs${i}_$s" }) { _, s -> YtChip(s, false, leading = Icons.Default.Search) { search(s) } }
                    }
                }
                if (res.items.isEmpty() && res.loading) itemsIndexed(List(6) { it }, key = { i, _ -> "sk$i" }) { _, _ -> if (music) SongRowSkeleton() else SkeletonBlock(null, 220.dp, Modifier.padding(16.dp), RoundedCornerShape(22.dp)) }
                if (res.items.isEmpty() && !res.loading && res.error == null) item("none") { YtProblem("No results for “$sub”.", null) }
                itemsIndexed(res.items, key = { i, it -> "r${i}_${it.id}" }) { _, item ->
                    if (music) SongRow(item) { onPlay(item, res.items) } else YtVideoCard(item, wide = true) { onPlay(item, res.items) }
                }
                if (res.error != null) item("err") { YtProblem(res.error) { searchMore(sub) } }
                if (res.items.isNotEmpty() && res.loading) item("more") { YtLoadingMore() }
            }
            return@Column
        }

        // Chip row: For you · every planned feed · + Topic. Long-press a topic chip to remove it.
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.testTag("youtube_chips"),
        ) {
            item("c_for_you") { YtChip("For you", chip == FOR_YOU, modifier = Modifier.testTag("youtube_chip_for_you")) { chip = FOR_YOU } }
            itemsIndexed(feeds, key = { _, f -> "c_${f.id}" }) { _, f ->
                val topic = f.overline == "Your topic"
                YtChip(
                    f.title.take(28), chip == f.id,
                    onLongClick = if (topic) ({ unpin(f.title) }) else null,
                ) { chip = f.id; if (pagedFeeds[f.id] == null) loadFeedPage(f) }
            }
            item("c_add") { YtChip("Topic", false, leading = Icons.Default.Add, modifier = Modifier.testTag("youtube_add_topic")) { addingTopic = true } }
        }

        val selected = feeds.firstOrNull { it.id == chip }
        if (chip != FOR_YOU && selected != null) {
            LaunchedEffect(selected.id) { if (pagedFeeds[selected.id] == null) loadFeedPage(selected) }
            val page = pagedFeeds[selected.id] ?: Paged(loading = true)
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            YtEndlessTrigger(listState, selected.pages && page.canLoadMore) { loadFeedPage(selected) }
            LazyColumn(Modifier.fillMaxSize().testTag("youtube_feed_list"), state = listState, contentPadding = PaddingValues(bottom = 160.dp)) {
                item("feed_head") {
                    YtFeedHeader(
                        title = selected.title, overline = selected.overline,
                        pinned = isTopic(selected.title), onPin = { pin(selected.title) }, onUnpin = { unpin(selected.title) },
                        canPin = selected.kind == YtFeed.Kind.QUERY,
                    )
                }
                if (page.items.isEmpty() && page.loading) itemsIndexed(List(6) { it }, key = { i, _ -> "fsk$i" }) { _, _ -> if (music) SongRowSkeleton() else SkeletonBlock(null, 220.dp, Modifier.padding(16.dp), RoundedCornerShape(22.dp)) }
                itemsIndexed(page.items, key = { i, it -> "fi${i}_${it.id}" }) { _, item ->
                    if (music) SongRow(item) { onPlay(item, page.items) } else YtVideoCard(item, wide = true) { onPlay(item, page.items) }
                }
                if (page.error != null) item("ferr") { YtProblem(page.error) { loadFeedPage(selected, reset = page.items.isEmpty()) } }
                if (page.items.isNotEmpty() && page.loading) item("fmore") { YtLoadingMore() }
                if (!selected.pages && page.items.isNotEmpty()) item("fend") {
                    Text("That's the whole list. Search or pick another chip for more.", color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
                }
            }
        } else LazyColumn(Modifier.fillMaxSize().testTag("youtube_home_list"), contentPadding = PaddingValues(bottom = 160.dp)) {
            item("for_you_head") {
                Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("おすすめ · FOR YOU", color = SpotifyGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
                        Text(
                            if (mine.isEmpty()) "Play a few things and this page learns what you like" else "Picked from what you play, your topics and today's mix",
                            color = TextSecondary, fontSize = 12.sp,
                        )
                    }
                    TextButton(onClick = { editingTopics = true }, modifier = Modifier.testTag("youtube_edit_topics")) { Text("Topics", color = SpotifyGreen, fontSize = 13.sp) }
                    IconButton(onClick = { shuffle = YtTopics.bumpShuffle(context) }, modifier = Modifier.testTag("youtube_shuffle")) {
                        Icon(Icons.Default.Shuffle, "Shuffle today's mix", tint = SpotifyGreen)
                    }
                }
            }
            if (mine.isNotEmpty()) item("history") {
                YtShelfRow(if (music) "Listen again" else "Watch again", null, Icons.Default.History, mine.take(20), music, onSeeAll = null) { onPlay(it, mine) }
            }
            // Featured: the first planned feed as quick picks (music) or big cards (video).
            val featured = feeds.firstOrNull()
            if (featured != null) when (val l = shelves[featured.id] ?: Load.Loading) {
                Load.Loading -> if (netOnline) item("feat_load") { SkeletonBlock(null, 230.dp, Modifier.padding(16.dp), RoundedCornerShape(24.dp)) }
                is Load.Failed -> item("feat_err") { YtProblem(l.message) { loadShelves(true) } }
                is Load.Ready -> {
                    item("feat_label") { YtLabel(featured.title, featured.overline) }
                    if (music) itemsIndexed(l.value.take(8), key = { i, it -> "f${i}_${it.id}" }) { _, item -> SongRow(item) { onPlay(item, l.value) } }
                    else itemsIndexed(l.value.take(4), key = { i, it -> "f${i}_${it.id}" }) { _, item -> YtVideoCard(item, wide = true) { onPlay(item, l.value) } }
                }
            }
            feeds.drop(1).forEach { f ->
                item("shelf_${f.id}") {
                    when (val l = shelves[f.id] ?: Load.Loading) {
                        Load.Loading -> if (netOnline) Column { YtLabel(f.title, f.overline); Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { repeat(3) { SkeletonBlock(if (music) 140.dp else 220.dp, if (music) 140.dp else 124.dp, shape = RoundedCornerShape(18.dp)) } } }
                        is Load.Failed -> Unit
                        is Load.Ready -> YtShelfRow(f.title, f.overline, if (music) Icons.Default.MusicNote else Icons.Default.SmartDisplay, l.value, music,
                            onSeeAll = { chip = f.id; if (pagedFeeds[f.id] == null) loadFeedPage(f) }) { onPlay(it, l.value) }
                    }
                }
            }
            item("note") {
                Text(
                    "Streams come straight from YouTube without an account. Your topics, searches and history stay on this device.",
                    color = TextTertiary, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                )
            }
        }
    }

    if (addingTopic) YtAddTopicDialog(music, onDismiss = { addingTopic = false }) { pin(it); addingTopic = false }
    if (editingTopics) YtTopicsDialog(
        topics = topics, music = music,
        onRemove = { unpin(it) }, onAdd = { pin(it) },
        onDismiss = { editingTopics = false },
    )
}

/** Loads the next page when the list is within 5 items of the end. */
@Composable
private fun YtEndlessTrigger(state: androidx.compose.foundation.lazy.LazyListState, enabled: Boolean, onMore: () -> Unit) {
    val nearEnd by remember(state) {
        androidx.compose.runtime.derivedStateOf {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 5
        }
    }
    LaunchedEffect(nearEnd, enabled) { if (nearEnd && enabled) onMore() }
}

@Composable
private fun YtLoadingMore() {
    Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
        androidx.compose.material3.CircularProgressIndicator(color = SpotifyGreen, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun YtFeedHeader(title: String, overline: String, pinned: Boolean, onPin: () -> Unit, onUnpin: () -> Unit, canPin: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(overline.uppercase(), color = SpotifyGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
            Text(title, color = TextPrimary, fontFamily = dev.asfakul.yumeiro.ui.theme.Manrope, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (canPin) TextButton(onClick = if (pinned) onUnpin else onPin, modifier = Modifier.testTag("youtube_pin_topic")) {
            Icon(if (pinned) Icons.Default.Close else Icons.Default.Add, null, tint = SpotifyGreen, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (pinned) "Remove topic" else "Add as topic", color = SpotifyGreen, fontSize = 13.sp)
        }
    }
}

@Composable
private fun YtAddTopicDialog(music: Boolean, onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    dev.asfakul.yumeiro.ui.components.TsukiDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a topic") },
        text = {
            Column {
                Text(
                    if (music) "Any artist, genre, anime or mood. It becomes a chip and a shelf on this page."
                    else "Any show, game, creator or interest. It becomes a chip and a shelf on this page.",
                    color = TextSecondary, fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.OutlinedTextField(
                    text, { text = it }, singleLine = true, placeholder = { Text(if (music) "e.g. Yoasobi, city pop" else "e.g. Frieren, speedruns") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { YtDiscovery.cleanTopic(text)?.let(onAdd) }),
                    modifier = Modifier.fillMaxWidth().testTag("youtube_topic_input"),
                )
            }
        },
        confirmButton = { TextButton(onClick = { YtDiscovery.cleanTopic(text)?.let(onAdd) }, enabled = YtDiscovery.cleanTopic(text) != null) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun YtTopicsDialog(topics: List<String>, music: Boolean, onRemove: (String) -> Unit, onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var adding by remember { mutableStateOf(false) }
    dev.asfakul.yumeiro.ui.components.TsukiDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your topics") },
        text = {
            Column {
                Text("Topics shape your chips and shelves. Tap one to remove it.", color = TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                if (topics.isEmpty()) Text("No topics yet — the page uses what you play and today's mix.", color = TextTertiary, fontSize = 12.sp)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("youtube_topics_list")) {
                    topics.forEach { t -> YtChip(t, true, trailing = Icons.Default.Close) { onRemove(t) } }
                    YtChip("Add", false, leading = Icons.Default.Add) { adding = true }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
    if (adding) YtAddTopicDialog(music, onDismiss = { adding = false }) { onAdd(it); adding = false }
}

@Composable
private fun YtSearchBar(query: String, placeholder: String, music: Boolean, autoFocus: Boolean = false, onChange: (String) -> Unit, onSearch: () -> Unit, onClear: () -> Unit) {
    val border = rememberGlassBorder()
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(autoFocus) { if (autoFocus) runCatching { focus.requestFocus() } }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).height(50.dp).clip(RoundedCornerShape(25.dp))
            .background(GlassFill).border(1.dp, border, RoundedCornerShape(25.dp)).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (music) Icons.Default.MusicNote else Icons.Default.SmartDisplay, null, tint = SpotifyGreen, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text(placeholder, color = TextTertiary, fontSize = 14.sp)
            BasicTextField(
                query, onChange, singleLine = true, cursorBrush = SolidColor(SpotifyGreen),
                textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("youtube_search"),
            )
        }
        IconButton(onClick = onClear, modifier = Modifier.size(40.dp).testTag("youtube_search_close")) { Icon(Icons.Default.Close, if (query.isNotEmpty()) "Clear" else "Close search", tint = TextSecondary) }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun YtChip(
    label: String, selected: Boolean, modifier: Modifier = Modifier,
    leading: androidx.compose.ui.graphics.vector.ImageVector? = null,
    trailing: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val brush = rememberSignatureBrush()
    val fg = if (selected) OnAccent else TextSecondary
    Row(
        modifier.clip(CircleShape).then(if (selected) Modifier.background(brush) else Modifier.background(GlassFill))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) { Icon(leading, null, tint = fg, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)) }
        Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailing != null) { Spacer(Modifier.width(4.dp)); Icon(trailing, "Remove", tint = fg, modifier = Modifier.size(14.dp)) }
    }
}

@Composable
private fun YtLabel(title: String, overline: String?) {
    Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 6.dp)) {
        if (overline != null) Text(overline.uppercase(), color = SpotifyGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
        Text(title, color = TextPrimary, fontFamily = dev.asfakul.yumeiro.ui.theme.Manrope, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

@Composable
private fun YtShelfRow(
    title: String, overline: String?, icon: androidx.compose.ui.graphics.vector.ImageVector, items: List<YtItem>, music: Boolean,
    onSeeAll: (() -> Unit)?, onClick: (YtItem) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = SpotifyGreen, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                if (overline != null) Text(overline.uppercase(), color = TextTertiary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, maxLines = 1)
                Text(title, color = TextPrimary, fontFamily = dev.asfakul.yumeiro.ui.theme.Manrope, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (onSeeAll != null) TextButton(onClick = onSeeAll) { Text("See all", color = SpotifyGreen, fontSize = 13.sp) }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(items, key = { i, it -> "${i}_${it.id}" }) { _, item ->
                if (music) SquareCard(item, 144.dp) { onClick(item) } else Box(Modifier.width(240.dp)) { YtVideoCard(item, wide = false) { onClick(item) } }
            }
        }
    }
}

/** YouTube-style video card: 16:9 thumbnail with duration / LIVE badge, channel and views below. */
@Composable
private fun YtVideoCard(item: YtItem, wide: Boolean, onClick: () -> Unit) {
    val border = rememberGlassBorder()
    Column(Modifier.fillMaxWidth().then(if (wide) Modifier.padding(horizontal = 16.dp, vertical = 8.dp) else Modifier).clickable(onClick = onClick).testTag("youtube_card")) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(if (wide) 22.dp else 16.dp)).border(1.dp, border, RoundedCornerShape(if (wide) 22.dp else 16.dp))) {
            PosterImage(item.thumbnail, emptyMap(), Modifier.fillMaxSize(), Icons.Default.SmartDisplay)
            val badge = when { item.live -> "LIVE"; item.durationSec > 0 -> formatYtDuration(item.durationSec); else -> null }
            if (badge != null) Surface(
                shape = RoundedCornerShape(8.dp), color = if (item.live) Color(0xFFE53950) else Color.Black.copy(alpha = 0.72f),
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            ) { Text(badge, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
        }
        Row(Modifier.padding(top = 10.dp, start = 2.dp, end = 2.dp)) {
            if (wide) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(Brush.linearGradient(listOf(SpotifyGreen, AccentSecondary))), contentAlignment = Alignment.Center) {
                    if (item.channelAvatar != null) PosterImage(item.channelAvatar, emptyMap(), Modifier.fillMaxSize())
                    else Text(item.channel.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(item.title, color = TextPrimary, fontSize = if (wide) 14.sp else 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(item.channel.takeIf { it.isNotBlank() }, item.views.takeIf { it >= 0 }?.let { formatYtViews(it) }, item.uploaded).joinToString(" · "),
                    color = TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SquareCard(item: YtItem, size: Dp, onClick: () -> Unit) {
    Column(Modifier.width(size).clickable(onClick = onClick)) {
        Box(Modifier.size(size).clip(RoundedCornerShape(18.dp))) {
            PosterImage(item.thumbnail, emptyMap(), Modifier.fillMaxSize(), Icons.Default.MusicNote)
            Box(Modifier.align(Alignment.BottomEnd).padding(8.dp).size(34.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(item.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(item.channel.removeSuffix(" - Topic"), color = TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SongRow(item: YtItem, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 7.dp).testTag("youtube_song"), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(54.dp).clip(RoundedCornerShape(12.dp))) { PosterImage(item.thumbnail, emptyMap(), Modifier.fillMaxSize(), Icons.Default.MusicNote) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(item.channel.removeSuffix(" - Topic").takeIf { it.isNotBlank() }, item.durationSec.takeIf { it > 0 }?.let { formatYtDuration(it) }).joinToString(" · "),
                color = TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Default.PlayArrow, "Play", tint = SpotifyGreen)
    }
}

@Composable
private fun SongRowSkeleton() {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        SkeletonBlock(54.dp, 54.dp, shape = RoundedCornerShape(12.dp))
        Spacer(Modifier.width(12.dp))
        Column { SkeletonBlock(180.dp, 14.dp); Spacer(Modifier.height(6.dp)); SkeletonBlock(110.dp, 12.dp) }
    }
}

@Composable
private fun YtProblem(message: String, onRetry: (() -> Unit)?) {
    Surface(shape = RoundedCornerShape(18.dp), color = GlassFill, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.WifiOff, null, tint = TextSecondary)
            Spacer(Modifier.width(12.dp))
            Text(message, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
            if (onRetry != null) TextButton(onClick = onRetry) { Text("Retry", color = SpotifyGreen) }
        }
    }
}
