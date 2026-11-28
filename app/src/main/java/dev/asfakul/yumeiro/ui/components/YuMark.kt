/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * F4 (Beta chapter): Yumeiro's moon-and-play mark drawn from its own geometry (the same paths and
 * colours as `ic_launch_moon`), so it can move: the crescent is drawn in by a sweep around the moon,
 * the play triangle slides and grows into place, and a moonlight glow blooms behind them. The splash
 * and the loader both use it, so waiting anywhere looks like the app opening.
 *
 * Cost: two cached paths, one clip and two gradient fills per frame, all in the draw phase (values
 * come in as lambdas read only inside the Canvas), nothing allocated per frame except the sweep
 * wedge path, which is reused.
 */
object YuMarkGeometry {
    /** The drawable's 288-unit canvas and the group transform around the 108-unit mark. */
    const val CANVAS = 288f
    const val SCALE = 2.4f
    const val SHIFT = 14.4f
    const val MOON = "M81.37,66.29 A30,30 0 1 1 51.23,24.13 A26,26 0 0 0 81.37,66.29 Z"
    const val PLAY = "M61.5,36.5 L61.5,57.5 L78.5,47 Z"
    /** Centre and radius of the moon's outer circle, and the triangle's centroid (mark units). */
    val moonCentre = Offset(54f, 54f)
    const val MOON_R = 30f
    val playCentre = Offset((61.5f + 61.5f + 78.5f) / 3f, 47f)
    /** The sweep that draws the crescent starts at its lower tip and runs clockwise round the moon. */
    const val SWEEP_START = 35f

    val moonColors = listOf(Color(0xFFFFD58A), Color(0xFFF0B455), Color(0xFFD98330))
    val moonStops = floatArrayOf(0f, 0.55f, 1f)
    val playColors = listOf(Color(0xFFF5C46B), Color(0xFFD98330))
    val moonBrush: Brush by lazy { Brush.linearGradient(*moonStops.zip(moonColors).toTypedArray(), start = Offset(24f, 84f), end = Offset(84f, 24f)) }
    val playBrush: Brush by lazy { Brush.linearGradient(playColors, start = Offset(60f, 34f), end = Offset(80f, 58f)) }

    fun ease(t: Float): Float { val x = t.coerceIn(0f, 1f); return 1f - (1f - x) * (1f - x) * (1f - x) }
    /** 0 → 1 → 0 over [t] in 0..1 (for glows). */
    fun bell(t: Float): Float { val x = t.coerceIn(0f, 1f); return sin(x * PI.toFloat()) }
    /** Phase [t] mapped into [from]..[to] (clamped 0..1). */
    fun phase(t: Float, from: Float, to: Float): Float = ((t - from) / (to - from)).coerceIn(0f, 1f)
}

/**
 * One frame of the mark: how much of the crescent, the triangle and the glow shows (each 0..1).
 * B17: the two parts of the logo move on their own: [moonTurn] turns the crescent about its centre (degrees,
 * an orbit), [playDx] moves the triangle along its play axis (mark units), and [alpha] fades the whole mark.
 */
data class YuMarkFrame(
    val moon: Float,
    val play: Float,
    val glow: Float,
    val playScale: Float = 1f,
    val moonTurn: Float = 0f,
    val playDx: Float = 0f,
    val alpha: Float = 1f,
) {
    companion object {
        val Full = YuMarkFrame(1f, 1f, 0f)
        /** The splash draw-in at progress [t] 0..1. */
        /** Most the crescent turns while it orbits in (B17), in degrees. */
        const val ORBIT_DEG = 38f
        /** How far the triangle travels on its "play" push (B17), in mark units. */
        const val PUSH = 6f

        /**
         * The splash draw-in at progress [t] 0..1 (Android 7–11, no system splash icon): the crescent is drawn by a
         * sweep of moonlight while it orbits into place; the triangle slides in after it and settles with a push.
         */
        fun drawIn(t: Float): YuMarkFrame = with(YuMarkGeometry) {
            val m = phase(t, 0f, 0.6f)
            val p = phase(t, 0.35f, 0.8f)
            YuMarkFrame(
                moon = ease(phase(t, 0f, 0.55f)),
                play = ease(p),
                glow = bell(phase(t, 0.45f, 1f)),
                moonTurn = -ORBIT_DEG * (1f - ease(m)),
                playDx = PUSH * 0.6f * bell(phase(t, 0.6f, 0.95f)),
            )
        }

        /**
         * Android 12+: the system splash already showed the whole mark, so both parts start from rest: the crescent
         * swings back on its orbit and returns (a damped sway), while the triangle "presses play": it dips, pushes
         * forward along its axis and comes back. Different motion for each part; every value is 0 at t = 0 and t = 1.
         */
        fun press(t: Float): YuMarkFrame = with(YuMarkGeometry) {
            val sway = phase(t, 0.04f, 0.78f)
            val pr = phase(t, 0f, 0.5f)
            // The push comes after the sway's peak (13 % of the splash clock apart), so the parts clearly move on their own.
            val push = phase(t, 0.4f, 0.95f)
            YuMarkFrame(
                moon = 1f, play = 1f,
                glow = bell(phase(t, 0.15f, 1f)),
                playScale = 1f - 0.16f * bell(pr),
                moonTurn = -ORBIT_DEG * 0.5f * bell(sway) * (1f - 0.55f * sway),
                playDx = PUSH * bell(push),
            )
        }
    }
}

