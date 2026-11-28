/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.tracking

import android.content.Context
import dev.asfakul.yumeiro.BuildConfig
import dev.asfakul.yumeiro.online.OnlineSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
 *
 * B18 (Trakt "not working properly"), checked against developer.trakt.tv on 2026-11-28:
 * - Every OAuth route (device code, device token, refresh, revoke) now goes to auth.trakt.tv; only the
 *   data API stays on api.trakt.tv. Before, sign-in and refresh used api.trakt.tv/oauth.
 * - The redirect URI on a refresh must match the app's own exactly, and new Trakt apps can't register
 *   the out-of-band URI. A refresh rejected with it is retried once without it before signing out
 *   (before, the first rejected refresh signed the user out after about six days).
 * - History is sent with the TMDB / IMDb ids the source already knows. Before, every play was matched
 *   by a title search, so remakes and same-name shows could land on the wrong title.
 * - The /sync/history answer is read: a title Trakt doesn't know is reported as "not found" instead of
 *   being logged as sent.
 */
object Trakt {
    private const val API_DEFAULT = "https://api.trakt.tv"
    private const val AUTH_DEFAULT = "https://auth.trakt.tv"
    @Volatile internal var apiForTest: String? = null
    @Volatile internal var authForTest: String? = null
    private val API: String get() = apiForTest ?: API_DEFAULT
    private val AUTH: String get() = authForTest ?: AUTH_DEFAULT
    private val JSON = "application/json".toMediaType()

    /**
     * Redirect URI sent on a token refresh; it must match the Trakt app's own. Apps made before
     * November 2026 could register the out-of-band URI; Trakt's form now refuses it and accepts any
     * http://localhost address, which the device sign-in never opens. [REDIRECT_LOCAL] is what the
     * setup guide asks for, so a refresh tries both, then none.
     */
    const val REDIRECT = "urn:ietf:wg:oauth:2.0:oob"
    const val REDIRECT_LOCAL = "http://localhost"
    private const val REFRESH_AHEAD_MS = 24L * 60 * 60 * 1000

    private fun userClientId(): String = OnlineSettings.secret("trakt_client").orEmpty()
    fun clientId(): String = userClientId().ifBlank { BuildConfig.TRAKT_CLIENT_ID }
    /**
     * Optional (only pre-October-2026 Trakt apps have one). Always paired with the Client ID it
     * belongs to: a user's own app never borrows the built-in secret.
     */
    fun clientSecret(): String =
        if (userClientId().isNotBlank()) OnlineSettings.secret("trakt_secret").orEmpty() else "" // ADR-013: no built-in secret
    val configured: Boolean get() = clientId().isNotBlank()
    fun token(): String? = OnlineSettings.secret("trakt_token")
    fun user(): String? = OnlineSettings.secret("trakt_user")
    val signedIn: Boolean get() = token() != null

    data class DeviceCode(val deviceCode: String, val userCode: String, val verificationUrl: String, val intervalSec: Int, val expiresSec: Int)

    /** A data-API call (api.trakt.tv); OAuth routes go through [auth]. Trakt asks every app to name itself. */
    private fun req(path: String, token: String? = null, base: String = API) = Request.Builder().url(base + path)
        .header("Content-Type", "application/json").header("trakt-api-version", "2").header("trakt-api-key", clientId())
        .header("User-Agent", "Yumeiro/${BuildConfig.VERSION_NAME}")
        .apply { if (token != null) header("Authorization", "Bearer $token") }

    private fun auth(path: String) = req(path, base = AUTH)

    /** Adds `client_secret` only when this app has one; new Trakt apps don't. */
    internal fun withSecret(o: JSONObject, secret: String): JSONObject = o.apply { if (secret.isNotBlank()) put("client_secret", secret) }

    internal fun refreshBody(clientId: String, secret: String, refreshToken: String, redirect: String? = REDIRECT): JSONObject = withSecret(
        JSONObject().put("refresh_token", refreshToken).put("client_id", clientId)
            .apply { if (redirect != null) put("redirect_uri", redirect) }.put("grant_type", "refresh_token"),
        secret,
    )

