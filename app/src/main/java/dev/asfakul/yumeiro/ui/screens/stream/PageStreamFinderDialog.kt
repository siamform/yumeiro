/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.stream

import dev.asfakul.yumeiro.online.web.SafeWebView
import dev.asfakul.yumeiro.online.web.SafeWebViewClient
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.asfakul.yumeiro.stream.DrmScheme
import dev.asfakul.yumeiro.stream.MediaKind
import dev.asfakul.yumeiro.stream.PlatformRules
import dev.asfakul.yumeiro.stream.StreamFormat
import dev.asfakul.yumeiro.stream.StreamRequest
import dev.asfakul.yumeiro.stream.StreamSniffer
import dev.asfakul.yumeiro.ui.theme.DarkSurface
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.OnAccent
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import java.net.URI

/** A media request seen while the page was loading / playing. */
data class CapturedStream(
    val url: String,
    val kind: MediaKind,
    val label: String,
    val headers: Map<String, String>,
)

/** Captures streams a page's own player requests. Pure logic (no WebView) so it can be unit tested. */
class PageStreamCapture {
    private val seen = LinkedHashMap<String, CapturedStream>()
    var licenseUrl: String? = null; private set
    var drmSystem: String? = null

    /** @return the new capture, or null when the request isn't a stream (or was already seen). */
    fun onRequest(url: String, method: String, headers: Map<String, String>): CapturedStream? {
        if (!url.startsWith("http", ignoreCase = true)) return null
        // Ad clips (VAST / pre-roll / ad CDNs) are never offered as the stream.
        if (dev.asfakul.yumeiro.online.web.AdFilter.isAd(url)) return null
        val lower = url.lowercase()
        if (method.equals("POST", true) && LICENSE_HINTS.any { it in lower }) {
            if (licenseUrl == null) licenseUrl = url
            return null
        }
        val sniff = StreamSniffer.fromUrl(url) ?: return null
        if (!sniff.isPlayableMedia) return null
        // Segments are noise; manifests and whole files are what the player needs.
        if (SEGMENT_EXT.any { lower.substringBefore('?').endsWith(it) } && sniff.kind != MediaKind.AUDIO) {
            if (seen.values.any { it.kind == MediaKind.HLS || it.kind == MediaKind.DASH }) return null
        }
        val key = lower.substringBefore('#')
        if (key in seen) return null
        val c = CapturedStream(url, sniff.kind, sniff.label, headers)
        seen[key] = c
        return c
    }

    fun all(): List<CapturedStream> = seen.values.sortedBy { rank(it.kind) }

    private fun rank(k: MediaKind) = when (k) {
        MediaKind.HLS, MediaKind.DASH, MediaKind.SMOOTH -> 0
        MediaKind.VIDEO -> 1
        else -> 2
    }

    /** Builds the request the player needs: the page's User-Agent, Referer/Origin, cookies and auth headers. */
    fun toRequest(c: CapturedStream, pageUrl: String, title: String, userAgent: String?, cookie: String?, base: StreamRequest?): StreamRequest {
        val h = c.headers.entries.associate { it.key.lowercase() to it.value }
        val pageOrigin = runCatching { URI(pageUrl).let { "${it.scheme}://${it.authority}" } }.getOrNull()
        val extra = c.headers.filterKeys { k ->
            val l = k.lowercase()
            l == "authorization" || (l.startsWith("x-") && l != "x-requested-with")
        }
        val widevine = drmSystem?.contains("widevine", true) == true && licenseUrl != null
        return StreamRequest(
            url = c.url,
            title = title,
            format = when (c.kind) {
                MediaKind.HLS -> StreamFormat.HLS
                MediaKind.DASH -> StreamFormat.DASH
                MediaKind.SMOOTH -> StreamFormat.SMOOTH
                else -> StreamFormat.PROGRESSIVE
            },
            userAgent = base?.userAgent ?: h["user-agent"] ?: userAgent,
            referer = base?.referer ?: h["referer"] ?: pageUrl,
            origin = base?.origin ?: h["origin"] ?: pageOrigin,
            cookie = listOfNotNull(base?.cookie, cookie?.takeIf { it.isNotBlank() }).joinToString("; ").ifBlank { null },
            headers = (base?.headers ?: emptyMap()) + extra,
            drmScheme = if (widevine) DrmScheme.WIDEVINE else base?.drmScheme ?: DrmScheme.NONE,
            drmLicenseUrl = if (widevine) licenseUrl else base?.drmLicenseUrl,
            clearKeys = base?.clearKeys,
            pageUrl = pageUrl,
        )
    }

    companion object {
        private val LICENSE_HINTS = listOf("license", "licence", "widevine", "drm", "/wv", "getkey")
        private val SEGMENT_EXT = listOf(".ts", ".m4s", ".aac", ".mp4", ".m4a", ".m4v", ".cmfv", ".cmfa")

        /** Injected into pages: reports `<video>` sources and which DRM system the page asks for. */
        const val HOOK_JS = """
(function(){
  if (window.__mtFinder) return; window.__mtFinder = true;
  try {
    var o = navigator.requestMediaKeySystemAccess;
    if (o) navigator.requestMediaKeySystemAccess = function(k){ try { MTFinder.onDrm(String(k)); } catch(e){} return o.apply(navigator, arguments); };
  } catch(e) {}
  function scan(){
    try {
      document.querySelectorAll('video,audio,source').forEach(function(v){
        var s = v.currentSrc || v.src;
        if (s && s.indexOf('blob:') !== 0 && s.indexOf('data:') !== 0) MTFinder.onMedia(s);
      });
    } catch(e) {}
  }
  scan(); setInterval(scan, 1500);
})();
"""
    }
}

