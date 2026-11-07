/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
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
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.asfakul.yumeiro.stream.StreamRequest
import dev.asfakul.yumeiro.ui.screens.stream.CapturedStream
import dev.asfakul.yumeiro.ui.screens.stream.PageStreamCapture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Last-resort recovery for links the host refuses everywhere except inside its own page.
 *
 * Loads the embed / episode page in an off-screen WebView (real Chromium: browser TLS fingerprint,
 * JavaScript, cookies, Cloudflare clearance), nudges its player to start muted, and captures the
 * manifest the page itself requests — together with the exact User-Agent, Referer, Origin and
 * cookies the page used. Playing that request natively then works for hosts that bind tokens to
 * the browser session. This automates the old advice "open it once in its own page".
 *
 * Nothing is decrypted or bypassed: DRM and sign-in stay with the site.
 */
object HeadlessCapture {
    private const val TAG = "HeadlessCapture"
    private val main = Handler(Looper.getMainLooper())

    /** JS that starts the page's player (muted autoplay is allowed) and clicks common play buttons. */
    private const val NUDGE_JS = """
(function(){
  function go(){
    try { document.querySelectorAll('video').forEach(function(v){ v.muted = true; var p = v.play(); if (p && p.catch) p.catch(function(){}); }); } catch(e) {}
    try {
      var sel = ['.vjs-big-play-button','.jw-icon-display','.plyr__control--overlaid','.play-button','#play','.btn-play','[aria-label=Play]','.fp-ui','.playbtn'];
      sel.forEach(function(s){ var b = document.querySelector(s); if (b) b.click(); });
    } catch(e) {}
  }
  go(); setTimeout(go, 1200); setTimeout(go, 3000); setTimeout(go, 6000);
})();
"""

    /**
     * Loads [pageUrl] (with [referer] if given) and returns a playable request for the first stream
     * the page requests, or null after [timeoutMs].
     */
    suspend fun capture(
        context: Context,
        pageUrl: String,
        title: String,
        referer: String? = null,
        timeoutMs: Long = 22_000,
    ): StreamRequest? = withContext(Dispatchers.Main) {
        val result = CompletableDeferred<StreamRequest?>()
        val capture = PageStreamCapture()
        var web: WebView? = null
        try {
            web = create(context.applicationContext, pageUrl, title, capture, result)
            val headers = referer?.takeIf { it.isNotBlank() }?.let { mapOf("Referer" to it) } ?: emptyMap()
            web.loadUrl(pageUrl, headers)
            withTimeoutOrNull(timeoutMs) { result.await() }
        } catch (t: Throwable) {
            Log.w(TAG, "capture failed for ${android.net.Uri.parse(pageUrl).host}", t)
            null
        } finally {
            web?.let { w -> main.post { runCatching { w.stopLoading(); w.destroy() } } }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun create(
        context: Context,
        pageUrl: String,
        title: String,
        capture: PageStreamCapture,
        result: CompletableDeferred<StreamRequest?>,
    ): WebView {
        val web = WebView(context)
        val cookies = CookieManager.getInstance().apply { setAcceptCookie(true); setAcceptThirdPartyCookies(web, true) }
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // Web pages must never read app files or content providers (default is "on" before Android 11).
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            loadsImagesAutomatically = false
            blockNetworkImage = true
        }
        val ua = web.settings.userAgentString

        fun deliver(c: CapturedStream) {
            if (result.isCompleted) return
            val cookie = runCatching { cookies.getCookie(c.url) }.getOrNull()
            val req = capture.toRequest(c, pageUrl, title, ua, cookie, base = null)
            // A manifest is final; for a plain file give the page a moment to reveal an HLS master.
            result.complete(req)
        }

        web.addJavascriptInterface(object {
            @JavascriptInterface fun onMedia(url: String) {
                capture.onRequest(url, "GET", emptyMap())?.let { c -> main.post { deliver(c) } }
            }
            @JavascriptInterface fun onDrm(system: String) { capture.drmSystem = system }
        }, "MTFinder")

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val c = capture.onRequest(request.url.toString(), request.method, request.requestHeaders.orEmpty())
                if (c != null) main.post { deliver(c) }
                return null
            }

            override fun onPageFinished(view: WebView, url: String?) {
                view.evaluateJavascript(PageStreamCapture.HOOK_JS, null)
                view.evaluateJavascript(NUDGE_JS, null)
            }
        }
        return web
    }
}
