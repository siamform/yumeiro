/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.settings

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.asfakul.yumeiro.online.web.SafeWebView
import dev.asfakul.yumeiro.online.web.SafeWebViewClient
import dev.asfakul.yumeiro.ui.components.AppMessages
import dev.asfakul.yumeiro.ui.components.SubPageHeader
import dev.asfakul.yumeiro.ui.components.TsukiDialog
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.OnAccent
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import dev.asfakul.yumeiro.youtube.YtAccount
import java.text.DateFormat
import java.util.Date

private val YouTubeRed = Color(0xFFFF4E45)

/** Settings → YouTube account: optional sign-in used only to get past YouTube's sign-in walls. */
@Composable
fun YouTubeAccountScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    remember(context) { YtAccount.init(context); 0 }
    val st by YtAccount.state.collectAsState()
    var signingIn by rememberSaveable { mutableStateOf(false) }
    var askSignOut by remember { mutableStateOf(false) }

    if (signingIn) {
        GoogleSignInPage(
            onDone = { ok ->
                signingIn = false
                if (ok) AppMessages.success(context, "Signed in to YouTube")
            },
        )
        return
    }

    Column(Modifier.fillMaxSize().testTag("youtube_account")) {
        SubPageHeader(title = "YouTube account", onBack = onBack, subtitle = "Optional · off unless you sign in")
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            GlassGroup {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icons.Default.AccountCircle, YouTubeRed, size = 46)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (st.signedIn) "Signed in" else "Not signed in", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text(
                                if (st.signedIn) "Since " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(st.since.takeIf { it > 0 } ?: System.currentTimeMillis()))
                                else "YouTube plays anonymously, like NewPipe",
                                color = TextTertiary, fontSize = 12.sp,
                            )
                        }
                        StatusChip(if (st.signedIn) "On" else "Off", if (st.signedIn) true else null)
                    }
                    Spacer(Modifier.height(12.dp))
                    if (st.signedIn) {
                        OutlinedButton(onClick = { askSignOut = true }, modifier = Modifier.fillMaxWidth().testTag("yt_sign_out")) {
                            Icon(Icons.AutoMirrored.Filled.Logout, null); Spacer(Modifier.width(8.dp)); Text("Sign out")
                        }
                    } else {
                        Button(
                            onClick = { signingIn = true },
                            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent),
                            modifier = Modifier.fillMaxWidth().testTag("yt_sign_in"),
                        ) { Icon(Icons.AutoMirrored.Filled.Login, null); Spacer(Modifier.width(8.dp)); Text("Sign in with Google", fontWeight = FontWeight.SemiBold) }
                    }
                }
            }

            GroupLabel("Use my account")
            GlassGroup {
                YtAccount.Mode.entries.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { YtAccount.setMode(context, m) }
                            .padding(horizontal = 8.dp, vertical = 6.dp).testTag("yt_mode_${m.name.lowercase()}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = st.mode == m, onClick = { YtAccount.setMode(context, m) }, colors = RadioButtonDefaults.colors(selectedColor = SpotifyGreen))
                        Column(Modifier.weight(1f)) {
                            Text(m.label, color = TextPrimary, fontSize = 15.sp)
                            Text(m.detail, color = TextTertiary, fontSize = 12.sp)
                        }
                    }
                }
            }

            GuideCard(SetupGuides.youtube, startExpanded = !st.signedIn)
        }
    }

    if (askSignOut) TsukiDialog(
        onDismissRequest = { askSignOut = false },
        icon = { Icon(Icons.AutoMirrored.Filled.Logout, null) },
        title = { Text("Sign out of YouTube?") },
        text = { Text("The saved sign-in is deleted from this phone and YouTube goes back to anonymous playback.") },
        confirmButton = {
            TextButton(onClick = { askSignOut = false; YtAccount.signOut(context); AppMessages.success(context, "Signed out of YouTube") }, modifier = Modifier.testTag("yt_sign_out_confirm")) {
                Text("Sign out", color = Color(0xFFFF8A80))
            }
        },
        dismissButton = { TextButton(onClick = { askSignOut = false }) { Text("Cancel") } },
    )
}

/**
 * Google's sign-in page in a hardened WebView. Once youtube.com loads with a signed-in session,
 * its cookies are captured and sealed. App-store and other non-web links (the "Get the YouTube
 * app" prompt) are sent back to youtube.com instead of failing with ERR_UNKNOWN_URL_SCHEME.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun GoogleSignInPage(onDone: (Boolean) -> Unit) {
    val context = LocalContext.current
    var progress by remember { mutableIntStateOf(0) }
    var gone by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var web by remember { mutableStateOf<WebView?>(null) }
    fun tryCapture(url: String?) {
        if (finished) return
        val host = url?.let { runCatching { Uri.parse(it).host }.getOrNull() }.orEmpty()
        if (!host.endsWith("youtube.com")) return
        CookieManager.getInstance().flush()
        val cookie = YtAccount.captureFromWebView() ?: return
        if (YtAccount.save(context, cookie)) { finished = true; onDone(true) }
    }
    BackHandler { if (web?.canGoBack() == true) web?.goBack() else onDone(false) }

    Column(Modifier.fillMaxSize().testTag("yt_login_page")) {
        SubPageHeader(title = "Sign in with Google", onBack = { onDone(false) }, subtitle = "Google's own page · Yumeiro never sees your password", handleBack = false)
        if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, color = SpotifyGreen, trackColor = GlassFill, modifier = Modifier.fillMaxWidth())
        if (gone) {
            Text("The sign-in page stopped responding. Go back and try again.", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(24.dp))
            return@Column
        }
        Surface(Modifier.fillMaxSize().padding(8.dp).border(1.dp, goldHairline(), RoundedCornerShape(18.dp)), shape = RoundedCornerShape(18.dp), color = Color.White) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        SafeWebView.harden(this)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // Google refuses sign-in from user agents marked as an embedded WebView ("; wv").
                        settings.userAgentString = runCatching { WebSettings.getDefaultUserAgent(ctx) }.getOrNull()?.replace("; wv", "")
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) { progress = newProgress }
                        }
                        webViewClient = object : SafeWebViewClient(onGone = { gone = true }) {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val scheme = request.url.scheme?.lowercase()
                                if (scheme == "http" || scheme == "https") return false
                                if (request.isForMainFrame) view.loadUrl("https://www.youtube.com/")
                                return true
                            }
                            override fun onPageFinished(view: WebView?, url: String?) { tryCapture(url) }
                            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                super.doUpdateVisitedHistory(view, url, isReload); tryCapture(url)
                            }
                        }
                        web = this
                        loadUrl(YtAccount.LOGIN_URL)
                    }
                },
                onRelease = { if (web === it) web = null; SafeWebView.dispose(it) },
            )
        }
    }
}
