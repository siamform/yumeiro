/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import android.webkit.CookieManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.web.SiteLogins
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * G13 N2-1: "Sign out of site" must remove the site's sign-in cookies from the in-app browser.
 * Real Android WebView cookie store (emulator). Sets the cookie kinds sites use for sign-in:
 * host-only, `Domain=` (shared with sub-domains), `__Secure-` and `__Host-` (Secure only), and
 * SameSite=None; then calls SiteLogins.remove and reads the store back.
 * Old code: only a host-only, non-Secure expiry line was written. Log tag YuSignOutTest, SIGNOUT_.
 */
@RunWith(AndroidJUnit4::class)
class SiteSignOutDeviceTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext.applicationContext
    private val host = "yu-signout.example"
    private fun log(s: String) = Log.i("YuSignOutTest", s)

    private fun set(url: String, value: String) {
        val done = CountDownLatch(1)
        var ok = false
        inst.runOnMainSync { CookieManager.getInstance().setCookie(url, value) { ok = it; done.countDown() } }
        done.await(5, TimeUnit.SECONDS)
        log("SIGNOUT_SET $value ok=$ok")
    }

    private fun read(url: String): String {
        var c: String? = null
        inst.runOnMainSync { c = CookieManager.getInstance().getCookie(url) }
        return c.orEmpty()
    }

    @Test fun signOutRemovesEverySignInCookie() {
        val url = "https://$host/"
        set(url, "hostOnly=1; Path=/")
        set(url, "session=2; Domain=$host; Path=/")
        set(url, "__Secure-auth=3; Secure; Path=/")
        set(url, "__Host-csrf=4; Secure; Path=/")
        set(url, "cross=5; Secure; SameSite=None; Path=/")
        set("https://www.$host/", "wwwSession=6; Domain=$host; Path=/")
        val before = read(url)
        log("SIGNOUT_BEFORE $before")
        assertTrue("setup: cookies were not stored: $before", before.contains("session=2") && before.contains("hostOnly=1"))
        SiteLogins.add(ctx, host)
        inst.runOnMainSync { SiteLogins.remove(ctx, host) }
        // setCookie without a callback is queued; read until the store settles (max 3 s).
        var after = read(url)
        val until = System.currentTimeMillis() + 3_000
        while (after.isNotBlank() && System.currentTimeMillis() < until) { Thread.sleep(100); after = read(url) }
        val www = read("https://www.$host/")
        log("SIGNOUT_AFTER root='$after' www='$www' signedIn=${SiteLogins.has(ctx, host)}")
        assertTrue("still signed in after sign-out: root='$after' www='$www'", after.isBlank() && www.isBlank())
        assertTrue("host still listed as signed in", !SiteLogins.has(ctx, host))
        log("SIGNOUT_RESULT PASS")
    }
}
