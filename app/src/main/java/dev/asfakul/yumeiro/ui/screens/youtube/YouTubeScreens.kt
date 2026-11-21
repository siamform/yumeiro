/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.youtube

import dev.asfakul.yumeiro.ui.feed.feedCard
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
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
import androidx.compose.material.icons.filled.Person
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
import dev.asfakul.yumeiro.youtube.interleaveFeeds
import dev.asfakul.yumeiro.youtube.isShort
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

internal fun friendly(t: Throwable): String = when {
    YtAccount.accountCanHelp(t) -> YtAccount.blockedHint()
    t.javaClass.simpleName.contains("ReCaptcha") -> "YouTube is rate-limiting this network. Try again in a few minutes or switch networks."
    !dev.asfakul.yumeiro.network.Connectivity.online.value -> "You're offline. Your downloads and songs on this phone still play."
    t is java.io.IOException -> "Couldn't reach YouTube. Check your connection and try again."
    else -> readable(dev.asfakul.yumeiro.online.OnlineErrors.friendly(t, "YouTube didn't answer. Try again in a moment."))
}

/** Internal library or resource messages are never shown to people as-is. */
internal fun readable(m: String): String {
    val internal = listOf("resource", "Exception", "null", "PublicSuffix", "java.", "kotlin.", "Unable to load")
    return if (m.isBlank() || internal.any { m.contains(it, ignoreCase = false) }) "YouTube didn't answer. Try again in a moment." else m
}

/** Videos → YouTube: a login-free home planned from what you watch, pin and search. */
@Composable
fun YouTubeVideoScreen(onPlay: (YtItem) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // F-12 (ADR-018): a Short from the shelf opens the vertical Shorts player on phones.
    var shorts by remember { mutableStateOf<Pair<List<YtItem>, Int>?>(null) }
    YtBrowse(
        music = false,
        searchFilter = YouTube.Filter.VIDEOS,
        placeholder = "Search YouTube",
        onPlay = { item, list ->
            val queue = list.filter { it.isShort() }
            val on = dev.asfakul.yumeiro.FeatureFlags.on(context, dev.asfakul.yumeiro.FeatureFlags.Flag.SHORTS_PLAYER)
            if (ShortsWindow.opens(item.isShort(), queue.size, dev.asfakul.yumeiro.Edition.isTv, on)) {
                shorts = queue to queue.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
            } else onPlay(item)
        },
        modifier = modifier.testTag("youtube_video_home"),
    )
    shorts?.let { (queue, start) ->
        ShortsPlayerDialog(queue, start, onDismiss = { shorts = null }, onFullPlayer = { shorts = null; onPlay(it) })
    }
}

/** Music → YouTube Music: songs planned from what you play, your topics and today's mix. */
@Composable
fun YouTubeMusicScreen(onPlay: (YtItem, List<YtItem>) -> Unit, modifier: Modifier = Modifier) {
    var basic by rememberSaveable { mutableStateOf(false) }
    if (!basic) {
        YtMusicBrowse(onPlay = onPlay, onBasic = { basic = true }, modifier = modifier.testTag("youtube_music_home"))
        return
    }
    YtBrowse(
        music = true,
        searchFilter = YouTube.Filter.SONGS,
        placeholder = "Songs, artists, anime OSTs…",
        onPlay = onPlay,
        modifier = modifier.testTag("youtube_music_home"),
    )
}


private const val FOR_YOU = "for_you"

/**
 * R15 Phase 3 (ADR-008): the discovery state of YouTube / YouTube Music lives for the process, not
 * for the composable. The plan, the shelves, every chip's endless list, the For-you stream and the
 * search results used to be `remember {}` state; leaving the tab or swiping the pager threw them
 * away, the list came back from the cache at the old scroll index ("scrolling stops and shows
 * saved data"), loads cancelled mid-way stayed "loading" forever, and a late answer for an old
 * query or plan overwrote the screen. See ui/feed/LiveFeed.kt.
 */
