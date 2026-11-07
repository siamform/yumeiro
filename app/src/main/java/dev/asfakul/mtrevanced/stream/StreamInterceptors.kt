/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.mtrevanced.stream

import android.content.Context
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-stream HTTP clients for the player. Online extensions often sign every video request
 * (tokens, Authorization, rotating cookies) through their own OkHttp interceptor; without it hosts
 * answer 401/403. A stream registered here plays through a client with those interceptors.
 */
object StreamInterceptors {
    private val byUrl = ConcurrentHashMap<String, List<Interceptor>>()
    private val clients = ConcurrentHashMap<String, OkHttpClient>()

    fun register(url: String, interceptors: List<Interceptor>) {
        if (interceptors.isEmpty()) { byUrl.remove(url); clients.remove(url); return }
        byUrl[url] = interceptors; clients.remove(url)
        // Keep the map small: only the latest streams matter.
        if (byUrl.size > 24) byUrl.keys.take(byUrl.size - 24).forEach { byUrl.remove(it); clients.remove(it) }
    }

    fun has(url: String?): Boolean = url != null && byUrl.containsKey(url)

    /** Client for [url]: the app client plus the stream's interceptors, or [base] when none. */
    fun clientFor(url: String?, base: OkHttpClient): OkHttpClient {
        val list = url?.let { byUrl[it] } ?: return base
        return clients.getOrPut(url) { base.newBuilder().apply { list.forEach { addInterceptor(it) } }.build() }
    }

    internal fun clear() { byUrl.clear(); clients.clear() }
}
