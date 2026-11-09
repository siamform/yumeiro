/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.net.Uri
import android.util.Log
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.utils.extractorApis
import dalvik.system.PathClassLoader
import dev.asfakul.yumeiro.network.NetworkClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Installs, loads, updates and removes CloudStream-compatible extensions.
 *
 * Loading follows CloudStream's own PluginManager: the `.cs3` file is made read-only (required on
 * Android 14+ for dynamic code), opened with a [PathClassLoader] whose parent is the app's class
 * loader (so it links against the bundled CloudStream library), its `manifest.json` names the
 * plugin class, and the plugin's `load()` registers MainAPI providers / extractors in [APIHolder].
 *
 * Safety & robustness:
 *  - Only files that verify against the published sha256 (when one is published) are loaded.
 *  - A broken or incompatible extension is isolated: any [Throwable] while loading is caught, the
 *    extension is marked with an error and everything else keeps working.
 *  - All state lives in one JSON file written atomically; files are stored per content hash so an
 *    update never overwrites a file that is still loaded.
 */
private object PluginManagerHook {
    fun currentlyLoading(path: String?) { com.lagradost.cloudstream3.plugins.PluginManager.currentlyLoading = path }
}

object ExtensionManager {
    private const val TAG = "Extensions"
    private const val STATE_VERSION = 1
    private const val MAX_FILE_BYTES = 25L * 1024 * 1024
    /** Built-in extension installed on first run (public-domain films & TV from archive.org). */
    const val DEFAULT_REPO = "https://raw.githubusercontent.com/Luna712/Luna712-CloudStream-Extensions/master/repo.json"
    const val DEFAULT_INTERNAL_NAME = "InternetArchiveProvider"
    private const val LEGACY_DEFAULT_MIGRATION = "migration:no-default-extension"
    val DEFAULT_KEY: String get() = RemoteExtension.keyOf(DEFAULT_REPO, DEFAULT_INTERNAL_NAME)

    data class RepoRef(val url: String, val name: String, val addedAt: Long)

    /** Runtime state of one installed extension. */
    data class LoadInfo(val providers: List<String> = emptyList(), val error: String? = null, val loaded: Boolean = false)

    data class Snapshot(
        val ready: Boolean = false,
        val installed: List<InstalledExtension> = emptyList(),
        val runtime: Map<String, LoadInfo> = emptyMap(),
        val repos: List<RepoRef> = emptyList(),
        val busy: Set<String> = emptySet(),
        val lastSync: Long = 0,
        /** Increases whenever the set of usable providers changes (UI reloads home rows). */
        val generation: Int = 0,
    ) {
        fun info(key: String) = runtime[key] ?: LoadInfo()
        val enabledCount: Int get() = installed.count { it.enabled && runtime[it.key]?.loaded == true }
    }

    open class ExtensionException(message: String, cause: Throwable? = null) : Exception(message, cause)

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    /** Plugins currently loaded, by installed key. */
    private val plugins = HashMap<String, BasePlugin>()
    private var removedDefaults = mutableSetOf<String>()
    @Volatile private var started = false

    private fun dir(context: Context) = File(context.filesDir, "extensions").apply { mkdirs() }
    private fun pluginDir(context: Context) = File(dir(context), "plugins").apply { mkdirs() }
    private fun stateFile(context: Context) = File(dir(context), "state.json")

