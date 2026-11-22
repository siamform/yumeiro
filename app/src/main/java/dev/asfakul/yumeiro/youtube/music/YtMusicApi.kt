/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.youtube.music

import android.content.Context
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.youtube.YtAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * YouTube Music's catalogue, read from the same web API music.youtube.com uses (no key, no
 * login). Home with its mood chips and endless shelves, Explore (new releases, charts, moods and
 * genres), album, playlist and artist pages, and search by type.
 *
 * Anonymous by default. When the user saved a YouTube account and chose "Always" in Settings,
 * the account headers are added so Home is their own (see [YtAccount]). Results are cached for a
 * few minutes so moving between pages is instant.
 */
object YtMusicApi {
    private const val BASE = "https://music.youtube.com/youtubei/v1/"
    private const val CLIENT_NAME = "WEB_REMIX"
    private const val CLIENT_VERSION = "1.20251101.01.00"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Safari/537.36"
    private const val TTL = 10 * 60_000L

    const val HOME = "FEmusic_home"
    const val EXPLORE = "FEmusic_explore"
    const val CHARTS = "FEmusic_charts"
    const val MOODS = "FEmusic_moods_and_genres"
    const val NEW_RELEASES = "FEmusic_new_releases_albums"

    /** Search result types, with YouTube Music's own filter parameters. */
    enum class Filter(val label: String, val params: String?) {
        ALL("All", null),
        SONGS("Songs", "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"),
        VIDEOS("Videos", "EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D"),
        ALBUMS("Albums", "EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D"),
        ARTISTS("Artists", "EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D"),
        PLAYLISTS("Playlists", "EgeKAQQoAEABagoQAxAEEAoQCRAF"),
    }

    /**
     * YouTube Music ties "load more" tokens to the anonymous visitor id it handed out with the
     * first page; without it continuations come back empty. Kept in memory only, never stored.
     */
    @Volatile private var visitor: String? = null
    private val visitorRx = Regex("\"visitorData\":\"([^\"]+)\"")

    private data class Cached(val at: Long, val page: YmPage)
    private val cache = ConcurrentHashMap<String, Cached>()

    suspend fun browse(context: Context, target: YmBrowse, force: Boolean = false): YmPage =
        cached("b:${target.browseId}:${target.params}", force) {
            post(context, "browse", body {
                put("browseId", target.browseId)
                target.params?.let { put("params", it) }
            }, saveAs = savedKey(target))
        }

    /**
     * FE-7b (docs/FEED-ENGINE-2.1.md): Home / Explore (and their chips) as last answered, saved on the phone and
     * keyed by the account state, so a cold start shows them at once while the live page loads. Before, pages
     * lived only in memory and every start waited for YouTube Music. A saved page from before the last account
     * change is never shown ([invalidate]). Disk read and parse on Dispatchers.IO.
     */
    suspend fun savedBrowse(target: YmBrowse): YmPage? = withContext(Dispatchers.IO) {
        val key = savedKey(target) ?: return@withContext null
        val age = dev.asfakul.yumeiro.cache.DataCache.ageOf(SAVED_NS, key) ?: return@withContext null
        if (System.currentTimeMillis() - age <= savedAfter) return@withContext null
        val text = dev.asfakul.yumeiro.cache.DataCache.read(SAVED_NS, key, maxAgeMs = dev.asfakul.yumeiro.cache.DataCache.Settings.keepDays * 86_400_000L)
            ?: return@withContext null
        runCatching { YmParser.parse(text) }.getOrNull()?.takeIf { it.shelves.isNotEmpty() }
    }

    internal fun savedKey(target: YmBrowse): String? {
        if (target.browseId != HOME && target.browseId != EXPLORE) return null
        val s = YtAccount.state.value
        return "${target.browseId}|${target.params}|${s.signedIn}:${s.mode.name}"
    }

    /** Test hook: what [post] saves for a live Home answer. */
    @androidx.annotation.VisibleForTesting
    internal fun saveForTests(target: YmBrowse, text: String) { savedKey(target)?.let { dev.asfakul.yumeiro.cache.DataCache.write(SAVED_NS, it, text) } }

    @Volatile private var savedAfter = 0L
    private const val SAVED_NS = "ymhome"