private class YtBrowseStore {
    var plan: List<YtFeed> = emptyList()
    var planKey: Any? = null
    val shelves = mutableStateMapOf<String, Load<List<YtItem>>>()
    var shelfGen = 0
    private val paged = HashMap<String, dev.asfakul.yumeiro.ui.feed.LivePaged<YtItem, Page>>()
    /** Every chip's endless list (created on first use; kept while the plan has the feed). */
    fun feed(context: android.content.Context, f: YtFeed): dev.asfakul.yumeiro.ui.feed.LivePaged<YtItem, Page> {
        val app = context.applicationContext
        return paged.getOrPut(f.id) {
            dev.asfakul.yumeiro.ui.feed.LivePaged({ it.id }, { cur -> YouTube.feed(app, f, cur).let { it.items to it.next } }, describe = ::friendly)
        }
    }
    fun forget(keep: Set<String>) { paged.keys.retainAll(keep); shelves.keys.retainAll(keep) }
    /** The For-you stream: append-only, shorts on their own shelf. */
    val videos = dev.asfakul.yumeiro.ui.feed.AppendOnly<YtItem> { it.id }
    val shorts = dev.asfakul.yumeiro.ui.feed.AppendOnly<YtItem> { it.id }
    var turn = 0
    var resultsFor by mutableStateOf<String?>(null)
    val results = dev.asfakul.yumeiro.ui.feed.LivePaged<YtItem, Page>({ it.id }, { _ -> emptyList<YtItem>() to null }, describe = ::friendly)
    var related by mutableStateOf<List<String>>(emptyList())
    /** BUG-2 (E2): channels matching the search, shown above the videos. */
    var channels by mutableStateOf<List<dev.asfakul.yumeiro.youtube.YtChannelHit>>(emptyList())

