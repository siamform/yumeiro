/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.groups

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.asfakul.yumeiro.online.telegram.TelegramAccountPlay
import dev.asfakul.yumeiro.online.telegram.TelegramPublic
import dev.asfakul.yumeiro.online.telegram.td.TgAccounts
import dev.asfakul.yumeiro.stream.Resolution
import dev.asfakul.yumeiro.stream.StreamRequest
import dev.asfakul.yumeiro.stream.StreamResolver
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * B16. Most "Telegram video doesn't play" reports were public posts Telegram keeps off its web preview ("Media is too
 * big"). Opened as a link (Network stream, shared link, history) they ended in "Telegram shows this video only in its
 * own app" even for a signed-in user, while the channel page played the same post with the account. The page fixture
 * is a real @durov channel page (saved 2026-11): post 552 is withheld, and @telegram post 441 has a public video.
 */
@RunWith(AndroidJUnit4::class)
class GroupB16TelegramTest {
    private val server = MockWebServer()
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun serve() {
        val html = javaClass.classLoader!!.getResource("telegram/real-2026-11.html")!!.readText()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.url.encodedPath.startsWith("/durov/") || request.url.encodedPath.startsWith("/telegram/")) MockResponse.Builder().body(html).build() else MockResponse.Builder().code(404).build()
        }
        server.start()
        TelegramPublic.base = server.url("/").toString()
        TelegramPublic.clearCache()
    }

    @After fun tearDown() {
        TelegramPublic.base = "https://t.me"
        TelegramPublic.clearCache()
        runCatching { server.close() }
    }

    @Test fun withheldPostLinkIsNotADeadEnd() {
        serve()
        val r = runBlocking { StreamResolver.resolve(ctx, StreamRequest("https://t.me/durov/552")) }
        assertTrue("was $r", r is Resolution.Failed)
        r as Resolution.Failed
        assertNotEquals("the bare 'only in its own app' dead end", TelegramPublic.Messages.APP_ONLY, r.message)
        val why = TgAccounts.unavailableReason(ctx)
        if (why != null) {
            // No TDLib here (JVM): the message says exactly why the account can't help.
            assertTrue(r.message, r.message.contains(why))
        } else {
            assertEquals(StreamResolver.TELEGRAM_SIGN_IN_TITLE, r.title)
            assertTrue(r.message, r.message.contains(TelegramAccountPlay.Words.NEEDS_SIGN_IN))
        }
    }

    @Test fun publicVideoStillPlaysWithoutTheAccount() {
        serve()
        val r = runBlocking { StreamResolver.resolve(ctx, StreamRequest("https://t.me/telegram/441")) }
        assertTrue("was $r", r is Resolution.Playable)
        assertEquals(StreamResolver.TELEGRAM_GROUP, (r as Resolution.Playable).request.group)
    }
}
