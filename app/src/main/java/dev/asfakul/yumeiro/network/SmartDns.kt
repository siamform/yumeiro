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
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Which resolver answered the last lookup of a host (shown in Video details → Stream health). */
enum class DnsSource { SYSTEM, ALTERNATIVE }

/**
 * OkHttp [Dns] that follows [NetworkSettings.dns]:
 *  - System DNS (default): plain system lookups, nothing else.
 *  - Fallback mode: system DNS first; the configured resolver is used **only** after an actual
 *    resolution failure (UnknownHostException). Hosts that failed are remembered for a few minutes
 *    so the next request doesn't wait for the system resolver to fail again.
 *  - Always mode: the configured resolver answers; if it can't be reached, system DNS is the safety net.
 * Answers from the alternative resolver are cached for their TTL (clamped to 30 s – 10 min).
 */
object SmartDns : Dns {
    private const val SYSTEM_FAILURE_MEMORY_MS = 5 * 60_000L
    private val cache = ConcurrentHashMap<String, Pair<List<InetAddress>, Long>>()
    private val systemFailures = ConcurrentHashMap<String, Long>()
    private val lastSource = ConcurrentHashMap<String, DnsSource>()
    private val dohClients = ConcurrentHashMap<DnsProvider, Dns>()

    /** Replaceable for tests. */
    @Volatile internal var system: Dns = Dns.SYSTEM
    @Volatile internal var alternativeOverride: ((String, DnsConfig) -> List<InetAddress>)? = null
    @Volatile internal var now: () -> Long = System::currentTimeMillis

    fun clearCache() { cache.clear(); systemFailures.clear(); lastSource.clear() }

    fun lastSourceFor(host: String): DnsSource? = lastSource[host.lowercase()]

    override fun lookup(hostname: String): List<InetAddress> {
        val host = hostname.lowercase()
        val config = NetworkSettings.dns.value
        if (!config.usesAlternative || isIpLiteral(host)) return systemLookup(host)
        return when (config.mode) {
            DnsMode.ALWAYS -> try {
                alternativeLookup(host, config)
            } catch (e: IOException) {
                try { systemLookup(host) } catch (_: UnknownHostException) { throw failure(host, config, e) }
            }
            DnsMode.FALLBACK -> {
                val failedAt = systemFailures[host]
                if (failedAt != null && now() - failedAt < SYSTEM_FAILURE_MEMORY_MS) {
                    try { return alternativeLookup(host, config) } catch (_: IOException) { /* try system again below */ }
                }
                try {
                    systemLookup(host).also { systemFailures.remove(host) }
                } catch (systemError: UnknownHostException) {
                    // A real resolution failure: retry once through the configured resolver.
                    systemFailures[host] = now()
                    try { alternativeLookup(host, config) } catch (e: IOException) { throw failure(host, config, e) }
                }
            }
        }
    }

    private fun systemLookup(host: String): List<InetAddress> =
        system.lookup(host).also { lastSource[host] = DnsSource.SYSTEM }

    private fun alternativeLookup(host: String, config: DnsConfig): List<InetAddress> {
        cache[host]?.let { (addresses, expiresAt) -> if (now() < expiresAt) { lastSource[host] = DnsSource.ALTERNATIVE; return addresses } }
        val override = alternativeOverride
        val addresses: List<InetAddress>
        var ttl = 120L
        if (override != null) {
            addresses = override(host, config)
        } else when (config.transport) {
            DnsTransport.DOH -> addresses = doh(config.provider).lookup(host)
            DnsTransport.DOT, DnsTransport.PLAIN -> {
                var last: IOException? = null
                var found: DnsMessage.Answer? = null
                // Try the resolver's IPv4 addresses in order (bounded: at most two servers).
                for (ip in config.provider.bootstrap.filter { ':' !in it }.take(2)) {
                    try { found = DnsWireClient(ip, config.transport, config.provider.tlsHost).resolve(host); break } catch (e: UnknownHostException) { throw e } catch (e: IOException) { last = e }
                }
                val answer = found ?: throw last ?: IOException("Resolver unreachable")
                addresses = answer.addresses; ttl = answer.ttlSeconds
            }
        }
        if (addresses.isEmpty()) throw UnknownHostException("$host: no addresses from ${config.provider.label}")
        cache[host] = addresses to (now() + ttl.coerceIn(30, 600) * 1000)
        lastSource[host] = DnsSource.ALTERNATIVE
        return addresses
    }

    private fun doh(provider: DnsProvider): Dns = dohClients.getOrPut(provider) {
        // Separate client: its own lookups must never recurse into SmartDns; bootstrap IPs avoid DNS entirely.
        val bootstrapClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS)
            .build()
        DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url(provider.dohUrl!!.toHttpUrl())
            .bootstrapDnsHosts(provider.bootstrap.map { InetAddress.getByName(it) })
            .includeIPv6(true)
            .resolvePrivateAddresses(false)
            .build()
    }

    private fun failure(host: String, config: DnsConfig, cause: Exception) =
        UnknownHostException("Unable to resolve host \"$host\" with system DNS or ${config.provider.label} (${config.transport.label}): ${cause.message}")
            .apply { initCause(cause) }

    internal fun isIpLiteral(host: String): Boolean =
        host.matches(Regex("""\d{1,3}(\.\d{1,3}){3}""")) || (host.contains(':') && host.all { it.isLetterOrDigit() || it == ':' || it == '.' || it == '[' || it == ']' || it == '%' })

    /** Resolves [host] with [config] right now, for the Settings "Test" button. */
    fun test(host: String, config: DnsConfig): Result<Pair<List<InetAddress>, Long>> = runCatching {
        val start = now()
        val result = if (!config.usesAlternative) system.lookup(host) else {
            cache.remove(host.lowercase())
            alternativeLookup(host.lowercase(), config)
        }
        result to (now() - start)
    }
}
