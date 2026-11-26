/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.web

import android.util.Log
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/**
 * Shared WebView hardening for every WebView the app creates (browser checks, page capture,
 * "find streams in page").
 *
 * - **Renderer crash safety.** When a WebView's renderer process dies (out of memory, a heavy
 *   ad/player page, a Chromium bug) Android kills the *app* unless the client handles
 *   [WebViewClient.onRenderProcessGone]. That was one source of the "app freezes, then force
 *   closes" reports while streams were opening (a headless page capture runs in the background).
 * - **No `X-Requested-With: <package>` header.** Android WebView adds it to every request; bot
 *   protection (Cloudflare WAF rules) uses it to flag embedded browsers and answers "Sorry, you
 *   have been blocked" instead of showing the captcha.
 */
object SafeWebView {
    private const val TAG = "SafeWebView"

    /**
     * FINAL-7: loads [url] with extra request headers. Older WebView builds (Chromium 53 on Android 7,
     * the TV image tv-api24) remove "Referer" from the caller's map inside `AwContents.loadUrl`; a
     * read-only map (`mapOf`, `emptyMap`, `buildMap`) then throws UnsupportedOperationException and the
     * page never opens (device-check 44600322143). The headers are always handed over as a fresh HashMap.
     */
    fun loadUrl(web: WebView, url: String, headers: Map<String, String>) = web.loadUrl(url, headersFor(headers))

    /** The header map WebView receives: a mutable copy, so the WebView may edit it. */
    fun headersFor(headers: Map<String, String>): MutableMap<String, String> = HashMap(headers)

    /** Applies the common settings. Call once right after creating the WebView. */
    // The allow-list feature constant is public API guarded by isFeatureSupported; lint's
    // RestrictedApi check misreads its @RestrictTo annotation on the constant's group.
    @android.annotation.SuppressLint("RestrictedApi")
    fun harden(web: WebView) {
        // NBUG-13 (ADR-045): the user's proxy is in place before the page loads.
        runCatching { dev.asfakul.yumeiro.network.AppProxy.ensureWebView(web.context) }
        runCatching {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                WebSettingsCompat.setRequestedWithHeaderOriginAllowList(web.settings, emptySet())
            }
        }
        web.settings.apply {
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            // With multiple windows OFF, an ad's window.open / target=_blank replaced the page
            // itself (the black ad screen on captcha pages). ON + a WebChromeClient that declines
            // onCreateWindow (the default) drops such pop-ups instead.
            setSupportMultipleWindows(true)
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
    }

    /**
     * Adds the [AdShield] page script (no pop-ups / click-catcher overlays) where the WebView
     * supports document-start scripts; elsewhere the pop-up handling in the client still applies.
     */
    @android.annotation.SuppressLint("RequiresFeature")
    fun addAdShield(web: WebView): androidx.webkit.ScriptHandler? = runCatching {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            androidx.webkit.WebViewCompat.addDocumentStartJavaScript(web, AdShield.DOCUMENT_START_JS, setOf("*"))
        } else null
    }.getOrNull()

    /**
     * Detaches and destroys a WebView. Never throws.
     *
     * Android 7.x (API 24–25) renders every page on one renderer thread inside the app. Destroying a page at once,
     * while its ad frames and scripts are still running, left that thread stuck: the next page never started and the
     * renderer then aborted the app ~90 s later (TV api24, runs 45761414933 and 45764062656: SIGSEGV 0xfbadbeef in
     * Chrome_InProcRe after adblock-tester.com was closed). There the page's scripts are switched off and it is blanked
     * first, and it is destroyed only once the blank page has replaced it (or after [RETIRE_MS]).
     */
    fun dispose(view: WebView?) {
        view ?: return
        runCatching { (view.parent as? ViewGroup)?.removeView(view) }
        runCatching { view.stopLoading() }
        if (android.os.Build.VERSION.SDK_INT >= 26) { runCatching { view.destroy() }; return }
        var done = false
        val finish = Runnable { if (!done) { done = true; runCatching { view.removeAllViews(); view.destroy() } } }
        runCatching {
            view.onPause()
            view.settings.javaScriptEnabled = false
            view.webChromeClient = null
            view.webViewClient = object : android.webkit.WebViewClient() {
                override fun onPageFinished(v: WebView?, url: String?) { if (url == "about:blank") view.post(finish) }
            }
            view.loadUrl("about:blank")
        }.onFailure { finish.run(); return }
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(finish, RETIRE_MS)
    }

    /** Longest wait for the blank page before an old (in-app renderer) WebView is destroyed anyway. */
    const val RETIRE_MS = 1_500L

    /** Logs the renderer crash, disposes the view and keeps the app alive. */
    fun handleRenderGone(view: WebView, detail: RenderProcessGoneDetail?): Boolean {
        Log.w(TAG, "WebView renderer gone (crashed=${if (android.os.Build.VERSION.SDK_INT >= 26) detail?.didCrash() else null}) — disposing it, the app keeps running")
        dispose(view)
        return true
    }
}

/** A [WebViewClient] that never lets a renderer crash take the app down. */
open class SafeWebViewClient(private val onGone: (() -> Unit)? = null) : WebViewClient() {
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail?): Boolean {
        val handled = SafeWebView.handleRenderGone(view, detail)
        runCatching { onGone?.invoke() }
        return handled
    }

    /** NBUG-13: the proxy's own login prompt is answered with the saved login; other prompts as before. */
    override fun onReceivedHttpAuthRequest(view: WebView?, handler: android.webkit.HttpAuthHandler, host: String?, realm: String?) {
        if (dev.asfakul.yumeiro.network.AppProxy.answerWebAuth(host, handler)) return
        super.onReceivedHttpAuthRequest(view, handler, host, realm)
    }
}
