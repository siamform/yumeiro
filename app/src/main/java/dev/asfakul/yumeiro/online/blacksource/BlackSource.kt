/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.blacksource

import android.content.Context
import com.lagradost.cloudstream3.MainAPI
import dev.asfakul.yumeiro.BuildConfig
import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.online.OnlineRepository
import dev.asfakul.yumeiro.online.SearchRanker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * F14 BlackSource (Group U, app-docs/BLACKSOURCE.md): advanced search by genre, tags and demographic.
 *  1. Yumeiro Cloud (`/v1/blacksource/…`, the owner's Cloudflare Worker) asks AniList with one fixed query and
 *     returns checked titles with their genres, tags and demographic. No third-party code runs there.
 *  2. The phone then looks for those titles in the user's own sources (their extensions, which already run here),
 *     and, when words were typed, keeps source results that AniList does not know by the source's own genres.
 * Opt-in: step 2 asks every source several times, so the first use explains the extra time.
 */
object BlackSource {
    private const val PREFS = "blacksource"
    private const val KEY_ON = "opted_in"

    @Volatile internal var baseForTest: String? = null
    private val base: String get() = baseForTest ?: "https://${BuildConfig.CLOUD_HOST}/v1/blacksource"

    fun optedIn(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ON, false)
    fun setOptedIn(c: Context, on: Boolean) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ON, on).apply()

    /** AniList genres and demographics (the Worker checks the same lists). */
    val GENRES = listOf(
        "Action", "Adventure", "Comedy", "Drama", "Ecchi", "Fantasy", "Horror", "Mahou Shoujo", "Mecha", "Music",
        "Mystery", "Psychological", "Romance", "Sci-Fi", "Slice of Life", "Sports", "Supernatural", "Thriller",
    )
    val DEMOGRAPHICS = listOf("Shounen", "Shoujo", "Seinen", "Josei", "Kids")

    data class Filter(
        val words: String = "",
        val genres: Set<String> = emptySet(),
        val tags: List<String> = emptyList(),
        val demographic: String? = null,
        val sort: String = "popular",
    ) {
        val empty get() = words.isBlank() && genres.isEmpty() && tags.isEmpty() && demographic == null
    }

    data class Title(
        val id: Int, val title: String, val english: String?, val native: String?, val synonyms: List<String>,
        val year: Int?, val episodes: Int?, val format: String?, val cover: String?,
        val genres: List<String>, val tags: List<String>, val demographic: String?,
    ) {
        val names: List<String> get() = listOfNotNull(title, english, native) + synonyms
    }

    data class Page(val items: List<Title>, val more: Boolean)

    class Failure(message: String) : Exception(message)

    /** Pure → unit tested. */
    fun searchUrl(f: Filter, page: Int = 1): String {
        val enc = { v: String -> URLEncoder.encode(v, "UTF-8") }
        val q = buildList {
            f.words.trim().takeIf { it.isNotEmpty() }?.let { add("q=" + enc(it.take(80))) }
            if (f.genres.isNotEmpty()) add("genres=" + enc(f.genres.sorted().joinToString(",")))
            val tags = f.tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(5)
            if (tags.isNotEmpty()) add("tags=" + enc(tags.joinToString(",")))
            f.demographic?.let { add("demographic=" + enc(it)) }
            add("sort=" + enc(f.sort))
            if (page > 1) add("page=$page")
        }
        return "$base/search?" + q.joinToString("&")
    }

    /** Pure → unit tested. The Worker's JSON; anything malformed is skipped, not trusted. */
    fun parse(json: String): Page {
        val o = JSONObject(json)
        val arr = o.optJSONArray("items") ?: JSONArray()
        val items = (0 until arr.length()).mapNotNull { i ->
            val r = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = r.optInt("id", 0).takeIf { it > 0 } ?: return@mapNotNull null
            val name = r.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            fun strs(k: String) = r.optJSONArray(k)?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotBlank() } } }.orEmpty()
            fun opt(k: String) = r.optString(k).takeIf { r.has(k) && it.isNotBlank() }
            Title(
                id = id, title = name, english = opt("english"), native = opt("native"), synonyms = strs("synonyms"),
                year = r.optInt("year", 0).takeIf { it > 0 }, episodes = r.optInt("episodes", 0).takeIf { it > 0 },
                format = opt("format"), cover = opt("cover")?.takeIf { it.startsWith("https://") },
                genres = strs("genres"), tags = strs("tags"), demographic = opt("demographic"),
            )
        }
        return Page(items, o.optBoolean("more", false))
    }

    suspend fun search(f: Filter, page: Int = 1): Page = withContext(Dispatchers.IO) {
        val conn = (dev.asfakul.yumeiro.network.AppProxy.open(URL(searchUrl(f, page))) as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            if (code == 429 || code == 503) throw Failure("busy")
            if (code !in 200..299) throw Failure("http $code")
            parse(conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) })
        } finally { conn.disconnect() }
    }

    /** Pure → unit tested: a source's result is this title when a normalized name matches (also "Name (Dub)", "Name Season 2"). */
    fun matches(itemName: String, t: Title): Boolean {
        val n = SearchRanker.normalize(itemName)
        if (n.isEmpty()) return false
        return t.names.any { c ->
            val m = SearchRanker.normalize(c)
            m.length >= 3 && (n == m || n.startsWith("$m ") || (m.length >= 8 && m.startsWith("$n ")))
        }
    }

    /** Pure → unit tested: does a source's own genre list satisfy the filter (genres, tags and demographic, any case)? */
    fun providerTagsMatch(tags: List<String>, f: Filter): Boolean {
        val have = tags.map { SearchRanker.normalize(it) }.toSet()
        val want = f.genres + f.tags + listOfNotNull(f.demographic)
        return want.isNotEmpty() && want.all { SearchRanker.normalize(it) in have }
    }

    data class SourceHit(val title: Title?, val item: OnlineItem, val providerTags: List<String> = emptyList())

    /**
     * Step 2. Looks up the first [limit] titles in the sources (3 at a time) and reports each title's matches as they
     * arrive. With words typed, source results AniList did not return are opened (up to [detailLimit]) and kept when
     * the source's own genres fit the filter.
     */
    suspend fun findInSources(
        titles: List<Title>, f: Filter, apis: List<MainAPI>, limit: Int = 12, detailLimit: Int = 8,
        onHit: (SourceHit) -> Unit, onProgress: (done: Int, total: Int) -> Unit,
    ) = coroutineScope {
        if (apis.isEmpty()) { onProgress(0, 0); return@coroutineScope }
        val pick = titles.take(limit)
        val words = f.words.trim()
        val total = pick.size + if (words.isNotEmpty()) 1 else 0
        var done = 0
        val gate = Semaphore(3)
        val lock = Any()
        fun step() = synchronized(lock) { done++; onProgress(done, total) }
        onProgress(0, total)
        val jobs = pick.map { t ->
            async {
                gate.withPermit {
                    val items = withTimeoutOrNull(40_000) { OnlineRepository.search(t.title, apis).toList().flatMap { it.items } }.orEmpty()
                    items.filter { matches(it.name, t) }.distinctBy { it.key }.forEach { onHit(SourceHit(t, it)) }
                }
                step()
            }
        }
        val extra = if (words.isEmpty()) null else async {
            gate.withPermit {
                val items = withTimeoutOrNull(40_000) { OnlineRepository.search(words, apis).toList().flatMap { it.items } }.orEmpty()
                val unknown = items.filter { i -> titles.none { matches(i.name, it) } }.distinctBy { it.key }.take(detailLimit)
                val byName = apis.associateBy { it.name }
                unknown.forEach { i ->
                    val api = byName[i.api] ?: return@forEach
                    val d = withTimeoutOrNull(12_000) { runCatching { OnlineRepository.load(api, i.url) }.getOrNull() } ?: return@forEach
                    if (providerTagsMatch(d.tags, f)) onHit(SourceHit(null, i, d.tags))
                }
            }
            step()
        }
        (jobs + listOfNotNull(extra)).awaitAll()
    }
}
