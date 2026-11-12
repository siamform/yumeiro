/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
val DockNavHeight = 62.dp
val DockMiniHeight = 68.dp
val DockGap = 2.dp
/** Space between the floating mini-player pill and the nav pill. */
val DockPillGap = 10.dp
val DockStandaloneCorner = 24.dp
val DockOuterCorner = 22.dp
val DockJunctionCorner = 6.dp

/**
 * The Yumeiro dock: one quiet ink bar holding the main destinations (Home · Music · Videos ·
 * Library by default; 3 to 6 items, see Settings → Navigation bar).
 *
 * Active state (Round 8, Snapchat-style): no capsule, circle or dot. The active icon switches
 * to its solid glyph, fills with the accent colour and springs up a touch; inactive tabs are
 * calm outlines. Tabs are icon-only (labels are announced to TalkBack and shown in Settings → Navigation bar). With the mini player
 * showing, both pieces form one joined stack.
 */
@Composable
fun MTBottomBar(
    currentRoute: String,
    onNavigateToRoute: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** True while the mini player is docked right above this bar. */
    joinedWithMiniPlayer: Boolean = false,
    /** What the bar shows (main tabs, then pinned shortcuts). */
    items: List<dev.asfakul.yumeiro.ui.navigation.BottomNavItem> = bottomNavItems,
) {
    val palette = LocalMtPalette.current
    val inactive = if (palette.isDark) palette.textSecondary.copy(alpha = 0.80f) else palette.textSecondary
    val border = palette.textPrimary.copy(alpha = if (palette.isDark) 0.07f else 0.08f)
    val shape = if (joinedWithMiniPlayer) {
        RoundedCornerShape(topStart = DockJunctionCorner, topEnd = DockJunctionCorner, bottomStart = DockOuterCorner, bottomEnd = DockOuterCorner)
    } else RoundedCornerShape(DockStandaloneCorner)
    val activeTint = if (palette.isDark) palette.accentBright else palette.accent
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = DockHorizontalPadding, end = DockHorizontalPadding, bottom = DockBottomPadding)
            .testTag("mt_bottom_navigation_bar")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(DockNavHeight)
                .shadow(if (joinedWithMiniPlayer) 2.dp else 6.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(shape)
                .background(palette.navBar, shape)
                .border(1.dp, border, shape)
        ) {
            val inner = 6.dp
            // Round 8: no indicator shape at all (the old sliding capsule / circle and the seal dot
            // are gone). Like Snapchat, the active destination is shown by its glyph alone: it
            // switches to the solid icon, fills with the accent colour and springs up a touch.
            Row(
                modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = inner),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val isSelected = currentRoute == item.route
                    val tint by animateColorAsState(if (isSelected) activeTint else inactive, label = "navTint")
                    val pop by animateFloatAsState(if (isSelected) 1.12f else 1f, androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 500f), label = "navPop")
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Tab,
                                onClickLabel = item.label,
                            ) { if (!isSelected) onNavigateToRoute(item.route) }
                            .semantics { selected = isSelected; contentDescription = item.label }
                            .testTag(item.testTag)
                            .tourTarget(item.testTag),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Icon-only (no captions): the active glyph is the solid version in the accent
                        // colour; inactive glyphs are outlines in a calm neutral.
                        androidx.compose.animation.Crossfade(isSelected, animationSpec = androidx.compose.animation.core.tween(160), label = "navGlyph") { sel ->
                            Icon(
                                imageVector = if (sel) item.selectedIcon else item.unselectedIcon,
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(if (sel) 27.dp else 25.dp).scale(pop).testTag(if (sel) "nav_icon_filled" else "nav_icon_outline"),
                            )
                        }
                    }
                }
            }
        }
    }
}
