/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.round2

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.notify.NotifySettings
import dev.asfakul.yumeiro.notify.ReliabilityGuide
import dev.asfakul.yumeiro.notify.ReliabilityGuide.Check
import dev.asfakul.yumeiro.ui.screens.extras.ReliabilityGuideDialog
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Round 2, order 9: notification reliability checks, maker guides and prompt pacing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReliabilityGuideTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun everyStrictMakerHasStepsAndPages() {
        val cases = mapOf(
            "Xiaomi" to "xiaomi", "POCO" to "xiaomi", "samsung" to "samsung", "HUAWEI" to "huawei", "HONOR" to "huawei",
            "OPPO" to "coloros", "realme" to "coloros", "OnePlus" to "coloros", "vivo" to "vivo", "TECNO MOBILE LIMITED" to "transsion",
            "INFINIX MOBILITY LIMITED" to "transsion", "asus" to "asus", "HMD Global" to "nokia", "Meizu" to "meizu",
        )
        cases.forEach { (m, key) ->
            val v = ReliabilityGuide.vendorFor(m)
            assertEquals(m, key, v.key)
            assertTrue(m, v.aggressive && v.steps.isNotEmpty() && v.pages.isNotEmpty())
        }
        assertFalse(ReliabilityGuide.vendorFor("Google", "google").aggressive)
        // Brand alone is enough (Redmi phones report MANUFACTURER=Xiaomi, some POCO report BRAND only).
        assertEquals("xiaomi", ReliabilityGuide.vendorFor("unknown", "Redmi").key)
    }

    @Test fun failingChecks() {
        assertEquals(emptySet<Check>(), ReliabilityGuide.failing(true, true, true, false, 10))
        assertEquals(setOf(Check.NOTIFICATIONS, Check.BATTERY), ReliabilityGuide.failing(false, true, false, false, 20))
        assertEquals(setOf(Check.NOT_RESTRICTED, Check.NOT_STANDBY_RESTRICTED), ReliabilityGuide.failing(true, true, true, true, 45))
        assertEquals(setOf(Check.BACKGROUND_ON), ReliabilityGuide.failing(true, false, true, false, 40))
    }

    @Test fun promptPacing() {
        val samsung = ReliabilityGuide.vendorFor("samsung")
        val pixel = ReliabilityGuide.vendorFor("Google")
        val day = 24 * 3_600_000L
        val now = 100 * day
        fun st(f: Set<Check> = emptySet(), stalled: Boolean = false, v: ReliabilityGuide.Vendor = samsung, done: Boolean = false) =
            ReliabilityGuide.Status(f, stalled, v, done)
        assertTrue("strict maker not set up", ReliabilityGuide.shouldPrompt(st(), 0, 0, now))
        assertFalse("maker confirmed and all fine", ReliabilityGuide.shouldPrompt(st(done = true), 0, 0, now))
        assertFalse("pixel, all fine", ReliabilityGuide.shouldPrompt(st(v = pixel), 0, 0, now))
        assertTrue("stalled even with battery exemption", ReliabilityGuide.shouldPrompt(st(stalled = true, v = pixel), 0, 0, now))
        assertTrue(ReliabilityGuide.shouldPrompt(st(setOf(Check.NOT_RESTRICTED), v = pixel), 0, 0, now))
        assertFalse("user turned notifications off: don't nag", ReliabilityGuide.shouldPrompt(st(setOf(Check.NOTIFICATIONS)), 0, 0, now))
        assertFalse("background checks off", ReliabilityGuide.shouldPrompt(st(setOf(Check.BACKGROUND_ON)), 0, 0, now))
        assertFalse("a week apart", ReliabilityGuide.shouldPrompt(st(), 1, now - 3 * day, now))
        assertTrue(ReliabilityGuide.shouldPrompt(st(), 1, now - 8 * day, now))
        assertFalse("at most 3 times", ReliabilityGuide.shouldPrompt(st(), 3, 0, now))
    }

    @Test fun missingMakerPageFallsBackToAppInfo() {
        val v = ReliabilityGuide.vendorFor("Xiaomi")
        assertTrue(ReliabilityGuide.availablePages(ctx, v).isEmpty()) // no MIUI security center here
        assertFalse(ReliabilityGuide.openVendorPage(ctx, v))
        val started: Intent = shadowOf(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, started.action)
    }

    @Test fun guideShowsChecksAndFixesBackgroundSwitch() {
        NotifySettings.init(ctx)
        NotifySettings.set(ctx, NotifySettings.Key.BACKGROUND, false)
        rule.setContent { MTPlayerTheme { ReliabilityGuideDialog(onClose = {}, inline = true) } }
        rule.onNodeWithText("Keep notifications on time").assertIsDisplayed()
        rule.onNodeWithTag("guide_background_on_fix").performClick()
        rule.waitForIdle()
        assertTrue(NotifySettings.get(ctx, NotifySettings.Key.BACKGROUND))
        rule.onNodeWithTag("guide_notifications").assertExists()
        rule.onNodeWithTag("guide_battery").assertExists()
    }
}
