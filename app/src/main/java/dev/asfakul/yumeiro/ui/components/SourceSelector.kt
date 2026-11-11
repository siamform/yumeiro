/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette

/** One place a tab can get its content from (e.g. Music → On device / Online / Mix). */
@Immutable
data class SourceOption(
    val key: String,
    val title: String,
    /** What this source is, in a few words ("Songs on this phone"). */
    val description: String,
    /** Live status from the real feature ("412 songs", "Offline — needs internet", "Add sources"). */
    val status: String,
    val icon: ImageVector,
    val testTag: String,
    /** Status reads as a warning (needs setup / offline), never blocks picking it. */
    val attention: Boolean = false,
)

/**
 * The source menu that drops down from a tab's title (Music · On device ⌄).
 *
 * Research: YouTube Music moved Library / Downloads / Device files into a menu opened from the
 * page title instead of permanent tabs
 * (https://www.androidcentral.com/apps-software/youtube-music-library-tab-redesign), and M3 menus
 * ask for a second selection cue besides colour, like a checkmark
 * (https://m3.material.io/components/menus/accessibility). The menu is drawn inside the app
 * window (not a popup) so it can animate from the title and stays inside the tour spotlight.
 */
@Composable
fun SourceMenu(
    visible: Boolean,
    heading: String,
    options: List<SourceOption>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    menuTag: String,
    modifier: Modifier = Modifier,
) {
    val p = LocalMtPalette.current
    val reduced = reducedMotion()
    if (visible) BackHandler(onBack = onDismiss)
    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(YuMotion.enter(reduced, 220)), exit = fadeOut(YuMotion.exit(reduced))) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (p.isDark) 0.45f else 0.25f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                    .testTag(menuTag + "_scrim"),
            )
        }
        AnimatedVisibility(
            visible,
            enter = if (reduced) fadeIn(YuMotion.enter(true)) else expandVertically(YuMotion.enter(false, 320), expandFrom = Alignment.Top) + fadeIn(YuMotion.enter(false, 200)),
            exit = if (reduced) fadeOut(YuMotion.exit(true)) else shrinkVertically(YuMotion.exit(false, 180), shrinkTowards = Alignment.Top) + fadeOut(YuMotion.exit(false, 150)),
            modifier = Modifier.padding(horizontal = 12.dp).padding(top = 2.dp),
        ) {
            val shape = RoundedCornerShape(24.dp)
            Column(
                Modifier.widthIn(max = 420.dp).fillMaxWidth().clip(shape)
                    .background(p.surfaceContainerHigh)
                    .border(1.dp, p.textPrimary.copy(alpha = 0.08f), shape)
                    .semantics { paneTitle = heading }
                    .testTag(menuTag)
                    .padding(8.dp),
            ) {
                Text(
                    heading, style = MaterialTheme.typography.labelMedium, color = p.textTertiary,
                    modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp),
                )
                options.forEachIndexed { i, o ->
                    SourceRow(o, o.key == selectedKey, index = i, reduced = reduced) { onSelect(o.key) }
                }
            }
        }
    }
}

@Composable
private fun SourceRow(o: SourceOption, selected: Boolean, index: Int, reduced: Boolean, onClick: () -> Unit) {
    val p = LocalMtPalette.current
    // Rows settle in one after another (30 ms apart); skipped entirely with reduced motion.
    val anim = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { if (!reduced) anim.animateTo(1f, YuMotion.enter(false, 260, delay = 40 + index * 30)) }
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .graphicsLayer { alpha = anim.value; translationY = (1f - anim.value) * 10.dp.toPx() }
            .clip(shape)
            .background(if (selected) p.accent.copy(alpha = if (p.isDark) 0.16f else 0.12f) else Color.Transparent)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { this.selected = selected; contentDescription = "${o.title}, ${o.status}" }
            .testTag(o.testTag)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(if (selected) p.accent else p.textPrimary.copy(alpha = 0.07f)),
            contentAlignment = Alignment.Center,
        ) { Icon(o.icon, null, tint = if (selected) p.onAccent else p.textSecondary, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(o.title, color = p.textPrimary, fontSize = 15.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(o.description, color = p.textSecondary, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
            if (selected) Icon(Icons.Default.Check, "Selected", tint = p.accent, modifier = Modifier.size(20.dp))
            Text(
                o.status, color = if (o.attention) p.accent else p.textSecondary, fontSize = 12.sp,
                fontWeight = if (o.attention) FontWeight.Bold else FontWeight.Normal, maxLines = 1,
            )
        }
    }
}
