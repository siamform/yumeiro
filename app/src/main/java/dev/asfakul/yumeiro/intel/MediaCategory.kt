/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.intel

import dev.asfakul.yumeiro.online.OnlineDetail
import dev.asfakul.yumeiro.online.OnlineKind

/**
 * B20: the media a taste belongs to. Anime, movies, series, manga and novels each keep their own taste (an anime
 * habit never decides film picks on its own, see R27-B20-B22-PLAN "Cross-media").
 */
enum class MediaCategory(val code: String, val label: String) {
    ANIME("anime", "Anime"),
    MOVIE("movie", "Movies"),
    SERIES("series", "Series"),
    MANGA("manga", "Manga"),
    NOVEL("novel", "Novels"),
    MUSIC("music", "Music"),
    YOUTUBE("youtube", "YouTube");

    companion object {
        fun of(code: String?): MediaCategory? = entries.firstOrNull { it.code == code }

        /** Pure: an online title page → its category; null for live TV and pages that aren't a title. */
        fun of(d: OnlineDetail): MediaCategory? = when {
            d.kind == OnlineKind.LIVE -> null
            dev.asfakul.yumeiro.online.anime.AnimeIds.isAnime(d) -> ANIME
            d.kind == OnlineKind.MOVIE -> MOVIE
            d.kind == OnlineKind.SERIES -> SERIES
            else -> ofType(d.type) ?: if (d.episodes.values.sumOf { it.size } > 1) SERIES else if (d.playData != null) MOVIE else null
        }

        /**
         * Pure: an extension's type string (CloudStream TvType names, Aniyomi/JS labels and the catalog's own
         * "TV series" / "Anime") → category. Null when the type says nothing (live, music, unknown).
         */
        fun ofType(type: String?): MediaCategory? {
            val t = type?.trim()?.lowercase()?.replace(" ", "")?.replace("_", "") ?: return null
            return when {
                t.isEmpty() -> null
                t.startsWith("anime") || t == "ova" || t == "ona" -> ANIME
                t.contains("live") || t == "music" || t == "audio" || t == "podcast" -> null
                t == "movie" || t == "film" || t == "torrent" || t == "documentary" || t == "cartoonmovie" -> MOVIE
                t.contains("series") || t.contains("drama") || t == "tv" || t == "show" || t == "cartoon" || t == "tvshow" -> SERIES
                else -> null
            }
        }
    }
}

/**
 * B20: what a tag says about a title. AniList puts every tag in a category (Cast-Traits, Theme-Action,
 * Setting-Universe, Technical, Demographic...); TMDB keywords are story themes; genres stay genres. The weight is
 * how much this aspect counts when two titles are compared (plan: "Preference and similarity").
 */
enum class Aspect(val code: Char, val label: String, val weight: Double) {
    CHARACTERS('c', "characters", 1.2),
    THEMES('t', "story", 1.3),
    SETTING('s', "setting", 0.7),
    GENRE('g', "genre", 0.8),
    STYLE('y', "style", 0.5),
    CREATORS('k', "creators", 0.3);

    companion object {
        fun of(c: Char): Aspect? = entries.firstOrNull { it.code == c }

        /** Pure: an AniList tag category ("Cast-Traits", "Theme-Drama", "Setting-Scene", "Technical") → aspect. */
        fun ofAniList(category: String?): Aspect {
            val c = category.orEmpty()
            return when {
                c.startsWith("Cast") -> CHARACTERS
                c.startsWith("Setting") -> SETTING
                c.startsWith("Technical") || c.startsWith("Demographic") -> STYLE
                else -> THEMES
            }
        }
    }
}

/** One tag of a title: [weight] 0..1 (AniList rank / 100; genres and keywords fixed), [spoiler] never shown. */
data class Tag(val name: String, val aspect: Aspect, val weight: Float, val spoiler: Boolean = false) {
    /** The matching key: same name, any case or spacing, is the same tag. */
    val id: String get() = norm(name)

    companion object {
        private val SEP = Regex("[\\s_\\-]+")
        fun norm(s: String): String = s.trim().lowercase().replace(SEP, " ")
    }
}
