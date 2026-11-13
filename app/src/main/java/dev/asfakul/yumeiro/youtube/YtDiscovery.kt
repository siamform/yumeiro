/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.youtube

import android.content.Context
import java.text.Normalizer
import java.util.Locale

/**
 * Where one YouTube / YouTube Music shelf gets its items. Unlike the old fixed [YtShelf] list, feeds
 * are planned per person from what they play, search and pin ([YtDiscovery.plan]).
 */
data class YtFeed(
    val kind: Kind,
    /** Kiosk id, search text or video id (for [Kind.RELATED]). */
    val arg: String,
    val title: String,
    /** Small label above the title ("Because you played", "Your topic", "Trending"...). */
    val overline: String,
    val music: Boolean,
) {
    enum class Kind { KIOSK, QUERY, RELATED }
    /** Stable id for caches, keys and saved chip selection. */
    val id: String get() = "${kind.name.lowercase()}:${if (music) "m" else "v"}:${YtDiscovery.fold(arg)}"
    /** Only searches page forever; kiosks and related lists are one page. */
    val pages: Boolean get() = kind == Kind.QUERY
}

/**
 * Plans the YouTube / YouTube Music home. Pure → unit tested (YtDiscoveryTest).
 *
 * Order: personal shelves first (related to recent plays, favourite channels), then the user's
 * own topics, recent searches, a live trending chart, and a small "explore" mix that rotates every
 * day (and on "Shuffle") so the page is never the same fixed list. Everything is de-duplicated.
 */
object YtDiscovery {
    /** Large, varied pools; only a few are shown each day. */
    internal val VIDEO_POOL = listOf(
        "anime openings", "amv edit", "anime news this week", "studio ghibli", "vtuber highlights", "game trailers",
        "speedrun", "film essays", "cooking japan street food", "tokyo walk 4k", "science explained", "retro gaming",
        "movie trailers", "stand up comedy", "music live session", "japanese city pop live", "manga drawing timelapse", "tech reviews",
    )
    internal val MUSIC_POOL = listOf(
        "anime songs", "j-pop hits", "city pop", "lofi hip hop", "vocaloid", "k-pop", "jazz cafe", "synthwave",
        "acoustic covers", "anime ost piano", "rock anthems", "edm festival", "r&b slow jams", "classical focus",
        "workout music", "indie pop", "game soundtrack", "rain ambience",
    )
    val DEFAULT_VIDEO_TOPICS = listOf("Anime openings", "AMV")
    val DEFAULT_MUSIC_TOPICS = listOf("J-Pop", "Lo-fi")

    data class Input(
        val music: Boolean,
        /** Most recent first. */
        val history: List<YtItem>,
        val topics: List<String>,
        /** Most recent first. */
        val searches: List<String>,
        /** Changes once a day (e.g. epoch day) plus the user's shuffle count. */
        val rotation: Long,
        val maxFeeds: Int = 12,
    )

