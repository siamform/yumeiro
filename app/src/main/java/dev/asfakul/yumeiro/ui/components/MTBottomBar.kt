/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.asfakul.yumeiro.ui.navigation.bottomNavItems
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import dev.asfakul.yumeiro.ui.theme.rememberGlassBorder
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow

// ---- Floating bottom dock metrics (mirrors ArchiveTune's joined mini-player + nav stack) ----
val DockHorizontalPadding = 16.dp
val DockBottomPadding = 14.dp
val DockMaxWidth = 480.dp
val DockNavHeight = 76.dp
val DockMiniHeight = 76.dp
val DockGap = 2.dp
/** Space between the floating mini-player pill and the nav pill. */
val DockPillGap = 10.dp
val DockStandaloneCorner = 26.dp
val DockOuterCorner = 24.dp
val DockJunctionCorner = 6.dp

/**
 * Floating navigation capsule. When the mini player is showing, both form ONE joined stack:
 * the mini player has big outer corners on top and small "junction" corners where it meets the
 * nav bar, which mirrors them — so it reads as a single floating dock instead of two bars.
 * The active tab gets a tonal accent pill (single hue) whose width springs in.
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
    val border = rememberGlassBorder()
    val shape = if (joinedWithMiniPlayer) {
        RoundedCornerShape(topStart = DockJunctionCorner, topEnd = DockJunctionCorner, bottomStart = DockOuterCorner, bottomEnd = DockOuterCorner)
    } else RoundedCornerShape(DockStandaloneCorner)
    val pillColor = palette.accent.copy(alpha = if (palette.isDark) 0.20f else 0.14f)
    val selectedTint = if (palette.isDark) palette.accentBright else palette.accent
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = DockHorizontalPadding, end = DockHorizontalPadding, bottom = DockBottomPadding)
            .testTag("mt_bottom_navigation_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(DockNavHeight)
                // Joined: a lighter shadow so no dark seam shows where the mini player meets it.
                .shadow(if (joinedWithMiniPlayer) 4.dp else 12.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(shape)
                .background(palette.navBar, shape)
                .border(1.dp, border, shape)
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            bottomNavItems.forEach { item ->
                val isSelected = currentRoute == item.route
                val pillWidth by animateDpAsState(if (isSelected) 56.dp else 32.dp, spring(dampingRatio = 0.7f, stiffness = 420f), label = "navPill")
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 60.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = androidx.compose.foundation.LocalIndication.current,
                            role = Role.Tab,
                        ) { if (!isSelected) onNavigateToRoute(item.route) }
                        .testTag(item.testTag),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        Modifier
                            .width(pillWidth)
                            .height(30.dp)
                            .clip(CircleShape)
                            .then(if (isSelected) Modifier.background(pillColor) else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.label,
                            tint = if (isSelected) selectedTint else inactive,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) palette.textPrimary else inactive,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
