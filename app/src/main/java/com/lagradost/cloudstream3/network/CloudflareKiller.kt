package com.lagradost.cloudstream3.network

import dev.asfakul.yumeiro.online.web.BrowserCheck
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Response
import java.net.URI

/**
 * CloudStream-compatible interceptor that extensions add to requests for sites behind Cloudflare.
 * Backed by [BrowserCheck], which opens the site in a real WebView (and asks the user to tap the
 * check when the site requires it).
 */
class CloudflareKiller : Interceptor {
    companion object {
        const val TAG = "CloudflareKiller"
        fun parseCookieMap(cookie: String): Map<String, String> = cookie.split(";").associate {
            val split = it.split("=")
            (split.getOrNull(0)?.trim() ?: "") to (split.getOrNull(1)?.trim() ?: "")
        }.filter { it.key.isNotBlank() && it.value.isNotBlank() }
    }

    /** Clearance cookies by host (read by some extensions to build their own requests). */
    val savedCookies: MutableMap<String, Map<String, String>> = object : java.util.AbstractMap<String, Map<String, String>>(), MutableMap<String, Map<String, String>> {
        private val extra = java.util.concurrent.ConcurrentHashMap<String, Map<String, String>>()
        override val entries: MutableSet<MutableMap.MutableEntry<String, Map<String, String>>> get() = extra.entries
        override fun get(key: String): Map<String, String>? = BrowserCheck.clearanceFor(key)?.cookies ?: extra[key]
        override fun containsKey(key: String): Boolean = get(key) != null
        override fun put(key: String, value: Map<String, String>): Map<String, String>? = extra.put(key, value)
        override fun remove(key: String): Map<String, String>? { BrowserCheck.forget(key); return extra.remove(key) }
    }

    /** Headers (WebView user agent + clearance cookies) to use for [url]. */
    fun getCookieHeaders(url: String): Headers {
        val host = runCatching { URI(url).host }.getOrNull().orEmpty()
        val ua = mapOf("user-agent" to BrowserCheck.userAgent())
        return getHeaders(ua, savedCookies[host] ?: emptyMap())
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        BrowserCheck.clearanceFor(request.url.host)?.let { request = BrowserCheck.withClearance(request, it) }
        val response = chain.proceed(request)
        if (!BrowserCheck.isChallenge(response)) return response
        BrowserCheck.forget(request.url.host)
        val url = request.url.toString()
        val clearance = BrowserCheck.solveBlocking(url, page = BrowserCheck.challengePage(url, request.method, request.header("Accept"))) ?: return response
        response.close()
        return chain.proceed(BrowserCheck.withClearance(chain.request(), clearance))
    }
}
