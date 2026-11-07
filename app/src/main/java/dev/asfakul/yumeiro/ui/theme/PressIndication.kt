/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.launch

/**
 * App-wide touch feedback ("soft press"). Replaces the default grey ripple, which on glass cards
 * spread as a square wash over the content. Pressing gently shrinks the element (3%) and lays a
 * faint highlight whose corners follow the element's shape, so it never bleeds past rounded edges.
 */
class SoftPressIndication(private val tint: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = Node(interactionSource, tint)
    override fun equals(other: Any?) = other is SoftPressIndication && other.tint == tint
    override fun hashCode() = tint.hashCode()

    private class Node(private val source: InteractionSource, private val tint: Color) : Modifier.Node(), DrawModifierNode {
        private val progress = Animatable(0f)

        override fun onAttach() {
            coroutineScope.launch {
                var pressed = 0
                source.interactions.collect { i ->
                    when (i) {
                        is PressInteraction.Press -> pressed++
                        is PressInteraction.Release, is PressInteraction.Cancel -> pressed = (pressed - 1).coerceAtLeast(0)
                    }
                    val target = if (pressed > 0) 1f else 0f
                    launch {
                        if (target == 1f) progress.animateTo(1f, tween(90)) else progress.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 500f))
                        invalidateDraw()
                    }
                }
            }
        }

        override fun ContentDrawScope.draw() {
            val p = progress.value
            if (p <= 0.001f) { drawContent(); return }
            val k = 1f - 0.03f * p
            scale(k) {
                this@draw.drawContent()
                val radius = minOf(size.minDimension / 2f, 20f * density)
                drawRoundRect(tint.copy(alpha = tint.alpha * p), cornerRadius = CornerRadius(radius, radius))
            }
            invalidateDraw()
        }
    }
}
