/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.stream.PlaylistStore
import dev.asfakul.yumeiro.ui.components.AppMessageHost
import dev.asfakul.yumeiro.ui.screens.stream.PlaylistDetailScreen
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * G7 LT-2 on the real PlaylistDetailScreen, remote playlist served by a local server.
 *
 *  a (LT-2, 4cee17d): the playlist store can't start (its index can't be written: folder read-only,
 *    index moved away). The page used to say "Playlist not found" with no Retry; it must show the
 *    reason with Retry, and Retry (folder writable, index back) must show the channels.
 *  b: the channels can't be downloaded (no cache, server 503): error with Retry; Retry after the
 *    server recovers shows the channels.
 *
 * Log tag YuPlRetry: PLR_RESULT PASS.
 */
@RunWith(AndroidJUnit4::class)
class PlaylistRetryDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var server: MockWebServer
    private val failing = AtomicBoolean(false)
    private val hits = AtomicInteger(0)
    private var id: String? = null

    private fun log(s: String) = Log.i("YuPlRetry", s)
    private fun has(tag: String) = compose.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun hasText(t: String) = compose.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun wait(what: String, ms: Long = 30_000, cond: () -> Boolean) {
        val ok = runCatching { compose.waitUntil(ms) { cond() }; true }.getOrDefault(false)
        if (!ok) FirstRun.shot("plr_fail_" + what.filter { it.isLetterOrDigit() }.take(30))
        assertTrue("$what (after ${ms}ms) · hits=${hits.get()}", ok)
    }

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: mockwebserver3.RecordedRequest): MockResponse {
                hits.incrementAndGet()
                if (failing.get()) return MockResponse.Builder().code(503).body("down").build()
                return MockResponse.Builder().addHeader("Content-Type", "audio/x-mpegurl").body(M3U).build()
            }
        }
        server.start()
    }

    @After fun tearDown() {
        id?.let { p -> runCatching { runBlocking { PlaylistStore.delete(ctx, p) } } }
        runCatching { server.close() }
    }

    private val streams get() = File(ctx.filesDir, "streams")
    private val index get() = File(streams, "playlists.json")
    private val parked get() = File(ctx.cacheDir, "plr_playlists.json")

    /** Folder writable, index back. No unload: Retry itself must load the store again (LT-2). */
    private fun restoreStore() {
        streams.setWritable(true, true)
        if (parked.exists()) { index.delete(); parked.copyTo(index, overwrite = true); parked.delete() }
    }

    @Test fun storeStartFailureShowsRetryAndRetryLoads() {
        val url = server.url("/list.m3u").toString()
        val p = runBlocking { PlaylistStore.init(ctx); PlaylistStore.addRemote(ctx, "YuPlRetry list", url) }
        id = p.id
        try {
            PlaylistStore.unloadForTests()
            assertTrue("index exists", index.exists())
            index.copyTo(parked, overwrite = true); assertTrue("index moved away", index.delete())
            File(streams, "playlists.json.tmp").delete(); File(streams, "playlists.json.bad").delete()
            assertTrue("folder made read-only", streams.setWritable(false, false))
            val probe = runCatching { File(streams, "plr_probe").createNewFile() }.getOrDefault(false)
            org.junit.Assume.assumeFalse("folder is still writable (root?)", probe)
            val startErr = runCatching { runBlocking { PlaylistStore.init(ctx) } }.exceptionOrNull()
            log("PLR a store start fails=${startErr != null} (${startErr?.javaClass?.simpleName})")
            assertTrue("store start fails with the folder read-only", startErr != null)

            compose.setContent { Box(Modifier.fillMaxSize()) { PlaylistDetailScreen(slug = p.id, onBack = {}, onOpenMarket = {}); AppMessageHost() } }
            wait("store error page with Retry") { has("playlist_error") || has("playlist_not_found") }
            FirstRun.shot("plr_a_error")
            assertTrue("LT-2: a failed store start shows 'not found' instead of the reason", !has("playlist_not_found"))
            assertTrue("Retry on screen", hasText("Retry"))
            log("PLR a shown=" + compose.onAllNodes(androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag("playlist_error")), true)
                .fetchSemanticsNodes().mapNotNull { n -> n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }?.joinToString { t -> t.text } }.joinToString(" | "))
            log("PLR a startErr=${startErr?.message}")
            assertTrue("LT-2: the store error shows a raw file path", !hasText("/data/") && !hasText("EACCES"))
            assertTrue("LT-2: the store error blames the connection", !hasText("Check your connection"))
            assertTrue("LT-2: the store error names storage", hasText("couldn't use its storage"))
            log("PLR a error page shown")

            restoreStore()
            compose.onAllNodesWithText("Retry", useUnmergedTree = true)[0].performClick()
            wait("channels after Retry") { hasText("YuPlRetry One") && !has("playlist_error") }
            FirstRun.shot("plr_a_ready")
            log("PLR a PASS")
        } finally { restoreStore(); PlaylistStore.unloadForTests() }
        log("PLR_RESULT PASS a")
    }

    @Test fun failedLoadShowsRetryAndRetryLoads() {
        val url = server.url("/list.m3u").toString()
        val p = runBlocking { PlaylistStore.init(ctx); PlaylistStore.addRemote(ctx, "YuPlRetry list", url) }
        id = p.id
        val cache = File(File(ctx.filesDir, "streams"), "ch_${p.id.filter { it.isLetterOrDigit() || it == '_' || it == '-' }}.m3u")
        assertTrue("cached channel file exists after add: $cache", cache.exists())
        assertTrue("cache removed", cache.delete())
        failing.set(true)
        log("PLR added id=${p.id} channels=${p.channelCount}; cache removed, server failing")

        compose.setContent { Box(Modifier.fillMaxSize()) { PlaylistDetailScreen(slug = p.id, onBack = {}, onOpenMarket = {}); AppMessageHost() } }
        wait("error page with Retry") { has("playlist_error") && hasText("Retry") }
        assertTrue("no channel shown while failing", !hasText("YuPlRetry One"))
        log("PLR error page shown hits=${hits.get()}")
        FirstRun.shot("plr_error")

        failing.set(false)
        compose.onAllNodesWithText("Retry", useUnmergedTree = true).fetchSemanticsNodes().size.let { assertTrue("Retry on screen", it > 0) }
        compose.onAllNodesWithText("Retry", useUnmergedTree = true)[0].performClick()
        wait("channels after Retry") { hasText("YuPlRetry One") && !has("playlist_error") }
        assertTrue("second channel shown", hasText("YuPlRetry Two"))
        FirstRun.shot("plr_ready")
        log("PLR_RESULT PASS b hits=${hits.get()}")
    }

    private companion object {
        val M3U = """
            #EXTM3U
            #EXTINF:-1 group-title="News",YuPlRetry One
            http://127.0.0.1:9/one.m3u8
            #EXTINF:-1 group-title="News",YuPlRetry Two
            http://127.0.0.1:9/two.m3u8
        """.trimIndent() + "\n"
    }
}
