/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.ecosystem

import dev.asfakul.yumeiro.sources.SourceHub
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * ADR-047: one parser per repository *format*, all producing the same [ParsedRepo]. A parser only
 * reads JSON; it never downloads, installs or runs anything. Which runtime runs the plugins is
 * the job of the ecosystem's [RuntimeAdapter], looked up by [ParsedRepo.ecosystem].
 *
 * Formats (read from the projects themselves, see ADR-047 for commits):
 * - CloudStream `repo.json`: `{name, manifestVersion, pluginLists}` and no `id`/`packageName`.
 *   Detected only; the existing CloudStream loader handles it, unchanged.
 * - Nuvio `manifest.json`: an object `{name, version, scrapers:[{id, filename, …}]}` (NuvioStreaming
 *   PluginModels) or a bare array of the scraper objects (tapframe/nuvio-providers HEAD).
 * - Vega `manifest.json`: a bare array of `{display_name, value, version, type, disabled}`; code is
 *   `dist/<value>/<file>.js` next to the manifest.
 * - SkyStream `repo.json` (`{name, id|packageName, pluginLists}`) and `mega_repo.json`
 *   (`{name, id, repos:[repo.json urls]}`); plugin lists are arrays of `{packageName, url: *.sky}`.
 */
data class PluginEntry(
    val id: String,
    val name: String,
    val version: String,
    /** File name → absolute URL of the code the adapter needs. */
    val files: Map<String, String>,
    val types: List<String> = emptyList(),
    val lang: String? = null,
    val icon: String? = null,
    val disabled: Boolean = false,
)

data class ParsedRepo(
    val ecosystem: SourceHub.Ecosystem,
    val format: String,
    val name: String,
    val plugins: List<PluginEntry>,
    /** More JSON files this repo points to (SkyStream plugin lists or child repos). */
    val children: List<String> = emptyList(),
)

interface ManifestParser {
    val format: String
    fun accepts(root: JsonElement): Boolean
    fun parse(root: JsonElement, sourceUrl: String): ParsedRepo
}

object RepoFormats {
    internal val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Order matters only where shapes overlap: SkyStream before CloudStream (both use `pluginLists`). */
    val parsers: List<ManifestParser> = listOf(SkyStreamRepoParser, CloudStreamRepoParser, NuvioManifestParser, VegaManifestParser)

    /** The parser for [text], or null when no format matches (not JSON, or another kind of file). */
    fun parse(text: String, sourceUrl: String): ParsedRepo? {
        val root = runCatching { json.parseToJsonElement(text.trimStart('\uFEFF', ' ', '\n', '\r', '\t')) }.getOrNull() ?: return null
        val p = parsers.firstOrNull { runCatching { it.accepts(root) }.getOrDefault(false) } ?: return null
        return p.parse(root, sourceUrl)
    }

    /** Folder of [url] with a trailing slash (`…/manifest.json` → `…/`), query and fragment dropped. */
    fun baseOf(url: String): String = url.substringBefore('#').substringBefore('?').substringBeforeLast('/') + "/"

    /** [ref] resolved against [base] (absolute links pass through; `./x` and `/x` are handled). */
    fun resolve(base: String, ref: String): String {
        val r = ref.trim()
        if (r.startsWith("http://") || r.startsWith("https://")) return r
        if (r.startsWith("//")) return base.substringBefore("//") + r
        if (r.startsWith("/")) {
            val scheme = base.substringBefore("://")
            val host = base.substringAfter("://").substringBefore('/')
            return "$scheme://$host$r"
        }
        return baseOf(base) + r.removePrefix("./")
    }

