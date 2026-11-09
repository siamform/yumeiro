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
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Simkl (movies, shows and anime in one list) with the AUTH V2 device flow (RFC 8628): the app shows
 * an 8-character code, the user approves it on simkl.com/pin, the app polls. Only a Client ID is
 * needed (no secret, no redirect). Access tokens last 7 days; refresh tokens 180 days and are
 * non-rotating, so a refresh only replaces the access token.
 *
 * Every request carries `client_id`, `app-name` and `app-version` (required by Simkl) and a
 * descriptive User-Agent. History is matched by Simkl itself from any ids we have (MAL, AniList,
 * IMDb, TMDB) plus title + year, so there is no separate search call.
 */
object Simkl {
    private const val API = "https://api.simkl.com"
    private const val APP_NAME = "yumeiro"
    private val JSON = "application/json".toMediaType()
    private const val REFRESH_AHEAD_MS = 24L * 60 * 60 * 1000
    const val SCOPE = "media:read media:write"

    private fun userClientId(): String = OnlineSettings.secret("simkl_client").orEmpty()
    fun clientId(): String = userClientId().ifBlank { BuildConfig.SIMKL_CLIENT_ID }
    val configured: Boolean get() = clientId().isNotBlank()
    fun token(): String? = OnlineSettings.secret("simkl_token")
    fun user(): String? = OnlineSettings.secret("simkl_user")
    val signedIn: Boolean get() = token() != null

    data class DeviceCode(val deviceCode: String, val userCode: String, val verificationUrl: String, val intervalSec: Int, val expiresSec: Int) {
        override fun toString() = "DeviceCode(userCode=$userCode)" // never log the polling credential
    }

    internal fun userAgent() = "Yumeiro/${BuildConfig.VERSION_NAME}"

    /** Adds the query parameters Simkl requires on every call. */
    internal fun url(path: String, clientId: String = clientId(), version: String = BuildConfig.VERSION_NAME): String =
        (API + path).toHttpUrl().newBuilder()
            .addQueryParameter("client_id", clientId).addQueryParameter("app-name", APP_NAME).addQueryParameter("app-version", version)
            .build().toString()

    private fun req(path: String, token: String? = null) = Request.Builder().url(url(path)).header("User-Agent", userAgent())
        .header("Accept", "application/json").apply { if (token != null) header("Authorization", "Bearer $token") }

    fun deviceCode(client: OkHttpClient): DeviceCode {
        val body = FormBody.Builder().add("client_id", clientId()).add("scope", SCOPE).build()
        val o = client.newCall(req("/oauth2/device").post(body).build()).execute().use { r ->
            if (r.code == 401) error("Simkl doesn't know this Client ID. Create an app for \"TV, devices & command line\" on simkl.com/settings/developer.")
            if (!r.isSuccessful) error("Simkl answered HTTP ${r.code}")
            JSONObject(r.body.string())
        }
        return DeviceCode(
            o.getString("device_code"), o.getString("user_code"),
            o.optString("verification_uri_complete").ifBlank { o.optString("verification_uri", "https://simkl.com/pin") },
            o.optInt("interval", 5), o.optInt("expires_in", 900),
        )
    }

    /** What to do with one poll answer. */
    internal sealed interface Poll {
        data object Pending : Poll
        data object SlowDown : Poll
        data class Done(val tokens: JSONObject) : Poll
        data class Fail(val message: String) : Poll
    }

    internal fun classify(status: Int, text: String): Poll {
        val o = runCatching { JSONObject(text) }.getOrNull()
        if (status == 200 && o?.has("access_token") == true) return Poll.Done(o)
        return when (o?.optString("error")) {
            "authorization_pending" -> Poll.Pending
            "slow_down" -> Poll.SlowDown
            "expired_token" -> Poll.Fail("The code expired. Try again.")
            "invalid_client" -> Poll.Fail("Simkl rejected the Client ID (it must be an AUTH V2 app).")
            else -> Poll.Fail("Simkl answered HTTP $status")
        }
    }

    /** True when the token response can write history (an unknown scope is silently downgraded to read-only). */
    internal fun canWrite(tokens: JSONObject): Boolean = tokens.optString("scope").let { it.isBlank() || "media:write" in it }

