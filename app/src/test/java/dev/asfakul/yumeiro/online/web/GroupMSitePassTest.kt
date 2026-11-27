/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.web

import dev.asfakul.yumeiro.online.ExtensionSpeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** B4 (Group M): a passed site check reaches the extensions of that site, and what the failed attempt left is dropped. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GroupMSitePassTest {
    @Test fun aPassCoversTheSiteAndItsSubdomainsOnly() {
        assertTrue(SitePass.matches("https://www.animesite.to/", "animesite.to"))
        assertTrue(SitePass.matches("https://animesite.to", "www.animesite.to"))
        assertTrue(SitePass.matches("https://api.animesite.to/v2", "animesite.to"))
        assertTrue(SitePass.matches("animesite.to", "ANIMESITE.TO"))
        assertTrue(SitePass.matches("http://127.0.0.1:41234", "127.0.0.1"))
        assertFalse(SitePass.matches("https://notanimesite.to/", "animesite.to"))
        assertFalse(SitePass.matches("https://other.net/", "animesite.to"))
        assertFalse(SitePass.matches(null, "animesite.to"))
        assertFalse(SitePass.matches("https://animesite.to/", ""))
    }

    @Test fun answersMadeUnderACheckPageAreKnown() {
        val t0 = 5_000_000L
        assertFalse(BrowserCheck.challengedSince("https://groupm-check.example/", t0))
        BrowserCheck.noteChallenge("www.groupm-check.example", t0 + 10)
        assertTrue("the check met during the call counts", BrowserCheck.challengedSince("https://groupm-check.example/", t0))
        assertFalse("a check before the call does not", BrowserCheck.challengedSince("https://groupm-check.example/", t0 + 11))
        assertFalse("another site is not affected", BrowserCheck.challengedSince("https://groupm-other.example/", t0))
    }

    @Test fun aPassEndsTheQuarantineTheCheckCausedButKeepsTheSpeed() {
        ExtensionSpeed.resetForTest()
        val now = System.currentTimeMillis()
        repeat(3) { ExtensionSpeed.record("GroupM Ext", 1_200, ok = false, now = now) }
        assertTrue(ExtensionSpeed.quarantined(ExtensionSpeed.stat("GroupM Ext"), now))
        ExtensionSpeed.forgive("GroupM Ext")
        val s = ExtensionSpeed.stat("GroupM Ext")!!
        assertFalse(ExtensionSpeed.quarantined(s, now))
        assertEquals(0, s.fails)
    }

    /** B4: Check now opens the check page where it came from (scheme and port kept). */
    @Test fun checkNowOpensThePageTheCheckCameFrom() {
        assertEquals("http://127.0.0.1:8123/", BrowserCheck.rootOf("http://127.0.0.1:8123/search?q=a"))
        assertEquals("https://site.to/", BrowserCheck.rootOf("https://site.to:443/x"))
        assertEquals(null, BrowserCheck.rootOf("ftp://site.to/"))
        BrowserCheck.noteChallenge("groupm-root.example", url = "http://groupm-root.example:8080/latest")
        assertEquals("http://groupm-root.example:8080/", BrowserCheck.checkPageFor("groupm-root.example"))
        assertEquals("https://groupm-unknown.example/", BrowserCheck.checkPageFor("www.groupm-unknown.example"))
    }
}
