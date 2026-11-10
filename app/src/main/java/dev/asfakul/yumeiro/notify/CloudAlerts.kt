/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.notify

import android.content.Context
import android.util.Log
import dev.asfakul.yumeiro.BuildConfig
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.online.OnlineBookmarks
import dev.asfakul.yumeiro.online.OnlineSettings
import dev.asfakul.yumeiro.online.anime.AniList
import dev.asfakul.yumeiro.online.anime.AnimeIds
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Yumeiro Cloud (server/ in this repo): tells our tiny server which AniList shows this device
 * follows and where its UnifiedPush endpoint is. When one of those shows airs, the server sends a
 * content-free nudge to the endpoint and the app runs its usual feed and new-episode checks.
 *
 * Only active with push on. Sends nothing else: no account, no titles, no history. Turning push
 * off just drops the local credentials; the server deletes the row the next time the dead
 * endpoint answers 404/410, or after 45 silent days.
 */
object CloudAlerts {
    private const val TAG = "CloudAlerts"
    private const val PREFS = "cloud_alerts"
    private const val SECRET = "cloud_device" // "<id>.<token>", Keystore-sealed
    private const val PUSH_SECRET = "push_endpoint" // owned by PushWake
    /** New AniList lookups per run (AniList allows ~90 requests/min; stay far below). */
    private const val LOOKUPS_PER_RUN = 10

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Registers / refreshes this device. Blocking network; call from a worker. */
    fun sync(c: Context) {
        val host = BuildConfig.CLOUD_HOST
        if (host.isBlank()) return
        PushWake.init(c)
        val endpoint = OnlineSettings.secret(PUSH_SECRET)
        if (!PushWake.state.value.enabled || endpoint.isNullOrBlank() || !endpoint.startsWith("https://")) { forget(c); return }

        val p = prefs(c)
        // After a failure, wait (6 h → 12 h → … 3 days) so a broken server or a used-up free quota
        // never turns into every device retrying every 2 hours.
        if (System.currentTimeMillis() < p.getLong("retry_at", 0L)) return
        val client = NetworkClient.get(c)
        OnlineBookmarks.ensureLoaded(c)
        val cache = runCatching { JSONObject(p.getString("ids", null) ?: "{}") }.getOrDefault(JSONObject())
        var lookups = 0
        val ids = ArrayList<Int>()
        for (b in CloudAlertsPlan.watched(OnlineBookmarks.items.value)) {
            if (!cache.has(b.key)) {
                if (lookups++ >= LOOKUPS_PER_RUN) continue
                cache.put(b.key, AnimeIds.aniListIdForTitle(client, b.name) ?: 0) // 0 = not anime / not found
            }
            cache.optInt(b.key).takeIf { it > 0 }?.let(ids::add)
        }
        p.edit().putString("ids", cache.toString()).apply()

        val follows = CloudAlertsPlan.follows(ids)
        val fp = CloudAlertsPlan.fingerprint(endpoint, follows)
        val now = System.currentTimeMillis()
        val creds = OnlineSettings.secret(SECRET)
        if (creds != null && !CloudAlertsPlan.due(fp, p.getString("fp", null), now, p.getLong("synced_at", 0L))) return

        // The server can't reach AniList (it blocks Cloudflare Workers), so the phone tells it when the
        // next episodes air. If AniList fails here the server keeps its last times and guesses weekly.
        val airing = JSONArray()
        for (pair in nextAiring(client, follows)) airing.put(JSONArray().put(pair.first).put(pair.second))
        val body = JSONObject().put("endpoint", endpoint).put("anilist", JSONArray(follows)).put("airing", airing).toString()
        val req = Request.Builder().url("https://$host/v1/devices")
            .post(body.toRequestBody("application/json".toMediaType()))
            .apply { if (creds != null) header("Authorization", "Bearer $creds") }
            .build()
        fun failed() {
            val n = p.getInt("failures", 0) + 1
            p.edit().putInt("failures", n).putLong("retry_at", now + CloudAlertsPlan.retryDelay(n)).apply()
        }
        val quick = client.newBuilder().callTimeout(20, java.util.concurrent.TimeUnit.SECONDS).build()
        runCatching {
            quick.newCall(req).execute().use { r ->
                if (!r.isSuccessful) { Log.w(TAG, "register HTTP ${r.code}"); failed(); return }
                val j = JSONObject(r.body.string())
                val id = j.optString("id")
                val token = j.optString("token").takeIf { it.isNotBlank() && it != "null" }
                if (token != null && id.isNotBlank()) OnlineSettings.setSecret(c, SECRET, "$id.$token")
                p.edit().putString("fp", fp).putLong("synced_at", now).remove("failures").remove("retry_at").apply()
            }
        }.onFailure { Log.w(TAG, "register: ${it.javaClass.simpleName}"); failed() }
    }

