/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * F-02 split (2.1.0): Telegram channels and websites were one "Telegram & web sites" page. Now the Telegram
 * page lists only saved channels and the Websites page only sites, and each page refuses the other kind with
 * a message instead of saving it out of sight.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SavedSitesSplitTest {
    private val tg = SavedSites.Site("https://t.me/s/yutest", "@yutest", 1L)
    private val web = SavedSites.Site("https://example.com", "Example", 2L)

    @Test fun eachPageShowsItsOwnKind() {
        val all = listOf(tg, web)
        assertEquals(listOf(tg), SavedSites.ofKind(all, SavedSites.Kind.TELEGRAM))
        assertEquals(listOf(web), SavedSites.ofKind(all, SavedSites.Kind.WEBSITE))
        assertEquals(all, SavedSites.ofKind(all, null))
    }

    @Test fun eachPageRefusesTheOtherKind() {
        assertNotNull(SavedSites.wrongPage("example.com", SavedSites.Kind.TELEGRAM))
        assertNotNull(SavedSites.wrongPage("@yutest", SavedSites.Kind.WEBSITE))
        assertNull(SavedSites.wrongPage("@yutest", SavedSites.Kind.TELEGRAM))
        assertNull(SavedSites.wrongPage("t.me/yutest", SavedSites.Kind.TELEGRAM))
        assertNull(SavedSites.wrongPage("example.com", SavedSites.Kind.WEBSITE))
        assertNull(SavedSites.wrongPage("anything", null))
    }
}
