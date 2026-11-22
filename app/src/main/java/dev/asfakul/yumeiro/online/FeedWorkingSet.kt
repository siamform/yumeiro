/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

/**
 * FE-4b (docs/FEED-ENGINE-2.1.md, 1000+ extensions): which extensions may fetch their home live right now.
 * Before, every extension went live at each visit (first fetch, or the background refresh behind its saved page),
 * three at a time: with 1000 extensions that is 1000 sites asked for a feed that shows a screenful, and the
 * picked / on-screen ones waited behind them. Like a YouTube subscriptions feed or an Instagram timeline, only the
 * working set goes live: the picked extension, the pinned ones, and the first [limit] in speed order. The others show
 * their saved page (any age, FE-1) and join the working set as the feed is scrolled to its end ([grow]).
 * With up to [PHONE] extensions (most users) nothing changes.
 */
object FeedWorkingSet {
    const val PHONE = 24
    /** 2 GB phones: half, so fewer homes are parsed and held at once. */
    const val LOW_END = 12
    const val STEP = 12

    fun initial(lowEnd: Boolean): Int = if (lowEnd) LOW_END else PHONE

    /** Names allowed a live fetch: picked, then pinned, then the first [limit] of [ordered] (speed order). */
    fun live(ordered: List<String>, limit: Int, picked: String?, pinned: Collection<String>): Set<String> {
        val all = ordered.toHashSet()
        val out = LinkedHashSet<String>()
        if (picked != null && picked in all) out += picked
        pinned.forEach { if (it in all) out += it }
        ordered.asSequence().take(limit.coerceAtLeast(0)).forEach { out += it }
        return out
    }

    /** Next limit when the feed reached its end and [waiting] extensions are outside the working set. */
    fun grow(limit: Int, waiting: Int): Int = if (waiting > 0) limit + STEP else limit
}
