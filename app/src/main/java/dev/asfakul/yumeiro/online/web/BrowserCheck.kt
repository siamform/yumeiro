/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.web

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
import kotlinx.coroutines.launch
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
    data class Pending(
        val url: String, val host: String, internal val done: CompletableDeferred<Boolean>,
        /** Opened by the user (captcha / sign in): never auto-closes, finished with Done. */
        val manual: Boolean = false,
        val purpose: Purpose = Purpose.CHECK,
    )

    /** Why a site page is shown. */
    enum class Purpose { CHECK, LOGIN }

    private val _pending = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = _pending.asStateFlow()

    private val cleared = ConcurrentHashMap<String, Clearance>()
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var appContext: Context? = null
    @Volatile private var ua: String? = null
    /** Hosts the user dismissed recently: don't ask again for a while. */
    private val declined = ConcurrentHashMap<String, Long>()

    private const val FALLBACK_UA =
        "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

    fun init(context: Context) {
        appContext = context.applicationContext
        // WebView start-up must happen on the main thread. Reading the default user agent from a
        // worker thread makes it wait for the main thread — a dead lock (frozen app, then "isn't
        // responding") whenever the main thread was itself waiting for that worker.
        if (onMainThread()) userAgent() else main.post { runCatching { userAgent() } }
    }

    /** The WebView's own user agent (clearance cookies are only valid with it). */
    fun userAgent(): String {
        ua?.let { return it }
        val ctx = appContext ?: return FALLBACK_UA
        val value = if (onMainThread()) {
            runCatching { WebSettings.getDefaultUserAgent(ctx) }.getOrNull()
        } else {
            // Ask the main thread and wait briefly; never block a worker on it indefinitely.
            val latch = java.util.concurrent.CountDownLatch(1)
            var got: String? = null
            main.post { got = runCatching { WebSettings.getDefaultUserAgent(ctx) }.getOrNull(); latch.countDown() }
            latch.await(3, java.util.concurrent.TimeUnit.SECONDS)
            got
        } ?: return FALLBACK_UA // not cached: the real one is picked up next time
        ua = value
        runCatching { WebViewResolver.webViewUserAgent = value }
        return value
    }

    fun clearanceFor(host: String): Clearance? = cleared[host] ?: cleared.entries.firstOrNull { host.endsWith("." + it.key) }?.value

    fun forget(host: String) { cleared.remove(host) }

    /** What a refused response is. */
    enum class Verdict {
        /** Real content (or an ordinary error): pass it through. */
        NONE,
        /** A "checking your browser" / Turnstile page that a real browser visit can pass. */
        CHALLENGE,
        /** A firewall block ("Sorry, you have been blocked", error 1020, geo block): no captcha can fix it. */
        BLOCKED,
    }

    private val CHALLENGE_MARKERS = listOf(
        "cf_chl_opt", "challenge-error-title", "challenge-error-text", "cf-challenge-running",
        "/cdn-cgi/challenge-platform/h/", "<title>just a moment", "cf-browser-verification", "cf_captcha_kind",
    )
    private val BLOCK_MARKERS = listOf(
        "cf-error-details", "sorry, you have been blocked", "error code: 1020", "error code: 1010",
        "error code: 1015", "error code: 1009", "attention required! | cloudflare", "access denied | ",
    )
    private val DDG_MARKERS = listOf("check.ddos-guard.net", "ddos-guard/js-challenge", "checking your browser", "ddg-captcha")

    /**
     * Classifies a response. Pure → unit-testable.
     *
     * Same rule Mihon/Tachiyomi moved to: Cloudflare marks every challenge with the documented
     * `cf-mitigated: challenge` header. Without it, only the challenge page's own markup counts —
     * the old check also matched `/cdn-cgi/challenge-platform/scripts/jsd/main.js`, a bot-detection
     * script Cloudflare injects into *ordinary* pages and error pages, so any 403 from a Cloudflare
     * host (hot-link protection, a WAF block, an API that wants other headers) opened the
     * "Verify you're human" page, which could never be passed and then said "you have been blocked".
     */
    fun classify(code: Int, mitigated: String?, server: String?, hasCfRay: Boolean, body: String): Verdict {
        if (mitigated.equals("challenge", true)) return Verdict.CHALLENGE
        if (code !in setOf(403, 429, 503)) return Verdict.NONE
        val srv = server.orEmpty().lowercase()
        val text = body.lowercase()
        if ("ddos-guard" in srv) {
            return if (DDG_MARKERS.any { it in text }) Verdict.CHALLENGE else Verdict.NONE
        }
        val cloudflare = "cloudflare" in srv || hasCfRay
        if (!cloudflare) return Verdict.NONE
        if (BLOCK_MARKERS.any { it in text } && CHALLENGE_MARKERS.none { it in text }) return Verdict.BLOCKED
        return if (CHALLENGE_MARKERS.any { it in text }) Verdict.CHALLENGE else Verdict.NONE
    }

    fun verdict(response: Response): Verdict {
        val mitigated = response.header("cf-mitigated")
        if (mitigated.equals("challenge", true)) return Verdict.CHALLENGE
        if (response.code !in setOf(403, 429, 503)) return Verdict.NONE
        val body = runCatching { response.peekBody(96 * 1024).string() }.getOrDefault("")
        return classify(response.code, mitigated, response.header("Server"), response.header("cf-ray") != null, body)
    }

    /** True when [response] is a browser-check page rather than real content. */
    fun isChallenge(response: Response): Boolean = verdict(response) == Verdict.CHALLENGE

    /**
     * The page to pass the check on. A challenge is per site (the clearance cookie is for the whole
     * host), so an API call, a POST, a manifest or a video segment is solved on the site's home page:
     * loading those directly in a browser is what made the firewall answer "you have been blocked".
     * Pure → unit-testable.
     */
    fun challengePage(url: String, method: String = "GET", accept: String? = null): String {
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return url
        val root = (uri.scheme ?: "https") + "://" + uri.rawAuthority + "/"
        if (!method.equals("GET", true)) return root
        if (accept?.contains("text/html", true) == true) return url
        val path = uri.rawPath.orEmpty().lowercase()
        val ext = path.substringAfterLast('/').substringAfterLast('.', "")
        val nonPage = ext in setOf("json", "m3u8", "m3u", "mpd", "mp4", "mkv", "webm", "ts", "m4s", "key", "vtt", "srt", "ass",
            "js", "css", "png", "jpg", "jpeg", "webp", "gif", "svg", "xml", "txt", "php")
        val apiLike = listOf("/api/", "/ajax/", "/graphql", "/v1/", "/v2/", "/embed-api/", "/_next/data/").any { it in "$path/" }
        return if (nonPage || apiLike) root else url
    }

    private fun onMainThread() = Looper.myLooper() == Looper.getMainLooper()

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
    suspend fun solve(url: String, interactive: Boolean = true, page: String = challengePage(url)): Clearance? {
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
            headless(page)?.let { return@withLock it.also { cleared[host] = it } }
            val now = System.currentTimeMillis()
            val recentlyDeclined = (declined[host] ?: 0L) > now - 10 * 60_000
            if (!interactive || recentlyDeclined || CommonActivity.activity == null) return@withLock null
            // Solved by hand a moment ago and the site asks again: don't pop the page up over and
            // over. Offer it calmly instead (one message, the user opens it when ready).
            if (BrowserCheckPolicy.tooSoon(askedAt[host], now)) { noteAskAgain(host, url); return@withLock null }
            askedAt[host] = now
            askUser(page, host)?.also { cleared[host] = it }
        }
    }

    /**
     * Blocking form for OkHttp interceptors. Never runs on the main thread: the check itself needs
     * the main thread (WebView), so waiting for it there froze the app until Android killed it.
     */
    fun solveBlocking(url: String, interactive: Boolean = true, page: String = challengePage(url)): Clearance? {
        if (onMainThread()) { Log.w(TAG, "browser check requested on the main thread — skipped"); return null }
        return runBlocking { solve(url, interactive, page) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun headless(url: String): Clearance? {
        val ctx = appContext ?: return null
        val home = url
        var web: WebView? = null
        return try {
            withContext(Dispatchers.Main) {
                // File / content:// access off on a local in this function (not only in apply {} / harden()): PR CodeQL scans only credit this.
                val page = WebView(ctx)
                page.settings.allowFileAccess = false
                page.settings.allowContentAccess = false
                web = page.apply {
                    SafeWebView.harden(this)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = userAgent()
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    SafeWebView.addAdShield(this)
                    webViewClient = object : SafeWebViewClient({ web = null }) {
                        override fun shouldInterceptRequest(view: WebView?, request: android.webkit.WebResourceRequest?): android.webkit.WebResourceResponse? {
                            val u = request?.url?.toString() ?: return null
                            return if (!request.isForMainFrame && AdShield.blockRequest(u)) AdFilter.blocked() else null
                        }
                        override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                            val r = request ?: return false
                            if (!r.isForMainFrame) return false
                            return AdShield.decideNavigation(home, view?.url, r.url.toString(), r.hasGesture(), r.isRedirect, login = false) == AdShield.Decision.BLOCK
                        }
                    }
                    // An off-screen WebView has no size; challenge scripts that check layout or
                    // visibility then never finish. Give it a phone-sized viewport.
                    measure(
                        android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                        android.view.View.MeasureSpec.makeMeasureSpec(1920, android.view.View.MeasureSpec.EXACTLY),
                    )
                    layout(0, 0, 1080, 1920)
                    loadUrl(url)
                }
            }
            withTimeoutOrNull(HEADLESS_MS) {
                var found: Clearance? = null
                var waited = 0L
                while (found == null) {
                    delay(500); waited += 500
                    val c = webCookies(url)
                    if (hasClearance(c)) { found = Clearance(c, userAgent()); break }
                    if (web == null) break // renderer gone
                    // A Turnstile checkbox needs a human: stop waiting and show the page right away.
                    if (waited >= 2_500 && waited % 1_500 == 0L && needsHuman(web)) break
                }
                found
            }
        } catch (t: Throwable) {
            Log.w(TAG, "headless check failed", t); null
        } finally {
            web?.let { w -> main.post { SafeWebView.dispose(w) } }
        }
    }

    /** True when the page shows an interactive widget (Turnstile / hCaptcha) a person has to tap. */
    private suspend fun needsHuman(web: WebView?): Boolean {
        val w = web ?: return false
        val done = CompletableDeferred<Boolean>()
        withContext(Dispatchers.Main) {
            runCatching { w.evaluateJavascript(NEEDS_HUMAN_JS) { r -> done.complete(r == "true") } }.onFailure { done.complete(false) }
        }
        return withTimeoutOrNull(1_500) { done.await() } ?: false
    }

    /** JS: an interactive challenge widget is on the page. */
    val NEEDS_HUMAN_JS = """(function(){try{return !!(document.querySelector('iframe[src*="challenges.cloudflare.com"]')||document.querySelector('.cf-turnstile,#turnstile-wrapper,#cf-chl-widget,iframe[src*="hcaptcha.com"]'));}catch(e){return false}})()"""

    /** JS: the page is a firewall block (not a challenge). */
    val IS_BLOCK_JS =
        "(function(){try{var t=(document.title||'').toLowerCase();var b=(document.body&&document.body.innerText||'').toLowerCase();" +
            "return !!document.getElementById('cf-error-details')||t.indexOf('attention required')>=0||b.indexOf('sorry, you have been blocked')>=0;}catch(e){return false}})()"

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

    private val blockedNoticeAt = ConcurrentHashMap<String, Long>()
    private val askedAt = ConcurrentHashMap<String, Long>()
    private val askAgainNoticeAt = ConcurrentHashMap<String, Long>()

    /** "Check again" as a single tap-to-open message, at most once per [BrowserCheckPolicy.ASK_COOLDOWN_MS]. */
    private fun noteAskAgain(host: String, url: String) {
        val now = System.currentTimeMillis()
        if (BrowserCheckPolicy.tooSoon(askAgainNoticeAt[host], now)) return
        askAgainNoticeAt[host] = now
        val ctx = appContext ?: return
        val root = challengePage(url, method = "POST")
        main.post {
            runCatching {
                dev.asfakul.yumeiro.ui.components.AppMessages.show(ctx, "$host wants another quick check", dev.asfakul.yumeiro.ui.components.AppMessages.Kind.INFO, "Open") {
                    openSite(root, Purpose.CHECK)
                }
            }
        }
    }

    /** One calm message per host (every 5 min) instead of an endless "verify you're human" page. */
    private fun noteBlocked(host: String, url: String = "https://$host/") {
        val now = System.currentTimeMillis()
        val last = blockedNoticeAt[host] ?: 0L
        if (now - last < 5 * 60_000) return
        blockedNoticeAt[host] = now
        val ctx = appContext ?: return
        val root = challengePage(url, method = "POST")
        main.post {
            runCatching {
                dev.asfakul.yumeiro.ui.components.AppMessages.error(
                    ctx, blockedMessage(host), "Open site",
                ) { openSite(root, Purpose.CHECK) }
            }
        }
    }

    /** The firewall notice. Pure → unit-testable. */
    fun blockedMessage(host: String) =
        "$host's firewall refused this source. Tap Open site to pass its captcha or sign in — or turn off VPN / custom DNS and try again."

    /** Called by the UI when the check page itself turned out to be a firewall block. */
    fun onBlocked(host: String) {
        declined[host] = System.currentTimeMillis()
        noteBlocked(host)
        cancel()
    }

    /** Called by the UI when the user closes the check page. */
    fun cancel() { _pending.value?.done?.complete(false); _pending.value = null }

    /** Called by the UI when the user taps Done on a page they opened themselves. */
    fun finish() { _pending.value?.done?.complete(true) }

    /** Called by the UI after each page load of the check page. */
    fun onPageLoaded(url: String) {
        val p = _pending.value ?: return
        if (p.manual) return
        if (hasClearance(webCookies(p.url)) || hasClearance(webCookies(url))) p.done.complete(true)
    }

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Main)

    /**
     * Opens [url] for the user (solve a captcha the site insists on, or sign in to an account the
     * extension needs). On Done, the site's cookies and the browser's user agent are sent with that
     * site's requests from then on — so a firewall that let the browser through lets the source
     * through too, and logged-in pages work in the extension.
     */
    fun openSite(url: String, purpose: Purpose = Purpose.CHECK, onDone: ((Boolean) -> Unit)? = null) {
        val host = runCatching { java.net.URI(url).host }.getOrNull() ?: return
        _pending.value?.done?.complete(false)
        val done = CompletableDeferred<Boolean>()
        _pending.value = Pending(url, host, done, manual = true, purpose = purpose)
        scope.launch {
            val ok = try { done.await() } finally { if (_pending.value?.done === done) _pending.value = null }
            if (ok) {
                runCatching { CookieManager.getInstance().flush() }
                declined.remove(host); blockedNoticeAt.remove(host); askAgainNoticeAt.remove(host)
                val c = webCookies(url)
                if (c.isNotEmpty()) cleared[host] = Clearance(c, userAgent())
                SiteLogins.add(appContext, host)
            }
            onDone?.invoke(ok)
        }
    }

    /** Merges the browser cookies of a site the user opened/signed in to into [request]. */
    internal fun withSiteCookies(request: Request): Request {
        val host = request.url.host
        if (!SiteLogins.has(appContext, host)) return request
        val web = webCookies(request.url.toString()).takeIf { it.isNotEmpty() } ?: return request
        return withClearance(request, Clearance(web, userAgent()))
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
    object Interceptor : okhttp3.Interceptor by CheckInterceptor(interactive = true)

    /**
     * For the video player's own requests (manifests, segments, keys): adds known clearances and
     * passes a challenge silently, but never opens the "Verify you're human" page — a dialog over
     * a playing video for one segment request froze playback and hid the picture. A source that
     * still refuses goes to the player's normal recovery (other headers → next source).
     */
    object MediaInterceptor : okhttp3.Interceptor by CheckInterceptor(interactive = false)

    private class CheckInterceptor(private val interactive: Boolean) : okhttp3.Interceptor {
        override fun intercept(chain: okhttp3.Interceptor.Chain): Response {
            var request = chain.request()
            if (request.header(SKIP_HEADER) != null) {
                return chain.proceed(request.newBuilder().removeHeader(SKIP_HEADER).build())
            }
            val host = request.url.host
            clearanceFor(host)?.let { request = withClearance(request, it) }
            request = withSiteCookies(request)
            val response = chain.proceed(request)
            when (verdict(response)) {
                Verdict.NONE -> return response
                Verdict.BLOCKED -> {
                    Log.w(TAG, "$host refused the request with a firewall block (not a captcha) — not opening the check page")
                    noteBlocked(host, request.url.toString())
                    return response
                }
                Verdict.CHALLENGE -> Unit
            }
            if (onMainThread()) return response
            if (clearanceFor(host) != null) forget(host)
            val page = challengePage(request.url.toString(), request.method, request.header("Accept"))
            val clearance = solveBlocking(request.url.toString(), interactive = interactive, page = page) ?: return response
            response.close()
            return chain.proceed(withClearance(chain.request(), clearance))
        }
    }
}

/** Timing rules for the "Verify you're human" page. Pure → unit-tested. */
object BrowserCheckPolicy {
    /** After the page was shown for a host, it isn't shown again automatically for this long. */
    const val ASK_COOLDOWN_MS = 3 * 60_000L

    fun tooSoon(lastAskedAt: Long?, now: Long): Boolean = lastAskedAt != null && now - lastAskedAt in 0 until ASK_COOLDOWN_MS
}
