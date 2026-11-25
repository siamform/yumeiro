/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.adchain

import dev.asfakul.yumeiro.online.web.AdFilter
import dev.asfakul.yumeiro.stream.LinkHosts
import java.net.URI
import java.net.URLDecoder

/**
 * ADCHAIN-3 (owner item 28): which addresses a chain may go to, and which address is the end of the chain.
 * Adapted from the contributor's DestinationValidator; differences: one ad list ([AdFilter]), file hosts come
 * from Yumeiro's own [LinkHosts] (no second list), and unsafe targets are refused (non-http schemes, user info
 * in the address, this phone / local network addresses, ad hosts, parked domains). Pure.
 */
object AdChainDestinations {
    /** Shortener / safelink gate hosts: a chain that ends on one of these has not reached its link. */
    private val GATES = setOf(
        "shrinkme.io", "shrinkme.click", "shrinkme.us", "shrinkme.site", "shrinkme.org", "shrinkearn.com", "srnky.com",
        "tpi.li", "oii.la", "clksz.com", "themezon.net", "mrproblogger.com", "hsalinks.in", "hsalinks.eu.org",
        "links.tpxsub.com", "ouo.io", "ouo.press", "exe.io", "exey.io", "gplinks.co", "gplinks.in", "adrinolinks.in",
        "shareus.io", "linkvertise.com", "cuty.io", "cutt.ly.zone", "adfoc.us", "za.gl", "urlshortx.com",
    )
    private val PARKED = setOf("asurasever.cloud", "hindidubbedanime.com")
    private val MEDIA = Regex("\\.(m3u8|mpd|mp4|mkv|webm|mov|avi|ts)(\$|[?#])", RegexOption.IGNORE_CASE)
    private val IPV4 = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\$")

    /** Tests talk to MockWebServer on this computer; the app never does. */
    @Volatile internal var allowLoopbackForTest = false

    fun hostOf(url: String): String? = runCatching { URI(url.trim()).host?.lowercase()?.trimEnd('.')?.removePrefix("www.") }.getOrNull()

    private fun under(host: String, set: Set<String>): Boolean {
        var h = host
        while (true) {
            if (h in set) return true
            val dot = h.indexOf('.')
            if (dot < 0 || dot == h.lastIndexOf('.')) return false
            h = h.substring(dot + 1)
        }
    }

    fun isGate(url: String): Boolean = hostOf(url)?.let { under(it, GATES) } == true

    /** Looks like a shortener link worth the HTTP walk (a known gate host or a wrapper that carries its target). */
    fun looksLikeChain(url: String): Boolean = isGate(url) || AdChainPages.unwrap(url) != null

    fun isMediaOrFileHost(url: String): Boolean {
        if (MEDIA.containsMatchIn(url.substringBefore('#'))) return true
        return when (LinkHosts.classify(url)) {
            is LinkHosts.Direct, is LinkHosts.Browser, is LinkHosts.TelegramPost -> true
            else -> false
        }
    }

    /** The reason [url] must not be opened by the chain, or null when it may. */
    fun refusal(url: String): String? {
        val u = runCatching { URI(url.trim()) }.getOrNull() ?: return "not a web address"
        val scheme = u.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return "not a web address"
        if (!u.rawUserInfo.isNullOrEmpty()) return "address hides its real host"
        val host = u.host?.lowercase()?.trimEnd('.') ?: return "no host"
        if (local(host)) return "local address"
        if (under(host.removePrefix("www."), PARKED)) return "dead site"
        if (AdFilter.isAd(url)) return "ad"
        return null
    }

    private fun local(host: String): Boolean {
        if (allowLoopbackForTest && (host == "localhost" || host == "127.0.0.1")) return false
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local") || host.endsWith(".internal")) return true
        if (host.startsWith("[") || host.contains(':')) return true // IPv6 literals: never needed by a shortener
        val m = IPV4.matchEntire(host) ?: return false
        val a = m.groupValues[1].toInt(); val b = m.groupValues[2].toInt()
        return a == 0 || a == 10 || a == 127 || (a == 169 && b == 254) || (a == 172 && b in 16..31) || (a == 192 && b == 168) || (a == 100 && b in 64..127) || a >= 224
    }

    /** Removes google.com/url and facebook l.php wrappers (up to 3 layers). */
    fun unwrapTracking(url: String): String {
        var cur = url.trim()
        repeat(3) {
            val host = hostOf(cur) ?: return cur
            val key = when {
                (host == "google.com" || host.endsWith(".google.com")) && "/url?" in cur -> listOf("q", "url")
                host.endsWith("facebook.com") && "/l.php" in cur -> listOf("u")
                else -> return cur
            }
            val q = cur.substringAfter('?', "")
            val next = q.split('&').firstNotNullOfOrNull { p ->
                if (p.substringBefore('=').lowercase() in key) runCatching { URLDecoder.decode(p.substringAfter('=', ""), "UTF-8") }.getOrNull() else null
            }
            if (next == null || !next.startsWith("http")) return cur
            cur = next
        }
        return cur
    }
}
