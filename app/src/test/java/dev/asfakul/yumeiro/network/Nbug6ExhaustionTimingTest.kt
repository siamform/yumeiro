/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.network

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * NBUG-6 measurement (research note §4, no app change): how long the automatic bypass takes when
 * the network's DNS blocks a host and every DNS-over-HTTPS resolver is unreachable, and that the
 * next lookup does not wait again. Logs NBUG6_EXHAUST lines for the note; the asserts are only the
 * sequencing the note relies on (one try per provider, then skipped).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Nbug6ExhaustionTimingTest {
    private val ip = InetAddress.getByAddress("site.example", byteArrayOf(93, 184.toByte(), 216.toByte(), 34))

    @After fun reset() {
        SmartDns.system = Dns.SYSTEM; SmartDns.alternativeOverride = null; SmartDns.dohFactory = null
        SmartDns.online = { Connectivity.online.value }; SmartDns.now = System::currentTimeMillis
        NetworkSettings.resetForTests()
        NetworkSettings.setForTests(DnsConfig())
    }

    private fun blockedNetwork() {
        SmartDns.system = Dns { h -> throw UnknownHostException(h) }
        SmartDns.online = { true }
        NetworkSettings.setForTests(DnsConfig())
    }

    private fun timed(block: () -> Unit): Long { val t = System.nanoTime(); runCatching(block); return (System.nanoTime() - t) / 1_000_000 }

    /** Resolvers that each hang [hangMs] then time out: the first lookup costs providers × hang, the second ~0. */
    @Test fun sequentialExhaustionIsProvidersTimesTimeoutThenSkipped() {
        val hangMs = 400L
        val asked = mutableListOf<DnsProvider>()
        blockedNetwork()
        SmartDns.dohFactory = { p -> Dns { h -> asked += p; Thread.sleep(hangMs); throw UnknownHostException(h).apply { initCause(SocketTimeoutException("timeout")) } } }
        val first = timed { SmartDns.lookup("site.example") }
        val second = timed { SmartDns.lookup("site.example") }
        println("NBUG6_EXHAUST model hangMs=$hangMs providers=${SmartDns.AUTO_PROVIDERS.size} firstMs=$first secondMs=$second asked=$asked")
        assertEquals("one try per provider", SmartDns.AUTO_PROVIDERS, asked)
        assertTrue("first lookup waits for every provider: $first ms", first >= hangMs * SmartDns.AUTO_PROVIDERS.size)
        assertTrue("second lookup skips the unreachable resolvers: $second ms", second < hangMs)
    }

    /**
     * The same with OkHttp's real DnsOverHttps (built like SmartDns.doh: 5 s connect / read) whose
     * bootstrap is a non-routable address, so connect itself has to time out. Measures the real wall
     * time on the CI runner; a runner that rejects the route at once logs that instead.
     */
    @Test fun realDohWithUnreachableBootstrap() {
        // A public name: DnsOverHttps refuses names under a non-public suffix such as .example as
        // "private" before any request (CI 44185781422: 0 ms, rotation stopped). Never resolved here.
        val host = "nbug6-measure.com"
        // In the app the startup initializer does this; under Robolectric DnsOverHttps could not
        // load the public suffix list ("Platform applicationContext not initialized", CI 44188521804).
        okhttp3.OkHttp.initialize(org.robolectric.RuntimeEnvironment.getApplication())
        blockedNetwork()
        val dead = InetAddress.getByName("10.255.255.1")
        val client = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).build()
        val perProvider = HashMap<DnsProvider, Long>()
        val errors = HashMap<DnsProvider, String>()
        SmartDns.dohFactory = { p ->
            val doh = DnsOverHttps.Builder().client(client).url(p.dohUrl!!.toHttpUrl())
                .bootstrapDnsHosts(listOf(dead)).includeIPv6(true).resolvePrivateAddresses(false).build()
            Dns { h ->
                val t = System.nanoTime()
                try { doh.lookup(h) } catch (e: Exception) { errors[p] = "${e.javaClass.simpleName}: ${e.message} / cause ${e.cause}"; throw e } finally { perProvider[p] = (System.nanoTime() - t) / 1_000_000 }
            }
        }
        val first = timed { SmartDns.lookup(host) }
        val second = timed { SmartDns.lookup(host) }
        println("NBUG6_EXHAUST real bootstrap=10.255.255.1 connectTimeoutS=5 firstMs=$first secondMs=$second perProviderMs=$perProvider errors=$errors")
        assertEquals("every provider was tried once", SmartDns.AUTO_PROVIDERS.toSet(), perProvider.keys)
        assertTrue("second lookup skips the unreachable resolvers: $second ms", second < 1_000)
        client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll()
    }
}
