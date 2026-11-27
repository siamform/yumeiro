/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import dev.asfakul.yumeiro.l10n.tr
import dev.asfakul.yumeiro.R
import android.content.Context
import kotlin.math.ceil

/**
 * NF-1: one status per episode row — Watched, "N min left", or Fresh (added since you last opened this title).
 * Everything is worked out on the phone from the resume store ([OnlineHistory]) and a small count of episodes
 * seen per title ([Seen]); nothing is sent anywhere.
 */
sealed interface EpisodeStatus {
    val label: String

    data object Watched : EpisodeStatus { override val label = tr(R.string.watched, "Watched") }
    data class InProgress(val minutesLeft: Int) : EpisodeStatus { override val label = tr(R.string.min_left, "%1\$s min left", minutesLeft) }
    data object Fresh : EpisodeStatus { override val label = tr(R.string.fresh, "Fresh") }

    companion object {
        /** Below this, a start was a blip (opened by mistake), not "in progress". Same 1 % the progress bar uses. */
        private const val MIN_FRACTION = 0.01f

        /**
         * [index] is the episode's zero-based [ordinal] minus one and [seenBefore] the highest ordinal seen on the last visit, so
         * "Fresh" works for lists sorted newest-first and for lists a source reorders; episodes without a number are never fresh.
         */
        fun of(progress: OnlineHistory.Progress?, index: Int, seenBefore: Int?): EpisodeStatus? {
            if (progress != null && progress.durationMs > 0) {
                if (progress.finished) return Watched
                if (progress.fraction > MIN_FRACTION) {
                    val left = (progress.durationMs - progress.positionMs).coerceAtLeast(0L)
                    return InProgress(ceil(left / 60_000.0).toInt().coerceAtLeast(1))
                }
            }
            return if (seenBefore != null && index >= seenBefore) Fresh else null
        }

        /** Season-aware position: S2E1 comes after S1E99. Null when the source gives no episode number. */
        fun ordinal(e: OnlineEpisode): Int? = e.episode?.let { (e.season ?: 0).coerceIn(0, 999) * 10_000 + it.coerceIn(0, 9_999) }

        /** Highest [ordinal] in a list, or null if none is numbered. */
        fun highest(list: List<OnlineEpisode>): Int? = list.mapNotNull { ordinal(it) }.maxOrNull()

        /** The stored count only grows: a source that briefly returns a short list must not make old episodes fresh again. */
        fun nextSeen(previous: Int?, now: Int): Int = maxOf(previous ?: 0, now)
    }

    /** Highest episode ordinal seen per title and version (prefs file `episode_seen`, at most [MAX] titles, oldest dropped). */
    object Seen {
        private const val FILE = "episode_seen"
        private const val MAX = 400

        private fun key(api: String, url: String, version: String) = "$api|$url|$version"

        fun get(context: Context, api: String, url: String, version: String): Int? = runCatching {
            val p = context.applicationContext.getSharedPreferences(dev.asfakul.yumeiro.profiles.Profiles.prefsName(context.applicationContext, FILE), Context.MODE_PRIVATE)
            p.getString(key(api, url, version), null)?.substringBefore(',')?.toIntOrNull()
        }.getOrNull()

        /** Call off the main thread after the episode list was shown. */
        fun put(context: Context, api: String, url: String, version: String, count: Int) {
            runCatching {
                val p = context.applicationContext.getSharedPreferences(dev.asfakul.yumeiro.profiles.Profiles.prefsName(context.applicationContext, FILE), Context.MODE_PRIVATE)
                val k = key(api, url, version)
                val prev = p.getString(k, null)?.substringBefore(',')?.toIntOrNull()
                val next = nextSeen(prev, count)
                val e = p.edit().putString(k, "$next,${System.currentTimeMillis()}")
                if (prev == null && p.all.size >= MAX) {
                    p.all.entries.minByOrNull { (it.value as? String)?.substringAfter(',')?.toLongOrNull() ?: 0L }?.let { e.remove(it.key) }
                }
                e.apply()
            }
        }
    }
}
