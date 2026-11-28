/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.intel

import android.content.Context
import android.util.Log
import dev.asfakul.yumeiro.data.SafeJsonFile
import dev.asfakul.yumeiro.profiles.Profiles
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * B20 (R27-B20-B22-PLAN "Event collection and weighting"): what one profile watched, read and said about each
 * title, kept on the device in that profile's own `intel/signals.json` (default profile `filesDir/intel/`, others
 * `profiles/<id>/intel/`, removed with the profile). Nothing here leaves the phone; the server never holds a taste.
 *
 * An entry is one title. Events only move it forward when something new happened (a part reached the start,
 * substantial or done mark, a rewatch, a rating), so the player's frequent progress calls cost a compare, not a
 * write. Incognito records nothing, and 18+ titles are never kept (they must never shape picks or AI context).
 */
object TasteSignals {
    private const val TAG = "TasteSignals"
    const val FILE = "intel/signals.json"
    const val MAX_TITLES = 2000
    private const val MAX_PARTS = 400
    private const val OPEN_GAP = 6 * 3_600_000L
    private const val REWATCH_GAP = 7 * 86_400_000L
    private const val DROP_AFTER = 21 * 86_400_000L
    private const val HALF_LIFE_DAYS = 180.0
    private const val FADE_FLOOR = 0.25

    enum class Feedback(val code: String, val label: String, val factor: Double) {
        LOVED("loved", "Loved it", 2.0),
        LIKED("liked", "Liked it", 1.3),
        OKAY("okay", "It was okay", 0.6),
        NOT_FOR_ME("no", "Not for me", -1.0);
        companion object { fun of(c: String?): Feedback? = entries.firstOrNull { it.code == c } }
    }

    data class Ids(val anilist: Int? = null, val mal: Int? = null, val tmdb: Int? = null, val tmdbKind: String? = null, val imdb: String? = null) {
        fun merge(o: Ids) = Ids(anilist ?: o.anilist, mal ?: o.mal, tmdb ?: o.tmdb, tmdbKind ?: o.tmdbKind, imdb ?: o.imdb)
        val empty: Boolean get() = anilist == null && mal == null && tmdb == null && imdb == null
    }

    /**
     * How a screen names a title to this store. [key]: `o|api|url` (online), `m|provider|url` (manga),
     * `n|plugin|path` (novel), `al|<id>` / `tm|<kind>|<id>` (a recommendation seen only in Discovery).
     * [sourceTags]: the extension's own genres, used until real metadata arrives.
     */
    data class Ref(
        val key: String, val category: MediaCategory, val title: String, val cover: String? = null, val year: Int? = null,
        val ids: Ids = Ids(), val sourceTags: List<String> = emptyList(), val adult: Boolean = false,
    )

    /** One episode / chapter: best share watched, started for real, when it was finished, watched again since. */
    data class Part(val best: Float, val engaged: Boolean, val doneAt: Long, val again: Boolean = false)

