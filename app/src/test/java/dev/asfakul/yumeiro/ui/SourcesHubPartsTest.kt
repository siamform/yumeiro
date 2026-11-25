/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import dev.asfakul.yumeiro.ui.screens.sources.HubPart
import dev.asfakul.yumeiro.ui.screens.sources.HubParts
import dev.asfakul.yumeiro.ui.screens.sources.SourcesHubScreen
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** SRC-HUB-1 (owner list 3): each source tab is Installed / Browse / Repositories, with an in-page link field. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h1600dp")
class SourcesHubPartsTest {
    @get:Rule val rule = createComposeRule()
    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun click(tag: String) { rule.onNodeWithTag(tag, true).performClick(); rule.waitForIdle() }

    private fun addDisabled() = rule.onNodeWithTag("hub_js_add_add", true).fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)

    @Test fun opensWhereTheUserHasSomething() {
        assertEquals(HubPart.INSTALLED, HubParts.default(installed = 3, repos = 1))
        assertEquals(HubPart.BROWSE, HubParts.default(installed = 0, repos = 2))
        assertEquals(HubPart.REPOS, HubParts.default(installed = 0, repos = 0))
        assertEquals("Installed · 4", HubParts.label(HubPart.INSTALLED, 4))
        assertEquals("Browse", HubParts.label(HubPart.BROWSE, null))
    }

    @Test fun javascriptTabIsSplitInThree() {
        rule.setContent { MTPlayerTheme { SourcesHubScreen(onBack = {}, initialTab = 3) } }
        rule.waitUntil(10_000) { has("hub_js") }
        listOf("installed", "browse", "repos").forEach { assertTrue("part $it", has("hub_part_$it")) }
        // Nothing added: it opens on Repositories with the link field in the page (no dialog), not on a mixed list.
        assertTrue(has("hub_js_add_field"))
        assertTrue(has("hub_js_add_paste"))
        assertTrue("Add is off while the field is empty", addDisabled())
        rule.onNodeWithTag("hub_js_add_field", true).performTextInput("https://example.org/index.json")
        rule.waitForIdle()
        assertFalse("Add is on once a link is typed", addDisabled())
        // Browse with no repository explains why and leads back to Repositories.
        click("hub_part_browse")
        assertFalse(has("hub_js_add_field"))
        assertTrue(has("hub_empty"))
        click("hub_empty_action")
        assertTrue(has("hub_js_add_field"))
        // Installed holds only installed sources.
        click("hub_part_installed")
        assertTrue(has("hub_empty"))
        assertFalse(has("hub_js_add_field"))
    }

    @Test fun stremioHasNoRepositoriesPart() {
        rule.setContent { MTPlayerTheme { SourcesHubScreen(onBack = {}, initialTab = 2) } }
        rule.waitUntil(10_000) { has("hub_stremio") }
        assertTrue(has("hub_part_installed") && has("hub_part_browse"))
        assertFalse(has("hub_part_repos"))
        rule.onNodeWithTag("hub_stremio", true).performScrollToNode(hasTestTag("hub_stremio_add_field"))
        assertTrue(has("hub_stremio_add_field"))
    }
}
