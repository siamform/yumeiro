/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import dev.asfakul.yumeiro.online.web.BrowserCheck
import dev.asfakul.yumeiro.online.web.SiteLogins
import dev.asfakul.yumeiro.ui.components.ErrorHelp
import dev.asfakul.yumeiro.ui.screens.online.SignupSteps
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SiteAccessTest {
    @Test fun loginMatchesSubdomains() {
        val hosts = setOf("site.to")
        assertTrue(SiteLogins.matches(hosts, "www.site.to"))
        assertTrue(SiteLogins.matches(hosts, "api.site.to"))
        assertFalse(SiteLogins.matches(hosts, "notsite.to"))
    }
    @Test fun blockedMessageNeverOffersACaptchaPage() {
        // Round 13: the interactive captcha page is gone (it only showed a blank screen).
        val m = BrowserCheck.blockedMessage("site.to")
        assertTrue(m.contains("site.to") && !m.contains("Open site") && !m.contains("captcha"))
    }
    @Test fun signupGuideMentionsHostAndWarnsAboutPayment() {
        val s = SignupSteps.steps("site.to")
        assertTrue(s.first().contains("site.to"))
        assertTrue(s.any { it.contains("Never pay") })
    }
    @Test fun updateTipOnlyForOutdatedErrors() {
        assertFalse(ErrorHelp.forExtension("site.to refused this request (firewall)").any { it.contains("Check for an update") })
        assertTrue(ErrorHelp.forExtension("NoSuchMethodError: parse failed").any { it.contains("Check for an update") })
    }
}