    /** Redirects tried in order on a refresh; a rejected refresh doesn't use up the refresh token. */
    internal val REFRESH_REDIRECTS: List<String?> = listOf(REDIRECT, REDIRECT_LOCAL, null)

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
        fun call(redirect: String?): Pair<Int, String>? = runCatching {
            val body = refreshBody(clientId(), clientSecret(), refresh, redirect).toString().toRequestBody(JSON)
            client.newCall(auth("/oauth/token").post(body).build()).execute().use { it.code to it.body.string() }
        }.getOrNull()
        var answer: Pair<Int, String> = 0 to ""
        for (r in REFRESH_REDIRECTS) {
            answer = call(r) ?: return current // network error: keep the current token
            if (answer.first !in setOf(400, 401)) break
        }
        val (status, text) = answer
        when (status) {
            200 -> { saveTokens(context, JSONObject(text)); token() }
            400, 401, 403 -> { signOut(context); null }
            else -> current
        }
    }

    fun deviceCode(client: OkHttpClient): DeviceCode {
        val body = JSONObject().put("client_id", clientId()).toString().toRequestBody(JSON)
        val o = client.newCall(auth("/oauth/device/code").post(body).build()).execute().use { r ->
            if (!r.isSuccessful) error("Trakt answered HTTP ${r.code}. Check the client ID.")
            JSONObject(r.body.string())
        }
        return DeviceCode(o.getString("device_code"), o.getString("user_code"), o.optString("verification_url", "https://trakt.tv/activate"),
            o.optInt("interval", 5), o.optInt("expires_in", 600))
    }

    /**
     * Polls until the user approves [code] on trakt.tv/activate.
     * Main-safe (IG-1): the polls are blocking OkHttp calls and the token is written to the encrypted
     * settings, so the whole loop runs on [Dispatchers.IO]. The Tracking card starts it from its
     * screen scope (main thread); before, a release build threw NetworkOnMainThreadException at the
     * first poll and a debug build froze the screen on every poll.
     */
    suspend fun awaitToken(context: Context, client: OkHttpClient, code: DeviceCode): String = withContext(Dispatchers.IO) {
        val until = System.currentTimeMillis() + code.expiresSec * 1000L
        var wait = code.intervalSec.coerceAtLeast(1) * 1000L
        while (System.currentTimeMillis() < until) {
            delay(wait)
            val body = withSecret(JSONObject().put("code", code.deviceCode).put("client_id", clientId()), clientSecret()).toString().toRequestBody(JSON)
            val (status, text) = client.newCall(auth("/oauth/device/token").post(body).build()).execute().use { it.code to it.body.string() }
            when (status) {
                200 -> {
                    val o = JSONObject(text)
                    val access = o.getString("access_token")
                    saveTokens(context, o)
                    val name = runCatching {
                        client.newCall(req("/users/settings", access).build()).execute().use { r -> JSONObject(r.body.string()).getJSONObject("user").optString("username") }
                    }.getOrNull()
                    OnlineSettings.setSecret(context, "trakt_user", name?.ifBlank { null } ?: "Trakt")
                    return@withContext user().orEmpty()
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

    /**
     * Trakt id for a title: the exact title match, else Trakt's first (year-filtered) result; null when
     * Trakt has none. IG-10: a 429/5xx answer throws instead of looking like "no match".
     */
    internal fun findId(client: OkHttpClient, type: String, title: String, year: Int?): Long? {
        val q = URLEncoder.encode(title, "UTF-8")
        val path = "/search/$type?query=$q&limit=5" + (year?.let { "&years=$it" } ?: "")
        val arr = client.newCall(req(path).build()).execute().use { r -> if (!r.isSuccessful) { TrackerHttp.check("Trakt", r.code); return null }; JSONArray(r.body.string()) }
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

    /** What happened to one play. [NOT_FOUND]: Trakt has no such title; [SIGNED_OUT]: sign in again. */
    enum class Sent { ADDED, NOT_FOUND, SIGNED_OUT, REJECTED }

    /** The `ids` object for a title: TMDB and/or IMDb when the source knows them, else the Trakt id. */
    internal fun ids(tmdb: Int?, imdb: String?, trakt: Long?): JSONObject? {
        val o = JSONObject()
        if (tmdb != null && tmdb > 0) o.put("tmdb", tmdb)
        imdb?.trim()?.takeIf { Regex("tt\\d{5,10}").matches(it) }?.let { o.put("imdb", it) }
        if (o.length() == 0 && trakt != null && trakt > 0) o.put("trakt", trakt)
        return o.takeIf { it.length() > 0 }
    }

    internal fun historyBody(ids: JSONObject, season: Int?, episode: Int?): JSONObject =
        if (episode != null) JSONObject().put("shows", JSONArray().put(JSONObject().put("ids", ids)
            .put("seasons", JSONArray().put(JSONObject().put("number", season ?: 1).put("episodes", JSONArray().put(JSONObject().put("number", episode)))))))
        else JSONObject().put("movies", JSONArray().put(JSONObject().put("ids", ids)))

    /** Reads the /sync/history answer: something added (or already there) means it landed. */
    internal fun added(text: String, isShow: Boolean): Boolean = runCatching {
        val o = JSONObject(text)
        val a = o.optJSONObject("added") ?: return@runCatching false
        val e = o.optJSONObject("existing")
        val key = if (isShow) "episodes" else "movies"
        a.optInt(key) > 0 || (e?.optInt(key) ?: 0) > 0
    }.getOrDefault(false)

    /**
     * Adds a watched movie or episode to the user's Trakt history. Uses [tmdb] / [imdb] when known and
     * falls back to a title search only when the source has neither. Throws on 429/5xx (IG-10 retry).
     */
    fun watched(
        context: Context, client: OkHttpClient, title: String, year: Int?, season: Int?, episode: Int?,
        tmdb: Int? = null, imdb: String? = null,
    ): Sent {
        val t = validToken(context, client) ?: return Sent.SIGNED_OUT
        val isShow = episode != null
        val idsObj = ids(tmdb, imdb, null)
            ?: ids(null, null, findId(client, if (isShow) "show" else "movie", title, year))
            ?: return Sent.NOT_FOUND
        val body = historyBody(idsObj, season, episode).toString()
        val post = { tok: String -> client.newCall(req("/sync/history", tok).post(body.toRequestBody(JSON)).build()).execute().use { it.code to it.body.string() } }
        var (code, text) = post(t)
        TrackerHttp.check("Trakt", code) // IG-10
        if (code == 401) {
            // Token revoked or expired early (e.g. signed in before expiry was tracked): refresh once and retry.
            val fresh = validToken(context, client, force = true) ?: return Sent.SIGNED_OUT
            post(fresh).also { code = it.first; text = it.second }
            TrackerHttp.check("Trakt", code)
        }
        return when {
            code == 401 -> Sent.SIGNED_OUT
            code !in 200..299 -> Sent.REJECTED
            added(text, isShow) -> Sent.ADDED
            else -> Sent.NOT_FOUND
        }
    }
}
