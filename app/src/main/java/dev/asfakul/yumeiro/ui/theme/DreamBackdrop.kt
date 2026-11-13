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
 * 夢色 "dream colour" backdrops. Every theme paints its own scene behind every page, so each one
 * carries a different mood instead of being the same gradient in another colour:
 *
 *  All themes share the production night-sky base (ink gradient, ambient halo, faint sparkles
 *  on dark themes) and add one quiet detail each:
 *  - Tsuki Night: low mist. Evening: a warm dusk line. Midnight: one faint ribbon.
 *  - Afterglow: a few embers. Washi Day: a faint ensō circle. Morning: a mist band.
 *  - Noon: two small clouds. Afternoon: one golden beam. Material You: a second bloom.
 *
 * The user can switch to their own photo (softly blurred and dimmed so text keeps its contrast)
 * or a calm plain gradient, and set the intensity. Scenes are vector shapes built once per size in
 * `drawWithCache` (see [pageBackground]); nothing animates, so it costs no battery.
 */
enum class DreamScene { TSUKI, WASHI, AFTERGLOW, MORNING, NOON, AFTERNOON, EVENING, MIDNIGHT, YOU }

enum class BackdropStyle(val label: String, val detail: String) {
    SCENE("Dream scene", "Each theme paints its own scene"),
    PHOTO("Your photo", "A picture of yours, softly blurred"),
    CALM("Calm", "Just the theme gradient"),
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

    /**
     * Copies the picked image into app storage, downscaled (the backdrop is drawn blurred, so
     * 540 px is plenty and keeps memory tiny). Returns false when the image can't be read.
     */
    suspend fun importPhoto(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val cr = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return@runCatching false
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching false
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 540 && bounds.outHeight / (sample * 2) >= 540) sample *= 2
            val bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
                ?: return@runCatching false
            val tmp = File(context.filesDir, "$FILE.tmp")
            tmp.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }
            bmp.recycle()
            if (!tmp.renameTo(photoFile(context))) { tmp.copyTo(photoFile(context), overwrite = true); tmp.delete() }
            val v = System.currentTimeMillis()
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_PHOTO_V, v).apply()
            _photoVersion.value = v
            setStyle(context, BackdropStyle.PHOTO)
            true
        }.getOrDefault(false)
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
    return produceState<ImageBitmap?>(null, version, style) {
        value = if (version == 0L || style != BackdropStyle.PHOTO) null else withContext(Dispatchers.IO) {
            runCatching {
                val f = BackdropSettings.photoFile(context)
                BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = 8 })?.asImageBitmap()
            }.getOrNull()
        }
    }
}

/** One prepared scene: shapes are built once; [draw] only issues draw calls. */
internal class SceneLayer(private val ops: List<DrawScope.(Float) -> Unit>) {
    fun draw(scope: DrawScope, top: Float) { ops.forEach { scope.it(top) } }
}

/**
 * Builds the quiet detail for [p] in window coordinates ([w] × [windowH]). Each op receives the
 * page's top offset in the window so stacked pages line up into one continuous picture.
 *
 * Round 11: the scene no longer replaces the page. Every theme keeps the production night-sky
 * base drawn by [pageBackground] (ink gradient, one ambient halo, its echo and the nine faint
 * sparkles on dark themes) and adds exactly one quiet detail of its own. No moon, no big shapes:
 * the base is what users liked, the detail only tells the themes apart.
 */
