/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.app.Instrumentation
import android.content.Intent
import android.provider.Browser
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.download.ExternalDownloader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G8 D5 (external downloaders) on a device: what ADM, 1DM and the "Ask each time" chooser actually
 * receive. No download app is installed on the CI emulators, so an instrumentation monitor stands
 * in for the other app: it takes the exact Intent Android would deliver (package, class, link,
 * headers, file name) without starting anything. Without the monitor the not-installed path runs
 * for real and must give the user a message instead of a crash.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 26) // ActivityMonitor.onStartActivity
class ExternalDownloaderDeviceTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext.applicationContext
    private var monitor: Instrumentation.ActivityMonitor? = null
    @Volatile private var captured: Intent? = null
    private val url = "https://cdn.example.org/v/clip-720.mp4?sig=abc"
    private val headers = mapOf("Referer" to "https://site.example/watch/1", "User-Agent" to "YuTest/1.0", "Cookie" to "cf=1", "X-Token" to "t1")

    private fun capture() {
        captured = null
        monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult {
                captured = intent
                return Instrumentation.ActivityResult(0, null)
            }
        }.also { inst.addMonitor(it) }
    }

    @After fun tearDown() { monitor?.let { inst.removeMonitor(it) } }

    @Test fun a_admGetsLinkHeadersAndName() {
        assertTrue("ADM unexpectedly installed", ExternalDownloader.installed(ctx).none { it.pkg == "com.dv.adm" })
        capture()
        val err = ExternalDownloader.send(ctx, "com.dv.adm", url, headers, "Show S1 E3.mp4")
        val i = captured
        Log.i(TAG, "EXTDL_CASE a err=$err pkg=${i?.`package`} action=${i?.action} data=${i?.dataString} title=${i?.getStringExtra("title")}")
        assertNull(err)
        assertNotNull(i)
        assertEquals(Intent.ACTION_VIEW, i!!.action)
        assertEquals("com.dv.adm", i.`package`)
        assertEquals(url, i.dataString)
        assertTrue(i.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        val h = i.getBundleExtra(Browser.EXTRA_HEADERS)
        assertEquals("https://site.example/watch/1", h?.getString("Referer"))
        assertEquals("cf=1", h?.getString("Cookie"))
        assertEquals("Show S1 E3.mp4", i.getStringExtra("title"))
        assertNull("1DM-only extras sent to ADM", i.getStringExtra("extra_referer"))
        Log.i(TAG, "EXTDL_RESULT PASS a")
    }

    @Test fun b_oneDmGetsItsOwnExtras() {
        capture()
        val err = ExternalDownloader.send(ctx, "idm.internet.download.manager", url, headers, "Show S1 E3.mp4")
        val i = captured
        Log.i(TAG, "EXTDL_CASE b err=$err cls=${i?.component?.className} ref=${i?.getStringExtra("extra_referer")} name=${i?.getStringExtra("extra_filename")}")
        assertNull(err)
        assertEquals("idm.internet.download.manager.Downloader", i!!.component?.className)
        assertEquals("idm.internet.download.manager", i.component?.packageName)
        assertEquals("https://site.example/watch/1", i.getStringExtra("extra_referer"))
        assertEquals("YuTest/1.0", i.getStringExtra("extra_useragent"))
        assertEquals("cf=1", i.getStringExtra("extra_cookies"))
        assertEquals("Show S1 E3.mp4", i.getStringExtra("extra_filename"))
        val other = i.getBundleExtra("extra_headers")
        assertEquals("t1", other?.getString("X-Token"))
        assertFalse("Referer duplicated in extra_headers", other?.containsKey("Referer") == true)
        assertTrue(i.getBooleanExtra("secure_uri", false))
        Log.i(TAG, "EXTDL_RESULT PASS b")
    }

    @Test fun c_chooserWrapsAVideoIntent() {
        capture()
        val err = ExternalDownloader.send(ctx, ExternalDownloader.CHOOSER, url, headers, "Clip.mp4")
        val i = captured
        @Suppress("DEPRECATION") val inner = i?.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        Log.i(TAG, "EXTDL_CASE c err=$err action=${i?.action} inner=${inner?.action} type=${inner?.type} data=${inner?.dataString}")
        assertNull(err)
        assertEquals(Intent.ACTION_CHOOSER, i!!.action)
        assertEquals(Intent.ACTION_VIEW, inner?.action)
        assertEquals("video/*", inner?.type)
        assertEquals(url, inner?.dataString)
        Log.i(TAG, "EXTDL_RESULT PASS c")
    }

    @Test fun d_notInstalledGivesAMessage() {
        // No monitor: Android really looks for ADM, finds nothing, and the user gets a message.
        val err = runCatching { ExternalDownloader.send(ctx, "com.dv.adm", url, headers, "Clip.mp4") }
        Log.i(TAG, "EXTDL_CASE d threw=${err.exceptionOrNull()} msg=${err.getOrNull()}")
        assertTrue("send threw: ${err.exceptionOrNull()}", err.isSuccess)
        assertEquals("ADM isn't installed or can't open this link", err.getOrNull())
        val bad = ExternalDownloader.send(ctx, "com.dv.adm", "magnet:?xt=urn:btih:abc", headers, null)
        assertEquals("This link can't be opened in another app", bad)
        assertEquals("ADM can't save streaming (HLS) links", ExternalDownloader.unsupportedReason("com.dv.adm", isHls = true, isDash = false))
        assertNull(ExternalDownloader.unsupportedReason("idm.internet.download.manager", isHls = true, isDash = false))
        Log.i(TAG, "EXTDL_RESULT PASS d")
    }

    private companion object { const val TAG = "YuExtDlTest" }
}
