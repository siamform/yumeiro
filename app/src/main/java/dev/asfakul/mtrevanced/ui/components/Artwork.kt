/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.mtrevanced.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.asfakul.mtrevanced.ui.theme.rememberArtBrush

/** Whether music is currently playing (drives the animated "now playing" bars in lists). */
val LocalIsPlaying = compositionLocalOf { true }

/**
 * Prototype-style artwork: a deterministic gradient tile (with a soft note glyph) that the real
 * album art is drawn over. If the art is missing or fails, the gradient stays — never a blank box.
 */
@Composable
fun TrackArtwork(
    artUri: String?,
    key: Any?,
    size: Dp,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    icon: ImageVector = Icons.Default.MusicNote,
    vinyl: Boolean = false,
) {
    val brush = rememberArtBrush(key)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(brush)
            .then(if (vinyl) Modifier.drawBehind { drawVinylRings() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.size(size * 0.42f))
        if (!artUri.isNullOrBlank()) {
            AsyncImage(
                model = artUri,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** Concentric vinyl grooves from the prototype's large artwork placeholder. */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawVinylRings() {
    val c = center
    val r = size.minDimension / 2f
    val stroke = Stroke(width = 1.dp.toPx())
    listOf(0.82f, 0.64f, 0.46f).forEach { f ->
        drawCircle(Color.White.copy(alpha = 0.14f), radius = r * f, center = c, style = stroke)
    }
    drawCircle(Color.White.copy(alpha = 0.10f), radius = r * 0.16f, center = c)
}

/**
 * 3-bar "now playing" equalizer glyph. The bar heights animate in the draw phase only (no
 * recomposition per frame) and freeze when [playing] is false.
 */
@Composable
fun EqualizerBars(color: Color, modifier: Modifier = Modifier, playing: Boolean = true, barCount: Int = 3) {
    val phase: State<Float>? = if (playing) {
        rememberInfiniteTransition(label = "eq").animateFloat(
            0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "eqPhase"
        )
    } else null
    Box(
        modifier
            .size(width = (barCount * 4 + (barCount - 1) * 2).dp, height = 14.dp)
            .drawBehind {
                val w = 3.dp.toPx(); val gap = (size.width - w * barCount) / (barCount - 1).coerceAtLeast(1)
                val t = phase?.value ?: 0f
                for (i in 0 until barCount) {
                    val base = floatArrayOf(0.55f, 1f, 0.75f, 0.9f)[i % 4]
                    val wave = if (phase == null) 0.6f else
                        (0.35f + 0.65f * kotlin.math.abs(kotlin.math.sin((t + i * 0.27f) * Math.PI.toFloat() * 2f)))
                    val h = size.height * (base * wave).coerceIn(0.2f, 1f)
                    drawRoundRect(
                        color, topLeft = Offset(i * (w + gap), size.height - h), size = Size(w, h),
                        cornerRadius = CornerRadius(w / 2)
                    )
                }
            }
    )
}
