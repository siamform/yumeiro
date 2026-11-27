/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

/**
 * Round 12 (1.7.0): which app this is. Yumeiro for phones and Yumeiro TV are separate builds of
 * the same code (product flavors `mobile` and `tv`); everything that differs reads this.
 */
object Edition {
    /** The TV app: always the ten-foot layout, tuned for remotes and small TV-stick memory. */
    val isTv: Boolean get() = BuildConfig.IS_TV_APP
    /** "phone" or "tv" (release file names, diagnostics). */
    val name: String get() = BuildConfig.EDITION
    val label: String get() = if (isTv) "Yumeiro TV" else "Yumeiro"

    /** Poster memory cache share of the heap: TV sticks have little memory and big posters. */
    val imageMemoryShare: Double get() = if (isTv) 0.12 else 0.20
    /** Network buffer ceiling (ms): TV boxes keep less in memory, phones ride out mobile data jitter. */
    val maxBufferMs: Int get() = if (isTv) 40_000 else 50_000
}
