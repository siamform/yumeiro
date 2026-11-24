/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.update

import android.app.DownloadManager

/**
 * UPD-MSG-1 (owner item 21): every word the updater shows, in one place.
 *
 * The popup said "Connecting to GitHub…" while the download was only queued. Ordinary users don't need to know where
 * the file is hosted, and the old text named a step that was not happening. Each line here names the step the
 * updater is really in ([DownloadStage]); nothing says "downloading" before the first byte arrives. Pure, so a JVM
 * test reads every message (UpdateStatusTextTest checks that none of them names the hosting service).
 */
object UpdateStatusText {
    const val CHECKING = "Checking for updates…"
    const val UP_TO_DATE = "You're on the latest version"
    const val PREPARING_TITLE = "Preparing your update…"
    const val PREPARING_DETAIL = "Finding the latest version…"
    const val STARTING_TITLE = "Starting download…"
    const val STARTING_DETAIL = "Your download will begin in a moment."
    const val WAITING_NET_TITLE = "Waiting for a connection…"
    const val WAITING_NET_DETAIL = "The download continues as soon as you're back online."
    const val DOWNLOADING_TITLE = "Downloading update…"
    const val VERIFYING_TITLE = "Checking the download…"
    const val VERIFYING_DETAIL = "Making sure the update is complete and safe."
    const val READY_TITLE = "Ready to install"
    const val INSTALLING_TITLE = "Installing update…"
    const val INSTALLING_DETAIL = "Confirm in the system dialog if Android asks."
    const val COMPLETED_TITLE = "Update completed!"
    const val SOURCE_LINE = "Official Yumeiro release · checksum and signature checked"

    fun completedBody(version: String) = "Yumeiro is now v$version. Open it to see what's new."

    /** The stage of a running system download, from its DownloadManager status and reason. */
    fun stageOf(status: Int, reason: Int, downloadedBytes: Long): DownloadStage = when {
        status == DownloadManager.STATUS_PAUSED &&
            (reason == DownloadManager.PAUSED_WAITING_FOR_NETWORK || reason == DownloadManager.PAUSED_QUEUED_FOR_WIFI ||
                reason == DownloadManager.PAUSED_WAITING_TO_RETRY) -> DownloadStage.WAITING_FOR_NETWORK
        downloadedBytes <= 0L -> DownloadStage.STARTING
        else -> DownloadStage.DOWNLOADING
    }

    fun title(stage: DownloadStage): String = when (stage) {
        DownloadStage.PREPARING -> PREPARING_TITLE
        DownloadStage.STARTING -> STARTING_TITLE
        DownloadStage.WAITING_FOR_NETWORK -> WAITING_NET_TITLE
        DownloadStage.DOWNLOADING -> DOWNLOADING_TITLE
        DownloadStage.VERIFYING -> VERIFYING_TITLE
    }

    /** The second line under the ring; null for DOWNLOADING (the dialog shows sizes, speed and time left). */
    fun detail(stage: DownloadStage): String? = when (stage) {
        DownloadStage.PREPARING -> PREPARING_DETAIL
        DownloadStage.STARTING -> STARTING_DETAIL
        DownloadStage.WAITING_FOR_NETWORK -> WAITING_NET_DETAIL
        DownloadStage.DOWNLOADING -> null
        DownloadStage.VERIFYING -> VERIFYING_DETAIL
    }

    /** True while no byte is moving, so the ring spins instead of showing a fake percentage. */
    fun indeterminate(stage: DownloadStage): Boolean = stage != DownloadStage.DOWNLOADING

    /** One line for the Settings › App updates card. */
    fun settingsLine(state: UpdateState): String? = when (state) {
        is UpdateState.Checking -> CHECKING
        is UpdateState.UpToDate -> UP_TO_DATE
        is UpdateState.Available -> "v${state.release.versionName} is available · tap to view"
        is UpdateState.Downloading ->
            if (state.stage == DownloadStage.DOWNLOADING) "Downloading update · ${(state.progress * 100).toInt()}%"
            else title(state.stage)
        is UpdateState.ReadyToInstall -> "v${state.release.versionName} is ready · tap to install"
        is UpdateState.Installing -> INSTALLING_TITLE
        is UpdateState.Failed -> state.message
        else -> null
    }

    /** A failed system download, in plain words (never a bare error number). */
    fun downloadFailed(reason: Int): String = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "The update couldn't download: there isn't enough free storage."
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "The update couldn't download: storage isn't available right now."
        DownloadManager.ERROR_CANNOT_RESUME -> "The download was interrupted. Tap Retry to start it again."
        DownloadManager.ERROR_HTTP_DATA_ERROR, DownloadManager.ERROR_UNHANDLED_HTTP_CODE, DownloadManager.ERROR_TOO_MANY_REDIRECTS ->
            "The update server didn't answer properly. Please try again in a few minutes."
        DownloadManager.ERROR_FILE_ERROR -> "The update file couldn't be saved. Free some space and try again."
        else -> "The download didn't finish. Check your connection and tap Retry."
    }
}
