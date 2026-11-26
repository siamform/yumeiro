/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.ecosystem

import android.content.Context
import android.util.Log
import com.lagradost.cloudstream3.MainAPI
import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.data.SafeJsonFile
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.sources.SourceHub
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/**
 * ADR-047: what an ecosystem must provide to run its plugins. One object per ecosystem; adding a
 * fourth ecosystem is one parser in [RepoFormats] plus one adapter registered in
 * [EcosystemRegistry.adapters], with no change to the registry, the store or the UI.
 */
interface RuntimeAdapter {
    val ecosystem: SourceHub.Ecosystem
    /** Remote kill switch for this ecosystem only. */
    val flag: FeatureFlags.Flag
    /** Files of [PluginEntry.files] that must download for an install to count. */
    fun required(p: PluginEntry): Set<String>
    /** Runs once after download (for example to read home rows); the result is saved with the plugin. */
    suspend fun prepare(context: Context, plugin: EcosystemRegistry.Installed, dir: File): JsonObject = JsonObject(emptyMap())
    /** The catalog source, or null for a stream-only ecosystem (Nuvio, ADR-057) that publishes none. */
    fun build(context: Context, plugin: EcosystemRegistry.Installed, dir: File, displayName: String): MainAPI?
}

/**
 * ADR-047: the one repository registry for script ecosystems (Vega, Nuvio, SkyStream and later
 * ones). It stores the user's repositories and installed plugins, downloads code with caps and
 * hashes, and publishes each ecosystem's sources to [SourceHub] under its own bridge key, so a
 * broken plugin or ecosystem can't take another one down. CloudStream repositories are never
 * handled here: their loader is unchanged.
 *
 * No repository is built in. Files: `filesDir/scripts/registry.json` and
 * `filesDir/scripts/<ecosystem>/<repo key>/<plugin id>/`.
 */
object EcosystemRegistry {
    private const val TAG = "EcosystemRegistry"
    const val MAX_MANIFEST_BYTES = 1L * 1024 * 1024
    const val MAX_FILE_BYTES = 2L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 50L * 1024 * 1024

    /** Ecosystems that can run in this version (Vega ADR-047, Nuvio ADR-057, SkyStream ADR-058 behind scripts_skystream). */
    val adapters: Map<SourceHub.Ecosystem, RuntimeAdapter> = listOf<RuntimeAdapter>(VegaAdapter, NuvioAdapter, SkyStreamAdapter).associateBy { it.ecosystem }

    /** Script ecosystems the registry knows the format of. */
    val known = setOf(SourceHub.Ecosystem.VEGA, SourceHub.Ecosystem.NUVIO, SourceHub.Ecosystem.SKYSTREAM)

    data class Repo(val url: String, val ecosystem: SourceHub.Ecosystem, val name: String, val addedAt: Long) {
        val key: String get() = sha256(url).take(12)
    }

    data class Installed(
        val repoUrl: String,
        val ecosystem: SourceHub.Ecosystem,
        val id: String,
        val name: String,
        val version: String,
        val lang: String?,
        /** File name → SHA-256 of the saved code. */
        val hashes: Map<String, String>,
        val extra: JsonObject = JsonObject(emptyMap()),
    ) {
        /** Unique across repos and ecosystems. */
        val key: String get() = "${ecosystem.name.lowercase()}:${sha256(repoUrl).take(12)}:$id"
        val storeName: String get() = key.replace(':', '_')
    }

    data class State(val repos: List<Repo> = emptyList(), val installed: List<Installed> = emptyList())

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Last publish error per ecosystem, for Source status and the Script repos tab. */
    private val _problems = MutableStateFlow<Map<String, String>>(emptyMap())
    val problems: StateFlow<Map<String, String>> = _problems.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    @Volatile private var loaded = false
    @Volatile private var started = false
    private lateinit var app: Context

    fun start(context: Context) {
        if (started) return
        started = true
        app = context.applicationContext
        scope.launch {
            runCatching { ensureLoaded(app); publish(app) }.onFailure { Log.e(TAG, "start", it) }
            runCatching { downloadMissing(app) }.onFailure { Log.w(TAG, "downloadMissing: ${it.message}") }
        }
    }

