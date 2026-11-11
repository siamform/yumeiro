/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.update

import dev.asfakul.yumeiro.security.TrustedSources
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Process-wide coordinator for checking, downloading and installing updates from GitHub Releases.
 *
 * Downloads run in the system [DownloadManager], so they continue when the dialog is dismissed
 * ("Download in background"), survive process death, and show native progress in the shade.
 */
class UpdateManager private constructor(context: Context) {

    private val app = context.applicationContext
    private val prefs = UpdatePreferences(app)
    private val client = GitHubReleaseClient()
    private val downloadManager = app.getSystemService(DownloadManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Whether the update popup is currently shown (independent from the download state). */
    private val _dialogVisible = MutableStateFlow(false)
    val dialogVisible: StateFlow<Boolean> = _dialogVisible.asStateFlow()

    private val _autoCheck = MutableStateFlow(prefs.autoCheckEnabled)
    val autoCheckEnabled: StateFlow<Boolean> = _autoCheck.asStateFlow()

    private val _lastChecked = MutableStateFlow(prefs.lastCheckedAt)
    val lastCheckedAt: StateFlow<Long> = _lastChecked.asStateFlow()

    val usesAuthenticatedChannel: Boolean get() = UpdateConfig.hasToken
    val currentVersionName: String get() = UpdateConfig.currentVersionName
    val releasesPageUrl: String get() = UpdateConfig.releasesPage

    private val _seamless = MutableStateFlow(prefs.seamlessUpdates)
    val seamlessUpdates: StateFlow<Boolean> = _seamless.asStateFlow()

    /** True while the current download/install was started silently (no popup, no prompts). */
    private var silentFlow = false
    private var idleInstallJob: Job? = null

    private var checkJob: Job? = null
    private var pollJob: Job? = null
    private var startupDone = false

    // ------------------------------------------------------------------------------- lifecycle

    /** Called once per process from MainActivity: restores downloads and runs the auto-check. */
    fun onAppStart(autoCheckAllowedForBuild: Boolean) {
        if (startupDone) return
        startupDone = true
        cleanupStaleApks()
        if (restoreDownload()) return
        if (autoCheckAllowedForBuild && prefs.autoCheckEnabled) checkForUpdates(userInitiated = false)
    }

    fun setSeamlessUpdates(enabled: Boolean) {
        prefs.seamlessUpdates = enabled
        _seamless.value = enabled
    }

    /** Seamless mode is possible only when "Install unknown apps" is already allowed. */
    private fun canGoSilent(): Boolean = false // the user always confirms updates

    /**
     * Called by [AppVisibility] whenever the app moves to the background. A silently downloaded
     * update is installed only then (and only when nothing is playing), so the user is never
     * kicked out of the app — the next launch simply opens the new version.
     */
    internal fun onAppBackgrounded() {
        val s = _state.value as? UpdateState.ReadyToInstall ?: return
        if (!silentFlow) return
        idleInstallJob?.cancel()
        idleInstallJob = scope.launch {
            // Short grace period (rotation, share sheets, quick app switches), then wait for silence.
            delay(IDLE_GRACE_MS)
            while (isActive && (AppVisibility.isForeground || isMediaPlaying())) delay(IDLE_RECHECK_MS)
            if (!isActive || _state.value != s) return@launch
            prefs.pendingUpdatedVersion = s.release.versionName
            install()
        }
    }

    internal fun onAppForegrounded() { idleInstallJob?.cancel() }

    private fun isMediaPlaying(): Boolean = runCatching {
        app.getSystemService(android.media.AudioManager::class.java)?.isMusicActive == true
    }.getOrDefault(false)

    internal val isSilentFlow: Boolean get() = silentFlow

    fun setAutoCheckEnabled(enabled: Boolean) {
        prefs.autoCheckEnabled = enabled
        _autoCheck.value = enabled
    }

    // ---------------------------------------------------------------------------------- check

    fun checkForUpdates(userInitiated: Boolean) {
        val current = _state.value
        if (current is UpdateState.Downloading || current is UpdateState.Installing) {
            if (userInitiated) _dialogVisible.value = true
            return
        }
        if (current is UpdateState.ReadyToInstall && userInitiated) {
            _dialogVisible.value = true
            return
        }
        if (checkJob?.isActive == true) return
        checkJob = scope.launch {
            _state.value = UpdateState.Checking
            try {
                val release = client.fetchLatestRelease()
                markChecked()
                if (isNewer(release)) {
                    _state.value = UpdateState.Available(release)
                    runCatching {
                        dev.asfakul.yumeiro.notify.Inbox.add(app, dev.asfakul.yumeiro.notify.Inbox.Item(
                            id = "update-${release.versionName}", kind = dev.asfakul.yumeiro.notify.Inbox.Kind.UPDATE,
                            title = "Yumeiro v${release.versionName} is available",
                            body = "Tap to see what's new and update. Nothing installs without your OK.",
                            at = System.currentTimeMillis(), link = "update",
                        ))
                    }
                    if (userInitiated) prefs.clearSnooze()
                    if (userInitiated || !prefs.isSnoozed(release.versionName)) _dialogVisible.value = true
                } else {
                    _state.value = UpdateState.UpToDate(System.currentTimeMillis())
                }
            } catch (t: Throwable) {
                // Silent on automatic checks: never bother the user with a popup for a failed check.
                _state.value = if (userInitiated) {
                    UpdateState.Failed(t.message ?: "Update check failed.")
                } else UpdateState.Idle
            }
        }
    }

    private fun isNewer(release: ReleaseInfo): Boolean {
        val code = release.versionCode
        if (code != null && code > 0) return code > UpdateConfig.currentVersionCode
        return SemVer.isNewer(release.versionName, UpdateConfig.currentVersionName)
    }

    private fun markChecked() {
        val now = System.currentTimeMillis()
        prefs.lastCheckedAt = now
        _lastChecked.value = now
    }

    // ------------------------------------------------------------------------------- dialog

    fun showDialog() { _dialogVisible.value = true }

    /** "Remind me later": hide the popup and snooze this version for 24 hours. */
    fun remindLater() {
        (_state.value as? UpdateState.Available)?.let {
            prefs.snooze(it.release.versionName, System.currentTimeMillis() + SNOOZE_MS)
        }
        _dialogVisible.value = false
    }

    /** Hide the popup while the download keeps running in the system DownloadManager. */
    fun continueInBackground() { _dialogVisible.value = false }

    fun dismissDialog() {
        _dialogVisible.value = false
        val s = _state.value
        if (s is UpdateState.Failed || s is UpdateState.UpToDate) _state.value = UpdateState.Idle
    }

    // ----------------------------------------------------------------------------- download

    fun startDownload() = startDownload(silent = false)

    private fun startDownload(silent: Boolean) {
        silentFlow = silent
        val release = when (val s = _state.value) {
            is UpdateState.Available -> s.release
            is UpdateState.Failed -> s.release ?: return
            else -> return
        }
        scope.launch {
            _state.value = UpdateState.Downloading(release, 0, release.asset.sizeBytes, 0, queued = true)
            try {
                val dir = updatesDir() ?: throw IllegalStateException("Storage is not available.")
                dir.listFiles()?.forEach { it.delete() }
                val target = File(dir, safeFileName(release))
                val resolved = client.resolveDownload(release)
                if (!TrustedSources.isGitHubDownloadHost(resolved.url)) {
                    throw IllegalStateException("Update rejected: the download link is not on GitHub.")
                }
                val request = DownloadManager.Request(Uri.parse(resolved.url))
                    .setTitle("Yumeiro ${release.tagName}")
                    .setDescription("Downloading update…")
                    .setMimeType(APK_MIME)
                    // Always visible: an update download is never hidden from the user (no
                    // DOWNLOAD_WITHOUT_NOTIFICATION permission; see docs/security/AUDIT.md).
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true)
                    .setDestinationInExternalFilesDir(app, Environment.DIRECTORY_DOWNLOADS, "updates/${target.name}")
                if (resolved.needsAuthHeader) {
                    request.addRequestHeader("Authorization", "Bearer ${UpdateConfig.token}")
                    request.addRequestHeader("Accept", "application/octet-stream")
                }
                val id = downloadManager.enqueue(request)
                prefs.downloadId = id
                prefs.downloadRelease = ReleaseCodec.encode(release)
                prefs.downloadSilent = silent
                UpdateNotifier.cancel(app)
                pollDownload(id, release)
            } catch (t: Throwable) {
                prefs.clearDownload()
                _state.value = UpdateState.Failed(t.message ?: "Couldn't start the download.", release)
            }
        }
    }

