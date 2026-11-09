/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.lyrics

import android.content.Context
import android.content.SharedPreferences
import dev.asfakul.yumeiro.BuildConfig
import dev.asfakul.yumeiro.scrobble.TrackNames
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.abs

/**
 * Online lyrics from LRCLIB (https://lrclib.net/docs), a free, open, community-run lyrics
 * database with time-synced LRC. No account; requests carry our User-Agent as LRCLIB asks, plus
 * the app id when one is built in. Only the song's title, artist, album and length are sent.
 * Hits are cached on disk; misses are remembered for a week so the same song isn't asked again.
 */
object LrcLib {
    private const val API = "https://lrclib.net/api/"
    private const val MISS_TTL_MS = 7L * 86_400_000
    private const val MAX_BYTES = 512 * 1024
    private var prefs: SharedPreferences? = null
    private val _enabled = MutableStateFlow(true)
    /** "Find lyrics online" setting. */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("lyrics_online", Context.MODE_PRIVATE).also { prefs = it }
        _enabled.value = p.getBoolean("enabled", true)
    }

    fun setEnabled(context: Context, v: Boolean) { init(context); _enabled.value = v; prefs?.edit()?.putBoolean("enabled", v)?.apply() }

    /** One LRCLIB record, reduced to what we use. */
    data class Hit(val track: String, val artist: String, val album: String, val durationSec: Double, val synced: String?, val plain: String?, val instrumental: Boolean) {
        val text: String? get() = synced?.takeIf { it.isNotBlank() } ?: plain?.takeIf { it.isNotBlank() }
    }

    internal fun parse(o: JSONObject) = Hit(
        o.optString("trackName"), o.optString("artistName"), o.optString("albumName"), o.optDouble("duration", 0.0),
        o.optString("syncedLyrics").takeUnless { o.isNull("syncedLyrics") }, o.optString("plainLyrics").takeUnless { o.isNull("plainLyrics") },
        o.optBoolean("instrumental"),
    )

    /**
     * Picks the best search result: same length within 2 s (LRCLIB's own /get tolerance) wins, then
     * synced over plain, then closest length. Results more than 10 s off are rejected when we know
     * the length, because they're usually a different version (live, remix, radio edit).
     */
    internal fun pick(hits: List<Hit>, durationSec: Int): Hit? {
        val usable = hits.filter { it.text != null || it.instrumental }
        val near = if (durationSec > 0) usable.filter { abs(it.durationSec - durationSec) <= 10 } else usable
        return near.minWithOrNull(
            compareBy<Hit> { if (durationSec > 0 && abs(it.durationSec - durationSec) <= 2) 0 else 1 }
                .thenBy { if (it.synced.isNullOrBlank()) 1 else 0 }
                .thenBy { if (durationSec > 0) abs(it.durationSec - durationSec) else 0.0 },
        )
    }

    internal fun cacheKey(artist: String, title: String, durationSec: Int) =
        "${artist.lowercase()}|${title.lowercase()}|$durationSec".hashCode().toUInt().toString(16)

    private fun cacheFile(context: Context, key: String) = File(File(context.cacheDir, "lyrics_online"), "$key.lrc")

    private fun request(client: OkHttpClient, path: String, params: Map<String, String>): String? {
        val url = (API + path).toHttpUrl().newBuilder().apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        val req = Request.Builder().url(url)
            .header("User-Agent", "Yumeiro v${BuildConfig.VERSION_NAME} (https://github.com/siamform/yumeiro)")
            .apply { if (BuildConfig.LRCLIB_APP_ID.isNotBlank()) header("Lrclib-App-Id", BuildConfig.LRCLIB_APP_ID) }
            .build()
        return client.newCall(req).execute().use { r ->
            when {
                r.code == 404 -> null
                !r.isSuccessful -> error("LRCLIB ${r.code}")
                else -> r.peekBody(MAX_BYTES.toLong()).string()
            }
        }
    }

    /**
     * Finds lyrics for a song. Returns the raw LRC/plain text, or null when LRCLIB has none (or the
     * song is instrumental). Throws on network errors so callers can tell "none" from "offline".
     */
    fun find(context: Context, client: OkHttpClient, rawTitle: String, rawArtist: String, rawAlbum: String, durationMs: Long): String? {
        init(context)
        if (TrackNames.isUnknown(rawArtist) || rawTitle.isBlank()) return null
        val artist = TrackNames.artist(rawArtist)
        val title = TrackNames.title(rawTitle, artist)
        val dur = (durationMs / 1000).toInt()
        val key = cacheKey(artist, title, dur)
        val f = cacheFile(context, key)
        if (f.isFile) return f.readText().ifBlank { null }
        val missAt = prefs?.getLong("miss:$key", 0L) ?: 0L
        if (missAt > 0 && System.currentTimeMillis() - missAt < MISS_TTL_MS) return null

        var hit: Hit? = null
        if (dur > 0) {
            val p = buildMap { put("track_name", title); put("artist_name", artist); if (!TrackNames.isUnknown(rawAlbum)) put("album_name", rawAlbum.trim()); put("duration", dur.toString()) }
            hit = request(client, "get", p)?.let { parse(JSONObject(it)) }?.takeIf { it.text != null || it.instrumental }
        }
        if (hit == null) {
            hit = request(client, "search", mapOf("track_name" to title, "artist_name" to artist))
                ?.let { s -> JSONArray(s).let { a -> List(a.length()) { parse(a.getJSONObject(it)) } } }
                ?.let { pick(it, dur) }
        }
        val text = hit?.takeUnless { it.instrumental }?.text
        if (text == null) {
            prefs?.edit()?.putLong("miss:$key", System.currentTimeMillis())?.apply()
            return null
        }
        runCatching { f.parentFile?.mkdirs(); f.writeText(text) }
        return text
    }
}
