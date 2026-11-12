/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.launch

/**
 * App-wide touch feedback ("soft press"). Replaces the default grey ripple, which on glass cards
 * spread as a square wash over the content. Pressing gently shrinks the element (3%) and lays a
 * faint highlight whose corners follow the element's shape, so it never bleeds past rounded edges.
 *
 * Keyboard / D-pad focus (Round 10): the old node ignored [FocusInteraction], so on a TV remote,
 * a Bluetooth keyboard or with Switch Access nothing showed which control was focused. A focused
 * element now gets an accent ring with a soft halo; on the TV layout it also lifts slightly
 * ([focusLift], e.g. 1.04) so the focus is readable from the sofa. Touch never produces focus on
 * clickables, so phones look exactly as before.
 */
class SoftPressIndication(
    private val tint: Color,
    private val focusColor: Color = Color.Unspecified,
    private val focusLift: Float = 1f,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = Node(interactionSource, tint, focusColor, focusLift)
    override fun equals(other: Any?) = other is SoftPressIndication && other.tint == tint && other.focusColor == focusColor && other.focusLift == focusLift
    override fun hashCode() = (tint.hashCode() * 31 + focusColor.hashCode()) * 31 + focusLift.hashCode()

    private class Node(
        private val source: InteractionSource,
        private val tint: Color,
        private val focusColor: Color,
        private val focusLift: Float,
    ) : Modifier.Node(), DrawModifierNode {
        private val progress = Animatable(0f)
        private val focus = Animatable(0f)

        override fun onAttach() {
            coroutineScope.launch {
                var pressed = 0
                var focused = 0
                source.interactions.collect { i ->
                    when (i) {
                        is PressInteraction.Press -> pressed++
                        is PressInteraction.Release, is PressInteraction.Cancel -> pressed = (pressed - 1).coerceAtLeast(0)
                        is FocusInteraction.Focus -> focused++
                        is FocusInteraction.Unfocus -> focused = (focused - 1).coerceAtLeast(0)
                    }
                    val target = if (pressed > 0) 1f else 0f
                    val focusTarget = if (focused > 0 && focusColor != Color.Unspecified) 1f else 0f
                    launch {
                        if (target == 1f) progress.animateTo(1f, tween(90)) else progress.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 500f))
                        invalidateDraw()
                    }
                    if (focus.targetValue != focusTarget) launch {
                        focus.animateTo(focusTarget, if (focusTarget == 1f) tween(140) else tween(110))
                        invalidateDraw()
                    }
                }
            }
        }

        override fun ContentDrawScope.draw() {
            val p = progress.value
            val f = focus.value
            if (p <= 0.001f && f <= 0.001f) { drawContent(); return }
            val k = (1f - 0.03f * p) * (1f + (focusLift - 1f) * f)
            scale(k) {
                this@draw.drawContent()
                val radius = minOf(size.minDimension / 2f, 20f * density)
                if (p > 0.001f) drawRoundRect(tint.copy(alpha = tint.alpha * p), cornerRadius = CornerRadius(radius, radius))
                if (f > 0.001f) {
                    // Halo first, then the crisp ring, both following the element's corners.
                    drawRoundRect(focusColor.copy(alpha = 0.16f * f), cornerRadius = CornerRadius(radius, radius))
                    val w = 2.5f * density
                    drawRoundRect(
                        focusColor.copy(alpha = f),
                        topLeft = androidx.compose.ui.geometry.Offset(w / 2f, w / 2f),
                        size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                        cornerRadius = CornerRadius((radius - w / 2f).coerceAtLeast(0f), (radius - w / 2f).coerceAtLeast(0f)),
                        style = Stroke(width = w),
                    )
                }
            }
            if (progress.isRunning || focus.isRunning) invalidateDraw()
        }
    }
}