    /** Polls until the user approves [code]. A declined code just stays pending, so the deadline ends the loop. */
    suspend fun awaitToken(context: Context, client: OkHttpClient, code: DeviceCode): String {
        val until = System.currentTimeMillis() + code.expiresSec * 1000L
        var wait = code.intervalSec.coerceAtLeast(5) * 1000L
        while (System.currentTimeMillis() < until) {
            delay(wait)
            val body = FormBody.Builder().add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                .add("client_id", clientId()).add("device_code", code.deviceCode).build()
            val (status, text) = client.newCall(req("/oauth2/token").post(body).build()).execute().use { it.code to it.body.string() }
            when (val p = classify(status, text)) {
                Poll.Pending -> Unit
                Poll.SlowDown -> wait += 5000 // the timer restarts with every attempt: really wait longer
                is Poll.Fail -> error(p.message)
                is Poll.Done -> {
                    if (!canWrite(p.tokens)) error("Simkl gave read-only access. Allow \"media:write\" and try again.")
                    saveTokens(context, p.tokens)
                    val name = runCatching {
                        client.newCall(req("/users/settings", token()).post("{}".toRequestBody(JSON)).build()).execute().use { r ->
                            JSONObject(r.body.string()).optJSONObject("user")?.optString("name")
                        }
                    }.getOrNull()
                    OnlineSettings.setSecret(context, "simkl_user", name?.ifBlank { null } ?: "Simkl")
                    return user().orEmpty()
                }
            }
        }
        error("The code expired. Try again.")
    }

    private fun saveTokens(context: Context, o: JSONObject) {
        OnlineSettings.setSecret(context, "simkl_token", o.getString("access_token"))
        o.optString("refresh_token").takeIf { it.isNotBlank() }?.let { OnlineSettings.setSecret(context, "simkl_refresh", it) }
        val expires = o.optLong("expires_in", 7L * 24 * 60 * 60)
        OnlineSettings.setSecret(context, "simkl_expires", (System.currentTimeMillis() + expires * 1000).toString())
    }

    internal fun needsRefresh(expiresAtMs: Long?, nowMs: Long): Boolean = expiresAtMs != null && expiresAtMs - nowMs < REFRESH_AHEAD_MS

    private val refreshLock = Any()

    /** The access token, refreshed when it expires within a day. A rejected refresh signs out. */
    fun validToken(context: Context, client: OkHttpClient, force: Boolean = false): String? = synchronized(refreshLock) {
        val current = token() ?: return null
        val expiresAt = OnlineSettings.secret("simkl_expires")?.toLongOrNull()
        if (!force && !needsRefresh(expiresAt, System.currentTimeMillis())) return current
        val refresh = OnlineSettings.secret("simkl_refresh") ?: return current
        val body = FormBody.Builder().add("grant_type", "refresh_token").add("client_id", clientId()).add("refresh_token", refresh).build()
        val (status, text) = runCatching {
            client.newCall(req("/oauth2/token").post(body).build()).execute().use { it.code to it.body.string() }
        }.getOrElse { return current }
        when (status) {
            200 -> { saveTokens(context, JSONObject(text)); token() }
            400, 401, 403 -> { signOut(context); null }
            else -> current
        }
    }

    fun signOut(context: Context) = listOf("simkl_token", "simkl_refresh", "simkl_expires", "simkl_user").forEach { OnlineSettings.setSecret(context, it, null) }

    /** Ids and names Simkl can match on. Any subset works; Simkl uses the first that resolves. */
    data class Match(val title: String, val year: Int?, val mal: Int? = null, val anilist: Int? = null, val imdb: String? = null, val tmdb: Int? = null)

    internal fun historyBody(m: Match, season: Int?, episode: Int?): JSONObject {
        val ids = JSONObject().apply {
            m.mal?.let { put("mal", it) }; m.anilist?.let { put("anilist", it) }
            m.imdb?.takeIf { it.startsWith("tt") }?.let { put("imdb", it) }; m.tmdb?.let { put("tmdb", it) }
        }
        val item = JSONObject().put("title", m.title).apply { m.year?.let { put("year", it) }; if (ids.length() > 0) put("ids", ids) }
        return if (episode == null) JSONObject().put("movies", JSONArray().put(item))
        else JSONObject().put("shows", JSONArray().put(item.put("seasons", JSONArray().put(
            JSONObject().put("number", season ?: 1).put("episodes", JSONArray().put(JSONObject().put("number", episode)))))))
    }

    /** True when Simkl reports at least one added item. */
    internal fun added(text: String): Boolean = runCatching {
        val a = JSONObject(text).optJSONObject("added") ?: return false
        a.optInt("movies") + a.optInt("shows") + a.optInt("episodes") > 0
    }.getOrDefault(false)

    /** Marks a movie (episode == null) or an episode watched. */
    fun watched(context: Context, client: OkHttpClient, match: Match, season: Int?, episode: Int?): Boolean {
        val t = validToken(context, client) ?: return false
        val body = historyBody(match, season, episode).toString()
        val post = { tok: String -> client.newCall(req("/sync/history", tok).post(body.toRequestBody(JSON)).build()).execute().use { it.code to it.body.string() } }
        var (code, text) = post(t)
        if (code == 401) {
            val fresh = validToken(context, client, force = true) ?: return false
            post(fresh).also { code = it.first; text = it.second }
        }
        return code in 200..299 && added(text)
    }
}
