/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.mtrevanced.online.web

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.network.WebViewResolver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles "checking your browser" pages (Cloudflare, DDoS-Guard and similar) for extension sites.
 *
 * Works like a normal browser visit: the page is opened in a real [WebView] so the site's own check
 * runs and sets its clearance cookie. Most checks pass on their own in a few seconds (headless). When
 * a site wants a human tap (e.g. a Turnstile checkbox) the page is shown to the user, who completes
 * it; nothing is solved on their behalf. The cookie and the WebView's user agent are then sent with
 * that site's requests until it expires.
 */
object BrowserCheck {
    private const val TAG = "BrowserCheck"
    private const val HEADLESS_MS = 20_000L
    private const val INTERACTIVE_MS = 120_000L
    /** Requests carrying this header are never intercepted (used to avoid loops). */
    const val SKIP_HEADER = "X-MT-Skip-Check"

    data class Clearance(val cookies: Map<String, String>, val userAgent: String, val at: Long = System.currentTimeMillis())

    /** A check that needs the user, shown by the app UI ([BrowserCheckHost]). */
    data class Pending(val url: String, val host: String, internal val done: CompletableDeferred<Boolean>)

    private val _pending = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = _pending.asStateFlow()

    private val cleared = ConcurrentHashMap<String, Clearance>()
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var appContext: Context? = null
    @Volatile private var ua: String? = null
    /** Hosts the user dismissed recently: don't ask again for a while. */
    private val declined = ConcurrentHashMap<String, Long>()

    fun init(context: Context) {
        appContext = context.applicationContext
        userAgent()
    }

    /** The WebView's own user agent (clearance cookies are only valid with it). */
    fun userAgent(): String {
        ua?.let { return it }
        val ctx = appContext
        val value = runCatching { ctx?.let { WebSettings.getDefaultUserAgent(it) } }.getOrNull()
            ?: "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
        ua = value
        runCatching { WebViewResolver.webViewUserAgent = value }
        return value
    }

    fun clearanceFor(host: String): Clearance? = cleared[host] ?: cleared.entries.firstOrNull { host.endsWith("." + it.key) }?.value

    fun forget(host: String) { cleared.remove(host) }

    /** True when [response] is a browser-check page rather than real content. */
    fun isChallenge(response: Response): Boolean {
        if (response.code !in setOf(403, 429, 503)) return false
        if (response.header("cf-mitigated").equals("challenge", true)) return true
        val server = response.header("Server").orEmpty().lowercase()
        val protectedBy = "cloudflare" in server || "ddos-guard" in server || response.header("cf-ray") != null
        if (!protectedBy) return false
        val body = runCatching { response.peekBody(96 * 1024).string() }.getOrDefault("")
        return listOf("challenge-platform", "cf-chl", "Just a moment", "cf_chl_opt", "ddos-guard", "Checking your browser", "turnstile")
            .any { body.contains(it, ignoreCase = true) }
    }

    private fun webCookies(url: String): Map<String, String> {
        val raw = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull().orEmpty()
        return raw.split(';').mapNotNull { part ->
            val name = part.substringBefore('=').trim(); val value = part.substringAfter('=', "").trim()
            if (name.isEmpty() || value.isEmpty()) null else name to value
        }.toMap()
    }

    private fun hasClearance(cookies: Map<String, String>) =
        cookies.keys.any { it == "cf_clearance" || it.startsWith("__ddg") || it == "__ddgid_" }

