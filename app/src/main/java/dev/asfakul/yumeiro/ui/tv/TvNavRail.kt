/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.tv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.asfakul.yumeiro.ui.navigation.BottomNavItem
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette

/** Width the content keeps free for the collapsed rail. */
val TvRailWidth = 84.dp
private val TvRailOpenWidth = 248.dp

/**
 * TV navigation: a slim ink rail on the left edge (a bottom bar is out of reach of a remote and
 * eats the short side of a 16:9 screen). Collapsed it shows only glyphs, so the content keeps the
 * screen. When focus enters it (D-pad Left from the page), it opens over the content with labels,
 * like a drawer of dream colours, and closes again the moment focus goes back to the page.
 * The same items as the phone bar (Settings → Navigation bar), plus Settings at the bottom.
 */
@Composable
fun TvNavRail(
    currentRoute: String,
    items: List<BottomNavItem>,
    onNavigate: (String) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalMtPalette.current
    var focusedInside by remember { mutableStateOf(false) }
    val width by animateDpAsState(if (focusedInside) TvRailOpenWidth else TvRailWidth, tween(180), label = "railWidth")
    Box(
        modifier
            .fillMaxHeight()
            .width(width)
            .zIndex(2f)
            // Collapsed: ink that fades into the page. Open: a solid panel with a soft edge shadow,
            // so the labels never sit on top of the page's own text.
            .then(
                if (focusedInside) Modifier.shadow(20.dp, RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp), clip = false)
                    .background(palette.navBar, RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp))
                else Modifier.background(Brush.horizontalGradient(listOf(palette.navBar, palette.navBar.copy(alpha = 0.90f), palette.navBar.copy(alpha = 0f)))),
            )
            .onFocusChanged { focusedInside = it.hasFocus }
            .testTag("tv_nav_rail"),
    ) {
        Column(
            Modifier.fillMaxHeight().padding(vertical = 28.dp, horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Brand mark: the crescent of the launcher icon in the accent colour.
            Row(Modifier.height(48.dp).padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("◆", color = palette.accent, fontSize = 18.sp)
                AnimatedVisibility(focusedInside, enter = fadeIn(tween(160)), exit = fadeOut(tween(90))) {
                    Text("  Yumeiro", color = palette.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
            Spacer(Modifier.height(12.dp))
            items.forEach { item ->
                RailItem(
                    label = item.label,
                    selected = currentRoute == item.route,
                    expanded = focusedInside,
                    icon = { sel -> if (sel) item.selectedIcon else item.unselectedIcon },
                    tag = "tv_${item.testTag}",
                ) { if (currentRoute != item.route) onNavigate(item.route) }
            }
            Spacer(Modifier.weight(1f))
            RailItem(
                label = "Settings",
                selected = false,
                expanded = focusedInside,
                icon = { sel -> if (sel) Icons.Filled.Settings else Icons.Outlined.Settings },
                tag = "tv_nav_settings",
                onClick = onSettings,
            )
        }
    }
}

@Composable
private fun RailItem(
    label: String,
    selected: Boolean,
    expanded: Boolean,
    icon: (Boolean) -> androidx.compose.ui.graphics.vector.ImageVector,
    tag: String,
    onClick: () -> Unit,
) {
    val palette = LocalMtPalette.current
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val accent = if (palette.isDark) palette.accentBright else palette.accent
    val bg by animateColorAsState(
        when {
            focused -> accent
            selected -> accent.copy(alpha = 0.16f)
            else -> Color.Transparent
        }, tween(140), label = "railBg",
    )
    val fg by animateColorAsState(
        when {
            focused -> palette.onAccent
            selected -> accent
            else -> palette.textSecondary
        }, tween(140), label = "railFg",
    )
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(bg, shape)
            // The rail draws its own focus (filled accent pill), so no extra ring here.
            .clickable(interactionSource = source, indication = null, role = Role.Tab, onClickLabel = label, onClick = onClick)
            .semantics { this.selected = selected; contentDescription = label }
            .testTag(tag)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon(selected || focused), null, tint = fg, modifier = Modifier.size(26.dp))
        if (expanded) {
            Spacer(Modifier.width(16.dp))
            Text(label, color = fg, fontSize = 17.sp, fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
