/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.novel

/**
 * Group X (B4): the "All sources" novel feed. Pure.
 *  - [merge]: one list from every source's page, taken in turns (the first of each source, then the second of
 *    each…) so no single source fills the screen, with the same novel on two sites shown once (the first source
 *    to list it keeps it). Titles are compared without case, spaces or punctuation.
 *  - [pick]: which source the page opens on. "All" when more than one source is installed and the person has not
 *    chosen one; a chosen source that was removed falls back the same way.
 */
object NovelFeedMerge {
    const val ALL = "__all__"

    fun titleKey(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

    fun merge(already: List<NovelItem>, groups: List<List<NovelItem>>): List<NovelItem> {
        val seen = HashSet<String>(already.size * 2)
        already.forEach { seen += titleKey(it.name).ifEmpty { it.pluginId + "|" + it.path } }
        val out = ArrayList<NovelItem>()
        val longest = groups.maxOfOrNull { it.size } ?: 0
        for (i in 0 until longest) for (g in groups) {
            val n = g.getOrNull(i) ?: continue
            val k = titleKey(n.name).ifEmpty { n.pluginId + "|" + n.path }
            if (seen.add(k)) out += n
        }
        return out
    }

    fun pick(saved: String?, installed: List<String>): String? = when {
        installed.isEmpty() -> null
        saved == ALL -> if (installed.size > 1) ALL else installed.first()
        saved != null && saved in installed -> saved
        installed.size > 1 -> ALL
        else -> installed.first()
    }
}