    /**
     * Gets a clearance for [url]: first silently, then (when [interactive] and the app is open) by
     * showing the page to the user. Returns null when the check could not be passed.
     */
    suspend fun solve(url: String, interactive: Boolean = true): Clearance? {
        val host = runCatching { java.net.URI(url).host }.getOrNull() ?: return null
        val lock = locks.getOrPut(host) { Mutex() }
        val before = cleared[host]
        return lock.withLock {
            // Another request solved it while we waited.
            cleared[host]?.takeIf { it !== before }?.let { return@withLock it }
            webCookies(url).takeIf { hasClearance(it) && before == null }?.let { c ->
                return@withLock Clearance(c, userAgent()).also { cleared[host] = it }
            }
            Log.i(TAG, "Checking $host in a WebView")
            headless(url)?.let { return@withLock it.also { cleared[host] = it } }
            val recentlyDeclined = (declined[host] ?: 0L) > System.currentTimeMillis() - 10 * 60_000
            if (!interactive || recentlyDeclined || CommonActivity.activity == null) return@withLock null
            askUser(url, host)?.also { cleared[host] = it }
        }
    }

    fun solveBlocking(url: String, interactive: Boolean = true): Clearance? = runBlocking { solve(url, interactive) }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun headless(url: String): Clearance? {
        val ctx = appContext ?: return null
        var web: WebView? = null
        return try {
            withContext(Dispatchers.Main) {
                web = WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.userAgentString = userAgent()
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = WebViewClient()
                    loadUrl(url)
                }
            }
            withTimeoutOrNull(HEADLESS_MS) {
                var found: Clearance? = null
                while (found == null) {
                    delay(500)
                    val c = webCookies(url)
                    if (hasClearance(c)) found = Clearance(c, userAgent())
                }
                found
            }
        } catch (t: Throwable) {
            Log.w(TAG, "headless check failed", t); null
        } finally {
            web?.let { w -> main.post { runCatching { w.stopLoading(); w.destroy() } } }
        }
    }

    private suspend fun askUser(url: String, host: String): Clearance? {
        val done = CompletableDeferred<Boolean>()
        _pending.value = Pending(url, host, done)
        val ok = try {
            withTimeoutOrNull(INTERACTIVE_MS) {
                while (!done.isCompleted) {
                    delay(500)
                    if (hasClearance(webCookies(url))) { done.complete(true); break }
                }
                done.await()
            } ?: false
        } finally {
            if (_pending.value?.done === done) _pending.value = null
        }
        if (!ok) { declined[host] = System.currentTimeMillis(); return null }
        return Clearance(webCookies(url), userAgent())
    }

    /** Called by the UI when the user closes the check page. */
    fun cancel() { _pending.value?.done?.complete(false); _pending.value = null }

    /** Called by the UI after each page load of the check page. */
    fun onPageLoaded(url: String) {
        val p = _pending.value ?: return
        if (hasClearance(webCookies(p.url)) || hasClearance(webCookies(url))) p.done.complete(true)
    }

    /** [request] with the clearance cookies and matching user agent for its host. */
    fun withClearance(request: Request, c: Clearance): Request {
        val existing = request.header("Cookie").orEmpty().split(';').mapNotNull { part ->
            val n = part.substringBefore('=').trim(); if (n.isEmpty()) null else n to part.substringAfter('=', "").trim()
        }.toMap()
        val merged = (existing + c.cookies).entries.joinToString("; ") { "${it.key}=${it.value}" }
        return request.newBuilder().header("Cookie", merged).header("User-Agent", c.userAgent).build()
    }

    /**
     * Application interceptor for extension traffic: adds known clearances and, when a site answers
     * with a browser check, passes it (see [solve]) and retries the request once.
     */
    object Interceptor : okhttp3.Interceptor {
        override fun intercept(chain: okhttp3.Interceptor.Chain): Response {
            var request = chain.request()
            if (request.header(SKIP_HEADER) != null) {
                return chain.proceed(request.newBuilder().removeHeader(SKIP_HEADER).build())
            }
            val host = request.url.host
            clearanceFor(host)?.let { request = withClearance(request, it) }
            val response = chain.proceed(request)
            if (!isChallenge(response)) return response
            val hadClearance = clearanceFor(host) != null
            if (hadClearance) forget(host)
            val clearance = solveBlocking(request.url.toString(), interactive = true) ?: return response
            response.close()
            return chain.proceed(withClearance(chain.request(), clearance))
        }
    }
}
