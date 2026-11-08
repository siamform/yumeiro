/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.network

import okhttp3.Dns
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.net.UnknownHostException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SmartDnsTest {
    private val ip = InetAddress.getByAddress("example.com", byteArrayOf(93, 184.toByte(), 216.toByte(), 34))
    private var systemCalls = 0
    private var altCalls = 0

    private fun setUp(systemWorks: Boolean, altWorks: Boolean = true, config: DnsConfig) {
        systemCalls = 0; altCalls = 0
        SmartDns.system = Dns { host -> systemCalls++; if (systemWorks) listOf(ip) else throw UnknownHostException(host) }
        SmartDns.alternativeOverride = { host, _ -> altCalls++; if (altWorks) listOf(ip) else throw UnknownHostException(host) }
        SmartDns.online = { true }
        NetworkSettings.setForTests(config)
    }

    @After fun reset() {
        SmartDns.system = Dns.SYSTEM; SmartDns.alternativeOverride = null; SmartDns.online = { Connectivity.online.value }
        NetworkSettings.setForTests(DnsConfig())
    }

    @Test fun systemWithoutAuto_neverUsesAlternative() {
        setUp(systemWorks = false, config = DnsConfig(auto = false))
        try { SmartDns.lookup("example.com"); fail() } catch (_: UnknownHostException) {}
        assertEquals(0, altCalls)
    }

    @Test fun fallbackMode_onlyAfterRealFailure() {
        setUp(systemWorks = true, config = DnsConfig(DnsProvider.CLOUDFLARE))
        SmartDns.lookup("example.com")
        assertEquals(0, altCalls)
        assertEquals(DnsSource.SYSTEM, SmartDns.lastSourceFor("example.com"))

        setUp(systemWorks = false, config = DnsConfig(DnsProvider.CLOUDFLARE))
        assertEquals(listOf(ip), SmartDns.lookup("example.com"))
        assertEquals(1, altCalls)
        assertEquals(DnsSource.ALTERNATIVE, SmartDns.lastSourceFor("example.com"))
        // Remembered failure: the next lookup goes straight to the alternative.
        SmartDns.lookup("example.com")
        assertEquals(1, systemCalls)
    }

    @Test fun alwaysMode_fallsBackToSystem() {
        setUp(systemWorks = true, altWorks = false, config = DnsConfig(DnsProvider.QUAD9, mode = DnsMode.ALWAYS))
        assertEquals(listOf(ip), SmartDns.lookup("example.com"))
        assertEquals(1, altCalls); assertEquals(1, systemCalls)
    }

    @Test fun bothFail_reportsBoth() {
        setUp(systemWorks = false, altWorks = false, config = DnsConfig(DnsProvider.GOOGLE))
        try { SmartDns.lookup("example.com"); fail() } catch (e: UnknownHostException) {
            assertTrue(e.message, e.message!!.contains("Google"))
        }
    }

    @Test fun auto_resolvesBlockedHostOverEncryptedDns_andRemembers() {
        setUp(systemWorks = false, config = DnsConfig())
        assertEquals(listOf(ip), SmartDns.lookup("blocked.example"))
        assertEquals(1, altCalls)
        assertTrue(SmartDns.isBypassed("blocked.example"))
        SmartDns.lookup("blocked.example")
        assertEquals(1, systemCalls) // remembered: straight to encrypted DNS
    }

    @Test fun auto_workingHostsStayOnSystemDns() {
        setUp(systemWorks = true, config = DnsConfig())
        assertEquals(listOf(ip), SmartDns.lookup("example.com"))
        assertEquals(0, altCalls)
        assertEquals(DnsSource.SYSTEM, SmartDns.lastSourceFor("example.com"))
    }

    @Test fun auto_sinkholeAnswerIsReplaced() {
        val sink = InetAddress.getByAddress("site.example", byteArrayOf(0, 0, 0, 0))
        setUp(systemWorks = true, config = DnsConfig())
        SmartDns.system = Dns { systemCalls++; listOf(sink) }
        assertEquals(listOf(ip), SmartDns.lookup("site.example"))
        assertTrue(SmartDns.isBypassed("site.example"))
    }

    @Test fun auto_lanNamesKeepPrivateAddresses() {
        val lan = InetAddress.getByAddress("nas.local", byteArrayOf(192.toByte(), 168.toByte(), 1, 5))
        setUp(systemWorks = true, config = DnsConfig())
        SmartDns.system = Dns { listOf(lan) }
        assertEquals(listOf(lan), SmartDns.lookup("nas.local"))
        assertEquals(0, altCalls)
    }

    @Test fun auto_rotatesProviders() {
        setUp(systemWorks = false, config = DnsConfig())
        val asked = mutableListOf<DnsProvider>()
        SmartDns.alternativeOverride = { _, c -> asked += c.provider; if (c.provider == DnsProvider.CLOUDFLARE) throw java.io.IOException("unreachable") else listOf(ip) }
        assertEquals(listOf(ip), SmartDns.lookup("rot.example"))
        assertEquals(listOf(DnsProvider.CLOUDFLARE, DnsProvider.QUAD9), asked)
    }

    @Test fun bogonRanges() {
        fun a(vararg b: Int) = InetAddress.getByAddress(b.map { it.toByte() }.toByteArray())
        assertTrue(SmartDns.isBogon(a(0, 0, 0, 0)))
        assertTrue(SmartDns.isBogon(a(127, 0, 0, 1)))
        assertTrue(SmartDns.isBogon(a(10, 1, 2, 3)))
        assertTrue(SmartDns.isBogon(a(100, 64, 0, 1)))
        assertTrue(!SmartDns.isBogon(a(104, 16, 0, 1)))
    }

    @Test fun blockingFailuresAreRecognised() {
        assertTrue(AutoBypassInterceptor.looksLikeBlocking(java.net.ConnectException("Failed to connect")))
        assertTrue(AutoBypassInterceptor.looksLikeBlocking(java.net.SocketException("Connection reset")))
        assertTrue(!AutoBypassInterceptor.looksLikeBlocking(java.net.SocketTimeoutException("timeout")))
    }

    @Test fun ipLiteralsSkipDns() {
        assertTrue(SmartDns.isIpLiteral("10.0.0.1"))
        assertTrue(SmartDns.isIpLiteral("2606:4700::1111"))
    }

    @Test fun dnsMessage_roundTrip() {
        val q = DnsMessage.query(0x1234, "example.com", 1)
        assertEquals(0x12, q[0].toInt() and 0xFF); assertEquals(0x34, q[1].toInt() and 0xFF)
        // Build an answer from the query: set QR bit, ANCOUNT=1, append a compressed A record.
        val resp = q.copyOf().also { it[2] = 0x81.toByte(); it[3] = 0x80.toByte(); it[7] = 1 } +
            byteArrayOf(0xC0.toByte(), 12, 0, 1, 0, 1, 0, 0, 0, 60, 0, 4, 93, 184.toByte(), 216.toByte(), 34)
        val a = DnsMessage.parse(resp, 0x1234, "example.com")
        assertEquals("93.184.216.34", a.addresses.single().hostAddress)
        assertEquals(60L, a.ttlSeconds)
    }
}
