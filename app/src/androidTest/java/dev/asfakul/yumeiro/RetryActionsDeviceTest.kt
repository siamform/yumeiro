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
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.sources.js.JsSources
import dev.asfakul.yumeiro.sources.stremio.StremioAddons
import dev.asfakul.yumeiro.ui.components.AppMessageHost
import dev.asfakul.yumeiro.ui.components.AppMessages
import dev.asfakul.yumeiro.ui.screens.sources.SourcesHubScreen
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/**
 * G10 ER-12: a failed network add names the reason and offers "Retry", and Retry repeats the same
 * call (the dialog with the typed link has closed by then). Runs the real Sources screen against a
 * local server whose first answer fails and whose later answers are valid.
 *
 *  a: JavaScript tab → Add → index.json link → error with Retry → Retry → "Repository added · 1 sources".
 *  b: Stremio tab → Add → manifest link → error with Retry → Retry → "<name> added".
 *  c: Mihon tab → Add → index.min.json link → error with Retry → Retry → "Repository added · 1 extensions".
 *  d: Home servers → WebDAV → Connect → reason inside the form, button says Retry (ER-19) → Retry → "WebDAV connected".
 *
 * Retry is tapped with a real screen tap (`input tap` at its on-screen centre), not a Compose
 * injected click, so a Retry drawn under a dialog window would fail here. Inbox pull-to-refresh
 * uses the same AppMessages.error(…, "Retry") call but its feed address is fixed (no local server).
 *
 * Log tag YuRetry: RTY_CASE / RTY_RESULT PASS.
 */
