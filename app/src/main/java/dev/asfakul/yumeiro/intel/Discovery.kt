/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.intel

import android.content.Context
import android.util.Log
import dev.asfakul.yumeiro.data.SafeJsonFile
import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.profiles.Profiles
import dev.asfakul.yumeiro.sources.hub.HubDiscover
import dev.asfakul.yumeiro.sources.hub.HubDiscover.toOnlineItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * B20 S3: the "For you" shelves of the open profile (`intel/feed.json`): ranked real titles per category with the
 * reasons, refreshed at most every 12 hours (sooner, but not within 30 minutes, when the taste changed). Titles the
 * profile turned away ("Not interested") never come back; an undo puts them back. Opening a pick is remembered so a
 * finished pick can ask once, outside playback, how it was (spec: completion prompt).
 */
object Discovery {
    private const val TAG = "Discovery"
    const val FILE = "intel/feed.json"
    private const val REFRESH_MS = 12 * 3_600_000L
    private const val CHANGED_MS = 30 * 60_000L
    private const val SIZE = 16
    private const val PROMPT_WITHIN = 30L * 86_400_000L

    /** Categories with a For You shelf. Music and YouTube already have their own play-based discovery (audit). */
    val SHELVES = listOf(MediaCategory.ANIME, MediaCategory.MOVIE, MediaCategory.SERIES, MediaCategory.MANGA, MediaCategory.NOVEL)

    data class Rec(
        val key: String,
        val category: MediaCategory,
        val title: String,
        val cover: String? = null,
        val year: Int? = null,
        val ids: TasteSignals.Ids = TasteSignals.Ids(),
        /** Taste match 0..100 (the weighted cosine, before popularity damping). */
        val match: Int = 0,
        val reasons: List<Similarity.Reason> = emptyList(),
        val because: List<String> = emptyList(),
        val gem: Boolean = false,
        val new: Boolean = false,
        val popularity: Int = 0,
        val score: Int = 0,
        val format: String? = null,
        val tags: List<Tag> = emptyList(),
    )

    data class Shelf(val category: MediaCategory, val recs: List<Rec>, val at: Long, val basis: String, val crossFrom: MediaCategory? = null)

    private data class Opened(val anilist: Int?, val tmdb: Int?, val title: String, val at: Long)

    private val lock = Any()
    private var loadedFor: String? = null
    private var shelves = HashMap<MediaCategory, Shelf>()
    private var opened = ArrayList<Opened>()
    private val _state = MutableStateFlow<Map<MediaCategory, Shelf>>(emptyMap())
    /** The open profile's shelves (UI). */
    val state: StateFlow<Map<MediaCategory, Shelf>> = _state.asStateFlow()
    private val gates = MediaCategory.entries.associateWith { Mutex() }
    @Volatile internal var clock: () -> Long = { System.currentTimeMillis() }

    // region store

