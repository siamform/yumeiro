/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.js

import android.content.Context
import android.util.Log
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.online.OnlineSettings
import dev.asfakul.yumeiro.sources.SourceHub
import dev.asfakul.yumeiro.sources.js.JsJson.s
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Request
import java.io.File

/**
 * Mangayomi-format JavaScript sources: a repo is an `index.json` list; each entry's
 * `sourceCodeUrl` is a single .js file. Only JavaScript entries (`sourceCodeLanguage` 1) are
 * used; Dart ones need Mangayomi's own interpreter. Anime entries become video sources, manga
 * entries become reader sources. Novels are not supported yet.
 */
object JsSources {
    private const val TAG = "JsSources"

    data class Entry(val raw: JsonObject) {
        val id get() = raw.s("id").orEmpty()
        val name get() = raw.s("name").orEmpty()
        val lang get() = raw.s("lang").orEmpty()
        val version get() = raw.s("version").orEmpty()
        val iconUrl get() = raw.s("iconUrl")
        val codeUrl get() = raw.s("sourceCodeUrl").orEmpty()
        val itemType get() = raw.s("itemType")?.toIntOrNull() ?: if (raw.s("isManga") == "false") 1 else 0
        val nsfw get() = raw.s("isNsfw") == "true"
        val isJs get() = raw.s("sourceCodeLanguage") == "1"
        val kindLabel get() = when (itemType) { 0 -> "Manga"; 1 -> "Anime / video"; else -> "Novel" }
    }

    data class State(val repos: List<String> = emptyList(), val installed: List<Entry> = emptyList())

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var started = false
    private lateinit var app: Context
    private val engines = HashMap<String, JsEngine>()

    private fun dir(c: Context) = File(c.filesDir, "js").apply { mkdirs() }

    fun start(context: Context) {
        if (started) return
        started = true
        app = context.applicationContext
        scope.launch {
            runCatching { ensureLoaded(app); publish() }.onFailure { Log.e(TAG, "start", it) }
        }
    }

    /*
     * N5-1: before, both files were parsed inside one runCatching, so one damaged file emptied
     * both lists and the next save wiped repos and installed sources; and an add or install that ran
     * before the start-up read finished saved a list without the old entries. Now each file is read
     * on its own through SafeJsonFile (atomic write, .tmp recovery, damaged copy kept as .bad), and
     * every change first waits for the saved lists under one lock.
     */
    private val lock = Any()
    @Volatile private var loaded = false

    private fun ensureLoaded(context: Context) = synchronized(lock) {
        if (loaded) return@synchronized
        val d = dir(context)
        val repos = dev.asfakul.yumeiro.data.SafeJsonFile.read(File(d, "repos.json")) { t ->
            (JsJson.json.parseToJsonElement(t) as JsonArray).mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        }.value.orEmpty()
        val installed = dev.asfakul.yumeiro.data.SafeJsonFile.read(File(d, "sources.json")) { t ->
            (JsJson.json.parseToJsonElement(t) as JsonArray).mapNotNull { (it as? JsonObject)?.let(::Entry) }
        }.value.orEmpty()
        // Keep anything a change already put in memory (cannot happen now, but never drop data).
        _state.value = State((repos + _state.value.repos).distinct(), (installed + _state.value.installed).distinctBy { it.id })
        loaded = true
    }

    private fun change(context: Context, f: (State) -> State) = synchronized(lock) {
        ensureLoaded(context)
        _state.value = f(_state.value)
        save(context)
    }

    @androidx.annotation.VisibleForTesting
    internal fun resetForTests() = synchronized(lock) { started = false; loaded = false; _state.value = State() }

    fun normalizeRepo(raw: String): String {
        var u = raw.trim()
        if (u.startsWith("mangayomi://")) u = android.net.Uri.parse(u).let { it.getQueryParameter("url") ?: it.getQueryParameter("manga") ?: it.getQueryParameter("anime") } ?: u
        if (!u.startsWith("http")) u = "https://$u"
        return u
    }

    suspend fun addRepo(context: Context, raw: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            if (!started) start(context)
            val url = normalizeRepo(raw)
            val n = fetchIndex(context, url).size
            require(n > 0) { "That link has no JavaScript sources" }
            change(context) { it.copy(repos = (it.repos - url) + url) }
            n
        }
    }

    fun removeRepo(context: Context, url: String) { change(context) { it.copy(repos = it.repos - url) } }

    suspend fun fetchIndex(context: Context, url: String): List<Entry> = withContext(Dispatchers.IO) {
        val body = NetworkClient.get(context).newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) error("Repo answered HTTP ${r.code}")
            r.body.string()
        }
        (JsJson.json.parseToJsonElement(body) as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::Entry) }
            .filter { it.isJs && it.itemType in 0..1 && it.codeUrl.isNotBlank() }
    }

    suspend fun available(context: Context): List<Entry> = withContext(Dispatchers.IO) {
        _state.value.repos.flatMap { runCatching { fetchIndex(context, it) }.getOrDefault(emptyList()) }
            .distinctBy { it.id }.filter { OnlineSettings.adult.value || !it.nsfw }.sortedBy { it.name.lowercase() }
    }

    suspend fun install(context: Context, e: Entry): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val code = NetworkClient.get(context).newCall(Request.Builder().url(e.codeUrl).build()).execute().use { r ->
                if (!r.isSuccessful) error("Download failed (HTTP ${r.code})")
                r.body.string()
            }
            require("DefaultExtension" in code) { "This file isn't a Mangayomi JavaScript source" }
            File(dir(context), "${e.id}.js").writeText(code)
            synchronized(engines) { engines.remove(e.id)?.close() }
            change(context) { it.copy(installed = it.installed.filterNot { x -> x.id == e.id } + e) }
            publish()
        }
    }

    fun uninstall(context: Context, id: String) {
        File(dir(context), "$id.js").delete()
        synchronized(engines) { engines.remove(id)?.close() }
        change(context) { it.copy(installed = it.installed.filterNot { x -> x.id == id }) }
        publish()
    }

    internal fun appContext(): Context = app

    internal fun engine(e: Entry): JsEngine = synchronized(engines) {
        engines.getOrPut(e.id) { JsEngine(app, e.id, e.raw.toString(), File(dir(app), "${e.id}.js").readText()) }
    }

    private fun save(context: Context) {
        dev.asfakul.yumeiro.data.SafeJsonFile.write(File(dir(context), "repos.json"), JsonArray(_state.value.repos.map(::JsonPrimitive)).toString())
        dev.asfakul.yumeiro.data.SafeJsonFile.write(File(dir(context), "sources.json"), JsonArray(_state.value.installed.map { it.raw }).toString())
    }

    private fun publish() {
        val list = _state.value.installed.filter { File(dir(app), "${it.id}.js").isFile }
        val taken = HashSet<String>()
        SourceHub.publishVideo("js", SourceHub.Ecosystem.JAVASCRIPT, list.filter { it.itemType == 1 }
            .map { JsVideoProvider(it, SourceHub.uniqueName(it.name, it.lang, taken).also(taken::add)) })
        SourceHub.publishManga("js", list.filter { it.itemType == 0 }.map { JsMangaProvider(it) })
    }
}
