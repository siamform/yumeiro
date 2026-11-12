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

private const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

/**
 * Covers the working page in auto mode: what is happening, and a way to see the page. The page
 * keeps running underneath (players only start while laid out).
 */
@Composable
private fun AutoFindCard(host: String, step: String, onShowPage: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.background(DarkSurface).testTag("page_finder_auto"), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(32.dp)) {
            androidx.compose.material3.CircularProgressIndicator(color = SpotifyGreen, strokeWidth = 3.dp, modifier = Modifier.size(42.dp))
            Text("Finding the video on $host", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            androidx.compose.animation.AnimatedContent(step, label = "autoStep") { Text("$it…", color = TextSecondary, fontSize = 13.sp) }
            androidx.compose.material3.TextButton(onClick = onShowPage, modifier = Modifier.testTag("page_finder_show_page")) {
                Text("Show the page", color = SpotifyGreen)
            }
        }
    }
}

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

        /**
         * Auto mode: starts the page's own player once (muted, so no surprise sound) — first any
         * `<video>` already on the page, else the visible play button of the common web players.
         * Nothing is clicked while a video is already playing, so it never toggles playback off.
         */
        const val AUTOPLAY_JS = """
(function(){
  try {
    var vs = Array.prototype.slice.call(document.querySelectorAll('video'));
    if (vs.some(function(v){ return !v.paused && !v.ended; })) return 'playing';
    var started = false;
    vs.forEach(function(v){ try { v.muted = true; var p = v.play(); if (p && p.catch) p.catch(function(){}); started = true; } catch(e){} });
    if (started) return 'video';
    var sel = ['.vjs-big-play-button','.plyr__control--overlaid','.jw-icon-display','.dplayer-mobile-play','.art-control-play',
      '.play-btn','.video-play-btn','.btn-play','.play-button','[class*="play-icon"]','[class*="playBtn"]','[class*="play-btn"]',
      '[aria-label="Play"]','button[title="Play"]'];
    for (var i = 0; i < sel.length; i++) {
      var b = document.querySelector(sel[i]);
      if (b && b.offsetParent !== null) { b.click(); return 'button'; }
    }
    return 'none';
  } catch(e) { return 'error'; }
})();
"""

        /** How long auto mode waits for the page's stream before showing the page itself. */
        const val AUTO_TIMEOUT_MS = 25_000L
        /** After the first capture, a short wait so a manifest (HLS / DASH) wins over a plain file. */
        const val AUTO_SETTLE_MS = 1_200L

        /** Pages that need the user: sign-in, verification. Auto mode shows them right away. */
        fun needsUser(url: String): Boolean {
            val u = url.lowercase()
            return listOf("/login", "passport.", "/signin", "/sign-in", "/auth/", "captcha", "/verify").any { it in u }
        }

        /** Best capture for auto play: manifests first, then whole video files; never audio-only. */
        fun bestForAuto(list: List<CapturedStream>): CapturedStream? =
            list.firstOrNull { it.kind == MediaKind.HLS || it.kind == MediaKind.DASH } ?: list.firstOrNull { it.kind == MediaKind.VIDEO }
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
    /** Start the page's player by itself and play the first stream (known file hosts). */
    auto: Boolean = false,
) {
    val capture = remember(pageUrl) { PageStreamCapture() }
    // Auto mode: the page works behind a "finding the video" card and only shows when it needs the user.
    var autoActive by remember(pageUrl) { mutableStateOf(auto) }
    var autoStep by remember(pageUrl) { mutableStateOf("Opening the link") }
    var autoNote by remember(pageUrl) { mutableStateOf<String?>(null) }
    var played by remember(pageUrl) { mutableStateOf(false) }
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
        if (played) return // a second capture or a double tap never opens two players
        played = true
        autoActive = false
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

    if (auto) {
        // Give up waiting after a while: the page is shown with an honest note instead of a spinner forever.
        androidx.compose.runtime.LaunchedEffect(pageUrl) {
            kotlinx.coroutines.delay(PageStreamCapture.AUTO_TIMEOUT_MS)
            if (autoActive && !played) {
                autoActive = false
                autoNote = "Yumeiro couldn't start this video by itself. Start it on the page and it will play here."
            }
        }
        // First capture → short settle (prefer a manifest) → play.
        androidx.compose.runtime.LaunchedEffect(pageUrl, found.size, autoActive) {
            if (!autoActive || played || found.isEmpty()) return@LaunchedEffect
            autoStep = "Picking up the stream"
            kotlinx.coroutines.delay(PageStreamCapture.AUTO_SETTLE_MS)
            PageStreamCapture.bestForAuto(found.toList())?.let { play(it) }
        }
        // Nudge the page's player a few times while the page settles (players load late).
        androidx.compose.runtime.LaunchedEffect(pageUrl, progress >= 100, autoActive) {
            if (!autoActive || progress < 100) return@LaunchedEffect
            autoStep = "Starting its player"
            repeat(8) {
                if (!autoActive || played) return@LaunchedEffect
                webViewRef?.evaluateJavascript(PageStreamCapture.AUTOPLAY_JS, null)
                kotlinx.coroutines.delay(2_000L)
            }
        }
    }

    DisposableEffect(pageUrl) {
        onDispose {
            webViewRef?.apply { stopLoading(); onPause(); destroy() }
            webViewRef = null
        }
    }

    // Back walks the page history first (browsing a saved website), then closes.
    Dialog(onDismissRequest = { val wv = webViewRef; if (wv != null && wv.canGoBack()) wv.goBack() else onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        dev.asfakul.yumeiro.ui.screens.online.FullScreenDialogBars()
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
                if (showHint && !autoActive) {
                    Surface(color = GlassFill, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(8.dp).clickable { showHint = false }.testTag("page_finder_hint")) {
                        Text(
                            (autoNote ?: reason) + "\n\nStart playback on the page; detected streams appear below. Tap to hide.",
                            color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(10.dp),
                        )
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            // Local + with(page), never WebView(..).apply {}: CodeQL counts apply's result as a new WebView and misses these settings.
                            val page = WebView(ctx)
                            page.settings.allowFileAccess = false
                            page.settings.allowContentAccess = false
                            with(page) {
                                webViewRef = this
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                // Auto mode may start the page's player (muted) without a tap.
                                settings.mediaPlaybackRequiresUserGesture = !auto
                                base?.userAgent?.let { settings.userAgentString = it }
                                // File hosts serve their web player to desktop browsers; on mobile UAs they push their app.
                                if (auto && base?.userAgent == null) settings.userAgentString = DESKTOP_UA
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
                                // Pop-ups and click-catcher ad overlays stay off while the user browses.
                                SafeWebView.addAdShield(this)
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
                                        if (autoActive && url != null && PageStreamCapture.needsUser(url)) {
                                            autoActive = false
                                            autoNote = "Sign in or finish the check on the page once; the video then plays here."
                                        }
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
                            page
                        },
                    )
                    if (autoActive) AutoFindCard(
                        host = PlatformRules.hostOf(pageUrl) ?: "the page",
                        step = autoStep,
                        onShowPage = { autoActive = false },
                        modifier = Modifier.fillMaxSize(),
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
