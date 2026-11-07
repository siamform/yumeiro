/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.network

import android.content.Context
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * One shared OkHttp client for stream probing, playlist downloads and playback, so DNS settings,
 * connection pooling and session cookies are consistent everywhere. Redirects are followed,
 * including http ⇄ https (many IPTV hosts redirect across protocols).
 */
object NetworkClient {
    @Volatile private var client: OkHttpClient? = null

    /** Session cookies set by stream servers (token / CDN cookies). Kept in memory only. */
    val cookies = SessionCookies()

    fun get(context: Context? = null): OkHttpClient {
        context?.let { NetworkSettings.init(it.applicationContext) }
        client?.let { return it }
        return synchronized(this) {
            client ?: OkHttpClient.Builder()
                .dns(SmartDns)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .addNetworkInterceptor(cookies)
                .build()
                .also { client = it }
        }
    }

    /** Client that reports redirects instead of following them (link analysis shows the chain). */
    fun noRedirects(context: Context? = null): OkHttpClient =
        get(context).newBuilder().followRedirects(false).followSslRedirects(false).build()
}

/**
 * Minimal in-memory cookie store applied as a network interceptor (so cookies set on any redirect
 * hop are kept). Unlike OkHttp's CookieJar it **merges** with a user-supplied `Cookie` header
 * instead of replacing it, so cookies from playlists / the Headers form are never lost.
 */
class SessionCookies : Interceptor {
    private val store = ConcurrentHashMap<String, Cookie>()

    fun clear() = store.clear()

    fun cookiesFor(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        store.entries.removeAll { it.value.expiresAt < now }
        return store.values.filter { it.matches(url) }
    }

    fun save(url: HttpUrl, setCookieHeaders: List<String>) {
        if (setCookieHeaders.isEmpty()) return
        val headers = okhttp3.Headers.Builder().apply { setCookieHeaders.forEach { add("Set-Cookie", it) } }.build()
        Cookie.parseAll(url, headers).forEach { c -> store["${c.name}|${c.domain}|${c.path}"] = c }
        if (store.size > 300) store.keys.take(store.size - 300).forEach { store.remove(it) }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        val jar = cookiesFor(request.url)
        if (jar.isNotEmpty()) {
            val existing = request.header("Cookie")?.takeIf { it.isNotBlank() }
            val existingNames = existing?.split(';')?.map { it.substringBefore('=').trim() }?.toSet().orEmpty()
            val extra = jar.filter { it.name !in existingNames }.joinToString("; ") { "${it.name}=${it.value}" }
            if (extra.isNotEmpty()) {
                request = request.newBuilder().header("Cookie", listOfNotNull(existing, extra).joinToString("; ")).build()
            }
        }
        val response = chain.proceed(request)
        save(request.url, response.headers("Set-Cookie"))
        return response
    }
}
