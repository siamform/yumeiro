/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.upcoming

import android.content.Context
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeToSequence
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * F-08 Upcoming (ADR-034): what comes out in the next two weeks, from Simkl's public calendar files
 * (`data.simkl.in/calendar/*.json`, no key, no account; the same files Simkl's own site uses). ADR-013 keeps
 * TMDB keys out of the APK, so TMDB is not used.
 *
 * Memory: the series file is ~2.5 MB (460 KB gzip) with ~5,600 entries. It is decoded one element at a time
 * ([decodeToSequence]) and only the kept entries (max [MAX] per kind, small data class) stay in memory; the
 * reduced list is cached as a few KB of JSON for 6 h, so a 2 GB phone never holds the full file.
 */
object Upcoming {
    enum class Kind(val file: String, val label: String) {
        SERIES("tv", "Series"), MOVIES("movie_release", "Movies"), ANIME("anime", "Anime"),
    }

    data class Entry(
        val title: String,
        val day: LocalDate,
        val rank: Int,
        val posterUrl: String?,
        val season: Int?,
        val firstEpisode: Int?,
        val lastEpisode: Int?,
        val simklId: Long,
    ) {
        val episodeLabel: String? get() = firstEpisode?.let { f ->
            val eps = if (lastEpisode != null && lastEpisode != f) "E$f–$lastEpisode" else "E$f"
            if (season != null) "S$season $eps" else eps
        }
    }

    const val DAYS = 14
    const val MAX = 150
    private const val TTL_MS = 6 * 60 * 60 * 1000L
    private const val BASE = "https://data.simkl.in/calendar/"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Streams the file, keeps entries whose local day is in [today, today + days), merges one show's episodes on
     * the same day, sorts by day then Simkl popularity (rank 1 = most popular; 0 = unranked, last) and caps at [max].
     * Malformed entries are skipped.
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun parse(input: InputStream, kind: Kind, today: LocalDate, zone: ZoneId, days: Int = DAYS, max: Int = MAX): List<Entry> {
        val end = today.plusDays(days.toLong())
        val merged = LinkedHashMap<String, Entry>()
        val seq = json.decodeToSequence<JsonElement>(input, DecodeSequenceMode.ARRAY_WRAPPED)
        for (el in seq) {
            val o = el as? JsonObject ?: continue
            val e = runCatching { toEntry(o, zone) }.getOrNull() ?: continue
            if (e.day.isBefore(today) || !e.day.isBefore(end)) continue
            val key = "${e.simklId}|${e.day}|${e.season}"
            val prev = merged[key]
            merged[key] = if (prev == null) e else prev.copy(
                firstEpisode = listOfNotNull(prev.firstEpisode, e.firstEpisode).minOrNull(),
                lastEpisode = listOfNotNull(prev.lastEpisode, prev.firstEpisode, e.firstEpisode).maxOrNull(),
            )
            // Bound memory even for a huge file: trim once the map is well past what can be kept.
            if (merged.size > max * 20) trim(merged, max * 10)
        }
        return merged.values.sortedWith(order).let { sorted ->
            // Keep the most popular [max], then show them in day order.
            sorted.sortedWith(compareBy<Entry> { if (it.rank > 0) 0 else 1 }.thenBy { it.rank }).take(max).sortedWith(order)
        }
    }

    private val order = compareBy<Entry> { it.day }.thenBy { if (it.rank > 0) 0 else 1 }.thenBy { it.rank }.thenBy { it.title }

    private fun trim(map: LinkedHashMap<String, Entry>, keep: Int) {
        val best = map.entries.sortedWith(compareBy<Map.Entry<String, Entry>> { if (it.value.rank > 0) 0 else 1 }.thenBy { it.value.rank }).take(keep)
        map.clear(); best.forEach { map[it.key] = it.value }
    }

    private fun toEntry(o: JsonObject, zone: ZoneId): Entry? {
        val title = o["title"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val date = o["date"]?.jsonPrimitive?.contentOrNull ?: return null
        val day = runCatching { OffsetDateTime.parse(date).atZoneSameInstant(zone).toLocalDate() }
            .getOrElse { LocalDate.parse(date.take(10)) }
        val id = (o["ids"] as? JsonObject)?.get("simkl_id")?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: title.hashCode().toLong()
        val ep = o["episode"] as? JsonObject
        val poster = o["poster"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() && !it.contains("..") }
        return Entry(
            title = title.take(200),
            day = day,
            rank = o["rank"]?.jsonPrimitive?.intOrNull?.coerceAtLeast(0) ?: 0,
            posterUrl = poster?.let { "https://simkl.in/posters/${it}_ca.jpg" },
            season = ep?.get("season")?.jsonPrimitive?.intOrNull,
            firstEpisode = ep?.get("episode")?.jsonPrimitive?.intOrNull,
            lastEpisode = null,
            simklId = id,
        )
    }

    fun dayLabel(day: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String = when (day) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> day.dayOfWeek.getDisplayName(TextStyle.FULL, locale) + ", " + day.dayOfMonth + " " + day.month.getDisplayName(TextStyle.SHORT, locale)
    }

    // region cache + network (call off the main thread)

    private fun cacheFile(context: Context, kind: Kind) = File(context.cacheDir, "upcoming_${kind.file}.json")

    /** Cached list (any age) or null. */
    fun cached(context: Context, kind: Kind, today: LocalDate): List<Entry>? = runCatching {
        val f = cacheFile(context, kind).takeIf { it.isFile } ?: return null
        f.inputStream().buffered().use { decodeCache(it) }.filter { !it.day.isBefore(today) }
    }.getOrNull()

    fun isFresh(context: Context, kind: Kind): Boolean =
        cacheFile(context, kind).let { it.isFile && System.currentTimeMillis() - it.lastModified() < TTL_MS }

    /** Downloads, reduces and caches. Throws on network errors (the screen keeps the cached list). */
    fun refresh(context: Context, client: OkHttpClient, kind: Kind, today: LocalDate, zone: ZoneId): List<Entry> {
        val req = Request.Builder().url(BASE + kind.file + ".json").header("Accept", "application/json").build()
        val list = client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
            val body = r.body ?: throw java.io.IOException("empty")
            parse(body.byteStream(), kind, today, zone)
        }
        runCatching {
            val f = cacheFile(context, kind)
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(encodeCache(list)); tmp.renameTo(f)
        }
        return list
    }

    internal fun encodeCache(list: List<Entry>): String = kotlinx.serialization.json.buildJsonArray {
        list.forEach { e ->
            add(kotlinx.serialization.json.buildJsonObject {
                put("t", kotlinx.serialization.json.JsonPrimitive(e.title)); put("d", kotlinx.serialization.json.JsonPrimitive(e.day.toString()))
                put("r", kotlinx.serialization.json.JsonPrimitive(e.rank)); put("p", kotlinx.serialization.json.JsonPrimitive(e.posterUrl))
                put("s", kotlinx.serialization.json.JsonPrimitive(e.season)); put("a", kotlinx.serialization.json.JsonPrimitive(e.firstEpisode))
                put("b", kotlinx.serialization.json.JsonPrimitive(e.lastEpisode)); put("i", kotlinx.serialization.json.JsonPrimitive(e.simklId))
            })
        }
    }.toString()

    @OptIn(ExperimentalSerializationApi::class)
    internal fun decodeCache(input: InputStream): List<Entry> =
        json.decodeToSequence<JsonElement>(input, DecodeSequenceMode.ARRAY_WRAPPED).mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            runCatching {
                Entry(
                    title = o["t"]!!.jsonPrimitive.content, day = LocalDate.parse(o["d"]!!.jsonPrimitive.content),
                    rank = o["r"]?.jsonPrimitive?.intOrNull ?: 0, posterUrl = o["p"]?.jsonPrimitive?.contentOrNull,
                    season = o["s"]?.jsonPrimitive?.intOrNull, firstEpisode = o["a"]?.jsonPrimitive?.intOrNull,
                    lastEpisode = o["b"]?.jsonPrimitive?.intOrNull, simklId = o["i"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
                )
            }.getOrNull()
        }.toList()

    // endregion
}
