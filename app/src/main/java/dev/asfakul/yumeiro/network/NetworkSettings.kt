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
) {
    val usesAlternative: Boolean get() = provider != DnsProvider.SYSTEM
    val summary: String get() = if (!usesAlternative) "System DNS" else "${provider.label} (${provider.primaryIp}) · ${transport.label} · ${mode.label.lowercase()}"
}

/** Persisted network preferences (Settings → Network). System DNS is the default. */
object NetworkSettings {
    private const val PREFS = "mt_network"
    private val _dns = MutableStateFlow(DnsConfig())
    val dns: StateFlow<DnsConfig> = _dns.asStateFlow()
    @Volatile private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _dns.value = DnsConfig(
            provider = enumOr(p.getString("dns_provider", null), DnsProvider.SYSTEM),
            transport = enumOr(p.getString("dns_transport", null), DnsTransport.DOH),
            mode = enumOr(p.getString("dns_mode", null), DnsMode.FALLBACK),
        )
        loaded = true
    }

    fun setDns(context: Context, config: DnsConfig) {
        _dns.value = config
        loaded = true
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("dns_provider", config.provider.name)
            .putString("dns_transport", config.transport.name)
            .putString("dns_mode", config.mode.name)
            .apply()
        SmartDns.clearCache()
    }

    /** For tests. */
    internal fun setForTests(config: DnsConfig) { _dns.value = config; loaded = true; SmartDns.clearCache() }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        runCatching { enumValueOf<T>(name ?: return default) }.getOrDefault(default)
}
