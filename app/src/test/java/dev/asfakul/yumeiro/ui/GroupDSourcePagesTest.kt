/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dev.asfakul.yumeiro.ui.screens.sources.HubPart
import dev.asfakul.yumeiro.ui.screens.sources.SourcePage
import dev.asfakul.yumeiro.ui.screens.sources.SourceRoute
import dev.asfakul.yumeiro.ui.screens.sources.SourcesHubScreen
import dev.asfakul.yumeiro.ui.screens.sources.SourcesNav
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** GROUP D: the Source page is a page flow (overview → ecosystem → Browse / Repositories) with predictable back. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h1600dp")
class GroupDSourcePagesTest {
    @get:Rule val rule = createComposeRule()
    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun click(tag: String) { rule.onNodeWithTag(tag, true).performClick(); rule.waitForIdle() }

    private val home = SourcesNav.home
    private val ani = SourceRoute(SourcePage.ANIYOMI)
    private val aniBrowse = SourceRoute(SourcePage.ANIYOMI, HubPart.BROWSE)

    @Test fun stackRules() {
        assertEquals(listOf(home), SourcesNav.start(-1))
        assertEquals(listOf(home), SourcesNav.start(99))
        assertEquals(listOf(home, SourceRoute(SourcePage.MIHON)), SourcesNav.start(1))
        assertEquals(listOf(home, SourceRoute(SourcePage.SCRIPTS)), SourcesNav.start(5))
        var s = SourcesNav.push(listOf(home), ani)
        s = SourcesNav.push(s, aniBrowse)
        assertEquals(listOf(home, ani, aniBrowse), s)
        // Browse → Repositories replaces Browse (siblings, not a chain); same page twice is ignored.
        s = SourcesNav.push(s, SourceRoute(SourcePage.ANIYOMI, HubPart.REPOS))
        assertEquals(3, s.size); assertEquals(HubPart.REPOS, s.last().part)
        assertEquals(s, SourcesNav.push(s, s.last()))
        // Another ecosystem sits straight on the overview.
        assertEquals(listOf(home, SourceRoute(SourcePage.JS)), SourcesNav.push(s, SourceRoute(SourcePage.JS)))
        assertEquals(listOf(home), SourcesNav.push(s, home))
        assertEquals(listOf(home, ani), SourcesNav.pop(listOf(home, ani, aniBrowse)))
        assertNull(SourcesNav.pop(listOf(home)))
        assertEquals(s, SourcesNav.restore(SourcesNav.save(s)))
        assertEquals(listOf(home), SourcesNav.restore(listOf("NOPE/")))
        assertEquals("src_route_aniyomi_browse", aniBrowse.tag)
    }

    @Test fun overviewOpensPagesAndBackWalksUp() {
        var left = 0
        rule.setContent { MTPlayerTheme { SourcesHubScreen(onBack = { left++ }, onOpenCloudStream = {}) } }
        rule.waitUntil(10_000) { has("src_home") }
        assertFalse(has("sources_tab_0")); assertFalse(has("hub_part_installed"))
        for (p in listOf("cloudstream", "aniyomi", "mihon", "js", "stremio", "scripts", "servers")) {
            rule.onNodeWithTag("src_home", true).performScrollToNode(hasTestTag("src_page_$p"))
            assertTrue("row $p", has("src_page_$p"))
        }
        rule.onNodeWithTag("src_home", true).performScrollToNode(hasTestTag("src_page_mihon"))
        click("src_page_mihon")
        rule.waitUntil(5_000) { has("src_route_mihon") && !has("src_home") }
        click("src_go_repos")
        rule.waitUntil(5_000) { has("src_route_mihon_repos") }
        click("subpage_back")
        rule.waitUntil(5_000) { has("src_route_mihon") && !has("src_route_mihon_repos") }
        click("subpage_back")
        rule.waitUntil(5_000) { has("src_home") }
        assertEquals(0, left)
        click("subpage_back")
        assertEquals(1, left)
    }

    @Test fun cloudStreamRowOnlyWhenTheHostLinksIt() {
        var opened = 0
        rule.setContent { MTPlayerTheme { SourcesHubScreen(onBack = {}, onOpenCloudStream = { opened++ }) } }
        rule.waitUntil(10_000) { has("src_page_cloudstream") }
        click("src_page_cloudstream")
        assertEquals(1, opened)
        assertTrue("CloudStream opens its own screen, the overview stays", has("src_home"))
    }

    @Test fun overviewHasIconsNotTextButtons() {
        rule.setContent { MTPlayerTheme { SourcesHubScreen(onBack = {}) } }
        rule.waitUntil(10_000) { has("src_home") }
        assertTrue(has("sources_add_any")); assertTrue(has("sources_help"))
        assertFalse(has("src_page_cloudstream"))
        // The Add anything dialog focuses a text field whose blinking cursor never lets Robolectric idle;
        // opening it is covered on the device (GroupDSourcePagesDeviceTest a checks the icon).
    }
}
