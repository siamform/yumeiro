/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui

import android.Manifest
import android.app.Application
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Opening Extensions must open exactly one page: Back returns to where the user was. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class ExtensionNavTest {
    val rule = createAndroidComposeRule<MainActivity>()
    private val grantRule = object : org.junit.rules.ExternalResource() {
        override fun before() {
            Robolectric.setupContentProvider(FakeMediaProvider::class.java, "media")
            shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(
                Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO,
            )
        }
    }
    @get:Rule val chain: org.junit.rules.RuleChain = org.junit.rules.RuleChain.outerRule(grantRule).around(rule)

    private fun count(tag: String) = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size
    private fun exists(tag: String) = count(tag) > 0
    private fun settle(ms: Long = 700) { rule.mainClock.advanceTimeBy(ms); rule.waitForIdle() }
    private fun waitFor(tag: String, ms: Long = 8_000) = rule.waitUntil(ms) { rule.mainClock.advanceTimeBy(100); exists(tag) }
    private fun back() { rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; settle(1_000) }

    private fun awaitHome() {
        dev.asfakul.yumeiro.ui.screens.extras.SetupGuide.resetForTests()
        dev.asfakul.yumeiro.ui.screens.extras.ExploreTour.resetForTests()
        dev.asfakul.yumeiro.network.Connectivity.overrideForTests(true)
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitUntil(10_000) { exists("nav_tab_music") }
        settle(1_000)
    }

    private fun assertSinglePage(log: StringBuilder) {
        // Watch the page for a few seconds: nothing may open it a second time.
        repeat(20) { settle(200); log.append(count("extensions_screen")).append(' ') }
        assertEquals("one Extensions page on screen ($log)", 1, count("extensions_screen"))
        back()
        settle(1_500)
        assertFalse("Back leaves Extensions (a second copy was stacked) ($log)", exists("extensions_screen"))
    }

    @Test fun homeShortcutOpensExtensionsOnce() {
        awaitHome()
        val list = "home_list"
        runCatching { rule.onNodeWithTag(list, true).performScrollToNode(hasTestTag("quick_card_store")) }
        settle(400)
        rule.onNodeWithTag("quick_card_store", true).performClick()
        waitFor("extensions_screen")
        assertSinglePage(StringBuilder())
    }

    @Test fun yumixButtonOpensExtensionsOnce() {
        awaitHome()
        rule.onNodeWithTag("nav_tab_videos", true).performClick(); settle(1_000)
        waitFor("video_source_switch")
        rule.onNodeWithTag("video_source_switch", true).performClick(); settle(600)
        rule.onNodeWithTag("video_source_online", true).performClick(); settle(1_500)
        waitFor("online_extensions")
        rule.onNodeWithTag("online_extensions", true).performClick()
        waitFor("extensions_screen")
        assertSinglePage(StringBuilder())
    }
}
