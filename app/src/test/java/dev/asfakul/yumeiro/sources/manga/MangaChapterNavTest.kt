/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** F-18: previous / next chapter from a source's chapter list. */
class MangaChapterNavTest {
    private fun ch(n: Int, number: Float = n.toFloat()) = MangaChapter("u$n", "Chapter $n", number)

    @Test fun newestFirstNumberedList() {
        val list = listOf(ch(3), ch(2), ch(1))
        val (prev, next) = MangaChapterNav.neighbours(list, "u2")
        assertEquals("u1", prev?.url)
        assertEquals("u3", next?.url)
    }

    @Test fun oldestFirstNumberedListUsesNumbers() {
        val (prev, next) = MangaChapterNav.neighbours(listOf(ch(1), ch(2), ch(3)), "u3")
        assertEquals("u2", prev?.url)
        assertNull(next)
    }

    @Test fun unnumberedListIsReadNewestFirst() {
        val list = listOf(ch(3, -1f), ch(2, -1f), ch(1, -1f))
        val (prev, next) = MangaChapterNav.neighbours(list, "u1")
        assertNull(prev)
        assertEquals("u2", next?.url)
    }

    @Test fun unknownChapterHasNoNeighbours() {
        val (prev, next) = MangaChapterNav.neighbours(listOf(ch(1), ch(2)), "missing")
        assertNull(prev); assertNull(next)
    }

    @Test fun duplicateUrlsCountOnce() {
        val (prev, next) = MangaChapterNav.neighbours(listOf(ch(2), ch(2), ch(1)), "u1")
        assertNull(prev)
        assertEquals("u2", next?.url)
    }
}