    /**
     * B16: plugins on the list whose files are all gone (a backup restored without extension files,
     * or cleared storage) are downloaded again from their repository. Files that are present but
     * changed stay refused (that is the tamper check, not a missing download).
     */
    internal fun missingFiles(context: Context, list: List<Installed>): List<Installed> =
        list.filter { p -> p.hashes.isNotEmpty() && p.hashes.keys.none { File(dirOf(context, p), it).isFile } }

    suspend fun downloadMissing(context: Context): Int = withContext(Dispatchers.IO) {
        ensureLoaded(context)
        val missing = missingFiles(context, _state.value.installed)
        if (missing.isEmpty()) return@withContext 0
        val offer = available(context)
        var n = 0
        for (p in missing) {
            val hit = offer.firstOrNull { (r, e) -> r.url == p.repoUrl && RepoFormats.safeId(e.id) == p.id } ?: continue
            install(context, hit.first, hit.second).onSuccess { n++ }.onFailure { Log.w(TAG, "re-download ${p.name}: ${it.message}") }
        }
        Log.i(TAG, "B16 downloaded again: $n of ${missing.size}")
        n
    }

    private fun root(c: Context) = File(c.filesDir, "scripts").apply { mkdirs() }
    private fun file(c: Context) = File(root(c), "registry.json")
    fun dirOf(c: Context, p: Installed) = File(root(c), "${p.ecosystem.name.lowercase()}/${sha256(p.repoUrl).take(12)}/${p.id}")

    private fun ensureLoaded(context: Context) = synchronized(lock) {
        if (loaded) return@synchronized
        val saved = SafeJsonFile.read(file(context)) { t -> decode(RepoFormats.json.parseToJsonElement(t) as JsonObject) }.value ?: State()
        // F-19-EXT-2: this profile's repos and plugins only; the others' are written back; a removed
        // profile's are dropped with their code and storage.
        val (myRepos, theirRepos, goneRepos) = SCOPE.splitAll(context, SCOPE.SCRIPT_REPOS, saved.repos.distinctBy { it.url }) { it.url }
        val (mine, theirs, gone) = SCOPE.splitAll(context, SCOPE.SCRIPT_PLUGINS, saved.installed.distinctBy { it.key }) { it.key }
        otherState = State(theirRepos, theirs)
        gone.forEach { p -> dirOf(context, p).deleteRecursively(); context.getSharedPreferences("script_kv_${p.storeName}", Context.MODE_PRIVATE).edit().clear().apply() }
        _state.value = State((myRepos + _state.value.repos).distinctBy { it.url }, (mine + _state.value.installed).distinctBy { it.key })
        loaded = true
        if (goneRepos.isNotEmpty() || gone.isNotEmpty()) SafeJsonFile.write(file(context), encode(merged()).toString())
    }

    private fun change(context: Context, f: (State) -> State) = synchronized(lock) {
        ensureLoaded(context)
        _state.value = f(_state.value)
        SafeJsonFile.write(file(context), encode(merged()).toString())
    }

    private val SCOPE = dev.asfakul.yumeiro.profiles.ProfileScope
    /** F-19-EXT-2: other profiles' repos and plugins (on disk, never shown or loaded here). */
    private var otherState = State()

    /** This profile's lists plus the other profiles' (what the file holds). */
    private fun merged(): State {
        val s = _state.value
        return State(s.repos + otherState.repos.filter { o -> s.repos.none { it.url == o.url } },
            s.installed + otherState.installed.filter { o -> s.installed.none { it.key == o.key } })
    }

    @androidx.annotation.VisibleForTesting
    internal fun resetForTests() = synchronized(lock) { started = false; loaded = false; _state.value = State(); otherState = State(); _problems.value = emptyMap() }

    /** OWN-5 test hook: a loaded state without disk or network (start() then does nothing). */
    @androidx.annotation.VisibleForTesting
    internal fun setStateForTests(s: State) = synchronized(lock) { started = true; loaded = true; _state.value = s }

    // region repositories

