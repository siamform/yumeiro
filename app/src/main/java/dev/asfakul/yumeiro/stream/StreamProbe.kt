/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import dev.asfakul.yumeiro.network.NetworkClient
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Why a probe couldn't reach the server. */
enum class NetworkFailure { DNS, TIMEOUT, CONNECTION, TLS, OTHER }

data class ProbeResult(
    val requestedUrl: String,
    val finalUrl: String,
    val status: Int,
    val contentType: String?,
    val sniff: Sniff,
    /** Body text when the response is textual (manifest, playlist, web page). */
    val text: String?,
    /** True when [text] is the complete body (not cut at the size limit). */
    val complete: Boolean,
    val retryAfterSeconds: Long?,
    val wwwAuthenticate: String?,
    val failure: NetworkFailure? = null,
    val failureMessage: String? = null,
) {
    val ok: Boolean get() = failure == null && status in 200..299
}

/**
 * Opens a URL the way the player will (same headers, User-Agent, cookies, DNS and redirect rules)
 * and inspects the response: status, final URL after redirects, Content-Type and the first bytes.
 * Only as much as needed is downloaded: 16 KB for media, up to 4 MB for text (playlists / pages).
 */
object StreamProbe {
    private const val MEDIA_PEEK = 16 * 1024L
    private const val TEXT_LIMIT = 4 * 1024 * 1024L

    fun probe(request: StreamRequest, defaultUserAgent: String, client: OkHttpClient = NetworkClient.get()): ProbeResult {
        val url = request.url.trim()
        val builder = try { Request.Builder().url(url) } catch (e: IllegalArgumentException) {
            return failed(url, NetworkFailure.OTHER, "Not a valid http(s) URL")
        }
        builder.header("User-Agent", request.userAgent ?: defaultUserAgent)
        builder.header("Accept", "*/*")
        request.requestHeaders().forEach { (k, v) -> runCatching { builder.header(k, v) } }
        return try {
            client.newCall(builder.get().build()).execute().use { response ->
                val body = response.body
                val source = body.source()
                // Peek the beginning first; keep reading only for textual content.
                source.request(MEDIA_PEEK)
                val head = source.buffer.snapshot().toByteArray().let { if (it.size > MEDIA_PEEK) it.copyOf(MEDIA_PEEK.toInt()) else it }
                val contentType = response.header("Content-Type")
                val sniff = StreamSniffer.classify(contentType, response.request.url.toString(), head)
                val textual = sniff.kind in setOf(MediaKind.HLS, MediaKind.DASH, MediaKind.SMOOTH, MediaKind.IPTV_PLAYLIST, MediaKind.WEB_PAGE) ||
                    (sniff.kind == MediaKind.UNKNOWN && looksTextual(head))
                var complete = false
                val text = if (textual) {
                    complete = !source.request(TEXT_LIMIT)
                    val bytes = source.buffer.snapshot().toByteArray()
                    (if (bytes.size > TEXT_LIMIT) bytes.copyOf(TEXT_LIMIT.toInt()) else bytes).decodeToString()
                } else null
                ProbeResult(
                    requestedUrl = url,
                    finalUrl = response.request.url.toString(),
                    status = response.code,
                    contentType = contentType,
                    sniff = sniff,
                    text = text,
                    complete = complete,
                    retryAfterSeconds = response.header("Retry-After")?.let(RetryAfter::parseSeconds),
                    wwwAuthenticate = response.header("WWW-Authenticate"),
                )
            }
        } catch (e: IOException) {
            failed(url, classify(e), e.message ?: e.javaClass.simpleName)
        }
    }

    fun classify(e: Throwable): NetworkFailure {
        var t: Throwable? = e
        while (t != null) {
            when (t) {
                is UnknownHostException -> return NetworkFailure.DNS
                is SSLException -> return NetworkFailure.TLS
                is ConnectException, is NoRouteToHostException -> return NetworkFailure.CONNECTION
                is InterruptedIOException -> return NetworkFailure.TIMEOUT // includes SocketTimeoutException
            }
            t = t.cause
        }
        return NetworkFailure.OTHER
    }

    private fun looksTextual(head: ByteArray): Boolean {
        if (head.isEmpty()) return false
        val sample = head.take(512)
        return sample.count { val b = it.toInt() and 0xFF; b == 9 || b == 10 || b == 13 || b in 32..126 || b >= 0xC0 } > sample.size * 0.9
    }

    private fun failed(url: String, f: NetworkFailure, message: String) = ProbeResult(
        url, url, 0, null, Sniff(MediaKind.UNKNOWN, null, "none"), null, false, null, null, f, message,
    )
}

/** `Retry-After` header: delay in seconds or an HTTP date (RFC 9110 §10.2.3). */
object RetryAfter {
    fun parseSeconds(value: String, now: Long = System.currentTimeMillis()): Long? {
        val v = value.trim()
        v.toLongOrNull()?.let { return it.coerceAtLeast(0) }
        return runCatching {
            val fmt = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US)
            ((fmt.parse(v)!!.time - now) / 1000).coerceAtLeast(0)
        }.getOrNull()
    }

    fun fromHeaders(headers: Map<out String?, List<String>>?): Long? =
        headers?.entries?.firstOrNull { it.key.equals("Retry-After", ignoreCase = true) }?.value?.firstOrNull()?.let(::parseSeconds)
}
