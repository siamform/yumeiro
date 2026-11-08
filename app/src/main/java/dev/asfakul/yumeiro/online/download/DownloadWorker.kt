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
import dev.asfakul.yumeiro.online.OnlineLink
import dev.asfakul.yumeiro.online.OnlineRepository
import dev.asfakul.yumeiro.online.web.BrowserCheck
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext

/** Runs one download from [OnlineDownloads] with a progress notification. */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "online_download"
        const val KEY_ID = "id"
        private const val CHANNEL = "downloads"
        private const val LOG = "DownloadWorker"
        private const val LINK_TIMEOUT_MS = 90_000L
    }

    private val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var lastNotified = 0L

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
            val out = OnlineDownloads.fileOf(applicationContext, item)
            val part = File(out.parentFile, out.name + ".part")
            val onProgress: (Long, Long) -> Unit = { done, total ->
                OnlineDownloads.progress(id, done, total)
                val now = System.currentTimeMillis()
                if (now - lastNotified > 900) {
                    lastNotified = now
                    val pct = if (total > 0) ((done * 100) / total).toInt() else 0
                    runCatching { notifications.notify(notifId, notification(label, pct, 100, total <= 0).build()) }
                }
            }
            val bytes = when (item.kind) {
                OnlineDownloads.Kind.YT_VIDEO, OnlineDownloads.Kind.YT_AUDIO -> YtDownloads.download(applicationContext, client, item, part, onProgress)
                else -> if (item.isHls) HlsDownloader(client, item.headers).download(item.url, part, onProgress) else direct(client, item, part, onProgress)
            }
            if (!part.renameTo(out)) { out.delete(); part.copyTo(out, overwrite = true); part.delete() }
            if ((item.kind == OnlineDownloads.Kind.YT_VIDEO || item.kind == OnlineDownloads.Kind.YT_AUDIO) && DownloadSettings.saveYouTubeToPhone.value) {
                val audio = item.kind == OnlineDownloads.Kind.YT_AUDIO
                val name = out.name.substringBeforeLast('-') + "." + out.extension
                YtDownloads.publish(applicationContext, out, name, YtDownloads.mimeOf(item.container ?: out.extension, audio), music = audio)?.let { uri ->
                    // The phone's copy is the one that plays; drop the app copy so space isn't used twice.
                    OnlineDownloads.resolved(id) { it.copy(publicUri = uri.toString()) }
                    out.delete()
                }
            }
            OnlineDownloads.finished(id, bytes)
            done(notifId, label, "Download complete")
            return Result.success()
        } catch (e: CancellationException) {
            OnlineDownloads.paused(id)
            throw e
        } catch (e: Throwable) {
            Log.w(LOG, "download ${item.title}", e)
            val msg = when (e) {
                is HttpStatus -> "Server answered HTTP ${e.code}" + if (e.code == 401 || e.code == 403) {
                    if (item.kind == OnlineDownloads.Kind.LINK) " — the link may have expired; play the episode again and retry" else " — tap retry for a fresh link"
                } else ""
                is NoLink -> e.message ?: "No downloadable source"
                is IOException -> e.message?.takeIf { it.isNotBlank() && !it.startsWith("HTTP") }?.let { "Network error — $it" } ?: "Network error — will retry"
                else -> e.message ?: "Download failed"
            }
            if (e is IOException && e !is HttpStatus && e !is NoLink && runAttemptCount < 3) return Result.retry()
            OnlineDownloads.failed(id, msg)
            done(notifId, label, "Download failed: $msg")
            return Result.failure()
        }
    }

    /** Finds a fresh link for a queued episode and names its file. */
    private suspend fun resolveEpisode(item: OnlineDownloads.Item): OnlineDownloads.Item {
        val data = item.data ?: throw NoLink("Episode data missing")
        ExtensionManager.ensureLoaded(applicationContext)
        val provider = ExtensionManager.provider(item.api) ?: throw NoLink("Extension \"${item.api}\" isn't installed")
        val links = ArrayList<OnlineLink>()
        var error: String? = null
        withTimeoutOrNull(LINK_TIMEOUT_MS) {
            OnlineRepository.links(provider, data).collect { ev ->
                when (ev) {
                    is LinkEvent.Link -> links += ev.link
                    is LinkEvent.Done -> if (!ev.ok) error = ev.error
                    else -> Unit
                }
            }
        }
        val sorted = OnlineRepository.sortLinks(links)
        // Links that need a separate audio file can't be saved as one file; use them only as a last resort.
        val link = DownloadNaming.pickLink(sorted.filter { it.audioTracks.isEmpty() }, DownloadSettings.quality.value)
            ?: DownloadNaming.pickLink(sorted, DownloadSettings.quality.value)
            ?: throw NoLink(error ?: if (links.isEmpty()) "No sources found for this episode" else "No downloadable source (only DASH/torrent)")
        val ext = DownloadNaming.extensionFor(link)
        val base = item.baseName ?: DownloadNaming.clean(item.episodeLabel ?: "Episode ${item.order + 1}", 90)
        // Same file name on retries so a partial file resumes.
        val name = if (item.fileName.isNotBlank() && item.fileName.substringAfterLast('.') == ext) item.fileName else "$base.$ext"
        OnlineDownloads.resolved(item.id) {
            it.copy(url = link.url, isHls = link.isHls, headers = OnlineDownloads.headersFor(link), fileName = name, quality = link.qualityLabel.ifBlank { null })
        }
        return OnlineDownloads.get(item.id) ?: throw NoLink("Removed")
    }

    private suspend fun direct(client: OkHttpClient, item: OnlineDownloads.Item, part: File, progress: (Long, Long) -> Unit): Long {
        val have = if (part.exists()) part.length() else 0L
        val b = Request.Builder().url(item.url)
        item.headers.forEach { (k, v) -> runCatching { b.header(k, v) } }
        if (have > 0) b.header("Range", "bytes=$have-")
        client.newCall(b.build()).execute().use { r ->
            if (!r.isSuccessful) throw HttpStatus(r.code)
            val resumed = r.code == 206 && have > 0
            val body = r.body
            val length = body.contentLength()
            val total = if (length > 0) length + (if (resumed) have else 0) else -1
            var done = if (resumed) have else 0L
            FileOutputStream(part, resumed).use { fos ->
                body.byteStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf); if (n < 0) break
                        fos.write(buf, 0, n); done += n
                        progress(done, total)
                    }
                }
            }
            if (total > 0 && done < total) throw IOException("Connection closed early")
            return done
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
    }

    private fun foreground(id: Int, title: String, p: Int, max: Int, indeterminate: Boolean): ForegroundInfo {
        val n = notification(title, p, max, indeterminate).build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ForegroundInfo(id, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(id, n)
    }

    private fun done(id: Int, title: String, text: String) {
        channel()
        runCatching {
            notifications.notify(id + 0x1000, NotificationCompat.Builder(applicationContext, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title.ifBlank { "Download" }).setContentText(text).setAutoCancel(true).build())
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
