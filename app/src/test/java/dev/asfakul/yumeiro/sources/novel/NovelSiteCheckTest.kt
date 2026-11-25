/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.novel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelSiteCheckTest {
    @Test fun `LNReader check errors are recognised, ordinary errors are not`() {
        assertTrue(NovelSiteCheck.isSiteCheck("Royal Road's bot check could not be passed. Open a novel from Royal Road in WebView, wait for it to load, then try again."))
        assertTrue(NovelSiteCheck.isSiteCheck("Captcha error, please open in webview"))
        assertTrue(NovelSiteCheck.isSiteCheck("HTTP 403 Forbidden"))
        assertFalse(NovelSiteCheck.isSiteCheck("Royal Road returned no details"))
        assertFalse(NovelSiteCheck.isSiteCheck("timeout"))
        assertFalse(NovelSiteCheck.isSiteCheck(null))
    }

    @Test fun `the sentence names the site and no WebView button`() {
        val m = NovelSiteCheck.message("Royal Road")
        assertTrue(m.startsWith("Royal Road asks for a quick check") && !m.contains("WebView"))
    }
}
