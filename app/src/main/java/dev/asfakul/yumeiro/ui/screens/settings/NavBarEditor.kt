/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.R
import dev.asfakul.yumeiro.ui.components.AppMessages
import dev.asfakul.yumeiro.ui.navigation.BottomNavItem
import dev.asfakul.yumeiro.ui.navigation.NavPrefs
import dev.asfakul.yumeiro.ui.navigation.Screen
import dev.asfakul.yumeiro.ui.navigation.bottomNavItems
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import dev.asfakul.yumeiro.ui.theme.yuRound
import kotlin.math.roundToInt

/**
 * B14: Settings → Navigation bar as one reorderable list. "On the bar" holds every item in bar
 * order: a drag handle (pick up, the others slide aside, drop springs into place, a haptic tick per
 * slot), Move up / Move down buttons (the single-pointer and D-pad way, WCAG 2.5.7) and a switch.
 * "More you can add" holds the rest with their switches, disabled with the reason once the bar is
 * full. Home stays first. The 3–7 / 3-shortcut rule lives in [NavPrefs].
 */
@Composable
internal fun NavBarEditor(cfg: NavPrefs.Config) {
    val context = LocalContext.current
    val items = NavPrefs.barItems(cfg)
    val full = stringResource(R.string.nav_edit_full)
    val scFull = stringResource(R.string.nav_edit_shortcuts_full, NavPrefs.MAX_SHORTCUTS)
    val min = stringResource(R.string.nav_edit_min, NavPrefs.MIN_ITEMS)
    val tabKind = stringResource(R.string.nav_edit_kind_tab)
    val scKind = stringResource(R.string.nav_edit_kind_shortcut)

    fun toggle(item: BottomNavItem, on: Boolean) {
        val sc = NavPrefs.Shortcut.of(item.route.removePrefix(NavPrefs.SHORTCUT_PREFIX)).takeIf { item.route.startsWith(NavPrefs.SHORTCUT_PREFIX) }
        val ok = if (sc != null) NavPrefs.setShortcut(context, sc, on) else NavPrefs.setTab(context, item.route, on)
        if (!ok) AppMessages.info(context, when {
            !on -> min
            sc != null && cfg.shortcuts.size >= NavPrefs.MAX_SHORTCUTS -> scFull
            else -> full
        })
    }
    fun tagOf(item: BottomNavItem): String {
        val sc = item.route.removePrefix(NavPrefs.SHORTCUT_PREFIX)
        return if (item.route.startsWith(NavPrefs.SHORTCUT_PREFIX)) "nav_settings_shortcut_$sc" else "nav_settings_tab_${item.label.lowercase()}"
    }

    GroupLabel(stringResource(R.string.nav_edit_on_bar))
    SettingsCard {
        Text(stringResource(R.string.nav_edit_on_bar_hint), color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        ReorderColumn(items, kindOf = { if (it.route.startsWith(NavPrefs.SHORTCUT_PREFIX)) scKind else tabKind }, switchTag = ::tagOf,
            onMove = { item, to -> NavPrefs.move(context, item.route, to) }, onOff = { toggle(it, false) })
        if (items.size > 5) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.nav_edit_crowded), color = TextTertiary, fontSize = 12.sp, modifier = Modifier.testTag("nav_edit_crowded"))
        }
    }

    val more = bottomNavItems.filter { it.route !in cfg.tabs } +
        NavPrefs.Shortcut.offered().filter { it !in cfg.shortcuts }.map { it.item }
    if (more.isNotEmpty()) {
        GroupLabel(stringResource(R.string.nav_edit_more))
        SettingsCard {
            Text(stringResource(R.string.nav_edit_more_hint), color = TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            val barFull = cfg.size >= NavPrefs.MAX_ITEMS
            more.forEachIndexed { i, item ->
                if (i > 0) SettingsDivider()
                val sc = item.route.startsWith(NavPrefs.SHORTCUT_PREFIX)
                val blocked = barFull || (sc && cfg.shortcuts.size >= NavPrefs.MAX_SHORTCUTS)
                val detail = NavPrefs.Shortcut.of(item.route.removePrefix(NavPrefs.SHORTCUT_PREFIX))?.detail.takeIf { sc }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("nav_more_row_${item.testTag}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(item.unselectedIcon, null, tint = TextSecondary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            when {
                                barFull -> full
                                sc && blocked -> scFull
                                detail != null -> detail
                                else -> tabKind
                            },
                            color = TextTertiary, fontSize = 12.5.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    // Disabled at the limit, but still says why when tapped (the row is the reason).
                    Switch(
                        checked = false, enabled = !blocked,
                        onCheckedChange = { toggle(item, true) },
                        colors = dev.asfakul.yumeiro.ui.theme.yumeSwitchColors(),
                        modifier = Modifier.testTag(tagOf(item)),
                    )
                }
            }
        }
    }
}

/** Fixed row height: the drag maths works in whole rows. 48 dp touch targets inside. */
private val RowH = 60.dp

