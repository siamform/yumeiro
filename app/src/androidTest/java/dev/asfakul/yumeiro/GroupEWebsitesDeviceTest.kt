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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * GROUP E on a real WebView, with a local server as the website (two "sites": localhost and 127.0.0.1).
 *  a  E1: the header is search, +, gear and ?; the gear's sheet toggles the saved switches by real taps.
 *  b  Owner item 10: Duplicate under another address saves a copy (same address is refused with the reason).
 *  c  E2: a real tap on a link to another site opens a second tab; the tab button shows 2, the switcher lists both,
 *     switching shows the first page again (same WebView, no reload); Close tab leaves one.
 *  d  E2: the page menu: Reload really reloads; the ad filter item turns this page's filter off and on.
 *  e  E3: a page with an anti-ad-blocker wall, an invisible full-screen click-catcher (an ad link) and a scroll lock:
 *     the wall and the catcher are gone, the page scrolls again, the player box stays; a real tap no longer goes
 *     to the ad.
 * Log tag YuGroupE, result lines GROUPE_<case>.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class GroupEWebsitesDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = inst.targetContext
    private lateinit var server: MockWebServer
    private val paths = Collections.synchronizedList(mutableListOf<String>())
    private fun log(s: String) = Log.i("YuGroupE", s)
    private fun has(tag: String) = compose.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun count(tag: String) = compose.onAllNodesWithTag(tag, true).fetchSemanticsNodes().size
    private fun hasText(t: String) = compose.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(ms: Long, what: () -> Boolean) = runCatching { compose.waitUntil(ms) { what() }; true }.getOrDefault(false)
    private fun click(tag: String) { compose.onNodeWithTag(tag, true).performClick(); compose.waitForIdle() }
    private val tv get() = Edition.isTv
    private val local get() = server.url("/").toString().trimEnd('/')
    private val other get() = local.replace("localhost", "127.0.0.1")

    private fun html(title: String, body: String) = "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>$title</title></head>" +
        "<body style=\"margin:0;background:#111;color:#eee;font:16px sans-serif\">$body</body></html>"

    private val linkPage get() = html("Yu A", """
<a id="l" href="$other/b" target="_blank" style="display:block;height:60vh;background:#244;color:#fff;font-size:28px;padding:20px">Open site B</a>
<script>window.yuMarker='a-'+Math.random();</script>""")

    private val bPage = html("Yu B", "<h1>Site B</h1>")

    private val annoyPage get() = html("Yu Annoy", """
<style>html,body{overflow:hidden;height:100%}</style>
<div id="player" style="position:fixed;left:0;top:0;width:100%;height:100%;z-index:500;background:transparent"><video id="v" muted width="320" height="180"></video></div>
<a id="catcher" href="$other/ad" style="position:fixed;left:0;top:0;width:100%;height:100%;z-index:99999;background:transparent"></a>
<div id="wall" style="position:fixed;left:0;top:0;width:100%;height:100%;z-index:100000;background:#000c;color:#fff;padding:40px">AdBlock Detected! Please disable your ad blocker to continue.</div>
<div style="height:3000px">long content</div>""")

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                paths += "${request.url.host}$path"
                fun page(b: String) = MockResponse.Builder().addHeader("Content-Type", "text/html; charset=utf-8").body(b).build()
                return when (path) {
                    "/a" -> page(linkPage)
                    "/b" -> page(bPage)
                    "/annoy" -> page(annoyPage)
                    "/ad" -> page(html("Ad", "<h1>AD</h1>"))
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
        IncomingMedia.consume()
        runCatching { server.close() }
    }

    private fun show() {
        compose.setContent { CompositionLocalProvider(LocalTvMode provides tv) { MTPlayerTheme { WebSitesScreen(onBack = {}, only = SavedSites.Kind.WEBSITE) } } }
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

    /** A real finger tap at the centre of the page element [id] (screen pixels from its CSS box). */
    private fun tapElement(w: WebView, id: String) {
        val r = js(w, "(function(){var r=document.getElementById('$id').getBoundingClientRect();return (r.left+r.width/2)+','+(r.top+Math.min(r.height/2,120));})()")
        val (cx, cy) = r.split(',').map { it.toFloat() }
        val loc = IntArray(2)
        var scale = 1f
        inst.runOnMainSync { w.getLocationOnScreen(loc); scale = w.resources.displayMetrics.density }
        val x = loc[0] + cx * scale; val y = loc[1] + cy * scale
        log("tap $id at $x,$y (css $cx,$cy)")
        FirstRun.shell("input tap ${x.toInt()} ${y.toInt()}")
        Thread.sleep(600)
    }

    private fun openSaved(name: String) {
        assertTrue("no $name row", waitFor(15_000) { hasText(name) })
        compose.onAllNodesWithText(name, substring = false, useUnmergedTree = true).onFirst().performClick()
        assertTrue("the website page did not open", waitFor(10_000) { has("browser_bar") })
    }

    @Test fun a_headerIconsAndSettingsSheet() {
        inst.runOnMainSync { SavedSites.add(ctx, "$local/a", "YuSiteA") }
        show()
        for (t in listOf("site_search_toggle", "site_add_open", "site_settings", "site_help")) assertTrue("header has $t", has(t))
        assertFalse("share icon left the header", has("site_export"))
        assertFalse("switches left the list", has("filter_ads_switch_row"))
        click("site_settings")
        assertTrue("settings sheet did not open", waitFor(5_000) { has("site_settings_sheet") })
        val before = BrowserFilters.filterAds.value
        click("filter_ads_switch_row")
        assertTrue("the ad filter switch did not change", waitFor(3_000) { BrowserFilters.filterAds.value == !before })
        click("filter_ads_switch_row")
        assertTrue(waitFor(3_000) { BrowserFilters.filterAds.value == before })
        assertTrue(has("site_manage_ads")); assertTrue(has("site_import")); assertTrue(has("site_export"))
        log("GROUPE_A PASS tv=$tv")
    }

    @Test fun b_duplicateUnderAnotherAddress() {
        inst.runOnMainSync { SavedSites.add(ctx, "$local/a", "YuSiteA") }
        show()
        click("site_menu"); click("site_duplicate")
        assertTrue(waitFor(5_000) { has("site_duplicate_dialog") })
        click("site_duplicate_save")
        assertTrue("same address was not refused", waitFor(5_000) { has("site_duplicate_error") })
        assertEquals(1, SavedSites.sites.value.size)
        compose.onNodeWithTag("site_duplicate_address", true).performTextClearance()
        compose.onNodeWithTag("site_duplicate_address", true).performTextInput("$other/a")
        compose.waitForIdle()
        click("site_duplicate_save")
        assertTrue("the copy was not saved", waitFor(5_000) { SavedSites.sites.value.size == 2 })
        val copy = SavedSites.sites.value.first { it.url.contains("127.0.0.1") }
        assertEquals("YuSiteA copy", copy.name)
        assertTrue("the original was replaced", SavedSites.sites.value.any { it.name == "YuSiteA" && it.url.contains("localhost") })
        log("GROUPE_B PASS tv=$tv")
    }

    @Test fun c_linkToAnotherSiteOpensASecondTab() {
        inst.runOnMainSync { SavedSites.add(ctx, "$local/a", "YuSiteA") }
        show()
        openSaved("YuSiteA")
        assertTrue("site A never loaded; requests=$paths", waitFor(20_000) { runCatching { js(web(), "document.title") == "Yu A" }.getOrDefault(false) })
        val wa = web()
        val marker = js(wa, "window.yuMarker")
        tapElement(wa, "l")
        assertTrue("no second tab after tapping a link to another site; requests=$paths tabs=${WebsiteTab.tabs.value.map { it.keep.pageUrl }}", waitFor(15_000) { WebsiteTab.tabs.value.size == 2 })
        assertTrue("the new tab is not site B", waitFor(15_000) { WebsiteTab.tab.value?.keep?.pageUrl?.contains("127.0.0.1") == true })
        assertTrue("site B never loaded; requests=$paths", waitFor(20_000) { paths.any { it.startsWith("127.0.0.1/b") } })
        assertTrue("tab button gone", waitFor(10_000) { has("browser_tabs") })
        click("browser_tabs")
        assertTrue("tab switcher did not open", waitFor(5_000) { has("browser_tabs_sheet") })
        assertEquals(2, count("browser_tab_row"))
        compose.onAllNodesWithTag("browser_tab_row", true)[0].performClick(); compose.waitForIdle()
        assertTrue("switching did not bring site A back", waitFor(10_000) { WebsiteTab.tab.value?.keep?.pageUrl?.contains("localhost") == true && has("browser_bar") })
        assertEquals("site A reloaded when switched back", marker, js(web(), "window.yuMarker"))
        click("browser_menu"); click("browser_close_tab")
        assertTrue("Close tab did not leave one tab", waitFor(10_000) { WebsiteTab.tabs.value.size == 1 })
        log("GROUPE_C PASS tv=$tv")
    }

    @Test fun d_pageMenuReloadAndFilter() {
        inst.runOnMainSync { SavedSites.add(ctx, "$local/a", "YuSiteA") }
        show()
        openSaved("YuSiteA")
        assertTrue(waitFor(20_000) { paths.count { it.endsWith("/a") } >= 1 })
        val loads = paths.count { it.endsWith("/a") }
        click("browser_menu")
        assertTrue("menu did not open", waitFor(5_000) { has("browser_reload") })
        for (t in listOf("browser_menu_filter", "browser_block", "browser_manage_ads", "browser_open_outside", "browser_close_tab")) assertTrue("menu has $t", has(t))
        click("browser_reload")
        assertTrue("Reload did not reload; requests=$paths", waitFor(15_000) { paths.count { it.endsWith("/a") } > loads })
        click("browser_menu"); click("browser_menu_filter")
        assertTrue("filter did not turn off for this page", waitFor(5_000) { BrowserFilters.pageChoice(web()) == false })
        click("browser_menu"); click("browser_menu_filter")
        assertTrue(waitFor(5_000) { BrowserFilters.pageChoice(web()) == true })
        log("GROUPE_D PASS tv=$tv")
    }

    @Test fun e_annoyancesAreCleared() {
        inst.runOnMainSync { SavedSites.add(ctx, "$local/annoy", "YuAnnoy") }
        show()
        openSaved("YuAnnoy")
        assertTrue("page never loaded; requests=$paths", waitFor(20_000) { runCatching { js(web(), "document.title") == "Yu Annoy" }.getOrDefault(false) })
        val w = web()
        val ok = waitFor(15_000) { js(w, "JSON.stringify(window.__yuCleanStats||null)").let { it.contains("\\\"walls\\\":1") || it.contains("\"walls\":1") } }
        val stats = js(w, "JSON.stringify(window.__yuCleanStats||null)")
        log("stats=$stats")
        assertTrue("no clean-up ran or the wall stayed: $stats", ok)
        assertEquals("wall still there", "false", js(w, "!!document.getElementById('wall')"))
        assertEquals("click-catcher still there", "false", js(w, "!!document.getElementById('catcher')"))
        assertEquals("the player box was removed", "true", js(w, "!!document.getElementById('player')"))
        assertEquals("page still scroll-locked", "auto", js(w, "getComputedStyle(document.body).overflowY"))
        // A real tap where the catcher was: the page stays (the ad page is never asked for).
        js(w, "document.getElementById('player').style.pointerEvents='none'")
        val x = IntArray(2); inst.runOnMainSync { w.getLocationOnScreen(x) }
        FirstRun.shell("input tap ${x[0] + 200} ${x[1] + 600}")
        Thread.sleep(2_000)
        assertFalse("the tap went to the ad; requests=$paths", paths.any { it.endsWith("/ad") })
        log("GROUPE_E PASS stats=$stats tv=$tv")
    }
}