    /** Next-episode times for [ids] from AniList, 50 per request. Empty on failure. */
    private fun nextAiring(client: okhttp3.OkHttpClient, ids: List<Int>): List<Pair<Int, Long>> {
        val q = "query(\$ids:[Int]){Page(perPage:50){media(id_in:\$ids,type:ANIME){id nextAiringEpisode{airingAt}}}}"
        val found = ArrayList<Pair<Int, Long?>>()
        for (group in ids.chunked(50)) {
            val media = runCatching {
                AniList.graphql(client, q, JSONObject().put("ids", JSONArray(group)), null)?.optJSONObject("Page")?.optJSONArray("media")
            }.getOrNull() ?: break
            for (i in 0 until media.length()) {
                val m = media.optJSONObject(i) ?: continue
                found += m.optInt("id") to m.optJSONObject("nextAiringEpisode")?.optLong("airingAt")
            }
        }
        return CloudAlertsPlan.airing(found, System.currentTimeMillis() / 1000)
    }

    /** Drops local credentials (push turned off). Keeps the AniList id cache. */
    fun forget(c: Context) {
        if (OnlineSettings.secret(SECRET) != null) OnlineSettings.setSecret(c, SECRET, null)
        prefs(c).edit().remove("fp").remove("synced_at").remove("failures").remove("retry_at").apply()
    }
}

/** Pure parts of [CloudAlerts], unit-tested. */
object CloudAlertsPlan {
    const val MAX_FOLLOWS = 300
    /** Re-register at least this often so the server knows the device is alive (it prunes at 45 days). */
    const val REFRESH_MS = 3L * 24 * 3600 * 1000

    /** Same set the new-episode check uses: notify on, still airing for the user, not movies. */
    fun watched(items: List<OnlineBookmarks.Bookmark>): List<OnlineBookmarks.Bookmark> = items.filter {
        it.notify && it.status != OnlineBookmarks.Status.COMPLETED && it.status != OnlineBookmarks.Status.DROPPED &&
            it.type?.contains("movie", true) != true
    }

    fun follows(ids: List<Int>): List<Int> = ids.filter { it > 0 }.distinct().sorted().take(MAX_FOLLOWS)

    fun fingerprint(endpoint: String, follows: List<Int>): String =
        MessageDigest.getInstance("SHA-256").digest("a2|$endpoint|${follows.joinToString(",")}".toByteArray()) // "a2": re-sync once after the airing-times update
            .take(12).joinToString("") { "%02x".format(it) }

    /** Keeps followed shows with a next episode from now to 15 days ahead (the server's accepted range). */
    fun airing(found: List<Pair<Int, Long?>>, nowS: Long): List<Pair<Int, Long>> =
        found.mapNotNull { (id, at) -> if (id > 0 && at != null && at > nowS - 3600 && at <= nowS + 15 * 86_400L) id to at else null }
            .distinctBy { it.first }.sortedBy { it.first }

    /** Back-off after failed registrations: 6 h, 12 h, 24 h, 48 h, then 3 days. */
    fun retryDelay(failures: Int): Long =
        (6L * 3600 * 1000 shl (failures - 1).coerceIn(0, 4)).coerceAtMost(REFRESH_MS)

    fun due(fp: String, oldFp: String?, now: Long, lastSync: Long): Boolean =
        fp != oldFp || now - lastSync >= REFRESH_MS || now < lastSync
}
