/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources

import android.content.Context
import android.net.Uri
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.online.normalizeRepoUrl
import dev.asfakul.yumeiro.sources.js.JsSources
import dev.asfakul.yumeiro.sources.servers.HomeServers
import dev.asfakul.yumeiro.sources.stremio.StremioAddons
import dev.asfakul.yumeiro.sources.tachiyomi.TachiyomiExtensions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

/**
 * "Add anything": one field (or one file) for every extension format Yumeiro runs. The user never
 * has to know which app an extension was made for; the link or file contents decide.
 *
 * | Paste or open                                                   | Detected as        | Engine                |
 * |-----------------------------------------------------------------|--------------------|-----------------------|
 * | `.cs3` file or link (zip with `manifest.json` + pluginClassName) | CloudStream plugin | ExtensionManager      |
 * | `repo.json` with `pluginLists`, `cloudstreamrepo://`, short code | CloudStream repo   | ExtensionManager      |
 * | `plugins.json` (array of `internalName` + `url`)                 | CloudStream list   | ExtensionManager      |
 * | `index.min.json` / v2 store, `aniyomi://` `tachiyomi://` `mihon://` | Aniyomi or Mihon repo (by package names) | TachiyomiExtensions |
 * | Extension `.apk` (manifest feature `tachiyomi.(anime)extension`) | Aniyomi or Mihon   | TachiyomiExtensions   |
 * | Stremio `manifest.json`, `stremio://`                            | Stremio add-on     | StremioAddons         |
 * | Mangayomi `index.json` (`sourceCodeUrl`), `mangayomi://`         | JavaScript repo    | JsSources             |
 * | Jellyfin / Emby / Plex address                                   | Home server        | HomeServers (sign-in) |
 *
 * Detection is split into pure functions ([hint], [sniff], [candidates]) so it is unit tested
 * without a network.
 */
object UniversalAdd {
    enum class Kind(val label: String) {
        CS_PLUGIN("CloudStream extension"),
        CS_REPO("CloudStream repository"),
        CS_LIST("CloudStream plugin list"),
        ANIYOMI_REPO("Aniyomi repository"),
        MIHON_REPO("Mihon repository"),
        TACHI_REPO("Aniyomi or Mihon repository"),
        TACHI_APK("Aniyomi or Mihon extension"),
        STREMIO("Stremio add-on"),
        JS_REPO("JavaScript (Mangayomi) repository"),
        JELLYFIN("Jellyfin server"),
        EMBY("Emby server"),
        PLEX("Plex server"),
    }

    /** What happened. [needsSignIn] is set for home servers: the user still has to sign in. */
    data class Outcome(val kind: Kind, val message: String, val needsSignIn: HomeServers.Kind? = null, val url: String? = null)

    /** A link cleaned up, with the kind its scheme already tells (if any). */
    data class Hint(val url: String, val kind: Kind? = null, val shortCode: String? = null)

    private val SHORT_CODE = Regex("^[A-Za-z0-9!_-]{2,64}$")

    /** Reads the scheme / shape of what was pasted. Pure. */
    fun hint(raw: String): Hint {
        val s = raw.trim().trim('"', '\'', '<', '>')
        fun query(u: String, vararg keys: String): String? = keys.firstNotNullOfOrNull { k ->
            Regex("""[?&]$k=([^&]+)""").find(u)?.groupValues?.get(1)?.let { java.net.URLDecoder.decode(it, "UTF-8") }
        }
        val lower = s.lowercase()
        return when {
            lower.startsWith("cloudstreamrepo://") || lower.startsWith("https://cs.repo/") -> Hint(normalizeRepoUrl(s), Kind.CS_REPO)
            lower.startsWith("stremio://") -> Hint(StremioAddons.normalize(s), Kind.STREMIO)
            lower.startsWith("aniyomi://") -> Hint(withScheme(query(s, "url") ?: s.substringAfter("://")), Kind.ANIYOMI_REPO)
            lower.startsWith("tachiyomi://") || lower.startsWith("mihon://") -> Hint(withScheme(query(s, "url") ?: s.substringAfter("://")), Kind.TACHI_REPO)
            lower.startsWith("mangayomi://") -> Hint(withScheme(query(s, "url", "anime", "manga") ?: s.substringAfter("://")), Kind.JS_REPO)
            !s.contains('.') && !s.contains('/') && SHORT_CODE.matches(s) -> Hint(s, Kind.CS_REPO, shortCode = s)
            else -> {
                val u = withScheme(s)
                val path = u.substringBefore('?').substringBefore('#').lowercase()
                val k = when {
                    path.endsWith(".cs3") -> Kind.CS_PLUGIN
                    path.endsWith(".apk") -> Kind.TACHI_APK
                    path.endsWith("/index.min.json") || path.endsWith("/index.pb") || path.endsWith("/index.pb.gz") -> Kind.TACHI_REPO
                    else -> null
                }
                Hint(u, k)
            }
        }
    }