    suspend fun home(context: Context, chip: YmChip? = null, force: Boolean = false): YmPage =
        browse(context, chip?.browse ?: YmBrowse(HOME), force)

    /** The next shelves (Home) or rows (playlist, search) after [token]. */
    suspend fun more(context: Context, endpoint: String = "browse", token: String): YmPage =
        cached("c:$endpoint:$token", false) {
            post(context, endpoint, body { put("continuation", java.net.URLDecoder.decode(token, "UTF-8")) })
        }

    suspend fun search(context: Context, query: String, filter: Filter): YmPage =
        cached("s:${filter.name}:$query", false) {
            post(context, "search", body {
                put("query", query)
                filter.params?.let { put("params", java.net.URLDecoder.decode(it, "UTF-8")) }
            })
        }

    /**
     * Forgets every cached page and the visitor id. G13 M2-1: called whenever the YouTube account
     * changes (sign in, sign out, mode), so a signed-out phone never keeps showing the previous
     * account's own Home for the rest of the cache time.
     */
    fun invalidate() {
        cache.clear(); visitor = null
        // FE-7b: saved Home pages of the previous account state are never shown again.
        savedAfter = System.currentTimeMillis()
        runCatching { dev.asfakul.yumeiro.cache.DataCache.dropNamespace(SAVED_NS) }
    }

    @androidx.annotation.VisibleForTesting
    internal fun cachedCount(): Int = cache.size

    @androidx.annotation.VisibleForTesting
    internal fun putForTests(key: String, page: YmPage, visitorId: String? = null) {
        cache[key] = Cached(System.currentTimeMillis(), page)
        if (visitorId != null) visitor = visitorId
    }

    @androidx.annotation.VisibleForTesting
    internal fun visitorForTests(): String? = visitor

    // ---- transport ---------------------------------------------------------------------------

    // B-28 (GATE-1 real clock, run 44793350371, api33-lowram): the request body was built on the
    // caller's thread (Main from YtMusicBrowse); the first JSON build loads kotlinx.serialization's
    // JsonElement classes, and the main thread waited 865 ms when Videos opened. The whole load,
    // body included, now runs on IO; only the memory cache lookup stays on the caller's thread.
    @androidx.annotation.VisibleForTesting
    internal suspend fun cached(key: String, force: Boolean, load: suspend () -> YmPage): YmPage {
        val now = System.currentTimeMillis()
        if (!force) cache[key]?.takeIf { now - it.at < TTL }?.let { return it.page }
        val page = withContext(Dispatchers.IO) { load() }
        cache[key] = Cached(now, page)
        return page
    }

    private inline fun body(extra: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) = buildJsonObject {
        putJsonObject("context") {
            putJsonObject("client") {
                put("clientName", CLIENT_NAME)
                put("clientVersion", CLIENT_VERSION)
                put("hl", Locale.getDefault().language.ifBlank { "en" })
                put("gl", Locale.getDefault().country.takeIf { it.length == 2 } ?: "US")
            }
        }
        extra()
    }

    private suspend fun post(context: Context, endpoint: String, body: kotlinx.serialization.json.JsonObject, saveAs: String? = null): YmPage =
        withContext(Dispatchers.IO) {
            val url = "$BASE$endpoint?prettyPrint=false"
            val req = Request.Builder().url(url)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .header("User-Agent", UA)
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
                .header("X-YouTube-Client-Name", "67")
                .header("X-YouTube-Client-Version", CLIENT_VERSION)
                .apply { visitor?.let { header("X-Goog-Visitor-Id", it) } }
                .apply { YtAccount.headersFor(url).forEach { (k, v) -> header(k, v) } }
                .build()
            NetworkClient.get(context).newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw IOException("YouTube Music answered ${r.code}")
                val text = r.body.string()
                if (!text.trimStart().startsWith("{")) throw IOException("YouTube Music is busy right now")
                if (visitor == null) visitor = visitorRx.find(text.take(4000))?.groupValues?.get(1)
                YmParser.parse(text).also { p ->
                    if (saveAs != null && p.shelves.isNotEmpty()) dev.asfakul.yumeiro.cache.DataCache.write(SAVED_NS, saveAs, text)
                }
            }
        }
}
