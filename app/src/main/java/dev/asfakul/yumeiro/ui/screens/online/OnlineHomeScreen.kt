/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.VideoLibrary
import dev.asfakul.yumeiro.ui.components.ModeOption
import dev.asfakul.yumeiro.ui.components.ModeSwitcher

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
import androidx.compose.material.icons.filled.FilterList
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
import dev.asfakul.yumeiro.ui.screens.extras.tourTarget
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
import kotlinx.coroutines.async
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
    var showFilter by rememberSaveable { mutableStateOf(false) }
    // One seed per visit: "More to explore" is shuffled, but stays put while you scroll.
    val shuffleSeed = rememberSaveable { System.nanoTime() }
    val homes = remember { mutableStateMapOf<String, ProviderHome>() }
    var refreshing by remember { mutableStateOf(false) }
    var feedLimit by rememberSaveable { mutableStateOf(24) }
    // Staged loading: at most 3 extensions fetch their home at once (each one already fans out
    // over its rows). Loading every extension at full parallelism is what starved the device and
    // crashed "For you" with many extensions enabled.
    val gate = remember { Semaphore(3) }

    // One fetch per extension at a time. Extensions finish loading one by one at start-up, and
    // every new engine snapshot used to restart the whole feed effect: in-flight homes were
    // cancelled and fetched again, so rows popped in, vanished and came back ("abnormal" loading).
    // Fetches now run in the screen's scope and survive those restarts; a name is only fetched
    // again when forced (pull to refresh, Retry) or after it failed.
    val inflight = remember { HashMap<String, kotlinx.coroutines.Deferred<Unit>>() }
    suspend fun loadOne(api: com.lagradost.cloudstream3.MainAPI, force: Boolean) {
        val name = api.name
        if (!force && homes[name] is ProviderHome.Ready) return
        inflight[name]?.takeIf { it.isActive }?.let { running -> if (!force) { running.await(); return } else running.cancel() }
        val job = scope.async {
            if (homes[name] !is ProviderHome.Ready) homes[name] = ProviderHome.Loading
            val result = gate.withPermit {
                runCatching { OnlineRepository.home(api, force) }
            }
            homes[name] = result.fold({ ProviderHome.Ready(it) }, { ProviderHome.Failed(OnlineErrors.friendly(it, "Couldn't load")) })
        }
        inflight[name] = job
        runCatching { job.await() }
    }

    suspend fun loadAll(force: Boolean) = supervisorScope {
        // The selected channel (if any) goes first so its page fills before the rest of the mix.
        val ordered = providers.filter { runCatching { it.hasMainPage }.getOrDefault(false) }.sortedBy { if (it.name == selected) 0 else 1 }
        ordered.map { api -> launch { runCatching { loadOne(api, force) } } }.joinAll()
    }
    val providerNames = remember(providers) { providers.map { it.name }.toSet() }
    LaunchedEffect(providerNames) {
        homes.keys.retainAll(providerNames)
        inflight.keys.filter { it !in providerNames }.forEach { inflight.remove(it)?.cancel() }
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
        // Search lives in the top bar (one magnifier per tab); this slim strip only says what feeds the
        // page and keeps the market / extension manager one tap away.
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                dev.asfakul.yumeiro.ui.components.Kana.overline("拡張", if (providers.isEmpty()) (if (engine.installed.isEmpty()) "No extensions yet" else "All extensions off") else "${providers.size} extension${if (providers.size == 1) "" else "s"} on", dev.asfakul.yumeiro.ui.components.kanaEnabled()).orEmpty(),
                style = dev.asfakul.yumeiro.ui.theme.OverlineStyle, color = TextTertiary, maxLines = 1,
                modifier = Modifier.weight(1f).clickable(onClickLabel = "Manage extensions", onClick = onOpenExtensions).padding(vertical = 12.dp),
            )
            Row(Modifier.tourTarget("yumix_sources")) {
            IconButton(onClick = onOpenMarket, modifier = Modifier.testTag("online_market")) {
                Icon(Icons.Default.Storefront, "Extension market", tint = TextPrimary)
            }
            IconButton(onClick = onOpenExtensions, modifier = Modifier.testTag("online_extensions")) {
                val errors = engine.installed.count { engine.info(it.key).error != null }
                BadgedBox(badge = { if (errors > 0) Badge { Text("$errors") } }) {
                    Icon(Icons.Default.Extension, "Extensions", tint = TextPrimary)
                }
            }
            // Round 10: one filter button replaces the row of round extension shortcuts. It opens a
            // sheet listing every extension that's on; a dot shows when the feed is narrowed to one.
            if (providers.size > 1) IconButton(onClick = { showFilter = true }, modifier = Modifier.testTag("online_filter")) {
                BadgedBox(badge = { if (selected != null) Badge(containerColor = dev.asfakul.yumeiro.ui.theme.LocalMtPalette.current.accent) }) {
                    Icon(Icons.Default.FilterList, if (selected != null) "Filter: $selected" else "Filter by extension", tint = if (selected != null) dev.asfakul.yumeiro.ui.theme.LocalMtPalette.current.accent else TextPrimary)
                }
            }
            }
        }
        if (showFilter) SourceFilterSheet(
            names = channelNames, selected = selected,
            status = { n -> when (val h = homes[n]) { is ProviderHome.Ready -> SourceStatus.Ready(h.rows.sumOf { it.items.size }); is ProviderHome.Failed -> SourceStatus.Failed; else -> SourceStatus.Loading } },
            onPick = { selected = it; showFilter = false },
            onDismiss = { showFilter = false },
        )

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
                // Round 8: "All" ⇄ extension page used to be a bare if/else (one-frame cut). It now
                // slides in the direction of the tapped channel and Back returns to "All".
                androidx.activity.compose.BackHandler(enabled = selected != null) { selected = null }
                dev.asfakul.yumeiro.ui.components.TabSwap(selected, { n -> if (n == null) -1 else channelNames.indexOf(n) }, Modifier.fillMaxSize(), label = "yumix_channel") { sel ->
                val listState = dev.asfakul.yumeiro.ui.components.ScrollMemory.list("online_home:" + (sel ?: ""))
                if (sel == null) {
                    val hero = remember(ready) { heroPicks(ready.mapNotNull { (_, rows) -> rows.firstOrNull { it.items.isNotEmpty() }?.items }) }
                    // Round 10 order: Resume → banners (spotlight + new episodes) → Continue → Fresh
                    // from your sources → categories → Latest episodes → per-extension → endless mix.
                    val latestAll = remember(ready) { YumixFeed.latest(ready, 30) }
                    val banner = remember(hero, latestAll) { YumixFeed.bannerSlides(hero, latestAll) }
                    val resume = remember(continueItems) { continueItems.firstOrNull { it.fraction < 0.92f } }
                    val bannerKeys = remember(banner) { banner.first.map { it.key }.toSet() }
                    val latest = remember(latestAll, bannerKeys) { latestAll.filter { it.key !in bannerKeys } }
                    // "Fresh from your sources": one mixed row across every extension (round-robin, de-duplicated).
                    val picks = remember(ready, bannerKeys) {
                        buildMixFeed(ready, 24, cardsPerShelf = Int.MAX_VALUE, skip = bannerKeys)
                            .filterIsInstance<FeedEntry.Card>().map { it.item }
                    }
                    val cats = remember(ready) { YumixFeed.categories(ready) }
                    var cat by rememberSaveable { mutableStateOf<String?>(null) }
                    // Then one tidy section per extension: its first rows as shelves. More sections
                    // appear as you scroll, so many extensions never load a wall of rows at once.
                    var groupLimit by rememberSaveable { mutableStateOf(4) }
                    val groups = remember(ready, groupLimit) { mixGroups(ready, groupLimit) }
                    // Endless "More to explore": every row of every extension, round-robin, then the
                    // next pages of those rows as you scroll (like a streaming app's bottomless home).
                    val explore = remember { androidx.compose.runtime.mutableStateListOf<HueRowFeed>() }
                    LaunchedEffect(ready) {
                        val have = explore.mapTo(HashSet()) { it.row.api + "|" + it.row.title + "|" + it.row.pageData }
                        ready.forEach { (_, rows) ->
                            rows.filter { it.items.isNotEmpty() }.forEach { r ->
                                if (have.add(r.api + "|" + r.title + "|" + r.pageData)) explore += HueRowFeed(r, r.items, 1, r.hasNext && r.pageData != null)
                            }
                        }
                        explore.removeAll { f -> ready.none { it.first == f.row.api } }
                    }
                    val shownAbove = remember(banner, picks, groups) {
                        HashSet<String>().apply { banner.first.forEach { add(it.key) }; picks.forEach { add(it.key) }; groups.forEach { g -> g.rows.forEach { r -> r.items.forEach { add(it.key) } } } }
                    }
                    val exploreItems by remember(shownAbove) { derivedStateOf { YumixFeed.blockShuffle(hueFeed(explore.toList(), null).filter { it.first.key !in shownAbove }, shuffleSeed) { it.first.key } } }
                    var exploreLimit by rememberSaveable { mutableStateOf(30) }
                    val exploreBusy by remember { derivedStateOf { explore.any { it.loading } } }
                    val exploreMore by remember { derivedStateOf { explore.any { it.hasNext } } }
                    // Round 8: the endless grid used to wait until *every* extension had answered
                    // (loadingNames.isEmpty()). One slow or hung extension therefore stopped "All" at
                    // a loading skeleton forever. Explore now starts as soon as the sections for the
                    // extensions that did answer are shown; late extensions join the grid when ready.
                    val groupsDone = groups.size >= ready.size && ready.isNotEmpty()
                    fun pageExplore() {
                        val targets = explore.indices.filter { explore[it].hasNext && !explore[it].loading }.sortedBy { explore[it].items.size }.take(3)
                        if (targets.isEmpty()) return
                        targets.forEach { explore[it] = explore[it].copy(loading = true) }
                        targets.forEach { i ->
                            scope.launch {
                                val f = explore.getOrNull(i) ?: return@launch
                                val api = providers.firstOrNull { it.name == f.row.api }
                                val res = if (api == null) null else runCatching { OnlineRepository.page(api, f.row.pageName.orEmpty(), f.row.pageData.orEmpty(), f.page + 1) }.getOrNull()
                                val cur = explore.getOrNull(i)?.takeIf { it.row == f.row } ?: return@launch
                                val known = cur.items.mapTo(HashSet()) { it.key }
                                val fresh = res?.items.orEmpty().filter { known.add(it.key) }
                                explore[i] = cur.copy(items = cur.items + fresh, page = cur.page + 1, hasNext = res?.hasNext == true && fresh.isNotEmpty(), loading = false)
                            }
                        }
                    }
                    val nearEnd by remember { derivedStateOf { val info = listState.layoutInfo; val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0; info.totalItemsCount > 0 && last >= info.totalItemsCount - 6 } }
                    LaunchedEffect(nearEnd, groups.size, ready.size, exploreItems.size, exploreBusy) {
                        if (!nearEnd) return@LaunchedEffect
                        when {
                            groups.size < ready.size -> groupLimit += 3
                            exploreLimit < exploreItems.size -> exploreLimit += 30
                            !exploreBusy && exploreMore -> pageExplore()
                        }
                    }
                    LazyColumn(Modifier.fillMaxSize().testTag("online_home_list"), state = listState, contentPadding = PaddingValues(bottom = 160.dp)) {
                        if (banner.first.isNotEmpty() || resume != null) item("hero") {
                            HeroCarousel(banner.first, onPlay = openItem, onInfo = openInfo, resume = resume, onResume = { onOpenTitle(it.toItem()) }, newKeys = banner.second)
                        } else if (loadingNames.isNotEmpty()) item("hero_skel") {
                            dev.asfakul.yumeiro.ui.components.SkeletonBlock(null, HERO_HEIGHT, Modifier.padding(horizontal = 20.dp), RoundedCornerShape(24.dp))
                        }
                        if (continueItems.isNotEmpty()) item("continue") {
                            ContinueRow(continueItems, onClick = { onOpenTitle(it) }, onSeeAll = onOpenHistory)
                        }
                        val mine = bookmarks.filter { b -> providers.any { it.name == b.api } }.distinctBy { it.api + "|" + it.url }
                        if (mine.isNotEmpty()) item("mylist") {
                            PosterShelf("My List", null, mine.map { it.toItem() }, horizontal = false, onClick = { onOpenTitle(it) }, onSeeAll = onOpenLibrary)
                        }
                        if (picks.isNotEmpty()) item("feed_label") {
                            PosterShelf("Fresh from your sources", "Mixed from ${ready.size} of ${providers.size} extensions", picks, horizontal = false, onClick = openInfo, onSeeAll = null)
                        }
                        if (cats.isNotEmpty()) item("categories") {
                            CategoryShelf(cats, cat, onPick = { cat = it }, onClick = openInfo)
                        }
                        if (latest.isNotEmpty()) item("latest") {
                            PosterShelf("Latest episodes", "New from ${ready.count { (_, rows) -> rows.any { YumixFeed.isLatestRow(it.title) } }} extension(s)", latest, horizontal = false, onClick = openInfo, onSeeAll = null)
                        }
                        groups.forEach { g ->
                            item("grp_h_" + g.name, contentType = "group_header") { ChannelSectionHeader(g.name, g.titles) { selected = g.name } }
                            g.rows.forEachIndexed { i, row ->
                                item("grp_${g.name}_${i}_${row.title}", contentType = "shelf") {
                                    PosterShelf(
                                        row.title, subtitle = null, items = row.items, horizontal = row.horizontal,
                                        onClick = openInfo, onSeeAll = if (row.pageData != null) ({ onOpenRow(row) }) else ({ selected = g.name }),
                                    )
                                }
                            }
                        }
                        if (groupsDone && exploreItems.isNotEmpty()) {
                            item("explore_h") {
                                dev.asfakul.yumeiro.ui.components.YuSection("探索", "More to explore", "From all ${ready.size} extensions", modifier = Modifier.padding(horizontal = 16.dp).testTag("online_explore"))
                            }
                            val rowsG = hueGridRows(exploreItems.take(exploreLimit))
                            items(rowsG, key = { r -> "ex_" + r.first().first.key }, contentType = { r -> if (r.first().second) "wide" else "tall" }) { r ->
                                val wide = r.first().second
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("explore_grid_row"), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    r.forEach { (item, _) -> PosterCard(item, onClick = { openInfo(item) }, horizontal = wide, modifier = Modifier.weight(1f)) }
                                    repeat((if (wide) 2 else 3) - r.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                            if (exploreBusy || exploreLimit < exploreItems.size) item("explore_more") { PosterGridSkeleton(1) }
                            else if (!exploreMore) item("explore_end") {
                                Text("You've reached the end. Pull down to refresh.", color = TextTertiary, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp))
                            }
                        }
                        if (loadingNames.isNotEmpty()) item("feed_loading") {
                            Column { SectionHeader("Loading ${loadingNames.size} more extension${if (loadingNames.size == 1) "" else "s"}…"); PosterGridSkeleton() }
                        }
                        val failed = providers.filter { homes[it.name] is ProviderHome.Failed }
                        if (failed.isNotEmpty()) item("feed_failed") {
                            ProviderProblem(
                                if (failed.size == 1) failed[0].name else "${failed.size} extensions didn't load",
                                failed.joinToString(" · ") { it.name }.take(160),
                            ) { scope.launch { supervisorScope { failed.forEach { api -> launch { loadOne(api, true) } } } } }
                        }
                        val searchOnly = providers.filter { !runCatching { it.hasMainPage }.getOrDefault(false) }
                        if (searchOnly.isNotEmpty()) item("search_only") {
                            SearchOnlyCard(searchOnly.joinToString(", ") { it.name }, onOpenSearch)
                        }
                    }
                } else {
                    val name: String = sel
                    val api = providers.firstOrNull { it.name == name }
                    val h = homes[name]
                    HuePage(
                        name = name, api = api,
                        rows = (h as? ProviderHome.Ready)?.rows,
                        loading = h == null || h is ProviderHome.Loading,
                        error = (h as? ProviderHome.Failed)?.message,
                        listState = listState,
                        strip = { ActiveFilterBar(name, onChange = { showFilter = true }, onClear = { selected = null }) },
                        onSearch = onOpenSearch,
                        onRetry = { if (api != null) scope.launch { loadOne(api, true) } },
                        openItem = openItem, openInfo = openInfo,
                    )
                }
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
enum class VideoSource(val label: String, val tag: String) {
    ONLINE("Yumix", "online"), YOUTUBE("YouTube", "youtube"), DEVICE("Device", "device"), LIVE("Live TV", "live");

    companion object {
        /** Yumix when a network is up, device videos when offline. */
        fun defaultFor(online: Boolean): VideoSource = if (online) ONLINE else DEVICE
    }
}

/** The Videos source menu: Yumix · YouTube · Device · Live TV, each with its live status. */
fun videoSourceOptions(extensionCount: Int, deviceCount: Int, online: Boolean): List<dev.asfakul.yumeiro.ui.components.SourceOption> =
    VideoSource.entries.map { v ->
        when (v) {
            VideoSource.ONLINE -> dev.asfakul.yumeiro.ui.components.SourceOption(
                v.tag, "Yumix", "Anime, movies and shows from your extensions",
                when { extensionCount == 0 -> "Add sources"; !online -> "Needs internet"; else -> "$extensionCount sources" },
                dev.asfakul.yumeiro.ui.components.YumixLogo.Icon, "video_source_" + v.tag, attention = extensionCount == 0 || !online,
            )
            VideoSource.YOUTUBE -> dev.asfakul.yumeiro.ui.components.SourceOption(
                v.tag, "YouTube", "Watch YouTube without signing in",
                if (online) "No login" else "Needs internet", Icons.Default.SmartDisplay, "video_source_" + v.tag, attention = !online,
            )
            VideoSource.DEVICE -> dev.asfakul.yumeiro.ui.components.SourceOption(
                v.tag, "Device", "Videos and folders on this phone",
                if (deviceCount == 1) "1 file" else "$deviceCount files", Icons.Default.VideoLibrary, "video_source_" + v.tag,
            )
            VideoSource.LIVE -> dev.asfakul.yumeiro.ui.components.SourceOption(
                v.tag, "Live TV", "IPTV channels from M3U playlists",
                if (online) "IPTV · M3U" else "Needs internet", Icons.Default.LiveTv, "video_source_" + v.tag, attention = !online,
            )
        }
    }

/** The four Videos modes (Yumix · YouTube · Device · Live TV) as mode cards. */
@Composable
fun VideoSourceSwitch(
    source: VideoSource,
    onChange: (VideoSource) -> Unit,
    modifier: Modifier = Modifier,
    extensionCount: Int = 0,
    deviceCount: Int = 0,
) {
    ModeSwitcher(
        options = VideoSource.entries.map { v ->
            when (v) {
                VideoSource.ONLINE -> ModeOption(v.tag, "Yumix", if (extensionCount == 0) "Add sources" else "$extensionCount sources", dev.asfakul.yumeiro.ui.components.YumixLogo.Icon, "video_source_" + v.tag)
                VideoSource.YOUTUBE -> ModeOption(v.tag, "YouTube", "No login", Icons.Default.SmartDisplay, "video_source_" + v.tag)
                VideoSource.DEVICE -> ModeOption(v.tag, "Device", "$deviceCount files", Icons.Default.VideoLibrary, "video_source_" + v.tag)
                VideoSource.LIVE -> ModeOption(v.tag, "Live TV", "IPTV · M3U", Icons.Default.LiveTv, "video_source_" + v.tag)
            }
        },
        selectedKey = source.tag,
        onSelect = { k -> VideoSource.entries.firstOrNull { it.tag == k }?.let(onChange) },
        modifier = modifier,
        testTag = "video_source_switch",
    )
}