    private fun withScheme(u: String): String = u.trim().let { if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it" }

    /**
     * Where to look when the link itself isn't a known file: the link, then the files each format
     * keeps at a fixed place (GitHub repo pages map to their raw branches). Pure.
     */
    fun candidates(url: String): List<String> {
        val clean = url.trim().removeSuffix("/")
        val out = LinkedHashSet<String>()
        val gh = Regex("""^https?://github\.com/([^/]+)/([^/?#]+?)(?:\.git)?(?:/(?:tree|blob)/([^/]+)(/.*)?)?/?$""").find(clean)
        if (gh != null) {
            val (o, r, branch, path) = gh.destructured
            val p = path.trim('/')
            val raw = "https://raw.githubusercontent.com/$o/$r"
            if (p.contains('.')) { out += "$raw/${branch.ifBlank { "HEAD" }}/$p"; return out.toList() }
            val branches = if (branch.isNotBlank()) listOf(branch) else listOf("builds", "repo", "main", "master")
            val sub = if (p.isBlank()) "" else "$p/"
            for (b in branches) for (f in listOf("repo.json", "index.min.json", "plugins.json", "index.json", "manifest.json")) out += "$raw/$b/$sub$f"
            return out.toList()
        }
        out += clean
        val path = clean.substringBefore('?').substringBefore('#')
        val lastSeg = path.substringAfterLast('/')
        if (!lastSeg.contains('.') || path.count { it == '/' } <= 2) {
            for (f in listOf("manifest.json", "repo.json", "index.min.json", "plugins.json", "index.json")) out += "$path/$f"
            out += "$path/System/Info/Public"
            out += "$path/identity"
        }
        return out.toList()
    }

    /** Decides the kind from a downloaded body. Pure; [bytes] may be the first few KB only for binaries. */
    fun sniff(url: String, bytes: ByteArray): Kind? {
        if (bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) return zipKind(bytes)
        val text = bytes.decodeToString().trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        if (text.startsWith("<")) {
            return if (text.contains("<MediaContainer") && text.contains("machineIdentifier")) Kind.PLEX else null
        }
        if (text.startsWith("{")) {
            val o = runCatching { JSONObject(text) }.getOrNull() ?: return null
            return when {
                o.has("pluginLists") -> Kind.CS_REPO
                o.has("resources") && (o.has("catalogs") || o.has("types")) && o.has("id") -> Kind.STREMIO
                o.optJSONObject("meta")?.let { it.has("signingKeyFingerprint") || it.has("name") } == true || o.has("index_v2") -> Kind.TACHI_REPO
                o.has("ProductName") || (o.has("ServerName") && o.has("Version") && o.has("Id")) ->
                    if (o.optString("ProductName").contains("Emby", true)) Kind.EMBY else Kind.JELLYFIN
                else -> null
            }
        }
        if (text.startsWith("[")) {
            val a = runCatching { JSONArray(text) }.getOrNull() ?: return null
            val items = (0 until minOf(a.length(), 50)).mapNotNull { a.optJSONObject(it) }
            if (items.isEmpty()) return null
            return when {
                items.any { it.has("internalName") && it.has("url") } -> Kind.CS_LIST
                items.any { it.has("pkg") && it.has("apk") } ->
                    if (items.any { it.optString("pkg").contains(".animeextension.") }) Kind.ANIYOMI_REPO else Kind.MIHON_REPO
                items.any { it.has("sourceCodeUrl") || (it.has("baseUrl") && (it.has("itemType") || it.has("isManga"))) } -> Kind.JS_REPO
                else -> null
            }
        }
        // Protobuf store index (Mihon v2) has no text marker; trust the file name.
        val path = url.substringBefore('?').lowercase()
        if (path.endsWith("/index.pb") || path.endsWith("/index.pb.gz")) return Kind.TACHI_REPO
        return null
    }

    private fun zipKind(bytes: ByteArray): Kind? = runCatching {
        ZipInputStream(bytes.inputStream()).use { z ->
            var apk = false
            while (true) {
                val e = z.nextEntry ?: break
                when (e.name) {
                    "manifest.json" -> if (z.readBytes().decodeToString().contains("pluginClassName")) return Kind.CS_PLUGIN
                    "AndroidManifest.xml" -> apk = true
                }
            }
            if (apk) Kind.TACHI_APK else null
        }
    }.getOrNull()

    // region install

    private const val MAX_BYTES = 64L * 1024 * 1024

    /** Detects what [raw] is and adds it with the right engine. */
    suspend fun add(context: Context, raw: String): Result<Outcome> = withContext(Dispatchers.IO) {
        runCatching {
            Bridges.start(context.applicationContext)
            var h = hint(raw)
            h.shortCode?.let { code -> h = hint(resolveShortCode(context, code)) }
            when (h.kind) {
                Kind.CS_REPO -> return@runCatching addAs(context, Kind.CS_REPO, h.url, null)
                Kind.STREMIO -> return@runCatching addAs(context, Kind.STREMIO, h.url, null)
                Kind.ANIYOMI_REPO, Kind.TACHI_REPO -> return@runCatching addAs(context, h.kind!!, h.url, null)
                Kind.JS_REPO -> return@runCatching addAs(context, Kind.JS_REPO, h.url, null)
                else -> Unit
            }
            var lastError: Throwable? = null
            for (url in if (h.kind != null) listOf(h.url) else candidates(h.url)) {
                val body = runCatching { download(context, url) }.onFailure { lastError = it }.getOrNull() ?: continue
                val kind = sniff(url, body) ?: continue
                return@runCatching addAs(context, kind, url, body)
            }
            throw IllegalStateException(
                if (lastError != null && candidates(h.url).size == 1) "Couldn't open that link (${lastError?.message})"
                else "Yumeiro couldn't find an extension, repository, add-on or server at that link",
            )
        }
    }

    /** A picked or shared file: `.cs3`, an extension `.apk`, or a saved index / manifest. */
    suspend fun addFile(context: Context, uri: Uri, displayName: String?): Result<Outcome> = withContext(Dispatchers.IO) {
        runCatching {
            Bridges.start(context.applicationContext)
            val head = context.contentResolver.openInputStream(uri)?.use { it.readNBytesCompat(MAX_BYTES) } ?: error("Could not open the file")
            when (sniff(displayName.orEmpty(), head)) {
                Kind.CS_PLUGIN -> {
                    val ext = ExtensionManager.installFromFile(context, uri, displayName)
                    Outcome(Kind.CS_PLUGIN, "${ext.name} installed")
                }
                Kind.TACHI_APK -> Outcome(Kind.TACHI_APK, installApk(context, head))
                else -> error("This file isn't an extension Yumeiro knows (.cs3, Aniyomi or Mihon .apk)")
            }
        }
    }

    private suspend fun addAs(context: Context, kind: Kind, url: String, body: ByteArray?): Outcome = when (kind) {
        Kind.CS_PLUGIN -> ExtensionManager.installFromUrl(context, url).let { Outcome(kind, "${it.name} installed") }
        Kind.CS_REPO, Kind.CS_LIST -> ExtensionManager.addRepository(context, url).let { Outcome(kind, "${it.name} added. Its extensions are in the market") }
        Kind.ANIYOMI_REPO, Kind.MIHON_REPO, Kind.TACHI_REPO -> {
            val k = when (kind) {
                Kind.ANIYOMI_REPO -> TachiyomiExtensions.Kind.ANIME
                Kind.MIHON_REPO -> TachiyomiExtensions.Kind.MANGA
                else -> tachiKind(context, url)
            }
            val n = TachiyomiExtensions.addRepo(context, url, k).getOrThrow()
            Outcome(if (k == TachiyomiExtensions.Kind.ANIME) Kind.ANIYOMI_REPO else Kind.MIHON_REPO, "${k.label} repository added ($n extensions)")
        }
        Kind.TACHI_APK -> Outcome(kind, installApk(context, body ?: download(context, url)))
        Kind.STREMIO -> StremioAddons.add(context, url).getOrThrow().let { Outcome(kind, "${it.name} added") }
        Kind.JS_REPO -> JsSources.addRepo(context, url).getOrThrow().let { Outcome(kind, "JavaScript repository added ($it sources)") }
        Kind.JELLYFIN, Kind.EMBY, Kind.PLEX -> {
            val base = url.substringBefore("/System/Info/Public").substringBefore("/identity").removeSuffix("/manifest.json")
            val sk = when (kind) { Kind.EMBY -> HomeServers.Kind.EMBY; Kind.PLEX -> HomeServers.Kind.PLEX; else -> HomeServers.Kind.JELLYFIN }
            Outcome(kind, "${sk.label} server found. Sign in to add it", needsSignIn = sk, url = base)
        }
    }

    /** Aniyomi and Mihon share the index format; package names tell them apart. */
    private suspend fun tachiKind(context: Context, url: String): TachiyomiExtensions.Kind {
        val base = TachiyomiExtensions.normalizeRepo(url)
        val idx = TachiyomiExtensions.fetchRepoIndex(context, TachiyomiExtensions.Repo(base, TachiyomiExtensions.Kind.MANGA))
        return if (idx.entries.any { it.pkg.contains(".animeextension.") }) TachiyomiExtensions.Kind.ANIME else TachiyomiExtensions.Kind.MANGA
    }

    private suspend fun installApk(context: Context, bytes: ByteArray): String {
        val tmp = File(context.cacheDir, "universal-${System.nanoTime()}.apk")
        try {
            tmp.writeBytes(bytes)
            val k = TachiyomiExtensions.apkKind(context, tmp) ?: error("This APK isn't an Aniyomi or Mihon extension")
            TachiyomiExtensions.installFile(context, tmp, k)
            return "${k.label} extension installed"
        } finally { tmp.delete() }
    }

    /** CloudStream short codes, resolved the way CloudStream does (cutt.ly, or py.md for `!code`). */
    private fun resolveShortCode(context: Context, code: String): String {
        val host = if (code.startsWith("!")) "https://py.md/" + code.removePrefix("!") else "https://cutt.ly/$code"
        val http = NetworkClient.get(context).newBuilder().followRedirects(false).followSslRedirects(false).build()
        val loc = http.newCall(Request.Builder().url(host).build()).execute().use { it.header("Location") }
        require(!loc.isNullOrBlank() && !loc.contains("/404") && loc.removeSuffix("/") != host.substringBeforeLast('/')) { "That short code doesn't point to a repository" }
        return loc
    }

    private fun download(context: Context, url: String): ByteArray =
        NetworkClient.get(context).newCall(Request.Builder().url(url).header("Accept", "application/json, */*").build()).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
            r.body.byteStream().readNBytesCompat(MAX_BYTES)
        }

    private fun java.io.InputStream.readNBytesCompat(max: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = read(buf); if (n < 0) break
            total += n
            if (total > max) throw java.io.IOException("File too large")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    // endregion
}
