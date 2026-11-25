/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.network.DnsConfig
import dev.asfakul.yumeiro.network.NetworkSettings
import dev.asfakul.yumeiro.network.SmartDns
import dev.asfakul.yumeiro.online.ExtensionCheckWindows
import dev.asfakul.yumeiro.online.ExtensionSetup
import dev.asfakul.yumeiro.online.web.BrowserCheck
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/**
 * Group A on a real device (A2 device DNS, A3 needs setup, A4 firewall note, A5 quiet checks): the same rules the
 * JVM tests pin, run on the device's real resolver, real OkHttp responses and real exception text.
 */
@RunWith(AndroidJUnit4::class)
class GroupAQuietDeviceTest {
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(s: String) = Log.i("YuGroupA", s)

    @After fun tearDown() {
        SmartDns.system = Dns.SYSTEM; SmartDns.alternativeOverride = null
    }

    @Test fun a2_freshInstallUsesTheDeviceResolver() {
        ctx.getSharedPreferences("mt_network", Context.MODE_PRIVATE).edit().clear().commit()
        NetworkSettings.resetForTests()
        NetworkSettings.init(ctx)
        assertEquals(DnsConfig(), NetworkSettings.dns.value)
        val doh = AtomicInteger()
        SmartDns.alternativeOverride = { _, _ -> doh.incrementAndGet(); emptyList() }
        val addrs = SmartDns.lookup("example.com")
        log("A2 example.com -> $addrs doh=${doh.get()}")
        assertTrue("the device resolver answered", addrs.isNotEmpty())
        assertEquals("no DNS-over-HTTPS by default", 0, doh.get())
        NetworkSettings.resetForTests()
    }

    @Test fun a3_anExtensionWithNoServerNeedsSetup() {
        val msg = runCatching { "".toHttpUrl() }.exceptionOrNull()?.message
        log("A3 real OkHttp message: $msg")
        assertTrue(ExtensionSetup.needsSetup(true, "", 3, msg))
        assertTrue(!ExtensionSetup.needsSetup(true, "https://x.example", 2, "Unable to resolve host \"x.example\""))
    }

    @Test fun a4_aRateLimitIsNotCalledAFirewallBlock() {
        val server = MockWebServer()
        server.enqueue(MockResponse.Builder().code(429).addHeader("Server", "cloudflare").addHeader("CF-RAY", "8a-DAC")
            .body("<html><title>Access denied</title>error code: 1015</html>").build())
        server.enqueue(MockResponse.Builder().code(403).addHeader("Server", "cloudflare").addHeader("CF-RAY", "8a-DAC")
            .body("<div id=\"cf-error-details\">Sorry, you have been blocked</div> error code: 1020").build())
        server.start()
        try {
            val client = OkHttpClient()
            fun verdict() = client.newCall(Request.Builder().url(server.url("/")).build()).execute().use {
                BrowserCheck.classify(it.code, it.header("cf-mitigated"), it.header("Server"), it.header("CF-RAY") != null, it.body?.string().orEmpty())
            }
            val first = verdict(); val second = verdict()
            log("A4 429/1015 -> $first, 403/1020 -> $second")
            assertEquals(BrowserCheck.Verdict.RATE_LIMITED, first)
            assertEquals(BrowserCheck.Verdict.BLOCKED, second)
        } finally { server.close() }
    }

    @Test fun a5_anExtensionCheckWindowIsNeverPutOverTheScreen() {
        for (p in ExtensionCheckWindows.Page.entries) for (t in listOf(0L, 7_000L, 25_000L)) {
            assertTrue("$p at $t ms", ExtensionCheckWindows.step(p, t, mayAsk = true, quiet = true) != ExtensionCheckWindows.Step.REVEAL)
        }
        assertEquals(ExtensionCheckWindows.Step.HAND_OFF, ExtensionCheckWindows.step(ExtensionCheckWindows.Page.HUMAN, 0, mayAsk = true, quiet = true))
        assertTrue(FeatureFlags.on(ctx, FeatureFlags.Flag.QUIET_EXT_CHECKS))
    }
}