    /** Starts the engine once per process: restores state and loads enabled extensions. */
    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        scope.launch { runCatching { ensureLoaded(app) }.onFailure { Log.e(TAG, "start", it) } }
        // Other ecosystems load in parallel; they publish into SourceHub as they become ready.
        runCatching { dev.asfakul.yumeiro.sources.Bridges.start(app) }.onFailure { Log.e(TAG, "bridges", it) }
    }

    /** Waits until saved extensions are loaded. Safe to call many times. */
    suspend fun ensureLoaded(context: Context) = withContext(Dispatchers.IO) {
        if (_state.value.ready) return@withContext
        mutex.withLock {
            if (_state.value.ready) return@withLock
            OnlineRuntime.init(context)
            readState(context)
            val runtime = HashMap<String, LoadInfo>()
            for (ext in _state.value.installed) {
                runtime[ext.key] = if (ext.enabled) loadInternal(context, ext) else LoadInfo()
            }
            cleanupFiles(context)
            _state.update { it.copy(ready = true, runtime = runtime, generation = it.generation + 1) }
        }
    }

    // region providers

    /** Loaded plugins by file path (CloudStream's PluginManager.plugins). */
    fun loadedPlugins(): Map<String, BasePlugin> = synchronized(plugins) {
        plugins.values.mapNotNull { p -> p.filename?.let { it to p } }.toMap()
    }

    /** Loaded plugin of an installed extension. */
    fun pluginOf(key: String): BasePlugin? = synchronized(plugins) { plugins[key] }

    /** Installed extension that owns the plugin file [path]. */
    fun installedForFile(path: String): InstalledExtension? {
        val name = File(path).name
        return _state.value.installed.firstOrNull { it.fileName == name }
    }

    fun pluginFile(context: Context, ext: InstalledExtension): File = File(pluginDir(context), ext.fileName)

    /** Providers of enabled, loaded extensions. */
    fun providers(): List<MainAPI> {
        val enabled = _state.value.installed.filter { it.enabled }.map { it.key }.toSet()
        val files = synchronized(plugins) { plugins.filterKeys { it in enabled }.values.mapNotNull { it.filename }.toSet() }
        val cloudstream = runCatching { APIHolder.allProviders.toList() }.getOrDefault(emptyList())
            .filter { it.sourcePlugin in files }
        // Aniyomi, Stremio, JavaScript and home-server sources join through the same MainAPI shape.
        return (cloudstream + dev.asfakul.yumeiro.sources.SourceHub.providers())
            .distinctBy { it.name }
            .also { ProviderOverrides.apply(it) }
    }

    /** Called by [dev.asfakul.yumeiro.sources.SourceHub] when bridged sources change, so screens reload. */
    fun bumpGeneration() = _state.update { it.copy(generation = it.generation + 1) }

    fun provider(name: String): MainAPI? = providers().firstOrNull { it.name == name }

    /** Extension that registered [api]. */
    fun extensionOf(api: MainAPI): InstalledExtension? {
        val key = synchronized(plugins) { plugins.entries.firstOrNull { it.value.filename == api.sourcePlugin }?.key } ?: return null
        return _state.value.installed.firstOrNull { it.key == key }
    }

    // endregion

    // region install / remove

    /** Downloads and installs (or updates) [remote]. Throws [ExtensionException] with a friendly message. */
    suspend fun install(
        context: Context, remote: RemoteExtension, review: ExtensionReview.Mode = ExtensionReview.Mode.ALWAYS,
    ): InstalledExtension = withContext(Dispatchers.IO) {
        ensureLoaded(context)
        if (remote.apiVersion > OnlineRuntime.API_VERSION) throw ExtensionException("${remote.name} needs a newer app version")
        markBusy(remote.key, true)
        try {
            val bytes = downloadFirst(listOfNotNull(remote.url, remote.sourceUrl).distinct())
            val sha = sha256(bytes)
            // Community/discovered entries point at the author's live file, which may have been
            // rebuilt since the market index (up to 6 h old) recorded its hash.
            val liveFile = remote.origin == "community" || remote.origin == "discovered"
            if (remote.sha256 != null && remote.sha256 != sha && !liveFile) throw ExtensionException("Download of ${remote.name} is corrupted (checksum mismatch)")
            val isUpdate = _state.value.installed.any { it.key == remote.key }
            gate(review, remote.name, remote.version, remote.repoName, isUpdate, bytes)
            mutex.withLock {
                val previous = _state.value.installed.firstOrNull { it.key == remote.key }
                val ext = InstalledExtension(
                    internalName = remote.internalName, name = remote.name, version = remote.version,
                    repoUrl = remote.repoUrl, repoName = remote.repoName, fileName = "", sourceUrl = remote.sourceUrl ?: remote.url,
                    sha256 = sha, description = remote.description, authors = remote.authors, tvTypes = remote.tvTypes,
                    language = remote.language, iconUrl = remote.iconUrl, enabled = previous?.enabled ?: true,
                    autoUpdate = previous?.autoUpdate ?: true, installedAt = previous?.installedAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
                installBytes(context, ext, bytes, previous)
            }
        } finally { markBusy(remote.key, false) }
    }

    /** Adds an extension from a local `.cs3` file picked by the user. */
    suspend fun installFromFile(context: Context, uri: Uri, displayName: String?, review: ExtensionReview.Mode = ExtensionReview.Mode.ALWAYS): InstalledExtension = withContext(Dispatchers.IO) {
        ensureLoaded(context)
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buf); if (n < 0) break
                total += n
                if (total > MAX_FILE_BYTES) throw ExtensionException("This file is too large to be an extension")
                out.write(buf, 0, n)
            }
            out.toByteArray()
        } ?: throw ExtensionException("Could not open the file")
        installManual(context, bytes, "file", displayName, review)
    }

    /** Adds an extension from a direct `.cs3` link. */
    suspend fun installFromUrl(context: Context, url: String, review: ExtensionReview.Mode = ExtensionReview.Mode.ALWAYS): InstalledExtension = withContext(Dispatchers.IO) {
        ensureLoaded(context)
        val clean = url.trim()
        if (!clean.startsWith("https://") && !clean.startsWith("http://")) throw ExtensionException("Enter a link that starts with https://")
        installManual(context, downloadFirst(listOf(clean)), clean, clean.substringAfterLast('/').substringBefore('?'), review)
    }

    private suspend fun installManual(context: Context, bytes: ByteArray, source: String, displayName: String?, review: ExtensionReview.Mode): InstalledExtension {
        val manifest = readManifest(bytes) ?: throw ExtensionException("This isn't a CloudStream extension (.cs3)")
        val internal = (displayName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: manifest.name ?: "Extension")
            .replace(Regex("[^A-Za-z0-9_.\\-]"), "_").take(80)
        val name = manifest.name ?: internal
        val repoUrl = "manual:$internal"
        val isUpdate = _state.value.installed.any { it.key == RemoteExtension.keyOf(repoUrl, internal) }
        gate(review, name, manifest.version, if (source == "file") "a file on this phone" else source, isUpdate, bytes)
        return mutex.withLock {
            val previous = _state.value.installed.firstOrNull { it.key == RemoteExtension.keyOf(repoUrl, internal) }
            val ext = InstalledExtension(
                internalName = internal, name = name, version = manifest.version ?: 0, repoUrl = repoUrl, repoName = "Added manually",
                fileName = "", sourceUrl = source, sha256 = sha256(bytes), manual = true, autoUpdate = false,
                installedAt = previous?.installedAt ?: System.currentTimeMillis(),
            )
            installBytes(context, ext, bytes, previous)
        }
    }

    /** Runs the safety check and asks the user (see [ExtensionReview]); throws when not approved. */
    private suspend fun gate(mode: ExtensionReview.Mode, name: String, version: Int?, source: String, isUpdate: Boolean, bytes: ByteArray) {
        val report = ExtensionScanner.scan(bytes)
        lastReports[name] = report
        if (!ExtensionReview.decide(mode, name, version, source, isUpdate, report)) {
            if (mode == ExtensionReview.Mode.AUTO || !ExtensionReview.hostAttached) {
                throw ExtensionException("$name was held back: the safety check flagged it (${report.findings.firstOrNull()?.title ?: report.summary})")
            }
            throw ExtensionReview.Declined(name)
        }
    }

    private val lastReports = java.util.concurrent.ConcurrentHashMap<String, ExtensionScanner.Report>()

    /** Safety report of an installed extension's file (scanned on demand, cached by file). */
    suspend fun reportFor(context: Context, ext: InstalledExtension): ExtensionScanner.Report? = withContext(Dispatchers.IO) {
        val f = pluginFile(context, ext)
        if (!f.exists()) return@withContext null
        reportCache[f.name] ?: runCatching { ExtensionScanner.scan(f.readBytes()) }.getOrNull()?.also { reportCache[f.name] = it }
    }
    private val reportCache = java.util.concurrent.ConcurrentHashMap<String, ExtensionScanner.Report>()

    /** Result of a fresh update check for one extension. */
    sealed class UpdateCheck {
        data object UpToDate : UpdateCheck()
        data class Available(val remote: RemoteExtension) : UpdateCheck()
        data class Unavailable(val reason: String) : UpdateCheck()
    }

    /**
     * Asks the extension's own repository right now (not the cached market) whether there's a newer
     * build. Manual installs from a link are re-downloaded and compared by checksum.
     */
    suspend fun checkUpdate(context: Context, ext: InstalledExtension): UpdateCheck = withContext(Dispatchers.IO) {
        if (ext.manual) {
            val src = ext.sourceUrl.takeIf { it.startsWith("http") } ?: return@withContext UpdateCheck.Unavailable("Added from a file — install a newer file to update it")
            val bytes = runCatching { downloadFirst(listOf(src)) }.getOrElse { return@withContext UpdateCheck.Unavailable(OnlineErrors.friendly(it)) }
            if (sha256(bytes) == ext.sha256) return@withContext UpdateCheck.UpToDate
            val m = readManifest(bytes) ?: return@withContext UpdateCheck.Unavailable("The link no longer points to an extension")
            return@withContext UpdateCheck.Available(RemoteExtension(
                internalName = ext.internalName, name = m.name ?: ext.name, url = src, version = m.version ?: ext.version,
                repoUrl = ext.repoUrl, repoName = ext.repoName, origin = "manual",
            ))
        }
        val fresh = runCatching { ExtensionSources.repoPlugins(ext.repoUrl, force = true).second }.getOrNull()
            ?.filter { it.internalName == ext.internalName }.orEmpty()
        val pool = fresh.ifEmpty {
            runCatching { ExtensionSources.all(context, force = true).items }.getOrDefault(emptyList()).filter { it.key == ext.key }
        }
        if (pool.isEmpty()) return@withContext UpdateCheck.Unavailable("Its repository didn't answer or no longer lists it")
        val newer = updatesForTests(listOf(ext), pool.map { it.copy(repoUrl = ext.repoUrl) }).firstOrNull()?.second
        if (newer != null) UpdateCheck.Available(newer) else UpdateCheck.UpToDate
    }

    /** Installs the update found by [checkUpdate] (manual installs keep their own entry). */
    suspend fun applyUpdate(context: Context, ext: InstalledExtension, remote: RemoteExtension): InstalledExtension =
        if (ext.manual) installFromUrl(context, remote.url) else install(context, remote)

    /** Writes [bytes], swaps the old version out and loads the new one. Caller holds [mutex]. */
    private fun installBytes(context: Context, ext: InstalledExtension, bytes: ByteArray, previous: InstalledExtension?): InstalledExtension {
        if (bytes.size < 64) throw ExtensionException("The download is empty")
        if (readManifest(bytes) == null) throw ExtensionException("${ext.name} is not a valid extension file")
        val safe = ext.internalName.replace(Regex("[^A-Za-z0-9_.\\-]"), "_")
        val file = File(pluginDir(context), "$safe-${ext.sha256.take(12)}.cs3")
        if (!file.exists()) {
            val tmp = File(file.parentFile, file.name + ".part")
            tmp.delete()
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(file)) { tmp.delete(); throw ExtensionException("Could not save ${ext.name}") }
        }
        val installed = ext.copy(fileName = file.name)
        // Unload the old version first so its providers don't show up twice.
        if (previous != null) unloadInternal(previous.key)
        val info = if (installed.enabled) loadInternal(context, installed) else LoadInfo()
        if (installed.enabled && !info.loaded) {
            // Keep the previous working version when the new one can't load.
            if (previous != null && previous.fileName != file.name) {
                File(pluginDir(context), previous.fileName).takeIf { it.exists() }?.let { loadInternal(context, previous) }
                deleteFile(file)
            } else if (previous == null) deleteFile(file)
            throw ExtensionException("${ext.name} can't run in this app: ${info.error ?: "unknown error"}")
        }
        if (previous != null && previous.fileName != file.name) deleteFile(File(pluginDir(context), previous.fileName))
        removedDefaults.remove(installed.key)
        _state.update { s ->
            s.copy(
                installed = s.installed.filterNot { it.key == installed.key } + installed,
                runtime = s.runtime + (installed.key to info),
                generation = s.generation + 1,
            )
        }
        writeState(context)
        return installed
    }

    suspend fun uninstall(context: Context, key: String) = withContext(Dispatchers.IO) {
        ensureLoaded(context)
        mutex.withLock {
            val ext = _state.value.installed.firstOrNull { it.key == key } ?: return@withLock
            unloadInternal(key)
            deleteFile(File(pluginDir(context), ext.fileName))
            if (key == DEFAULT_KEY) removedDefaults.add(key)
            _state.update { s -> s.copy(installed = s.installed.filterNot { it.key == key }, runtime = s.runtime - key, generation = s.generation + 1) }
            writeState(context)
        }
    }

    suspend fun setEnabled(context: Context, key: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        ensureLoaded(context)
        mutex.withLock {
            val ext = _state.value.installed.firstOrNull { it.key == key } ?: return@withLock
            val updated = ext.copy(enabled = enabled)
            val info = if (enabled) loadInternal(context, updated) else { unloadInternal(key); LoadInfo() }
            _state.update { s ->
                s.copy(installed = s.installed.map { if (it.key == key) updated else it }, runtime = s.runtime + (key to info), generation = s.generation + 1)
            }
            writeState(context)
        }
    }

    suspend fun setAutoUpdate(context: Context, key: String, auto: Boolean) = withContext(Dispatchers.IO) {
        ensureLoaded(context)
        mutex.withLock {
            _state.update { s -> s.copy(installed = s.installed.map { if (it.key == key) it.copy(autoUpdate = auto) else it }) }
            writeState(context)
        }
    }

    private fun markBusy(key: String, busy: Boolean) = _state.update { it.copy(busy = if (busy) it.busy + key else it.busy - key) }

    // endregion

    // region repositories

    /** Fetches and validates a repository, then remembers it. Returns the parsed repository. */
    suspend fun addRepository(context: Context, input: String): ExtensionRepo = withContext(Dispatchers.IO) {
        ensureLoaded(context)
        val url = normalizeRepoUrl(input)
        if (!url.startsWith("https://") && !url.startsWith("http://")) throw ExtensionException("Enter the repository link (https://…/repo.json)")
        val repo = try { ExtensionSources.fetchRepo(url) } catch (e: ExtensionException) { throw e } catch (e: Exception) {
            throw ExtensionException(OnlineErrors.friendly(e, "Could not read this repository"))
        }
        mutex.withLock {
            _state.update { s ->
                s.copy(repos = s.repos.filterNot { it.url.equals(url, true) } + RepoRef(url, repo.name, System.currentTimeMillis()))
            }
            writeState(context)
        }
        ExtensionSources.invalidate(url)
        repo
    }

    suspend fun removeRepository(context: Context, url: String) = withContext(Dispatchers.IO) {
        ensureLoaded(context)
        mutex.withLock {
            _state.update { s -> s.copy(repos = s.repos.filterNot { it.url == url }) }
            writeState(context)
        }
    }

    // endregion

    // region sync & updates

    /** Installed extensions with a newer version in [available]. */
    fun updatesFrom(available: List<RemoteExtension>): List<Pair<InstalledExtension, RemoteExtension>> =
        updatesForTests(_state.value.installed, available)

    internal fun updatesForTests(installed: List<InstalledExtension>, available: List<RemoteExtension>): List<Pair<InstalledExtension, RemoteExtension>> {
        val byKey = available.associateBy { it.key }
        return installed.mapNotNull { ext ->
            if (ext.manual) return@mapNotNull null
            val remote = byKey[ext.key] ?: return@mapNotNull null
            val newer = remote.version > ext.version || (remote.version == ext.version && (remote.origin == "curated" || remote.origin == "user") && remote.sha256 != null && remote.sha256 != ext.sha256)
            if (newer) ext to remote else null
        }
    }

    /**
     * Called when the app opens (like the Stream playlist sync): refreshes the market and user
     * repositories at most every few hours, and applies updates
     * to extensions that have automatic updates on. Never throws.
     */
    suspend fun sync(context: Context, force: Boolean = false): SyncResult = withContext(Dispatchers.IO) {
        runCatching { ensureLoaded(context) }
        val now = System.currentTimeMillis()
        if (!force && now - _state.value.lastSync < ExtensionMarket.CHECK_INTERVAL_MS && _state.value.lastSync > 0) return@withContext SyncResult()
        val available = runCatching { ExtensionSources.all(context, force) }.getOrElse { return@withContext SyncResult(error = OnlineErrors.friendly(it)) }
        val installedNow = mutableListOf<String>()
        // 1.8.0 installed Internet Archive automatically; its "featured" feed is random recent uploads
        // (often news/political clips), so it is no longer a default. Remove that automatic install once.
        if (LEGACY_DEFAULT_MIGRATION !in removedDefaults) {
            if (_state.value.installed.any { it.key == DEFAULT_KEY }) runCatching { uninstall(context, DEFAULT_KEY) }
            mutex.withLock { removedDefaults.add(LEGACY_DEFAULT_MIGRATION); writeState(context) }
        }
        val updated = mutableListOf<String>()
        val failed = mutableListOf<String>()
        for ((ext, remote) in updatesFrom(available.items)) {
            if (!ext.autoUpdate) continue
            runCatching { install(context, remote, ExtensionReview.Mode.AUTO) }.onSuccess { updated += it.name }.onFailure { failed += ext.name }
        }
        mutex.withLock { _state.update { it.copy(lastSync = now) }; writeState(context) }
        SyncResult(installed = installedNow, updated = updated, failed = failed, pending = updatesFrom(available.items).size)
    }

    data class SyncResult(
        val installed: List<String> = emptyList(),
        val updated: List<String> = emptyList(),
        val failed: List<String> = emptyList(),
        val pending: Int = 0,
        val error: String? = null,
    )

    // endregion

    // region loading

    private fun loadInternal(context: Context, ext: InstalledExtension): LoadInfo {
        val file = File(pluginDir(context), ext.fileName)
        if (!file.exists()) return LoadInfo(error = "File missing — reinstall it")
        synchronized(plugins) { plugins[ext.key] }?.let { p ->
            return LoadInfo(providersOf(p.filename), loaded = true)
        }
        return try {
            runCatching { file.setReadOnly() }
            val loader = PathClassLoader(file.absolutePath, context.classLoader)
            val manifestText = loader.getResourceAsStream("manifest.json")?.use { it.readBytes().decodeToString() }
                ?: return LoadInfo(error = "No manifest in the extension")
            val manifest = JSONObject(manifestText)
            val className = manifest.optString("pluginClassName").ifBlank { return LoadInfo(error = "Manifest has no plugin class") }
            val instance = loader.loadClass(className).getDeclaredConstructor().newInstance() as BasePlugin
            instance.filename = file.absolutePath
            if (manifest.optBoolean("requiresResources") && instance is Plugin) {
                runCatching {
                    val assets = AssetManager::class.java.getDeclaredConstructor().newInstance()
                    AssetManager::class.java.getMethod("addAssetPath", String::class.java).invoke(assets, file.absolutePath)
                    @Suppress("DEPRECATION")
                    instance.resources = Resources(assets, context.resources.displayMetrics, context.resources.configuration)
                }
            }
            PluginManagerHook.currentlyLoading(file.absolutePath)
            // CloudStream loads plugins with the activity as context; some extensions rely on that.
            val loadContext = com.lagradost.cloudstream3.CommonActivity.activity ?: context
            try {
                if (instance is Plugin) instance.load(loadContext) else instance.load()
            } finally { PluginManagerHook.currentlyLoading(null) }
            synchronized(plugins) { plugins[ext.key] = instance }
            val mine = APIHolder.allProviders.toList().filter { it.sourcePlugin == file.absolutePath }
            mine.forEach { api -> runCatching { api.init() } }
            Log.i(TAG, "Loaded ${ext.internalName}: ${mine.map { it.name }}")
            LoadInfo(mine.map { it.name }, loaded = true)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to load ${ext.internalName}", t)
            // Remove anything it managed to register before failing.
            removeRegistrations(file.absolutePath)
            LoadInfo(error = describeLoadError(t))
        }
    }

    private fun providersOf(filename: String?): List<String> =
        APIHolder.allProviders.toList().filter { it.sourcePlugin == filename }.map { it.name }

    private fun unloadInternal(key: String) {
        val plugin = synchronized(plugins) { plugins.remove(key) } ?: return
        runCatching { plugin.beforeUnload() }
        plugin.filename?.let { removeRegistrations(it) }
    }

    private fun removeRegistrations(filename: String) {
        runCatching {
            val apis = APIHolder.allProviders.toList().filter { it.sourcePlugin == filename }
            apis.forEach { APIHolder.allProviders.remove(it); APIHolder.removePluginMapping(it) }
        }
        runCatching { extractorApis.toList().filter { it.sourcePlugin == filename }.forEach { extractorApis.remove(it) } }
    }

    internal fun describeLoadError(t: Throwable): String {
        val root = generateSequence(t) { it.cause }.last()
        val missing = root.message?.substringAfterLast('/')?.substringAfterLast('.')?.substringBefore(';')?.take(60)
        return when (root) {
            is NoClassDefFoundError, is ClassNotFoundException ->
                if (root.message.orEmpty().contains("popcorn", ignoreCase = true)) "made for a different app (not CloudStream-compatible)"
                else "uses a CloudStream part this version doesn't have yet${missing?.let { " ($it)" } ?: ""}. Update Yumeiro or try another source"
            is NoSuchMethodError, is NoSuchFieldError, is AbstractMethodError, is IncompatibleClassChangeError ->
                "built for a different CloudStream version${missing?.let { " ($it)" } ?: ""}. Look for an updated copy of this extension"
            is ClassCastException -> "not a CloudStream extension"
            is OutOfMemoryError -> "too large to load on this device"
            else -> root.message?.take(120) ?: root.javaClass.simpleName
        }
    }

    // endregion

    // region files & state

    private fun readManifest(bytes: ByteArray): BasePlugin.Manifest? = runCatching {
        val tmp = File.createTempFile("ext", ".zip")
        try {
            tmp.writeBytes(bytes)
            ZipFile(tmp).use { zip ->
                if (zip.getEntry("classes.dex") == null) return null
                val entry = zip.getEntry("manifest.json") ?: return null
                val o = JSONObject(zip.getInputStream(entry).use { it.readBytes().decodeToString() })
                if (o.optString("pluginClassName").isBlank()) return null
                BasePlugin.Manifest().apply {
                    name = o.optStringOrNull("name")
                    pluginClassName = o.optString("pluginClassName")
                    version = if (o.has("version")) o.optInt("version") else null
                    requiresResources = o.optBoolean("requiresResources")
                }
            }
        } finally { tmp.delete() }
    }.getOrNull()

    private fun downloadFirst(urls: List<String>): ByteArray {
        var last: Exception? = null
        for (u in urls) {
            try {
                val req = Request.Builder().url(u).header("Cache-Control", "no-cache").build()
                NetworkClient.get().newCall(req).execute().use { r ->
                    if (!r.isSuccessful) throw ExtensionException("Server answered HTTP ${r.code}")
                    val len = r.body.contentLength()
                    if (len > MAX_FILE_BYTES) throw ExtensionException("The extension is too large")
                    val src = r.body.source()
                    if (src.request(MAX_FILE_BYTES + 1)) throw ExtensionException("The extension is too large")
                    return src.buffer.readByteArray()
                }
            } catch (e: Exception) { last = e }
        }
        val e = last ?: ExtensionException("Nothing to download")
        throw if (e is ExtensionException) e else ExtensionException(OnlineErrors.friendly(e, "Download failed"), e)
    }

    private fun deleteFile(f: File) { runCatching { f.setWritable(true); f.delete() } }

    /** Removes files no installed extension points to (failed updates, crashes mid-install). */
    private fun cleanupFiles(context: Context) {
        val keep = _state.value.installed.map { it.fileName }.toSet()
        pluginDir(context).listFiles()?.forEach { f -> if (f.name !in keep) deleteFile(f) }
    }

    private fun readState(context: Context) {
        val f = stateFile(context)
        val o = runCatching { if (f.exists()) JSONObject(f.readText()) else null }.getOrNull()
        if (o == null) { _state.update { it.copy(installed = emptyList(), repos = emptyList()) }; return }
        val installed = o.optJSONArray("installed")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(InstalledExtension::fromJson) } }.orEmpty()
        val repos = o.optJSONArray("repos")?.let { a ->
            (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { r -> r.optString("url").ifBlank { null }?.let { RepoRef(it, r.optString("name").ifBlank { it }, r.optLong("addedAt")) } } }
        }.orEmpty()
        removedDefaults = o.optJSONArray("removedDefaults").strings().toMutableSet()
        _state.update { it.copy(installed = installed.distinctBy { e -> e.key }, repos = repos, lastSync = o.optLong("lastSync")) }
    }

    private fun writeState(context: Context) {
        val s = _state.value
        val o = JSONObject()
            .put("version", STATE_VERSION)
            .put("installed", JSONArray().apply { s.installed.forEach { put(it.toJson()) } })
            .put("repos", JSONArray().apply { s.repos.forEach { put(JSONObject().put("url", it.url).put("name", it.name).put("addedAt", it.addedAt)) } })
            .put("removedDefaults", JSONArray(removedDefaults.toList()))
            .put("lastSync", s.lastSync)
        val f = stateFile(context)
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(o.toString())
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    internal fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** For tests: forget everything loaded in this process. */
    internal fun resetForTests() {
        synchronized(plugins) { plugins.keys.toList().forEach { unloadInternal(it) } }
        removedDefaults.clear()
        started = false
        _state.value = Snapshot()
    }

    // endregion
}
