/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.youtube.music

import androidx.compose.runtime.Immutable
import dev.asfakul.yumeiro.youtube.YtItem
import dev.asfakul.yumeiro.youtube.YouTube

/**
 * The YouTube Music catalogue as Yumeiro shows it: pages of shelves (Quick picks, albums,
 * charts, an artist's top songs...), each shelf a list of songs, videos, albums, playlists,
 * artists or mood buttons. Built by [YmParser] from YouTube Music's own web responses.
 */

/** A name with an optional link (an artist or an album inside a song's subtitle). */
@Immutable
data class YmRef(val name: String, val browseId: String? = null)

/** A page the user can open: an album, playlist, artist, mood category or a "More" list. */
@Immutable
data class YmBrowse(val browseId: String, val params: String? = null)

@Immutable
sealed interface YmItem {
    val key: String
    val title: String

    /** A song or a music video; plays directly. */
    @Immutable
    data class Song(
        val videoId: String,
        override val title: String,
        val artists: List<YmRef> = emptyList(),
        val album: YmRef? = null,
        val thumbnail: String? = null,
        val durationSec: Long = 0,
        /** A music video (16:9) rather than an audio track. */
        val video: Boolean = false,
        /** Extra line from YouTube ("1.2B plays", "Song • 2024"). */
        val note: String? = null,
    ) : YmItem {
        override val key get() = "s:$videoId"
        val artistLine: String get() = artists.joinToString(", ") { it.name }
    }

    /** An album, single, playlist, artist or podcast; opens a page. */
    @Immutable
    data class Collection(
        val browse: YmBrowse,
        override val title: String,
        val subtitle: String = "",
        val thumbnail: String? = null,
        val kind: Kind = Kind.PLAYLIST,
        /** Plays the whole collection straight away (the round play button on the cover). */
        val playlistId: String? = null,
    ) : YmItem {
        override val key get() = "c:${browse.browseId}"
        enum class Kind { ALBUM, PLAYLIST, ARTIST, PODCAST, OTHER }
    }

    /** A Moods & genres button (coloured stripe, opens a category page). */
    @Immutable
    data class Mood(override val title: String, val browse: YmBrowse, val color: Long? = null) : YmItem {
        override val key get() = "m:${browse.browseId}:${browse.params}"
    }
}

@Immutable
data class YmShelf(
    val title: String,
    /** Small line above the title ("Start radio based on a song", an artist name...). */
    val strapline: String? = null,
    val items: List<YmItem>,
    val style: Style,
    /** "More" opens the full list on its own page. */
    val more: YmBrowse? = null,
) {
    /** LIST: song rows (Quick picks, Top songs). CAROUSEL: covers in a row. GRID: mood buttons. */
    enum class Style { LIST, CAROUSEL, GRID }
}

@Immutable
data class YmChip(val title: String, val browse: YmBrowse, val selected: Boolean = false)

@Immutable
data class YmHeader(
    val title: String,
    val subtitle: String = "",
    val secondSubtitle: String = "",
    val description: String = "",
    val thumbnail: String? = null,
    /** Plays the page (album/playlist id, or an artist's radio). */
    val playlistId: String? = null,
    val subscribers: String? = null,
    val artist: Boolean = false,
)

@Immutable
data class YmPage(
    val header: YmHeader? = null,
    val shelves: List<YmShelf> = emptyList(),
    val chips: List<YmChip> = emptyList(),
    /** Loads more shelves (Home) or more rows of the last list (playlists, search). */
    val continuation: String? = null,
)

/** A YouTube Music song as the player's [YtItem] (the existing YouTube playback path). */
fun YmItem.Song.toYt(): YtItem = YtItem(
    id = videoId,
    url = YouTube.watchUrl(videoId),
    title = title,
    channel = artistLine.ifBlank { "YouTube Music" },
    channelUrl = artists.firstOrNull()?.browseId?.let { "https://music.youtube.com/channel/$it" },
    thumbnail = thumbnail,
    durationSec = durationSec,
    music = !video,
)
