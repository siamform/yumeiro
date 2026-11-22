/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.ecosystem

import android.content.Context
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSearchResponseList
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.sources.SourceHub
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okio.ByteString.Companion.decodeBase64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream

/**
 * F-05-S (ADR-058): runtime adapter for SkyStream plugins (https://github.com/akashdh11/skystream).
 * A plugin is a `.sky` zip of `plugin.json` (the manifest, with `baseUrl`) and `plugin.js`, which
 * defines the globals `getHome(cb)`, `search(query, cb)`, `load(url, cb)` and `loadStreams(url, cb)`
 * and answers `cb({success, data})`. They map onto CloudStream's [MainAPI] like Vega's, so they show
 * up as normal sources. Same sandbox as Vega and Nuvio (ADR-047): a fresh QuickJS per call,
 * 48 / 32 MB, 25 s, 30 requests, SRC-2 guard. Written from SkyStream's public developer guide and
 * SDK type list; no SkyStream code is used. Not supported (reported, not faked): captchas,
 * sub-providers (`providers` in plugin.json), generated playlists (`magic_m3u8:`), DRM and torrents.
 */
object SkyStreamAdapter : RuntimeAdapter {
    override val ecosystem = SourceHub.Ecosystem.SKYSTREAM
    override val flag = FeatureFlags.Flag.SCRIPTS_SKYSTREAM
    const val PRELUDE = "sky_runtime.js"
    const val MAX_ENTRY_BYTES = 4L * 1024 * 1024
    const val MAX_ENTRIES = 64
    const val MAX_CATEGORIES = 16
    const val MAX_ITEMS = 60
    const val MAX_EPISODES = 2000
    const val MAX_STREAMS = 100

    override fun required(p: PluginEntry): Set<String> = setOf(SkyStreamRepoParser.SKY_FILE)

    /** What a `.sky` file holds. */
    data class Package(val js: String, val manifest: JsonObject)

