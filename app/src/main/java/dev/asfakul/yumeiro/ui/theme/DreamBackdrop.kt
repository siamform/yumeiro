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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 夢色 "dream colour" backdrops. Every theme paints its own scene behind every page, so each one
 * carries a different mood instead of being the same gradient in another colour:
 *
 *  - Tsuki Night: a crescent moon, a quiet star field and low mist.
 *  - Washi Day: warm paper fibres, a soft sun wash and a faint ensō brush circle.
 *  - Afterglow: an ember horizon with drifting sparks.
 *  - Time of day: sunrise mist (morning), clear sky and clouds (noon), golden light beams
 *    (afternoon), dusk with first stars (evening) and an aurora (midnight).
 *  - Material You: soft wallpaper-colour blooms.
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

private fun stars(rnd: java.util.Random, n: Int, w: Float, h: Float, maxR: Float, alpha: Float): List<Triple<Offset, Float, Float>> =
    List(n) { Triple(Offset(rnd.nextFloat() * w, rnd.nextFloat() * h), 0.6f + rnd.nextFloat() * maxR, alpha * (0.35f + rnd.nextFloat() * 0.65f)) }

/**
 * Builds the scene for [p] in window coordinates ([w] × [windowH]). Each op receives the page's
 * top offset in the window so stacked pages line up into one continuous picture.
 */
