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
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import dev.asfakul.yumeiro.online.web.SavedSites
import dev.asfakul.yumeiro.online.web.SiteHistory
import dev.asfakul.yumeiro.ui.screens.sites.WebSitesScreen
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Owner list 5 on the real Websites page: "+" and search icons, address / web search, Recently visited. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h1400dp")
class WebsitesPageTest {
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

    private fun show() { rule.setContent { MTPlayerTheme { WebSitesScreen(onBack = {}, only = SavedSites.Kind.WEBSITE) } }; rule.waitForIdle() }

    @Test fun plusOpensTheAddCard() {
        show()
        assertFalse("with saved sites the add card waits behind +", has("site_add_field"))
        click("site_add_open")
        assertTrue(has("site_add_field"))
        rule.onNodeWithTag("site_add_field", true).performTextInput("a5x2nt.movielinkbd.at")
        click("site_add")
        assertTrue(SavedSites.sites.value.any { it.host == "a5x2nt.movielinkbd.at" })
        assertFalse("closes after adding", has("site_add_field"))
    }

    @Test fun searchGoesToAnAddressOrTheWeb() {
        show()
        click("site_search_toggle")
        assertTrue(has("site_search"))
        rule.onNodeWithTag("site_search", true).performTextInput("archive")
        rule.waitForIdle()
        assertTrue("saved site matches", has("site_row"))
        assertTrue(has("site_web_search"))
        rule.onNodeWithTag("site_search", true).performTextClearance()
        rule.onNodeWithTag("site_search", true).performTextInput("bbc.com/bengali")
        rule.waitForIdle()
        assertTrue(has("site_go"))
        assertTrue(shows("Go to bbc.com"))
        assertFalse(has("site_web_search"))
    }

    @Test fun visitedSitesShowUntilSaved() {
        SiteHistory.visit(ctx, "https://a5x2nt.movielinkbd.at/watch/1", "Dune — MovieLinkBD")
        SiteHistory.visit(ctx, "https://archive.org/details/x", "Internet Archive")
        show()
        assertTrue(shows("Recently visited"))
        // archive.org is saved, so only MovieLinkBD is listed.
        assertEquals(1, rule.onAllNodesWithTag("recent_row", true).fetchSemanticsNodes().size)
        click("recent_save")
        assertTrue(SavedSites.sites.value.any { it.name == "MovieLinkBD" })
        assertFalse(has("recent_row"))
        // Off: nothing is remembered and the list is cleared.
        click("recent_switch")
        SiteHistory.visit(ctx, "https://www.youtube.com/", "YouTube")
        assertTrue(SiteHistory.visits.value.isEmpty())
    }
}
