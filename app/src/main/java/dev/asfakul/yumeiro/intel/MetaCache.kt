/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.intel

import android.content.Context
import android.util.Log
import dev.asfakul.yumeiro.profiles.Profiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * B20: real, public metadata for a title (who the characters are, what the story is about, the setting), so a
 * taste is more than "Action, Drama". Anime, manga and light novels come from AniList's public API (the phone asks
 * it directly: AniList refuses Cloudflare Worker addresses, research Q5), films and series from Yumeiro Cloud's
 * TMDB title page (genres + keywords; the TMDB token stays on the Worker).
 *
 * The cache is not personal (public tags of a title), so it is shared by every profile in `cacheDir/intel-meta`
 * (30 days). Which titles were looked up is never written there, only the public answer under a hash of its id.
 * AniList calls are spaced 2.5 s apart (its limit is 30-90 a minute) and a 429 pauses the queue.
 */
object MetaCache {
    private const val TAG = "MetaCache"
    private const val TTL = 30L * 86_400_000L
    private const val SPACING_MS = 2_500L

    /** One title's public metadata. [popularity]: AniList members / TMDB votes; [score] 0..100. */
    data class Meta(
        val title: String,
        val category: MediaCategory,
        val ids: TasteSignals.Ids,
        val tags: List<Tag>,
        val year: Int? = null,
        val cover: String? = null,
        val popularity: Int = 0,
        val score: Int = 0,
        val format: String? = null,
        val description: String? = null,
        val releasedAt: String? = null,
        val adult: Boolean = false,
        val alt: List<String> = emptyList(),
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = ConcurrentHashMap<String, Pair<String, TasteSignals.Entry>>()
    /** Title key → when its lookup last failed (offline, 429): tried again 10 minutes later at the earliest. */
    private val tried = ConcurrentHashMap<String, Long>()
    private const val RETRY_MS = 10 * 60_000L
    private val running = java.util.concurrent.atomic.AtomicBoolean(false)
    private val aniGate = Mutex()
    @Volatile private var lastAniAt = 0L
    /** AniList spacing (rate limit 30/min while degraded); tests shorten it. */
    @Volatile internal var spacingMs = SPACING_MS
    @Volatile private var pausedUntil = 0L

    /** AniList GraphQL: (query, variables) → `data`, or throws. Tests replace it. */
    @Volatile internal var aniList: suspend (String, JSONObject) -> JSONObject? = { q, v ->
        withContext(Dispatchers.IO) {
            dev.asfakul.yumeiro.online.anime.AniList.graphqlOrThrow(dev.asfakul.yumeiro.network.NetworkClient.get(), q, v, null)
        }
    }

    /** Yumeiro Cloud hub GET of a path such as `/title?type=tv&id=1`, or null. Tests replace it. */
    @Volatile internal var hubGet: suspend (String) -> String? = { path ->
        val host = dev.asfakul.yumeiro.BuildConfig.CLOUD_HOST
        if (host.isBlank()) null
        else dev.asfakul.yumeiro.sources.classics.YumeiroClassicsProvider.httpGet("https://$host/v1/hub$path", timeoutSec = 8)
    }

    /**
     * When true, [enqueue] does nothing. On by default in unit tests (Robolectric), so the many tests that save a
     * bookmark or play an episode never call AniList; the metadata tests turn it off and use fakes.
     */
    @Volatile internal var disabled = android.os.Build.FINGERPRINT == "robolectric"

    // region the queue

    /** Looks [e] up in the background and puts its tags on the entry (only in the profile that asked). */
    fun enqueue(c: Context, e: TasteSignals.Entry) {
        if (disabled || e.category == MediaCategory.MUSIC || e.category == MediaCategory.YOUTUBE) return
        val now = System.currentTimeMillis()
        val last = tried[e.key]
        if (last != null && (last == 0L || now - last < RETRY_MS)) return
        tried[e.key] = 0L // 0 = queued or running
        val app = c.applicationContext
        queue[e.key] = Profiles.currentId(app) to e
        if (running.compareAndSet(false, true)) scope.launch {
            try {
                while (true) {
                    val (key, job) = queue.entries.firstOrNull()?.let { it.key to it.value } ?: break
                    queue.remove(key)
                    val (profile, entry) = job
                    val result = runCatching { lookup(app, entry) }
                    result.onSuccess { meta ->
                        // Nothing matched: the source's genres stay, and the title is not asked about again (metaAt set).
                        TasteSignals.applyMeta(app, profile, key, meta?.ids ?: TasteSignals.Ids(), meta?.tags.orEmpty(), meta?.year)
                        tried.remove(key)
                    }.onFailure {
                        Log.i(TAG, "lookup failed: ${it.javaClass.simpleName}")
                        tried[key] = System.currentTimeMillis()
                    }
                }
            } finally { running.set(false) }
        }
    }

    /** Tests: waits until the queue is empty. */
    internal suspend fun idle(timeoutMs: Long = 10_000) { withTimeoutOrNull(timeoutMs) { while (running.get() || queue.isNotEmpty()) delay(20) } }

    internal fun resetForTests() { queue.clear(); tried.clear(); pausedUntil = 0; lastAniAt = 0; spacingMs = SPACING_MS }

    // endregion

    /** Metadata of [e]: cache first, then AniList / Yumeiro Cloud. Null when nothing matched; throws when offline. */
    suspend fun lookup(c: Context, e: TasteSignals.Entry): Meta? = when (e.category) {
        MediaCategory.ANIME -> anime(c, e.ids, e.title, e.year, "ANIME", null)
        MediaCategory.MANGA -> anime(c, e.ids, e.title, e.year, "MANGA", null)
        MediaCategory.NOVEL -> anime(c, e.ids, e.title, e.year, "MANGA", "NOVEL")
        MediaCategory.MOVIE, MediaCategory.SERIES -> film(c, e.ids, e.title, e.year, if (e.category == MediaCategory.MOVIE) "movie" else "tv")
        else -> null
    }

    // region AniList

    internal const val MEDIA_FIELDS = "id idMal type format isAdult title{romaji english native} synonyms startDate{year month day} " +
        "seasonYear genres tags{name rank category isMediaSpoiler isAdult} popularity averageScore meanScore coverImage{large} " +
        "studios(isMain:true){nodes{name}} staff(perPage:3,sort:RELEVANCE){edges{role node{name{full}}}} description(asHtml:false)"

    private suspend fun anime(c: Context, ids: TasteSignals.Ids, title: String, year: Int?, type: String, format: String?): Meta? {
        ids.anilist?.let { id -> return cached(c, "al:$id") { byId(id) } }
        val clean = dev.asfakul.yumeiro.online.anime.AnimeIds.cleanTitle(title)
        if (clean.isBlank()) return null
        return cached(c, "alq:$type:${format.orEmpty()}:${Tag.norm(clean)}:${year ?: 0}") {
            val q = "query(\$s:String,\$t:MediaType,\$f:MediaFormat,\$m:Int){Page(perPage:5){media(search:\$s,type:\$t,format:\$f,idMal:\$m,isAdult:false){$MEDIA_FIELDS}}}"
            val v = JSONObject().put("s", clean).put("t", type).apply { format?.let { put("f", it) }; ids.mal?.let { put("m", it) } }
            val list = ani(q, v)?.optJSONObject("Page")?.optJSONArray("media") ?: return@cached null
            val parsed = (0 until list.length()).mapNotNull { list.optJSONObject(it)?.let(::parseAniList) }
            pick(parsed, clean, year)
        }
    }

    private suspend fun byId(id: Int): Meta? =
        ani("query(\$id:Int){Media(id:\$id){$MEDIA_FIELDS}}", JSONObject().put("id", id))?.optJSONObject("Media")?.let(::parseAniList)

    /** One AniList call through the gate (spaced, paused after a 429). */
    internal suspend fun ani(q: String, v: JSONObject): JSONObject? = aniGate.withLock {
        val now = System.currentTimeMillis()
        if (now < pausedUntil) throw java.io.IOException("AniList paused (rate limit)")
        val wait = lastAniAt + spacingMs - now
        if (wait > 0) delay(wait)
        lastAniAt = System.currentTimeMillis()
        try { aniList(q, v) } catch (e: dev.asfakul.yumeiro.online.anime.AnimeIds.HttpError) {
            if (e.code == 429) pausedUntil = System.currentTimeMillis() + 60_000
            throw e
        }
    }

    /** Pure: the best of [found] for [title] (same name first, then the closest year), or null when none is close. */
    internal fun pick(found: List<Meta>, title: String, year: Int?): Meta? {
        if (found.isEmpty()) return null
        val want = Tag.norm(title)
        val named = found.filter { m -> (listOf(m.title) + m.alt).any { Tag.norm(it) == want } }
        val pool = named.ifEmpty { found.filter { m -> (listOf(m.title) + m.alt).any { close(Tag.norm(it), want) } } }
        if (pool.isEmpty()) return null
        return if (year == null) pool.first() else pool.minByOrNull { m -> m.year?.let { kotlin.math.abs(it - year) } ?: 5 }
    }

    /** One name contains the other (\"Mirai Nikki\" / \"Mirai Nikki (TV)\") and they are not wildly different lengths. */
    private fun close(a: String, b: String): Boolean =
        a.isNotEmpty() && b.isNotEmpty() && (a.contains(b) || b.contains(a)) && minOf(a.length, b.length) * 2 >= maxOf(a.length, b.length)

    /** Pure: an AniList Media object → [Meta]. Adult tags are dropped; spoiler tags kept but marked. */
    internal fun parseAniList(m: JSONObject): Meta? {
        val id = m.optInt("id").takeIf { it > 0 } ?: return null
        val t = m.optJSONObject("title")
        val name = t?.optString("english")?.takeIf { it.isNotBlank() && it != "null" } ?: t?.optString("romaji")?.takeIf { it.isNotBlank() && it != "null" } ?: return null
        val alt = listOfNotNull(t?.optString("romaji"), t?.optString("english"), t?.optString("native"))
            .filter { it.isNotBlank() && it != "null" && it != name } + (m.optJSONArray("synonyms")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty().take(6))
        val type = m.optString("type")
        val format = m.optString("format").ifBlank { null }?.takeIf { it != "null" }
        val cat = when { type == "ANIME" -> MediaCategory.ANIME; format == "NOVEL" -> MediaCategory.NOVEL; else -> MediaCategory.MANGA }
        val tags = ArrayList<Tag>()
        m.optJSONArray("genres")?.let { g -> for (i in 0 until g.length()) g.optString(i).takeIf { it.isNotBlank() && it != "Hentai" }?.let { tags += Tag(it, Aspect.GENRE, 1f) } }
        m.optJSONArray("tags")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                if (o.optBoolean("isAdult")) continue
                val rank = o.optInt("rank")
                if (rank < 20) continue
                val tagName = o.optString("name")
                if (tagName.isBlank()) continue
                tags += Tag(tagName, Aspect.ofAniList(o.optString("category")), rank / 100f, o.optBoolean("isMediaSpoiler"))
            }
        }
        m.optJSONObject("studios")?.optJSONArray("nodes")?.let { n -> for (i in 0 until minOf(2, n.length())) n.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }?.let { tags += Tag(it, Aspect.CREATORS, 0.6f) } }
        m.optJSONObject("staff")?.optJSONArray("edges")?.let { e ->
            for (i in 0 until e.length()) {
                val o = e.optJSONObject(i) ?: continue
                val role = o.optString("role")
                if (!role.contains("Story", true) && !role.contains("Original", true) && !role.contains("Art", true)) continue
                o.optJSONObject("node")?.optJSONObject("name")?.optString("full")?.takeIf { it.isNotBlank() }?.let { tags += Tag(it, Aspect.CREATORS, 0.6f) }
            }
        }
        val sd = m.optJSONObject("startDate")
        val year = m.optInt("seasonYear").takeIf { it > 1900 } ?: sd?.optInt("year")?.takeIf { it > 1900 }
        val released = sd?.let { d -> val y = d.optInt("year"); if (y > 1900) "%04d-%02d-%02d".format(y, d.optInt("month").coerceIn(1, 12), d.optInt("day").coerceIn(1, 28)) else null }
        return Meta(
            title = name, category = cat,
            ids = TasteSignals.Ids(anilist = id, mal = m.optInt("idMal").takeIf { it > 0 }),
            tags = tags.distinctBy { it.id }, year = year,
            cover = m.optJSONObject("coverImage")?.optString("large")?.takeIf { it.startsWith("https://") },
            popularity = m.optInt("popularity"), score = m.optInt("averageScore").takeIf { it > 0 } ?: m.optInt("meanScore"),
            format = format, description = m.optString("description").takeIf { it.isNotBlank() && it != "null" }?.let(::plain)?.take(600),
            releasedAt = released, adult = m.optBoolean("isAdult"), alt = alt.distinct(),
        )
    }

    private val TAGS = Regex("<[^>]+>")
    private val SPACES = Regex("\\s+")
    private fun plain(s: String): String = s.replace(TAGS, " ").replace("&quot;", "\"").replace("&amp;", "&").replace(SPACES, " ").trim()

    // endregion

    // region films and series (Yumeiro Cloud / TMDB)

    private suspend fun film(c: Context, ids: TasteSignals.Ids, title: String, year: Int?, kind: String): Meta? {
        var tmdb = ids.tmdb?.takeIf { ids.tmdbKind == null || ids.tmdbKind == kind }
        if (tmdb == null && ids.imdb != null) tmdb = dev.asfakul.yumeiro.sources.lookup.StreamLookup.tmdbForImdb(ids.imdb, kind == "tv")
        if (tmdb == null) {
            val clean = title.trim().ifBlank { return null }
            val body = hubGet("/search?q=${java.net.URLEncoder.encode(clean, "UTF-8")}") ?: throw java.io.IOException("hub search failed")
            val items = dev.asfakul.yumeiro.sources.hub.HubDiscover.parsePage(body)?.items.orEmpty().filter { it.kind == kind }
            val named = items.filter { Tag.norm(it.title) == Tag.norm(clean) }.ifEmpty { items.filter { close(Tag.norm(it.title), Tag.norm(clean)) } }
            tmdb = (if (year == null) named.firstOrNull() else named.minByOrNull { i -> i.year?.let { kotlin.math.abs(it - year) } ?: 5 })?.id?.toInt() ?: return null
        }
        val id = tmdb
        return cached(c, "tm:$kind:$id") { hubGet("/title?type=$kind&id=$id")?.let { parseHubTitle(it, kind) } }
    }

    /** A catalog title by its TMDB id (cached 30 days; shared with the lookups above). */
    internal suspend fun filmById(c: Context, id: Int, kind: String): Meta? =
        cached(c, "tm:$kind:$id") { hubGet("/title?type=$kind&id=$id")?.let { parseHubTitle(it, kind) } }

    /** Pure: `/v1/hub/title` → [Meta]: genres (genre aspect) and TMDB keywords (story themes). */
    internal fun parseHubTitle(body: String, kind: String): Meta? {
        val o = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val id = o.optInt("tmdb").takeIf { it > 0 } ?: return null
        val title = o.optString("title").ifBlank { return null }
        val tags = ArrayList<Tag>()
        o.optJSONArray("genres")?.let { g -> for (i in 0 until g.length()) g.optString(i).takeIf { it.isNotBlank() }?.let { tags += Tag(it, Aspect.GENRE, 1f) } }
        o.optJSONArray("keywords")?.let { k -> for (i in 0 until k.length()) k.optString(i).takeIf { it.isNotBlank() }?.let { tags += Tag(it, Aspect.THEMES, 0.7f) } }
        val rating = o.optDouble("rating").takeIf { !it.isNaN() } ?: 0.0
        return Meta(
            title = title, category = if (kind == "movie") MediaCategory.MOVIE else MediaCategory.SERIES,
            ids = TasteSignals.Ids(tmdb = id, tmdbKind = kind, imdb = o.optString("imdb").takeIf { it.startsWith("tt") }),
            tags = tags.distinctBy { it.id }, year = o.optInt("year").takeIf { it > 1900 }, cover = o.optString("poster").takeIf { it.startsWith("https://") },
            popularity = o.optInt("votes"), score = (rating * 10).toInt(), description = o.optString("overview").ifBlank { null }?.take(600),
            releasedAt = o.optString("date").takeIf { it.length == 10 }, alt = listOfNotNull(o.optString("original").ifBlank { null }),
        )
    }

    // endregion

    // region disk cache

    private fun dir(c: Context) = File(c.applicationContext.cacheDir, "intel-meta")
    private fun fileOf(c: Context, key: String): File {
        val h = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
        return File(dir(c), "$h.json")
    }

    /** Cached [Meta] for [key], or [load] it (a null answer is cached too, as "nothing matched", for a day). */
    internal suspend fun cached(c: Context, key: String, load: suspend () -> Meta?): Meta? {
        val f = fileOf(c, key)
        val now = System.currentTimeMillis()
        val hit = withContext(Dispatchers.IO) { runCatching { if (f.isFile) JSONObject(f.readText()) else null }.getOrNull() }
        if (hit != null) {
            val at = hit.optLong("at")
            val none = hit.optBoolean("none")
            if (now - at < (if (none) 86_400_000L else TTL)) return if (none) null else hit.optJSONObject("m")?.let(::decode)
        }
        val m = load()
        withContext(Dispatchers.IO) {
            runCatching {
                f.parentFile?.mkdirs()
                val o = JSONObject().put("at", now)
                if (m == null) o.put("none", true) else o.put("m", encode(m))
                dev.asfakul.yumeiro.data.SafeJsonFile.write(f, o.toString())
            }
        }
        return m
    }

    internal fun encode(m: Meta): JSONObject = JSONObject().apply {
        put("t", m.title); put("c", m.category.code)
        put("id", TasteSignals.encode(TasteSignals.Entry("x", m.category, m.title, ids = m.ids)).optJSONObject("id") ?: JSONObject())
        put("g", JSONArray().apply { m.tags.forEach { t -> put(JSONArray().put(t.name).put(t.aspect.code.toString()).put((t.weight * 100).toInt()).put(if (t.spoiler) 1 else 0)) } })
        m.year?.let { put("y", it) }; m.cover?.let { put("i", it) }
        put("pop", m.popularity); put("sc", m.score)
        m.format?.let { put("fm", it) }; m.description?.let { put("ds", it) }; m.releasedAt?.let { put("rd", it) }
        if (m.adult) put("ad", 1)
        if (m.alt.isNotEmpty()) put("alt", JSONArray(m.alt))
    }

    internal fun decode(o: JSONObject): Meta? {
        val cat = MediaCategory.of(o.optString("c")) ?: return null
        val e = TasteSignals.decode(JSONObject().put("k", "x").put("c", cat.code).put("t", o.optString("t")).put("id", o.optJSONObject("id") ?: JSONObject()).put("g", o.optJSONArray("g") ?: JSONArray())) ?: return null
        return Meta(
            title = o.optString("t"), category = cat, ids = e.ids, tags = e.tags, year = o.optInt("y").takeIf { it > 1900 },
            cover = o.optString("i").ifBlank { null }, popularity = o.optInt("pop"), score = o.optInt("sc"),
            format = o.optString("fm").ifBlank { null }, description = o.optString("ds").ifBlank { null },
            releasedAt = o.optString("rd").ifBlank { null }, adult = o.optInt("ad") == 1,
            alt = o.optJSONArray("alt")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty(),
        )
    }

    // endregion
}
