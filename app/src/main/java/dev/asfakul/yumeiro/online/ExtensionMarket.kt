/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import dev.asfakul.yumeiro.security.TrustedSources
import android.content.Context
import dev.asfakul.yumeiro.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Extension Market: extensions from the repositories listed in `.github/extensions/sources.json`,
 * validated by the `Extension market` workflow (manifest, class name, size, sha256) and mirrored to
 * the `extensions` branch. Cached on the device; works offline from the last copy.
 */
object ExtensionMarket {
    const val BASE = "https://raw.githubusercontent.com/siamform/yumeiro/extensions"
    const val INDEX_URL = "$BASE/index.json"
    const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
    private const val MAX_INDEX_BYTES = 24L * 1024 * 1024

    data class MarketRepo(
        val url: String, val name: String, val description: String?, val stars: Int?, val count: Int, val ok: Boolean,
        val origin: String = "curated", val verified: Boolean = true, val github: String? = null, val pushed: Long = 0,
    )
    data class Catalog(val generated: Long, val repos: List<MarketRepo>, val extensions: List<RemoteExtension>, val fetchedAt: Long, val fromCache: Boolean)

    sealed interface State {
        data object Idle : State
        data object Loading : State
        data class Ready(val catalog: Catalog, val refreshing: Boolean = false, val warning: String? = null) : State
        data class Error(val message: String) : State
    }

    private val mutex = Mutex()
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()
    val catalog: Catalog? get() = (_state.value as? State.Ready)?.catalog

    private fun cacheFile(context: Context) = File(File(context.filesDir, "extensions").apply { mkdirs() }, "market.json")

    fun parse(text: String, fetchedAt: Long = System.currentTimeMillis(), fromCache: Boolean = false): Catalog {
        val root = JSONObject(text)
        val repos = root.optJSONArray("repositories")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                val url = o.optString("url").ifBlank { return@mapNotNull null }
                val origin = o.optString("origin").ifBlank { "curated" }
                MarketRepo(url, o.optString("name").ifBlank { ExtensionFormats.repoNameFromUrl(url) }, o.optStringOrNull("description"),
                    if (o.has("stars") && !o.isNull("stars")) o.optInt("stars") else null, o.optInt("count"), o.optBoolean("ok", true),
                    origin = origin, verified = o.optBoolean("verified", origin == "curated"), github = o.optStringOrNull("github"),
                    pushed = parseTime(o.optString("pushed")))
            }
        }.orEmpty()
        val names = repos.associateBy { it.url }
        val exts = root.optJSONArray("extensions")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                val internal = o.optString("internalName").trim().takeIf { it.matches(Regex("[A-Za-z0-9_.\\-]{1,120}")) } ?: return@mapNotNull null
                val url = o.optString("url").trim().takeIf { it.startsWith("https://") } ?: return@mapNotNull null
                val repoUrl = o.optString("repoUrl").ifBlank { return@mapNotNull null }
                RemoteExtension(
                    internalName = internal,
                    name = o.optString("name").ifBlank { internal },
                    url = url,
                    version = o.optInt("version"),
                    apiVersion = o.optInt("apiVersion", 1),
                    status = o.optInt("status", 1),
                    description = o.optStringOrNull("description"),
                    authors = o.optJSONArray("authors").strings(),
                    tvTypes = o.optJSONArray("tvTypes").strings(),
                    language = o.optStringOrNull("language"),
                    iconUrl = o.optStringOrNull("iconUrl"),
                    fileSize = if (o.has("size")) o.optLong("size") else null,
                    sha256 = o.optStringOrNull("sha256")?.lowercase(),
                    sourceUrl = o.optStringOrNull("source"),
                    repoUrl = repoUrl,
                    repoName = o.optStringOrNull("repoName") ?: names[repoUrl]?.name ?: ExtensionFormats.repoNameFromUrl(repoUrl),
                    verified = if (o.has("verified")) o.optBoolean("verified") else names[repoUrl]?.verified ?: true,
                    stars = names[repoUrl]?.stars ?: if (o.has("stars")) o.optInt("stars") else null,
                    updated = parseTime(o.optString("updated")).takeIf { it > 0 } ?: names[repoUrl]?.pushed ?: 0L,
                    origin = o.optString("origin").ifBlank { names[repoUrl]?.origin ?: "curated" },
                    score = o.optDouble("score", 0.0).takeIf { !it.isNaN() } ?: 0.0,
                    rank = o.optInt("rank", 0),
                    primary = o.optBoolean("primary", true),
                    copies = o.optInt("copies", 1).coerceAtLeast(1),
                    categories = ExtensionFormats.withShortDrama(o.optJSONArray("categories").strings().ifEmpty { ExtensionFormats.categoriesOf(o.optJSONArray("tvTypes").strings()) }, o.optString("name"), o.optString("internalName"), o.optStringOrNull("description")),
                    compatNote = o.optStringOrNull("compat"),
                )
            }
        }.orEmpty().distinctBy { it.key }
        return Catalog(parseTime(root.optString("generated")), repos, exts, fetchedAt, fromCache)
    }

    private fun parseTime(s: String?): Long = runCatching { Instant.parse(s!!.trim()).toEpochMilli() }.getOrDefault(0L)

    /** Shows the cached catalog immediately, refreshing it when stale or [force]d. Never throws. */
    suspend fun load(context: Context, force: Boolean = false): Catalog? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val file = cacheFile(context)
            val cached = catalog ?: runCatching { if (file.exists()) parse(file.readText(), file.lastModified(), true) else null }.getOrNull()
            _state.value = if (cached != null) State.Ready(cached, refreshing = true) else State.Loading
            if (!force && cached != null && System.currentTimeMillis() - cached.fetchedAt < CHECK_INTERVAL_MS) {
                _state.value = State.Ready(cached); return@withLock cached
            }
            try {
                val text = TrustedSources.fetchCatalogText(INDEX_URL, MAX_INDEX_BYTES)
                val cat = parse(text)
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(text)
                if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
                _state.value = State.Ready(cat)
                cat
            } catch (e: Exception) {
                val msg = if (e.message?.contains("404") == true) "The market is being prepared. Try again in a few minutes" else OnlineErrors.friendly(e)
                _state.value = if (cached != null) State.Ready(cached, warning = "Offline — showing saved market ($msg)") else State.Error(msg)
                cached
            }
        }
    }

    internal fun resetForTests() { _state.value = State.Idle }
}

