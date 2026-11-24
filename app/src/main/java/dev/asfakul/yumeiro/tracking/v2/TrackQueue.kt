/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.tracking.v2

import org.json.JSONArray
import org.json.JSONObject

/**
 * TRACK-2 (item 26.3, Tracking V2): what is waiting to reach the user's trackers, and the pure rules
 * for it. Studied from Zangetsu's TrackerHub (fan every write out to every connected tracker, one
 * failing never blocks the others, incognito and the auto-track switch gate everything) and from
 * V1's own lessons (IG-2, IG-10, IG-11: never lower progress, retry only what failed).
 *
 * What V2 adds over V1 (AnimeSync keeps an in-memory "done" set and gives up after three ticks):
 *  - a queue on disk, so progress made offline or just before the app was closed still arrives;
 *  - one entry per title: a newer episode / chapter replaces an older one that hasn't gone out yet,
 *    and an older one never replaces a newer one (so a re-read of chapter 3 doesn't send 3 after 40);
 *  - per-tracker pending state with backoff, so a tracker that is down is retried later on its own;
 *  - manga and novels (chapters) as well as anime, shows and movies.
 */
enum class TrackKind { ANIME, SHOW, MOVIE, MANGA, NOVEL;
    val reading: Boolean get() = this == MANGA || this == NOVEL
}

data class TrackItem(
    /** Stable per title: kind + source + url (the source's own address of the title). */
    val key: String,
    val kind: TrackKind,
    val title: String,
    val year: Int? = null,
    /** Episode or chapter reached (1-based). */
    val unit: Int,
    val season: Int? = null,
    val total: Int? = null,
    val malId: Int? = null,
    val aniListId: Int? = null,
    val imdbId: String? = null,
    val tmdbId: Int? = null,
    /** Trackers this still has to reach ("anilist", "mal", "kitsu", "trakt", "simkl"). */
    val pending: Set<String>,
    val tries: Int = 0,
    val nextAt: Long = 0L,
    val lastError: String? = null,
    val at: Long = 0L,
)

object TrackQueue {
    const val MAX_TRIES = 8
    const val MAX_ITEMS = 300
    private val BACKOFF_MS = longArrayOf(60_000L, 5 * 60_000L, 30 * 60_000L, 2 * 3_600_000L, 12 * 3_600_000L)

    fun backoff(tries: Int): Long = BACKOFF_MS[(tries - 1).coerceIn(0, BACKOFF_MS.size - 1)]

    /**
     * Adds [item]. Same title already waiting: the higher episode / chapter wins (same unit: the
     * trackers are merged); a lower one is dropped, it would only be skipped by "never lower" anyway.
     * The newest items are kept when the queue is full.
     */
    fun enqueue(queue: List<TrackItem>, item: TrackItem): List<TrackItem> {
        if (item.pending.isEmpty() || item.unit <= 0) return queue
        val old = queue.firstOrNull { it.key == item.key }
        val next = when {
            old == null -> item
            item.unit > old.unit || (item.unit == old.unit && item.season != old.season && (item.season ?: 0) > (old.season ?: 0)) -> item
            item.unit == old.unit && item.season == old.season -> old.copy(pending = old.pending + item.pending, nextAt = 0L, tries = 0)
            else -> return queue
        }
        return (listOf(next) + queue.filterNot { it.key == item.key }).take(MAX_ITEMS)
    }

    fun due(queue: List<TrackItem>, now: Long): List<TrackItem> = queue.filter { it.nextAt <= now && it.pending.isNotEmpty() }

    /**
     * After one round for [key]: trackers in [done] are finished (sent, already up to date, or not
     * applicable), the rest wait with backoff. Gone from the queue when nothing is left, or after
     * [MAX_TRIES] rounds (kept in [TrackV2.log] with the last error so the user can see it).
     */
    fun afterRound(queue: List<TrackItem>, key: String, done: Set<String>, error: String?, now: Long): List<TrackItem> =
        queue.mapNotNull { q ->
            if (q.key != key) return@mapNotNull q
            val left = q.pending - done
            if (left.isEmpty()) return@mapNotNull null
            val tries = q.tries + 1
            if (tries >= MAX_TRIES) null else q.copy(pending = left, tries = tries, nextAt = now + backoff(tries), lastError = error ?: q.lastError)
        }

