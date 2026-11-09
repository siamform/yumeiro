/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.subtitles

import dev.asfakul.yumeiro.online.OnlineSettings
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * SubSource (subsource.net) — free subtitle API v1. Needs the user's own free API key from their
 * SubSource profile page; limits are about 60 requests a minute. Sent in the X-API-Key header.
 * Flow: find the title (by IMDb id, else text), list its subtitles, download the ZIP.
 */
object SubSource {
    const val NAME = "SubSource"
    private const val API = "https://api.subsource.net/api/v1"
    private const val MAX_ZIP = 12L * 1024 * 1024

    fun available(): Boolean = OnlineSettings.subsourceKey.value.isNotBlank()

    private fun key(): String = OnlineSettings.subsourceKey.value.ifBlank { throw IllegalStateException("Add a free SubSource API key in Settings → Online videos") }

    private fun get(client: OkHttpClient, url: okhttp3.HttpUrl): String =
        client.newCall(Request.Builder().url(url).header("X-API-Key", key()).header("Accept", "application/json").build()).execute().use { r ->
            if (r.code == 401 || r.code == 403) throw IllegalStateException("SubSource rejected the API key")
            if (r.code == 429) throw IllegalStateException("SubSource is busy, try again in a minute")
            if (!r.isSuccessful) throw IllegalStateException("SubSource answered HTTP ${r.code}")
            r.body.string()
        }

    data class Title(val id: Long, val title: String, val alternate: String?, val year: Int?)

    fun search(client: OkHttpClient, title: String, imdbId: String?, season: Int?, episode: Int?, language: String): List<SubtitleHit> {
        val lang = SubtitleLanguages.subsource(language) ?: return emptyList()
        fun find(byImdb: Boolean): List<Title> = parseTitles(get(client, "$API/movies/search".toHttpUrl().newBuilder().apply {
            if (byImdb) { addQueryParameter("searchType", "imdb"); addQueryParameter("imdb", imdbId) }
            else { addQueryParameter("searchType", "text"); addQueryParameter("q", title.lowercase()) }
            season?.let { addQueryParameter("season", it.toString()) }
        }.build()))
        val titles = (if (!imdbId.isNullOrBlank()) find(true) else emptyList()).ifEmpty { find(false) }
        val best = bestTitle(titles, title) ?: return emptyList()
        val url = "$API/subtitles".toHttpUrl().newBuilder().apply {
            addQueryParameter("movieId", best.id.toString())
            addQueryParameter("language", lang)
            addQueryParameter("limit", "60")
            season?.let { addQueryParameter("seasonNumber", it.toString()) }
            episode?.let { addQueryParameter("episodeNumber", it.toString()) }
        }.build()
        return parseSubtitles(get(client, url), language)
    }

    internal fun parseTitles(text: String): List<Title> {
        val arr = runCatching { JSONObject(text).optJSONArray("data") }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Title(o.optLong("movieId").takeIf { it > 0 } ?: return@mapNotNull null, o.optString("title"),
                o.optString("alternateTitle").ifBlank { null }, o.optInt("releaseYear").takeIf { it > 0 })
        }
    }

    /** Exact (case-insensitive) title or alternate title first, else the site's first hit. */
    internal fun bestTitle(titles: List<Title>, wanted: String): Title? {
        val w = wanted.trim().lowercase()
        return titles.firstOrNull { it.title.lowercase() == w || it.alternate?.lowercase() == w } ?: titles.firstOrNull()
    }

    internal fun parseSubtitles(text: String, language: String): List<SubtitleHit> {
        val o = runCatching { JSONObject(text) }.getOrNull() ?: return emptyList()
        if (o.has("success") && !o.optBoolean("success", true)) return emptyList()
        val arr = o.optJSONArray("data") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val s = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = s.optLong("subtitleId").takeIf { it > 0 } ?: return@mapNotNull null
            val releases = s.optJSONArray("releaseInfo")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } }.orEmpty()
            val commentary = s.optString("commentary")
            val uploaderId = s.optLong("uploaderId")
            val uploader = s.optJSONArray("contributors")?.let { a ->
                (0 until a.length()).mapNotNull { a.optJSONObject(it) }.firstOrNull { it.optLong("id") == uploaderId }?.optString("displayname")
            }?.ifBlank { null }
            SubtitleHit(
                provider = NAME, id = id.toString(), language = SubtitleLanguages.normalize(language),
                release = releases.firstOrNull() ?: commentary.ifBlank { "SubSource subtitle" },
                downloads = s.optInt("downloads", 0),
                hearingImpaired = s.optBoolean("hearingImpaired") || Regex("""(?i)\b(sdh|hi|cc)\b""").containsMatchIn(commentary) && !commentary.contains("non", ignoreCase = true),
                uploader = uploader,
            )
        }.distinctBy { it.id }
    }

    fun download(client: OkHttpClient, hit: SubtitleHit): ByteArray {
        require(hit.provider == NAME && hit.id.all(Char::isDigit))
        return client.newCall(Request.Builder().url("$API/subtitles/${hit.id}/download").header("X-API-Key", key()).build()).execute().use { r ->
            if (r.code == 401 || r.code == 403) throw IllegalStateException("SubSource rejected the API key")
            if (!r.isSuccessful) throw IllegalStateException("SubSource download failed (HTTP ${r.code})")
            SubtitleNet.readCapped(r.body.byteStream(), MAX_ZIP)
        }
    }
}
