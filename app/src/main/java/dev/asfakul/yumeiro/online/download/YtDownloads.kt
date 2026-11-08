/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import dev.asfakul.yumeiro.youtube.YouTube
import dev.asfakul.yumeiro.youtube.YouTubeStreams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.coroutineContext

/** YouTube / YouTube Music downloads: quality list, chunked fetch, muxing and saving to the phone. */
object YtDownloads {
    /** googlevideo throttles one big request; 8 MB ranges keep full speed (NewPipe does the same). */
    private const val CHUNK = 8L * 1024 * 1024

    private fun containerOf(s: Stream): String? = when (s.format) {
        MediaFormat.MPEG_4, MediaFormat.M4A -> if (s.format == MediaFormat.M4A) "m4a" else "mp4"
        MediaFormat.WEBM, MediaFormat.WEBMA, MediaFormat.WEBMA_OPUS -> "webm"
        else -> null
    }

    private fun sizeOf(s: Stream): Long = runCatching { s.itagItem?.contentLength ?: -1L }.getOrDefault(-1L).coerceAtLeast(0L)

    fun options(info: StreamInfo): List<YtDownloadOptions.Choice> {
        val videos = (info.videoOnlyStreams + info.videoStreams).filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }.mapNotNull { v ->
            val c = containerOf(v) ?: return@mapNotNull null
            YtDownloadOptions.Video(v.itag, v.height, v.fps, c, v.codec.orEmpty(), sizeOf(v), v.isVideoOnly())
        }
        val audios = info.audioStreams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }.mapNotNull { a ->
            val c = containerOf(a) ?: return@mapNotNull null
            val original = runCatching { a.audioTrackType }.getOrNull().let { it == null || it == AudioTrackType.ORIGINAL }
            YtDownloadOptions.Audio(a.itag, (a.averageBitrate.takeIf { it > 0 } ?: a.bitrate).coerceAtLeast(0), c, a.codec.orEmpty(), sizeOf(a), original)
        }
        return YtDownloadOptions.build(videos, audios, Build.VERSION.SDK_INT)
    }

    suspend fun optionsFor(context: Context, id: String): Pair<StreamInfo, List<YtDownloadOptions.Choice>> = withContext(Dispatchers.IO) {
        val info = YouTube.info(context, id)
        info to options(info)
    }

    /** Runs a YouTube download into [out] (video + audio joined when needed). */
    internal suspend fun download(context: Context, client: OkHttpClient, item: OnlineDownloads.Item, out: File, progress: (Long, Long) -> Unit): Long {
        val id = item.ytId ?: throw IOException("Missing video id")
        // Links expire after a few hours: always extract fresh ones.
        val info = YouTube.info(context, id, force = true)
        val streams = info.videoOnlyStreams + info.videoStreams
        val video = if (item.videoItag > 0) streams.firstOrNull { it.itag == item.videoItag } ?: throw IOException("This quality is no longer offered — pick another") else null
        val audio = if (item.audioItag > 0) info.audioStreams.firstOrNull { it.itag == item.audioItag } ?: throw IOException("This audio quality is no longer offered — pick another") else null
        val parts = listOfNotNull(video, audio)
        if (parts.isEmpty()) throw IOException("Nothing to download")
        val sizes = parts.map { sizeOf(it) }
        val total = if (sizes.all { it > 0 }) sizes.sum() else -1L
        val dir = out.parentFile!!
        if (parts.size == 1) {
            return fetch(client, parts[0].content, YouTubeStreams.headersFor(parts[0].content), out, sizes[0]) { progress(it, total) }
        }
        val vFile = File(dir, out.name + ".v"); val aFile = File(dir, out.name + ".a")
        val vBytes = fetch(client, video!!.content, YouTubeStreams.headersFor(video.content), vFile, sizes[0]) { progress(it, total) }
        fetch(client, audio!!.content, YouTubeStreams.headersFor(audio.content), aFile, sizes[1]) { progress(vBytes + it, total) }
        MediaMux.merge(vFile, aFile, out, webm = item.container == "webm")
        vFile.delete(); aFile.delete()
        return out.length()
    }

    /** Range-chunked, resumable fetch of [url] into [file]. */
    private suspend fun fetch(client: OkHttpClient, url: String, headers: Map<String, String>, file: File, knownLength: Long, progress: (Long) -> Unit): Long {
        var have = if (file.exists()) file.length() else 0L
        var total = knownLength
        if (total in 1..have) return have
        FileOutputStream(file, true).use { fos ->
            val buf = ByteArray(64 * 1024)
            while (total <= 0 || have < total) {
                coroutineContext.ensureActive()
                val end = have + CHUNK - 1
                val b = Request.Builder().url(url).header("Range", "bytes=$have-$end")
                headers.forEach { (k, v) -> runCatching { b.header(k, v) } }
                var got = 0L
                client.newCall(b.build()).execute().use { r ->
                    if (r.code == 416) return have
                    if (!r.isSuccessful) throw HttpStatus(r.code)
                    if (r.code == 200 && have > 0) { fos.channel.truncate(0); have = 0 }
                    r.header("Content-Range")?.substringAfter('/')?.toLongOrNull()?.let { total = it }
                    if (r.code == 200) total = r.body.contentLength().takeIf { it > 0 } ?: total
                    r.body.byteStream().use { input ->
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf); if (n < 0) break
                            fos.write(buf, 0, n); have += n; got += n
                            progress(have)
                        }
                    }
                }
                if (got == 0L) break
            }
        }
        if (total > 0 && have < total) throw IOException("Connection closed early")
        return have
    }

    fun mimeOf(container: String, audioOnly: Boolean) = when {
        audioOnly && container == "m4a" -> "audio/mp4"
        audioOnly -> "audio/webm"
        container == "webm" -> "video/webm"
        else -> "video/mp4"
    }

    /** Copies a finished file into the phone's Music/Yumeiro or Movies/Yumeiro (Android 10+, no permission). */
    fun publish(context: Context, file: File, displayName: String, mime: String, music: Boolean): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val resolver = context.contentResolver
        val collection = if (music) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, (if (music) "Music" else "Movies") + "/Yumeiro")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { os -> file.inputStream().use { it.copyTo(os, 256 * 1024) } } ?: throw IOException("No output stream")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            uri
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }
}
