/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.update

import dev.asfakul.yumeiro.l10n.tr
import dev.asfakul.yumeiro.R
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
    val CHECKING: String get() = tr(R.string.checking_for_updates, "Checking for updates…")
    val UP_TO_DATE: String get() = tr(R.string.youre_on_the_latest_version, "You're on the latest version")
    val PREPARING_TITLE: String get() = tr(R.string.preparing_your_update, "Preparing your update…")
    val PREPARING_DETAIL: String get() = tr(R.string.finding_the_latest_version, "Finding the latest version…")
    val STARTING_TITLE: String get() = tr(R.string.starting_download, "Starting download…")
    val STARTING_DETAIL: String get() = tr(R.string.your_download_will_begin_in_a, "Your download will begin in a moment.")
    val WAITING_NET_TITLE: String get() = tr(R.string.waiting_for_a_connection, "Waiting for a connection…")
    val WAITING_NET_DETAIL: String get() = tr(R.string.the_download_continues_as_soon_as, "The download continues as soon as you're back online.")
    val DOWNLOADING_TITLE: String get() = tr(R.string.downloading_update, "Downloading update…")
    val VERIFYING_TITLE: String get() = tr(R.string.checking_the_download, "Checking the download…")
    val VERIFYING_DETAIL: String get() = tr(R.string.making_sure_the_update_is_complete, "Making sure the update is complete and safe.")
    val READY_TITLE: String get() = tr(R.string.ready_to_install, "Ready to install")
    val INSTALLING_TITLE: String get() = tr(R.string.installing_update, "Installing update…")
    val INSTALLING_DETAIL: String get() = tr(R.string.confirm_in_the_system_dialog_if, "Confirm in the system dialog if Android asks.")
    val COMPLETED_TITLE: String get() = tr(R.string.update_completed, "Update completed!")
    val SOURCE_LINE: String get() = tr(R.string.official_yumeiro_release_checksum_and_signature, "Official Yumeiro release · checksum and signature checked")

    fun completedBody(version: String) = tr(R.string.yumeiro_is_now_v_open_it, "Yumeiro is now v%1\$s. Open it to see what's new.", version)

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
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> tr(R.string.the_update_couldnt_download_there_isnt, "The update couldn't download: there isn't enough free storage.")
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> tr(R.string.the_update_couldnt_download_storage_isnt, "The update couldn't download: storage isn't available right now.")
        DownloadManager.ERROR_CANNOT_RESUME -> tr(R.string.the_download_was_interrupted_tap_retry, "The download was interrupted. Tap Retry to start it again.")
        DownloadManager.ERROR_HTTP_DATA_ERROR, DownloadManager.ERROR_UNHANDLED_HTTP_CODE, DownloadManager.ERROR_TOO_MANY_REDIRECTS ->
            tr(R.string.the_update_server_didnt_answer_properly, "The update server didn't answer properly. Please try again in a few minutes.")
        DownloadManager.ERROR_FILE_ERROR -> tr(R.string.the_update_file_couldnt_be_saved, "The update file couldn't be saved. Free some space and try again.")
        else -> tr(R.string.the_download_didnt_finish_check_your, "The download didn't finish. Check your connection and tap Retry.")
    }
}
