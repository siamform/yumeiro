/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.sites

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.online.telegram.TelegramAccountPlay
import dev.asfakul.yumeiro.online.telegram.td.TgMiniApp
import dev.asfakul.yumeiro.online.web.SavedSites
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * GROUP F: the Telegram page is modular. The header holds icons (search, add, user, more); the list holds only
 * channels; the account (sign out, bot switch, chats) sits behind the user button; a withheld post asks for
 * sign-in in Yumeiro (owner items 4, 6) instead of sending the user to the Telegram app.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class GroupFTelegramTest {
    @get:Rule val rule = createComposeRule()
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()

    @Test fun headerIsIconsAndListHasNoCards() {
        rule.setContent { MTPlayerTheme { WebSitesScreen(onBack = {}, only = SavedSites.Kind.TELEGRAM) } }
        rule.waitForIdle()
        for (t in listOf("telegram_screen", "tg_add_open", "tg_user", "tg_more")) assertTrue("missing $t", has(t))
        for (t in listOf("site_add_field", "tg_account_card", "site_help_hint", "tg_home_search", "tg_logout", "tg_miniapp_switch"))
            assertFalse("$t should be gone from the page", has(t))
        assertTrue("empty state explains adding", has("tg_empty") && has("tg_empty_add"))
        rule.onNodeWithTag("tg_more").performClick()
        for (t in listOf("site_import", "site_help")) assertTrue("menu has $t", has(t))
        rule.onNodeWithTag("site_help").performClick()
        assertTrue("help opens", has("site_help_close"))
    }

    @Test fun signedInSheetHoldsChatsBotAndSignOut() {
        TgMiniApp.setConsented(ctx, false)
        var chats = 0
        rule.setContent { MTPlayerTheme { TelegramUserSheet(signedIn = true, me = null, unavailable = null, onDismiss = {}, onChats = { chats++ }, onSignIn = {}, onSignedOut = {}) } }
        rule.waitForIdle()
        for (t in listOf("tg_user_sheet", "tg_user_me", "tg_user_chats", "tg_user_bot_row", "tg_user_signout")) assertTrue("missing $t", has(t))
        assertFalse(has("tg_user_sign_in"))
        rule.onNodeWithTag("tg_user_bot_row", true).performClick(); rule.waitForIdle()
        assertTrue("bot switch turns the mini app consent on", TgMiniApp.consented(ctx))
        rule.onNodeWithTag("tg_user_chats", true).performClick(); rule.waitForIdle()
        assertEquals(1, chats)
        rule.onNodeWithTag("tg_user_signout", true).performClick(); rule.waitForIdle()
        rule.onNodeWithTag("tg_logout_confirm").assertIsDisplayed()
    }

    @Test fun signedOutAndUnavailableSheets() {
        var mode by androidx.compose.runtime.mutableStateOf<String?>(null)
        rule.setContent { MTPlayerTheme { TelegramUserSheet(signedIn = false, me = null, unavailable = mode, onDismiss = {}, onChats = {}, onSignIn = {}, onSignedOut = {}) } }
        rule.waitForIdle()
        assertTrue(has("tg_user_sign_in")); assertFalse(has("tg_user_signout"))
        mode = "This phone can't run Telegram sign-in."
        rule.waitForIdle()
        assertTrue(has("tg_account_unavailable")); assertFalse(has("tg_user_sign_in"))
    }

    @Test fun words() {
        assertFalse(TelegramAccountPlay.Words.NEEDS_SIGN_IN.contains("Telegram app"))
        assertTrue(TelegramAccountPlay.Words.NEEDS_SIGN_IN.contains("in Yumeiro"))
        assertEquals(TelegramAccountPlay.Words.ROW_SIGN_IN, TelegramAccountPlay.rowNote(false))
        assertEquals(TelegramAccountPlay.Words.ROW_ACCOUNT, TelegramAccountPlay.rowNote(true))
        assertEquals("1 channel added, 2 already saved", TelegramHomeWords.imported(1, 2, 0, 0))
        assertTrue(TelegramHomeWords.empty(true).endsWith("watch your own chats."))
        assertFalse(TelegramHomeWords.empty(false).contains("sign in"))
    }
}
