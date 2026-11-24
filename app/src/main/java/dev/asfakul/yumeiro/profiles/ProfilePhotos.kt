/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.profiles

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PROFILE-PIC-1 (owner list 30): a profile's own picture, picked from the gallery with the system photo
 * picker (no storage permission). The picture is centre-cropped to a square, scaled to [SIZE] px and
 * kept as `filesDir/profiles/<id>/avatar.jpg`, so it goes with the profile when the profile is deleted,
 * is in backups with the rest of the profile (and stays out of them with a locked profile's data), and
 * never depends on the gallery copy still existing. [Profiles.Profile.photo] holds the save time, which
 * is also the cache key, so a changed picture shows at once everywhere.
 */
object ProfilePhotos {
    const val SIZE = 320
    private const val NAME = "avatar.jpg"
    private val cache = LruCache<String, Bitmap>(Profiles.MAX_PROFILES * 2)

    fun file(context: Context, id: String): File = File(File(Profiles.root(context), id), NAME)

    /** Centre square of [w] x [h]: left, top, side. Pure. */
    internal fun centreSquare(w: Int, h: Int): Triple<Int, Int, Int> {
        val side = minOf(w, h).coerceAtLeast(1)
        return Triple((w - side) / 2, (h - side) / 2, side)
    }

    /** Imports [uri] as [id]'s picture. Null on success, otherwise a message for the user. */
    suspend fun import(context: Context, id: String, uri: Uri): String? = withContext(Dispatchers.IO) {
        if (Profiles.find(context, id) == null) return@withContext "That profile no longer exists."
        val cr = context.contentResolver
        val type = runCatching { cr.getType(uri) }.getOrNull().orEmpty()
        if (type.startsWith("video/")) return@withContext "That's a video. Pick a photo instead."
        val size = runCatching { cr.openAssetFileDescriptor(uri, "r")?.use { it.length } }.getOrNull() ?: -1L
        if (size > 60L * 1024 * 1024) return@withContext "That picture is over 60 MB. Pick a smaller one."
        val error = runCatching {
            // Shares the backdrop's decoder: down-sampled while decoding, EXIF rotation applied (Android 9+),
            // and a cut-off or damaged file is refused instead of saved half grey.
            val src = dev.asfakul.yumeiro.ui.theme.BackdropSettings.decodeScaled(context, uri, SIZE)
                ?: return@runCatching "This picture's format isn't supported here. Try a JPEG or PNG."
            if (src.width < 64 || src.height < 64) { src.recycle(); return@runCatching "That picture is too small. Use one at least 64 × 64 px." }
            val (l, t, side) = centreSquare(src.width, src.height)
            val square = Bitmap.createBitmap(src, l, t, side, side)
            val out = if (side > SIZE) Bitmap.createScaledBitmap(square, SIZE, SIZE, true) else square
            val dest = file(context, id).apply { parentFile?.mkdirs() }
            val tmp = File(dest.parentFile, "$NAME.tmp")
            tmp.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            if (out !== square) out.recycle()
            if (square !== src) square.recycle()
            src.recycle()
            if (!tmp.renameTo(dest)) { tmp.copyTo(dest, overwrite = true); tmp.delete() }
            null
        }.getOrElse { e ->
            dev.asfakul.yumeiro.diagnostics.CrashLog.nonFatal("Profile picture import ($type, $size bytes)", e)
            when (e) {
                is java.io.FileNotFoundException, is SecurityException -> "Yumeiro couldn't read that picture. If it's only in the cloud, download it to the phone first."
                is OutOfMemoryError -> "That picture is too large to open on this phone. Pick a smaller one."
                is dev.asfakul.yumeiro.ui.theme.BackdropSettings.BrokenPicture -> "That picture couldn't be opened: the file is damaged or incomplete. Try another copy."
                else -> "That picture couldn't be opened. Try a JPEG or PNG."
            }
        }
        if (error != null) return@withContext error
        // A new version after every change, so the old bitmap is never shown from the cache.
        val previous = Profiles.find(context, id)?.photo ?: 0L
        Profiles.setPhoto(context, id, maxOf(System.currentTimeMillis(), previous + 1))
        null
    }

    /** Removes [id]'s picture; the profile shows the default moon avatar again. */
    suspend fun remove(context: Context, id: String) = withContext(Dispatchers.IO) {
        file(context, id).delete()
        Profiles.setPhoto(context, id, 0L)
    }

    /** The picture for [profile] (off the main thread), or null for the default avatar. */
    suspend fun load(context: Context, profile: Profiles.Profile): Bitmap? {
        if (profile.photo <= 0L) return null
        val key = "${profile.id}@${profile.photo}"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val f = file(context, profile.id)
            if (!f.isFile) return@withContext null
            runCatching { android.graphics.BitmapFactory.decodeFile(f.path) }.getOrNull()?.also { cache.put(key, it) }
        }
    }

    /** Already decoded, so a list redraw does not flash the moon first. */
    fun cached(profile: Profiles.Profile): Bitmap? = if (profile.photo > 0L) cache.get("${profile.id}@${profile.photo}") else null
}
