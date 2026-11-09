/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.download

import android.app.Application
import android.provider.Browser
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExternalDownloaderTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val headers = mapOf("Referer" to "https://site.example/", "User-Agent" to "UA/1", "Cookie" to "a=b", "X-Token" to "t")

    @Test fun oneDmGetsItsOwnExtras() {
        val ex = ExternalDownloader.extras("idm.internet.download.manager.plus", headers, "Show E1.mp4")
        assertEquals("https://site.example/", ex["extra_referer"])
        assertEquals("UA/1", ex["extra_useragent"])
        assertEquals("a=b", ex["extra_cookies"])
        assertEquals("Show E1.mp4", ex["extra_filename"])
        assertEquals(mapOf("X-Token" to "t"), ex["extra_headers"])
        assertEquals(headers, ex[Browser.EXTRA_HEADERS])
    }

    @Test fun otherAppsGetStandardHeadersOnly() {
        val ex = ExternalDownloader.extras("com.dv.adm", headers, null)
        assertEquals(headers, ex[Browser.EXTRA_HEADERS])
        assertNull(ex["extra_referer"])
    }

    @Test fun streamSupportRules() {
        assertNull(ExternalDownloader.unsupportedReason("idm.internet.download.manager", isHls = true, isDash = false))
        assertNotNull(ExternalDownloader.unsupportedReason("com.dv.adm", isHls = true, isDash = false))
        assertNull(ExternalDownloader.unsupportedReason("com.dv.adm", isHls = false, isDash = false))
        assertNotNull(ExternalDownloader.unsupportedReason(ExternalDownloader.CHOOSER, isHls = false, isDash = true))
        assertNotNull(ExternalDownloader.unsupportedReason(ExternalDownloader.IN_APP, isHls = false, isDash = false))
    }

    @Test fun fileNamesAreSafe() {
        assertEquals("Show Episode 3.mp4", ExternalDownloader.fileName("Show", "Episode 3", false))
        assertEquals("a b c.mp4", ExternalDownloader.fileName("a/b:c", null, true))
        assertEquals("video.mp4", ExternalDownloader.fileName("", null, false))
    }

    @Test fun sendOpensTheChosenAppWithHeaders() {
        val err = ExternalDownloader.send(context, "com.dv.adm", "https://cdn.example/v.mp4", headers, "v.mp4")
        // Robolectric has no such app: the intent is still recorded, or a friendly error comes back.
        val started = shadowOf(context).nextStartedActivity
        if (started != null) {
            assertEquals("com.dv.adm", started.`package`)
            assertEquals("https://cdn.example/v.mp4", started.dataString)
            assertTrue(started.getBundleExtra(Browser.EXTRA_HEADERS)!!.getString("Referer") == "https://site.example/")
        } else assertNotNull(err)
        assertNotNull(ExternalDownloader.send(context, "com.dv.adm", "file:///sdcard/x", emptyMap(), null))
        assertFalse(ExternalDownloader.installed(context).any { it.pkg == "com.dv.adm" })
    }
}
