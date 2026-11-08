/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.tachiyomi

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.util.Log
import dalvik.system.PathClassLoader
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.online.OnlineSettings
import dev.asfakul.yumeiro.sources.SourceHub
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.util.ChildFirstPathClassLoader
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/**
 * Installs and loads Aniyomi (anime) and Mihon / Tachiyomi (manga) extension APKs without
 * installing them into Android: the APK is kept read-only in app storage and its classes are
 * loaded with a child-first class loader against the vendored extensions-lib API — the same
 * "private extension" path Aniyomi itself uses.
 *
 * Repos are plain `index.min.json` files; Yumeiro ships none and adds none by itself.
 */
object TachiyomiExtensions {
    private const val TAG = "TachiyomiExt"

    enum class Kind(val label: String) { ANIME("Aniyomi"), MANGA("Mihon") }

    @Serializable
    data class Repo(val url: String, val kind: Kind, val name: String = "")

    /** One entry of a repo's index.min.json. */
    @Serializable
    data class IndexEntry(
        val name: String,
        val pkg: String,
        val apk: String,
        val lang: String = "",
        val code: Long = 0,
        val version: String = "",
        val nsfw: Int = 0,
    )

    data class Available(val repo: Repo, val entry: IndexEntry) {
        val apkUrl get() = repo.base() + "/apk/" + entry.apk
        val iconUrl get() = repo.base() + "/icon/" + entry.pkg + ".png"
        val displayName get() = entry.name.substringAfter("Aniyomi: ").substringAfter("Tachiyomi: ")
    }

    data class Installed(
        val pkg: String,
        val name: String,
        val kind: Kind,
        val versionName: String,
        val versionCode: Long,
        val lib: Double?,
        val nsfw: Boolean,
        val signature: String?,
        val trusted: Boolean,
        val sources: Int,
        val error: String? = null,
        val iconUrl: String? = null,
    )

    data class State(
        val repos: List<Repo> = emptyList(),
        val installed: List<Installed> = emptyList(),
        val ready: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    @Volatile private var started = false

    private val SUPPORTED_ANIME_LIB = listOf(12.0, 13.0, 14.0, 15.0, 16.0, 17.0)
    private const val MIN_MANGA_LIB = 1.2
    private const val MAX_MANGA_LIB = 1.5

    private fun root(context: Context) = File(context.filesDir, "tachiyomi").apply { mkdirs() }
    private fun extDir(context: Context, kind: Kind) = File(root(context), kind.name.lowercase()).apply { mkdirs() }
    private fun reposFile(context: Context) = File(root(context), "repos.json")
    private fun prefs(context: Context) = context.getSharedPreferences("tachiyomi_bridge", Context.MODE_PRIVATE)

    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        scope.launch { runCatching { reload(app) }.onFailure { Log.e(TAG, "start", it) } }
    }

    // region repos

    /** Normalises a pasted repo link to its base folder (the one that holds index.min.json). */
    fun normalizeRepo(raw: String): String {
        var u = raw.trim().removeSuffix("/")
        if (u.startsWith("tachiyomi://") || u.startsWith("aniyomi://") || u.startsWith("mihon://")) {
            u = android.net.Uri.parse(u).getQueryParameter("url") ?: u
        }
        u = u.removeSuffix("/index.min.json").removeSuffix("/index.json").removeSuffix("/repo.json")
        // github.com/<owner>/<repo>[/tree/<branch>] -> raw.githubusercontent.com/<owner>/<repo>/<branch>
        Regex("""^https?://github\.com/([^/]+)/([^/]+)(?:/tree/([^/]+))?/?$""").find(u)?.let { m ->
            val (owner, repo, branch) = m.destructured
            u = "https://raw.githubusercontent.com/$owner/$repo/${branch.ifBlank { "repo" }}"
        }
        if (!u.startsWith("http")) u = "https://$u"
        return u
    }

