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

    /** Applies the common settings. Call once right after creating the WebView. */
    fun harden(web: WebView) {
        runCatching {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                WebSettingsCompat.setRequestedWithHeaderOriginAllowList(web.settings, emptySet())
            }
        }
        web.settings.apply {
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
    }

    /** Detaches and destroys a WebView whose renderer is gone. Never throws. */
    fun dispose(view: WebView?) {
        view ?: return
        runCatching { (view.parent as? ViewGroup)?.removeView(view) }
        runCatching { view.stopLoading() }
        runCatching { view.destroy() }
    }

    /** Logs the renderer crash, disposes the view and keeps the app alive. */
    fun handleRenderGone(view: WebView, detail: RenderProcessGoneDetail?): Boolean {
        Log.w(TAG, "WebView renderer gone (crashed=${detail?.didCrash()}) — disposing it, the app keeps running")
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
}
