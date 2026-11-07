/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.anime

import android.content.Context
import android.net.Uri
import dev.asfakul.yumeiro.BuildConfig
import dev.asfakul.yumeiro.online.OnlineSettings
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.SecureRandom

/** AniList account: sign in (OAuth implicit grant), then watched episodes update your list. */
object AniList {
    const val REDIRECT = "yumeiro://anilist-login"
    private const val API = "https://graphql.anilist.co"

    fun clientId(): String = OnlineSettings.secret("anilist_client").orEmpty().ifBlank { BuildConfig.ANILIST_CLIENT_ID }
    fun token(): String? = OnlineSettings.secret("anilist_token")
    fun user(): String? = OnlineSettings.secret("anilist_user")
    val signedIn: Boolean get() = token() != null

    fun loginUrl(): String? = clientId().takeIf { it.isNotBlank() }?.let {
        "https://anilist.co/api/v2/oauth/authorize?client_id=${Uri.encode(it)}&response_type=token"
    }

    /** Handles yumeiro://anilist-login#access_token=…; returns true when consumed. */
    fun handleRedirect(context: Context, uri: Uri, client: OkHttpClient): Boolean {
        if (uri.scheme != "yumeiro" || uri.host != "anilist-login") return false
        val frag = uri.fragment ?: uri.query ?: return true
        val token = frag.split('&').firstOrNull { it.startsWith("access_token=") }?.substringAfter('=')?.let(Uri::decode) ?: return true
        OnlineSettings.setSecret(context, "anilist_token", token)
        val name = runCatching { graphql(client, "query{Viewer{name}}", JSONObject(), token)?.optJSONObject("Viewer")?.optString("name") }.getOrNull()
        OnlineSettings.setSecret(context, "anilist_user", name ?: "AniList")
        return true
    }

    fun signOut(context: Context) { OnlineSettings.setSecret(context, "anilist_token", null); OnlineSettings.setSecret(context, "anilist_user", null) }

    /** Sets progress to [episode] (never lowers it) and marks the show as watching / completed. */
    fun watched(client: OkHttpClient, mediaId: Int, episode: Int, total: Int?): Boolean {
        val t = token() ?: return false
        val cur = graphql(client, "query(\$id:Int){Media(id:\$id){episodes mediaListEntry{progress status}}}", JSONObject().put("id", mediaId), t)?.optJSONObject("Media")
        val have = cur?.optJSONObject("mediaListEntry")?.optInt("progress") ?: 0
        if (episode <= have) return true
        val eps = total ?: cur?.optInt("episodes")?.takeIf { it > 0 }
        val status = if (eps != null && episode >= eps) "COMPLETED" else "CURRENT"
        return graphql(client, "mutation(\$id:Int,\$p:Int,\$s:MediaListStatus){SaveMediaListEntry(mediaId:\$id,progress:\$p,status:\$s){id}}",
            JSONObject().put("id", mediaId).put("p", episode).put("s", status), t) != null
    }

    internal fun graphql(client: OkHttpClient, query: String, vars: JSONObject, token: String?): JSONObject? =
        AnimeIds.post(client, API, JSONObject().put("query", query).put("variables", vars).toString(), token)?.optJSONObject("data")
}

/** MyAnimeList account: OAuth with PKCE, then watched episodes update your list. */
object MyAnimeList {
    const val REDIRECT = "yumeiro://mal-login"

    fun clientId(): String = OnlineSettings.secret("mal_client").orEmpty().ifBlank { BuildConfig.MAL_CLIENT_ID }
    fun token(): String? = OnlineSettings.secret("mal_token")
    fun user(): String? = OnlineSettings.secret("mal_user")
    val signedIn: Boolean get() = token() != null

    fun loginUrl(context: Context): String? {
        val id = clientId().takeIf { it.isNotBlank() } ?: return null
        // MAL only supports the "plain" PKCE method: verifier == challenge (43–128 chars).
        val verifier = ByteArray(48).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }.take(96)
        OnlineSettings.setSecret(context, "mal_verifier", verifier)
        return "https://myanimelist.net/v1/oauth2/authorize?response_type=code&client_id=${Uri.encode(id)}" +
            "&code_challenge=$verifier&code_challenge_method=plain&redirect_uri=${Uri.encode(REDIRECT)}"
    }

    fun handleRedirect(context: Context, uri: Uri, client: OkHttpClient): Boolean {
        if (uri.scheme != "yumeiro" || uri.host != "mal-login") return false
        val code = uri.getQueryParameter("code") ?: return true
        val verifier = OnlineSettings.secret("mal_verifier") ?: return true
        val body = FormBody.Builder().add("client_id", clientId()).add("code", code).add("code_verifier", verifier)
            .add("grant_type", "authorization_code").add("redirect_uri", REDIRECT).build()
        val token = runCatching {
            client.newCall(Request.Builder().url("https://myanimelist.net/v1/oauth2/token").post(body).build()).execute().use { r ->
                if (r.isSuccessful) JSONObject(r.body.string()) else null
            }
        }.getOrNull() ?: return true
        OnlineSettings.setSecret(context, "mal_token", token.optString("access_token"))
        OnlineSettings.setSecret(context, "mal_refresh", token.optString("refresh_token"))
        val name = runCatching {
            client.newCall(Request.Builder().url("https://api.myanimelist.net/v2/users/@me").header("Authorization", "Bearer ${token.optString("access_token")}").build())
                .execute().use { r -> if (r.isSuccessful) JSONObject(r.body.string()).optString("name") else null }
        }.getOrNull()
        OnlineSettings.setSecret(context, "mal_user", name ?: "MyAnimeList")
        return true
    }

    fun signOut(context: Context) { listOf("mal_token", "mal_refresh", "mal_user").forEach { OnlineSettings.setSecret(context, it, null) } }

    fun watched(client: OkHttpClient, malId: Int, episode: Int, total: Int?): Boolean {
        val t = token() ?: return false
        val cur = runCatching {
            client.newCall(Request.Builder().url("https://api.myanimelist.net/v2/anime/$malId?fields=num_episodes,my_list_status").header("Authorization", "Bearer $t").build())
                .execute().use { r -> if (r.isSuccessful) JSONObject(r.body.string()) else null }
        }.getOrNull()
        val have = cur?.optJSONObject("my_list_status")?.optInt("num_episodes_watched") ?: 0
        if (episode <= have) return true
        val eps = total ?: cur?.optInt("num_episodes")?.takeIf { it > 0 }
        val body = FormBody.Builder().add("num_watched_episodes", episode.toString())
            .add("status", if (eps != null && episode >= eps) "completed" else "watching").build()
        return runCatching {
            client.newCall(Request.Builder().url("https://api.myanimelist.net/v2/anime/$malId/my_list_status").patch(body).header("Authorization", "Bearer $t").build())
                .execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }
}