@Composable
fun YuMark(modifier: Modifier = Modifier, glowColor: Color = YuMarkGeometry.moonColors[1], frame: () -> YuMarkFrame) {
    val moon = remember { PathParser().parsePathString(YuMarkGeometry.MOON).toPath() }
    val play = remember { PathParser().parsePathString(YuMarkGeometry.PLAY).toPath() }
    val wedge = remember { Path() }
    // B17: built once per colour, not every frame (the splash and the loader animate at 60 fps).
    val glow = remember(glowColor) {
        Brush.radialGradient(listOf(glowColor.copy(alpha = 0.34f), Color.Transparent), center = YuMarkGeometry.moonCentre, radius = YuMarkGeometry.MOON_R * 1.9f)
    }
    Canvas(modifier) {
        val f = frame()
        drawMark(f, moon, play, wedge, glow)
    }
}

private fun DrawScope.drawMark(f: YuMarkFrame, moon: Path, play: Path, wedge: Path, glow: Brush) = with(YuMarkGeometry) {
    val k = size.minDimension / CANVAS
    val left = (size.width - CANVAS * k) / 2f
    val top = (size.height - CANVAS * k) / 2f
    withTransform({
        translate(left + SHIFT * k, top + SHIFT * k)
        scale(SCALE * k, SCALE * k, pivot = Offset.Zero)
    }) {
        if (f.glow > 0f) {
            drawCircle(glow, radius = MOON_R * 1.9f, center = moonCentre, alpha = (f.glow * f.alpha).coerceIn(0f, 1f))
        }
        if (f.moon > 0f && f.alpha > 0f) rotate(f.moonTurn, pivot = moonCentre) {
            val brush = moonBrush
            if (f.moon >= 1f) drawPath(moon, brush, alpha = f.alpha)
            else {
                wedge.reset()
                val r = MOON_R + 4f
                wedge.moveTo(moonCentre.x, moonCentre.y)
                wedge.arcTo(Rect(moonCentre, r), SWEEP_START, 360f * f.moon, forceMoveTo = false)
                wedge.close()
                clipPath(wedge) { drawPath(moon, brush, alpha = f.alpha) }
                // The tip of the sweep: a small bright point, like moonlight drawing the edge.
                val a = (SWEEP_START + 360f * f.moon) * PI.toFloat() / 180f
                val tip = Offset(moonCentre.x + cos(a) * (MOON_R - 2f), moonCentre.y + sin(a) * (MOON_R - 2f))
                drawCircle(moonColors[0].copy(alpha = 0.9f * (1f - f.moon) * f.alpha), radius = 2.2f, center = tip)
            }
        }
        if (f.play > 0f && f.alpha > 0f) {
            val s = (0.55f + 0.45f * f.play) * f.playScale
            val dx = (1f - f.play) * -8f + f.playDx
            withTransform({
                translate(dx, 0f)
                scale(s, s, pivot = playCentre)
            }) {
                val brush = playBrush
                drawPath(play, brush, alpha = f.play * f.alpha)
                drawPath(play, brush, alpha = f.play * f.alpha, style = Stroke(width = 5f, join = StrokeJoin.Round, cap = StrokeCap.Round))
            }
        }
    }
}
