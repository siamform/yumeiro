/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.novel

import android.content.Context
import android.util.Log
import dev.asfakul.yumeiro.data.SafeJsonFile
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.online.OnlineRuntime
import dev.asfakul.yumeiro.profiles.ProfileScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.Request
import java.io.File

/**
 * NOVEL-1 (owner item 26): novel sources = LNReader plugins. Same shape as Yumeiro's other ecosystems
 * (JsSources): the user adds a repository (an LNReader `plugins.min.json`), installs plugins from it, and each
 * installed plugin's code is kept in filesDir/novel. Lists are written atomically (SafeJsonFile) and follow
 * profiles (ProfileScope, NOVEL_REPOS / NOVEL_PLUGINS). Nothing is bundled: Yumeiro ships no novel source.
 */
object NovelSources {
    private const val TAG = "NovelSources"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    data class State(val repos: List<String> = emptyList(), val installed: List<NovelPlugin> = emptyList())

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val lock = Any()
    @Volatile private var loaded = false
    private var otherRepos: List<String> = emptyList()
    private var otherInstalled: List<NovelPlugin> = emptyList()
    private val engines = LinkedHashMap<String, NovelEngine>(8, 0.75f, true)

    private fun dir(c: Context) = File(c.applicationContext.filesDir, "novel").apply { mkdirs() }
    private fun codeFile(c: Context, id: String) = File(dir(c), "${safe(id)}.js")
    private val UNSAFE = Regex("[^A-Za-z0-9._-]")
    private fun safe(id: String) = id.replace(UNSAFE, "_")

    /** Reads the saved lists. Blocking file I/O: call off the main thread. */
    fun ensureLoaded(context: Context) = synchronized(lock) {
        if (loaded) return@synchronized
        val d = dir(context)
        val repos = SafeJsonFile.read(File(d, "repos.json")) { t ->
            (json.parseToJsonElement(t) as JsonArray).mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        }.value.orEmpty()
        val installed = SafeJsonFile.read(File(d, "plugins.json")) { t ->
            (json.parseToJsonElement(t) as JsonArray).mapNotNull { (it as? JsonObject)?.let(NovelPlugin::from) }
        }.value.orEmpty()
        val (myRepos, theirRepos, _) = ProfileScope.splitAll(context, ProfileScope.NOVEL_REPOS, repos) { it }
        val (mine, theirs, gone) = ProfileScope.splitAll(context, ProfileScope.NOVEL_PLUGINS, installed.distinctBy { it.id }) { it.id }
        otherRepos = theirRepos; otherInstalled = theirs
        gone.forEach { codeFile(context, it.id).delete() }
        _state.value = State(myRepos.distinct(), mine)
        loaded = true
    }

    /** Tests only: show these lists without disk or network (UI tests of the sources pages). */
    @Volatile internal var availableForTest: List<NovelPlugin>? = null

    @androidx.annotation.VisibleForTesting
    internal fun setForTest(state: State, available: List<NovelPlugin>? = null) = synchronized(lock) {
        loaded = true; otherRepos = emptyList(); otherInstalled = emptyList()
        _state.value = state; availableForTest = available
    }

    private fun change(context: Context, f: (State) -> State) = synchronized(lock) {
        ensureLoaded(context)
        _state.value = f(_state.value)
        SafeJsonFile.write(File(dir(context), "repos.json"), JsonArray((_state.value.repos + (otherRepos - _state.value.repos.toSet())).map(::JsonPrimitive)).toString())
        SafeJsonFile.write(File(dir(context), "plugins.json"), JsonArray((_state.value.installed + otherInstalled.filter { o -> _state.value.installed.none { it.id == o.id } }).map { it.toJson() }).toString())
    }

    fun normalizeRepo(raw: String): String {
        var u = raw.trim()
        if (!u.startsWith("http")) u = "https://$u"
        return u
    }

