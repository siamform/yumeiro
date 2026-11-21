/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * GATE-1-RENDER: compiles the GPU programs the app's first screens need while nothing else is drawing.
 *
 * Gate trace 44949137965 (api33-lowram): the main thread waited 616 ms (`postAndWait`) when the Source
 * menu opened and 472 ms when Yumix drew, because the RenderThread was compiling GPU shaders for draw
 * types it met for the first time (Skia `shader_compile` / `cache_miss` for FillRectOp, FillRRectOp and
 * CircleOp, 100–230 ms each) and the first poster uploads set up the bitmap upload thread
 * (`uploadHardwareBitmap` 343 ms). The shader cache starts empty after an install or update.
 *
 * A short while after start (no input expected yet), a tiny canvas under the app draws one group of
 * those primitives per frame (circles filled / stroked / arcs, rects solid and gradient, rounded rects,
 * a rounded clip, a small image) and then leaves; a tiny bitmap is copied to a hardware bitmap on a
 * background thread first. Each compile then happens alone in an idle frame instead of inside the
 * first tap. The canvas is 8 dp, drawn first (every page paints its own background over it) and has
 * no semantics. [enabled] is fixed for the life of the call site (flag SHADER_PREWARM).
 */
object ShaderPrewarm {
    const val STEPS = 7
    const val START_DELAY_MS = 2_500L
    const val GAP_MS = 120L
    const val TAG = "shader_prewarm"

    /** Steps drawn so far in this process (tests and logs). */
    @Volatile var stepsDrawn = 0
        internal set

    /** Creates the bitmap upload thread's GPU context now (API 26+). Background thread only. */
    fun warmUploader() {
        if (Build.VERSION.SDK_INT < 26) return
        runCatching {
            val src = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
            src.copy(Bitmap.Config.HARDWARE, false)?.recycle()
            src.recycle()
        }
    }

    internal fun DrawScope.drawStep(step: Int, image: ImageBitmap?) {
        val c = Color(0x08FFFFFF) // almost transparent: the colour is a uniform, the GPU program is the same
        val w = size.width; val h = size.height
        when (step) {
            0 -> { drawCircle(c, w / 2f); drawCircle(c, w / 3f, style = Stroke(w / 10f)) }
            1 -> drawArc(c, -90f, 270f, useCenter = false, style = Stroke(w / 8f, cap = StrokeCap.Round))
            2 -> { drawRect(c); drawRect(c, Offset(w / 4f, h / 4f), Size(w / 3f, h / 3f)) }
            3 -> {
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, c)))
                drawRect(Brush.horizontalGradient(listOf(c, Color.Transparent)))
                drawCircle(Brush.radialGradient(listOf(c, Color.Transparent)))
            }
            4 -> {
                drawRoundRect(c, cornerRadius = CornerRadius(w / 4f))
                drawRoundRect(c, cornerRadius = CornerRadius(w / 6f), style = Stroke(w / 12f))
                drawRoundRect(Brush.verticalGradient(listOf(Color.Transparent, c)), cornerRadius = CornerRadius(w / 4f))
            }
            5 -> clipPath(Path().apply { addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(w / 4f))) }) { drawRect(c) }
            6 -> image?.let { drawImage(it, alpha = 0.03f) }
        }
    }
}

@Composable
fun ShaderPrewarmHost(enabled: Boolean, startDelayMs: Long = ShaderPrewarm.START_DELAY_MS) {
    if (!enabled) return
    var step by remember { mutableIntStateOf(-1) }
    val image = remember { runCatching { Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).asImageBitmap() }.getOrNull() }
    LaunchedEffect(Unit) {
        delay(startDelayMs)
        withContext(Dispatchers.IO) { ShaderPrewarm.warmUploader() }
        for (i in 0 until ShaderPrewarm.STEPS) {
            step = i
            withFrameNanos { }
            withFrameNanos { }
            ShaderPrewarm.stepsDrawn = i + 1
            delay(ShaderPrewarm.GAP_MS)
        }
        step = ShaderPrewarm.STEPS
        android.util.Log.i("YuGate", "shader prewarm done (${ShaderPrewarm.STEPS} steps)")
    }
    if (step in 0 until ShaderPrewarm.STEPS) {
        val s = step
        Canvas(Modifier.size(8.dp).testTag(ShaderPrewarm.TAG)) { with(ShaderPrewarm) { drawStep(s, image) } }
    }
}