/**
 * Full-screen page browser for links that point to a streaming site's web page (e.g. Toffee).
 * The page runs normally — the user signs in and starts playback themselves — and the stream
 * manifest the site's player requests is picked up with the same session headers. Nothing is
 * decrypted or bypassed: DRM, sign-in and region checks stay with the site.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PageStreamFinderDialog(
    pageUrl: String,
    reason: String,
    base: StreamRequest?,
    onPlay: (StreamRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    val capture = remember(pageUrl) { PageStreamCapture() }
    val found = remember(pageUrl) { mutableStateListOf<CapturedStream>() }
    var progress by remember { mutableIntStateOf(0) }
    var pageTitle by remember { mutableStateOf("") }
    var currentUrl by remember { mutableStateOf(pageUrl) }
    var drmNote by remember { mutableStateOf<String?>(null) }
    var showHint by remember { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val main = remember { Handler(Looper.getMainLooper()) }

    fun record(url: String, method: String, headers: Map<String, String>) {
        synchronized(capture) { capture.onRequest(url, method, headers) }?.let { c ->
            main.post { if (found.none { it.url == c.url }) { found.clear(); found.addAll(synchronized(capture) { capture.all() }) } }
        }
    }

    fun play(c: CapturedStream) {
        val wv = webViewRef
        val request = synchronized(capture) {
            capture.toRequest(
                c, currentUrl, pageTitle.ifBlank { base?.title.orEmpty() },
                userAgent = wv?.settings?.userAgentString,
                cookie = runCatching { CookieManager.getInstance().getCookie(c.url) }.getOrNull(),
                base = base,
            )
        }
        wv?.onPause()
        onPlay(request)
    }

    DisposableEffect(pageUrl) {
        onDispose {
            webViewRef?.apply { stopLoading(); onPause(); destroy() }
            webViewRef = null
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(color = DarkSurface, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("page_finder_close")) { Icon(Icons.Default.Close, "Close", tint = TextPrimary) }
                    Column(Modifier.weight(1f)) {
                        Text(pageTitle.ifBlank { "Find stream" }, color = TextPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(PlatformRules.hostOf(currentUrl) ?: currentUrl, color = TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { webViewRef?.reload() }) { Icon(Icons.Default.Refresh, "Reload", tint = TextSecondary) }
                }
                if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, color = SpotifyGreen, modifier = Modifier.fillMaxWidth())
                if (showHint) {
                    Surface(color = GlassFill, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(8.dp).clickable { showHint = false }) {
                        Text(
                            reason + "\n\nStart playback on the page; detected streams appear below. Tap to hide.",
                            color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(10.dp),
                        )
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewRef = this
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                settings.mediaPlaybackRequiresUserGesture = true
                                base?.userAgent?.let { settings.userAgentString = it }
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                addJavascriptInterface(object {
                                    @JavascriptInterface fun onMedia(src: String) = record(src, "GET", emptyMap())
                                    @JavascriptInterface fun onDrm(system: String) {
                                        synchronized(capture) { capture.drmSystem = system }
                                        main.post { drmNote = "This page uses DRM ($system). It plays here only if the site's licence server accepts this app; protection is never bypassed." }
                                    }
                                }, "MTFinder")
                                webChromeClient = object : android.webkit.WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) { progress = newProgress }
                                    override fun onReceivedTitle(view: WebView?, title: String?) { pageTitle = title.orEmpty() }
                                }
                                SafeWebView.harden(this)
                                webViewClient = object : SafeWebViewClient(onGone = { onDismiss() }) {
                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        val scheme = request?.url?.scheme?.lowercase()
                                        return scheme != "http" && scheme != "https" // block intent:, market:, file: …
                                    }
                                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                        // Built-in ad blocking: pre-rolls and pop-unders never load or get offered as "the stream".
                                        if (request != null && dev.asfakul.yumeiro.online.web.AdFilter.isAd(request.url.toString())) return dev.asfakul.yumeiro.online.web.AdFilter.blocked()
                                        request?.let { record(it.url.toString(), it.method ?: "GET", it.requestHeaders.orEmpty()) }
                                        return null // the page loads normally; we only observe
                                    }
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        url?.let { currentUrl = it }
                                        view?.evaluateJavascript(PageStreamCapture.HOOK_JS, null)
                                    }
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        view?.evaluateJavascript(PageStreamCapture.HOOK_JS, null)
                                    }
                                }
                                val headers = buildMap {
                                    base?.referer?.let { put("Referer", it) }
                                    base?.headers?.let { putAll(it) }
                                }
                                loadUrl(pageUrl, headers)
                            }
                        },
                    )
                }
                Surface(color = DarkSurface, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (found.isEmpty()) "No stream detected yet — start the video on the page." else "Detected streams",
                            color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        )
                        drmNote?.let { Text(it, color = TextTertiary, fontSize = 11.sp) }
                        LazyColumn(Modifier.heightIn(max = 180.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(found, key = { it.url }) { c ->
                                Row(
                                    Modifier.fillMaxWidth().background(GlassFill, RoundedCornerShape(12.dp)).clickable { play(c) }.padding(10.dp).testTag("page_finder_stream"),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(c.label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                        Text(c.url, color = TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Box(Modifier.size(34.dp).background(SpotifyGreen, RoundedCornerShape(17.dp)), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.PlayArrow, "Play", tint = OnAccent, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
