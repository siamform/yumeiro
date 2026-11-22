/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.upcoming

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * F-22-C: Upcoming series by country, with genres. Simkl's calendar files have no country or genre, so the
 * Country filter reads TVmaze's public schedule (no key; data CC BY-SA, credited on screen):
 * `api.tvmaze.com/schedule?country=XX&date=YYYY-MM-DD` (one country, one day, show embedded) and
 * `schedule/web?country=&date=` (global streaming channels, [STREAMING]). 14 small requests per country,
 * sequential (TVmaze allows 20 per 10 s), reduced to [Upcoming.Entry] and cached 6 h like the Simkl lists.
 */
object TvmazeSchedule {
    /** Country chips (ISO 3166-1; the UK is GB on TVmaze). [STREAMING] = global web channels (Netflix, …). */
    val COUNTRIES = listOf("US", "GB", "JP", "KR", "IN", "CN", "CA", "AU", "FR", "DE")
    const val STREAMING = "WEB"
    private const val TTL_MS = 6 * 60 * 60 * 1000L
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun label(country: String): String = if (country == STREAMING) "Streaming" else country

    fun url(country: String, day: LocalDate): String =
        if (country == STREAMING) "https://api.tvmaze.com/schedule/web?country=&date=$day"
        else "https://api.tvmaze.com/schedule?country=$country&date=$day"

    /** One day's schedule → entries placed in [zone]. Malformed items are skipped. */
    fun parseDay(body: String, zone: ZoneId): List<Upcoming.Entry> {
        val arr = runCatching { json.parseToJsonElement(body) as? JsonArray }.getOrNull() ?: return emptyList()
        return arr.mapNotNull { el -> runCatching { toEntry(el as JsonObject, zone) }.getOrNull() }
    }

