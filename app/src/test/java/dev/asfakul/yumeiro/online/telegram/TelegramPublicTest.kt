/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.telegram

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** F-02 (ADR-042): public Telegram links and Telegram's web-preview markup (hand-written fixtures). */
class TelegramPublicTest {
    private fun fixture(name: String) = javaClass.classLoader!!.getResource("telegram/$name")!!.readText()

    private val server = MockWebServer()
    private val hits = AtomicInteger()
    private val client = OkHttpClient()

    @Before fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                hits.incrementAndGet()
                val path = request.url.encodedPath
                return when {
                    path == "/s/yutest" -> MockResponse.Builder().addHeader("Content-Type", "text/html").body(fixture("channel.html")).build()
                    path == "/yutest/6" && request.url.queryParameter("embed") == "1" -> MockResponse.Builder().body(fixture("channel.html")).build()
                    path == "/yutest/7" -> MockResponse.Builder().body(fixture("channel.html")).build()
                    path == "/yutest/99" -> MockResponse.Builder().body(fixture("post_error.html")).build()
                    path == "/s/blocked" -> MockResponse.Builder().code(503).build()
                    else -> MockResponse.Builder().code(404).build()
                }
            }
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

    @Test fun linksAreClassified() {
        assertEquals(TelegramPublic.Post("animechan", 42), TelegramPublic.parse("https://t.me/animechan/42"))
        assertEquals(TelegramPublic.Post("animechan", 42), TelegramPublic.parse("https://t.me/s/animechan/42?single"))
        assertEquals(TelegramPublic.Post("animechan", 42), TelegramPublic.parse("t.me/animechan/42"))
        assertEquals(TelegramPublic.Channel("animechan"), TelegramPublic.parse("https://t.me/animechan"))
        assertEquals(TelegramPublic.Channel("animechan"), TelegramPublic.parse("https://telegram.me/s/animechan"))
        assertEquals(TelegramPublic.Channel("animechan"), TelegramPublic.parse("@animechan"))
        assertEquals(TelegramPublic.Private, TelegramPublic.parse("https://t.me/c/1234567/89"))
        assertEquals(TelegramPublic.Private, TelegramPublic.parse("https://t.me/+AbCdEf123"))
        assertEquals(TelegramPublic.Private, TelegramPublic.parse("https://t.me/joinchat/AbCdEf"))
        // Not channels: reserved paths, too short names, other hosts, other schemes.
        assertNull(TelegramPublic.parse("https://t.me/share/url?url=x"))
        assertNull(TelegramPublic.parse("https://t.me/ab"))
        assertEquals(TelegramPublic.Channel("abc"), TelegramPublic.parse("https://t.me/abc"))
        assertNull(TelegramPublic.parse("https://example.com/animechan/42"))
        // Telegram's own app links (as Hikari accepts them).
        assertEquals(TelegramPublic.Channel("animechan"), TelegramPublic.parse("tg://resolve?domain=animechan"))
        assertEquals(TelegramPublic.Post("animechan", 42), TelegramPublic.parse("tg://resolve?domain=animechan&post=42"))
        assertEquals(TelegramPublic.Private, TelegramPublic.parse("tg://privatepost?channel=123&post=4"))
        assertNull(TelegramPublic.parse("tg://settings"))
        assertEquals(TelegramPublic.Post("animechan", 42), TelegramPublic.parse("https://t.me/animechan/42?comment=7"))
        assertNull(TelegramPublic.parse("@a"))
        assertEquals("animechan", TelegramPublic.channelOf("https://t.me/s/animechan"))
        assertNull(TelegramPublic.channelOf("https://t.me/animechan/42"))
    }

    @Test fun channelPageListsVideoPostsOnly() {
        val page = TelegramPublic.parsePage(fixture("channel.html"), "yutest")
        assertEquals("Yumeiro Test Channel", page.title)
        assertEquals(5, page.before)
        assertEquals(listOf(6, 7, 8, 9, 10), page.posts.map { it.id })
        val p6 = page.posts.first { it.id == 6 }
        assertEquals("https://cdn.example.test/file/6.mp4?token=abc", p6.videoUrl)
        assertEquals("https://cdn.example.test/thumb6.jpg", p6.thumbUrl)
        assertEquals("1:23", p6.duration)
        assertEquals("2026-11-02T10:00:00+00:00", p6.date)
        assertTrue(p6.text.startsWith("Episode 1"))
        assertEquals("https://t.me/yutest/6", p6.link)
        // Telegram keeps this one for its apps: listed, but not playable (never faked).
        val p7 = page.posts.first { it.id == 7 }
        assertNull(p7.videoUrl)
        assertEquals("24:10", p7.duration)
        assertEquals(1, page.withheld)
        // A video sent as a file: its file name is the title; data-src and the poster attribute are read.
        val p10 = page.posts.first { it.id == 10 }
        assertEquals("Show.S01E03.1080p.mkv", p10.text)
        assertEquals("https://cdn.example.test/file/10.mp4", p10.videoUrl)
        assertEquals("https://cdn.example.test/poster10.jpg", p10.thumbUrl)
        // Album: each video of the post is its own entry.
        assertEquals("https://cdn.example.test/file/9.mp4", page.posts.first { it.id == 9 }.videoUrl)
    }

    @Test fun channelHeaderIsRead() {
        val page = TelegramPublic.parsePage(fixture("channel.html"), "yutest")
        assertEquals("https://cdn.example.test/avatar.jpg", page.avatarUrl)
        assertEquals("12.3K", page.subscribers)
        assertEquals("Test episodes for Yumeiro.", page.description)
        // A post page (embed) has no header: all three stay empty, nothing is invented.
        val bare = TelegramPublic.parsePage("<html><body></body></html>", "yutest")
        assertNull(bare.avatarUrl); assertNull(bare.subscribers); assertNull(bare.description)
    }

    @Test fun newSinceLastVisit() {
        assertTrue("first visit: nothing is new", TelegramSeen.newIds(null, listOf(5, 6, 7)).isEmpty())
        assertEquals(setOf(7, 8), TelegramSeen.newIds(6, listOf(5, 6, 7, 8)))
        assertTrue(TelegramSeen.newIds(8, listOf(5, 6, 7, 8)).isEmpty())
    }

    @Test fun layoutChangeGivesNoPostsNotACrash() {
        val page = TelegramPublic.parsePage("<html><body><div class='something_new'></div></body></html>", "yutest")
        assertTrue(page.posts.isEmpty())
        assertNull(page.before)
        assertEquals("@yutest", page.title)
    }

    @Test fun fetchChannelUsesThePreviewAndCachesTenMinutes() {
        val r = TelegramPublic.fetchChannel(client, "yutest", now = 1_000L) as TelegramPublic.Result.Ok
        assertEquals(5, r.value.posts.size)
        TelegramPublic.fetchChannel(client, "yutest", now = 1_000L + TelegramPublic.CACHE_MS - 1)
        assertEquals("second read within 10 minutes comes from the cache", 1, hits.get())
        TelegramPublic.fetchChannel(client, "yutest", now = 1_000L + TelegramPublic.CACHE_MS + 1)
        assertEquals(2, hits.get())
    }

    @Test fun fetchFailuresHaveHonestMessages() {
        val missing = TelegramPublic.fetchChannel(client, "nosuchchannel") as TelegramPublic.Result.Failed
        assertEquals(TelegramPublic.Messages.noPosts("nosuchchannel"), missing.message)
        val down = TelegramPublic.fetchChannel(client, "blocked") as TelegramPublic.Result.Failed
        assertEquals(TelegramPublic.Messages.OFFLINE, down.message)
        val appOnly = TelegramPublic.fetchPost(client, "yutest", 7) as TelegramPublic.Result.Failed
        assertEquals(TelegramPublic.Messages.APP_ONLY, appOnly.message)
        val gone = TelegramPublic.fetchPost(client, "yutest", 99) as TelegramPublic.Result.Failed
        assertEquals(TelegramPublic.Messages.noVideo("yutest", 99), gone.message)
        server.close()
        val offline = TelegramPublic.fetchChannel(OkHttpClient(), "yutest") as TelegramPublic.Result.Failed
        assertEquals(TelegramPublic.Messages.OFFLINE, offline.message)
    }

    @Test fun searchAndPagingUseTelegramsOwnParameters() {
        assertEquals("${TelegramPublic.base.trimEnd('/')}/s/yutest?q=episode+2", TelegramPublic.pageUrl("yutest", query = "episode 2"))
        assertEquals("${TelegramPublic.base.trimEnd('/')}/s/yutest?before=5", TelegramPublic.pageUrl("yutest", before = 5))
        assertEquals("${TelegramPublic.base.trimEnd('/')}/s/yutest", TelegramPublic.pageUrl("yutest", query = "  "))
    }

    @Test fun fetchPostReturnsThatPostsVideo() {
        val r = TelegramPublic.fetchPost(client, "yutest", 6) as TelegramPublic.Result.Ok
        assertEquals("https://cdn.example.test/file/6.mp4?token=abc", r.value.videoUrl)
    }
}
