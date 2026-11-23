/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.meta

import android.content.Context
import dev.asfakul.yumeiro.BuildConfig
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.online.OnlineDetail
import dev.asfakul.yumeiro.online.OnlineKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale

/**
 * META-1 (owner list 13): the facts a title page shows — release date, length, IMDb rating with its vote count,
 * certificate, country, language, director, writers, production companies, network, cast and trailers — and only
 * the ones that exist.
 *
 * Sources, all free and keyless (ADR-013 keeps keys such as TMDB's out of the APK):
 *  - the extension's own answer (CloudStream `actors`, `trailers`, `duration`), always first;
 *  - Cinemeta (`v3-cinemeta.strem.io/meta/<movie|series>/<imdb>.json`, Stremio's official catalog): director,
 *    writers, cast, country, release date, runtime, IMDb rating, YouTube trailers;
 *  - Simkl (`api.simkl.com/<movies|tv>/<imdb>?extended=full`, the build's public client id): IMDb vote count,
 *    certificate, language, network;
 *  - Wikidata (SPARQL by IMDb id, P345): production companies (P272), original language (P364), country (P495).
 * They need the title's IMDb id, which most movie and series extensions give. Each source is optional: one that
 * fails or has nothing just leaves its fields out. The merged remote part is cached for 7 days per IMDb id.
 */
object TitleFacts {
    @Serializable data class Person(val name: String, val role: String? = null, val image: String? = null)
    @Serializable data class Trailer(val name: String, val url: String)

    @Serializable data class Facts(
        val released: String? = null,
        val runtimeMin: Int? = null,
        val rating: Double? = null,
        val votes: Long? = null,
        val certification: String? = null,
        val countries: List<String> = emptyList(),
        val languages: List<String> = emptyList(),
        val directors: List<String> = emptyList(),
        val writers: List<String> = emptyList(),
        val companies: List<String> = emptyList(),
        val network: String? = null,
        val cast: List<Person> = emptyList(),
        val trailers: List<Trailer> = emptyList(),
    ) {
        /** [this] first, [other] for anything [this] lacks. */
        fun or(other: Facts) = Facts(
            released ?: other.released, runtimeMin ?: other.runtimeMin,
            rating ?: other.rating, if (rating != null) votes else votes ?: other.votes,
            certification ?: other.certification,
            countries.ifEmpty { other.countries }, languages.ifEmpty { other.languages },
            directors.ifEmpty { other.directors }, writers.ifEmpty { other.writers }, companies.ifEmpty { other.companies },
            network ?: other.network, cast.ifEmpty { other.cast }, trailers.ifEmpty { other.trailers },
        )
        val isEmpty get() = this == Facts()
    }

    private val json = Json { ignoreUnknownKeys = true }
    private const val CACHE_NS = "facts"
    private const val CACHE_MS = 7L * 24 * 60 * 60 * 1000
    private val UA = "Yumeiro/" + BuildConfig.VERSION_NAME + " (https://yumeiro.click; title facts)"

    /** What the extension itself gave. */
    fun fromDetail(d: OnlineDetail) = Facts(
        runtimeMin = d.durationMin,
        cast = d.cast.map { Person(it.name, it.role, it.image) },
        trailers = d.trailers.mapIndexed { i, u -> Trailer(if (d.trailers.size == 1) "Trailer" else "Trailer ${i + 1}", u) },
    )

    /** Extension facts plus, with an IMDb id, the free sources above. Never throws; off the main thread. */
    suspend fun load(context: Context, d: OnlineDetail): Facts = withContext(Dispatchers.IO) {
        val own = fromDetail(d)
        val imdb = d.imdbId?.takeIf { IMDB.matches(it) } ?: return@withContext own
        val cached = dev.asfakul.yumeiro.cache.DataCache.read(CACHE_NS, imdb, CACHE_MS)
            ?.let { runCatching { json.decodeFromString(Facts.serializer(), it) }.getOrNull() }
        val remote = cached ?: fetch(NetworkClient.get(context), imdb, d.kind == OnlineKind.MOVIE).also { f ->
            if (!f.isEmpty) dev.asfakul.yumeiro.cache.DataCache.write(CACHE_NS, imdb, json.encodeToString(Facts.serializer(), f))
        }
        own.or(remote)
    }

