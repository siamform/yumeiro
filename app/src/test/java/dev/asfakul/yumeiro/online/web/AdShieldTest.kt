/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.web

import dev.asfakul.yumeiro.online.web.AdShield.Decision.ALLOW
import dev.asfakul.yumeiro.online.web.AdShield.Decision.BLOCK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdShieldTest {
    private val home = "https://watch.example.to/"

    private fun nav(target: String, gesture: Boolean = true, redirect: Boolean = false, login: Boolean = false, current: String = home) =
        AdShield.decideNavigation(home, current, target, gesture, redirect, login)

    @Test fun staysOnTheSite() {
        assertEquals(ALLOW, nav("https://example.to/anime/one-piece"))
        assertEquals(ALLOW, nav("https://cdn.example.to/x", gesture = false))
        assertEquals("example.co.uk", AdShield.site("a.b.example.co.uk"))
        assertTrue(AdShield.sameSite("https://x.example.co.uk/", "http://example.co.uk/a"))
    }

    @Test fun captchaAndAuthHostsAlwaysPass() {
        assertEquals(ALLOW, nav("https://challenges.cloudflare.com/cdn-cgi/challenge-platform/h/b", gesture = false))
        assertEquals(ALLOW, nav("https://newassets.hcaptcha.com/captcha/v1"))
        assertEquals(ALLOW, nav("https://accounts.google.com/o/oauth2/auth"))
        assertFalse(AdShield.blockRequest("https://challenges.cloudflare.com/turnstile/v0/api.js"))
    }

    @Test fun popUnderJumpsAreHeldBack() {
        // Tap on the page sends it to an ad network.
        assertEquals(BLOCK, nav("https://www.popads.net/click?id=1"))
        // Script moves the whole page to some other site without a tap.
        assertEquals(BLOCK, nav("https://random-offer.xyz/landing", gesture = false))
        // Tap on a fake "play" overlay that leaves the site (check page).
        assertEquals(BLOCK, nav("https://random-offer.xyz/landing", gesture = true))
        // Escapes into other apps.
        assertEquals(BLOCK, nav("intent://scan/#Intent;scheme=market;end"))
        assertEquals(BLOCK, nav("market://details?id=x"))
        assertEquals(ALLOW, nav("about:blank"))
    }

    @Test fun serverRedirectsAndLoginTapsAreAllowed() {
        assertEquals(ALLOW, nav("https://example-mirror.cc/", gesture = false, redirect = true))
        assertEquals(BLOCK, nav("https://ads.exoclick.com/x", gesture = false, redirect = true))
        assertEquals(ALLOW, nav("https://sso.partner-login.com/", gesture = true, login = true))
        assertEquals(BLOCK, nav("https://sso.partner-login.com/", gesture = false, login = true))
    }

    @Test fun popupsAreDroppedExceptSignIn() {
        assertEquals(BLOCK, AdShield.decidePopup(home, home, "https://random-offer.xyz/", login = false))
        assertEquals(BLOCK, AdShield.decidePopup(home, home, "https://example.to/page", login = false))
        assertEquals(BLOCK, AdShield.decidePopup(home, home, null, login = true))
        assertEquals(ALLOW, AdShield.decidePopup(home, home, "https://accounts.google.com/signin", login = true))
        assertEquals(ALLOW, AdShield.decidePopup(home, home, "https://example.to/login", login = true))
        assertEquals(BLOCK, AdShield.decidePopup(home, home, "https://doubleclick.net/x", login = true))
    }

    @Test fun adRequestsGetEmptyAnswers() {
        assertTrue(AdShield.blockRequest("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"))
        assertTrue(AdShield.blockRequest("https://example.to/player/vast.xml"))
        assertFalse(AdShield.blockRequest("https://example.to/player/main.js"))
    }

    @Test fun checkPageCooldownAndText() {
        assertFalse(BrowserCheckPolicy.tooSoon(null, 1_000))
        assertTrue(BrowserCheckPolicy.tooSoon(0, 60_000))
        assertFalse(BrowserCheckPolicy.tooSoon(0, BrowserCheckPolicy.ASK_COOLDOWN_MS + 1))
        assertEquals("Blocked 1 ad or pop-up", AdShieldText.blocked(1))
        assertEquals("Blocked 4 ads & pop-ups", AdShieldText.blocked(4))
    }
}
