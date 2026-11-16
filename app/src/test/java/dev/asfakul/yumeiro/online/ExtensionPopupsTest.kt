/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import dev.asfakul.yumeiro.online.ExtensionPopups.Action
import dev.asfakul.yumeiro.online.ExtensionPopups.Kind
import dev.asfakul.yumeiro.online.ExtensionPopups.Mode
import dev.asfakul.yumeiro.online.ExtensionPopups.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** ADR-014 decision logic (unit). The window plumbing is covered by ExtensionPopupsDeviceTest (emulator). */
class ExtensionPopupsTest {
    private fun s(vararg t: String, input: Boolean = false, web: Boolean = false) = Snapshot(t.toList(), input, web)

    @Test fun donationDialogsArePromotional() {
        // Wording taken from real repos' donation dialogs (phisher98 DonationManager, Ko-fi / Telegram prompts).
        assertEquals(Kind.PROMOTIONAL, ExtensionPopups.classify(s("Support the developer", "If you enjoy these extensions, consider donating.", "Donate", "Later")))
        assertEquals(Kind.PROMOTIONAL, ExtensionPopups.classify(s("Buy me a coffee", "Ko-fi")))
        assertEquals(Kind.PROMOTIONAL, ExtensionPopups.classify(s("Join our Telegram channel for updates", "Join", "Close")))
        assertEquals(Kind.PROMOTIONAL, ExtensionPopups.classify(s("Star the repo on GitHub")))
    }

    @Test fun anythingThatDoesAJobIsFunctional() {
        assertEquals(Kind.FUNCTIONAL, ExtensionPopups.classify(s("Log in", "Username", "Password")))
        assertEquals(Kind.FUNCTIONAL, ExtensionPopups.classify(s("Donate", input = true))) // has a text field
        assertEquals(Kind.FUNCTIONAL, ExtensionPopups.classify(s("Support us", web = true))) // a web page
        assertEquals(Kind.FUNCTIONAL, ExtensionPopups.classify(s("Choose a server", "Server 1", "Server 2")))
        assertEquals(Kind.FUNCTIONAL, ExtensionPopups.classify(s("Login to keep supporting the developer"))) // mixed: shown
        assertEquals(Kind.FUNCTIONAL, ExtensionPopups.classify(s())) // nothing readable: shown, never lost
        assertEquals(Kind.FUNCTIONAL, ExtensionPopups.classify(s("Sources updated", "OK")))
    }

    @Test fun decisionTable() {
        // Functional windows are always shown, in every mode, even over the player (a captcha while resolving).
        for (m in Mode.entries) for (p in listOf(true, false)) for (r in listOf(true, false))
            assertEquals(Action.SHOW_FRAMED, ExtensionPopups.decide(Kind.FUNCTIONAL, m, p, r))
        assertEquals(Action.SHOW_FRAMED, ExtensionPopups.decide(Kind.PROMOTIONAL, Mode.SHOW, playerOnScreen = true, rateOk = false))
        assertEquals(Action.DROP, ExtensionPopups.decide(Kind.PROMOTIONAL, Mode.BLOCK_PROMOTIONAL, false, true))
        assertEquals(Action.HOLD_AND_CARD, ExtensionPopups.decide(Kind.PROMOTIONAL, Mode.QUIET, false, true))
        assertEquals(Action.HOLD_DEFERRED, ExtensionPopups.decide(Kind.PROMOTIONAL, Mode.QUIET, true, true))
        assertEquals(Action.DROP, ExtensionPopups.decide(Kind.PROMOTIONAL, Mode.QUIET, false, rateOk = false))
    }

    @Test fun onceAWeekPerExtension() {
        val day = 24L * 60 * 60 * 1000
        assertTrue(ExtensionPopups.rateOk(last = 0, now = 10 * day))
        assertFalse(ExtensionPopups.rateOk(last = 10 * day, now = 16 * day))
        assertTrue(ExtensionPopups.rateOk(last = 10 * day, now = 17 * day))
        assertTrue(ExtensionPopups.rateOk(last = 20 * day, now = 10 * day)) // clock moved back: do not lock out for ever
    }

    @Test fun extensionCodeIsRecognised() {
        assertTrue(ExtensionWindowWatch.isExtensionClass("com.phisher98.DonationManager"))
        assertFalse(ExtensionWindowWatch.isExtensionClass("dev.asfakul.yumeiro.MainActivity"))
        assertFalse(ExtensionWindowWatch.isExtensionClass("android.app.Dialog"))
        // The bundled CloudStream library is ours; an extension using the same package name is not.
        assertFalse(ExtensionWindowWatch.isExtensionClass("com.lagradost.cloudstream3.MainActivity"))
        assertTrue(ExtensionWindowWatch.isExtensionClass("com.lagradost.SomeProviderPlugin"))
    }
}

/** ADR-014 / ExtensionGuard: a window added after its screen is gone is not "inside a frame" (unit). */
class ExtensionGuardSetViewTest {
    private fun err(vararg frames: Pair<String, String>) = IllegalStateException("x").apply {
        stackTrace = frames.map { (c, m) -> StackTraceElement(c, m, "F.kt", 1) }.toTypedArray()
    }

    @Test fun badTokenInSetViewIsNotAFrame() {
        val badToken = err("android.view.ViewRootImpl" to "setView", "android.view.WindowManagerGlobal" to "addView", "com.phisher98.DonationManager" to "show", "android.os.Handler" to "dispatchMessage")
        assertFalse(ExtensionGuard.unwoundThroughFrame(badToken))
        assertTrue(ExtensionGuard.isFromExtension(badToken))
        assertTrue(ExtensionGuard.unwoundThroughFrame(err("android.view.ViewRootImpl" to "performTraversals", "com.example.Provider" to "onDraw")))
    }
}
