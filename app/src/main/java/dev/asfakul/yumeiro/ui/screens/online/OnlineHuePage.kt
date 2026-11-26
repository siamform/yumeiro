/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.MainAPI
import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.online.OnlineRepository
import dev.asfakul.yumeiro.online.OnlineRow
import dev.asfakul.yumeiro.ui.components.SkeletonBlock
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

/** One playlist (row) of a Hue with its own paging cursor. */
internal data class HueRowFeed(val row: OnlineRow, val items: List<OnlineItem>, val page: Int, val hasNext: Boolean, val loading: Boolean = false, val fails: Int = 0) {
    /**
     * R15: the next page came back. A failed or timed-out page used to set hasNext = false, so one
     * slow answer ended the endless feed for good ("it suddenly stops"). It now keeps the row open
     * and retries (up to [MAX_PAGE_FAILS] times in a row); an empty page ends the row as before.
     */
    fun afterPage(res: dev.asfakul.yumeiro.online.OnlinePage?): HueRowFeed {
        if (res == null) return copy(loading = false, fails = fails + 1, hasNext = fails + 1 < MAX_PAGE_FAILS)
        val known = items.mapTo(HashSet()) { it.key }
        val fresh = res.items.filter { known.add(it.key) }
        return copy(items = items + fresh, page = page + 1, hasNext = res.hasNext && fresh.isNotEmpty(), loading = false, fails = 0)
    }
    companion object {
        const val MAX_PAGE_FAILS = 3
        /** Wait before a failed row may be asked again (2 s, 4 s, …). */
        fun retryDelayMs(fails: Int): Long = (2_000L * fails).coerceAtMost(8_000L)
    }
}

/**
 * Round-robin merge of a Hue's playlists into one YouTube-style feed (row0[0], row1[0], row0[1]…),
 * de-duplicated by title key. [only] restricts the feed to one playlist (the "tabs" at the top).
 */
/** Chunks the channel feed into grid rows: consecutive portrait items by 3, landscape by 2. */
internal fun hueGridRows(feed: List<Pair<OnlineItem, Boolean>>): List<List<Pair<OnlineItem, Boolean>>> {
    val out = ArrayList<List<Pair<OnlineItem, Boolean>>>()
    var cur = ArrayList<Pair<OnlineItem, Boolean>>()
    for (e in feed) {
        val per = if (e.second) 2 else 3
        if (cur.isNotEmpty() && (cur.first().second != e.second || cur.size >= per)) { out += cur; cur = ArrayList() }
        cur += e
    }
    if (cur.isNotEmpty()) out += cur
    return out
}

/** EXT-SEC-1: posters per section shelf on an extension's home (the rest are behind "See all"). */
internal const val SHELF_ITEMS = 20

internal fun hueFeed(rows: List<HueRowFeed>, only: Int?): List<Pair<OnlineItem, Boolean>> {
    val src = if (only != null) listOfNotNull(rows.getOrNull(only)) else rows
    val out = ArrayList<Pair<OnlineItem, Boolean>>()
    val seen = HashSet<String>()
    val max = src.maxOfOrNull { it.items.size } ?: 0
    for (i in 0 until max) for (r in src) {
        val it = r.items.getOrNull(i) ?: continue
        if (seen.add(it.key)) out += it to r.row.horizontal
    }
    return out
}

/**
 * A single Hue (extension "channel") page: header, playlist tabs and an endless vertical feed of
 * big cards, like a YouTube channel. Instead of "See all", the next page of each playlist loads
 * automatically as you scroll.
 */
