/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.lagradost.cloudstream3.MainAPI
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.online.OnlineRepository
import dev.asfakul.yumeiro.online.OnlineRow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The installed extensions' home rows, loaded once for Home (at most 3 extensions at a time,
 * through [OnlineRepository]'s cache, so the Yumix tab reuses the same pages). 18+ extensions are
 * left out when 18+ is off or private, exactly like the Yumix feed.
 */
@Stable
internal class ExtensionFeed {
    val homes = mutableStateMapOf<String, List<OnlineRow>>()
    var providers by mutableStateOf<List<MainAPI>>(emptyList())
    var loading by mutableStateOf(false)
    val names: List<String> get() = providers.map { it.name }
    /** R15: the order the extensions answered in. Sections only ever append below the ones shown. */
    val order = mutableStateListOf<String>()
    internal var loadedFor: List<String>? = null
    internal var gen = 0
    fun put(name: String, rows: List<OnlineRow>) {
        homes[name] = rows
        if (rows.isNotEmpty() && name !in order) order += name
    }
    /** Extension name → its rows, in the order they arrived, only those that loaded something. */
    val ready: List<Pair<String, List<OnlineRow>>> get() {
        val live = providers.mapTo(HashSet()) { it.name }
        return order.filter { it in live }.mapNotNull { n -> homes[n]?.takeIf { it.isNotEmpty() }?.let { n to it } }
    }
}

/**
 * R15 Phase 3 (ADR-008): Home's extension rows and its endless Yumix random page live for the
 * process. They were `remember {}` state: leaving Home and coming back rebuilt both, the rows came
 * back from the cache in a different order and the random page was re-dealt from them, so the
 * list restored at the old scroll spot showed other cards ("the screen shows saved data").
 */
internal object HomeMemory {
    val feed = ExtensionFeed()
    /** The Yumix random seed for this process; Shuffle replaces it. */
    var seed: Long = System.nanoTime()
    private var random: YumixRandom? = null
    fun random(seed: Long): YumixRandom = random?.takeIf { it.seed == seed } ?: YumixRandom(seed).also { random = it }
}

@Composable
internal fun rememberExtensionFeed(): ExtensionFeed {
    val context = LocalContext.current
    val engine by ExtensionManager.state.collectAsState()
    LaunchedEffect(Unit) { ExtensionManager.start(context) }
    val adultOn by dev.asfakul.yumeiro.online.OnlineSettings.adult.collectAsState()
    val adultPrivate by dev.asfakul.yumeiro.online.OnlineSettings.adultPrivate.collectAsState()
    val providers = remember(engine.generation, engine.usable, adultOn, adultPrivate) {
        // SPD-1: rows join as each extension finishes loading instead of after the last one.
        if (engine.usable) ExtensionManager.providers()
            .filter { runCatching { it.hasMainPage }.getOrDefault(false) }
            .filterNot { (!adultOn || adultPrivate) && dev.asfakul.yumeiro.online.AdultContent.isAdultApi(it.name) }
        else emptyList()
    }
    val feed = HomeMemory.feed
    LaunchedEffect(providers) {
        feed.providers = providers
        val names = providers.map { it.name }
        feed.homes.keys.retainAll(names.toSet())
        feed.order.retainAll(names.toSet())
        if (providers.isEmpty()) return@LaunchedEffect
        // Back on Home with the same extensions: the rows on screen stay, nothing reloads.
        if (feed.loadedFor == names && !feed.loading) return@LaunchedEffect
        feed.loadedFor = names
        val g = ++feed.gen
        feed.loading = true
        // Loads in the process scope: leaving Home no longer cancels them half-way.
        dev.asfakul.yumeiro.ui.feed.FeedScope.main.launch {
            val gate = Semaphore(3)
            supervisorScope {
                // E4 / SPEED-1: fastest extensions first; saved pages skip the gate entirely.
                val ordered = if (dev.asfakul.yumeiro.FeatureFlags.isOn(dev.asfakul.yumeiro.FeatureFlags.Flag.E4_SPEED_ORDER))
                    dev.asfakul.yumeiro.online.ExtensionSpeed.order(providers, { it.name }, null) else providers
                ordered.map { api ->
                    launch {
                        // FE-1: an old saved page shows at once too, then is refreshed live behind it.
                        val cached = runCatching { OnlineRepository.feedHome(api) }.getOrNull()
                        val rows = cached ?: gate.withPermit { withTimeoutOrNull(30_000) { runCatching { OnlineRepository.home(api) }.getOrNull() } }
                        if (g == feed.gen) {
                            // A failed refresh never wipes rows that are already shown.
                            val fresh = rows.orEmpty().filter { it.items.isNotEmpty() }
                            if (fresh.isNotEmpty() || feed.homes[api.name].isNullOrEmpty()) feed.put(api.name, fresh)
                        }
                        if (cached != null && OnlineRepository.takeStaleHome(api.name)) {
                            val live = gate.withPermit { withTimeoutOrNull(30_000) { runCatching { OnlineRepository.home(api, force = true) }.getOrNull() } }
                                ?.filter { it.items.isNotEmpty() }
                            if (g == feed.gen && !live.isNullOrEmpty()) feed.put(api.name, live)
                        }
                    }
                }.joinAll()
            }
            if (g == feed.gen) feed.loading = false
        }
    }
    return feed
}

