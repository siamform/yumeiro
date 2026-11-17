/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Round 13: episode thumbnails for series whose extension doesn't send any.
 *
 * Looked up once per title, in the background, from free public catalogues that need no key:
 * ani.zip (anime, by AniList / MyAnimeList id) and TVmaze (series, by IMDb id or by name). When
 * nothing is found the rows fall back to the series artwork with the episode number on top, so an
 * episode never shows an empty box.
 */
object EpisodeStills {
    private const val TAG = "EpisodeStills"
    /** Title key → ("season:episode" or ":absolute") → image URL. */
    private val _stills = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())
    val stills: StateFlow<Map<String, Map<String, String>>> = _stills.asStateFlow()
    private val tried = ConcurrentHashMap.newKeySet<String>()

    fun keyOf(d: OnlineDetail) = d.api + "|" + d.url

    /** Thumbnail for [e] of [d], from the extension first, then the catalogues. */
    fun imageFor(d: OnlineDetail, e: OnlineEpisode, found: Map<String, String>?): String? =
        e.posterUrl ?: found?.let { m -> m["${e.season ?: 1}:${e.episode}"] ?: m[":${e.episode}"] }

    /** True when most episodes have no picture from the extension (worth a lookup). */
    fun needsLookup(d: OnlineDetail): Boolean {
        val all = d.episodes.values.flatten()
        return all.isNotEmpty() && all.count { it.posterUrl == null } * 2 > all.size
    }

    /** Lookups outlive the screen that asked (see IG-5). */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Starts the lookup for [d] once. IG-5: it used to run in the caller's composition scope after
     * marking the title as tried, so leaving the screen mid-lookup (the result was dropped on
     * cancellation) or one offline attempt left the title without thumbnails until the app restarted.
     * The lookup now runs in its own scope and always stores its result; a failed lookup (network
     * error) clears the mark so the next visit tries again. "Nothing found" stays marked.
     */
    fun ensure(context: Context, d: OnlineDetail) {
        val key = keyOf(d)
        if (!needsLookup(d) || !tried.add(key)) return
        val app = context.applicationContext
        scope.launch {
            runCatching { lookup(app, d) }
                .onSuccess { found -> if (found.isNotEmpty()) _stills.update { it + (key to found) } }
                .onFailure { Log.i(TAG, "lookup ${d.name}: ${it.message}"); tried.remove(key) }
        }
    }

    private fun lookup(context: Context, d: OnlineDetail): Map<String, String> {
        val client = dev.asfakul.yumeiro.network.NetworkClient.get(context)
        fun get(url: String): String? = client.newCall(Request.Builder().url(url).header("Accept", "application/json").build()).execute().use { r ->
            if (r.isSuccessful) r.body?.string() else null
        }
        if (d.kind == OnlineKind.ANIME || d.aniListId != null || d.malId != null) {
            val q = d.aniListId?.let { "anilist_id=$it" } ?: d.malId?.let { "mal_id=$it" }
            if (q != null) get("https://api.ani.zip/mappings?$q")?.let { parseAniZip(it) }?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        val showId = d.imdbId?.takeIf { it.startsWith("tt") }?.let { id ->
            get("https://api.tvmaze.com/lookup/shows?imdb=$id")?.let { JSONObject(it).optInt("id").takeIf { n -> n > 0 } }
        } ?: if (d.kind != OnlineKind.ANIME) get("https://api.tvmaze.com/singlesearch/shows?q=" + android.net.Uri.encode(d.name))?.let { body ->
            val o = JSONObject(body)
            val premiered = o.optString("premiered").take(4).toIntOrNull()
            o.optInt("id").takeIf { it > 0 && (d.year == null || premiered == null || kotlin.math.abs(premiered - d.year) <= 1) }
        } else null
        if (showId == null) return emptyMap()
        return get("https://api.tvmaze.com/shows/$showId/episodes")?.let { parseTvMaze(it) }.orEmpty()
    }

    /** ani.zip `episodes` object → keys ":absolute" and "season:episode". Pure → unit-tested. */
    internal fun parseAniZip(body: String): Map<String, String> {
        val eps = JSONObject(body).optJSONObject("episodes") ?: return emptyMap()
        val out = HashMap<String, String>()
        eps.keys().forEach { k ->
            val e = eps.optJSONObject(k) ?: return@forEach
            val img = e.optString("image").takeIf { it.startsWith("http") } ?: return@forEach
            k.toIntOrNull()?.let { out[":$it"] = img }
            val s = e.optInt("seasonNumber", -1); val n = e.optInt("episodeNumber", -1)
            if (s > 0 && n > 0) out.putIfAbsent("$s:$n", img)
        }
        return out
    }

    /** TVmaze episode list → "season:episode" keys. Pure → unit-tested. */
    internal fun parseTvMaze(body: String): Map<String, String> {
        val arr = JSONArray(body)
        val out = HashMap<String, String>()
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            val img = e.optJSONObject("image")?.let { it.optString("medium").ifBlank { it.optString("original") } }?.takeIf { it.startsWith("http") } ?: continue
            val s = e.optInt("season", -1); val n = e.optInt("number", -1)
            if (s > 0 && n > 0) out["$s:$n"] = img.replace("http://", "https://")
        }
        return out
    }
}
