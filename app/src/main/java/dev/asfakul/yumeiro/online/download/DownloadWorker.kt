/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.online.LinkEvent
import kotlinx.coroutines.async
import dev.asfakul.yumeiro.online.OnlineLink
import dev.asfakul.yumeiro.online.OnlineRepository
import dev.asfakul.yumeiro.online.web.BrowserCheck
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException

/** Runs one download from [OnlineDownloads] with a progress notification. */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        /** Fastest the download list is updated with progress (G8/BUG-3). */
        internal const val PROGRESS_MS = 250L
        const val TAG = "online_download"
        const val KEY_ID = "id"
        private const val CHANNEL = "downloads"
        /** Notification tap: opens the Downloads page ([dev.asfakul.yumeiro.MainActivity]). */
        const val ACTION_OPEN_DOWNLOADS = "dev.asfakul.yumeiro.OPEN_DOWNLOADS"
        private const val LOG = "DownloadWorker"
        private const val LINK_TIMEOUT_MS = 90_000L
    }

    private val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var lastNotified = 0L
    private val progressGate = ProgressGate(PROGRESS_MS)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        OnlineDownloads.ensureLoaded(applicationContext)
        DownloadSettings.init(applicationContext)
        val id = inputData.getString(KEY_ID) ?: return@withContext Result.failure()
        val first = OnlineDownloads.get(id) ?: return@withContext Result.failure()
        if (first.status == OnlineDownloads.Status.DONE) return@withContext Result.success()
        // Limit parallel downloads; one at a time per extension (sites ban bursts, like Aniyomi).
        val key = if (first.kind == OnlineDownloads.Kind.YT_VIDEO || first.kind == OnlineDownloads.Kind.YT_AUDIO) null else first.api
        DownloadGate.acquire(id, key)
        try {
            run(id)
        } finally {
            DownloadGate.release(id)
        }
    }

    private suspend fun run(id: String): Result {
        var item = OnlineDownloads.get(id) ?: return Result.failure()
        if (item.status == OnlineDownloads.Status.DONE || item.status == OnlineDownloads.Status.PAUSED) return Result.success()
        val label = listOfNotNull(item.title.takeIf { it.isNotBlank() }, item.episodeLabel?.takeIf { item.kind == OnlineDownloads.Kind.EPISODE }).joinToString(" · ")
        val notifId = 0x5000 + (id.hashCode() and 0xfff)
        runCatching { setForeground(foreground(notifId, label, 0, 0, true)) }
        val client = NetworkClient.get(applicationContext).newBuilder().addInterceptor(BrowserCheck.MediaInterceptor).build()
        try {
            OnlineDownloads.progress(id, item.bytes, item.total)
            if (item.kind == OnlineDownloads.Kind.EPISODE && (item.url.isBlank() || item.fileName.isBlank() || runAttemptCount > 0)) {
                item = resolveEpisode(item)
            }
            var out = OnlineDownloads.fileOf(applicationContext, item)
            val part = File(out.parentFile, out.name + ".part")
            val named = out // side files are named after this one even if [out] becomes .webm
            val onProgress: (Long, Long) -> Unit = { done, total ->
                val now = System.currentTimeMillis()
                if (progressGate.pass(now, done, total)) OnlineDownloads.progress(id, done, total)
                if (now - lastNotified > 900) {
                    lastNotified = now
                    val pct = if (total > 0) ((done * 100) / total).toInt() else 0
                    runCatching { notifications.notify(notifId, notification(label, pct, 100, total <= 0).build()) }
                }
            }
            val bytes = when (item.kind) {
                OnlineDownloads.Kind.YT_VIDEO, OnlineDownloads.Kind.YT_AUDIO -> YtDownloads.download(applicationContext, client, item, part, onProgress)
                else -> forced(client, item, part, onProgress) { webm ->
                    // A DASH stream whose tracks are both WebM is saved as .webm.
                    if (webm && out.extension.equals("mp4", true)) {
                        val renamed = File(out.parentFile, out.nameWithoutExtension + ".webm")
                        OnlineDownloads.resolved(id) { it.copy(fileName = it.fileName.substringBeforeLast('.') + ".webm") }
                        out = renamed
                    }
                }
            }
            // G8 (R17): deleted while the last bytes came in: don't leave an untracked file behind.
            if (OnlineDownloads.get(id) == null) { OnlineDownloads.removeFiles(named); OnlineDownloads.removeFiles(out); return Result.success() }
            if (!part.renameTo(out)) { out.delete(); part.copyTo(out, overwrite = true); part.delete() }
            YtDownloads.afterDownload(applicationContext, item, out)
            OnlineDownloads.finished(id, bytes)
            done(notifId, label, if (OnlineDownloads.get(id)?.keep == true) "Saved · Permanent · tap to open Downloads" else "Saved · Offline · tap to open Downloads")
            return Result.success()
        } catch (e: CancellationException) {
            // R16 (G8): a stop by the system (network lost, quota) used to mark the item PAUSED, so the
            // rerun WorkManager makes when the network is back saw PAUSED and quit: stuck until tapped.
            // A user pause or delete already set its own state; only a running item goes back to QUEUED.
            OnlineDownloads.stopped(id)
            // G8 (R17): a delete cancels the work, but a connection that was mid-read finishes its
            // piece and can write a side file after delete removed them; clean up once it stopped.
            if (OnlineDownloads.get(id) == null && item.fileName.isNotBlank()) runCatching { OnlineDownloads.removeFiles(OnlineDownloads.fileOf(applicationContext, item)) }
            throw e
        } catch (e: Throwable) {
            Log.w(LOG, "download ${item.title}", e)
            val diskFull = e is DiskFull || DiskFull.isDiskFull(e)
            val msg = when {
                diskFull -> (e as? DiskFull)?.message ?: "Storage full — free up space, then tap retry"
                else -> when (e) {
                is HttpStatus -> "Server answered HTTP ${e.code}" + if (e.code == 401 || e.code == 403) {
                    if (item.kind == OnlineDownloads.Kind.LINK) " — the link may have expired; play the episode again and retry" else " — tap retry for a fresh link"
                } else ""
                is NoLink -> e.message ?: "No downloadable source"
                is IOException -> e.message?.takeIf { it.isNotBlank() && !it.startsWith("HTTP") }?.let { "Network error — $it" } ?: "Network error — will retry"
                else -> e.message ?: "Download failed"
            } }
            // A full disk doesn't get better by retrying; the partial file stays for a later resume.
            if (!diskFull && e is IOException && e !is HttpStatus && e !is NoLink && runAttemptCount < 3) return Result.retry()
            OnlineDownloads.failed(id, msg)
            done(notifId, label, "Download failed: $msg · tap to retry in Downloads")
            return Result.failure()
        }
    }

    /** Finds a fresh link for a queued episode and names its file. */
    private suspend fun resolveEpisode(item: OnlineDownloads.Item): OnlineDownloads.Item {
        val data = item.data ?: throw NoLink("Episode data missing")
        ExtensionManager.ensureLoaded(applicationContext)
        val provider = ExtensionManager.provider(item.api) ?: throw NoLink("Extension \"${item.api}\" isn't installed")
        val links = ArrayList<OnlineLink>()
        val torrents = ArrayList<String>()
        var error: String? = null
        withTimeoutOrNull(LINK_TIMEOUT_MS) {
            OnlineRepository.links(provider, data).collect { ev ->
                when (ev) {
                    is LinkEvent.Link -> links += ev.link
                    is LinkEvent.Skipped -> ev.url?.let { torrents += it }
                    is LinkEvent.Done -> if (!ev.ok) error = ev.error
                    else -> Unit
                }
            }
        }
        val sorted = OnlineRepository.sortLinks(links)
        // Links that need a separate audio file can't be saved as one file; use them only as a last resort.
        // R15 forced download: a plain file first, then split video/audio, then DASH (ForceGrab).
        val link = DownloadNaming.pickLink(sorted.filter { it.audioTracks.isEmpty() }, DownloadSettings.quality.value)
            ?: DownloadNaming.pickLink(sorted, DownloadSettings.quality.value)
            ?: DownloadNaming.pickLink(sorted, DownloadSettings.quality.value, allowDash = true)
            ?: torrents.firstOrNull()?.let { t -> OnlineLink("Torrent", "Torrent", t, null, -1, false, false, emptyMap()) }
            ?: throw NoLink(error ?: if (links.isEmpty()) "No sources found for this episode" else "No downloadable source")
        val ext = DownloadNaming.extensionFor(link)
        val base = item.baseName ?: DownloadNaming.clean(item.episodeLabel ?: "Episode ${item.order + 1}", 90)
        // Same file name on retries so a partial file resumes.
        val name = if (item.fileName.isNotBlank() && item.fileName.substringAfterLast('.') == ext) item.fileName else "$base.$ext"
        OnlineDownloads.resolved(item.id) {
            it.copy(url = link.url, isHls = link.isHls, headers = OnlineDownloads.headersFor(link), fileName = name, quality = link.qualityLabel.ifBlank { null },
                isDash = link.isDash, audioUrl = link.audioTracks.firstOrNull()?.first)
        }
        return OnlineDownloads.get(item.id) ?: throw NoLink("Removed")
    }

    /**
     * R15 forced download (see [ForceGrab]): DASH, split video + audio, torrents through
     * TorrServer, HLS, and direct files over several connections with header escalation.
     */
    private suspend fun forced(client: OkHttpClient, item: OnlineDownloads.Item, part: File, progress: (Long, Long) -> Unit, onWebm: (Boolean) -> Unit): Long {
        val lowEnd = dev.asfakul.yumeiro.DeviceClass.lowEnd
        val space: (Long) -> Unit = { need ->
            val free = OnlineDownloads.freeBytes(applicationContext)
            if (free in 0 until need + DiskFull.MARGIN) throw DiskFull(need, free)
        }
        fun direct(url: String, headers: Map<String, String>, f: File, p: (Long, Long) -> Unit) =
            Segmented(client, ForceGrab.connections(lowEnd), space).let { s -> suspend { s.download(url, headers, f, p) } }
        return when {
            item.isDash -> DashGrab(client, item.headers).download(item.url, part, DownloadSettings.quality.value, lowEnd, progress).also { onWebm(it.webm) }.bytes
            item.audioUrl != null -> {
                val v = File(part.parentFile, part.name + ".v"); val a = File(part.parentFile, part.name + ".a")
                var vb = 0L; var ab = 0L
                kotlinx.coroutines.coroutineScope {
                    val vj = this.async { if (item.isHls) HlsDownloader(client, item.headers, DownloadSettings.quality.value).download(item.url, v) { d, _ -> vb = d; progress(vb + ab, -1) } else direct(item.url, item.headers, v) { d, _ -> vb = d; progress(vb + ab, -1) }() }
                    val aj = this.async { direct(item.audioUrl, item.headers, a) { d, _ -> ab = d; progress(vb + ab, -1) }() }
                    vj.await(); aj.await()
                }
                MediaMux.merge(v, a, part, webm = item.fileName.endsWith(".webm", true))
                v.delete(); a.delete()
                part.length()
            }
            // G8-2: a variant with a separate audio rendition is saved as two tracks and joined here.
            item.isHls -> HlsDownloader(client, item.headers, DownloadSettings.quality.value)
                .download(item.url, part, join = { v, a, o -> MediaMux.merge(v, a, o, webm = false) }, progress = progress)
            dev.asfakul.yumeiro.stream.LinkHosts.isTorrent(item.url) -> {
                dev.asfakul.yumeiro.stream.TorrentSettings.init(applicationContext)
                val server = dev.asfakul.yumeiro.stream.TorrentSettings.server.value
                if (server.isBlank()) throw NoLink("This is a torrent. Add your TorrServer address in Settings › Network to download it")
                direct(dev.asfakul.yumeiro.stream.LinkHosts.torrServerUrl(server, item.url, item.title, m3u = false), emptyMap(), part, progress)()
            }
            else -> direct(item.url, item.headers, part, progress)()
        }
    }

    private fun channel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && notifications.getNotificationChannel(CHANNEL) == null) {
            notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun notification(title: String, progress: Int, max: Int, indeterminate: Boolean): NotificationCompat.Builder {
        channel()
        return NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title.ifBlank { "Downloading" })
            .setContentText(if (indeterminate) "Downloading…" else "$progress%")
            .setOnlyAlertOnce(true).setOngoing(true)
            .setProgress(max, progress, indeterminate)
            .setContentIntent(openDownloads())
    }

    private fun openDownloads(): android.app.PendingIntent = android.app.PendingIntent.getActivity(
        applicationContext, 0x5fff,
        android.content.Intent(applicationContext, dev.asfakul.yumeiro.MainActivity::class.java).setAction(ACTION_OPEN_DOWNLOADS)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP),
        android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun foreground(id: Int, title: String, p: Int, max: Int, indeterminate: Boolean): ForegroundInfo {
        val n = notification(title, p, max, indeterminate).build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ForegroundInfo(id, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(id, n)
    }

    private fun done(id: Int, title: String, text: String) {
        channel()
        runCatching {
            notifications.notify(id + 0x1000, NotificationCompat.Builder(applicationContext, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title.ifBlank { "Download" }).setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(openDownloads()).setAutoCancel(true).build())
        }
    }
}

