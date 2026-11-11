/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.asfakul.yumeiro.ui.theme.BrandGold
import dev.asfakul.yumeiro.ui.theme.LocalAmbientColor
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette

/**
 * Yumeiro's alert: a drop-in replacement for Material's `AlertDialog` (same parameters) drawn in
 * the Tsuki style — ink glass surface, a soft glow in the page's ambient colour, a gold hairline,
 * an icon badge and a spring entrance. Every confirm / info / error popup uses it so alerts look
 * like part of the app instead of stock Android.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TsukiDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(28.dp),
    containerColor: Color = Color.Unspecified,
    iconContentColor: Color = Color.Unspecified,
    titleContentColor: Color = Color.Unspecified,
    textContentColor: Color = Color.Unspecified,
    @Suppress("UNUSED_PARAMETER") tonalElevation: Dp = 0.dp,
    properties: DialogProperties = DialogProperties(),
) {
    val p = LocalMtPalette.current
    val glow = LocalAmbientColor.current?.value ?: p.accent
    val base = if (containerColor.isSpecified()) containerColor else p.surfaceContainer
    val surface = remember(base, glow, p.isDark) {
        Brush.verticalGradient(
            listOf(
                lerpColor(base, glow, if (p.isDark) 0.16f else 0.08f),
                base,
                lerpColor(base, Color.Black, if (p.isDark) 0.18f else 0.02f),
            ),
        )
    }
    val hairline = remember(glow, p.isDark) {
        Brush.linearGradient(listOf(BrandGold.copy(alpha = if (p.isDark) 0.55f else 0.7f), glow.copy(alpha = 0.18f), Color.White.copy(alpha = 0.06f)))
    }
    val scale = remember { Animatable(0.92f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) { alpha.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow)) }
    LaunchedEffect(Unit) { scale.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)) }
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        // Opaque surface + real border: the old version drew a gradient inside a *transparent*
        // Surface with a shadow, which showed a dark box / broken edges behind the card on many
        // phones (and let the page bleed through in light mode). The wash is now painted inside
        // the clipped, opaque card.
        Surface(
            shape = shape,
            color = base,
            contentColor = p.textPrimary,
            shadowElevation = 16.dp,
            tonalElevation = 0.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, hairline),
            modifier = modifier
                .widthIn(min = 280.dp, max = 520.dp)
                .graphicsLayer { scaleX = scale.value; scaleY = scale.value; this.alpha = alpha.value },
        ) {
            Column(Modifier.background(surface).padding(start = 22.dp, end = 22.dp, top = 0.dp, bottom = 14.dp)) {
                // The moon path: a short gold stroke hanging from the top edge, shared with the dock.
                Box(
                    Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 3.dp)
                        .clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                        .background(Brush.horizontalGradient(listOf(p.accent2, p.accent, p.accent2)))
                )
                androidx.compose.foundation.layout.Spacer(Modifier.heightIn(min = 20.dp))
                // Header: seal-style icon tile beside the title (editorial, left-aligned).
                if (icon != null || title != null) {
                    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        if (icon != null) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                                    .background(Brush.linearGradient(listOf(glow.copy(alpha = if (p.isDark) 0.30f else 0.20f), BrandGold.copy(alpha = 0.10f))))
                                    .border(1.dp, BrandGold.copy(alpha = 0.40f), RoundedCornerShape(14.dp)),
                            ) {
                                CompositionLocalProvider(LocalContentColor provides if (iconContentColor.isSpecified()) iconContentColor else BrandGold) {
                                    Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { icon() }
                                }
                            }
                            androidx.compose.foundation.layout.Spacer(Modifier.size(14.dp))
                        }
                        if (title != null) {
                            CompositionLocalProvider(LocalContentColor provides if (titleContentColor.isSpecified()) titleContentColor else p.textPrimary) {
                                ProvideTextStyle(MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 24.sp, color = LocalContentColor.current)) {
                                    Box(Modifier.weight(1f)) { title() }
                                }
                            }
                        }
                    }
                }
                if (text != null) {
                    CompositionLocalProvider(LocalContentColor provides if (textContentColor.isSpecified()) textContentColor else p.textSecondary) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium.copy(color = LocalContentColor.current, lineHeight = 21.sp)) {
                            Box(Modifier.weight(1f, fill = false).fillMaxWidth().padding(bottom = 16.dp)) { text() }
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().padding(bottom = 6.dp).height(0.5.dp).background(p.textTertiary.copy(alpha = 0.18f)))
                CompositionLocalProvider(LocalContentColor provides if (p.isDark) p.accentBright else p.accent) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
            }
        }
    }
}

private fun Color.isSpecified() = this != Color.Unspecified

private fun lerpColor(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)
