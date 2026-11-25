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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicInteger

/**
 * OWN-7 / OWN-2 (owner list, 2.1.0): with no saved DNS choice (fresh install, skipped setup tour, or an update from
 * a version where the user kept the default) the app must use Cloudflare DNS-over-HTTPS, so online features work
 * without the setup guide's "Set up DNS" step. A saved choice is kept; the kill switch brings back System DNS.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Own7CloudflareDefaultTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @Before fun setUp() {
        Thread.getAllStackTraces().keys.filter { it.name == "yu-prefs" && it !== Thread.currentThread() }.forEach { it.join(10_000) }
        app.getSharedPreferences("mt_network", Context.MODE_PRIVATE).edit().clear().commit()
        NetworkSettings.resetForTests()
        // NET-1 (A2): Cloudflare is no longer the default; these tests cover the opt-in path behind the flag.
        FeatureFlags.set(app, FeatureFlags.Flag.CLOUDFLARE_DEFAULT_DNS, true)
    }

    @After fun tearDown() {
        FeatureFlags.set(app, FeatureFlags.Flag.CLOUDFLARE_DEFAULT_DNS, FeatureFlags.Flag.CLOUDFLARE_DEFAULT_DNS.default)
        app.getSharedPreferences("mt_network", Context.MODE_PRIVATE).edit().clear().commit()
        SmartDns.system = Dns.SYSTEM; SmartDns.alternativeOverride = null
        NetworkSettings.resetForTests()
    }

    @Test fun aFreshInstallUsesCloudflareDoh() {
        NetworkSettings.init(app)
        val c = NetworkSettings.dns.value
        assertEquals(DnsProvider.CLOUDFLARE, c.provider)
        assertEquals(DnsTransport.DOH, c.transport)
        assertEquals(DnsMode.ALWAYS, c.mode)
    }

    @Test fun aSavedChoiceIsKept() {
        NetworkSettings.setDns(app, DnsConfig()) // the user picked System DNS in Settings → Network
        NetworkSettings.resetForTests()
        NetworkSettings.init(app)
        assertEquals(DnsConfig(), NetworkSettings.dns.value)
    }

    @Test fun theKillSwitchBringsBackSystemDns() {
        FeatureFlags.set(app, FeatureFlags.Flag.CLOUDFLARE_DEFAULT_DNS, false)
        NetworkSettings.init(app)
        assertEquals(DnsConfig(), NetworkSettings.dns.value)
    }

    @Test fun publicNamesGoToCloudflareLanNamesAndOutagesToSystem() {
        val systemCalls = AtomicInteger(); val dohCalls = AtomicInteger()
        val addr = InetAddress.getByAddress(byteArrayOf(93, 184.toByte(), 216.toByte(), 34))
        SmartDns.system = Dns { systemCalls.incrementAndGet(); listOf(addr) }
        var dohUp = true
        SmartDns.alternativeOverride = { _, cfg ->
            dohCalls.incrementAndGet()
            assertEquals(DnsProvider.CLOUDFLARE, cfg.provider)
            if (!dohUp) throw java.io.IOException("cloudflare unreachable")
            listOf(addr)
        }
        NetworkSettings.init(app)
        SmartDns.lookup("api.example.com")
        assertEquals(1 to 0, dohCalls.get() to systemCalls.get())
        SmartDns.lookup("jellyfin.local")
        assertEquals("LAN name: system DNS", 1 to 1, dohCalls.get() to systemCalls.get())
        dohUp = false
        SmartDns.lookup("other.example.com")
        assertEquals("Cloudflare down: system DNS is the safety net", 2 to 2, dohCalls.get() to systemCalls.get())
    }
}
