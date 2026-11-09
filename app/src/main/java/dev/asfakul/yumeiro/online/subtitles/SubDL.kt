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
 * SubDL (subdl.com) — free subtitle API. Needs the user's own free API key (subdl.com → sign in →
 * API key in the account panel); free keys allow about 2,000 searches a day. Docs: subdl.com/api-doc.
 * The key rides in the query string, so URLs are never logged or shown in errors.
 */
object SubDL {
    const val NAME = "SubDL"
    private const val API = "https://api.subdl.com/api/v1/subtitles"
    private const val DL = "https://dl.subdl.com"
    private const val MAX_ZIP = 12L * 1024 * 1024

    fun available(): Boolean = OnlineSettings.subdlKey.value.isNotBlank()

    /** SubDL refuses titles with these characters; each becomes a space (keeps "Grey s Anatomy" searchable). */
    internal fun sanitizeTitle(title: String): String = title.replace(Regex("""[<>{}\[\]'"`´’;\\/]"""), " ").replace(Regex("\\s+"), " ").trim()

    fun search(client: OkHttpClient, title: String, imdbId: String?, season: Int?, episode: Int?, language: String): List<SubtitleHit> {
        val key = OnlineSettings.subdlKey.value.ifBlank { throw IllegalStateException("Add a free SubDL API key in Settings → Online videos") }
        val url = API.toHttpUrl().newBuilder().apply {
            addQueryParameter("api_key", key)
            if (!imdbId.isNullOrBlank()) addQueryParameter("imdb_id", imdbId) else addQueryParameter("film_name", sanitizeTitle(title))
            if (season != null || episode != null) addQueryParameter("type", "tv")
            season?.let { addQueryParameter("season_number", it.toString()) }
            episode?.let { addQueryParameter("episode_number", it.toString()) }
            addQueryParameter("languages", SubtitleLanguages.subdl(language))
            addQueryParameter("subs_per_page", "30")
            addQueryParameter("releases", "1")
            addQueryParameter("hi", "1")
        }.build()
        val text = client.newCall(Request.Builder().url(url).header("Accept", "application/json").build()).execute().use { r ->
            if (r.code == 401 || r.code == 403) throw IllegalStateException("SubDL rejected the API key")
            if (r.code == 429) throw IllegalStateException("SubDL daily limit reached, try again later")
            r.body.string()
        }
        return parse(text, language)
    }

    internal fun parse(text: String, language: String): List<SubtitleHit> {
        val o = runCatching { JSONObject(text) }.getOrNull() ?: return emptyList()
        if (o.has("status") && !o.optBoolean("status", true)) return emptyList()
        val arr = o.optJSONArray("subtitles") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val s = arr.optJSONObject(i) ?: return@mapNotNull null
            val path = s.optString("url").takeIf { it.startsWith("/subtitle/") && !it.contains("..") } ?: return@mapNotNull null
            SubtitleHit(
                provider = NAME, id = path,
                language = s.optString("language").ifBlank { s.optString("lang") }.takeIf { it.length in 2..5 }?.let(SubtitleLanguages::normalize)
                    ?: SubtitleLanguages.normalize(language),
                release = s.optString("release_name").ifBlank { s.optString("name") }.ifBlank { "SubDL subtitle" },
                hearingImpaired = s.optBoolean("hi"),
                seasonPack = s.optBoolean("full_season") || (s.optInt("episode_from", 0) > 0 && s.optInt("episode_end", 0) > s.optInt("episode_from", 0)),
                uploader = s.optString("author").ifBlank { null },
            )
        }.distinctBy { it.id }
    }

    /** The ZIP for [hit] (anonymous download from dl.subdl.com). */
    fun download(client: OkHttpClient, hit: SubtitleHit): ByteArray {
        require(hit.provider == NAME && hit.id.startsWith("/subtitle/"))
        return client.newCall(Request.Builder().url(DL + hit.id).build()).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("SubDL download failed (HTTP ${r.code})")
            SubtitleNet.readCapped(r.body.byteStream(), MAX_ZIP)
        }
    }
}
