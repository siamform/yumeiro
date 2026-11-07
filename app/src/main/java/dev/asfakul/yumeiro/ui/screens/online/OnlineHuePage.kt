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
internal data class HueRowFeed(val row: OnlineRow, val items: List<OnlineItem>, val page: Int, val hasNext: Boolean, val loading: Boolean = false)

/**
 * Round-robin merge of a Hue's playlists into one YouTube-style feed (row0[0], row1[0], row0[1]…),
 * de-duplicated by title key. [only] restricts the feed to one playlist (the "tabs" at the top).
 */
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
) {
    val scope = rememberCoroutineScope()
    val feeds = remember(name, rows) {
        mutableStateListOf<HueRowFeed>().apply {
            rows.orEmpty().filter { it.items.isNotEmpty() }.forEach { add(HueRowFeed(it, it.items, 1, it.hasNext && it.pageData != null)) }
        }
    }
    var only by remember(name) { mutableStateOf<Int?>(null) }
    val feed by remember(feeds, only) { derivedStateOf { hueFeed(feeds.toList(), only) } }
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
                val known = f.items.mapTo(HashSet()) { it.key }
                val fresh = res?.items.orEmpty().filter { known.add(it.key) }
                feeds[i] = f.copy(items = f.items + fresh, page = f.page + 1, hasNext = res?.hasNext == true && fresh.isNotEmpty(), loading = false)
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
    LaunchedEffect(nearEnd, feed.size, only, pagingBusy) { if (nearEnd && !pagingBusy && canLoadMore) loadMore() }

    LazyColumn(Modifier.fillMaxSize().testTag("online_home_list"), state = listState, contentPadding = PaddingValues(bottom = 160.dp)) {
        item("channels") { strip() }
        item("channel_header") { ChannelHeader(name, feeds.size, feed.size, onSearch) }
        when {
            api != null && !runCatching { api.hasMainPage }.getOrDefault(false) -> item("nomain") { HueNotice("$name is search-only — use search to find titles") }
            rows == null && loading -> items(3, key = { "load_$it" }) { FeedCardSkeleton() }
            error != null -> item("fail") { HueNotice("$name didn't load: $error", "Retry", onRetry) }
            feeds.isEmpty() -> item("empty") { HueNotice("Nothing to show on $name right now") }
            else -> {
                if (feeds.size > 1) item("tabs") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("hue_tabs")) {
                        item("all") { OnlineChip("All", only == null, { only = null }) }
                        items(feeds.size, key = { "tab_$it" }) { i -> OnlineChip(feeds[i].row.title, only == i, { only = if (only == i) null else i }) }
                    }
                }
                if (only == null) {
                    val hero = heroPicks(listOf(feeds.first().items), max = 6)
                    if (hero.isNotEmpty()) item("hero") { HeroCarousel(hero, onPlay = openItem, onInfo = openInfo) }
                }
                items(feed, key = { "hf_" + it.first.key }, contentType = { "card" }) { (item, horizontal) ->
                    FeedCard(item, horizontal, onClick = { openInfo(item) }, onChannel = {})
                }
                if (pagingBusy) items(2, key = { "more_$it" }) { FeedCardSkeleton() }
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
private fun HueNotice(message: String, action: String? = null, onAction: (() -> Unit)? = null) {
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(18.dp), color = dev.asfakul.yumeiro.ui.theme.GlassFill,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("online_provider_error"),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, color = dev.asfakul.yumeiro.ui.theme.TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
            if (action != null && onAction != null) androidx.compose.material3.TextButton(onClick = onAction) { Text(action, color = dev.asfakul.yumeiro.ui.theme.SpotifyGreen) }
        }
    }
}
