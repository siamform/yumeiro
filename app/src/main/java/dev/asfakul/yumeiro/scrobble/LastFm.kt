/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.scrobble

import android.content.Context
import dev.asfakul.yumeiro.BuildConfig
import dev.asfakul.yumeiro.online.OnlineSettings
import kotlinx.coroutines.delay
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Last.fm scrobbling with the desktop auth flow (https://www.last.fm/api/desktopauth): get a token,
 * the user approves it on last.fm in the browser, then the app trades it for a session key. The
 * user's password never touches Yumeiro. Session keys don't expire; a revoked one (error 9) signs
 * out. The API key and shared secret are a pair: a user's own key is signed on the phone with the
 * user's own secret; Yumeiro's built-in key is signed by Yumeiro Cloud (SEC-LFM, ADR-048), so its
 * shared secret is not in the APK.
 */
object LastFm {
    private const val API = "https://ws.audioscrobbler.com/2.0/"
    const val MAX_BATCH = 50

    private fun userKey(): String = OnlineSettings.secret("lastfm_key").orEmpty()
    fun apiKey(): String = userKey().ifBlank { BuildConfig.LASTFM_API_KEY }
    private fun userSecret(): String = OnlineSettings.secret("lastfm_secret").orEmpty()
    /** Built-in key: signed by Yumeiro Cloud. Own key: needs its own secret. */
    val configured: Boolean get() = if (userKey().isNotBlank()) userSecret().isNotBlank() else BuildConfig.LASTFM_API_KEY.isNotBlank() && BuildConfig.CLOUD_HOST.isNotBlank()
    fun session(): String? = OnlineSettings.secret("lastfm_session")
    fun user(): String? = OnlineSettings.secret("lastfm_user")
    val signedIn: Boolean get() = session() != null

    private fun userAgent() = "Yumeiro/${BuildConfig.VERSION_NAME} (https://github.com/siamform/yumeiro)"

    /** A Last.fm error with its numeric code (https://www.last.fm/api/errorcodes). */
    class ApiException(val code: Int, message: String) : Exception(message) {
        /** Worth retrying later: service offline / temporarily unavailable / rate limit / operation failed. */
        val transient: Boolean get() = code in setOf(8, 11, 16, 29)
        /** The session was revoked or is invalid: sign out. */
        val badSession: Boolean get() = code == 9
    }

    internal fun signed(params: Map<String, String>, secret: String): Map<String, String> =
        params + ("api_sig" to LastFmSignature.sign(params, secret)) + ("format" to "json")

    /** The signed parameters: on the phone for the user's own key, by Yumeiro Cloud for the built-in one. */
    private fun sign(client: OkHttpClient, params: Map<String, String>): Map<String, String> =
        if (userKey().isNotBlank()) signed(params, userSecret())
        else params + ("api_sig" to cloudSig(client, params, BuildConfig.CLOUD_HOST)) + ("format" to "json")

    /**
     * SEC-LFM: asks Yumeiro Cloud for the signature. Its refusals become transient [ApiException]s (Last.fm's
     * codes 16 / 29), so the scrobble queue keeps the plays and tries again on its next flush, never in a loop.
     */
    internal fun cloudSig(client: OkHttpClient, params: Map<String, String>, host: String): String {
        if (host.isBlank()) throw ApiException(26, "Last.fm needs your own API key in this build (Settings → Tracking)")
        val body = JSONObject().put("params", JSONObject(params)).toString()
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url("https://$host/v1/lastfm/sign").post(body).header("User-Agent", userAgent()).build()
        return client.newCall(req).execute().use { r ->
            val o = runCatching { JSONObject(r.body.string()) }.getOrNull()
            val sig = o?.optString("api_sig").orEmpty()
            when {
                r.isSuccessful && sig.length == 32 -> sig
                r.code == 429 -> throw ApiException(29, "Last.fm is busy. Your plays are kept and sent later")
                r.code == 503 || r.code >= 500 || o == null -> throw ApiException(16, "Yumeiro's Last.fm link isn't available right now. Your plays are kept and sent later; you can also add your own API key in Settings → Tracking")
                // Kept as transient: a refusal is a server set-up problem, never the user's plays (they expire after 14 days).
                else -> throw ApiException(16, "Yumeiro's server refused this Last.fm request (${o.optString("error").ifBlank { "HTTP ${r.code}" }})")
            }
        }
    }

    /** Throws [ApiException] when the JSON is a Last.fm error. */
    internal fun check(text: String): JSONObject {
        val o = runCatching { JSONObject(text) }.getOrElse { throw ApiException(16, "Last.fm sent an unreadable answer") }
        if (o.has("error")) throw ApiException(o.optInt("error"), o.optString("message").ifBlank { "Last.fm error ${o.optInt("error")}" })
        return o
    }

    private fun get(client: OkHttpClient, params: Map<String, String>): JSONObject {
        val url = API.toHttpUrl().newBuilder().apply { sign(client, params).forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        return client.newCall(Request.Builder().url(url).header("User-Agent", userAgent()).build()).execute().use { check(it.body.string()) }
    }

    private fun post(client: OkHttpClient, params: Map<String, String>): JSONObject {
        val body = FormBody.Builder().apply { sign(client, params).forEach { (k, v) -> add(k, v) } }.build()
        return client.newCall(Request.Builder().url(API).post(body).header("User-Agent", userAgent()).build()).execute().use { check(it.body.string()) }
    }

    /** Step 1: a request token (valid 60 minutes) and the page where the user approves it. */
    fun requestToken(client: OkHttpClient): Pair<String, String> {
        if (!configured) error("Add a Last.fm API key first")
        val token = get(client, mapOf("method" to "auth.getToken", "api_key" to apiKey())).getString("token")
        return token to "https://www.last.fm/api/auth/?api_key=${apiKey()}&token=$token"
    }

    /** Step 2: polls until the user approved [token] on last.fm, then stores the session. */
    suspend fun awaitSession(context: Context, client: OkHttpClient, token: String, timeoutMs: Long = 5 * 60_000L): String {
        val until = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < until) {
            delay(3000)
            try {
                val s = get(client, mapOf("method" to "auth.getSession", "api_key" to apiKey(), "token" to token)).getJSONObject("session")
                OnlineSettings.setSecret(context, "lastfm_session", s.getString("key"))
                OnlineSettings.setSecret(context, "lastfm_user", s.optString("name").ifBlank { "Last.fm" })
                return user().orEmpty()
            } catch (e: ApiException) {
                when (e.code) {
                    14 -> Unit // not approved yet
                    15, 4 -> error("The sign-in link expired. Try again.")
                    else -> if (!e.transient) throw e
                }
            }
        }
        error("Sign-in timed out. Try again.")
    }

    fun signOut(context: Context) = listOf("lastfm_session", "lastfm_user").forEach { OnlineSettings.setSecret(context, it, null) }

    internal fun nowPlayingParams(p: ScrobblePlay, apiKey: String, sk: String): Map<String, String> = buildMap {
        put("method", "track.updateNowPlaying"); put("api_key", apiKey); put("sk", sk)
        put("artist", p.artist); put("track", p.track)
        if (p.album.isNotBlank()) put("album", p.album)
        if (p.durationSec > 0) put("duration", p.durationSec.toString())
    }

    internal fun scrobbleParams(batch: List<ScrobblePlay>, apiKey: String, sk: String): Map<String, String> = buildMap {
        put("method", "track.scrobble"); put("api_key", apiKey); put("sk", sk)
        batch.take(MAX_BATCH).forEachIndexed { i, p ->
            put("artist[$i]", p.artist); put("track[$i]", p.track); put("timestamp[$i]", p.startedAtSec.toString())
            if (p.album.isNotBlank()) put("album[$i]", p.album)
            if (p.durationSec > 0) put("duration[$i]", p.durationSec.toString())
        }
    }

    fun nowPlaying(client: OkHttpClient, play: ScrobblePlay) {
        val sk = session() ?: return
        post(client, nowPlayingParams(play, apiKey(), sk))
    }

    /** Sends up to [MAX_BATCH] scrobbles. Throws [ApiException] on a Last.fm error. */
    fun scrobble(client: OkHttpClient, batch: List<ScrobblePlay>) {
        val sk = session() ?: return
        if (batch.isEmpty()) return
        post(client, scrobbleParams(batch, apiKey(), sk))
    }
}
