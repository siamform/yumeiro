package com.lagradost.cloudstream3.network

import com.lagradost.cloudstream3.app
import dev.asfakul.mtrevanced.online.web.BrowserCheck
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * CloudStream-compatible DDoS-Guard helper: fetches the site's check cookies; falls back to a
 * WebView visit ([BrowserCheck]) when that is not enough.
 * @param alwaysBypass fetch the cookies before the first request instead of after a 403.
 */
class DdosGuardKiller(private val alwaysBypass: Boolean) : Interceptor {
    val savedCookiesMap = mutableMapOf<String, Map<String, String>>()
    private var ddosBypassPath: String? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (alwaysBypass) return bypass(chain, request) ?: chain.proceed(request)
        val response = chain.proceed(request)
        if (response.code != 403) return response
        val retried = bypass(chain, request) ?: return response
        response.close()
        return retried
    }

    private fun bypass(chain: Interceptor.Chain, request: Request): Response? = runCatching {
        val host = request.url.host
        val cookies = savedCookiesMap[host] ?: runBlocking {
            ddosBypassPath = ddosBypassPath ?: Regex("'(.*?)'").find(app.get("https://check.ddos-guard.net/check.js").text)?.groupValues?.get(1)
            app.get(request.url.scheme + "://" + host + (ddosBypassPath ?: ""), headers = mapOf(BrowserCheck.SKIP_HEADER to "1")).cookies
        }.also { savedCookiesMap[host] = it }
        val withCookies = request.newBuilder().headers(getHeaders(request.headers.associate { it.first to it.second }, cookies + requestCookies(request))).build()
        val response = chain.proceed(withCookies)
        if (response.code != 403) return@runCatching response
        val clearance = BrowserCheck.solveBlocking(request.url.toString()) ?: return@runCatching response
        response.close()
        chain.proceed(BrowserCheck.withClearance(withCookies, clearance))
    }.getOrNull()

    private fun requestCookies(request: Request): Map<String, String> =
        CloudflareKiller.parseCookieMap(request.header("Cookie").orEmpty())
}
