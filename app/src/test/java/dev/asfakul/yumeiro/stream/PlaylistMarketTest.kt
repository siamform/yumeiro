/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlaylistMarketTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var server: MockWebServer

    @Before fun setUp() {
        java.io.File(context.filesDir, "streams").deleteRecursively()
        PlaylistStore.resetForTests(); PlaylistMarket.resetForTests()
        // Entries in these tests point at the local MockWebServer instead of the official market branch.
        PlaylistMarket.entryUrlAllowed = { it.startsWith("http://") || it.startsWith("https://") }
        server = MockWebServer(); server.start()
    }
    @After fun tearDown() { server.close() }

    private fun index(vararg entries: String) = """{"version":1,"generated":"2026-11-03T06:00:00Z","categories":["Sports","Country"],"playlists":[${entries.joinToString(",")}]}"""
    private fun entry(id: String, sha: String, url: String = server.url("/$id.m3u").toString(), extra: String = "") =
        """{"id":"$id","name":"Name $id","category":"Sports","repo":"o/$id","stars":42,"url":"$url","source":"${server.url("/src/$id.m3u")}","channels":2,"groups":1,"sha256":"$sha","updated":"2026-11-02T00:00:00Z","alive":6,"sampled":8,"ok":true$extra}"""
    private fun m3u(vararg titles: String) = "#EXTM3U\n" + titles.joinToString("") { "#EXTINF:-1 group-title=\"G\",$it\nhttps://cdn.example/$it.m3u8\n" }

    @Test fun parsesCatalogAndSkipsBrokenEntries() {
        val text = index(
            entry("good", "s1"),
            """{"id":"no url","name":"x"}""",
            """{"id":"bad id with spaces","url":"https://x/y.m3u"}""",
            """{"id":"ftp","url":"ftp://x/y.m3u"}""",
            entry("good", "dupe"),
        )
        val c = PlaylistMarket.parse(text)
        assertEquals(listOf("good"), c.entries.map { it.id })
        val e = c.entries.single()
        assertEquals(42, e.stars); assertEquals("o/good", e.repo); assertEquals(0.75f, e.health!!, 0.001f)
        assertTrue(e.updated > 0); assertTrue(c.generated > 0)
        assertEquals(listOf("Sports"), c.categories) // categories without entries are hidden
        assertEquals("m_good", e.installId)
    }

    @Test fun productionCatalogOnlyAcceptsOfficialMarketUrls() {
        PlaylistMarket.resetForTests()
        val c = PlaylistMarket.parse(index(
            entry("ok", "s", url = "https://raw.githubusercontent.com/siamform/yumeiro/market/playlists/ok.m3u"),
            entry("evil", "s", url = "https://evil.example/siamform/yumeiro/market/x.m3u"),
            entry("plain", "s", url = "http://raw.githubusercontent.com/siamform/yumeiro/market/playlists/p.m3u"),
            entry("fork", "s", url = "https://raw.githubusercontent.com/someone/yumeiro/market/playlists/f.m3u"),
        ))
        assertEquals(listOf("ok"), c.entries.map { it.id })
    }

    @Test fun malformedCatalogThrowsButMissingFieldsAreTolerated() {
        assertTrue(runCatching { PlaylistMarket.parse("<html>") }.isFailure)
        val c = PlaylistMarket.parse("""{"playlists":[{"id":"a","url":"https://x/a.m3u"}]}""")
        assertEquals("Community", c.entries.single().category)
        assertNull(c.entries.single().health)
        assertEquals(listOf("Community"), c.categories)
    }

    @Test fun installUsesMirrorThenFallsBackToSource() = runBlocking {
        PlaylistStore.init(context)
        server.enqueue(MockResponse.Builder().code(503).build()) // mirror down
        server.enqueue(MockResponse.Builder().body(m3u("A", "B")).build()) // original source
        val e = PlaylistMarket.parse(index(entry("sp", "sha1"))).entries.single()
        val p = PlaylistStore.installFromMarket(context, e)
        assertEquals("m_sp", p.id); assertEquals("sp", p.marketId); assertEquals("sha1", p.marketSha); assertEquals(2, p.channelCount)
        assertTrue(p.autoUpdate)
        assertEquals("/sp.m3u", server.takeRequest().url.encodedPath)
        assertEquals("/src/sp.m3u", server.takeRequest().url.encodedPath)
        // Installing again is a no-op that returns the installed copy.
        assertSame(PlaylistStore.get("m_sp"), PlaylistStore.installFromMarket(context, e))
        assertEquals(listOf("A", "B"), PlaylistStore.channels(context, p.id).map { it.title })
    }

    @Test fun installFailsCleanlyWhenEverythingIsDown() = runBlocking {
        PlaylistStore.init(context)
        server.enqueue(MockResponse.Builder().code(404).build())
        server.enqueue(MockResponse.Builder().body("<html>blocked</html>").build())
        val e = PlaylistMarket.parse(index(entry("x", "s"))).entries.single()
        assertTrue(runCatching { PlaylistStore.installFromMarket(context, e) }.isFailure)
        assertNull(PlaylistStore.get("m_x"))
    }

    @Test fun detectsAndAppliesUpdatesRespectingAutoUpdate() = runBlocking {
        PlaylistStore.init(context)
        server.enqueue(MockResponse.Builder().body(m3u("A")).build())
        server.enqueue(MockResponse.Builder().body(m3u("A")).build())
        val v1 = PlaylistMarket.parse(index(entry("a", "v1"), entry("b", "v1")))
        PlaylistStore.installFromMarket(context, v1.entries[0])
        PlaylistStore.installFromMarket(context, v1.entries[1])
        PlaylistStore.setAutoUpdate(context, "m_b", false)
        assertTrue(PlaylistMarket.updatesFor(PlaylistStore.playlists.value, v1).isEmpty())

        val v2 = PlaylistMarket.parse(index(entry("a", "v2"), entry("b", "v2")))
        assertEquals(setOf("m_a", "m_b"), PlaylistMarket.updatesFor(PlaylistStore.playlists.value, v2).map { it.first.id }.toSet())

        // Background (auto) run: only "a" is updated.
        server.enqueue(MockResponse.Builder().body(m3u("A", "B", "C")).build())
        val auto = PlaylistStore.applyMarketUpdates(context, v2, onlyAuto = true)
        assertEquals(listOf("Name a"), auto.updated)
        assertEquals("v2", PlaylistStore.get("m_a")!!.marketSha); assertEquals(3, PlaylistStore.get("m_a")!!.channelCount)
        assertEquals("v1", PlaylistStore.get("m_b")!!.marketSha)

        // Manual "Update all" also updates "b".
        server.enqueue(MockResponse.Builder().body(m3u("X", "Y")).build())
        val manual = PlaylistStore.applyMarketUpdates(context, v2, onlyAuto = false)
        assertEquals(listOf("Name b"), manual.updated)
        assertTrue(PlaylistMarket.updatesFor(PlaylistStore.playlists.value, v2).isEmpty())
        // Persisted.
        PlaylistStore.resetForTests(); PlaylistStore.init(context)
        assertEquals("v2", PlaylistStore.get("m_b")!!.marketSha); assertFalse(PlaylistStore.get("m_b")!!.autoUpdate)
    }

    @Test fun failedUpdateKeepsOldCopyAndOldSha() = runBlocking {
        PlaylistStore.init(context)
        server.enqueue(MockResponse.Builder().body(m3u("A", "B")).build())
        PlaylistStore.installFromMarket(context, PlaylistMarket.parse(index(entry("a", "v1"))).entries.single())
        server.enqueue(MockResponse.Builder().code(500).build())
        server.enqueue(MockResponse.Builder().code(500).build())
        val r = PlaylistStore.applyMarketUpdates(context, PlaylistMarket.parse(index(entry("a", "v2"))), onlyAuto = false)
        assertEquals(listOf("Name a"), r.failed)
        assertEquals("v1", PlaylistStore.get("m_a")!!.marketSha)
        assertEquals(listOf("A", "B"), PlaylistStore.channels(context, "m_a").map { it.title })
    }

    @Test fun friendlyErrorsAreReadable() {
        assertEquals("No internet connection", PlaylistMarket.friendlyError(java.net.UnknownHostException("x")))
        assertTrue(PlaylistMarket.friendlyError(IllegalStateException("HTTP 404")).contains("being prepared"))
        assertEquals("Could not load the market", PlaylistMarket.friendlyError(RuntimeException()))
    }

    @Test fun marketPlaylistsDoNotStaleRefreshOnOpen() = runBlocking {
        PlaylistStore.init(context)
        server.enqueue(MockResponse.Builder().body(m3u("A")).build())
        PlaylistStore.installFromMarket(context, PlaylistMarket.parse(index(entry("a", "v1"))).entries.single())
        val before = server.requestCount
        // Even if old, opening a market playlist uses the cache (updates go through the catalog).
        repeat(3) { PlaylistStore.channels(context, "m_a") }
        assertEquals(before, server.requestCount)
    }
}
