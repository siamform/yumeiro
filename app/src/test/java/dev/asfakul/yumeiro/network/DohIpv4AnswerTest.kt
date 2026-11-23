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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.Inet4Address
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * LIVE-DNS-1 (probe 45143967998, emulated 3G): DnsOverHttps returned raw.githubusercontent.com with IPv6 addresses
 * only (its A request timed out, the AAAA one arrived). The app cached that for 120 s on a link with no IPv6, so
 * both Live TV playlists failed before IPv4 was ever tried.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DohIpv4AnswerTest {
    private val v6 = InetAddress.getByName("2606:50c0:8000::154")
    private val v4 = InetAddress.getByAddress("raw.githubusercontent.com", byteArrayOf(185.toByte(), 199.toByte(), 108, 133.toByte()))
    private var clock = 1_000_000L
    private val host = "raw.githubusercontent.com"

    @Before fun setUp() {
        SmartDns.clearCache()
        SmartDns.system = Dns { h -> throw UnknownHostException(h) } // only the encrypted resolver answers
        SmartDns.online = { true }
        SmartDns.now = { clock }
        // The fresh-install default since OWN-7: Cloudflare over HTTPS, Always.
        NetworkSettings.setForTests(DnsConfig(DnsProvider.CLOUDFLARE, DnsTransport.DOH, DnsMode.ALWAYS))
        SmartDns.dohFactory = { _ -> Dns { listOf(v6) } } // the AAAA half came back, the A half did not
    }

    @After fun reset() {
        SmartDns.system = Dns.SYSTEM; SmartDns.dohFactory = null; SmartDns.dohV4Factory = null
        SmartDns.online = { Connectivity.online.value }; SmartDns.now = System::currentTimeMillis
        SmartDns.clearCache()
        NetworkSettings.resetForTests()
        NetworkSettings.setForTests(DnsConfig())
    }

    @Test fun anIpv6OnlyAnswerAsksForIpv4Again() {
        var asked = 0
        SmartDns.dohV4Factory = { _ -> Dns { asked++; listOf(v4) } }
        val got = SmartDns.lookup(host)
        assertEquals(1, asked)
        assertTrue("IPv4 must be in the answer (got $got)", got.any { it is Inet4Address })
        assertEquals(listOf(v4, v6), got)
        // A full answer is cached as before.
        clock += 60_000
        assertEquals(listOf(v4, v6), SmartDns.lookup(host))
        assertEquals(1, asked)
    }

    @Test fun whenIpv4StillFailsTheAnswerIsKeptOnlyBriefly() {
        var asked = 0
        SmartDns.dohV4Factory = { _ -> Dns { h -> asked++; throw UnknownHostException(h).apply { initCause(SocketTimeoutException("timeout")) } } }
        assertEquals(listOf(v6), SmartDns.lookup(host))
        assertEquals(1, asked)
        clock += SmartDns.PARTIAL_ANSWER_MS + 1
        SmartDns.dohV4Factory = { _ -> Dns { asked++; listOf(v4) } }
        assertEquals(listOf(v4, v6), SmartDns.lookup(host))
        assertEquals("the next lookup after the short hold asked again", 2, asked)
    }

    @Test fun aHostWithNoARecordsKeepsItsTtl() {
        var asked = 0
        SmartDns.dohV4Factory = { _ -> Dns { h -> asked++; throw UnknownHostException("$h: no A records") } }
        assertEquals(listOf(v6), SmartDns.lookup(host))
        clock += 60_000
        assertEquals(listOf(v6), SmartDns.lookup(host))
        assertEquals("an IPv6-only host is not asked again inside its TTL", 1, asked)
    }

    @Test fun anAnswerWithIpv4IsUnchanged() {
        var asked = 0
        SmartDns.dohFactory = { _ -> Dns { listOf(v6, v4) } }
        SmartDns.dohV4Factory = { _ -> Dns { asked++; listOf(v4) } }
        assertEquals(listOf(v6, v4), SmartDns.lookup(host))
        assertEquals(0, asked)
    }
}
