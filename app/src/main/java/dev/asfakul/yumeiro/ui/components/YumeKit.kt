/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.ui.theme.BrandGold
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import dev.asfakul.yumeiro.ui.theme.OverlineStyle
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import dev.asfakul.yumeiro.ui.theme.Yume

/*
 * Tsuki no Michi kit — the shared building blocks of the redesign. Every tab and sub-page uses
 * these so headers, panels, icon buttons and selection marks look and behave the same everywhere.
 * All colours come from the active [LocalMtPalette]; nothing here introduces a new hue.
 */

/** The gold "moon path" stroke: a short vertical bar marking a section or a selected row. */
@Composable
fun MoonPath(modifier: Modifier = Modifier, height: Dp = 18.dp, color: Color = SpotifyGreen) {
    val p = LocalMtPalette.current
    val brush = remember(color, p.accent2) { Brush.verticalGradient(listOf(color, p.accent2.copy(alpha = 0.55f))) }
    Box(modifier.width(Yume.moonStroke).height(height).clip(CircleShape).background(brush))
}

/**
 * Section header used by every page: moon-path mark, optional kana/eyebrow line, a title, an
 * optional count and a "See all" action with a 48dp target.
 */
@Composable
fun MoonSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    count: Int? = null,
    actionLabel: String = "See all",
    onAction: (() -> Unit)? = null,
    testTag: String? = null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = Yume.touch).then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MoonPath(height = if (overline != null) 30.dp else 18.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).semantics { heading() }) {
            if (overline != null) {
                Text(overline.uppercase(), style = OverlineStyle, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    title, style = MaterialTheme.typography.titleLarge, color = TextPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (count != null && count > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text("$count", style = MaterialTheme.typography.labelMedium, color = TextTertiary, modifier = Modifier.padding(bottom = 2.dp))
                }
            }
        }
        if (onAction != null) {
            Row(
                Modifier.heightIn(min = Yume.touch).clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = "$actionLabel $title", onClick = onAction)
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge, color = SpotifyGreen)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SpotifyGreen, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Thin gold hairline used on lifted surfaces (panels, dialogs, the dock). */
@Composable
fun rememberMoonHairline(): Brush {
    val p = LocalMtPalette.current
    return remember(p.isDark, p.accent) {
        Brush.linearGradient(
            listOf(
                BrandGold.copy(alpha = if (p.isDark) 0.30f else 0.45f),
                p.accent.copy(alpha = 0.08f),
                Color.White.copy(alpha = if (p.isDark) 0.04f else 0.5f),
            ),
        )
    }
}

/** A lifted ink panel: tonal fill + gold hairline. The single container style of the redesign. */
@Composable
fun YumePanel(
    modifier: Modifier = Modifier,
    shape: Shape = Yume.shapeMd,
    hairline: Boolean = true,
    color: Color = GlassFill,
    content: @Composable ColumnScope.() -> Unit,
) {
    val border = rememberMoonHairline()
    Surface(
        shape = shape, color = color,
        modifier = modifier.then(if (hairline) Modifier.border(1.dp, border, shape) else Modifier),
    ) { Column(content = content) }
}

/**
 * Flat 48dp icon button (no individual glass circle). Used inside [ActionCapsule] and on
 * sub-page headers; an optional numeric [badge] is drawn on the corner.
 */
@Composable
fun YumeIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = TextPrimary,
    badge: Int = 0,
    badgeTag: String? = null,
) {
    Box(
        modifier.size(Yume.touch).clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(22.dp))
        if (badge > 0) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = 7.dp, end = 6.dp)
                    .size(if (badge > 9) 19.dp else 16.dp)
                    .background(SpotifyGreen, CircleShape)
                    .then(if (badgeTag != null) Modifier.testTag(badgeTag) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (badge > 9) "9+" else badge.toString(), color = LocalMtPalette.current.onAccent,
                    fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                )
            }
        }
    }
}

/** One glass capsule grouping a page's header actions (search · inbox · settings). */
@Composable
fun ActionCapsule(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val border = rememberMoonHairline()
    val shape = RoundedCornerShape(50)
    Row(
        modifier.clip(shape).background(GlassFill, shape).border(1.dp, border, shape).padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        content = content,
    )
}

/** Small tonal status tag ("3 live", "Update", "Offline"). */
@Composable
fun MoonTag(text: String, modifier: Modifier = Modifier, strong: Boolean = false) {
    val p = LocalMtPalette.current
    Box(
        modifier.clip(CircleShape)
            .background(if (strong) p.accent else p.accent.copy(alpha = if (p.isDark) 0.16f else 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text, style = MaterialTheme.typography.labelSmall,
            color = if (strong) p.onAccent else if (p.isDark) p.accentBright else p.accent, maxLines = 1,
        )
    }
}

/** Secondary line colour helper for rows (keeps tertiary text consistent). */
val RowSubtle: Color @Composable get() = TextSecondary.copy(alpha = 0.86f)

/** Drag handle for every bottom sheet: the moon path as a short gold stroke. */
@Composable
fun MoonSheetHandle() {
    val p = LocalMtPalette.current
    Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.width(36.dp).height(4.dp).clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(p.accent2.copy(alpha = 0.7f), p.accent, p.accent2.copy(alpha = 0.7f))))
        )
    }
}
