/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.r15

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * FE-S2 (docs/FEED-ENGINE-2.1.md FE-6): `SearchRanker.rank` normalizes every title with regexes. It ran inside a
 * `remember { }` in composition (main thread) and again on each extension's answer; the typeahead
 * (`SearchSuggest.rank`, ~400 titles) did the same on every keystroke. This guard fails when a UI file calls either
 * from a remember block or without a Dispatchers.Default context just above it.
 */
class SearchRankOffMainTest {
    @Test fun searchRankingNeverRunsInComposition() {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
        val hits = ArrayList<String>()
        File(root, "dev/asfakul/yumeiro/ui").walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            val lines = f.readLines()
            lines.forEachIndexed { i, line ->
                if (!line.contains("SearchRanker.rank(") && !line.contains("SearchSuggest.rank(")) return@forEachIndexed
                val inRemember = lines.subList(maxOf(0, i - 3), i + 1).any { it.contains("remember(") || it.contains("remember {") }
                val offMain = lines.subList(maxOf(0, i - 12), i + 1).any { it.contains("Dispatchers.Default") }
                if (inRemember || !offMain) hits += "${f.name}:${i + 1}: ${line.trim().take(120)}"
            }
        }
        assertTrue("SearchRanker.rank on the main thread:\n" + hits.joinToString("\n"), hits.isEmpty())
    }
}
