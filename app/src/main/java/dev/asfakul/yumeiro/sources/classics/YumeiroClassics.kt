/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.classics

import android.content.Context
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSearchResponseList
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.sources.SourceHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * OWN-9 (owner 2026-11-22): an official Yumeiro source that works on a fresh install with nothing to add.
 *
 * "Yumeiro Classics": public-domain and freely licensed films, cartoons and TV from the Internet Archive's own
 * public APIs (advancedsearch + metadata; https://archive.org/developers/). Every file is one the Archive hosts
 * for free playback and download; nothing is bypassed. 18+ is dropped twice: in the query and again on the
 * title/subject words. Built in and on by default (like Yumeiro Drama); one tap turns it off.
 */
object YumeiroClassics {
    const val NAME = "Yumeiro Classics"
    private const val PREFS = "yumeiro_classics"
    private const val BRIDGE = "classics"

    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun start(context: Context) {
        if (!FeatureFlags.on(context, FeatureFlags.Flag.OFFICIAL_CLASSICS)) {
            SourceHub.remove(BRIDGE)
            if (dev.asfakul.yumeiro.sources.hub.YumeiroHub.on()) dev.asfakul.yumeiro.sources.hub.YumeiroHub.setClassics(null)
            return
        }
        val on = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("enabled", true)
        _enabled.value = on
        publish(on)
    }

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("enabled", on).apply()
        _enabled.value = on
        publish(on)
    }

    private fun publish(on: Boolean) {
        // OWN-13: one built-in source, "Yumeiro", holds this part.
        if (dev.asfakul.yumeiro.sources.hub.YumeiroHub.on()) {
            SourceHub.remove(BRIDGE)
            dev.asfakul.yumeiro.sources.hub.YumeiroHub.setClassics(if (on) YumeiroClassicsProvider() else null)
            return
        }
        if (on) SourceHub.publishVideo(BRIDGE, SourceHub.Ecosystem.YUMEIRO, listOf(YumeiroClassicsProvider()))
        else SourceHub.remove(BRIDGE)
    }
}

