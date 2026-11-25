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
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.time.LocalDate

/**
 * H-9a (owner item 9): Upcoming → Movies by country. Simkl's movie calendar has no country, and TVmaze has no
 * movies, so this reads Wikidata (public SPARQL endpoint, no key; data CC0): films whose country of origin
 * (P495, matched by its ISO code P297) have a publication date (P577) in the next [Upcoming.DAYS] days. One
 * request per country, ranked by how many Wikipedias have the film (a fair popularity signal), cached 6 h.
 * Coverage is thinner than Simkl's worldwide list; the screen says where the data comes from.
 */
object MovieReleases {
    private const val TTL_MS = 6 * 60 * 60 * 1000L
    private const val ENDPOINT = "https://query.wikidata.org/sparql"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Pure: the SPARQL text for [country] (ISO 3166-1 alpha-2) between [from] (inclusive) and [to] (exclusive). */
    fun query(country: String, from: LocalDate, to: LocalDate): String {
        require(country.length == 2 && country.all { it in 'A'..'Z' }) { "country" }
        return """
            SELECT ?film ?label ?d ?img ?links WHERE {
              ?c wdt:P297 "$country". ?film wdt:P495 ?c; wdt:P577 ?d.
              FILTER(?d >= "${from}T00:00:00Z"^^xsd:dateTime && ?d < "${to}T00:00:00Z"^^xsd:dateTime)
              ?film wdt:P31 wd:Q11424.
              OPTIONAL { ?film rdfs:label ?label FILTER(LANG(?label) = "en") }
              OPTIONAL { ?film wdt:P18 ?img }
              OPTIONAL { ?film wikibase:sitelinks ?links }
            } LIMIT 600
        """.trimIndent()
    }

    /**
     * Pure: SPARQL JSON → one entry per film (its earliest date in the window, so a film released in several
     * countries on several days is listed once), most Wikipedias first for [max], shown by day then title.
     */
    fun parse(body: String, country: String, today: LocalDate, days: Int = Upcoming.DAYS, max: Int = Upcoming.MAX): List<Upcoming.Entry> {
        val rows = runCatching {
            ((json.parseToJsonElement(body) as JsonObject)["results"] as JsonObject)["bindings"] as JsonArray
        }.getOrNull() ?: return emptyList()
        val end = today.plusDays(days.toLong())
        val best = HashMap<String, Upcoming.Entry>()
        for (el in rows) {
            val o = el as? JsonObject ?: continue
            fun v(k: String) = ((o[k] as? JsonObject)?.get("value") as? JsonPrimitive)?.content
            val uri = v("film") ?: continue
            val qid = uri.substringAfterLast('/').takeIf { it.startsWith("Q") } ?: continue
            val title = v("label")?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            val day = runCatching { LocalDate.parse(v("d").orEmpty().take(10)) }.getOrNull() ?: continue
            if (day.isBefore(today) || !day.isBefore(end)) continue
            val links = v("links")?.toIntOrNull() ?: 0
            val img = v("img")?.takeIf { it.startsWith("http") }?.let { poster(it) }
            val e = Upcoming.Entry(
                title = title.take(200), day = day,
                // Upcoming sorts rank 1 first, 0 last: more Wikipedias = more known.
                rank = if (links > 0) (1000 - links).coerceAtLeast(1) else 0,
                posterUrl = img, season = null, firstEpisode = null, lastEpisode = null,
                simklId = qid.drop(1).toLongOrNull() ?: title.hashCode().toLong(),
                country = country,
            )
            val prev = best[qid]
            best[qid] = when {
                prev == null -> e
                e.day.isBefore(prev.day) -> e.copy(posterUrl = e.posterUrl ?: prev.posterUrl)
                else -> prev.copy(posterUrl = prev.posterUrl ?: e.posterUrl)
            }
        }
        return best.values.sortedWith(compareBy<Upcoming.Entry> { if (it.rank > 0) 0 else 1 }.thenBy { it.rank }).take(max)
            .sortedWith(compareBy<Upcoming.Entry> { it.day }.thenBy { if (it.rank > 0) 0 else 1 }.thenBy { it.rank }.thenBy { it.title })
    }

    /** Commons file URL → a 300 px thumbnail (Special:FilePath scales on the server). */
    internal fun poster(commons: String): String =
        (if (commons.startsWith("http://")) "https://" + commons.removePrefix("http://") else commons).substringBefore('?') + "?width=300"

    // region cache + network (call off the main thread)

    private fun cacheFile(context: Context, country: String) = File(context.cacheDir, "upcoming_movies_${country.lowercase()}.json")

    fun cached(context: Context, country: String, today: LocalDate): List<Upcoming.Entry>? = runCatching {
        val f = cacheFile(context, country).takeIf { it.isFile } ?: return null
        f.inputStream().buffered().use { Upcoming.decodeCache(it) }.filter { !it.day.isBefore(today) }
    }.getOrNull()

    fun isFresh(context: Context, country: String): Boolean =
        cacheFile(context, country).let { it.isFile && System.currentTimeMillis() - it.lastModified() < TTL_MS }

    /** Downloads, reduces and caches. Any failure throws, so a failed answer is never shown as "nothing". */
    fun refresh(context: Context, client: OkHttpClient, country: String, today: LocalDate): List<Upcoming.Entry> {
        val url = ENDPOINT.toHttpUrl().newBuilder()
            .addQueryParameter("query", query(country, today, today.plusDays(Upcoming.DAYS.toLong())))
            .addQueryParameter("format", "json").build()
        val req = Request.Builder().url(url)
            .header("Accept", "application/sparql-results+json")
            .header("User-Agent", "Yumeiro/" + dev.asfakul.yumeiro.BuildConfig.VERSION_NAME + " (https://yumeiro.click; upcoming movies)")
            .build()
        // H-9 (run 45710085458 api36/tv run 1: SocketTimeoutException): a cold Wikidata SPARQL query can take
        // longer than the app's normal read timeout. This one gets 40 s to answer and one more try after a
        // timeout, a 429 or a 5xx; a second failure still shows the error card, never "nothing".
        val slow = client.newBuilder().readTimeout(java.time.Duration.ofSeconds(40)).callTimeout(java.time.Duration.ofSeconds(50)).build()
        val body = fetchWithRetry(slow, req)
        val list = parse(body, country, today)
        runCatching {
            val f = cacheFile(context, country)
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(Upcoming.encodeCache(list)); tmp.renameTo(f)
        }
        return list
    }

    internal fun fetchWithRetry(client: OkHttpClient, req: Request, pauseMs: Long = 1_500): String {
        var last: java.io.IOException? = null
        repeat(2) { attempt ->
            if (attempt > 0) Thread.sleep(pauseMs)
            try {
                client.newCall(req).execute().use { r ->
                    if (r.isSuccessful) return r.body.string()
                    val e = java.io.IOException("HTTP ${r.code}")
                    if (r.code != 429 && r.code < 500) throw e
                    last = e
                }
            } catch (e: java.io.InterruptedIOException) { last = e }
        }
        throw last ?: java.io.IOException("no answer")
    }

    // endregion
}
