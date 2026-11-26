/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import android.webkit.WebView
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.web.BrowserFilters
import dev.asfakul.yumeiro.online.web.SavedSites
import dev.asfakul.yumeiro.stream.IncomingMedia
import dev.asfakul.yumeiro.ui.screens.sites.WebSitesScreen
import dev.asfakul.yumeiro.ui.screens.sites.WebsiteTab
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import dev.asfakul.yumeiro.ui.tv.LocalTvMode
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.net.URLEncoder
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * B12 (owner): website redirects, on a real WebView with a local server as two sites (localhost and 127.0.0.1).
 *  a  Stop redirects on: a page's script jumping to the other site is stopped (the other site is never asked), the
 *     bar says "Redirect stopped", and its Open button goes there.
 *  b  Stop redirects off: the same jump goes through.
 *  c  A tapped `intent:` link never leaves Yumeiro: its web fallback loads in the page.
 *  d  A tapped link to a video file plays in Yumeiro's player (handed to IncomingMedia), not the page's own player.
 * Log tag YuGroupN, GN_CASE / GN_RESULT lines.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class GroupNRedirectsDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = inst.targetContext
    private lateinit var server: MockWebServer
    private val paths = Collections.synchronizedList(mutableListOf<String>())
    private fun log(s: String) = Log.i("YuGroupN", s)
    private fun has(tag: String) = compose.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun hasText(t: String) = compose.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(ms: Long, what: () -> Boolean) = runCatching { compose.waitUntil(ms) { what() }; true }.getOrDefault(false)
    private val local get() = server.url("/").toString().trimEnd('/')
    private val other get() = local.replace("localhost", "127.0.0.1")
    private fun asked(p: String) = synchronized(paths) { paths.any { it == p } }

    private fun stopRedirects(on: Boolean) = BrowserFilters.setStopRedirects(ctx, on)

    private fun html(title: String, body: String) = "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>$title</title></head>" +
        "<body style=\"margin:0;background:#111;color:#eee;font:16px sans-serif\">$body</body></html>"

    private val big = "display:block;height:30vh;background:#244;color:#fff;font-size:24px;padding:16px;margin:8px"

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                paths += "${request.url.host}$path"
                fun page(b: String) = MockResponse.Builder().addHeader("Content-Type", "text/html; charset=utf-8").body(b).build()
                return when (path) {
                    "/jump" -> page(html("Yu Jump", "<h1>Jumping soon</h1><script>setTimeout(function(){location.href='$other/landing'},700)</script>"))
                    "/landing" -> page(html("Yu Landing", "<h1>Landing</h1>"))
                    "/links" -> {
                        val fb = URLEncoder.encode("$local/fallback", "UTF-8")
                        page(html("Yu Links", "<a id=\"i\" href=\"intent://x/#Intent;scheme=yumeirotest;package=com.example.none;S.browser_fallback_url=$fb;end\" style=\"$big\">App link</a>" +
                            "<a id=\"m\" href=\"$other/clip.mp4\" style=\"$big\">Video file</a>"))
                    }
                    "/fallback" -> page(html("Yu Fallback", "<h1>Fallback</h1>"))
                    "/clip.mp4" -> MockResponse.Builder().addHeader("Content-Type", "video/mp4").body("not really a video").build()
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
        inst.runOnMainSync {
            WebsiteTab.closeAll(); SavedSites.init(ctx)
            SavedSites.sites.value.forEach { SavedSites.remove(ctx, it.url) }
            BrowserFilters.setFilterAds(ctx, true)
        }
        IncomingMedia.consume()
    }

    @After fun tearDown() {
        inst.runOnMainSync { WebsiteTab.closeAll(); SavedSites.sites.value.forEach { SavedSites.remove(ctx, it.url) }; BrowserFilters.setFilterAds(ctx, true) }
        stopRedirects(true)
        IncomingMedia.consume()
        runCatching { server.close() }
    }

    private fun show() {
        compose.setContent { CompositionLocalProvider(LocalTvMode provides Edition.isTv) { MTPlayerTheme { WebSitesScreen(onBack = {}, only = SavedSites.Kind.WEBSITE) } } }
        compose.waitForIdle()
    }

    private fun js(web: WebView, code: String): String {
        val latch = CountDownLatch(1)
        var out = ""
        inst.runOnMainSync { web.evaluateJavascript(code) { out = it ?: ""; latch.countDown() } }
        latch.await(10, TimeUnit.SECONDS)
        return out.trim('"')
    }

    private fun web(): WebView { val w = WebsiteTab.tab.value?.keep?.webViewForTest; assertNotNull("no kept WebView", w); return w!! }

    private fun open(path: String, name: String, title: String) {
        inst.runOnMainSync { SavedSites.add(ctx, "$local$path", name) }
        show()
        assertTrue("no $name row", waitFor(15_000) { hasText(name) })
        compose.onAllNodesWithText(name, substring = false, useUnmergedTree = true).onFirst().performClick()
        assertTrue("the website page did not open", waitFor(10_000) { has("browser_bar") })
        assertTrue("$name never loaded; requests=$paths", waitFor(20_000) { runCatching { js(web(), "document.title") == title }.getOrDefault(false) })
    }

    /** A real finger tap at the centre of the page element [id]. */
    private fun tapElement(w: WebView, id: String) {
        val r = js(w, "(function(){var r=document.getElementById('$id').getBoundingClientRect();return (r.left+r.width/2)+','+(r.top+Math.min(r.height/2,120));})()")
        val (cx, cy) = r.split(',').map { it.toFloat() }
        val loc = IntArray(2)
        var scale = 1f
        inst.runOnMainSync { w.getLocationOnScreen(loc); scale = w.resources.displayMetrics.density }
        FirstRun.shell("input tap ${(loc[0] + cx * scale).toInt()} ${(loc[1] + cy * scale).toInt()}")
        Thread.sleep(600)
    }

    @Test fun a_scriptJumpIsStoppedAndCanBeOpened() {
        stopRedirects(true)
        open("/jump", "YuJump", "Yu Jump")
        Thread.sleep(3_000)
        val note = waitFor(5_000) { hasText("Redirect stopped") }
        val title = js(web(), "document.title")
        log("GN_CASE B12 stop jump asked=${asked("127.0.0.1/landing")} note=$note title=$title requests=$paths")
        assertFalse("the page's own jump reached the other site with Stop redirects on: $paths", asked("127.0.0.1/landing"))
        assertTrue("the page did not stay where it was ($title)", title == "Yu Jump")
        assertTrue("no Redirect stopped note", note)
        compose.onAllNodesWithText("Open", substring = false, useUnmergedTree = true).onFirst().performClick()
        assertTrue("Open did not go to the held address: $paths", waitFor(15_000) { asked("127.0.0.1/landing") })
        log("GN_RESULT PASS B12 stop")
    }

    @Test fun b_switchOffLetsTheJumpGo() {
        stopRedirects(false)
        open("/jump", "YuJumpOff", "Yu Jump")
        assertTrue("with Stop redirects off the jump did not go: $paths", waitFor(15_000) { asked("127.0.0.1/landing") })
        log("GN_CASE B12 off asked=true")
        log("GN_RESULT PASS B12 off")
    }

    @Test fun c_appLinkFallbackLoadsHere() {
        stopRedirects(true)
        open("/links", "YuLinks", "Yu Links")
        tapElement(web(), "i")
        val ok = waitFor(15_000) { asked("localhost/fallback") }
        val title = runCatching { js(web(), "document.title") }.getOrDefault("")
        log("GN_CASE B12 intent fallback=$ok title=$title bar=${has("browser_bar")} requests=$paths")
        assertTrue("the app link's web fallback did not load in the page: $paths", ok)
        assertTrue("Yumeiro's browser is gone (another app opened?)", has("browser_bar"))
        log("GN_RESULT PASS B12 intent")
    }

    @Test fun d_videoLinkPlaysInYumeiro() {
        stopRedirects(true)
        open("/links", "YuLinks2", "Yu Links")
        tapElement(web(), "m")
        val ok = waitFor(15_000) { IncomingMedia.pending.value?.uri?.endsWith("/clip.mp4") == true }
        log("GN_CASE B12 media handed=${IncomingMedia.pending.value?.uri} requests=$paths")
        assertTrue("the video link did not go to Yumeiro's player (pending=${IncomingMedia.pending.value?.uri}, requests=$paths)", ok)
        log("GN_RESULT PASS B12 media")
    }
}