    internal suspend fun fetch(client: OkHttpClient, imdb: String, movie: Boolean): Facts = coroutineScope {
        val cine = async { get(client, "https://v3-cinemeta.strem.io/meta/${if (movie) "movie" else "series"}/$imdb.json")?.let(::parseCinemeta) }
        val simkl = async {
            val id = runCatching { dev.asfakul.yumeiro.tracking.Simkl.clientId() }.getOrNull().orEmpty()
            if (id.isBlank()) null else get(
                client, "https://api.simkl.com/${if (movie) "movies" else "tv"}/$imdb".toHttpUrl().newBuilder()
                    .addQueryParameter("extended", "full").addQueryParameter("client_id", id)
                    .addQueryParameter("app-name", "yumeiro").addQueryParameter("app-version", BuildConfig.VERSION_NAME).build().toString(),
            )?.let(::parseSimkl)
        }
        val wiki = async {
            get(client, "https://query.wikidata.org/sparql".toHttpUrl().newBuilder().addQueryParameter("query", wikidataQuery(imdb))
                .addQueryParameter("format", "json").build().toString())?.let(::parseWikidata)
        }
        // Cinemeta first (it carries the most), then Simkl for votes / certificate / language, Wikidata for companies.
        val c = cine.await() ?: Facts()
        val s = simkl.await() ?: Facts()
        val w = wiki.await() ?: Facts()
        // Simkl's IMDb rating comes with its vote count; use the pair together when Simkl has it.
        val rated = if (s.rating != null) c.copy(rating = s.rating, votes = s.votes) else c
        rated.or(s).or(w)
    }

    private fun get(client: OkHttpClient, url: String): String? = runCatching {
        client.newCall(Request.Builder().url(url).header("User-Agent", UA).header("Accept", "application/json, application/sparql-results+json").build())
            .execute().use { r -> if (r.isSuccessful) r.body.string() else null }
    }.getOrNull()

    private val IMDB = Regex("tt\\d{5,10}")

    internal fun wikidataQuery(imdb: String) = """
        SELECT (GROUP_CONCAT(DISTINCT ?coL; separator="|") AS ?companies) (GROUP_CONCAT(DISTINCT ?langL; separator="|") AS ?langs)
               (GROUP_CONCAT(DISTINCT ?ctL; separator="|") AS ?countries) WHERE {
          ?item wdt:P345 "$imdb".
          OPTIONAL { ?item wdt:P272 ?co. ?co rdfs:label ?coL FILTER(lang(?coL) = "en") }
          OPTIONAL { ?item wdt:P364 ?lang. ?lang rdfs:label ?langL FILTER(lang(?langL) = "en") }
          OPTIONAL { ?item wdt:P495 ?ct. ?ct rdfs:label ?ctL FILTER(lang(?ctL) = "en") }
        } GROUP BY ?item LIMIT 1
    """.trimIndent()

    // region parsing (pure; MetaFactsTest feeds real answers)

    private fun JsonObject.s(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
    private fun JsonObject.list(k: String): List<String> = when (val v = this[k]) {
        is JsonArray -> v.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }
        is JsonPrimitive -> v.contentOrNull?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        else -> emptyList()
    }
    private fun root(text: String): JsonObject? = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()

