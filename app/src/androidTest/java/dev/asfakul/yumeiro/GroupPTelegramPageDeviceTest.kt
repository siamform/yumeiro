/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.asfakul.yumeiro.online.telegram.td.TgChat
import dev.asfakul.yumeiro.online.telegram.td.TgMe
import dev.asfakul.yumeiro.ui.screens.sites.TelegramChatsPage
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * B8 (beta chapter), log tag YuGroupP: the signed-in Telegram chats page keeps no fixed account card or search
 * field over the list. The header has a search icon (opens the field, back closes it and clears it) and the
 * account's avatar (opens the account card with sign out); the chat kinds are chips inside the list.
 * The page itself is driven with plain data: signing in to a real Telegram account is MANUAL (P-B8-REAL).
 */
@RunWith(AndroidJUnit4::class)
class GroupPTelegramPageDeviceTest {
    @get:Rule val rule = createComposeRule()
    private fun log(s: String) = Log.i("YuGroupP", s)
    private fun n(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().size
    private fun tap(tag: String) { rule.onAllNodesWithTag(tag, true)[0].performClick(); rule.waitForIdle() }
    private fun texts(): List<String> = rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), true)
        .fetchSemanticsNodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { t -> t.text } }

    @Test fun b8_headerIconsAndScrollingFilters() {
        val chats = (1..40).map { i ->
            TgChat(i.toLong(), when (i % 3) { 0 -> "Anime channel $i"; 1 -> "Movie group $i"; else -> "Friend $i" },
                when (i % 3) { 0 -> TgChat.Kind.CHANNEL; 1 -> TgChat.Kind.GROUP; else -> TgChat.Kind.PRIVATE }, 0)
        }
        var signOut = 0
        var back = 0
        rule.setContent {
            MTPlayerTheme {
                TelegramChatsPage(
                    chats = chats, me = TgMe("Yumeiro Tester", "yumeiro_tester", 0) to null, loading = false, error = null,
                    photo = { null }, onOpen = {}, onRetry = {}, onSignOut = { signOut++ }, onBack = { back++ },
                )
            }
        }
        rule.waitForIdle()
        // Nothing fixed over the list: no account card, no search field until asked for.
        assertEquals("B8: no fixed account card", 0, n("tg_me"))
        assertEquals("B8: no fixed search field", 0, n("tg_search_field"))
        assertEquals("B8: a search icon in the header", 1, n("tg_search_open"))
        assertEquals("B8: an account icon in the header", 1, n("tg_me_button"))
        assertTrue("B8: kind chips in the list", n("tg_kinds") == 1 && n("tg_kind_channels") == 1)

        // Search: opens, filters as you type, back closes and clears.
        tap("tg_search_open")
        assertEquals("B8: search field opens", 1, n("tg_search_field"))
        rule.onAllNodesWithTag("tg_search_field", true)[0].performTextReplacement("anime")
        rule.waitForIdle()
        val shown = (1..40).count { n("tg_chat_$it") > 0 }
        val onlyAnime = (1..40).filter { n("tg_chat_$it") > 0 }.all { it % 3 == 0 }
        log("GROUPP_B8 search shown=$shown onlyAnime=$onlyAnime")
        assertTrue("B8: search narrows to matching chats", shown in 1..13 && onlyAnime)
        rule.onAllNodesWithTag("tg_search_field", true)[0].performTextReplacement("zzz-none")
        rule.waitForIdle()
        assertTrue("B8: no-match line with a hint", n("tg_chats_empty") == 1 && texts().any { "Try part of the name" in it })
        tap("tg_search_back")
        assertEquals("B8: search closes", 0, n("tg_search_field"))
        assertTrue("B8: list back in full", n("tg_chat_1") == 1)
        assertEquals("B8: closing search is not leaving the page", 0, back)

        // Kind chip.
        tap("tg_kind_groups")
        val groups = (1..12).filter { n("tg_chat_$it") > 0 }
        log("GROUPP_B8 groups=$groups")
        assertTrue("B8: Groups chip shows groups only", groups.isNotEmpty() && groups.all { it % 3 == 1 })
        tap("tg_kind_all")

        // Account icon: card with name, @username and sign out.
        tap("tg_me_button")
        val card = texts()
        log("GROUPP_B8 card=$card")
        assertTrue("B8: account card shows who is signed in", n("tg_me") == 1 && "Yumeiro Tester" in card && "@yumeiro_tester" in card)
        tap("tg_me_signout")
        assertEquals("B8: sign out asks the page (it confirms)", 1, signOut)
        assertEquals("B8: card closed", 0, n("tg_me"))
        log("GROUPP_B8_RESULT PASS")
    }
}
