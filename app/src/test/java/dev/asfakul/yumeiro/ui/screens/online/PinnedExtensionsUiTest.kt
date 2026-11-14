/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import dev.asfakul.yumeiro.online.PinnedExtensions
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** R15, Phase 5: pin favourite extensions in "Show videos from" and pick them from the pinned bar. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class PinnedExtensionsUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun pinInTheSheetThenPickFromTheBar() {
        var picked: String? = "none"
        rule.setContent {
            MTPlayerTheme {
                var pins by remember { mutableStateOf(listOf<String>()) }
                var sheet by remember { mutableStateOf(true) }
                androidx.compose.foundation.layout.Column {
                    PinnedExtensionBar(pins, selected = null) { picked = it }
                    if (sheet) SourceFilterSheet(
                        names = listOf("Alpha", "Beta", "Gamma"), selected = null,
                        status = { SourceStatus.Ready(10) }, onPick = {}, onDismiss = { sheet = false },
                        pins = pins, onTogglePin = { pins = PinnedExtensions.toggled(pins, it) ?: pins },
                    )
                }
            }
        }
        val pin = rule.onNodeWithTag("online_filter_pin_Gamma")
        pin.assertIsOff().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        pin.performClick()
        rule.onNodeWithTag("online_filter_pin_Gamma").assertIsOn()
        rule.onNodeWithTag("online_pinned_Gamma").assertIsDisplayed().performClick()
        assertEquals("Gamma", picked)
        rule.onNodeWithTag("online_pinned_all").performClick()
        assertNull(picked)
        // Unpin: the chip goes away.
        rule.onNodeWithTag("online_filter_pin_Gamma").performClick()
        rule.onNodeWithTag("online_pinned_bar").assertDoesNotExist()
    }
}
