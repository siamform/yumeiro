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
                    Text("Verify you're human", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("${p.host} checks visitors — tap the box if one is shown. This closes on its own.", color = TextSecondary, fontSize = 11.sp, lineHeight = 14.sp)
                }
                IconButton(onClick = { BrowserCheck.cancel() }, modifier = Modifier.testTag("browser_check_cancel")) { Icon(Icons.Default.Close, "Cancel", tint = TextPrimary) }
            }
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
