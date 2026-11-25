/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.tracking.v2

import android.content.Context
import dev.asfakul.yumeiro.online.OnlineSettings
import dev.asfakul.yumeiro.online.anime.AniList
import dev.asfakul.yumeiro.online.anime.AnimeIds
import dev.asfakul.yumeiro.online.anime.MyAnimeList
import dev.asfakul.yumeiro.tracking.TrackerHttp
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * TRACK-2: chapter progress for manga and novels, with V1's rules for episodes (IG-10, IG-11): read
 * the current progress first and never lower it; a failed read writes nothing and throws (retried);
 * completed once the last chapter is reached.
 */
object TrackReading {
    /** Pure: what to write, or null when [have] is already at or past [chapter]. */
    fun plan(chapter: Int, have: Int, total: Int?): Pair<Int, Boolean>? =
        if (chapter <= have) null else chapter to (total != null && total > 0 && chapter >= total)

    fun aniList(client: OkHttpClient, mediaId: Int, chapter: Int, total: Int?): Boolean {
        val t = AniList.token() ?: return false
        try {
            val cur = AniList.graphqlOrThrow(client, "query(\$id:Int){Media(id:\$id){chapters mediaListEntry{progress}}}", JSONObject().put("id", mediaId), t)
                ?.optJSONObject("Media")
            val have = cur?.optJSONObject("mediaListEntry")?.optInt("progress") ?: 0
            val (p, done) = plan(chapter, have, total ?: cur?.optInt("chapters")?.takeIf { it > 0 }) ?: return true
            return AniList.graphqlOrThrow(client, "mutation(\$id:Int,\$p:Int,\$s:MediaListStatus){SaveMediaListEntry(mediaId:\$id,progress:\$p,status:\$s){id}}",
                JSONObject().put("id", mediaId).put("p", p).put("s", if (done) "COMPLETED" else "CURRENT"), t) != null
        } catch (e: AnimeIds.HttpError) {
            if (e.code != 401) throw e
            if (AniList.token() == t) { OnlineSettings.clearSecret("anilist_token"); OnlineSettings.clearSecret("anilist_user") } // IG-12
            return false
        }
    }

    fun myAnimeList(context: Context, client: OkHttpClient, malId: Int, chapter: Int, total: Int?): Boolean {
        val t = MyAnimeList.validToken(context, client) ?: return false
        val code = TrackerHttp.check("MyAnimeList", malWrite(client, t, malId, chapter, total))
        if (code != 401) return code in 200..299
        val fresh = MyAnimeList.validToken(context, client, force = true) ?: return false
        if (fresh == t) return false
        return TrackerHttp.check("MyAnimeList", malWrite(client, fresh, malId, chapter, total)) in 200..299
    }

    private fun malWrite(client: OkHttpClient, t: String, malId: Int, chapter: Int, total: Int?): Int {
        var getCode = -1
        val cur = runCatching {
            client.newCall(Request.Builder().url("https://api.myanimelist.net/v2/manga/$malId?fields=num_chapters,my_list_status").header("Authorization", "Bearer $t").build())
                .execute().use { r -> getCode = r.code; if (r.isSuccessful) JSONObject(r.body.string()) else null }
        }.getOrNull()
        if (getCode == 401) return 401
        if (cur == null) { if (getCode == 404) return 404; throw java.io.IOException("MyAnimeList read HTTP $getCode") }
        val have = cur.optJSONObject("my_list_status")?.optInt("num_chapters_read") ?: 0
        val (p, done) = plan(chapter, have, total ?: cur.optInt("num_chapters").takeIf { it > 0 }) ?: return 200
        val body = FormBody.Builder().add("num_chapters_read", p.toString()).add("status", if (done) "completed" else "reading").build()
        return runCatching {
            client.newCall(Request.Builder().url("https://api.myanimelist.net/v2/manga/$malId/my_list_status").header("Authorization", "Bearer $t").patch(body).build())
                .execute().use { it.code }
        }.getOrDefault(-1)
    }
}
