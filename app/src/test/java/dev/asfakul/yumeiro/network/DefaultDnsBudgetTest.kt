/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.network

import okhttp3.Dns
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * ONLINE-INIT-1 (R19): Cloudflare is supporting infrastructure, so the built-in default resolver must not hold
 * startup when Cloudflare is slow or unreachable. Field run 45115735797 (pcap): a dead 1.1.1.1 DoH connection
 * held the first lookups 5.5 s. Here Cloudflare is a local server that accepts and never answers (real OkHttp
 * DnsOverHttps), system DNS works. Fail-before: the default waited the full 5 s read timeout (or more).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DefaultDnsBudgetTest {
    private val closers = ArrayList<Closeable>()
    private val publicIp = InetAddress.getByAddress("example.org", byteArrayOf(93, 184.toByte(), 216.toByte(), 34))

    @Before fun setUp() {
        okhttp3.OkHttp.initialize(RuntimeEnvironment.getApplication())
        // NET-1 (A2): device DNS is now the default; this test covers the optional Cloudflare default (flag on).
        dev.asfakul.yumeiro.FeatureFlags.set(RuntimeEnvironment.getApplication(), dev.asfakul.yumeiro.FeatureFlags.Flag.CLOUDFLARE_DEFAULT_DNS, true)
        NetworkSettings.resetForTests(); SmartDns.resetEnginesForTests()
        SmartDns.online = { true }
        val ss = ServerSocket(0, 50, InetAddress.getLoopbackAddress()); closers += ss
        val held = CopyOnWriteArrayList<Socket>(); closers += Closeable { held.forEach { runCatching { it.close() } } }
        thread(isDaemon = true) { while (!ss.isClosed) runCatching { held += ss.accept() } }
        SmartDns.dohEndpoint = { "http://127.0.0.1:${ss.localPort}/dns-query" to listOf("127.0.0.1") }
        SmartDns.system = Dns { listOf(publicIp) }
    }

    @After fun tearDown() {
        closers.forEach { runCatching { it.close() } }
        SmartDns.system = Dns.SYSTEM; SmartDns.dohEndpoint = { it.dohUrl!! to it.bootstrap }
        SmartDns.resetEnginesForTests(); SmartDns.online = { Connectivity.online.value }
        NetworkSettings.resetForTests()
        dev.asfakul.yumeiro.FeatureFlags.set(RuntimeEnvironment.getApplication(), dev.asfakul.yumeiro.FeatureFlags.Flag.CLOUDFLARE_DEFAULT_DNS, dev.asfakul.yumeiro.FeatureFlags.Flag.CLOUDFLARE_DEFAULT_DNS.default)
    }

    private fun timedLookup(): Pair<Long, List<InetAddress>> {
        val t0 = System.nanoTime()
        val r = SmartDns.lookup("example.org")
        return (System.nanoTime() - t0) / 1_000_000 to r
    }

    @Test fun builtInDefaultFallsBackToSystemDnsWithinTheShortBudget() {
        val def = NetworkSettings.freshDefault(RuntimeEnvironment.getApplication())
        assertTrue(def.quick); assertEquals(DnsProvider.CLOUDFLARE, def.provider)
        NetworkSettings.setForTests(def)
        val (ms, r) = timedLookup()
        assertEquals(listOf(publicIp), r)
        assertTrue("default lookup took $ms ms", ms < SmartDns.FALLBACK_ATTEMPT_MS + 1_000)
        // Cloudflare is then skipped for a while: the next lookup does not wait again.
        val (ms2, _) = timedLookup()
        assertTrue("second lookup took $ms2 ms", ms2 < 500)
    }

    @Test fun aResolverTheUserSavesKeepsTheNormalTimeouts() {
        NetworkSettings.setDns(RuntimeEnvironment.getApplication(), NetworkSettings.freshDefault(RuntimeEnvironment.getApplication()))
        assertFalse(NetworkSettings.dns.value.quick)
        val (ms, r) = timedLookup()
        assertEquals(listOf(publicIp), r)
        assertTrue("saved choice gave up after $ms ms", ms >= 4_500)
    }
}
