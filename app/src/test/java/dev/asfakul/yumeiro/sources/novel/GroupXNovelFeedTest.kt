/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.novel

import org.junit.Assert.assertEquals
import org.junit.Test

/** Group X (B4): the "All sources" novel feed: taken in turns, the same novel once, and when it is the default. */
class GroupXNovelFeedTest {
    private fun n(src: String, name: String) = NovelItem(src, name, "novel/" + name.lowercase().replace(' ', '-'), null)

    @Test fun sourcesTakeTurns() {
        val a = listOf(n("a", "One"), n("a", "Two"), n("a", "Three"))
        val b = listOf(n("b", "Red"), n("b", "Blue"))
        assertEquals(listOf("One", "Red", "Two", "Blue", "Three"), NovelFeedMerge.merge(emptyList(), listOf(a, b)).map { it.name })
    }

    @Test fun theSameNovelOnTwoSitesShowsOnce() {
        val a = listOf(n("a", "Shadow Slave"), n("a", "Lord of the Mysteries"))
        val b = listOf(n("b", "shadow  slave!"), n("b", "Omniscient Reader"))
        val first = NovelFeedMerge.merge(emptyList(), listOf(a, b))
        assertEquals(listOf("Shadow Slave", "Lord of the Mysteries", "Omniscient Reader"), first.map { it.name })
        assertEquals("a", first[0].pluginId)
        // Next page: what is already on screen is not added again.
        val more = NovelFeedMerge.merge(first, listOf(listOf(n("a", "Omniscient Reader"), n("a", "Martial Peak"))))
        assertEquals(listOf("Martial Peak"), more.map { it.name })
    }

    @Test fun titlesWithoutLettersStillCount() {
        val out = NovelFeedMerge.merge(emptyList(), listOf(listOf(n("a", "…"), n("a", "!!!")), listOf(n("b", "…"))))
        assertEquals(3, out.size)
    }

    @Test fun allIsTheDefaultWithMoreThanOneSource() {
        assertEquals(null, NovelFeedMerge.pick(null, emptyList()))
        assertEquals("a", NovelFeedMerge.pick(null, listOf("a")))
        assertEquals(NovelFeedMerge.ALL, NovelFeedMerge.pick(null, listOf("a", "b")))
        assertEquals("b", NovelFeedMerge.pick("b", listOf("a", "b")))
        assertEquals(NovelFeedMerge.ALL, NovelFeedMerge.pick("gone", listOf("a", "b")))
        assertEquals("a", NovelFeedMerge.pick(NovelFeedMerge.ALL, listOf("a")))
        assertEquals(NovelFeedMerge.ALL, NovelFeedMerge.pick(NovelFeedMerge.ALL, listOf("a", "b")))
    }
}
