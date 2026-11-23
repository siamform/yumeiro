/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.anime

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * SKIP-2: intro / recap / end-credit times for TV episodes and movies from the community IntroDB
 * (api.introdb.app, free, no key; keyed by IMDb id + season + episode, or IMDb id alone for a movie).
 * Anime keeps AniSkip, which is keyed by MyAnimeList id.
 */
object IntroDb {
    private val cache = ConcurrentHashMap<String, List<AniSkip.Segment>>()
    private fun imdb(id: String) = id.length in 9..11 && id.startsWith("tt") && id.drop(2).all { it in '0'..'9' }

    /** @return null when IntroDB could not be asked; empty when it has nothing for this episode / movie. */
    fun fetch(client: OkHttpClient, imdbId: String, season: Int?, episode: Int?): List<AniSkip.Segment>? {
        val id = imdbId.trim().lowercase()
        if (!imdb(id)) return emptyList()
        val movie = season == null || episode == null
        val key = if (movie) id else "$id:$season:$episode"
        cache[key]?.let { return it }
        val url = if (movie) "https://api.introdb.app/segments?imdb_id=$id&is_movie=true"
        else "https://api.introdb.app/segments?imdb_id=$id&season=$season&episode=$episode"
        val list: List<AniSkip.Segment>? = runCatching {
            client.newCall(Request.Builder().url(url).header("Accept", "application/json").build()).execute().use { r ->
                when {
                    r.isSuccessful -> parse(r.body.string(), movie)
                    r.code == 404 -> emptyList()
                    else -> null
                }
            }
        }.getOrNull()
        if (list != null) cache[key] = list
        return list
    }

    /**
     * IntroDB gives one aggregated time per segment and no video length. Only times confirmed by two or
     * more people are skipped automatically ([AniSkip.Segment.fits]); a single submission is a button.
     * Movies use end credits only (IntroDB's movie segments are end credits and the post-credit scene;
     * skipping the credits lands on that scene).
     */
    internal fun parse(text: String, movie: Boolean): List<AniSkip.Segment> {
        val o = JSONObject(text)
        fun seg(name: String, kind: AniSkip.Kind): AniSkip.Segment? {
            val s = o.optJSONObject(name) ?: return null
            val start = s.optLong("start_ms", -1).takeIf { it >= 0 } ?: (s.optDouble("start_sec", -1.0) * 1000).toLong()
            val end = s.optLong("end_ms", -1).takeIf { it >= 0 } ?: (s.optDouble("end_sec", -1.0) * 1000).toLong()
            if (start < 0 || end <= start) return null
            return AniSkip.Segment(kind, start, end, 0, fits = s.optInt("submission_count", 0) >= 2)
        }
        val list = if (movie) listOfNotNull(seg("outro", AniSkip.Kind.OUTRO))
        else listOfNotNull(seg("recap", AniSkip.Kind.RECAP), seg("intro", AniSkip.Kind.INTRO), seg("outro", AniSkip.Kind.OUTRO))
        return list.sortedBy { it.startMs }
    }
}
