/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private sealed interface ProviderHome {
    data object Loading : ProviderHome
    data class Ready(val rows: List<OnlineRow>) : ProviderHome
    /** [fix]: B-32 diagnosis of an empty home (browser check, moved site, out-of-date extension…). */
    data class Failed(val message: String, val fix: dev.asfakul.yumeiro.online.EmptyHome.Diagnosis? = null) : ProviderHome
}

/**
 * R15: the Yumix home lives as long as the app process, not as long as the pager page. The main
 * pager disposes pages it isn't showing (beyondViewportPageCount = 0), which threw away every
 * loaded home and every "More to explore" page: coming back reloaded the feed from the copy saved
 * on the phone (up to 6 h old) and the scroll ended where those saved pages ended. Fetches also
 * run here, so leaving the tab no longer cancels them half-way (rows stuck "loading").
 */
/** One line about every extension's section state, for logs and device tests. */
internal fun yumixDebugSummary(): String = YumixMemory.homes.entries.joinToString(", ") { (k, v) ->
    k + "=" + when (v) { is ProviderHome.Ready -> "Ready(${v.rows.size} rows, ${v.rows.sumOf { it.items.size }} items)"; is ProviderHome.Failed -> "Failed"; ProviderHome.Loading -> "Loading" }
}.ifEmpty { "(no sections)" } + " · explore ${YumixMemory.exploreShown.size}"

/** Keys of every title Yumix holds right now (sections and "More to explore"), for device tests. */
internal fun yumixItemKeys(): Set<String> = HashSet<String>().apply {
    YumixMemory.homes.values.forEach { h -> (h as? ProviderHome.Ready)?.rows?.forEach { r -> r.items.forEach { add(it.key) } } }
    YumixMemory.exploreShown.forEach { add(it.first.key) }
}

private object YumixMemory {
    val homes = androidx.compose.runtime.mutableStateMapOf<String, ProviderHome>()
    val inflight = HashMap<String, kotlinx.coroutines.Deferred<Boolean>>()
    val explore = androidx.compose.runtime.mutableStateListOf<HueRowFeed>()
    /** What "More to explore" shows; append-only (see YumixFeed.appendNew). */
    val exploreShown = androidx.compose.runtime.mutableStateListOf<Pair<OnlineItem, Boolean>>()
    val exploreSeen = HashSet<String>()
    val seed = System.nanoTime()
    /**
     * R16 G4: bumped when pull to refresh starts the feed over. A page that was in flight then
     * belongs to the old feed and is dropped instead of landing on a row of the new one (its page
     * number would be wrong, so a page would be skipped and the next one repeated).
     */
    @Volatile var generation = 0
    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)
    fun resetExplore() { generation++; explore.clear(); exploreShown.clear(); exploreSeen.clear() }

    /** Adds rows that aren't in the explore feed yet; drops rows of extensions that are gone. */
    fun syncExplore(ready: List<Pair<String, List<OnlineRow>>>) {
        val have = explore.mapTo(HashSet()) { it.row.api + "|" + it.row.title + "|" + it.row.pageData }
        ready.forEach { (_, rows) ->
            rows.filter { it.items.isNotEmpty() }.forEach { r ->
                if (have.add(r.api + "|" + r.title + "|" + r.pageData)) explore += HueRowFeed(r, r.items, 1, r.hasNext && r.pageData != null)
            }
        }
        explore.removeAll { f -> ready.none { it.first == f.row.api } }
        // R16 G4: titles already shown from an extension that is now off (or an 18+ one that is
        // now hidden) used to stay in "More to explore" until pull to refresh. The user turned it
        // off, so they go now; they may come back if it is turned on again.
        val gone = YumixFeed.goneFrom(exploreShown.map { it.first }, ready.mapTo(HashSet()) { it.first })
        if (gone.isNotEmpty()) { exploreShown.removeAll { it.first.key in gone }; exploreSeen.removeAll(gone) }
    }
}

/**
 * R15: merges a background refresh into rows already on screen: existing rows keep their order
 * and items (new titles are appended), new rows go at the end. Nothing the user is looking at
 * is replaced.
 */
