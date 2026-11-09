/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import android.content.Context
import com.lagradost.api.setContext
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.online.web.BrowserCheck
import java.lang.ref.WeakReference

/** One-time wiring of the bundled CloudStream library into the app. */
object OnlineRuntime {
    /** Plugin API version this app implements (CloudStream's `apiVersion` in plugins.json). */
    const val API_VERSION = 1

    @Volatile private var initialized = false
    @Volatile private var extClient: okhttp3.OkHttpClient? = null

    /** HTTP client for extension traffic: the app's client + browser-check handling. */
    fun extensionClient(context: Context): okhttp3.OkHttpClient = extClient ?: synchronized(this) {
        extClient ?: NetworkClient.get(context.applicationContext).newBuilder()
            .addInterceptor(BrowserCheck.Interceptor)
            .build().also { extClient = it }
    }

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val app = context.applicationContext
            runCatching { setContext(WeakReference<Any>(app)) }
            runCatching { com.lagradost.cloudstream3.CloudStreamApp.init(app) }
            // Same id keys as CloudStream, so extensions' addMalId/addAniListId/addImdbId land in
            // separate entries (AniSkip, AniList/MAL sync and subtitle search read them).
            runCatching {
                com.lagradost.cloudstream3.LoadResponse.malIdPrefix = "mal"
                com.lagradost.cloudstream3.LoadResponse.kitsuIdPrefix = "kitsu"
                com.lagradost.cloudstream3.LoadResponse.aniListIdPrefix = "anilist"
                com.lagradost.cloudstream3.LoadResponse.simklIdPrefix = "simkl"
            }
            runCatching { with(com.lagradost.cloudstream3.ui.settings.Globals) { app.updateTv() } }
            runCatching { BrowserCheck.init(app) }
            runCatching { ProviderOverrides.init(app) }
            runCatching { OnlineSettings.init(app) }
            // Extensions use CloudStream's `app` HTTP helper: route it through our client so they get
            // the same DNS fallback / DNS-over-HTTPS, timeouts and cookie handling as the rest of the
            // app, plus automatic handling of "checking your browser" pages (BrowserCheck).
            runCatching {
                val client = extensionClient(app)
                val current = com.lagradost.cloudstream3.app
                com.lagradost.cloudstream3.app = if (current is SafeRequests) current.also { it.baseClient = client }
                    else SafeRequests(current, client)
            }.onFailure { runCatching { com.lagradost.cloudstream3.app.baseClient = extensionClient(app) } }
            initialized = true
        }
    }
}

/** Human-readable messages for network / extension failures. */
object OnlineErrors {
    /** OkHttp's message for a link without http(s) — a site/extension data problem, not an outdated extension. */
    fun isBadLink(e: Throwable): Boolean {
        val m = e.message.orEmpty()
        return e is IllegalArgumentException && (m.contains("Expected URL scheme") || m.contains("unexpected url") ||
            m.contains("Invalid URL host") || m.contains("no scheme was found"))
    }

    /** True when updating the extension is a sensible suggestion for this error. */
    fun extensionMayBeOutdated(e: Throwable): Boolean {
        val root = generateSequence(e) { it.cause }.last()
        return !isBadLink(root) && !isBadLink(e) && (root is NoSuchMethodError || root is NoClassDefFoundError ||
            root is ClassCastException || root is AbstractMethodError || root is NullPointerException ||
            root is IndexOutOfBoundsException || root is NoSuchElementException ||
            root.javaClass.name.contains("Json", true) || root.javaClass.name.contains("Selector", true))
    }

    fun friendly(e: Throwable, fallback: String = "Something went wrong"): String {
        val root = generateSequence(e) { it.cause }.last()
        val m = (e.message ?: root.message).orEmpty()
        return when {
            e is ExtensionManager.ExtensionException -> m
            dev.asfakul.yumeiro.youtube.YtAccount.accountCanHelp(e) -> dev.asfakul.yumeiro.youtube.YtAccount.blockedHint()
            root is java.net.UnknownHostException ->
                if (!dev.asfakul.yumeiro.network.Connectivity.online.value) "No internet connection"
                else "This site can't be reached on your network. It may be blocked — try again, or turn a VPN on or off"
            root is java.net.ConnectException || root is java.net.NoRouteToHostException ||
                (root is java.net.SocketException && root.message.orEmpty().contains("reset", true)) ->
                "Your network refused the connection. The site may be blocked here — try a VPN"
            root is java.net.SocketTimeoutException || root is java.io.InterruptedIOException ||
                e is kotlinx.coroutines.TimeoutCancellationException -> "The server took too long to answer"
            root is javax.net.ssl.SSLException -> "Secure connection failed"
            m.startsWith("HTTP 404") || m.contains("HTTP 404") -> "Not found (404)"
            m.startsWith("HTTP") -> "Server error ($m)"
            root is org.json.JSONException -> "The server sent unreadable data"
            root is NotImplementedError -> "This extension doesn't support that"
            isBadLink(root) || isBadLink(e) -> "The site sent a broken link for this source — try another source"
            m.isNotBlank() -> m.take(160)
            else -> fallback
        }
    }
}
