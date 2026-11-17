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
        val chain = generateSequence(e) { it.cause }.take(8).toList()
        val root = chain.last()
        val m = (e.message ?: root.message).orEmpty()
        return when {
            e is ExtensionManager.ExtensionException -> m
            dev.asfakul.yumeiro.youtube.YtAccount.accountCanHelp(e) -> dev.asfakul.yumeiro.youtube.YtAccount.blockedHint()
            // Any link, not only the root: Android's UnknownHostException carries a GaiException as its
            // cause, so a root-only check showed the raw "Unable to resolve host" text (device run 43702633505).
            // G10 ER-14: a full disk outside downloads (backup, extension or playlist install) showed
            // the raw "write failed: ENOSPC (No space left on device)".
            chain.any { val t = it.message.orEmpty(); "ENOSPC" in t || "No space left" in t } ->
                "Not enough storage. Free up some space, then try again"
            // G7 LT-2: a file the app can't write (folder not writable, read-only storage) showed the raw
            // "/data/user/0/…/playlists.json.tmp: open failed: EACCES (Permission denied)".
            chain.any { val t = it.message.orEmpty(); "EACCES" in t || "EROFS" in t || "EPERM" in t || ("open failed:" in t && "/data/" in t) } ->
                "The app couldn't use its storage on this device. Restart the app, then try again"
            chain.any { it is java.net.UnknownHostException } ->
                if (!dev.asfakul.yumeiro.network.Connectivity.online.value) "No internet connection"
                else "This site can't be reached on your network. It may be blocked — try again, or turn a VPN on or off"
            chain.any { it is java.net.ConnectException || it is java.net.NoRouteToHostException ||
                (it is java.net.SocketException && it.message.orEmpty().contains("reset", true)) } ->
                "Your network refused the connection. The site may be blocked here — try a VPN"
            chain.any { it is java.net.SocketTimeoutException || it is java.io.InterruptedIOException } ||
                e is kotlinx.coroutines.TimeoutCancellationException -> "The server took too long to answer"
            chain.any { it is javax.net.ssl.SSLException } -> "Secure connection failed"
            m.startsWith("HTTP 404") || m.contains("HTTP 404") -> "Not found (404)"
            // R17 G10 (ER-1): 4xx answers are not server errors; say what the server said.
            m.startsWith("HTTP") -> when (m.substringAfter("HTTP ").take(3).toIntOrNull()) {
                401 -> "Access denied: the server needs authorization (HTTP 401)"
                403 -> "Access denied by the server (HTTP 403)"
                410 -> "Removed from the server (HTTP 410)"
                429 -> "Too many requests, wait a minute and retry (HTTP 429)"
                451 -> "Not available in your country (HTTP 451)"
                else -> "Server error ($m)"
            }
            root is org.json.JSONException -> "The server sent unreadable data"
            root is NotImplementedError -> "This extension doesn't support that"
            isBadLink(root) || isBadLink(e) -> "The site sent a broken link for this source — try another source"
            m.isNotBlank() -> m.take(160)
            else -> fallback
        }
    }
}