internal fun mergeRows(old: List<OnlineRow>, fresh: List<OnlineRow>): List<OnlineRow> {
    val byKey = fresh.associateBy { it.title + "|" + it.pageData }
    val seenRows = HashSet<String>()
    val merged = old.map { o ->
        val k = o.title + "|" + o.pageData
        seenRows += k
        val n = byKey[k] ?: return@map o
        val keys = o.items.mapTo(HashSet()) { it.key }
        o.copy(items = o.items + n.items.filter { keys.add(it.key) }, hasNext = o.hasNext || n.hasNext)
    }
    return merged + fresh.filter { seenRows.add(it.title + "|" + it.pageData) }
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
    onOpenUpcoming: (() -> Unit)? = null,
) {
    androidx.compose.runtime.SideEffect { dev.asfakul.yumeiro.diagnostics.DrawCounter.hit("yumix") } // FREEZE-4
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine by ExtensionManager.state.collectAsState()
    LaunchedEffect(Unit) { ExtensionManager.start(context) }

    val adultOn by dev.asfakul.yumeiro.online.OnlineSettings.adult.collectAsState()
    val adultPrivate by dev.asfakul.yumeiro.online.OnlineSettings.adultPrivate.collectAsState()
    // 18+ Hues stay out of the feed when 18+ is off or kept private (they are still searchable).
    val providers = remember(engine.generation, engine.usable, adultOn, adultPrivate) {
        ExtensionManager.feedProviders(adultOn, adultPrivate)
    }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var showFilter by rememberSaveable { mutableStateOf(false) }
    // One seed per visit: "More to explore" is shuffled, but stays put while you scroll.
    val shuffleSeed = YumixMemory.seed
    val homes = YumixMemory.homes
    var refreshing by remember { mutableStateOf(false) }
    var feedLimit by rememberSaveable { mutableStateOf(24) }
    // Staged loading: at most 3 extensions fetch their home at once (each one already fans out
    // over its rows). Loading every extension at full parallelism is what starved the device and
    // crashed "For you" with many extensions enabled.
    val gate = remember { Semaphore(3) }
    // FE-2: same three slots, served by priority. The picked extension moves to the front even after loading began.
    val pgate = remember { dev.asfakul.yumeiro.online.PriorityGate(3) { dev.asfakul.yumeiro.online.PlayingNow.backgroundLimit(3) } }
    // FE-P: when playback stops, waiting background fetches get their slots back.
    LaunchedEffect(pgate) { dev.asfakul.yumeiro.online.PlayingNow.active.collect { pgate.poke() } }
    val prioritized = dev.asfakul.yumeiro.FeatureFlags.isOn(dev.asfakul.yumeiro.FeatureFlags.Flag.FE_PRIORITY_GATE)
    suspend fun <T> slot(name: String, block: suspend () -> T): T =
        if (prioritized) pgate.run(name, if (name == selected) dev.asfakul.yumeiro.online.PriorityGate.PICKED else dev.asfakul.yumeiro.online.PriorityGate.BACKGROUND, block)
        else gate.withPermit { block() }

    // One fetch per extension at a time. Extensions finish loading one by one at start-up, and
    // every new engine snapshot used to restart the whole feed effect: in-flight homes were
    // cancelled and fetched again, so rows popped in, vanished and came back ("abnormal" loading).
    // Fetches now run in the screen's scope and survive those restarts; a name is only fetched
    // again when forced (pull to refresh, Retry) or after it failed.
    val inflight = YumixMemory.inflight
    // FE-4b: only the working set fetches live; [waitingLive] are shown from their saved page (or not yet) until
    // the feed is scrolled to its end and the set grows (FeedWorkingSet.grow).
    val workingSetOn = dev.asfakul.yumeiro.FeatureFlags.isOn(dev.asfakul.yumeiro.FeatureFlags.Flag.FE4_WORKING_SET)
    var workingLimit by rememberSaveable { mutableStateOf(dev.asfakul.yumeiro.online.FeedWorkingSet.initial(dev.asfakul.yumeiro.DeviceClass.lowEnd)) }
    val waitingLive = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    /** R15 / FE-1: an old saved copy is on screen; fetch the live page and merge it in. */
    fun refreshStale(api: com.lagradost.cloudstream3.MainAPI) {
        val name = api.name
        YumixMemory.scope.launch {
            val live = slot(name) { runCatching { OnlineRepository.home(api, force = true) }.getOrNull() }
            val cur = homes[name] as? ProviderHome.Ready
            if (!live.isNullOrEmpty() && cur != null) homes[name] = ProviderHome.Ready(mergeRows(cur.rows, live))
        }
    }
    /** Loads one extension's home; true when it brought titles that were not on screen yet. [live] false = saved page only. */
    suspend fun loadOne(api: com.lagradost.cloudstream3.MainAPI, force: Boolean, live: Boolean = true): Boolean {
        val name = api.name
        if (!live) {
            // FE-4b: outside the working set: the saved page if there is one, no slot, no live refresh yet.
            if (homes[name] !is ProviderHome.Ready) {
                val saved = runCatching { OnlineRepository.feedHome(api) }.getOrNull()
                if (!saved.isNullOrEmpty()) homes[name] = ProviderHome.Ready(saved)
            }
            waitingLive[name] = true
            return false
        }
        waitingLive.remove(name)
        if (!force && homes[name] is ProviderHome.Ready) {
            // FE-4b: shown from its saved page while outside the working set; now inside, refresh it live.
            if (workingSetOn && OnlineRepository.takeStaleHome(name)) refreshStale(api)
            return false
        }
        inflight[name]?.takeIf { it.isActive }?.let { running -> if (!force) { return runCatching { running.await() }.getOrDefault(false) } else running.cancel() }
        val job = YumixMemory.scope.async {
            if (homes[name] !is ProviderHome.Ready) homes[name] = ProviderHome.Loading
            // SPEED-1: a saved page shows at once; only live fetches wait for one of the three slots.
            // FE-1: an old saved page too (refreshed live below via takeStaleHome).
            val cached = if (force) null else runCatching { OnlineRepository.feedHome(api) }.getOrNull()
            // FE-3: the extension shows at its first row; the slower rows fill in as they answer.
            val firstRows = dev.asfakul.yumeiro.FeatureFlags.isOn(dev.asfakul.yumeiro.FeatureFlags.Flag.FE3_FIRST_ROW)
            val before = homes[name] as? ProviderHome.Ready
            val onRows: ((List<OnlineRow>) -> Unit)? = if (!firstRows) null else { soFar ->
                if (isActive) YumixFeed.homeAfterFetch(before?.rows, soFar)?.let { homes[name] = ProviderHome.Ready(it) }
            }
            val result = if (cached != null) Result.success(cached) else slot(name) {
                // FE-2c: its row calls keep the lane in the shared extension-call limit too.
                val lane = if (name == selected) dev.asfakul.yumeiro.online.PriorityGate.PICKED else dev.asfakul.yumeiro.online.PriorityGate.BACKGROUND
                runCatching { kotlinx.coroutines.withContext(dev.asfakul.yumeiro.online.PriorityGate.Lane(lane)) { OnlineRepository.home(api, force, onRows) } }
            }
            // R16 G4: a fetch cancelled by a newer one (Retry, pull to refresh) used to fall
            // through here with the CancellationException as its "error" and briefly put
            // "Couldn't load" over the newer fetch's loading state. The newer fetch owns the section.
            if (!isActive) return@async false
            // FE-3: rows shown early by onRows are part of this fetch, so compare with what was there before it.
            val prev = if (firstRows) before?.rows else (homes[name] as? ProviderHome.Ready)?.rows
            val fresh = result.getOrNull()
            // A failed or empty answer keeps what is on screen (see YumixFeed.homeAfterFetch).
            val rows = YumixFeed.homeAfterFetch(prev, fresh)
            // B-32: an empty answer with no error used to say only "Nothing came back from this
            // extension"; the site is checked once and the real reason is shown.
            val diagnosis = if (rows == null && result.exceptionOrNull() == null && runCatching { api.hasMainPage }.getOrDefault(false))
                dev.asfakul.yumeiro.online.EmptyHome.probe(api, dev.asfakul.yumeiro.network.NetworkClient.get()) else null
            if (!isActive) return@async false
            homes[name] = if (rows != null) ProviderHome.Ready(rows)
                else ProviderHome.Failed(diagnosis?.message ?: result.exceptionOrNull()?.let { OnlineErrors.friendly(it, "Couldn't load") } ?: "Nothing came back from this extension", diagnosis)
            // R15: an old saved copy was shown; fetch the live page now and merge it in.
            if (!force && !fresh.isNullOrEmpty() && OnlineRepository.takeStaleHome(name)) refreshStale(api)
            YumixFeed.broughtNew(prev, fresh)
        }
        inflight[name] = job
        return runCatching { job.await() }.getOrDefault(false)
    }

    /** Returns how many extensions answered with titles that were not shown yet. */
    suspend fun loadAll(force: Boolean): Int = supervisorScope {
        // The selected channel (if any) goes first so its page fills before the rest of the mix.
        val withHome = providers.filter { runCatching { it.hasMainPage }.getOrDefault(false) }
        // E4: then the fastest measured on this phone; quarantined (failing) extensions wait for the rest.
        val ordered = if (dev.asfakul.yumeiro.FeatureFlags.isOn(dev.asfakul.yumeiro.FeatureFlags.Flag.E4_SPEED_ORDER))
            dev.asfakul.yumeiro.online.ExtensionSpeed.order(withHome, { it.name }, selected)
            else withHome.sortedBy { if (it.name == selected) 0 else 1 }
        val liveNow = if (!workingSetOn) null else dev.asfakul.yumeiro.online.FeedWorkingSet.live(
            ordered.map { it.name }, workingLimit, selected, dev.asfakul.yumeiro.online.PinnedExtensions.pins.value)
        ordered.map { api -> async { runCatching { loadOne(api, force, live = liveNow == null || api.name in liveNow) }.getOrDefault(false) } }.awaitAll().count { it }
    }
    // FE-4b: the working set grew (feed scrolled to its end): the next extensions go live.
    LaunchedEffect(workingLimit) { if (workingSetOn && workingLimit > dev.asfakul.yumeiro.online.FeedWorkingSet.initial(dev.asfakul.yumeiro.DeviceClass.lowEnd)) loadAll(false) }
    val providerNames = remember(providers) { providers.map { it.name }.toSet() }
    LaunchedEffect(providerNames) {
        // The extension engine starts with no providers; don't wipe the kept home before it's ready.
        if (engine.ready) {
            val dropped = homes.keys.filter { it !in providerNames }
            if (dropped.isNotEmpty()) android.util.Log.i("YuYumix", "sections dropped (extension gone): $dropped")
            homes.keys.retainAll(providerNames)
        }
        inflight.keys.filter { it !in providerNames }.forEach { inflight.remove(it)?.cancel() }
        waitingLive.keys.retainAll(providerNames)
        if (selected != null && providers.none { it.name == selected }) selected = null
        loadAll(false)
    }
    // FE-2: picking an extension moves its waiting fetch to the front; one that failed or never started loads now.
    LaunchedEffect(selected) {
        val pick = selected ?: return@LaunchedEffect
        if (!prioritized) return@LaunchedEffect
        pgate.bump(pick, dev.asfakul.yumeiro.online.PriorityGate.PICKED)
        val api = providers.firstOrNull { it.name == pick } ?: return@LaunchedEffect
        if ((homes[pick] !is ProviderHome.Ready || waitingLive.containsKey(pick)) && inflight[pick]?.isActive != true) runCatching { loadOne(api, false) }
    }

    LaunchedEffect(Unit) { OnlineHistory.ensureLoaded(context) }
    val historyAll by OnlineHistory.entries.collectAsState()
    val history = remember(historyAll, adultOn, adultPrivate) { OnlineHistory.forFeed(historyAll, adultOn, adultPrivate) }
    val continueItems = remember(history, providers) { history.filter { h -> providers.any { it.name == h.api } }.distinctBy { it.key } }
    LaunchedEffect(Unit) { dev.asfakul.yumeiro.online.OnlineBookmarks.ensureLoaded(context) }
    val bookmarks by dev.asfakul.yumeiro.online.OnlineBookmarks.items.collectAsState()
    val channelNames = remember(providers) { providers.map { it.name } }
    val withHome = remember(providers) { providers.filter { runCatching { it.hasMainPage }.getOrDefault(false) }.map { it.name } }
    // FE-4b: extensions waiting outside the working set are not "loading" (they go live when the feed reaches its end).
    val loadingNames = YumixFeed.stillLoading(withHome) { n -> waitingLive.containsKey(n) || homes[n].let { it != null && it !is ProviderHome.Loading } }
    val openItem: (OnlineItem) -> Unit = { OnlineRepository.previews[it.key] = it; onOpenTitle(it) }
    val openInfo: (OnlineItem) -> Unit = { OnlineRepository.previews[it.key] = it; onOpenInfo(it) }

    // Round 13: the extension bar slides away while scrolling down and comes back on a small
    // scroll up (like YouTube's bottom navigation), so the feed gets the whole screen.
    val barHide = remember { dev.asfakul.yumeiro.ui.components.HideOnScroll() }
    Column(modifier.fillMaxSize().nestedScroll(barHide).testTag("online_home")) {
        // Search lives in the top bar (one magnifier per tab); this slim strip only says what feeds the
        // page and keeps the market / extension manager one tap away.
        androidx.compose.animation.AnimatedVisibility(
            visible = barHide.visible || showFilter,
            enter = androidx.compose.animation.expandVertically(expandFrom = Alignment.Top) + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically(shrinkTowards = Alignment.Top) + androidx.compose.animation.fadeOut(),
        ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 2.dp).testTag("online_extension_bar"), verticalAlignment = Alignment.CenterVertically) {
            Text(
                dev.asfakul.yumeiro.ui.components.Kana.overline("拡張", if (providers.isEmpty()) (if (engine.installed.isEmpty()) "No extensions yet" else "All extensions off") else "${providers.size} source${if (providers.size == 1) "" else "s"} on", dev.asfakul.yumeiro.ui.components.kanaEnabled()).orEmpty(),
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
        }
        // R15 Phase 5: favourite extensions come first in "Show videos from" (only those that are on).
        // PIN-1 (2.1.0, owner's master list): the extra row of pinned chips on the feed is gone; pins
        // order the filter sheet and the feed, the sheet is the one place to pick an extension.
        val pinsAll by dev.asfakul.yumeiro.online.PinnedExtensions.pins.collectAsState()
        val pins = remember(pinsAll, channelNames) { pinsAll.filter { it in channelNames } }
        remember { dev.asfakul.yumeiro.online.PinnedExtensions.init(context); true }
        // F-21: language chip, source count, ecosystem and repo per row; built once per provider set.
        val labels = remember(providers, showFilter) { if (showFilter && dev.asfakul.yumeiro.FeatureFlags.on(context, dev.asfakul.yumeiro.FeatureFlags.Flag.EXTENSION_LABELS)) providers.associate { it.name to dev.asfakul.yumeiro.online.ExtensionLabels.forApi(it) } else emptyMap() }
        val sourceOf = remember(providers, showFilter) { if (showFilter) providers.associate { it.name to dev.asfakul.yumeiro.sources.SourceHub.ecosystemOf(it).label } else emptyMap() }
        if (showFilter) SourceFilterSheet(
            names = channelNames, selected = selected,
            pins = pins, details = labels::get,
            // YF-2 (owner list 10): source chips from each provider's own ecosystem (CloudStream, Vega, SkyStream…).
            group = sourceOf::get,
            onTogglePin = { n ->
                if (!dev.asfakul.yumeiro.online.PinnedExtensions.toggle(context, n))
                    dev.asfakul.yumeiro.ui.components.AppMessages.show(context, "You can pin up to ${dev.asfakul.yumeiro.online.PinnedExtensions.MAX} extensions. Unpin one first", dev.asfakul.yumeiro.ui.components.AppMessages.Kind.WARNING)
            },
            status = { n -> when (val h = homes[n]) { is ProviderHome.Ready -> SourceStatus.Ready(h.rows.sumOf { it.items.size }); is ProviderHome.Failed -> SourceStatus.Failed; else -> SourceStatus.Loading } },
            onPick = { selected = it; showFilter = false },
            onDismiss = { showFilter = false },
        )

        // GATE-1-VIDEOS-LOWRAM: the strip above shows in the page's first frame, the feed one frame later
        // (api33-lowram composed both in one 790 ms message, run 44939433464).
        val feedNextFrame = remember { dev.asfakul.yumeiro.FeatureFlags.isOn(dev.asfakul.yumeiro.FeatureFlags.Flag.TAB_NEXT_FRAME) }
        dev.asfakul.yumeiro.ui.components.NextFramePage(feedNextFrame) {
        when {
            !engine.ready && providers.isEmpty() -> LazyColumn(Modifier.fillMaxSize()) { items(3) { PosterGridSkeleton(); Spacer(Modifier.height(12.dp)) } }
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
                onRefresh = { scope.launch {
                    refreshing = true
                    // X9-8: logged so a device run can tell "pull never fired" from "refresh slow".
                    val t0 = android.os.SystemClock.uptimeMillis()
                    android.util.Log.i("YuYumix", "pull to refresh: start (${providers.size} extensions)")
                    val answered = loadAll(true); refreshing = false
                    android.util.Log.i("YuYumix", "pull to refresh: done in ${android.os.SystemClock.uptimeMillis() - t0} ms, $answered answered")
                    // Pull to refresh is the one place the feed starts over (the user asked for it).
                    // R16 G4: only when something actually came back. Offline, the refresh used to
                    // empty every section and "More to explore" and leave nothing to scroll.
                    val readyNow = { providers.mapNotNull { p -> (homes[p.name] as? ProviderHome.Ready)?.let { p.name to it.rows } } }
                    when (YumixFeed.afterRefresh(answered, dev.asfakul.yumeiro.network.Connectivity.isOnline(context))) {
                        YumixFeed.AfterRefresh.START_OVER -> { YumixMemory.resetExplore(); YumixMemory.syncExplore(readyNow()) }
                        YumixFeed.AfterRefresh.NOTHING_NEW -> dev.asfakul.yumeiro.ui.components.AppMessages.show(
                            context, "Nothing new yet. Showing what was already loaded.", dev.asfakul.yumeiro.ui.components.AppMessages.Kind.INFO)
                        YumixFeed.AfterRefresh.OFFLINE -> {
                            // X9-8: a first fill from a saved copy is added, never a start-over.
                            if (answered > 0) YumixMemory.syncExplore(readyNow())
                            dev.asfakul.yumeiro.ui.components.AppMessages.show(
                                context, "Couldn't refresh: you're offline. Showing what was already loaded.", dev.asfakul.yumeiro.ui.components.AppMessages.Kind.INFO)
                        }
                    }
                } },
                modifier = Modifier.fillMaxSize(),
            ) {
                // FE-4: the mix is rebuilt per frame (first update at once, then at most every 300 ms), not per row answer.
                val framed = remember { dev.asfakul.yumeiro.FeatureFlags.isOn(dev.asfakul.yumeiro.FeatureFlags.Flag.FE4_FEED_FRAMES) }
                val readyNow0 = { providers.mapNotNull { p -> (homes[p.name] as? ProviderHome.Ready)?.let { p.name to it.rows } } }
                val ready = if (!framed) readyNow0() else {
                    val r by androidx.compose.runtime.produceState(readyNow0(), providers) {
                        with(dev.asfakul.yumeiro.online.FeedFrames) {
                            androidx.compose.runtime.snapshotFlow { readyNow0() }.atMostEvery().collect { value = it }
                        }
                    }
                    r
                }
                // Round 8: "All" ⇄ extension page used to be a bare if/else (one-frame cut). It now
                // slides in the direction of the tapped channel and Back returns to "All".
                androidx.activity.compose.BackHandler(enabled = selected != null) { selected = null }
                dev.asfakul.yumeiro.ui.components.TabSwap(selected, { n -> if (n == null) -1 else channelNames.indexOf(n) }, Modifier.fillMaxSize(), label = "yumix_channel") { sel ->
                val listState = dev.asfakul.yumeiro.ui.components.ScrollMemory.list("online_home:" + (sel ?: ""))
                if (sel == null) {
                    // DEDUP-1 (owner list 14): one card per title across extensions on "All"; an extension's own page is unfiltered.
                    val feedReady = remember(ready) { dev.asfakul.yumeiro.online.TitleDedupe.acrossExtensions(ready) }
                    val hero = remember(feedReady) { heroPicks(feedReady.mapNotNull { (_, rows) -> rows.firstOrNull { it.items.isNotEmpty() }?.items }) }
                    // Round 10 order: Resume → banners (spotlight + new episodes) → Continue → Fresh
                    // from your sources → Latest episodes (categories are the chip bar on top) → per-extension → endless mix.
                    val latestAll = remember(feedReady) { YumixFeed.latest(feedReady, 30) }
                    val banner = remember(hero, latestAll) { YumixFeed.bannerSlides(hero, latestAll) }
                    val resume = remember(continueItems) { continueItems.firstOrNull { it.fraction < 0.92f } }
                    val bannerKeys = remember(banner) { banner.first.map { it.key }.toSet() }
                    val latest = remember(latestAll, bannerKeys) { latestAll.filter { it.key !in bannerKeys } }
                    // "Fresh from your sources": one mixed row across every extension (round-robin, de-duplicated).
                    val picks = remember(feedReady, bannerKeys) {
                        buildMixFeed(feedReady, 24, cardsPerShelf = Int.MAX_VALUE, skip = bannerKeys)
                            .filterIsInstance<FeedEntry.Card>().map { it.item }
                    }
                    val upcomingOn = remember { dev.asfakul.yumeiro.FeatureFlags.on(context, dev.asfakul.yumeiro.FeatureFlags.Flag.UPCOMING) }
                    val tagSections = remember { dev.asfakul.yumeiro.FeatureFlags.on(context, dev.asfakul.yumeiro.FeatureFlags.Flag.YUMIX_TAG_SECTIONS) }
                    val cats = remember(feedReady) { YumixFeed.categories(feedReady, byTags = tagSections) }
                    var cat by rememberSaveable { mutableStateOf<String?>(null) }
                    androidx.activity.compose.BackHandler(enabled = cat != null) { cat = null }
                    // Then one tidy section per extension: its first rows as shelves. More sections
                    // appear as you scroll, so many extensions never load a wall of rows at once.
                    var groupLimit by rememberSaveable { mutableStateOf(4) }
                    val groups = remember(feedReady, groupLimit) { mixGroups(feedReady, groupLimit) }
                    // Endless "More to explore": every row of every extension, round-robin, then the
                    // next pages of those rows as you scroll (like a streaming app's bottomless home).
                    val explore = YumixMemory.explore
                    LaunchedEffect(ready) { YumixMemory.syncExplore(ready) }
                    val shownAbove = remember(banner, picks, groups) {
                        HashSet<String>().apply { banner.first.forEach { add(it.key) }; picks.forEach { add(it.key) }; groups.forEach { g -> g.rows.forEach { r -> r.items.forEach { add(it.key) } } } }
                    }
                    // DEDUP-1: the titles of every card above, so "More to explore" doesn't bring another extension's copy.
                    val shownAboveTitles = remember(feedReady) { feedReady.flatMap { (_, rows) -> rows.flatMap { it.items } } }
                    // R15: append-only. The old derived list re-merged and re-shuffled everything on each
                    // page, so cards already on screen moved, and cards vanished when a section above grew.
                    val exploreItems = YumixMemory.exploreShown
                    val latestShownAbove = androidx.compose.runtime.rememberUpdatedState(shownAbove)
                    val latestTitlesAbove = androidx.compose.runtime.rememberUpdatedState(shownAboveTitles)
                    LaunchedEffect(Unit) {
                        androidx.compose.runtime.snapshotFlow { explore.toList() }.collect { snap ->
                            val skip = latestShownAbove.value
                            val above = latestTitlesAbove.value
                            val shownNow = exploreItems.toList()
                            val tail = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                val candidates = dev.asfakul.yumeiro.online.TitleDedupe.exploreCandidates(hueFeed(snap, null), above, shownNow.map { it.first }) { it.first }
                                YumixFeed.appendNew(candidates, YumixMemory.exploreSeen, skip, shuffleSeed) { it.first.key }
                            }
                            if (tail.isNotEmpty()) exploreItems.addAll(tail)
                        }
                    }
                    var exploreLimit by rememberSaveable { mutableStateOf(30) }
                    val exploreBusy by remember { derivedStateOf { explore.any { it.loading } } }
                    val exploreMore by remember { derivedStateOf { explore.any { it.hasNext } } }
                    // Round 8: the endless grid used to wait until *every* extension had answered
                    // (loadingNames.isEmpty()). One slow or hung extension therefore stopped "All" at
                    // a loading skeleton forever. Explore now starts as soon as the sections for the
                    // extensions that did answer are shown; late extensions join the grid when ready.
                    val groupsDone = YumixFeed.sectionsDone(groups.size, feedReady)
                    fun pageExplore() {
                        val targets = explore.indices.filter { explore[it].hasNext && !explore[it].loading }.sortedBy { explore[it].items.size }.take(3)
                        if (targets.isEmpty()) return
                        targets.forEach { explore[it] = explore[it].copy(loading = true) }
                        val gen = YumixMemory.generation
                        targets.forEach { i ->
                            YumixMemory.scope.launch {
                                val f = explore.getOrNull(i) ?: return@launch
                                val api = providers.firstOrNull { it.name == f.row.api }
                                val res = if (api == null) null else runCatching { OnlineRepository.page(api, f.row.pageName.orEmpty(), f.row.pageData.orEmpty(), f.page + 1) }.getOrNull()
                                if (res == null) kotlinx.coroutines.delay(HueRowFeed.retryDelayMs(f.fails + 1))
                                // A refresh started the feed over while this page loaded: drop it.
                                if (gen != YumixMemory.generation) return@launch
                                // Index can move when rows are added/removed: find the row again.
                                val j = explore.indexOfFirst { it.row == f.row }.takeIf { it >= 0 } ?: return@launch
                                explore[j] = explore[j].afterPage(res)
                            }
                        }
                    }
                    val stagedOn = remember { dev.asfakul.yumeiro.FeatureFlags.isOn(dev.asfakul.yumeiro.FeatureFlags.Flag.FEED_STAGED_REVEAL) }
                    val staged = dev.asfakul.yumeiro.ui.components.rememberStagedCount(stagedOn, listState)
                    val lateOn = remember { dev.asfakul.yumeiro.FeatureFlags.isOn(dev.asfakul.yumeiro.FeatureFlags.Flag.FEED_STAGED_LATE) }
                    val late = dev.asfakul.yumeiro.ui.components.rememberStagedLate(lateOn)
                    val nearEnd by remember { derivedStateOf { val info = listState.layoutInfo; val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0; info.totalItemsCount > 0 && last >= info.totalItemsCount - 6 && staged.intValue == dev.asfakul.yumeiro.ui.components.StagedReveal.ALL } }
                    LaunchedEffect(nearEnd, groups.size, ready.size, exploreItems.size, exploreBusy) {
                        if (!nearEnd) return@LaunchedEffect
                        when {
                            !groupsDone && groupLimit < feedReady.size -> groupLimit += 3
                            // FE-4b: every section shown; extensions outside the working set go live next.
                            workingSetOn && waitingLive.isNotEmpty() -> workingLimit = dev.asfakul.yumeiro.online.FeedWorkingSet.grow(workingLimit, waitingLive.size)
                            exploreLimit < exploreItems.size -> exploreLimit += 30
                            !exploreBusy && exploreMore -> pageExplore()
                        }
                    }
                    // Read the append-only list once, as an immutable copy (SnapshotStateList.toList()
                    // returns the record's persistent list). Iterating the live list inside the lazy
                    // content crashed with ConcurrentModificationException when prefetch ran the
                    // content in another snapshot while a page was being appended (run 43664241241).
                    val exploreSnap = exploreItems.toList()
                    val exploreRows = remember(exploreSnap, exploreLimit) { hueGridRows(exploreSnap.take(exploreLimit)) }
                    val catItems = remember(cats, cat) { cats.firstOrNull { it.first.name == cat }?.second }
                    // GATE-1-RENDER-2: sections join one per frame on open (StagedReveal), flag FEED_STAGED_REVEAL.
                    LazyColumn(Modifier.fillMaxSize().testTag("online_home_list"), state = listState, contentPadding = PaddingValues(bottom = 160.dp)) {
                        val st = dev.asfakul.yumeiro.ui.components.StagedList(this, staged.intValue, late)
                        if (cats.isNotEmpty()) st.item("categories") {
                            CategoryBar(cats, cat, onPick = { cat = it })
                        }
                        if (catItems != null) {
                            st.items(categoryGridRows(catItems), key = { r -> "cat_" + cat + "_" + r.first().key }) { r -> CategoryGridRow(r, openInfo) }
                        } else {
                        if (banner.first.isNotEmpty() || resume != null) st.item("hero") {
                            HeroCarousel(banner.first, onPlay = openItem, onInfo = openInfo, resume = resume, onResume = { onOpenTitle(it.toItem()) }, newKeys = banner.second)
                        } else if (loadingNames.isNotEmpty()) st.item("hero_skel") {
                            dev.asfakul.yumeiro.ui.components.SkeletonBlock(null, HERO_HEIGHT, Modifier.padding(horizontal = 20.dp), RoundedCornerShape(24.dp))
                        }
                        if (continueItems.isNotEmpty()) st.item("continue") {
                            ContinueRow(continueItems, onClick = { onOpenTitle(it) }, onSeeAll = onOpenHistory)
                        }
                        val saved = bookmarks.filter { b -> providers.any { it.name == b.api } }.distinctBy { it.api + "|" + it.url }
                        // F-09: My List holds listed titles; Favorites get their own shelf (a Favorite can be in both).
                        val mine = saved.filter { it.listed }
                        val favorites = saved.filter { it.favorite }
                        if (mine.isNotEmpty()) st.item("mylist") {
                            PosterShelf("My List", null, mine.map { it.toItem() }, horizontal = false, onClick = { onOpenTitle(it) }, onSeeAll = onOpenLibrary)
                        }
                        if (favorites.isNotEmpty()) st.item("favorites") {
                            PosterShelf("Favorites", null, favorites.map { it.toItem() }, horizontal = false, onClick = { onOpenTitle(it) }, onSeeAll = onOpenLibrary)
                        }
                        // F-08: one compact entry to the release calendar (kill switch FeatureFlags.UPCOMING).
                        if (onOpenUpcoming != null && upcomingOn) st.item("upcoming_entry") {
                            // UP-BANNER-1 (R21 item 16A): a rotating "Coming up" card; the plain entry when nothing is due.
                            UpcomingBanner(onOpenUpcoming, onSearch = { q -> OnlineSearchPending.query = q; onOpenSearch() })
                        }
                        if (picks.isNotEmpty()) st.item("feed_label") {
                            PosterShelf("Fresh from your sources", "Mixed from ${ready.size} of ${providers.size} sources", picks, horizontal = false, onClick = openInfo, onSeeAll = null)
                        }
                        if (latest.isNotEmpty()) st.item("latest") {
                            PosterShelf("Latest episodes", "New from ${ready.count { (_, rows) -> rows.any { YumixFeed.isLatestRow(it.title) } }} extension(s)", latest, horizontal = false, onClick = openInfo, onSeeAll = null)
                        }
                        groups.forEach { g ->
                            // Round 2, order 4: no "extension name + View" header between sections; the
                            // shelf's small subtitle says which extension it is from.
                            g.rows.forEachIndexed { i, row ->
                                st.item("grp_${g.name}_${i}_${row.title}", contentType = "shelf") {
                                    PosterShelf(
                                        row.title, subtitle = g.name, items = row.items, horizontal = row.horizontal,
                                        onClick = openInfo, onSeeAll = if (row.pageData != null) ({ onOpenRow(row) }) else ({ selected = g.name }),
                                    )
                                }
                            }
                        }
                        if (groupsDone && exploreSnap.isNotEmpty()) {
                            st.item("explore_h") {
                                dev.asfakul.yumeiro.ui.components.YuSection("探索", "More to explore", "From all ${ready.size} sources", modifier = Modifier.padding(horizontal = 16.dp).testTag("online_explore"))
                            }
                            st.items(exploreRows, key = { r -> "ex_" + r.first().first.key }, contentType = { r -> if (r.first().second) "wide" else "tall" }) { r ->
                                val wide = r.first().second
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("explore_grid_row"), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    r.forEach { (item, _) -> dev.asfakul.yumeiro.ui.feed.FeedSlot("ex_" + r.first().first.key) { PosterCard(item, onClick = { openInfo(item) }, horizontal = wide, modifier = Modifier.weight(1f)) } }
                                    repeat((if (wide) 2 else 3) - r.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                            if (exploreBusy || exploreLimit < exploreSnap.size) st.item("explore_more") { PosterGridSkeleton(1) }
                            else if (!exploreMore) st.item("explore_end") {
                                Text("You've reached the end. Pull down to refresh.", color = TextTertiary, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp))
                            }
                        }
                        if (loadingNames.isNotEmpty()) st.item("feed_loading") {
                            Column { SectionHeader("Loading ${loadingNames.size} more extension${if (loadingNames.size == 1) "" else "s"}…"); PosterGridSkeleton() }
                        }
                        val failed = providers.filter { homes[it.name] is ProviderHome.Failed }
                        if (failed.isNotEmpty()) st.item("feed_failed") {
                            ProviderProblem(
                                if (failed.size == 1) failed[0].name else "${failed.size} extensions didn't load",
                                failed.joinToString(" · ") { it.name }.take(160),
                            ) { scope.launch { supervisorScope { failed.forEach { api -> launch { loadOne(api, true) } } } } }
                        }
                        val searchOnly = providers.filter { !runCatching { it.hasMainPage }.getOrDefault(false) }
                        if (searchOnly.isNotEmpty()) st.item("search_only") {
                            SearchOnlyCard(YumixFeed.searchOnlyLabel(searchOnly.map { it.name }), onOpenSearch)
                        }
                        }
                    }
                } else {
                    val name: String = sel
                    val api = providers.firstOrNull { it.name == name }
                    val h = homes[name]
                    // B-32 (R19): the site works but this copy finds nothing → offer another repository's copy.
                    val nothingParsed = (h as? ProviderHome.Failed)?.fix?.kind == dev.asfakul.yumeiro.online.EmptyHome.Kind.NOTHING_PARSED
                    val marketState by dev.asfakul.yumeiro.online.ExtensionMarket.state.collectAsState()
                    LaunchedEffect(nothingParsed) {
                        if (nothingParsed && dev.asfakul.yumeiro.online.ExtensionMarket.catalog == null) dev.asfakul.yumeiro.online.ExtensionMarket.load(context)
                    }
                    val otherCopy = remember(nothingParsed, api, engine.installed, marketState) {
                        if (!nothingParsed || api == null) null
                        else ExtensionManager.extensionOf(api)?.let { mine ->
                            (marketState as? dev.asfakul.yumeiro.online.ExtensionMarket.State.Ready)?.catalog?.extensions
                                ?.let { dev.asfakul.yumeiro.online.OtherCopy.pick(mine, engine.installed, it) }
                        }
                    }
                    HuePage(
                        name = name, api = api,
                        rows = (h as? ProviderHome.Ready)?.rows,
                        loading = h == null || h is ProviderHome.Loading,
                        error = (h as? ProviderHome.Failed)?.message,
                        errorFix = (h as? ProviderHome.Failed)?.fix,
                        listState = listState,
                        strip = { ActiveFilterBar(name, onChange = { showFilter = true }, onClear = { selected = null }) },
                        onSearch = onOpenSearch,
                        onRetry = { if (api != null) scope.launch { loadOne(api, true) } },
                        openItem = openItem, openInfo = openInfo,
                        otherCopy = otherCopy,
                        onOpenRow = onOpenRow,
                        onTryCopy = { copy ->
                            scope.launch {
                                runCatching { ExtensionManager.install(context, copy) }
                                    .onSuccess { inst ->
                                        dev.asfakul.yumeiro.online.OnlineRepository.clearCaches()
                                        val err = ExtensionManager.state.value.info(inst.key).error
                                        // PIN-2 names the second copy "Name · Repo"; switch the page to it.
                                        val base = name.substringBefore(" · ")
                                        val next = ExtensionManager.providers().firstOrNull { p ->
                                            p.name != name && p.name.startsWith(base) && ExtensionManager.extensionOf(p)?.key == inst.key
                                        }
                                        when {
                                            err != null -> dev.asfakul.yumeiro.ui.components.AppMessages.error(context, "${inst.name} v${inst.version} installed but couldn't start: $err")
                                            next != null -> { selected = next.name; dev.asfakul.yumeiro.ui.components.AppMessages.success(context, "Showing ${next.name} (v${inst.version})") }
                                            else -> dev.asfakul.yumeiro.ui.components.AppMessages.success(context, "${inst.name} v${inst.version} from ${inst.repoName} installed")
                                        }
                                    }
                                    .onFailure { if (it !is dev.asfakul.yumeiro.online.ExtensionReview.Declined) dev.asfakul.yumeiro.ui.components.AppMessages.error(context, dev.asfakul.yumeiro.online.OnlineErrors.friendly(it)) }
                            }
                        },
                    )
                }
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
                Text(name, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
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

@Composable
internal fun UpcomingEntryCard(onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(16.dp))
            .background(dev.asfakul.yumeiro.ui.theme.GlassFill).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("online_upcoming_entry"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.DateRange, null, tint = dev.asfakul.yumeiro.ui.theme.SpotifyGreen)
        androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text("Upcoming", color = dev.asfakul.yumeiro.ui.theme.TextPrimary, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 15.sp)
            Text("Movies, series and anime in the next two weeks", color = dev.asfakul.yumeiro.ui.theme.TextSecondary, fontSize = 12.sp, maxLines = 1)
        }
        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = dev.asfakul.yumeiro.ui.theme.TextTertiary)
    }
}
