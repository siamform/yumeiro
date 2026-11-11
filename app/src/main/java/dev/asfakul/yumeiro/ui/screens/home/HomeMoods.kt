/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.home

import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.online.OnlineRow

/**
 * "Mood of the hour" (気分). Yumeiro picks the mood itself — from the time of day by default —
 * and fills it from online: songs from YouTube Music, a matching video shelf from YouTube, and a
 * row from your extensions whose title fits the mood. "Shuffle" jumps to another mood (or one of
 * your own YouTube Music topics). Nothing here is stored; it is a suggestion, not a setting.
 */
internal data class HomeMood(
    val key: String,
    /** One kanji for the mood card. */
    val kanji: String,
    val title: String,
    val line: String,
    val musicQuery: String,
    val videoQuery: String,
    /** Words that make an extension row a fit ("Romance", "Action", …). */
    val rowWords: List<String>,
)

internal object HomeMoods {
    val MORNING = HomeMood("morning", "朝", "Morning light", "Soft, bright and easy", "morning acoustic chill", "anime opening 4k", listOf("slice", "comedy", "family", "popular", "trending"))
    val DAY = HomeMood("day", "昼", "Daytime energy", "Upbeat songs and action picks", "upbeat jpop hits", "amv edit", listOf("action", "adventure", "sports", "trending", "popular"))
    val EVENING = HomeMood("evening", "夕", "Golden hour", "City pop and stories to sink into", "city pop 80s japanese", "anime movie trailer", listOf("drama", "romance", "movie", "film"))
    val NIGHT = HomeMood("night", "夜", "Midnight drift", "Lo-fi, slow songs and late-night watches", "lofi hip hop", "anime night ambience", listOf("horror", "thriller", "mystery", "romance", "psychological"))
    val ANIME = HomeMood("anime", "歌", "Anime songs", "Openings, endings and J-pop", "anime songs jpop", "anime opening 4k", listOf("anime", "seasonal", "airing"))
    val HITS = HomeMood("hits", "熱", "Top hits", "What everyone is playing", "top hits this week", "trending music video", listOf("trending", "popular", "top"))
    val FOCUS = HomeMood("focus", "静", "Deep focus", "Calm sounds to work to", "study music focus", "relaxing anime scenery", listOf("documentary", "slice", "drama"))

    val all = listOf(MORNING, DAY, EVENING, NIGHT, ANIME, HITS, FOCUS)

    /** The mood for an hour of the day (0–23). */
    fun forHour(hour: Int): HomeMood = when (hour) {
        in 5..10 -> MORNING
        in 11..16 -> DAY
        in 17..20 -> EVENING
        else -> NIGHT
    }

    /** A mood built from one of your own YouTube Music topics. */
    fun fromTopic(topic: String) = HomeMood("topic:$topic", "好", topic, "One of your topics", topic, topic, topic.split(' ').filter { it.length > 2 })

    /** The next mood after [current] in a shuffled order that never repeats the current one. */
    fun shuffle(current: HomeMood, topics: List<String>, random: java.util.Random): HomeMood {
        val pool = (all + topics.map(::fromTopic)).filter { it.key != current.key }
        return pool[random.nextInt(pool.size)]
    }

    /**
     * Picks titles from your extensions for a mood: rows whose title contains one of the mood's
     * words first, then (if none) one row picked by [seed], so every mood still shows something.
     */
    fun extensionPicks(mood: HomeMood, channels: List<Pair<String, List<OnlineRow>>>, seed: Long, max: Int = 12): List<OnlineItem> {
        val rows = channels.flatMap { it.second }
        val fit = rows.filter { r -> mood.rowWords.any { w -> r.title.contains(w, ignoreCase = true) } }
        val pick = fit.ifEmpty { if (rows.isEmpty()) emptyList() else listOf(rows[java.util.Random(seed).nextInt(rows.size)]) }
        return pick.flatMap { it.items }.distinctBy { it.key }.shuffled(java.util.Random(seed)).take(max)
    }
}
