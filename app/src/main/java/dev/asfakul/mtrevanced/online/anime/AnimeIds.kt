/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.mtrevanced.online.anime

import dev.asfakul.mtrevanced.online.OnlineDetail
import dev.asfakul.mtrevanced.online.OnlineKind
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * MyAnimeList / AniList ids for an anime title: from the extension when it provides them,
 * otherwise found once through AniList's public search by title and year.
 */
object AnimeIds {
    data class Ids(val anilist: Int?, val mal: Int?, val episodes: Int?)

    private val cache = ConcurrentHashMap<String, Ids>()

    fun isAnime(d: OnlineDetail): Boolean =
        d.kind == OnlineKind.ANIME || d.malId != null || d.aniListId != null || d.type.contains("anime", true) || d.type.equals("OVA", true)

    fun of(client: OkHttpClient, d: OnlineDetail): Ids? {
        if (!isAnime(d)) return null
        if (d.malId != null && d.aniListId != null) return Ids(d.aniListId, d.malId, null)
        val key = "${d.api}|${d.url}"
        cache[key]?.let { return it }
        val found = runCatching {
            if (d.aniListId != null) byAniListId(client, d.aniListId) else search(client, cleanTitle(d.name), d.year)
        }.getOrNull()
        val ids = Ids(found?.anilist ?: d.aniListId, d.malId ?: found?.mal, found?.episodes)
        if (ids.anilist != null || ids.mal != null) cache[key] = ids
        return ids.takeIf { it.anilist != null || it.mal != null }
    }

    /** Strips "(Dub)", "Season 2 [1080p]" style suffixes extensions add. */
    internal fun cleanTitle(name: String): String = name
        .replace(Regex("""\s*[(\[][^)\]]*(dub|sub|uncensored|\d{3,4}p)[^)\]]*[)\]]""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s+(dub|dubbed|sub|subbed)$""", RegexOption.IGNORE_CASE), "")
        .trim()

    private fun search(client: OkHttpClient, title: String, year: Int?): Ids? {
        val q = "query(\$s:String,\$y:Int){Page(perPage:5){media(search:\$s,type:ANIME,seasonYear:\$y){id idMal episodes}}}"
        val vars = JSONObject().put("s", title).apply { if (year != null) put("y", year) }
        var media = AniList.graphql(client, q, vars, null)?.optJSONObject("Page")?.optJSONArray("media")
        if ((media == null || media.length() == 0) && year != null) {
            media = AniList.graphql(client, q, JSONObject().put("s", title), null)?.optJSONObject("Page")?.optJSONArray("media")
        }
        val m = media?.optJSONObject(0) ?: return null
        return Ids(m.optInt("id").takeIf { it > 0 }, m.optInt("idMal").takeIf { it > 0 }, m.optInt("episodes").takeIf { it > 0 })
    }

    private fun byAniListId(client: OkHttpClient, id: Int): Ids? {
        val m = AniList.graphql(client, "query(\$id:Int){Media(id:\$id){id idMal episodes}}", JSONObject().put("id", id), null)?.optJSONObject("Media") ?: return null
        return Ids(m.optInt("id").takeIf { it > 0 }, m.optInt("idMal").takeIf { it > 0 }, m.optInt("episodes").takeIf { it > 0 })
    }

    internal val JSON = "application/json".toMediaType()
    internal fun post(client: OkHttpClient, url: String, body: String, token: String?): JSONObject? {
        val b = Request.Builder().url(url).post(body.toRequestBody(JSON)).header("Accept", "application/json")
        if (token != null) b.header("Authorization", "Bearer $token")
        return client.newCall(b.build()).execute().use { r -> if (!r.isSuccessful) null else JSONObject(r.body.string()) }
    }
}
