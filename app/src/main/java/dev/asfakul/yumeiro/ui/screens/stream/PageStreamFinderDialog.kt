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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import java.net.URI

internal const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

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
    /** OL-3: title of the video this stream belongs to (the page's video title, og:title or page title). */
    val title: String = "",
)

/** Captures streams a page's own player requests. Pure logic (no WebView) so it can be unit tested. */
class PageStreamCapture(private val clock: () -> Long = { System.nanoTime() / 1_000_000L }) {
    private val seen = LinkedHashMap<String, CapturedStream>()
    /** OL-3: when each capture was first seen, so a video switch keeps only what the new video asked for. */
    private val seenAt = HashMap<String, Long>()
    /** OL-3: title of the video playing now; new captures take it. */
    var mediaTitle: String = ""; private set
    var licenseUrl: String? = null; private set
    var drmSystem: String? = null

    /** @return the new capture, or null when the request isn't a stream (or was already seen). */
    fun onRequest(url: String, method: String, headers: Map<String, String>): CapturedStream? {
        if (!url.startsWith("http", ignoreCase = true)) return null
        // A hostile page can call the JS bridge in a loop with endless unique URLs: cap memory.
        if (url.length > MAX_URL || seen.size >= MAX_CAPTURES) return null
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
        val c = CapturedStream(url, sniff.kind, sniff.label, headers, mediaTitle)
        seen[key] = c
        seenAt[key] = clock()
        return c
    }

    fun all(): List<CapturedStream> = seen.values.sortedBy { rank(it.kind) }

    /** OL-2 Rescan: forgets every capture so the page's streams are offered again from scratch. DRM info stays. */
    fun reset() { seen.clear(); seenAt.clear() }

    /**
     * OL-3: the page switched to another video (a new `<video>` source, a new page or an in-page
     * navigation). Captures older than [NEW_MEDIA_GRACE_MS] belong to the old video and are dropped; the
     * newest ones stay because the new video's manifest is often requested just before the switch is seen.
     * @return true when the list changed.
     */
    fun newMedia(title: String?): Boolean {
        val now = clock()
        val old = seenAt.filterValues { now - it > NEW_MEDIA_GRACE_MS }.keys
        old.forEach { seen.remove(it); seenAt.remove(it) }
        val titled = title?.trim()?.takeIf { it.isNotEmpty() }?.let { retitle(it) } ?: false
        return old.isNotEmpty() || titled
    }

    /** OL-3: the video playing now is called [title]; every capture of it shows that title. @return true when anything changed. */
    fun retitle(title: String): Boolean {
        val t = title.trim().take(MAX_TITLE)
        if (t.isEmpty() || (t == mediaTitle && seen.values.all { it.title == t })) return false
        mediaTitle = t
        for (k in seen.keys.toList()) seen[k] = seen.getValue(k).copy(title = t)
        return true
    }

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
        /** More than any real page offers (qualities × audio tracks × mirrors). */
        const val MAX_CAPTURES = 64
        const val MAX_URL = 8_192
        const val MAX_TITLE = 200
        /** OL-3: on a video switch, captures newer than this stay (the new video's own manifest). */
        const val NEW_MEDIA_GRACE_MS = 1_500L
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
  // OL-3: which video plays now. A new source on a video element means the page switched video.
  function title(v){
    try {
      var t = v && (v.getAttribute('title') || v.getAttribute('aria-label'));
      if (t) return String(t);
      var m = document.querySelector('meta[property="og:title"]');
      return String((m && m.content) || document.title || '');
    } catch(e) { return ''; }
  }
  document.addEventListener('loadstart', function(e){
    var v = e.target; if (!v || v.tagName !== 'VIDEO') return;
    var s = v.currentSrc || v.src || '';
    window.__mtLoadAt = performance.now();
    try {
      if (window.__mtSrc && s && s !== window.__mtSrc) MTFinder.onNewMedia(title(v)); else MTFinder.onTitle(title(v));
    } catch(x) {}
    if (s) window.__mtSrc = s;
  }, true);
  document.addEventListener('playing', function(e){
    var v = e.target; if (!v || v.tagName !== 'VIDEO') return;
    try { MTFinder.onTitle(title(v)); } catch(x) {}
  }, true);
  // The hook often lands after the first video already started loading: name it now.
  try {
    var v0 = document.querySelector('video');
    if (v0) { var s0 = v0.currentSrc || v0.src; if (s0) window.__mtSrc = s0; MTFinder.onTitle(title(v0)); }
  } catch(x) {}
})();
"""

        /**
         * OL-2 Rescan: reports what the page's players play now (their current source) and the media files
         * and manifests the page already fetched (Resource Timing), so a cleared list fills again without a reload.
         */
        const val RESCAN_JS = """
