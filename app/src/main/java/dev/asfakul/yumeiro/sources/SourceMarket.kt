/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources

import android.content.Context
import dev.asfakul.yumeiro.online.ExtensionMarket
import dev.asfakul.yumeiro.security.TrustedSources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.io.File
import java.time.Instant

/**
 * Source markets for Aniyomi, Mihon, Mangayomi (JavaScript) and Stremio. The `Extension market`
 * workflow finds repositories on GitHub (topics, keywords, README add-repo links), reads every
 * index, drops dead, stale and duplicate ones and ranks the rest by stars, last update and size;
 * Stremio add-ons come from the official and community catalogs, each manifest checked. The result
 * is `sources.json` on the `extensions` branch. Yumeiro only lists them; adding one is the user's
 * choice and goes through the same path as pasting a link by hand.
 */
object SourceMarket {
    const val URL = "${ExtensionMarket.BASE}/sources.json"
    private const val MAX_BYTES = 4L * 1024 * 1024
    private const val FRESH_MS = 6 * 60 * 60 * 1000L

    enum class Kind(val key: String) { ANIYOMI("aniyomi"), MIHON("mihon"), MANGAYOMI("mangayomi") }
    enum class Sort(val label: String) { TOP("Top"), RECENT("Recently updated"), MOST("Most extensions") }
    enum class AddonSort(val label: String) { FEATURED("Featured"), NAME("A–Z") }

    data class Repo(
        val kind: Kind, val url: String, val github: String, val name: String, val owner: String,
        val description: String, val stars: Int, val updated: Long, val count: Int, val nsfw: Int,
        val langs: List<String>, val sample: List<String>, val score: Double,
    ) {
        /** Only 18+ extensions: hidden unless the adult setting is on. */
        val adultOnly get() = count == 0 && nsfw > 0
    }

    data class Addon(
        val url: String, val name: String, val description: String, val version: String,
        val types: List<String>, val resources: List<String>, val catalogs: Int, val featured: Boolean, val adult: Boolean,
    )

    data class Catalog(val generated: Long, val repos: Map<Kind, List<Repo>>, val addons: List<Addon>)

    sealed interface State {
        data object Idle : State
        data object Loading : State
        data class Ready(val catalog: Catalog, val stale: Boolean = false) : State
        data class Error(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()
    private val mutex = Mutex()
    private var fetchedAt = 0L

    private fun cacheFile(context: Context) = File(File(context.filesDir, "extensions").apply { mkdirs() }, "sources.json")

    /** Loads the market: memory → fresh download (every 6 h) → last cached copy. Never throws. */
    suspend fun load(context: Context, force: Boolean = false) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            if (!force && _state.value is State.Ready && now - fetchedAt < FRESH_MS) return@withLock
            val cache = cacheFile(context)
            if (_state.value !is State.Ready) {
                runCatching { parse(cache.readText()) }.getOrNull()?.let { _state.value = State.Ready(it, stale = true) }
                    ?: run { _state.value = State.Loading }
            }
            runCatching {
                val text = TrustedSources.fetchCatalogText(URL, MAX_BYTES)
                val catalog = parse(text)
                runCatching { cache.writeText(text) }
                fetchedAt = now
                _state.value = State.Ready(catalog)
            }.onFailure {
                if (_state.value !is State.Ready) _state.value = State.Error("Couldn't load the market. Check your connection and try again.")
            }
        }
    }

    // ---------- pure logic (unit-tested) ----------

    fun parse(text: String): Catalog {
        val root = Json.parseToJsonElement(text) as JsonObject
        val repos = Kind.entries.associateWith { kind ->
            (root[kind.key] as? JsonArray).orEmpty().mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val url = o.str("url").takeIf { it.startsWith("https://") } ?: return@mapNotNull null
                Repo(
                    kind, url, o.str("github"), o.str("name").ifBlank { o.str("github").substringAfter('/') }, o.str("owner"),
                    o.str("desc"), o.int("stars"), time(o.str("updated")), o.int("count"), o.int("nsfw"),
                    o.strings("langs"), o.strings("sample"), (o["score"] as? JsonPrimitive)?.doubleOrNull ?: 0.0,
                )
            }.distinctBy { it.url }
        }
        val addons = (root["stremio"] as? JsonArray).orEmpty().mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val url = o.str("url").takeIf { it.startsWith("https://") && it.endsWith("/manifest.json") } ?: return@mapNotNull null
            val name = o.str("name").ifBlank { return@mapNotNull null }
            Addon(url, name, o.str("desc"), o.str("version"), o.strings("types"), o.strings("resources"), o.int("catalogs"),
                o.bool("featured"), o.bool("adult"))
        }.distinctBy { it.url }
        return Catalog(time(root.str("generated")), repos, addons)
    }

    fun filterRepos(list: List<Repo>, query: String, sort: Sort, adult: Boolean): List<Repo> {
        val q = query.trim()
        return list
            .filter { adult || !it.adultOnly }
            .filter {
                q.isEmpty() || it.name.contains(q, true) || it.owner.contains(q, true) || it.description.contains(q, true) ||
                    it.langs.any { l -> l.equals(q, true) } || it.sample.any { s -> s.contains(q, true) }
            }
            .let { l ->
                when (sort) {
                    Sort.TOP -> l.sortedByDescending { it.score }
                    Sort.RECENT -> l.sortedByDescending { it.updated }
                    Sort.MOST -> l.sortedByDescending { it.count + if (adult) it.nsfw else 0 }
                }
            }
    }

    fun filterAddons(list: List<Addon>, query: String, sort: AddonSort, adult: Boolean): List<Addon> {
        val q = query.trim()
        return list
            .filter { adult || !it.adult }
            .filter { q.isEmpty() || it.name.contains(q, true) || it.description.contains(q, true) || it.types.any { t -> t.equals(q, true) } }
            .let { l ->
                when (sort) {
                    AddonSort.FEATURED -> l.sortedWith(compareByDescending<Addon> { it.featured }.thenBy { it.name.lowercase() })
                    AddonSort.NAME -> l.sortedBy { it.name.lowercase() }
                }
            }
    }

    /** "2 days ago" style label for a repository's last update. */
    fun ago(ms: Long, now: Long = System.currentTimeMillis()): String {
        if (ms <= 0) return "unknown"
        val d = (now - ms) / 86_400_000L
        return when {
            d < 1 -> "today"
            d < 2 -> "yesterday"
            d < 30 -> "$d days ago"
            d < 365 -> "${d / 30} mo ago"
            else -> "${d / 365} yr ago"
        }
    }

    private fun time(s: String) = runCatching { Instant.parse(s).toEpochMilli() }.getOrDefault(0L)
    private fun JsonObject.str(k: String) = ((this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: "").trim()
    private fun JsonObject.int(k: String) = (this[k] as? JsonPrimitive)?.intOrNull ?: 0
    private fun JsonObject.bool(k: String) = (this[k] as? JsonPrimitive)?.booleanOrNull ?: false
    private fun JsonObject.strings(k: String) = (this[k] as? JsonArray).orEmpty().mapNotNull { e: JsonElement ->
        (e as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }
    }
}
