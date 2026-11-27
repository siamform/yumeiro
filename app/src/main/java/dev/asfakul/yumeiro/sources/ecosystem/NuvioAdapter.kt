/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.ecosystem

import dev.asfakul.yumeiro.l10n.tr
import dev.asfakul.yumeiro.R
import android.content.Context
import android.util.Log
import com.lagradost.cloudstream3.MainAPI
import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.online.LinkEvent
import dev.asfakul.yumeiro.online.OnlineLink
import dev.asfakul.yumeiro.online.OnlineSubtitle
import dev.asfakul.yumeiro.online.StreamAddon
import dev.asfakul.yumeiro.online.StreamRequest
import dev.asfakul.yumeiro.sources.SourceHub
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.io.File

/**
 * F-05-N (ADR-057): runs Nuvio "local scrapers" (https://github.com/tapframe/nuvio-providers):
 * one JavaScript file per scraper that exports `getStreams(tmdbId, mediaType, season, episode)` and
 * answers `[{name, title, url, quality, size?, headers?, subtitles?}]`. Scrapers have no catalog, so
 * [build] publishes nothing; their streams join the opened title's list through [NuvioStreams].
 * Same sandbox as Vega (ADR-047): a fresh QuickJS per call, 48 / 32 MB, 25 s, 30 requests, SRC-2.
 * Written from the public contract; no Nuvio code is used.
 */
object NuvioAdapter : RuntimeAdapter {
    override val ecosystem = SourceHub.Ecosystem.NUVIO
    override val flag = FeatureFlags.Flag.SCRIPTS_NUVIO
    const val FILE = "scraper.js"
    const val MODULE = "scraper"

    /** What Nuvio's own runtime offers to `require` (and what Yumeiro's runtime offers too). */
    private val PROVIDED = setOf("cheerio", "cheerio-without-node-native", "react-native-cheerio", "crypto-js", "axios")
    /** Node.js built-in modules: a scraper that needs one at run time can't work in a phone sandbox. */
    private val NODE_BUILTINS = setOf(
        "assert", "buffer", "child_process", "crypto", "dns", "events", "fs", "http", "http2", "https", "net", "os",
        "path", "process", "querystring", "stream", "string_decoder", "timers", "tls", "tty", "url", "util", "vm",
        "worker_threads", "zlib",
    )

    override fun required(p: PluginEntry): Set<String> = setOf(FILE)

    override suspend fun prepare(context: Context, plugin: EcosystemRegistry.Installed, dir: File): JsonObject {
        val needs = nodeNeeds(File(dir, FILE).readText())
        return buildJsonObject { if (needs.isNotEmpty()) put("needs", buildJsonArray { needs.forEach { add(JsonPrimitive(it)) } }) }
    }

    /** Stream-only: no catalog source is published. */
    override fun build(context: Context, plugin: EcosystemRegistry.Installed, dir: File, displayName: String): MainAPI? = null

    /**
     * Node.js built-ins the code asks `require` for (`require("zlib")`, `require('node:fs')`), sorted.
     * Plain text search, no regex. A bundle may only reach some of them on a path that never runs,
     * so they are reported, not refused.
     */
    fun nodeNeeds(code: String): List<String> {
        val out = sortedSetOf<String>()
        var i = code.indexOf("require(")
        while (i >= 0) {
            var j = i + 8
            while (j < code.length && code[j] == ' ') j++
            val q = code.getOrNull(j)
            if (q == '"' || q == '\'' || q == '`') {
                val end = code.indexOf(q, j + 1)
                if (end > j && end - j < 80) {
                    val name = code.substring(j + 1, end).removePrefix("node:").substringBefore('/')
                    if (name in NODE_BUILTINS && name !in PROVIDED) out += name
                }
            }
            i = code.indexOf("require(", i + 8)
        }
        return out.toList()
    }

    /** The problem line for the Script repos tab, or null. */
    /**
     * F-05-N-ERR: the line shown for a scraper that failed: its first non-blank, non-stack line
     * ("Broken: Error: site changed"), not the "at getStreams (scraper.js:2:67)" frame.
     */
    fun reasonOf(name: String, message: String): String {
        val lines = message.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val line = lines.firstOrNull { !it.startsWith("at ") } ?: lines.firstOrNull() ?: message
        return "$name: $line"
    }

