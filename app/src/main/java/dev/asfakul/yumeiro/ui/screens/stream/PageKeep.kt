/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.stream

import android.content.MutableContextWrapper
import android.webkit.WebView
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

/**
 * OL-1: everything a website page needs to survive while it is not on screen — the WebView itself
 * (page, scroll, sign-in, JavaScript state), what it detected, and its title / address. Handed to
 * [PageStreamFinderDialog] as `keep`: the dialog then reuses this WebView instead of creating and
 * destroying its own. Only [release] destroys it.
 */
class PageKeep(val pageUrl: String) {
    /**
     * The kept page. Its context is a [MutableContextWrapper] so it can move to a new Activity.
     * CQ-97..104: held in a private field (read directly, not through a getter). Whoever hands a WebView in
     * gets file and content:// access switched off again here, so a kept page can never read app files.
     */
    private var page: WebView? = null
    internal var webView: WebView?
        get() = page
        set(value) {
            if (value != null) {
                value.settings.allowFileAccess = false
                value.settings.allowContentAccess = false
            }
            page = value
        }
    internal val capture = PageStreamCapture()
    internal val found = mutableStateListOf<CapturedStream>()
    internal val pageTitle = mutableStateOf("")
    internal val currentUrl = mutableStateOf(pageUrl)
    internal val progress = mutableIntStateOf(0)
    internal val drmNote = mutableStateOf<String?>(null)
    internal val panel = mutableStateOf(StreamPanel.OPEN)

    /** Device tests: the kept page itself (same instance after hide / show = not re-created). */
    @get:androidx.annotation.VisibleForTesting val webViewForTest: WebView? get() = page

    /** True while the WebView is alive (false after [release], e.g. on low memory: the page reloads at [currentUrl]). */
    val alive: Boolean get() = page != null
    val title: String get() = pageTitle.value
    val address: String get() = currentUrl.value

    /** Re-points the kept WebView at [activity] (Activity re-created) before it is shown again. */
    internal fun adopt(activity: android.content.Context) {
        (page?.context as? MutableContextWrapper)?.baseContext = activity
    }

    /** Off screen: the page's own players stop (no sound behind the app) and timers ease off. */
    fun pause() {
        // A plain local, not page?.apply { }: CodeQL counts apply's result as a new, unhardened WebView.
        val w = page ?: return
        w.evaluateJavascript(PAUSE_JS, null)
        w.onPause()
    }

    fun resume() { page?.onResume() }

    /** Destroys the page (closing the tab, or low memory). Captures are cleared; the address is kept. */
    fun release() {
        val w = page
        if (w != null) {
            // Android 7 renders pages inside the app: the page is blanked and retired gently (see SafeWebView.dispose).
            dev.asfakul.yumeiro.online.web.SafeWebView.dispose(w)
        }
        page = null
        synchronized(capture) { capture.reset() }
        found.clear()
    }

    companion object {
        const val PAUSE_JS = "(function(){try{document.querySelectorAll('video,audio').forEach(function(v){try{v.pause();}catch(e){}});}catch(e){}})();"
    }
}
