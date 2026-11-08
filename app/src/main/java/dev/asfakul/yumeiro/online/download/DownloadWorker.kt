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
import dev.asfakul.yumeiro.online.web.BrowserCheck
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.coroutineContext

/** Runs one download from [OnlineDownloads] with a progress notification. */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "online_download"
        const val KEY_ID = "id"
        private const val CHANNEL = "downloads"
        private const val LOG = "DownloadWorker"
    }

    private val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var lastNotified = 0L

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        OnlineDownloads.ensureLoaded(applicationContext)
        val id = inputData.getString(KEY_ID) ?: return@withContext Result.failure()
        val item = OnlineDownloads.get(id) ?: return@withContext Result.failure()
        if (item.status == OnlineDownloads.Status.DONE) return@withContext Result.success()
        val notifId = 0x5000 + (id.hashCode() and 0xfff)
        runCatching { setForeground(foreground(notifId, item.title, 0, 0, true)) }
        val client = NetworkClient.get(applicationContext).newBuilder().addInterceptor(BrowserCheck.MediaInterceptor).build()
        val out = OnlineDownloads.fileOf(applicationContext, item)
        val part = File(out.parentFile, out.name + ".part")
        try {
            val onProgress: (Long, Long) -> Unit = { done, total ->
                OnlineDownloads.progress(id, done, total)
                val now = System.currentTimeMillis()
                if (now - lastNotified > 900) {
                    lastNotified = now
                    val pct = if (total > 0) ((done * 100) / total).toInt() else 0
                    runCatching { notifications.notify(notifId, notification(item.title, pct, 100, total <= 0).build()) }
                }
            }
            val bytes = if (item.isHls) HlsDownloader(client, item.headers).download(item.url, part, onProgress)
            else direct(client, item, part, onProgress)
            if (!part.renameTo(out)) { out.delete(); part.copyTo(out, overwrite = true); part.delete() }
            OnlineDownloads.finished(id, bytes)
            done(notifId, item.title, "Download complete")
            Result.success()
        } catch (e: CancellationException) {
            OnlineDownloads.paused(id)
            throw e
        } catch (e: Throwable) {
            Log.w(LOG, "download ${item.title}", e)
            val msg = when (e) {
                is HttpStatus -> "Server answered HTTP ${e.code}" + if (e.code == 401 || e.code == 403) " — the link may have expired; play the episode again and retry" else ""
                is IOException -> "Network error — will retry"
                else -> e.message ?: "Download failed"
            }
            if (e is IOException && e !is HttpStatus && runAttemptCount < 3) return@withContext Result.retry()
            OnlineDownloads.failed(id, msg)
            done(notifId, item.title, "Download failed: $msg")
            Result.failure()
        }
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
                        coroutineContext.ensureActive()
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
