/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import kotlinx.coroutines.launch

/**
 * GROUP C (owner master prompt): page tools are small icons instead of wide boxes and paragraphs.
 *  - "?" opens a short help card ([HelpIconButton]) instead of an "about" paragraph in the page.
 *  - "+" opens an add sheet ([AddIconButton] + [AddLinkSheet]) instead of a big link box with an Add button.
 *  - the magnifier opens a slim field only while searching ([SearchIconButton] + [HeaderSearchField]).
 *  - the filter icon opens a sheet ([FilterIconButton] + [FilterSheet]) instead of a row of chips.
 * Every icon is a 48 dp IconButton (touch target, D-pad focus on TV) with a spoken label.
 */
object YuActions {
    const val HELP_SUFFIX = "_help"
    const val PLUS_SUFFIX = "_plus"
}

/** "?" help icon; the text shows in a small card with one "Got it" button. */
@Composable
fun HelpIconButton(title: String, text: String, tag: String, modifier: Modifier = Modifier) {
    val p = LocalMtPalette.current
    var open by rememberSaveable(tag) { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier.testTag(tag)) {
        Icon(Icons.AutoMirrored.Filled.HelpOutline, "Help: $title", tint = p.textSecondary)
    }
    if (open) TsukiDialog(
        onDismissRequest = { open = false },
        title = { Text(title) },
        text = { Text(text, fontSize = 14.sp, modifier = Modifier.testTag(tag + "_text")) },
        confirmButton = { TextButton(onClick = { open = false }, modifier = Modifier.testTag(tag + "_ok")) { Text("Got it", color = p.accent, fontWeight = FontWeight.Bold) } },
        modifier = Modifier.testTag(tag + "_dialog"),
    )
}

/** "+" icon in the accent colour. */
@Composable
fun AddIconButton(description: String, tag: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalMtPalette.current
    IconButton(onClick = onClick, modifier = modifier.testTag(tag)) { Icon(Icons.Default.Add, description, tint = p.accent) }
}

/** A section heading with a hint line and trailing icons (search, filter, add, help). */
@Composable
fun YuSectionHeader(title: String, hint: String? = null, modifier: Modifier = Modifier, icons: @Composable RowScope.() -> Unit = {}) {
    val p = LocalMtPalette.current
    Row(modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            if (hint != null) Text(hint, color = p.textTertiary, fontSize = 11.sp)
        }
        icons()
    }
}

/**
 * The add sheet behind a "+" icon: one link field with Paste, an Add button, and optional quick picks under it.
 * The text stays after a failed add so it can be fixed; a good add closes the sheet. [onAdd] returns true on success.
 * The field is the TV-safe one (the remote's OK opens the keyboard; focus alone does not, FINAL-39).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLinkSheet(
    title: String,
    placeholder: String,
    hint: String?,
    tag: String,
    onDismiss: () -> Unit,
    onAdd: suspend (String) -> Boolean,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val p = LocalMtPalette.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by rememberSaveable(tag) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    fun submit() {
        val url = text.trim()
        if (url.isEmpty() || busy) return
        busy = true
        scope.launch {
            val ok = onAdd(url)
            busy = false
            if (ok) { text = ""; onDismiss() }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = p.surfaceContainerHigh, dragHandle = sheetDragHandle(), modifier = Modifier.testTag(tag)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = dev.asfakul.yumeiro.ui.theme.SectionTitleStyle, color = p.textPrimary)
            hint?.let { Text(it, color = p.textTertiary, fontSize = 12.sp) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                dev.asfakul.yumeiro.ui.screens.watch.WatchSearchField(
                    query = text, onQuery = { text = it.take(800) }, hint = placeholder, tag = tag + "_field",
                    modifier = Modifier.weight(1f), leadingIcon = Icons.Default.Link, keyboardType = KeyboardType.Uri, onDone = { submit() },
                )
                IconButton(onClick = { clipboard.getText()?.text?.trim()?.takeIf { it.isNotEmpty() }?.let { text = it } }, modifier = Modifier.testTag(tag + "_paste")) {
                    Icon(Icons.Default.ContentPaste, "Paste link", tint = p.textSecondary)
                }
            }
            if (busy) LinearProgressIndicator(color = p.accent, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = { submit() }, enabled = !busy && text.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent), shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag(tag + "_add"),
            ) { Text(if (busy) "Adding…" else "Add", fontWeight = FontWeight.Bold) }
            extra?.invoke(this)
        }
    }
}
