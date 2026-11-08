/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.tracking

import android.content.Context
import dev.asfakul.yumeiro.BuildConfig
import dev.asfakul.yumeiro.online.OnlineSettings
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Trakt tracking for movies and shows using the OAuth device-code flow (no redirect needed, works
 * on TVs). A free Trakt API app is required; its id/secret come from the build or are pasted by
 * the user in Settings → Tracking.
 */
object Trakt {
    private const val API = "https://api.trakt.tv"
    private val JSON = "application/json".toMediaType()

    fun clientId(): String = OnlineSettings.secret("trakt_client").orEmpty().ifBlank { BuildConfig.TRAKT_CLIENT_ID }
    fun clientSecret(): String = OnlineSettings.secret("trakt_secret").orEmpty().ifBlank { BuildConfig.TRAKT_CLIENT_SECRET }
    val configured: Boolean get() = clientId().isNotBlank() && clientSecret().isNotBlank()
    fun token(): String? = OnlineSettings.secret("trakt_token")
    fun user(): String? = OnlineSettings.secret("trakt_user")
    val signedIn: Boolean get() = token() != null

    data class DeviceCode(val deviceCode: String, val userCode: String, val verificationUrl: String, val intervalSec: Int, val expiresSec: Int)

    private fun req(path: String, token: String? = null) = Request.Builder().url(API + path)
        .header("Content-Type", "application/json").header("trakt-api-version", "2").header("trakt-api-key", clientId())
        .apply { if (token != null) header("Authorization", "Bearer $token") }

    fun deviceCode(client: OkHttpClient): DeviceCode {
        val body = JSONObject().put("client_id", clientId()).toString().toRequestBody(JSON)
        val o = client.newCall(req("/oauth/device/code").post(body).build()).execute().use { r ->
            if (!r.isSuccessful) error("Trakt answered HTTP ${r.code}. Check the client ID.")
            JSONObject(r.body.string())
        }
        return DeviceCode(o.getString("device_code"), o.getString("user_code"), o.optString("verification_url", "https://trakt.tv/activate"),
            o.optInt("interval", 5), o.optInt("expires_in", 600))
    }

    /** Polls until the user approves [code] on trakt.tv/activate. */
    suspend fun awaitToken(context: Context, client: OkHttpClient, code: DeviceCode): String {
        val until = System.currentTimeMillis() + code.expiresSec * 1000L
        var wait = code.intervalSec.coerceAtLeast(1) * 1000L
        while (System.currentTimeMillis() < until) {
            delay(wait)
            val body = JSONObject().put("code", code.deviceCode).put("client_id", clientId()).put("client_secret", clientSecret()).toString().toRequestBody(JSON)
            val (status, text) = client.newCall(req("/oauth/device/token").post(body).build()).execute().use { it.code to it.body.string() }
            when (status) {
                200 -> {
                    val o = JSONObject(text)
                    val access = o.getString("access_token")
                    OnlineSettings.setSecret(context, "trakt_token", access)
                    OnlineSettings.setSecret(context, "trakt_refresh", o.optString("refresh_token"))
                    val name = runCatching {
                        client.newCall(req("/users/settings", access).build()).execute().use { r -> JSONObject(r.body.string()).getJSONObject("user").optString("username") }
                    }.getOrNull()
                    OnlineSettings.setSecret(context, "trakt_user", name?.ifBlank { null } ?: "Trakt")
                    return user().orEmpty()
                }
                400 -> Unit // still pending
                429 -> wait += 1000
                404 -> error("Invalid device code")
                409 -> error("This code was already used")
                410 -> error("The code expired. Try again.")
                418 -> error("You denied access on Trakt")
                else -> error("Trakt answered HTTP $status")
            }
        }
        error("The code expired. Try again.")
    }

    fun signOut(context: Context) = listOf("trakt_token", "trakt_refresh", "trakt_user").forEach { OnlineSettings.setSecret(context, it, null) }

    /** Trakt id for a title; null when no confident match. */
    internal fun findId(client: OkHttpClient, type: String, title: String, year: Int?): Long? {
        val q = URLEncoder.encode(title, "UTF-8")
        val path = "/search/$type?query=$q&limit=5" + (year?.let { "&years=$it" } ?: "")
        val arr = client.newCall(req(path).build()).execute().use { r -> if (!r.isSuccessful) return null; JSONArray(r.body.string()) }
        return pickId(arr, type, title)
    }

    internal fun pickId(arr: JSONArray, type: String, title: String): Long? {
        val norm = { s: String -> s.lowercase().filter { it.isLetterOrDigit() } }
        var fallback: Long? = null
        for (i in 0 until arr.length()) {
            val m = arr.getJSONObject(i).optJSONObject(type) ?: continue
            val id = m.optJSONObject("ids")?.optLong("trakt")?.takeIf { it > 0 } ?: continue
            if (norm(m.optString("title")) == norm(title)) return id
            if (fallback == null && i == 0) fallback = id
        }
        return fallback
    }

    /** Adds a watched movie or episode to the user's Trakt history. */
    fun watched(client: OkHttpClient, title: String, year: Int?, season: Int?, episode: Int?): Boolean {
        val t = token() ?: return false
        val isShow = episode != null
        val id = findId(client, if (isShow) "show" else "movie", title, year) ?: return false
        val body = if (isShow) JSONObject().put("shows", JSONArray().put(JSONObject().put("ids", JSONObject().put("trakt", id))
            .put("seasons", JSONArray().put(JSONObject().put("number", season ?: 1).put("episodes", JSONArray().put(JSONObject().put("number", episode)))))))
        else JSONObject().put("movies", JSONArray().put(JSONObject().put("ids", JSONObject().put("trakt", id))))
        return client.newCall(req("/sync/history", t).post(body.toString().toRequestBody(JSON)).build()).execute().use { it.isSuccessful }
    }
}
