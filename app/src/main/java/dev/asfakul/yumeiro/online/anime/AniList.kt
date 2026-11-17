/*
 * Yumeiro — Copyright (C) 2026 siamform
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

    fun loginUrl(context: Context): String? = clientId().takeIf { it.isNotBlank() }?.let {
        OAuthGuard.begin(context, "anilist")
        "https://anilist.co/api/v2/oauth/authorize?client_id=${Uri.encode(it)}&response_type=token"
    }

    /**
     * Handles yumeiro://anilist-login#access_token=…; returns true when consumed.
     *
     * Any web page or app can open this link, so a token is only taken right after the user tapped
     * "Sign in" here (login CSRF: someone else's account must not be swapped in), and only if AniList
     * accepts it. A bad link never signs the user out.
     */
    fun handleRedirect(context: Context, uri: Uri, client: OkHttpClient): Boolean {
        if (uri.scheme != "yumeiro" || uri.host != "anilist-login") return false
        if (!OAuthGuard.consume(context, "anilist")) return true
        val frag = uri.fragment ?: uri.query ?: return true
        val token = frag.split('&').firstOrNull { it.startsWith("access_token=") }?.substringAfter('=')?.let(Uri::decode)
            ?.takeIf { it.length in 20..4096 && it.none { c -> c.isWhitespace() } } ?: return true
        val name = runCatching { graphql(client, "query{Viewer{name}}", JSONObject(), token)?.optJSONObject("Viewer")?.optString("name") }
            .getOrNull()?.takeIf { it.isNotBlank() } ?: return true
        OnlineSettings.setSecret(context, "anilist_token", token)
        OnlineSettings.setSecret(context, "anilist_user", name.take(64))
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
        OAuthGuard.begin(context, "mal")
        return "https://myanimelist.net/v1/oauth2/authorize?response_type=code&client_id=${Uri.encode(id)}" +
            "&code_challenge=$verifier&code_challenge_method=plain&redirect_uri=${Uri.encode(REDIRECT)}"
    }

    fun handleRedirect(context: Context, uri: Uri, client: OkHttpClient): Boolean {
        if (uri.scheme != "yumeiro" || uri.host != "mal-login") return false
        if (!OAuthGuard.consume(context, "mal")) return true // not started here: ignore (login CSRF)
        val code = uri.getQueryParameter("code")?.takeIf { it.length in 8..4096 } ?: return true
        val verifier = OnlineSettings.secret("mal_verifier") ?: return true
        OnlineSettings.setSecret(context, "mal_verifier", null) // one use
        val body = FormBody.Builder().add("client_id", clientId()).add("code", code).add("code_verifier", verifier)
            .add("grant_type", "authorization_code").add("redirect_uri", REDIRECT).build()
        val token = runCatching {
            client.newCall(Request.Builder().url("https://myanimelist.net/v1/oauth2/token").post(body).build()).execute().use { r ->
                if (r.isSuccessful) JSONObject(r.body.string()) else null
            }
        }.getOrNull()?.takeIf { it.optString("access_token").isNotBlank() } ?: return true
        saveTokens(context, token)
        val name = runCatching {
            client.newCall(Request.Builder().url("https://api.myanimelist.net/v2/users/@me").header("Authorization", "Bearer ${token.optString("access_token")}").build())
                .execute().use { r -> if (r.isSuccessful) JSONObject(r.body.string()).optString("name") else null }
        }.getOrNull()
        OnlineSettings.setSecret(context, "mal_user", name ?: "MyAnimeList")
        return true
    }

    fun signOut(context: Context) { listOf("mal_token", "mal_refresh", "mal_expires", "mal_user").forEach { OnlineSettings.setSecret(context, it, null) } }

    private const val TOKEN_URL = "https://myanimelist.net/v1/oauth2/token"
    private const val REFRESH_AHEAD_MS = 24L * 60 * 60 * 1000

    private fun saveTokens(context: Context, o: JSONObject) {
        OnlineSettings.setSecret(context, "mal_token", o.getString("access_token"))
        // MAL rotates the refresh token on every refresh; keep the old one only if none came back.
        o.optString("refresh_token").takeIf { it.isNotBlank() }?.let { OnlineSettings.setSecret(context, "mal_refresh", it) }
        val seconds = o.optLong("expires_in", 0L).takeIf { it > 0 } ?: (31L * 24 * 60 * 60)
        OnlineSettings.setSecret(context, "mal_expires", (System.currentTimeMillis() + seconds * 1000).toString())
    }

    internal fun needsRefresh(expiresAtMs: Long?, nowMs: Long): Boolean = expiresAtMs != null && expiresAtMs - nowMs < REFRESH_AHEAD_MS

    private val refreshLock = Any()

    /**
     * IG-3: MAL access tokens expire (about 31 days in practice). Before, the refresh token was stored
     * and never used, so tracking silently stopped while the card still showed the account. Refreshes
     * a day ahead of a known expiry, or once on a 401 ([force]). A refused refresh signs out; a
     * network error keeps the current token.
     */
    fun validToken(context: Context, client: OkHttpClient, force: Boolean = false): String? = synchronized(refreshLock) {
        val current = token() ?: return null
        val expiresAt = OnlineSettings.secret("mal_expires")?.toLongOrNull()
        if (!force && !needsRefresh(expiresAt, System.currentTimeMillis())) return current
        val refresh = OnlineSettings.secret("mal_refresh")?.takeIf { it.isNotBlank() } ?: return current
        val id = clientId().takeIf { it.isNotBlank() } ?: return current
        val body = FormBody.Builder().add("client_id", id).add("grant_type", "refresh_token").add("refresh_token", refresh).build()
        val (status, text) = runCatching {
            client.newCall(Request.Builder().url(TOKEN_URL).post(body).build()).execute().use { it.code to it.body.string() }
        }.getOrElse { return current }
        when (status) {
            200 -> {
                val o = runCatching { JSONObject(text) }.getOrNull()?.takeIf { it.optString("access_token").isNotBlank() } ?: return current
                saveTokens(context, o); token()
            }
            400, 401, 403 -> { signOut(context); null }
            else -> current
        }
    }

    /** Marks [episode] watched (never lowers progress). A 401 refreshes the token once and tries again. */
    fun watched(context: Context, client: OkHttpClient, malId: Int, episode: Int, total: Int?): Boolean {
        val t = validToken(context, client) ?: return false
        val code = watchedWith(client, t, malId, episode, total)
        if (code != 401) return code in 200..299
        val fresh = validToken(context, client, force = true) ?: return false
        if (fresh == t) return false
        return watchedWith(client, fresh, malId, episode, total) in 200..299
    }

    /** HTTP code of the decisive call (401 from either call), 200 when already up to date, -1 on a network error. */
    private fun watchedWith(client: OkHttpClient, t: String, malId: Int, episode: Int, total: Int?): Int {
        var getCode = -1
        val cur = runCatching {
            client.newCall(Request.Builder().url("https://api.myanimelist.net/v2/anime/$malId?fields=num_episodes,my_list_status").header("Authorization", "Bearer $t").build())
                .execute().use { r -> getCode = r.code; if (r.isSuccessful) JSONObject(r.body.string()) else null }
        }.getOrNull()
        if (getCode == 401) return 401
        val have = cur?.optJSONObject("my_list_status")?.optInt("num_episodes_watched") ?: 0
        if (episode <= have) return 200
        val eps = total ?: cur?.optInt("num_episodes")?.takeIf { it > 0 }
        val body = FormBody.Builder().add("num_watched_episodes", episode.toString())
            .add("status", if (eps != null && episode >= eps) "completed" else "watching").build()
        return runCatching {
            client.newCall(Request.Builder().url("https://api.myanimelist.net/v2/anime/$malId/my_list_status").patch(body).header("Authorization", "Bearer $t").build())
                .execute().use { it.code }
        }.getOrDefault(-1)
    }
}

/**
 * OAuth redirects are only accepted within 10 minutes of the user starting sign-in from this app,
 * and only once. Stops other apps / web pages from pushing their own account (or junk) into ours.
 */
internal object OAuthGuard {
    const val WINDOW_MS = 10 * 60_000L
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("mt_oauth_guard", Context.MODE_PRIVATE)
    fun begin(c: Context, provider: String, now: Long = System.currentTimeMillis()) { prefs(c).edit().putLong(provider, now).apply() }
    fun consume(c: Context, provider: String, now: Long = System.currentTimeMillis()): Boolean {
        val at = prefs(c).getLong(provider, 0L)
        prefs(c).edit().remove(provider).apply()
        return at > 0L && now >= at && now - at <= WINDOW_MS
    }
}