class YumeiroClassicsProvider(
    private val fetch: suspend (String) -> String? = ::httpGet,
) : MainAPI() {
    override var name = YumeiroClassics.NAME
    override var mainUrl = "https://archive.org"
    override var lang = "en"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.Cartoon, TvType.TvSeries, TvType.Documentary)

    init { sourcePlugin = "yumeiro-builtin:classics" }

    override val mainPage: List<MainPageData> = SHELVES.map { (title, collection) -> MainPageData(title, collection) }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // OWN-13: a shelf is a collection id, or a whole query (films by language, documentaries).
        val docs = query(if (':' in request.data) request.data else "collection:${request.data}", page)
        return newHomePageResponse(request.name, docs.map { it.toResult() }, docs.size >= ROWS)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val words = cleanQuery(query)
        if (words.isBlank()) return newSearchResponseList(emptyList(), false)
        val docs = query("title:($words) AND collection:(${SEARCH_COLLECTIONS.joinToString(" OR ")})", page)
        return newSearchResponseList(docs.map { it.toResult() }, docs.size >= ROWS)
    }

    override suspend fun search(query: String): List<SearchResponse> = search(query, 1).items

    override suspend fun load(url: String): LoadResponse {
        val id = idOf(url) ?: error("Not a Yumeiro Classics title")
        val meta = fetch("$mainUrl/metadata/$id")?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?.optJSONObject("metadata") ?: error("The Internet Archive didn't answer for $id")
        val title = meta.text("title") ?: id
        if (adult(title, meta.text("subject"))) error("This title isn't available in Yumeiro Classics")
        return newMovieLoadResponse(title, url, TvType.Movie, "classics:$id") {
            posterUrl = "$mainUrl/services/img/$id"
            plot = meta.text("description")?.let { stripHtml(it) }?.take(1_500)
            year = (meta.text("year") ?: meta.text("date"))?.take(4)?.toIntOrNull()
            tags = meta.text("subject")?.split(';', ',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.take(6)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val id = idOf(data) ?: return false
        val files = fetch("$mainUrl/metadata/$id")?.let { runCatching { JSONObject(it) }.getOrNull() }?.optJSONArray("files") ?: return false
        val picked = playable(files)
        picked.forEach { f ->
            callback(newExtractorLink(name, "${name} · ${f.label}", "$mainUrl/download/$id/${enc(f.name)}", ExtractorLinkType.VIDEO) {
                quality = f.height
            })
        }
        subtitles(files).forEach { (label, file) -> subtitleCallback(SubtitleFile(label, "$mainUrl/download/$id/${enc(file)}")) }
        return picked.isNotEmpty()
    }

    private data class Doc(val id: String, val title: String, val year: Int?)

    private fun Doc.toResult(): SearchResponse = newMovieSearchResponse(title, "classics:$id", TvType.Movie, fix = false) {
        posterUrl = "$mainUrl/services/img/$id"
        year = this@toResult.year
    }

    private suspend fun query(q: String, page: Int): List<Doc> {
        val full = "($q) AND mediatype:movies AND NOT title:($ADULT_Q) AND NOT subject:($ADULT_Q)"
        val url = "$mainUrl/advancedsearch.php?q=${enc(full)}&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=year&fl%5B%5D=subject" +
            "&sort%5B%5D=downloads+desc&rows=$ROWS&page=${page.coerceAtLeast(1)}&output=json"
        val docs = fetch(url)?.let { runCatching { JSONObject(it).optJSONObject("response")?.optJSONArray("docs") }.getOrNull() } ?: return emptyList()
        return (0 until docs.length()).mapNotNull { i ->
            val o = docs.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("identifier").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val title = o.optString("title").ifBlank { id }
            if (adult(title, o.text("subject"))) return@mapNotNull null
            Doc(id, title, o.optString("year").take(4).toIntOrNull())
        }
    }

    internal data class Pick(val name: String, val label: String, val height: Int)

    companion object {
        const val ROWS = 40
        /** Home shelves: the Archive's curated public collections. */
        val SHELVES = listOf(
            "Classic feature films" to "feature_films",
            "Film noir" to "Film_Noir",
            "Sci-fi and horror" to "SciFi_Horror",
            "Comedy" to "Comedy_Films",
            "Silent films" to "silent_films",
            "Cartoons and animation" to "animationandcartoons",
            "Classic TV" to "classic_tv",
            // OWN-13: more legal shelves, checked on archive.org 2026-11-22 (counts in docs/OPEN-ITEMS-2.1.0.md).
            "Documentaries and newsreels" to "collection:(prelinger)",
            "Classic cartoons" to "classic_cartoons",
            "Indian classics (Hindi)" to "collection:(feature_films) AND language:(Hindi)",
            "French classics" to "collection:(feature_films) AND language:(French)",
            "German classics" to "collection:(feature_films) AND language:(German)",
            "Italian classics" to "collection:(feature_films) AND language:(Italian)",
            "Russian classics" to "collection:(feature_films) AND language:(Russian)",
            "Japanese classics" to "collection:(feature_films) AND language:(Japanese)",
            "Spanish-language classics" to "collection:(feature_films) AND language:(Spanish)",
            "Chinese classics" to "collection:(feature_films) AND language:(Chinese)",
        )
        /** Collections search looks in (every shelf's collection). */
        val SEARCH_COLLECTIONS = listOf("feature_films", "Film_Noir", "SciFi_Horror", "Comedy_Films", "silent_films", "animationandcartoons", "classic_tv", "prelinger", "classic_cartoons")
        private val ADULT_WORDS = listOf("sex", "sexy", "nude", "nudity", "nudist", "erotic", "erotica", "adult", "porn", "xxx", "stag", "burlesque", "exploitation", "striptease")
        private val ADULT_Q = ADULT_WORDS.joinToString(" OR ")
        private val RX_ADULT = Regex("""\b(${ADULT_WORDS.joinToString("|")})\b""", RegexOption.IGNORE_CASE)
        private val RX_TAGS = Regex("<[^>]+>")
        private val RX_QUERY = Regex("""[^\p{L}\p{N} ]""")
        private val RX_SPACE = Regex("\\s+")

        fun adult(vararg text: String?): Boolean = text.any { !it.isNullOrBlank() && RX_ADULT.containsMatchIn(it) }

        /** Lower-case letters, digits and spaces only, so the user's words can't change the Archive query (operators are upper case). */
        fun cleanQuery(q: String): String = q.lowercase().replace(RX_QUERY, " ").trim().split(RX_SPACE).filter { it.isNotEmpty() }.take(8).joinToString(" ")

        private val RX_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,99}")
        fun idOf(url: String): String? = url.trim().takeIf { it.startsWith("classics:") }?.removePrefix("classics:")?.takeIf { RX_ID.matches(it) && ".." !in it }

        private fun stripHtml(s: String) = s.replace(RX_TAGS, " ").replace("&nbsp;", " ").replace(RX_SPACE, " ").trim()
        private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20").replace("%2F", "/")

        private fun JSONObject.text(k: String): String? = when (val v = opt(k)) {
            is JSONArray -> (0 until v.length()).joinToString("; ") { v.optString(it) }
            null, JSONObject.NULL -> null
            else -> v.toString()
        }?.takeIf { it.isNotBlank() }

        /** MP4 files the player can play, best first (h.264 originals, then the Archive's 512 kb copy), one per height. */
        internal fun playable(files: JSONArray): List<Pick> {
            val all = (0 until files.length()).mapNotNull { files.optJSONObject(it) }
                .filter { it.optString("name").endsWith(".mp4", true) }
                .map { f ->
                    val h = f.optString("height").toIntOrNull() ?: if (f.optString("format").contains("512Kb", true)) 240 else 360
                    Pick(f.optString("name"), "${h}p", h)
                }
            return all.sortedByDescending { it.height }.distinctBy { it.height }.take(3)
        }

        internal fun subtitles(files: JSONArray): List<Pair<String, String>> =
            (0 until files.length()).mapNotNull { files.optJSONObject(it)?.optString("name") }
                .filter { it.endsWith(".srt", true) || it.endsWith(".vtt", true) }
                .map { n -> (if (".asr." in n) "English (auto)" else "English") to n }
                .take(2)

        suspend fun httpGet(url: String, timeoutSec: Long = 20): String? = withContext(Dispatchers.IO) {
            runCatching {
                val call = NetworkClient.get().newCall(Request.Builder().url(url).header("User-Agent", "Yumeiro (https://yumeiro.click)").build())
                call.timeout().timeout(timeoutSec, java.util.concurrent.TimeUnit.SECONDS)
                call.execute().use { r -> if (r.isSuccessful) r.body.string() else null }
            }.getOrNull()
        }
    }
}
