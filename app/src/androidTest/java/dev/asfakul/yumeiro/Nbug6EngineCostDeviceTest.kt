/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.os.Debug
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.network.DnsConfig
import dev.asfakul.yumeiro.network.DnsMode
import dev.asfakul.yumeiro.network.DnsProvider
import dev.asfakul.yumeiro.network.DnsTransport
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.network.NetworkSettings
import dev.asfakul.yumeiro.network.SmartDns
import okhttp3.Request
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * NBUG-6 measurement (research note §2, no app change): what one more DNS-over-HTTPS engine costs
 * on a phone, and whether two features with different DNS policies share connections to one host.
 * Logs NBUG6_COST lines (tag YuNbug6) only: native + Java heap and thread count before, after each
 * engine resolved 20 hosts, and after idling; pool connections for one host through one client
 * twice versus two clients that differ only in Dns. Fails nothing; no network → skipped, not passed.
 */
@RunWith(AndroidJUnit4::class)
class Nbug6EngineCostDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private fun log(s: String) = Log.i("YuNbug6", "NBUG6_COST $s")

    private val hosts = listOf(
        "google.com", "github.com", "kernel.org", "wikipedia.org", "cloudflare.com", "mozilla.org", "debian.org",
        "python.org", "apache.org", "gnu.org", "archlinux.org", "ubuntu.com", "fedoraproject.org", "gitlab.com",
        "stackoverflow.com", "bbc.co.uk", "reddit.com", "youtube.com", "android.com", "kotlinlang.org",
    )

    private fun threads(): Int = runCatching {
        File("/proc/self/status").readLines().first { it.startsWith("Threads:") }.substringAfter(':').trim().toInt()
    }.getOrDefault(-1)

    private fun okThreads(): Int = Thread.getAllStackTraces().keys.count { it.name.startsWith("OkHttp") }

    private fun snapshot(label: String) {
        Runtime.getRuntime().gc(); Thread.sleep(300)
        val rt = Runtime.getRuntime()
        log("$label nativeKiB=${Debug.getNativeHeapAllocatedSize() / 1024} javaKiB=${(rt.totalMemory() - rt.freeMemory()) / 1024} " +
            "threads=${threads()} okhttpThreads=${okThreads()}")
    }

    @Test fun engineCostAndConnectionReuse() {
        NetworkSettings.init(ctx)
        val original = NetworkSettings.dns.value
        val base = NetworkClient.get(ctx)
        val online = runCatching { java.net.InetAddress.getAllByName("google.com").isNotEmpty() }.getOrDefault(false)
        assumeTrue("no network on this emulator", online)
        try {
            snapshot("before engines=0")
            var engines = 0
            for (p in listOf(DnsProvider.CLOUDFLARE, DnsProvider.QUAD9, DnsProvider.GOOGLE)) {
                NetworkSettings.setForTests(DnsConfig(p, DnsTransport.DOH, DnsMode.ALWAYS))
                val t = System.nanoTime()
                val ok = hosts.count { h -> runCatching { SmartDns.lookup(h).isNotEmpty() && SmartDns.lastSourceFor(h) == dev.asfakul.yumeiro.network.DnsSource.ALTERNATIVE }.getOrDefault(false) }
                engines++
                log("resolved provider=$p ok=$ok/${hosts.size} ms=${(System.nanoTime() - t) / 1_000_000}")
                snapshot("after engines=$engines")
            }
            Thread.sleep(8_000); snapshot("idle8s engines=$engines")
            Thread.sleep(57_000); snapshot("idle65s engines=$engines")

            // Connection reuse: same client twice, then a client that differs only in its Dns (same pool).
            NetworkSettings.setForTests(original)
            val url = "https://www.google.com/generate_204"
            fun hit(c: okhttp3.OkHttpClient) = runCatching { c.newCall(Request.Builder().url(url).build()).execute().use { it.code } }.getOrDefault(-1)
            base.connectionPool.evictAll()
            val a = hit(base); val b = hit(base)
            val same = base.connectionPool.connectionCount()
            val other = base.newBuilder().dns(okhttp3.Dns.SYSTEM).build()
            val c = hit(other)
            val mixed = base.connectionPool.connectionCount()
            log("pool sameClientTwice=$same (codes $a,$b) plusClientWithOtherDns=$mixed (code $c)")
        } finally {
            NetworkSettings.setForTests(original)
        }
    }
}
