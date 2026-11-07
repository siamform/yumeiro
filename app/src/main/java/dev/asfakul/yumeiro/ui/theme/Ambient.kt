/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * "Ambient" background system.
 *
 * Instead of painting fixed brand hues behind every page, Yumeiro takes ONE colour from what you
 * are listening to / watching (content-based colour, like Spotify album headers and Apple Music's
 * Now Playing) and lets it glow softly behind neutral sumi-ink surfaces. With nothing playing —
 * or for grey artwork — the glow falls back to the theme accent (moonlight gold).
 *
 * The colour is clamped (saturation 0.30–0.70, value 0.62–0.85) so it never turns neon and text
 * contrast on top of it stays stable.
 */
object AmbientColor {
    private val cache = LruCache<String, Int>(96)
    private const val NONE = 0

    /** Extracts the ambient colour of an artwork URI (cached). Null when the art is grey / missing. */
    suspend fun extract(context: Context, uri: String?): Color? {
        if (uri.isNullOrBlank()) return null
        cache.get(uri)?.let { return if (it == NONE) null else Color(it) }
        val result = runCatching {
            val request = ImageRequest.Builder(context).data(uri).size(32).allowHardware(false).build()
            val drawable = (context.imageLoader.execute(request) as? SuccessResult)?.drawable ?: return@runCatching null
            val bmp = (drawable as? BitmapDrawable)?.bitmap ?: drawable.toBitmap(32, 32, Bitmap.Config.ARGB_8888)
            withContext(Dispatchers.Default) { fromBitmap(bmp) }
        }.getOrNull()
        cache.put(uri, result?.let { android.graphics.Color.argb(255, (it.red * 255).toInt(), (it.green * 255).toInt(), (it.blue * 255).toInt()) } ?: NONE)
        return result
    }

    /** Saturation-weighted circular hue mean of a small bitmap → one clamped, tasteful colour. */
    fun fromBitmap(bmp: Bitmap): Color? {
        val w = bmp.width.coerceAtMost(48)
        val h = bmp.height.coerceAtMost(48)
        if (w <= 0 || h <= 0) return null
        val px = IntArray(w * h)
        val scaled = if (bmp.width == w && bmp.height == h) bmp else Bitmap.createScaledBitmap(bmp, w, h, true)
        scaled.getPixels(px, 0, w, 0, 0, w, h)
        val hsv = FloatArray(3)
        var x = 0.0; var y = 0.0; var weight = 0.0; var sat = 0.0
        for (c in px) {
            android.graphics.Color.colorToHSV(c, hsv)
            val v = hsv[2]
            if (v < 0.18f || v > 0.97f) continue
            val wgt = (hsv[1] * hsv[1]) * (1.0 - kotlin.math.abs(v - 0.6) )
            if (wgt <= 0.0) continue
            val rad = Math.toRadians(hsv[0].toDouble())
            x += cos(rad) * wgt; y += sin(rad) * wgt
            weight += wgt; sat += hsv[1] * wgt
        }
        if (weight < px.size * 0.02) return null // essentially greyscale art
        val strength = sqrt(x * x + y * y) / weight
        if (strength < 0.25) return null // hues cancel out (rainbow art) → use the accent
        var hue = Math.toDegrees(atan2(y, x)).toFloat()
        if (hue < 0f) hue += 360f
        val s = (sat / weight).toFloat().coerceIn(0.30f, 0.70f)
        return Color.hsv(hue, s, 0.78f)
    }
}

/** The page glow colour for the current content (animated). */
val LocalAmbientColor = staticCompositionLocalOf<State<Color>?> { null }

/**
 * Resolves and animates the ambient colour for [artUri]. Falls back to the palette accent.
 * Respects the "Ambient colour" appearance toggle.
 */
@Composable
fun rememberAmbientColor(artUri: String?): State<Color> {
    val context = LocalContext.current
    val palette = LocalMtPalette.current
    val enabled by ThemeSettings.ambient.collectAsStateWithLifecycle()
    val target by produceState<Color?>(null, artUri, enabled) {
        value = if (enabled) AmbientColor.extract(context.applicationContext, artUri) else null
    }
    return animateColorAsState(target ?: palette.accent, tween(900), label = "ambient")
}