    fun encode(queue: List<TrackItem>): String = JSONArray().apply { queue.forEach { put(toJson(it)) } }.toString()

    fun decode(text: String): List<TrackItem> {
        val a = JSONArray(text)
        return (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { fromJson(it) } }
    }

    internal fun toJson(q: TrackItem) = JSONObject().put("k", q.key).put("kind", q.kind.name).put("t", q.title).put("u", q.unit)
        .put("p", JSONArray(q.pending.toList())).put("n", q.tries).put("next", q.nextAt).put("at", q.at).apply {
            q.year?.let { put("y", it) }; q.season?.let { put("s", it) }; q.total?.let { put("tot", it) }
            q.malId?.let { put("mal", it) }; q.aniListId?.let { put("al", it) }; q.imdbId?.let { put("imdb", it) }
            q.tmdbId?.let { put("tmdb", it) }; q.lastError?.let { put("err", it) }
        }

    internal fun fromJson(o: JSONObject): TrackItem? {
        val kind = runCatching { TrackKind.valueOf(o.optString("kind")) }.getOrNull() ?: return null
        val key = o.optString("k").ifBlank { return null }
        val pending = o.optJSONArray("p")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }.toSet() }.orEmpty()
        fun int(name: String) = if (o.has(name)) o.optInt(name).takeIf { it > 0 } else null
        return TrackItem(
            key, kind, o.optString("t"), int("y"), o.optInt("u"), int("s"), int("tot"), int("mal"), int("al"),
            o.optString("imdb").ifBlank { null }, int("tmdb"), pending, o.optInt("n"), o.optLong("next"),
            o.optString("err").ifBlank { null }, o.optLong("at"),
        )
    }

    /**
     * The chapter number in a chapter's name ("Chapter 12", "Ch. 12.5 - The end", "Vol.2 Chapter 7",
     * "第12話"): the number after a chapter word, else the last number in the name. Whole chapters
     * only (12.5 counts as 12, trackers count whole chapters). Pure, no regex (start-up rule R15).
     */
    fun chapterNumber(name: String, number: Double? = null): Int? {
        number?.takeIf { it >= 1.0 }?.let { return it.toInt() }
        val lower = name.lowercase()
        val words = listOf("chapter", "chap", "ch.", "ch ", "episode", "ep.", "#", "第")
        for (w in words) {
            val at = lower.indexOf(w)
            if (at >= 0) leadingNumber(lower, at + w.length)?.let { return it }
        }
        // Last run of digits, skipping a volume number ("vol 2" alone is not a chapter).
        var end = lower.length - 1
        while (end >= 0) {
            if (lower[end].isDigit()) {
                var start = end
                while (start > 0 && lower[start - 1].isDigit()) start--
                val before = lower.substring(0, start).trimEnd()
                val isVolume = before.endsWith("vol") || before.endsWith("vol.") || before.endsWith("volume") || before.endsWith("season") || before.endsWith(" s") || before == "s"
                if (!isVolume) return lower.substring(start, end + 1).take(6).toIntOrNull()?.takeIf { it > 0 }
                end = start - 1
            } else end--
        }
        return null
    }

    private fun leadingNumber(s: String, from: Int): Int? {
        var i = from
        while (i < s.length && (s[i] == ' ' || s[i] == '.' || s[i] == ':' || s[i] == '-')) i++
        val start = i
        while (i < s.length && s[i].isDigit()) i++
        return if (i > start) s.substring(start, i).take(6).toIntOrNull()?.takeIf { it > 0 } else null
    }
}
