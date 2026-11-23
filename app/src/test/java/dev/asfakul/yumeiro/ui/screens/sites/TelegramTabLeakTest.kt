/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.sites

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import dev.asfakul.yumeiro.online.web.SavedSites
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * TG-TAB-1 (owner report): the last website browsed showed as an "Open tab" shortcut on the Telegram page. The
 * Telegram page shares WebSitesScreen; the kept website tab belongs to the Websites page only.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class TelegramTabLeakTest {
    @get:Rule val rule = createComposeRule()

    @After fun tearDown() = WebsiteTab.close()

    private fun keptTab() {
        WebsiteTab.open(null, "https://example.org/watch", "Example")
        WebsiteTab.minimize(byPlay = false)
    }

    @Test fun telegramPageShowsNoWebsiteTab() {
        keptTab()
        rule.setContent { MTPlayerTheme { WebSitesScreen(onBack = {}, only = SavedSites.Kind.TELEGRAM) } }
        rule.waitForIdle()
        assertEquals(0, rule.onAllNodesWithTag("website_tab_card").fetchSemanticsNodes().size)
    }

    @Test fun websitesPageStillShowsItsKeptTab() {
        keptTab()
        rule.setContent { MTPlayerTheme { WebSitesScreen(onBack = {}, only = SavedSites.Kind.WEBSITE) } }
        rule.onNodeWithTag("website_tab_card").assertIsDisplayed()
    }
}