    suspend fun addRepo(context: Context, raw: String, kind: Kind): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val base = normalizeRepo(raw)
            val repo = Repo(base, kind, base.substringAfter("://").substringBefore("/"))
            val count = fetchIndex(context, repo).size
            require(count > 0) { "That link has no extensions" }
            mutex.withLock {
                val repos = (_state.value.repos.filterNot { it.url == base } + repo)
                saveRepos(context, repos)
                _state.update { it.copy(repos = repos) }
            }
            count
        }
    }

    suspend fun removeRepo(context: Context, url: String) = mutex.withLock {
        val repos = _state.value.repos.filterNot { it.url == url }
        saveRepos(context, repos)
        _state.update { it.copy(repos = repos) }
    }

    suspend fun fetchIndex(context: Context, repo: Repo): List<IndexEntry> = withContext(Dispatchers.IO) {
        val body = NetworkClient.get(context).newCall(Request.Builder().url(repo.base() + "/index.min.json").build())
            .execute().use { r ->
                if (!r.isSuccessful) error("Repo answered HTTP ${r.code}")
                r.body.string()
            }
        TachiyomiRuntime.json.decodeFromString(ListSerializer(IndexEntry.serializer()), body)
    }

    /** Every extension offered by the saved repos of [kind], newest version per package. */
    suspend fun available(context: Context, kind: Kind): List<Available> = withContext(Dispatchers.IO) {
        _state.value.repos.filter { it.kind == kind }.flatMap { repo ->
            runCatching { fetchIndex(context, repo) }.getOrDefault(emptyList()).map { Available(repo, it) }
        }.groupBy { it.entry.pkg }.map { (_, list) -> list.maxBy { it.entry.code } }
            .filter { OnlineSettings.adult.value || it.entry.nsfw == 0 }
            .sortedBy { it.displayName.lowercase() }
    }

    private fun saveRepos(context: Context, repos: List<Repo>) {
        reposFile(context).writeText(TachiyomiRuntime.json.encodeToString(ListSerializer(Repo.serializer()), repos))
    }

    private fun readRepos(context: Context): List<Repo> = runCatching {
        TachiyomiRuntime.json.decodeFromString(ListSerializer(Repo.serializer()), reposFile(context).readText())
    }.getOrDefault(emptyList())

    // endregion

    // region install

    suspend fun install(context: Context, item: Available): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val tmp = File(context.cacheDir, "tachiyomi-${item.entry.pkg}.apk")
            NetworkClient.get(context).newCall(Request.Builder().url(item.apkUrl).build()).execute().use { r ->
                if (!r.isSuccessful) error("Download failed (HTTP ${r.code})")
                tmp.outputStream().use { out -> r.body.byteStream().copyTo(out) }
            }
            try { installFile(context, tmp, item.repo.kind, item.iconUrl) } finally { tmp.delete() }
        }
    }

    /** Installs a local APK file (from a repo download or the file picker). */
    @SuppressLint("PackageManagerGetSignatures")
    suspend fun installFile(context: Context, apk: File, kind: Kind, iconUrl: String? = null) = mutex.withLock {
        val pm = context.packageManager
        val info = pm.getPackageArchiveInfo(apk.absolutePath, FLAGS) ?: error("Not a valid APK")
        val detected = kindOf(info) ?: error("This APK isn't an Aniyomi or Mihon extension")
        require(detected == kind) { "This is a ${detected.label} extension, not ${kind.label}" }
        val target = File(extDir(context, kind), info.packageName + ".ext")
        val old = if (target.isFile) pm.getPackageArchiveInfo(target.absolutePath, FLAGS) else null
        if (old != null) {
            val newSig = signatures(info)
            val oldSig = signatures(old)
            require(newSig.isNotEmpty() && newSig.containsAll(oldSig)) { "Signature doesn't match the installed version" }
        }
        target.setWritable(true)
        target.delete()
        apk.copyTo(target, overwrite = true)
        target.setReadOnly() // Android 14+ refuses to load writable dex files.
        iconUrl?.let { prefs(context).edit().putString("icon:" + info.packageName, it).apply() }
        reloadLocked(context)
    }

    suspend fun uninstall(context: Context, pkg: String) = mutex.withLock {
        Kind.entries.forEach { k -> File(extDir(context, k), "$pkg.ext").let { it.setWritable(true); it.delete() } }
        reloadLocked(context)
    }

    /** Trusts the signing certificate of [pkg] (the user saw its fingerprint) and loads it. */
    suspend fun trust(context: Context, pkg: String, signature: String) = mutex.withLock {
        val set = prefs(context).getStringSet("trusted", emptySet()).orEmpty() + "$pkg:$signature"
        prefs(context).edit().putStringSet("trusted", set).apply()
        reloadLocked(context)
    }

    // endregion

    // region load

    suspend fun reload(context: Context) = mutex.withLock { reloadLocked(context) }

    private suspend fun reloadLocked(context: Context) = withContext(Dispatchers.IO) {
        TachiyomiRuntime.init(context)
        val repos = readRepos(context)
        val anime = mutableListOf<AnimeCatalogueSource>()
        val manga = mutableListOf<CatalogueSource>()
        val installed = mutableListOf<Installed>()
        for (kind in Kind.entries) {
            for (file in extDir(context, kind).listFiles { f -> f.name.endsWith(".ext") }.orEmpty()) {
                val result = runCatching { load(context, file, kind) }.getOrElse { e ->
                    Log.w(TAG, "load ${file.name}", e)
                    LoadResult(Installed(file.nameWithoutExtension, file.nameWithoutExtension, kind, "?", 0, null, false, null, false, 0, e.message ?: e.javaClass.simpleName))
                }
                installed += result.info.copy(iconUrl = prefs(context).getString("icon:" + result.info.pkg, null))
                result.anime.filterIsInstance<AnimeCatalogueSource>().let(anime::addAll)
                result.manga.filterIsInstance<CatalogueSource>().let(manga::addAll)
            }
        }
        val taken = HashSet<String>()
        val animeApis = anime.map { src ->
            AniyomiProvider(src, SourceHub.uniqueName(src.name, src.lang, taken).also(taken::add))
        }
        SourceHub.publishVideo("aniyomi", SourceHub.Ecosystem.ANIYOMI, animeApis)
        SourceHub.publishManga("mihon", manga.map { MihonMangaProvider(it) })
        _state.update { State(repos, installed.sortedBy { it.name.lowercase() }, ready = true) }
    }

    private class LoadResult(val info: Installed, val anime: List<AnimeSource> = emptyList(), val manga: List<MangaSource> = emptyList())

    @SuppressLint("PackageManagerGetSignatures")
    private fun load(context: Context, file: File, kind: Kind): LoadResult {
        val pm = context.packageManager
        val pkg = pm.getPackageArchiveInfo(file.absolutePath, FLAGS) ?: error("Unreadable APK")
        val app = pkg.applicationInfo ?: error("Unreadable APK")
        app.sourceDir = file.absolutePath
        app.publicSourceDir = file.absolutePath
        val meta = app.metaData ?: error("Missing extension metadata")
        val versionName = pkg.versionName.orEmpty()
        val prefix = if (kind == Kind.ANIME) "tachiyomi.animeextension" else "tachiyomi.extension"
        val name = meta.getString("aniyomix.name")
            ?: pm.getApplicationLabel(app).toString().substringAfter("Aniyomi: ").substringAfter("Tachiyomi: ")
        val lib = (meta.getInt("aniyomix.extensionLib").takeIf { it != 0 }?.toDouble())
            ?: versionName.substringBeforeLast('.').toDoubleOrNull()
        val nsfw = meta.getInt("$prefix.nsfw") == 1 || meta.getInt("aniyomix.contentWarning") > 0
        val sig = signatures(pkg).lastOrNull()
        val trusted = sig != null && "${pkg.packageName}:$sig" in prefs(context).getStringSet("trusted", emptySet()).orEmpty()
        val base = Installed(pkg.packageName, name, kind, versionName, pkg.longVersionCode, lib, nsfw, sig, trusted, 0)

        val libOk = when (kind) {
            Kind.ANIME -> lib != null && lib in SUPPORTED_ANIME_LIB
            Kind.MANGA -> lib != null && lib in MIN_MANGA_LIB..MAX_MANGA_LIB
        }
        if (!libOk) return LoadResult(base.copy(error = "Extension library $lib isn't supported yet"))
        if (sig == null) return LoadResult(base.copy(error = "This extension isn't signed"))
        if (!trusted) return LoadResult(base) // Shown as "Trust this extension?" in the UI.
        if (nsfw && !OnlineSettings.adult.value) return LoadResult(base.copy(error = "18+ — turn on adult content to load it"))

        val classes = meta.getString("$prefix.class").orEmpty().split(";").map { it.trim() }.filter { it.isNotEmpty() }
            .map { if (it.startsWith(".")) pkg.packageName + it else it }
        require(classes.isNotEmpty()) { "Extension declares no source classes" }

        fun instantiate(loader: ClassLoader): List<Any> = classes.flatMap { cls ->
            when (val obj = Class.forName(cls, false, loader).getDeclaredConstructor().newInstance()) {
                is AnimeSourceFactory -> obj.createSources()
                is SourceFactory -> obj.createSources()
                is AnimeSource, is MangaSource -> listOf(obj)
                else -> error("Unknown source class ${obj.javaClass.name}")
            }
        }
        val objs = try {
            instantiate(ChildFirstPathClassLoader(file.absolutePath, null, context.classLoader))
        } catch (e: LinkageError) {
            instantiate(PathClassLoader(file.absolutePath, null, context.classLoader))
        }
        val anime = objs.filterIsInstance<AnimeSource>()
        val manga = objs.filterIsInstance<MangaSource>()
        return LoadResult(base.copy(sources = anime.size + manga.size), anime, manga)
    }

    private fun kindOf(info: PackageInfo): Kind? {
        val features = info.reqFeatures.orEmpty().mapNotNull { it.name }
        return when {
            "tachiyomi.animeextension" in features -> Kind.ANIME
            "tachiyomi.extension" in features -> Kind.MANGA
            info.applicationInfo?.metaData?.containsKey("tachiyomi.animeextension.class") == true -> Kind.ANIME
            info.applicationInfo?.metaData?.containsKey("tachiyomi.extension.class") == true -> Kind.MANGA
            else -> null
        }
    }

    /** SHA-256 fingerprints of the APK's signing certificates. */
    @Suppress("DEPRECATION")
    private fun signatures(info: PackageInfo): List<String> {
        val signing = info.signingInfo
        val sigs = when {
            signing == null -> info.signatures?.toList().orEmpty()
            signing.hasMultipleSigners() -> signing.apkContentsSigners.toList()
            else -> signing.signingCertificateHistory.toList()
        }
        return sigs.map { s ->
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }

    @Suppress("DEPRECATION")
    private val FLAGS = PackageManager.GET_CONFIGURATIONS or PackageManager.GET_META_DATA or
        PackageManager.GET_SIGNATURES or PackageManager.GET_SIGNING_CERTIFICATES

    // endregion
}

private fun TachiyomiExtensions.Repo.base() = url.removeSuffix("/")