    private fun toEntry(o: JsonObject, zone: ZoneId): Upcoming.Entry? {
        val show = (o["show"] as? JsonObject) ?: ((o["_embedded"] as? JsonObject)?.get("show") as? JsonObject) ?: return null
        val title = show["name"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val airtime = o["airtime"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val stamp = o["airstamp"]?.jsonPrimitive?.contentOrNull
        val airdate = o["airdate"]?.jsonPrimitive?.contentOrNull
        val (day, time) = when {
            airtime.isNotBlank() && stamp != null -> OffsetDateTime.parse(stamp).atZoneSameInstant(zone).let { it.toLocalDate() to it.toLocalTime() }
            airdate != null -> LocalDate.parse(airdate) to null
            stamp != null -> OffsetDateTime.parse(stamp).toLocalDate() to null
            else -> return null
        }
        val channel = (show["network"] as? JsonObject) ?: (show["webChannel"] as? JsonObject)
        val country = ((channel?.get("country") as? JsonObject)?.get("code"))?.jsonPrimitive?.contentOrNull
        val genres = (show["genres"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }.orEmpty()
        val weight = show["weight"]?.jsonPrimitive?.intOrNull?.coerceIn(0, 100) ?: 0
        val poster = ((show["image"] as? JsonObject)?.get("medium"))?.jsonPrimitive?.contentOrNull?.takeIf { it.startsWith("https://") }
        return Upcoming.Entry(
            title = title.take(200), day = day,
            // Upcoming sorts rank 1 first, 0 last; TVmaze weight 100 = most popular.
            rank = if (weight > 0) 101 - weight else 0,
            posterUrl = poster,
            season = o["season"]?.jsonPrimitive?.intOrNull,
            firstEpisode = o["number"]?.jsonPrimitive?.intOrNull,
            lastEpisode = null,
            simklId = show["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: title.hashCode().toLong(),
            time = time,
            country = country,
            genres = genres.take(4),
        )
    }

    /**
     * Days' entries → one list: in [today, today + days), one row per show per day and season (episodes merged
     * into a range), most popular [max] kept, shown by day then air time then popularity.
     */
    fun merge(days: List<List<Upcoming.Entry>>, today: LocalDate, dayCount: Int = Upcoming.DAYS, max: Int = Upcoming.MAX): List<Upcoming.Entry> {
        val end = today.plusDays(dayCount.toLong())
        val merged = LinkedHashMap<String, Upcoming.Entry>()
        for (e in days.flatten()) {
            if (e.day.isBefore(today) || !e.day.isBefore(end)) continue
            val key = "${e.simklId}|${e.day}|${e.season}"
            val prev = merged[key]
            merged[key] = if (prev == null) e else prev.copy(
                firstEpisode = listOfNotNull(prev.firstEpisode, e.firstEpisode).minOrNull(),
                lastEpisode = listOfNotNull(prev.lastEpisode, prev.firstEpisode, e.firstEpisode).maxOrNull(),
                time = listOfNotNull(prev.time, e.time).minOrNull(),
            )
        }
        val popular = merged.values.sortedWith(compareBy<Upcoming.Entry> { if (it.rank > 0) 0 else 1 }.thenBy { it.rank }).take(max)
        return popular.sortedWith(compareBy<Upcoming.Entry> { it.day }.thenBy { it.time ?: java.time.LocalTime.MAX }
            .thenBy { if (it.rank > 0) 0 else 1 }.thenBy { it.rank }.thenBy { it.title })
    }

    /** Genre chips for a list: most common first, at most [limit]. */
    fun genresOf(list: List<Upcoming.Entry>, limit: Int = 8): List<String> =
        list.flatMap { it.genres }.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).take(limit).map { it.key }

    fun filterGenre(list: List<Upcoming.Entry>, genre: String?): List<Upcoming.Entry> =
        if (genre == null) list else list.filter { e -> e.genres.any { it.equals(genre, ignoreCase = true) } }

    /** Card badge: "JP • Anime, Fantasy"; null when nothing is known. */
    fun badge(e: Upcoming.Entry): String? =
        listOfNotNull(e.country, e.genres.take(2).joinToString(", ").takeIf { it.isNotEmpty() }).joinToString(" • ").takeIf { it.isNotEmpty() }

    // region cache + network (call off the main thread)

    private fun cacheFile(context: Context, country: String) = File(context.cacheDir, "upcoming_tvmaze_${country.lowercase()}.json")

    fun cached(context: Context, country: String, today: LocalDate): List<Upcoming.Entry>? = runCatching {
        val f = cacheFile(context, country).takeIf { it.isFile } ?: return null
        f.inputStream().buffered().use { Upcoming.decodeCache(it) }.filter { !it.day.isBefore(today) }
    }.getOrNull()

    fun isFresh(context: Context, country: String): Boolean =
        cacheFile(context, country).let { it.isFile && System.currentTimeMillis() - it.lastModified() < TTL_MS }

    /**
     * Downloads [Upcoming.DAYS] days, reduces and caches. One retry after a short pause on HTTP 429 (TVmaze's
     * documented advice). Any other failure throws, so a half-loaded schedule is never shown as complete.
     */
    fun refresh(context: Context, client: OkHttpClient, country: String, today: LocalDate, zone: ZoneId): List<Upcoming.Entry> {
        val days = (0 until Upcoming.DAYS).map { i -> parseDay(get(client, url(country, today.plusDays(i.toLong()))), zone) }
        val list = merge(days, today)
        runCatching {
            val f = cacheFile(context, country)
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(Upcoming.encodeCache(list)); tmp.renameTo(f)
        }
        return list
    }

    private fun get(client: OkHttpClient, url: String): String {
        repeat(2) { attempt ->
            val req = Request.Builder().url(url).header("Accept", "application/json").build()
            client.newCall(req).execute().use { r ->
                if (r.code == 429 && attempt == 0) { Thread.sleep(2_000); return@repeat }
                if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
                return r.body?.string() ?: throw java.io.IOException("empty")
            }
        }
        throw java.io.IOException("HTTP 429")
    }

    // endregion
}