    /** Reads [url] and returns what it is, without saving anything (Universal add uses this to route). */
    suspend fun inspect(context: Context, url: String): ParsedRepo = withContext(Dispatchers.IO) {
        val parsed = RepoFormats.parse(fetchText(context, url, MAX_MANIFEST_BYTES), url) ?: error("This repository file is not valid")
        if (parsed.ecosystem == SourceHub.Ecosystem.SKYSTREAM && parsed.children.isNotEmpty() &&
            FeatureFlags.on(context, SkyStreamAdapter.flag)) expandSky(parsed) { fetchText(context, it, MAX_MANIFEST_BYTES) } else parsed
    }

    /**
     * F-05-S (ADR-058): a SkyStream repo lists its plugins in `pluginLists` files (SkyStream v2.9.0
     * reads only those), and a mega repo lists child repos. Reads at most
     * [SkyStreamRepoParser.MAX_CHILDREN] files, one level of child repos, first entry of an id wins.
     * A list that fails is skipped (the others still count).
     */
    internal fun expandSky(repo: ParsedRepo, read: (String) -> String): ParsedRepo {
        val plugins = LinkedHashMap<String, PluginEntry>()
        repo.plugins.forEach { plugins.putIfAbsent(it.id, it) }
        val queue = ArrayDeque(repo.children.map { it to 0 })
        val seen = HashSet<String>()
        var reads = 0
        while (queue.isNotEmpty() && reads < SkyStreamRepoParser.MAX_CHILDREN) {
            val (u, depth) = queue.removeFirst()
            if (!seen.add(u)) continue
            reads++
            val root = runCatching { RepoFormats.json.parseToJsonElement(read(u)) }.onFailure { Log.w(TAG, "sky list $u: ${it.message}") }.getOrNull() ?: continue
            if (SkyStreamRepoParser.isPluginList(root)) {
                SkyStreamRepoParser.parsePluginList(root, u).forEach { plugins.putIfAbsent(it.id, it) }
            } else if (depth == 0 && runCatching { SkyStreamRepoParser.accepts(root) }.getOrDefault(false)) {
                SkyStreamRepoParser.parse(root, u).children.forEach { queue.addLast(it to 1) }
            }
        }
        return repo.copy(plugins = plugins.values.take(SkyStreamRepoParser.MAX_PLUGINS))
    }

    /** Adds a script repository. CloudStream repositories are refused: they belong to the existing loader. */
    suspend fun addRepo(context: Context, url: String): Result<ParsedRepo> = withContext(Dispatchers.IO) {
        runCatching {
            if (!started) start(context)
            val parsed = inspect(context, url)
            require(parsed.ecosystem in known) { "This is a ${parsed.ecosystem.label} repository; add it from its own section" }
            val adapter = adapters[parsed.ecosystem]
                ?: error("This is a ${parsed.ecosystem.label} repository. Yumeiro recognises it, but can't run ${parsed.ecosystem.label} plugins in this version yet. Nothing was added.")
            require(FeatureFlags.on(context, adapter.flag)) { "${parsed.ecosystem.label} sources are switched off in this version" }
            require(parsed.plugins.isNotEmpty()) { "That repository has no ${parsed.ecosystem.label} plugins" }
            change(context) { s ->
                otherState = otherState.copy(repos = otherState.repos.filterNot { it.url == url })
                s.copy(repos = s.repos.filterNot { it.url == url } + Repo(url, parsed.ecosystem, parsed.name, System.currentTimeMillis()))
            }
            SCOPE.claim(context, SCOPE.SCRIPT_REPOS, url)
            parsed
        }
    }

    fun removeRepo(context: Context, url: String) {
        val kept = SCOPE.release(context, SCOPE.SCRIPT_REPOS, url)
        change(context) { s ->
            if (kept) s.repos.firstOrNull { it.url == url }?.let { r -> otherState = otherState.copy(repos = otherState.repos.filterNot { it.url == url } + r) }
            s.copy(repos = s.repos.filterNot { it.url == url })
        }
    }

