/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.web

import dev.asfakul.yumeiro.online.web.PopupPolicy.Nav
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * B12 (owner): website redirects. A page moving to another site by itself is stopped (Settings → Stop redirects),
 * app links never open another app (an intent's web fallback loads in the page), and a link to a video or
 * playlist plays in Yumeiro's player. The device test drives the same rules on a real WebView.
 */
@RunWith(RobolectricTestRunner::class) // android.net.Uri in LinkHosts
@Config(sdk = [36])
class GroupNRedirectPolicyTest {
    private val page = "https://anime.example/watch/1"

    private fun nav(target: String, gesture: Boolean = false, redirect: Boolean = false, stop: Boolean = true, chain: Boolean = false) =
        PopupPolicy.navigation(page, target, gesture, redirect, stop, chain)

    @Test fun scriptJumpToAnotherSiteIsHeld() {
        assertEquals(Nav.Hold("https://win-prize.example/claim"), nav("https://win-prize.example/claim"))
        // Same site (sub-domains too) is the page's own navigation.
        assertEquals(Nav.Allow, nav("https://anime.example/watch/2"))
        assertEquals(Nav.Allow, nav("https://cdn.anime.example/page"))
    }

    @Test fun tapsServerRedirectsTheWalkerAndTheSwitchLetItGo() {
        assertEquals("a tap goes where the person asked", Nav.Allow, nav("https://other.example/", gesture = true))
        assertEquals("a site that moved domain answers with an HTTP redirect", Nav.Allow, nav("https://anime-new.example/", redirect = true))
        assertEquals("the short-link walker drives its own jumps", Nav.Allow, nav("https://other.example/", chain = true))
        assertEquals("Stop redirects off", Nav.Allow, nav("https://other.example/", stop = false))
    }

    @Test fun appLinksNeverLeaveYumeiro() {
        for (u in listOf("market://details?id=com.example", "tel:123", "whatsapp://send?text=x", "file:///sdcard/a", "intent://scan/#Intent;package=com.example.app;end")) {
            assertEquals(u, Nav.Block, nav(u, gesture = true))
        }
        val fb = "intent://x/#Intent;scheme=myapp;package=com.example;S.browser_fallback_url=https%3A%2F%2Fanime.example%2Ffallback%3Fa%3D1;end"
        assertEquals(Nav.InApp("https://anime.example/fallback?a=1"), nav(fb))
        assertEquals("an https intent opens its own address here", Nav.InApp("https://other.example/path?q=1"),
            nav("intent://other.example/path?q=1#Intent;scheme=https;package=com.android.chrome;end", gesture = true))
        assertEquals("a script-made intent to another site is a redirect like any other",
            Nav.Hold("https://other.example/path"), nav("intent://other.example/path#Intent;scheme=https;end"))
    }

    @Test fun mediaLinksPlayInYumeiro() {
        val p = nav("https://files.other.example/ep1.mp4", gesture = true)
        assertTrue("$p", p is Nav.Play && p.url == "https://files.other.example/ep1.mp4")
        assertTrue("a script jump to a playlist plays too (not held)", nav("https://cdn.other.example/master.m3u8?t=1") is Nav.Play)
    }

    @Test fun intentFallbackParsing() {
        assertNull(PopupPolicy.intentFallback("intent://x#Intent;package=a;end"))
        assertNull(PopupPolicy.intentFallback("intent://x"))
        assertEquals("http://h.example/p", PopupPolicy.intentFallback("intent://h.example/p#Intent;scheme=http;end"))
        assertNull("javascript: fallback is ignored", PopupPolicy.intentFallback("intent://x#Intent;scheme=myapp;S.browser_fallback_url=javascript%3Aalert(1);end"))
    }
}
