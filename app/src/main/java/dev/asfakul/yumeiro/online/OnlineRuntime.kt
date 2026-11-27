/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import dev.asfakul.yumeiro.l10n.tr
import dev.asfakul.yumeiro.R
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
            .addNetworkInterceptor(BrowserCheck.HopInterceptor) // VERIFY-2: the pass and its agent on redirect hops too
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
            runCatching { SiteClones.init(app) }
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
    /** G10 ER-21: text for a socket or DNS call that Android or a firewall app blocked. */
    val NETWORK_BLOCKED: String get() =
        tr(R.string.android_or_a_firewall_app_is, "Android or a firewall app is blocking Yumeiro's internet. Allow Wi-Fi and mobile data for Yumeiro in Settings › Apps")

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

    fun friendly(e: Throwable, fallback: String = tr(R.string.something_went_wrong, "Something went wrong")): String {
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
                tr(R.string.not_enough_storage_free_up_some, "Not enough storage. Free up some space, then try again")
            // G10 ER-21: EPERM/EACCES on a socket, connect or DNS lookup means Android (data saver,
            // "Allow network access" off, Private DNS) or a firewall app blocks the app's internet.
            // Chromium maps the same errnos to ERR_ACCESS_DENIED (net_errors_posix.cc); this must win
            // over the storage rule below, which said "couldn't use its storage" for a network block.
            chain.any { it is java.net.SocketException || it is java.net.UnknownHostException } &&
                chain.any { val t = it.message.orEmpty(); "EPERM" in t || "EACCES" in t || "Permission denied" in t } ->
                NETWORK_BLOCKED
            // G7 LT-2: a file the app can't write (folder not writable, read-only storage) showed the raw
            // "/data/user/0/…/playlists.json.tmp: open failed: EACCES (Permission denied)".
            // GROUP-14 LT-2b: Android 7 words it "<path> (Permission denied)" with no errno; any file error
            // that names an app path is a storage problem, and the path itself is never shown.
            chain.any { val t = it.message.orEmpty(); "EACCES" in t || "EROFS" in t || "EPERM" in t ||
                "Permission denied" in t || "Read-only file system" in t ||
                ((it is java.io.FileNotFoundException || "open failed:" in t) && ("/data/" in t || "/storage/" in t)) } ->
                tr(R.string.the_app_couldnt_use_its_storage, "The app couldn't use its storage on this device. Restart the app, then try again")
            // G7-L1: an IPTV extension whose site answered with a web page or an error text.
            chain.any { val t = it.message.orEmpty(); "#EXTM3U" in t && ("start" in t || "header" in t.lowercase()) } ->
                tr(R.string.the_site_sent_something_that_isnt, "The site sent something that isn't a playlist. It may be down or blocked; try again later")
            chain.any { it is java.net.UnknownHostException } ->
                if (!dev.asfakul.yumeiro.network.Connectivity.online.value) tr(R.string.no_internet_connection, "No internet connection")
                else tr(R.string.this_site_cant_be_reached_on, "This site can't be reached on your network. It may be blocked — try again, or turn a VPN on or off")
            chain.any { it is java.net.ConnectException || it is java.net.NoRouteToHostException ||
                (it is java.net.SocketException && it.message.orEmpty().contains("reset", true)) } ->
                tr(R.string.your_network_refused_the_connection_the, "Your network refused the connection. The site may be blocked here — try a VPN")
            chain.any { it is java.net.SocketTimeoutException || it is java.io.InterruptedIOException } ||
                e is kotlinx.coroutines.TimeoutCancellationException -> tr(R.string.the_server_took_too_long_to, "The server took too long to answer")
            chain.any { it is javax.net.ssl.SSLException } -> tr(R.string.secure_connection_failed, "Secure connection failed")
            m.startsWith("HTTP 404") || m.contains("HTTP 404") -> tr(R.string.not_found_404, "Not found (404)")
            // R17 G10 (ER-1): 4xx answers are not server errors; say what the server said.
            m.startsWith("HTTP") -> when (m.substringAfter("HTTP ").take(3).toIntOrNull()) {
                401 -> tr(R.string.access_denied_the_server_needs_authorization, "Access denied: the server needs authorization (HTTP 401)")
                403 -> tr(R.string.access_denied_by_the_server_http, "Access denied by the server (HTTP 403)")
                410 -> tr(R.string.removed_from_the_server_http_410, "Removed from the server (HTTP 410)")
                429 -> tr(R.string.too_many_requests_wait_a_minute, "Too many requests, wait a minute and retry (HTTP 429)")
                451 -> tr(R.string.not_available_in_your_country_http, "Not available in your country (HTTP 451)")
                else -> tr(R.string.server_error, "Server error (%1\$s)", m)
            }
            root is org.json.JSONException -> tr(R.string.the_server_sent_unreadable_data, "The server sent unreadable data")
            root is NotImplementedError -> tr(R.string.this_extension_doesnt_support_that, "This extension doesn't support that")
            isBadLink(root) || isBadLink(e) -> tr(R.string.the_site_sent_a_broken_link, "The site sent a broken link for this source — try another source")
            m.isNotBlank() -> m.take(160)
            else -> fallback
        }
    }
}