    /** Every plugin offered by the saved repositories that has a runtime, newest manifest first. */
    suspend fun available(context: Context): List<Pair<Repo, PluginEntry>> = withContext(Dispatchers.IO) {
        ensureLoaded(context)
        _state.value.repos.filter { adapters.containsKey(it.ecosystem) }.flatMap { r ->
            runCatching { inspect(context, r.url) }.onFailure { Log.w(TAG, "repo ${r.name}: ${it.message}") }
                .getOrNull()?.plugins.orEmpty().filter { !it.disabled }.map { r to it }
        }
    }

    // endregion

    // region plugins

    suspend fun install(context: Context, repo: Repo, entry: PluginEntry): Result<Installed> = withContext(Dispatchers.IO) {
        runCatching {
            val adapter = adapters[repo.ecosystem] ?: error("Yumeiro can't run ${repo.ecosystem.label} plugins in this version")
            val base = Installed(repo.url, repo.ecosystem, RepoFormats.safeId(entry.id), entry.name, entry.version, entry.lang, emptyMap())
            val dir = dirOf(context, base)
            val staging = File(dir.parentFile, dir.name + ".new").apply { deleteRecursively(); mkdirs() }
            val used = root(context).walkTopDown().filter { it.isFile }.sumOf { it.length() }
            val hashes = LinkedHashMap<String, String>()
            var total = 0L
            try {
                for ((fileName, url) in entry.files) {
                    val safe = RepoFormats.safeId(fileName)
                    val bytes = runCatching { fetchBytes(context, url, MAX_FILE_BYTES) }.getOrElse { e ->
                        if (fileName in adapter.required(entry)) throw IllegalStateException("Couldn't download $fileName: ${e.message}") else null
                    } ?: continue
                    total += bytes.size
                    require(used + total <= MAX_TOTAL_BYTES) { "Script sources are using all their space (50 MB). Remove some first." }
                    File(staging, safe).writeBytes(bytes)
                    hashes[safe] = sha256(bytes)
                }
                val missing = adapter.required(entry) - hashes.keys
                require(missing.isEmpty()) { "This plugin is missing ${missing.joinToString()}" }
                dir.deleteRecursively()
                require(staging.renameTo(dir)) { "Couldn't save the plugin" }
            } finally { staging.deleteRecursively() }
            val prepared = base.copy(hashes = hashes)
            val prepExtra = runCatching { adapter.prepare(context, prepared, dir) }.getOrDefault(JsonObject(emptyMap()))
            // F-05-N: the media types the manifest declares, for stream-only adapters that filter by them.
            val extra = if (entry.types.isEmpty()) prepExtra else JsonObject(prepExtra + ("types" to JsonArray(entry.types.map(::JsonPrimitive))))
            val done = prepared.copy(extra = extra)
            change(context) { s ->
                otherState = otherState.copy(installed = otherState.installed.filterNot { it.key == done.key })
                s.copy(installed = s.installed.filterNot { it.key == done.key } + done)
            }
            SCOPE.claim(context, SCOPE.SCRIPT_PLUGINS, done.key)
            publish(context)
            done
        }
    }

    fun uninstall(context: Context, key: String) {
        val p = _state.value.installed.firstOrNull { it.key == key } ?: return
        // F-19-EXT-2: another profile still has it: keep its code and storage for that profile.
        val kept = SCOPE.release(context, SCOPE.SCRIPT_PLUGINS, key)
        if (!kept) {
            dirOf(context, p).deleteRecursively()
            context.getSharedPreferences("script_kv_${p.storeName}", Context.MODE_PRIVATE).edit().clear().apply()
        }
        change(context) { s ->
            if (kept) otherState = otherState.copy(installed = otherState.installed.filterNot { it.key == key } + p)
            s.copy(installed = s.installed.filterNot { it.key == key })
        }
        publish(context)
    }

    /** True when the saved code still matches the hashes recorded at install. */
    fun intact(context: Context, p: Installed): Boolean = p.hashes.all { (name, h) -> File(dirOf(context, p), name).let { it.isFile && sha256(it.readBytes()) == h } }

    // endregion

