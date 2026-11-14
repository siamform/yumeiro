/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.youtube.music

import dev.asfakul.yumeiro.youtube.YtItem

/**
 * Round 13: an endless online Music feed, the way ArchiveTune builds its Home. YouTube Music's own
 * Home stops after a handful of continuation pages; after that we keep appending "Similar to …"
 * shelves seeded from what the user listened to (most recent first, one seed per artist), then from
 * songs already on the page, so scrolling never hits a wall. Pure: the network call lives in the UI.
 */
object YmEndless {
    /** Most extra shelves one Home session grows by (each needs one network call). */
    const val MAX_SHELVES = 80
    private const val SHELF_SIZE = 20

    data class Seed(val id: String, val title: String, val artist: String)

    private fun artistKey(s: String) = s.removeSuffix(" - Topic").trim().lowercase()

    /** The next seed not used yet: history first (spread over artists), then songs on the page. */
    fun nextSeed(history: List<YtItem>, shelves: List<YmShelf>, used: Set<String>): Seed? {
        if (used.size >= MAX_SHELVES) return null
        val fromHistory = history.asSequence().filter { it.id.length == 11 }.map { Seed(it.id, it.title, it.channel.removeSuffix(" - Topic")) }
        val fromPage = shelves.asSequence().flatMap { it.items.asSequence() }.filterIsInstance<YmItem.Song>()
            .filter { it.videoId.length == 11 }.map { Seed(it.videoId, it.title, it.artistLine) }
        val all = (fromHistory + fromPage).filter { it.id !in used && it.title.isNotBlank() }.toList()
        // Prefer an artist we have not seeded yet, so the feed stays varied.
        return all.firstOrNull { s -> artistKey(s.artist) !in used } ?: all.firstOrNull()
    }

    /** Records [seed] as used (both its id and its artist, so the next seed is a different artist). */
    fun use(used: Set<String>, seed: Seed): Set<String> = used + seed.id + artistKey(seed.artist)

    /** A "Similar to <title>" shelf from [related], without songs already on the page or the seed itself. */
    fun shelfFor(seed: Seed, related: List<YtItem>, shown: Set<String>): YmShelf? {
        val songs = related.asSequence()
            .filter { it.id.length == 11 && it.id != seed.id && it.id !in shown && it.title.isNotBlank() && !it.live }
            .distinctBy { it.id }.take(SHELF_SIZE)
            .map { YmItem.Song(videoId = it.id, title = it.title, artists = listOf(YmRef(it.channel.removeSuffix(" - Topic"))), thumbnail = it.thumbnail, durationSec = it.durationSec) }
            .toList()
        if (songs.size < 3) return null
        return YmShelf(title = "Similar to ${seed.title}", strapline = seed.artist.ifBlank { null }, items = songs, style = YmShelf.Style.LIST)
    }

    /** Song ids already on the page (so new shelves never repeat them). */
    fun shownIds(shelves: List<YmShelf>): Set<String> =
        shelves.flatMap { s -> s.items.filterIsInstance<YmItem.Song>().map { it.videoId } }.toSet()
}
