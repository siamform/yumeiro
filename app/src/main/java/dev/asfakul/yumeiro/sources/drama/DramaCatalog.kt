/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.drama

/**
 * Yumeiro Drama — the built-in short-drama hub.
 *
 * Short dramas (vertical 1–3 minute episodes from apps like DramaBox, ShortMax, NetShort,
 * DramaWave, GoodShort and FlexTV) are usually locked inside one app per studio. A
 * [DramaCatalog] is one public index that lists many of those studios; the provider merges
 * every catalog into one place with platform shelves, search, episodes and streams.
 *
 * Catalogs are plain Kotlin adapters (no third-party plugin code runs), so new indexes can be
 * added without touching the player or the UI. Each adapter only reads public pages and
 * public JSON that the index itself serves to its web player; it never bypasses logins,
 * payments or DRM.
 */
interface DramaCatalog {
    /** Stable id, used in internal URLs (`drama:<id>|…`). */
    val id: String
    /** Human label shown as the server name in the player. */
    val label: String
    /** Platforms (studios) this catalog can list as shelves. */
    val platforms: List<DramaPlatform>

    suspend fun shelf(platform: DramaPlatform, page: Int): List<DramaShow>
    suspend fun search(query: String): List<DramaShow>
    suspend fun series(url: String): DramaSeries
    suspend fun streams(series: DramaSeries, episode: Int): DramaStreams
}

data class DramaPlatform(val slug: String, val name: String)

data class DramaShow(
    val title: String,
    val url: String,
    val cover: String?,
    val episodes: Int?,
    val platform: String?,
    val language: String? = null,
)

data class DramaSeries(
    val title: String,
    val url: String,
    val cover: String?,
    val plot: String?,
    val platform: String?,
    val episodes: Int,
    val language: String?,
    /** Catalog-specific playback hints (ids, endpoints, session ticket). */
    val hints: Map<String, String> = emptyMap(),
)

data class DramaSubtitle(val url: String, val language: String?, val label: String?)

data class DramaStreams(
    /** Quality (e.g. "720") → media URL; "" when the catalog doesn't say. */
    val sources: Map<String, String>,
    val subtitles: List<DramaSubtitle>,
    val referer: String?,
)
