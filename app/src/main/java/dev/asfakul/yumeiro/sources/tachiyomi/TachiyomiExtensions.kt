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
import eu.kanade.tachiyomi.source.Source as MangaSource
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
 * Repos are `index.min.json` files or TachiyomiX v2 stores (`repo.json` → `index.pb` /
 * `index.json`, see [TachiyomiIndex]); Yumeiro ships none and adds none by itself. The
 * Discover list ([dev.asfakul.yumeiro.sources.SourceMarket]) only suggests ones found on GitHub.
 */
object TachiyomiExtensions {
    private const val TAG = "TachiyomiExt"

    enum class Kind(val label: String) { ANIME("Aniyomi"), MANGA("Mihon") }

    @Serializable
    /** [fingerprint]: the signing-certificate SHA-256 the store says its extensions use (v2 / repo.json). */
    data class Repo(val url: String, val kind: Kind, val name: String = "", val fingerprint: String? = null)

    /**
     * One extension of a repo index. v1 (`index.min.json`) fills the first fields; v2 stores also
     * give absolute [apkUrl] / [iconUrl] (APKs often live on GitHub releases) and the [lib] version.
     */
    @Serializable
    data class IndexEntry(
        val name: String,
        val pkg: String,
        val apk: String,
        val lang: String = "",
        val code: Long = 0,
        val version: String = "",
        val nsfw: Int = 0,
        val apkUrl: String? = null,
        val iconUrl: String? = null,
        val lib: String? = null,
    )

    data class Available(val repo: Repo, val entry: IndexEntry) {
        val apkUrl get() = entry.apkUrl ?: (repo.base() + "/apk/" + entry.apk)
        val iconUrl get() = entry.iconUrl ?: (repo.base() + "/icon/" + entry.pkg + ".png")
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
    private const val MAX_MANGA_LIB = 1.6

    private fun root(context: Context) = File(context.filesDir, "tachiyomi").apply { mkdirs() }
    private fun extDir(context: Context, kind: Kind) = File(root(context), kind.name.lowercase()).apply { mkdirs() }
    private fun reposFile(context: Context) = File(root(context), "repos.json")
    private fun prefs(context: Context) = context.getSharedPreferences("tachiyomi_bridge", Context.MODE_PRIVATE)

    /**
     * Trusted `package:signature` pairs, sealed with the Keystore (security/SecretBox) so another
     * process or a backup can't quietly add a trusted signer. Older plain sets are migrated once.
     */
    private fun trustedSet(context: Context): Set<String> {
        val p = prefs(context)
        p.getString("trusted_v2", null)?.let { sealed ->
            return dev.asfakul.yumeiro.security.SecretBox.open(sealed)?.split('\n')?.filter { it.isNotBlank() }?.toSet().orEmpty()
        }
        val legacy = p.getStringSet("trusted", null) ?: return emptySet()
        saveTrusted(context, legacy)
        return legacy
    }

    private fun saveTrusted(context: Context, set: Set<String>) {
        val sealed = dev.asfakul.yumeiro.security.SecretBox.seal(set.sorted().joinToString("\n")) ?: return
        prefs(context).edit().putString("trusted_v2", sealed).remove("trusted").apply()
    }

    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        scope.launch { runCatching { reload(app) }.onFailure { Log.e(TAG, "start", it) } }
    }

    // region repos