    internal fun JsonObject.s(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    internal fun JsonObject.b(k: String): Boolean? = (this[k] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.contentOrNull?.toBooleanStrictOrNull() }
    internal fun JsonObject.strings(k: String): List<String> = when (val v = this[k]) {
        is JsonArray -> v.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        is JsonPrimitive -> listOfNotNull(v.contentOrNull)
        else -> emptyList()
    }
    internal fun JsonElement.objects(): List<JsonObject> = (this as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

    /** Ids become folder names: keep them plain so a hostile manifest can't escape the folder. */
    fun safeId(raw: String): String = buildString {
        for (c in raw.take(80)) append(if (c.isLetterOrDigit() || c == '-' || c == '_' || c == '.') c else '_')
    }.trim('.').ifBlank { "plugin" }
}

/** CloudStream repo.json. Recognised so it is routed to the existing, untouched loader. */
object CloudStreamRepoParser : ManifestParser {
    override val format = "cloudstream-repo"
    override fun accepts(root: JsonElement): Boolean =
        root is JsonObject && root["pluginLists"] is JsonArray && !root.containsKey("id") && !root.containsKey("packageName")

    override fun parse(root: JsonElement, sourceUrl: String): ParsedRepo = with(RepoFormats) {
        val o = root as JsonObject
        ParsedRepo(SourceHub.Ecosystem.CLOUDSTREAM, format, o.s("name") ?: "CloudStream repository", emptyList(),
            o.strings("pluginLists").map { resolve(sourceUrl, it) })
    }
}

/** Nuvio: object form (app's PluginModels) or bare-array form (nuvio-providers). */
object NuvioManifestParser : ManifestParser {
    override val format = "nuvio-manifest"
    private fun isScraper(o: JsonObject) = o.containsKey("filename") && o.containsKey("id")
    override fun accepts(root: JsonElement): Boolean = when (root) {
        is JsonObject -> root["scrapers"] is JsonArray
        is JsonArray -> with(RepoFormats) { root.objects().let { l -> l.isNotEmpty() && l.any(::isScraper) } }
        else -> false
    }

    override fun parse(root: JsonElement, sourceUrl: String): ParsedRepo = with(RepoFormats) {
        val name = (root as? JsonObject)?.s("name") ?: "Nuvio scrapers"
        val list = (root as? JsonObject)?.get("scrapers") ?: root
        val plugins = list.objects().filter(::isScraper).map { o ->
            val id = safeId(o.s("id")!!)
            PluginEntry(
                id = id, name = o.s("name") ?: id, version = o.s("version") ?: "0",
                files = mapOf("scraper.js" to resolve(sourceUrl, o.s("filename")!!)),
                types = o.strings("supportedTypes"), icon = o.s("logo"),
                disabled = o.b("enabled") == false,
            )
        }
        ParsedRepo(SourceHub.Ecosystem.NUVIO, format, name, plugins)
    }
}

/** Vega: bare array of providers, code in `dist/<value>/`. */
object VegaManifestParser : ManifestParser {
    override val format = "vega-manifest"
    /** Files a Vega provider build has; `episodes` and `settings` are optional. */
    val FILES = listOf("catalog", "posts", "meta", "stream", "episodes")
    val REQUIRED = setOf("catalog", "posts", "meta", "stream")

    override fun accepts(root: JsonElement): Boolean = with(RepoFormats) {
        root is JsonArray && root.objects().let { l -> l.isNotEmpty() && l.any { it.containsKey("display_name") && it.containsKey("value") && it.containsKey("version") } }
    }

    override fun parse(root: JsonElement, sourceUrl: String): ParsedRepo = with(RepoFormats) {
        val base = baseOf(sourceUrl)
        val plugins = root.objects().filter { it.s("value") != null && it.s("display_name") != null }.map { o ->
            val value = safeId(o.s("value")!!)
            PluginEntry(
                id = value, name = o.s("display_name")!!, version = o.s("version") ?: "0",
                files = FILES.associate { f -> "$f.js" to "${base}dist/$value/$f.js" },
                types = listOfNotNull(o.s("type")), icon = o.s("icon"),
                disabled = o.b("disabled") == true,
            )
        }
        ParsedRepo(SourceHub.Ecosystem.VEGA, format, "Vega providers", plugins)
    }
}

/** SkyStream repo.json / mega_repo.json. Plugins are `.sky` zips listed in the plugin lists. */
object SkyStreamRepoParser : ManifestParser {
    override val format = "skystream-repo"
    override fun accepts(root: JsonElement): Boolean = root is JsonObject && root.containsKey("name") &&
        (root.containsKey("id") || root.containsKey("packageName")) && (root["pluginLists"] is JsonArray || root["repos"] is JsonArray)

    override fun parse(root: JsonElement, sourceUrl: String): ParsedRepo = with(RepoFormats) {
        val o = root as JsonObject
        val children = (o.strings("pluginLists") + o.strings("repos")).map { resolve(sourceUrl, it) }
        ParsedRepo(SourceHub.Ecosystem.SKYSTREAM, format, o.s("name") ?: "SkyStream repository", emptyList(), children)
    }

    /**
     * A SkyStream plugin list (array of `{packageName, name, version, url, baseUrl, languages,
     * categories}`, as in akashdh11/skystream-plugins dist/plugins.json). F-05-S: `version` may be a
     * number; the first language is the plugin's language; categories are its types.
     */
    fun parsePluginList(root: JsonElement, sourceUrl: String): List<PluginEntry> = with(RepoFormats) {
        root.objects().filter { it.s("packageName") != null && it.s("url") != null }.take(MAX_PLUGINS).map { p ->
            val id = safeId(p.s("packageName")!!)
            PluginEntry(id, p.s("name") ?: id, p.s("version") ?: "0", mapOf(SKY_FILE to resolve(sourceUrl, p.s("url")!!)),
                (p.strings("tvTypes") + p.strings("categories")).distinct(), p.s("lang") ?: p.strings("languages").firstOrNull(),
                p.s("iconUrl") ?: p.s("icon"), disabled = p.b("disabled") == true)
        }
    }

    /** True for a SkyStream plugin list: a JSON array whose entries have packageName and a .sky url. */
    fun isPluginList(root: JsonElement): Boolean = with(RepoFormats) {
        val list = root.objects()
        list.isNotEmpty() && list.all { it.s("packageName") != null && it.s("url") != null } && list.any { it.s("url")!!.substringBefore('?').endsWith(".sky") }
    }

    const val SKY_FILE = "plugin.sky"
    /** F-05-S: at most this many plugin lists / child repos are read for one repository. */
    const val MAX_CHILDREN = 16
    const val MAX_PLUGINS = 300
}
