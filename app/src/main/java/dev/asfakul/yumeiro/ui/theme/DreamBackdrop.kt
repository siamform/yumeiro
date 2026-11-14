/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.theme

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Page backdrop choice. The default is the original Yumeiro background (theme gradient, ambient
 * halo and echo, faint sparkles on dark themes). The user can switch to their own photo, softly
 * blurred and dimmed so text keeps its contrast. [DreamScene] only names the theme now; no theme
 * draws extra shapes.
 */
enum class DreamScene { TSUKI, WASHI, AFTERGLOW, MORNING, NOON, AFTERNOON, EVENING, MIDNIGHT, YOU }

enum class BackdropStyle(val label: String, val detail: String) {
    SCENE("Yumeiro", "The original gradient and glow"),
    PHOTO("Your photo", "A picture of yours, softly blurred"),
    /** Older saved value; treated as [SCENE]. Not shown in settings. */
    CALM("Yumeiro", "The original gradient and glow"),
}

/** Persisted backdrop choice (shares the appearance preferences file). */
object BackdropSettings {
    private const val PREFS = "mt_appearance"
    private const val KEY_STYLE = "backdrop_style"
    private const val KEY_INTENSITY = "backdrop_intensity"
    private const val KEY_PHOTO_V = "backdrop_photo_version"
    private const val FILE = "backdrop_photo.jpg"

    private val _style = MutableStateFlow(BackdropStyle.SCENE)
    val style: StateFlow<BackdropStyle> = _style.asStateFlow()
    private val _intensity = MutableStateFlow(1f)
    /** 0.3 (faint) … 1.6 (vivid). */
    val intensity: StateFlow<Float> = _intensity.asStateFlow()
    private val _photoVersion = MutableStateFlow(0L)
    /** Changes whenever the photo file is replaced or removed (0 = no photo). */
    val photoVersion: StateFlow<Long> = _photoVersion.asStateFlow()
    @Volatile private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _style.value = runCatching { BackdropStyle.valueOf(p.getString(KEY_STYLE, BackdropStyle.SCENE.name)!!) }.getOrDefault(BackdropStyle.SCENE)
        _intensity.value = p.getFloat(KEY_INTENSITY, 1f).coerceIn(0.3f, 1.6f)
        _photoVersion.value = if (photoFile(context).exists()) p.getLong(KEY_PHOTO_V, 1L).coerceAtLeast(1L) else 0L
        if (_style.value == BackdropStyle.PHOTO && _photoVersion.value == 0L) _style.value = BackdropStyle.SCENE
        if (_style.value == BackdropStyle.CALM) _style.value = BackdropStyle.SCENE
        loaded = true
    }

    fun setStyle(context: Context, style: BackdropStyle) {
        _style.value = style
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_STYLE, style.name).apply()
    }

    fun setIntensity(context: Context, value: Float) {
        val v = value.coerceIn(0.3f, 1.6f)
        _intensity.value = v
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putFloat(KEY_INTENSITY, v).apply()
    }

    internal fun photoFile(context: Context) = File(context.filesDir, FILE)

    /** Shown under the picker: what the import accepts. */
    const val ACCEPTED = "JPEG, PNG, WebP, GIF (first frame), BMP, HEIC/HEIF (Android 9+) and AVIF (Android 12+). Up to 60 MB and at least 200 × 200 px; larger pictures are scaled down."

    /**
     * Round 2, order 8: copies the picked image into app storage, downscaled (the backdrop is
     * drawn blurred, so 540 px is plenty). Returns null when it worked, else the reason to show.
     *
     * Root cause of the "picture couldn't be opened" toast (since the R1-R11b redesign, PR #27):
     * the size probe was `openInputStream(uri)?.use { decodeStream(.., inJustDecodeBounds) } ?: fail`,
     * and decodeStream always returns null in bounds-only mode, so every picture was refused.
     * The old import also decoded with BitmapFactory only and reported every problem the same way: HEIC/AVIF photos on phones whose BitmapFactory lacks them, cloud items that need a
     * moment to download, and any failure after the file was already saved all ended the same way.
     * Now Android 9+ decodes with ImageDecoder (HEIF, AVIF, WebP, EXIF turns applied, software
     * bitmap so it can be scaled and saved), older phones with BitmapFactory, and each failure
     * names its cause and lands in Crash reports.
     */
    suspend fun importPhoto(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        val type = runCatching { cr.getType(uri) }.getOrNull().orEmpty()
        val size = runCatching { cr.openAssetFileDescriptor(uri, "r")?.use { it.length } }.getOrNull() ?: -1L
        if (size > 60L * 1024 * 1024) return@withContext "That picture is over 60 MB. Pick a smaller one."
        if (type.startsWith("video/")) return@withContext "That's a video. Pick a photo instead."
        val result = runCatching {
            val bmp = decodeScaled(context, uri, 540) ?: return@runCatching "This picture's format isn't supported here ($type). Try a JPEG or PNG."
            if (bmp.width < 200 && bmp.height < 200) { bmp.recycle(); return@runCatching "That picture is too small (${bmp.width} × ${bmp.height}). Use one at least 200 × 200 px." }
            val tmp = File(context.filesDir, "$FILE.tmp")
            tmp.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }
            bmp.recycle()
            if (!tmp.renameTo(photoFile(context))) { tmp.copyTo(photoFile(context), overwrite = true); tmp.delete() }
            null
        }.getOrElse { e ->
            dev.asfakul.yumeiro.diagnostics.CrashLog.nonFatal("Backdrop photo import ($type, $size bytes)", e)
            when (e) {
                is java.io.FileNotFoundException, is SecurityException -> "Yumeiro couldn't read that picture. If it's only in the cloud, download it to the phone first."
                is OutOfMemoryError -> "That picture is too large to open on this phone. Pick a smaller one."
                else -> "That picture couldn't be opened (${e.javaClass.simpleName}). Try a JPEG or PNG."
            }
        }
        if (result != null) return@withContext result
        val v = System.currentTimeMillis()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_PHOTO_V, v).apply()
        _photoVersion.value = v
        setStyle(context, BackdropStyle.PHOTO)
        null
    }

    /** Decodes [uri] with its shorter side near [target] px, upright, as a software bitmap. */
    internal fun decodeScaled(context: Context, uri: Uri, target: Int): android.graphics.Bitmap? {
        val cr = context.contentResolver
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            val viaDecoder = runCatching {
                android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(cr, uri)) { d, info, _ ->
                    d.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                    d.isMutableRequired = false
                    val w = info.size.width; val h = info.size.height
                    val short = minOf(w, h)
                    if (short > target * 2) { val f = target.toFloat() / short; d.setTargetSize((w * f).toInt().coerceAtLeast(1), (h * f).toInt().coerceAtLeast(1)) }
                }
            }
            viaDecoder.getOrNull()?.let { return it }
            // ImageDecoder refused (rare formats): fall through to BitmapFactory.
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // decodeStream returns null with inJustDecodeBounds by design; only a missing stream is an error
        // (the 1.8.0 import treated that null as failure, so every picture was refused).
        val input = cr.openInputStream(uri) ?: throw java.io.FileNotFoundException(uri.toString())
        input.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) sample *= 2
        val raw = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        // Camera photos are stored sideways with an EXIF turn; apply it so the backdrop isn't rotated.
        val turn = runCatching {
            cr.openInputStream(uri)?.use { android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1) }
        }.getOrNull()?.let { when (it) { 6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f } } ?: 0f
        return if (turn == 0f) raw else android.graphics.Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height,
            android.graphics.Matrix().apply { postRotate(turn) }, true).also { if (it !== raw) raw.recycle() }
    }

    fun removePhoto(context: Context) {
        photoFile(context).delete()
        _photoVersion.value = 0L
        if (_style.value == BackdropStyle.PHOTO) setStyle(context, BackdropStyle.SCENE)
    }
}

