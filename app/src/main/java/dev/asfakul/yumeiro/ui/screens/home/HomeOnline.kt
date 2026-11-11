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
    /** Extension name → its rows, in provider order, only those that loaded something. */
    val ready: List<Pair<String, List<OnlineRow>>> get() = providers.mapNotNull { p -> homes[p.name]?.takeIf { it.isNotEmpty() }?.let { p.name to it } }
}

@Composable
internal fun rememberExtensionFeed(): ExtensionFeed {
    val context = LocalContext.current
    val engine by ExtensionManager.state.collectAsState()
    LaunchedEffect(Unit) { ExtensionManager.start(context) }
    val adultOn by dev.asfakul.yumeiro.online.OnlineSettings.adult.collectAsState()
    val adultPrivate by dev.asfakul.yumeiro.online.OnlineSettings.adultPrivate.collectAsState()
    val providers = remember(engine.generation, engine.ready, adultOn, adultPrivate) {
        if (engine.ready) ExtensionManager.providers()
            .filter { runCatching { it.hasMainPage }.getOrDefault(false) }
            .filterNot { (!adultOn || adultPrivate) && dev.asfakul.yumeiro.online.AdultContent.isAdultApi(it.name) }
        else emptyList()
    }
    val feed = remember { ExtensionFeed() }
    LaunchedEffect(providers) {
        feed.providers = providers
        feed.homes.keys.retainAll(providers.map { it.name }.toSet())
        if (providers.isEmpty()) return@LaunchedEffect
        feed.loading = true
        val gate = Semaphore(3)
        supervisorScope {
            providers.map { api ->
                launch {
                    val rows = gate.withPermit { withTimeoutOrNull(30_000) { runCatching { OnlineRepository.home(api) }.getOrNull() } }
                    feed.homes[api.name] = rows.orEmpty().filter { it.items.isNotEmpty() }
                }
            }.joinAll()
        }
        feed.loading = false
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
internal class YumixRandom(private val seed: Long) {
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
