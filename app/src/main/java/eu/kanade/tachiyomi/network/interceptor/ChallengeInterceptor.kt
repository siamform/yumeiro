package eu.kanade.tachiyomi.network.interceptor

import android.webkit.CookieManager
import dev.asfakul.yumeiro.online.web.BrowserCheck
import okhttp3.Interceptor
import okhttp3.Response

/**
 * When a site answers with a browser check (Cloudflare, DDoS-Guard…), solve it once in Yumeiro's
 * WebView (headless first, then asks the user) and retry with the cleared cookies and the same
 * user agent. Replaces Aniyomi's CloudflareInterceptor.
 */
class ChallengeInterceptor(private val userAgent: () -> String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (!BrowserCheck.isChallenge(response)) return response
        val url = request.url.toString()
        val cleared = runCatching { BrowserCheck.solveBlocking(url) }.getOrNull() ?: return response
        response.close()
        val cookies = CookieManager.getInstance().getCookie(url).orEmpty()
        val retry = request.newBuilder()
            .header("User-Agent", userAgent())
            .apply { if (cookies.isNotBlank()) header("Cookie", cookies) }
            .build()
        @Suppress("UNUSED_VARIABLE") val unused = cleared
        return chain.proceed(retry)
    }
}
