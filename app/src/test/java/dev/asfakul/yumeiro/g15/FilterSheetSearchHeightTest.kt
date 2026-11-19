/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.g15

import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import dev.asfakul.yumeiro.ui.screens.online.SourceFilterSheet
import dev.asfakul.yumeiro.ui.screens.online.SourceStatus
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * NBUG-10 (G15-1, owner report 2026-11-18): Yumix "Show videos from" → search showed only 2 extensions although
 * more matched. Cause: on phones the sheet opens at half height (`skipPartiallyExpanded = isTv`). With many
 * extensions it stays there; the keyboard then covers everything below the first rows, and the list cannot scroll
 * them up because they are already "inside" the list. CloudStream (`selectHomepage`, STATE_EXPANDED) and Mihon
 * (`AdaptiveSheet`, no half state) open such pickers fully; so does Yumeiro's own search sheet (YuSearch.kt).
 * The test opens the sheet with 30 extensions on a phone-sized screen: it must open fully (top edge in the top
 * 40 % of the window), and after typing every match must be inside the window.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class FilterSheetSearchHeightTest {
    @get:Rule val rule = createComposeRule()

    private val names = (1..27).map { "Source $it" } + listOf("Anime One", "Anime Two", "Anime Three")

    @Test fun manyExtensionsOpenFullyAndEveryMatchIsOnScreen() {
        var heightPx = 0f
        rule.setContent {
            heightPx = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp * density }
            MTPlayerTheme {
                SourceFilterSheet(names = names, selected = null, status = { SourceStatus.Ready(10) }, onPick = {}, onDismiss = {})
            }
        }
        rule.waitForIdle()
        val top = rule.onNodeWithTag("online_filter_sheet").fetchSemanticsNode().boundsInRoot.top
        assertTrue("the sheet opened at ${(top / heightPx * 100).toInt()} % of the screen; with 30 extensions it must open fully (top in the top 40 %)", top < heightPx * 0.4f)

        rule.onNodeWithTag("online_filter_search").performTextInput("anime")
        rule.waitForIdle()
        val hits = listOf("Anime One", "Anime Two", "Anime Three")
        hits.forEach { n ->
            val node = rule.onAllNodesWithTag("online_filter_$n", true).fetchSemanticsNodes().single()
            assertTrue("'$n' is outside the window (${node.boundsInRoot})", node.boundsInRoot.bottom <= heightPx + 1f)
        }
        assertEquals("only the matches are listed", 0, rule.onAllNodesWithTag("online_filter_Source 1", true).fetchSemanticsNodes().size)
    }
}
