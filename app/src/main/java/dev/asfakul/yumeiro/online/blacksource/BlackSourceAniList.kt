/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.blacksource

import org.json.JSONArray
import org.json.JSONObject

/**
 * B9: the phone asks AniList itself, with the same ONE fixed query and the same checks as the Worker
 * (server/src/blacksource.ts). Reason: AniList refuses requests from Cloudflare Workers ("manually blocked", 403,
 * seen on the beta Worker 2026-11-28), so the Worker is now only the fallback. People choose values, never query
 * text; every answer field is treated as untrusted and the filters are checked again on each title.
 */
object BlackSourceAniList {
    const val URL = "https://graphql.anilist.co"
    private const val PER_PAGE = 30
    private val SORTS = mapOf("popular" to "POPULARITY_DESC", "score" to "SCORE_DESC", "trending" to "TRENDING_DESC", "newest" to "START_DATE_DESC")

    const val QUERY = "query(\$page:Int,\$perPage:Int,\$search:String,\$genres:[String],\$tags:[String],\$sort:[MediaSort]," +
        "\$without:[String],\$formats:[MediaFormat],\$statuses:[MediaStatus],\$after:FuzzyDateInt,\$before:FuzzyDateInt,\$score:Int){" +
        "Page(page:\$page,perPage:\$perPage){pageInfo{hasNextPage}" +
        "media(type:ANIME,isAdult:false,search:\$search,genre_in:\$genres,tag_in:\$tags,sort:\$sort,genre_not_in:\$without," +
        "format_in:\$formats,status_in:\$statuses,startDate_greater:\$after,startDate_lesser:\$before,averageScore_greater:\$score){" +
        "id idMal title{romaji english native} synonyms format status seasonYear startDate{year} averageScore episodes coverImage{large} " +
        "genres tags{name rank category isAdult isMediaSpoiler}}}}"

    private fun tagOk(t: String) = t.length in 1..40 && t[0].isLetterOrDigit() && t[0].code < 128 &&
        t.all { (it.isLetterOrDigit() && it.code < 128) || it in " '&-." }

    /** Pure → unit tested. The request body, or null when there is nothing to search for. */
    fun body(filter: BlackSource.Filter, page: Int = 1): JSONObject? {
        val f = filter.cleaned()
        if (f.empty) return null
        val q = f.words.trim().take(80)
        val tags = f.tags.map { it.trim() }.filter(::tagOk).distinct().take(5)
        val v = JSONObject()
            .put("page", page.coerceIn(1, 10)).put("perPage", PER_PAGE)
            .put("sort", JSONArray(listOfNotNull("SEARCH_MATCH".takeIf { q.isNotEmpty() }, SORTS.getValue(f.sort))))
        if (q.isNotEmpty()) v.put("search", q)
        if (f.genres.isNotEmpty()) v.put("genres", JSONArray(f.genres.sorted()))
        val allTags = tags + listOfNotNull(f.demographic)
        if (allTags.isNotEmpty()) v.put("tags", JSONArray(allTags))
        if (f.without.isNotEmpty()) v.put("without", JSONArray(f.without.sorted()))
        if (f.formats.isNotEmpty()) v.put("formats", JSONArray(BlackSource.FORMATS.filter { it in f.formats }))
        if (f.statuses.isNotEmpty()) v.put("statuses", JSONArray(BlackSource.STATUSES.filter { it in f.statuses }))
        // FuzzyDateInt is YYYYMMDD and both bounds are strict.
        f.yearFrom?.let { v.put("after", (it - 1) * 10000 + 1231) }
        f.yearTo?.let { v.put("before", (it + 1) * 10000 + 101) }
        f.minScore?.let { v.put("score", it - 1) }
        return JSONObject().put("query", QUERY).put("variables", v)
    }

    private fun str(v: Any?, max: Int = 200): String? {
        if (v !is String) return null
        val t = v.map { if (it.code < 0x20 || it.code == 0x7f) ' ' else it }.joinToString("").trim()
        return t.takeIf { it.isNotEmpty() }?.take(max)
    }
    private fun int(o: JSONObject?, k: String, min: Int, max: Int): Int? {
        val v = o?.opt(k) ?: return null
        return (v as? Number)?.takeIf { it.toDouble() == it.toLong().toDouble() }?.toLong()?.takeIf { it in min..max }?.toInt()
    }
    private fun JSONArray?.items(): List<Any?> = if (this == null) emptyList() else (0 until length()).map { opt(it) }

