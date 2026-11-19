package dev.asfakul.yumeiro.online.web

// F-04 (docs/proposals/FEATURE-F-04-webview-site-list.md): saved websites get search, pin, move up / down and a
// text import / export. Fail-before: Site has no `pinned`, and the list helpers do not exist.

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class F04SiteListTest {
    private fun s(u: String, pinned: Boolean = false) = SavedSites.Site("https://$u", SavedSites.defaultName("https://$u"), 0, pinned)

    @Test fun oldSavedListsStillLoadWithNothingPinned() {
        val old = """[{"url":"https://a.com","name":"A","addedAt":5}]"""
        val list = SavedSites.decode(old)
        assertEquals(1, list.size)
        assertFalse(list[0].pinned)
    }

    @Test fun pinnedSitesComeFirstAndKeepTheirOrder() {
        val list = listOf(s("a.com"), s("b.com", true), s("c.com"), s("d.com", true))
        assertEquals(listOf("b.com", "d.com", "a.com", "c.com"), SavedSites.ordered(list).map { it.host })
    }

    @Test fun searchMatchesNameOrHostIgnoringCase() {
        val list = listOf(s("anime-site.net"), s("movies.example.org"), s("news.com"))
        assertEquals(listOf("anime-site.net"), SavedSites.filter(list, "ANIME").map { it.host })
        assertEquals(listOf("movies.example.org"), SavedSites.filter(list, "example").map { it.host })
        assertEquals(3, SavedSites.filter(list, "  ").size)
    }

    @Test fun moveUpAndDownStaysInsideItsGroup() {
        val list = listOf(s("p.com", true), s("a.com"), s("b.com"))
        assertEquals(listOf("p.com", "b.com", "a.com"), SavedSites.move(list, "https://b.com", up = true).map { it.host })
        // The first unpinned site does not jump above a pinned one.
        assertEquals(list.map { it.host }, SavedSites.move(list, "https://a.com", up = true).map { it.host })
        assertEquals(list.map { it.host }, SavedSites.move(list, "https://b.com", up = false).map { it.host })
    }

    @Test fun importMergesSkipsBadLinesAndCapsTheList() {
        val existing = listOf(s("a.com"))
        val r = SavedSites.merge(existing, "a.com\nhttps://b.com/watch\nnot a link\n\nftp://x.com\nhttps://b.com/watch/", now = 9)
        assertEquals(listOf("a.com", "b.com"), r.sites.map { it.host })
        assertEquals(1, r.added); assertEquals(2, r.duplicates); assertEquals(2, r.bad); assertEquals(0, r.overLimit)
        val many = (1..250).joinToString("\n") { "site$it.com" }
        val big = SavedSites.merge(emptyList(), many, now = 1)
        assertEquals(200, big.sites.size); assertEquals(50, big.overLimit)
    }

    @Test fun exportIsOneLinkPerLineAndRoundTrips() {
        val list = listOf(s("a.com", true), s("b.com"))
        val text = SavedSites.export(list)
        assertTrue(text.lines().containsAll(listOf("https://a.com", "https://b.com")))
        assertEquals(list.map { it.url }, SavedSites.merge(emptyList(), text, now = 1).sites.map { it.url })
    }
}
