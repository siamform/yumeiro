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
        Log.w(TAG, "${AdChainDestinations.hostOf(request.url)}: ${out::class.simpleName} ${(out as? AdChainHttp.Outcome.Reached)?.hops ?: ""}")
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
                when {
                    out.url == request.url -> null
                    prior is Resolution.NeedsBrowser -> prior.copy(pageUrl = out.url)
                    else -> Resolution.NeedsBrowser(out.url, out.reason, request.copy(userAgent = ua))
                }
            }
            else -> null
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
