/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import dev.asfakul.yumeiro.ui.navigation.bottomNavItems
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** NAV-BAR-2 (owner list 33): labelled tabs, a visible active pill, same routes and one TalkBack label per tab. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h780dp-xxhdpi")
class BottomBarLabelsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun everyTabHasALabelAndTheActiveOneHasThePill() {
        var route by mutableStateOf(bottomNavItems.first().route)
        rule.setContent {
            MTPlayerTheme { MTBottomBar(currentRoute = route, onNavigateToRoute = { route = it }) }
        }
        bottomNavItems.forEach { item ->
            rule.onNodeWithTag("nav_label_${item.testTag}", useUnmergedTree = true).assertExists()
            // The label text is not read twice: the tab is announced once by its content description.
            rule.onNodeWithContentDescription(item.label).assertExists()
        }
        rule.onNodeWithTag(bottomNavItems.first().testTag).assertIsSelected()
        rule.onNodeWithTag("nav_active_pill", useUnmergedTree = true).assertExists()

        val target = bottomNavItems[1]
        rule.onNodeWithTag(target.testTag).performClick()
        rule.waitForIdle()
        assertEquals("tapping a tab still navigates to its own route", target.route, route)
        rule.onNodeWithTag(target.testTag).assertIsSelected()
        rule.onNodeWithTag(bottomNavItems.first().testTag).assertIsNotSelected()
    }
}
