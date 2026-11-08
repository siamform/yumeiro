/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

/**
 * Finds the same episode in another version (Sub ↔ Dub) of a title. Never guesses by list
 * position: dub lists are often shorter or start later, so "same index" is a different episode.
 */
object EpisodeMatcher {
    /** Index of [current] inside [candidates], or -1 when that episode doesn't exist there. */
    fun match(current: OnlineEpisode?, candidates: List<OnlineEpisode>): Int {
        if (current == null || candidates.isEmpty()) return -1
        val num = current.episode
        if (num != null) {
            // Exact season + episode first; a missing season and season 1 are the same thing.
            val exact = candidates.indexOfFirst { it.episode == num && sameSeason(it.season, current.season) }
            if (exact >= 0) return exact
            // Some sources number seasons differently but only one entry has this number.
            val byNumber = candidates.withIndex().filter { it.value.episode == num }
            if (byNumber.size == 1 && (current.season == null || byNumber[0].value.season == null)) return byNumber[0].index
        }
        val key = normalize(current.name)
        if (key.length >= 3) {
            val byName = candidates.indexOfFirst { normalize(it.name) == key }
            if (byName >= 0) return byName
        }
        return -1
    }

    private fun sameSeason(a: Int?, b: Int?) = (a ?: 1) == (b ?: 1)

    /** "Episode 5 - The Return (Dub)" and "the return" compare equal. */
    internal fun normalize(name: String?): String = name.orEmpty().lowercase()
        .replace(Regex("""\((sub|dub|subbed|dubbed|eng(lish)?|raw)\)|\[(sub|dub)]"""), " ")
        .replace(Regex("""^\s*(ep(isode)?\.?\s*\d+\s*[-:·.]?\s*)"""), " ")
        .replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()
}
