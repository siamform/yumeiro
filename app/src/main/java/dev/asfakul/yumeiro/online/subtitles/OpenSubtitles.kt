/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.subtitles

import dev.asfakul.yumeiro.online.OnlineSettings
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Subtitle search on OpenSubtitles.com (REST API v1). Needs a free API key from
 * opensubtitles.com/consumers (Settings → Online videos). Searches by IMDb id when the extension
 * gives one, else by title, with season/episode for shows.
 */
object OpenSubtitles {
    private const val API = "https://api.opensubtitles.com/api/v1"
    private val UA = "Yumeiro v${dev.asfakul.yumeiro.BuildConfig.VERSION_NAME}"
    private const val KEY_HEADER = "Api-Key"
    private const val DOMAIN = "opensubtitles.com"

    /** The key header never follows a redirect off opensubtitles.com (VIP hosts are subdomains) or onto plain HTTP. */
    private fun scoped(client: OkHttpClient): OkHttpClient = SubtitleNet.keyScoped(client, KEY_HEADER, DOMAIN)

    data class Result(val fileId: Long, val language: String, val release: String, val downloads: Int, val hearingImpaired: Boolean, val fromTrusted: Boolean)

    /** The user's own key, else the one built into release builds (OPENSUBTITLES_API_KEY). */
    fun key(): String = OnlineSettings.openSubtitlesKey.value.ifBlank { dev.asfakul.yumeiro.BuildConfig.OPENSUBTITLES_API_KEY }
    val builtIn: Boolean get() = dev.asfakul.yumeiro.BuildConfig.OPENSUBTITLES_API_KEY.isNotBlank()
    fun available(): Boolean = key().isNotBlank()

    internal fun searchParams(title: String, imdbId: String?, season: Int?, episode: Int?, language: String): List<Pair<String, String>> = buildMap {
        if (imdbId != null) put("imdb_id", imdbId.removePrefix("tt").trimStart('0')) else put("query", title.lowercase())
        if (season != null) put("season_number", season.toString())
        if (episode != null) put("episode_number", episode.toString())
        put("languages", language.lowercase())
    }.toSortedMap().toList()

    fun search(client: OkHttpClient, title: String, imdbId: String?, season: Int?, episode: Int?, language: String): List<Result> {
        val key = key().ifBlank { throw IllegalStateException("Add an OpenSubtitles API key in Settings → Online videos") }
        // The API 301-redirects any request whose parameters aren't sorted by name (and lowercase),
        // costing a round trip each search, so they are added in sorted order.
        val url = "$API/subtitles".toHttpUrl().newBuilder().apply {
            searchParams(title, imdbId, season, episode, language).forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        val text = scoped(client).newCall(Request.Builder().url(url).header(KEY_HEADER, key).header("User-Agent", UA).header("Accept", "application/json").build()).execute().use { r ->
            if (r.code == 401 || r.code == 403) throw IllegalStateException("OpenSubtitles rejected the API key")
            if (!r.isSuccessful) throw IllegalStateException("OpenSubtitles answered HTTP ${r.code}")
            r.body.string()
        }
        return parse(text)
    }

    internal fun parse(text: String): List<Result> {
        val data = JSONObject(text).optJSONArray("data") ?: return emptyList()
        return (0 until data.length()).mapNotNull { i ->
            val a = data.optJSONObject(i)?.optJSONObject("attributes") ?: return@mapNotNull null
            val file = a.optJSONArray("files")?.optJSONObject(0) ?: return@mapNotNull null
            Result(
                fileId = file.optLong("file_id").takeIf { it > 0 } ?: return@mapNotNull null,
                language = a.optString("language"), release = a.optString("release").ifBlank { file.optString("file_name") },
                downloads = a.optInt("download_count"), hearingImpaired = a.optBoolean("hearing_impaired"), fromTrusted = a.optBoolean("from_trusted"),
            )
        }.sortedWith(compareByDescending<Result> { it.fromTrusted }.thenByDescending { it.downloads })
    }

    /** Temporary download link for [fileId] (SRT). */
    fun link(client: OkHttpClient, fileId: Long): String {
        val key = key()
        val body = JSONObject().put("file_id", fileId).put("sub_format", "srt").toString().toRequestBody("application/json".toMediaType())
        return scoped(client).newCall(Request.Builder().url("$API/download").post(body).header(KEY_HEADER, key).header("User-Agent", UA).header("Accept", "application/json").build()).execute().use { r ->
            val o = runCatching { JSONObject(r.body.string()) }.getOrNull()
            if (!r.isSuccessful) throw IllegalStateException(o?.optString("message")?.takeIf { it.isNotBlank() } ?: "OpenSubtitles answered HTTP ${r.code}")
            o?.optString("link")?.takeIf { it.startsWith("http") } ?: throw IllegalStateException("No download link")
        }
    }
}
