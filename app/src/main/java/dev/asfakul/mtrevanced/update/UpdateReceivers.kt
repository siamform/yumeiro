/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.mtrevanced.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat

/**
 * Receives the system DownloadManager "complete" broadcast so the user is notified even if the
 * app process was killed while the update downloaded in the background.
 *
 * The broadcast payload is never trusted: the id is only used to query DownloadManager for the
 * job this app enqueued, and the APK is fully verified before installation.
 */
class DownloadCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (id < 0) return
        val pending = goAsync()
        UpdateManager.get(context).onSystemDownloadComplete(id) { pending.finish() }
    }
}

/** Receives the PackageInstaller session result (not exported; explicit PendingIntent only). */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ApkInstaller.ACTION_INSTALL_STATUS) return
        val manager = UpdateManager.get(context)
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm != null) manager.onUserActionRequired(confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> manager.onInstallSucceeded()
            PackageInstaller.STATUS_FAILURE_ABORTED -> manager.onInstallCancelled()
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                manager.onInstallFailed(describeFailure(status, msg))
            }
        }
    }

    private fun describeFailure(status: Int, msg: String?): String = when (status) {
        PackageInstaller.STATUS_FAILURE_CONFLICT ->
            "Install conflict. The installed app was signed with a different key — uninstall it once, then install this update."
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "This update isn't compatible with your device."
        PackageInstaller.STATUS_FAILURE_STORAGE -> "Not enough storage to install the update."
        PackageInstaller.STATUS_FAILURE_BLOCKED -> "Installation was blocked by the system."
        PackageInstaller.STATUS_FAILURE_INVALID -> "The update package is invalid."
        else -> msg?.takeIf { it.isNotBlank() } ?: "Installation failed."
    }
}

/**
 * Fires in the NEW version right after an update replaced the app. Posts a small
 * "Updated to vX" notification when the update was installed seamlessly in the background.
 */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val prefs = UpdatePreferences(context)
        val version = prefs.pendingUpdatedVersion ?: return
        prefs.pendingUpdatedVersion = null
        prefs.clearDownload()
        UpdateNotifier.notifyUpdated(context, version)
    }
}
