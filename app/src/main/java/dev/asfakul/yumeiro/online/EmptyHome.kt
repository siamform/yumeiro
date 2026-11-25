/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import com.lagradost.cloudstream3.MainAPI
import dev.asfakul.yumeiro.online.web.BrowserCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * B-32: an extension's home came back with no titles and no error ("Nothing came back from this
 * extension"). Yumeiro now opens the extension's site once and says why, in plain words:
 * a browser check, a firewall block, a moved address, a site error, no connection, or a site that
 * works while the extension finds nothing on it (the extension is out of date for that site).
 *
 * Found on HindiSubAnime (phisher98 AnimeDekhoProvider): hindisubanime.co answers 200 with 15 shows
 * per page in `article.post` cards, but the extension only reads `a.dgt-card` (AnimeDekho's new
 * layout), so every row is empty. That is the extension's bug; CloudStream shows the same empty home.
 */
object EmptyHome {
    enum class Kind { BROWSER_CHECK, BLOCKED, MOVED, SITE_ERROR, UNREACHABLE, NOTHING_PARSED }

    data class Diagnosis(val kind: Kind, val message: String, val site: String, val movedTo: String? = null)

    /** Pure → unit-testable. [finalUrl] is where redirects ended; null when the request failed. */
    fun explain(site: String, code: Int?, finalUrl: String?, verdict: BrowserCheck.Verdict, error: Throwable?): Diagnosis {
        val host = hostOf(site) ?: site
        if (error != null || code == null) {
            val why = error?.let { OnlineErrors.friendly(it, "the site didn't answer") } ?: "the site didn't answer"
            return Diagnosis(Kind.UNREACHABLE, "Couldn't reach $host: $why", site)
        }
        if (verdict == BrowserCheck.Verdict.CHALLENGE)
            return Diagnosis(Kind.BROWSER_CHECK, "$host asks for a browser check first. Tap Open site, pass the check, then Retry", site)
        if (verdict == BrowserCheck.Verdict.BLOCKED)
            return Diagnosis(Kind.BLOCKED, "$host's firewall blocked this connection. Try another network, or change DNS in Settings › Network", site)
        if (verdict == BrowserCheck.Verdict.RATE_LIMITED)
            return Diagnosis(Kind.SITE_ERROR, "$host is getting too many requests right now. Try again in a minute", site)
        val moved = finalUrl?.let(::hostOf)?.takeIf { bare(it) != bare(host) }
        if (moved != null && code in 200..399) {
            val base = finalUrl.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host}" + (if (it.port != defaultPort(it.scheme)) ":${it.port}" else "") }
            return Diagnosis(Kind.MOVED, "$host moved to $moved. Tap Use new address", site, base)
        }
        if (code >= 400) return Diagnosis(Kind.SITE_ERROR, "$host answered with error $code. The site may be down; try again later", site)
        return Diagnosis(
            Kind.NOTHING_PARSED,
            "$host works, but this extension found no titles on it. The extension is probably out of date for the site; update it in Extensions or tell its author",
            site,
        )
    }

    /** One GET of the extension's site, off the main thread. Never throws. */
    suspend fun probe(api: MainAPI, http: OkHttpClient): Diagnosis = withContext(Dispatchers.IO) {
        val site = runCatching { api.mainUrl }.getOrNull().orEmpty().trim().trimEnd('/')
        if (site.toHttpUrlOrNull() == null) return@withContext explain(site.ifBlank { api.name }, null, null, BrowserCheck.Verdict.NONE, IllegalArgumentException("no site address"))
        val client = http.newBuilder().callTimeout(15, TimeUnit.SECONDS).build()
        runCatching {
            client.newCall(Request.Builder().url("$site/").header("User-Agent", runCatching { BrowserCheck.userAgent() }.getOrDefault("Mozilla/5.0")).build()).execute().use { r ->
                explain(site, r.code, r.request.url.toString(), runCatching { BrowserCheck.verdict(r) }.getOrDefault(BrowserCheck.Verdict.NONE), null)
            }
        }.getOrElse { explain(site, null, null, BrowserCheck.Verdict.NONE, it) }
    }

    private fun hostOf(url: String): String? = url.toHttpUrlOrNull()?.host
    private fun bare(h: String) = h.lowercase().removePrefix("www.")
    private fun defaultPort(scheme: String) = if (scheme == "https") 443 else 80
}