internal fun buildScene(p: MtPalette, w: Float, windowH: Float, k: Float): SceneLayer {
    val ops = mutableListOf<DrawScope.(Float) -> Unit>()
    val a = k.coerceIn(0.3f, 1.6f)
    val ink = p.textPrimary
    val rnd = java.util.Random(0x7E1AL + p.scene.ordinal)
    fun dust(n: Int, y0: Float, y1: Float, maxR: Float, alpha: Float, tint: Color) {
        val s = List(n) { Triple(Offset(rnd.nextFloat() * w, y0 + rnd.nextFloat() * (y1 - y0)), 0.5f + rnd.nextFloat() * maxR, alpha * (0.35f + rnd.nextFloat() * 0.65f)) }
        ops += { top -> s.forEach { (c, r, al) -> drawCircle(tint.copy(alpha = (al * a).coerceAtMost(0.6f)), r, Offset(c.x, c.y - top)) } }
    }
    fun band(color: Color, y0: Float, y1: Float, alpha: Float) {
        val col = color.copy(alpha = (alpha * a).coerceAtMost(0.5f))
        ops += { top -> drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.5f to col, 1f to Color.Transparent, startY = y0 - top, endY = y1 - top)) }
    }
    when (p.scene) {
        // Tsuki Night: low mist along the bottom of the sky.
        DreamScene.TSUKI -> band(ink, windowH * 0.80f, windowH * 1.04f, 0.030f)
        // Evening: a warm dusk line on the horizon.
        DreamScene.EVENING -> band(p.accent2, windowH * 0.84f, windowH * 1.06f, 0.07f)
        // Midnight: one thin, very faint ribbon across the upper sky.
        DreamScene.MIDNIGHT -> {
            val y = windowH * 0.16f
            val path = Path().apply {
                moveTo(-w * 0.1f, y + w * 0.10f)
                cubicTo(w * 0.3f, y - w * 0.06f, w * 0.6f, y + w * 0.16f, w * 1.1f, y)
                lineTo(w * 1.1f, y + w * 0.06f)
                cubicTo(w * 0.6f, y + w * 0.22f, w * 0.3f, y, -w * 0.1f, y + w * 0.16f)
                close()
            }
            val c = p.accent.copy(alpha = (0.07f * a).coerceAtMost(0.25f))
            ops += { top -> translateY(-top) { drawPath(path, c) } }
        }
        // Afterglow: a few embers rising near the bottom.
        DreamScene.AFTERGLOW -> dust(12, windowH * 0.62f, windowH * 0.98f, 1.6f, 0.22f, p.accentBright)
        // Washi Day: a faint open ensō brush circle.
        DreamScene.WASHI -> {
            val ec = Offset(w * 0.84f, windowH * 0.30f); val er = w * 0.26f
            val enso = ink.copy(alpha = (0.035f * a).coerceAtMost(0.15f))
            ops += { top ->
                drawArc(enso, 200f, 300f, false, Offset(ec.x - er, ec.y - er - top), Size(er * 2, er * 2), style = Stroke(w * 0.018f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
        }
        // Morning: one soft mist band.
        DreamScene.MORNING -> band(Color.White, windowH * 0.26f, windowH * 0.38f, 0.40f)
        // Noon: two small, faint clouds.
        DreamScene.NOON -> {
            val clouds = List(2) { i ->
                val cx = w * (0.14f + 0.50f * i); val cy = windowH * (0.08f + 0.06f * i)
                val s = w * 0.05f
                listOf(Offset(cx, cy) to s, Offset(cx + s * 0.9f, cy - s * 0.3f) to s * 0.85f, Offset(cx + s * 1.7f, cy) to s * 0.7f)
            }.flatten()
            val cloudCol = Color.White.copy(alpha = (0.35f * a).coerceAtMost(0.7f))
            ops += { top -> clouds.forEach { (c, r) -> drawCircle(cloudCol, r, Offset(c.x, c.y - top)) } }
        }
        // Afternoon: a single golden beam from the top-right corner.
        DreamScene.AFTERNOON -> {
            val path = Path().apply {
                moveTo(w * 1.05f, -windowH * 0.02f); lineTo(w * 0.34f, windowH * 0.70f); lineTo(w * 0.48f, windowH * 0.70f); close()
            }
            val c = p.accentBright.copy(alpha = (0.05f * a).coerceAtMost(0.3f))
            ops += { top -> translateY(-top) { drawPath(path, Brush.verticalGradient(listOf(c, Color.Transparent), startY = 0f, endY = windowH * 0.70f)) } }
        }
        // Material You: a second wallpaper-colour bloom low on the page.
        DreamScene.YOU -> {
            val col = p.accent2.copy(alpha = (0.10f * a).coerceAtMost(0.4f))
            ops += { top -> drawRect(Brush.radialGradient(0f to col, 1f to Color.Transparent, center = Offset(w * 1.0f, windowH * 0.70f - top), radius = w * 0.8f)) }
        }
    }
    return SceneLayer(ops)
}

private inline fun DrawScope.translateY(dy: Float, block: DrawScope.() -> Unit) {
    drawContext.transform.translate(0f, dy)
    try { block() } finally { drawContext.transform.translate(0f, -dy) }
}

/** Draws the user's photo cover-cropped over the window, then a theme scrim for contrast. */
internal fun DrawScope.drawBackdropPhoto(img: ImageBitmap, p: MtPalette, top: Float, windowH: Float, k: Float) {
    val w = size.width
    val scale = maxOf(w / img.width, windowH / img.height)
    val dw = img.width * scale; val dh = img.height * scale
    val dx = (w - dw) / 2f; val dy = (windowH - dh) / 2f - top
    drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset(dx.toInt(), dy.toInt()), IntSize(dw.toInt() + 1, dh.toInt() + 1),
        filterQuality = androidx.compose.ui.graphics.FilterQuality.High)
    // Stronger scrim at lower intensity; text tiers keep AA contrast at every setting.
    val base = if (p.isDark) 0.80f else 0.82f
    val scrim = (base - (k - 1f) * 0.18f).coerceIn(0.62f, 0.92f)
    drawRect(Brush.verticalGradient(0f to p.bgTop.copy(alpha = scrim - 0.08f), 0.45f to p.bgMid.copy(alpha = scrim), 1f to p.bgBottom.copy(alpha = scrim + 0.04f), startY = -top, endY = windowH - top))
}
