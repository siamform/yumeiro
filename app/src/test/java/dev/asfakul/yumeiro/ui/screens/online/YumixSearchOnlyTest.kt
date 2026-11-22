/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** B-25 (GATE-1 run 44786829781): search-only extensions are not "loading", and their card stays short. */
class YumixSearchOnlyTest {
    @Test fun onlyExtensionsWithAHomeCanBeLoading() {
        val loading = YumixFeed.stillLoading(listOf("A", "B")) { it == "A" }
        assertEquals(setOf("B"), loading)
        assertTrue(YumixFeed.stillLoading(emptyList()) { false }.isEmpty())
    }

    @Test fun threeHundredSearchOnlyNamesMakeAShortLabel() {
        val names = (1..300).map { "Extension number $it" }
        val label = YumixFeed.searchOnlyLabel(names)
        assertEquals("Extension number 1, Extension number 2, Extension number 3 and 297 more", label)
        assertTrue(label.length < 120)
        assertEquals("A, B", YumixFeed.searchOnlyLabel(listOf("A", "B")))
    }
}
