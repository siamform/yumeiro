/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.network

import dev.asfakul.yumeiro.FeatureFlags
import okhttp3.Dns
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * NBUG-6 (ADR-046) automatic DNS fallback on a mock blackholed network: the hard limits of the
 * 2026-11-19 tenth-cycle prompt §6, measured, not assumed.
 *  - The "system DNS" here sends a real DNS query over UDP to a local server that drops it (no answer
 *    ever), with a 1.2 s socket timeout standing in for the OS resolver's timeout, and then reports
 *    the timeout the way Android does (UnknownHostException, "Try again").
 *  - 1.1.1.1 is replaced by a local TCP server that accepts and never answers (a blackholed resolver),
 *    9.9.9.9 by a local DoH server that answers. Both run through OkHttp's real DnsOverHttps built by
 *    SmartDns with the fallback's 3.5 s budget, so the budget itself is what is measured.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Nbug6FallbackTest {
    private val publicIp = byteArrayOf(93, 184.toByte(), 216.toByte(), 34)
    private val closers = ArrayList<Closeable>()
    private val systemCalls = AtomicInteger()

    @Before fun setUp() {
        okhttp3.OkHttp.initialize(RuntimeEnvironment.getApplication())
        NetworkSettings.resetForTests()
        SmartDns.resetEnginesForTests()
        SmartDns.fallbackAttempts.set(0); SmartDns.fallbackMaxConcurrent.set(0)
        SmartDns.online = { true }
    }

    @After fun tearDown() {
        closers.forEach { runCatching { it.close() } }
        SmartDns.system = Dns.SYSTEM; SmartDns.alternativeOverride = null; SmartDns.dohFactory = null
        SmartDns.dohEndpoint = { it.dohUrl!! to it.bootstrap }
        SmartDns.resetEnginesForTests()
        SmartDns.online = { Connectivity.online.value }; SmartDns.now = System::currentTimeMillis
        FeatureFlags.set(RuntimeEnvironment.getApplication(), FeatureFlags.Flag.DNS_FALLBACK, true)
        NetworkSettings.resetForTests()
    }

    // region local servers

    /** A UDP "DNS server" that reads every query and never answers. */
    private fun droppingDns(): Int {
        val s = DatagramSocket(0, InetAddress.getLoopbackAddress()); closers += s
        thread(isDaemon = true) { val buf = ByteArray(512); while (!s.isClosed) runCatching { s.receive(DatagramPacket(buf, buf.size)) } }
        return s.localPort
    }

    /** System DNS on a network whose DNS drops every packet: a real query, no reply, an OS-style timeout. */
    private fun blackholedSystem(osTimeoutMs: Int = 1_200) {
        val port = droppingDns()
        SmartDns.system = Dns { host ->
            systemCalls.incrementAndGet()
            DatagramSocket().use { c ->
                c.soTimeout = osTimeoutMs
                val q = query(host, 1)
                c.send(DatagramPacket(q, q.size, InetAddress.getLoopbackAddress(), port))
                try { c.receive(DatagramPacket(ByteArray(512), 512)) } catch (e: SocketTimeoutException) {
                    throw UnknownHostException("Unable to resolve host \"$host\": Try again (EAI_AGAIN)").apply { initCause(e) }
                }
            }
            error("the dropping server answered")
        }
    }

    /** A resolver that accepts the connection and never sends a byte. */
    private fun blackholedDoh(): String {
        val ss = ServerSocket(0, 50, InetAddress.getLoopbackAddress()); closers += ss
        val held = CopyOnWriteArrayList<Socket>()
        closers += Closeable { held.forEach { runCatching { it.close() } } }
        thread(isDaemon = true) { while (!ss.isClosed) runCatching { held += ss.accept() } }
        return "http://127.0.0.1:${ss.localPort}/dns-query"
    }

    /** A minimal RFC 8484 GET server: A queries get [publicIp], AAAA queries get no records. */
    private fun workingDoh(hits: AtomicInteger): String {
        val ss = ServerSocket(0, 50, InetAddress.getLoopbackAddress()); closers += ss
        thread(isDaemon = true) {
            while (!ss.isClosed) {
                val sock = runCatching { ss.accept() }.getOrNull() ?: continue
                thread(isDaemon = true) { sock.use { serve(it, hits) } }
            }
        }
        return "http://127.0.0.1:${ss.localPort}/dns-query"
    }

    private fun serve(sock: Socket, hits: AtomicInteger) {
        val input = sock.getInputStream().bufferedReader(Charsets.ISO_8859_1)
        while (true) {
            val line = input.readLine() ?: return
            if (line.isEmpty()) continue
            val target = line.split(' ').getOrNull(1) ?: return
            while (input.readLine()?.isNotEmpty() == true) { /* headers */ }
            hits.incrementAndGet()
            val dns = target.substringAfter("dns=", "").substringBefore('&')
            val body = answer(Base64.getUrlDecoder().decode(dns))
            val out = sock.getOutputStream()
            out.write("HTTP/1.1 200 OK\r\nContent-Type: application/dns-message\r\nContent-Length: ${body.size}\r\nCache-Control: max-age=60\r\n\r\n".toByteArray())
            out.write(body); out.flush()
        }
    }

    private fun query(host: String, type: Int): ByteArray {
        val b = java.io.ByteArrayOutputStream()
        b.write(byteArrayOf(0x12, 0x34, 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 0))
        host.split('.').forEach { b.write(it.length); b.write(it.toByteArray()) }
        b.write(0); b.write(byteArrayOf(0, type.toByte(), 0, 1))
        return b.toByteArray()
    }

    private fun answer(q: ByteArray): ByteArray {
        val qtype = ((q[q.size - 4].toInt() and 0xFF) shl 8) or (q[q.size - 3].toInt() and 0xFF)
        val isA = qtype == 1
        val b = java.io.ByteArrayOutputStream()
        b.write(q, 0, 2) // id
        b.write(byteArrayOf(0x81.toByte(), 0x80.toByte(), 0, 1, 0, if (isA) 1 else 0, 0, 0, 0, 0))
        b.write(q, 12, q.size - 12) // the question as asked
        if (isA) {
            b.write(byteArrayOf(0xC0.toByte(), 0x0C, 0, 1, 0, 1, 0, 0, 0, 60, 0, 4))
            b.write(publicIp)
        }
        return b.toByteArray()
    }

    // endregion

    private fun endpoints(cloudflare: String, quad9: String, google: String? = null) {
        SmartDns.dohEndpoint = { p ->
            when (p) {
                DnsProvider.CLOUDFLARE -> cloudflare to listOf("127.0.0.1")
                DnsProvider.QUAD9 -> quad9 to listOf("127.0.0.1")
                else -> (google ?: error("$p is not in the fallback chain")) to listOf("127.0.0.1")
            }
        }
        SmartDns.resetEnginesForTests()
    }

    private fun ms(block: () -> Unit): Long { val t = System.nanoTime(); block(); return (System.nanoTime() - t) / 1_000_000 }

    @Test fun theSettingIsOffByDefault() {
        assertFalse("Automatic DNS fallback defaults to Off", DnsConfig().fallback)
    }

    @Test fun offMeansSystemDnsOnlyAndNoChain() {
        blackholedSystem(osTimeoutMs = 200)
        endpoints(blackholedDoh(), workingDoh(AtomicInteger()))
        NetworkSettings.setForTests(DnsConfig(auto = false, fallback = false))
        val r = runCatching { SmartDns.lookup("nbug6-off.com") }
        assertTrue(r.exceptionOrNull() is UnknownHostException)
        assertEquals("no resolver asked when Off", 0, SmartDns.fallbackAttempts.get())
    }

    @Test fun aHealthyNetworkNeverFiresTheFallback() {
        SmartDns.system = Dns { h -> systemCalls.incrementAndGet(); listOf(InetAddress.getByAddress(h, publicIp)) }
        val q9 = AtomicInteger()
        endpoints(blackholedDoh(), workingDoh(q9))
        NetworkSettings.setForTests(DnsConfig(fallback = true))
        repeat(20) { SmartDns.lookup("healthy$it.com") }
        assertEquals(20, systemCalls.get())
        assertEquals("the chain is never walked on a healthy network", 0, SmartDns.fallbackAttempts.get())
        assertEquals(0, q9.get())
        assertNull(SmartDns.sessionResolver)
    }

    @Test fun blackholedNetworkFallsBackWithinTheBudgetOnceThenUsesTheSessionResolver() {
        blackholedSystem()
        val q9 = AtomicInteger()
        endpoints(blackholedDoh(), workingDoh(q9))
        NetworkSettings.setForTests(DnsConfig(fallback = true))

        var first: List<InetAddress> = emptyList()
        val firstMs = ms { first = SmartDns.lookup("nbug6-fallback.com") }
        println("NBUG6_FALLBACK firstMs=$firstMs attempts=${SmartDns.fallbackAttempts.get()} session=${SmartDns.sessionResolver}")
        assertEquals(InetAddress.getByAddress(publicIp), first.first().let { InetAddress.getByAddress(it.address) })
        assertEquals("system, then 1.1.1.1 (dead), then 9.9.9.9", 2, SmartDns.fallbackAttempts.get())
        assertTrue("the dead resolver costs its full 3.5 s budget: $firstMs ms", firstMs >= SmartDns.FALLBACK_ATTEMPT_MS)
        assertTrue("system timeout + one 3.5 s attempt + the answer, never 15 s: $firstMs ms", firstMs < 1_200 + SmartDns.FALLBACK_ATTEMPT_MS + 1_500)
        assertEquals(DnsProvider.QUAD9, SmartDns.sessionResolver)

        val sysBefore = systemCalls.get()
        val secondMs = ms { SmartDns.lookup("another-host.com") }
        println("NBUG6_FALLBACK secondMs=$secondMs")
        assertEquals("the session resolver answers: system DNS is not asked again", sysBefore, systemCalls.get())
        assertEquals("no chain walk for the second request", 2, SmartDns.fallbackAttempts.get())
        assertTrue("the timeout is paid once, not per request: $secondMs ms", secondMs < 1_000)

        SmartDns.onNetworkChanged()
        assertNull("a new network starts again from system DNS", SmartDns.sessionResolver)
    }

    @Test fun everythingDeadFailsWithinTheBoundAndAsksAtMostThreeResolvers() {
        blackholedSystem()
        endpoints(blackholedDoh(), blackholedDoh())
        NetworkSettings.setForTests(DnsConfig(fallback = true))
        var error: Throwable? = null
        val t = ms { error = runCatching { SmartDns.lookup("nbug6-dead.com") }.exceptionOrNull() }
        println("NBUG6_FALLBACK allDeadMs=$t attempts=${SmartDns.fallbackAttempts.get()}")
        assertTrue(error is UnknownHostException)
        assertEquals("system + 2 = 3 resolvers at most", 2, SmartDns.fallbackAttempts.get())
        assertTrue("1.2 + 3.5 + 3.5 s, not 15 s: $t ms", t < 1_200 + 2 * SmartDns.FALLBACK_ATTEMPT_MS + 1_500)
        assertNull(SmartDns.sessionResolver)
        // Both resolvers are now marked down for 2 min: the next lookup does not wait for them again.
        val again = ms { runCatching { SmartDns.lookup("nbug6-dead2.com") } }
        assertTrue("dead resolvers are skipped: $again ms (only the OS timeout remains)", again < 1_200 + 1_000)
    }

    @Test fun concurrentRequestsNeverWalkTheChainInParallel() {
        blackholedSystem()
        endpoints(blackholedDoh(), workingDoh(AtomicInteger()))
        NetworkSettings.setForTests(DnsConfig(fallback = true))
        val done = CountDownLatch(4)
        val results = CopyOnWriteArrayList<Boolean>()
        repeat(4) { i -> thread { results += runCatching { SmartDns.lookup("parallel$i.com") }.isSuccess; done.countDown() } }
        assertTrue(done.await(30, TimeUnit.SECONDS))
        assertEquals(listOf(true, true, true, true), results.toList())
        assertEquals("one resolver at a time across all threads", 1, SmartDns.fallbackMaxConcurrent.get())
        assertEquals(DnsProvider.QUAD9, SmartDns.sessionResolver)
    }

    @Test fun oneBlockedNameIsRememberedForThatHostOnly() {
        // "No address associated with hostname" (EAI_NODATA) in 10 ms: the network refuses one name, DNS itself works.
        SmartDns.system = Dns { h -> systemCalls.incrementAndGet(); if (h == "blocked-one.com") throw UnknownHostException("Unable to resolve host \"$h\": No address associated with hostname") else listOf(InetAddress.getByAddress(h, publicIp)) }
        endpoints(blackholedDoh(), workingDoh(AtomicInteger()))
        NetworkSettings.setForTests(DnsConfig(fallback = true))
        SmartDns.lookup("blocked-one.com")
        assertNull("a single blocked name does not move the whole session", SmartDns.sessionResolver)
        assertTrue(SmartDns.isBypassed("blocked-one.com"))
        SmartDns.lookup("fine.com")
        assertEquals("other hosts still use system DNS", 2, systemCalls.get())
    }

    @Test fun theKillSwitchTurnsTheFallbackOffEvenWhenTheSettingIsOn() {
        val app = RuntimeEnvironment.getApplication()
        NetworkSettings.attach(app)
        FeatureFlags.set(app, FeatureFlags.Flag.DNS_FALLBACK, false)
        blackholedSystem(osTimeoutMs = 200)
        endpoints(blackholedDoh(), workingDoh(AtomicInteger()))
        NetworkSettings.setForTests(DnsConfig(auto = false, fallback = true))
        assertTrue(runCatching { SmartDns.lookup("nbug6-killed.com") }.exceptionOrNull() is UnknownHostException)
        assertEquals(0, SmartDns.fallbackAttempts.get())
    }

    @Test fun theSettingSurvivesARestart() {
        val app = RuntimeEnvironment.getApplication()
        NetworkSettings.setDns(app, DnsConfig(fallback = true))
        NetworkSettings.resetForTests()
        NetworkSettings.init(app)
        assertTrue(NetworkSettings.dns.value.fallback)
        NetworkSettings.setDns(app, DnsConfig())
    }
}
