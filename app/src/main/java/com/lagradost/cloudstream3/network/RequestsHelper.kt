package com.lagradost.cloudstream3.network

import android.content.Context
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.nicehttp.Requests
import dev.asfakul.yumeiro.online.OnlineRuntime
import okhttp3.Headers
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient

/** CloudStream-compatible client setup: extensions get the app's network stack (DNS, browser checks). */
fun Requests.initClient(context: Context) {
    this.baseClient = buildDefaultClient(context)
}

fun Requests.initClient(context: Context, ignoreSSL: Boolean = false) {
    this.baseClient = buildDefaultClient(context, ignoreSSL)
}

fun buildDefaultClient(context: Context): OkHttpClient = buildDefaultClient(context, false)

fun buildDefaultClient(context: Context, ignoreSSL: Boolean = false): OkHttpClient =
    OnlineRuntime.extensionClient(context)

private val DEFAULT_HEADERS = mapOf("user-agent" to USER_AGENT)

/** Set headers > set cookies > default headers. */
fun getHeaders(headers: Map<String, String>, cookie: Map<String, String>): Headers {
    val cookieMap = if (cookie.isNotEmpty()) mapOf("Cookie" to cookie.entries.joinToString(" ") { "${it.key}=${it.value};" }) else mapOf()
    return (DEFAULT_HEADERS + headers + cookieMap).toHeaders()
}
