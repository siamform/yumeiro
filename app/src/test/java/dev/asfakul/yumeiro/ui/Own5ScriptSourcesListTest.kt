/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.online.ExtensionMarket
import dev.asfakul.yumeiro.sources.SourceHub
import dev.asfakul.yumeiro.sources.ecosystem.EcosystemRegistry
import dev.asfakul.yumeiro.ui.screens.online.ExtensionsScreen
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * OWN-5 (owner list, 2.1.0): after adding Vega, its installed providers did not show in the installed extensions
 * list; the only way to manage them was Sources → Script repos. The Extensions page now lists them with a direct
 * Manage shortcut.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Own5ScriptSourcesListTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before fun reset() {
        java.io.File(context.filesDir, "extensions").deleteRecursively()
        ExtensionManager.resetForTests(); ExtensionMarket.resetForTests()
        val repo = "https://raw.githubusercontent.com/Zenda-Cross/vega-providers/main/manifest.json"
        EcosystemRegistry.setStateForTests(EcosystemRegistry.State(
            repos = listOf(EcosystemRegistry.Repo(repo, SourceHub.Ecosystem.VEGA, "Vega providers", 1L)),
            installed = listOf(EcosystemRegistry.Installed(repo, SourceHub.Ecosystem.VEGA, "vega", "VMovies", "2.52", null, emptyMap())),
        ))
    }
    @After fun tearDown() { EcosystemRegistry.resetForTests() }

    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()

    @Test fun installedVegaProvidersShowWithAManageShortcut() {
        var scriptRepos = 0
        rule.setContent { MTPlayerTheme { ExtensionsScreen(onBack = {}, onOpenMarket = {}, onOpenSources = {}, onOpenScriptRepos = { scriptRepos++ }) } }
        rule.waitUntil(10_000) { has("extensions_list") }
        rule.onNodeWithTag("extensions_list").performScrollToNode(hasTestTag("extensions_script_sources"))
        rule.waitUntil(10_000) { has("extensions_script_sources") }
        assertEquals(1, rule.onAllNodesWithText("VMovies", substring = true, useUnmergedTree = true).fetchSemanticsNodes().size)
        rule.onNodeWithTag("extensions_script_sources").performClick()
        assertEquals(1, scriptRepos)
    }
}
