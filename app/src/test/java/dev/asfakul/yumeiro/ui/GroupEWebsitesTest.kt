/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.asfakul.yumeiro.online.web.BrowserFilters
import dev.asfakul.yumeiro.online.web.ElementBlocker
import dev.asfakul.yumeiro.online.web.PageAnnoyances
import dev.asfakul.yumeiro.online.web.PopupPolicy
import dev.asfakul.yumeiro.online.web.SavedSites
import dev.asfakul.yumeiro.online.web.SiteHistory
import dev.asfakul.yumeiro.ui.screens.sites.WebSitesScreen
import dev.asfakul.yumeiro.ui.screens.sites.WebsiteTab
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * GROUP E on the real Websites page and its pure parts.
 *  E1: the header has search, +, a gear and ? (no share / import icons); the gear's sheet holds the three switches,
 *      Manage ads and the list tools; the list itself no longer ends in switches.
 *  Owner item 10: Duplicate saves a copy under another address and never replaces a saved site.
 *  E2: several tabs, the live-page limit, closing the front tab shows its neighbour; a tapped link to another
 *      site opens a new tab.
 *  E3: anti-ad-blocker wall text.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h1400dp")
class GroupEWebsitesTest {
    @get:Rule val rule = createComposeRule()
    private val ctx get() = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun shows(t: String) = rule.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun click(tag: String) { rule.onNodeWithTag(tag, true).performClick(); rule.waitForIdle() }

    @Before fun setUp() {
        SavedSites.resetForTest(); SiteHistory.resetForTest()
        ctx.getSharedPreferences("yu_sites", 0).edit().clear().commit()
        ctx.getSharedPreferences("yu_sites_recent", 0).edit().clear().commit()
        SavedSites.init(ctx); SiteHistory.init(ctx)
        SavedSites.add(ctx, "archive.org")
    }

    @After fun tearDown() { WebsiteTab.closeAll(); WebsiteTab.liveLimit = 4 }

    private fun show() { rule.setContent { MTPlayerTheme { WebSitesScreen(onBack = {}, only = SavedSites.Kind.WEBSITE) } }; rule.waitForIdle() }

    @Test fun e1_headerIsIconsAndSettingsLiveBehindTheGear() {
        show()
        assertTrue(has("site_search_toggle")); assertTrue(has("site_add_open")); assertTrue(has("site_settings")); assertTrue(has("site_help"))
        assertFalse("share icon left the header", has("site_export"))
        assertFalse("import icon left the header", has("site_import"))
        assertFalse("switches left the list", has("recent_switch_row") || has("filter_ads_switch_row") || has("resolve_links_switch_row"))
        click("site_settings")
        assertTrue(has("site_settings_sheet"))
        for (t in listOf("recent_switch_row", "filter_ads_switch_row", "resolve_links_switch_row", "site_manage_ads", "site_import", "site_export")) assertTrue("settings has $t", has(t))
        val before = BrowserFilters.filterAds.value
        click("filter_ads_switch_row")
        assertEquals(!before, BrowserFilters.filterAds.value)
        BrowserFilters.setFilterAds(ctx, true)
    }

    @Test fun e1_manageAdsListsAndShowsHiddenPartsAgain() {
        ElementBlocker.resetForTest()
        ctx.getSharedPreferences("yu_web_block", 0).edit().clear().commit()
        ElementBlocker.add(ctx, "https://watch.example.com/a", "div.banner")
        ElementBlocker.add(ctx, "https://watch.example.com/b", "#popup")
        show()
        click("site_settings"); click("site_manage_ads")
        assertTrue(has("manage_ads"))
        assertTrue(shows("example.com")); assertTrue(shows("div.banner")); assertTrue(shows("#popup"))
        rule.onAllNodesWithTag("manage_ads_rule", true)[0].performClick(); rule.waitForIdle()
        assertEquals(1, ElementBlocker.rulesFor(ctx, "https://example.com/").size)
        click("manage_ads_clear_example.com")
        assertTrue(ElementBlocker.rulesFor(ctx, "https://example.com/").isEmpty())
        assertTrue(has("manage_ads_empty"))
    }

