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
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import dev.asfakul.yumeiro.ui.screens.extras.tourTarget
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.graphicsLayer
import dev.asfakul.yumeiro.ui.navigation.bottomNavItems
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import dev.asfakul.yumeiro.ui.theme.Yume

// ---- Floating bottom dock metrics -----------------------------------------------------------
val DockHorizontalPadding = 14.dp
val DockBottomPadding = 12.dp
val DockMaxWidth = 480.dp
/*
 * Round 10: the bottom nav pill was 62 dp and read as cramped; it is now 68 dp (Material 3's
 * flexible bar is 64 dp, the iOS floating tab capsule 62 pt). The music mini player keeps its
 * original 68 dp. Pages measure the dock, so content padding follows automatically.
 */
val DockNavHeight = 68.dp
val DockMiniHeight = 68.dp
val DockGap = 2.dp
/** Space between the floating mini-player pill and the nav pill. */
val DockPillGap = 10.dp
val DockStandaloneCorner = 24.dp
val DockOuterCorner = 22.dp
val DockJunctionCorner = 6.dp

/**
 * The Yumeiro dock: one floating bar holding the main destinations (Home · Music · Videos ·
 * Library by default; 3 to 6 items, see Settings → Navigation bar).
 *
 * NAV-BAR-2 (owner list 33, users: "looks cheap, background and icons blend, no labels"):
 * - The bar sits on the raised surface tone, not a near-black that matched the page, with a
 *   hairline border, so it reads as its own layer in every theme (AMOLED keeps a near-black).
 * - Every tab has a short label under its icon (one line, ellipsis for long pinned names).
 * - The active tab gets an accent pill behind its solid icon and an accent, bold label; inactive
 *   tabs are outlines in the secondary text colour (≥ 4.5:1 on the bar).
 * - B1 (Group B): one capsule for the bar slides to the new tab with a short spring (draw-phase
 *   translation, never a width or layout animation) and a light haptic tick confirms the change;
 *   with reduced motion it jumps.
 * Same items, order, routes, test tags and TalkBack labels as before.
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
    val reduced = reducedMotion()
    val amoled = palette.isDark && palette.background == Color.Black
    val barColor = when {
        amoled -> palette.surface
        palette.isDark -> palette.surfaceContainerHigh
        else -> palette.surface
    }
    val inactive = palette.textSecondary
    val border = palette.textPrimary.copy(alpha = 0.10f)
    val shape = if (joinedWithMiniPlayer) {
        RoundedCornerShape(topStart = DockJunctionCorner, topEnd = DockJunctionCorner, bottomStart = DockOuterCorner, bottomEnd = DockOuterCorner)
    } else RoundedCornerShape(DockStandaloneCorner)
    val activeTint = if (palette.isDark) palette.accentBright else palette.accent
    val pillColor = activeTint.copy(alpha = if (palette.isDark) 0.18f else 0.14f)
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
                .shadow(if (joinedWithMiniPlayer) 2.dp else 8.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(shape)
                .background(barColor, shape)
                .border(1.dp, border, shape)
        ) {
            // B1: one accent capsule for the whole bar that slides from tab to tab (draw-phase
            // translation only, no relayout); with reduced motion it jumps. A light haptic tick
            // confirms a tab change.
            val selectedIndex = items.indexOfFirst { it.route == currentRoute }
            val capsule = remember { androidx.compose.animation.core.Animatable(selectedIndex.coerceAtLeast(0).toFloat()) }
            val capsuleAlpha by animateFloatAsState(if (selectedIndex >= 0) 1f else 0f, if (reduced) androidx.compose.animation.core.snap() else androidx.compose.animation.core.tween(160), label = "navCapsuleAlpha")
            androidx.compose.runtime.LaunchedEffect(selectedIndex, reduced) {
                if (selectedIndex < 0) return@LaunchedEffect
                val target = selectedIndex.toFloat()
                if (reduced || capsuleAlpha == 0f) capsule.snapTo(target)
                else capsule.animateTo(target, NavCapsule.spec())
            }
            val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
                val count = items.size.coerceAtLeast(1)
                val tabW = maxWidth / count
                val capW = NavCapsule.width(tabW)
                Box(
                    Modifier
                        .padding(top = NavCapsule.Top)
                        .size(capW, NavCapsule.Height)
                        .graphicsLayer {
                            translationX = (capsule.value * tabW.toPx()) + (tabW - capW).toPx() / 2f
                            alpha = capsuleAlpha
                        }
                        .clip(CircleShape)
                        .background(pillColor)
                        .testTag("nav_capsule")
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val isSelected = currentRoute == item.route
                    val tint by animateColorAsState(if (isSelected) activeTint else inactive, label = "navTint")
                    // Keyboard / D-pad focus: a ring around the tab (touch never focuses it).
                    val tabSource = remember { MutableInteractionSource() }
                    val tabFocused by tabSource.collectIsFocusedAsState()
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(
                                interactionSource = tabSource,
                                indication = androidx.compose.material3.ripple(bounded = true, color = activeTint),
                                role = Role.Tab,
                                onClickLabel = item.label,
                            ) { if (!isSelected) { haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentTick); onNavigateToRoute(item.route) } }
                            .padding(top = NavCapsule.Top)
                            .then(if (tabFocused) Modifier.border(2.dp, activeTint, RoundedCornerShape(16.dp)) else Modifier)
                            .semantics { selected = isSelected; contentDescription = item.label }
                            .testTag(item.testTag)
                            .tourTarget(item.testTag),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top,
                    ) {
                        // Up to seven tabs: the pill narrows with the tab (56 dp at most) so it never touches its neighbour.
                        Box(Modifier.padding(horizontal = 3.dp).widthIn(max = 56.dp).fillMaxWidth().height(NavCapsule.Height), contentAlignment = Alignment.Center) {
                            // Marker for tests / TalkBack trees; the visible capsule is drawn once for the bar.
                            Box(Modifier.matchParentSize().testTag(if (isSelected) "nav_active_pill" else "nav_pill_off"))
                            androidx.compose.animation.Crossfade(isSelected, animationSpec = androidx.compose.animation.core.tween(if (reduced) 0 else 160), label = "navGlyph") { sel ->
                                Icon(
                                    imageVector = if (sel) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = null,
                                    tint = tint,
                                    modifier = Modifier.size(24.dp).testTag(if (sel) "nav_icon_filled" else "nav_icon_outline"),
                                )
                            }
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            item.label,
                            color = tint,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            lineHeight = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 2.dp).testTag("nav_label_${item.testTag}").clearAndSetSemantics { },
                        )
                    }
                }
            }
        }
    }
}

/**
 * B1 capsule geometry and motion. The capsule sits behind the icon of the selected tab: tabs lay
 * out from [Top] (icon box [Height], 3 dp gap, 13 dp label → centred in the 68 dp bar).
 */
object NavCapsule {
    val Top = 11.dp
    val Height = 30.dp
    private val Max = 56.dp
    fun width(tab: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp = minOf(Max, tab - 6.dp).coerceAtLeast(0.dp)
    /** Critically damped-ish spring: one small overshoot, settles in ~300 ms. */
    fun spec(): androidx.compose.animation.core.AnimationSpec<Float> =
        androidx.compose.animation.core.spring(dampingRatio = 0.78f, stiffness = 520f)
}
