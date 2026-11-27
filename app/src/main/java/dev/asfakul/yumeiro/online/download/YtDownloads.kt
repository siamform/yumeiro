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
    /** G8 (R17): connection drops in a row with no bytes before a download gives up. */
    private const val MAX_DROPS = 4

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
        // OL-12: playlist batches pick the best audio now, so long queues never hold a stale format.
        val audioItag = if (item.audioItag == YtDownloadOptions.AUTO_AUDIO)
            YtDownloadOptions.bestAudio(options(info))?.audioItag ?: throw IOException("No audio offered for this song")
        else item.audioItag
        val audio = if (audioItag > 0) info.audioStreams.firstOrNull { it.itag == audioItag } ?: throw IOException("This audio quality is no longer offered — pick another") else null
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
    internal suspend fun fetch(client: OkHttpClient, url: String, headers: Map<String, String>, file: File, knownLength: Long, progress: (Long) -> Unit): Long {
        var have = if (file.exists()) file.length() else 0L
        var total = knownLength
        if (total in 1..have) return have
        var drops = 0
        FileOutputStream(file, true).use { fos ->
            val buf = ByteArray(64 * 1024)
            while (total <= 0 || have < total) {
                coroutineContext.ensureActive()
                val end = have + CHUNK - 1
                val b = Request.Builder().url(url).header("Range", "bytes=$have-$end")
                headers.forEach { (k, v) -> runCatching { b.header(k, v) } }
                var got = 0L
                try {
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
                    drops = 0
                } catch (e: HttpStatus) { throw e } catch (e: IOException) {
                    // G8 (R17): a dropped connection used to fail the whole download (the worker then
                    // waited for its backoff). The bytes stay; ask again from [have]. Only drops that
                    // bring nothing count towards the limit, like a download manager.
                    if (got > 0) drops = 0 else if (++drops >= MAX_DROPS) throw e
                    kotlinx.coroutines.delay(if (got > 0) 200L else 800L * drops)
                    continue
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
    /**
     * "Save YouTube and link downloads to phone" (`yt_to_phone`): a finished YouTube download is copied to the
     * phone's Movies/Yumeiro or Music/Yumeiro and the app copy deleted. Off: the file stays in the app.
     * Returns the public Uri when published. Moved out of DownloadWorker unchanged (G9 effect test).
     */
    internal fun afterDownload(context: Context, item: OnlineDownloads.Item, out: File): Uri? {
        if (isPlayLink(item)) return afterLinkDownload(context, item, out)
        if (item.kind != OnlineDownloads.Kind.YT_VIDEO && item.kind != OnlineDownloads.Kind.YT_AUDIO) return null
        if (!DownloadSettings.saveYouTubeToPhone.value) return null
        val audio = item.kind == OnlineDownloads.Kind.YT_AUDIO
        val name = out.name.substringBeforeLast('-') + "." + out.extension
        return publish(context, out, name, mimeOf(item.container ?: out.extension, audio), music = audio, profile = item.profile)?.also { uri ->
            // The phone's copy is the one that plays; drop the app copy so space isn't used twice.
            OnlineDownloads.resolved(item.id) { it.copy(publicUri = uri.toString()) }
            out.delete()
        }
    }

    /** BUG-7 7b: a link the user pasted (Live → Play link) or a website video saved from the player. */
    fun isPlayLink(item: OnlineDownloads.Item) = item.kind == OnlineDownloads.Kind.LINK && item.api == "Network"

    fun linkMime(ext: String) = when (ext.lowercase()) {
        "ts" -> "video/mp2t"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "avi" -> "video/x-msvideo"
        else -> "video/mp4"
    }

    /**
     * BUG-7 7b (owner): Play link downloads stayed in the app's private folder, so the gallery never
     * saw them. With "Save downloads to phone" on (the YouTube switch, now for links too) the file is
     * copied to Movies/Yumeiro. The app copy is deleted only after the copy is complete, and the item
     * becomes Permanent so the auto-delete never removes a file the user now sees in the gallery.
     */
    private fun afterLinkDownload(context: Context, item: OnlineDownloads.Item, out: File): Uri? {
        if (!DownloadSettings.saveYouTubeToPhone.value) return null
        val name = out.name.substringBeforeLast('-') + "." + out.extension
        return publish(context, out, name, linkMime(out.extension), music = false, profile = item.profile)?.also { uri ->
            OnlineDownloads.resolved(item.id) { it.copy(publicUri = uri.toString(), keep = true) }
            out.delete()
        }
    }

    /** B19: another profile's phone copies go to their own folder (`Movies/Yumeiro/<profile name>`), not mixed with the main profile's. */
    internal fun profileFolder(context: Context, profile: String): String {
        if (profile == dev.asfakul.yumeiro.profiles.Profiles.DEFAULT) return ""
        val name = dev.asfakul.yumeiro.profiles.Profiles.find(context, profile)?.name.orEmpty()
        val clean = DownloadNaming.clean(name.ifBlank { "Profile $profile" }, 40).ifBlank { "Profile $profile" }
        return "/$clean"
    }

    fun publish(context: Context, file: File, displayName: String, mime: String, music: Boolean, profile: String = dev.asfakul.yumeiro.profiles.Profiles.DEFAULT): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val resolver = context.contentResolver
        val collection = if (music) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, (if (music) "Music" else "Movies") + "/Yumeiro" + profileFolder(context, profile))
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