    private fun ensure(c: Context) {
        val id = Profiles.currentId(c)
        if (loadedFor == id) return
        shelves = HashMap(); opened = ArrayList()
        SafeJsonFile.read(Profiles.file(c, FILE)) { JSONObject(it) }.value?.let { o ->
            o.optJSONObject("s")?.let { s -> s.keys().forEach { k -> MediaCategory.of(k)?.let { cat -> s.optJSONObject(k)?.let { decodeShelf(cat, it) }?.let { shelves[cat] = it } } } }
            o.optJSONArray("o")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let { opened += Opened(it.optInt("al").takeIf { v -> v > 0 }, it.optInt("tm").takeIf { v -> v > 0 }, it.optString("t"), it.optLong("at")) } }
        }
        loadedFor = id
        _state.value = HashMap(shelves)
    }

    private fun save(c: Context) {
        val text = synchronized(lock) {
            JSONObject().put("v", 1)
                .put("s", JSONObject().apply { shelves.forEach { (k, v) -> put(k.code, encodeShelf(v)) } })
                .put("o", JSONArray().apply { opened.takeLast(100).forEach { put(JSONObject().put("t", it.title).put("at", it.at).apply { it.anilist?.let { v -> put("al", v) }; it.tmdb?.let { v -> put("tm", v) } }) } })
                .toString()
        }
        runCatching { SafeJsonFile.write(Profiles.file(c, FILE), text) }.onFailure { Log.w(TAG, "save failed: ${it.javaClass.simpleName}") }
    }

    fun shelf(c: Context, cat: MediaCategory): Shelf? = synchronized(lock) { ensure(c); shelves[cat] }

    /** Forgets what is in memory (profile reset / restore). */
    fun unload() = synchronized(lock) { loadedFor = null; shelves = HashMap(); opened = ArrayList(); _state.value = emptyMap() }

    internal fun resetForTests() { unload(); clock = { System.currentTimeMillis() } }

    // endregion

    // region refresh

    /** Which taste a category's shelf uses: its own, or (too little of its own) the closest one, labelled. */
    internal fun tasteFor(entries: List<TasteSignals.Entry>, cat: MediaCategory, now: Long): TasteProfile? {
        val own = TasteProfile.build(entries, cat, now)
        if (own.titles >= TasteProfile.MIN_TITLES) return own
        val near = when (cat) {
            MediaCategory.MANGA -> listOf(MediaCategory.ANIME, MediaCategory.NOVEL)
            MediaCategory.NOVEL -> listOf(MediaCategory.MANGA, MediaCategory.ANIME)
            MediaCategory.ANIME -> listOf(MediaCategory.MANGA)
            MediaCategory.MOVIE -> listOf(MediaCategory.SERIES)
            MediaCategory.SERIES -> listOf(MediaCategory.MOVIE)
            else -> emptyList()
        }
        return near.map { TasteProfile.build(entries, it, now) }.firstOrNull { it.titles >= TasteProfile.MIN_TITLES }
    }

    /** What the taste rests on: a shelf built on other seeds or many more titles is out of date. */
    internal fun basis(t: TasteProfile): String = t.category.code + ":" + (t.titles / 3) + ":" + t.seeds.take(4).joinToString(",") { it.key.hashCode().toString(36) }

    /** Pure: ranks [found] for [taste] and turns them into shelf cards. */
    internal fun rank(cat: MediaCategory, taste: TasteProfile, found: List<Candidates.Found>, entries: List<TasteSignals.Entry>, now: Long, size: Int = SIZE): List<Rec> {
        val cands = found.map { f ->
            val m = f.meta
            Similarity.Candidate(keyOf(m), cat, m.title, m.tags, m.popularity, m.score, m.year, m.releasedAt, f.because)
        }
        val idf = Similarity.idf(cands.map { it.tags } + entries.filter { it.category == taste.category }.map { it.tags })
        val gems = Similarity.gems(cands)
        val byKey = found.associateBy { keyOf(it.meta) }
        val scored = cands.map { Similarity.score(taste, it, idf) }.filter { it.relevance >= 0.05 && it.reasons.isNotEmpty() }
        return Similarity.mmr(scored, size).map { s ->
            val m = byKey.getValue(s.candidate.key).meta
            Rec(
                s.candidate.key, cat, m.title, m.cover, m.year, m.ids, (s.relevance * 100).toInt().coerceIn(1, 99), s.reasons,
                s.candidate.because.take(2), s.candidate.key in gems, Similarity.isNew(m.releasedAt, now), m.popularity, m.score, m.format,
                m.tags.filter { !it.spoiler }.take(24),
            )
        }
    }

    internal fun keyOf(m: MetaCache.Meta): String = when {
        m.ids.anilist != null -> "al:${m.ids.anilist}"
        m.ids.tmdb != null -> "tm:${m.ids.tmdbKind ?: "movie"}:${m.ids.tmdb}"
        else -> "t:" + Tag.norm(m.title)
    }

    /** Builds a category's shelf when due (or [force]); keeps the old one when offline. Returns the current shelf. */
    suspend fun refresh(c: Context, cat: MediaCategory, force: Boolean = false): Shelf? = gates.getValue(cat).withLock {
        withContext(Dispatchers.IO) {
            val app = c.applicationContext
            runCatching { TasteImport.ensure(app) }
            val profile = Profiles.currentId(app)
            val now = clock()
            val entries = TasteSignals.entries(app)
            val taste = tasteFor(entries, cat, now)
            val old = shelf(app, cat)
            if (taste == null) {
                // Not enough taste yet: an old shelf from a reset taste goes, nothing is invented.
                if (old != null) synchronized(lock) { shelves.remove(cat); _state.value = HashMap(shelves) }.also { save(app) }
                return@withContext null
            }
            val b = basis(taste)
            val due = old == null || force || now - old.at > REFRESH_MS || (old.basis != b && now - old.at > CHANGED_MS)
            if (!due) return@withContext old
            val known = Candidates.Known.of(entries)
            val found = runCatching {
                when (cat) {
                    MediaCategory.MOVIE, MediaCategory.SERIES -> Candidates.films(app, cat, taste, known)
                    else -> Candidates.aniList(cat, taste, known, now)
                }
            }.onFailure { Log.i(TAG, "refresh ${cat.code}: ${it.javaClass.simpleName}") }.getOrNull()
            if (found.isNullOrEmpty()) return@withContext old
            val recs = rank(cat, taste, found, entries, now)
            if (Profiles.currentId(app) != profile) return@withContext null // the profile changed meanwhile
            val shelf = Shelf(cat, recs, now, b, taste.category.takeIf { it != cat })
            synchronized(lock) { ensure(app); shelves[cat] = shelf; _state.value = HashMap(shelves) }
            save(app)
            shelf
        }
    }

    private val similarMemo = object : LinkedHashMap<String, List<Rec>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Rec>>?) = size > 16
    }

    /**
     * "Similar by story" for one anime / manga / novel page: titles that share its strongest character, story and
     * setting tags (AniList), ranked against this title (not the profile), minus what this profile already finished,
     * rated or turned away. Empty when the title can't be matched on AniList.
     */
    suspend fun similar(c: Context, ref: TasteSignals.Ref): List<Rec> = withContext(Dispatchers.IO) {
        if (ref.adult || ref.category !in setOf(MediaCategory.ANIME, MediaCategory.MANGA, MediaCategory.NOVEL)) return@withContext emptyList()
        val app = c.applicationContext
        val memoKey = Profiles.currentId(app) + "|" + ref.key
        synchronized(similarMemo) { similarMemo[memoKey] }?.let { return@withContext it }
        val probe = TasteSignals.Entry(ref.key, ref.category, ref.title, ref.cover, ref.year, TasteSignals.get(app, ref.key)?.ids?.merge(ref.ids) ?: ref.ids)
        val seed = MetaCache.lookup(app, probe)?.takeIf { it.ids.anilist != null && it.tags.size >= 3 } ?: return@withContext emptyList()
        val entries = TasteSignals.entries(app)
        val known = Candidates.Known.of(entries)
        val found = Candidates.likeTitle(seed).filter { !known.has(it.meta) && it.meta.category == seed.category }
        if (found.isEmpty()) return@withContext emptyList()
        val cands = found.map { f -> Similarity.Candidate(keyOf(f.meta), f.meta.category, f.meta.title, f.meta.tags, f.meta.popularity, f.meta.score, f.meta.year, f.meta.releasedAt, f.because) }
        val idf = Similarity.idf(cands.map { it.tags } + listOf(seed.tags))
        val gems = Similarity.gems(cands)
        val byKey = found.associateBy { keyOf(it.meta) }
        val now = clock()
        val recs = Similarity.mmr(cands.map { Similarity.like(seed.tags, it, idf) }.filter { it.reasons.isNotEmpty() && it.relevance >= 0.08 }, 12).map { s ->
            val m = byKey.getValue(s.candidate.key).meta
            Rec(s.candidate.key, m.category, m.title, m.cover, m.year, m.ids, (s.relevance * 100).toInt().coerceIn(1, 99), s.reasons, s.candidate.because.take(1),
                s.candidate.key in gems, Similarity.isNew(m.releasedAt, now), m.popularity, m.score, m.format, m.tags.filter { !it.spoiler }.take(24))
        }
        synchronized(similarMemo) { similarMemo[memoKey] = recs }
        recs
    }

    // endregion

    // region actions

    private fun refOf(r: Rec) = TasteSignals.Ref(r.key, r.category, r.title, r.cover, r.year, r.ids)

    /** "Not interested": the pick leaves the shelf for good and its tags count a little against similar ones. */
    fun dismiss(c: Context, r: Rec): TasteSignals.Entry? {
        val before = TasteSignals.dismiss(c, refOf(r))
        TasteSignals.applyMeta(c, Profiles.currentId(c), r.key, r.ids, r.tags, r.year)
        synchronized(lock) {
            ensure(c)
            shelves[r.category]?.let { s -> shelves[r.category] = s.copy(recs = s.recs.filter { it.key != r.key }) }
            _state.value = HashMap(shelves)
        }
        save(c)
        return before
    }

    /** Undo of [dismiss]: the card goes back where it was. */
    fun undoDismiss(c: Context, r: Rec, before: TasteSignals.Entry?, index: Int) {
        TasteSignals.undoDismiss(c, r.key, before)
        synchronized(lock) {
            ensure(c)
            shelves[r.category]?.let { s -> if (s.recs.none { it.key == r.key }) shelves[r.category] = s.copy(recs = s.recs.toMutableList().apply { add(index.coerceIn(0, size), r) }) }
            _state.value = HashMap(shelves)
        }
        save(c)
    }

    /** A pick was opened: remembered so finishing it can ask once how it was. Nothing in Incognito. */
    fun opened(c: Context, r: Rec) {
        if (dev.asfakul.yumeiro.security.Privacy.incognito.value) return
        synchronized(lock) { ensure(c); opened.removeAll { it.title == Tag.norm(r.title) }; opened += Opened(r.ids.anilist, r.ids.tmdb, Tag.norm(r.title), clock()) }
        save(c)
    }

    private fun fromPick(e: TasteSignals.Entry): Boolean = e.fromDiscovery || synchronized(lock) {
        opened.any { o -> (o.anilist != null && o.anilist == e.ids.anilist) || (o.tmdb != null && o.tmdb == e.ids.tmdb) || o.title == Tag.norm(e.title) }
    }

    /**
     * The completion prompt (spec): a title found through For You, finished in the last 30 days, never rated and
     * never asked about. One at a time; asked once (Skip counts as asked). Null when there is nothing to ask.
     */
    fun promptFor(c: Context): TasteSignals.Entry? {
        val now = clock()
        synchronized(lock) { ensure(c) }
        return TasteSignals.entries(c).filter { it.completed && it.feedback == null && it.promptedAt == 0L && now - it.completedAt < PROMPT_WITHIN && fromPick(it) }
            .maxByOrNull { it.completedAt }
    }

    /** Where a pick opens. Films and series: the catalog title (played through the user's own sources); anime: the
     *  catalog title found by name and year when there is one; null = search the installed sources by name. */
    suspend fun resolve(r: Rec): OnlineItem? = withContext(Dispatchers.IO) {
        when (r.category) {
            MediaCategory.MOVIE, MediaCategory.SERIES -> r.ids.tmdb?.let { id ->
                val kind = if (r.category == MediaCategory.MOVIE) "movie" else "tv"
                HubDiscover.Item(r.title, kind, id.toLong(), r.cover, r.year, null, r.score / 10.0, r.popularity, emptyList(), false).toOnlineItem()
            }
            MediaCategory.ANIME -> runCatching {
                if (!HubDiscover.available()) return@runCatching null
                val body = MetaCache.hubGet("/search?q=${java.net.URLEncoder.encode(r.title, "UTF-8")}") ?: return@runCatching null
                val items = HubDiscover.parsePage(body)?.items.orEmpty().filter { it.anime || it.genres.any { g -> g.equals("Animation", true) } }
                val named = items.filter { Tag.norm(it.title) == Tag.norm(r.title) }
                (if (r.year == null) named.firstOrNull() else named.minByOrNull { i -> i.year?.let { kotlin.math.abs(it - r.year) } ?: 9 })
                    ?.takeIf { r.year == null || it.year == null || kotlin.math.abs(it.year - r.year) <= 1 }?.toOnlineItem()
            }.getOrNull()
            else -> null
        }
    }

    // endregion

    // region JSON

    private fun encodeShelf(s: Shelf) = JSONObject().put("at", s.at).put("b", s.basis).apply { s.crossFrom?.let { put("x", it.code) } }
        .put("r", JSONArray().apply { s.recs.forEach { put(encodeRec(it)) } })

    private fun decodeShelf(cat: MediaCategory, o: JSONObject): Shelf? {
        val a = o.optJSONArray("r") ?: return null
        return Shelf(cat, (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let { r -> decodeRec(cat, r) } }, o.optLong("at"), o.optString("b"), MediaCategory.of(o.optString("x").ifBlank { null }))
    }

    internal fun encodeRec(r: Rec): JSONObject = JSONObject().apply {
        put("k", r.key); put("t", r.title); r.cover?.let { put("i", it) }; r.year?.let { put("y", it) }
        r.ids.anilist?.let { put("al", it) }; r.ids.mal?.let { put("mal", it) }; r.ids.tmdb?.let { put("tm", it) }; r.ids.tmdbKind?.let { put("tk", it) }
        put("m", r.match); put("p", r.popularity); put("s", r.score); r.format?.let { put("f", it) }
        if (r.gem) put("gem", true); if (r.new) put("new", true)
        put("w", JSONArray().apply { r.reasons.forEach { put(JSONObject().put("n", it.name).put("a", it.aspect.code.toString()).put("s", it.share)) } })
        put("b", JSONArray(r.because))
        put("g", JSONArray().apply { r.tags.forEach { put(JSONObject().put("n", it.name).put("a", it.aspect.code.toString()).put("w", it.weight.toDouble())) } })
    }

    internal fun decodeRec(cat: MediaCategory, o: JSONObject): Rec? {
        val key = o.optString("k").ifBlank { return null }
        val title = o.optString("t").ifBlank { return null }
        fun int(k: String) = o.optInt(k).takeIf { it > 0 }
        val reasons = o.optJSONArray("w")?.let { a -> (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { r -> Aspect.of(r.optString("a").firstOrNull() ?: ' ')?.let { Similarity.Reason(r.optString("n"), it, r.optDouble("s")) } } } }.orEmpty()
        val tags = o.optJSONArray("g")?.let { a -> (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { t -> Aspect.of(t.optString("a").firstOrNull() ?: ' ')?.let { Tag(t.optString("n"), it, t.optDouble("w").toFloat()) } } } }.orEmpty()
        return Rec(
            key, cat, title, o.optString("i").ifBlank { null }, int("y"),
            TasteSignals.Ids(int("al"), int("mal"), int("tm"), o.optString("tk").ifBlank { null }),
            o.optInt("m"), reasons, o.optJSONArray("b")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty(),
            o.optBoolean("gem"), o.optBoolean("new"), o.optInt("p"), o.optInt("s"), o.optString("f").ifBlank { null }, tags,
        )
    }

    // endregion
}