    /**
     * Publishes each ecosystem on its own bridge key. A plugin that fails to build, or whose code
     * changed on disk, is left out and reported; the others and the other ecosystems go on.
     */
    fun publish(context: Context) {
        val c = context.applicationContext
        val problems = HashMap<String, String>()
        for (eco in known) {
            val adapter = adapters[eco]
            val apis = if (adapter == null || !FeatureFlags.on(c, adapter.flag)) emptyList() else {
                val taken = HashSet<String>()
                _state.value.installed.filter { it.ecosystem == eco }.mapNotNull { p ->
                    runCatching {
                        check(intact(c, p)) { "code changed on disk" }
                        if (eco == SourceHub.Ecosystem.NUVIO) NuvioAdapter.problemOf(p)?.let { problems[p.key] = it }
                        adapter.build(c, p, dirOf(c, p), SourceHub.uniqueName(p.name, p.lang, taken).also(taken::add))
                    }.onFailure { problems[p.key] = it.message ?: it.javaClass.simpleName; Log.w(TAG, "${p.key}: ${it.message}") }.getOrNull()
                }
            }
            runCatching { SourceHub.publishVideo(bridgeKey(eco), eco, apis) }.onFailure { problems[eco.name] = it.message.orEmpty() }
        }
        _problems.value = problems
    }

    fun bridgeKey(eco: SourceHub.Ecosystem) = "eco:" + eco.name.lowercase()

    // region io

    private fun fetchBytes(context: Context, url: String, cap: Long): ByteArray {
        require(url.startsWith("https://") || url.startsWith("http://")) { "Only web links can be used" }
        return NetworkClient.get(context).newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            val len = r.body.contentLength()
            require(len <= cap) { "The file is too big" }
            val src = r.body.source()
            src.request(cap + 1)
            require(src.buffer.size <= cap) { "The file is too big" }
            src.buffer.readByteArray()
        }
    }

    private fun fetchText(context: Context, url: String, cap: Long) = String(fetchBytes(context, url, cap), Charsets.UTF_8)

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { b ->
        val v = b.toInt() and 0xff
        HEX[v shr 4].toString() + HEX[v and 15]
    }
    private const val HEX = "0123456789abcdef"
    fun sha256(s: String): String = sha256(s.toByteArray())

    // endregion

    // region store format

    internal fun encode(s: State): JsonObject = buildJsonObject {
        put("v", 1)
        put("repos", JsonArray(s.repos.map { r -> buildJsonObject { put("url", r.url); put("ecosystem", r.ecosystem.name); put("name", r.name); put("addedAt", r.addedAt) } }))
        put("installed", JsonArray(s.installed.map { p ->
            buildJsonObject {
                put("repoUrl", p.repoUrl); put("ecosystem", p.ecosystem.name); put("id", p.id); put("name", p.name); put("version", p.version)
                p.lang?.let { put("lang", it) }
                put("hashes", JsonObject(p.hashes.mapValues { JsonPrimitive(it.value) }))
                put("extra", p.extra)
            }
        }))
    }

    internal fun decode(o: JsonObject): State {
        fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
        fun eco(n: String?) = SourceHub.Ecosystem.entries.firstOrNull { it.name == n }
        val repos = (o["repos"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { r ->
            Repo(r.s("url") ?: return@mapNotNull null, eco(r.s("ecosystem")) ?: return@mapNotNull null, r.s("name").orEmpty(), (r["addedAt"] as? JsonPrimitive)?.longOrNull ?: 0)
        }
        val installed = (o["installed"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { p ->
            Installed(
                p.s("repoUrl") ?: return@mapNotNull null, eco(p.s("ecosystem")) ?: return@mapNotNull null,
                p.s("id") ?: return@mapNotNull null, p.s("name").orEmpty(), p.s("version").orEmpty(), p.s("lang"),
                (p["hashes"] as? JsonObject).orEmpty().mapValues { (it.value as? JsonPrimitive)?.contentOrNull.orEmpty() },
                p["extra"] as? JsonObject ?: JsonObject(emptyMap()),
            )
        }
        return State(repos, installed)
    }

    // endregion
}
