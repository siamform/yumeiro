/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import androidx.compose.ui.graphics.toArgb
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
    /**
     * ADR-014: when extension code asks for the window manager (it is about to build a dialog or a
     * pop-up), the window it opens next is checked against the extension pop-up policy. The
     * manager itself is never wrapped (the framework casts it). The stack is only read while an
     * extension is loaded.
     */
    override fun getSystemService(name: String): Any? {
        val service = super.getSystemService(name)
        if (name == WINDOW_SERVICE && service is android.view.WindowManager) {
            dev.asfakul.yumeiro.online.ExtensionWindowWatch.onWindowServiceRequested(service)
        }
        return service
    }

    /** F12: compact text app-wide (every window this activity opens), see [dev.asfakul.yumeiro.ui.theme.UiScale]. */
    override fun attachBaseContext(newBase: android.content.Context) {
        // F2: the picked language first, then F12's compact text scale on top of it.
        super.attachBaseContext(dev.asfakul.yumeiro.ui.theme.UiScale.wrap(dev.asfakul.yumeiro.l10n.AppLanguage.wrap(newBase)))
    }

    override fun onDestroy() {
        dev.asfakul.yumeiro.online.ExtensionPopups.clear()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // F19: Android's own per-app language page changed the language while this process ran: restart, so no
        // text made earlier stays in the old language. Otherwise text outside screens uses this activity's language.
        dev.asfakul.yumeiro.l10n.AppLanguage.resetCache()
        if (dev.asfakul.yumeiro.l10n.Tr.staleFor(dev.asfakul.yumeiro.l10n.AppLanguage.saved(this))) {
            dev.asfakul.yumeiro.l10n.AppLanguage.restartInto(this)
            return
        }
        dev.asfakul.yumeiro.l10n.Tr.use(this)
        if (dev.asfakul.yumeiro.l10n.AppLanguage.takeReopenSettings(this)) dev.asfakul.yumeiro.ui.screens.extras.AppPages.runAction("settings")
        // F-07: one result launcher for hand-offs to VLC / MX / mpv / Just (watched position comes back here).
        dev.asfakul.yumeiro.player.ExternalPlayerHost.launcher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) {
            dev.asfakul.yumeiro.player.ExternalPlayerHost.onResult(it.data)
        }
        dev.asfakul.yumeiro.online.ExtensionPopups.init(this)
        enableEdgeToEdge()
        // SPLASH-2: the window is the saved theme's page colour from the first pixel (it was always black).
        dev.asfakul.yumeiro.ui.theme.LaunchLook.beforeFirstFrame(this)
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

        // PR-1: decided before the first frame, so nothing of the app is drawn (or reachable with the
        // remote) behind "Who's watching?" (Plex fixed exactly that bypass on Android TV).
        dev.asfakul.yumeiro.ui.screens.profiles.ProfileSession.decide(this)
        // A11Y-EXT-1: an extension's invisible 1 px WebView stays out of screen readers and remote focus.
        dev.asfakul.yumeiro.online.ForeignViewGuard.attach(this)
        setContent {
            val palette = rememberMtPalette()
            LaunchedEffect(palette.isDark, palette.background) {
                // SPLASH-2: a theme change also changes the window colour and the next cold start's splash.
                dev.asfakul.yumeiro.ui.theme.LaunchLook.apply(
                    this@MainActivity,
                    dev.asfakul.yumeiro.ui.theme.LaunchLook.lookFor(palette.isDark, palette.background == androidx.compose.ui.graphics.Color.Black),
                    palette.background.toArgb(),
                )
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
                    val profileChosen by dev.asfakul.yumeiro.ui.screens.profiles.ProfileSession.chosen.collectAsState()
                    if (profileChosen) MTPlayerApp(viewModel = viewModel)
                    else dev.asfakul.yumeiro.ui.screens.profiles.ProfileGate(this@MainActivity) // PR-1
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
                link.startsWith("https://") -> if (!dev.asfakul.yumeiro.ui.screens.extras.Links.open(this, link, tell = false)) pages.open(dev.asfakul.yumeiro.ui.screens.extras.AppPages.Page.INBOX)
                else -> pages.runAction(link)
            }
            return
        }
        dev.asfakul.yumeiro.widget.ContinueWidget.itemFrom(intent)?.let { dev.asfakul.yumeiro.widget.TitleNav.open(it); return }
        intent?.data?.let { uri ->
            if (dev.asfakul.yumeiro.online.anime.AnimeSync.handleRedirect(this, uri) { msg -> runOnUiThread { dev.asfakul.yumeiro.ui.components.AppMessages.show(this, msg) } }) return
        }
        // Extension, repository and add-on links / files (any ecosystem) go to "Add anything".
        val incoming = intent?.data ?: intent?.takeIf { it.action == Intent.ACTION_SEND }?.let {
            @Suppress("DEPRECATION") (it.getParcelableExtra(Intent.EXTRA_STREAM) as? android.net.Uri)
                ?: it.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf { t -> !t.contains(' ') }?.let(android.net.Uri::parse)
        }
        val incomingName = incoming?.takeIf { it.scheme == "content" }?.let { u ->
            runCatching { contentResolver.query(u, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } }.getOrNull()
        }
        if (incoming != null && dev.asfakul.yumeiro.ui.screens.sources.UniversalAddInbox.offer(incoming, incomingName)) return
        IncomingMedia.handle(this, intent)
    }
}
