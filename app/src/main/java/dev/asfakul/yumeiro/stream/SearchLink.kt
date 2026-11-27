/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import java.net.URI
import java.util.Locale

/**
 * F3 (Group T, audits/AUDIT_T.md): a link, magnet or torrent info-hash typed or pasted into a search box is played
 * instead of being searched as words. Pure, no Regex; null means "search it as words".
 */
object SearchLink {
    private val SCHEMES = listOf("http://", "https://", "rtsp://", "rtsps://", "rtmp://")

    fun parse(query: String): String? {
        val t = query.trim()
        if (t.isEmpty() || t.length > 4096 || t.any { it.isWhitespace() }) return null
        val lower = t.lowercase(Locale.ROOT)
        if (lower.startsWith("magnet:?") && lower.contains("xt=urn:bt")) return t
        if (SCHEMES.any { lower.startsWith(it) }) {
            val host = runCatching { URI(t).host }.getOrNull()
                ?: t.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#').substringAfterLast('@').substringBefore(':')
            val real = host.contains('.') || host.contains(':') || host.equals("localhost", ignoreCase = true)
            return t.takeIf { real }
        }
        // A bare 40-hex / 32-base32 info-hash. Base32 hashes must contain a digit so a long one-word title is not one.
        if (t.any { it.isDigit() }) LinkHosts.magnetFromHash(t)?.let { return it }
        return null
    }
}
