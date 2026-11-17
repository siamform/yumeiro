/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.tracking

import android.content.Context
import dev.asfakul.yumeiro.online.OnlineSettings
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Kitsu anime tracking via Kitsu's OAuth password grant (no app credential needed; ADR-013).
 */
object Kitsu {
    private const val BASE = "https://kitsu.app/api/edge/"
    private const val LOGIN = "https://kitsu.app/api/oauth/token"
    // ADR-013: Kitsu has no app registration ("client_id and client_secret can be omitted"). The
    // password grant is sent with no client fields unless the user entered their own pair in
    // Settings, so the APK carries no Kitsu credential. Probe 2026-11-16: kitsu.app answers
    // `invalid_grant` (bad user) without client fields, `invalid_client` with empty ones.
    internal fun userClient(id: String?, secret: String?): Pair<String, String>? =
        if (!id.isNullOrBlank() && !secret.isNullOrBlank()) id to secret else null
    private fun userClient(): Pair<String, String>? =
        userClient(OnlineSettings.secret("kitsu_client"), OnlineSettings.secret("kitsu_client_secret"))
    val configured: Boolean get() = true
    internal fun loginBody(email: String, password: String, client: Pair<String, String>?): FormBody =
        FormBody.Builder().add("grant_type", "password").add("username", email.trim()).add("password", password)
            .apply { client?.let { (id, secret) -> add("client_id", id).add("client_secret", secret) } }.build()
    private val VND = "application/vnd.api+json".toMediaType()

    fun token(): String? = OnlineSettings.secret("kitsu_token")
    fun user(): String? = OnlineSettings.secret("kitsu_user")
    private fun userId(): String? = OnlineSettings.secret("kitsu_uid")
    val signedIn: Boolean get() = token() != null && userId() != null

    /** Signs in with e-mail + password. The password is sent to Kitsu once and never stored. */
    fun login(context: Context, client: OkHttpClient, email: String, password: String): Result<String> = runCatching {
        check(configured) { "Kitsu sign-in is not set up in this build" }
        val body = loginBody(email, password, userClient())
        val tok = client.newCall(Request.Builder().url(LOGIN).post(body).build()).execute().use { r ->
            if (!r.isSuccessful) error(if (r.code == 400 || r.code == 401) "Wrong e-mail or password" else "Kitsu answered HTTP ${r.code}")
            JSONObject(r.body.string())
        }
        val access = tok.getString("access_token")
        val me = get(client, "users?filter[self]=true", access).getJSONArray("data").getJSONObject(0)
        val name = me.optJSONObject("attributes")?.optString("name").orEmpty().ifBlank { "Kitsu" }
        saveTokens(context, tok)
        OnlineSettings.setSecret(context, "kitsu_uid", me.getString("id"))
        OnlineSettings.setSecret(context, "kitsu_user", name)
        name
    }

    fun signOut(context: Context) = listOf("kitsu_token", "kitsu_refresh", "kitsu_expires", "kitsu_uid", "kitsu_user").forEach { OnlineSettings.setSecret(context, it, null) }

    /** A Kitsu answer that is not 2xx; [code] lets a 401 trigger one refresh (IG-3). */
    class HttpError(val code: Int) : IllegalStateException("Kitsu HTTP $code")

    private const val REFRESH_AHEAD_MS = 24L * 60 * 60 * 1000

    private fun saveTokens(context: Context, o: JSONObject) {
        OnlineSettings.setSecret(context, "kitsu_token", o.getString("access_token"))
        o.optString("refresh_token").takeIf { it.isNotBlank() }?.let { OnlineSettings.setSecret(context, "kitsu_refresh", it) }
        val created = o.optLong("created_at", System.currentTimeMillis() / 1000)
        val expires = o.optLong("expires_in", 30L * 24 * 60 * 60) // Kitsu's default is 30 days
        OnlineSettings.setSecret(context, "kitsu_expires", ((created + expires) * 1000).toString())
    }

    internal fun needsRefresh(expiresAtMs: Long?, nowMs: Long): Boolean = expiresAtMs != null && expiresAtMs - nowMs < REFRESH_AHEAD_MS

