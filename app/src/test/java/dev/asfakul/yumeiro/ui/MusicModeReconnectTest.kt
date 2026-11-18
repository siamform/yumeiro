/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui

import android.Manifest
import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.MainActivity
import dev.asfakul.yumeiro.network.Connectivity
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * G6-MO1: the Music tab follows the connection until the user picks a mode. A user who picked
 * "Online" was moved to "On device" when the connection dropped (right: Online cannot play offline),
 * but the pick was only a yes/no flag, so when the connection came back the tab stayed on "On device"
 * for good. The tab must go back to the mode the user picked.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class MusicModeReconnectTest {
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

    @After fun online() = Connectivity.overrideForTests(true)

    private fun exists(tag: String) = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun settle(ms: Long = 700) { rule.mainClock.advanceTimeBy(ms); rule.waitForIdle() }
    private fun waitFor(tag: String, ms: Long = 8_000) = rule.waitUntil(ms) { rule.mainClock.advanceTimeBy(100); exists(tag) }

    /** The Music tab's source title ("On device", "Online", "Mix"). */
    private fun modeTitle(): String = rule.onNodeWithTag("music_source_switch", useUnmergedTree = false).fetchSemanticsNode().let { n ->
        (n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text } ?: "") + " " +
            (n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ") ?: "")
    }

    @Test fun pickedOnlineComesBackAfterTheConnectionReturns() {
        dev.asfakul.yumeiro.ui.screens.extras.SetupGuide.resetForTests()
        dev.asfakul.yumeiro.ui.screens.extras.ExploreTour.resetForTests()
        Connectivity.overrideForTests(true)
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitUntil(10_000) { exists("nav_tab_music") }
        settle(1_000)
        rule.onNodeWithTag("nav_tab_music", true).performClick(); settle(1_000)
        waitFor("music_source_switch")

        // The user picks Online themselves.
        rule.onNodeWithTag("music_source_switch", true).performClick(); settle()
        waitFor("music_source_online")
        rule.onNodeWithTag("music_source_online", true).performClick(); settle()
        val picked = modeTitle()
        assertTrue("Online not picked: [$picked]", "Online" in picked)

        Connectivity.overrideForTests(false); settle(1_000)
        val offline = modeTitle()
        assertTrue("offline must leave Online: [$offline]", "On device" in offline)

        Connectivity.overrideForTests(true); settle(1_000)
        val back = modeTitle()
        assertTrue("connection back, the picked mode Online must return: [$back]", "Online" in back && "On device" !in back)
    }
}
