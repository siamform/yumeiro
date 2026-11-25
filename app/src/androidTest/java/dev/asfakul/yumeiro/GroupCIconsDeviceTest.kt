/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.sources.novel.NovelPlugin
import dev.asfakul.yumeiro.sources.novel.NovelSources
import dev.asfakul.yumeiro.ui.screens.novel.NovelHomeScreen
import dev.asfakul.yumeiro.ui.screens.novel.NovelSourcesScreen
import dev.asfakul.yumeiro.ui.screens.sources.SourcesHubScreen
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import dev.asfakul.yumeiro.ui.tv.LocalTvMode
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * GROUP C on the device (phones and TV, TV layout on the TV emulator): the Source page tabs and the
 * Novel pages show search, filter, add and help as icons; the box, sheet or help card appears only
 * after the icon is pressed. Test d drives the "+" icon with real remote keys (`input keyevent`).
 * Before Group C the pages had the wide link box, the search box, chip rows and "about" paragraphs.
 * Log tag YuGroupC.
 */
@RunWith(AndroidJUnit4::class)
class GroupCIconsDeviceTest {
    @get:Rule val rule = createComposeRule()
    private val tv = InstrumentationRegistry.getInstrumentation().targetContext.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun hasText(t: String) = rule.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun click(tag: String) { rule.onNodeWithTag(tag, true).performClick(); rule.waitForIdle() }
    private fun waitTag(tag: String) = rule.waitUntil(15_000) { has(tag) }
    private fun disabled(tag: String) = rule.onNodeWithTag(tag, true).fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)
    private fun key(code: Int) { FirstRun.shell("input keyevent $code"); Thread.sleep(500); rule.waitForIdle() }

    private val plugins = listOf(
        NovelPlugin("p.en", "Alpha Novels", "https://a.example", "English", "1.0", "https://a.example/a.js", ""),
        NovelPlugin("p.ja", "Beta Shosetsu", "https://b.example", "日本語", "1.0", "https://b.example/b.js", ""),
    )

    @After fun reset() { NovelSources.setForTest(NovelSources.State(), null) }

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent { CompositionLocalProvider(LocalTvMode provides tv) { MTPlayerTheme { content() } } }
    }

    private fun addFlow(prefix: String) {
        assertFalse("$prefix: link box sits in the page", has(prefix + "_field"))
        assertTrue("$prefix: no + icon", has(prefix + "_plus"))
        click(prefix + "_plus")
        waitTag(prefix + "_field")
        assertTrue(has(prefix + "_paste"))
        assertTrue("Add on while empty", disabled(prefix + "_add"))
        rule.onNodeWithTag(prefix + "_field", true).performTextInput("https://example.org/index.json")
        rule.waitForIdle()
        assertFalse("Add off after typing", disabled(prefix + "_add"))
    }

    private fun helpFlow(tag: String, words: String) {
        assertFalse("$tag: about text sits in the page", hasText(words))
        assertTrue("$tag: no ? icon", has(tag))
        click(tag)
        waitTag(tag + "_dialog")
        assertTrue(hasText(words))
        click(tag + "_ok")
        assertFalse(has(tag + "_dialog"))
    }

    @Test fun a_sourceTabsUseIcons() {
        show { SourcesHubScreen(onBack = {}, initialTab = 3) }
        waitTag("hub_js")
        helpFlow("hub_js_help", "sandboxed QuickJS")
        addFlow("hub_js_add")
        Log.i("YuGroupC", "GROUPC_A PASS tv=$tv")
    }

    @Test fun b_novelSourcesUseIcons() {
        NovelSources.setForTest(NovelSources.State(repos = listOf("https://r.example/plugins.min.json")), plugins)
        show { NovelSourcesScreen(onBack = {}) }
        waitTag("novel_plugin_p.en")
        helpFlow("novel_sources_help", "LNReader plugin list")
        assertFalse(has("novel_plugin_filter"))
        click("novel_plugin_search")
        waitTag("novel_plugin_filter")
        assertFalse(has("novel_lang_日本語"))
        click("novel_lang_filter")
        waitTag("novel_lang_sheet")
        click("novel_lang_日本語")
        click("novel_lang_sheet_done")
        rule.waitUntil(5_000) { !has("novel_plugin_p.en") }
        assertTrue(has("novel_plugin_p.ja"))
        assertFalse(has("novel_repo_lnreader"))
        addFlow("novel_repo")
        assertTrue(has("novel_repo_lnreader"))
        Log.i("YuGroupC", "GROUPC_B PASS tv=$tv")
    }

    @Test fun c_novelHomeUsesIcons() {
        NovelSources.setForTest(NovelSources.State(installed = plugins), emptyList())
        show { NovelHomeScreen(onBack = {}, onOpenNovel = {}, onOpenSources = {}) }
        waitTag("novel_search_open")
        assertFalse("search box before the icon", has("novel_search"))
        assertFalse("source chip row in the page", has("novel_source_bar"))
        click("novel_search_open")
        waitTag("novel_search")
        click("novel_source_filter")
        waitTag("novel_source_sheet")
        click("novel_source_p.ja")
        click("novel_source_sheet_done")
        rule.waitUntil(5_000) { !has("novel_source_sheet") }
        Log.i("YuGroupC", "GROUPC_C PASS tv=$tv")
    }

    /** Remote keys: the + icon takes focus, OK opens the sheet, the next key lands inside the sheet. */
    @Test fun d_remoteOpensAddSheet() {
        rule.setContent { CompositionLocalProvider(LocalTvMode provides true) { MTPlayerTheme { SourcesHubScreen(onBack = {}, initialTab = 3) } } }
        waitTag("hub_js_add_plus")
        rule.onNodeWithTag("hub_js_add_plus", true).requestFocus()
        rule.waitForIdle()
        assertTrue("+ icon not focusable", rule.onAllNodes(isFocused() and hasTestTag("hub_js_add_plus"), true).fetchSemanticsNodes().isNotEmpty())
        key(23) // DPAD_CENTER
        waitTag("hub_js_add")
        Thread.sleep(800)
        key(20) // DPAD_DOWN
        val inSheet = rule.onAllNodes(isFocused() and hasAnyAncestor(hasTestTag("hub_js_add")), true).fetchSemanticsNodes().isNotEmpty()
        Log.i("YuGroupC", "GROUPC_D inSheet=$inSheet tv=$tv")
        assertTrue("first remote key after opening is not inside the add sheet", inSheet)
    }
}
