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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
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
 * Round 9: how much a popup matters. The tone colours the icon seal, the top stroke, the border
 * and the main button, and adds a short plain-words badge ("Can't be undone"), so people can
 * tell a tip from a destructive action at a glance (colour is never the only cue: icon + text).
 */
enum class DialogTone(val badge: String?) {
    /** Regular choices and forms: the theme accent, no badge. */
    NEUTRAL(null),
    /** Helpful context ("Approve on Trakt", "Download?"). */
    INFO("Good to know"),
    SUCCESS("Done"),
    /** Something to think about first (18+ content, risky extension, overwrite). */
    WARNING("Heads up"),
    /** Deletes or signs out: cannot be undone. */
    DANGER("Can't be undone"),
    /** Security / data-loss level. */
    CRITICAL("Important");

    fun color(dark: Boolean, accent: Color): Color = when (this) {
        NEUTRAL, INFO -> accent
        SUCCESS -> if (dark) Color(0xFF5BD69A) else Color(0xFF14784B)
        WARNING -> if (dark) Color(0xFFFFC266) else Color(0xFF9A5B00)
        DANGER, CRITICAL -> if (dark) Color(0xFFFF7A7A) else Color(0xFFB3261E)
    }

    fun defaultIcon(): androidx.compose.ui.graphics.vector.ImageVector? = when (this) {
        NEUTRAL -> null
        INFO -> androidx.compose.material.icons.Icons.Outlined.Info
        SUCCESS -> androidx.compose.material.icons.Icons.Outlined.CheckCircle
        WARNING -> androidx.compose.material.icons.Icons.Outlined.WarningAmber
        DANGER -> androidx.compose.material.icons.Icons.Outlined.DeleteForever
        CRITICAL -> androidx.compose.material.icons.Icons.Outlined.GppMaybe
    }
}

/** Tone of the dialog being drawn; buttons inside can read it (e.g. [DialogConfirmButton]). */
val LocalDialogTone = androidx.compose.runtime.staticCompositionLocalOf { DialogTone.NEUTRAL }

/**
 * The main action of a [TsukiDialog]: a filled pill in the dialog's tone (red for DANGER), so
 * the primary choice is obvious and a destructive one looks destructive.
 */
@Composable
fun DialogConfirmButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val p = LocalMtPalette.current
    val tone = LocalDialogTone.current
    val c = tone.color(p.isDark, if (p.isDark) p.accentBright else p.accent)
    val on = if (c.luminance() > 0.45f) Color(0xFF15110C) else Color.White
    androidx.compose.material3.Button(
        onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(22.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = c, contentColor = on, disabledContainerColor = c.copy(alpha = 0.3f)),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 10.dp),
    ) { androidx.compose.material3.Text(text, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
}

/** True in screenshot tests: dialogs draw in place instead of in their own window. */
val LocalPopupPreview = androidx.compose.runtime.staticCompositionLocalOf { false }

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
    tone: DialogTone = DialogTone.NEUTRAL,
) {
    val p = LocalMtPalette.current
    val toneColor = tone.color(p.isDark, p.accent)
    val glow = if (tone == DialogTone.NEUTRAL || tone == DialogTone.INFO) LocalAmbientColor.current?.value ?: p.accent else toneColor
    // A tone without its own icon still gets one, so meaning never rests on colour alone.
    val shownIcon: (@Composable () -> Unit)? = icon ?: tone.defaultIcon()?.let { v -> { androidx.compose.material3.Icon(v, null) } }
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
    val seal = if (tone == DialogTone.NEUTRAL) BrandGold else toneColor
    val hairline = remember(glow, p.isDark, seal) {
        Brush.linearGradient(listOf(seal.copy(alpha = if (p.isDark) 0.55f else 0.7f), glow.copy(alpha = 0.18f), p.textPrimary.copy(alpha = 0.06f)))
    }
    val scale = remember { Animatable(0.92f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) { alpha.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow)) }
    LaunchedEffect(Unit) { scale.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)) }
    val card: @Composable () -> Unit = {
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
                        .background(if (tone == DialogTone.NEUTRAL) Brush.horizontalGradient(listOf(p.accent2, p.accent, p.accent2)) else Brush.horizontalGradient(listOf(toneColor.copy(alpha = 0.6f), toneColor, toneColor.copy(alpha = 0.6f))))
                )
                androidx.compose.foundation.layout.Spacer(Modifier.heightIn(min = 20.dp))
                // Header: seal-style icon tile beside the title (editorial, left-aligned).
                if (shownIcon != null || title != null) {
                    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        if (shownIcon != null) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                                    .background(Brush.linearGradient(listOf(glow.copy(alpha = if (p.isDark) 0.30f else 0.16f), seal.copy(alpha = 0.10f))))
                                    .border(1.dp, seal.copy(alpha = 0.45f), RoundedCornerShape(14.dp)),
                            ) {
                                // Light themes: BrandGold on cream is too faint, use the deeper accent.
                                val sealInk = if (tone != DialogTone.NEUTRAL) toneColor else if (p.isDark) BrandGold else p.accent
                                CompositionLocalProvider(LocalContentColor provides if (iconContentColor.isSpecified()) iconContentColor else sealInk) {
                                    Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { shownIcon() }
                                }
                            }
                            androidx.compose.foundation.layout.Spacer(Modifier.size(14.dp))
                        }
                        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                            tone.badge?.let { b ->
                                androidx.compose.material3.Text(
                                    b.uppercase(), color = toneColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
                                    modifier = Modifier.padding(bottom = 2.dp).semantics { contentDescription = b },
                                )
                            }
                            if (title != null) {
                                CompositionLocalProvider(LocalContentColor provides if (titleContentColor.isSpecified()) titleContentColor else p.textPrimary) {
                                    ProvideTextStyle(MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 24.sp, color = LocalContentColor.current)) {
                                        Box(Modifier.semantics { heading() }) { title() }
                                    }
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
                CompositionLocalProvider(LocalContentColor provides if (p.isDark) p.accentBright else p.accent, LocalDialogTone provides tone) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        dismissButton?.invoke()
                        // Destructive / warning confirms read in the tone colour even when the caller
                        // passes a plain TextButton (its colour comes from colorScheme.primary).
                        if (tone == DialogTone.DANGER || tone == DialogTone.CRITICAL || tone == DialogTone.WARNING) {
                            MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = toneColor)) { confirmButton() }
                        } else confirmButton()
                    }
                }
            }
        }
    }
    if (LocalPopupPreview.current) Box(Modifier.fillMaxSize().padding(horizontal = 32.dp), contentAlignment = Alignment.Center) { card() }
    else Dialog(onDismissRequest = onDismissRequest, properties = properties) { card() }
}

private fun Color.isSpecified() = this != Color.Unspecified

private fun lerpColor(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)