    fun problemOf(p: EcosystemRegistry.Installed): String? {
        val needs = (p.extra["needs"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
        return if (needs.isEmpty()) null else "Uses Node.js parts Yumeiro can't run (${needs.joinToString()}); it may find nothing"
    }

    /** Media types the scraper says it handles ("movie", "tv"); empty means all. */
    fun types(p: EcosystemRegistry.Installed): List<String> =
        (p.extra["types"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.lowercase() }.orEmpty()

    fun handles(p: EcosystemRegistry.Installed, mediaType: String): Boolean {
        val t = types(p)
        return t.isEmpty() || mediaType in t || (mediaType == "tv" && ("series" in t || "show" in t))
    }

    /** `[tmdbId, mediaType, season, episode]` as Nuvio passes them (a movie gets nulls). */
    fun argsOf(r: StreamRequest): String = buildJsonArray {
        add(JsonPrimitive(r.tmdbId?.toString()))
        add(JsonPrimitive(r.mediaType))
        add(if (r.mediaType == "tv") JsonPrimitive(r.season) else JsonNull)
        add(if (r.mediaType == "tv") JsonPrimitive(r.episode) else JsonNull)
    }.toString()

    /**
     * A scraper's answer as link events. Dropped: no web link, DRM data (proposal F-05 §4: no
     * provider-supplied keys). Torrents become Skipped with their link (TorrServer / B-31 handle them).
     */
    fun eventsOf(pluginName: String, result: JsonElement): List<LinkEvent> {
        val list = when (result) {
            is JsonArray -> result
            is JsonObject -> result["streams"] as? JsonArray ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }
        val out = ArrayList<LinkEvent>()
        for (e in list) {
            val s = e as? JsonObject ?: continue
            val url = s.str("url")?.trim() ?: continue
            if (s["drm"].let { it != null && it !is JsonNull } || s["drmKey"] != null || s["licenseUrl"] != null) {
                out += LinkEvent.Skipped("DRM"); continue
            }
            if (url.startsWith("magnet:", true) || s.str("infoHash") != null) { out += LinkEvent.Skipped("Torrent", url); continue }
            if (!url.startsWith("https://", true) && !url.startsWith("http://", true)) continue
            val headers = (s["headers"] as? JsonObject)?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }?.toMap().orEmpty()
            val referer = headers.entries.firstOrNull { it.key.equals("Referer", true) }?.value
            val name = s.str("name") ?: pluginName
            val title = s.str("title")?.lineSequence()?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() && it != name }
            val type = s.str("type")?.lowercase()
            val path = url.substringBefore('?').substringBefore('#').lowercase()
            out += LinkEvent.Link(
                OnlineLink(
                    name = (if (title != null) "$name · $title" else name).take(120),
                    source = "Nuvio · $pluginName",
                    url = url,
                    referer = referer,
                    quality = VegaAdapter.qualityOf(s.str("quality"), name, title),
                    isHls = type == "hls" || path.endsWith(".m3u8") || path.contains(".m3u8"),
                    isDash = type == "dash" || path.endsWith(".mpd"),
                    headers = headers,
                ),
            )
            (s["subtitles"] as? JsonArray)?.forEach { st ->
                val o = st as? JsonObject ?: return@forEach
                val su = o.str("url") ?: return@forEach
                if (su.startsWith("http://", true) || su.startsWith("https://", true)) {
                    out += LinkEvent.Subtitle(OnlineSubtitle(o.str("lang") ?: o.str("language") ?: o.str("label") ?: "Unknown", su, headers))
                }
            }
        }
        return out
    }

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.takeIf { it.isNotBlank() }
}

/**
 * F-05-N (ADR-057): installed Nuvio scrapers as a [StreamAddon]. Each scraper runs in its own call
 * (ScriptRuntime's gate keeps 4 at once, 2 on low-end phones and TV); its links are sent as soon as
 * it answers. A scraper that fails or times out ends only itself.
 */
object NuvioStreams : StreamAddon {
    private const val TAG = "NuvioStreams"
    override val id = "nuvio"
    override val label = tr(R.string.nuvio_scrapers, "Nuvio scrapers")

    private fun ready(context: Context): List<EcosystemRegistry.Installed> =
        EcosystemRegistry.state.value.installed.filter { it.ecosystem == SourceHub.Ecosystem.NUVIO }

    override fun enabled(context: Context): Boolean =
        FeatureFlags.on(context, NuvioAdapter.flag) && ready(context).isNotEmpty()

    override fun streams(context: Context, request: StreamRequest): Flow<LinkEvent> {
        val app = context.applicationContext
        val plugins = ready(app).map { it to EcosystemRegistry.dirOf(app, it) }
        return streamsOf(app, plugins, request) { p -> EcosystemRegistry.intact(app, p) }
    }

    /**
     * Runs [plugins] (installed scraper and its folder) for [request]. Split from [streams] so the
     * device test runs a fixture scraper through the same path without touching the registry.
     */
    fun streamsOf(
        context: Context,
        plugins: List<Pair<EcosystemRegistry.Installed, File>>,
        request: StreamRequest,
        intact: (EcosystemRegistry.Installed) -> Boolean = { true },
    ): Flow<LinkEvent> = channelFlow {
        val app = context.applicationContext
        // Nuvio scrapers look titles up by TMDB id; without one there is nothing to ask.
        if (request.tmdbId == null) return@channelFlow
        val asked = plugins.filter { NuvioAdapter.handles(it.first, request.mediaType) }
        val any = java.util.concurrent.atomic.AtomicBoolean(false)
        val jobs = asked.map { (p, dir) ->
            launch {
                try {
                    check(intact(p)) { "code changed on disk" }
                    val result = ScriptRuntime.call(
                        app, p.storeName, listOf(ScriptRuntime.Module(NuvioAdapter.MODULE, File(dir, NuvioAdapter.FILE))),
                        NuvioAdapter.MODULE, "getStreams", NuvioAdapter.argsOf(request),
                        invoker = "__invokePositional",
                        globals = mapOf(
                            "SCRAPER_ID" to JsonPrimitive(p.id).toString(),
                            "SCRAPER_SETTINGS" to "{}",
                        ),
                    )
                    for (ev in NuvioAdapter.eventsOf(p.name, result)) { if (ev is LinkEvent.Link) any.set(true); send(ev) }
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    // The call budget ran out for this scraper only; the others go on.
                    send(LinkEvent.Skipped("${p.name}: ${ScriptRuntime.STOPPED}"))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Log.w(TAG, "${p.name}: ${e.message}")
                    send(LinkEvent.Skipped(NuvioAdapter.reasonOf(p.name, e.message ?: e.javaClass.simpleName)))
                }
            }
        }
        jobs.forEach { it.join() }
        if (!any.get() && asked.isNotEmpty()) send(LinkEvent.Done(false, NO_STREAMS))
    }.flowOn(Dispatchers.IO)

    val NO_STREAMS: String get() = tr(R.string.no_scrapers_returned_a_stream, "No scrapers returned a stream")
}
