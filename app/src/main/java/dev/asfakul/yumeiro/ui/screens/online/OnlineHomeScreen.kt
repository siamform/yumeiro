/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.online.OnlineErrors
import dev.asfakul.yumeiro.online.OnlineHistory
import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.online.OnlineRepository
import dev.asfakul.yumeiro.online.OnlineRow
import dev.asfakul.yumeiro.ui.components.StateMessage
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.OnAccent
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private sealed interface ProviderHome {
    data object Loading : ProviderHome
    data class Ready(val rows: List<OnlineRow>) : ProviderHome
    data class Failed(val message: String) : ProviderHome
}

/**
 * Videos → Online: popular / latest rows from every enabled extension, a global search entry and
 * access to the Extensions manager and market.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineHomeScreen(
    onOpenSearch: () -> Unit,
    onOpenTitle: (OnlineItem) -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenMarket: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenRow: (OnlineRow) -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenInfo: (OnlineItem) -> Unit = onOpenTitle,
    onOpenLibrary: () -> Unit = onOpenHistory,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine by ExtensionManager.state.collectAsState()
    LaunchedEffect(Unit) { ExtensionManager.start(context) }

    val adultOn by dev.asfakul.yumeiro.online.OnlineSettings.adult.collectAsState()
    val adultPrivate by dev.asfakul.yumeiro.online.OnlineSettings.adultPrivate.collectAsState()
    // 18+ Hues stay out of the feed when 18+ is off or kept private (they are still searchable).
    val providers = remember(engine.generation, engine.ready, adultOn, adultPrivate) {
        if (engine.ready) ExtensionManager.providers().filterNot { (!adultOn || adultPrivate) && dev.asfakul.yumeiro.online.AdultContent.isAdultApi(it.name) } else emptyList()
    }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val homes = remember { mutableStateMapOf<String, ProviderHome>() }
    var refreshing by remember { mutableStateOf(false) }
    var feedLimit by rememberSaveable { mutableStateOf(24) }
    // Staged loading: at most 3 extensions fetch their home at once (each one already fans out
    // over its rows). Loading every extension at full parallelism is what starved the device and
    // crashed "For you" with many extensions enabled.
    val gate = remember { Semaphore(3) }

    suspend fun loadOne(api: com.lagradost.cloudstream3.MainAPI, force: Boolean) {
        val name = api.name
        if (!force && homes[name] is ProviderHome.Ready) return
        if (homes[name] !is ProviderHome.Ready) homes[name] = ProviderHome.Loading
        val result = gate.withPermit {
            runCatching { OnlineRepository.home(api, force) }
        }
        homes[name] = result.fold({ ProviderHome.Ready(it) }, { ProviderHome.Failed(OnlineErrors.friendly(it, "Couldn't load")) })
    }

    suspend fun loadAll(force: Boolean) = supervisorScope {
        // The selected channel (if any) goes first so its page fills before the rest of the mix.
        val ordered = providers.filter { runCatching { it.hasMainPage }.getOrDefault(false) }.sortedBy { if (it.name == selected) 0 else 1 }
        ordered.map { api -> launch { runCatching { loadOne(api, force) } } }.joinAll()
    }
    LaunchedEffect(providers) {
        homes.keys.retainAll(providers.map { it.name }.toSet())
        if (selected != null && providers.none { it.name == selected }) selected = null
        loadAll(false)
    }

    LaunchedEffect(Unit) { OnlineHistory.ensureLoaded(context) }
    val historyAll by OnlineHistory.entries.collectAsState()
    val history = remember(historyAll, adultOn, adultPrivate) { OnlineHistory.forFeed(historyAll, adultOn, adultPrivate) }
    val continueItems = remember(history, providers) { history.filter { h -> providers.any { it.name == h.api } }.distinctBy { it.key } }
    LaunchedEffect(Unit) { dev.asfakul.yumeiro.online.OnlineBookmarks.ensureLoaded(context) }
    val bookmarks by dev.asfakul.yumeiro.online.OnlineBookmarks.items.collectAsState()
    val channelNames = remember(providers) { providers.map { it.name } }
    val loadingNames = providers.filter { homes[it.name] == null || homes[it.name] is ProviderHome.Loading }.map { it.name }.toSet()
    val openItem: (OnlineItem) -> Unit = { OnlineRepository.previews[it.key] = it; onOpenTitle(it) }
    val openInfo: (OnlineItem) -> Unit = { OnlineRepository.previews[it.key] = it; onOpenInfo(it) }

    Column(modifier.fillMaxSize().testTag("online_home")) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(24.dp), color = GlassFill,
                modifier = Modifier.weight(1f).height(48.dp).clickable(onClick = onOpenSearch).testTag("online_search_entry"),
            ) {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, null, tint = SpotifyGreen, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Search every Hue…", color = TextTertiary, fontSize = 13.sp)
                }
            }
            IconButton(onClick = onOpenMarket, modifier = Modifier.testTag("online_market")) {
                Icon(Icons.Default.Storefront, "Extension market", tint = TextPrimary)
            }
            IconButton(onClick = onOpenExtensions, modifier = Modifier.testTag("online_extensions")) {
                val errors = engine.installed.count { engine.info(it.key).error != null }
                BadgedBox(badge = { if (errors > 0) Badge { Text("$errors") } }) {
                    Icon(Icons.Default.Extension, "Extensions", tint = TextPrimary)
                }
            }
        }

        when {
            !engine.ready -> LazyColumn(Modifier.fillMaxSize()) { items(3) { PosterGridSkeleton(); Spacer(Modifier.height(12.dp)) } }
            engine.installed.isEmpty() && providers.isEmpty() -> GetStarted(onOpenMarket = onOpenMarket, onAddManually = onOpenExtensions)
            providers.isEmpty() -> StateMessage(
                icon = Icons.Default.Extension,
                title = "All extensions are turned off",
                message = "Turn an extension on to see its videos here.",
                primary = "Manage extensions" to onOpenExtensions,
                secondary = "Open market" to onOpenMarket,
                testTag = "online_empty",
            )
            else -> PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { scope.launch { refreshing = true; loadAll(true); refreshing = false } },
                modifier = Modifier.fillMaxSize(),
            ) {
                val ready = providers.mapNotNull { p -> (homes[p.name] as? ProviderHome.Ready)?.let { p.name to it.rows } }
                val listState = dev.asfakul.yumeiro.ui.components.ScrollMemory.list("online_home:" + (selected ?: ""))
                if (selected == null) {
                    val hero = remember(ready) { heroPicks(ready.mapNotNull { (_, rows) -> rows.firstOrNull { it.items.isNotEmpty() }?.items }) }
                    val feed = remember(ready, feedLimit, hero) { buildMixFeed(ready, feedLimit, skip = hero.map { it.key }.toSet()) }
                    // Infinite feed: grow the mix as the user nears the end.
                    val nearEnd by remember { derivedStateOf { val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0; last >= listState.layoutInfo.totalItemsCount - 4 } }
                    LaunchedEffect(nearEnd, feed.size) { if (nearEnd && feed.count { it is FeedEntry.Card } >= feedLimit) feedLimit += 16 }
                    LazyColumn(Modifier.fillMaxSize().testTag("online_home_list"), state = listState, contentPadding = PaddingValues(bottom = 160.dp)) {
                        if (providers.size > 1) item("channels") { ChannelStrip(channelNames, loadingNames, null) { selected = it } }
                        if (hero.isNotEmpty()) item("hero") {
                            HeroCarousel(hero, onPlay = openItem, onInfo = openInfo)
                        } else if (loadingNames.isNotEmpty()) item("hero_skel") {
                            dev.asfakul.yumeiro.ui.components.SkeletonBlock(null, 380.dp, Modifier.padding(horizontal = 16.dp), RoundedCornerShape(24.dp))
                        }
                        if (continueItems.isNotEmpty()) item("continue") {
                            ContinueRow(continueItems, onClick = { onOpenTitle(it) }, onSeeAll = onOpenHistory)
                        }
                        val mine = bookmarks.filter { b -> providers.any { it.name == b.api } }.distinctBy { it.api + "|" + it.url }
                        if (mine.isNotEmpty()) item("mylist") {
                            PosterShelf("My List", null, mine.map { it.toItem() }, horizontal = false, onClick = { onOpenTitle(it) }, onSeeAll = onOpenLibrary)
                        }
                        if (feed.isNotEmpty()) item("feed_label") {
                            FeedLabel("Spectrum", "mixed from ${ready.size} of ${providers.size} Hues")
                        }
                        items(feed, key = { it.key }, contentType = { if (it is FeedEntry.Card) "card" else "shelf" }) { e ->
                            when (e) {
                                is FeedEntry.Card -> FeedCard(e.item, e.horizontal, onClick = { openInfo(e.item) }, onChannel = { selected = e.item.api })
                                is FeedEntry.Shelf -> PosterShelf(
                                    e.row.title, subtitle = "Playlist · ${e.row.api}", items = e.row.items, horizontal = e.row.horizontal,
                                    onClick = openItem, onSeeAll = if (e.row.pageData != null) ({ onOpenRow(e.row) }) else null,
                                )
                            }
                        }
                        if (loadingNames.isNotEmpty()) item("feed_loading") {
                            Column { SectionHeader("Painting ${loadingNames.size} more Hue${if (loadingNames.size == 1) "" else "s"}…"); FeedCardSkeleton() }
                        }
                        val failed = providers.filter { homes[it.name] is ProviderHome.Failed }
                        if (failed.isNotEmpty()) item("feed_failed") {
                            ProviderProblem(
                                if (failed.size == 1) failed[0].name else "${failed.size} Hues didn't load",
                                failed.joinToString(" · ") { it.name }.take(160),
                            ) { scope.launch { supervisorScope { failed.forEach { api -> launch { loadOne(api, true) } } } } }
                        }
                        val searchOnly = providers.filter { !runCatching { it.hasMainPage }.getOrDefault(false) }
                        if (searchOnly.isNotEmpty()) item("search_only") {
                            SearchOnlyCard(searchOnly.joinToString(", ") { it.name }, onOpenSearch)
                        }
                    }
                } else {
                    val name = selected ?: return@PullToRefreshBox
                    val api = providers.firstOrNull { it.name == name }
                    val h = homes[name]
                    HuePage(
                        name = name, api = api,
                        rows = (h as? ProviderHome.Ready)?.rows,
                        loading = h == null || h is ProviderHome.Loading,
                        error = (h as? ProviderHome.Failed)?.message,
                        listState = listState,
                        strip = { ChannelStrip(channelNames, loadingNames, name) { selected = it } },
                        onSearch = onOpenSearch,
                        onRetry = { if (api != null) scope.launch { loadOne(api, true) } },
                        openItem = openItem, openInfo = openInfo,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderProblem(name: String, message: String, onRetry: (() -> Unit)?) {
    Surface(shape = RoundedCornerShape(18.dp), color = GlassFill, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("online_provider_error")) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CloudOff, null, tint = WarnColor, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(message, color = TextSecondary, fontSize = 12.sp)
            }
            if (onRetry != null) TextButton(onClick = onRetry) { Text("Retry", color = SpotifyGreen) }
        }
    }
}

@Composable
private fun SearchOnlyCard(name: String, onSearch: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = GlassFill, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { Icon(Icons.Default.Search, null, tint = SpotifyGreen) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("Search-only source — find titles with search", color = TextSecondary, fontSize = 12.sp)
            }
            TextButton(onClick = onSearch) { Text("Search", color = SpotifyGreen) }
        }
    }
}

/** Where the Videos tab gets its videos from. */
enum class VideoSource(val label: String, val tag: String) { ONLINE("Hues", "online"), YOUTUBE("YouTube", "youtube"), DEVICE("Device", "device") }

/** "Hues | YouTube | Device" switch shown at the top of the Videos tab. */
@Composable
fun VideoSourceSwitch(source: VideoSource, onChange: (VideoSource) -> Unit, modifier: Modifier = Modifier) {
    val brush = dev.asfakul.yumeiro.ui.theme.rememberSignatureBrush()
    Surface(shape = RoundedCornerShape(24.dp), color = GlassFill, modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().testTag("video_source_switch")) {
        Row(Modifier.padding(4.dp)) {
            VideoSource.entries.forEach { value ->
                val sel = value == source
                Box(
                    Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(20.dp))
                        .then(if (sel) Modifier.background(brush) else Modifier)
                        .clickable { onChange(value) }.testTag("video_source_" + value.tag),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(value.label, color = if (sel) OnAccent else TextSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}
