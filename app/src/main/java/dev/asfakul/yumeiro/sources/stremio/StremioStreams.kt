/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.stremio

import dev.asfakul.yumeiro.l10n.tr
import dev.asfakul.yumeiro.R
import android.content.Context
import android.util.Log
import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.online.LinkEvent
import dev.asfakul.yumeiro.online.OnlineLink
import dev.asfakul.yumeiro.online.OnlineSubtitle
import dev.asfakul.yumeiro.online.StreamAddon
import dev.asfakul.yumeiro.online.StreamRequest
import dev.asfakul.yumeiro.sources.ecosystem.VegaAdapter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.net.URLEncoder

/**
 * F-05-STREMIO: Stremio add-ons that only answer `stream` (no catalog: Torrentio-style, debrid or
 * direct-link add-ons) give links to every title that has an IMDb id, from any source, through the
 * F-05-N [StreamAddon] seam. Before this they were only asked when a title from a Stremio catalog
 * was played, so a user who added one and watched a CloudStream title got nothing from it.
 *
 * Protocol (public, Stremio add-on SDK docs): `GET <base>/stream/<type>/<id>.json`; a movie's id is
 * the IMDb id, an episode's is `tt…:<season>:<episode>`. Answer `{streams: [{url | infoHash | ytId,
 * name, title / description, subtitles, behaviorHints.proxyHeaders.request}]}`. Written from the
 * docs; no Stremio code is used. Links are de-duplicated by URL where they are collected
 * (OnlineSession), so a Stremio catalog title that also asks these add-ons shows each link once.
 */
object StremioStreams : StreamAddon {
    private const val TAG = "StremioStreams"
    const val TIMEOUT_MS = 20_000L
    override val id = "stremio"
    override val label = tr(R.string.stremio_add_ons, "Stremio add-ons")

    /** The add-ons that have no catalog but answer streams. */
    fun streamOnly(all: List<StremioAddons.Addon>): List<StremioAddons.Addon> =
        all.filter { a -> a.resources.any { it.name == "stream" } && (a.catalogs.isEmpty() || a.resources.none { it.name == "catalog" }) }

    override fun enabled(context: Context): Boolean =
        FeatureFlags.on(context, FeatureFlags.Flag.F05_STREMIO_STREAMS) && streamOnly(StremioAddons.addons.value).isNotEmpty()

    /** Stremio's type and video id for [r], or null without an IMDb id (what these add-ons look up). */
    fun idFor(r: StreamRequest): Pair<String, String>? {
        val imdb = r.imdbId?.trim()?.takeIf { it.startsWith("tt") && it.length > 2 } ?: return null
        return if (r.mediaType == "tv") "series" to "$imdb:${r.season ?: 1}:${r.episode ?: 1}" else "movie" to imdb
    }

    fun urlFor(a: StremioAddons.Addon, type: String, id: String): String =
        "${a.base}/stream/$type/${URLEncoder.encode(id, "UTF-8").replace("+", "%20")}.json"

    override fun streams(context: Context, request: StreamRequest): Flow<LinkEvent> {
        val app = context.applicationContext
        return streamsOf(streamOnly(StremioAddons.addons.value), request) { url -> StremioAddons.getJson(app, url).jsonObject }
    }

    /**
     * Asks every add-on in [addons] that serves this type and id, all at once; each sends its links as
     * soon as it answers. One that fails or takes longer than [TIMEOUT_MS] ends only itself. [fetch]
     * is the HTTP call (tests pass a fake).
     */
    fun streamsOf(addons: List<StremioAddons.Addon>, request: StreamRequest, fetch: suspend (String) -> JsonObject): Flow<LinkEvent> = channelFlow {
        val (type, vid) = idFor(request) ?: return@channelFlow
        val asked = addons.filter { it.supports("stream", type, vid) }
        if (asked.isEmpty()) return@channelFlow
        val any = java.util.concurrent.atomic.AtomicBoolean(false)
        asked.map { a ->
            launch {
                try {
                    val res = withTimeoutOrNull(TIMEOUT_MS) { fetch(urlFor(a, type, vid)) }
                    if (res == null) { send(LinkEvent.Skipped("${a.name}: no answer in ${TIMEOUT_MS / 1000} s")); return@launch }
                    for (ev in eventsOf(a.name, res)) { if (ev is LinkEvent.Link) any.set(true); send(ev) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Log.w(TAG, "${a.name}: ${e.message}")
                    send(LinkEvent.Skipped("${a.name}: ${e.message ?: e.javaClass.simpleName}"))
                }
            }
        }.forEach { it.join() }
        if (!any.get()) send(LinkEvent.Done(false, NO_STREAMS))
    }.flowOn(Dispatchers.IO)

    val NO_STREAMS: String get() = tr(R.string.no_stremio_add_on_returned_a, "No Stremio add-on returned a stream")

    /**
     * One add-on's answer as link events: web links become links (source "Stremio · <add-on>"),
     * torrents become Skipped with their magnet link (played through TorrServer like other torrents),
     * YouTube ids and browser-only links are skipped.
     */
    fun eventsOf(addonName: String, res: JsonObject): List<LinkEvent> {
        val out = ArrayList<LinkEvent>()
        for (e in (res["streams"] as? JsonArray).orEmpty()) {
            val s = e as? JsonObject ?: continue
            val name = s.str("name")?.replace('\n', ' ')
            val desc = (s.str("description") ?: s.str("title"))?.lineSequence()?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            val url = s.str("url")?.trim()
            if (url == null) {
                val hash = s.str("infoHash")
                if (hash != null) {
                    val trackers = (s["sources"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                        .filter { it.startsWith("tracker:") }.map { it.removePrefix("tracker:") }
                    val magnet = dev.asfakul.yumeiro.stream.LinkHosts.magnetFromHash(hash, desc, trackers, (s["fileIdx"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull())
                    out += LinkEvent.Skipped("Torrent", magnet)
                }
                continue
            }
            if (!url.startsWith("https://", true) && !url.startsWith("http://", true)) continue
            val headers = ((s["behaviorHints"] as? JsonObject)?.get("proxyHeaders") as? JsonObject)?.get("request")
                ?.let { h -> (h as? JsonObject)?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }?.toMap() }.orEmpty()
            val path = url.substringBefore('?').substringBefore('#').lowercase()
            val label = listOfNotNull(name, desc).joinToString(" · ").ifBlank { addonName }
            out += LinkEvent.Link(
                OnlineLink(
                    name = label.take(120),
                    source = "Stremio · $addonName",
                    url = url,
                    referer = headers.entries.firstOrNull { it.key.equals("Referer", true) }?.value,
                    quality = VegaAdapter.qualityOf(name, desc),
                    isHls = path.endsWith(".m3u8") || path.contains(".m3u8"),
                    isDash = path.endsWith(".mpd"),
                    headers = headers,
                ),
            )
            (s["subtitles"] as? JsonArray)?.forEach { st ->
                val o = st as? JsonObject ?: return@forEach
                val su = o.str("url") ?: return@forEach
                if (su.startsWith("http://", true) || su.startsWith("https://", true)) out += LinkEvent.Subtitle(OnlineSubtitle(o.str("lang") ?: "Unknown", su, headers))
            }
        }
        return out
    }

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.takeIf { it.isNotBlank() }
}
