/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.intel

import android.content.Context
import android.util.Log
import dev.asfakul.yumeiro.sources.hub.HubDiscover
import org.json.JSONArray
import org.json.JSONObject

/**
 * B20 S3: where recommendations come from (plan "Candidate generation and network"). Real titles only: AniList's
 * public recommendations and tag searches for anime, manga and novels, Yumeiro Cloud's TMDB recommendations and
 * discover lists for films and series. At most 6 calls per refresh; the caller refreshes a category every 12 h at
 * most. Ranking happens in [Similarity], not here.
 */
object Candidates {
    private const val TAG = "Candidates"

    /** A real title found for a profile, with the seed titles that led to it. */
    data class Found(val meta: MetaCache.Meta, val because: List<String> = emptyList())

    /** What the profile already knows (started, finished, rated, turned away): never recommended again. */
    data class Known(val anilist: Set<Int>, val tmdb: Set<Int>, val titles: Set<String>) {
        fun has(m: MetaCache.Meta): Boolean =
            (m.ids.anilist != null && m.ids.anilist in anilist) || (m.ids.tmdb != null && m.ids.tmdb in tmdb) ||
                Tag.norm(m.title) in titles || m.alt.any { Tag.norm(it) in titles }

        companion object {
            fun of(entries: List<TasteSignals.Entry>): Known {
                val k = entries.filter { it.started || it.completed || it.feedback != null || it.dismissedAt > 0 || it.saved }
                return Known(k.mapNotNull { it.ids.anilist }.toSet(), k.mapNotNull { it.ids.tmdb }.toSet(), k.map { Tag.norm(it.title) }.toSet())
            }
        }
    }

    /** Lighter than [MetaCache.MEDIA_FIELDS] (no staff): candidate lists are long and AniList limits query cost. */
    internal const val FIELDS = "id idMal type format isAdult title{romaji english native} synonyms startDate{year month day} " +
        "seasonYear genres tags{name rank category isMediaSpoiler isAdult} popularity averageScore meanScore coverImage{large} " +
        "studios(isMain:true){nodes{name}}"

    private fun aniType(cat: MediaCategory) = if (cat == MediaCategory.ANIME) "ANIME" else "MANGA"
    private fun aniFormats(cat: MediaCategory): JSONArray? = when (cat) {
        MediaCategory.MANGA -> JSONArray(listOf("MANGA", "ONE_SHOT"))
        MediaCategory.NOVEL -> JSONArray(listOf("NOVEL"))
        else -> null
    }