private val ArrivalTitle = Regex("(?i)\\b(latest|new|recent|recently|update[sd]?|airing|ongoing|release[sd]?|added|this (week|season)|today)\\b")

/** Rows an extension labels as new ("Latest episodes", "Recently added", "Airing now", …). */
internal fun arrivalRows(rows: List<OnlineRow>): List<OnlineRow> = rows.filter { ArrivalTitle.containsMatchIn(it.title) }

/**
 * New arrivals across extensions: each extension's "latest / recently added / airing" rows (its
 * first row when it has none), interleaved one title per extension so no single source floods
 * the row. Never repeats a title.
 */
internal fun newArrivals(channels: List<Pair<String, List<OnlineRow>>>, max: Int = 18): List<OnlineItem> {
    val lists = channels.map { (_, rows) ->
        val pick = arrivalRows(rows).ifEmpty { rows.take(1) }
        pick.flatMap { it.items }.distinctBy { it.key }
    }.filter { it.isNotEmpty() }
    val out = LinkedHashMap<String, OnlineItem>()
    var i = 0
    while (out.size < max && lists.any { i < it.size }) {
        lists.forEach { l -> if (i < l.size && out.size < max) out.putIfAbsent(l[i].key, l[i]) }
        i++
    }
    return out.values.toList()
}

/**
 * Yumix random: an endless page of random titles, grouped by extension. Each group is up to six
 * titles from one extension; groups go round-robin over extensions, and when an extension runs
 * out of loaded titles the next page of one of its rows is fetched. Shuffling reseeds everything.
 */
@Stable
internal class YumixRandom(val seed: Long) {
    data class Group(val key: String, val api: String, val items: List<OnlineItem>, val row: OnlineRow?)

    private val pools = HashMap<String, MutableList<OnlineItem>>()
    private val used = HashMap<String, Int>()
    private val nextPage = HashMap<String, Int>()
    private val exhausted = HashSet<String>()
    private val seen = HashSet<String>()
    val groups = mutableStateListOf<Group>()
    var loadingMore by mutableStateOf(false)
        private set
    var finished by mutableStateOf(false)
        private set
    private var turn = 0

    private fun pool(name: String, rows: List<OnlineRow>): MutableList<OnlineItem> = pools.getOrPut(name) {
        rows.flatMap { it.items }.distinctBy { it.key }.shuffled(java.util.Random(seed xor name.hashCode().toLong())).toMutableList()
    }

    /** Adds [count] more groups. Fetches extra pages from the network only when a pool is empty. */
    suspend fun more(channels: List<Pair<String, List<OnlineRow>>>, providers: List<MainAPI>, count: Int = 2, per: Int = 6) {
        if (loadingMore || channels.isEmpty()) return
        loadingMore = true
        try {
            var added = 0
            var guard = 0
            while (added < count && guard < channels.size * 3) {
                guard++
                val live = channels.filter { it.first !in exhausted }
                if (live.isEmpty()) { finished = true; break }
                val (name, rows) = live[turn % live.size]
                turn++
                val pool = pool(name, rows)
                var from = used[name] ?: 0
                if (pool.size - from < per) {
                    // Top up from the next page of a row that has more.
                    val api = providers.firstOrNull { it.name == name }
                    val row = rows.filter { it.hasNext && it.pageName != null && it.pageData != null }.randomOrNull(java.util.Random(seed + turn))
                    val page = nextPage[name] ?: 2
                    val fetched = if (api != null && row != null) withTimeoutOrNull(20_000) {
                        runCatching { OnlineRepository.page(api, row.pageName!!, row.pageData!!, page) }.getOrNull()
                    } else null
                    if (fetched != null && fetched.items.isNotEmpty()) {
                        nextPage[name] = page + 1
                        pool += fetched.items.filter { it.key !in seen && pool.none { p -> p.key == it.key } }.shuffled(java.util.Random(seed + page))
                    }
                }
                val slice = pool.drop(from).filter { it.key !in seen }.take(per)
                from += per
                used[name] = from
                if (slice.size < 3) { exhausted += name; continue }
                slice.forEach { seen += it.key }
                groups += Group("$name#${groups.size}", name, slice, rows.firstOrNull())
                added++
            }
            if (channels.all { it.first in exhausted }) finished = true
        } finally {
            loadingMore = false
        }
    }
}

private fun <T> List<T>.randomOrNull(r: java.util.Random): T? = if (isEmpty()) null else this[r.nextInt(size)]