@Composable
private fun ReorderColumn(
    items: List<BottomNavItem>,
    kindOf: (BottomNavItem) -> String,
    switchTag: (BottomNavItem) -> String,
    onMove: (BottomNavItem, Int) -> Boolean,
    onOff: (BottomNavItem) -> Unit,
) {
    val p = LocalMtPalette.current
    val haptics = LocalHapticFeedback.current
    val reduced = dev.asfakul.yumeiro.ui.components.LocalReducedMotion.current
    val rowPx = with(LocalDensity.current) { RowH.toPx() }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val latest by rememberUpdatedState(items)
    val from = items.indexOfFirst { it.route == dragging }
    // Where the picked-up row would land now (Home's slot 0 is never a target).
    val target = if (from < 0) -1 else (from + (dragY / rowPx).roundToInt()).coerceIn(1, items.lastIndex)
    val homeFirst = stringResource(R.string.nav_edit_home_first)

    Column {
        items.forEachIndexed { i, item ->
            val home = item.route == Screen.Home.route
            val isDragged = item.route == dragging
            // Rows between the origin and the target step aside by one row.
            val shift = when {
                from < 0 || isDragged -> 0f
                i in (from + 1)..target -> -rowPx
                i in target until from -> rowPx
                else -> 0f
            }
            val offset by animateFloatAsState(shift, if (reduced) snap() else spring(dampingRatio = 0.8f, stiffness = 500f), label = "navRowShift")
            val lift by animateFloatAsState(if (isDragged) 1f else 0f, if (reduced) snap() else spring(stiffness = 500f), label = "navRowLift")
            val upLabel = stringResource(R.string.nav_edit_move_up, item.label)
            val downLabel = stringResource(R.string.nav_edit_move_down, item.label)
            val dragLabel = stringResource(R.string.nav_edit_drag, item.label)
            val canUp = !home && i > 1
            val canDown = !home && i < items.lastIndex
            Row(
                Modifier
                    .fillMaxWidth().height(RowH)
                    .graphicsLayer {
                        translationY = if (isDragged) dragY else offset
                        scaleX = 1f + 0.03f * lift; scaleY = 1f + 0.03f * lift
                        shadowElevation = 12f * lift
                    }
                    .clip(yuRound(14))
                    .background(if (isDragged) p.surfaceContainerHigh else androidx.compose.ui.graphics.Color.Transparent)
                    .semantics {
                        customActions = buildList {
                            if (canUp) add(CustomAccessibilityAction(upLabel) { onMove(item, i - 1) })
                            if (canDown) add(CustomAccessibilityAction(downLabel) { onMove(item, i + 1) })
                        }
                    }
                    .testTag("nav_order_row_${item.testTag}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The handle: press and drag (no long press needed; the rest of the row still scrolls the page).
                Box(
                    Modifier.size(40.dp, 48.dp)
                        .then(if (home) Modifier else Modifier.pointerInput(item.route) {
                            detectDragGestures(
                                onDragStart = {
                                    dragging = item.route; dragY = 0f
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragEnd = {
                                    val list = latest
                                    val f = list.indexOfFirst { it.route == item.route }
                                    val t = if (f < 0) -1 else (f + (dragY / rowPx).roundToInt()).coerceIn(1, list.lastIndex)
                                    if (f >= 0 && t != f) onMove(item, t)
                                    dragging = null; dragY = 0f
                                },
                                onDragCancel = { dragging = null; dragY = 0f },
                            ) { change, amount ->
                                change.consume()
                                val list = latest
                                val f = list.indexOfFirst { it.route == item.route }
                                val before = (f + (dragY / rowPx).roundToInt()).coerceIn(1, list.lastIndex)
                                dragY = (dragY + amount.y).coerceIn((1 - f) * rowPx, (list.lastIndex - f) * rowPx)
                                val after = (f + (dragY / rowPx).roundToInt()).coerceIn(1, list.lastIndex)
                                if (after != before) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            }
                        })
                        .semantics { contentDescription = if (home) homeFirst else dragLabel }
                        .testTag("nav_order_handle_${item.testTag}"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (home) Icons.Filled.Lock else Icons.Filled.DragIndicator, null,
                        tint = if (home) TextTertiary else TextSecondary, modifier = Modifier.size(if (home) 18.dp else 22.dp))
                }
                Icon(item.selectedIcon, null, tint = if (isDragged) p.accent else TextPrimary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (home) homeFirst else kindOf(item), color = TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!home) {
                    IconButton(onClick = { onMove(item, i - 1) }, enabled = canUp, modifier = Modifier.testTag("nav_order_up_${item.testTag}")) {
                        Icon(Icons.Filled.KeyboardArrowUp, upLabel)
                    }
                    IconButton(onClick = { onMove(item, i + 1) }, enabled = canDown, modifier = Modifier.testTag("nav_order_down_${item.testTag}")) {
                        Icon(Icons.Filled.KeyboardArrowDown, downLabel)
                    }
                }
                Switch(
                    checked = true, enabled = !home,
                    onCheckedChange = { onOff(item) },
                    colors = dev.asfakul.yumeiro.ui.theme.yumeSwitchColors(),
                    modifier = Modifier.testTag(switchTag(item)),
                )
            }
        }
    }
}
