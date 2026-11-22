/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.profiles.Profiles
import dev.asfakul.yumeiro.ui.theme.AccentTone
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * OWN-12 on a device: removing a profile's PIN asks for the current PIN first; a wrong PIN keeps it, the right one
 * removes it. Path: Settings > Profiles > profile row (unlock to edit) > Remove PIN > PIN pad. Log tag YuOwn12.
 */
@RunWith(AndroidJUnit4::class)
class Own12PinGuardDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var added: String? = null
    private fun log(s: String) = Log.i("YuOwn12", s)
    private fun has(tag: String) = runCatching { rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    private fun waitTag(what: String, tag: String, ms: Long = 20_000) {
        val ok = runCatching { rule.waitUntil(ms) { has(tag) }; true }.getOrDefault(false)
        assertTrue("$what ($tag) after ${ms}ms · ${FirstRun.onScreen(rule)}", ok)
    }
    private fun pause(ms: Long) { val until = SystemClock.uptimeMillis() + ms; runCatching { rule.waitUntil(ms + 5_000) { SystemClock.uptimeMillis() > until } } }
    private fun typePin(pin: String) {
        waitTag("PIN pad", "pin_pad", 15_000)
        pin.forEach { d -> rule.onAllNodesWithTag("pin_key_$d", true)[0].performClick(); rule.waitForIdle() }
        if (has("pin_key_ok")) rule.onAllNodesWithTag("pin_key_ok", true)[0].performClick()
        rule.waitForIdle(); pause(800)
    }
    private fun pinOf(id: String) = Profiles.find(ctx, id)?.hasPin == true

    @After fun tearDown() { added?.let { runCatching { Profiles.remove(ctx, it) } } }

    @Test fun removingAPinAsksForTheCurrentPin() {
        assertTrue("flag own12_pin_guard is on", FeatureFlags.isOn(FeatureFlags.Flag.PIN_GUARD))
        val p = Profiles.add(ctx, "Own12 test", AccentTone.entries.first()); added = p.id
        assertTrue("PIN set", Profiles.setPin(ctx, p.id, "4826"))
        FirstRun.touchMode()
        FirstRun.tapVisible(rule, "topbar_settings_action")
        waitTag("Settings", "settings_home")
        FirstRun.scrollTo(rule, "settings_cat_profiles")
        FirstRun.tapVisible(rule, "settings_cat_profiles")
        waitTag("Profiles page", "profiles_settings")
        FirstRun.scrollTo(rule, "profile_row_${p.id}")
        FirstRun.tapVisible(rule, "profile_row_${p.id}")
        typePin("4826") // unlock to edit (another profile)
        waitTag("edit dialog", "profile_pin_remove")
        FirstRun.tapVisible(rule, "profile_pin_remove")
        pause(600)
        FirstRun.shot("own12_remove_asks_pin")
        assertTrue("OWN-12: Remove PIN opens the PIN pad · ${FirstRun.onScreen(rule)}", has("pin_pad"))
        assertTrue("PIN still set before the current PIN is given", pinOf(p.id))
        typePin("1111") // wrong
        assertTrue("a wrong PIN keeps the lock", pinOf(p.id))
        log("OWN12 wrong PIN kept the lock")
        if (!has("pin_pad")) { waitTag("edit dialog again", "profile_pin_remove"); FirstRun.tapVisible(rule, "profile_pin_remove") }
        // A wrong PIN may leave the pad up with an error; type the right one.
        typePin("4826")
        runCatching { rule.waitUntil(5_000) { !pinOf(p.id) } }
        assertFalse("OWN-12: the right PIN removes the lock", pinOf(p.id))
        log("OWN12_RESULT PASS")
    }
}