(function(){
  try {
    document.querySelectorAll('video,audio,source').forEach(function(v){
      var s = v.currentSrc || v.src;
      if (s && s.indexOf('blob:') !== 0 && s.indexOf('data:') !== 0) MTFinder.onMedia(s);
    });
    var since = (window.__mtLoadAt || 0) - 2000; // OL-3: only what the video playing now fetched
    (performance.getEntriesByType('resource') || []).forEach(function(e){
      if (e.startTime < since) return;
      var n = String(e.name || ''); var l = n.toLowerCase().split('?')[0];
      if (/\.(m3u8|mpd|mp4|webm|mkv|m4v|mov)$/.test(l)) MTFinder.onMedia(n);
    });
  } catch(e) {}
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
    /** OL-1: keep the page alive in [keep] (website tabs): the WebView is reused and never destroyed here. */
    keep: PageKeep? = null,
    /** OL-1: hides the page and keeps it (Minimize button; Back at the first page). Null = no minimize. */
    onMinimize: (() -> Unit)? = null,
) {
    val capture = keep?.capture ?: remember(pageUrl) { PageStreamCapture() }
    // Auto mode: the page works behind a "finding the video" card and only shows when it needs the user.
    var autoActive by remember(pageUrl) { mutableStateOf(auto) }
    var autoStep by remember(pageUrl) { mutableStateOf("Opening the link") }
    var autoNote by remember(pageUrl) { mutableStateOf<String?>(null) }
    var played by remember(pageUrl) { mutableStateOf(false) }
    val found = keep?.found ?: remember(pageUrl) { mutableStateListOf<CapturedStream>() }
    // OL-1: a kept page keeps its title, address, progress and notes too (the WebView's callbacks write them).
    // Own states are always remembered (same slots every composition); a kept page's states win.
    val ownProgress = remember { mutableIntStateOf(0) }
    val ownTitle = remember { mutableStateOf("") }
    val ownUrl = remember { mutableStateOf(pageUrl) }
    val ownDrm = remember { mutableStateOf<String?>(null) }
    val ownPanel = remember { mutableStateOf(StreamPanel.OPEN) }
    val progressState: androidx.compose.runtime.MutableIntState = keep?.progress ?: ownProgress
    val titleState: androidx.compose.runtime.MutableState<String> = keep?.pageTitle ?: ownTitle
    val urlState: androidx.compose.runtime.MutableState<String> = keep?.currentUrl ?: ownUrl
    val drmState: androidx.compose.runtime.MutableState<String?> = keep?.drmNote ?: ownDrm
    val panelState: androidx.compose.runtime.MutableState<StreamPanel> = keep?.panel ?: ownPanel
    var progress by progressState
    var pageTitle by titleState
    var currentUrl by urlState
    var drmNote by drmState
    var showHint by remember { mutableStateOf(true) }
    var panel by panelState
    // OL-8: a later note (sign in, auto gave up) shows the banner again; it hides by itself after 3 s.
    androidx.compose.runtime.LaunchedEffect(autoNote) { if (autoNote != null) showHint = true }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val main = remember { Handler(Looper.getMainLooper()) }

    fun refresh() = main.post { found.clear(); found.addAll(synchronized(capture) { capture.all() }) }

    /** OL-3: the page switched video or page: keep only the new video's streams. */
    fun switched(title: String?) {
        if (!dev.asfakul.yumeiro.FeatureFlags.isOn(dev.asfakul.yumeiro.FeatureFlags.Flag.STREAM_CURRENT_ONLY)) return
        if (synchronized(capture) { capture.newMedia(title) }) refresh()
    }

    fun titled(title: String) {
        if (!dev.asfakul.yumeiro.FeatureFlags.isOn(dev.asfakul.yumeiro.FeatureFlags.Flag.STREAM_CURRENT_ONLY)) return
        if (synchronized(capture) { capture.retitle(title) }) refresh()
    }

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
                c, currentUrl, c.title.ifBlank { pageTitle.ifBlank { base?.title.orEmpty() } },
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

    DisposableEffect(pageUrl, keep) {
        onDispose {
            // OL-1: a kept page only leaves the screen; its owner releases it.
            if (keep == null) webViewRef?.apply { stopLoading(); onPause(); destroy() }
            webViewRef = null
        }
    }

    // Back walks the page history first (browsing a saved website), then minimizes (kept tab) or closes.
    Dialog(onDismissRequest = { val wv = webViewRef; if (wv != null && wv.canGoBack()) wv.goBack() else (onMinimize ?: onDismiss)() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        dev.asfakul.yumeiro.ui.screens.online.FullScreenDialogBars()
        Surface(color = DarkSurface, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("page_finder_close")) { Icon(Icons.Default.Close, "Close", tint = TextPrimary) }
                    Column(Modifier.weight(1f)) {
                        Text(pageTitle.ifBlank { "Find stream" }, color = TextPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(PlatformRules.hostOf(currentUrl) ?: currentUrl, color = TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (onMinimize != null) IconButton(onClick = onMinimize, modifier = Modifier.testTag("page_finder_minimize")) { Icon(Icons.Default.KeyboardArrowDown, "Minimize", tint = TextSecondary) }
                    IconButton(onClick = { webViewRef?.reload() }) { Icon(Icons.Default.Refresh, "Reload", tint = TextSecondary) }
                }
                if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, color = SpotifyGreen, modifier = Modifier.fillMaxWidth())
                if (showHint && !autoActive) PageFinderHint(autoNote ?: reason, onHide = { showHint = false })
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            // OL-1: a kept page comes back as it was (no reload).
                            keep?.webView?.let { kept ->
                                (kept.parent as? android.view.ViewGroup)?.removeView(kept)
                                keep.adopt(ctx)
                                kept.onResume()
                                webViewRef = kept
                                return@AndroidView kept
                            }
                            // Local + with(page), never WebView(..).apply {}: CodeQL counts apply's result as a new WebView and misses these settings.
                            val page = if (keep != null) WebView(android.content.MutableContextWrapper(ctx)) else WebView(ctx)
                            keep?.webView = page
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
                                    @JavascriptInterface fun onNewMedia(title: String) = switched(title)
                                    @JavascriptInterface fun onTitle(title: String) = titled(title)
                                    @JavascriptInterface fun onDrm(system: String) {
                                        val system = dev.asfakul.yumeiro.online.web.pageDrmSystemName(system)
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
                                // DL-WEB: a file link on the page opens the shared download popup.
                                dev.asfakul.yumeiro.online.web.WebDownloads.attach(this) { currentUrl }
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
                                    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                        // OL-3: an in-page navigation (single-page sites) to another address is another video.
                                        if (url != null && !isReload && url.substringBefore('#') != currentUrl.substringBefore('#')) {
                                            currentUrl = url
                                            switched(null)
                                        }
                                    }
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        if (url != null && url.substringBefore('#') != currentUrl.substringBefore('#')) switched(null)
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
                                // OL-1: a kept tab that gave its page back on low memory reopens where it was.
                                dev.asfakul.yumeiro.online.web.SafeWebView.loadUrl(this, if (keep != null) currentUrl else pageUrl, headers)
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
                DetectedStreamsPanel(
                    found = found, drmNote = drmNote, state = panel,
                    nowTitle = if (dev.asfakul.yumeiro.FeatureFlags.isOn(dev.asfakul.yumeiro.FeatureFlags.Flag.STREAM_CURRENT_ONLY)) found.firstOrNull { it.title.isNotBlank() }?.title ?: pageTitle else null, onState = { panel = it },
                    onRescan = {
                        // OL-2: start over: forget what was seen, then ask the page for what it plays now.
                        synchronized(capture) { capture.reset() }
                        found.clear()
                        webViewRef?.evaluateJavascript(PageStreamCapture.RESCAN_JS, null)
                    },
                    onPlay = { play(it) },
                )
            }
        }
    }
}