    fun cancelDownload() {
        pollJob?.cancel()
        val id = prefs.downloadId
        if (id >= 0) runCatching { downloadManager.remove(id) }
        val release = (state.value as? UpdateState.Downloading)?.release
        prefs.clearDownload()
        _state.value = release?.let { UpdateState.Available(it) } ?: UpdateState.Idle
    }

    private fun pollDownload(id: Long, release: ReleaseInfo) {
        pollJob?.cancel()
        pollJob = scope.launch {
            var lastBytes = 0L
            var lastTime = System.currentTimeMillis()
            var speed = 0L
            while (isActive) {
                val snap = query(id)
                if (snap == null) {
                    // Removed externally (e.g. user cancelled from the notification shade).
                    prefs.clearDownload()
                    _state.value = UpdateState.Available(release)
                    return@launch
                }
                when (snap.status) {
                    DownloadManager.STATUS_SUCCESSFUL -> { onDownloadFinished(release, snap); return@launch }
                    DownloadManager.STATUS_FAILED -> {
                        prefs.clearDownload()
                        val msg = "Download failed (${reasonText(snap.reason)})."
                        if (silentFlow) {
                            // Silent attempt failed: stay quiet, the next check retries.
                            _state.value = UpdateState.Available(release)
                        } else {
                            _state.value = UpdateState.Failed(msg, release)
                            if (!_dialogVisible.value) UpdateNotifier.notifyFailed(app, msg)
                        }
                        return@launch
                    }
                    else -> {
                        val now = System.currentTimeMillis()
                        val dt = now - lastTime
                        if (dt >= 1000) {
                            speed = ((snap.downloaded - lastBytes) * 1000 / dt).coerceAtLeast(0)
                            lastBytes = snap.downloaded
                            lastTime = now
                        }
                        val total = if (snap.total > 0) snap.total else release.asset.sizeBytes
                        _state.value = UpdateState.Downloading(
                            release, snap.downloaded, total, speed,
                            queued = snap.status == DownloadManager.STATUS_PENDING ||
                                snap.status == DownloadManager.STATUS_PAUSED,
                        )
                    }
                }
                delay(POLL_MS)
            }
        }
    }