    internal fun refreshBody(refreshToken: String, client: Pair<String, String>?): FormBody =
        FormBody.Builder().add("grant_type", "refresh_token").add("refresh_token", refreshToken)
            .apply { client?.let { (id, secret) -> add("client_id", id).add("client_secret", secret) } }.build()

    private val refreshLock = Any()

    /**
     * IG-3: Kitsu access tokens last 30 days. Before, the refresh token was stored and never used, so a
     * month after sign-in every update failed with 401 while the card still showed the account. The
     * token is refreshed a day ahead of a known expiry, or once on a 401 ([force]); a refused refresh
     * signs out (the card then asks to sign in again); a network error keeps the current token.
     */
    fun validToken(context: Context, client: OkHttpClient, force: Boolean = false): String? = synchronized(refreshLock) {
        val current = token() ?: return null
        val expiresAt = OnlineSettings.secret("kitsu_expires")?.toLongOrNull()
        if (!force && !needsRefresh(expiresAt, System.currentTimeMillis())) return current
        val refresh = OnlineSettings.secret("kitsu_refresh")?.takeIf { it.isNotBlank() } ?: return current
        val (status, text) = runCatching {
            client.newCall(Request.Builder().url(LOGIN).post(refreshBody(refresh, userClient())).build()).execute().use { it.code to it.body.string() }
        }.getOrElse { return current }
        when (status) {
            200 -> { saveTokens(context, JSONObject(text)); token() }
            400, 401, 403 -> { signOut(context); null }
            else -> current
        }
    }

    private fun get(client: OkHttpClient, path: String, token: String): JSONObject =
        client.newCall(Request.Builder().url(BASE + path).header("Accept", "application/vnd.api+json").header("Authorization", "Bearer $token").build())
            .execute().use { r -> if (!r.isSuccessful) throw HttpError(r.code); JSONObject(r.body.string()) }

    /** Kitsu id for a MyAnimeList anime id, via Kitsu's mappings. */
    private fun kitsuIdForMal(client: OkHttpClient, malId: Int, t: String): String? {
        val o = get(client, "mappings?filter[externalSite]=myanimelist/anime&filter[externalId]=$malId&include=item", t)
        return o.optJSONArray("included")?.optJSONObject(0)?.optString("id")?.takeIf { it.isNotBlank() }
    }

    /** Marks [episode] watched (never lowers progress). A 401 refreshes the token once and tries again. */
    fun watched(context: Context, client: OkHttpClient, malId: Int, episode: Int, total: Int?): Boolean {
        val t = validToken(context, client) ?: return false
        return try {
            watchedWith(client, t, malId, episode, total)
        } catch (e: HttpError) {
            if (e.code != 401) throw e
            val fresh = validToken(context, client, force = true) ?: return false
            if (fresh == t) return false
            watchedWith(client, fresh, malId, episode, total)
        }
    }

    private fun watchedWith(client: OkHttpClient, t: String, malId: Int, episode: Int, total: Int?): Boolean {
        val uid = userId() ?: return false
        val animeId = kitsuIdForMal(client, malId, t) ?: return false
        val existing = get(client, "library-entries?filter[animeId]=$animeId&filter[userId]=$uid", t).optJSONArray("data")?.optJSONObject(0)
        val have = existing?.optJSONObject("attributes")?.optInt("progress") ?: 0
        if (episode <= have) return true
        val status = if (total != null && episode >= total) "completed" else "current"
        val attrs = JSONObject().put("progress", episode).put("status", status)
        val data = JSONObject().put("type", "libraryEntries").put("attributes", attrs)
        val req = if (existing != null) {
            data.put("id", existing.getString("id"))
            Request.Builder().url(BASE + "library-entries/" + existing.getString("id")).patch(JSONObject().put("data", data).toString().toRequestBody(VND))
        } else {
            data.put("relationships", JSONObject()
                .put("user", JSONObject().put("data", JSONObject().put("id", uid).put("type", "users")))
                .put("anime", JSONObject().put("data", JSONObject().put("id", animeId).put("type", "anime"))))
            Request.Builder().url(BASE + "library-entries").post(JSONObject().put("data", data).toString().toRequestBody(VND))
        }
        return client.newCall(req.header("Authorization", "Bearer $t").header("Accept", "application/vnd.api+json").build()).execute().use { it.isSuccessful }
    }
}
