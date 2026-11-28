/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.subtitles

import dev.asfakul.yumeiro.online.OnlineSettings
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

// Precompiled once (never during composition, scrolling or per call): see docs/STABILITY-R15.md.
private val RX_SUBDL_1 = Regex("""[<>{}\[\]'"`´’;\\/]""")
private val RX_SUBDL_2 = Regex("\\s+")

/**
 * SubDL (subdl.com) — free subtitle API. Needs the user's own free API key (subdl.com → sign in →
 * API key in the account panel); free keys allow about 2,000 searches a day. Docs: subdl.com/api-doc.
 * The key rides in the query string, so URLs are never logged or shown in errors.
 */
object SubDL {
    const val NAME = "SubDL"
    private const val BASE_DEFAULT = "https://api.subdl.com/api/v1"
    @Volatile internal var baseForTest: String? = null
    private val BASE: String get() = baseForTest ?: BASE_DEFAULT
    private val API: String get() = "$BASE/subtitles"
    /** B19: where the user gets the free key (checked 2026-11-28: subdl.com/panel/api is the API page). */
    const val KEY_PAGE = "https://subdl.com/panel/api"
    const val REGISTER_PAGE = "https://subdl.com/panel/register"
    private const val DL = "https://dl.subdl.com"
    private const val MAX_ZIP = 12L * 1024 * 1024

    fun available(): Boolean = OnlineSettings.subdlKey.value.isNotBlank()

    /** The account name shown on the Tracking page once a key is linked (null: not linked yet or unnamed). */
    fun account(): String? = OnlineSettings.secret("subdl_user")

    /** B19: the answer to "is this key good?", read from SubDL itself; never guessed. */
    sealed interface KeyCheck {
        data class Valid(val account: String?) : KeyCheck
        data object Rejected : KeyCheck
        data class Unreachable(val reason: String) : KeyCheck
    }

    /**
     * Checks [key] with SubDL's documented account endpoint (/api/v1/me). Old keys or a missing endpoint
     * (404) fall back to one real search (The Matrix, tt0133093): a key that searches is a key that works.
     * The key rides in the query string, so URLs are never logged or shown.
     */
    fun checkKey(client: OkHttpClient, key: String): KeyCheck {
        val k = key.trim()
        if (k.length < 8 || k.any { it.isWhitespace() }) return KeyCheck.Rejected
        fun get(url: okhttp3.HttpUrl): Pair<Int, String> =
            client.newCall(Request.Builder().url(url).header("Accept", "application/json").build()).execute().use { it.code to it.body.string() }
        return try {
            val me = get("$BASE/me".toHttpUrl().newBuilder().addQueryParameter("api_key", k).build())
            when {
                me.first == 401 || me.first == 403 -> KeyCheck.Rejected
                me.first == 429 -> KeyCheck.Unreachable("SubDL daily limit reached, try again later")
                me.first in 200..299 && JSONObject(me.second).optBoolean("status", true) -> KeyCheck.Valid(parseAccount(me.second))
                me.first in 200..299 -> KeyCheck.Rejected
                me.first == 404 -> {
                    val s = get(API.toHttpUrl().newBuilder().addQueryParameter("api_key", k).addQueryParameter("imdb_id", "tt0133093")
                        .addQueryParameter("subs_per_page", "1").build())
                    when {
                        s.first == 401 || s.first == 403 -> KeyCheck.Rejected
                        s.first in 200..299 && runCatching { JSONObject(s.second).optBoolean("status", false) }.getOrDefault(false) -> KeyCheck.Valid(null)
                        s.first in 200..299 -> KeyCheck.Rejected
                        else -> KeyCheck.Unreachable("SubDL answered HTTP ${s.first}")
                    }
                }
                else -> KeyCheck.Unreachable("SubDL answered HTTP ${me.first}")
            }
        } catch (e: java.io.IOException) {
            KeyCheck.Unreachable("Couldn't reach SubDL. Check the connection")
        } catch (e: org.json.JSONException) {
            KeyCheck.Unreachable("SubDL sent an answer Yumeiro can't read")
        }
    }

