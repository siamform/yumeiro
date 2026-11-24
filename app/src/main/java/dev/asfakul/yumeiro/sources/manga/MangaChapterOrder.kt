/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.manga

/**
 * MANGA-ORDER-1 (owner item 27): reading order of chapters from their metadata, never from download time.
 * The source's number wins; without one the number is read from the name ("Chapter 12.5", "Ch. 13", "Vol. 2 Ch. 15",
 * "#7", "Episode 4", "12 - Title"). Chapters with no number (specials, extras, oneshots) keep their place after the
 * numbered chapter they were listed with in the source order when that is known, else go after every numbered one in
 * natural name order.
 */
object MangaChapterOrder {
    private val LABELLED = Regex("""(?i)(?:chapter|chap|ch|episode|ep|第|#)\.?\s*(\d+(?:[.,]\d+)?)""")
    private val VOLUME = Regex("""(?i)\b(?:vol(?:ume)?|v)\.?\s*\d+(?:\.\d+)?""")
    private val ANY = Regex("""(\d+(?:[.,]\d+)?)""")
    private val DIGITS = Regex("""\d+""")

    /** The chapter number in [name], or -1 when there is none (a special or extra). */
    fun number(name: String): Float {
        LABELLED.find(name)?.let { return it.groupValues[1].replace(',', '.').toFloatOrNull() ?: -1f }
        val rest = VOLUME.replace(name, " ")
        return ANY.find(rest)?.groupValues?.get(1)?.replace(',', '.')?.toFloatOrNull() ?: -1f
    }

    fun numberOf(c: MangaChapter): Float = if (c.number >= 0f) c.number else number(c.name)

    /** Oldest first. */
    fun readingOrder(chapters: List<MangaChapter>): List<MangaChapter> {
        val list = chapters.distinctBy { it.url }
        val withNo = list.map { it to numberOf(it) }
        val numbered = withNo.filter { it.second >= 0f }.sortedWith(compareBy<Pair<MangaChapter, Float>> { it.second }.thenBy { natural(it.first.name) })
        val unnumbered = withNo.filter { it.second < 0f }.sortedBy { natural(it.first.name) }
        return numbered.map { it.first } + unnumbered.map { it.first }
    }

    /** Newest first, the order sources (Mihon convention) and the title page use. */
    fun newestFirst(chapters: List<MangaChapter>): List<MangaChapter> = readingOrder(chapters).reversed()

    /** "Chapter 2" before "Chapter 10": digits are padded for comparison. */
    private fun natural(s: String): String = DIGITS.replace(s.lowercase()) { it.value.padStart(10, '0') }
}
