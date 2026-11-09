/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebChromeClient
import dev.asfakul.yumeiro.online.web.SafeWebView
import dev.asfakul.yumeiro.online.web.SafeWebViewClient
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.asfakul.yumeiro.online.web.BrowserCheck
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary

/**
 * Shows a site's "verify you're human" page when it can't pass on its own. The user completes the
 * check in a normal web page; the cookie it sets is then used for that site's requests.
 * Add once near the app root.
 *
 * The page loaded is the site's own page (an API / video URL is solved on the home page, see
 * [BrowserCheck.challengePage]). If the site answers with a firewall block instead of a check, the
 * page closes and explains it, instead of leaving the user on "Sorry, you have been blocked".
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserCheckHost() {
    val pending by BrowserCheck.pending.collectAsState()
    val p = pending ?: return
    var progress by remember(p) { mutableIntStateOf(0) }
    var rendererGone by remember(p) { mutableStateOf(false) }
    var visible by remember(p) { mutableStateOf(false) }
    var blocked by remember(p) { mutableIntStateOf(0) }
    var lastBlocked by remember(p) { mutableStateOf<String?>(null) }
    var webRef by remember(p) { mutableStateOf<WebView?>(null) }
    Dialog(onDismissRequest = { BrowserCheck.cancel() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("browser_check")) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VerifiedUser, null, tint = SpotifyGreen, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    val login = p.purpose == BrowserCheck.Purpose.LOGIN
                    Text(if (!p.manual) "Verify you're human" else if (login) "Sign in to ${p.host}" else "Open ${p.host}",
                        color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                    Text(
                        when {
                            !p.manual -> "${p.host} checks visitors — tap the box if one is shown. This closes on its own."
                            login -> "Sign in on the site, then tap Done. Yumeiro keeps the login for this site only."
                            else -> "Solve the captcha or sign in if the site asks, then tap Done."
                        },
                        color = TextSecondary, fontSize = 11.sp, lineHeight = 14.sp,
                    )
                }
                if (p.manual) androidx.compose.material3.TextButton(onClick = { BrowserCheck.finish() }, modifier = Modifier.testTag("browser_check_done")) {
                    Text("Done", color = SpotifyGreen, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = { BrowserCheck.cancel() }, modifier = Modifier.testTag("browser_check_cancel")) { Icon(Icons.Default.Close, "Cancel", tint = TextPrimary) }
            }
            if (p.manual && p.purpose == BrowserCheck.Purpose.LOGIN) SignupGuide(p.host)
            if (progress in 1..99) {
                LinearProgressIndicator(progress = { progress / 100f }, color = SpotifyGreen, modifier = Modifier.fillMaxWidth().height(2.dp))
            } else Spacer(Modifier.height(2.dp))
            if (rendererGone) {
                Text("The page stopped responding. Close this and try again.", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(24.dp))
                return@Column
            }
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            SafeWebView.harden(this)
                            SafeWebView.addAdShield(this)
                            // Never flash black while the first page paints.
                            setBackgroundColor(android.graphics.Color.WHITE)
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.userAgentString = BrowserCheck.userAgent()
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            val root = BrowserCheck.challengePage(p.url, method = "POST") // scheme://host/
                            var triedRoot = p.url == root
                            val login = p.purpose == BrowserCheck.Purpose.LOGIN
                            val main = this
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) { progress = newProgress; if (newProgress >= 80) visible = true }
                                // Pop-ups / new tabs: read where they go, then drop them (ads) or, for the
                                // site's own sign-in pages, open them in this page.
                                override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
                                    val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                                    val probe = WebView(ctx)
                                    probe.webViewClient = object : android.webkit.WebViewClient() {
                                        private var handled = false
                                        private fun route(target: String?) {
                                            if (handled) return
                                            handled = true
                                            val decision = dev.asfakul.yumeiro.online.web.AdShield.decidePopup(p.url, main.url, target, login)
                                            if (decision == dev.asfakul.yumeiro.online.web.AdShield.Decision.ALLOW && target != null) main.loadUrl(target)
                                            else { blocked++; if (target != null && target.startsWith("http")) lastBlocked = target }
                                            main.post { SafeWebView.dispose(probe) }
                                        }
                                        override fun shouldOverrideUrlLoading(v: WebView?, request: android.webkit.WebResourceRequest?): Boolean { route(request?.url?.toString()); return true }
                                        override fun onPageStarted(v: WebView?, url: String?, favicon: android.graphics.Bitmap?) { route(url) }
                                    }
                                    transport.webView = probe
                                    resultMsg.sendToTarget()
                                    return true
                                }
                            }
                            webViewClient = object : SafeWebViewClient(onGone = { rendererGone = true }) {
                                override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                    val r = request ?: return false
                                    if (!r.isForMainFrame) return false
                                    val target = r.url.toString()
                                    val d = dev.asfakul.yumeiro.online.web.AdShield.decideNavigation(p.url, view?.url, target, r.hasGesture(), r.isRedirect, login)
                                    if (d == dev.asfakul.yumeiro.online.web.AdShield.Decision.ALLOW) return false
                                    blocked++
                                    if (target.startsWith("http")) lastBlocked = target
                                    return true
                                }
                                override fun shouldInterceptRequest(view: WebView?, request: android.webkit.WebResourceRequest?): android.webkit.WebResourceResponse? {
                                    val url = request?.url?.toString() ?: return null
                                    if (request.isForMainFrame || !dev.asfakul.yumeiro.online.web.AdShield.blockRequest(url)) return null
                                    return dev.asfakul.yumeiro.online.web.AdFilter.blocked()
                                }
                                override fun onPageCommitVisible(view: WebView?, url: String?) { visible = true }
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    visible = true
                                    CookieManager.getInstance().flush()
                                    BrowserCheck.onPageLoaded(url ?: p.url)
                                    // "Sorry, you have been blocked" is a firewall answer, not a captcha.
                                    if (p.manual) return
                                    view?.evaluateJavascript(BrowserCheck.IS_BLOCK_JS) { r ->
                                        if (r != "true") return@evaluateJavascript
                                        if (!triedRoot) { triedRoot = true; view.loadUrl(root) }
                                        else BrowserCheck.onBlocked(p.host)
                                    }
                                }
                            }
                            webRef = this
                            loadUrl(p.url)
                        }
                    },
                    onRelease = { webRef = null; SafeWebView.dispose(it) },
                )
                if (!visible) Column(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("browser_check_loading"),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    androidx.compose.material3.CircularProgressIndicator(color = SpotifyGreen, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Opening ${p.host}…", color = TextSecondary, fontSize = 13.sp)
                    Text("Ads and pop-ups are blocked for you.", color = TextSecondary.copy(alpha = 0.7f), fontSize = 11.sp)
                }
                if (blocked > 0) AdShieldBar(
                    count = blocked, canOpen = lastBlocked != null,
                    onOpen = { lastBlocked?.let { u -> webRef?.loadUrl(u) }; lastBlocked = null },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

/** Slim bar: "Blocked 3 ads & pop-ups" with an "Open anyway" for the last page the shield held back. */
@Composable
internal fun AdShieldBar(count: Int, canOpen: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(12.dp)
            .background(androidx.compose.ui.graphics.Color(0xF0141414), androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp).testTag("ad_shield_bar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.VerifiedUser, null, tint = SpotifyGreen, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(dev.asfakul.yumeiro.online.web.AdShieldText.blocked(count), color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
        if (canOpen) androidx.compose.material3.TextButton(onClick = onOpen, modifier = Modifier.testTag("ad_shield_open")) {
            Text("Open anyway", color = SpotifyGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        } else Spacer(Modifier.height(40.dp))
    }
}

/** Steps for signing in to a site, and for making an account when the user has none. Pure text → [SignupSteps]. */
object SignupSteps {
    fun steps(host: String): List<String> = listOf(
        "Look for Sign up, Register or Create account on $host (often in the top menu or ☰).",
        "Use a spare email you can open, and a password you don't use anywhere else.",
        "Open the confirmation email if the site sends one — the link opens here or in your mail app.",
        "Sign in on this page, then tap Done. If a captcha shows, solve it first.",
        "Never pay or enter card details for a free site — close it and pick another source instead.",
    )
}

@Composable
private fun SignupGuide(host: String) {
    var open by remember(host) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(SpotifyGreen.copy(alpha = 0.08f)).padding(horizontal = 16.dp, vertical = 8.dp).testTag("signup_guide")) {
        androidx.compose.material3.TextButton(onClick = { open = !open }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text(if (open) "Hide the guide" else "No account? Quick sign-up guide", color = SpotifyGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        if (open) SignupSteps.steps(host).forEachIndexed { i, step ->
            Text("${i + 1}. $step", color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}