internal class HttpStatus(val code: Int) : IOException("HTTP $code")
internal class NoLink(message: String) : IOException(message)

/** Parallel-download gate: at most [DownloadSettings.parallel] running, one per extension key. */
internal object DownloadGate {
    private val running = HashMap<String, String?>()

    suspend fun acquire(id: String, key: String?) {
        while (true) {
            synchronized(this) {
                val busyKey = key != null && running.values.any { it == key }
                if (running.size < DownloadSettings.parallel.value && !busyKey) { running[id] = key; return }
            }
            kotlinx.coroutines.delay(700)
        }
    }

    fun release(id: String) = synchronized(this) { running.remove(id); Unit }
}

/** Daily auto-delete pass for finished downloads. */
class DownloadCleanupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching { OnlineDownloads.cleanup(applicationContext) }
        Result.success()
    }
}

/** The storage can't hold the rest of a download (checked up front, or ENOSPC while writing). */
internal class DiskFull(needed: Long, free: Long) : java.io.IOException(
    "Storage full — needs about ${needed / (1024 * 1024)} MB more, ${free / (1024 * 1024)} MB free. Free up space, then tap retry",
) {
    companion object {
        /** Keeps room for the OS and other apps. */
        const val MARGIN = 64L * 1024 * 1024
        fun isDiskFull(e: Throwable): Boolean {
            var t: Throwable? = e
            while (t != null) {
                val m = t.message.orEmpty()
                if ("ENOSPC" in m || "No space left" in m) return true
                t = t.cause
            }
            return false
        }
    }
}

