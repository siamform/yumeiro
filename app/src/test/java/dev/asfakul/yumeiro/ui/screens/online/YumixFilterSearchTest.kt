/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YumixFilterSearchTest {
    private val names = listOf("SuperStream", "HiAnime", "AnimePahe", "Animé Fénix", "Moviesdrive", "Movies-Box")

    @Test fun blankQueryKeepsEverythingInOrder() {
        assertEquals(names, filterExtensionNames(names, ""))
        assertEquals(names, filterExtensionNames(names, "   "))
    }

    @Test fun caseAndAccentBlind() {
        assertEquals(listOf("Animé Fénix"), filterExtensionNames(names, "FENIX"))
        assertEquals(listOf("AnimePahe", "Animé Fénix", "HiAnime"), filterExtensionNames(names, "anime"))
    }

    @Test fun everyWordMustMatchInAnyOrder() {
        assertEquals(listOf("Movies-Box"), filterExtensionNames(names, "box movies"))
        assertEquals(listOf("Moviesdrive", "Movies-Box"), filterExtensionNames(names, "movies"))
    }

    @Test fun noMatchIsEmpty() {
        assertTrue(filterExtensionNames(names, "zzz").isEmpty())
    }
}
