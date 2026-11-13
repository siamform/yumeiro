/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette

/** One mode of a page (Music: Device / Online / Mix; Videos: Yumix / YouTube / Device / Live TV). */
@Immutable
data class ModeOption(
    val key: String,
    val title: String,
    /** Short live status ("128 songs", "No login"). */
    val subtitle: String,
    val icon: ImageVector,
    val testTag: String,
)

/**
 * Mode cards: a row of equal cards, each with an icon, a clear name and a live status line.
 * Replaces the old segmented "tab" switches so people can see what each mode is before they tap
 * (recognition over recall). Selected = tonal accent fill + accent outline + filled icon disc.
 */
@Composable
fun ModeSwitcher(
    options: List<ModeOption>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    val p = LocalMtPalette.current
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { o ->
            val sel = o.key == selectedKey
            val shape = RoundedCornerShape(16.dp)
            val fill by animateColorAsState(
                if (sel) p.accent.copy(alpha = if (p.isDark) 0.16f else 0.12f) else p.surfaceContainerHigh.copy(alpha = if (p.isDark) 0.7f else 1f),
                label = "modeFill",
            )
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 76.dp)
                    .clip(shape)
                    .background(fill, shape)
                    .border(if (sel) 1.5.dp else 1.dp, if (sel) p.accent else p.textPrimary.copy(alpha = 0.06f), shape)
                    .clickable(role = Role.Tab) { if (!sel) onSelect(o.key) }
                    .semantics { selected = sel; contentDescription = "${o.title}, ${o.subtitle}" }
                    .testTag(o.testTag)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape)
                        .background(if (sel) p.accent else p.textPrimary.copy(alpha = 0.07f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(o.icon, null, tint = if (sel) p.onAccent else p.textSecondary, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    o.title, color = p.textPrimary, fontSize = 13.sp, lineHeight = 16.sp,
                    fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    o.subtitle, color = if (sel) p.textSecondary else p.textTertiary, fontSize = 11.sp, lineHeight = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