    private fun list(a: JSONArray?): List<MetaCache.Meta> =
        if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(MetaCache::parseAniList) }

    /**
     * Anime / manga / novels: AniList recommendations of up to 3 seeds, one tag search, one quieter well-rated
     * search (hidden gems) and one search for new releases; [taste] supplies the tags (maybe another category's,
     * \"From your anime taste\").
     */
    suspend fun aniList(cat: MediaCategory, taste: TasteProfile, known: Known, now: Long): List<Found> {
        val out = LinkedHashMap<Int, Found>()
        fun add(m: MetaCache.Meta, because: String?) {
            if (m.adult || m.category != cat || known.has(m)) return
            val id = m.ids.anilist ?: return
            val old = out[id]
            out[id] = if (old == null) Found(m, listOfNotNull(because)) else old.copy(because = (old.because + listOfNotNull(because)).distinct())
        }
        val seeds = if (taste.category == cat) taste.seeds.filter { it.ids.anilist != null }.take(3) else emptyList()
        for (s in seeds) {
            val q = "query(\$id:Int){Media(id:\$id){recommendations(perPage:12,sort:RATING_DESC){nodes{rating mediaRecommendation{$FIELDS}}}}}"
            val data = runCatching { MetaCache.ani(q, JSONObject().put("id", s.ids.anilist)) }.onFailure { Log.i(TAG, "recs: ${it.javaClass.simpleName}") }.getOrNull() ?: continue
            val nodes = data.optJSONObject("Media")?.optJSONObject("recommendations")?.optJSONArray("nodes") ?: continue
            for (i in 0 until nodes.length()) {
                val n = nodes.optJSONObject(i) ?: continue
                if (n.optInt("rating") < 1) continue // a recommendation nobody upvoted is noise
                n.optJSONObject("mediaRecommendation")?.let(MetaCache::parseAniList)?.let { add(it, s.title) }
            }
        }
        val tags = (taste.top(12, Aspect.THEMES) + taste.top(6, Aspect.CHARACTERS) + taste.top(4, Aspect.SETTING))
            .sortedByDescending { it.second }.map { taste.names[it.first] ?: it.first }.take(3)
        val genres = taste.top(3, Aspect.GENRE).map { taste.names[it.first] ?: it.first }
        val no = JSONArray((known.anilist + out.keys).take(200).toList())
        val base = "\$t:MediaType,\$f:[MediaFormat],\$no:[Int]"
        val vars = { JSONObject().put("t", aniType(cat)).apply { aniFormats(cat)?.let { put("f", it) } }.put("no", no) }
        suspend fun search(args: String, extra: JSONObject.() -> Unit, label: String) {
            val q = "query($base${args.substringBefore('|')}){Page(perPage:30){media(type:\$t,format_in:\$f,id_not_in:\$no,isAdult:false,${args.substringAfter('|')}){$FIELDS}}}"
            val data = runCatching { MetaCache.ani(q, vars().apply(extra)) }.onFailure { Log.i(TAG, "$label: ${it.javaClass.simpleName}") }.getOrNull() ?: return
            list(data.optJSONObject("Page")?.optJSONArray("media")).forEach { add(it, null) }
        }
        if (tags.isNotEmpty()) {
            search(",\$tags:[String]|tag_in:\$tags,sort:[SCORE_DESC]", { put("tags", JSONArray(tags)) }, "tags")
            search(",\$tags:[String]|tag_in:\$tags,popularity_lesser:40000,averageScore_greater:69,sort:[SCORE_DESC]", { put("tags", JSONArray(tags)) }, "gems")
        }
        if (genres.isNotEmpty()) {
            val since = java.time.Instant.ofEpochMilli(now - 150L * 86_400_000L).atZone(java.time.ZoneOffset.UTC).toLocalDate()
            val fuzzy = since.year * 10_000 + since.monthValue * 100 + since.dayOfMonth
            search(",\$g:[String],\$d:FuzzyDateInt|genre_in:\$g,startDate_greater:\$d,sort:[POPULARITY_DESC]", { put("g", JSONArray(genres.take(2))); put("d", fuzzy) }, "new")
        }
        return out.values.toList()
    }

    /** Films / series: TMDB recommendations of up to 3 seeds, top rated and new in the taste's genres. */
    suspend fun films(c: Context, cat: MediaCategory, taste: TasteProfile, known: Known): List<Found> {
        if (!HubDiscover.available()) return emptyList()
        val kind = if (cat == MediaCategory.MOVIE) "movie" else "tv"
        val out = LinkedHashMap<Long, Pair<HubDiscover.Item, List<String>>>()
        fun add(i: HubDiscover.Item, because: String?) {
            if (i.anime || i.kind != kind || i.id.toInt() in known.tmdb || Tag.norm(i.title) in known.titles) return
            val old = out[i.id]
            out[i.id] = i to ((old?.second.orEmpty()) + listOfNotNull(because)).distinct()
        }
        val seeds = if (taste.category == cat) taste.seeds.filter { it.ids.tmdb != null && (it.ids.tmdbKind == null || it.ids.tmdbKind == kind) }.take(3) else emptyList()
        for (s in seeds) runCatching { HubDiscover.recommend(kind, s.ids.tmdb!!.toLong()) }.getOrNull()?.items?.forEach { add(it, s.title) }
        val table = if (kind == "movie") HubDiscover.MOVIE_GENRES else HubDiscover.TV_GENRES
        val genreIds = taste.top(4, Aspect.GENRE).mapNotNull { (id, _) -> table.firstOrNull { Tag.norm(it.second) == id }?.first }.take(2)
        if (genreIds.isNotEmpty()) {
            runCatching { HubDiscover.page(HubDiscover.Query(kind, HubDiscover.Sort.TOP, genres = genreIds, minRating = 7)) }.getOrNull()?.items?.forEach { add(it, null) }
            runCatching { HubDiscover.page(HubDiscover.Query(kind, HubDiscover.Sort.NEW, genres = genreIds)) }.getOrNull()?.items?.forEach { add(it, null) }
        }
        // Keywords (story themes) for the most promising: TMDB lists only carry genres.
        val firstPass = out.values.sortedByDescending { (i, b) -> b.size * 2 + i.genres.count { g -> taste.positive[Aspect.GENRE]?.containsKey(Tag.norm(g)) == true } }.take(18)
        return firstPass.map { (i, because) ->
            val meta = runCatching { MetaCache.filmById(c, i.id.toInt(), kind) }.getOrNull() ?: MetaCache.Meta(
                title = i.title, category = cat, ids = TasteSignals.Ids(tmdb = i.id.toInt(), tmdbKind = kind),
                tags = i.genres.map { Tag(it, Aspect.GENRE, 1f) }, year = i.year, cover = i.poster,
                popularity = i.votes ?: 0, score = ((i.rating ?: 0.0) * 10).toInt(), releasedAt = i.date?.toString(),
            )
            Found(meta.copy(cover = meta.cover ?: i.poster, category = cat), because)
        }
    }

    /** "Similar by story" for one title: its AniList recommendations plus a search on its own strongest tags. */
    suspend fun likeTitle(seed: MetaCache.Meta): List<Found> {
        val id = seed.ids.anilist ?: return emptyList()
        val out = LinkedHashMap<Int, Found>()
        val q = "query(\$id:Int){Media(id:\$id){recommendations(perPage:15,sort:RATING_DESC){nodes{rating mediaRecommendation{$FIELDS}}}}}"
        val data = MetaCache.ani(q, JSONObject().put("id", id))
        val nodes = data?.optJSONObject("Media")?.optJSONObject("recommendations")?.optJSONArray("nodes")
        if (nodes != null) for (i in 0 until nodes.length()) {
            val n = nodes.optJSONObject(i) ?: continue
            if (n.optInt("rating") < 1) continue
            n.optJSONObject("mediaRecommendation")?.let(MetaCache::parseAniList)?.takeIf { !it.adult && it.ids.anilist != id }?.let { out[it.ids.anilist!!] = Found(it, listOf(seed.title)) }
        }
        val tags = seed.tags.filter { !it.spoiler && (it.aspect == Aspect.THEMES || it.aspect == Aspect.CHARACTERS) }.sortedByDescending { it.weight * it.aspect.weight }.take(3).map { it.name }
        if (tags.isNotEmpty()) {
            val type = if (seed.category == MediaCategory.ANIME) "ANIME" else "MANGA"
            val tq = "query(\$t:MediaType,\$tags:[String],\$no:[Int]){Page(perPage:25){media(type:\$t,tag_in:\$tags,id_not_in:\$no,isAdult:false,sort:[SCORE_DESC]){$FIELDS}}}"
            runCatching { MetaCache.ani(tq, JSONObject().put("t", type).put("tags", JSONArray(tags)).put("no", JSONArray(listOf(id)))) }.getOrNull()
                ?.optJSONObject("Page")?.optJSONArray("media")?.let(::list)?.filter { !it.adult }?.forEach { m -> m.ids.anilist?.let { out.putIfAbsent(it, Found(m)) } }
        }
        return out.values.toList()
    }
}
