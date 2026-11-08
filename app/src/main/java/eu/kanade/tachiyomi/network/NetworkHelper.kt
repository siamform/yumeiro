package eu.kanade.tachiyomi.network

import android.content.Context
import eu.kanade.tachiyomi.network.interceptor.ChallengeInterceptor
import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Network stack handed to Aniyomi / Mihon extensions (same public API as their extensions-lib).
 * Built on Yumeiro's shared client, so smart DNS / DoH settings apply, with WebView cookies and
 * the in-app browser check for Cloudflare-style challenges.
 */
class NetworkHelper(private val context: Context) {

    val cookieJar = AndroidCookieJar()

    val client: OkHttpClient = dev.asfakul.yumeiro.network.NetworkClient.get(context).newBuilder()
        .cookieJar(cookieJar)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(2, TimeUnit.MINUTES)
        .cache(Cache(File(context.cacheDir, "tachiyomi_network_cache"), 5L * 1024 * 1024))
        .addInterceptor(UncaughtExceptionInterceptor())
        .addInterceptor(UserAgentInterceptor(::defaultUserAgentProvider))
        .addInterceptor(ChallengeInterceptor(::defaultUserAgentProvider))
        .build()

    @Deprecated("The regular client handles Cloudflare by default")
    @Suppress("UNUSED")
    val cloudflareClient: OkHttpClient = client

    fun defaultUserAgentProvider(): String = dev.asfakul.yumeiro.online.web.BrowserCheck.userAgent()
}
