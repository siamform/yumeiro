/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.network

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Public DNS resolvers that can be used instead of (or as a fallback for) the system resolver.
 *
 * These are plain DNS services: no VPN, no account and no Cloudflare WARP / paid plan involved.
 * [bootstrap] holds the resolver's own IP addresses so it can be reached without DNS.
 */
enum class DnsProvider(
    val label: String,
    val primaryIp: String,
    val bootstrap: List<String>,
    /** DNS-over-HTTPS endpoint (RFC 8484). */
    val dohUrl: String?,
    /** Hostname on the resolver's TLS certificate, used for DNS-over-TLS (RFC 7858). */
    val tlsHost: String?,
) {
    SYSTEM("System DNS", "", emptyList(), null, null),
    CLOUDFLARE("Cloudflare", "1.1.1.1", listOf("1.1.1.1", "1.0.0.1", "2606:4700:4700::1111", "2606:4700:4700::1001"),
        "https://cloudflare-dns.com/dns-query", "cloudflare-dns.com"),
    QUAD9("Quad9", "9.9.9.9", listOf("9.9.9.9", "149.112.112.112", "2620:fe::fe", "2620:fe::9"),
        "https://dns.quad9.net/dns-query", "dns.quad9.net"),
    GOOGLE("Google", "8.8.8.8", listOf("8.8.8.8", "8.8.4.4", "2001:4860:4860::8888", "2001:4860:4860::8844"),
        "https://dns.google/dns-query", "dns.google"),
}

/** How the alternative resolver is reached. Encrypted transports hide lookups from the network. */
enum class DnsTransport(val label: String, val description: String) {
    DOH("DNS over HTTPS", "Encrypted, port 443 — works on most networks"),
    DOT("DNS over TLS", "Encrypted, port 853 — may be blocked on some networks"),
    PLAIN("Plain DNS", "Unencrypted UDP port 53 — fastest, visible to the network"),
}

/** When the alternative resolver is used. */
enum class DnsMode(val label: String) {
    /** System DNS first; the alternative is tried only after a real resolution failure. */
    FALLBACK("Only when system DNS fails"),
    /** Every lookup goes to the alternative resolver (system DNS is the safety net). */
    ALWAYS("Always use it"),
}

data class DnsConfig(
    val provider: DnsProvider = DnsProvider.SYSTEM,
    val transport: DnsTransport = DnsTransport.DOH,
    val mode: DnsMode = DnsMode.FALLBACK,
    /**
     * With System DNS: detect hosts the current network blocks (no answer, sinkhole address, or
     * connections reset right after a system lookup) and quietly resolve them over encrypted DNS
     * (Cloudflare → Quad9 → Google). On by default; nothing changes for hosts that work.
     */
    val auto: Boolean = true,
    /**
     * NBUG-6 (ADR-046) "Automatic DNS fallback", default Off. With System DNS: when the system
     * resolver itself fails or times out, ask 1.1.1.1 and then 9.9.9.9 in turn (3.5 s each, never at
     * once) and keep the one that answered for the rest of the session. Kill switch
     * `nbug6_dns_fallback`.
     */
    val fallback: Boolean = false,
) {
    val usesAlternative: Boolean get() = provider != DnsProvider.SYSTEM
    val summary: String get() = if (!usesAlternative) (if (auto) "System DNS · auto bypass for blocked sites" else "System DNS") else "${provider.label} (${provider.primaryIp}) · ${transport.label} · ${mode.label.lowercase()}"
}

/** Persisted network preferences (Settings → Network). System DNS is the default. */
object NetworkSettings {
    private const val PREFS = "mt_network"
    private val _dns = MutableStateFlow(DnsConfig())
    val dns: StateFlow<DnsConfig> = _dns.asStateFlow()
    @Volatile private var loaded = false
    @Volatile private var prefs: android.content.SharedPreferences? = null
    private const val BYPASS_KEY = "dns_bypass_hosts"

    /** Remembers which hosts needed the DNS bypass, so the next launch goes straight to it. */
    internal fun saveBypass(hosts: Set<String>) {
        prefs?.edit()?.putStringSet(BYPASS_KEY, HashSet(hosts))?.apply()
    }

    @Volatile private var app: Context? = null

