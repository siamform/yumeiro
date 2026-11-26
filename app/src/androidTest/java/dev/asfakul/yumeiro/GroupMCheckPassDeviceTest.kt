/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.os.SystemClock
import android.util.Log
import android.webkit.CookieManager
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSearchResponseList
import dev.asfakul.yumeiro.online.OnlineRepository
import dev.asfakul.yumeiro.online.OnlineRuntime
import dev.asfakul.yumeiro.online.web.BrowserCheck
import dev.asfakul.yumeiro.online.web.VerificationCenter
import dev.asfakul.yumeiro.sources.SourceHub
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/**
 * B4 (Group M, owner report: "extensions don't load after a successful Cloudflare captcha").
 *
 * A local site behaves like a Cloudflare-protected one: without its pass cookie every page is the
 * "Just a moment…" check page (403, `cf-mitigated: challenge`, a tap-the-box widget the silent check
 * cannot pass). The check page's button sets the pass cookie, as Turnstile does once a person taps it.
 * The extension reads titles from the page; on the check page it finds none (no error), exactly what a
 * real extension does. Nothing in the app is replaced: the requests go through the extension client,
 * the check page is Yumeiro's own check sheet, and the button is tapped on screen with UiAutomator.
 *
 *  - [searchFindsTitlesAfterThePass]: a search made while the check was up comes back empty; after the
 *    user passes the check, the same search must find the titles. Before: the empty answer (the check
 *    page) was saved (memory 10 min, phone 1 h) and served again.
 *  - [yumixReloadsTheExtensionAfterThePass]: Yumix shows the extension as failed; the user passes the
 *    check (Security checks › Check now / the Verify card open the same sheet); the extension must load
 *    without Retry. Before: it stayed "didn't load".
 *  - [openSiteOpensTheCheckPage]: the extension's page says "asks for a browser check first. Tap Open
 *    site"; Open site must open the check page, and after the pass the titles show. Before: it ran only
 *    the silent check (which cannot pass this site), so nothing opened.
 *
 * Log tag YuGroupM: GM_CASE / GM_RESULT. A real Cloudflare site with a real captcha stays on MANUAL_TESTS.md.
 */