    companion object {
        val video = YtBrowseStore()
        val music = YtBrowseStore()
    }
}

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
    val store = remember(music) { if (music) YtBrowseStore.music else YtBrowseStore.video }
    val app = context.applicationContext
    var chip by rememberSaveable { mutableStateOf(FOR_YOU) }
    var topics by remember { mutableStateOf(YtTopics.load(context, music)) }
    var shuffle by remember { mutableStateOf(YtTopics.shuffleCount(context)) }
    var editingTopics by remember { mutableStateOf(false) }
    var addingTopic by remember { mutableStateOf(false) }
    // A channel opened from a card replaces the page in place (no new screen, no flash).
    var channelUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var channelName by rememberSaveable { mutableStateOf("") }
    val openChannel: ((YtItem) -> Unit)? = if (music) null else { item ->
        // BUG-2: a card without a channel link used to do nothing at all on tap.
        if (item.channelUrl == null) dev.asfakul.yumeiro.ui.components.AppMessages.info(context, "Channel not known yet. Play the video once to learn it")
        else { channelName = item.channel; channelUrl = item.channelUrl }
    }
    LaunchedEffect(Unit) { YtHistory.load(context) }
    // BUG-2 (E1, E4): a channel opened from the watch page or a shared link.
    if (!music) {
        val launch by dev.asfakul.yumeiro.youtube.YtChannelLaunch.request.collectAsState()
        LaunchedEffect(launch) {
            dev.asfakul.yumeiro.youtube.YtChannelLaunch.take()?.let { r -> channelName = r.name; channelUrl = r.url }
        }
    }
    val history by YtHistory.items.collectAsState()
    // Offline, the page banner already explains what still works; empty skeleton shelves would only look broken.
    val netOnline by dev.asfakul.yumeiro.network.Connectivity.online.collectAsState()
    val mine = remember(history) { history.filter { it.music == music } }
    // Re-plan only when the inputs change; history is reduced to what the planner reads so a new
    // play doesn't reshuffle the page under the user's finger.
    val historySeed = remember(mine) { mine.take(24) }
    val searches = remember(submitted) { dev.asfakul.yumeiro.ui.components.RecentSearches.load(context, recentScope) }
    // R15: the plan is frozen once the For-you stream shows cards. A new play or search used to
    // re-plan it (new feed ids) and reshuffle the whole page under the user's finger; now only an
    // explicit change (topics, Shuffle) starts a new plan.
    val feeds = remember(music, historySeed, topics, searches, shuffle) {
        val key = Triple(music, topics, shuffle)
        if (store.plan.isEmpty() || store.planKey != key || (store.videos.items.isEmpty() && store.shelves.values.none { it is Load.Ready })) {
            val fresh = YtDiscovery.plan(YtDiscovery.Input(music, historySeed, topics, searches, java.time.LocalDate.now().toEpochDay() + shuffle * 101))
            if (store.planKey != null && store.planKey != key) { store.videos.clear(); store.shorts.clear(); store.turn = 0 }
            store.plan = fresh
            store.planKey = key
            store.forget(fresh.mapTo(HashSet()) { it.id })
        }
        store.plan
    }
    val shelves = store.shelves

    /** Loads in the process scope (never cancelled by leaving the tab); a stale answer is dropped. */
    fun loadShelves(force: Boolean) {
        val gate = Semaphore(2)
        if (force) store.shelfGen++
        val g = store.shelfGen
        feeds.forEach { f ->
            val cur = shelves[f.id]
            if (!force && (cur is Load.Ready || cur is Load.Loading)) return@forEach
            // Stale-while-revalidate: cards already on screen stay while the new copy loads.
            if (cur !is Load.Ready) shelves[f.id] = Load.Loading
            dev.asfakul.yumeiro.ui.feed.FeedScope.main.launch {
                // FE-7a: the copy saved on the phone shows at once (no skeleton), then the live one replaces it.
                if (shelves[f.id] !is Load.Ready) YouTube.savedFeed(f)?.let { saved ->
                    if (g == store.shelfGen && shelves[f.id] !is Load.Ready) shelves[f.id] = Load.Ready(saved)
                }
                val r = gate.withPermit {
                    runCatching { YouTube.feed(app, f, force = force) }.fold(
                        { if (it.items.isEmpty()) Load.Failed("Nothing here right now.") else Load.Ready(it.items) },
                        { Load.Failed(friendly(it)) },
                    )
                }
                if (g != store.shelfGen && shelves[f.id] !is Load.Loading) return@launch
                if (r is Load.Failed && shelves[f.id] is Load.Ready) return@launch
                shelves[f.id] = r
            }
        }
    }
    LaunchedEffect(feeds) { loadShelves(false) }
    LaunchedEffect(netOnline) { if (netOnline && shelves.values.any { it is Load.Failed }) loadShelves(false) }

    /** A chip's endless list: first page on first open, then more as the list nears its end. */
    fun openFeed(f: YtFeed) { store.feed(app, f).let { if (!it.started && !it.loading) it.more() } }

    LaunchedEffect(query) {
        if (query.isBlank() || query == submitted) { suggestions = emptyList(); return@LaunchedEffect }
        // Instant local typeahead first (recent searches + what you played), then YouTube's own suggestions.
        // FE-S2: folding (regex) and the saved recent searches stay off the main thread.
        val topicsNow = topics; val mineNow = mine
        val local = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { dev.asfakul.yumeiro.ui.components.SearchSuggest.rank(
            query,
            dev.asfakul.yumeiro.ui.components.RecentSearches.asSuggestions(context, recentScope) +
                topicsNow.map { dev.asfakul.yumeiro.ui.components.Suggestion(it, SuggestionKind.RECENT, "Your topic") } +
                mineNow.take(80).map { dev.asfakul.yumeiro.ui.components.Suggestion(it.title, SuggestionKind.YOUTUBE, if (music) "Listened" else "Watched") },
            limit = 3,
        ) }
        suggestions = local
        delay(200)
        val remote = runCatching { YouTube.suggestions(context, query) }.getOrDefault(emptyList())
            .map { dev.asfakul.yumeiro.ui.components.Suggestion(it, SuggestionKind.QUERY) }
        suggestions = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { (local + remote).distinctBy { dev.asfakul.yumeiro.ui.components.SearchSuggest.fold(it.text) }.take(8) }
    }
    /** R15: a new search drops every answer of the previous one (it used to overwrite the new list). */
    fun startResults(text: String) {
        store.resultsFor = text
        store.related = emptyList()
        store.channels = emptyList()
        if (!music) dev.asfakul.yumeiro.ui.feed.FeedScope.main.launch {
            val c = YouTube.searchChannels(app, text)
            if (store.resultsFor == text) store.channels = c
        }
        store.results.reset { cur -> YouTube.search(app, text, searchFilter, cur).let { it.items to it.next } }
        store.results.more()
        dev.asfakul.yumeiro.ui.feed.FeedScope.main.launch {
            val s = runCatching { YouTube.suggestions(app, text) }.getOrDefault(emptyList())
            if (store.resultsFor == text) store.related = YtDiscovery.relatedChips(text, s, topics)
        }
    }
    fun search(q: String) {
        val text = q.trim().takeIf { it.isNotEmpty() } ?: return
        query = text; submitted = text; suggestions = emptyList(); focus.clearFocus()
        dev.asfakul.yumeiro.ui.components.RecentSearches.add(context, recentScope, text)
        startResults(text)
    }
    // Back on the tab (or after the process was restored) with a search open: same results, or a fresh load.
    LaunchedEffect(submitted) { submitted?.let { if (store.resultsFor != it) startResults(it) } }
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

    val openUrl = channelUrl
    if (openUrl != null) {
        Box(modifier.fillMaxSize()) { YtChannelPage(openUrl, channelName, onBack = { channelUrl = null }, onPlay = onPlay) }
        return
    }

    // The search bar floats over the page (slides down from under the top bar) instead of pushing
    // the whole feed down: opening search no longer re-lays out ("reloads") the page under it.
    Box(modifier.fillMaxSize()) {

        val searchKey = if (music) dev.asfakul.yumeiro.ui.components.TabSearch.YT_MUSIC else dev.asfakul.yumeiro.ui.components.TabSearch.YT_VIDEO
        val asked by dev.asfakul.yumeiro.ui.components.TabSearch.open.collectAsState()
        val barOpen = asked == searchKey || query.isNotEmpty() || submitted != null
        Column(Modifier.fillMaxSize().padding(top = if (submitted != null) YT_SEARCH_BAR_H else 0.dp)) {
        val sub = submitted
        val res = store.results
        if (sub != null && store.resultsFor == sub) {
            val listState = dev.asfakul.yumeiro.ui.components.ScrollMemory.list("yt_results:" + sub)
            val relatedSearches = store.related
            YtEndlessTrigger(listState, res.canLoadMore) { res.more() }
            val rcols = if (music) 1 else ytVideoColumns()
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
                val foundChannels = store.channels
                if (foundChannels.isNotEmpty()) item("res_channels") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("youtube_channel_results")) {
                        itemsIndexed(foundChannels, key = { _, c -> "ch_" + c.url }) { _, c ->
                            YtChip(c.name.ifBlank { "Channel" }.take(28), false, leading = Icons.Default.Person) { channelName = c.name; channelUrl = c.url }
                        }
                    }
                }
                if (res.items.isEmpty() && res.loading) itemsIndexed(List(6) { it }, key = { i, _ -> "sk$i" }) { _, _ -> if (music) SongRowSkeleton() else SkeletonBlock(null, 220.dp, Modifier.padding(16.dp), RoundedCornerShape(22.dp)) }
                if (res.items.isEmpty() && !res.loading && res.error == null) item("none") { YtProblem("No results for “$sub”.", null) }
                if (rcols > 1) itemsIndexed(res.items.chunked(rcols), key = { _, r -> "rr_${r.first().id}" }) { _, row ->
                    YtVideoRow(row, rcols, openChannel) { item -> onPlay(item, res.items) }
                } else itemsIndexed(res.items, key = { _, it -> "r_${it.id}" }) { _, item ->
                    if (music) SongRow(item) { onPlay(item, res.items) } else YtVideoCard(item, wide = true, onChannel = openChannel) { onPlay(item, res.items) }
                }
                res.error?.let { e -> item("err") { YtProblem(e) { res.retry() } } }
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
                ) { chip = f.id; openFeed(f) }
            }
            item("c_add") { YtChip("Topic", false, leading = Icons.Default.Add, modifier = Modifier.testTag("youtube_add_topic")) { addingTopic = true } }
            if (!music && topics.isNotEmpty()) item("c_edit") { YtChip("Edit topics", false, modifier = Modifier.testTag("youtube_edit_topics_chip")) { editingTopics = true } }
        }

        val selected = feeds.firstOrNull { it.id == chip }
        if (chip != FOR_YOU && selected != null) {
            LaunchedEffect(selected.id) { openFeed(selected) }
            val page = store.feed(app, selected)
            val listState = dev.asfakul.yumeiro.ui.components.ScrollMemory.list("yt_feed:" + selected.id)
            YtEndlessTrigger(listState, selected.pages && page.started && page.canLoadMore) { page.more() }
            val fcols = if (music) 1 else ytVideoColumns()
            LazyColumn(Modifier.fillMaxSize().testTag("youtube_feed_list"), state = listState, contentPadding = PaddingValues(bottom = 160.dp)) {
                item("feed_head") {
                    YtFeedHeader(
                        title = selected.title, overline = selected.overline,
                        pinned = isTopic(selected.title), onPin = { pin(selected.title) }, onUnpin = { unpin(selected.title) },
                        canPin = selected.kind == YtFeed.Kind.QUERY,
                    )
                }
                if (page.items.isEmpty() && (page.loading || !page.started) && page.error == null) itemsIndexed(List(6) { it }, key = { i, _ -> "fsk$i" }) { _, _ -> if (music) SongRowSkeleton() else SkeletonBlock(null, 220.dp, Modifier.padding(16.dp), RoundedCornerShape(22.dp)) }
                if (fcols > 1) itemsIndexed(page.items.chunked(fcols), key = { _, r -> "fr_${r.first().id}" }) { _, row ->
                    YtVideoRow(row, fcols, openChannel) { item -> onPlay(item, page.items) }
                } else itemsIndexed(page.items, key = { _, it -> "fi_${it.id}" }) { _, item ->
                    if (music) SongRow(item) { onPlay(item, page.items) } else YtVideoCard(item, wide = true, onChannel = openChannel) { onPlay(item, page.items) }
                }
                page.error?.let { e -> item("ferr") { YtProblem(e) { page.retry() } } }
                if (page.items.isNotEmpty() && page.loading) item("fmore") { YtLoadingMore() }
                if (!selected.pages && page.items.isNotEmpty()) item("fend") {
                    Text("That's the whole list. Search or pick another chip for more.", color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
                }
            }
        } else if (!music) {
            // YouTube home: one mixed stream of full-width videos with a Shorts shelf and
            // "Watch again" woven in, paging forever from the planned feeds.
            // R15: append-only. New pages are interleaved and added at the end; cards on screen
            // never move, vanish or get replaced (the stream used to be rebuilt from every list on
            // each page, so everything below the first changed feed shifted under the finger).
            val paged = feeds.map { f -> store.feed(app, f) }
            LaunchedEffect(store, feeds) {
                androidx.compose.runtime.snapshotFlow {
                    feeds.mapIndexed { i, f -> paged[i].items.toList().ifEmpty { (shelves[f.id] as? Load.Ready)?.value.orEmpty() } }
                }.collect { lists ->
                    val merged = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { interleaveFeeds(lists) }
                    store.shorts.append(merged.filter { it.isShort() })
                    store.videos.append(merged.filterNot { it.isShort() })
                }
            }
            val videos = store.videos.items
            val shorts = store.shorts.items
            val anyLoading = feeds.any { shelves[it.id] == null || shelves[it.id] is Load.Loading } || paged.any { it.loading }
            val allFailed = feeds.isNotEmpty() && feeds.all { shelves[it.id] is Load.Failed }
            val listState = dev.asfakul.yumeiro.ui.components.ScrollMemory.list("yt_foryou")
            val pageable = feeds.indices.filter { feeds[it].pages && paged[it].canLoadMore }
            YtEndlessTrigger(listState, videos.isNotEmpty() && pageable.isNotEmpty() && paged.none { it.loading }) {
                // Two feeds per step keeps the stream varied without flooding the network.
                repeat(minOf(2, pageable.size)) { k -> paged[pageable[(store.turn + k) % pageable.size]].more() }
                store.turn += 2
            }
            val cols = ytVideoColumns()
            val rows = if (cols == 1) null else remember(videos.size, videos.firstOrNull()?.id, cols) { videos.toList().chunked(cols) }
            LazyColumn(Modifier.fillMaxSize().testTag("youtube_home_list"), state = listState, contentPadding = PaddingValues(bottom = 160.dp)) {
                if (videos.isEmpty() && anyLoading && netOnline) itemsIndexed(List(4) { it }, key = { i, _ -> "hsk$i" }) { _, _ ->
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        SkeletonBlock(null, 200.dp, shape = RoundedCornerShape(18.dp))
                        Row(Modifier.padding(top = 10.dp)) {
                            SkeletonBlock(36.dp, 36.dp, shape = CircleShape); Spacer(Modifier.width(12.dp))
                            Column { SkeletonBlock(220.dp, 14.dp); Spacer(Modifier.height(6.dp)); SkeletonBlock(140.dp, 12.dp) }
                        }
                    }
                }
                if (videos.isEmpty() && allFailed) item("home_err") { YtProblem((shelves[feeds.first().id] as Load.Failed).message) { loadShelves(true) } }
                // G15-2 (TV-YT-1, ADR-031): wide screens (TV, tablets) lay the stream out in rows of
                // 2–4 cards; phones keep one column. Rows are keyed by their first card, so a row that
                // fills up as pages append stays in place (append-only, R15).
                if (rows != null) itemsIndexed(rows, key = { _, r -> "hr_${r.first().id}" }) { ri, row ->
                    YtVideoRow(row, cols, openChannel) { item -> onPlay(item, videos.toList()) }
                    if (ri == 0 && shorts.isNotEmpty()) {
                        Column(Modifier.padding(vertical = 8.dp).testTag("youtube_shorts_shelf")) {
                            YtLabel("Shorts", "ショート")
                            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                itemsIndexed(shorts, key = { _, s -> "s_${s.id}" }) { _, s -> YtShortCard(s, Modifier.width(150.dp)) { onPlay(s, shorts.toList()) } }
                            }
                        }
                    }
                    if (ri == 1 && mine.isNotEmpty()) {
                        YtShelfRow("Watch again", null, Icons.Default.History, mine.take(20), music = false, onSeeAll = null) { onPlay(it, mine) }
                    }
                }
                if (rows == null) itemsIndexed(videos, key = { _, it -> "h_${it.id}" }) { i, item ->
                    YtVideoCard(item, wide = true, onChannel = openChannel) { onPlay(item, videos.toList()) }
                    if (i == 1 && shorts.isNotEmpty()) {
                        Column(Modifier.padding(vertical = 8.dp).testTag("youtube_shorts_shelf")) {
                            YtLabel("Shorts", "ショート")
                            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                itemsIndexed(shorts, key = { _, s -> "s_${s.id}" }) { _, s -> YtShortCard(s, Modifier.width(150.dp)) { onPlay(s, shorts.toList()) } }
                            }
                        }
                    }
                    if (i == 4 && mine.isNotEmpty()) {
                        YtShelfRow("Watch again", null, Icons.Default.History, mine.take(20), music = false, onSeeAll = null) { onPlay(it, mine) }
                    }
                }
                if (videos.isNotEmpty() && paged.any { it.loading }) item("home_more") { YtLoadingMore() }
                item("note") {
                    Text(
                        "Streams come straight from YouTube without an account. Your topics, searches and history stay on this device.",
                        color = TextTertiary, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    )
                }
            }
        } else LazyColumn(Modifier.fillMaxSize().testTag("youtube_home_list"), state = dev.asfakul.yumeiro.ui.components.ScrollMemory.list("youtube_home"), contentPadding = PaddingValues(bottom = 160.dp)) {
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
                            onSeeAll = { chip = f.id; openFeed(f) }) { onPlay(it, l.value) }
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
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().zIndex(2f)) {
        androidx.compose.animation.AnimatedVisibility(
            visible = barOpen,
            enter = androidx.compose.animation.slideInVertically(dev.asfakul.yumeiro.ui.components.YuMotion.enter(dev.asfakul.yumeiro.ui.components.reducedMotion(), 240)) { -it / 2 } + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically(dev.asfakul.yumeiro.ui.components.YuMotion.exit(dev.asfakul.yumeiro.ui.components.reducedMotion(), 180)) { -it / 2 } + androidx.compose.animation.fadeOut(),
        ) {
            YtSearchBar(
                query, placeholder, music, autoFocus = asked == searchKey, floating = true,
                onChange = { query = it; if (it.isBlank()) submitted = null }, onSearch = { search(query) },
                onClear = { query = ""; submitted = null; dev.asfakul.yumeiro.ui.components.TabSearch.close(searchKey) },
            )
        }
        dev.asfakul.yumeiro.ui.components.SuggestionList(
            query = query, suggestions = suggestions,
            onPick = { search(it.text) }, onFill = { query = it.text },
            tag = "youtube_suggestions",
        )
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
internal fun YtEndlessTrigger(state: androidx.compose.foundation.lazy.LazyListState, enabled: Boolean, onMore: () -> Unit) {
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
internal fun YtLoadingMore() {
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

/** Height the floating search bar takes (50dp + 2 × 6dp), reserved above results. */
internal val YT_SEARCH_BAR_H = 62.dp

@Composable
internal fun YtSearchBar(query: String, placeholder: String, music: Boolean, autoFocus: Boolean = false, floating: Boolean = false, onChange: (String) -> Unit, onSearch: () -> Unit, onClear: () -> Unit) {
    val border = rememberGlassBorder()
    val pal = dev.asfakul.yumeiro.ui.theme.LocalMtPalette.current
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(autoFocus) { if (autoFocus) runCatching { focus.requestFocus() } }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).height(50.dp)
            .then(if (floating) Modifier.shadow(10.dp, RoundedCornerShape(25.dp), ambientColor = Color.Black.copy(alpha = 0.4f), spotColor = Color.Black.copy(alpha = 0.4f)) else Modifier)
            .clip(RoundedCornerShape(25.dp))
            .background(if (floating) pal.surfaceContainerHigh else GlassFill).border(1.dp, border, RoundedCornerShape(25.dp)).padding(horizontal = 16.dp),
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
internal fun YtChip(
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

/** G15-2 (ADR-031): one column below 600 dp; from there width / 300 dp, 2–4 (NewPipe's Auto grid, SmartTube rows). */
internal fun ytColumnsFor(widthDp: Int): Int = if (widthDp < 600) 1 else (widthDp / 300).coerceIn(2, 4)

@Composable
internal fun ytVideoColumns(): Int = ytColumnsFor(androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp)

/** A row of [cols] equal-width video cards (the last row keeps empty slots so cards never stretch). */
@Composable
private fun YtVideoRow(row: List<YtItem>, cols: Int, onChannel: ((YtItem) -> Unit)?, onPlay: (YtItem) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
        row.forEach { item -> Box(Modifier.weight(1f)) { YtVideoCard(item, wide = true, onChannel = onChannel) { onPlay(item) } } }
        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
    }
}

/**
 * YouTube-style video card: 16:9 thumbnail with duration / LIVE badge, then avatar, title and
 * "channel · views · age". Tapping the avatar or channel name opens the channel when [onChannel] is set.
 */
@Composable
internal fun YtVideoCard(item: YtItem, wide: Boolean, showChannel: Boolean = true, onChannel: ((YtItem) -> Unit)? = null, onClick: () -> Unit) {
    val radius = if (wide) 18.dp else 14.dp
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).then(if (wide) Modifier.padding(horizontal = 12.dp, vertical = 8.dp) else Modifier).testTag("youtube_card").feedCard("youtube", item.id)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(radius)).background(GlassFill)) {
            PosterImage(item.thumbnail, emptyMap(), Modifier.fillMaxSize(), Icons.Default.SmartDisplay)
            val badge = when { item.live -> "LIVE"; item.durationSec > 0 -> formatYtDuration(item.durationSec); else -> null }
            if (badge != null) Surface(
                shape = RoundedCornerShape(6.dp), color = if (item.live) Color(0xFFE53950) else Color.Black.copy(alpha = 0.78f),
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            ) { Text(badge, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)) }
        }
        val channelTap = if (onChannel != null && !item.channelUrl.isNullOrBlank()) Modifier.clickable(onClickLabel = "Open channel") { onChannel(item) } else Modifier
        Row(Modifier.padding(top = 10.dp, start = 2.dp, end = 2.dp)) {
            if (wide && showChannel) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(Brush.linearGradient(listOf(SpotifyGreen, AccentSecondary))).then(channelTap).testTag("youtube_card_avatar"), contentAlignment = Alignment.Center) {
                    if (item.channelAvatar != null) PosterImage(item.channelAvatar, emptyMap(), Modifier.fillMaxSize())
                    else Text(item.channel.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(item.title, color = TextPrimary, fontSize = if (wide) 15.sp else 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = if (wide) 20.sp else 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(
                    listOfNotNull(item.channel.takeIf { it.isNotBlank() && showChannel }, item.views.takeIf { it >= 0 }?.let { formatYtViews(it) }, item.uploaded).joinToString(" · "),
                    color = TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = if (showChannel) channelTap else Modifier,
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
internal fun SongRowSkeleton() {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        SkeletonBlock(54.dp, 54.dp, shape = RoundedCornerShape(12.dp))
        Spacer(Modifier.width(12.dp))
        Column { SkeletonBlock(180.dp, 14.dp); Spacer(Modifier.height(6.dp)); SkeletonBlock(110.dp, 12.dp) }
    }
}

@Composable
internal fun YtProblem(message: String, onRetry: (() -> Unit)?) {
    Surface(shape = RoundedCornerShape(18.dp), color = GlassFill, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.WifiOff, null, tint = TextSecondary)
            Spacer(Modifier.width(12.dp))
            Text(message, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
            if (onRetry != null) TextButton(onClick = onRetry) { Text("Retry", color = SpotifyGreen) }
        }
    }
}