internal fun buildScene(p: MtPalette, w: Float, windowH: Float, k: Float): SceneLayer {
    val ops = mutableListOf<DrawScope.(Float) -> Unit>()
    val a = k.coerceIn(0.3f, 1.6f)
    val ink = p.textPrimary
    val rnd = java.util.Random(0x7E1AL + p.scene.ordinal)
    fun starField(n: Int, h: Float, maxR: Float, alpha: Float, tint: Color = Color.White) {
        val s = stars(rnd, n, w, h, maxR, alpha * a)
        ops += { top -> s.forEach { (c, r, al) -> drawCircle(tint.copy(alpha = al.coerceAtMost(1f)), r, Offset(c.x, c.y - top)) } }
    }
    fun glow(color: Color, cx: Float, cy: Float, radius: Float, alpha: Float) {
        val col = color.copy(alpha = (alpha * a).coerceAtMost(1f))
        ops += { top ->
            drawRect(Brush.radialGradient(0f to col, 0.6f to col.copy(alpha = col.alpha * 0.3f), 1f to Color.Transparent, center = Offset(cx, cy - top), radius = radius))
        }
    }
    fun band(color: Color, y0: Float, y1: Float, alpha: Float) {
        val col = color.copy(alpha = (alpha * a).coerceAtMost(1f))
        ops += { top -> drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.5f to col, 1f to Color.Transparent, startY = y0 - top, endY = y1 - top)) }
    }
    when (p.scene) {
        DreamScene.TSUKI, DreamScene.EVENING -> {
            val evening = p.scene == DreamScene.EVENING
            val moonC = Offset(w * 0.80f, windowH * 0.085f)
            val mr = w * 0.085f
            glow(p.accentBright, moonC.x, moonC.y, w * 0.55f, if (evening) 0.10f else 0.14f)
            // Crescent: the moon disc minus an offset disc (even-odd path, no offscreen layer).
            val crescent = Path().apply {
                fillType = PathFillType.EvenOdd
                addOval(androidx.compose.ui.geometry.Rect(moonC, mr))
                addOval(androidx.compose.ui.geometry.Rect(Offset(moonC.x + mr * 0.42f, moonC.y - mr * 0.22f), mr * 0.9f))
            }
            val moonCol = lerp(p.accentBright, Color.White, 0.55f).copy(alpha = (0.30f * a).coerceAtMost(0.85f))
            ops += { top -> translateY(-top) { drawPath(crescent, moonCol) } }
            starField(if (evening) 18 else 34, windowH * (if (evening) 0.35f else 0.55f), 1.6f, 0.30f)
            if (evening) {
                glow(p.accent2, w * 0.15f, windowH * 1.02f, w * 1.1f, 0.16f)
                band(p.accent, windowH * 0.78f, windowH * 1.05f, 0.06f)
            } else band(ink, windowH * 0.80f, windowH * 1.02f, 0.035f)
        }
        DreamScene.MIDNIGHT -> {
            starField(60, windowH * 0.7f, 1.5f, 0.38f)
            // Aurora: two soft ribbons waving across the upper sky.
            listOf(p.accent to 0.16f, p.accent2 to 0.10f).forEachIndexed { i, (col, al) ->
                val path = Path().apply {
                    val y = windowH * (0.10f + i * 0.07f)
                    moveTo(-w * 0.1f, y + w * 0.12f)
                    cubicTo(w * 0.25f, y - w * 0.10f, w * 0.55f, y + w * 0.22f, w * 1.1f, y - w * 0.02f)
                    lineTo(w * 1.1f, y + w * 0.16f)
                    cubicTo(w * 0.55f, y + w * 0.36f, w * 0.25f, y + w * 0.06f, -w * 0.1f, y + w * 0.30f)
                    close()
                }
                val c = col.copy(alpha = (al * a).coerceAtMost(0.6f))
                ops += { top -> translateY(-top) { drawPath(path, Brush.verticalGradient(listOf(Color.Transparent, c, Color.Transparent), startY = windowH * 0.02f, endY = windowH * 0.40f)) } }
            }
            band(Color(0xFF000000), windowH * 0.82f, windowH * 1.1f, 0.25f)
        }
        DreamScene.WASHI -> {
            glow(p.accentBright, w * 0.12f, windowH * 0.02f, w * 0.95f, 0.16f)
            // Ensō: one open brush circle, very faint.
            val ec = Offset(w * 0.84f, windowH * 0.30f); val er = w * 0.30f
            val enso = ink.copy(alpha = (0.045f * a).coerceAtMost(0.2f))
            ops += { top ->
                drawArc(enso, 200f, 300f, false, Offset(ec.x - er, ec.y - er - top), Size(er * 2, er * 2), style = Stroke(w * 0.022f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
            // Paper fibres.
            val fibres = List(70) {
                val x = rnd.nextFloat() * w; val y = rnd.nextFloat() * windowH
                val len = 8f + rnd.nextFloat() * 26f; val ang = rnd.nextFloat() * PI.toFloat()
                Triple(Offset(x, y), Offset(x + cos(ang) * len, y + sin(ang) * len), 0.02f + rnd.nextFloat() * 0.035f)
            }
            ops += { top -> fibres.forEach { (s, e, al) -> drawLine(ink.copy(alpha = (al * a).coerceAtMost(0.2f)), Offset(s.x, s.y - top), Offset(e.x, e.y - top), 1.1f) } }
        }
        DreamScene.AFTERGLOW -> {
            glow(p.accent, w * 0.5f, windowH * 0.02f, w * 1.15f, 0.20f)
            glow(p.accent2, w * 0.95f, windowH * 0.55f, w * 0.7f, 0.10f)
            band(p.accent, windowH * 0.20f, windowH * 0.34f, 0.05f)
            val embers = List(26) { Triple(Offset(rnd.nextFloat() * w, windowH * (0.15f + rnd.nextFloat() * 0.8f)), 1.2f + rnd.nextFloat() * 2.6f, 0.10f + rnd.nextFloat() * 0.25f) }
            ops += { top -> embers.forEach { (c, r, al) -> drawCircle(p.accentBright.copy(alpha = (al * a).coerceAtMost(0.9f)), r, Offset(c.x, c.y - top)) } }
        }
        DreamScene.MORNING -> {
            glow(p.accentBright, w * 0.18f, windowH * 0.06f, w * 0.85f, 0.26f)
            glow(p.accent2, w * 0.95f, windowH * 0.18f, w * 0.6f, 0.12f)
            band(Color.White, windowH * 0.22f, windowH * 0.34f, 0.55f)
            band(Color.White, windowH * 0.52f, windowH * 0.62f, 0.40f)
        }
        DreamScene.NOON -> {
            glow(p.accentBright, w * 0.85f, windowH * 0.0f, w * 0.9f, 0.20f)
            // Soft clouds: clusters of white ovals.
            val clouds = List(4) { i ->
                val cx = w * (0.1f + 0.28f * i + rnd.nextFloat() * 0.08f); val cy = windowH * (0.07f + rnd.nextFloat() * 0.18f)
                val s = w * (0.07f + rnd.nextFloat() * 0.05f)
                listOf(Offset(cx, cy) to s, Offset(cx + s * 0.9f, cy - s * 0.3f) to s * 0.85f, Offset(cx + s * 1.7f, cy) to s * 0.7f)
            }.flatten()
            val cloudCol = Color.White.copy(alpha = (0.55f * a).coerceAtMost(0.95f))
            ops += { top -> clouds.forEach { (c, r) -> drawCircle(cloudCol, r, Offset(c.x, c.y - top)) } }
        }
        DreamScene.AFTERNOON -> {
            glow(p.accentBright, w * 1.0f, windowH * 0.0f, w * 1.1f, 0.24f)
            // Golden beams falling from the top-right corner.
            listOf(0.20f, 0.42f, 0.66f).forEachIndexed { i, f ->
                val path = Path().apply {
                    moveTo(w * 1.05f, -windowH * 0.02f)
                    lineTo(w * (f - 0.08f), windowH * 0.75f)
                    lineTo(w * (f + 0.06f), windowH * 0.75f)
                    close()
                }
                val c = p.accentBright.copy(alpha = ((0.07f - i * 0.015f) * a).coerceAtMost(0.4f))
                ops += { top -> translateY(-top) { drawPath(path, Brush.verticalGradient(listOf(c, Color.Transparent), startY = 0f, endY = windowH * 0.75f)) } }
            }
        }
        DreamScene.YOU -> {
            glow(p.accent, w * 0.05f, windowH * 0.05f, w * 0.9f, if (p.isDark) 0.18f else 0.16f)
            glow(p.accent2, w * 1.0f, windowH * 0.45f, w * 0.8f, 0.12f)
            glow(p.accentBright, w * 0.2f, windowH * 0.95f, w * 0.8f, 0.08f)
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
