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
import androidx.compose.runtime.derivedStateOf
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
    /**
     * B20-PERF-EXT: rows are held in [Rows] (equal only to itself). With plain lists, every put compared the
     * whole map deeply, every row and title of every extension, on the main thread (device run 45943500959:
     * a 414 ms stall in SnapshotStateMap.put while 30 extensions loaded).
     */
    val homes = mutableStateMapOf<String, Rows>()
    class Rows(val list: List<OnlineRow>)
    var providers by mutableStateOf<List<MainAPI>>(emptyList())
    var loading by mutableStateOf(false)
    val names: List<String> get() = providers.map { it.name }
    /** R15: the order the extensions answered in. Sections only ever append below the ones shown. */
    val order = mutableStateListOf<String>()
    internal var loadedFor: List<String>? = null
    internal var gen = 0
    /**
     * B20-PERF-EXT: extensions whose rows were already asked for. Each extension that finished loading used to
     * restart the rows of all of them (a new generation and a new 3-slot gate each time, the old loads still
     * running), so 60 extensions meant about 1,800 row loads and up to 180 at once. Now only the new ones load,
     * all through one gate.
     */
    internal val requested = HashSet<String>()
    internal val gate = Semaphore(3)
    internal var active = 0
    fun rows(name: String): List<OnlineRow>? = homes[name]?.list
    fun put(name: String, rows: List<OnlineRow>) {
        homes[name] = Rows(rows)
        if (rows.isNotEmpty() && name !in order) order += name
    }
    /** Names of [providers], set with them (an extension's `name` runs its own code). */
    var liveNames by mutableStateOf<Set<String>>(emptySet())
    /**
     * Extension name → its rows, in the order they arrived, only those that loaded something.
     * B20-PERF-EXT: worked out only when the rows, their order or the extensions change, and the same list is
     * handed out until then. It was rebuilt on every read while Home composed, and Home's `remember(channels)`
     * then compared the new list with the old one row by row (device run 45947352301, TV, 60 extensions: a
     * 761 ms main-thread stall in this getter while a video played).
     */
    val ready: List<Pair<String, List<OnlineRow>>> by derivedStateOf {
        val live = liveNames
        order.filter { it in live }.mapNotNull { n -> rows(n)?.takeIf { it.isNotEmpty() }?.let { n to it } }
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
        ExtensionManager.feedProviders(adultOn, adultPrivate)
            .filter { runCatching { it.hasMainPage }.getOrDefault(false) }
    }
    val feed = HomeMemory.feed
    LaunchedEffect(providers) {
        val names = providers.map { it.name }
        val live = names.toSet()
        feed.providers = providers
        feed.liveNames = live
        feed.homes.keys.retainAll(live)
        feed.order.retainAll(live)
        feed.requested.retainAll(live)
        if (providers.isEmpty()) return@LaunchedEffect
        feed.loadedFor = names
        // Back on Home with the same extensions: the rows on screen stay, nothing reloads. B20-PERF-EXT: when
        // more extensions finish loading, only those load their rows; the ones already asked are left alone.
        val fresh = providers.filter { it.name !in feed.requested }
        if (fresh.isEmpty()) return@LaunchedEffect
        feed.requested += fresh.map { it.name }
        val g = feed.gen
        val gate = feed.gate
        feed.active += fresh.size
        feed.loading = true
        // Loads in the process scope: leaving Home no longer cancels them half-way.
        dev.asfakul.yumeiro.ui.feed.FeedScope.main.launch {
            supervisorScope {
                // E4 / SPEED-1: fastest extensions first; saved pages skip the gate entirely.
                val ordered = if (dev.asfakul.yumeiro.FeatureFlags.isOn(dev.asfakul.yumeiro.FeatureFlags.Flag.E4_SPEED_ORDER))
                    dev.asfakul.yumeiro.online.ExtensionSpeed.order(fresh, { it.name }, null) else fresh
                ordered.map { api ->
                    launch {
                        try {
                            // FE-1: an old saved page shows at once too, then is refreshed live behind it.
                            val cached = runCatching { OnlineRepository.feedHome(api) }.getOrNull()
                            // FE-2c: Home is on screen, so its first fetch is VISIBLE; the stale refresh below stays background.
                            val rows = cached ?: gate.withPermit { withTimeoutOrNull(30_000) { runCatching {
                                kotlinx.coroutines.withContext(dev.asfakul.yumeiro.online.PriorityGate.Lane(dev.asfakul.yumeiro.online.PriorityGate.VISIBLE)) { OnlineRepository.home(api) }
                            }.getOrNull() } }
                            if (g == feed.gen && api.name in feed.requested) {
                                // A failed refresh never wipes rows that are already shown.
                                val got = rows.orEmpty().filter { it.items.isNotEmpty() }
                                if (got.isNotEmpty() || feed.rows(api.name).isNullOrEmpty()) feed.put(api.name, got)
                            }
                            if (cached != null && OnlineRepository.takeStaleHome(api.name)) {
                                val liveRows = gate.withPermit { withTimeoutOrNull(30_000) { runCatching { OnlineRepository.home(api, force = true) }.getOrNull() } }
                                    ?.filter { it.items.isNotEmpty() }
                                if (g == feed.gen && api.name in feed.requested && !liveRows.isNullOrEmpty()) feed.put(api.name, liveRows)
                            }
                        } finally {
                            feed.active--
                            if (feed.active <= 0) { feed.active = 0; feed.loading = false }
                        }
                    }
                }.joinAll()
            }
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
