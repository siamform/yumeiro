/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import dev.asfakul.yumeiro.adchain.AdChainDestinations
import dev.asfakul.yumeiro.adchain.AdChainHttp
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.online.web.BrowserCheck
import dev.asfakul.yumeiro.online.web.BrowserFilters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie

/**
 * ADCHAIN-3 (owner item 28, ADR-064): the one place AdChain joins [StreamResolver]. Only for shortener links
 * (a known gate host or a wrapper carrying its target), only when "Resolve links" is on, and only after the
 * normal resolver said "needs the browser" or failed — every other link is untouched.
 *
 * The HTTP walk ([AdChainHttp]) either reaches the link (then the normal resolver plays / downloads it, with
 * the chain's cookies, referer and the WebView's user agent), or stops at a step that needs a browser or a
 * person: then its cookies go into the WebView's cookie store (domain, path, expiry, secure kept) and Yumeiro's
 * existing page browser + link walker (ADCHAIN-1/2, BrowserCheckHost) continue from that step. A failed walk
 * returns null, so the user gets exactly what they got before (never trapped).
 */
object AdChainBridge {
    private const val TAG = "AdChain"

    suspend fun after(context: Context, request: StreamRequest, prior: Resolution, thorough: Boolean): Resolution? {
        if (prior !is Resolution.NeedsBrowser && prior !is Resolution.Failed) return null
        if (!BrowserFilters.resolveLinks.value) return null
        if (!AdChainDestinations.looksLikeChain(request.url)) return null
        val ua = runCatching { BrowserCheck.userAgent() }.getOrNull() ?: return null
        val client = StreamInterceptors.clientFor(request.url, NetworkClient.get(context))
        val walker = AdChainHttp(client, ua)
        val out = walker.walk(request.url)
        // ADCHAIN-3c: readable outcome in release builds too (R8 renames the classes: probe 45530077234 logged "e4").
        // Hosts and reasons only; never the full link, cookies or tokens.
        val label = when (out) {
            is AdChainHttp.Outcome.Reached -> "Reached ${AdChainDestinations.hostOf(out.url)} in ${out.hops} hops"
            is AdChainHttp.Outcome.NeedsBrowser -> "NeedsBrowser at ${AdChainDestinations.hostOf(out.url)}${out.check?.let { " (check $it)" } ?: ""}: ${out.reason.take(120)}"
            is AdChainHttp.Outcome.NotAChain -> "NotAChain"
            is AdChainHttp.Outcome.Failed -> "Failed at ${AdChainDestinations.hostOf(out.url)}: ${out.reason.take(120)}"
        }
        Log.w(TAG, "${AdChainDestinations.hostOf(request.url)}: $label")
        return when (out) {
            is AdChainHttp.Outcome.Reached -> {
                val req = request.copy(
                    url = out.url,
                    referer = out.referer ?: request.referer,
                    userAgent = ua,
                    cookie = walker.jar.header(out.url) ?: request.cookie,
                    pageUrl = request.pageUrl ?: request.url,
                )
                val r = withContext(Dispatchers.IO) { StreamResolver.resolveBlocking(req, ua, StreamInterceptors.clientFor(req.url, NetworkClient.get(context)), thorough) }
                when (r) {
                    is Resolution.Failed -> { syncCookies(out.cookies); Resolution.NeedsBrowser(out.url, prior.reasonOr(), req) }
                    else -> r
                }
            }
            is AdChainHttp.Outcome.NeedsBrowser -> {
                syncCookies(out.cookies)
                needsBrowser(prior, out, request, ua)
            }
            else -> null
        }
    }

    internal const val SHORT_LINK_REASON = "This is a short link. Open it in the page browser: Yumeiro goes through its " +
        "steps and walks on to the real link by itself. If the site asks you to tick a box, do it there (Yumeiro " +
        "doesn't answer these checks for you)."

    internal const val SHORT_LINK_TITLE = "This is a short link"
    internal const val CHECK_PAGE_TITLE = "The site asks for a check"

    /**
     * What the card says when the walk stopped at a step that needs the page browser. Pure.
     * ADCHAIN-3d (probes 45530077234 / 45533723311) and 3e (probe 45537876436): srnky.com stops on its first page
     * (out.url == request.url). That branch used to return null, so the resolver's generic "a web page, not a
     * media file … start playback" text stayed, which is wrong for a short link. The page to open is unchanged
     * in that case; only the title and text say what the page browser will do.
     */
    internal fun needsBrowser(
        prior: Resolution,
        out: AdChainHttp.Outcome.NeedsBrowser,
        request: StreamRequest,
        ua: String,
    ): Resolution {
        val reason = if (out.check != null) StreamResolver.CHECK_PAGE_REASON else SHORT_LINK_REASON
        val title = if (out.check != null) CHECK_PAGE_TITLE else SHORT_LINK_TITLE
        return when {
            prior is Resolution.NeedsBrowser && out.url == request.url -> prior.copy(reason = reason, title = title)
            prior is Resolution.NeedsBrowser -> prior.copy(pageUrl = out.url, reason = reason, title = title)
            else -> Resolution.NeedsBrowser(out.url, reason, request.copy(userAgent = ua), title = title)
        }
    }

    private fun Resolution.reasonOr(): String = (this as? Resolution.NeedsBrowser)?.reason ?: "Open the link's page to finish"

    /** Hands the chain's session to the WebView so the browser continues the same visit. */
    private suspend fun syncCookies(cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        withContext(Dispatchers.Main) {
            runCatching {
                val cm = CookieManager.getInstance()
                for (c in cookies) {
                    val url = "${if (c.secure) "https" else "http"}://${c.domain}${c.path}"
                    cm.setCookie(url, c.toString())
                }
                cm.flush()
            }
        }
    }
}
