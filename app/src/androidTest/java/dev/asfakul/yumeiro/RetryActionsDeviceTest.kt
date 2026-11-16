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
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
 *
 * Log tag YuRetry: RTY_CASE / RTY_RESULT PASS.
 */
@RunWith(AndroidJUnit4::class)
class RetryActionsDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var server: MockWebServer
    private val hits = AtomicInteger(0)
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
                // The first request of each case fails; later ones answer. Any client-side retry would
                // count here too, so the test compares hits before and after the Retry tap.
                val n = hits.incrementAndGet()
                if (n == 1) return MockResponse.Builder().code(404).body("gone").build()
                return when (request.url.encodedPath) {
                    "/repo/index.json" -> MockResponse.Builder().addHeader("Content-Type", "application/json").body(JS_INDEX).build()
                    "/addon/manifest.json" -> MockResponse.Builder().addHeader("Content-Type", "application/json").body(MANIFEST).build()
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

    /** Opens the URL dialog through the section's "Add", types [url] and confirms. */
    private fun addThroughDialog(list: String, section: String, url: String) {
        wait("$list list") { has(list) }
        compose.onNodeWithTag(list, true).performScrollToNode(hasText(section, substring = true))
        compose.waitForIdle()
        // The section row's own "Add" is the one right after its title; the market may show other "Add"s.
        val adds = compose.onAllNodesWithText("Add", useUnmergedTree = true)
        val n = adds.fetchSemanticsNodes().size
        assertTrue("an Add button on screen", n > 0)
        val sectionTop = compose.onAllNodesWithText(section, substring = true, useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot.top
        val idx = adds.fetchSemanticsNodes().withIndex()
            .filter { kotlin.math.abs(it.value.boundsInRoot.top - sectionTop) < 120f }
            .minByOrNull { kotlin.math.abs(it.value.boundsInRoot.top - sectionTop) }?.index
        assertTrue("the '$section' Add button", idx != null)
        adds[idx!!].performClick()
        wait("URL dialog") { has("hub_url_field") }
        compose.onNodeWithTag("hub_url_field", true).performTextInput(url)
        compose.waitForIdle()
        // Dialog confirm: the last "Add" (the dialog is drawn on top, after the page).
        val confirm = compose.onAllNodesWithText("Add", useUnmergedTree = true)
        confirm[confirm.fetchSemanticsNodes().size - 1].performClick()
    }

    private fun failThenRetry(case: String, success: String) {
        wait("$case: error message with Retry") { has("app_message") && has("app_message_action") }
        val err = messageText()
        val before = hits.get()
        log("RTY_CASE $case error='$err' hits=$before")
        FirstRun.shot("rty_${case}_error")
        assertTrue("$case: the message names the reason, not a bare 'try again' ('$err')", err.contains("404") || err.contains("found", true) || err.contains("answered", true))
        compose.onNodeWithTag("app_message_action", true).performClick()
        wait("$case: Retry repeats the call") { hits.get() > before }
        wait("$case: success after Retry") { hasText(success) }
        log("RTY_CASE $case retried hits=${hits.get()} success='$success'")
        log("RTY_RESULT PASS $case")
    }

    @Test fun a_javascriptRepoRetry() {
        val url = server.url("/repo/index.json").toString()
        show(3)
        addThroughDialog("hub_js", "Your repositories", url)
        failThenRetry("a", "Repository added · 1 sources")
        wait("a: repo saved") { JsSources.state.value.repos.any { it == url } }
        addedJs = url
    }

    @Test fun b_stremioAddonRetry() {
        val url = server.url("/addon/manifest.json").toString()
        show(2)
        addThroughDialog("hub_stremio", "Your add-ons", url)
        failThenRetry("b", "ER12 Test added")
        wait("b: add-on saved") { StremioAddons.addons.value.any { it.manifestUrl == url } }
        addedStremio = url
    }

    private companion object {
        const val JS_INDEX = """[{"id":"er12","name":"ER12 Source","lang":"en","sourceCodeLanguage":"1","itemType":"0","isManga":"true","sourceCodeUrl":"https://example.invalid/er12.js","baseUrl":"https://example.invalid","version":"0.0.1"}]"""
        const val MANIFEST = """{"id":"test.er12","name":"ER12 Test","version":"1.0.0","types":["movie"],"resources":["stream"],"catalogs":[]}"""
    }
}
