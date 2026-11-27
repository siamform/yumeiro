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
        val app = context.applicationContext
        appContext = app
        runCatching { VerificationCenter.init(app) }
        // R15 (device run 43626650174): starting WebView to read its user agent took 1.6 s of the
        // main thread during app start (with the first screen still composing, the system raised
        // "isn't responding"). The agent only changes when WebView updates, so it is kept on disk
        // per WebView version and WebView is started for it only once the app is idle.
        // R16 (ST-7): the idle warm-up still started WebView on the main thread (1.2 s and 2.5 s in
        // stress run 43680725238, every fresh install). Our own WebViews set their user agent to
        // [userAgent] (BrowserCheckHost, the solver below), so clearance cookies match any agent we
        // pick. Without a saved copy, the agent is now built from the installed WebView's version.
        Thread({
            val cached = runCatching { cachedAgent(app) }.getOrNull()
            remember(cached ?: estimatedAgent(android.os.Build.VERSION.RELEASE ?: "14", runCatching {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) WebView.getCurrentWebViewPackage()?.versionName else null
            }.getOrNull()))
        }, "yu-browser-ua").start()
    }

    private const val UA_PREFS = "browser_check"

    /** Identifies the installed WebView build: a different build has a different user agent. */
    private fun webViewBuild(ctx: Context): String = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            WebView.getCurrentWebViewPackage()?.let { it.packageName + "@" + it.versionName }
        } else null
    }.getOrNull() ?: ("sdk" + android.os.Build.VERSION.SDK_INT + "/" + android.os.Build.FINGERPRINT)

    private fun cachedAgent(ctx: Context): String? {
        val p = ctx.getSharedPreferences(UA_PREFS, Context.MODE_PRIVATE)
        val ua = p.getString("ua", null) ?: return null
        return ua.takeIf { p.getString("ua_build", null) == webViewBuild(ctx) && it.isNotBlank() }
    }

    private fun remember(value: String) {
        ua = value
        runCatching { WebViewResolver.webViewUserAgent = value }
    }

    /**
     * Used off the main thread before WebView has been started: same shape as a WebView agent, with
     * this phone's Android version and the installed WebView's Chrome version. Not stored.
     */
    internal fun estimatedAgent(androidRelease: String, webViewVersion: String?): String {
        val chrome = webViewVersion?.takeIf { it.firstOrNull()?.isDigit() == true }?.substringBefore(' ') ?: "130.0.0.0"
        return "Mozilla/5.0 (Linux; Android $androidRelease; K; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/$chrome Mobile Safari/537.36"
    }

    /** The WebView's own user agent (clearance cookies are only valid with it). */
    fun userAgent(): String {
        ua?.let { return it }
        val ctx = appContext ?: return FALLBACK_UA
        if (!onMainThread()) {
            // Never start WebView for a worker: that blocks on the main thread (dead lock when the main
            // thread waits for this worker, and a long freeze during start-up). Disk copy first.
            runCatching { cachedAgent(ctx) }.getOrNull()?.let { remember(it); return it }
            val version = runCatching {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) WebView.getCurrentWebViewPackage()?.versionName else null
            }.getOrNull()
            return estimatedAgent(android.os.Build.VERSION.RELEASE ?: "14", version)
        }
        val value = runCatching { WebSettings.getDefaultUserAgent(ctx) }.getOrNull() ?: return FALLBACK_UA
        remember(value)
        Thread({
            val build = runCatching { webViewBuild(ctx) }.getOrNull()
            runCatching { ctx.getSharedPreferences(UA_PREFS, Context.MODE_PRIVATE).edit().putString("ua", value).putString("ua_build", build).apply() }
        }, "yu-browser-ua-save").start()
        return value
    }

    fun clearanceFor(host: String): Clearance? {
        (cleared[host] ?: cleared.entries.firstOrNull { host.endsWith("." + it.key) }?.value)?.let { return it }
        // ADR-059: after a restart, a site passed earlier is reused from the browser's cookie store
        // (the check is not run again). Off the main thread only: the cookie store may wait on WebView.
        if (onMainThread() || !centerOn()) return null
        if (VerificationCenter.site(host)?.state != VerificationCenter.State.CLEAR) return null
        val c = webCookies("https://$host/").takeIf { hasClearance(it) } ?: return null
        return Clearance(c, userAgent()).also { cleared[host] = it }
    }

    private fun accurateNotes() = dev.asfakul.yumeiro.FeatureFlags.isOn(dev.asfakul.yumeiro.FeatureFlags.Flag.ACCURATE_BLOCK_NOTE)

    private fun centerOn() = dev.asfakul.yumeiro.FeatureFlags.isOn(dev.asfakul.yumeiro.FeatureFlags.Flag.VERIFY_CENTER)

    fun forget(host: String) { cleared.remove(host) }

    private val challengedAt = ConcurrentHashMap<String, Long>()

    /** B4: [host] answered with a check page now (an extension request met it). */
    fun noteChallenge(host: String, now: Long = System.currentTimeMillis(), url: String? = null) {
        if (challengedAt.size > 256) challengedAt.clear()
        val h = VerificationPolicy.normalize(host)
        challengedAt[h] = now
        rootOf(url)?.let { if (siteRoot.size > 256) siteRoot.clear(); siteRoot[h] = it }
    }

    private val siteRoot = ConcurrentHashMap<String, String>()

    /**
     * B4: the address to open for [host]'s check: the scheme and port its check page came from (a site on
     * http or another port opened as https://host/ before, so its check page never loaded).
     */
    fun checkPageFor(host: String): String = siteRoot[VerificationPolicy.normalize(host)] ?: "https://${VerificationPolicy.normalize(host)}/"

    /** "scheme://host[:port]/" of [url], or null. Pure. */
    internal fun rootOf(url: String?): String? = runCatching {
        val u = java.net.URI(url ?: return null)
        val scheme = u.scheme?.lowercase()?.takeIf { it == "http" || it == "https" } ?: return null
        val host = u.host?.takeIf { it.isNotBlank() } ?: return null
        val port = if (u.port == -1 || (scheme == "https" && u.port == 443) || (scheme == "http" && u.port == 80)) "" else ":${u.port}"
        "$scheme://$host$port/"
    }.getOrNull()

    /**
     * B4: did [mainUrl]'s site answer with a check page at or after [since]? An extension that met the check page
     * parses it as "no titles" without an error, and that empty answer was saved (search: 1 h on the phone), so the
     * site stayed empty after the user passed its check. Answers made under a check are not saved.
     */
    fun challengedSince(mainUrl: String?, since: Long): Boolean {
        if (challengedAt.isEmpty()) return false
        return challengedAt.entries.any { (h, at) -> at >= since && SitePass.matches(mainUrl, h) }
    }

    private val blockedAt = ConcurrentHashMap<String, Long>()

    /** B7: did [mainUrl]'s site refuse a request with a firewall block at or after [since]? */
    fun blockedSince(mainUrl: String?, since: Long): Boolean {
        if (blockedAt.isEmpty()) return false
        return blockedAt.entries.any { (h, at) -> at >= since && SitePass.matches(mainUrl, h) }
    }

    internal fun rememberForTest(host: String, c: Clearance) { cleared[host] = c }

    /** What a refused response is. */
    enum class Verdict {
        /** Real content (or an ordinary error): pass it through. */
        NONE,
        /** A "checking your browser" / Turnstile page that a real browser visit can pass. */
        CHALLENGE,
        /** A firewall block ("Sorry, you have been blocked", error 1020, geo block): no captcha can fix it. */
        BLOCKED,
        /**
         * NET-2 (A4): Cloudflare's rate limit (HTTP 429, error 1015): the site asked to slow down. Not a block;
         * nothing is shown, the caller's normal retry / next-source path takes it.
         */
        RATE_LIMITED,
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
        // VERIFY-2: the DooPlay/AnimeOnline family's own "One moment, please" script page (403 until its script ran).
        if (!cloudflare) return if ("wsidchk-form" in text) Verdict.CHALLENGE else Verdict.NONE
        if (accurateNotes() && CHALLENGE_MARKERS.none { it in text } && (code == 429 || "error code: 1015" in text)) return Verdict.RATE_LIMITED
        if (BLOCK_MARKERS.any { it in text } && CHALLENGE_MARKERS.none { it in text }) return Verdict.BLOCKED
        return if (CHALLENGE_MARKERS.any { it in text }) Verdict.CHALLENGE else Verdict.NONE
    }

    /**
     * VERIFY-2 (from Hikari's ExtensionCloudflareInterceptor): some sites answer the check page itself with 200
     * ("Just a moment…", DooPlay's "One moment, please" wsidchk form), which an extension then read as an empty site.
     * Only a small HTML document made of a check page's own markup counts; real pages are never peeked past 32 KB.
     * Pure → unit-testable.
     */
    fun okPageIsCheck(contentType: String?, body: String): Boolean {
        if (contentType?.contains("text/html", true) != true) return false
        if (body.length > OK_CHECK_MAX) return false
        val t = body.lowercase()
        return OK_CHECK_MARKERS.any { it in t } || DDG_MARKERS.take(2).any { it in t }
    }

    private const val OK_CHECK_MAX = 32 * 1024
    private val OK_CHECK_MARKERS = listOf("cf_chl_opt", "cf-challenge-running", "<title>just a moment", "wsidchk-form", "challenge-error-title")

    fun verdict(response: Response): Verdict {
        val mitigated = response.header("cf-mitigated")
        if (mitigated.equals("challenge", true)) return Verdict.CHALLENGE
        if (response.code in 200..299) {
            val type = response.header("Content-Type")
            val len = response.header("Content-Length")?.toLongOrNull()
            if (type?.contains("text/html", true) != true || (len != null && len > OK_CHECK_MAX)) return Verdict.NONE
            val body = runCatching { response.peekBody(OK_CHECK_MAX + 1L).string() }.getOrDefault("")
            return if (okPageIsCheck(type, body)) Verdict.CHALLENGE else Verdict.NONE
        }
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
            // ADR-059: a site whose firewall refuses this network fails at once (also after a restart):
            // no WebView is opened for a check that cannot be passed.
            if (centerOn() && VerificationCenter.isBlocked(host)) {
                Log.i(TAG, "$host blocks this network (verification center) — not opening a WebView")
                return@withLock null
            }
            webCookies(url).takeIf { hasClearance(it) && before == null }?.let { c ->
                return@withLock Clearance(c, userAgent()).also { cleared[host] = it }
            }
            // G10 A5 (R17): a failed check was recorded in [declined] but never read, so every
            // later request to the host (and every request queued on [lock]) opened its own
            // off-screen WebView for up to 20 s: a source page with 10 requests waited minutes and
            // kept a WebView renderer alive the whole time. Now it fails fast for a short while;
            // opening the site by hand ([openSite]) clears it.
            if (BrowserCheckPolicy.recentlyFailed(declined[host], System.currentTimeMillis())) {
                Log.i(TAG, "$host failed its check moments ago — not opening another WebView yet")
                return@withLock null
            }
            Log.i(TAG, "Checking $host in a WebView")
            headless(page)?.let { return@withLock it.also { cleared[host] = it; VerificationCenter.report(host, VerificationCenter.Outcome.PASSED) } }
            // Round 13: the interactive captcha page is gone. It only ever showed a blank screen
            // (Turnstile / hCaptcha refuse to render inside an app WebView), so a check that the
            // silent pass can't clear now just fails this source and playback moves to the next.
            declined[host] = System.currentTimeMillis()
            if (centerOn()) VerificationCenter.report(host, VerificationCenter.Outcome.FAILED)
            // B4: the silent pass could not clear it (the site wants a tap). One quiet card with Verify, which opens
            // the check page; the extensions of the site reload by themselves once it is passed.
            if (interactive && centerOn()) appContext?.let { ctx -> runCatching { dev.asfakul.yumeiro.online.ExtensionCheckWindows.askToVerify(ctx, host, page) } }
            null
        }
    }

    private val warmedAt = ConcurrentHashMap<String, Long>()

    /**
     * VERIFY-2 (Hikari's CloudflareSolver.warm): a site that asked for a check before, whose pass has since run out,
     * is passed again in the background when its extension is opened, so the home / search requests that follow
     * don't each meet the check. Silent only (never shows a page); at most once per 90 s per site; blocked or
     * paused sites are left alone. Call off the main thread.
     */
    suspend fun warm(url: String) {
        val host = runCatching { java.net.URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: return
        if (!centerOn() || onMainThread()) return
        val site = VerificationCenter.site(host) ?: return
        if (site.passes <= 0 || !VerificationCenter.mayAsk(host)) return
        if (cleared[host] != null || hasClearance(webCookies("https://$host/"))) return
        val now = System.currentTimeMillis()
        if (!BrowserCheckPolicy.mayWarm(warmedAt[host], now)) return
        warmedAt[host] = now
        Log.i(TAG, "warming $host (its pass ran out)")
        solve("https://$host/", interactive = false)
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
                // Local + with(page), never WebView(..).apply {}: CodeQL counts apply's result as a new WebView and misses these settings.
                val page = WebView(ctx)
                page.settings.allowFileAccess = false
                page.settings.allowContentAccess = false
                web = page
                with(page) {
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

    private val blockedNoticeAt = ConcurrentHashMap<String, Long>()
    private val askedAt = ConcurrentHashMap<String, Long>()
    private val askAgainNoticeAt = ConcurrentHashMap<String, Long>()

    /** One calm message per host (every 5 min) instead of an endless "verify you're human" page. */
    private fun noteBlocked(host: String, url: String = "https://$host/") {
        if (blockedAt.size > 256) blockedAt.clear()
        blockedAt[VerificationPolicy.normalize(host)] = System.currentTimeMillis()
        if (centerOn()) {
            // ADR-059: one note per block period for the site, whichever path (ours or an extension's) met it.
            val before = VerificationCenter.site(host)
            val already = before?.state == VerificationCenter.State.BLOCKED && VerificationPolicy.holding(before, System.currentTimeMillis())
            VerificationCenter.report(host, VerificationCenter.Outcome.BLOCKED)
            if (already) return
        }
        val now = System.currentTimeMillis()
        val last = blockedNoticeAt[host] ?: 0L
        val quietFor = if (accurateNotes()) BLOCK_NOTE_QUIET_MS else 5 * 60_000L
        if (now - last < quietFor) return
        blockedNoticeAt[host] = now
        val ctx = appContext ?: return
        if (!accurateNotes()) {
            main.post { runCatching { dev.asfakul.yumeiro.ui.components.AppMessages.error(ctx, blockedMessage(host)) } }
            return
        }
        // NET-2 (A4): nothing is said while Yumeiro isn't on screen (background feed refresh, workers): the site is
        // only marked in Security checks. On screen, every block met within [BLOCK_NOTE_GATHER_MS] becomes one note,
        // so opening Home with many sources never stacks one firewall message per site.
        if (!dev.asfakul.yumeiro.update.AppVisibility.isForeground) return
        synchronized(pendingBlocks) {
            pendingBlocks += host
            if (pendingBlocks.size > 1) return
        }
        main.postDelayed({
            val hosts = synchronized(pendingBlocks) { pendingBlocks.toList().also { pendingBlocks.clear() } }
            if (hosts.isNotEmpty() && dev.asfakul.yumeiro.update.AppVisibility.isForeground) {
                runCatching { dev.asfakul.yumeiro.ui.components.AppMessages.error(ctx, blockedMessage(hosts)) }
            }
        }, BLOCK_NOTE_GATHER_MS)
    }

    private val pendingBlocks = LinkedHashSet<String>()
    const val BLOCK_NOTE_GATHER_MS = 2_000L
    /** NET-2: a site's firewall note is not repeated for 30 min (the Security checks list keeps showing it). */
    const val BLOCK_NOTE_QUIET_MS = 30 * 60_000L

    /** The firewall notice. Pure → unit-testable. */
    fun blockedMessage(host: String) =
        "$host's firewall refused this source, so Yumeiro skipped it. Turning off a VPN or custom DNS can help."

    /** NET-2: one notice for every site blocked at about the same time. Pure → unit-testable. */
    fun blockedMessage(hosts: List<String>): String = when (hosts.size) {
        0 -> ""
        1 -> blockedMessage(hosts[0])
        2 -> "The firewalls of ${hosts[0]} and ${hosts[1]} refused Yumeiro, so those sources were skipped. A VPN or blocked network is the usual reason."
        else -> "The firewalls of ${hosts[0]} and ${hosts.size - 1} other sites refused Yumeiro, so those sources were skipped. A VPN or blocked network is the usual reason. See Settings › Security checks."
    }

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
                if (hasClearance(c) || purpose == Purpose.CHECK) VerificationCenter.report(host, VerificationCenter.Outcome.PASSED)
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

    /**
     * VERIFY-2 (Zangetsu CfClearance.Interceptor): a pass works only with the user agent that earned it. As a network
     * interceptor this runs on every hop, redirects included, so a redirect into a passed site carries that site's
     * pass and agent too. Memory only (no cookie-store or disk read on the hop).
     */
    object HopInterceptor : okhttp3.Interceptor {
        override fun intercept(chain: okhttp3.Interceptor.Chain): Response {
            val request = chain.request()
            val host = request.url.host
            val c = cleared[host] ?: cleared.entries.firstOrNull { host.endsWith("." + it.key) }?.value
                ?: return chain.proceed(request)
            return chain.proceed(withClearance(request, c))
        }
    }

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
                Verdict.RATE_LIMITED -> {
                    Log.i(TAG, "$host asked to slow down (rate limit) — no note")
                    return response
                }
                Verdict.CHALLENGE -> noteChallenge(host, url = request.url.toString())
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

    /** After a silent check failed for a host, requests to it fail at once for this long. */
    const val FAILED_RETRY_MS = 60_000L

    fun recentlyFailed(failedAt: Long?, now: Long): Boolean = failedAt != null && now - failedAt in 0 until FAILED_RETRY_MS

    /** VERIFY-2: a background warm-up for a site runs at most this often. */
    const val WARM_COOLDOWN_MS = 90_000L

    fun mayWarm(lastAt: Long?, now: Long): Boolean = lastAt == null || now - lastAt !in 0 until WARM_COOLDOWN_MS
}
