/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.anime

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Intro / outro / recap times for anime episodes from the community AniSkip database
 * (api.aniskip.com, keyed by MyAnimeList id + episode number).
 */
object AniSkip {
    enum class Kind(val label: String) { INTRO("Skip intro"), OUTRO("Skip outro"), RECAP("Skip recap"), MIXED_INTRO("Skip intro"), MIXED_OUTRO("Skip outro") }

    data class Segment(val kind: Kind, val startMs: Long, val endMs: Long) {
        fun contains(posMs: Long) = posMs in startMs until (endMs - 500)
    }

    private val cache = ConcurrentHashMap<String, List<Segment>>()

    fun segments(client: OkHttpClient, malId: Int, episode: Int, durationMs: Long = 0): List<Segment> {
        val key = "$malId:$episode"
        cache[key]?.let { return it }
        val len = if (durationMs > 0) durationMs / 1000 else 0
        val url = "https://api.aniskip.com/v2/skip-times/$malId/$episode?types[]=op&types[]=ed&types[]=mixed-op&types[]=mixed-ed&types[]=recap&episodeLength=$len"
        val list = runCatching {
            client.newCall(Request.Builder().url(url).header("Accept", "application/json").build()).execute().use { r ->
                // 404 = no data for this episode (normal).
                if (!r.isSuccessful) emptyList() else parse(r.body.string())
            }
        }.getOrDefault(emptyList())
        cache[key] = list
        return list
    }

    internal fun parse(text: String): List<Segment> {
        val o = JSONObject(text)
        if (!o.optBoolean("found")) return emptyList()
        val a = o.optJSONArray("results") ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val r = a.optJSONObject(i) ?: return@mapNotNull null
            val iv = r.optJSONObject("interval") ?: return@mapNotNull null
            val kind = when (r.optString("skipType")) {
                "op" -> Kind.INTRO; "ed" -> Kind.OUTRO; "recap" -> Kind.RECAP
                "mixed-op" -> Kind.MIXED_INTRO; "mixed-ed" -> Kind.MIXED_OUTRO
                else -> return@mapNotNull null
            }
            val s = (iv.optDouble("startTime") * 1000).toLong(); val e = (iv.optDouble("endTime") * 1000).toLong()
            if (e > s) Segment(kind, s, e) else null
        }.sortedBy { it.startMs }
    }
}
