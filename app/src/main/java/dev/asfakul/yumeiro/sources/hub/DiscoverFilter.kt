/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.hub

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Group W (B1): what the "Find something" page is asked to show. Everything is optional; what is set must all
 * match (genres and themes all, any one of the streaming services).
 *
 * It travels inside the page's route as one short text ([encode] / [decode]), so a Genres or Services card, a
 * Home chip and Back all open the same results, and a broken or old value just opens the page unfiltered.
 */
data class DiscoverFilter(
    val kind: GenreCatalog.Kind = GenreCatalog.Kind.ALL,
    val sort: HubDiscover.Sort = HubDiscover.Sort.POPULAR,
    /** [GenreCatalog.Genre.key]s, up to [MAX_GENRES]. */
    val genres: List<String> = emptyList(),
    /** TMDB watch-provider ids, up to [MAX_SERVICES]; [region] is the country they are looked up in. */
    val services: List<Int> = emptyList(),
    val region: String? = null,
    /** Country the title comes from (ISO 3166-1) and its original language (ISO 639-1). */
    val origin: String? = null,
    val lang: String? = null,
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val minRating: Int? = null,
    /** The page title when opened from a card ("Netflix", "Isekai"); null = "Find something". */
    val title: String? = null,
    /** "movie" or "tv": only films or only series of [kind] (anime films, say); null = both where both exist. */
    val only: String? = null,
    /** A ready-made catalog shelf ("hot_today"): shown as it is, the filters do not apply to it. */
    val shelf: String? = null,
) {
    /** How many filters are on (the sort and kind do not count). */
    val activeCount: Int get() = genres.size + services.size + listOfNotNull(origin, lang, minRating).size + (if (yearFrom != null || yearTo != null) 1 else 0)

    fun cleared(): DiscoverFilter = DiscoverFilter(kind = kind, sort = sort, region = region, only = only)

    /** True when filters can be changed (a ready-made shelf cannot). */
    val filterable: Boolean get() = shelf == null

    fun encode(): String = buildList {
        if (kind != GenreCatalog.Kind.ALL) add("k=${kind.name.lowercase()}")
        if (sort != HubDiscover.Sort.POPULAR) add("s=${sort.key}")
        if (genres.isNotEmpty()) add("g=" + genres.joinToString(","))
        if (services.isNotEmpty()) add("p=" + services.joinToString(","))
        region?.let { add("r=$it") }
        origin?.let { add("o=$it") }
        lang?.let { add("l=$it") }
        if (yearFrom != null || yearTo != null) add("y=${yearFrom ?: ""}-${yearTo ?: ""}")
        minRating?.let { add("m=$it") }
        title?.let { add("t=" + java.net.URLEncoder.encode(it.take(60), "UTF-8")) }
        only?.let { add("f=$it") }
        shelf?.let { add("sh=$it") }
    }.joinToString(";")

    /** Pure: the catalog asks for one page; "All" asks for films and series together (see [load]). */
    fun queries(): List<HubDiscover.Query> {
        if (shelf != null) return emptyList()
        val anime = kind == GenreCatalog.Kind.ANIME
        val types = when (kind) {
            GenreCatalog.Kind.MOVIE -> listOf("movie")
            GenreCatalog.Kind.TV -> listOf("tv")
            // Anime is series first, then films; "All" films and series.
            GenreCatalog.Kind.ANIME -> listOf("tv", "movie")
            GenreCatalog.Kind.ALL -> listOf("movie", "tv")
        }.filter { only == null || it == only }
        val chosen = genres.mapNotNull { GenreCatalog.byKey(it) }
        return types.mapNotNull { type ->
            val k = if (type == "movie") GenreCatalog.Kind.MOVIE else GenreCatalog.Kind.TV
            // A kind that cannot have one of the chosen genres is left out (a film "Reality" does not exist).
            if (chosen.any { !it.supports(k) }) return@mapNotNull null
            val ids = chosen.mapNotNull { if (type == "movie") it.movie else it.tv }.filter { !(anime && it == 16) }.distinct()
            val kws = chosen.filter { (if (type == "movie") it.movie else it.tv) == null }.mapNotNull { it.keyword }.distinct()
            HubDiscover.Query(
                kind = type, sort = sort, genres = ids.take(3), providers = services.take(MAX_SERVICES),
                region = if (services.isEmpty()) null else region, origin = origin, lang = lang,
                anime = anime, keywords = kws.take(3), yearFrom = yearFrom, yearTo = yearTo, minRating = minRating,
            )
        }
    }

    companion object {
        const val MAX_GENRES = 3
        const val MAX_SERVICES = 3
        const val MIN_YEAR = 1950

        /** Pure: the text from [encode] back; anything unknown or out of range is dropped. */
        fun decode(raw: String?): DiscoverFilter {
            if (raw.isNullOrBlank()) return DiscoverFilter()
            val m = raw.split(';').mapNotNull { p -> p.indexOf('=').takeIf { it > 0 }?.let { p.substring(0, it) to p.substring(it + 1) } }.toMap()
            val iso = { v: String? -> v?.uppercase()?.takeIf { it.length == 2 && it.all { c -> c in 'A'..'Z' } } }
            val years = m["y"]?.split('-')
            val yr = { v: String? -> v?.toIntOrNull()?.takeIf { it in 1900..2100 } }
            return DiscoverFilter(
                kind = GenreCatalog.Kind.entries.firstOrNull { it.name.equals(m["k"], ignoreCase = true) } ?: GenreCatalog.Kind.ALL,
                sort = HubDiscover.Sort.entries.firstOrNull { it.key == m["s"] } ?: HubDiscover.Sort.POPULAR,
                genres = m["g"]?.split(',')?.filter { GenreCatalog.byKey(it) != null }?.distinct()?.take(MAX_GENRES).orEmpty(),
                services = m["p"]?.split(',')?.mapNotNull { it.toIntOrNull()?.takeIf { n -> n in 1..999_999 } }?.distinct()?.take(MAX_SERVICES).orEmpty(),
                region = iso(m["r"]), origin = iso(m["o"]),
                lang = m["l"]?.lowercase()?.takeIf { it.length == 2 && it.all { c -> c in 'a'..'z' } },
                yearFrom = yr(years?.getOrNull(0)), yearTo = yr(years?.getOrNull(1)),
                minRating = m["m"]?.toIntOrNull()?.takeIf { it in 1..9 },
                title = m["t"]?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrNull() }?.trim()?.take(60)?.ifEmpty { null },
                only = m["f"]?.takeIf { it == "movie" || it == "tv" },
                shelf = m["sh"]?.takeIf { it.length in 1..40 && it.all { c -> c.isLetterOrDigit() || c == '_' } },
            )
        }

        /** Pure: a catalog ask as a filter (Discover rows' "See all"). */
        fun of(q: HubDiscover.Query, title: String?): DiscoverFilter = DiscoverFilter(
            kind = when { q.anime -> GenreCatalog.Kind.ANIME; q.kind == "movie" -> GenreCatalog.Kind.MOVIE; else -> GenreCatalog.Kind.TV },
            sort = q.sort,
            genres = q.genres.mapNotNull { GenreCatalog.ofTmdb(it, q.kind)?.key }.distinct().take(MAX_GENRES),
            services = q.providers.take(MAX_SERVICES), region = q.region, origin = q.origin, lang = q.lang,
            yearFrom = q.yearFrom, yearTo = q.yearTo, minRating = q.minRating, title = title,
            only = if (q.anime) q.kind else null,
        )

        /** Pure: two pages (films and series) woven together one by one, duplicates dropped. */
        fun weave(pages: List<HubDiscover.Page>): HubDiscover.Page {
            val out = ArrayList<HubDiscover.Item>()
            val seen = HashSet<String>()
            val longest = pages.maxOfOrNull { it.items.size } ?: 0
            for (i in 0 until longest) for (p in pages) p.items.getOrNull(i)?.let { if (seen.add(it.url)) out += it }
            return HubDiscover.Page(out, pages.any { it.hasNext })
        }
    }

    /**
     * Page [page] of the results. Films and series are asked at the same time; one failing still shows the
     * other (null only when every ask failed).
     */
    suspend fun load(page: Int): HubDiscover.Page? {
        shelf?.let { return HubDiscover.shelf(it, page) }
        val qs = queries()
        if (qs.isEmpty()) return HubDiscover.Page(emptyList(), false)
        val got = coroutineScope { qs.map { q -> async { HubDiscover.page(q, page) } }.map { it.await() } }
        val ok = got.filterNotNull()
        return if (ok.isEmpty()) null else weave(ok)
    }
}
