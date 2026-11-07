/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
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
    private const val UA = "Yumeiro v1"

    data class Result(val fileId: Long, val language: String, val release: String, val downloads: Int, val hearingImpaired: Boolean, val fromTrusted: Boolean)

    fun available(): Boolean = OnlineSettings.openSubtitlesKey.value.isNotBlank()

    fun search(client: OkHttpClient, title: String, imdbId: String?, season: Int?, episode: Int?, language: String): List<Result> {
        val key = OnlineSettings.openSubtitlesKey.value.ifBlank { throw IllegalStateException("Add an OpenSubtitles API key in Settings → Online videos") }
        val url = "$API/subtitles".toHttpUrl().newBuilder().apply {
            if (imdbId != null) addQueryParameter("imdb_id", imdbId.removePrefix("tt").trimStart('0')) else addQueryParameter("query", title)
            if (season != null) addQueryParameter("season_number", season.toString())
            if (episode != null) addQueryParameter("episode_number", episode.toString())
            addQueryParameter("languages", language.lowercase())
        }.build()
        val text = client.newCall(Request.Builder().url(url).header("Api-Key", key).header("User-Agent", UA).header("Accept", "application/json").build()).execute().use { r ->
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
        val key = OnlineSettings.openSubtitlesKey.value
        val body = JSONObject().put("file_id", fileId).put("sub_format", "srt").toString().toRequestBody("application/json".toMediaType())
        return client.newCall(Request.Builder().url("$API/download").post(body).header("Api-Key", key).header("User-Agent", UA).header("Accept", "application/json").build()).execute().use { r ->
            val o = runCatching { JSONObject(r.body.string()) }.getOrNull()
            if (!r.isSuccessful) throw IllegalStateException(o?.optString("message")?.takeIf { it.isNotBlank() } ?: "OpenSubtitles answered HTTP ${r.code}")
            o?.optString("link")?.takeIf { it.startsWith("http") } ?: throw IllegalStateException("No download link")
        }
    }
}
