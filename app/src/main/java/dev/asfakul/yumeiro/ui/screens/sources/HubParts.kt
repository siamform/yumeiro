package dev.asfakul.yumeiro.ui.screens.sources

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.OnAccent
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import kotlinx.coroutines.launch
import dev.asfakul.yumeiro.ui.theme.yuRound

/**
 * SRC-HUB-1 (owner list 3): every source tab is split into three parts instead of one long list that mixed
 * Discover, your repositories, installed and available extensions.
 */
enum class HubPart(val tag: String) { INSTALLED("installed"), BROWSE("browse"), REPOS("repos") }

object HubParts {
    /** Where a tab opens: what you have, else what your repositories offer, else where to add one. */
    fun default(installed: Int, repos: Int): HubPart = when {
        installed > 0 -> HubPart.INSTALLED
        repos > 0 -> HubPart.BROWSE
        else -> HubPart.REPOS
    }

    fun label(part: HubPart, count: Int?, reposLabel: String = "Repositories"): String = when (part) {
        HubPart.INSTALLED -> "Installed"
        HubPart.BROWSE -> "Browse"
        HubPart.REPOS -> reposLabel
    } + (count?.takeIf { it > 0 }?.let { " · $it" } ?: "")
}

/** The three-part switch at the top of a source tab. [counts] is null for a part with no number to show. */
@Composable
internal fun HubPartRow(
    selected: HubPart, counts: Map<HubPart, Int?>,
    parts: List<HubPart> = HubPart.entries, reposLabel: String = "Repositories", onSelect: (HubPart) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(yuRound(22)).background(GlassFill).padding(4.dp)
            .testTag("hub_parts"),
    ) {
        parts.forEach { part ->
            val sel = part == selected
            val label = HubParts.label(part, counts[part], reposLabel)
            Box(
                Modifier.weight(1f).height(36.dp).clip(yuRound(18)).background(if (sel) SpotifyGreen else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(part) }
                    .semantics { this.selected = sel; contentDescription = label }
                    .testTag("hub_part_${part.tag}"),
                contentAlignment = Alignment.Center,
            ) { Text(label, color = if (sel) OnAccent else TextSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1) }
        }
    }
}

/**
 * A link field with Paste and Add, in the page (it used to sit behind a dialog). The text stays after a failed
 * add so it can be fixed, and clears after a good one. [onAdd] returns true when the link was added.
 */
@Composable
internal fun AddLinkField(title: String, placeholder: String, hint: String?, tag: String, onAdd: suspend (String) -> Boolean) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var text by rememberSaveable(tag) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    fun submit() {
        val url = text.trim()
        if (url.isEmpty() || busy) return
        busy = true
        scope.launch { if (onAdd(url)) text = ""; busy = false }
    }
    Column(Modifier.fillMaxWidth().clip(yuRound(18)).background(GlassFill).padding(14.dp).testTag(tag), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        hint?.let { Text(it, color = TextTertiary, fontSize = 12.sp) }
        OutlinedTextField(
            text, { text = it }, singleLine = true, enabled = !busy,
            leadingIcon = { Icon(Icons.Default.Link, null) },
            trailingIcon = {
                IconButton(onClick = { clipboard.getText()?.text?.trim()?.takeIf { it.isNotEmpty() }?.let { text = it } }, modifier = Modifier.testTag("${tag}_paste")) {
                    Icon(Icons.Default.ContentPaste, "Paste link")
                }
            },
            placeholder = { Text(placeholder, maxLines = 1) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth().testTag("${tag}_field"),
        )
        Button(
            onClick = { submit() }, enabled = !busy && text.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent), shape = yuRound(20),
            modifier = Modifier.fillMaxWidth().testTag("${tag}_add"),
        ) { Text(if (busy) "Adding…" else "Add") }
    }
}

/** An empty part with the one action that fills it. */
@Composable
internal fun HubEmpty(text: String, action: Pair<String, () -> Unit>) {
    Column(Modifier.fillMaxWidth().clip(yuRound(18)).background(GlassFill).padding(16.dp).testTag("hub_empty"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text, color = TextSecondary, fontSize = 14.sp)
        Button(
            onClick = action.second,
            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent), shape = yuRound(20),
            modifier = Modifier.testTag("hub_empty_action"),
        ) { Text(action.first) }
    }
}
