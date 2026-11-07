/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.mtrevanced.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.mtrevanced.online.ExtensionManager
import dev.asfakul.mtrevanced.online.ExtensionMarket
import dev.asfakul.mtrevanced.ui.screens.online.ExtensionsScreen
import dev.asfakul.mtrevanced.ui.screens.online.OnlineHomeScreen
import dev.asfakul.mtrevanced.ui.screens.online.OnlineDetailScreen
import dev.asfakul.mtrevanced.ui.screens.online.VideoSourceSwitch
import dev.asfakul.mtrevanced.ui.theme.MTPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class OnlinePagesTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before fun reset() {
        java.io.File(context.filesDir, "extensions").deleteRecursively()
        ExtensionManager.resetForTests(); ExtensionMarket.resetForTests()
    }

    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun settle(cond: () -> Boolean) = rule.waitUntil(10_000) { cond() }

    @Test fun switchesBetweenDeviceAndOnline() {
        var market = 0; var manage = 0
        rule.setContent {
            MTPlayerTheme {
                var source by remember { mutableStateOf(dev.asfakul.mtrevanced.ui.screens.online.VideoSource.DEVICE) }
                Column {
                    VideoSourceSwitch(source, { source = it })
                    if (source == dev.asfakul.mtrevanced.ui.screens.online.VideoSource.ONLINE) OnlineHomeScreen(onOpenSearch = {}, onOpenTitle = {}, onOpenExtensions = { manage++ }, onOpenMarket = { market++ })
                }
            }
        }
        rule.onNodeWithTag("video_source_online").performClick()
        settle { has("online_empty") }
        rule.onNodeWithTag("online_empty_primary").performClick()
        rule.onNodeWithTag("online_empty_secondary").performClick()
        assertEquals(1, market); assertEquals(1, manage)
        rule.onNodeWithTag("video_source_device").performClick()
        settle { !has("online_home") }
    }

    @Test fun extensionsPageShowsEmptyStateAndAddMenu() {
        var market = 0
        rule.setContent { MTPlayerTheme { ExtensionsScreen(onBack = {}, onOpenMarket = { market++ }) } }
        settle { has("extensions_empty") }
        rule.onNodeWithTag("extensions_empty_primary").performClick()
        assertEquals(1, market)
        rule.onNodeWithTag("extensions_add").performClick()
        settle { has("extensions_add_repo") }
        rule.onNodeWithTag("extensions_add_repo").performClick()
        settle { has("input_dialog_field") }
    }

    @Test fun unknownSourceShowsNotFound() {
        var back = 0
        rule.setContent { MTPlayerTheme { OnlineDetailScreen(api = "Missing", url = "https://x/1", onBack = { back++ }, onOpenTitle = {}, onOpenExtensions = {}) } }
        settle { has("online_detail_missing") }
        rule.onNodeWithTag("online_detail_missing_secondary").performClick()
        assertEquals(1, back)
    }
}