    fun plan(input: Input): List<YtFeed> {
        val m = input.music
        val out = LinkedHashMap<String, YtFeed>()
        fun add(f: YtFeed) { if (f.arg.isNotBlank() && out.size < input.maxFeeds) out.putIfAbsent(dedupeKey(f), f) }

        val mine = input.history.filter { it.music == m }
        // 1. "Because you played": related lists for the two most recent distinct plays.
        mine.distinctBy { it.id }.take(2).forEach {
            add(YtFeed(YtFeed.Kind.RELATED, it.id, it.title.take(60), if (m) "Because you listened to" else "Because you watched", m))
        }
        // 2. Channels / artists you come back to (at least twice).
        mine.mapNotNull { it.channel.removeSuffix(" - Topic").trim().takeIf { c -> c.isNotEmpty() } }
            .groupingBy { it }.eachCount().filter { it.value >= 2 }
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(2).forEach { (channel, _) ->
                add(YtFeed(YtFeed.Kind.QUERY, if (m) "$channel songs" else channel, channel, if (m) "Artist you play" else "Channel you watch", m))
            }
        // 3. Your topics, in your order.
        input.topics.forEach { add(YtFeed(YtFeed.Kind.QUERY, it, it, "Your topic", m)) }
        // 4. Your newest searches (skipping ones that are already a topic).
        input.searches.take(4).forEach { if (out.size < input.maxFeeds) add(YtFeed(YtFeed.Kind.QUERY, it, it, "From your searches", m)) }
        // 5. Live chart.
        if (m) add(YtFeed(YtFeed.Kind.KIOSK, "trending_music", "Trending music", "Trending", true))
        else {
            add(YtFeed(YtFeed.Kind.KIOSK, "trending_gaming", "Trending in gaming", "Trending", false))
            add(YtFeed(YtFeed.Kind.KIOSK, "live", "Live now", "Trending", false))
        }
        // 6. Explore mix: 3 picks from the pool, rotated daily / on shuffle.
        rotate(if (m) MUSIC_POOL else VIDEO_POOL, input.rotation).take(3).forEach {
            add(YtFeed(YtFeed.Kind.QUERY, it, it.replaceFirstChar { c -> c.titlecase(Locale.ROOT) }, "Explore", m))
        }
        return out.values.toList()
    }

    /** Deterministic shuffle: same rotation → same order (so the page doesn't jump while open). */
    internal fun <T> rotate(pool: List<T>, rotation: Long): List<T> {
        if (pool.isEmpty()) return pool
        val r = java.util.Random(rotation * -7046029254386353131L + 7)
        return pool.shuffled(r)
    }

    private fun dedupeKey(f: YtFeed) = if (f.kind == YtFeed.Kind.QUERY) "q:" + fold(f.arg.removeSuffix(" songs")) else f.id

    /** Lower-case, accents removed, punctuation collapsed: "J-Pop" == "jpop" == "J Pop". */
    fun fold(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), "")

    /** Cleans a typed topic; null when empty or too long to be a topic. */
    fun cleanTopic(raw: String): String? = raw.trim().replace(Regex("\\s+"), " ").takeIf { it.length in 2..60 }

    /** Adds [topic] to the front (moving it if it already exists), capped at [max]. */
    fun withTopic(topics: List<String>, topic: String, max: Int = 12): List<String> {
        val t = cleanTopic(topic) ?: return topics
        return (listOf(t) + topics.filterNot { fold(it) == fold(t) }).take(max)
    }

    fun withoutTopic(topics: List<String>, topic: String): List<String> = topics.filterNot { fold(it) == fold(topic) }

    /** "Related" ideas for the chip row after a search: YouTube's own autocomplete, minus duplicates. */
    fun relatedChips(query: String, suggestions: List<String>, existing: List<String>, max: Int = 6): List<String> {
        val seen = (existing + query).map(::fold).toMutableSet()
        return suggestions.mapNotNull { cleanTopic(it) }.filter { seen.add(fold(it)) }.take(max)
    }
}

/** The user's pinned YouTube / YouTube Music topics (device-only, like history). */
object YtTopics {
    private const val PREFS = "yt_topics"
    private fun key(music: Boolean) = if (music) "music" else "video"
    private const val SHUFFLE = "shuffle"

    fun load(c: Context, music: Boolean): List<String> {
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains(key(music))) return if (music) YtDiscovery.DEFAULT_MUSIC_TOPICS else YtDiscovery.DEFAULT_VIDEO_TOPICS
        return p.getString(key(music), "").orEmpty().split('\n').filter { it.isNotBlank() }
    }

    fun save(c: Context, music: Boolean, topics: List<String>) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key(music), topics.joinToString("\n")).apply()
    }

    fun shuffleCount(c: Context): Long = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(SHUFFLE, 0L)
    fun bumpShuffle(c: Context): Long {
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val n = p.getLong(SHUFFLE, 0L) + 1
        p.edit().putLong(SHUFFLE, n).apply()
        return n
    }
}
