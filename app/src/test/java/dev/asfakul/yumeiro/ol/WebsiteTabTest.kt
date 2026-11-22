/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ol

import android.content.ComponentCallbacks2
import dev.asfakul.yumeiro.ui.screens.sites.WebsiteTab
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** OL-1: a website stays open in the background and comes back as it was. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WebsiteTabTest {
    @After fun tearDown() = WebsiteTab.close()

    @Test fun playingAVideoHidesTheTabAndClosingThePlayerBringsItBack() {
        WebsiteTab.open(null, "https://site.example/", "Example")
        val keep = WebsiteTab.tab.value!!.keep
        WebsiteTab.minimize(byPlay = true)
        assertFalse(WebsiteTab.tab.value!!.visible)
        WebsiteTab.onPlayerClosed()
        val t = WebsiteTab.tab.value!!
        assertTrue(t.visible)
        assertSame("the same page comes back, not a new one", keep, t.keep)
    }

    @Test fun aMinimizedTabStaysHiddenWhenThePlayerCloses() {
        WebsiteTab.open(null, "https://site.example/", "Example")
        WebsiteTab.minimize(byPlay = false)
        WebsiteTab.onPlayerClosed()
        assertFalse(WebsiteTab.tab.value!!.visible)
        WebsiteTab.resume()
        assertTrue(WebsiteTab.tab.value!!.visible)
    }

    @Test fun openingTheSameSiteAgainResumesItAndAnotherSiteReplacesIt() {
        WebsiteTab.open(null, "https://site.example/", "Example")
        val keep = WebsiteTab.tab.value!!.keep
        WebsiteTab.minimize(byPlay = false)
        WebsiteTab.open(null, "https://site.example/", "Example")
        assertSame(keep, WebsiteTab.tab.value!!.keep)
        assertTrue(WebsiteTab.tab.value!!.visible)
        WebsiteTab.open(null, "https://other.example/", "Other")
        assertEquals("https://other.example/", WebsiteTab.tab.value!!.keep.pageUrl)
    }

    @Test fun closeEndsTheTab() {
        WebsiteTab.open(null, "https://site.example/", "Example")
        WebsiteTab.close()
        assertNull(WebsiteTab.tab.value)
    }

    @Test fun lowMemoryKeepsTheTabButNotAVisibleOne() {
        WebsiteTab.open(null, "https://site.example/", "Example")
        WebsiteTab.trim(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
        assertNotNull("a visible tab is never dropped", WebsiteTab.tab.value)
        WebsiteTab.minimize(byPlay = false)
        WebsiteTab.trim(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
        val t = WebsiteTab.tab.value!!
        assertFalse(t.keep.alive)
        assertEquals("the address is kept and reloads on resume", "https://site.example/", t.keep.address)
    }
}
