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
import androidx.compose.material.icons.filled.RemoveModerator
import androidx.compose.material.icons.filled.Shield
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import dev.asfakul.yumeiro.stream.Sniff
import dev.asfakul.yumeiro.stream.StreamSniffer
import dev.asfakul.yumeiro.ui.theme.DarkSurface
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import java.net.URI
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

/**
 * Covers the working page in auto mode: what is happening, and a way to see the page. The page
 * keeps running underneath (players only start while laid out).
 */
@Composable
private fun AutoFindCard(host: String, step: String, onShowPage: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.background(DarkSurface).testTag("page_finder_auto"), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(32.dp)) {
            dev.asfakul.yumeiro.ui.components.YuLoader(size = 56.dp, label = "Looking for streams")
            Text("Finding the video on $host", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            androidx.compose.animation.AnimatedContent(step, label = "autoStep") { Text("$it…", color = TextSecondary, fontSize = 13.sp) }
            androidx.compose.material3.TextButton(onClick = onShowPage, modifier = Modifier.testTag("page_finder_show_page")) {
                Text("Show the page", color = SpotifyGreen)
            }
        }
    }
}

/** DW-BOT-1: asked once before a Telegram bot's mini app opens with the user's account. */
@Composable
private fun MiniAppConsentCard(bot: String, onAllow: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.background(DarkSurface).testTag("page_finder_miniapp_consent"), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(28.dp)) {
            Text("Open @$bot with your Telegram account?", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Text(dev.asfakul.yumeiro.online.telegram.td.TgMiniApp.CONSENT_TEXT, color = TextSecondary, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.TextButton(onClick = onCancel, modifier = Modifier.testTag("page_finder_miniapp_cancel")) { Text("Not now", color = TextSecondary) }
                androidx.compose.material3.Button(onClick = onAllow, modifier = Modifier.testTag("page_finder_miniapp_allow")) { Text("Open") }
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
    fun onRequest(url: String, method: String, headers: Map<String, String>): CapturedStream? = onRequest(url, method, headers, null)

    /**
     * SD-1: what the page itself proved is media. [kind] comes from [MEDIA_TAP_JS] ("hls-master", "hls",
     * "dash": the response body starts like a playlist) or from a `<video>` element's own source
     * ("element"). Hosts hide playlists behind addresses without an extension and even a fake
     * image/jpeg type, so the address alone can't tell.
     */
    fun onPageMedia(url: String, kind: String): CapturedStream? {
        val hint = when (kind) {
            "hls-master" -> Sniff(MediaKind.HLS, "master", "page")
            "hls" -> {
                // A master's variant playlists are the same video: offer the master only.
                if (seen.values.any { it.kind == MediaKind.HLS && it.label.endsWith("master") }) return null
                Sniff(MediaKind.HLS, null, "page")
            }
            "dash" -> Sniff(MediaKind.DASH, null, "page")
            // A <video> element's source is media whatever its address looks like; a known extension still wins.
            "element" -> StreamSniffer.fromUrl(url) ?: Sniff(MediaKind.VIDEO, null, "element")
            "audio" -> StreamSniffer.fromUrl(url) ?: Sniff(MediaKind.AUDIO, null, "element")
            else -> return null
        }
        return onRequest(url, "GET", emptyMap(), hint)
    }

    private fun onRequest(url: String, method: String, headers: Map<String, String>, hint: Sniff?): CapturedStream? {
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
        val sniff = hint ?: StreamSniffer.fromUrl(url) ?: return null
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
      document.querySelectorAll('video,audio,video source,audio source').forEach(function(v){
        var s = v.currentSrc || v.src;
        if (s && s.indexOf('blob:') !== 0 && s.indexOf('data:') !== 0) {
          var a = (v.tagName === 'AUDIO' || (v.parentNode && v.parentNode.tagName === 'AUDIO'));
          if (a && MTFinder.onManifest) MTFinder.onManifest(s, 'audio'); else MTFinder.onMedia(s);
        }
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
    document.querySelectorAll('video,audio,video source,audio source').forEach(function(v){
      var s = v.currentSrc || v.src;
      if (s && s.indexOf('blob:') !== 0 && s.indexOf('data:') !== 0) {
        var a = (v.tagName === 'AUDIO' || (v.parentNode && v.parentNode.tagName === 'AUDIO'));
        if (a && MTFinder.onManifest) MTFinder.onManifest(s, 'audio'); else MTFinder.onMedia(s);
      }
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

        /**
         * SD-1: runs at document start in every frame (players often sit in an iframe). It reads only the
         * first bytes of what the page's own XHR / fetch calls get back and reports playlists
         * (`#EXTM3U`, `<MPD`) with `MTFinder.onManifest(url, kind)`. Nothing is changed or blocked: the
         * page gets its response untouched, and a fetch body is read from a clone that is cancelled after
         * its first chunk, so a large file is never downloaded twice.
         */
        const val MEDIA_TAP_JS = """
(function(){
  if (window.__yuTap) return; window.__yuTap = true;
  function kind(t){
    if (!t) return '';
    var s = String(t).slice(0, 4096).replace(/^\uFEFF/, '').replace(/^\s+/, '');
    if (s.indexOf('#EXTM3U') === 0) return s.indexOf('#EXT-X-STREAM-INF') >= 0 ? 'hls-master' : 'hls';
    if (/^(<\?xml[^>]*>\s*)?<MPD[\s>]/.test(s)) return 'dash';
    return '';
  }
  function tell(u, k){ try { if (k && window.MTFinder && MTFinder.onManifest) MTFinder.onManifest(String(u), k); } catch(e) {} }
  function bytes(a, n){ var s = ''; n = Math.min(a.length, n); for (var i = 0; i < n; i++) s += String.fromCharCode(a[i]); return s; }
  try {
    var X = XMLHttpRequest.prototype, op = X.open, sd = X.send;
    X.open = function(m, u){ try { this.__yuU = new URL(u, location.href).href; } catch(e) {} return op.apply(this, arguments); };
    X.send = function(){
      var x = this;
      try {
        x.addEventListener('load', function(){
          try {
            var rt = x.responseType, t = '';
            if (rt === '' || rt === 'text') t = x.responseText;
            else if (rt === 'arraybuffer' && x.response) t = bytes(new Uint8Array(x.response), 4096);
            tell(x.responseURL || x.__yuU, kind(t));
          } catch(e) {}
        });
      } catch(e) {}
      return sd.apply(this, arguments);
    };
  } catch(e) {}
  try {
    var f = window.fetch;
    if (f) window.fetch = function(){
      return f.apply(this, arguments).then(function(r){
        try {
          if (r && r.body && r.clone && !r.bodyUsed) {
            var rd = r.clone().body.getReader();
            rd.read().then(function(c){ try { rd.cancel(); } catch(e) {} if (c && c.value) tell(r.url, kind(bytes(c.value, 4096))); }).catch(function(){});
          }
        } catch(e) {}
        return r;
      });
    };
  } catch(e) {}
})();
"""

        /** SD-1: adds [MEDIA_TAP_JS] to every frame of [web] where the WebView supports document-start scripts. */
        @android.annotation.SuppressLint("RequiresFeature")
        fun addMediaTap(web: android.webkit.WebView): Boolean = runCatching {
            if (!androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT)) return@runCatching false
            androidx.webkit.WebViewCompat.addDocumentStartJavaScript(web, MEDIA_TAP_JS, setOf("*"))
            true
        }.getOrDefault(false)

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
    /** WEB-BAR-1 (owner list 5): Yumeiro's browser for the Websites page: bottom bar, element blocker, visited sites. */
    browser: Boolean = false,
    /** GROUP E2: website tabs. Count for the bar's tab button, the tab switcher, a link to another site in a new tab, close this tab. */
    tabCount: Int = 0,
    onTabs: (() -> Unit)? = null,
    onNewTab: ((String) -> Unit)? = null,
    onCloseTab: (() -> Unit)? = null,
    /** GROUP E2: drawn inside the page's window (the tab switcher), so it sits above the page, not under it. */
    overlay: @Composable () -> Unit = {},
) {
    val newTabNow = androidx.compose.runtime.rememberUpdatedState(onNewTab)
    var manageAds by remember { mutableStateOf(false) }
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
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    // WEB-BAR-1: page history state for Back / Forward, and the element picker.
    var canBack by remember { mutableStateOf(false) }
    var canForward by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    // Notes of the browser bar, shown inside the page (an app message would sit behind this full-screen window).
    var barNote by remember { mutableStateOf<BarNote?>(null) }
    androidx.compose.runtime.LaunchedEffect(barNote) { if (barNote != null) { kotlinx.coroutines.delay(5_000); barNote = null } }
    fun history(view: WebView?) { view?.let { canBack = it.canGoBack(); canForward = it.canGoForward() } }

    // ADCHAIN-1 (owner list 23, ADR-060): a link-shortener chain walks to the real link by itself.
    var chainState by remember(pageUrl) { mutableStateOf(dev.asfakul.yumeiro.online.web.LinkChainPolicy.State()) }
    var chainBanner by remember(pageUrl) { mutableStateOf<ChainBanner?>(null) }
    // ADCHAIN-2 (ADR-062): the saved switches (Websites page) and this page's own Filter button.
    val resolveLinks by dev.asfakul.yumeiro.online.web.BrowserFilters.resolveLinks.collectAsState()
    val filterSaved by dev.asfakul.yumeiro.online.web.BrowserFilters.filterAds.collectAsState()
    var pageFilter by remember(pageUrl) { mutableStateOf(dev.asfakul.yumeiro.online.web.BrowserFilters.pageChoice(keep?.webView)) }
    val filterOn = pageFilter ?: filterSaved
    val chainActiveNow = androidx.compose.runtime.rememberUpdatedState(chainState.active)
    val chainRevealedNow = androidx.compose.runtime.rememberUpdatedState(chainState.revealed)
    // A chain that ended on a host page (Gofile): the first stream its player loads plays in Yumeiro.
    var chainAutoPick by remember(pageUrl) { mutableStateOf(false) }
    if (dev.asfakul.yumeiro.FeatureFlags.isOn(dev.asfakul.yumeiro.FeatureFlags.Flag.ADCHAIN_1)) {
        androidx.compose.runtime.LaunchedEffect(pageUrl) {
            val script = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { dev.asfakul.yumeiro.online.web.LinkChain.script(appContext) }.getOrNull()
            } ?: return@LaunchedEffect
            var lastUrl = ""
            var quiet = 0
            while (true) {
                kotlinx.coroutines.delay(if (chainState.active) 1_000L else 1_500L)
                val wv = webViewRef ?: continue
                val url = currentUrl
                if (url != lastUrl) { lastUrl = url; quiet = 0 }
                // An ordinary page is read a few times after it loads, then left alone until the address changes.
                if (!chainState.active && quiet >= 6) continue
                if (chainState.stopped) continue
                // Switched off on the Websites page: Yumeiro leaves every page alone (nothing is tapped or hidden).
                if (!resolveLinks) { if (chainBanner != null) chainBanner = null; continue }
                if (progress in 1..29) continue
                // A page that is navigating away may never answer; the next scan tries again.
                val result = kotlinx.coroutines.withTimeoutOrNull(4_000L) {
                    kotlinx.coroutines.suspendCancellableCoroutine<String?> { c ->
                        wv.evaluateJavascript(script) { r -> if (c.isActive) c.resumeWith(Result.success(r)) }
                    }
                }
                val scan = dev.asfakul.yumeiro.online.web.LinkChain.parse(result)
                if (scan?.chain != true) quiet++
                val host = PlatformRules.hostOf(url) ?: url
                val adPage = dev.asfakul.yumeiro.online.web.BrowserFilters.pageOn(wv) && dev.asfakul.yumeiro.online.web.AdFilter.isAd(url)
                val (next, act) = dev.asfakul.yumeiro.online.web.LinkChainPolicy.next(chainState, scan, url, host, android.os.SystemClock.elapsedRealtime(), adPage)
                // Device evidence: host, step and action only (never the address; shortener links carry tokens).
                if (scan?.chain == true || next.active) android.util.Log.w("YuLinkChain", "$host step=${scan?.step} ${act::class.simpleName} taps=${next.taps}")
                chainState = next
                val title = dev.asfakul.yumeiro.online.web.LinkChainPolicy.title(next, scan?.stepText)
                val hidden = if (next.defused > 0) " · ${next.defused} ad layer${if (next.defused == 1) "" else "s"} hidden" else ""
                when (act) {
                    is dev.asfakul.yumeiro.online.web.LinkChainPolicy.Action.Idle -> if (!next.active) chainBanner = null
                    is dev.asfakul.yumeiro.online.web.LinkChainPolicy.Action.Wait -> chainBanner = ChainBanner(title, act.text + hidden, ChainBanner.Kind.WORKING)
                    is dev.asfakul.yumeiro.online.web.LinkChainPolicy.Action.AskUser -> chainBanner = ChainBanner(title, act.text, ChainBanner.Kind.ASK)
                    is dev.asfakul.yumeiro.online.web.LinkChainPolicy.Action.Tap -> {
                        chainBanner = ChainBanner(title, "Tapping “${act.label}” for you$hidden", ChainBanner.Kind.WORKING)
                        dev.asfakul.yumeiro.online.web.LinkChain.tap(wv, act.x, act.y, act.vw, act.vh)
                    }
                    is dev.asfakul.yumeiro.online.web.LinkChainPolicy.Action.Open -> {
                        chainBanner = ChainBanner(title, act.text, ChainBanner.Kind.WORKING)
                        dev.asfakul.yumeiro.online.web.SafeWebView.loadUrl(wv, act.url, emptyMap())
                    }
                    is dev.asfakul.yumeiro.online.web.LinkChainPolicy.Action.Back -> {
                        chainBanner = ChainBanner(title, act.text, ChainBanner.Kind.WORKING)
                        if (wv.canGoBack()) wv.goBack()
                    }
                    is dev.asfakul.yumeiro.online.web.LinkChainPolicy.Action.Done -> {
                        // The sign-in and session cookies of every step are on disk before anything else reads them.
                        runCatching { CookieManager.getInstance().flush() }
                        val dest = dev.asfakul.yumeiro.online.web.ChainDestination.of(act.url)
                        android.util.Log.w("YuLinkChain", "reached ${act.host} kind=${dest::class.simpleName}")
                        when (dest) {
                            is dev.asfakul.yumeiro.online.web.ChainDestination.Kind.Media, is dev.asfakul.yumeiro.online.web.ChainDestination.Kind.FileHost -> {
                                val u: String
                                val label: String
                                if (dest is dev.asfakul.yumeiro.online.web.ChainDestination.Kind.Media) { u = dest.url; label = dest.label }
                                else { val fh = dest as dev.asfakul.yumeiro.online.web.ChainDestination.Kind.FileHost; u = fh.url; label = fh.label }
                                chainBanner = ChainBanner("Link reached", "$label · playing in Yumeiro", ChainBanner.Kind.DONE)
                                if (!played) {
                                    played = true
                                    val req = StreamRequest(
                                        url = u, title = pageTitle.ifBlank { dev.asfakul.yumeiro.online.web.WebDownloads.fileName(u, null) },
                                        format = dev.asfakul.yumeiro.stream.StreamSniffer.fromUrl(u)?.format ?: StreamFormat.AUTO,
                                        userAgent = wv.settings.userAgentString, referer = chainState.pages.lastOrNull(),
                                        cookie = runCatching { CookieManager.getInstance().getCookie(u) }.getOrNull(), pageUrl = act.url,
                                    )
                                    wv.onPause()
                                    onPlay(req)
                                }
                            }
                            is dev.asfakul.yumeiro.online.web.ChainDestination.Kind.HostPage -> {
                                chainBanner = ChainBanner("Link reached", "${dest.label} · starting its player", ChainBanner.Kind.DONE)
                                // Hosts like Gofile hand the file only to their own page: start its player, play what it loads.
                                chainAutoPick = true
                                repeat(6) {
                                    if (!played) wv.evaluateJavascript(PageStreamCapture.AUTOPLAY_JS, null)
                                    kotlinx.coroutines.delay(2_000L)
                                }
                            }
                            dev.asfakul.yumeiro.online.web.ChainDestination.Kind.Page -> chainBanner = ChainBanner("Link reached", act.host, ChainBanner.Kind.DONE)
                        }
                        kotlinx.coroutines.delay(5_000L)
                        chainBanner = null
                        chainState = dev.asfakul.yumeiro.online.web.LinkChainPolicy.State() // another link can be walked
                        quiet = 6
                    }
                    is dev.asfakul.yumeiro.online.web.LinkChainPolicy.Action.GiveUp -> {
                        chainBanner = ChainBanner(title, act.text, ChainBanner.Kind.STOPPED)
                        kotlinx.coroutines.delay(10_000L)
                        chainBanner = null
                    }
                }
            }
        }
    }
    /** WEB-BLOCK-1: generic ad-box rules plus the site's own picked elements. */
    fun applyBlock(view: WebView?, url: String?) {
        if (!browser || view == null || url == null) return
        if (!dev.asfakul.yumeiro.online.web.BrowserFilters.pageOn(view)) return // ADCHAIN-2: filter off for this page
        view.evaluateJavascript(dev.asfakul.yumeiro.online.web.ElementBlocker.styleJs(dev.asfakul.yumeiro.online.web.ElementBlocker.css(dev.asfakul.yumeiro.online.web.ElementBlocker.rulesFor(appContext, url))), null)
        // GROUP E3: see-through click-catchers, anti-ad-blocker walls and the scroll lock they leave.
        view.evaluateJavascript(dev.asfakul.yumeiro.online.web.PageAnnoyances.CLEAN_JS, null)
    }
    // TB-PREVIEW-1 (owner list 19): TeraBox captures that turned out to be the 30-second preview (never auto-played again),
    // and the TeraBox share this page belongs to (resolved through TeraBox's API once the user has signed in here).
    val rejected = remember(pageUrl) { HashSet<String>() }
    val teraShare = remember(pageUrl, base) { dev.asfakul.yumeiro.stream.TeraBoxApi.shareIn(base?.url, base?.pageUrl, pageUrl) }
    var teraResumed by remember(pageUrl) { mutableStateOf(false) }
    val teraSignedInAtOpen = remember(pageUrl) { teraShare != null && dev.asfakul.yumeiro.stream.TeraBoxApi.signedIn(teraCookies()) }

    /** ADCHAIN-2: this page's Filter button. Only this page changes; cookies and sign-ins stay. */
    fun togglePageFilter() {
        val wv = webViewRef ?: return
        val on = !filterOn
        pageFilter = on
        dev.asfakul.yumeiro.online.web.BrowserFilters.setPage(wv, on)
        barNote = BarNote(if (on) "Ad filter on for this page" else "Ad filter off for this page. Pop-ups and ads can show", null, null)
        wv.reload()
    }

    /** ADCHAIN-2: a pop-up the filter held back; the person can still open it. */
    fun popupHeld(target: String?) = main.post {
        barNote = BarNote("Pop-up blocked", if (target != null) "Open" else null, target?.let { t -> { webViewRef?.let { dev.asfakul.yumeiro.online.web.SafeWebView.loadUrl(it, t, emptyMap()) }; Unit } })
    }

    /** B12: Stop redirects held the page's own jump to another site; the person can still go there. */
    fun redirectHeld(target: String) = main.post {
        barNote = BarNote("Redirect stopped", "Open", { webViewRef?.let { dev.asfakul.yumeiro.online.web.SafeWebView.loadUrl(it, target, emptyMap()) }; Unit })
    }

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

    fun pageMedia(url: String, kind: String) {
        synchronized(capture) { capture.onPageMedia(url, kind) }?.let { c ->
            main.post { if (found.none { it.url == c.url }) { found.clear(); found.addAll(synchronized(capture) { capture.all() }) } }
        }
    }

    /** TB-PREVIEW-1: plays what TeraBox's API or the capture check gave, or keeps the page with the reason. */
    fun afterTeraCheck(r: dev.asfakul.yumeiro.stream.Resolution?, capturedUrl: String?) {
        when (r) {
            is dev.asfakul.yumeiro.stream.Resolution.Playable -> { webViewRef?.onPause(); onPlay(r.request) }
            else -> {
                capturedUrl?.let { rejected += it }
                played = false
                autoActive = false
                autoNote = when (r) {
                    is dev.asfakul.yumeiro.stream.Resolution.NeedsBrowser -> r.reason
                    is dev.asfakul.yumeiro.stream.Resolution.Failed -> "${r.title}. ${r.message}"
                    else -> dev.asfakul.yumeiro.stream.TeraBoxApi.SIGN_IN_REASON
                }
                // TB-LOGIN: already signed in but TeraBox still answered with the preview: say so, never loop back to sign-in.
                val toSignIn = r is dev.asfakul.yumeiro.stream.Resolution.NeedsBrowser && r.pageUrl == dev.asfakul.yumeiro.stream.StreamResolver.TERABOX_SIGN_IN
                if (toSignIn && dev.asfakul.yumeiro.stream.TeraBoxApi.signedIn(teraCookies())) {
                    autoNote = dev.asfakul.yumeiro.stream.TeraBoxApi.SIGNED_PREVIEW_REASON
                } else if (r is dev.asfakul.yumeiro.stream.Resolution.NeedsBrowser && r.pageUrl != currentUrl) {
                    // Sign-in needed: take the user to TeraBox's sign-in page in this same browser.
                    webViewRef?.loadUrl(r.pageUrl)
                }
            }
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
        if (dev.asfakul.yumeiro.stream.TeraBoxApi.isWebStream(c.url)) {
            // TB-PREVIEW-1: TeraBox's page player may hold only the 30-second preview; check it before playing.
            autoStep = "Checking the TeraBox stream"
            val share = teraShare ?: currentUrl
            scope.launch {
                val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching { dev.asfakul.yumeiro.stream.StreamResolver.checkCapture(request, dev.asfakul.yumeiro.network.NetworkClient.get(appContext), share) }.getOrNull()
                }
                afterTeraCheck(r, c.url)
            }
            return
        }
        wv?.onPause()
        onPlay(request)
    }

    androidx.compose.runtime.LaunchedEffect(chainAutoPick, found.size) {
        if (!chainAutoPick || played || found.isEmpty()) return@LaunchedEffect
        kotlinx.coroutines.delay(PageStreamCapture.AUTO_SETTLE_MS)
        PageStreamCapture.bestForAuto(found.filter { it.url !in rejected })?.let { play(it) }
    }

    /** TB-PREVIEW-1: the user just signed in to TeraBox on this page: play the share in full without asking again. */
    fun resumeTeraBox() {
        val share = teraShare ?: return
        if (teraResumed || played || teraSignedInAtOpen) return
        if (!dev.asfakul.yumeiro.stream.TeraBoxApi.signedIn(teraCookies())) return
        teraResumed = true
        played = true
        autoNote = "Signed in. Getting the full video from TeraBox"
        val req = (base ?: StreamRequest(url = share)).copy(url = share, format = StreamFormat.AUTO, pageUrl = share)
        scope.launch {
            val r = runCatching { dev.asfakul.yumeiro.stream.StreamResolver.resolve(appContext, req) }.getOrNull()
            afterTeraCheck(r, null)
        }
    }

    // DW-BOT-1: a Telegram bot's mini app (t.me/<bot>?startapp=…) opens through the signed-in account, as in Telegram.
    var miniAsk by remember(pageUrl) { mutableStateOf<dev.asfakul.yumeiro.online.telegram.td.TgMiniApp.Link?>(null) }
    fun openMini(link: dev.asfakul.yumeiro.online.telegram.td.TgMiniApp.Link) {
        if (!dev.asfakul.yumeiro.online.telegram.td.TgMiniApp.consented(appContext)) { autoActive = false; miniAsk = link; return }
        autoStep = "Opening @${link.bot} on Telegram"
        scope.launch {
            val s = runCatching { dev.asfakul.yumeiro.online.telegram.td.TgAccounts.session(appContext) }.getOrNull()
            val st = s?.let { kotlinx.coroutines.withTimeoutOrNull(20_000L) { it.state.first { x -> x != dev.asfakul.yumeiro.online.telegram.td.TgAuthState.Starting } } }
            if (s == null || st != dev.asfakul.yumeiro.online.telegram.td.TgAuthState.Ready) {
                autoActive = false
                autoNote = "Sign in to Telegram (Websites > Telegram) to open @${link.bot} here."
                return@launch
            }
            when (val r = dev.asfakul.yumeiro.online.telegram.td.TgMiniApp.open(s, link)) {
                is dev.asfakul.yumeiro.online.telegram.td.TgMiniApp.Opened.Url -> {
                    android.util.Log.w("YuMiniApp", "opened @${link.bot} mini app") // device evidence; never the address (it carries the signed launch data)
                    webViewRef?.let { dev.asfakul.yumeiro.online.web.SafeWebView.loadUrl(it, r.url, emptyMap()) }
                }
                is dev.asfakul.yumeiro.online.telegram.td.TgMiniApp.Opened.Error -> {
                    android.util.Log.w("YuMiniApp", "@${link.bot}: ${r.message}")
                    autoActive = false; autoNote = r.message
                }
            }
        }
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
            PageStreamCapture.bestForAuto(found.filter { it.url !in rejected })?.let { play(it) }
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
                    // GROUP E2: in the browser the ad filter and Reload live in the bottom bar's menu.
                    if (!browser) IconButton(
                        onClick = { togglePageFilter() },
                        modifier = Modifier.testTag("page_filter_toggle").semantics { stateDescription = if (filterOn) "On" else "Off" },
                    ) {
                        Icon(
                            if (filterOn) Icons.Default.Shield else Icons.Default.RemoveModerator,
                            if (filterOn) "Ad filter on for this page. Turn off" else "Ad filter off for this page. Turn on",
                            tint = if (filterOn) SpotifyGreen else TextSecondary,
                        )
                    }
                    if (!browser) IconButton(onClick = { webViewRef?.reload() }) { Icon(Icons.Default.Refresh, "Reload", tint = TextSecondary) }
                }
                if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, color = SpotifyGreen, modifier = Modifier.fillMaxWidth())
                if (showHint && !autoActive) PageFinderHint(autoNote ?: reason, onHide = { showHint = false })
                chainBanner?.let { b ->
                    LinkChainBanner(b, onStop = if (b.kind == ChainBanner.Kind.DONE || chainState.stopped) null else ({
                        chainState = chainState.copy(stopped = true)
                        chainBanner = null
                    }), onBack = if (b.kind == ChainBanner.Kind.STOPPED && canBackAny(webViewRef)) ({ webViewRef?.goBack(); chainBanner = null }) else null,
                        onOutside = if (b.kind == ChainBanner.Kind.STOPPED) ({
                            if (!OutsideBrowser.open(appContext, currentUrl)) barNote = BarNote("No browser on this phone can open the page", null, null)
                        }) else null)
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            // OL-1: a kept page comes back as it was (no reload).
                            keep?.webView?.let { kept ->
                                kept.settings.allowFileAccess = false
                                kept.settings.allowContentAccess = false
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
                                    @JavascriptInterface fun onMedia(src: String) = pageMedia(src, "element")
                                    @JavascriptInterface fun onManifest(url: String, kind: String) = pageMedia(url, kind)
                                    @JavascriptInterface fun onNewMedia(title: String) = switched(title)
                                    @JavascriptInterface fun onTitle(title: String) = titled(title)
                                    @JavascriptInterface fun onDrm(system: String) {
                                        val system = dev.asfakul.yumeiro.online.web.pageDrmSystemName(system)
                                        synchronized(capture) { capture.drmSystem = system }
                                        main.post { drmNote = "This page uses DRM ($system). It plays here only if the site's licence server accepts this app; protection is never bypassed." }
                                    }
                                }, "MTFinder")
                                if (browser) addJavascriptInterface(object {
                                    @JavascriptInterface fun onPicked(selector: String) = main.post {
                                        val url = currentUrl
                                        picking = false
                                        barNote = if (dev.asfakul.yumeiro.online.web.ElementBlocker.add(appContext, url, selector)) BarNote("Hidden on this site from now on", "Undo") {
                                            dev.asfakul.yumeiro.online.web.ElementBlocker.undoLast(appContext, url)
                                            webViewRef?.reload()
                                        } else BarNote("That part can't be blocked", null, null)
                                    }
                                    @JavascriptInterface fun onPickEnd() = main.post { picking = false }
                                }, "YuBlock")
                                webChromeClient = object : android.webkit.WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        progress = newProgress
                                        // Hide ad boxes as soon as the page has a body, not only when every image is in.
                                        if (newProgress >= 60) applyBlock(view, view?.url)
                                    }
                                    override fun onReceivedTitle(view: WebView?, title: String?) { pageTitle = title.orEmpty() }
                                    // ADCHAIN-2: a new window (target=_blank, window.open) opens in this page when it is the
                                    // person's own link; ad windows are held back with a note that can still open them.
                                    override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
                                        val v = view ?: return false
                                        val hit = runCatching { v.hitTestResult }.getOrNull()
                                        val link = hit?.type == WebView.HitTestResult.SRC_ANCHOR_TYPE || hit?.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                                        val target = hit?.extra?.takeIf { link }
                                        val filter = dev.asfakul.yumeiro.online.web.BrowserFilters.pageOn(v)
                                        val newTab = newTabNow.value
                                        return when (dev.asfakul.yumeiro.online.web.PopupPolicy.window(v.url, target, isUserGesture, link, filter, chainActiveNow.value, tabs = newTab != null)) {
                                            dev.asfakul.yumeiro.online.web.PopupPolicy.Decision.OPEN_HERE -> { dev.asfakul.yumeiro.online.web.SafeWebView.loadUrl(v, target!!, emptyMap()); false }
                                            dev.asfakul.yumeiro.online.web.PopupPolicy.Decision.NEW_TAB -> { main.post { newTab?.invoke(target!!) }; false }
                                            dev.asfakul.yumeiro.online.web.PopupPolicy.Decision.CAPTURE -> {
                                                // Filter off, a script window from a tap: a hidden helper learns where it goes, then this page opens it.
                                                val helper = WebView(v.context)
                                                helper.webViewClient = object : android.webkit.WebViewClient() {
                                                    override fun shouldOverrideUrlLoading(h: WebView?, r: WebResourceRequest?): Boolean {
                                                        r?.url?.toString()?.takeIf { it.startsWith("http") }?.let { dev.asfakul.yumeiro.online.web.SafeWebView.loadUrl(v, it, emptyMap()) }
                                                        main.post { runCatching { helper.destroy() } }
                                                        return true
                                                    }
                                                }
                                                val msg = resultMsg
                                                val transport = msg?.obj as? WebView.WebViewTransport
                                                if (msg != null && transport != null) { transport.webView = helper; msg.sendToTarget(); true } else { helper.destroy(); false }
                                            }
                                            dev.asfakul.yumeiro.online.web.PopupPolicy.Decision.BLOCK -> { if (isUserGesture) popupHeld(target); false }
                                        }
                                    }
                                }
                                SafeWebView.harden(this)
                                // Pop-ups and click-catcher ad overlays stay off while the user browses (unless the filter is off).
                                dev.asfakul.yumeiro.online.web.BrowserFilters.shield(this)
                                // SD-1: playlists the page's player loads are found by their content, in every frame.
                                PageStreamCapture.addMediaTap(this)
                                // DL-WEB: a file link on the page opens the shared download popup.
                                // ADCHAIN-2: a video / audio file the page hands over plays in Yumeiro's player.
                                dev.asfakul.yumeiro.online.web.WebDownloads.attach(this, { currentUrl }) { req ->
                                    main.post { if (!played) { played = true; webViewRef?.onPause(); onPlay(req.copy(title = req.title.ifBlank { pageTitle })) } }
                                    true
                                }
                                webViewClient = object : SafeWebViewClient(onGone = { onDismiss() }) {
                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        // DW-BOT-1: a hop to another bot's mini app (the DiskWala bot sends its links on) opens it here.
                                        if (request?.isForMainFrame == true) dev.asfakul.yumeiro.online.telegram.td.TgMiniApp.parse(request.url.toString())?.let { openMini(it); return true }
                                        // ADCHAIN-2: the page jumping to an ad host (pop-under, "you won" redirect) is stopped; the person can still open it.
                                        if (request?.isForMainFrame == true && dev.asfakul.yumeiro.online.web.PopupPolicy.blockJump(view?.url, request.url.toString(), dev.asfakul.yumeiro.online.web.BrowserFilters.pageOn(view))) {
                                            android.util.Log.w("YuAdFilter", "held an ad redirect to ${dev.asfakul.yumeiro.online.web.ChainDestination.hostOf(request.url.toString())}")
                                            // During a walk whose real link the site already revealed, the hijack is answered with that link.
                                            val real = chainRevealedNow.value?.takeIf { chainActiveNow.value }
                                            if (real != null && view != null) dev.asfakul.yumeiro.online.web.SafeWebView.loadUrl(view, real, emptyMap())
                                            else popupHeld(request.url.toString())
                                            return true
                                        }
                                        if (request == null || !request.isForMainFrame) {
                                            val scheme = request?.url?.scheme?.lowercase()
                                            return scheme != "http" && scheme != "https" // block intent:, market:, file: …
                                        }
                                        // B12: app links never leave Yumeiro (an intent's web fallback loads here), media plays in
                                        // Yumeiro's player, and with Stop redirects a page can't move to another site by itself.
                                        val target = request.url.toString()
                                        return when (val nav = dev.asfakul.yumeiro.online.web.PopupPolicy.navigation(view?.url, target, request.hasGesture(),
                                            request.isRedirect, dev.asfakul.yumeiro.online.web.BrowserFilters.stopRedirects.value, chainActiveNow.value)) {
                                            dev.asfakul.yumeiro.online.web.PopupPolicy.Nav.Allow -> false
                                            dev.asfakul.yumeiro.online.web.PopupPolicy.Nav.Block -> { android.util.Log.w("YuRedirect", "blocked app link ${target.substringBefore(':')}:"); true }
                                            is dev.asfakul.yumeiro.online.web.PopupPolicy.Nav.InApp -> { view?.let { dev.asfakul.yumeiro.online.web.SafeWebView.loadUrl(it, nav.url, emptyMap()) }; true }
                                            is dev.asfakul.yumeiro.online.web.PopupPolicy.Nav.Hold -> {
                                                android.util.Log.w("YuRedirect", "held a redirect to ${dev.asfakul.yumeiro.online.web.ChainDestination.hostOf(nav.url)}")
                                                redirectHeld(nav.url); true
                                            }
                                            is dev.asfakul.yumeiro.online.web.PopupPolicy.Nav.Play -> {
                                                android.util.Log.w("YuRedirect", "plays ${nav.label} link in Yumeiro's player")
                                                val referer = view?.url
                                                main.post {
                                                    if (!played) {
                                                        played = true; webViewRef?.onPause()
                                                        onPlay(dev.asfakul.yumeiro.stream.StreamRequest(url = nav.url, title = pageTitle, referer = referer))
                                                    }
                                                }
                                                true
                                            }
                                        }
                                    }
                                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                        // Built-in ad blocking: pre-rolls and pop-unders never load or get offered as "the stream".
                                        if (request != null && dev.asfakul.yumeiro.online.web.AdFilter.isAd(request.url.toString())) {
                                            val on = dev.asfakul.yumeiro.online.web.BrowserFilters.pageOn(view)
                                            dev.asfakul.yumeiro.online.web.AdFilter.note(request.url.toString(), blocked = on)
                                            if (on) return dev.asfakul.yumeiro.online.web.AdFilter.blocked()
                                        }
                                        request?.let { record(it.url.toString(), it.method ?: "GET", it.requestHeaders.orEmpty()) }
                                        // TB-LOGIN: device evidence of the sign-in calls (method and path only; never the query, body or cookies).
                                        request?.url?.takeIf { teraShare != null && it.host.orEmpty().contains("terabox") && (it.path.orEmpty().startsWith("/passport") || it.path.orEmpty().startsWith("/api/check")) }
                                            ?.let { android.util.Log.w("YuTeraBox", "${request.method} ${it.host}${it.path}") }
                                        return null // the page loads normally; we only observe
                                    }
                                    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                        history(view)
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
                                        history(view)
                                        applyBlock(view, url)
                                        // WEB-RECENT-1: the site goes to "Recently visited" on the Websites page.
                                        if (browser && url != null) dev.asfakul.yumeiro.online.web.SiteHistory.visit(appContext, url, view?.title)
                                        if (teraShare != null && url.orEmpty().contains("terabox")) {
                                            // TB-LOGIN: which cookie names the page has (names only), so a lost sign-in shows on the device log.
                                            val at = android.net.Uri.parse(url).let { "${it.host}${it.path}" }
                                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                                val jar = teraCookies()
                                                val names = jar.split(';').map { it.substringBefore('=').trim() }.filter { it.isNotEmpty() }.distinct().sorted()
                                                android.util.Log.w("YuTeraBox", "page $at signedIn=${dev.asfakul.yumeiro.stream.TeraBoxApi.signedIn(jar)} cookies=$names")
                                            }
                                        }
                                        resumeTeraBox()
                                    }
                                }
                                val headers = buildMap {
                                    base?.referer?.let { put("Referer", it) }
                                    base?.headers?.let { putAll(it) }
                                }
                                // OL-1: a kept tab that gave its page back on low memory reopens where it was.
                                val mini = if (keep == null) dev.asfakul.yumeiro.online.telegram.td.TgMiniApp.parse(pageUrl) else null
                                if (mini != null) main.post { openMini(mini) }
                                else dev.asfakul.yumeiro.online.web.SafeWebView.loadUrl(this, if (keep != null) currentUrl else pageUrl, headers)
                            }
                            page
                        },
                    )
                    miniAsk?.let { link ->
                        MiniAppConsentCard(
                            bot = link.bot,
                            onAllow = {
                                dev.asfakul.yumeiro.online.telegram.td.TgMiniApp.setConsented(appContext, true)
                                miniAsk = null; autoActive = auto; openMini(link)
                            },
                            onCancel = onDismiss,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
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
                barNote?.let { n -> BrowserNote(n) { barNote = null } }
                if (browser) BrowserBar(
                    canBack = canBack, canForward = canForward, picking = picking,
                    onBack = { webViewRef?.let { if (it.canGoBack()) it.goBack() } },
                    onForward = { webViewRef?.let { if (it.canGoForward()) it.goForward() } },
                    onReload = { webViewRef?.reload() },
                    onBlock = {
                        picking = !picking
                        webViewRef?.evaluateJavascript(dev.asfakul.yumeiro.online.web.ElementBlocker.PICK_JS, null)
                        barNote = if (picking) BarNote("Tap the part of the page to hide. Tap the block button again to stop.", null, null) else null
                    },
                    onOpenOutside = {
                        if (!OutsideBrowser.open(appContext, currentUrl)) barNote = BarNote("No browser on this phone can open the page", null, null)
                    },
                    accent = SpotifyGreen,
                    filterOn = filterOn, onFilter = { togglePageFilter() }, onManageAds = { manageAds = true },
                    tabCount = tabCount, onTabs = onTabs, onCloseTab = onCloseTab,
                )
            }
        }
        if (manageAds) {
            var changed by remember { mutableStateOf(false) }
            dev.asfakul.yumeiro.ui.screens.sites.ManageAdsDialog(onDismiss = {
                manageAds = false
                // Show again what was un-hidden: the page's style sheet is rebuilt from the saved rules.
                if (changed) webViewRef?.reload()
            }, onChanged = { changed = true })
        }
        overlay()
    }
}

private fun canBackAny(view: WebView?): Boolean = runCatching { view?.canGoBack() == true }.getOrDefault(false)

/** TB-PREVIEW-1: the cookies the web view holds for TeraBox's sign-in hosts (the `ndus` sign-in lives on one of them). */
private fun teraCookies(): String = listOf("https://dm.terabox.app", "https://www.terabox.app", "https://www.terabox.com", "https://www.1024terabox.com", "https://www.1024tera.com")
    .mapNotNull { runCatching { CookieManager.getInstance().getCookie(it) }.getOrNull() }.joinToString("; ")