    /** A display name from the /me answer: username, else name, else a masked e-mail. */
    internal fun parseAccount(text: String): String? {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val holders = listOfNotNull(root, root.optJSONObject("user"), root.optJSONObject("account"), root.optJSONObject("data"))
        for (key in listOf("username", "user_name", "name")) for (o in holders) {
            o.optString(key).trim().takeIf { it.isNotEmpty() && it != "null" && it.length <= 60 }?.let { return it }
        }
        for (o in holders) o.optString("email").trim().takeIf { "@" in it }?.let { e ->
            val (u, d) = e.split("@", limit = 2)
            return u.take(2) + "•••@" + d
        }
        return null
    }

    /** Checks [key] and, when SubDL accepts it, stores it (encrypted) with the account name. */
    fun link(context: android.content.Context, client: OkHttpClient, key: String): KeyCheck {
        val r = checkKey(client, key)
        if (r is KeyCheck.Valid) {
            OnlineSettings.setSubdlKey(context, key.trim())
            OnlineSettings.setSecret(context, "subdl_user", r.account ?: "SubDL account")
        }
        return r
    }

    fun unlink(context: android.content.Context) {
        OnlineSettings.setSubdlKey(context, "")
        OnlineSettings.setSecret(context, "subdl_user", null)
    }

    /** SubDL refuses titles with these characters; each becomes a space (keeps "Grey s Anatomy" searchable). */
    internal fun sanitizeTitle(title: String): String = title.replace(RX_SUBDL_1, " ").replace(RX_SUBDL_2, " ").trim()

    fun search(client: OkHttpClient, title: String, imdbId: String?, season: Int?, episode: Int?, language: String): List<SubtitleHit> {
        val key = OnlineSettings.subdlKey.value.ifBlank { throw IllegalStateException("Add a free SubDL API key in Settings → Subtitles & intros") }
        val url = API.toHttpUrl().newBuilder().apply {
            addQueryParameter("api_key", key)
            if (!imdbId.isNullOrBlank()) addQueryParameter("imdb_id", imdbId) else addQueryParameter("film_name", sanitizeTitle(title))
            if (season != null || episode != null) addQueryParameter("type", "tv")
            season?.let { addQueryParameter("season_number", it.toString()) }
            episode?.let { addQueryParameter("episode_number", it.toString()) }
            addQueryParameter("languages", SubtitleLanguages.subdl(language))
            addQueryParameter("subs_per_page", "30")
            addQueryParameter("releases", "1")
            addQueryParameter("hi", "1")
        }.build()
        val text = client.newCall(Request.Builder().url(url).header("Accept", "application/json").build()).execute().use { r ->
            if (r.code == 401 || r.code == 403) throw IllegalStateException("SubDL rejected the API key")
            if (r.code == 429) throw IllegalStateException("SubDL daily limit reached, try again later")
            r.body.string()
        }
        return parse(text, language)
    }

    internal fun parse(text: String, language: String): List<SubtitleHit> {
        val o = runCatching { JSONObject(text) }.getOrNull() ?: return emptyList()
        if (o.has("status") && !o.optBoolean("status", true)) return emptyList()
        val arr = o.optJSONArray("subtitles") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val s = arr.optJSONObject(i) ?: return@mapNotNull null
            val path = s.optString("url").takeIf { it.startsWith("/subtitle/") && !it.contains("..") } ?: return@mapNotNull null
            SubtitleHit(
                provider = NAME, id = path,
                language = s.optString("language").ifBlank { s.optString("lang") }.takeIf { it.length in 2..5 }?.let(SubtitleLanguages::normalize)
                    ?: SubtitleLanguages.normalize(language),
                release = s.optString("release_name").ifBlank { s.optString("name") }.ifBlank { "SubDL subtitle" },
                hearingImpaired = s.optBoolean("hi"),
                seasonPack = s.optBoolean("full_season") || (s.optInt("episode_from", 0) > 0 && s.optInt("episode_end", 0) > s.optInt("episode_from", 0)),
                uploader = s.optString("author").ifBlank { null },
            )
        }.distinctBy { it.id }
    }

    /** The ZIP for [hit] (anonymous download from dl.subdl.com). */
    fun download(client: OkHttpClient, hit: SubtitleHit): ByteArray {
        require(hit.provider == NAME && hit.id.startsWith("/subtitle/"))
        return client.newCall(Request.Builder().url(DL + hit.id).build()).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("SubDL download failed (HTTP ${r.code})")
            SubtitleNet.readCapped(r.body.byteStream(), MAX_ZIP)
        }
    }
}
