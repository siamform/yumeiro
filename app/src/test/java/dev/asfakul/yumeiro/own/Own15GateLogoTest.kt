/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.own

import android.app.Activity
import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.profiles.Profiles
import dev.asfakul.yumeiro.ui.screens.profiles.ProfileSession
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * OWN-15 (owner 2026-11-22: locked profile, app swiped away, unlock hours later: force close, then stuck on the logo;
 * device run 45039700220 showed the logo staying after the PIN): after "Who's watching?" the app opens straight in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Own15GateLogoTest {
    @Test fun flagIsOn() = assertTrue(FeatureFlags.isOn(FeatureFlags.Flag.GATE_SKIPS_LOGO))

    @Test fun openingTheCurrentProfileFromTheGateSkipsTheLogo() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        ProfileSession.chosen.value = false
        ProfileSession.open(activity, Profiles.currentId(activity))
        assertTrue("the gate closes", ProfileSession.chosen.value)
        assertTrue(ProfileSession.passedGate)
        assertFalse("no second logo after the gate", ProfileSession.showLogo())
    }

    @Test fun theAppAsksTheSessionBeforeShowingTheLogo() {
        val app = File("src/main/java/dev/asfakul/yumeiro/ui/screens/MTPlayerApp.kt").readText()
        // B1 (Beta): also once per process (SplashGate).
        assertTrue(app.contains("mutableStateOf(dev.asfakul.yumeiro.ui.screens.splash.SplashGate.shouldShow(dev.asfakul.yumeiro.ui.screens.profiles.ProfileSession.showLogo()))"))
        val ui = File("src/main/java/dev/asfakul/yumeiro/ui/screens/profiles/ProfileUi.kt").readText()
        assertTrue("open never throws to the gate", ui.contains("CrashLog.nonFatal(\"ProfileSession.open\", t)"))
    }
}