@Composable
internal fun HuePage(
    name: String,
    api: MainAPI?,
    rows: List<OnlineRow>?,
    loading: Boolean,
    error: String?,
    listState: LazyListState,
    strip: @Composable () -> Unit,
    onSearch: () -> Unit,
    onRetry: () -> Unit,
    openItem: (OnlineItem) -> Unit,
    openInfo: (OnlineItem) -> Unit,
    errorFix: dev.asfakul.yumeiro.online.EmptyHome.Diagnosis? = null,
    needsSetup: Boolean = false,
    /** B-32 (R19): another repository's copy of this extension, offered when its home parses nothing. */
    otherCopy: dev.asfakul.yumeiro.online.RemoteExtension? = null,
    onTryCopy: (dev.asfakul.yumeiro.online.RemoteExtension) -> Unit = {},
    /** EXT-SEC-1 (owner list R21, item 15): "See all" of one of this extension's sections (rows with paging). */
    onOpenRow: ((OnlineRow) -> Unit)? = null,
    /** GROUP G: change / clear the "Show videos from" pick from the page's own header. */
    onChangeFilter: (() -> Unit)? = null,
    onClearFilter: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    // R15: keyed by the channel only. A refreshed copy of [rows] (background refresh of a saved
    // page) used to rebuild this list, throwing away every page loaded while scrolling; new rows
    // are now merged in and loaded pages stay.
    val feeds = remember(name) {
        mutableStateListOf<HueRowFeed>().apply {
            rows.orEmpty().filter { it.items.isNotEmpty() }.forEach { add(HueRowFeed(it, it.items, 1, it.hasNext && it.pageData != null)) }
        }
    }
    LaunchedEffect(rows) {
        val have = feeds.mapTo(HashSet()) { it.row.title + "|" + it.row.pageData }
        rows.orEmpty().filter { it.items.isNotEmpty() && have.add(it.title + "|" + it.pageData) }
            .forEach { feeds += HueRowFeed(it, it.items, 1, it.hasNext && it.pageData != null) }
    }
    var only by remember(name) { mutableStateOf<Int?>(null) }
    // EXT-SEC-1: the extension opens on its own home: one shelf per section with "See all", like its site. The
    // endless mixed feed stays one tap away ("Everything"); a section tab still shows that section endlessly.
    var home by androidx.compose.runtime.saveable.rememberSaveable(name) { mutableStateOf(true) }
    // R15: the "all playlists" feed only ever appends (see YumixFeed.appendNew), so cards on screen
    // never shift when a shorter playlist gets its next page.
    val allSeen = remember(name) { HashSet<String>() }
    val allFeed = remember(name) { mutableStateListOf<Pair<OnlineItem, Boolean>>() }
    LaunchedEffect(name) {
        androidx.compose.runtime.snapshotFlow { feeds.toList() }.collect { snap ->
            val tail = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                YumixFeed.appendNew(hueFeed(snap, null), allSeen, emptySet(), 0L, shuffle = false) { it.first.key }
            }
            if (tail.isNotEmpty()) allFeed.addAll(tail)
        }
    }
    val fullFeed by remember(feeds, only) { derivedStateOf { if (only == null) allFeed.toList() else hueFeed(feeds.toList(), only) } }
    // Round 13: search inside this extension's section (it can hold more than 1000 titles).
    var query by androidx.compose.runtime.saveable.rememberSaveable(name) { mutableStateOf("") }
    val searched by androidx.compose.runtime.produceState(fullFeed, fullFeed, query) {
        value = if (query.isBlank()) fullFeed else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { filterByTitle(fullFeed, query) { it.first.name } }
    }
    val feed = if (query.isBlank()) fullFeed else searched
    val shelves = home && only == null && query.isBlank() && feeds.size > 1
    val pagingBusy by remember(feeds) { derivedStateOf { feeds.any { it.loading } } }
    val canLoadMore by remember(feeds, only) { derivedStateOf { (if (only != null) listOfNotNull(feeds.getOrNull(only!!)) else feeds).any { it.hasNext } } }

    fun loadMore() {
        val provider = api ?: return
        val targets = feeds.indices.filter { i -> (only == null || only == i) && feeds[i].hasNext && !feeds[i].loading }
            // Fetch the shortest playlists first so the round-robin feed keeps flowing; max 2 at once.
            .sortedBy { feeds[it].items.size }.take(2)
        if (targets.isEmpty()) return
        targets.forEach { feeds[it] = feeds[it].copy(loading = true) }
        scope.launch {
            targets.map { i ->
                async {
                    val f = feeds[i]
                    val res = runCatching { OnlineRepository.page(provider, f.row.pageName.orEmpty(), f.row.pageData.orEmpty(), f.page + 1) }.getOrNull()
                    i to res
                }
            }.awaitAll().forEach { (i, res) ->
                val f = feeds.getOrNull(i) ?: return@forEach
                if (res == null) {
                    // Keep "loading" for a moment so the scroll trigger doesn't hammer a failing row.
                    launch { kotlinx.coroutines.delay(HueRowFeed.retryDelayMs(f.fails + 1)); feeds.getOrNull(i)?.takeIf { it.row == f.row }?.let { feeds[i] = it.afterPage(null) } }
                } else feeds[i] = f.afterPage(res)
            }
        }
    }

    val nearEnd by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 5
        }
    }
    LaunchedEffect(nearEnd, feed.size, only, pagingBusy, shelves) { if (!shelves && nearEnd && !pagingBusy && canLoadMore) loadMore() }
    // While searching, keep fetching pages (one at a time) so titles further down are found too.
    LaunchedEffect(query, pagingBusy, fullFeed.size) {
        if (query.isNotBlank() && !pagingBusy && canLoadMore && fullFeed.size < SECTION_SEARCH_CAP) { kotlinx.coroutines.delay(250); loadMore() }
    }

    LazyColumn(Modifier.fillMaxSize().testTag("online_home_list"), state = listState, contentPadding = PaddingValues(bottom = 160.dp)) {
        item("channels") { strip() }
        item("channel_header") { ChannelHeader(name, feeds.size, fullFeed.size, onChangeFilter, onClearFilter) }
        // GROUP G2: the page's one search box (the top bar's magnifier hides while one extension is picked).
        if (feeds.isNotEmpty() || (api != null && !runCatching { api.hasMainPage }.getOrDefault(false))) item("section_search") {
            dev.asfakul.yumeiro.ui.screens.watch.WatchSearchField(
                query = query, onQuery = { query = it.take(80) },
                hint = if (feeds.isEmpty()) "Search $name" else "Search ${fullFeed.size}${if (canLoadMore) "+" else ""} titles on $name",
                tag = "hue_section_search", modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                // Enter searches the whole site when the loaded titles have no match (or nothing is loaded).
                onDone = { if (query.isNotBlank() && (feeds.isEmpty() || feed.isEmpty())) { OnlineSearchPending.query = query.trim(); onSearch() } },
            )
        }
        if (query.isNotBlank()) item("section_search_state") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${feed.size} match${if (feed.size == 1) "" else "es"} in ${fullFeed.size} loaded" + if (pagingBusy) " · looking further…" else "",
                    color = TextTertiary, fontSize = 12.sp, modifier = Modifier.weight(1f).testTag("hue_section_search_count"),
                )
                androidx.compose.material3.TextButton(onClick = { OnlineSearchPending.query = query.trim(); onSearch() }, modifier = Modifier.testTag("hue_search_everywhere")) { Text("Search all sources", color = dev.asfakul.yumeiro.ui.theme.LocalMtPalette.current.accent, fontSize = 12.sp) }
            }
        }
        when {
            api != null && !runCatching { api.hasMainPage }.getOrDefault(false) -> item("nomain") { HueNotice("$name is search-only — use search to find titles") }
            rows == null && loading -> item("load") { PosterGridSkeleton(3) }
            needsSetup && api != null -> item("setup") {
                // EXT-SETUP-1 (A3): not a failure; its own settings make it work.
                val ctx = androidx.compose.ui.platform.LocalContext.current
                HueNotice("$name needs setting up before it shows anything (a server, sign-in or sections to show)", "Set up", {
                    if (!dev.asfakul.yumeiro.online.ExtensionSetup.open(ctx, api)) dev.asfakul.yumeiro.ui.components.AppMessages.error(ctx, "$name's settings couldn't open. Try updating the extension.")
                }, "Retry", onRetry)
            }
            error != null -> item("fail") {
                // B-32: the diagnosis brings its own fix next to Retry.
                val fix = errorFix
                val second: Pair<String, () -> Unit>? = when (fix?.kind) {
                    dev.asfakul.yumeiro.online.EmptyHome.Kind.BROWSER_CHECK -> "Open site" to {
                        // B4: this ran only the silent check, which cannot pass a site that wants a tap (that is why
                        // the home failed), so nothing opened and Retry met the same check. It opens the site's check
                        // page now; once passed, the extension reloads by itself (VerificationCenter.passed) and here.
                        val site = fix.site.let { if (it.startsWith("http")) it.trimEnd('/') + "/" else "https://$it/" }
                        dev.asfakul.yumeiro.online.web.BrowserCheck.openSite(site) { ok -> if (ok) onRetry() }
                    }
                    dev.asfakul.yumeiro.online.EmptyHome.Kind.MOVED -> if (api != null && fix.movedTo != null) ("Use new address" to {
                        dev.asfakul.yumeiro.online.ProviderOverrides.setMainUrl(api, fix.movedTo); onRetry()
                    }) else null
                    dev.asfakul.yumeiro.online.EmptyHome.Kind.NOTHING_PARSED ->
                        otherCopy?.let { c -> dev.asfakul.yumeiro.online.OtherCopy.label(c) to { onTryCopy(c) } } ?: ("Search" to onSearch)
                    else -> null
                }
                HueNotice("$name didn't load: $error", "Retry", onRetry, second?.first, second?.second)
            }
            feeds.isEmpty() -> item("empty") { HueNotice("Nothing to show on $name right now") }
            else -> {
                if (feeds.size > 1) item("tabs") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("hue_tabs")) {
                        item("home") { OnlineChip("Home", only == null && home, { only = null; home = true }, Modifier.testTag("hue_tab_home")) }
                        item("all") { OnlineChip("Everything", only == null && !home, { only = null; home = false }, Modifier.testTag("hue_tab_all")) }
                        items(feeds.size, key = { "tab_$it" }) { i -> OnlineChip(feeds[i].row.title, only == i, { if (only == i) { only = null; home = true } else { only = i; home = false } }, Modifier.testTag("hue_tab_$i")) }
                    }
                }
                if (shelves) {
                    items(feeds.size, key = { i -> "shelf_" + i + "_" + feeds[i].row.title }, contentType = { "shelf" }) { i ->
                        val f = feeds[i]
                        Box(Modifier.testTag("hue_shelf")) {
                            PosterShelf(
                                f.row.title, subtitle = null, items = f.items.take(SHELF_ITEMS), horizontal = f.row.horizontal, onClick = openInfo,
                                // Paged sections open the endless See all page; the rest show here as one section.
                                onSeeAll = if (onOpenRow != null && f.row.pageData != null) ({ onOpenRow(f.row) }) else ({ only = i; home = false }),
                            )
                        }
                    }
                    item("browse_all") {
                        androidx.compose.material3.OutlinedButton(
                            onClick = { home = false },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp).heightIn(min = 48.dp).testTag("hue_browse_all"),
                        ) { Text("Browse everything on $name", color = dev.asfakul.yumeiro.ui.theme.LocalMtPalette.current.accent) }
                    }
                    return@LazyColumn
                }
                // An organised poster grid (3 portrait / 2 landscape per row) instead of one huge card each.
                val gridRows = hueGridRows(feed)
                items(gridRows, key = { r -> "hg_" + r.first().first.key }, contentType = { r -> if (r.first().second) "wide" else "tall" }) { r ->
                    val wide = r.first().second
                    val per = if (wide) 2 else 3
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("hue_grid_row"), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        r.forEach { (item, _) -> dev.asfakul.yumeiro.ui.feed.FeedSlot("hg_" + r.first().first.key) { PosterCard(item, onClick = { openInfo(item) }, horizontal = wide, modifier = Modifier.weight(1f)) } }
                        repeat(per - r.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                if (pagingBusy) item("more") { PosterGridSkeleton() }
                else if (!canLoadMore) item("end") {
                    Text(
                        "You're all caught up on $name", color = TextTertiary, fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(24.dp).testTag("hue_feed_end"),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Skeleton with the shape of a [FeedCard]. */
@Composable
internal fun FeedCardSkeleton() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("feed_card_skeleton")) {
        Box(Modifier.fillMaxWidth().height(0.dp).padding(0.dp))
        SkeletonBlock(width = null, height = 196.dp, shape = RoundedCornerShape(22.dp))
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            SkeletonBlock(width = 36.dp, height = 36.dp, shape = CircleShape)
            Spacer(Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SkeletonBlock(width = 220.dp, height = 14.dp)
                SkeletonBlock(width = 120.dp, height = 11.dp)
            }
        }
    }
}

@Composable
private fun HueNotice(message: String, action: String? = null, onAction: (() -> Unit)? = null, action2: String? = null, onAction2: (() -> Unit)? = null) {
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(18.dp), color = dev.asfakul.yumeiro.ui.theme.GlassFill,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("online_provider_error"),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, color = dev.asfakul.yumeiro.ui.theme.TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
            if (action2 != null && onAction2 != null) androidx.compose.material3.TextButton(onClick = onAction2, modifier = Modifier.testTag("online_provider_fix")) { Text(action2, color = dev.asfakul.yumeiro.ui.theme.SpotifyGreen) }
            if (action != null && onAction != null) androidx.compose.material3.TextButton(onClick = onAction) { Text(action, color = dev.asfakul.yumeiro.ui.theme.SpotifyGreen) }
        }
    }
}
