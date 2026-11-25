/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.sites

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import dev.asfakul.yumeiro.online.web.SavedSites
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * TG-PAGE-2 (UX tour probe 45155200957): the Telegram page's header buttons still said "Add websites from a list" and
 * "How websites work" to TalkBack, and its help showed the Websites guide. The Websites page keeps its own words.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class TelegramPageWordsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun telegramPageSpeaksAboutChannels() {
        rule.setContent { MTPlayerTheme { WebSitesScreen(onBack = {}, only = SavedSites.Kind.TELEGRAM) } }
        // GROUP F: import and help moved into the "More" menu.
        assertEquals(0, rule.onAllNodesWithContentDescription("Add websites from a list").fetchSemanticsNodes().size)
        rule.onNodeWithContentDescription("More").performClick()
        rule.onNodeWithText("Add channels from a list").assertIsDisplayed()
        rule.onNodeWithText("How Telegram works").performClick()
        rule.onNodeWithText("Watch Telegram channels in Yumeiro").assertIsDisplayed()
    }

    @Test fun websitesPageKeepsItsWords() {
        rule.setContent { MTPlayerTheme { WebSitesScreen(onBack = {}, only = SavedSites.Kind.WEBSITE) } }
        rule.onNodeWithContentDescription("How websites work").performClick()
        rule.onNodeWithText("Watch any website in Yumeiro's player").assertIsDisplayed()
    }

    @Test fun websitesListImportLivesInSettings() {
        rule.setContent { MTPlayerTheme { WebSitesScreen(onBack = {}, only = SavedSites.Kind.WEBSITE) } }
        // GROUP E: list import moved into Website settings.
        rule.onNodeWithContentDescription("Website settings").performClick()
        rule.waitForIdle()
        assertEquals(1, rule.onAllNodesWithText("Add websites from a list", useUnmergedTree = true).fetchSemanticsNodes().size)
    }
}
