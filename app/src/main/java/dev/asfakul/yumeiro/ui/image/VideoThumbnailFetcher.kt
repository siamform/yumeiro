/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.image

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Model for a local video thumbnail. Use `AsyncImage(model = VideoThumb(video.uri))`. */
data class VideoThumb(val uri: String)

/**
 * Loads video thumbnails from MediaStore (fast, system-cached) with a frame-grab fallback.
 * Coil cannot decode video URIs on its own, which is why thumbnails were blank before.
 */
class VideoThumbnailFetcher(
    private val context: Context,
    private val data: VideoThumb,
) : Fetcher {

    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val uri = Uri.parse(data.uri)
        val bitmap = loadFromMediaStore(uri) ?: grabFrame(uri)
            ?: throw IllegalStateException("No thumbnail for ${data.uri}")
        DrawableResult(BitmapDrawable(context.resources, bitmap), isSampled = true, dataSource = DataSource.DISK)
    }

    private fun loadFromMediaStore(uri: Uri): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.loadThumbnail(uri, Size(512, 288), null)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Video.Thumbnails.getThumbnail(
                context.contentResolver, ContentUris.parseId(uri), MediaStore.Video.Thumbnails.MINI_KIND, null,
            )
        }
    }.getOrNull()

    private fun grabFrame(uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val frame = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
            frame?.let { scaleDown(it) }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun scaleDown(src: Bitmap): Bitmap {
        val maxW = 512
        if (src.width <= maxW) return src
        val h = (src.height * (maxW / src.width.toFloat())).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, maxW, h, true).also { if (it !== src) src.recycle() }
    }

    class Factory(private val context: Context) : Fetcher.Factory<VideoThumb> {
        override fun create(data: VideoThumb, options: Options, imageLoader: ImageLoader): Fetcher =
            VideoThumbnailFetcher(context.applicationContext, data)
    }

    class Keyer : coil.key.Keyer<VideoThumb> {
        override fun key(data: VideoThumb, options: Options): String = "video-thumb:${data.uri}"
    }
}
