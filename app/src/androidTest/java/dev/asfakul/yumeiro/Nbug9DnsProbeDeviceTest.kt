/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.network.Connectivity
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
import java.util.concurrent.TimeUnit

/**
 * G9 audit, NBUG-9 reproduction probe (no app change, fails nothing): owner report "after Clear all
 * data / a fresh install nothing online loads until Settings → Network → DNS is set to Cloudflare".
 *
 * The emulator's resolver is broken the way a failing ISP resolver is: Private DNS set to a host
 * that does not exist (strict mode → every system lookup fails). Then real HTTPS requests go through
 * the app's client with the fresh-install setting (System DNS + auto bypass) and with what the owner
 * picked (Cloudflare, "only when system DNS fails" on = the page's default), and Cloudflare always.
 * Logs NBUG9 lines (tag YuNbug9): config, host, ok, HTTP code, ms, error. A baseline with the
 * emulator's normal DNS comes first. No network → skipped, not passed.
 */
@RunWith(AndroidJUnit4::class)
class Nbug9DnsProbeDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val ctx = ins.targetContext.applicationContext
    private fun log(s: String) = Log.i("YuNbug9", "NBUG9 $s")

    private fun sh(cmd: String): String =
        ins.uiAutomation.executeShellCommand(cmd).let { pfd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText().trim() }
        }

    private val hosts = listOf(
        "https://music.youtube.com/",
        "https://www.gstatic.com/generate_204",
        "https://api.github.com/",
    )

    private val configs = listOf(
        "fresh_default" to DnsConfig(),
        "cloudflare_fallback" to DnsConfig(DnsProvider.CLOUDFLARE, DnsTransport.DOH, DnsMode.FALLBACK),
        "cloudflare_always" to DnsConfig(DnsProvider.CLOUDFLARE, DnsTransport.DOH, DnsMode.ALWAYS),
    )

    private fun netState(): String = runCatching {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        "internet=${caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)} " +
            "validated=${caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)} appOnline=${Connectivity.online.value}"
    }.getOrDefault("unknown")

    private fun round(phase: String) {
        val sys = System.nanoTime()
        val sysResult = runCatching { java.net.InetAddress.getAllByName("music.youtube.com").size }
        log("phase=$phase systemLookup ok=${sysResult.isSuccess} ms=${(System.nanoTime() - sys) / 1_000_000} " +
            "err=${sysResult.exceptionOrNull()?.javaClass?.simpleName} ${netState()}")
        for ((name, config) in configs) {
            NetworkSettings.setForTests(config) // also clears SmartDns caches and the bypass list
            val client = NetworkClient.get(ctx).newBuilder().callTimeout(60, TimeUnit.SECONDS).build()
            client.connectionPool.evictAll()
            val start = System.nanoTime()
            var okCount = 0
            for (url in hosts) {
                val t = System.nanoTime()
                val r = runCatching { client.newCall(Request.Builder().url(url).build()).execute().use { it.code } }
                val ms = (System.nanoTime() - t) / 1_000_000
                if (r.isSuccess) okCount++
                val e = r.exceptionOrNull()
                log("phase=$phase config=$name url=$url ok=${r.isSuccess} code=${r.getOrNull()} ms=$ms " +
                    "src=${SmartDns.lastSourceFor(hostOf(url))} " +
                    "err=${e?.javaClass?.simpleName}:${e?.message?.take(120)}")
            }
            log("phase=$phase config=$name total ok=$okCount/${hosts.size} ms=${(System.nanoTime() - start) / 1_000_000}")
        }
    }

    private fun hostOf(url: String): String = url.substringAfter("://").substringBefore('/')

    @Test fun freshInstallWithABrokenSystemResolver() {
        NetworkSettings.init(ctx)
        val original = NetworkSettings.dns.value
        val online = runCatching { java.net.InetAddress.getAllByName("google.com").isNotEmpty() }.getOrDefault(false)
        assumeTrue("no network on this emulator", online)
        val mode = sh("settings get global private_dns_mode")
        val spec = sh("settings get global private_dns_specifier")
        log("start sdk=${android.os.Build.VERSION.SDK_INT} privateDns=$mode/$spec ${netState()}")
        try {
            round("baseline")
            sh("settings put global private_dns_specifier nbug9-broken-resolver.invalid")
            sh("settings put global private_dns_mode hostname")
            Thread.sleep(5_000)
            round("broken_system_dns")
        } finally {
            if (mode == "null" || mode.isBlank()) sh("settings delete global private_dns_mode") else sh("settings put global private_dns_mode $mode")
            if (spec == "null" || spec.isBlank()) sh("settings delete global private_dns_specifier") else sh("settings put global private_dns_specifier $spec")
            NetworkSettings.setForTests(original)
            log("restored privateDns=${sh("settings get global private_dns_mode")}")
        }
    }
}
