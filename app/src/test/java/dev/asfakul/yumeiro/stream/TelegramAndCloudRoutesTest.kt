/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import dev.asfakul.yumeiro.online.telegram.TelegramPublic
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F-02 / F-03 (ADR-042). Before: a t.me post link fell through to the generic page probe (and a
 * private t.me/c link was probed too); an expired TeraBox / DiskWala stream had no refresher.
 */
class TelegramAndCloudRoutesTest {
    private val server = MockWebServer()

    @After fun tearDown() {
        TelegramPublic.base = "https://t.me"
        TelegramPublic.enabled = true
        TelegramPublic.clearCache()
        runCatching { server.close() }
    }

    @Test fun telegramLinksGetTheirOwnRoute() {
        assertEquals(LinkHosts.TelegramPost("animechan", 42), LinkHosts.classify("https://t.me/animechan/42"))
        assertEquals(LinkHosts.TelegramPrivate, LinkHosts.classify("https://t.me/c/1234567/89"))
        // A channel link is not a single video: it stays on the old path (Web sites opens it as a channel).
        assertNull(LinkHosts.classify("https://t.me/animechan"))
    }

    @Test fun flagOffKeepsTheOldPath() {
        TelegramPublic.enabled = false
        assertNull(LinkHosts.classify("https://t.me/animechan/42"))
    }

    @Test fun privateTelegramLinkFailsWithoutAnyRequest() {
        val r = StreamResolver.resolveBlocking(StreamRequest("https://t.me/c/1234567/89"), "UA", OkHttpClient())
        assertTrue(r is StreamResolver.Resolution.Failed)
        assertEquals(TelegramPublic.Messages.PRIVATE, (r as StreamResolver.Resolution.Failed).message)
    }

    @Test fun telegramPostPlaysThePreviewVideo() {
        val html = javaClass.classLoader!!.getResource("telegram/channel.html")!!.readText()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.url.encodedPath == "/yutest/6") MockResponse.Builder().body(html).build() else MockResponse.Builder().code(404).build()
        }
        server.start()
        TelegramPublic.base = server.url("/").toString()
        val r = StreamResolver.resolveBlocking(StreamRequest("https://t.me/yutest/6"), "UA", OkHttpClient())
        assertTrue("was $r", r is StreamResolver.Resolution.Playable)
        val req = (r as StreamResolver.Resolution.Playable).request
        assertEquals("https://cdn.example.test/file/6.mp4?token=abc", req.url)
        assertEquals("https://t.me/yutest/6", req.pageUrl)
        assertEquals(StreamResolver.TELEGRAM_GROUP, req.group)
        assertEquals(StreamFormat.PROGRESSIVE, req.format)
    }

    @Test fun cloudHostsAreKnown() {
        assertEquals("TeraBox", LinkHosts.cloudHost("https://www.1024terabox.com/s/1abc"))
        assertEquals("DiskWala", LinkHosts.cloudHost("https://www.diskwala.com/app/xyz"))
        assertNull(LinkHosts.cloudHost("https://example.com/v.mp4"))
        assertTrue(CloudRecapture.owns(StreamRequest("https://cdn.example/x.m3u8", pageUrl = "https://www.terabox.app/sharing/link?surl=abc")))
        assertFalse(CloudRecapture.owns(StreamRequest("https://cdn.example/x.m3u8", pageUrl = "https://example.com/watch")))
        assertFalse(CloudRecapture.owns(StreamRequest("https://cdn.example/x.m3u8")))
    }

    @Test fun recaptureBudgetIsTwoPerPagePerTenMinutes() {
        var now = 0L
        val b = CloudRecapture.Budget { now }
        assertTrue(b.take("p")); assertTrue(b.take("p")); assertFalse("third try within 10 minutes", b.take("p"))
        assertTrue("another page has its own budget", b.take("q"))
        now = CloudRecapture.Budget.WINDOW_MS + 1
        assertTrue(b.take("p"))
    }
}
