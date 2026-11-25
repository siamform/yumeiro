/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.online.ExtensionReview
import dev.asfakul.yumeiro.online.ExtensionSources
import dev.asfakul.yumeiro.online.OnlineRuntime
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * GROUP G2 on the device (phones and TV), in the real app: Yumix → "Show videos from" → one extension. The page then
 * has exactly one search (its own box, `hue_section_search`), no magnifier in the top bar, one header with the
 * extension's name (no "Showing: name" strip repeating it) and the filter / show-all actions in that header.
 * "Show all" brings the top bar's magnifier back. Before Group G the page had the strip, the header's own search
 * icon, the top-bar magnifier and the box: three search entries. Log tag YuGroupG.
 */
@RunWith(AndroidJUnit4::class)
class GroupGDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(s: String) = Log.i("YuGroupG", s)
    private fun n(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().size
    private fun has(tag: String) = n(tag) > 0
    private fun waitTag(tag: String, ms: Long = 30_000) {
        val ok = runCatching { rule.waitUntil(ms) { has(tag) }; true }.getOrDefault(false)
        assertTrue("no '$tag' after ${ms}ms · ${FirstRun.onScreen(rule)}", ok)
    }

    @Before fun setUp() {
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        runCatching { ui.grantRuntimePermission(ctx.packageName, "android.permission.POST_NOTIFICATIONS") }
        FirstRun.toTabs(rule)
        runBlocking {
            withTimeout(300_000) {
                ExtensionManager.start(ctx); ExtensionManager.ensureLoaded(ctx)
                var have = ExtensionManager.state.value.installed.count { it.enabled }
                if (have < WANT) {
                    val items = ExtensionSources.all(ctx, force = true).items.filter {
                        it.primary && !it.isNsfw && !it.isDown && it.apiVersion <= OnlineRuntime.API_VERSION
                    }
                    for (c in items) {
                        if (have >= WANT) break
                        if (ExtensionManager.state.value.installed.any { it.internalName == c.internalName }) continue
                        runCatching { ExtensionManager.install(ctx, c, ExtensionReview.Mode.AUTO) }
                            .onSuccess { have++; log("installed ${c.internalName}") }
                            .onFailure { log("install ${c.internalName} failed: ${it.message}") }
                    }
                }
            }
        }
    }

    private val notRows = setOf("online_filter_all", "online_filter_sheet", "online_filter_clear", "online_filter_empty",
        "online_filter_search", "online_filter_search_clear", "online_filter_active", "online_filter_active_clear")

    private fun rowTags(): List<String> = rule.onAllNodes(SemanticsMatcher("filter row") { node ->
        val t = node.config.getOrElseNullable(SemanticsProperties.TestTag) { null } ?: return@SemanticsMatcher false
        t.startsWith("online_filter_") && !t.startsWith("online_filter_pin_") && !t.startsWith("online_filter-") && t !in notRows
    }, true).fetchSemanticsNodes().sortedBy { it.boundsInRoot.top }.map { it.config[SemanticsProperties.TestTag] }

    @Test fun a_oneExtensionPageHasOneSearch() {
        FirstRun.tapVisible(rule, "nav_tab_home"); FirstRun.tapVisible(rule, "quick_card_hues")
        waitTag("online_filter", 120_000)
        val tv = has("tv_nav_rail")
        log("start tv=$tv topbarSearch=${n("topbar_search_action")}")
        if (!tv) assertEquals("all extensions: the top bar keeps its magnifier", 1, n("topbar_search_action"))
        FirstRun.tapVisible(rule, "online_filter"); waitTag("online_filter_sheet")
        val rows = rowTags()
        log("sheet rows: $rows")
        assertTrue("need an extension in the sheet: $rows", rows.isNotEmpty())
        rule.onAllNodesWithTag(rows.first(), true)[0].performClick(); rule.waitForIdle()
        waitTag("online_channel_header", 60_000)
        waitTag("hue_section_search", 120_000)
        rule.waitForIdle()
        val found = mapOf(
            "header" to n("online_channel_header"), "box" to n("hue_section_search"), "topbar" to n("topbar_search_action"),
            "showing" to rule.onAllNodesWithText("Showing", substring = true).fetchSemanticsNodes().size,
            "filter" to n("online_filter_active"), "clear" to n("online_filter_active_clear"),
        )
        log("RESULT_A ${rows.first()} $found")
        FirstRun.shot("groupg_one_extension")
        assertEquals("one header: $found", 1, found["header"])
        assertEquals("one search box: $found", 1, found["box"])
        assertEquals("no top-bar magnifier on a one-extension page: $found", 0, found["topbar"])
        assertEquals("no 'Showing:' strip repeating the name: $found", 0, found["showing"])
        assertEquals("filter action in the header: $found", 1, found["filter"])
        assertEquals("show-all action in the header: $found", 1, found["clear"])
        FirstRun.tapVisible(rule, "online_filter_active_clear")
        val back = runCatching { rule.waitUntil(20_000) { !has("online_channel_header") }; true }.getOrDefault(false)
        log("RESULT_A cleared=$back topbar=${n("topbar_search_action")}")
        assertTrue("show all returns to every extension", back)
        if (!tv) { rule.waitUntil(10_000) { has("topbar_search_action") }; assertEquals(1, n("topbar_search_action")) }
    }

    private companion object { const val WANT = 2 }
}
