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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * G7 LT-4: remote playlists are downloaded to a temp file and validated line by line. Same answers
 * as before (web page, single HLS stream, empty list, too large), the saved file is the body as
 * served, and no temp file is left behind on success or failure.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RemotePlaylistStreamTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val dir get() = File(context.filesDir, "streams")
    private lateinit var server: MockWebServer

    @Before fun setUp() {
        dir.deleteRecursively()
        PlaylistStore.resetForTests()
        server = MockWebServer(); server.start()
    }
    @After fun tearDown() { server.close() }

    private fun m3u(vararg titles: String) = "\uFEFF#EXTM3U\r\n" + titles.joinToString("") { "#EXTINF:-1 group-title=\"G\",$it\r\nhttps://cdn.example/$it.m3u8\r\n" }
    private fun parts() = dir.listFiles { f -> f.name.endsWith(".part") }.orEmpty().toList()
    private fun failure(block: suspend () -> Any?): String? = runBlocking { runCatching { block() }.exceptionOrNull()?.message }

    @Test fun addAndRefreshKeepTheBodyAndLeaveNoTempFile() = runBlocking {
        val body = m3u("A", "B", "C")
        server.enqueue(MockResponse.Builder().body(body).build())
        val p = PlaylistStore.addRemote(context, "Mine", server.url("/list.m3u").toString())
        assertEquals(3, p.channelCount)
        val saved = File(dir, "ch_${p.id.filter { it.isLetterOrDigit() || it == '_' || it == '-' }}.m3u")
        assertEquals(body, saved.readText())
        assertEquals(listOf("A", "B", "C"), PlaylistStore.channels(context, p.id).map { it.title })
        server.enqueue(MockResponse.Builder().body(m3u("A", "B", "C", "D")).build())
        assertEquals(4, PlaylistStore.refresh(context, p.id))
        assertEquals(4, PlaylistStore.get(p.id)!!.channelCount)
        assertEquals(4, PlaylistStore.channels(context, p.id).size)
        assertTrue(parts().isEmpty())
    }

    @Test fun badBodiesAreRejectedAsBeforeAndTheSavedCopyIsKept() = runBlocking {
        server.enqueue(MockResponse.Builder().body(m3u("A", "B")).build())
        val p = PlaylistStore.addRemote(context, "Mine", server.url("/list.m3u").toString())
        server.enqueue(MockResponse.Builder().body("  <html>blocked</html>").build())
        assertEquals("The link returned a web page, not a playlist", failure { PlaylistStore.refresh(context, p.id) })
        server.enqueue(MockResponse.Builder().body("#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\nseg1.ts\n").build())
        assertEquals("This is a single HLS stream, not a channel list", failure { PlaylistStore.refresh(context, p.id) })
        server.enqueue(MockResponse.Builder().body("#EXTM3U\n# nothing here\n").build())
        assertEquals("No channels found in playlist", failure { PlaylistStore.refresh(context, p.id) })
        server.enqueue(MockResponse.Builder().code(404).build())
        assertEquals("HTTP 404", failure { PlaylistStore.refresh(context, p.id) })
        // Every failed refresh kept the saved list and left no temp file.
        assertEquals(listOf("A", "B"), PlaylistStore.channels(context, p.id).map { it.title })
        assertTrue(parts().isEmpty())
        server.enqueue(MockResponse.Builder().body("<!doctype html>").build())
        assertTrue(failure { PlaylistStore.addRemote(context, "Page", server.url("/page").toString()) }!!.contains("web page"))
        assertEquals(1, PlaylistStore.playlists.value.count { it.name == "Mine" })
        assertFalse(PlaylistStore.playlists.value.any { it.name == "Page" })
        assertTrue(parts().isEmpty())
    }

    @Test fun aBodyOverTheCapIsRefused() = runBlocking {
        val big = okio.Buffer()
        val line = "#EXTINF:-1,X\nhttps://cdn.example/x.m3u8\n".encodeToByteArray()
        while (big.size <= 40L * 1024 * 1024) big.write(line)
        server.enqueue(MockResponse.Builder().body(big).build())
        assertEquals("Playlist too large", failure { PlaylistStore.addRemote(context, "Big", server.url("/big.m3u").toString()) })
        assertTrue(parts().isEmpty())
    }

    @Test fun analyzeReadsTheFileLineByLine() = runBlocking {
        server.enqueue(MockResponse.Builder().body("#EXTM3U url-tvg=\"https://epg.example/a.xml\"\n#EXTINF:-1 group-title=\"News\",A\nhttps://a/1.m3u8\n#EXTINF:-1 group-title=\"News\",B\nhttps://a/2.m3u8\n").build())
        val a = PlaylistStore.analyze(server.url("/x.m3u").toString())
        assertEquals(2, a.channels); assertFalse(a.isHls); assertEquals(listOf("News" to 2), a.groups)
        server.enqueue(MockResponse.Builder().body("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nlow.m3u8\n").build())
        val h = PlaylistStore.analyze(server.url("/h.m3u8").toString())
        assertTrue(h.isHls); assertEquals(0, h.channels)
    }
}
