/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.stream

import android.app.Application
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** CQ-97..104: a WebView handed to [PageKeep] never keeps file or content:// access, and release forgets it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PageKeepHardeningTest {
    @Test fun keptPageHasFileAndContentAccessOff() {
        val web = WebView(ApplicationProvider.getApplicationContext<Application>())
        web.settings.allowFileAccess = true
        web.settings.allowContentAccess = true
        val keep = PageKeep("https://site.example/watch")
        keep.webView = web
        assertSame(web, keep.webView)
        assertSame(web, keep.webViewForTest)
        assertTrue(keep.alive)
        assertFalse(web.settings.allowFileAccess)
        assertFalse(web.settings.allowContentAccess)
        keep.release()
        assertFalse(keep.alive)
        assertNull(keep.webView)
    }
}
