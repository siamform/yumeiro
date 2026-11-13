/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/** Full-page background gradient (cached per palette, drawn once per frame — no allocation). */
@Composable
fun rememberPageBrush(): Brush {
    val p = LocalMtPalette.current
    return remember(p.bgTop, p.bgMid, p.bgBottom) {
        Brush.verticalGradient(0f to p.bgTop, 0.52f to p.bgMid, 1f to p.bgBottom)
    }
}

/**
 * Paints the themed page background ("Ambient" system), aligned to the window (not the
 * composable) so stacked pages / bars line up seamlessly:
 *  - a neutral sumi-ink vertical gradient (no brand hues on the page itself),
 *  - ONE soft ambient glow at the top taken from the artwork of what's playing
 *    ([LocalAmbientColor]; theme accent when nothing plays), echoed faintly lower down,
 *  - a few very faint moon-sky sparkles on dark themes.
 * Everything is built once per size/colour in [drawWithCache]; position is read in the draw phase.
 */
fun Modifier.pageBackground(): Modifier = composed {
    val p = LocalMtPalette.current
    val ambientState = LocalAmbientColor.current
    val view = LocalView.current
    val context = androidx.compose.ui.platform.LocalContext.current
    remember(context) { BackdropSettings.init(context) }
    val style by BackdropSettings.style.collectAsState()
    val intensity by BackdropSettings.intensity.collectAsState()
    val photo by rememberBackdropPhoto()
    val offsetY = remember { mutableFloatStateOf(0f) }
    this
        .onGloballyPositioned { offsetY.floatValue = it.positionInRoot().y }
        .drawWithCache {
            val top = offsetY.floatValue
            val ambient = ambientState?.value ?: p.accent
            val windowH = view.rootView.height.toFloat().takeIf { it > 0f } ?: size.height
            val w = size.width
            val brush = Brush.verticalGradient(
                0f to p.bgTop, 0.42f to p.bgMid, 1f to p.bgBottom,
                startY = -top, endY = windowH - top
            )
            val glowA = (if (p.isDark) 0.20f else 0.14f) * (if (style == BackdropStyle.CALM) 1f else intensity.coerceIn(0.5f, 1.3f))
            val halo = Brush.radialGradient(
                0f to ambient.copy(alpha = glowA), 0.55f to ambient.copy(alpha = glowA * 0.35f), 1f to Color.Transparent,
                center = Offset(w * 0.78f, -top - w * 0.10f), radius = w * 1.05f,
            )
            val echo = Brush.radialGradient(
                listOf(ambient.copy(alpha = glowA * 0.28f), Color.Transparent),
                center = Offset(w * 0.05f, -top + windowH * 0.55f), radius = w * 0.8f,
            )
            val img = photo
            val scene = if (style == BackdropStyle.SCENE || (style == BackdropStyle.PHOTO && img == null)) buildScene(p, w, windowH, intensity) else null
            // The production night sky stays under every dark scene; a photo replaces it.
            val sparkles = if (img == null && p.isDark) sparkleField(w, windowH * 0.45f) else emptyList()
            val starColor = Color.White
            onDrawBehind {
                drawRect(brush)
                if (img != null) drawBackdropPhoto(img, p, top, windowH, intensity)
                scene?.draw(this, top)
                drawRect(halo)
                drawRect(echo)
                sparkles.forEach { (c, r, a) ->
                    val cy = c.y - top
                    if (cy < -r || cy > size.height + r) return@forEach
                    val o = Offset(c.x, cy)
                    drawLine(starColor.copy(alpha = a), o.copy(x = o.x - r), o.copy(x = o.x + r), strokeWidth = r * 0.24f)
                    drawLine(starColor.copy(alpha = a), o.copy(y = o.y - r), o.copy(y = o.y + r), strokeWidth = r * 0.24f)
                }
            }
        }
}

/** Deterministic sparkle positions (same sky on every page → no flicker between screens). */
private fun sparkleField(w: Float, h: Float): List<Triple<Offset, Float, Float>> {
    val rnd = java.util.Random(0x5A4B)
    return List(9) {
        val r = 2.5f + rnd.nextFloat() * 4.5f
        Triple(Offset(rnd.nextFloat() * w, 40f + rnd.nextFloat() * h), r, 0.06f + rnd.nextFloat() * 0.12f)
    }
}

/** Accent → deeper accent signature gradient (single hue family) used for selected states, CTAs and progress. */
@Composable
fun rememberSignatureBrush(): Brush {
    val p = LocalMtPalette.current
    return remember(p.accent, p.accent2) { Brush.linearGradient(listOf(p.accent, p.accent2)) }
}

/** Thin gradient hairline border used on floating glass surfaces (nav bar, mini player). */
@Composable
fun rememberGlassBorder(): Brush {
    val p = LocalMtPalette.current
    return remember(p.isDark, p.accent, p.accent2) {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = if (p.isDark) 0.12f else 0.9f),
                Color.White.copy(alpha = if (p.isDark) 0.05f else 0.6f),
            )
        )
    }
}

/** Translucent "glass" surface used by cards, pills and round buttons (no blur → cheap). */
fun Modifier.glass(shape: Shape): Modifier = composed { clip(shape).background(LocalMtPalette.current.glass, shape) }

/** Artwork gradient pairs taken from the prototype; chosen deterministically per item. */
@Immutable
data class ArtGradient(val start: Color, val end: Color)

/** Muted, low-saturation placeholder tones (no rainbow) — real artwork is drawn on top. */
val ArtGradients = listOf(
    ArtGradient(Color(0xFF4A5470), Color(0xFF1B1E29)),
    ArtGradient(Color(0xFF6B5A44), Color(0xFF231D16)),
    ArtGradient(Color(0xFF46645C), Color(0xFF16211E)),
    ArtGradient(Color(0xFF5A5C66), Color(0xFF1C1D22)),
    ArtGradient(Color(0xFF6A4E4A), Color(0xFF231A18)),
    ArtGradient(Color(0xFF4A5E6E), Color(0xFF161E24)),
)

fun artGradientFor(key: Any?): ArtGradient {
    val h = (key?.hashCode() ?: 0)
    return ArtGradients[Math.floorMod(h, ArtGradients.size)]
}

/** Shared linear gradient brush for a key (cached). */
@Composable
fun rememberArtBrush(key: Any?): Brush = remember(key) {
    val g = artGradientFor(key)
    Brush.linearGradient(listOf(g.start, g.end))
}

/** Round 44dp glass container for top-bar style icon buttons. */
@Composable
fun GlassCircle(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.glass(CircleShape), contentAlignment = Alignment.Center, content = content)
}

val ChipSelectedColor: Color @Composable @ReadOnlyComposable get() = LocalMtPalette.current.chipSelected
val OnChipSelectedColor: Color @Composable @ReadOnlyComposable get() = LocalMtPalette.current.onChipSelected
