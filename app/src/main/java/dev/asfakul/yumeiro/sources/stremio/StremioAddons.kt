/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.stremio

import android.content.Context
import android.util.Log
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.Request
import org.json.JSONArray
import java.io.File

/**
 * Stremio add-ons over their public HTTP protocol (manifest, catalog, meta, stream, subtitles).
 * https://stremio.github.io/stremio-addon-sdk/protocol.html
 *
 * Add-ons with catalogs become browsable sources. Add-ons that only answer `stream` or
 * `subtitles` (most of them) are asked for links whenever any Stremio title is played — the
 * way the Stremio app itself works. Torrent-only results are skipped (no torrent engine).
 */
object StremioAddons {
    private const val TAG = "Stremio"

    /** A known public catalog add-on (metadata only, no streams) offered as a one-tap start. */
    const val CINEMETA = "https://v3-cinemeta.strem.io/manifest.json"

    data class Catalog(val type: String, val id: String, val name: String, val searchable: Boolean, val requiresExtra: Boolean)

    /** A resource an add-on answers, limited to some types and id prefixes (null = any). */
    data class Resource(val name: String, val types: List<String>?, val idPrefixes: List<String>?)

    data class Addon(
        val manifestUrl: String,
        val id: String,
        val name: String,
        val version: String,
        val description: String,
        val logo: String?,
        val types: List<String>,
        val idPrefixes: List<String>?,
        val resources: List<Resource>,
        val catalogs: List<Catalog>,
    ) {
        val base get() = manifestUrl.removeSuffix("/manifest.json").removeSuffix("/")

        fun supports(resource: String, type: String, id: String?): Boolean {
            val r = resources.firstOrNull { it.name == resource } ?: return false
            val types = r.types ?: this.types
            val prefixes = r.idPrefixes ?: this.idPrefixes
            if (types.isNotEmpty() && type !in types) return false
            if (id != null && !prefixes.isNullOrEmpty() && prefixes.none { id.startsWith(it) }) return false
            return true
        }
    }

    private val _addons = MutableStateFlow<List<Addon>>(emptyList())
    val addons: StateFlow<List<Addon>> = _addons.asStateFlow()

    val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var started = false
    private lateinit var appContext: Context

    private fun file(context: Context) = File(context.filesDir, "stremio").apply { mkdirs() }.let { File(it, "addons.json") }

    fun start(context: Context) {
        if (started) return
        started = true
        appContext = context.applicationContext
        scope.launch {
            val urls = runCatching { JSONArray(file(appContext).readText()).let { a -> List(a.length()) { a.getString(it) } } }.getOrDefault(emptyList())
            val loaded = urls.mapNotNull { u -> runCatching { fetchManifest(appContext, u) }.onFailure { Log.w(TAG, u, it) }.getOrNull() }
            _addons.value = loaded
            publish()
        }
    }

    /** Accepts `https://…/manifest.json`, `stremio://…/manifest.json` or an add-on base URL. */
    fun normalize(raw: String): String {
        var u = raw.trim()
        if (u.startsWith("stremio://")) u = "https://" + u.removePrefix("stremio://")
        if (!u.startsWith("http")) u = "https://$u"
        if (!u.endsWith("/manifest.json")) u = u.removeSuffix("/") + "/manifest.json"
        return u
    }

    suspend fun add(context: Context, raw: String): Result<Addon> = withContext(Dispatchers.IO) {
        runCatching {
            if (!started) start(context)
            val addon = fetchManifest(context, normalize(raw))
            _addons.value = _addons.value.filterNot { it.manifestUrl == addon.manifestUrl } + addon
            save(context)
            publish()
            addon
        }
    }

    fun remove(context: Context, manifestUrl: String) {
        _addons.value = _addons.value.filterNot { it.manifestUrl == manifestUrl }
        save(context)
        publish()
    }

    private fun save(context: Context) {
        file(context).writeText(JSONArray(_addons.value.map { it.manifestUrl }).toString())
    }

    private fun publish() {
        val taken = HashSet<String>()
        val apis = _addons.value.filter { a -> a.catalogs.isNotEmpty() && a.resources.any { it.name == "catalog" } }
            .map { StremioProvider(it, SourceHub.uniqueName(it.name, null, taken).also(taken::add)) }
        SourceHub.publishVideo("stremio", SourceHub.Ecosystem.STREMIO, apis)
    }

    suspend fun fetchManifest(context: Context, url: String): Addon {
        val o = getJson(context, url).jsonObject
        val types = o.strings("types")
        val resources = (o["resources"] as? JsonArray).orEmpty().map { r ->
            when (r) {
                is JsonPrimitive -> Resource(r.content, null, null)
                else -> r.jsonObject.let { Resource(it.str("name").orEmpty(), it.stringsOrNull("types"), it.stringsOrNull("idPrefixes")) }
            }
        }
        val catalogs = (o["catalogs"] as? JsonArray).orEmpty().map { it.jsonObject }.map { c ->
            val extra = (c["extra"] as? JsonArray).orEmpty().map { it.jsonObject }
            val searchable = extra.any { it.str("name") == "search" } || "search" in c.strings("extraSupported")
            val requires = extra.any { it.bool("isRequired") == true && it.str("name") != "search" } ||
                c.strings("extraRequired").any { it != "search" } ||
                (extra.any { it.str("name") == "search" && it.bool("isRequired") == true })
            Catalog(c.str("type").orEmpty(), c.str("id").orEmpty(), c.str("name") ?: c.str("id").orEmpty(), searchable, requires)
        }
        return Addon(
            manifestUrl = url,
            id = o.str("id") ?: url,
            name = o.str("name") ?: url.substringAfter("://").substringBefore("/"),
            version = o.str("version").orEmpty(),
            description = o.str("description").orEmpty(),
            logo = o.str("logo"),
            types = types,
            idPrefixes = o.stringsOrNull("idPrefixes"),
            resources = resources,
            catalogs = catalogs,
        )
    }

    suspend fun getJson(context: Context, url: String): JsonElement = withContext(Dispatchers.IO) {
        NetworkClient.get(context).newCall(Request.Builder().url(url).header("Accept", "application/json").build()).execute().use { r ->
            if (!r.isSuccessful) error("Add-on answered HTTP ${r.code}")
            json.parseToJsonElement(r.body.string())
        }
    }

    internal fun context(): Context = appContext

    // Small JSON helpers shared by the provider.
    internal fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
    internal fun JsonObject.bool(k: String): Boolean? = (this[k] as? JsonPrimitive)?.booleanOrNull
    internal fun JsonObject.strings(k: String): List<String> = stringsOrNull(k).orEmpty()
    internal fun JsonObject.stringsOrNull(k: String): List<String>? =
        (this[k] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
}
