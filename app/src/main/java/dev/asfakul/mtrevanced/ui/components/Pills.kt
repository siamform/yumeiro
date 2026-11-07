/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.mtrevanced.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.asfakul.mtrevanced.ui.theme.ChipSelectedColor
import dev.asfakul.mtrevanced.ui.theme.GlassFill
import dev.asfakul.mtrevanced.ui.theme.OnChipSelectedColor
import dev.asfakul.mtrevanced.ui.theme.TextPrimary

/** Filter pill: 36dp capsule; selected = tonal moonlight accent, idle = glass. */
@Composable
fun FilterPill(
    label: String,
    selected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "",
    icon: ImageVector? = null,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(shape)
            .then(
                if (selected) Modifier.background(dev.asfakul.mtrevanced.ui.theme.rememberSignatureBrush(), shape)
                else Modifier.background(GlassFill, shape).border(1.dp, TextPrimary.copy(alpha = 0.07f), shape)
            )
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp)
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (selected) Color.White else TextPrimary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else TextPrimary, maxLines = 1)
    }
}

/** Horizontally scrolling pill tabs replacing Material tab rows across the app. */
@Composable
fun PillTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    tagPrefix: String,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState()
    LaunchedEffect(selectedIndex) {
        if (selectedIndex in tabs.indices) runCatching { state.animateScrollToItem((selectedIndex - 1).coerceAtLeast(0)) }
    }
    LazyRow(
        state = state,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(tabs, key = { i, _ -> i }) { index, title ->
            FilterPill(
                label = title,
                selected = index == selectedIndex,
                onClick = { if (index != selectedIndex) onSelect(index) },
                testTag = "${tagPrefix}_$index"
            )
        }
    }
}