    private suspend fun onDownloadFinished(release: ReleaseInfo, snap: Snapshot) {
        val file = snap.localUri?.let { Uri.parse(it).path }?.let(::File)
            ?: File(updatesDir(), safeFileName(release))
        try {
            ApkInstaller.verify(app, file, release.asset.sha256)
            _state.value = UpdateState.ReadyToInstall(release, file.absolutePath)
            if (silentFlow && ApkInstaller.canInstallPackages(app)) {
                // No popup and no notification: install as soon as the app is idle.
                if (!AppVisibility.isForeground) onAppBackgrounded()
            } else if (canGoSilent()) {
                // The user already said "Update": no second "Install now" step, go straight on.
                install()
            } else if (!_dialogVisible.value) {
                UpdateNotifier.notifyReady(app, release.versionName)
            }
        } catch (t: Throwable) {
            file.delete()
            prefs.clearDownload()
            val msg = t.message ?: "The downloaded update could not be verified."
            if (silentFlow) {
                _state.value = UpdateState.Idle
            } else {
                _state.value = UpdateState.Failed(msg, release)
                if (!_dialogVisible.value) UpdateNotifier.notifyFailed(app, msg)
            }
        }
    }

    /** Entry point from [DownloadCompleteReceiver] (may run in a fresh process). */
    internal fun onSystemDownloadComplete(id: Long, done: () -> Unit) {
        if (id != prefs.downloadId) { done(); return }
        val release = ReleaseCodec.decode(prefs.downloadRelease)
        if (release == null) { done(); return }
        scope.launch {
            try {
                if (pollJob?.isActive == true) return@launch // in-process poller will handle it
                val snap = query(id) ?: return@launch
                if (snap.status == DownloadManager.STATUS_SUCCESSFUL) onDownloadFinished(release, snap)
            } finally {
                done()
            }
        }
    }

    // ------------------------------------------------------------------------------ install

    /** Returns false when the user must first allow "Install unknown apps" for Yumeiro. */
    fun needsInstallPermission(): Boolean = !ApkInstaller.canInstallPackages(app)

    fun installPermissionIntent() = ApkInstaller.unknownSourcesSettingsIntent(app)

    fun install() {
        val s = _state.value as? UpdateState.ReadyToInstall ?: return
        scope.launch {
            _state.value = UpdateState.Installing(s.release)
            try {
                val file = File(s.apkPath)
                ApkInstaller.verify(app, file, s.release.asset.sha256)
                ApkInstaller.install(app, file)
                UpdateNotifier.cancel(app)
            } catch (t: Throwable) {
                _state.value = UpdateState.Failed(t.message ?: "Installation failed.", s.release)
            }
        }
    }