    /**
     * Normalises a pasted repo link to its base folder (the one that holds `repo.json` /
     * `index.min.json` / `index.pb`). Accepts the index or repo.json link itself, `github.com`
     * repo, tree and raw links, and the `tachiyomi://` / `mihon://` / `aniyomi://` add-repo links.
     */
    fun normalizeRepo(raw: String): String {
        var u = raw.trim().removeSuffix("/")
        if (u.startsWith("tachiyomi://") || u.startsWith("aniyomi://") || u.startsWith("mihon://")) {
            u = Regex("""[?&]url=([^&]+)""").find(u)?.groupValues?.get(1)
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: u
            u = u.trim().removeSuffix("/")
        }
        u = u.substringBefore('#').substringBefore('?')
        for (file in listOf("/index.min.json", "/index.json", "/index.pb", "/index.pb.gz", "/index.json.gz", "/repo.json")) {
            u = u.removeSuffix(file)
        }
        // github.com/<owner>/<repo>/(raw|blob)/<branch>[/path] -> raw.githubusercontent.com/<owner>/<repo>/<branch>[/path]
        Regex("""^https?://github\.com/([^/]+)/([^/]+)/(?:raw|blob)/(.+)$""").find(u)?.let { m ->
            val (owner, repo, rest) = m.destructured
            u = "https://raw.githubusercontent.com/$owner/$repo/$rest"
        }
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
            val host = base.substringAfter("://").substringBefore("/")
            val index = fetchRepoIndex(context, Repo(base, kind, host))
            val count = index.entries.size
            require(count > 0) { "That link has no extensions" }
            val repo = Repo(base, kind, index.meta.name.ifBlank { host }, index.meta.fingerprint)
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

    suspend fun fetchIndex(context: Context, repo: Repo): List<IndexEntry> = fetchRepoIndex(context, repo).entries

    /**
     * Reads a repo in whichever format it publishes. One small `repo.json` request decides: a v2
     * store points at its `index_v2`; otherwise `index.min.json` is read, and if that only holds
     * "update your app" stubs the v2 files next to it are tried.
     */
    suspend fun fetchRepoIndex(context: Context, repo: Repo): TachiyomiIndex.Parsed = withContext(Dispatchers.IO) {
        val base = repo.base()
        val http = NetworkClient.get(context)
        fun bytes(url: String): ByteArray = http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) error("Repo answered HTTP ${r.code}")
            r.body.bytes()
        }
        fun v2(url: String, meta: TachiyomiIndex.Meta): TachiyomiIndex.Parsed {
            val raw = bytes(url)
            val list = TachiyomiIndex.extensionListUrl(raw)
            val parsed = if (list != null) {
                TachiyomiIndex.parseV2(bytes(TachiyomiIndex.resolve(url, list)), TachiyomiIndex.resolve(url, list))
                    .let { it.copy(meta = TachiyomiIndex.parseV2(raw, url).meta) }
            } else TachiyomiIndex.parseV2(raw, url)
            return parsed.copy(
                entries = parsed.entries.filterNot(TachiyomiIndex::isPlaceholder),
                meta = parsed.meta.copy(
                    name = parsed.meta.name.ifBlank { meta.name },
                    fingerprint = meta.fingerprint ?: parsed.meta.fingerprint,
                ),
            )
        }

        val meta = runCatching { TachiyomiIndex.parseRepoJson(bytes("$base/repo.json").decodeToString()) }
            .getOrDefault(TachiyomiIndex.Meta())
        meta.indexV2?.let { url -> runCatching { v2(url, meta) }.getOrNull()?.takeIf { it.entries.isNotEmpty() }?.let { return@withContext it } }

