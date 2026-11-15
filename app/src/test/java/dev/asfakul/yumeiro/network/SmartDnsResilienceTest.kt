/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.network

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import okhttp3.Dns
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** G12 X11-1, X11-2, X11-3. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SmartDnsResilienceTest {
    private val ip = InetAddress.getByAddress("site.example", byteArrayOf(93, 184.toByte(), 216.toByte(), 34))
    private var clock = 1_000_000L

    @After fun reset() {
        SmartDns.system = Dns.SYSTEM; SmartDns.alternativeOverride = null; SmartDns.dohFactory = null
        SmartDns.online = { Connectivity.online.value }; SmartDns.now = System::currentTimeMillis
        NetworkSettings.resetForTests()
        NetworkSettings.setForTests(DnsConfig())
    }

    /** How OkHttp's DnsOverHttps reports a request that never reached the resolver. */
    private fun dohTimeout(host: String) = UnknownHostException(host).apply { initCause(SocketTimeoutException("timeout")) }

    /** X11-3: Cloudflare unreachable must move on to Quad9 (before: stopped, "blocked" host failed). */
    @Test fun unreachableDohProviderRotatesToTheNext() {
        val asked = mutableListOf<DnsProvider>()
        SmartDns.system = Dns { h -> throw UnknownHostException(h) }  // the network's DNS blocks it
        SmartDns.online = { true }
        SmartDns.now = { clock }
        NetworkSettings.setForTests(DnsConfig())
        SmartDns.dohFactory = { p -> Dns { h -> asked += p; if (p == DnsProvider.CLOUDFLARE) throw dohTimeout(h) else listOf(ip) } }
        assertEquals(listOf(ip), SmartDns.lookup("site.example"))
        assertEquals(listOf(DnsProvider.CLOUDFLARE, DnsProvider.QUAD9), asked)
    }

    /** A real "no such name" from a resolver still stops the rotation (another won't know better). */
    @Test fun nxdomainStillStops() {
        val asked = mutableListOf<DnsProvider>()
        SmartDns.system = Dns { h -> throw UnknownHostException(h) }
        SmartDns.online = { true }
        NetworkSettings.setForTests(DnsConfig())
        SmartDns.dohFactory = { p -> Dns { h -> asked += p; throw UnknownHostException("$h: NXDOMAIN") } }
        runCatching { SmartDns.lookup("nope.example") }
        assertEquals(listOf(DnsProvider.CLOUDFLARE), asked)
    }

    /** X11-1: once every resolver timed out, the next lookup must not wait for them again. */
    @Test fun unreachableResolversAreSkippedForAWhile() {
        var encrypted = 0
        var system = 0
        SmartDns.system = Dns { system++; listOf(ip) }
        SmartDns.online = { true }
        SmartDns.now = { clock }
        NetworkSettings.setForTests(DnsConfig())
        SmartDns.alternativeOverride = { _, _ -> encrypted++; throw IOException("connect timed out") }
        SmartDns.markBlocked("site.example")
        assertEquals(listOf(ip), SmartDns.lookup("site.example"))
        assertEquals(3, encrypted)
        assertEquals(listOf(ip), SmartDns.lookup("site.example"))
        assertEquals("second lookup asked no unreachable resolver", 3, encrypted)
        assertEquals(2, system)
        // After the pause, or on a new network, they are tried again.
        SmartDns.onNetworkChanged()
        SmartDns.lookup("site.example")
        assertEquals(6, encrypted)
    }

    /** X11-2: a request made before any screen loaded the settings must use the saved DNS choice. */
    @Test fun firstLookupUsesSavedSettings() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        app.getSharedPreferences("mt_network", Context.MODE_PRIVATE).edit()
            .putString("dns_provider", "CLOUDFLARE").putString("dns_mode", "ALWAYS").commit()
        NetworkSettings.resetForTests()
        var alt = 0
        SmartDns.system = Dns { listOf(ip) }
        SmartDns.alternativeOverride = { _, _ -> alt++; listOf(ip) }
        NetworkSettings.attach(app)
        SmartDns.lookup("site.example")
        assertEquals("the saved 'always Cloudflare' choice answered", 1, alt)
    }
}
