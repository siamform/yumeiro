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
 * Round 10 made the nav pill 68 dp. F11 (Beta chapter, owner: "too tall"): SkyStream's floating
 * pill is 64 dp icon-only; Yumeiro keeps labels and is now 60 dp, with the icon and label sharing
 * one capsule (B6). The music mini player keeps its 68 dp and the junction corners when docked
 * above. Pages measure the dock, so content padding follows automatically.
 */
val DockNavHeight = 60.dp
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
                        .align(Alignment.CenterStart)
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
                            .then(if (tabFocused) Modifier.border(2.dp, activeTint, RoundedCornerShape(16.dp)) else Modifier)
                            .semantics { selected = isSelected; contentDescription = item.label }
                            .testTag(item.testTag)
                            .tourTarget(item.testTag),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        // B6: icon and label sit together inside the one capsule; this box only
                        // marks the selected tab's slot for tests / TalkBack trees.
                        Box(Modifier.size(NavCapsule.IconBox), contentAlignment = Alignment.Center) {
                            Box(Modifier.matchParentSize().testTag(if (isSelected) "nav_active_pill" else "nav_pill_off"))
                            androidx.compose.animation.Crossfade(isSelected, animationSpec = androidx.compose.animation.core.tween(if (reduced) 0 else 160), label = "navGlyph") { sel ->
                                Icon(
                                    imageVector = if (sel) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = null,
                                    tint = tint,
                                    modifier = Modifier.size(NavCapsule.Icon).testTag(if (sel) "nav_icon_filled" else "nav_icon_outline"),
                                )
                            }
                        }
                        Spacer(Modifier.height(NavCapsule.Gap))
                        Text(
                            item.label,
                            color = tint,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = NavCapsule.LabelSize,
                            lineHeight = NavCapsule.LabelLine,
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
 * Capsule geometry and motion. B6 (Beta chapter): the one capsule encloses the selected tab's icon
 * AND its label (it used to sit behind the icon only, with the label floating under it). Tabs are
 * centred in the [DockNavHeight] bar: icon box [IconBox], [Gap], one label line [LabelLine] = 36 dp
 * of content inside a [Height] capsule (6 dp air above and below).
 */
object NavCapsule {
    val Height = 48.dp
    val Icon = 22.dp
    val IconBox = 24.dp
    val Gap = 1.dp
    val LabelSize = 10.5.sp
    val LabelLine = 12.sp
    private val Max = 68.dp
    fun width(tab: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp = minOf(Max, tab - 4.dp).coerceAtLeast(0.dp)
    /** Critically damped-ish spring: one small overshoot, settles in ~300 ms. */
    fun spec(): androidx.compose.animation.core.AnimationSpec<Float> =
        androidx.compose.animation.core.spring(dampingRatio = 0.78f, stiffness = 520f)
}
