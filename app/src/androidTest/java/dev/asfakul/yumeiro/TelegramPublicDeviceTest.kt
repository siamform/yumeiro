/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.telegram.TelegramPublic
import dev.asfakul.yumeiro.online.telegram.TelegramSeen
import dev.asfakul.yumeiro.online.web.SavedSites
import dev.asfakul.yumeiro.stream.IncomingMedia
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import dev.asfakul.yumeiro.ui.tv.LocalTvMode
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections

/**
 * F-02 (ADR-042): a saved public Telegram channel opens its video posts (Telegram's web preview,
 * served here from a hand-written fixture), says which posts only Telegram's app can play,
 * searches with Telegram's own `?q=`, and a tap hands the post's video to the player.
 * It also shows the channel's header, marks posts new since the last visit and offers "Open in
 * Telegram" for app-only posts. Old code: a t.me site opened the page browser; no tg_ tags exist.
 */
@RunWith(AndroidJUnit4::class)
class TelegramPublicDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var server: MockWebServer
    private val paths = Collections.synchronizedList(mutableListOf<String>())
    private fun has(tag: String) = compose.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun waitTag(tag: String, ms: Long = 15_000) = runCatching { compose.waitUntil(ms) { has(tag) }; true }.getOrDefault(false)
    /**
     * FINAL-5: a lazy list composes only what fits. On tv-api34 (540 dp tall) post 6 was below the fold and
     * never composed (run 44595998571), so look for a row by scrolling the list to it, as a user would.
     */
    private fun row(tag: String): Boolean = runCatching { compose.onNodeWithTag("tg_list").performScrollToNode(hasTestTag(tag)); compose.waitForIdle(); has(tag) }.getOrDefault(false)
    private fun rowWithin(tag: String, ms: Long): Boolean {
        val end = android.os.SystemClock.uptimeMillis() + ms
        while (android.os.SystemClock.uptimeMillis() < end) { if (row(tag)) return true; Thread.sleep(300) }
        return row(tag)
    }
    private fun log(s: String) = Log.i("YuTelegram", s)

    @Before fun setUp() {
        val html = inst.context.assets.open("telegram/channel.html").use { it.readBytes().decodeToString() }
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                paths += request.url.encodedPath + (request.url.encodedQuery?.let { "?$it" } ?: "")
                return when (request.url.encodedPath) {
                    "/s/yutest" -> MockResponse.Builder().addHeader("Content-Type", "text/html; charset=utf-8").body(
                        if (request.url.queryParameter("q") != null) html.replace("data-post=\"yutest/6\"", "data-post=\"yutest/6\" data-hit=\"1\"").substringBefore("<div class=\"tgme_widget_message_wrap\"><div class=\"tgme_widget_message js-widget_message\" data-post=\"yutest/7\">") + "</section></body></html>"
                        else html,
                    ).build()
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
        TelegramPublic.base = server.url("/").toString()
        TelegramPublic.clearCache()
        inst.runOnMainSync {
            SavedSites.init(ctx); SavedSites.add(ctx, "@yutest")
            // The last visit saw up to post 6: 7–10 are new now.
            TelegramSeen.forget(ctx, "yutest"); TelegramSeen.mark(ctx, "yutest", 6)
        }
        IncomingMedia.consume()
    }

    @After fun tearDown() {
        TelegramPublic.base = "https://t.me"
        TelegramPublic.clearCache()
        inst.runOnMainSync { SavedSites.remove(ctx, TelegramPublic.channelUrl("yutest")) }
        IncomingMedia.consume()
        runCatching { server.close() }
    }

    @Test fun savedChannelListsVideoPostsSearchesAndPlays() {
        assertTrue("'@yutest' was not saved as t.me/s/yutest", SavedSites.sites.value.any { it.url == "https://t.me/s/yutest" && it.name == "@yutest" })
        compose.setContent { CompositionLocalProvider(LocalTvMode provides Edition.isTv) { MTPlayerTheme { dev.asfakul.yumeiro.ui.screens.sites.WebSitesScreen(onBack = {}, only = SavedSites.Kind.TELEGRAM) } } }
        assertTrue("no site row", waitTag("site_row"))
        compose.onAllNodesWithTag("site_row", true).onFirst().performClick()
        assertTrue("the channel screen didn't open (old code opens the page browser)", waitTag("tg_channel"))
        assertTrue("no post listed; requests=$paths", runCatching { compose.waitUntil(15_000) { compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("tg_post_*") { it.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.TestTag) { null }?.startsWith("tg_post_") == true }, true).fetchSemanticsNodes().isNotEmpty() }; true }.getOrDefault(false))
        // The channel's own header (top of the list), and what is new since the last visit.
        assertTrue("no channel header", row("tg_header"))
        assertTrue("no subscriber count", has("tg_subscribers"))
        assertTrue("file-name post 10 not listed", row("tg_post_10"))
        assertTrue("post 10 (after the last visit) isn't marked new", has("tg_new_10"))
        assertTrue("withheld post 7 not listed", row("tg_post_7"))
        assertTrue("post 6 not listed; requests=$paths", row("tg_post_6"))
        assertTrue("post 6 (seen last time) is marked new", !has("tg_new_6"))
        assertTrue("older posts button missing (data-before=5)", rowWithin("tg_older", 30_000))
        assertEquals("the newest post is remembered for the next visit", 10, TelegramSeen.get(ctx, "yutest"))
        FirstRun.shot("telegram_channel")

        // A withheld post says why, with a way to open it in Telegram, instead of a player that would fail.
        row("tg_post_7"); compose.onAllNodesWithTag("tg_post_7", true).onFirst().performClick()
        assertTrue("no card for the app-only post", rowWithin("tg_app_only", 15_000))
        // GROUP F (owner items 4 and 6): where sign-in runs, the card asks for it here; "Open in Telegram" only otherwise.
        if (dev.asfakul.yumeiro.online.telegram.td.TgAccounts.enabled(ctx)) assertTrue("no Sign in to play button", has("tg_sign_in_play") && !has("tg_open_app"))
        else assertTrue("no Open in Telegram button", has("tg_open_app"))
        assertEquals(null, IncomingMedia.pending.value)

        // Search goes to Telegram's own ?q= (GROUP F: the box opens from the search icon).
        compose.onAllNodesWithTag("tg_search_toggle", true).onFirst().performClick()
        assertTrue("search box", waitTag("tg_search", 5_000))
        compose.onAllNodesWithTag("tg_search", true).onFirst().performTextInput("Episode")
        compose.onAllNodesWithTag("tg_search_go", true).onFirst().performClick()
        compose.waitUntil(10_000) { paths.any { it.startsWith("/s/yutest?q=Episode") } }
        compose.waitUntil(10_000) { has("tg_post_6") && !has("tg_post_7") }
        FirstRun.shot("telegram_search")

        row("tg_post_6"); compose.onAllNodesWithTag("tg_post_6", true).onFirst().performClick()
        compose.waitUntil(5_000) { IncomingMedia.pending.value != null }
        val item = IncomingMedia.pending.value!!
        log("TG play uri=${item.uri} folder=${item.folderName} requests=$paths")
        assertEquals("https://cdn.example.test/file/6.mp4?token=abc", item.uri)
        assertEquals("Telegram", item.folderName)
    }
}
