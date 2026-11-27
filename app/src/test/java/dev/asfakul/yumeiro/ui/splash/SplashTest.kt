/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.splash

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.asfakul.yumeiro.ui.screens.splash.SplashScreen
import dev.asfakul.yumeiro.ui.screens.splash.SplashTiming
import dev.asfakul.yumeiro.ui.theme.LaunchLook
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import dev.asfakul.yumeiro.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** SPLASH-2 (owner list 31). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class SplashTest {
    @get:Rule val rule = createComposeRule()

    @Test fun launchLookFollowsYumeirosThemeNotOnlyThePhones() {
        assertEquals(LaunchLook.Look.LIGHT, LaunchLook.lookFor(LaunchLook.isDark(ThemeMode.LIGHT, systemDark = true, hour = 12), amoled = false))
        assertEquals(LaunchLook.Look.DARK, LaunchLook.lookFor(LaunchLook.isDark(ThemeMode.DARK, systemDark = false, hour = 12), amoled = false))
        assertEquals(LaunchLook.Look.BLACK, LaunchLook.lookFor(LaunchLook.isDark(ThemeMode.SYSTEM, systemDark = true, hour = 12), amoled = true))
        assertEquals(LaunchLook.Look.LIGHT, LaunchLook.lookFor(LaunchLook.isDark(ThemeMode.SYSTEM, systemDark = false, hour = 12), amoled = true))
        assertTrue(LaunchLook.isDark(ThemeMode.AFTERGLOW, systemDark = false, hour = 12))
    }

    @Test fun holdIsAtLeastOneAndAHalfSecondsEvenWithReducedMotion() {
        // B17 (owner, Beta) replaced the old "shorter than 900 ms" rule.
        assertTrue(SplashTiming.hold(false) >= 1500L)
        assertTrue(SplashTiming.hold(true) >= 1500L)
    }

    @Test fun showsTheMarkAndWordmarkThenFinishesByItself() {
        var done = 0
        rule.mainClock.autoAdvance = false
        rule.setContent { MTPlayerTheme { SplashScreen(onSplashFinished = { done++ }) } }
        rule.onNodeWithTag("splash_logo_icon", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("splash_wordmark", useUnmergedTree = true).assertExists()
        rule.mainClock.advanceTimeBy(SplashTiming.hold(false) - 200)
        assertEquals(0, done)
        rule.mainClock.advanceTimeBy(400)
        assertEquals(1, done)
    }

    @Test fun aTapSkipsIt() {
        var done = false
        rule.setContent { MTPlayerTheme { SplashScreen(onSplashFinished = { done = true }) } }
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("splash_screen_root").performClick()
        assertTrue(done)
    }
}