    /**
     * G12 X11-2: called first thing in Application.onCreate (no disk access). Requests made before
     * any screen called [init] used the default DNS settings and an empty bypass list; now the first
     * DNS lookup loads the user's settings on the OkHttp thread that needs them ([ensureLoaded]).
     */
    fun attach(context: Context) { app = context.applicationContext ?: context }

    /** NBUG-6: the kill switch; true when no context is attached (unit tests drive [SmartDns] directly). */
    internal fun fallbackAllowed(): Boolean = app?.let { dev.asfakul.yumeiro.FeatureFlags.on(it, dev.asfakul.yumeiro.FeatureFlags.Flag.DNS_FALLBACK) } ?: true

    /** Loads the settings if [attach] was called and nothing loaded them yet. Disk read: off main. */
    fun ensureLoaded() { if (!loaded) app?.let { init(it) } }

    /**
     * CI-DNS-RACE (unit run 44894173529, Nbug6FallbackTest): [init] read the prefs and then wrote [dns] without
     * looking again, so a setting saved while the prefs were being read (PrefsWarmup / StreamResolver load them on a
     * background thread) was overwritten with the old stored one. Writes now go through [writeLock] (no disk inside):
     * the stored value applies only if nothing was set meanwhile.
     */
    private val writeLock = Any()
    /** Test hook: runs after [init] read the prefs, before it writes. */
    @Volatile internal var afterPrefsReadForTests: (() -> Unit)? = null

    /** CI-DNS-RACE-2: bumped by [resetForTests]; an init that started before a reset must not write after it. */
    @Volatile private var generation = 0

    @Synchronized
    fun init(context: Context) {
        if (loaded) return
        val gen = generation
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = if (!p.contains("dns_provider")) freshDefault(context) else DnsConfig(
            provider = enumOr(p.getString("dns_provider", null), DnsProvider.SYSTEM),
            transport = enumOr(p.getString("dns_transport", null), DnsTransport.DOH),
            mode = enumOr(p.getString("dns_mode", null), DnsMode.FALLBACK),
            auto = p.getBoolean("dns_auto", true),
            fallback = p.getBoolean("dns_fallback_auto", false),
        )
        val bypass = p.getStringSet(BYPASS_KEY, emptySet()).orEmpty()
        afterPrefsReadForTests?.invoke()
        prefs = p
        SmartDns.restoreBypass(bypass)
        synchronized(writeLock) { if (!loaded && gen == generation) { _dns.value = stored; loaded = true } }
    }

    /**
     * OWN-7 / OWN-2: the settings used when the user never saved a DNS choice (fresh install, or an update from a
     * version where they kept the default). Before, that was System DNS + auto bypass, and on networks whose DNS
     * blocks or hijacks the sources' hosts in a way the bypass does not notice, online features failed until the
     * user picked a resolver in Settings → Network (the setup guide's "Set up DNS" step). Now Cloudflare
     * DNS-over-HTTPS answers, system DNS is the safety net when Cloudflare can't be reached, and LAN names still
     * use system DNS. A saved choice (any [setDns]) is always kept. Kill switch: [FeatureFlags.Flag.CLOUDFLARE_DEFAULT_DNS].
     */
    internal fun freshDefault(context: Context): DnsConfig =
        if (dev.asfakul.yumeiro.FeatureFlags.on(context, dev.asfakul.yumeiro.FeatureFlags.Flag.CLOUDFLARE_DEFAULT_DNS))
            DnsConfig(provider = DnsProvider.CLOUDFLARE, transport = DnsTransport.DOH, mode = DnsMode.ALWAYS)
        else DnsConfig()

    fun setDns(context: Context, config: DnsConfig) {
        synchronized(writeLock) { _dns.value = config; loaded = true }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("dns_provider", config.provider.name)
            .putString("dns_transport", config.transport.name)
            .putString("dns_mode", config.mode.name)
            .putBoolean("dns_auto", config.auto)
            .putBoolean("dns_fallback_auto", config.fallback)
            .apply()
        SmartDns.clearCache()
    }

    /** For tests. */
    internal fun setForTests(config: DnsConfig) { synchronized(writeLock) { _dns.value = config; loaded = true }; SmartDns.clearCache() }
    internal fun resetForTests() { synchronized(writeLock) { generation++; loaded = false }; app = null; prefs = null; _dns.value = DnsConfig(); SmartDns.clearCache() }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        runCatching { enumValueOf<T>(name ?: return default) }.getOrDefault(default)
}
