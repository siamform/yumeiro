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

// Precompiled once (R15 audit): these ran a regex compile on every call.
private val RX_SMARTDNS_HOT_1 = Regex("""\d{1,3}(\.\d{1,3}){3}""")

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

    fun clearCache() { cache.clear(); systemFailures.clear(); lastSource.clear(); bypass.clear(); preferred = 0; resolverDownUntil.clear(); sessionResolver = null }

    /**
     * G12 X11-1: a resolver that could not be reached (timeout, refused, TLS) is skipped for
     * [RESOLVER_DOWN_MS]. Before, on a network that blocks DNS-over-HTTPS every lookup of a bypassed
     * host waited for all three providers' timeouts again before falling back to system DNS.
     */
    private const val RESOLVER_DOWN_MS = 2 * 60_000L
    private val resolverDownUntil = ConcurrentHashMap<DnsProvider, Long>()

    /** A new network: resolvers that were unreachable may work now. */
    fun onNetworkChanged() { resolverDownUntil.clear(); sessionResolver = null }

    fun lastSourceFor(host: String): DnsSource? = lastSource[host.lowercase()]

    override fun lookup(hostname: String): List<InetAddress> {
        val host = hostname.lowercase()
        NetworkSettings.ensureLoaded()
        val config = NetworkSettings.dns.value
        if (isIpLiteral(host)) return systemLookup(host)
        if (!config.usesAlternative) return when {
            isLocalName(host) -> systemLookup(host)
            config.fallback && NetworkSettings.fallbackAllowed() -> fallbackLookup(host, config)
            config.auto -> autoLookup(host)
            else -> systemLookup(host)
        }
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

    // region NBUG-6 automatic DNS fallback (ADR-046)

    /**
     * The chain after system DNS: at most three resolvers in all (system, 1.1.1.1, 9.9.9.9). Quad9 is
     * the third rather than Google: same reach, and it is already one of the auto-bypass engines.
     */
    internal val FALLBACK_CHAIN = listOf(DnsProvider.CLOUDFLARE, DnsProvider.QUAD9)
    /** Per attempt: connect, read and whole call, so one dead resolver costs at most this. */
    internal const val FALLBACK_ATTEMPT_MS = 3_500L

    /** The resolver that answered after system DNS failed; used first until the network changes. */
    @Volatile internal var sessionResolver: DnsProvider? = null
        private set
    private val chainLock = Any()
    /** Test evidence: attempts made while walking the chain and the most that ever ran at once (must stay 1). */
    internal val fallbackAttempts = java.util.concurrent.atomic.AtomicInteger()
    internal val fallbackMaxConcurrent = java.util.concurrent.atomic.AtomicInteger()
    private val fallbackRunning = java.util.concurrent.atomic.AtomicInteger()

    /**
     * System DNS first, on the caller's thread, with no wrapper: a healthy network pays nothing. Only a
     * failure (UnknownHostException while online, or a host the connection check marked blocked) walks
     * the chain, one resolver at a time. A system-wide failure (the resolver timed out or is unreachable)
     * keeps the resolver that answered for the session, so the timeout is paid once, not per request; a
     * single host the network refuses is remembered for that host only, as in auto bypass.
     */
    private fun fallbackLookup(host: String, config: DnsConfig): List<InetAddress> {
        sessionResolver?.let { p ->
            try { return fallbackAttempt(host, p) } catch (e: UnknownHostException) { throw e } catch (_: IOException) {
                sessionResolver = null // that resolver went away: system DNS may be back
            }
        }
        if (isBypassed(host)) runCatching { chain(host, sessionWide = false) }.getOrNull()?.let { return it }
        val started = now()
        val answer = try {
            systemLookup(host)
        } catch (systemError: UnknownHostException) {
            if (!online()) throw systemError
            val wide = isSystemWide(systemError, now() - started)
            val real = chain(host, sessionWide = wide) ?: throw systemError
            if (!wide) markBlocked(host)
            return real
        }
        if (config.auto && looksSinkholed(host, answer)) {
            val real = runCatching { chain(host, sessionWide = false) }.getOrNull()
            if (real != null && !looksSinkholed(host, real)) { markBlocked(host); return real }
        }
        return answer
    }

    /**
     * The system resolver failed as a whole (not "this name has no address"): Android reports a
     * timeout or an unreachable resolver as EAI_AGAIN ("Try again"), and a lookup that took the full
     * attempt budget is a timeout whatever it says.
     */
    internal fun isSystemWide(e: UnknownHostException, tookMs: Long): Boolean {
        if (tookMs >= FALLBACK_ATTEMPT_MS) return true
        var c: Throwable? = e
        while (c != null) {
            val m = c.message.orEmpty()
            if ("EAI_AGAIN" in m || "Try again" in m || "timed out" in m || "ETIMEDOUT" in m) return true
            c = c.cause
        }
        return false
    }

    /** Walks [FALLBACK_CHAIN] in order, one attempt at a time across all threads. Null: none answered. */
    private fun chain(host: String, sessionWide: Boolean): List<InetAddress>? = synchronized(chainLock) {
        // Another thread may have found a resolver while this one waited for the lock.
        sessionResolver?.let { p -> runCatching { fallbackAttempt(host, p) }.getOrNull()?.let { return it } }
        for (p in FALLBACK_CHAIN) {
            try {
                return chainAttempt(host, p).also { if (sessionWide) sessionResolver = p }
            } catch (e: UnknownHostException) {
                throw e // a real answer (NXDOMAIN, no records): another resolver won't know better
            } catch (_: IOException) { /* unreachable or timed out (ResolverUnreachable): next resolver */ }
        }
        null
    }

    private fun fallbackAttempt(host: String, provider: DnsProvider): List<InetAddress> =
        alternativeLookup(host, DnsConfig(provider, DnsTransport.DOH, DnsMode.FALLBACK, auto = true, fallback = true))

    /** One attempt of the chain walk, counted (the walk must never run two at once). */
    private fun chainAttempt(host: String, provider: DnsProvider): List<InetAddress> {
        fallbackAttempts.incrementAndGet()
        fallbackMaxConcurrent.accumulateAndGet(fallbackRunning.incrementAndGet()) { a, b -> maxOf(a, b) }
        try { return fallbackAttempt(host, provider) } finally { fallbackRunning.decrementAndGet() }
    }

    // endregion

    private fun systemLookup(host: String): List<InetAddress> =
        system.lookup(host).also { lastSource[host] = DnsSource.SYSTEM }

    private fun alternativeLookup(host: String, config: DnsConfig): List<InetAddress> {
        cache[host]?.let { (addresses, expiresAt) -> if (now() < expiresAt) { lastSource[host] = DnsSource.ALTERNATIVE; return addresses } }
        resolverDownUntil[config.provider]?.let { until ->
            if (now() < until) throw IOException("${config.provider.label} was unreachable a moment ago")
        }
        try {
            return fetchAlternative(host, config).also { resolverDownUntil.remove(config.provider) }
        } catch (e: UnknownHostException) {
            throw e // a real answer (NXDOMAIN or no addresses): the resolver works
        } catch (e: IOException) {
            resolverDownUntil[config.provider] = now() + RESOLVER_DOWN_MS
            throw e
        }
    }

    private fun fetchAlternative(host: String, config: DnsConfig): List<InetAddress> {
        val override = alternativeOverride
        val addresses: List<InetAddress>
        var ttl = 120L
        if (override != null) {
            addresses = override(host, config)
        } else when (config.transport) {
            DnsTransport.DOH -> addresses = try {
                (if (config.fallback) fallbackDoh(config.provider) else doh(config.provider)).lookup(host)
            } catch (e: UnknownHostException) {
                // G12 X11-3: OkHttp's DnsOverHttps reports a failed request (timeout, refused, TLS) as
                // UnknownHostException with the network error as its cause. Only a cause-less one is a
                // real answer (NXDOMAIN, no records); the other means this resolver is unreachable.
                throw unreachableOrAnswer(e)
            }
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

    /** Resolver could not be reached (not an answer about the name). */
    class ResolverUnreachable(message: String, cause: Throwable) : IOException(message, cause)

    internal fun unreachableOrAnswer(e: UnknownHostException): IOException {
        val cause = e.cause
        return if (cause is IOException && cause !is UnknownHostException) ResolverUnreachable(e.message ?: "resolver unreachable", cause) else e
    }

    /** Test hook: replaces the real DNS-over-HTTPS clients. */
    @Volatile internal var dohFactory: ((DnsProvider) -> Dns)? = null

    /** Test hook (NBUG-6 blackhole test): the DoH URL and bootstrap IP literals of a provider. */
    @Volatile internal var dohEndpoint: (DnsProvider) -> Pair<String, List<String>> = { it.dohUrl!! to it.bootstrap }
    private val bootstrapClients = ConcurrentHashMap<DnsProvider, OkHttpClient>()
    private val fallbackClients = ConcurrentHashMap<DnsProvider, Dns>()

    // Separate client: its own lookups must never recurse into SmartDns; bootstrap IPs avoid DNS entirely.
    private fun bootstrapClient(provider: DnsProvider): OkHttpClient = bootstrapClients.getOrPut(provider) {
        OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).build()
    }

    private fun buildDoh(provider: DnsProvider, client: OkHttpClient): Dns {
        val (url, bootstrap) = dohEndpoint(provider)
        return DnsOverHttps.Builder()
            .client(client)
            .url(url.toHttpUrl())
            .bootstrapDnsHosts(bootstrap.map { InetAddress.getByName(it) })
            .includeIPv6(true)
            .resolvePrivateAddresses(false)
            .build()
    }

    private fun doh(provider: DnsProvider): Dns = dohFactory?.invoke(provider) ?: dohClients.getOrPut(provider) {
        buildDoh(provider, bootstrapClient(provider))
    }

    /**
     * NBUG-6: the same provider's engine with a [FALLBACK_ATTEMPT_MS] budget. `newBuilder()` shares the
     * provider's dispatcher and connection pool, so this is not a second engine: no extra threads, no
     * second pool (the cost measured in device run 44192612391 came from separate clients).
     */
    private fun fallbackDoh(provider: DnsProvider): Dns = dohFactory?.invoke(provider) ?: fallbackClients.getOrPut(provider) {
        val budget = FALLBACK_ATTEMPT_MS
        buildDoh(provider, bootstrapClient(provider).newBuilder()
            .connectTimeout(budget, TimeUnit.MILLISECONDS).readTimeout(budget, TimeUnit.MILLISECONDS)
            .callTimeout(budget, TimeUnit.MILLISECONDS).build())
    }

    /** Tests: drop the built engines so a changed [dohEndpoint] takes effect. */
    internal fun resetEnginesForTests() { dohClients.clear(); fallbackClients.clear(); bootstrapClients.clear() }

    private fun failure(host: String, config: DnsConfig, cause: Exception) =
        UnknownHostException("Unable to resolve host \"$host\" with system DNS or ${config.provider.label} (${config.transport.label}): ${cause.message}")
            .apply { initCause(cause) }

    internal fun isIpLiteral(host: String): Boolean =
        host.matches(RX_SMARTDNS_HOT_1) || (host.contains(':') && host.all { it.isLetterOrDigit() || it == ':' || it == '.' || it == '[' || it == ']' || it == '%' })

    /** Resolves [host] with [config] right now, for the Settings "Test" button. */
    fun test(host: String, config: DnsConfig): Result<Pair<List<InetAddress>, Long>> = runCatching {
        val start = now()
        val result = if (!config.usesAlternative) system.lookup(host) else {
            cache.remove(host.lowercase())
            resolverDownUntil.remove(config.provider) // the button must really try
            alternativeLookup(host.lowercase(), config)
        }
        result to (now() - start)
    }
}
