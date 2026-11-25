/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.asfakul.yumeiro.sources.novel.NovelPlugin
import dev.asfakul.yumeiro.sources.novel.NovelSources
import dev.asfakul.yumeiro.ui.screens.novel.NovelHomeScreen
import dev.asfakul.yumeiro.ui.screens.novel.NovelSourcesScreen
import dev.asfakul.yumeiro.ui.screens.sources.SourcesHubScreen
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * GROUP C: search, filter, add and help are icons. No wide link box, search box, chip row or "about"
 * paragraph sits in these pages until its icon is pressed.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h1600dp")
class GroupCIconsTest {
    @get:Rule val rule = createComposeRule()
    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun hasText(t: String) = rule.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun click(tag: String) { rule.onNodeWithTag(tag, true).performClick(); rule.waitForIdle() }
    private fun disabled(tag: String) = rule.onNodeWithTag(tag, true).fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)

    private val plugins = listOf(
        NovelPlugin("p.en", "Alpha Novels", "https://a.example", "English", "1.0", "https://a.example/a.js", ""),
        NovelPlugin("p.ja", "Beta Shosetsu", "https://b.example", "日本語", "1.0", "https://b.example/b.js", ""),
    )

    @After fun reset() { NovelSources.setForTest(NovelSources.State(), null) }

    /** One add flow: "+" opens the sheet with field, Paste and Add (off while empty). */
    private fun addFlow(prefix: String) {
        assertFalse("no link box in the page", has(prefix + "_field"))
        assertTrue("plus icon", has(prefix + "_plus"))
        click(prefix + "_plus")
        assertTrue(has(prefix + "_field")); assertTrue(has(prefix + "_paste"))
        assertTrue("Add off while empty", disabled(prefix + "_add"))
        rule.onNodeWithTag(prefix + "_field", true).performTextInput("https://example.org/index.json")
        rule.waitForIdle()
        assertFalse("Add on once typed", disabled(prefix + "_add"))
    }

    private fun helpFlow(tag: String, words: String) {
        assertFalse("about text not in the page", hasText(words))
        click(tag)
        assertTrue(has(tag + "_dialog")); assertTrue(hasText(words))
        click(tag + "_ok")
        assertFalse(has(tag + "_dialog"))
    }

    @Test fun javascriptRepositoriesUseIcons() {
        rule.setContent { MTPlayerTheme { SourcesHubScreen(onBack = {}, initialTab = 3) } }
        rule.waitUntil(10_000) { has("hub_js") }
        helpFlow("hub_js_help", "sandboxed QuickJS")
        addFlow("hub_js_add")
    }

    @Test fun aniyomiRepositoriesUseIcons() {
        rule.setContent { MTPlayerTheme { SourcesHubScreen(onBack = {}, initialTab = 0) } }
        rule.waitUntil(10_000) { has("hub_tachiyomi_anime") }
        helpFlow("hub_tachiyomi_anime_help", "run inside Yumeiro")
        addFlow("hub_tachiyomi_anime_add")
    }

    @Test fun stremioAddUsesIcons() {
        rule.setContent { MTPlayerTheme { SourcesHubScreen(onBack = {}, initialTab = 2) } }
        rule.waitUntil(10_000) { has("hub_stremio") }
        helpFlow("hub_stremio_help", "torrent-only streams")
        addFlow("hub_stremio_add")
    }

    @Test fun novelSourcesUseIcons() {
        NovelSources.setForTest(NovelSources.State(repos = listOf("https://r.example/plugins.min.json")), plugins)
        rule.setContent { MTPlayerTheme { NovelSourcesScreen(onBack = {}) } }
        rule.waitUntil(10_000) { has("novel_plugin_p.en") }
        helpFlow("novel_sources_help", "LNReader plugin list")
        // Search: icon first, field after.
        assertFalse(has("novel_plugin_filter"))
        click("novel_plugin_search")
        assertTrue(has("novel_plugin_filter"))
        // Language: a filter icon with a sheet, not a chip row.
        assertFalse("no language chips in the page", has("novel_lang_日本語"))
        assertFalse(has("novel_lang_sheet"))
        click("novel_lang_filter")
        assertTrue(has("novel_lang_sheet"))
        click("novel_lang_日本語")
        click("novel_lang_sheet_done")
        assertFalse(has("novel_plugin_p.en")); assertTrue(has("novel_plugin_p.ja"))
        // Add repository: "+" sheet, with LNReader's list as a quick pick inside it.
        assertFalse(has("novel_repo_lnreader"))
        addFlow("novel_repo")
        assertTrue(has("novel_repo_lnreader"))
    }

    @Test fun novelHomeSearchAndSourceAreIcons() {
        NovelSources.setForTest(NovelSources.State(installed = plugins), emptyList())
        rule.setContent { MTPlayerTheme { NovelHomeScreen(onBack = {}, onOpenNovel = {}, onOpenSources = {}) } }
        rule.waitUntil(10_000) { has("novel_search_open") }
        assertFalse("no search box until the icon", has("novel_search"))
        assertFalse("no source chip row", has("novel_source_bar"))
        click("novel_search_open")
        assertTrue(has("novel_search"))
        click("novel_source_filter")
        assertTrue(has("novel_source_sheet"))
        click("novel_source_p.ja")
        click("novel_source_sheet_done")
        rule.waitForIdle()
        assertFalse(has("novel_source_sheet"))
    }

    @Test fun iconsAreFullTouchTargets() {
        rule.setContent { MTPlayerTheme { SourcesHubScreen(onBack = {}, initialTab = 3) } }
        rule.waitUntil(10_000) { has("hub_js_add_plus") }
        for (t in listOf("hub_js_add_plus", "hub_js_help")) {
            val n = rule.onNodeWithTag(t, true).fetchSemanticsNode()
            val minPx = with(n.layoutInfo.density) { 48.0f * density }
            val touch = n.touchBoundsInRoot // the 40 dp icon reserves 48 dp of touch area (minimum interactive size)
            assertTrue("$t touch ${touch.width} x ${touch.height}", touch.width >= minPx - 1 && touch.height >= minPx - 1)
            val merged = rule.onNodeWithTag(t).fetchSemanticsNode().config
            assertEquals(true, merged.getOrElseNullable(SemanticsProperties.ContentDescription) { null }?.firstOrNull()?.isNotBlank())
        }
    }
}
