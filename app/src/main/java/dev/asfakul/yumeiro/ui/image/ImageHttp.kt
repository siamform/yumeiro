/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.image

import android.content.Context
import dev.asfakul.yumeiro.BuildConfig
import dev.asfakul.yumeiro.network.NetworkClient
import okhttp3.Interceptor
import okhttp3.OkHttpClient

/**
 * NBUG-14 (NB14-2, ADR-026): the HTTP client for pictures (posters, channel logos, the widget).
 *
 * It is the shared [NetworkClient] (same DNS, pool, request cap) plus a named User-Agent on requests
 * that have none. Without it OkHttp sends `okhttp/<version>`, which some image hosts refuse:
 * upload.wikimedia.org answers 403 "Please set a user-agent and respect our robot policy" (thousands
 * of IPTV channel logos live there). A request's own User-Agent (a channel's headers) still wins.
 */
object ImageHttp {
    fun userAgent(): String = "Yumeiro/${BuildConfig.VERSION_NAME} (https://github.com/siamform/yumeiro)"

    /** Adds [userAgent] when the request has no User-Agent of its own. */
    val namedAgent = Interceptor { chain ->
        val req = chain.request()
        chain.proceed(if (req.header("User-Agent").isNullOrBlank()) req.newBuilder().header("User-Agent", userAgent()).build() else req)
    }

    @Volatile private var client: OkHttpClient? = null

    fun client(context: Context): OkHttpClient = client ?: synchronized(this) {
        client ?: NetworkClient.get(context).newBuilder().addInterceptor(namedAgent).build().also { client = it }
    }
}
