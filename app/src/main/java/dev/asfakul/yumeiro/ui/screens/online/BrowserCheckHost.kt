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
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        SafeWebView.harden(this)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = BrowserCheck.userAgent()
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        val root = BrowserCheck.challengePage(p.url, method = "POST") // scheme://host/
                        var triedRoot = p.url == root
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) { progress = newProgress }
                        }
                        webViewClient = object : SafeWebViewClient(onGone = { rendererGone = true }) {
                            override fun onPageFinished(view: WebView?, url: String?) {
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
                        loadUrl(p.url)
                    }
                },
                onRelease = { SafeWebView.dispose(it) },
            )
        }
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