@RunWith(AndroidJUnit4::class)
class GroupMCheckPassDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = inst.targetContext
    private lateinit var server: MockWebServer
    private lateinit var base: String
    private lateinit var host: String
    private val checkPages = AtomicInteger()
    private val contentPages = AtomicInteger()
    private fun log(s: String) = Log.i(TAG, s)

    private class CheckSite(base: String, override var name: String) : MainAPI() {
        override var mainUrl = base
        override val hasMainPage = true
        override val supportedTypes = setOf(TvType.Movie)
        override val mainPage = mainPageOf("$base/latest" to "Latest")
        override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse =
            newHomePageResponse(request.name, titles(app.get(request.data).text))
        override suspend fun search(query: String, page: Int): SearchResponseList =
            newSearchResponseList(titles(app.get("$mainUrl/search?q=$query").text), false)
        override suspend fun load(url: String): LoadResponse = newMovieLoadResponse("Yu Pass Film", url, TvType.Movie, url)
        private suspend fun titles(html: String): List<SearchResponse> {
            val out = ArrayList<SearchResponse>()
            for (m in TITLE.findAll(html)) out += newMovieSearchResponse(m.groupValues[2], mainUrl + m.groupValues[1], TvType.Movie)
            return out
        }
    }

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val passed = request.headers["Cookie"].orEmpty().contains("cf_clearance=$PASS")
                if (!passed) {
                    checkPages.incrementAndGet()
                    return MockResponse.Builder().code(403).addHeader("cf-mitigated", "challenge").addHeader("Server", "cloudflare")
                        .addHeader("cf-ray", "8f00000000000000-DAC").addHeader("Content-Type", "text/html; charset=UTF-8")
                        .body(CHECK_PAGE).build()
                }
                contentPages.incrementAndGet()
                return MockResponse.Builder().code(200).addHeader("Content-Type", "text/html; charset=UTF-8").body(CONTENT_PAGE).build()
            }
        }
        server.start()
        host = server.hostName
        base = "http://$host:${server.port}"
        OnlineRuntime.init(ctx)
        BrowserCheck.init(ctx)
        VerificationCenter.init(ctx)
        VerificationCenter.forget(host)
        BrowserCheck.forget(host)
        clearCookies()
        OnlineRepository.clearCaches()
    }

    @After fun tearDown() {
        runCatching { BrowserCheck.cancel() }
        SourceHub.remove(BRIDGE)
        runCatching { VerificationCenter.forget(host) }
        clearCookies()
        runCatching { server.close() }
    }

    private fun clearCookies() {
        val done = java.util.concurrent.CountDownLatch(1)
        inst.runOnMainSync { CookieManager.getInstance().removeAllCookies { done.countDown() } }
        done.await(5, java.util.concurrent.TimeUnit.SECONDS)
    }

    private fun cookie(): String? = runCatching { CookieManager.getInstance().getCookie("$base/") }.getOrNull()

    private fun search(api: MainAPI, q: String) = runBlocking { OnlineRepository.search(q, listOf(api)).toList() }.lastOrNull()

    /** Waits for Yumeiro's check sheet, taps the page's own button on screen, waits for the cookie, taps Done. */
    private fun passOnScreen(label: String) {
        val sheet = runCatching { rule.waitUntil(30_000) { rule.onAllNodesWithTag("browser_check_done", true).fetchSemanticsNodes().isNotEmpty() }; true }.getOrDefault(false)
        FirstRun.shot("groupm_${label}_check_sheet")
        assertTrue("$label: Yumeiro's check page never opened · ${FirstRun.onScreen(rule)}", sheet)
        val device = UiDevice.getInstance(inst)
        val end = SystemClock.uptimeMillis() + 30_000
        var tapped = false
        while (SystemClock.uptimeMillis() < end && cookie()?.contains("cf_clearance=$PASS") != true) {
            val button = device.findObject(By.text(BUTTON)) ?: device.findObject(By.desc(BUTTON))
            if (button != null && !tapped) { button.click(); tapped = true; log("GM_CASE $label tapped the page's button") }
            SystemClock.sleep(500)
            if (tapped && SystemClock.uptimeMillis() > end - 20_000) tapped = false // tap again if the first one landed while loading
        }
        val pass = cookie()
        log("GM_CASE $label cookie after tap: ${pass?.replace(Regex("cf_clearance=[^;]+"), "cf_clearance=<set>")}")
        assertTrue("$label: the check page's button never gave the pass (WebView not tappable?)", pass?.contains("cf_clearance=$PASS") == true)
        FirstRun.shot("groupm_${label}_passed")
        rule.onAllNodesWithTag("browser_check_done", true)[0].performClick()
        rule.waitForIdle()
    }

    @Test fun searchFindsTitlesAfterThePass() {
        FirstRun.toTabs(rule)
        val api = CheckSite(base, "YuGroupM Search")
        val first = search(api, "yu")
        log("GM_CASE search before pass items=${first?.items?.size} error=${first?.error} checkPages=${checkPages.get()}")
        assertTrue("precondition: the check page must hide the titles (got ${first?.items?.size})", first?.items.isNullOrEmpty())
        rule.runOnUiThread { BrowserCheck.openSite("$base/") }
        passOnScreen("search")
        SystemClock.sleep(1_500)
        val after = search(api, "yu")
        log("GM_CASE search after pass items=${after?.items?.map { it.name }} error=${after?.error} content=${contentPages.get()}")
        assertTrue("the same search found nothing after the check was passed (old empty answer served): ${after?.items?.size}",
            after?.items?.any { it.name == "Yu Pass Film" } == true)
        log("GM_RESULT search PASS")
    }

    private fun summary() = dev.asfakul.yumeiro.ui.screens.online.yumixDebugSummary()

    private fun state(name: String): String = Regex(Regex.escape(name) + "=([A-Za-z]+(\\([^)]*\\))?)").find(summary())?.groupValues?.get(1) ?: "absent"

    private fun openYumixWith(api: MainAPI) {
        dev.asfakul.yumeiro.online.YumixDefaults.resetForTest()
        SourceHub.publishVideo(BRIDGE, SourceHub.Ecosystem.CLOUDSTREAM, listOf(api))
        FirstRun.toTabs(rule)
        FirstRun.tapVisible(rule, "nav_tab_home"); rule.waitForIdle()
        FirstRun.tapVisible(rule, "quick_card_hues"); rule.waitForIdle()
        val failed = runCatching { rule.waitUntil(120_000) { state(api.name).startsWith("Failed") }; true }.getOrDefault(false)
        log("GM_CASE ${api.name} before pass: ${state(api.name)} · ${summary().take(400)}")
        assertTrue("precondition: ${api.name} should fail on the check page, was ${state(api.name)}", failed)
    }

    @Test fun yumixReloadsTheExtensionAfterThePass() {
        val api = CheckSite(base, "YuGroupM Feed")
        openYumixWith(api)
        // Security checks › Check now and the Verify card open this same sheet.
        rule.runOnUiThread { VerificationCenter.checkNow(host) }
        passOnScreen("feed")
        val loaded = runCatching { rule.waitUntil(45_000) { state(api.name).startsWith("Ready") && !state(api.name).contains(" 0 items") }; true }.getOrDefault(false)
        FirstRun.shot("groupm_feed_after_pass")
        log("GM_CASE feed after pass: ${state(api.name)} content=${contentPages.get()}")
        assertTrue("${api.name} did not load after its check was passed (stayed ${state(api.name)}) · ${FirstRun.onScreen(rule)}", loaded)
        log("GM_RESULT feed PASS")
    }

    @Test fun openSiteOpensTheCheckPage() {
        val api = CheckSite(base, "YuGroupM Page")
        openYumixWith(api)
        FirstRun.tapVisible(rule, "online_filter")
        val row = runCatching { rule.waitUntil(10_000) { rule.onAllNodesWithText(api.name, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }; true }.getOrDefault(false)
        assertTrue("the filter sheet does not list ${api.name} · ${FirstRun.onScreen(rule)}", row)
        rule.onAllNodesWithText(api.name, useUnmergedTree = true)[0].performClick()
        rule.waitForIdle()
        val fix = runCatching { rule.waitUntil(60_000) { rule.onAllNodesWithTag("online_provider_fix", true).fetchSemanticsNodes().isNotEmpty() }; true }.getOrDefault(false)
        FirstRun.shot("groupm_page_failed")
        assertTrue("the extension page shows no Open site button · ${FirstRun.onScreen(rule)}", fix)
        rule.onAllNodesWithTag("online_provider_fix", true)[0].performClick()
        passOnScreen("page")
        val loaded = runCatching { rule.waitUntil(45_000) { state(api.name).startsWith("Ready") && !state(api.name).contains(" 0 items") }; true }.getOrDefault(false)
        FirstRun.shot("groupm_page_after_pass")
        log("GM_CASE page after pass: ${state(api.name)}")
        assertTrue("${api.name} did not load after Open site and the pass (stayed ${state(api.name)})", loaded)
        log("GM_RESULT page PASS")
    }

    private companion object {
        const val TAG = "YuGroupM"
        const val BRIDGE = "yu-groupm-check"
        const val PASS = "yu-groupm-pass"
        const val BUTTON = "Verify you are human"
        val TITLE = Regex("""class="yu-title" href="([^"]+)">([^<]+)<""")
        val CHECK_PAGE = """<!DOCTYPE html><html><head><title>Just a moment...</title><meta name="viewport" content="width=device-width"></head>
            |<body><h1>Checking if the site connection is secure</h1><div class="cf-turnstile" id="cf-chl-widget">
            |<button id="yu-pass" style="font-size:28px;padding:24px" onclick="document.cookie='cf_clearance=$PASS; path=/; max-age=3600';location.reload()">$BUTTON</button>
            |</div><script>window._cf_chl_opt={cType:'managed'};</script></body></html>""".trimMargin()
        val CONTENT_PAGE = """<!DOCTYPE html><html><head><title>Yu Site</title></head><body>
            |<a class="yu-title" href="/film/1">Yu Pass Film</a><a class="yu-title" href="/film/2">Yu Second Film</a>
            |</body></html>""".trimMargin()
    }
}
