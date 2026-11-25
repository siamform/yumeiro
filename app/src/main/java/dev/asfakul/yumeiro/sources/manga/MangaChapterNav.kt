/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.manga

/**
 * F-18: the chapters before and after the one being read. Sources list chapters newest first (Mihon /
 * Tachiyomi convention), but not all do, so when every chapter has a number the numbers decide; otherwise
 * the list is read newest first. Duplicated URLs count once.
 */
object MangaChapterNav {
    fun neighbours(chapters: List<MangaChapter>, currentUrl: String): Pair<MangaChapter?, MangaChapter?> {
        val list = chapters.distinctBy { it.url }
        if (list.none { it.url == currentUrl }) return null to null
        val numbered = list.isNotEmpty() && list.all { it.number >= 0f } && list.map { it.number }.distinct().size == list.size
        // MANGA-ORDER-1: without the source's numbers, numbers read from the names decide when every chapter has one.
        val named = !numbered && list.map { MangaChapterOrder.numberOf(it) }.let { n -> n.all { it >= 0f } && n.distinct().size == n.size }
        val reading = when {
            numbered -> list.sortedBy { it.number }
            named -> MangaChapterOrder.readingOrder(list)
            else -> list.reversed()
        }
        val i = reading.indexOfFirst { it.url == currentUrl }
        return reading.getOrNull(i - 1) to reading.getOrNull(i + 1)
    }
}
