/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.network

import android.content.Context
import dev.asfakul.yumeiro.FeatureFlags
import okhttp3.Dns
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicInteger

/**
 * NET-1 (A2): with no saved DNS choice the device's own DNS answers (no DNS-over-HTTPS round trip in front of
 * every new host). A saved Cloudflare choice is kept.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Net1SystemDnsDefaultTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @Before fun setUp() {
        Thread.getAllStackTraces().keys.filter { it.name == "yu-prefs" && it !== Thread.currentThread() }.forEach { it.join(10_000) }
        app.getSharedPreferences("mt_network", Context.MODE_PRIVATE).edit().clear().commit()
        NetworkSettings.resetForTests()
    }

    @After fun tearDown() {
        app.getSharedPreferences("mt_network", Context.MODE_PRIVATE).edit().clear().commit()
        SmartDns.system = Dns.SYSTEM; SmartDns.alternativeOverride = null
        NetworkSettings.resetForTests()
    }

    @Test fun theBuiltInDefaultIsOff() {
        assertFalse(FeatureFlags.Flag.CLOUDFLARE_DEFAULT_DNS.default)
    }

    @Test fun aFreshInstallUsesDeviceDnsAndNeverAsksDoh() {
        NetworkSettings.init(app)
        assertEquals(DnsConfig(), NetworkSettings.dns.value)
        val systemCalls = AtomicInteger(); val dohCalls = AtomicInteger()
        val addr = InetAddress.getByAddress(byteArrayOf(93, 184.toByte(), 216.toByte(), 34))
        SmartDns.system = Dns { systemCalls.incrementAndGet(); listOf(addr) }
        SmartDns.alternativeOverride = { _, _ -> dohCalls.incrementAndGet(); listOf(addr) }
        SmartDns.lookup("api.example.com")
        SmartDns.lookup("other.example.org")
        assertEquals(0, dohCalls.get())
        assertEquals(2, systemCalls.get())
    }

    @Test fun aSavedCloudflareChoiceIsKept() {
        val cf = DnsConfig(provider = DnsProvider.CLOUDFLARE, transport = DnsTransport.DOH, mode = DnsMode.ALWAYS)
        NetworkSettings.setDns(app, cf)
        NetworkSettings.resetForTests()
        NetworkSettings.init(app)
        assertEquals(DnsProvider.CLOUDFLARE, NetworkSettings.dns.value.provider)
        assertEquals(DnsMode.ALWAYS, NetworkSettings.dns.value.mode)
    }
}