/** The photo as a very small bitmap: drawn scaled up it reads as a soft blur on every API level. */
@Composable
internal fun rememberBackdropPhoto(): State<ImageBitmap?> {
    val context = LocalContext.current
    BackdropSettings.init(context)
    val version by BackdropSettings.photoVersion.collectAsState()
    val style by BackdropSettings.style.collectAsState()
    return produceState(PhotoCache.peek(version, style), version, style) {
        value = PhotoCache.get(context, version, style)
    }
}

/**
 * Round 13: the photo backdrop looked like it did nothing. It was decoded at 1/8 of an already
 * small copy (about 67 px wide) under an 80% scrim, so only a faint tint showed, and every page
 * decoded it again. Now one decoded copy (half size: still soft, but clearly the photo) is shared
 * by every page in the app.
 */
private object PhotoCache {
    @Volatile private var key: Long = 0L
    @Volatile private var img: ImageBitmap? = null

    fun peek(version: Long, style: BackdropStyle): ImageBitmap? = if (style == BackdropStyle.PHOTO && version != 0L && version == key) img else null

    suspend fun get(context: Context, version: Long, style: BackdropStyle): ImageBitmap? {
        if (version == 0L || style != BackdropStyle.PHOTO) return null
        peek(version, style)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                BitmapFactory.decodeFile(BackdropSettings.photoFile(context).path, BitmapFactory.Options().apply { inSampleSize = 2 })?.asImageBitmap()
            }.getOrNull()?.also { key = version; img = it }
        }
    }
}

/** One prepared scene: shapes are built once; [draw] only issues draw calls. */
internal class SceneLayer(private val ops: List<DrawScope.(Float) -> Unit>) {
    fun draw(scope: DrawScope, top: Float) { ops.forEach { scope.it(top) } }
}

/**
 * Kept for the settings preview and older callers. Round 11c: no scene shapes at all (no moon,
 * sun, clouds, rings or beams). Every theme uses the original Yumeiro background drawn by
 * [pageBackground]: the theme gradient, the ambient halo and its echo, and faint sparkles on
 * dark themes, exactly as on main.
 */
@Suppress("UNUSED_PARAMETER")
internal fun buildScene(p: MtPalette, w: Float, windowH: Float, k: Float): SceneLayer = SceneLayer(emptyList())

/** Draws the user's photo cover-cropped over the window, then a theme scrim for contrast. */
internal fun DrawScope.drawBackdropPhoto(img: ImageBitmap, p: MtPalette, top: Float, windowH: Float, k: Float) {
    val w = size.width
    val scale = maxOf(w / img.width, windowH / img.height)
    val dw = img.width * scale; val dh = img.height * scale
    val dx = (w - dw) / 2f; val dy = (windowH - dh) / 2f - top
    drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset(dx.toInt(), dy.toInt()), IntSize(dw.toInt() + 1, dh.toInt() + 1),
        filterQuality = androidx.compose.ui.graphics.FilterQuality.High)
    // Stronger scrim at lower intensity; text tiers keep AA contrast at every setting.
    // Round 13: lighter scrim so the photo is really seen across the app; cards and text sit on
    // glass surfaces that keep their own contrast.
    val base = if (p.isDark) 0.60f else 0.66f
    val scrim = (base - (k - 1f) * 0.18f).coerceIn(0.42f, 0.84f)
    drawRect(Brush.verticalGradient(0f to p.bgTop.copy(alpha = scrim - 0.08f), 0.45f to p.bgMid.copy(alpha = scrim), 1f to p.bgBottom.copy(alpha = scrim + 0.04f), startY = -top, endY = windowH - top))
}