@RunWith(AndroidJUnit4::class)
class RetryActionsDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var server: MockWebServer
    private val hits = AtomicInteger(0)
    /** True until the error is on screen; the server then answers so Retry can succeed. */
    private val failing = java.util.concurrent.atomic.AtomicBoolean(true)
    private var addedMihon: String? = null
    private var addedServer: String? = null
    private var addedJs: String? = null
    private var addedStremio: String? = null

    private fun log(s: String) = Log.i("YuRetry", s)
    private fun has(tag: String) = compose.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun hasText(t: String) = compose.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    /** Text of the shown message (merged tree: the pill is clickable, so its texts merge into it). */
    private fun messageText(): String = runCatching {
        compose.onAllNodesWithTag("app_message", false).fetchSemanticsNodes().firstOrNull()
            ?.config?.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }?.joinToString(" ") { t -> t.text }
    }.getOrNull().orEmpty()
    private fun clearMessage() { AppMessages.current.value?.id?.let(AppMessages::dismiss) }
    private fun wait(what: String, ms: Long = 20_000, cond: () -> Boolean) {
        val ok = runCatching { compose.waitUntil(ms) { cond() }; true }.getOrDefault(false)
        if (!ok) FirstRun.shot("rty_fail_" + what.filter { it.isLetterOrDigit() }.take(30))
        assertTrue("$what (after ${ms}ms) · hits=${hits.get()} · message='${messageText()}'", ok)
    }

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: mockwebserver3.RecordedRequest): MockResponse {
                if (failing.get()) return MockResponse.Builder().code(404).body("gone").build()
                hits.incrementAndGet()
                val json = { body: String -> MockResponse.Builder().addHeader("Content-Type", "application/json").body(body).build() }
                return when (request.url.encodedPath) {
                    "/repo/index.json" -> json(JS_INDEX)
                    "/addon/manifest.json" -> json(MANIFEST)
                    "/mihon/index.min.json" -> json(MIHON_INDEX)
                    "/dav/" -> MockResponse.Builder().code(207).addHeader("Content-Type", "application/xml; charset=utf-8").body(DAV_LIST).build()
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
        clearMessage()
    }

    @After fun tearDown() {
        addedJs?.let { u -> runCatching { JsSources.removeRepo(ctx, u) } }
        addedStremio?.let { u -> runCatching { StremioAddons.remove(ctx, u) } }
        addedMihon?.let { u -> runCatching { kotlinx.coroutines.runBlocking { dev.asfakul.yumeiro.sources.tachiyomi.TachiyomiExtensions.removeRepo(ctx, u) } } }
        addedServer?.let { id -> runCatching { dev.asfakul.yumeiro.sources.servers.HomeServers.remove(ctx, id) } }
        runCatching { clearMessage() }
        runCatching { server.close() }
    }

    private fun show(tab: Int) {
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                SourcesHubScreen(onBack = {}, initialTab = tab)
                AppMessageHost()
            }
        }
        compose.waitForIdle()
    }

    /**
     * SRC-HUB-1 / GROUP C / GROUP D: repositories and add-ons are added with the page header's "+" (add sheet),
     * from any page of that ecosystem. Types [url] and taps Add. [part] is kept for the log only.
     */
    private fun addThroughField(list: String, part: String, field: String, url: String) {
        wait("$list list") { has(list) }
        compose.onNodeWithTag("${field}_plus", true).performClick()
        compose.waitForIdle()
        wait("${field}_field") { has("${field}_field") }
        compose.onNodeWithTag("${field}_field", true).performTextInput(url)
        compose.waitForIdle()
        compose.onNodeWithTag("${field}_add", true).performClick()
    }

    /** A real tap on the screen at the node's centre: goes through window order like a finger. */
    private fun screenTap(tag: String) {
        // FINAL-34 (run 44663118888, tv-api24): after typing the server URL, the landscape TV keyboard opens in
        // full-screen mode over the form, so the screen tap on Retry landed on the keyboard (hits=0). Close
        // the keyboard first, as a TV user does with Back (no-op when no keyboard is shown).
        runCatching { androidx.test.espresso.Espresso.closeSoftKeyboard() }
        compose.waitForIdle()
        val n = compose.onAllNodesWithTag(tag, true).fetchSemanticsNodes().first()
        val x = n.positionOnScreen.x + n.size.width / 2f
        val y = n.positionOnScreen.y + n.size.height / 2f
        log("RTY_TAP $tag at ${x.toInt()},${y.toInt()}")
        FirstRun.shell("input tap ${x.toInt()} ${y.toInt()}")
    }

    /**
     * GROUP D: a failed add inside the add sheet shows its reason and Retry in the sheet (the bottom message sat
     * under the sheet's window, so a real tap could not reach its Retry: runs 45678492039 a–c, hits=0).
     */
    private fun sheetFailThenRetry(case: String, field: String, success: String) {
        wait("$case: reason with Retry inside the sheet") { has("${field}_error") && has("${field}_retry") }
        val err = compose.onAllNodesWithTag("${field}_error", false).fetchSemanticsNodes().first()
            .config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }.joinToString(" ") { t -> t.text }
        log("RTY_CASE $case error='$err'")
        FirstRun.shot("rty_${case}_error")
        assertTrue("$case: the reason is named ('$err')", err.contains("404") || err.contains("found", true) || err.contains("answered", true))
        failing.set(false)
        screenTap("${field}_retry")
        wait("$case: Retry repeats the call") { hits.get() > 0 }
        wait("$case: success after Retry") { hasText(success) }
        wait("$case: sheet closes") { !has("${field}_field") }
        log("RTY_CASE $case retried hits=${hits.get()} success='$success'")
        log("RTY_RESULT PASS $case")
    }

    private fun failThenRetry(case: String, success: String) {
        wait("$case: error message with Retry") { has("app_message") && has("app_message_action") }
        val err = messageText()
        log("RTY_CASE $case error='$err'")
        FirstRun.shot("rty_${case}_error")
        assertTrue("$case: the message names the reason, not a bare 'try again' ('$err')", err.contains("404") || err.contains("found", true) || err.contains("answered", true))
        failing.set(false)
        screenTap("app_message_action")
        wait("$case: Retry repeats the call") { hits.get() > 0 }
        wait("$case: success after Retry") { hasText(success) }
        log("RTY_CASE $case retried hits=${hits.get()} success='$success'")
        log("RTY_RESULT PASS $case")
    }

    @Test fun a_javascriptRepoRetry() {
        val url = server.url("/repo/index.json").toString()
        show(3)
        addThroughField("hub_js", "repos", "hub_js_add", url)
        sheetFailThenRetry("a", "hub_js_add", "Repository added · 1 sources")
        wait("a: repo saved") { JsSources.state.value.repos.any { it == url } }
        addedJs = url
    }

    @Test fun b_stremioAddonRetry() {
        val url = server.url("/addon/manifest.json").toString()
        show(2)
        addThroughField("hub_stremio", "browse", "hub_stremio_add", url)
        sheetFailThenRetry("b", "hub_stremio_add", "ER12 Test added")
        wait("b: add-on saved") { StremioAddons.addons.value.any { it.manifestUrl == url } }
        addedStremio = url
    }

    @Test fun c_mihonRepoRetry() {
        val url = server.url("/mihon/index.min.json").toString()
        show(1)
        addThroughField("hub_tachiyomi_manga", "repos", "hub_tachiyomi_manga_add", url)
        sheetFailThenRetry("c", "hub_tachiyomi_manga_add", "Repository added · 1 extensions")
        val base = dev.asfakul.yumeiro.sources.tachiyomi.TachiyomiExtensions.normalizeRepo(url)
        wait("c: repo saved") { dev.asfakul.yumeiro.sources.tachiyomi.TachiyomiExtensions.state.value.repos.any { it.url == base } }
        addedMihon = base
    }

    @Test fun d_homeServerRetry() {
        val url = server.url("/dav/").toString()
        show(4)
        wait("hub_servers list") { has("hub_servers") }
        compose.onNodeWithTag("hub_servers", true).performScrollToNode(hasText("Add a server", substring = true))
        compose.waitForIdle()
        compose.onAllNodesWithText("WebDAV", useUnmergedTree = true)[0].performClick()
        wait("server form") { has("server_url") }
        compose.onNodeWithTag("server_url", true).performTextInput(url)
        compose.waitForIdle()
        compose.onAllNodesWithText("Connect", useUnmergedTree = true)[0].performClick()
        // ER-19: the form stays open on failure, so the reason and Retry must be inside it (a bottom
        // message sat behind the dialog and keyboard, run 43985787392).
        wait("d: reason shown inside the form") { has("server_error") }
        val err = compose.onAllNodesWithTag("server_error", false).fetchSemanticsNodes().first()
            .config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }.joinToString(" ") { t -> t.text }
        log("RTY_CASE d error='$err'")
        FirstRun.shot("rty_d_error")
        assertTrue("d: the reason is named ('$err')", err.contains("404") || err.contains("found", true))
        assertTrue("d: the button says Retry", hasText("Retry"))
        failing.set(false)
        screenTap("server_connect")
        wait("d: Retry repeats the call") { hits.get() > 0 }
        wait("d: success after Retry") { hasText("WebDAV connected") }
        wait("d: form closed") { !has("server_url") }
        log("RTY_CASE d retried hits=${hits.get()}")
        log("RTY_RESULT PASS d")
        wait("d: server saved") { dev.asfakul.yumeiro.sources.servers.HomeServers.servers.value.any { it.url.trimEnd('/') == url.trimEnd('/') } }
        addedServer = dev.asfakul.yumeiro.sources.servers.HomeServers.servers.value.first { it.url.trimEnd('/') == url.trimEnd('/') }.id
    }

    /**
     * ER-19 second site: the Kitsu sign-in form also stays open on failure. Real kitsu.io with a
     * made-up account (or no route on CI): either way sign-in fails, and the reason must be inside
     * the form, not a bottom message behind it.
     */
    @Test fun e_kitsuSignInFailureIsShownInTheForm() {
        // Other device tests in the same run may leave Kitsu signed in (run 46104092927 api36); this case needs it signed out.
        dev.asfakul.yumeiro.tracking.Kitsu.signOut(ctx)
        compose.setContent { Box(Modifier.fillMaxSize()) { dev.asfakul.yumeiro.ui.screens.settings.TrackingScreen(onBack = {}); AppMessageHost() } }
        wait("tracking screen") { has("tracking_screen") }
        wait("Kitsu sign-in button") { has("tracker_kitsu_signin") }
        // FINAL-38 (run 44666731015, tv-api24): on the landscape TV window the Kitsu card is below the fold of
        // the tracking list, so a click on the unscrolled node hit nothing and the form never opened. Scroll the
        // node into view first (a no-op where it is already visible); the form must still open from the tap.
        runCatching { compose.onNodeWithTag("tracker_kitsu_signin", true).performScrollTo() }
        compose.waitForIdle()
        compose.onNodeWithTag("tracker_kitsu_signin", true).performClick()
        wait("Kitsu form") { has("kitsu_email") }
        runCatching { compose.onNodeWithTag("kitsu_email", true).performScrollTo() }
        compose.onNodeWithTag("kitsu_email", true).performTextInput("yumeiro-er19-test@example.invalid")
        compose.onNodeWithTag("kitsu_pass", true).performTextInput("not-a-real-password")
        runCatching { androidx.test.espresso.Espresso.closeSoftKeyboard() }
        compose.waitForIdle()
        val confirm = compose.onAllNodesWithText("Sign in", useUnmergedTree = true)
        runCatching { confirm[confirm.fetchSemanticsNodes().size - 1].performScrollTo() }
        confirm[confirm.fetchSemanticsNodes().size - 1].performClick()
        wait("e: reason shown inside the form", 45_000) { has("kitsu_error") }
        val err = compose.onAllNodesWithTag("kitsu_error", false).fetchSemanticsNodes().first()
            .config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }.joinToString(" ") { t -> t.text }
        FirstRun.shot("rty_e_error")
        log("RTY_CASE e error='$err' bottomMessage=${has("app_message")}")
        assertTrue("e: a reason is named", err.isNotBlank())
        assertTrue("e: no bottom message hidden behind the form", !has("app_message"))
        assertTrue("e: the button says Retry", hasText("Retry"))
        log("RTY_RESULT PASS e")
    }

    private companion object {
        const val JS_INDEX = """[{"id":"er12","name":"ER12 Source","lang":"en","sourceCodeLanguage":"1","itemType":"0","isManga":"true","sourceCodeUrl":"https://example.invalid/er12.js","baseUrl":"https://example.invalid","version":"0.0.1"}]"""
        const val MIHON_INDEX = """[{"name":"Tachiyomi: ER12","pkg":"eu.kanade.tachiyomi.extension.en.er12","apk":"tachiyomi-en.er12-v1.4.1.apk","lang":"en","code":1,"version":"1.4.1","nsfw":0}]"""
        const val DAV_LIST = """<?xml version="1.0" encoding="utf-8"?><d:multistatus xmlns:d="DAV:"><d:response><d:href>/dav/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype><d:displayname>dav</d:displayname></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response><d:response><d:href>/dav/clip.mp4</d:href><d:propstat><d:prop><d:resourcetype/><d:getcontenttype>video/mp4</d:getcontenttype><d:getcontentlength>1000</d:getcontentlength><d:displayname>clip.mp4</d:displayname></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response></d:multistatus>"""
        const val MANIFEST = """{"id":"test.er12","name":"ER12 Test","version":"1.0.0","types":["movie"],"resources":["stream"],"catalogs":[]}"""
    }
}
