/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui

import android.Manifest
import android.app.Application
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.MainActivity
import dev.asfakul.yumeiro.network.Connectivity
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * G6-SP1: on Android 13+ music and videos are two separate permissions. With videos allowed and
 * music denied, Music said "No audio files detected… tap Rescan" and never offered to allow
 * music access: the prompt card needed a "permanently denied" flag no caller ever set, and
 * "videos allowed" counted as access. Music must show the card, ask for READ_MEDIA_AUDIO from it,
 * and drop the card once access is allowed (e.g. back from Android settings).
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class MusicAccessPromptTest {
    val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val grantRule = object : org.junit.rules.ExternalResource() {
        override fun before() {
            Robolectric.setupContentProvider(FakeMediaProvider::class.java, "media")
            shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.POST_NOTIFICATIONS)
            shadowOf(app).denyPermissions(Manifest.permission.READ_MEDIA_AUDIO)
        }
    }
    @get:Rule val chain: org.junit.rules.RuleChain = org.junit.rules.RuleChain.outerRule(grantRule).around(rule)

    @After fun online() = Connectivity.overrideForTests(true)

    private fun exists(tag: String) = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun settle(ms: Long = 700) { rule.mainClock.advanceTimeBy(ms); rule.waitForIdle() }
    private fun waitFor(tag: String, ms: Long = 8_000) = runCatching { rule.waitUntil(ms) { rule.mainClock.advanceTimeBy(100); exists(tag) } }.isSuccess

    @Test fun videosOnlyShowsMusicAccessCardThatAsksForAudio() {
        dev.asfakul.yumeiro.ui.screens.extras.SetupGuide.resetForTests()
        dev.asfakul.yumeiro.ui.screens.extras.ExploreTour.resetForTests()
        // Offline: Music opens on the device library.
        Connectivity.overrideForTests(false)
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitUntil(10_000) { exists("nav_tab_music") }
        settle(1_000)
        rule.onNodeWithTag("nav_tab_music", true).performClick(); settle(1_000)
        assertTrue("Music device library not shown", waitFor("music_screen_root"))

        assertTrue("music access denied but no card offering to allow it", waitFor("permission_prompt_card", 5_000))
        rule.onNodeWithTag("grant_permission_button", true).performClick(); settle()
        val asked = shadowOf(rule.activity).lastRequestedPermission?.requestedPermissions?.toList().orEmpty()
        assertTrue("card must ask for READ_MEDIA_AUDIO, asked $asked", Manifest.permission.READ_MEDIA_AUDIO in asked)

        // Allowed in Android settings; back in the app.
        shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_AUDIO)
        rule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        settle(1_000)
        rule.waitUntil(5_000) { rule.mainClock.advanceTimeBy(100); !exists("permission_prompt_card") }
        assertFalse(exists("permission_prompt_card"))
    }
}
