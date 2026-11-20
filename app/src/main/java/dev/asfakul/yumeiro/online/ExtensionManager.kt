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

// Precompiled once (never during composition, scrolling or per call): see docs/STABILITY-R15.md.
private val RX_EXTENSIONMANAGER_1 = Regex("[^A-Za-z0-9_.\\-]")

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
        /** SPD-1: at least one extension is loaded and usable while the rest still load. */
        val partial: Boolean = false,
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
        /** SPD-1: the feed can show what is loaded so far. */
        val usable: Boolean get() = ready || partial
    }

    open class ExtensionException(message: String, cause: Throwable? = null) : Exception(message, cause)

    /** A background (AUTO) install the safety check refused: never installed silently (NBUG-16 NB16-6). */
    class HeldBack(val extension: String, reason: String) : ExtensionException("$extension was held back: the safety check flagged it ($reason)")

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    /** Plugins currently loaded, by installed key. */
    private val plugins = HashMap<String, BasePlugin>()

    /** Class loaders of the extensions loaded in this process (for restoring their fragments, G12 X8-3). */
    fun pluginClassLoaders(): List<ClassLoader> =
        synchronized(plugins) { plugins.values.mapNotNull { it.javaClass.classLoader } }.distinct()

    /** True while at least one extension is loaded (ADR-014: only then can extension code open windows). */
    fun anyPluginLoaded(): Boolean = synchronized(plugins) { plugins.isNotEmpty() }

    /**
     * ADR-014: (key, display name) of the loaded extension whose dex defines [className], or null.
     * The class must come from the plugin's own loader, not a parent (the app), so app classes never match.
     */
    fun extensionForClass(className: String): Pair<String, String>? {
        val loaded = synchronized(plugins) { plugins.toList() }
        for ((key, plugin) in loaded) {
            val loader = plugin.javaClass.classLoader ?: continue
            val cls = runCatching { Class.forName(className, false, loader) }.getOrNull() ?: continue
            if (cls.classLoader !== loader) continue
            val name = _state.value.installed.firstOrNull { it.key == key }?.name ?: plugin.javaClass.simpleName
            return key to name
        }
        return null
    }
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
        scope.launch {
            // First run: nothing wakes up until the welcome screen / explore tour is done or skipped.
            OnboardingGate.awaitOpen()
            runCatching { ensureLoaded(app) }.onFailure { Log.e(TAG, "start", it) }
        }
        // Round 12: after a restore (or a lost file) download missing extensions again.
        // G9-2: retried with back-off and on reconnect instead of once per process.
        dev.asfakul.yumeiro.backup.RepairRetry.launch(scope, "cloudstream") {
            OnboardingGate.awaitOpen()
            ensureLoaded(app)
            repairMissing(app).second == 0
        }
        // Other ecosystems load in parallel; they publish into SourceHub as they become ready.
        scope.launch {
            OnboardingGate.awaitOpen()
            runCatching { dev.asfakul.yumeiro.sources.Bridges.start(app) }.onFailure { Log.e(TAG, "bridges", it) }
        }
    }

    /** Progress of re-downloading extensions after a restore: done / total, null when idle. */
    private val _repair = MutableStateFlow<Pair<Int, Int>?>(null)
    val repair: StateFlow<Pair<Int, Int>?> = _repair.asStateFlow()

    /** G9-5: extensions the user said no to while repairing; not offered again in this process. */
    private val declinedRepair: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    /** Installed extensions whose file is gone (restored without files, or deleted by the system). */
    fun missingFiles(context: Context): List<InstalledExtension> =
        _state.value.installed.filter { it.fileName.isBlank() || !File(pluginDir(context), it.fileName).isFile }

    /**
     * Downloads every extension whose file is missing from where it was installed from, with the
     * same safety check as an auto-update. Returns (repaired, failed).
     */
    suspend fun repairMissing(context: Context): Pair<Int, Int> = withContext(Dispatchers.IO) {
        ensureLoaded(context)
        val missing = missingFiles(context).filter { it.sourceUrl.startsWith("http") && it.key !in declinedRepair }
        if (missing.isEmpty()) { dev.asfakul.yumeiro.backup.BackupMeta.clearPendingDownloads(context); return@withContext 0 to 0 }
        // G9-2: offline counts as failed so the retry helper tries again when the network is back.
        if (!dev.asfakul.yumeiro.network.Connectivity.isOnline(context)) return@withContext 0 to missing.size
        var ok = 0; var failed = 0
        _repair.value = 0 to missing.size
        for (ext in missing) {
            markBusy(ext.key, true)
            try {
                val bytes = downloadFirst(listOf(ext.sourceUrl))
                // G9-5: AUTO refused a flagged extension the user had accepted before the backup, so
                // it never came back. Ask again instead (IF_RISKY); without the review host on
                // screen it is held back and the next try asks. Never installed silently.
                gate(ExtensionReview.Mode.IF_RISKY, ext.name, ext.version, ext.repoName, true, bytes)
                mutex.withLock {
                    val current = _state.value.installed.firstOrNull { it.key == ext.key } ?: ext
                    installBytes(context, current.copy(sha256 = sha256(bytes), updatedAt = System.currentTimeMillis()), bytes, current)
                }
                ok++
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                if (t is ExtensionReview.Declined) declinedRepair += ext.key // G9-5: not asked again this process
                else { Log.w(TAG, "repair ${ext.name}", t); failed++ }
            } finally { markBusy(ext.key, false); _repair.value = (ok + failed) to missing.size }
        }
        _repair.value = null
        if (failed == 0) dev.asfakul.yumeiro.backup.BackupMeta.clearPendingDownloads(context)
        if (ok > 0) OnlineRepository.refreshAfterExtensionChange()
        ok to failed
    }

    /** Waits until saved extensions are loaded. Safe to call many times. */
    suspend fun ensureLoaded(context: Context) = withContext(Dispatchers.IO) {
        if (_state.value.ready) return@withContext
        mutex.withLock {
            if (_state.value.ready) return@withLock
            OnlineRuntime.init(context)
            readState(context)
            val runtime = java.util.concurrent.ConcurrentHashMap<String, LoadInfo>()
            val all = _state.value.installed
            all.filter { !it.enabled }.forEach { runtime[it.key] = LoadInfo() }
            // SPD-1 (ADR-038): the feed used to wait here for every extension. Each one is published as soon
            // as it is loaded (pinned first), and the next files are opened while the current one loads.
            val pins = runCatching { PinnedExtensions.init(context); PinnedExtensions.pins.value }.getOrDefault(emptyList())
            val order = StartupSpeed.loadOrder(all.filter { it.enabled }, { it.name }, pins)
            StartupSpeed.loadProgressively(
                order,
                ahead = if (dev.asfakul.yumeiro.DeviceClass.lowEnd) 2 else 4,
                open = { ext ->
                    val file = File(pluginDir(context), ext.fileName)
                    if (!file.isFile || synchronized(plugins) { plugins.containsKey(ext.key) }) null
                    else { runCatching { file.setReadOnly() }; loaderFactory(file, context.classLoader) }
                },
                load = { ext, loader -> loadInternal(context, ext, loader) },
                publish = { ext, info ->
                    runtime[ext.key] = info
                    if (info.loaded) _state.update { it.copy(partial = true, runtime = it.runtime + (ext.key to info), generation = it.generation + 1) }
                },
            )
            cleanupFiles(context)
            _state.update { it.copy(ready = true, partial = true, runtime = HashMap(runtime), generation = it.generation + 1) }
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

    /**
     * Providers of enabled, loaded extensions.
     * FREEZE-2: built once per provider change ([providerStamp]) and shared. Screens read this while composing
     * (several times per frame on Home, Yumix and Search); rebuilding it on every call was the cost behind the
     * 2.0.0 touch freeze (FREEZE-1).
     */
    fun providers(): List<MainAPI> = providerMemo.get(providerStamp()) { buildProviders() }

    private val providerMemo = StampMemo<List<MainAPI>>()

    private fun buildProviders(): List<MainAPI> {
        val enabled = _state.value.installed.filter { it.enabled }.map { it.key }.toSet()
        val files = synchronized(plugins) { plugins.filterKeys { it in enabled }.values.mapNotNull { it.filename }.toSet() }
        val cloudstream = runCatching { APIHolder.allProviders.toList() }.getOrDefault(emptyList())
            .filter { it.sourcePlugin in files }
        // Aniyomi, Stremio, JavaScript and home-server sources join through the same MainAPI shape.
        val all = (cloudstream + dev.asfakul.yumeiro.sources.SourceHub.providers()).distinct()
        // PIN-2: the same extension name from two repositories used to show once (distinctBy name). Now the
        // one installed first keeps its name (its history, bookmarks, downloads and pins are under it) and
        // the others get "Name · Repo", so both are listed and can be picked.
        val ext = extensionLookup()
        uniqueNames(all, { it.name }, { ext(it)?.installedAt ?: Long.MAX_VALUE }, { ext(it)?.repoName })
            .forEach { (api, newName) -> runCatching { api.name = newName } }
        return all.distinctBy { it.name }
            .also { ProviderOverrides.apply(it) }
    }

    /**
     * PIN-2, pure: new names for items whose name is taken. Per name, the lowest [rank] (then list order)
     * keeps it; each other gets "name · label" (or "name · 2", "name · 3" when the label is blank or taken).
     */
    internal fun <T> uniqueNames(items: List<T>, name: (T) -> String, rank: (T) -> Long, label: (T) -> String?): List<Pair<T, String>> {
        val taken = items.map(name).toMutableSet()
        val out = ArrayList<Pair<T, String>>()
        items.withIndex().groupBy { name(it.value) }.values.filter { it.size > 1 }.forEach { group ->
            val ordered = group.sortedWith(compareBy({ rank(it.value) }, { it.index }))
            ordered.drop(1).forEach { (_, item) ->
                val base = name(item)
                var candidate = label(item)?.trim()?.takeIf { it.isNotEmpty() }?.let { "$base · $it" }
                var n = 2
                while (candidate == null || candidate in taken) { candidate = "$base · ${n++}" }
                taken += candidate
                out += item to candidate
            }
        }
        return out
    }

    /** Called by [dev.asfakul.yumeiro.sources.SourceHub] when bridged sources change, so screens reload. */
    fun bumpGeneration() = _state.update { it.copy(generation = it.generation + 1) }

    fun provider(name: String): MainAPI? = providers().firstOrNull { it.name == name }

    /**
     * FREEZE-1: changes whenever [providers] can change: engine state, bridged sources, loaded CloudStream
     * providers or plugins. Engine and bridge states are compared by identity (every update is a new copy).
     */
    fun providerStamp(): Any = ProviderStamp(
        _state.value,
        dev.asfakul.yumeiro.sources.SourceHub.state.value,
        runCatching { APIHolder.allProviders.size }.getOrDefault(-1),
        synchronized(plugins) { plugins.size },
        ProviderOverrides.ready,
    )

    private class ProviderStamp(val engine: Any, val hub: Any, val apis: Int, val plugins: Int, val overrides: Boolean) {
        override fun equals(other: Any?): Boolean = other is ProviderStamp && other.engine === engine &&
            other.hub === hub && other.apis == apis && other.plugins == plugins && other.overrides == overrides
        override fun hashCode(): Int = System.identityHashCode(engine) * 31 + apis * 7 + plugins
    }

    /** FREEZE-1: [extensionOf] for many providers with one pass over the plugins. */
    fun extensionLookup(): (MainAPI) -> InstalledExtension? {
        val byFile = synchronized(plugins) { plugins.entries.mapNotNull { e -> e.value.filename?.let { it to e.key } }.toMap() }
        val byKey = _state.value.installed.associateBy { it.key }
        return { api -> byFile[api.sourcePlugin]?.let { byKey[it] } }
    }

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
            // NBUG-16 NB16-4: the file's own manifest version is what is installed (CloudStream saves the
            // same). Community/discovered listings lag the author's live file (StreamPlay listed 711,
            // file 713), and storing the listing's number flagged the same file as an update later.
            // Never below the listing: a file older than its listing would otherwise be flagged (and
            // downloaded again) on every sync.
            val version = maxOf(readManifest(bytes)?.version ?: remote.version, remote.version)
            gate(review, remote.name, version, remote.repoName, isUpdate, bytes)
            mutex.withLock {
                val previous = _state.value.installed.firstOrNull { it.key == remote.key }
                val ext = InstalledExtension(
                    internalName = remote.internalName, name = remote.name, version = version,
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
            .replace(RX_EXTENSIONMANAGER_1, "_").take(80)
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
                throw HeldBack(name, report.findings.firstOrNull()?.title ?: report.summary)
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
        val safe = ext.internalName.replace(RX_EXTENSIONMANAGER_1, "_")
        // G9-7: the same file from two repos used to share one file name, so uninstalling (or a
        // failed update of) one copy deleted the other's file and unloaded its providers. Each
        // installed entry now has its own file.
        val file = File(pluginDir(context), "$safe-${ext.sha256.take(12)}-${sha256(ext.key.toByteArray()).take(8)}.cs3")
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
                deleteIfUnused(context, file, ext.key)
            } else if (previous == null) deleteIfUnused(context, file, ext.key)
            throw ExtensionException("${ext.name} can't run in this app: ${info.error ?: "unknown error"}")
        }
        if (previous != null && previous.fileName != file.name) deleteIfUnused(context, File(pluginDir(context), previous.fileName), previous.key)
        removedDefaults.remove(installed.key)
        // The user added Internet Archive themselves: the 1.8.0 clean-up must not take it away.
        if (previous == null && installed.key == DEFAULT_KEY) removedDefaults.add(LEGACY_DEFAULT_MIGRATION)
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
            deleteIfUnused(context, File(pluginDir(context), ext.fileName), key)
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
        val heldBack = mutableListOf<String>()
        for ((ext, remote) in updatesFrom(available.items)) {
            if (!ext.autoUpdate) continue
            runCatching { install(context, remote, ExtensionReview.Mode.AUTO) }.onSuccess { updated += it.name }.onFailure {
                // NBUG-16 NB16-6: a refused update used to vanish into `failed`, which nothing showed.
                // Reported once per extension version per app run; the Update button stays.
                if (it is HeldBack) { if (heldBackTold.add(remote.key + "@" + remote.version)) heldBack += ext.name } else failed += ext.name
            }
        }
        mutex.withLock { _state.update { it.copy(lastSync = now) }; writeState(context) }
        SyncResult(installed = installedNow, updated = updated, failed = failed, pending = updatesFrom(available.items).size, heldBack = heldBack)
    }

    data class SyncResult(
        val installed: List<String> = emptyList(),
        val updated: List<String> = emptyList(),
        val failed: List<String> = emptyList(),
        val pending: Int = 0,
        val error: String? = null,
        /** Automatic updates the safety check refused, newly this run (NB16-6). */
        val heldBack: List<String> = emptyList(),
    )
    private val heldBackTold = java.util.Collections.synchronizedSet(HashSet<String>())

    // endregion

    // region loading

    /**
     * Builds the class loader for one extension file. Tests on the JVM (Robolectric cannot open DEX)
     * swap this for a loader over a converted jar; the app always uses [PathClassLoader].
     */
    @androidx.annotation.VisibleForTesting
    internal var loaderFactory: (File, ClassLoader) -> ClassLoader = { f, parent -> PathClassLoader(f.absolutePath, parent) }

    private fun loadInternal(context: Context, ext: InstalledExtension, opened: ClassLoader? = null): LoadInfo {
        val file = File(pluginDir(context), ext.fileName)
        if (!file.exists()) return LoadInfo(error = "File missing — reinstall it")
        synchronized(plugins) { plugins[ext.key] }?.let { p ->
            return LoadInfo(providersOf(p.filename), loaded = true)
        }
        return try {
            runCatching { file.setReadOnly() }
            val loader = opened ?: loaderFactory(file, context.classLoader)
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
        // R16 G4: one line per unload, with the caller, so a section that leaves Yumix can be traced.
        Log.i(TAG, "Unloaded $key (${plugin.filename?.substringAfterLast('/')}) from ${Throwable().stackTrace.drop(1).take(4).joinToString(" < ") { it.methodName + ":" + it.lineNumber }}")
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

    /**
     * Reads the plugin manifest straight from memory (no temp file: a file in the shared temp dir
     * could be read by other apps on old Android versions, CodeQL local-temp-file).
     */
    private fun readManifest(bytes: ByteArray): BasePlugin.Manifest? = runCatching {
        var hasDex = false
        var manifest: String? = null
        java.util.zip.ZipInputStream(bytes.inputStream()).use { zin ->
            var budget = 64L * 1024 * 1024 // never inflate more than this from one file
            while (true) {
                val e = zin.nextEntry ?: break
                when (e.name) {
                    "classes.dex" -> hasDex = true
                    "manifest.json" -> {
                        val out = java.io.ByteArrayOutputStream()
                        val buf = ByteArray(8192)
                        while (true) {
                            val n = zin.read(buf); if (n < 0) break
                            out.write(buf, 0, n); budget -= n
                            if (out.size() > 1_000_000 || budget < 0) return null
                        }
                        manifest = out.toByteArray().decodeToString()
                    }
                }
                if (hasDex && manifest != null) break
            }
        }
        if (!hasDex) return null
        val o = JSONObject(manifest ?: return null)
        if (o.optString("pluginClassName").isBlank()) return null
        BasePlugin.Manifest().apply {
            name = o.optStringOrNull("name")
            pluginClassName = o.optString("pluginClassName")
            version = if (o.has("version")) o.optInt("version") else null
            requiresResources = o.optBoolean("requiresResources")
        }
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

    /** G9-7: deletes [f] unless another installed entry than [ownerKey] still uses it (older installs shared names). */
    private fun deleteIfUnused(context: Context, f: File, ownerKey: String) {
        if (f.name.isBlank() || f.parentFile != pluginDir(context)) return
        val used = _state.value.installed.any { it.key != ownerKey && it.fileName == f.name }
        if (!used) deleteFile(f)
    }

    /** Removes files no installed extension points to (failed updates, crashes mid-install). */
    private fun cleanupFiles(context: Context) {
        val keep = _state.value.installed.map { it.fileName }.toSet()
        pluginDir(context).listFiles()?.forEach { f -> if (f.name !in keep) deleteFile(f) }
    }

    /**
     * R16 G4: whether the one-time removal of 1.8.0's automatic Internet Archive install is still
     * due, decided once from the saved state before anything new can be installed. It used to be
     * decided at the first [sync], so on a new install (or before the first sync after an update)
     * an Internet Archive the user had just added was uninstalled without a word. Found by
     * `YumixDeviceTest` (runs 43716649966 … 43721853978: the row appeared, then "Unloaded" from
     * [uninstall] called by [sync]).
     */
    internal fun legacyDefaultMigrationDue(done: Set<String>, installedKeys: List<String>): Boolean =
        LEGACY_DEFAULT_MIGRATION !in done && DEFAULT_KEY in installedKeys

    private fun readState(context: Context) {
        val f = stateFile(context)
        val o = runCatching { if (f.exists()) JSONObject(f.readText()) else null }.getOrNull()
        // R16 G4: a new install has nothing to migrate (see [legacyDefaultMigrationDue]).
        if (o == null) { removedDefaults.add(LEGACY_DEFAULT_MIGRATION); _state.update { it.copy(installed = emptyList(), repos = emptyList()) }; return }
        val installed = o.optJSONArray("installed")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(InstalledExtension::fromJson) } }.orEmpty()
        val repos = o.optJSONArray("repos")?.let { a ->
            (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { r -> r.optString("url").ifBlank { null }?.let { RepoRef(it, r.optString("name").ifBlank { it }, r.optLong("addedAt")) } } }
        }.orEmpty()
        removedDefaults = o.optJSONArray("removedDefaults").strings().toMutableSet()
        if (!legacyDefaultMigrationDue(removedDefaults, installed.map { it.key })) removedDefaults.add(LEGACY_DEFAULT_MIGRATION)
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
