/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.upcoming

import android.content.Context
import dev.asfakul.yumeiro.sources.hub.HubDiscover
import java.io.File
import java.time.LocalDate

/**
 * B14 (Beta chapter, Group O): "Upcoming › Movies / Series + India shows nothing". TVmaze lists no Indian TV
 * channels at all (0 episodes on every day checked, 2026-11-27) and Wikidata knows few Indian films. Like
 * VyseTV and Zangetsu, a country now asks TMDB Discover (through Yumeiro Cloud) with an explicit forward date
 * window and the country of origin, which has the country's releases and premieres; the old schedules stay as
 * the fallback when the hub has nothing or does not answer.
 *
 * Every filter works on the result: genres (names come with each title), the day chips (real release days)
 * and search. Cached 6 h per category and country, like the other lists.
 */
object UpcomingHub {
    /** Days shown for a country list: the hub window (premieres are rarer than episodes, two weeks is often empty). */
    const val DAYS = 60
    private const val PAGES = 3
    private const val TTL_MS = 6 * 60 * 60 * 1000L

    /** Where a list comes from, in the order they are asked (pure; unit-tested). */
    enum class From { HUB, SIMKL, TVMAZE, WIKIDATA }

    fun plan(kind: Upcoming.Kind, country: String?, hubOn: Boolean): List<From> = when {
        country == null -> listOf(From.SIMKL)
        country == TvmazeSchedule.STREAMING -> listOf(From.TVMAZE) // global web channels: TVmaze's own list
        !hubOn -> listOf(if (kind == Upcoming.Kind.MOVIES) From.WIKIDATA else From.TVMAZE)
        kind == Upcoming.Kind.MOVIES -> listOf(From.HUB, From.WIKIDATA)
        else -> listOf(From.HUB, From.TVMAZE)
    }

    /** Pure: the hub query for [kind] in [country]. Anime = Japanese animation from that country's view. */
    fun query(kind: Upcoming.Kind, country: String): HubDiscover.Query = when (kind) {
        Upcoming.Kind.MOVIES -> HubDiscover.Query("movie", HubDiscover.Sort.UPCOMING, origin = country)
        Upcoming.Kind.SERIES -> HubDiscover.Query("tv", HubDiscover.Sort.UPCOMING, origin = country)
        Upcoming.Kind.ANIME -> HubDiscover.Query("tv", HubDiscover.Sort.UPCOMING, anime = true)
    }

    /** Pure: hub items → entries in [today, today + days), most popular first (hub order) for [max]. */
    fun entries(items: List<HubDiscover.Item>, country: String, today: LocalDate, days: Int = DAYS, max: Int = Upcoming.MAX): List<Upcoming.Entry> {
        val end = today.plusDays(days.toLong())
        val seen = HashSet<String>()
        return items.withIndex().mapNotNull { (i, it) ->
            val day = it.date ?: return@mapNotNull null
            if (day.isBefore(today) || !day.isBefore(end)) return@mapNotNull null
            if (!seen.add("${it.kind}:${it.id}")) return@mapNotNull null
            Upcoming.Entry(
                title = it.title, day = day, rank = i + 1, posterUrl = it.poster,
                season = null, firstEpisode = null, lastEpisode = null, simklId = it.id,
                country = country, genres = it.genres.take(4),
            )
        }.take(max).sortedWith(compareBy<Upcoming.Entry> { it.day }.thenBy { it.rank }.thenBy { it.title })
    }

    private fun cacheFile(context: Context, kind: Upcoming.Kind, country: String) =
        File(context.cacheDir, "upcoming_hub_${kind.file}_${country.lowercase()}.json")

    fun cached(context: Context, kind: Upcoming.Kind, country: String, today: LocalDate): List<Upcoming.Entry>? = runCatching {
        val f = cacheFile(context, kind, country).takeIf { it.isFile } ?: return null
        f.inputStream().buffered().use { Upcoming.decodeCache(it) }.filter { !it.day.isBefore(today) }
    }.getOrNull()

    fun isFresh(context: Context, kind: Upcoming.Kind, country: String): Boolean =
        cacheFile(context, kind, country).let { it.isFile && System.currentTimeMillis() - it.lastModified() < TTL_MS }

    /** Asks the hub (a few pages); throws when it does not answer, so a failure is never shown as "nothing". */
    suspend fun refresh(context: Context, kind: Upcoming.Kind, country: String, today: LocalDate): List<Upcoming.Entry> {
        val q = query(kind, country)
        val all = ArrayList<HubDiscover.Item>()
        for (p in 1..PAGES) {
            val page = HubDiscover.page(q, p) ?: if (p == 1) throw java.io.IOException("hub did not answer") else break
            all += page.items
            if (!page.hasNext) break
        }
        val list = entries(all, country, today)
        runCatching {
            val f = cacheFile(context, kind, country)
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(Upcoming.encodeCache(list)); tmp.renameTo(f)
        }
        return list
    }
}