    suspend fun addRepo(context: Context, raw: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val url = normalizeRepo(raw)
            val n = fetchIndex(context, url).size
            require(n > 0) { "That link has no novel plugins (an LNReader plugins.min.json is expected)" }
            change(context) { otherRepos = otherRepos - url; it.copy(repos = (it.repos - url) + url) }
            ProfileScope.claim(context, ProfileScope.NOVEL_REPOS, url)
            n
        }
    }

    fun removeRepo(context: Context, url: String) {
        val kept = ProfileScope.release(context, ProfileScope.NOVEL_REPOS, url)
        change(context) { if (kept) otherRepos = otherRepos - url + url; it.copy(repos = it.repos - url) }
    }

    suspend fun fetchIndex(context: Context, url: String): List<NovelPlugin> = withContext(Dispatchers.IO) {
        val body = NetworkClient.get(context).newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) error("Repository answered HTTP ${r.code}")
            r.body.string()
        }
        (json.parseToJsonElement(body) as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(NovelPlugin::from) }
    }

    /** Every plugin in the added repositories (a repository that fails is skipped, not fatal). */
    suspend fun available(context: Context): List<NovelPlugin> = withContext(Dispatchers.IO) {
        availableForTest?.let { return@withContext it }
        ensureLoaded(context)
        coroutineScope {
            _state.value.repos.map { r -> async { runCatching { fetchIndex(context, r) }.onFailure { Log.w(TAG, "index: ${it.message}") }.getOrDefault(emptyList()) } }
                .flatMap { it.await() }
        }.distinctBy { it.id }.sortedWith(compareBy({ it.language.lowercase() }, { it.name.lowercase() }))
    }

    suspend fun install(context: Context, p: NovelPlugin): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val code = NetworkClient.get(context).newCall(Request.Builder().url(p.url).build()).execute().use { r ->
                if (!r.isSuccessful) error("Download failed (HTTP ${r.code})")
                r.body.string()
            }
            require("exports" in code) { "This file isn't an LNReader plugin" }
            codeFile(context, p.id).writeText(code)
            synchronized(engines) { engines.remove(p.id)?.close() }
            change(context) { otherInstalled = otherInstalled.filterNot { x -> x.id == p.id }; it.copy(installed = it.installed.filterNot { x -> x.id == p.id } + p) }
            ProfileScope.claim(context, ProfileScope.NOVEL_PLUGINS, p.id)
        }
    }

    fun uninstall(context: Context, id: String) {
        val kept = ProfileScope.release(context, ProfileScope.NOVEL_PLUGINS, id)
        if (!kept) codeFile(context, id).delete()
        synchronized(engines) { engines.remove(id)?.close() }
        change(context) { s ->
            if (kept) s.installed.firstOrNull { it.id == id }?.let { e -> otherInstalled = otherInstalled.filterNot { it.id == id } + e }
            s.copy(installed = s.installed.filterNot { it.id == id })
        }
    }

    fun plugin(id: String): NovelPlugin? = _state.value.installed.firstOrNull { it.id == id }

    /** The engine for [p]; at most [MAX_ENGINES] stay open (least recently used is closed). */
    fun engine(context: Context, p: NovelPlugin): NovelEngine = synchronized(engines) {
        engines[p.id] ?: run {
            OnlineRuntime.init(context) // browser-check passes (VERIFY-1/2) apply to novel sources too
            val code = codeFile(context, p.id).takeIf { it.isFile }?.readText() ?: error("${p.name} isn't installed any more")
            NovelEngine(context.applicationContext, p, code, OnlineRuntime.extensionClient(context)).also {
                engines[p.id] = it
                while (engines.size > MAX_ENGINES) engines.entries.first().let { e -> e.value.close(); engines.remove(e.key) }
            }
        }
    }

    private fun need(context: Context, id: String): NovelPlugin { ensureLoaded(context); return plugin(id) ?: error("This novel source isn't installed") }

    suspend fun popular(context: Context, id: String, page: Int, latest: Boolean = false): List<NovelItem> = withContext(Dispatchers.IO) {
        val p = need(context, id); val e = engine(context, p)
        val opts = buildJsonObject { put("showLatestNovels", latest) }
        NovelParse.items(id, e.site, e.call("popularNovels", listOf(JsonPrimitive(page), opts)))
    }

    suspend fun search(context: Context, id: String, query: String, page: Int = 1): List<NovelItem> = withContext(Dispatchers.IO) {
        val p = need(context, id); val e = engine(context, p)
        NovelParse.items(id, e.site, e.call("searchNovels", listOf(JsonPrimitive(query), JsonPrimitive(page))))
    }

    /** One result group per source; a source that fails or takes longer than [perSourceMs] is reported, not waited for. */
    data class SearchGroup(val plugin: NovelPlugin, val items: List<NovelItem>, val error: String?)

    suspend fun searchAll(context: Context, query: String, perSourceMs: Long = 25_000L): List<SearchGroup> = withContext(Dispatchers.IO) {
        ensureLoaded(context)
        coroutineScope {
            _state.value.installed.map { p ->
                async {
                    val r = withTimeoutOrNull(perSourceMs) { runCatching { search(context, p.id, query) } }
                    when {
                        r == null -> SearchGroup(p, emptyList(), "took too long")
                        r.isFailure -> SearchGroup(p, emptyList(), r.exceptionOrNull()?.message ?: "failed")
                        else -> SearchGroup(p, r.getOrThrow(), null)
                    }
                }
            }.map { it.await() }
        }
    }

    private val detailCache = android.util.LruCache<String, NovelDetails>(24)

    /** Details opened in this run (the reader reuses them for Previous / Next without asking the site again). */
    fun cachedDetails(id: String, path: String): NovelDetails? = detailCache.get(NovelLibrary.key(id, path))

    fun rememberDetails(d: NovelDetails) { detailCache.put(NovelLibrary.key(d.pluginId, d.path), d) }

    suspend fun details(context: Context, id: String, path: String): NovelDetails = withContext(Dispatchers.IO) {
        val p = need(context, id); val e = engine(context, p)
        (NovelParse.details(id, e.site, path, e.call("parseNovel", listOf(JsonPrimitive(path)))) ?: error("${p.name} returned no details"))
            .also(::rememberDetails)
    }

    /** LNReader `parsePage(path, page)`: the chapters of page [page] (novels whose list is split in pages). */
    suspend fun chapterPage(context: Context, id: String, path: String, page: Int): List<NovelChapter> = withContext(Dispatchers.IO) {
        val p = need(context, id); val e = engine(context, p)
        val o = e.call("parsePage", listOf(JsonPrimitive(path), JsonPrimitive(page.toString()))) as? JsonObject
        NovelParse.chapters(o?.get("chapters"))
    }

    /** The chapter as HTML. */
    suspend fun chapterHtml(context: Context, id: String, path: String): String = withContext(Dispatchers.IO) {
        val p = need(context, id); val e = engine(context, p)
        (e.call("parseChapter", listOf(JsonPrimitive(path))) as? JsonPrimitive)?.contentOrNull ?: error("${p.name} returned no text for this chapter")
    }

    fun webUrl(context: Context, id: String, path: String): String? {
        val p = plugin(id) ?: return null
        val site = synchronized(engines) { engines[id]?.site } ?: p.site
        return NovelParse.absolute(site, path)
    }

    const val MAX_ENGINES = 4
    /** The LNReader project's own plugin index (shown as a suggestion; the user decides to add it). */
    const val LNREADER_INDEX = "https://raw.githubusercontent.com/LNReader/lnreader-plugins/plugins/v3.0.0/.dist/plugins.min.json"
}
