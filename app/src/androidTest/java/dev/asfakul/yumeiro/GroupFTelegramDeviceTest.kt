/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.telegram.TelegramAccountPlay
import dev.asfakul.yumeiro.online.telegram.TelegramPublic
import dev.asfakul.yumeiro.online.telegram.TelegramSeen
import dev.asfakul.yumeiro.online.telegram.td.TgAccounts
import dev.asfakul.yumeiro.online.web.SavedSites
import dev.asfakul.yumeiro.stream.IncomingMedia
import dev.asfakul.yumeiro.ui.screens.sites.WebSitesScreen
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import dev.asfakul.yumeiro.ui.tv.LocalTvMode
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.util.Collections

/**
 * GROUP F on a device (phones and TV): the Telegram page is icons only (search, add, user, more), the list holds
 * just the channels, adding goes through the + sheet, the user button goes to sign-in when nobody is signed in,
 * and (owner items 4 and 6) a post Telegram keeps for signed-in users asks for sign-in here and comes back to the
 * channel afterwards, instead of "only in the Telegram app". The channel is a fixture served by a local server.
 *
 * Old code: an add card, an account card and a help line sit in the list; there is no user button; a withheld
 * post offers only "Open in Telegram".
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class GroupFTelegramDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = inst.targetContext
    private lateinit var server: MockWebServer
    private val paths = Collections.synchronizedList(mutableListOf<String>())
    private val tv = Edition.isTv

    private fun has(tag: String) = compose.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun waitTag(tag: String, ms: Long = 15_000) = runCatching { compose.waitUntil(ms) { has(tag) }; true }.getOrDefault(false)
    private fun waitGone(tag: String, ms: Long = 10_000) = runCatching { compose.waitUntil(ms) { !has(tag) }; true }.getOrDefault(false)
    private fun click(tag: String) { compose.onAllNodesWithTag(tag, true).onFirst().performClick(); compose.waitForIdle() }
    private fun text(tag: String): String = compose.onAllNodesWithTag(tag, true).fetchSemanticsNodes().firstOrNull()
        ?.config?.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun row(tag: String): Boolean = runCatching { compose.onNodeWithTag("tg_list").performScrollToNode(hasTestTag(tag)); compose.waitForIdle(); has(tag) }.getOrDefault(false)
    private fun log(s: String) = Log.i("YuGroupF", s)

    @Before fun setUp() {
        val html = inst.context.assets.open("telegram/channel.html").use { it.readBytes().decodeToString() }
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                paths += request.url.encodedPath
                return if (request.url.encodedPath == "/s/yutest") MockResponse.Builder().addHeader("Content-Type", "text/html; charset=utf-8").body(html).build()
                else MockResponse.Builder().code(404).build()
            }
        }
        server.start()
        TelegramPublic.base = server.url("/").toString()
        TelegramPublic.clearCache()
        inst.runOnMainSync {
            SavedSites.init(ctx)
            SavedSites.sites.value.filter { SavedSites.kindOf(it.url) == SavedSites.Kind.TELEGRAM }.forEach { SavedSites.remove(ctx, it.url) }
            SavedSites.add(ctx, "@yutest")
            TelegramSeen.forget(ctx, "yutest")
        }
        IncomingMedia.consume()
        compose.setContent { CompositionLocalProvider(LocalTvMode provides tv) { MTPlayerTheme { WebSitesScreen(onBack = {}, only = SavedSites.Kind.TELEGRAM) } } }
        assertTrue("the Telegram page did not open", waitTag("telegram_screen"))
    }

    @After fun tearDown() {
        TelegramPublic.base = "https://t.me"
        TelegramPublic.clearCache()
        inst.runOnMainSync { SavedSites.sites.value.filter { SavedSites.kindOf(it.url) == SavedSites.Kind.TELEGRAM }.forEach { SavedSites.remove(ctx, it.url) } }
        IncomingMedia.consume()
        runBlocking { TgAccounts.closeAll() }
        runCatching { server.close() }
    }

    @Test fun a_headerIsIconsAndTheListIsChannels() {
        for (t in listOf("tg_home_search_toggle", "tg_add_open", "tg_user", "tg_more")) assertTrue("header has $t", has(t))
        for (t in listOf("site_add_field", "tg_account_card", "site_help_hint", "tg_home_search", "site_import")) assertFalse("$t is not on the page any more", has(t))
        assertTrue("the saved channel is listed", waitTag("site_row"))
        click("tg_more")
        assertTrue("menu: import", waitTag("site_import", 5_000)); assertTrue("menu: export", has("site_export")); assertTrue("menu: help", has("site_help"))
        click("site_help")
        assertTrue("help opens", waitTag("site_help_close", 5_000)); click("site_help_close")
        click("tg_home_search_toggle")
        assertTrue("search box opens", waitTag("tg_home_search", 5_000))
        compose.onNodeWithTag("tg_home_search", true).performTextInput("zzzz")
        assertTrue("search filters the channels", waitTag("site_no_match", 5_000))
        click("tg_home_search_toggle")
        assertTrue("closing search shows the list again", waitGone("tg_home_search") && waitTag("site_row", 5_000))
        FirstRun.shot("groupf_header")
        log("GROUPF_A PASS tv=$tv")
    }

    @Test fun b_addSheetSavesAChannelAndExplainsAWrongOne() {
        click("tg_add_open")
        assertTrue("add sheet", waitTag("tg_add_field", 5_000))
        compose.onNodeWithTag("tg_add_field", true).performTextInput("example.com")
        click("tg_add_add")
        assertTrue("a website gets a reason in the sheet", waitTag("tg_add_error", 5_000))
        val reason = text("tg_add_error")
        log("GROUPF_B reason='$reason'")
        assertTrue("reason is the real one ('$reason')", reason.contains("Websites", true))
        assertFalse("no Retry for a typing mistake", has("tg_add_retry"))
        compose.onNodeWithTag("tg_add_field", true).performTextReplacement("@yufnew")
        click("tg_add_add")
        assertTrue("sheet closes", waitGone("tg_add_field"))
        assertTrue("channel saved", SavedSites.sites.value.any { it.url == TelegramPublic.channelUrl("yufnew") })
        log("GROUPF_B PASS tv=$tv")
    }

    @Test fun c_userButtonWithNobodySignedInGoesToSignIn() {
        val enabled = TgAccounts.enabled(ctx)
        val signedIn = TgAccounts.signedInHint(ctx)
        log("GROUPF_C enabled=$enabled signedIn=$signedIn")
        click("tg_user")
        if (enabled && !signedIn) {
            assertTrue("the user button did not open sign-in", waitTag("tg_account", 10_000) || has("tg_keys"))
            click("subpage_back")
            assertTrue("Back returns to the Telegram page", waitTag("telegram_screen", 10_000))
        } else {
            assertTrue("the user sheet did not open", waitTag("tg_user_sheet", 5_000))
            if (!enabled) assertTrue("the sheet says why sign-in can't run", has("tg_account_unavailable"))
            else for (t in listOf("tg_user_chats", "tg_user_bot_row", "tg_user_signout")) assertTrue("user sheet has $t", has(t))
        }
        log("GROUPF_C PASS tv=$tv")
    }

    @Test fun d_withheldPostAsksForSignInHereAndComesBack() {
        click("site_row")
        assertTrue("channel screen", waitTag("tg_channel"))
        assertTrue("posts load; requests=$paths", runCatching { compose.waitUntil(20_000) { row("tg_post_7") }; true }.getOrDefault(false))
        val enabled = TgAccounts.enabled(ctx)
        if (enabled) assertTrue("the withheld row says it plays after sign-in",
            compose.onAllNodesWithText(TelegramAccountPlay.Words.ROW_SIGN_IN, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue("no 'only in the Telegram app' wording",
            compose.onAllNodesWithText("Only in the Telegram app", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        compose.onAllNodesWithTag("tg_post_7", true).onFirst().performClick()
        assertTrue("no card for the withheld post", runCatching { compose.waitUntil(15_000) { row("tg_app_only") }; true }.getOrDefault(false))
        assertEquals("nothing was handed to the player", null, IncomingMedia.pending.value)
        val words = text("tg_app_only_text")
        log("GROUPF_D enabled=$enabled card='$words'")
        if (enabled) {
            assertEquals(TelegramAccountPlay.Words.NEEDS_SIGN_IN, words)
            assertTrue("Sign in to play", has("tg_sign_in_play"))
            assertFalse("no Open in Telegram where sign-in works", has("tg_open_app"))
            FirstRun.shot("groupf_withheld")
            click("tg_sign_in_play")
            assertTrue("sign-in did not open", waitTag("tg_account", 10_000) || has("tg_keys"))
            // The account page swaps its header from "Getting ready" to the phone step while TDLib starts; on the 2 GB
            // emulator a tap landing in that swap was lost (run 45694288259). Tap again only if the page is still there.
            click("subpage_back")
            var back = waitTag("tg_channel", 10_000)
            if (!back && has("tg_account")) { log("GROUPF_D back tap lost during the header swap; tapping again"); click("subpage_back"); back = waitTag("tg_channel", 10_000) }
            assertTrue("after sign-in Back returns to the same channel (account=${has("tg_account")} home=${has("telegram_screen")})", back)
        } else {
            assertTrue("where sign-in can't run, Open in Telegram stays", has("tg_open_app"))
        }
        log("GROUPF_D PASS tv=$tv")
    }

    @Test fun e_channelSearchIsAnIcon() {
        click("site_row")
        assertTrue("channel screen", waitTag("tg_channel"))
        assertFalse("search box is hidden until asked for", has("tg_search"))
        click("tg_search_toggle")
        assertTrue("the search icon opens the box", waitTag("tg_search", 5_000))
        compose.onNodeWithTag("tg_search", true).performTextInput("Episode")
        click("tg_search_go")
        assertTrue("search goes to Telegram's ?q=; requests=$paths", runCatching { compose.waitUntil(10_000) { paths.count { it == "/s/yutest" } >= 2 }; true }.getOrDefault(false))
        log("GROUPF_E PASS tv=$tv")
    }
}
