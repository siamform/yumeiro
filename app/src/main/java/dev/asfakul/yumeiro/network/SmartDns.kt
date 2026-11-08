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
 *  - System DNS (default): system lookups. With [DnsConfig.auto] (default on) a host the network
 *    blocks (no answer, a sinkhole address, or connections reset after a system lookup) is
 *    resolved over DNS-over-HTTPS instead, rotating Cloudflare → Quad9 → Google, and remembered.
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
    @Volatile internal var online: () -> Boolean = { Connectivity.online.value }

    fun clearCache() { cache.clear(); systemFailures.clear(); lastSource.clear(); bypass.clear(); preferred = 0 }

    fun lastSourceFor(host: String): DnsSource? = lastSource[host.lowercase()]

    override fun lookup(hostname: String): List<InetAddress> {
        val host = hostname.lowercase()
        val config = NetworkSettings.dns.value
        if (isIpLiteral(host)) return systemLookup(host)
        if (!config.usesAlternative) return if (config.auto && !isLocalName(host)) autoLookup(host) else systemLookup(host)
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

    // region automatic bypass (System DNS + auto)

    /** Resolvers tried, in order, when the network's DNS blocks a host. */
    internal val AUTO_PROVIDERS = listOf(DnsProvider.CLOUDFLARE, DnsProvider.QUAD9, DnsProvider.GOOGLE)
    private const val BYPASS_MEMORY_MS = 12 * 60 * 60_000L
    private const val MAX_BYPASS_HOSTS = 200
    private val bypass = ConcurrentHashMap<String, Long>()
    @Volatile private var preferred = 0

    fun restoreBypass(hosts: Set<String>) { val t = now(); hosts.take(MAX_BYPASS_HOSTS).forEach { bypass.putIfAbsent(it.lowercase(), t) } }

    fun isBypassed(host: String): Boolean = bypass[host.lowercase()]?.let { now() - it < BYPASS_MEMORY_MS } == true

    /**
     * Marks [host] as blocked by the network's DNS (called by [AutoBypassInterceptor] when a
     * connection to a system-resolved address fails). Returns false if it was already bypassed.
     */
    fun markBlocked(host: String): Boolean {
        val h = host.lowercase()
        if (isIpLiteral(h) || isLocalName(h) || isBypassed(h)) return false
        bypass[h] = now()
        if (bypass.size > MAX_BYPASS_HOSTS) bypass.entries.sortedBy { it.value }.take(bypass.size - MAX_BYPASS_HOSTS).forEach { bypass.remove(it.key) }
        cache.remove(h)
        runCatching { NetworkSettings.saveBypass(bypass.keys) }
        return true
    }

    private fun autoLookup(host: String): List<InetAddress> {
        if (isBypassed(host)) {
            try { return encryptedLookup(host) } catch (_: IOException) { /* the network may have changed: system below */ }
        }
        val answer = try {
            systemLookup(host)
        } catch (systemError: UnknownHostException) {
            if (!online()) throw systemError
            return try { encryptedLookup(host).also { markBlocked(host) } } catch (_: IOException) { throw systemError }
        }
        if (looksSinkholed(host, answer)) {
            // ISP block pages / sinkholes answer with 0.0.0.0, loopback or private addresses.
            val real = runCatching { encryptedLookup(host) }.getOrNull()
            if (real != null && !looksSinkholed(host, real)) { markBlocked(host); return real }
        }
        return answer
    }

    /** Encrypted lookup that rotates through [AUTO_PROVIDERS], starting with the last one that worked. */
    private fun encryptedLookup(host: String): List<InetAddress> {
        var last: IOException? = null
        val start = preferred
        for (i in AUTO_PROVIDERS.indices) {
            val idx = (start + i) % AUTO_PROVIDERS.size
            try {
                return alternativeLookup(host, DnsConfig(AUTO_PROVIDERS[idx], DnsTransport.DOH, DnsMode.FALLBACK, auto = true))
                    .also { preferred = idx }
            } catch (e: UnknownHostException) {
                last = e // NXDOMAIN from a real resolver: another one won't know better
                break
            } catch (e: IOException) { last = e }
        }
        throw last ?: UnknownHostException(host)
    }

    /** Addresses a public name should never resolve to (block pages, sinkholes, captive DNS). */
    internal fun looksSinkholed(host: String, addresses: List<InetAddress>): Boolean =
        addresses.isNotEmpty() && !isLocalName(host) && addresses.all { isBogon(it) }

    internal fun isBogon(a: InetAddress): Boolean {
        val b = a.address
        if (b.size == 16) return a.isAnyLocalAddress || a.isLoopbackAddress || a.isLinkLocalAddress || a.isSiteLocalAddress || (b[0].toInt() and 0xFE) == 0xFC
        if (b.size != 4) return false
        val o0 = b[0].toInt() and 0xFF; val o1 = b[1].toInt() and 0xFF
        return o0 == 0 || o0 == 127 || o0 == 10 ||
            (o0 == 192 && o1 == 168) || (o0 == 172 && o1 in 16..31) ||
            (o0 == 100 && o1 in 64..127) || (o0 == 169 && o1 == 254)
    }

    /** LAN names (media servers, routers) legitimately resolve to private addresses. */
    internal fun isLocalName(host: String): Boolean =
        '.' !in host || host == "localhost" || listOf(".local", ".lan", ".home", ".internal", ".localdomain", ".home.arpa", ".localhost").any { host.endsWith(it) }

    // endregion

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