    data class Entry(
        val key: String,
        val category: MediaCategory,
        val title: String,
        val cover: String? = null,
        val year: Int? = null,
        val ids: Ids = Ids(),
        val tags: List<Tag> = emptyList(),
        /** When real metadata (AniList / TMDB) was put on [tags]; 0 = only the source's genres so far. */
        val metaAt: Long = 0,
        val opens: Int = 0,
        val lastOpenAt: Long = 0,
        val parts: Map<String, Part> = emptyMap(),
        val totalParts: Int = 0,
        val completions: Int = 0,
        val completedAt: Long = 0,
        val rewatches: Int = 0,
        val firstAt: Long = 0,
        val lastAt: Long = 0,
        val feedback: Feedback? = null,
        val feedbackAt: Long = 0,
        val saved: Boolean = false,
        val favorite: Boolean = false,
        val fromDiscovery: Boolean = false,
        val promptedAt: Long = 0,
        /** "Not interested" on a recommendation (never shown again; its strongest tags count a little against). */
        val dismissedAt: Long = 0,
    ) {
        val engagedParts: Int get() = parts.values.count { it.engaged }
        val doneParts: Int get() = parts.values.count { it.doneAt > 0 }
        val started: Boolean get() = engagedParts > 0
        val completed: Boolean get() = completions > 0
        /** How far through the title (0..1): one part = its best share; a series = parts finished / all parts. */
        val progress: Float get() = when {
            completed -> 1f
            totalParts > 1 -> (doneParts.toFloat() / totalParts).coerceIn(0f, 1f)
            else -> parts.values.maxOfOrNull { it.best } ?: 0f
        }
        val substantial: Boolean get() = completed || doneParts >= 3 || progress >= 0.4f
        /** Started, under 10 % and untouched for 21 days, never finished nor rated. */
        fun dropped(now: Long): Boolean = started && !completed && feedback == null && progress < 0.1f && now - lastAt > DROP_AFTER

        /**
         * Plan weights: open +0.15, start +0.3, substantial +0.6, complete +0.8 (not proof of enjoyment), rewatch
         * +0.25 each (4 at most), saved +0.2, favorite +0.4; saturated `1 − e^(−x)`; rating ×2.0 / ×1.3 / ×0.6,
         * "Not for me" and "Not interested" negative; dropped ×0.5; faded with a 180-day half-life (floor 0.25).
         */
        fun weight(now: Long): Double {
            var x = 0.0
            if (opens > 0) x += 0.15
            if (started) x += 0.3
            if (substantial) x += 0.6
            if (completed) x += 0.8
            x += min(rewatches, 4) * 0.25
            if (saved) x += 0.2
            if (favorite) x += 0.4
            val sat = 1 - exp(-x)
            val days = max(0L, now - max(lastAt, feedbackAt)) / 86_400_000.0
            val fade = max(FADE_FLOOR, 0.5.pow(days / HALF_LIFE_DAYS))
            val fb = feedback
            return when {
                fb == Feedback.NOT_FOR_ME -> -max(sat, 0.5) * fade
                dismissedAt > 0 && !started -> -0.3
                fb != null -> sat.coerceAtLeast(0.3) * fb.factor * fade
                dropped(now) -> sat * 0.5 * fade
                else -> sat * fade
            }
        }
    }

    private val _version = MutableStateFlow(0)
    /** Bumps on every change (Discovery and the title page buttons listen). */
    val version: StateFlow<Int> = _version.asStateFlow()