/** Everything installable: the market plus repositories the user added. */
object ExtensionSources {
    private const val REPO_CACHE_MS = 30 * 60 * 1000L
    private const val MAX_LIST_BYTES = 4L * 1024 * 1024

    data class RepoListing(
        val url: String, val name: String, val description: String?, val count: Int, val verified: Boolean, val error: String? = null,
        val stars: Int? = null, val origin: String = "user", val updated: Long = 0, val github: String? = null,
    )
    data class Available(val items: List<RemoteExtension>, val repos: List<RepoListing>, val warning: String? = null)

    private data class Cached(val at: Long, val repo: ExtensionRepo, val items: List<RemoteExtension>)
    private val repoCache = ConcurrentHashMap<String, Cached>()

    fun invalidate(url: String) { repoCache.remove(url) }

    fun downloadText(url: String, max: Long = MAX_LIST_BYTES): String {
        val req = Request.Builder().url(url).header("Accept", "application/json").header("Cache-Control", "no-cache").build()
        NetworkClient.get().newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
            val src = r.body.source()
            if (src.request(max + 1)) throw java.io.IOException("File too large")
            return src.buffer.readUtf8()
        }
    }

    fun fetchRepo(url: String): ExtensionRepo = ExtensionFormats.parseRepo(url, downloadText(url))

    /** A repository's plugins (cached for a while). */
    suspend fun repoPlugins(url: String, force: Boolean = false): Pair<ExtensionRepo, List<RemoteExtension>> = withContext(Dispatchers.IO) {
        repoCache[url]?.takeIf { !force && System.currentTimeMillis() - it.at < REPO_CACHE_MS }?.let { return@withContext it.repo to it.items }
        val repo = fetchRepo(url)
        val items = coroutineScope {
            repo.pluginLists.take(8).map { list ->
                async { runCatching { ExtensionFormats.parsePlugins(list, downloadText(list), repo) }.getOrDefault(emptyList()) }
            }.awaitAll().flatten().distinctBy { it.internalName }
        }
        repoCache[url] = Cached(System.currentTimeMillis(), repo, items)
        repo to items
    }

    suspend fun all(context: Context, force: Boolean = false): Available = coroutineScope {
        val market = async { ExtensionMarket.load(context, force) }
        val userRepos = ExtensionManager.state.value.repos
        val custom = userRepos.map { ref ->
            async {
                runCatching { withTimeout(25_000) { repoPlugins(ref.url, force) } }.fold(
                    { (repo, items) -> RepoListing(ref.url, repo.name, repo.description, items.size, false) to items },
                    { RepoListing(ref.url, ref.name, null, 0, false, OnlineErrors.friendly(it)) to emptyList() },
                )
            }
        }.awaitAll()
        val cat = market.await()
        val marketRepos = cat?.repos.orEmpty().map { RepoListing(it.url, it.name, it.description, it.count, it.verified, if (it.ok) null else "Unavailable", it.stars, it.origin, it.pushed, it.github) }
        val customUrls = custom.map { it.first.url.lowercase() }.toSet()
        val items = (cat?.extensions.orEmpty() + custom.flatMap { it.second })
            // Market copy (verified, mirrored) wins over the same plugin from a user-added repo.
            .distinctBy { it.key }
        val warning = (ExtensionMarket.state.value as? ExtensionMarket.State.Ready)?.warning
            ?: (ExtensionMarket.state.value as? ExtensionMarket.State.Error)?.message
        Available(items, marketRepos.filter { it.url.lowercase() !in customUrls } + custom.map { it.first }, warning)
    }
}
