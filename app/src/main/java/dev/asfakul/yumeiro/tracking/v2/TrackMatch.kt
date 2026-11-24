/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.tracking.v2

import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject

/**
 * TRACK-2: finding the tracker entry for a title. V1 (AnimeIds.search) takes AniList's first search
 * hit, which for a common word or a sequel is often the wrong show, and then writes progress to it.
 * V2 scores every hit against every title AniList knows for it (romaji, English, native, synonyms),
 * prefers the right year and format, and writes nothing when no hit is close enough: a missed
 * update is fixable, a wrong one silently changes somebody else's list entry.
 */
object TrackMatch {
    const val MIN_SCORE = 0.72

    data class Candidate(val aniListId: Int, val malId: Int?, val titles: List<String>, val year: Int?, val format: String?, val total: Int?)
    data class Match(val aniListId: Int?, val malId: Int?, val total: Int?, val score: Double)

    /** Lower case letters and digits only, words split by single spaces. Pure. */
    fun normalize(s: String): String {
        val b = StringBuilder(s.length)
        var space = false
        for (ch in s.lowercase()) {
            if (ch.isLetterOrDigit()) { b.append(ch); space = false }
            else if (!space && b.isNotEmpty()) { b.append(' '); space = true }
        }
        return b.toString().trim()
    }

    /** Extensions add "(Dub)", "[1080p]", "Season 2 (Sub)": cut bracketed tails. Pure. */
    fun clean(title: String): String {
        val b = StringBuilder()
        var depth = 0
        for (ch in title) {
            when (ch) { '(', '[' -> depth++; ')', ']' -> if (depth > 0) depth-- else b.append(ch); else -> if (depth == 0) b.append(ch) }
        }
        return b.toString().trim().ifBlank { title.trim() }
    }

    /** 0..1: 1 for the same words, then shared-word overlap (Dice on words), with a small prefix bonus. Pure. */
    fun similarity(a: String, b: String): Double {
        val x = normalize(a); val y = normalize(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        if (x == y) return 1.0
        val wx = x.split(' ').toSet(); val wy = y.split(' ').toSet()
        val dice = 2.0 * wx.intersect(wy).size / (wx.size + wy.size)
        val prefix = if (x.startsWith(y) || y.startsWith(x)) 0.08 else 0.0
        return (dice + prefix).coerceAtMost(0.99)
    }

    /**
     * The best candidate for [title], or null when none reaches [MIN_SCORE]. A year that matches
     * adds a little and one that is two or more years off takes a little, so "Hunter x Hunter"
     * (2011) wins over (1999) when the source says 2011. Pure.
     */
    fun pick(title: String, year: Int?, candidates: List<Candidate>): Match? {
        val t = clean(title)
        return candidates.map { c ->
            var s = c.titles.maxOfOrNull { similarity(t, it) } ?: 0.0
            if (year != null && c.year != null) s += if (c.year == year) 0.05 else if (kotlin.math.abs(c.year - year) >= 2) -0.08 else 0.0
            c to s
        }.filter { it.second >= MIN_SCORE }.maxByOrNull { it.second }?.let { (c, s) -> Match(c.aniListId, c.malId, c.total, s) }
    }

    /** Parses an AniList Page.media answer. Pure. */
    fun candidates(media: JSONArray?): List<Candidate> = (0 until (media?.length() ?: 0)).mapNotNull { i ->
        val m = media!!.optJSONObject(i) ?: return@mapNotNull null
        val id = m.optInt("id").takeIf { it > 0 } ?: return@mapNotNull null
        val t = m.optJSONObject("title")
        val titles = listOfNotNull(t?.optString("romaji"), t?.optString("english"), t?.optString("native")).filter { it.isNotBlank() && it != "null" } +
            (m.optJSONArray("synonyms")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty())
        val total = (m.optInt("episodes").takeIf { it > 0 } ?: m.optInt("chapters").takeIf { it > 0 })
        Candidate(id, m.optInt("idMal").takeIf { it > 0 }, titles, m.optJSONObject("startDate")?.optInt("year")?.takeIf { it > 0 }, m.optString("format").ifBlank { null }, total)
    }

    /**
     * AniList search for [kind]. Novels are AniList MANGA entries with the NOVEL format; manga
     * excludes it. Network call; throws on failure (the queue retries), null = no close match.
     */
    fun search(client: OkHttpClient, kind: TrackKind, title: String, year: Int?): Match? {
        val type = if (kind.reading) "MANGA" else "ANIME"
        val format = when (kind) { TrackKind.NOVEL -> ",format_in:[NOVEL]"; TrackKind.MANGA -> ",format_not_in:[NOVEL]"; else -> "" }
        val q = "query(\$s:String){Page(perPage:8){media(search:\$s,type:$type$format){id idMal format episodes chapters startDate{year} title{romaji english native} synonyms}}}"
        val data = dev.asfakul.yumeiro.online.anime.AniList.graphqlOrThrow(client, q, JSONObject().put("s", clean(title)), null)
        return pick(title, year, candidates(data?.optJSONObject("Page")?.optJSONArray("media")))
    }
}
