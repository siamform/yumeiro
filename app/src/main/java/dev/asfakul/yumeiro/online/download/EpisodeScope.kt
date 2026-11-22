/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.download

import dev.asfakul.yumeiro.online.OnlineEpisode

/**
 * OL-11: which episodes a bulk download takes. "This episode only", "All episodes" or a
 * from–to range, picked by the episode number the user sees (E50 … E80), not the list position.
 * Saved or already-queued episodes are skipped, so the same range can be re-run safely.
 * The quality / Offline-Permanent choice made next in [dev.asfakul.yumeiro.ui.screens.downloads.DownloadPrompt]
 * applies to every episode of the job.
 */
object EpisodeScope {
    /** The number shown for [e]: its episode number, else its 1-based place in the list. */
    fun numberOf(position: Int, e: OnlineEpisode): Int = e.episode ?: (position + 1)

    /** Smallest and largest episode number in [list] (`null` when empty). */
    fun bounds(list: List<IndexedValue<OnlineEpisode>>): IntRange? {
        if (list.isEmpty()) return null
        val n = list.mapIndexed { i, v -> numberOf(i, v.value) }
        return n.min()..n.max()
    }

    /**
     * Episodes of [list] whose number is within [from]..[to] (inclusive, either order), minus [have].
     * Returns (index in the full ordered list, episode) pairs, in list order.
     */
    fun range(list: List<IndexedValue<OnlineEpisode>>, from: Int, to: Int, have: Set<String> = emptySet()): List<Pair<Int, OnlineEpisode>> {
        val lo = minOf(from, to); val hi = maxOf(from, to)
        return list.filterIndexed { i, v -> numberOf(i, v.value) in lo..hi && v.value.data !in have }.map { it.index to it.value }
    }

    /** "This episode only": the episode at [current] (the one Play / Continue opens), unless it is saved. */
    fun single(ordered: List<OnlineEpisode>, current: Int?, have: Set<String> = emptySet()): Pair<Int, OnlineEpisode>? {
        val i = current ?: return null
        val e = ordered.getOrNull(i) ?: return null
        return if (e.data in have) null else i to e
    }

    /** Parses a typed episode number; blank or junk gives `null`. */
    fun parse(text: String): Int? = text.trim().takeIf { it.isNotEmpty() && it.length <= 6 && it.all(Char::isDigit) }?.toIntOrNull()
}
