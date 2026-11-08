/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException

/**
 * Some networks don't refuse a blocked site's name; they hand out an address that resets the
 * connection or breaks TLS (common with ISP filtering). When that happens to a host that was
 * resolved by the system DNS, mark it for the encrypted-DNS bypass and retry the request once.
 * Only GET/HEAD are retried, so nothing is ever sent twice.
 */
object AutoBypassInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return try {
            chain.proceed(request)
        } catch (e: IOException) {
            val host = request.url.host
            val config = NetworkSettings.dns.value
            val eligible = config.auto && !config.usesAlternative &&
                request.method in RETRYABLE && looksLikeBlocking(e) &&
                SmartDns.lastSourceFor(host) == DnsSource.SYSTEM &&
                Connectivity.online.value &&
                !chain.call().isCanceled()
            if (!eligible || !SmartDns.markBlocked(host)) throw e
            android.util.Log.i("AutoBypass", "${e.javaClass.simpleName} on a system-resolved host: retrying over encrypted DNS")
            try { chain.proceed(request) } catch (retry: IOException) { retry.addSuppressed(e); throw retry }
        }
    }

    private val RETRYABLE = setOf("GET", "HEAD")

    /** Failure shapes typical of DNS / SNI filtering (not plain timeouts on slow servers). */
    internal fun looksLikeBlocking(e: IOException): Boolean {
        val chain = generateSequence<Throwable>(e) { it.cause }.take(6).toList()
        return chain.any { t ->
            t is ConnectException || t is NoRouteToHostException || t is SSLException ||
                (t is SocketException && t.message.orEmpty().let { m -> "reset" in m.lowercase() || "refused" in m.lowercase() }) ||
                (t is SocketTimeoutException && t.message.orEmpty().contains("connect", ignoreCase = true))
        }
    }
}
