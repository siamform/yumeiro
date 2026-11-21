/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.os.SystemClock
import android.util.Log
import android.webkit.WebView
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.web.SavedSites
import dev.asfakul.yumeiro.stream.IncomingMedia
import dev.asfakul.yumeiro.ui.screens.sites.WebsiteTab
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import dev.asfakul.yumeiro.ui.tv.LocalTvMode
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Owner list OL-1, OL-2, OL-3, OL-8 on a real WebView. A local server is the website: a page with a
 * `<video>` (title "Episode One", /media/one.mp4) that can switch to "Episode Two" (/media/two.mp4)
 * the way a site's next-video button does.
 *  - OL-8: the helper banner hides by itself about 3 s after it shows.
 *  - OL-3: after the switch only two.mp4 is listed, with "Now playing: Episode Two".
 *  - OL-2: Collapse hides the rows, Expand shows them, Close leaves the "Streams" pill, the pill reopens.
 *  - OL-1: playing a stream hides the page (old code closed and destroyed it); when the player closes it
 *    comes back as the same WebView with the same JavaScript state (no reload). Minimize -> "Open tab"
 *    card -> Resume does the same.
 * Log tag YuOlTab, result line OL_TAB_RESULT.
 */
@RunWith(AndroidJUnit4::class)
class WebsiteTabDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = inst.targetContext
    private lateinit var server: MockWebServer
    private lateinit var siteUrl: String
    private val paths = Collections.synchronizedList(mutableListOf<String>())
    private fun log(s: String) = Log.i("YuOlTab", s)
    private fun has(tag: String) = compose.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun count(tag: String) = compose.onAllNodesWithTag(tag, true).fetchSemanticsNodes().size
    private fun hasText(t: String) = compose.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(ms: Long, what: () -> Boolean) = runCatching { compose.waitUntil(ms) { what() }; true }.getOrDefault(false)

    private val page = """
<!doctype html><html><head><title>Yu Watch</title><meta property="og:title" content="Yu Watch page"></head>
<body style="background:#000;color:#fff">
<h1>Yu Watch</h1>
<video id="v" title="Episode One" src="/media/one.mp4" muted preload="auto" width="320" height="180"></video>
<script>
  window.yuMarker = 'kept-' + Math.random();
  function yuNext(){ var v = document.getElementById('v'); v.setAttribute('title', 'Episode Two'); v.src = '/media/two.mp4'; v.load(); return 'ok'; }
</script>
</body></html>
""".trimIndent()

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                paths += path
                return when {
                    path == "/watch/1" -> MockResponse.Builder().addHeader("Content-Type", "text/html; charset=utf-8").body(page).build()
                    path.startsWith("/media/") -> MockResponse.Builder().addHeader("Content-Type", "video/mp4").body(Buffer().write(ByteArray(2048))).build()
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
        siteUrl = server.url("/watch/1").toString()
        inst.runOnMainSync { WebsiteTab.close(); SavedSites.init(ctx); SavedSites.add(ctx, siteUrl, "YuSite") }
        IncomingMedia.consume()
    }

    @After fun tearDown() {
        inst.runOnMainSync { WebsiteTab.close(); SavedSites.sites.value.filter { it.name == "YuSite" }.forEach { SavedSites.remove(ctx, it.url) } }
        IncomingMedia.consume()
        runCatching { server.close() }
    }

    private fun js(web: WebView, code: String): String {
        val latch = CountDownLatch(1)
        var out = ""
        inst.runOnMainSync { web.evaluateJavascript(code) { out = it ?: ""; latch.countDown() } }
        latch.await(10, TimeUnit.SECONDS)
        return out.trim('"')
    }

    private fun web(): WebView = assertNotNullAndGet(WebsiteTab.tab.value?.keep?.webViewForTest, "no kept WebView")
    private fun <T> assertNotNullAndGet(v: T?, msg: String): T { assertNotNull(msg, v); return v!! }

    @Test fun websiteTabStaysAliveAndItsPanelFollowsTheVideo() {
        compose.setContent { CompositionLocalProvider(LocalTvMode provides Edition.isTv) { MTPlayerTheme { dev.asfakul.yumeiro.ui.screens.sites.WebSitesScreen(onBack = {}) } } }
        assertTrue("no YuSite row", waitFor(15_000) { hasText("YuSite") })
        compose.onAllNodesWithText("YuSite", substring = false, useUnmergedTree = true).onFirst().performClick()

        // OL-8: the banner shows, then hides by itself after about 3 s.
        assertTrue("the website page did not open", waitFor(10_000) { has("page_finder_hint") })
        val shownAt = SystemClock.uptimeMillis()
        assertTrue("the helper banner never hid by itself", waitFor(8_000) { !has("page_finder_hint") })
        val hintMs = SystemClock.uptimeMillis() - shownAt
        log("hint hid after $hintMs ms")
        assertTrue("the banner hid too early ($hintMs ms)", hintMs >= 2_000)

        // OL-3: the first video's stream, with its title.
        assertTrue("one.mp4 was never detected; requests=$paths", waitFor(20_000) { hasText("/media/one.mp4") })
        assertTrue("no 'Now playing: Episode One'", waitFor(10_000) { hasText("Now playing: Episode One") })
        val w1 = web()
        val marker = js(w1, "window.yuMarker")
        log("marker=$marker")
        assertTrue("page script did not run: $marker", marker.startsWith("kept-"))

        // OL-3: the page switches video -> only the new one is listed.
        SystemClock.sleep(1_800) // older than the 1.5 s grace, as a video that played a while
        assertEquals("ok", js(w1, "yuNext()"))
        assertTrue("two.mp4 was never detected; requests=$paths", waitFor(20_000) { hasText("/media/two.mp4") })
        assertTrue("the old video's stream is still listed after the switch", waitFor(10_000) { !hasText("/media/one.mp4") })
        assertTrue("no 'Now playing: Episode Two'", waitFor(10_000) { hasText("Now playing: Episode Two") })
        assertEquals(1, count("page_finder_stream"))

        // OL-2: collapse / expand / close / pill.
        compose.onNodeWithTag("page_finder_streams_collapse", true).performClick()
        assertTrue("collapse kept the rows", waitFor(5_000) { count("page_finder_stream") == 0 && has("page_finder_streams_expand") })
        compose.onNodeWithTag("page_finder_streams_expand", true).performClick()
        assertTrue("expand did not bring the rows back", waitFor(5_000) { count("page_finder_stream") == 1 })
        compose.onNodeWithTag("page_finder_streams_close", true).performClick()
        assertTrue("close left the panel", waitFor(5_000) { !has("page_finder_streams_panel") && has("page_finder_streams_pill") })
        compose.onNodeWithTag("page_finder_streams_pill", true).performClick()
        assertTrue("the pill did not reopen the panel", waitFor(5_000) { count("page_finder_stream") == 1 })

        // OL-1: play -> the page hides (kept), the player gets the stream with its title.
        compose.onAllNodesWithTag("page_finder_stream", true).onFirst().performClick()
        assertTrue("playing did not hide the page", waitFor(10_000) { !has("page_finder_hint") && !has("page_finder_streams_panel") })
        val played = IncomingMedia.pending.value
        assertNotNull("the stream was not handed to the player", played)
        log("played=${played?.title}")
        assertTrue("the tab was closed instead of kept", WebsiteTab.tab.value != null && WebsiteTab.tab.value?.visible == false)
        assertSame("the WebView was destroyed / replaced", w1, WebsiteTab.tab.value?.keep?.webViewForTest)
        IncomingMedia.consume()

        // The player closes (MTPlayerApp calls this) -> the same page comes back, not reloaded.
        inst.runOnMainSync { WebsiteTab.onPlayerClosed() }
        assertTrue("the page did not come back after the player closed", waitFor(10_000) { has("page_finder_streams_panel") })
        assertSame("a new WebView came back", w1, web())
        assertEquals("the page reloaded (JavaScript state lost)", marker, js(w1, "window.yuMarker"))

        // Minimize -> "Open tab" card -> Resume: same page again.
        compose.onNodeWithTag("page_finder_minimize", true).performClick()
        assertTrue("no Open tab card after Minimize", waitFor(10_000) { has("website_tab_card") })
        compose.onNodeWithTag("website_tab_resume", true).performClick()
        assertTrue("Resume did not bring the page back", waitFor(10_000) { has("page_finder_streams_panel") })
        assertSame(w1, web())
        assertEquals("Resume reloaded the page", marker, js(w1, "window.yuMarker"))
        log("OL_TAB_RESULT hintMs=$hintMs switched=true panel=true kept=true marker=$marker tv=${Edition.isTv}")

        // Close ends the tab.
        compose.onNodeWithTag("page_finder_close", true).performClick()
        assertTrue("Close did not end the tab", waitFor(10_000) { WebsiteTab.tab.value == null })
    }
}