        val v1 = runCatching { TachiyomiIndex.parseV1(bytes("$base/index.min.json").decodeToString()) }
        val v1List = v1.getOrNull()
        if (v1List != null && v1List.isNotEmpty() && !TachiyomiIndex.isPlaceholderOnly(v1List)) {
            return@withContext TachiyomiIndex.Parsed(v1List, meta)
        }
        for (file in listOf("index.pb", "index.json")) {
            runCatching { v2("$base/$file", meta) }.getOrNull()?.takeIf { it.entries.isNotEmpty() }?.let { return@withContext it }
        }
        if (v1List != null) {
            // Only update stubs and no readable v2 index next to them.
            error(if (v1List.isEmpty()) "That repo is empty" else "This repo moved to a newer index Yumeiro couldn't find")
        }
        throw v1.exceptionOrNull() ?: IllegalStateException("No extension index found at that link")
    }

    /** Every extension offered by the saved repos of [kind], newest version per package. */
    suspend fun available(context: Context, kind: Kind): List<Available> = withContext(Dispatchers.IO) {
        _state.value.repos.filter { it.kind == kind }.flatMap { repo ->
            val idx = runCatching { fetchRepoIndex(context, repo) }.getOrNull() ?: return@flatMap emptyList()
            val r = repo.copy(fingerprint = idx.meta.fingerprint ?: repo.fingerprint)
            idx.entries.map { Available(r, it) }
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
            try {
                installFile(context, tmp, item.repo.kind, item.iconUrl, item.repo.fingerprint)
            } finally { tmp.delete() }
        }
    }

    /**
     * Installs a local APK file (from a repo download or the file picker). When the repo the APK
     * came from declares a signing fingerprint and the APK is signed with exactly that certificate,
     * it's trusted right away (the user trusted the store by adding it) — like Mihon does.
     */
    @SuppressLint("PackageManagerGetSignatures")
    suspend fun installFile(
        context: Context, apk: File, kind: Kind, iconUrl: String? = null, repoFingerprint: String? = null,
    ) = mutex.withLock {
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
        val sig = signatures(info).lastOrNull()
        if (sig != null && repoFingerprint != null && sig.equals(TachiyomiIndex.cleanFingerprint(repoFingerprint), true)) {
            saveTrusted(context, trustedSet(context) + "${info.packageName}:$sig")
        }
        iconUrl?.let { prefs(context).edit().putString("icon:" + info.packageName, it).apply() }
        reloadLocked(context)
    }

    suspend fun uninstall(context: Context, pkg: String) = mutex.withLock {
        Kind.entries.forEach { k -> File(extDir(context, k), "$pkg.ext").let { it.setWritable(true); it.delete() } }
        reloadLocked(context)
    }

    /** Trusts the signing certificate of [pkg] (the user saw its fingerprint) and loads it. */
    suspend fun trust(context: Context, pkg: String, signature: String) = mutex.withLock {
        saveTrusted(context, trustedSet(context) + "$pkg:$signature")
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
        SourceHub.publishManga("mihon", manga.map { MihonMangaProvider(it, MemoStore(File(context.filesDir, "tachiyomi/memo/${it.id}.json"))) })
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
        val x = if (kind == Kind.ANIME) "aniyomix" else "tachiyomix"
        val name = (meta.getString("$x.name") ?: meta.getString("aniyomix.name"))
            ?: pm.getApplicationLabel(app).toString().substringAfter("Aniyomi: ").substringAfter("Tachiyomi: ")
        // tachiyomix 1.6 writes "1.6" (aapt stores it as a float); aniyomix writes an int like 16.
        val lib = libVersion(meta.get("$x.extensionLib")) ?: versionName.substringBeforeLast('.').toDoubleOrNull()
        // tachiyomix.contentWarning: 0 safe, 1 mixed, 2 NSFW. Only NSFW waits for the adult switch, like
        // the store listing; without it, fall back to the old nsfw flag.
        val warning = (meta.get("$x.contentWarning") as? Number)?.toInt()
        val nsfw = if (warning != null && kind == Kind.MANGA) warning >= 2
        else meta.getInt("$prefix.nsfw") == 1 || (warning ?: 0) > 0
        val sig = signatures(pkg).lastOrNull()
        val trusted = sig != null && "${pkg.packageName}:$sig" in trustedSet(context)
        val base = Installed(pkg.packageName, name, kind, versionName, dev.asfakul.yumeiro.OsCompat.versionCode(pkg), lib, nsfw, sig, trusted, 0)

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

    /** Reads an extension-lib meta-data value: int 16 (aniyomix), float/string 1.6 (tachiyomix). */
    internal fun libVersion(value: Any?): Double? = when (value) {
        null -> null
        is Int -> value.takeIf { it != 0 }?.toDouble()
        is Float -> value.toString().toDoubleOrNull()
        is Number -> value.toDouble().takeIf { it != 0.0 }
        else -> value.toString().trim().toDoubleOrNull()
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
    private fun signatures(info: PackageInfo): List<String> = dev.asfakul.yumeiro.OsCompat.signerDigests(info)

    @Suppress("DEPRECATION")
    private val FLAGS = PackageManager.GET_CONFIGURATIONS or PackageManager.GET_META_DATA or
        dev.asfakul.yumeiro.OsCompat.SIGNING_FLAGS

    // endregion
}

private fun TachiyomiExtensions.Repo.base() = url.removeSuffix("/")