    /**
     * The system still wants a confirmation (Android 9–11, or a device that ignores
     * USER_ACTION_NOT_REQUIRED). In the foreground we show it right away; in the background we
     * post a quiet notification instead of throwing a dialog over whatever the user is doing.
     */
    internal fun onUserActionRequired(confirm: Intent) {
        if (AppVisibility.isForeground || !silentFlow) {
            confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { app.startActivity(confirm); return } catch (_: Exception) { }
        }
        val s = _state.value
        if (s is UpdateState.Installing) restoreDownload()
        prefs.pendingUpdatedVersion = null
        (s as? UpdateState.Installing)?.release?.let { UpdateNotifier.notifyReady(app, it.versionName) }
    }

    internal fun onInstallSucceeded() {
        prefs.clearDownload()
        _dialogVisible.value = false
    }

    internal fun onInstallCancelled() {
        val s = _state.value
        if (s is UpdateState.Installing) restoreDownload()
    }

    internal fun onInstallFailed(message: String) {
        prefs.pendingUpdatedVersion = null
        val release = (state.value as? UpdateState.Installing)?.release
        if (silentFlow && !AppVisibility.isForeground) {
            // Background attempt: don't pop a dialog; fall back to the normal flow next launch.
            silentFlow = false
            prefs.clearDownload()
            _state.value = release?.let { UpdateState.Available(it) } ?: UpdateState.Idle
            return
        }
        _state.value = UpdateState.Failed(message, release)
        _dialogVisible.value = true
    }

    // ------------------------------------------------------------------------------ helpers

    /** Restores an in-flight or finished download after process restart. Returns true if found. */
    private fun restoreDownload(): Boolean {
        val id = prefs.downloadId
        val release = ReleaseCodec.decode(prefs.downloadRelease)
        if (id < 0 || release == null) return false
        silentFlow = false // silent installs removed; a restored download always asks the user
        if (!isNewer(release)) { prefs.clearDownload(); return false }
        val snap = query(id) ?: run { prefs.clearDownload(); return false }
        return when (snap.status) {
            DownloadManager.STATUS_SUCCESSFUL -> {
                scope.launch { onDownloadFinished(release, snap) }
                true
            }
            DownloadManager.STATUS_FAILED -> { prefs.clearDownload(); false }
            else -> {
                _state.value = UpdateState.Downloading(release, snap.downloaded, snap.total, 0, queued = true)
                pollDownload(id, release)
                true
            }
        }
    }

    private fun cleanupStaleApks() {
        if (prefs.downloadId >= 0) return
        updatesDir()?.listFiles()?.forEach { it.delete() }
    }

    private fun updatesDir(): File? =
        app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { File(it, "updates") }?.apply { mkdirs() }

    private fun safeFileName(release: ReleaseInfo): String =
        release.asset.name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "update.apk" }

    private data class Snapshot(
        val status: Int, val reason: Int, val downloaded: Long, val total: Long, val localUri: String?,
    )

    private fun query(id: Long): Snapshot? {
        val cursor: Cursor = runCatching { downloadManager.query(DownloadManager.Query().setFilterById(id)) }
            .getOrNull() ?: return null
        return runCatching { cursor.use { c -> readSnapshot(c) } }.getOrNull()
    }

    private fun readSnapshot(cursor: Cursor): Snapshot? {
        cursor.let { c ->
            if (!c.moveToFirst()) return null
            fun col(name: String) = c.getColumnIndexOrThrow(name)
            return Snapshot(
                status = c.getInt(col(DownloadManager.COLUMN_STATUS)),
                reason = c.getInt(col(DownloadManager.COLUMN_REASON)),
                downloaded = c.getLong(col(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                total = c.getLong(col(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                localUri = c.getString(col(DownloadManager.COLUMN_LOCAL_URI)),
            )
        }
    }

    private fun reasonText(reason: Int): String = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "not enough storage"
        DownloadManager.ERROR_HTTP_DATA_ERROR, DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "server error"
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "too many redirects"
        DownloadManager.ERROR_CANNOT_RESUME -> "connection lost"
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "storage unavailable"
        else -> "error $reason"
    }

    companion object {
        private const val APK_MIME = "application/vnd.android.package-archive"
        private const val POLL_MS = 400L
        private const val SNOOZE_MS = 24L * 60 * 60 * 1000
        private const val IDLE_GRACE_MS = 15_000L
        private const val IDLE_RECHECK_MS = 60_000L

        @Volatile private var instance: UpdateManager? = null

        fun get(context: Context): UpdateManager =
            instance ?: synchronized(this) {
                instance ?: UpdateManager(context).also { instance = it }
            }
    }
}