    fun parseCinemeta(text: String): Facts? {
        val m = (root(text)?.get("meta") as? JsonObject) ?: return null
        val trailers = (m["trailerStreams"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.s("ytId") }
            .ifEmpty { (m["trailers"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.s("source") } }
            .distinct().take(6)
        return Facts(
            released = m.s("released")?.take(10)?.takeIf { DATE.matches(it) },
            runtimeMin = m.s("runtime")?.let(::minutes),
            rating = m.s("imdbRating")?.toDoubleOrNull()?.takeIf { it > 0 },
            countries = m.list("country"),
            directors = m.list("director"),
            writers = m.list("writer"),
            cast = m.list("cast").take(20).map { Person(it) },
            trailers = trailers.mapIndexed { i, id -> Trailer(if (trailers.size == 1) "Trailer" else "Trailer ${i + 1}", "https://www.youtube.com/watch?v=$id") },
        )
    }

    fun parseSimkl(text: String): Facts? {
        val o = root(text) ?: return null
        if (o.s("title") == null) return null
        val imdb = ((o["ratings"] as? JsonObject)?.get("imdb") as? JsonObject)
        val trailers = (o["trailers"] as? JsonArray).orEmpty().mapNotNull { t -> (t as? JsonObject)?.let { it.s("youtube")?.let { id -> Trailer(it.s("name") ?: "Trailer", "https://www.youtube.com/watch?v=$id") } } }.take(6)
        return Facts(
            released = (o.s("released") ?: o.s("first_aired"))?.take(10)?.takeIf { DATE.matches(it) },
            runtimeMin = o.s("runtime")?.let(::minutes),
            rating = imdb?.s("rating")?.toDoubleOrNull()?.takeIf { it > 0 },
            votes = imdb?.s("votes")?.toLongOrNull()?.takeIf { it > 0 },
            certification = o.s("certification"),
            countries = o.list("country").map(::countryName),
            languages = (o.s("original_language") ?: o.s("language"))?.let { listOf(languageName(it)) }.orEmpty(),
            directors = o.list("director"),
            network = o.s("network"),
            trailers = trailers,
        )
    }

    fun parseWikidata(text: String): Facts? {
        val b = ((root(text)?.get("results") as? JsonObject)?.get("bindings") as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
        fun field(k: String) = ((b[k] as? JsonObject)?.s("value")).orEmpty().split('|').map { it.trim() }.filter { it.isNotEmpty() && !QID.matches(it) }
        return Facts(companies = field("companies").take(6), languages = field("langs"), countries = field("countries"))
    }

    private val DATE = Regex("\\d{4}-\\d{2}-\\d{2}")
    private val QID = Regex("Q\\d+")
    private val HOURS = Regex("(\\d+)\\s*h")
    private val MINS = Regex("(\\d+)\\s*m")

    /** "142 min", "2h 22m", "47" -> minutes. */
    internal fun minutes(s: String): Int? {
        val t = s.lowercase()
        val h = HOURS.find(t)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val m = MINS.find(t)?.groupValues?.get(1)?.toIntOrNull() ?: if (h == 0) t.trim().toIntOrNull() ?: 0 else 0
        return (h * 60 + m).takeIf { it in 1..1500 }
    }

    /** "US" -> "United States"; anything that isn't a 2-letter code stays as it is. */
    internal fun countryName(code: String): String =
        if (code.length == 2) Locale("", code.uppercase()).getDisplayCountry(Locale.ENGLISH).ifBlank { code } else code
    internal fun languageName(code: String): String =
        if (code.length in 2..3) Locale(code.lowercase()).getDisplayLanguage(Locale.ENGLISH).ifBlank { code } else code

    // endregion

    // region display (pure)

    /** "2.7M votes", "442K votes", "980 votes". */
    fun votesText(n: Long): String = when {
        n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1e6).replace(".0M", "M")
        n >= 10_000 -> "${n / 1000}K"
        n >= 1_000 -> String.format(Locale.US, "%.1fK", n / 1e3).replace(".0K", "K")
        else -> n.toString()
    } + if (n == 1L) " vote" else " votes"

    /** "1994-10-14" -> "14 October 1994" (English month names, like the rest of the app). */
    fun dateText(iso: String): String = runCatching {
        java.time.LocalDate.parse(iso).format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH))
    }.getOrDefault(iso)

    /** The labelled rows of the About block, in order, without the ones that are missing. */
    fun rows(f: Facts, kind: OnlineKind): List<Pair<String, String>> = buildList {
        f.released?.let { add((if (kind == OnlineKind.MOVIE) "Release date" else "First aired") to dateText(it)) }
        f.certification?.let { add("Certificate" to it) }
        if (f.countries.isNotEmpty()) add((if (f.countries.size == 1) "Country" else "Countries") to f.countries.joinToString(", "))
        if (f.languages.isNotEmpty()) add((if (f.languages.size == 1) "Language" else "Languages") to f.languages.joinToString(", "))
        if (f.directors.isNotEmpty()) add((if (f.directors.size == 1) "Director" else "Directors") to f.directors.joinToString(", "))
        if (f.writers.isNotEmpty()) add((if (f.writers.size == 1) "Writer" else "Writers") to f.writers.joinToString(", "))
        f.network?.let { add("Network" to it) }
        if (f.companies.isNotEmpty()) add((if (f.companies.size == 1) "Production company" else "Production companies") to f.companies.joinToString(", "))
    }

    /** YouTube id of a trailer address, for its thumbnail; null for other hosts. */
    fun youTubeId(url: String): String? = YT.find(url)?.groupValues?.get(1)
    private val YT = Regex("(?:youtube\\.com/(?:watch\\?(?:.*&)?v=|embed/|shorts/)|youtu\\.be/)([A-Za-z0-9_-]{11})")

    // endregion
}