    /**
     * Reads plugin.js and plugin.json out of a `.sky` zip. Only those two names count (any folder
     * in front is ignored, nothing is written to disk); each is capped at [MAX_ENTRY_BYTES] and the
     * zip at [MAX_ENTRIES] entries, so a zip bomb or a path like `../x` can do nothing.
     */
    fun readPackage(sky: File): Package {
        var js: String? = null
        var manifest: JsonObject? = null
        var entries = 0
        ZipInputStream(sky.inputStream().buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                require(++entries <= MAX_ENTRIES) { "This plugin file has too many entries" }
                if (e.isDirectory) continue
                val name = e.name.substringAfterLast('/').substringAfterLast('\\')
                if (name != "plugin.js" && name != "plugin.json") continue
                val bytes = java.io.ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val n = zip.read(buf)
                    if (n < 0) break
                    total += n
                    require(total <= MAX_ENTRY_BYTES) { "$name is too big" }
                    bytes.write(buf, 0, n)
                }
                val text = bytes.toString(Charsets.UTF_8.name())
                if (name == "plugin.js" && js == null) js = text
                if (name == "plugin.json" && manifest == null) {
                    manifest = runCatching { RepoFormats.json.parseToJsonElement(text) as? JsonObject }.getOrNull()
                        ?: error("plugin.json is not valid")
                }
            }
        }
        return Package(js?.takeIf { it.isNotBlank() } ?: error("This plugin has no plugin.js"), manifest ?: JsonObject(emptyMap()))
    }

    /** The `manifest` global: plugin.json with `baseUrl` set (first mirror when only `domains` is given). */
    fun manifestOf(m: JsonObject): JsonObject {
        val base = m.str("baseUrl") ?: (m["domains"] as? JsonArray)?.firstNotNullOfOrNull { (it as? JsonObject)?.str("url") }
        return if (base == null || m.str("baseUrl") == base) m else JsonObject(m + ("baseUrl" to JsonPrimitive(base)))
    }

    override suspend fun prepare(context: Context, plugin: EcosystemRegistry.Installed, dir: File): JsonObject {
        val m = readPackage(File(dir, SkyStreamRepoParser.SKY_FILE)).manifest
        val keep = listOf("name", "version", "baseUrl", "packageName", "description")
        return buildJsonObject { put("manifest", JsonObject(manifestOf(m).filterKeys { it in keep || it == "languages" || it == "categories" })) }
    }

    override fun build(context: Context, plugin: EcosystemRegistry.Installed, dir: File, displayName: String): MainAPI {
        val sky = File(dir, SkyStreamRepoParser.SKY_FILE)
        val pkg = readPackage(sky)
        require(!pkg.manifest.containsKey("providers")) { "SkyStream sub-providers aren't supported yet" }
        return SkyStreamProvider(context.applicationContext, plugin, sky, displayName, manifestOf(pkg.manifest))
    }

    // region mapping (pure, unit-tested)

    internal fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    internal fun JsonObject.int(k: String): Int? = (this[k] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toInt()

    fun typeOf(t: String?): TvType = when (t?.lowercase()) {
        "series", "tvseries", "tv" -> TvType.TvSeries
        "anime" -> TvType.Anime
        "livestream", "live", "livetv", "iptv" -> TvType.Live
        else -> TvType.Movie
    }

    data class Item(val title: String, val url: String, val poster: String?, val type: TvType)

    fun itemsOf(e: JsonElement?): List<Item> = (e as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { o ->
        val url = o.str("url") ?: return@mapNotNull null
        Item(o.str("title") ?: o.str("name") ?: url, url, o.str("posterUrl") ?: o.str("poster"), typeOf(o.str("type")))
    }.distinctBy { it.url }.take(MAX_ITEMS)

    /** getHome's map as rows: "Trending" first (SkyStream's hero row), then the plugin's order. */
    fun homeOf(e: JsonElement?): List<Pair<String, List<Item>>> {
        val o = e as? JsonObject ?: return emptyList()
        val rows = o.entries.map { (k, v) -> k to itemsOf(v) }.filter { it.second.isNotEmpty() }
        return (rows.filter { it.first == "Trending" } + rows.filter { it.first != "Trending" }).take(MAX_CATEGORIES)
    }

    /** What one StreamResult becomes. */
    sealed interface Stream {
        data class Ok(
            val url: String, val label: String, val quality: Int, val headers: Map<String, String>,
            val type: ExtractorLinkType, val subtitles: List<Pair<String, String>>,
        ) : Stream
        data class Skip(val reason: String) : Stream
    }

    const val MAGIC_PROXY = "MAGIC_PROXY_v1"

    fun streamOf(o: JsonObject, sourceName: String): Stream {
        if (o.str("drmKid") != null || o.str("drmKey") != null || o.str("licenseUrl") != null) return Stream.Skip("DRM")
        var url = o.str("url") ?: return Stream.Skip("No link")
        if (url.startsWith("magic_m3u8:")) return Stream.Skip("Generated playlist")
        // SkyStream's byte proxy only adds the plugin's headers; Yumeiro's player sends them itself.
        if (url.startsWith(MAGIC_PROXY)) url = url.removePrefix(MAGIC_PROXY).decodeBase64()?.utf8() ?: return Stream.Skip("No link")
        if (url.startsWith("magnet:", true) || url.substringBefore('?').endsWith(".torrent", true)) return Stream.Skip("Torrent")
        if (!url.startsWith("https://") && !url.startsWith("http://")) return Stream.Skip("Unsupported link")
        val headers = (o["headers"] as? JsonObject)?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }?.toMap().orEmpty()
        val q = o.str("quality")
        val qualityNum = VegaAdapter.qualityOf(q, o.str("name"), o.str("source"))
        val path = url.substringBefore('?').lowercase()
        val type = when {
            path.endsWith(".m3u8") || q?.contains("hls", true) == true -> ExtractorLinkType.M3U8
            path.endsWith(".mpd") -> ExtractorLinkType.DASH
            else -> ExtractorLinkType.VIDEO
        }
        val label = listOfNotNull(o.str("source") ?: o.str("name"), q?.let { if (it.all(Char::isDigit)) "${it}p" else it })
            .distinct().joinToString(" · ").ifBlank { sourceName }.take(160)
        val subs = (o["subtitles"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            .mapNotNull { s -> s.str("url")?.let { (s.str("label") ?: s.str("lang") ?: "Subtitle") to it } }
        return Stream.Ok(url, label, qualityNum, headers, type, subs)
    }

    /** One playable target for loadLinks: the URL for loadStreams plus any "instant" streams. */
    fun target(url: String, streams: JsonElement?): String = buildJsonObject {
        put("u", url)
        (streams as? JsonArray)?.let { put("s", JsonArray(it.take(MAX_STREAMS))) }
    }.toString()

    // endregion
}

internal class SkyStreamProvider(
    private val context: Context,
    private val plugin: EcosystemRegistry.Installed,
    private val sky: File,
    displayName: String,
    private val manifest: JsonObject,
) : MainAPI() {
    override var name = displayName
    override var lang = plugin.lang ?: "all"
    override var mainUrl = with(SkyStreamAdapter) { manifest.str("baseUrl") } ?: "https://${plugin.id}.skystream.invalid"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.Live)
    override val mainPage: List<MainPageData> = listOf(MainPageData("Home", ""))

    init { sourcePlugin = "yumeiro-bridge:skystream:" + plugin.key }

    private suspend fun call(fn: String, vararg args: String): JsonElement {
        // The .sky is re-read per call: its hash was checked at publish, and nothing is unpacked to disk.
        val js = withContext(Dispatchers.IO) { SkyStreamAdapter.readPackage(sky).js }
        return ScriptRuntime.call(
            context, plugin.storeName, listOf(ScriptRuntime.Module("plugin", sky, raw = true, code = js)), "plugin", fn,
            JsonArray(args.map { JsonPrimitive(it) }).toString(), invoker = "__invokeSky",
            globals = mapOf("manifest" to manifest.toString()), preludes = listOf(SkyStreamAdapter.PRELUDE),
        )
    }

    private fun results(items: List<SkyStreamAdapter.Item>): List<SearchResponse> = items.map { i ->
        newMovieSearchResponse(i.title, i.url, i.type, fix = false) { posterUrl = i.poster }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (page > 1) return newHomePageResponse(request.name, emptyList(), false)
        val rows = SkyStreamAdapter.homeOf(call("getHome"))
        return newHomePageResponse(rows.map { (title, items) -> HomePageList(title, results(items)) }, false)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        if (page > 1) return newSearchResponseList(emptyList(), false)
        return newSearchResponseList(results(SkyStreamAdapter.itemsOf(call("search", query))), false)
    }

    override suspend fun search(query: String): List<SearchResponse> = search(query, 1).items

    override suspend fun load(url: String): LoadResponse { return with(SkyStreamAdapter) {
        val info = call("load", url) as? JsonObject ?: error("This source returned no details")
        val title = info.str("title") ?: url
        val type = typeOf(info.str("type"))
        val itemUrl = info.str("url") ?: url
        val eps = (info["episodes"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.filter { it.str("url") != null }.take(MAX_EPISODES)
        val poster = info.str("posterUrl")
        val plotText = info.str("description")
        val tagList = (info["tags"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        if (type == TvType.Live) return newLiveStreamLoadResponse(title, itemUrl, target(itemUrl, info["streams"])) {
            posterUrl = poster; plot = plotText
        }
        if (type == TvType.Movie || eps.isEmpty()) {
            val first = eps.firstOrNull()
            val data = if (first != null) target(first.str("url")!!, first["streams"]) else target(itemUrl, info["streams"])
            return newMovieLoadResponse(title, itemUrl, TvType.Movie, data) {
                posterUrl = poster; backgroundPosterUrl = info.str("bannerUrl"); plot = plotText; tags = tagList
                year = info.int("year")
            }
        }
        val episodes = eps.mapIndexed { n, e ->
            newEpisode(target(e.str("url")!!, e["streams"]), {
                name = e.str("name"); season = e.int("season") ?: 1; episode = e.int("episode") ?: (n + 1)
                posterUrl = e.str("posterUrl"); description = e.str("description")
            }, false)
        }
        newTvSeriesLoadResponse(title, itemUrl, if (type == TvType.Anime) TvType.Anime else TvType.TvSeries, episodes) {
            posterUrl = poster; backgroundPosterUrl = info.str("bannerUrl"); plot = plotText; tags = tagList
            year = info.int("year")
        }
    } }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val t = runCatching { RepoFormats.json.parseToJsonElement(data) as? JsonObject }.getOrNull()
        val url = t?.let { with(SkyStreamAdapter) { it.str("u") } } ?: data
        val instant = (t?.get("s") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val streams = instant + (call("loadStreams", url) as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        var found = false
        val skipped = HashMap<String, Int>()
        for (o in streams.take(SkyStreamAdapter.MAX_STREAMS)) {
            when (val s = SkyStreamAdapter.streamOf(o, name)) {
                is SkyStreamAdapter.Stream.Skip -> skipped[s.reason] = (skipped[s.reason] ?: 0) + 1
                is SkyStreamAdapter.Stream.Ok -> {
                    callback(newExtractorLink(name, s.label, s.url, s.type) {
                        headers = s.headers
                        referer = s.headers["Referer"] ?: s.headers["referer"] ?: ""
                        quality = s.quality
                    })
                    s.subtitles.forEach { (label, u) -> subtitleCallback(SubtitleFile(label, u)) }
                    found = true
                }
            }
        }
        if (skipped.isNotEmpty()) android.util.Log.i("SkyStreamProvider", "$name: skipped $skipped")
        return found
    }
}
