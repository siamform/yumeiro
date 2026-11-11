/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import dev.asfakul.yumeiro.ui.theme.OverlineStyle
import dev.asfakul.yumeiro.ui.theme.SectionTitleStyle

/** Header magnifier: toggles a [HeaderSearchField] instead of pinning a wide box to the page. */
@Composable
fun SearchIconButton(open: Boolean, onToggle: () -> Unit, tag: String, description: String = "Search") {
    val p = LocalMtPalette.current
    IconButton(onClick = onToggle, modifier = Modifier.testTag(tag)) {
        Icon(if (open) Icons.Default.Close else Icons.Default.Search, if (open) "Close search" else description, tint = if (open) p.accent else p.textPrimary)
    }
}

/** Filter button with a count of active filters; opens a [FilterSheet]. */
@Composable
fun FilterIconButton(active: Int, onClick: () -> Unit, tag: String) {
    val p = LocalMtPalette.current
    IconButton(onClick = onClick, modifier = Modifier.testTag(tag)) {
        BadgedBox(badge = { if (active > 0) Badge(containerColor = p.accent, contentColor = p.onAccent) { Text("$active") } }) {
            Icon(Icons.Default.Tune, if (active > 0) "Filters, $active on" else "Filters", tint = if (active > 0) p.accent else p.textPrimary)
        }
    }
}

/**
 * A slim (44dp) glass search field that slides down under the page header only while searching.
 * Focuses itself when opened; the keyboard's search key just hides the keyboard (results are live).
 */
@Composable
fun HeaderSearchField(
    open: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    placeholder: String,
    tag: String,
    modifier: Modifier = Modifier,
) {
    val p = LocalMtPalette.current
    val reduced = reducedMotion()
    AnimatedVisibility(
        visible = open,
        enter = expandVertically(YuMotion.enter(reduced, 260)) + fadeIn(YuMotion.enter(reduced, 200)),
        exit = shrinkVertically(YuMotion.exit(reduced, 180)) + fadeOut(YuMotion.exit(reduced, 140)),
        modifier = modifier,
    ) {
        val focus = remember { FocusRequester() }
        val focusManager = LocalFocusManager.current
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        val shape = RoundedCornerShape(22.dp)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).height(44.dp).clip(shape)
                .background(p.textPrimary.copy(alpha = if (p.isDark) 0.07f else 0.05f))
                .border(1.dp, p.accent.copy(alpha = 0.45f), shape)
                .padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Search, null, tint = p.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) Text(placeholder, color = p.textTertiary, fontSize = 13.sp, maxLines = 1)
                BasicTextField(
                    value = query, onValueChange = onQuery, singleLine = true,
                    textStyle = TextStyle(color = p.textPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(p.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag(tag),
                )
            }
            if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Close, "Clear search", tint = p.textSecondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Bottom sheet holding a page's filters, with Reset and a Show-results button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    onDismiss: () -> Unit,
    onReset: (() -> Unit)?,
    resultCount: Int?,
    tag: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalMtPalette.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = p.surfaceContainerHigh, modifier = Modifier.testTag(tag)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(kanaOverline("絞込", "Refine"), style = OverlineStyle)
                    Text("Filters", style = SectionTitleStyle, color = p.textPrimary)
                }
                if (onReset != null) TextButton(onClick = onReset, modifier = Modifier.testTag(tag + "_reset")) { Text("Reset", color = p.accent, fontWeight = FontWeight.Bold) }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) { content() }
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(48.dp).testTag(tag + "_done"),
            ) { Text(if (resultCount != null) "Show $resultCount result${if (resultCount == 1) "" else "s"}" else "Done", fontWeight = FontWeight.Bold) }
        }
    }
}

/** One labelled group of choices inside a [FilterSheet]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterGroup(title: String, content: @Composable () -> Unit) {
    val p = LocalMtPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), style = OverlineStyle, color = p.textTertiary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}
