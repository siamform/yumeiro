/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.web

import dev.asfakul.yumeiro.online.web.ElementBlocker
import dev.asfakul.yumeiro.online.web.SavedSites
import dev.asfakul.yumeiro.online.web.SiteHistory
import dev.asfakul.yumeiro.online.web.SiteMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Owner list 5 (Websites): names and icons read from real pages (head tags saved 2026-11-24 from the live sites),
 * the visited-sites list, and the element blocker's rules.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BrowserFeaturesTest {
    private fun page(name: String) = requireNotNull(javaClass.classLoader!!.getResource("web/$name")).readText()

    @Test fun siteNamesAndIconsComeFromThePage() {
        val mlb = SiteMeta.parse(page("a5x2nt_movielinkbd_at_.html"), "https://a5x2nt.movielinkbd.at/")
        assertEquals("MovieLinkBD", mlb.name)
        assertEquals("https://movielinkbd.at/cdn/img/kmovielinkbd.png", mlb.iconUrl)
        val ia = SiteMeta.parse(page("archive_org_.html"), "https://archive.org/")
        assertEquals("Internet Archive", ia.name)
        assertEquals("https://archive.org/offshoot_assets/apple-touch-icon.png", ia.iconUrl)
        // og:site_name wins over the long title.
        assertEquals("BBC News বাংলা", SiteMeta.parse(page("www_bbc_com_bengali.html"), "https://www.bbc.com/bengali").name)
        assertEquals("YouTube", SiteMeta.parse(page("www_youtube_com_.html"), "https://www.youtube.com/").name)
        // No tags at all: no name, the site's /favicon.ico.
        val none = SiteMeta.parse("<html><body>hi</body></html>", "https://example.org/watch/1")
        assertNull(none.name)
        assertEquals("https://example.org/favicon.ico", none.iconUrl)
    }

    @Test fun aNameTheUserTypedIsKept() {
        val guessed = SavedSites.Site("https://a5x2nt.movielinkbd.at", SavedSites.defaultName("https://a5x2nt.movielinkbd.at"))
        val meta = SiteMeta.Meta("MovieLinkBD", "https://movielinkbd.at/i.png")
        assertEquals("MovieLinkBD", SavedSites.withMeta(guessed, meta).name)
        assertEquals("https://movielinkbd.at/i.png", SavedSites.withMeta(guessed, meta).iconUrl)
        val mine = guessed.copy(name = "My movies")
        assertEquals("My movies", SavedSites.withMeta(mine, meta).name)
        // Old saved lists (no icon field) still decode.
        assertEquals(1, SavedSites.decode("""[{"url":"https://a.com","name":"A","addedAt":1,"pinned":false}]""").size)
    }

    @Test fun visitedSitesAreOnePerSiteNewestFirst() {
        var l = SiteHistory.record(emptyList(), "https://a5x2nt.movielinkbd.at/watch/123", "Dune — MovieLinkBD", 1)
        l = SiteHistory.record(l, "https://archive.org/details/x", "Internet Archive: x", 2)
        l = SiteHistory.record(l, "https://a5x2nt.movielinkbd.at/other", null, 3)
        assertEquals(listOf("https://a5x2nt.movielinkbd.at", "https://archive.org"), l.map { it.url })
        assertEquals("MovieLinkBD", l[0].name)
        assertEquals(3L, l[0].at)
        // Not a web address: nothing recorded.
        assertEquals(l, SiteHistory.record(l, "about:blank", null, 4))
        // Capped.
        var many = emptyList<SiteHistory.Visit>()
        for (i in 0 until 40) many = SiteHistory.record(many, "https://site$i.com/", null, i.toLong())
        assertEquals(SiteHistory.MAX, many.size)
        assertEquals("https://site39.com", many.first().url)
        // Saved sites are not repeated under Recently visited.
        val saved = listOf(SavedSites.Site("https://archive.org", "IA"))
        assertEquals(listOf("https://a5x2nt.movielinkbd.at"), SiteHistory.unsaved(l, saved).map { it.url })
    }

    @Test fun pickedElementsAreSafeSelectorsOnly() {
        assertEquals("div.player-ads > a:nth-of-type(2)", ElementBlocker.valid(" div.player-ads > a:nth-of-type(2) "))
        assertEquals("#sticky\\-ad", ElementBlocker.valid("#sticky\\-ad").let { it ?: "#sticky\\-ad" })
        listOf("", "a{color:red}", "div; } body { display:none", "</style><script>", "@import x", "a /* x */", "div[style*=url(x)]")
            .forEach { assertNull("rejected: $it", ElementBlocker.valid(it)) }
        val css = ElementBlocker.css(listOf("div.ad-box", "a{x}"))
        assertTrue(css.contains("div.ad-box"))
        assertFalse(css.contains("a{x}"))
        assertTrue(css.endsWith("{ display: none !important; }"))
        assertTrue(ElementBlocker.styleJs(css).contains("yu-block"))
        assertEquals("movielinkbd.at", ElementBlocker.siteOf("https://a5x2nt.movielinkbd.at/watch"))
    }

    @Test fun rulesAreRememberedPerSite() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        ElementBlocker.resetForTest()
        ElementBlocker.clear(ctx, "https://movielinkbd.at")
        assertTrue(ElementBlocker.add(ctx, "https://a5x2nt.movielinkbd.at/x", "div.popup"))
        assertFalse(ElementBlocker.add(ctx, "https://a5x2nt.movielinkbd.at/x", "div{}"))
        assertEquals(listOf("div.popup"), ElementBlocker.rulesFor(ctx, "https://www.movielinkbd.at/"))
        assertTrue(ElementBlocker.rulesFor(ctx, "https://archive.org/").isEmpty())
        ElementBlocker.resetForTest()
        assertEquals("kept after restart", listOf("div.popup"), ElementBlocker.rulesFor(ctx, "https://movielinkbd.at/"))
        assertTrue(ElementBlocker.undoLast(ctx, "https://movielinkbd.at/"))
        assertTrue(ElementBlocker.rulesFor(ctx, "https://movielinkbd.at/").isEmpty())
    }
}
