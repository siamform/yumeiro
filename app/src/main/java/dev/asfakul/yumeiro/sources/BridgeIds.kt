/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources

/**
 * GROUP-13 N4-2. CloudStream's `newMovieSearchResponse` / `newAnimeSearchResponse` / `newEpisode`
 * default to `fix = true`, and `fixUrl()` (library 4.8.0) turns every id that does not start with
 * "http" into `"$mainUrl/$id"`. Bridge ids are not links ("movie/tt1", "rk:12", "item:ab",
 * "d:/Films", SMB paths, DLNA ids), so `load()` / `loadLinks()` got `"$mainUrl/movie/tt1"` back and
 * found nothing.
 *
 * Ids that already play keep the shape they were saved with (history, progress and favourites are
 * keyed by them); readers accept both shapes through [unfix].
 */
object BridgeIds {
    /** The bridge id inside [s]: [s] without a leading `"$mainUrl/"`. Anything else is returned as is. */
    fun unfix(mainUrl: String, s: String): String {
        val base = mainUrl.trimEnd('/')
        if (base.isEmpty() || !s.startsWith(base)) return s
        val rest = s.substring(base.length)
        if (!rest.startsWith("/")) return s // "http://srv:8096…" is not under "http://srv:809"
        return rest.trimStart('/')
    }
}