    @Test fun item10_menuOffersDuplicate() {
        show()
        click("site_menu")
        // The dialog itself (two text fields) is on the device: GroupEWebsitesDeviceTest b.
        assertTrue(has("site_duplicate"))
    }

    @Test fun item10_pureParts() {
        val sites = SavedSites.sites.value
        assertNotNull(SavedSites.duplicateError(sites, "https://archive.org/"))
        assertNull(SavedSites.duplicateError(sites, "archive.org/details/films"))
        assertNotNull(SavedSites.duplicateError(sites, "not an address"))
        assertEquals("Archive copy", SavedSites.copyName("Archive", emptyList()))
        assertEquals("Archive copy 2", SavedSites.copyName("Archive copy", listOf("Archive copy")))
    }

    @Test fun e2_severalTabsWithALiveLimit() {
        WebsiteTab.liveLimit = 2
        WebsiteTab.open(null, "https://a.example/", "A")
        WebsiteTab.open(null, "https://b.example/", "B")
        WebsiteTab.open(null, "https://c.example/", "C")
        assertEquals(listOf("A", "B", "C"), WebsiteTab.tabs.value.map { it.name })
        assertEquals("C", WebsiteTab.tab.value!!.name)
        assertTrue(WebsiteTab.tab.value!!.visible)
        assertEquals("only the front tab is visible", 1, WebsiteTab.tabs.value.count { it.visible })
        val a = WebsiteTab.tabs.value.first()
        // Opening A again shows the same tab (no new one).
        WebsiteTab.open(null, "https://a.example/", "A")
        assertEquals(3, WebsiteTab.tabs.value.size)
        assertEquals(a.id, WebsiteTab.tab.value!!.id)
        WebsiteTab.switchTo(WebsiteTab.tabs.value[1].id)
        assertEquals("B", WebsiteTab.tab.value!!.name)
        // Closing the front tab brings its neighbour, still visible.
        WebsiteTab.close()
        assertEquals(listOf("A", "C"), WebsiteTab.tabs.value.map { it.name })
        assertEquals("C", WebsiteTab.tab.value!!.name)
        assertTrue(WebsiteTab.tab.value!!.visible)
        repeat(WebsiteTab.MAX_TABS + 2) { WebsiteTab.open(null, "https://n$it.example/", "N$it") }
        assertEquals(WebsiteTab.MAX_TABS, WebsiteTab.tabs.value.size)
        WebsiteTab.closeAll()
        assertNull(WebsiteTab.tab.value)
    }

    @Test fun e2_aTappedLinkToAnotherSiteOpensANewTab() {
        assertEquals(PopupPolicy.Decision.NEW_TAB, PopupPolicy.window("https://a.example/x", "https://b.example/y", gesture = true, link = true, filter = true, chainActive = false, tabs = true))
        assertEquals(PopupPolicy.Decision.OPEN_HERE, PopupPolicy.window("https://a.example/x", "https://a.example/y", gesture = true, link = true, filter = true, chainActive = false, tabs = true))
        assertEquals(PopupPolicy.Decision.BLOCK, PopupPolicy.window("https://a.example/x", "https://b.example/y", gesture = false, link = false, filter = true, chainActive = false, tabs = true))
        assertEquals(PopupPolicy.Decision.OPEN_HERE, PopupPolicy.window("https://a.example/x", "https://b.example/y", gesture = true, link = true, filter = true, chainActive = false, tabs = false))
    }

    @Test fun e3_antiAdBlockWallText() {
        assertTrue(PageAnnoyances.isWall("AdBlock Detected!\n Please disable your ad blocker to continue"))
        assertTrue(PageAnnoyances.isWall("It looks like you\u2019re using an ad blocker"))
        assertFalse(PageAnnoyances.isWall("Sign in to continue watching"))
        assertTrue(PageAnnoyances.CLEAN_JS.contains("__yuCleanStats"))
    }
}