/** R16 (G8): what to do with the answer to a `Range: bytes=have-` request. */
internal object RangeResume {
    enum class Action { APPEND, RESTART, COMPLETE }

    fun decide(have: Long, code: Int, contentRange: String?): Action {
        val cr = contentRange?.trim()?.lowercase()?.removePrefix("bytes")?.trim()
        return when (code) {
            // "bytes */12345": the server's full size; equal to what we have means the file is complete.
            416 -> if (cr?.substringAfter('/', "")?.toLongOrNull() == have) Action.COMPLETE else Action.RESTART
            // "bytes 500-999/1000": append only if it starts where the .part ends.
            206 -> {
                if (cr == null) return Action.APPEND
                val start = cr.substringBefore('-', "").trim().toLongOrNull()
                if (start == null || start == have) Action.APPEND else Action.RESTART
            }
            else -> Action.APPEND
        }
    }
}

/**
 * G8/BUG-3: engines report every 64 KiB read on every connection (100+ calls a second at 10 MB/s).
 * Each report copied the whole download list and woke every collector (Downloads screen, badges).
 * The gate lets one report through per [intervalMs], and always the last one (done == total).
 * Thread-safe: several connections report at once. `ProgressGateTest`.
 */
internal class ProgressGate(private val intervalMs: Long) {
    private val last = java.util.concurrent.atomic.AtomicLong(Long.MIN_VALUE / 2)
    fun pass(now: Long, done: Long, total: Long): Boolean {
        if (total > 0 && done >= total) { last.set(now); return true }
        val prev = last.get()
        return now - prev >= intervalMs && last.compareAndSet(prev, now)
    }
}
