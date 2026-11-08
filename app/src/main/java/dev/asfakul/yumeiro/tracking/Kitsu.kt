/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.tracking

import android.content.Context
import dev.asfakul.yumeiro.BuildConfig
import dev.asfakul.yumeiro.online.OnlineSettings
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Kitsu anime tracking via Kitsu's OAuth password grant. The client id/secret are build-time
 * config (see app/build.gradle.kts), never committed.
 */
object Kitsu {
    private const val BASE = "https://kitsu.app/api/edge/"
    private const val LOGIN = "https://kitsu.app/api/oauth/token"
    // OAuth client of the Kitsu password grant. Injected at build time (KITSU_CLIENT_ID /
    // KITSU_CLIENT_SECRET env or Gradle properties) so no credential lives in the source tree;
    // users can also enter their own in Settings. Login is hidden when neither is set.
    private fun clientId(): String = OnlineSettings.secret("kitsu_client").orEmpty().ifBlank { BuildConfig.KITSU_CLIENT_ID }
    private fun clientSecret(): String = OnlineSettings.secret("kitsu_client_secret").orEmpty().ifBlank { BuildConfig.KITSU_CLIENT_SECRET }
    val configured: Boolean get() = clientId().isNotBlank() && clientSecret().isNotBlank()
    private val VND = "application/vnd.api+json".toMediaType()

    fun token(): String? = OnlineSettings.secret("kitsu_token")
    fun user(): String? = OnlineSettings.secret("kitsu_user")
    private fun userId(): String? = OnlineSettings.secret("kitsu_uid")
    val signedIn: Boolean get() = token() != null && userId() != null

    /** Signs in with e-mail + password. The password is sent to Kitsu once and never stored. */
    fun login(context: Context, client: OkHttpClient, email: String, password: String): Result<String> = runCatching {
        check(configured) { "Kitsu sign-in is not set up in this build" }
        val body = FormBody.Builder().add("grant_type", "password").add("username", email.trim()).add("password", password)
            .add("client_id", clientId()).add("client_secret", clientSecret()).build()
        val tok = client.newCall(Request.Builder().url(LOGIN).post(body).build()).execute().use { r ->
            if (!r.isSuccessful) error(if (r.code == 400 || r.code == 401) "Wrong e-mail or password" else "Kitsu answered HTTP ${r.code}")
            JSONObject(r.body.string())
        }
        val access = tok.getString("access_token")
        val me = get(client, "users?filter[self]=true", access).getJSONArray("data").getJSONObject(0)
        val name = me.optJSONObject("attributes")?.optString("name").orEmpty().ifBlank { "Kitsu" }
        OnlineSettings.setSecret(context, "kitsu_token", access)
        OnlineSettings.setSecret(context, "kitsu_refresh", tok.optString("refresh_token"))
        OnlineSettings.setSecret(context, "kitsu_uid", me.getString("id"))
        OnlineSettings.setSecret(context, "kitsu_user", name)
        name
    }

    fun signOut(context: Context) = listOf("kitsu_token", "kitsu_refresh", "kitsu_uid", "kitsu_user").forEach { OnlineSettings.setSecret(context, it, null) }

    private fun get(client: OkHttpClient, path: String, token: String): JSONObject =
        client.newCall(Request.Builder().url(BASE + path).header("Accept", "application/vnd.api+json").header("Authorization", "Bearer $token").build())
            .execute().use { r -> if (!r.isSuccessful) error("Kitsu HTTP ${r.code}"); JSONObject(r.body.string()) }

    /** Kitsu id for a MyAnimeList anime id, via Kitsu's mappings. */
    fun kitsuIdForMal(client: OkHttpClient, malId: Int): String? {
        val t = token() ?: return null
        val o = get(client, "mappings?filter[externalSite]=myanimelist/anime&filter[externalId]=$malId&include=item", t)
        return o.optJSONArray("included")?.optJSONObject(0)?.optString("id")?.takeIf { it.isNotBlank() }
    }

    /** Marks [episode] watched (never lowers progress). */
    fun watched(client: OkHttpClient, malId: Int, episode: Int, total: Int?): Boolean {
        val t = token() ?: return false
        val uid = userId() ?: return false
        val animeId = kitsuIdForMal(client, malId) ?: return false
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