    private val lock = Any()
    private var map = LinkedHashMap<String, Entry>()
    private var loadedFor: String? = null
    private var imported = false
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "taste-signals").apply { isDaemon = true } }
    private val pending = AtomicBoolean(false)

    /** Tests: when the store thinks it is. */
    @Volatile internal var clock: () -> Long = { System.currentTimeMillis() }

    private var dirty = false

    /** This store's file of profile [id] (the default profile keeps `filesDir/intel/`). */
    private fun fileFor(c: Context, id: String): java.io.File =
        if (id == Profiles.DEFAULT) java.io.File(c.applicationContext.filesDir, FILE)
        else java.io.File(java.io.File(c.applicationContext.filesDir, "profiles/$id"), FILE)

    private fun text(): String = JSONObject().put("v", 1).put("imported", imported)
        .put("titles", JSONArray().apply { map.values.forEach { put(encode(it)) } }).toString()

    private fun ensure(c: Context) {
        val id = Profiles.currentId(c)
        if (loadedFor == id) return
        // A profile switch with a change not yet on disk: it goes to the profile it belongs to first.
        val was = loadedFor
        if (was != null && dirty) runCatching { SafeJsonFile.write(fileFor(c, was), text()) }
        dirty = false
        map = LinkedHashMap()
        imported = false
        val o = SafeJsonFile.read(fileFor(c, id)) { JSONObject(it) }.value
        if (o != null) {
            imported = o.optBoolean("imported")
            o.optJSONArray("titles")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let(::decode)?.let { map[it.key] = it } }
        }
        loadedFor = id
    }

    fun entries(c: Context): List<Entry> = synchronized(lock) { ensure(c); map.values.toList() }
    fun get(c: Context, key: String): Entry? = synchronized(lock) { ensure(c); map[key] }
    fun imported(c: Context): Boolean = synchronized(lock) { ensure(c); imported }

    private fun recording(ref: Ref): Boolean =
        !ref.adult && !dev.asfakul.yumeiro.security.Privacy.incognito.value && ref.key.isNotBlank() && ref.title.isNotBlank()

    /** Applies [change] to the title of [ref] (created when new); writes only when the entry really changed. */
    private fun update(c: Context, ref: Ref, change: (Entry, Long) -> Entry): Entry? {
        if (!recording(ref)) return null
        val now = clock()
        val next: Entry
        synchronized(lock) {
            ensure(c)
            val old = map[ref.key]
            val base = (old ?: Entry(ref.key, ref.category, ref.title, firstAt = now)).let { e ->
                e.copy(
                    title = ref.title.ifBlank { e.title }, cover = ref.cover ?: e.cover, year = ref.year ?: e.year,
                    ids = ref.ids.merge(e.ids),
                    tags = if (e.metaAt == 0L && ref.sourceTags.isNotEmpty() && e.tags.isEmpty()) sourceTags(ref.sourceTags) else e.tags,
                )
            }
            next = change(base, now)
            if (next == old) return old
            map.remove(ref.key)
            map[ref.key] = next
            trim(now)
        }
        changed(c)
        if (next.metaAt == 0L) MetaCache.enqueue(c, next)
        return next
    }

    /** The source's genres as tags until real metadata is in (genre aspect, full weight). */
    internal fun sourceTags(raw: List<String>): List<Tag> =
        dev.asfakul.yumeiro.sources.hub.GenreCatalog.clean(raw, 12).map { Tag(it, Aspect.GENRE, 1f) }

    private fun trim(now: Long) {
        if (map.size <= MAX_TITLES) return
        val drop = map.values.sortedBy { kotlin.math.abs(it.weight(now)) * 10 + it.lastAt / 1e13 }.take(map.size - MAX_TITLES).map { it.key }
        drop.forEach { map.remove(it) }
    }

    /** A title page was opened (counts once per 6 hours). */
    fun opened(c: Context, ref: Ref, fromDiscovery: Boolean = false): Entry? = update(c, ref) { e, now ->
        val again = now - e.lastOpenAt < OPEN_GAP
        e.copy(
            opens = if (again) e.opens else e.opens + 1, lastOpenAt = if (again) e.lastOpenAt else now,
            lastAt = if (again) e.lastAt else max(e.lastAt, now), fromDiscovery = e.fromDiscovery || fromDiscovery,
        )
    }

    /**
     * The player's progress on [part] (episode data; null for a film) of a title with [totalParts] parts. A part
     * starts at 2 minutes (or 5 % after one minute), is done at 90 %; the title is complete when a film is done, the
     * last episode is done or 80 % of the episodes are. A finished part watched again from the start 7 days or more
     * later is a rewatch.
     */
    fun played(c: Context, ref: Ref, part: String?, positionMs: Long, durationMs: Long, partIndex: Int = -1, totalParts: Int = 0): Entry? {
        if (positionMs <= 0) return null
        val frac = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        val engaged = positionMs >= 120_000 || (frac >= 0.05f && positionMs >= 60_000)
        val done = durationMs > 0 && frac >= 0.9f
        return progress(c, ref, part, frac, engaged, done, partIndex, totalParts)
    }

    /**
     * Reading: a chapter [part] counts as started when [engaged] (past page 3 / a fifth of a novel chapter) and as
     * done when read to the end; [isLast] marks the last chapter (completes the title).
     */
    fun read(c: Context, ref: Ref, part: String, share: Float, engaged: Boolean, done: Boolean, isLast: Boolean = false): Entry? =
        progress(c, ref, part, share, engaged || done, done, if (isLast) Int.MAX_VALUE else -1, 0)

    private fun progress(c: Context, ref: Ref, part: String?, share: Float, engaged: Boolean, done: Boolean, partIndex: Int, totalParts: Int): Entry? {
        val pk = partKey(part)
        // Most progress calls change nothing: answer them without a write.
        synchronized(lock) {
            ensure(c)
            val p = map[ref.key]?.parts?.get(pk)
            if (p != null && !done && p.best >= share && (p.engaged || !engaged) && !(p.doneAt > 0 && share < 0.3f)) return map[ref.key]
        }
        return update(c, ref) { e, now ->
            val old = e.parts[pk]
            var again = old?.again ?: false
            var rewatches = e.rewatches
            var doneAt = old?.doneAt ?: 0L
            if (old != null && old.doneAt > 0 && share < 0.3f && now - old.doneAt >= REWATCH_GAP) again = true
            if (done) {
                if (doneAt == 0L) doneAt = now
                else if (again) { rewatches++; doneAt = now; again = false }
            }
            val nextPart = Part(max(old?.best ?: 0f, share), (old?.engaged ?: false) || engaged, doneAt, again)
            var parts = e.parts - pk + (pk to nextPart)
            if (parts.size > MAX_PARTS) parts = parts.entries.drop(parts.size - MAX_PARTS).associate { it.key to it.value }
            val total = max(e.totalParts, totalParts)
            val doneCount = parts.values.count { it.doneAt > 0 }
            val complete = done && (part == null || (partIndex >= 0 && (partIndex == Int.MAX_VALUE || (total > 0 && partIndex >= total - 1)))) ||
                (total >= 2 && doneCount >= kotlin.math.ceil(total * 0.8).toInt())
            val changedPart = nextPart != old
            e.copy(
                parts = parts, totalParts = total, rewatches = rewatches,
                completions = if (complete && e.completions == 0) 1 else e.completions,
                completedAt = if (complete && e.completions == 0) now else e.completedAt,
                lastAt = if (changedPart) now else e.lastAt,
            )
        }
    }

    internal fun partKey(part: String?): String = if (part.isNullOrEmpty()) "_" else (part.hashCode().toUInt().toString(36) + part.length.toString(36))

    /** Loved / Liked / Okay / Not for me; null clears the rating. */
    fun setFeedback(c: Context, ref: Ref, feedback: Feedback?): Entry? =
        update(c, ref) { e, now -> e.copy(feedback = feedback, feedbackAt = if (feedback != null) now else 0L, promptedAt = max(e.promptedAt, now)) }

    fun setSaved(c: Context, ref: Ref, saved: Boolean, favorite: Boolean? = null): Entry? =
        update(c, ref) { e, now -> e.copy(saved = saved, favorite = favorite ?: e.favorite, lastAt = if (saved && !e.saved) now else e.lastAt) }

    /**
     * My List / Favorites / library state of [key] ("Completed" set by hand counts as complete). [ref] creates the
     * entry when the title is new to the store; without it only a known title is updated.
     */
    fun syncSaved(c: Context, key: String, ref: Ref?, saved: Boolean, favorite: Boolean, completed: Boolean = false): Entry? {
        val known = get(c, key)
        val r = ref ?: known?.let { Ref(it.key, it.category, it.title, it.cover, it.year, it.ids) } ?: return null
        return update(c, r) { e, now ->
            e.copy(
                saved = saved, favorite = favorite, lastAt = if ((saved && !e.saved) || (favorite && !e.favorite)) now else e.lastAt,
                completions = if (completed && e.completions == 0) 1 else e.completions,
                completedAt = if (completed && e.completions == 0) now else e.completedAt,
            )
        }
    }

    /** The completion question was shown (asked once per title). */
    fun prompted(c: Context, ref: Ref): Entry? = update(c, ref) { e, now -> e.copy(promptedAt = now) }

    /** "Not interested" on a recommendation; returns what was there before for Undo. */
    fun dismiss(c: Context, ref: Ref): Entry? {
        val before = get(c, ref.key)
        update(c, ref) { e, now -> e.copy(dismissedAt = now) }
        return before
    }

    /** Undo of [dismiss]: puts the entry back as it was (or removes one the dismiss created). */
    fun undoDismiss(c: Context, key: String, before: Entry?) {
        synchronized(lock) {
            ensure(c)
            if (before == null) map.remove(key) else map[key] = before
        }
        changed(c)
    }

    /** Real metadata for [key] (from [MetaCache]); written only into the profile that asked ([profileId]). */
    fun applyMeta(c: Context, profileId: String, key: String, ids: Ids, tags: List<Tag>, year: Int?) {
        synchronized(lock) {
            ensure(c)
            if (loadedFor != profileId) return
            val e = map[key] ?: return
            map[key] = e.copy(ids = e.ids.merge(ids), tags = if (tags.isNotEmpty()) tags else e.tags, year = e.year ?: year, metaAt = clock())
        }
        changed(c)
    }

    /** One-time import from the existing history and libraries is done (see [TasteImport]). */
    fun markImported(c: Context, items: List<Entry>) {
        synchronized(lock) {
            ensure(c)
            for (e in items) if (e.key !in map) map[e.key] = e
            imported = true
            trim(clock())
        }
        changed(c)
        // The most recent titles get real tags now; older ones when they are opened again (AniList is spaced 2.5 s).
        items.filter { it.metaAt == 0L }.sortedByDescending { it.lastAt }.take(150).forEach { MetaCache.enqueue(c, it) }
    }

    /** Removes titles (history delete, profile reset). */
    fun forget(c: Context, pick: (Entry) -> Boolean) {
        synchronized(lock) { ensure(c); map.values.filter(pick).map { it.key }.forEach { map.remove(it) } }
        changed(c)
    }

    private fun changed(c: Context) {
        synchronized(lock) { dirty = true }
        _version.value++
        val app = c.applicationContext
        if (pending.compareAndSet(false, true)) writer.execute {
            pending.set(false)
            save(app)
        }
    }

    private fun save(c: Context) {
        val (file, text) = synchronized(lock) {
            val id = loadedFor ?: return
            if (!dirty) return
            dirty = false
            fileFor(c, id) to text()
        }
        runCatching { SafeJsonFile.write(file, text) }.onFailure { Log.w(TAG, "save failed: ${it.javaClass.simpleName}") }
    }

    /** Waits for pending writes (tests, profile switch, backup). */
    fun flush() { runCatching { writer.submit {}.get(5, TimeUnit.SECONDS) } }

    /** Forgets what is in memory (the profile's files were deleted or restored); the next call reads the disk. */
    fun unload() { flush(); synchronized(lock) { map = LinkedHashMap(); loadedFor = null; imported = false; dirty = false }; _version.value++ }

    internal fun resetForTests() { flush(); synchronized(lock) { map = LinkedHashMap(); loadedFor = null; imported = false; dirty = false }; clock = { System.currentTimeMillis() } }

    // region JSON (short keys: the file can hold 2,000 titles)

    internal fun encode(e: Entry): JSONObject = JSONObject().apply {
        put("k", e.key); put("c", e.category.code); put("t", e.title)
        e.cover?.let { put("i", it) }; e.year?.let { put("y", it) }
        JSONObject().apply {
            e.ids.anilist?.let { put("al", it) }; e.ids.mal?.let { put("mal", it) }; e.ids.tmdb?.let { put("tm", it) }
            e.ids.tmdbKind?.let { put("tk", it) }; e.ids.imdb?.let { put("im", it) }
        }.takeIf { it.length() > 0 }?.let { put("id", it) }
        if (e.tags.isNotEmpty()) put("g", JSONArray().apply {
            e.tags.forEach { t -> put(JSONArray().put(t.name).put(t.aspect.code.toString()).put((t.weight * 100).toInt()).apply { if (t.spoiler) put(1) }) }
        })
        if (e.metaAt > 0) put("ma", e.metaAt)
        if (e.opens > 0) { put("o", e.opens); put("oa", e.lastOpenAt) }
        if (e.parts.isNotEmpty()) put("p", JSONObject().apply {
            e.parts.forEach { (k, p) -> put(k, JSONArray().put((p.best * 1000).toInt()).put(if (p.engaged) 1 else 0).put(p.doneAt).put(if (p.again) 1 else 0)) }
        })
        if (e.totalParts > 0) put("tp", e.totalParts)
        if (e.completions > 0) { put("cp", e.completions); put("ca", e.completedAt) }
        if (e.rewatches > 0) put("rw", e.rewatches)
        put("f", e.firstAt); put("l", e.lastAt)
        e.feedback?.let { put("fb", it.code); put("fa", e.feedbackAt) }
        if (e.saved) put("s", 1)
        if (e.favorite) put("fv", 1)
        if (e.fromDiscovery) put("d", 1)
        if (e.promptedAt > 0) put("pa", e.promptedAt)
        if (e.dismissedAt > 0) put("x", e.dismissedAt)
    }

    internal fun decode(o: JSONObject): Entry? {
        val key = o.optString("k").ifBlank { return null }
        val cat = MediaCategory.of(o.optString("c")) ?: return null
        val id = o.optJSONObject("id")
        val ids = if (id == null) Ids() else Ids(
            id.optInt("al").takeIf { it > 0 }, id.optInt("mal").takeIf { it > 0 }, id.optInt("tm").takeIf { it > 0 },
            id.optString("tk").ifBlank { null }, id.optString("im").ifBlank { null },
        )
        val tags = o.optJSONArray("g")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                val t = a.optJSONArray(i) ?: return@mapNotNull null
                val aspect = t.optString(1).firstOrNull()?.let(Aspect::of) ?: return@mapNotNull null
                Tag(t.optString(0).ifBlank { return@mapNotNull null }, aspect, (t.optInt(2) / 100f).coerceIn(0f, 1f), t.optInt(3) == 1)
            }
        }.orEmpty()
        val parts = o.optJSONObject("p")?.let { p ->
            p.keys().asSequence().mapNotNull { k ->
                val v = p.optJSONArray(k) ?: return@mapNotNull null
                k to Part((v.optInt(0) / 1000f).coerceIn(0f, 1f), v.optInt(1) == 1, v.optLong(2), v.optInt(3) == 1)
            }.toMap()
        }.orEmpty()
        return Entry(
            key = key, category = cat, title = o.optString("t"), cover = o.optString("i").ifBlank { null },
            year = o.optInt("y").takeIf { it in 1800..2999 }, ids = ids, tags = tags, metaAt = o.optLong("ma"),
            opens = o.optInt("o"), lastOpenAt = o.optLong("oa"), parts = parts, totalParts = o.optInt("tp"),
            completions = o.optInt("cp"), completedAt = o.optLong("ca"), rewatches = o.optInt("rw"),
            firstAt = o.optLong("f"), lastAt = o.optLong("l"),
            feedback = Feedback.of(o.optString("fb").ifBlank { null }), feedbackAt = o.optLong("fa"),
            saved = o.optInt("s") == 1, favorite = o.optInt("fv") == 1, fromDiscovery = o.optInt("d") == 1,
            promptedAt = o.optLong("pa"), dismissedAt = o.optLong("x"),
        )
    }

    // endregion

    /** IDF used by the taste model: `ln((N+1)/(df+1)) + 1` (a tag on every title says little). Pure. */
    internal fun idf(n: Int, df: Int): Double = ln((n + 1.0) / (df + 1.0)) + 1.0
}
