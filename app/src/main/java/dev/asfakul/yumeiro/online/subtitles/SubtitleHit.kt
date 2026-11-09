/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.subtitles

/** One search result from any subtitle site, ready to show in a list. */
data class SubtitleHit(
    /** "OpenSubtitles", "SubDL" or "SubSource". */
    val provider: String,
    /** Provider-specific id (file id, archive path or subtitle id). */
    val id: String,
    /** Yumeiro language code ("en", "pt-br"). */
    val language: String,
    val release: String,
    val downloads: Int = 0,
    val hearingImpaired: Boolean = false,
    /** True when the site says this archive covers a whole season (we pick the episode inside). */
    val seasonPack: Boolean = false,
    val uploader: String? = null,
) {
    val key: String get() = "$provider:$id"
}
