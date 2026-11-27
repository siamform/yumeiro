/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import com.lagradost.cloudstream3.MainAPI
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** F7: clone sites (a second copy of a source on another address). */
@RunWith(RobolectricTestRunner::class)
class GroupMSiteClonesTest {
    class Site : MainAPI() {
        override var name = "Site"
        override var mainUrl = "https://site.to"
    }
    class NoPlainConstructor(override var name: String) : MainAPI() { override var mainUrl = "https://other.to" }

    @After fun tearDown() = SiteClones.resetForTests()

    @Test fun namesAndChecks() {
        assertEquals("Site · clone.cc", SiteClones.nameFor("Site", "https://www.clone.cc/", emptyList()))
        assertEquals("Site · clone.cc 2", SiteClones.nameFor("Site", "clone.cc", listOf("Site · clone.cc")))
        assertEquals("That isn't a web address", SiteClones.problem("https://site.to", "not a url at all", emptyList(), "Site"))
        assertEquals("That is the address Site already uses", SiteClones.problem("https://site.to", "www.site.to", emptyList(), "Site"))
        val one = listOf(SiteClones.Clone("Site · clone.cc", "Site", "https://clone.cc", 1))
        assertEquals("Site already has a clone on clone.cc", SiteClones.problem("https://site.to", "https://clone.cc/home", one, "Site"))
        assertNull(SiteClones.problem("https://site.to", "clone2.cc", one, "Site"))
        assertEquals(one, SiteClones.decode(SiteClones.encode(one)))
        assertEquals(emptyList<SiteClones.Clone>(), SiteClones.decode("{broken"))
    }

    @Test fun aCloneIsItsOwnSourceOnTheOtherAddress() {
        val orig = Site().apply { sourcePlugin = "/data/plugins/site.cs3" }
        SiteClones.resetForTests(listOf(SiteClones.Clone("Site · clone.cc", "Site", "https://clone.cc", 1), SiteClones.Clone("Gone · x.cc", "Gone", "https://x.cc", 2)))
        val out = SiteClones.expand(listOf(orig))
        assertEquals(listOf("Site", "Site · clone.cc"), out.map { it.name })
        val clone = out[1]
        assertNotSame(orig, clone)
        assertTrue(clone is Site)
        assertEquals("https://clone.cc", clone.mainUrl)
        assertEquals("https://site.to", orig.mainUrl)
        assertEquals(orig.sourcePlugin, clone.sourcePlugin)
        assertSame("the same copy is kept between lists", clone, SiteClones.expand(listOf(orig))[1])
        assertEquals("Site", SiteClones.originOf("Site · clone.cc"))
    }

    @Test fun aSourceWithoutPlainConstructorIsLeftAlone() {
        SiteClones.resetForTests(listOf(SiteClones.Clone("Other · clone.cc", "Other", "https://clone.cc", 1)))
        assertEquals(listOf("Other"), SiteClones.expand(listOf(NoPlainConstructor("Other"))).map { it.name })
    }
}
