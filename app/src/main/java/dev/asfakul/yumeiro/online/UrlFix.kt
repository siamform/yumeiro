/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Repairs the URLs extensions hand to the HTTP client. Sites often return protocol-relative
 * (`//cdn.host/x`), relative (`/embed/1`), JSON-escaped (`https:\/\/…`) or padded links; extensions
 * pass them straight to OkHttp, which throws "Expected URL scheme 'http' or 'https' but no scheme
 * was found". That isn't the extension being outdated, just a sloppy link — so fix it here.
 */
object UrlFix {
    /** Returns a usable absolute http(s) URL for [raw], or [raw] unchanged when it can't be repaired. */
    fun normalize(raw: String, base: String? = null): String {
        var u = raw.trim().trim('"', '\'')
        if (u.isEmpty()) return raw
        u = u.replace("\\/", "/").replace("&amp;", "&").replace(" ", "%20")
        val lower = u.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://")) return u
        // "https:/host" / "https:host" typos.
        Regex("^(https?):/*(?=[^/])", RegexOption.IGNORE_CASE).find(u)?.let { m ->
            return m.groupValues[1].lowercase() + "://" + u.substring(m.range.last + 1)
        }
        if (u.startsWith("//")) return "https:$u"
        val b = base?.trim()?.takeIf { it.startsWith("http", ignoreCase = true) }?.replace("\\/", "/")
        if (b != null) {
            val resolved = runCatching { java.net.URI(b).resolve(u.replace("|", "%7C")).toString() }.getOrNull()
            if (resolved != null && resolved.startsWith("http", ignoreCase = true)) return resolved
        }
        // Bare host ("cdn.example.com/x.m3u8").
        if (Regex("^[a-z0-9-]+(\\.[a-z0-9-]+)+(:\\d+)?(/.*)?$", RegexOption.IGNORE_CASE).matches(u)) return "https://$u"
        return raw
    }

    fun isHttp(u: String?): Boolean = u != null && (u.startsWith("http://", true) || u.startsWith("https://", true))
}

/** The extension whose code is running, so relative links can be resolved against its site. */
class ProviderBase(val mainUrl: String) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<ProviderBase>
}
