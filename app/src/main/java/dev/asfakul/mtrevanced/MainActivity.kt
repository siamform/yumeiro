/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.mtrevanced

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.asfakul.mtrevanced.player.PipController
import dev.asfakul.mtrevanced.stream.IncomingMedia
import dev.asfakul.mtrevanced.stream.StreamRepository
import dev.asfakul.mtrevanced.ui.screens.MTPlayerApp
import dev.asfakul.mtrevanced.ui.theme.MTPlayerTheme
import dev.asfakul.mtrevanced.ui.theme.rememberMtPalette
import dev.asfakul.mtrevanced.ui.viewmodel.MainViewModel
import dev.asfakul.mtrevanced.update.UpdateManager
import dev.asfakul.mtrevanced.update.UpdateNotifier

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
            dev.asfakul.mtrevanced.ui.components.AppMessages.show(this, text)
        }

        // Background update check on app open (once per process; restores running downloads).
        // Re-signed / repackaged copies never auto-update (official APKs could not install over them).
        val unofficial = dev.asfakul.mtrevanced.security.IntegrityGuard.isUnofficial(this)
        UpdateManager.get(this).onAppStart(autoCheckAllowedForBuild = BuildConfig.AUTO_UPDATE_CHECK && !unofficial)
        handleIntent(intent)

        setContent {
            val palette = rememberMtPalette()
            LaunchedEffect(palette.isDark) {
                val style = if (palette.isDark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            MTPlayerTheme(palette = palette) {
                val viewModel: MainViewModel = viewModel()
                MTPlayerApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
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
        if (intent?.action == dev.asfakul.mtrevanced.notify.AppNotifier.ACTION_OPEN_INBOX) {
            // Opened from a Yumeiro notification: show the inbox and mark that item as read.
            intent.getStringExtra(dev.asfakul.mtrevanced.notify.AppNotifier.EXTRA_ITEM)?.let {
                dev.asfakul.mtrevanced.notify.Inbox.markRead(this, it)
            }
            dev.asfakul.mtrevanced.ui.screens.extras.AppPages.open(dev.asfakul.mtrevanced.ui.screens.extras.AppPages.Page.INBOX)
            return
        }
        intent?.data?.let { uri ->
            if (dev.asfakul.mtrevanced.online.anime.AnimeSync.handleRedirect(this, uri) { msg -> runOnUiThread { dev.asfakul.mtrevanced.ui.components.AppMessages.show(this, msg) } }) return
        }
        IncomingMedia.handle(this, intent)
    }
}