    /** Pure → unit tested. One AniList media row → a title, or null when broken, adult, or missing a filter. */
    fun title(r: JSONObject, f: BlackSource.Filter): BlackSource.Title? {
        val id = int(r, "id", 1, Int.MAX_VALUE) ?: return null
        val t = r.optJSONObject("title")
        val name = str(t?.opt("romaji")) ?: str(t?.opt("english")) ?: str(t?.opt("native")) ?: return null
        val rawGenres = r.optJSONArray("genres").items()
        if (rawGenres.contains("Hentai")) return null
        val genres = rawGenres.mapNotNull { str(it, 40) }
        var demographic: String? = null
        val tags = mutableListOf<String>()
        val allTags = mutableSetOf<String>()
        r.optJSONArray("tags").items().forEach { x ->
            val tg = x as? JSONObject ?: return@forEach
            val n = str(tg.opt("name"), 40) ?: return@forEach
            if (tg.opt("isAdult") == true) return@forEach
            allTags += n.lowercase()
            if (tg.opt("category") == "Demographic") { if (demographic == null) demographic = BlackSource.DEMOGRAPHICS.find { it == n }; return@forEach }
            if (tg.opt("isMediaSpoiler") == true) return@forEach
            if ((int(tg, "rank", 0, 100) ?: 0) >= 50 && tags.size < 10) tags += n
        }
        val gl = genres.map { it.lowercase() }.toSet()
        if (f.genres.any { it.lowercase() !in gl }) return null
        if (f.tags.any { it.trim().lowercase() !in allTags }) return null
        if (f.demographic != null && demographic != f.demographic) return null
        if (f.without.any { it.lowercase() in gl }) return null
        val fmt = str(r.opt("format"), 20)
        if (f.formats.isNotEmpty() && fmt !in f.formats) return null
        val status = str(r.opt("status"), 20)
        if (f.statuses.isNotEmpty() && status !in f.statuses) return null
        val start = int(r.optJSONObject("startDate"), "year", BlackSource.YEAR_MIN, 2100)
        val yr = int(r, "seasonYear", 1900, 2100) ?: start
        val began = start ?: yr
        if ((f.yearFrom != null || f.yearTo != null) && began == null) return null
        if (f.yearFrom != null && began!! < f.yearFrom) return null
        if (f.yearTo != null && began!! > f.yearTo) return null
        val score = int(r, "averageScore", 1, 100)
        if (f.minScore != null && (score == null || score < f.minScore)) return null
        val en = str(t?.opt("english"))?.takeIf { it != name }
        val na = str(t?.opt("native"))?.takeIf { it != name }
        return BlackSource.Title(
            id = id, title = name, english = en, native = na,
            synonyms = r.optJSONArray("synonyms").items().mapNotNull { str(it, 120) }.take(8),
            year = yr, episodes = int(r, "episodes", 1, 100000), format = fmt,
            cover = str(r.optJSONObject("coverImage")?.opt("large"), 400)?.takeIf { it.startsWith("https://") },
            genres = genres, tags = tags, demographic = demographic,
            status = status?.takeIf { it in BlackSource.STATUSES }, score = score,
        )
    }

    /** Pure → unit tested. AniList's answer → a page; null when the answer is not the expected shape. */
    fun shape(json: String, filter: BlackSource.Filter): BlackSource.Page? {
        val f = filter.cleaned().let { c -> c.copy(tags = c.tags.map { it.trim() }.filter(::tagOk).distinct().take(5)) }
        val page = runCatching { JSONObject(json) }.getOrNull()?.optJSONObject("data")?.optJSONObject("Page") ?: return null
        val media = page.optJSONArray("media") ?: return null
        val items = media.items().mapNotNull { (it as? JSONObject)?.let { m -> title(m, f) } }
        return BlackSource.Page(items, page.optJSONObject("pageInfo")?.opt("hasNextPage") == true)
    }
}
