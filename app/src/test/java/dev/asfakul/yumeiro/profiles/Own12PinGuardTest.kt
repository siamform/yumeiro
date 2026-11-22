/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.profiles

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.ui.screens.profiles.ProfilesSettings
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
 * OWN-12 (owner 2026-11-22): "when user try to set pin its never ask for reset/forget password question,
 * also when user try remove it never ask for current pass". Root cause: the edit dialog's Change PIN /
 * Remove PIN / security-question rows ran at once, and saving a first PIN closed the flow.
 */
@RunWith(RobolectricTestRunner::class)
// A tall phone: the pad's bottom row (0, OK) is off the 470 dp default screen. 320 dp wide, not 411: Robolectric never
// idles with a text field in a dialog on a screen wider than 320 dp (robolectric/robolectric#8460), which hung the
// security-questions step (CI 45051586296). The device test Own12PinGuardDeviceTest checks it on real screens.
@Config(sdk = [34], qualifiers = "w320dp-h891dp-xxhdpi")
class Own12PinGuardTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val ctx get() = rule.activity
    private val id = Profiles.DEFAULT

    @After fun tearDown() { Profiles.setPin(ctx, id, null); Profiles.setRecovery(ctx, id, emptyList(), emptyList()) }

    private fun open() {
        rule.setContent { MTPlayerTheme { ProfilesSettings(rule.activity) } }
        rule.onNodeWithTag("profile_row_$id").performClick()
        rule.waitForIdle()
    }

    private fun typePin(pin: String) {
        pin.forEach { rule.onNodeWithTag("pin_key_$it").performClick() }
        rule.onNodeWithTag("pin_key_ok").performClick()
        rule.waitForIdle()
    }

    @Test fun removePinAsksForTheCurrentPin() {
        assertTrue(Profiles.setPin(ctx, id, "1234"))
        open()
        rule.onNodeWithTag("profile_pin_remove").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("pin_pad").assertIsDisplayed()
        assertTrue("the PIN must not go before the current PIN is typed", Profiles.find(ctx, id)!!.hasPin)
        typePin("9999")
        rule.onNodeWithTag("pin_error").assertIsDisplayed()
        rule.onNodeWithText("Wrong PIN").assertIsDisplayed()
        assertTrue("a wrong PIN removes nothing", Profiles.find(ctx, id)!!.hasPin)
        typePin("1234")
        assertFalse("the right PIN removes it", Profiles.find(ctx, id)!!.hasPin)
    }

    @Test fun changePinAsksForTheCurrentPinFirst() {
        assertTrue(Profiles.setPin(ctx, id, "1234"))
        open()
        rule.onNodeWithTag("profile_pin").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Current PIN for ${Profiles.find(ctx, id)!!.name}").assertIsDisplayed()
    }

    @Test fun firstPinGoesOnToTheSecurityQuestions() {
        open()
        rule.onNodeWithTag("profile_pin").performClick()
        rule.waitForIdle()
        typePin("1234")
        typePin("1234")
        assertTrue(Profiles.find(ctx, id)!!.hasPin)
        rule.onNodeWithTag("recovery_q_0").assertIsDisplayed()
        rule.onNodeWithTag("recovery_save").assertIsDisplayed()
    }

    @Test fun flagIsOn() { assertEquals(true, FeatureFlags.Flag.PIN_GUARD.default) }
}
