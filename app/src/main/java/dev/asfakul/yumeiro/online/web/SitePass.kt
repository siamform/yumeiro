/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.web

/**
 * B4 (Group M): which extensions a passed site check belongs to. Pure → unit-tested.
 *
 * A pass is per site and the cookie covers its subdomains, so an extension whose address is
 * `https://www.site.to/` belongs to a pass on `site.to`, and one on `api.site.to` too. Port and path
 * don't matter (a local test server on another port is a different site, though: the host must match).
 */
object SitePass {
    fun hostOf(url: String?): String? {
        val u = url?.trim().orEmpty()
        if (u.isEmpty()) return null
        val withScheme = if ("://" in u) u else "https://$u"
        return runCatching { java.net.URI(withScheme).host }.getOrNull()?.let(VerificationCenter::normalize)?.takeIf { it.isNotBlank() }
    }

    /** True when a pass on [passedHost] lets the extension at [mainUrl] through. */
    fun matches(mainUrl: String?, passedHost: String?): Boolean {
        val a = hostOf(mainUrl) ?: return false
        val b = passedHost?.let(VerificationCenter::normalize)?.takeIf { it.isNotBlank() } ?: return false
        return a == b || a.endsWith(".$b") || b.endsWith(".$a")
    }
}
