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
    // N2-1: sign-out must also expire Domain= and Secure-only cookies.
    @Test fun signOutExpiresDomainAndSecureCookies() {
        val lines = SiteLogins.expiryLines("https://site.to/", "session", "site.to")
        assertTrue(lines.any { "Domain=site.to" in it && "Max-Age=0" in it && "Secure" in it })
        assertTrue(lines.any { "Domain=" !in it && "Secure" in it })
        val host = SiteLogins.expiryLines("https://site.to/", "__Host-x", "site.to")
        assertTrue("__Host- cookies may not carry Domain", host.isNotEmpty() && host.none { "Domain=" in it })
        assertTrue(SiteLogins.expiryLines("http://site.to/", "a", "site.to").none { "Secure" in it })
    }

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
