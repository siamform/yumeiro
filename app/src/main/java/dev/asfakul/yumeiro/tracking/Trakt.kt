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
 * on TVs). A free Trakt API app is required; its Client ID comes from the build or is pasted by the
 * user in Settings → Tracking.
 *
 * Since 2026-10-01 Trakt issues no client secret to new apps (PKCE-only, trakt/trakt-api#974) and the
 * secret is optional on every /oauth call, so only the Client ID is required. A secret from an older
 * app is still sent. Access tokens last 7 days and refresh tokens are single-use: [validToken]
 * refreshes ahead of expiry and always stores the new pair.
 */
object Trakt {
    private const val API = "https://api.trakt.tv"
    private val JSON = "application/json".toMediaType()

    /** Must match the redirect URI registered on the Trakt app (sent on token refresh). */
    const val REDIRECT = "urn:ietf:wg:oauth:2.0:oob"
    private const val REFRESH_AHEAD_MS = 24L * 60 * 60 * 1000

    private fun userClientId(): String = OnlineSettings.secret("trakt_client").orEmpty()
    fun clientId(): String = userClientId().ifBlank { BuildConfig.TRAKT_CLIENT_ID }
    /**
     * Optional (only pre-October-2026 Trakt apps have one). Always paired with the Client ID it
     * belongs to: a user's own app never borrows the built-in secret.
     */
    fun clientSecret(): String =
        if (userClientId().isNotBlank()) OnlineSettings.secret("trakt_secret").orEmpty() else BuildConfig.TRAKT_CLIENT_SECRET
    val configured: Boolean get() = clientId().isNotBlank()
    fun token(): String? = OnlineSettings.secret("trakt_token")
    fun user(): String? = OnlineSettings.secret("trakt_user")
    val signedIn: Boolean get() = token() != null

    data class DeviceCode(val deviceCode: String, val userCode: String, val verificationUrl: String, val intervalSec: Int, val expiresSec: Int)

    private fun req(path: String, token: String? = null) = Request.Builder().url(API + path)
        .header("Content-Type", "application/json").header("trakt-api-version", "2").header("trakt-api-key", clientId())
        .apply { if (token != null) header("Authorization", "Bearer $token") }

    /** Adds `client_secret` only when this app has one; new Trakt apps don't. */
    internal fun withSecret(o: JSONObject, secret: String): JSONObject = o.apply { if (secret.isNotBlank()) put("client_secret", secret) }

    internal fun refreshBody(clientId: String, secret: String, refreshToken: String): JSONObject = withSecret(
        JSONObject().put("refresh_token", refreshToken).put("client_id", clientId)
            .put("redirect_uri", REDIRECT).put("grant_type", "refresh_token"),
        secret,
    )

    /** True when a known expiry is within a day. Older sign-ins without one are refreshed by the 401 retry in [watched]. */
    internal fun needsRefresh(expiresAtMs: Long?, nowMs: Long): Boolean = expiresAtMs != null && expiresAtMs - nowMs < REFRESH_AHEAD_MS

    private fun saveTokens(context: Context, o: JSONObject) {
        OnlineSettings.setSecret(context, "trakt_token", o.getString("access_token"))
        OnlineSettings.setSecret(context, "trakt_refresh", o.optString("refresh_token").ifBlank { null })
        val created = o.optLong("created_at", System.currentTimeMillis() / 1000)
        val expires = o.optLong("expires_in", 7L * 24 * 60 * 60)
        OnlineSettings.setSecret(context, "trakt_expires", ((created + expires) * 1000).toString())
    }

    private val refreshLock = Any()

    /**
     * The access token, refreshed first when it expires within a day. Refresh tokens are single-use,
     * so refreshes are serialized and the new pair is stored at once. A rejected refresh signs out
     * (the user signs in again); a network error keeps the current token.
     */
    fun validToken(context: Context, client: OkHttpClient, force: Boolean = false): String? = synchronized(refreshLock) {
        val current = token() ?: return null
        val expiresAt = OnlineSettings.secret("trakt_expires")?.toLongOrNull()
        if (!force && !needsRefresh(expiresAt, System.currentTimeMillis())) return current
        val refresh = OnlineSettings.secret("trakt_refresh") ?: return current
        val body = refreshBody(clientId(), clientSecret(), refresh).toString().toRequestBody(JSON)
        val (status, text) = runCatching {
            client.newCall(req("/oauth/token").post(body).build()).execute().use { it.code to it.body.string() }
        }.getOrElse { return current }
        when (status) {
            200 -> { saveTokens(context, JSONObject(text)); token() }
            400, 401, 403 -> { signOut(context); null }
            else -> current
        }
    }

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
            val body = withSecret(JSONObject().put("code", code.deviceCode).put("client_id", clientId()), clientSecret()).toString().toRequestBody(JSON)
            val (status, text) = client.newCall(req("/oauth/device/token").post(body).build()).execute().use { it.code to it.body.string() }
            when (status) {
                200 -> {
                    val o = JSONObject(text)
                    val access = o.getString("access_token")
                    saveTokens(context, o)
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

    fun signOut(context: Context) = listOf("trakt_token", "trakt_refresh", "trakt_expires", "trakt_user").forEach { OnlineSettings.setSecret(context, it, null) }

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
    fun watched(context: Context, client: OkHttpClient, title: String, year: Int?, season: Int?, episode: Int?): Boolean {
        val t = validToken(context, client) ?: return false
        val isShow = episode != null
        val id = findId(client, if (isShow) "show" else "movie", title, year) ?: return false
        val body = if (isShow) JSONObject().put("shows", JSONArray().put(JSONObject().put("ids", JSONObject().put("trakt", id))
            .put("seasons", JSONArray().put(JSONObject().put("number", season ?: 1).put("episodes", JSONArray().put(JSONObject().put("number", episode)))))))
        else JSONObject().put("movies", JSONArray().put(JSONObject().put("ids", JSONObject().put("trakt", id))))
        val post = { tok: String -> client.newCall(req("/sync/history", tok).post(body.toString().toRequestBody(JSON)).build()).execute().use { it.code } }
        val code = post(t)
        if (code != 401) return code in 200..299
        // Token revoked or expired early (e.g. signed in before expiry was tracked): refresh once and retry.
        val fresh = validToken(context, client, force = true) ?: return false
        return post(fresh) in 200..299
    }
}
