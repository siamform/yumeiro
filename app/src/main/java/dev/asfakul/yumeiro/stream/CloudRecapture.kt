/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.online.web.SafeWebView
import dev.asfakul.yumeiro.online.web.SafeWebViewClient
import dev.asfakul.yumeiro.ui.screens.stream.CapturedStream
import dev.asfakul.yumeiro.ui.screens.stream.DESKTOP_UA
import dev.asfakul.yumeiro.ui.screens.stream.PageStreamCapture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * F-03 (ADR-042): TeraBox and DiskWala hand their web player a stream address that expires (the
 * player then gets 403 / 410). Before this, the viewer saw "Couldn't refresh the stream" and had to
 * open the link again and seek. This refresher opens the same share page once more, off screen,
 * starts the page's own player exactly like the link finder's auto mode, and returns the stream
 * that page requests; the player resumes at the saved position (VideoPlayerModal.RefreshLink).
 *
 * Same rules as the link finder: the page runs as in a browser, with its own cookies; sign-in,
 * checks and premium limits stay with the site (a page that needs the user returns null, so the
 * player shows its error card). Bounded by [Budget]: at most [Budget.MAX] tries per page in
 * [Budget.WINDOW_MS], each limited to [PageStreamCapture.AUTO_TIMEOUT_MS]; the WebView is destroyed
 * right after. Nothing runs at registration or on the normal playback path.
 */
object CloudRecapture {
    @Volatile private var registered = false

    /** Pure: how often one page may be captured again (unit-tested). */
    class Budget(private val clock: () -> Long = System::currentTimeMillis) {
        private val tries = ConcurrentHashMap<String, List<Long>>()
        fun take(pageUrl: String): Boolean {
            val now = clock()
            var ok = false
            tries.compute(pageUrl) { _, old ->
                val recent = old.orEmpty().filter { now - it < WINDOW_MS }
                if (recent.size < MAX) { ok = true; recent + now } else recent
            }
            return ok
        }
        fun reset() = tries.clear()
        companion object {
            const val MAX = 2
            const val WINDOW_MS = 10 * 60_000L
        }
    }

    private val budget = Budget()

    /** Whether [request] is a stream captured from a TeraBox / DiskWala page (cheap; no network). */
    fun owns(request: StreamRequest): Boolean = request.pageUrl?.let { page ->
        LinkHosts.cloudHost(page) != null || (testHosts.isNotEmpty() && runCatching { java.net.URI(page).host }.getOrNull()?.let { it in testHosts } == true)
    } == true

    /** Device tests serve a fixture share page from 127.0.0.1; empty in the app. */
    @androidx.annotation.VisibleForTesting @Volatile internal var testHosts: Set<String> = emptySet()

    /** Device tests: forget the per-page budget. */
    @androidx.annotation.VisibleForTesting internal fun resetBudget() = budget.reset()

    fun install(context: Context) {
        if (registered) return
        registered = true
        val app = context.applicationContext
        StreamRefreshers.register(object : StreamRefreshers.Refresher {
            override fun handles(request: StreamRequest) = owns(request) && FeatureFlags.on(app, FeatureFlags.Flag.CLOUD_CAPTURE_V2)
            override suspend fun refresh(context: Context, request: StreamRequest): StreamRequest? {
                val page = request.pageUrl ?: return null
                if (!budget.take(page)) return null
                return capture(context.applicationContext, page, request)
            }
        })
    }

    /** Opens [pageUrl] off screen, starts its player and returns the request for the stream it plays, or null. */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun capture(context: Context, pageUrl: String, old: StreamRequest): StreamRequest? = withContext(Dispatchers.Main) {
        val capture = PageStreamCapture()
        val first = CompletableDeferred<Unit>()
        val needsUser = CompletableDeferred<Unit>()
        var title = old.title
        val web = WebView(context)
        try {
            web.settings.allowFileAccess = false
            web.settings.allowContentAccess = false
            with(web) {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.userAgentString = old.userAgent ?: DESKTOP_UA
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                fun record(url: String, method: String, headers: Map<String, String>) {
                    synchronized(capture) { capture.onRequest(url, method, headers) }?.let { first.complete(Unit) }
                }
                addJavascriptInterface(object {
                    @JavascriptInterface fun onMedia(src: String) = record(src, "GET", emptyMap())
                    @JavascriptInterface fun onDrm(system: String) = Unit
                }, "MTFinder")
                SafeWebView.harden(this)
                webViewClient = object : SafeWebViewClient(onGone = { needsUser.complete(Unit) }) {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val scheme = request?.url?.scheme?.lowercase()
                        return scheme != "http" && scheme != "https"
                    }
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                        if (request != null && dev.asfakul.yumeiro.online.web.AdFilter.isAd(request.url.toString())) return dev.asfakul.yumeiro.online.web.AdFilter.blocked()
                        request?.let { record(it.url.toString(), it.method ?: "GET", it.requestHeaders.orEmpty()) }
                        return null
                    }
                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                        if (url != null && PageStreamCapture.needsUser(url)) needsUser.complete(Unit)
                        view?.evaluateJavascript(PageStreamCapture.HOOK_JS, null)
                    }
                    override fun onPageFinished(view: WebView?, url: String?) {
                        view?.title?.takeIf { it.isNotBlank() }?.let { if (title.isBlank()) title = it }
                        view?.evaluateJavascript(PageStreamCapture.HOOK_JS, null)
                    }
                }
                loadUrl(pageUrl, old.referer?.let { mapOf("Referer" to it) } ?: emptyMap())
            }
            val found: CapturedStream? = withTimeoutOrNull(PageStreamCapture.AUTO_TIMEOUT_MS) {
                coroutineScope {
                    val nudge = launch { repeat(10) { delay(2_000L); web.evaluateJavascript(PageStreamCapture.AUTOPLAY_JS, null) } }
                    val giveUp = launch { needsUser.await() }
                    kotlinx.coroutines.selects.select<Unit> {
                        first.onAwait { }
                        giveUp.onJoin { }
                    }
                    nudge.cancel(); giveUp.cancel()
                    if (needsUser.isCompleted) return@coroutineScope null
                    delay(PageStreamCapture.AUTO_SETTLE_MS)
                    PageStreamCapture.bestForAuto(synchronized(capture) { capture.all() })
                }
            }
            found?.let { c ->
                synchronized(capture) {
                    capture.toRequest(
                        c, pageUrl, title, userAgent = web.settings.userAgentString,
                        cookie = runCatching { CookieManager.getInstance().getCookie(c.url) }.getOrNull(),
                        base = null,
                    )
                }.copy(title = old.title.ifBlank { title }, logoUrl = old.logoUrl, group = old.group)
            }
        } finally {
            runCatching { web.stopLoading(); web.onPause(); web.destroy() }
        }
    }
}
