/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import dev.asfakul.yumeiro.ui.screens.extras.tourTarget
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.asfakul.yumeiro.ui.navigation.bottomNavItems
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import dev.asfakul.yumeiro.ui.theme.Yume

// ---- Floating bottom dock metrics -----------------------------------------------------------
val DockHorizontalPadding = 14.dp
val DockBottomPadding = 12.dp
val DockMaxWidth = 480.dp
val DockNavHeight = 68.dp
val DockMiniHeight = 68.dp
val DockGap = 2.dp
/** Space between the floating mini-player pill and the nav pill. */
val DockPillGap = 10.dp
val DockStandaloneCorner = 24.dp
val DockOuterCorner = 22.dp
val DockJunctionCorner = 6.dp

/**
 * The Yumeiro dock: one quiet ink bar holding the four main destinations (Home · Videos · Music ·
 * Library). The active tab is marked by a solid gold capsule that glides behind its icon (one shared,
 * spring-animated indicator) with an ink icon on top and a bold label underneath — a clear,
 * high-contrast selected state instead of a thin stroke. Every tab keeps its label (recognition over
 * recall). When the mini player is showing, both pieces form one joined stack.
 */
@Composable
fun MTBottomBar(
    currentRoute: String,
    onNavigateToRoute: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** True while the mini player is docked right above this bar. */
    joinedWithMiniPlayer: Boolean = false,
) {
    val palette = LocalMtPalette.current
    val inactive = if (palette.isDark) palette.textSecondary.copy(alpha = 0.78f) else palette.textTertiary
    val border = palette.textPrimary.copy(alpha = if (palette.isDark) 0.07f else 0.08f)
    val shape = if (joinedWithMiniPlayer) {
        RoundedCornerShape(topStart = DockJunctionCorner, topEnd = DockJunctionCorner, bottomStart = DockOuterCorner, bottomEnd = DockOuterCorner)
    } else RoundedCornerShape(DockStandaloneCorner)
    val selectedIndex = bottomNavItems.indexOfFirst { it.route == currentRoute }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = DockHorizontalPadding, end = DockHorizontalPadding, bottom = DockBottomPadding)
            .testTag("mt_bottom_navigation_bar")
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(DockNavHeight)
                .shadow(if (joinedWithMiniPlayer) 2.dp else 6.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(shape)
                .background(palette.navBar, shape)
                .border(1.dp, border, shape)
        ) {
            val inner = 6.dp
            val tabW = (maxWidth - inner * 2) / bottomNavItems.size
            val indicatorW = 58.dp
            val indicatorH = 30.dp
            val target = inner + tabW * selectedIndex.coerceAtLeast(0) + (tabW - indicatorW) / 2
            val x by animateDpAsState(target, Yume.settle(), label = "dockPillX")
            val indicatorAlpha by animateFloatAsState(if (selectedIndex >= 0) 1f else 0f, label = "dockPillA")
            // Icon row sits 11dp from the top (68dp bar: 11 + 30 pill + 4 + ~16 label + rest).
            Box(
                Modifier
                    .offset(x = x, y = 9.dp)
                    .width(indicatorW)
                    .height(indicatorH)
                    .scale(indicatorAlpha)
                    .clip(CircleShape)
                    .background(palette.accent)
            )
            Row(
                modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = inner),
                verticalAlignment = Alignment.Top
            ) {
                bottomNavItems.forEach { item ->
                    val isSelected = currentRoute == item.route
                    val tint by animateColorAsState(if (isSelected) palette.onAccent else inactive, label = "navTint")
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = androidx.compose.foundation.LocalIndication.current,
                                role = Role.Tab,
                            ) { if (!isSelected) onNavigateToRoute(item.route) }
                            .semantics { selected = isSelected }
                            .testTag(item.testTag)
                            .tourTarget(item.testTag),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(9.dp))
                        Box(Modifier.width(indicatorW).height(indicatorH), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label,
                                tint = tint,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium),
                            color = if (isSelected) palette.textPrimary else inactive,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
