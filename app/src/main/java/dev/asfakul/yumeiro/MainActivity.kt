/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.asfakul.yumeiro.player.PipController
import dev.asfakul.yumeiro.stream.IncomingMedia
import dev.asfakul.yumeiro.stream.StreamRepository
import dev.asfakul.yumeiro.ui.screens.MTPlayerApp
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import dev.asfakul.yumeiro.ui.theme.rememberMtPalette
import dev.asfakul.yumeiro.ui.viewmodel.MainViewModel
import dev.asfakul.yumeiro.update.UpdateManager
import dev.asfakul.yumeiro.update.UpdateNotifier

/**
 * Extends CloudStream's compatible MainActivity (an AppCompatActivity) so extensions can show their
 * own dialogs and settings screens from the current activity.
 */
class MainActivity : com.lagradost.cloudstream3.MainActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        StreamRepository.init(this)
        // Messages from extensions use the app's own message bar.
        com.lagradost.cloudstream3.CommonActivity.toastHandler = { text, _ ->
            dev.asfakul.yumeiro.ui.components.AppMessages.show(this, text)
        }

        // Background update check on app open (once per process; restores running downloads).
        // Re-signed / repackaged copies never auto-update (official APKs could not install over them).
        val unofficial = dev.asfakul.yumeiro.security.IntegrityGuard.isUnofficial(this)
        UpdateManager.get(this).onAppStart(autoCheckAllowedForBuild = BuildConfig.AUTO_UPDATE_CHECK && !unofficial)
        handleIntent(intent)
        dev.asfakul.yumeiro.ui.tv.TvMode.init(this)

        setContent {
            val palette = rememberMtPalette()
            LaunchedEffect(palette.isDark) {
                val style = if (palette.isDark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            // TV layout: what the device reports, unless Settings → Navigation bar → Layout overrides it.
            val tvDetected = androidx.compose.runtime.remember { dev.asfakul.yumeiro.ui.tv.TvMode.detect(this@MainActivity) }
            val tvPref by dev.asfakul.yumeiro.ui.tv.TvMode.pref.collectAsState()
            val tv = dev.asfakul.yumeiro.ui.tv.TvMode.resolve(tvPref, tvDetected)
            androidx.compose.runtime.CompositionLocalProvider(dev.asfakul.yumeiro.ui.tv.LocalTvMode provides tv) {
                MTPlayerTheme(palette = palette) {
                    val viewModel: MainViewModel = viewModel()
                    MTPlayerApp(viewModel = viewModel)
                    dev.asfakul.yumeiro.ui.screens.extras.AppLockGate(this@MainActivity)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dev.asfakul.yumeiro.security.Privacy.onStart(this)
    }

    override fun onStop() {
        super.onStop()
        dev.asfakul.yumeiro.security.Privacy.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    @Deprecated("Only for the Android 7–8 app-lock screen (no BiometricPrompt there).")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == dev.asfakul.yumeiro.security.Privacy.REQ_CONFIRM_CREDENTIAL) {
            dev.asfakul.yumeiro.security.Privacy.onCredentialResult(resultCode == RESULT_OK)
        }
    }

    /** Android 9–11: enter picture-in-picture when the user presses Home during video playback. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        PipController.onUserLeaveHint?.invoke()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == UpdateNotifier.ACTION_OPEN_UPDATE) {
            // Opened from the "update ready" notification: bring the update dialog back.
            UpdateManager.get(this).showDialog()
            return
        }
        if (intent?.action == dev.asfakul.yumeiro.online.download.DownloadWorker.ACTION_OPEN_DOWNLOADS) {
            dev.asfakul.yumeiro.ui.screens.downloads.DownloadsNav.open()
            return
        }
        if (intent?.action == dev.asfakul.yumeiro.notify.AppNotifier.ACTION_OPEN_INBOX) {
            // Opened from a Yumeiro notification: show the inbox and mark that item as read.
            intent.getStringExtra(dev.asfakul.yumeiro.notify.AppNotifier.EXTRA_ITEM)?.let {
                dev.asfakul.yumeiro.notify.Inbox.markRead(this, it)
            }
            // A notice with a deep link opens its target straight away (validated again here: the
            // intent could come from an old notification). Anything else lands in the inbox.
            val link = dev.asfakul.yumeiro.notify.FcmPlan.safeLink(intent.getStringExtra(dev.asfakul.yumeiro.notify.AppNotifier.EXTRA_LINK))
            val pages = dev.asfakul.yumeiro.ui.screens.extras.AppPages
            when {
                link == null || link == "inbox" -> pages.open(dev.asfakul.yumeiro.ui.screens.extras.AppPages.Page.INBOX)
                link.startsWith("https://") -> if (!dev.asfakul.yumeiro.ui.screens.extras.Links.open(this, link)) pages.open(dev.asfakul.yumeiro.ui.screens.extras.AppPages.Page.INBOX)
                else -> pages.runAction(link)
            }
            return
        }
        dev.asfakul.yumeiro.widget.ContinueWidget.itemFrom(intent)?.let { dev.asfakul.yumeiro.widget.TitleNav.open(it); return }
        intent?.data?.let { uri ->
            if (dev.asfakul.yumeiro.online.anime.AnimeSync.handleRedirect(this, uri) { msg -> runOnUiThread { dev.asfakul.yumeiro.ui.components.AppMessages.show(this, msg) } }) return
        }
        IncomingMedia.handle(this, intent)
    }
}
